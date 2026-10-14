// LSPlant JNI bridge for WSCLauncher.
//
// Replaces the canyie/Pine Java method-hooking layer with LSPosed/LSPlant.
//
// JNI bridge: lsplant::Init / Hook / UnHook, exposed as native methods on
// dev/wsc.launcher.Hooker. The bridge is linked into libwscmodhost.so.
//
// Symbol resolution: we implement a robust ELF parser here because:
//   - The v1.0.5 hand-rolled parser returned NULL for some private libart.so
//     symbols, causing lsplant::Init to dereference NULL → SIGSEGV.
//   - The v1.0.6 attempt (Dobby 1.2's DobbySymbolResolver) crashes inside
//     elf_ctx_init on Android 15 because Dobby 1.2 (2021) doesn't handle
//     modern libart.so section layouts (large sh_addralign,GapNum, etc.).
//
// The implementation here walks PT_LOAD program headers to compute the runtime
// load bias of libart.so (handles /apex/com.android.art/lib64/libart.so on
// Android 10+), then parses .dynsym/.symtab with bounds checking on every
// read to avoid the crashes Dobby's older parser hit.
#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <link.h>
#include <elf.h>
#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <string>
#include <string_view>
#include <mutex>

#include <dobby.h>
#include <lsplant.hpp>

#define LOG_TAG "WSCMod"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

// Information about a loaded shared object (soname -> full path + load bias).
struct LoadedLib {
    std::string path;
    uintptr_t load_bias = 0;
};

// Find libart.so's full path and load bias via dl_iterate_phdr.
// On Android 10+ libart.so is at /apex/com.android.art/lib64/libart.so (or
// /apex/com.android.art/lib/libart.so for arm32); dlpi_addr already gives
// us the load bias for PIE'd shared libraries.
LoadedLib findLoadedLib(const char* soname) {
    LoadedLib result;
    struct Cb { const char* needle; LoadedLib* out; } cb{soname, &result};
    dl_iterate_phdr([](struct dl_phdr_info* info, size_t, void* data) -> int {
        Cb* c = static_cast<Cb*>(data);
        if (!info || !info->dlpi_name || !*info->dlpi_name) return 0;
        // Match by suffix; dlpi_name may be a full path ending in /libart.so.
        std::string_view name(info->dlpi_name);
        std::string_view needle(c->needle);
        if (name.size() >= needle.size() &&
            name.substr(name.size() - needle.size()) == needle) {
            c->out->path = info->dlpi_name;
            c->out->load_bias = static_cast<uintptr_t>(info->dlpi_addr);
            return 1;
        }
        return 0;
    }, &cb);
    return result;
}

// Read a file into memory read-only.
struct MappedFile {
    void* base = nullptr;
    size_t size = 0;
    int fd = -1;
    bool ok() const { return base != nullptr; }
    void close() {
        if (base) { munmap(base, size); base = nullptr; }
        if (fd >= 0) { ::close(fd); fd = -1; }
    }
};
MappedFile mapFile(const char* path) {
    MappedFile m;
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return m;
    off_t size = lseek(fd, 0, SEEK_END);
    if (size <= 0) { ::close(fd); return m; }
    void* base = mmap(nullptr, size, PROT_READ, MAP_SHARED, fd, 0);
    if (base == MAP_FAILED) { ::close(fd); return m; }
    m.base = base; m.size = static_cast<size_t>(size); m.fd = fd;
    return m;
}

// Bounds-checked byte reader (kept for future use; currently unused because
// the parsers do their own inline bounds checks inline).
[[maybe_unused]] inline bool inRange(const MappedFile& mf, const void* ptr, size_t need) {
    auto p = reinterpret_cast<uintptr_t>(ptr);
    auto base = reinterpret_cast<uintptr_t>(mf.base);
    return p >= base && p + need <= base + mf.size;
}

// Resolve a symbol by parsing .dynsym and .symtab of an ELF file.
// Uses st_value (file-relative) + the runtime load_bias to produce a runtime
// address. We accept STT_FUNC, STT_OBJECT, and STT_NOTYPE (some ART internals
// are typed NOTYPE).
void* resolveSymbolInElf(const MappedFile& mf, uintptr_t load_bias, std::string_view sym) {
    if (mf.size < sizeof(ElfW(Ehdr))) return nullptr;
    auto* ehdr = static_cast<ElfW(Ehdr)*>(mf.base);
    if (memcmp(ehdr->e_ident, ELFMAG, SELFMAG) != 0) return nullptr;

    // Validate section header table bounds before reading anything.
    uintptr_t shdr_off = ehdr->e_shoff;
    size_t shdr_size = static_cast<size_t>(ehdr->e_shentsize) * ehdr->e_shnum;
    if (shdr_off == 0 || ehdr->e_shnum == 0 || ehdr->e_shentsize < sizeof(ElfW(Shdr))) return nullptr;
    if (shdr_off + shdr_size > mf.size) return nullptr;
    auto* shdrs = reinterpret_cast<ElfW(Shdr)*>(static_cast<char*>(mf.base) + shdr_off);

    // Section-header string table.
    if (ehdr->e_shstrndx >= ehdr->e_shnum) return nullptr;
    ElfW(Shdr)* shstr_hdr = &shdrs[ehdr->e_shstrndx];
    if (shstr_hdr->sh_offset + shstr_hdr->sh_size > mf.size) return nullptr;
    auto* shstr = static_cast<const char*>(mf.base) + shstr_hdr->sh_offset;

    // Locate the symbol/string tables by name (matches both 32 and 64-bit ELFs
    // because ElfW(Shdr) is the right size on each ABI).
    ElfW(Shdr)* dynsym = nullptr;
    ElfW(Shdr)* dynstr = nullptr;
    ElfW(Shdr)* symtab = nullptr;
    ElfW(Shdr)* strtab = nullptr;
    for (int i = 0; i < ehdr->e_shnum; i++) {
        // Bounds-check the section name pointer.
        if (shdrs[i].sh_name >= shstr_hdr->sh_size) continue;
        const char* name = shstr + shdrs[i].sh_name;
        if (shdrs[i].sh_type == SHT_DYNSYM && strcmp(name, ".dynsym") == 0) dynsym = &shdrs[i];
        else if (shdrs[i].sh_type == SHT_SYMTAB && strcmp(name, ".symtab") == 0) symtab = &shdrs[i];
        else if (shdrs[i].sh_type == SHT_STRTAB && strcmp(name, ".dynstr") == 0) dynstr = &shdrs[i];
        else if (shdrs[i].sh_type == SHT_STRTAB && strcmp(name, ".strtab") == 0) strtab = &shdrs[i];
    }

    auto lookup = [&](ElfW(Shdr)* table, ElfW(Shdr)* strtabSection) -> void* {
        if (!table || !strtabSection) return nullptr;
        if (table->sh_entsize < sizeof(ElfW(Sym))) return nullptr;
        if (table->sh_offset + table->sh_size > mf.size) return nullptr;
        if (strtabSection->sh_offset + strtabSection->sh_size > mf.size) return nullptr;
        auto* entries = reinterpret_cast<ElfW(Sym)*>(static_cast<char*>(mf.base) + table->sh_offset);
        const char* strs = static_cast<const char*>(mf.base) + strtabSection->sh_offset;
        size_t strtab_size = strtabSection->sh_size;
        size_t count = table->sh_size / sizeof(ElfW(Sym));
        for (size_t i = 0; i < count; i++) {
            unsigned char type = ELF64_ST_TYPE(entries[i].st_info);
            if (type != STT_FUNC && type != STT_OBJECT && type != STT_NOTYPE) continue;
            if (entries[i].st_value == 0) continue;
            // Bounds-check the symbol name string.
            if (entries[i].st_name >= strtab_size) continue;
            const char* name = strs + entries[i].st_name;
            // Make sure name is NUL-terminated within the strtab.
            const char* end = static_cast<const char*>(memchr(name, '\0', strtab_size - entries[i].st_name));
            if (!end) continue;
            std::string_view sv(name, end - name);
            if (sv == sym) {
                return reinterpret_cast<void*>(load_bias + entries[i].st_value);
            }
        }
        return nullptr;
    };

    void* addr = lookup(dynsym, dynstr);
    if (!addr) addr = lookup(symtab, strtab);
    return addr;
}

// Resolve the first symbol whose name starts with `prefix`.
void* resolveSymbolPrefixInElf(const MappedFile& mf, uintptr_t load_bias, std::string_view prefix) {
    if (mf.size < sizeof(ElfW(Ehdr))) return nullptr;
    auto* ehdr = static_cast<ElfW(Ehdr)*>(mf.base);
    if (memcmp(ehdr->e_ident, ELFMAG, SELFMAG) != 0) return nullptr;
    uintptr_t shdr_off = ehdr->e_shoff;
    size_t shdr_size = static_cast<size_t>(ehdr->e_shentsize) * ehdr->e_shnum;
    if (shdr_off == 0 || ehdr->e_shnum == 0 || ehdr->e_shentsize < sizeof(ElfW(Shdr))) return nullptr;
    if (shdr_off + shdr_size > mf.size) return nullptr;
    auto* shdrs = reinterpret_cast<ElfW(Shdr)*>(static_cast<char*>(mf.base) + shdr_off);
    if (ehdr->e_shstrndx >= ehdr->e_shnum) return nullptr;
    ElfW(Shdr)* shstr_hdr = &shdrs[ehdr->e_shstrndx];
    if (shstr_hdr->sh_offset + shstr_hdr->sh_size > mf.size) return nullptr;
    auto* shstr = static_cast<const char*>(mf.base) + shstr_hdr->sh_offset;

    ElfW(Shdr)* dynsym = nullptr;
    ElfW(Shdr)* dynstr = nullptr;
    ElfW(Shdr)* symtab = nullptr;
    ElfW(Shdr)* strtab = nullptr;
    for (int i = 0; i < ehdr->e_shnum; i++) {
        if (shdrs[i].sh_name >= shstr_hdr->sh_size) continue;
        const char* name = shstr + shdrs[i].sh_name;
        if (shdrs[i].sh_type == SHT_DYNSYM && strcmp(name, ".dynsym") == 0) dynsym = &shdrs[i];
        else if (shdrs[i].sh_type == SHT_SYMTAB && strcmp(name, ".symtab") == 0) symtab = &shdrs[i];
        else if (shdrs[i].sh_type == SHT_STRTAB && strcmp(name, ".dynstr") == 0) dynstr = &shdrs[i];
        else if (shdrs[i].sh_type == SHT_STRTAB && strcmp(name, ".strtab") == 0) strtab = &shdrs[i];
    }

    auto lookup = [&](ElfW(Shdr)* table, ElfW(Shdr)* strtabSection) -> void* {
        if (!table || !strtabSection) return nullptr;
        if (table->sh_entsize < sizeof(ElfW(Sym))) return nullptr;
        if (table->sh_offset + table->sh_size > mf.size) return nullptr;
        if (strtabSection->sh_offset + strtabSection->sh_size > mf.size) return nullptr;
        auto* entries = reinterpret_cast<ElfW(Sym)*>(static_cast<char*>(mf.base) + table->sh_offset);
        const char* strs = static_cast<const char*>(mf.base) + strtabSection->sh_offset;
        size_t strtab_size = strtabSection->sh_size;
        size_t count = table->sh_size / sizeof(ElfW(Sym));
        for (size_t i = 0; i < count; i++) {
            unsigned char type = ELF64_ST_TYPE(entries[i].st_info);
            if (type != STT_FUNC && type != STT_OBJECT && type != STT_NOTYPE) continue;
            if (entries[i].st_value == 0) continue;
            if (entries[i].st_name >= strtab_size) continue;
            const char* name = strs + entries[i].st_name;
            const char* end = static_cast<const char*>(memchr(name, '\0', strtab_size - entries[i].st_name));
            if (!end) continue;
            std::string_view sv(name, end - name);
            if (sv.size() >= prefix.size() && sv.substr(0, prefix.size()) == prefix) {
                return reinterpret_cast<void*>(load_bias + entries[i].st_value);
            }
        }
        return nullptr;
    };

    void* addr = lookup(dynsym, dynstr);
    if (!addr) addr = lookup(symtab, strtab);
    return addr;
}

// Cached libart.so info (path + load_bias).
LoadedLib g_libart;
std::once_flag g_libart_once;
void initLibart() {
    std::call_once(g_libart_once, [] {
        g_libart = findLoadedLib("libart.so");
        if (g_libart.path.empty()) {
            LOGE("libart.so not found in process map");
        } else {
            LOGI("libart.so at %s load_bias=0x%lx", g_libart.path.c_str(), (unsigned long)g_libart.load_bias);
        }
    });
}

void* resolveArtSymbol(std::string_view sym) {
    // Try dlsym first — this handles all EXPORTED libart.so symbols correctly
    // via the dynamic linker's lookup, including all symbols in .dynsym.
    // This is what lsparself does internally for public symbols.
    std::string name(sym);
    // RTLD_DEFAULT searches all libraries loaded in the current process.
    dlerror();  // clear
    void* addr = dlsym(RTLD_DEFAULT, name.c_str());
    if (addr) {
        LOGI("resolveArtSymbol (dlsym): %.*s -> %p", (int)sym.size(), sym.data(), addr);
        return addr;
    }
    // If dlsym didn't find it, try our ELF parser over libart.so's .symtab
    // (which contains private/internal symbols not in .dynsym).
    initLibart();
    if (g_libart.path.empty()) return nullptr;
    MappedFile mf = mapFile(g_libart.path.c_str());
    if (!mf.ok()) {
        LOGE("mapFile(%s) failed", g_libart.path.c_str());
        return nullptr;
    }
    addr = resolveSymbolInElf(mf, g_libart.load_bias, sym);
    mf.close();
    if (addr) {
        LOGI("resolveArtSymbol (elf): %.*s -> %p", (int)sym.size(), sym.data(), addr);
    } else {
        // Log misses so we can see which critical symbols we're failing to resolve.
        LOGE("resolveArtSymbol MISS: %.*s", (int)sym.size(), sym.data());
    }
    return addr;
}

void* resolveArtSymbolPrefix(std::string_view prefix) {
    initLibart();
    if (g_libart.path.empty()) return nullptr;
    MappedFile mf = mapFile(g_libart.path.c_str());
    if (!mf.ok()) return nullptr;
    void* addr = resolveSymbolPrefixInElf(mf, g_libart.load_bias, prefix);
    mf.close();
    if (addr) {
        LOGI("resolveArtSymbolPrefix: %.*s -> %p", (int)prefix.size(), prefix.data(), addr);
    } else {
        LOGE("resolveArtSymbolPrefix MISS: %.*s", (int)prefix.size(), prefix.data());
    }
    return addr;
}

// Inline hooker using Dobby. Returns the trampoline to call the original.
void* inlineHooker(void* target, void* hooker) {
    void* origin = nullptr;
    int ret = DobbyHook(target, hooker, &origin);
    if (ret != 0) {
        LOGE("DobbyHook failed for target=%p hooker=%p ret=%d", target, hooker, ret);
        return nullptr;
    }
    return origin;
}

bool inlineUnhooker(void* func) {
    int ret = DobbyDestroy(func);
    if (ret != 0) {
        LOGE("DobbyDestroy failed for func=%p ret=%d", func, ret);
        return false;
    }
    return true;
}

} // namespace

// Called from mod_host.cpp JNI_OnLoad (the single JNI entry point) so we
// initialize LSPlant before any Java hook is requested.
extern "C" [[gnu::visibility("default")]] bool wsc_lsplant_init(JNIEnv* env) {
    initLibart();
    lsplant::InitInfo info{};
    info.inline_hooker = inlineHooker;
    info.inline_unhooker = inlineUnhooker;
    info.art_symbol_resolver = resolveArtSymbol;
    info.art_symbol_prefix_resolver = resolveArtSymbolPrefix;
    if (!lsplant::Init(env, info)) {
        LOGE("lsplant::Init failed; Java hooks unavailable.");
        return false;
    }
    LOGI("lsplant::Init ok");
    return true;
}

// private native Method doHook(Member target, Method callback);
extern "C" JNIEXPORT jobject JNICALL
Java_dev_wsc_launcher_Hooker_doHook(JNIEnv* env, jobject thiz, jobject target, jobject callback) {
    if (!target || !callback) {
        LOGE("doHook: target or callback is null");
        return nullptr;
    }
    jobject backup = lsplant::Hook(env, target, thiz, callback);
    if (!backup) {
        LOGE("lsplant::Hook returned null for target=%p", target);
    }
    return backup;
}

// private native boolean doUnhook(Member target);
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_wsc_launcher_Hooker_doUnhook(JNIEnv* env, jclass, jobject target) {
    if (!target) return JNI_FALSE;
    return lsplant::UnHook(env, target) ? JNI_TRUE : JNI_FALSE;
}
