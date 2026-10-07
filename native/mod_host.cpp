#include "include/wsc_mod_api.h"
#include "vendor/shadowhook.h"
#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <link.h>
#include <string>
#include <vector>
#include <mutex>
#include <unordered_map>
#include <atomic>
#include <cstring>

static JavaVM* vm;
static jclass bridge;
static jmethodID showChoice;
static std::mutex mutex;
struct Request { WscChoiceCallback callback; uint64_t token; int count; };
static std::unordered_map<uint64_t, Request> requests;
static std::atomic<uint64_t> sequence{1};
static std::vector<void*> plugins; // Keep every loaded module mapped for the whole guest lifetime.
static WscHostV1 host{};
static void logMessage(int priority, const char* text) { __android_log_write(priority, "WSCMod", text ? text : ""); }
static void* hook(void* address, void* replacement, void** original) {
    void* handle = shadowhook_hook_func_addr(address, replacement, original);
    if (!handle) logMessage(ANDROID_LOG_ERROR, shadowhook_to_errmsg(shadowhook_get_errno()));
    return handle;
}
static jbyteArray utf8(JNIEnv* env, const char* value) {
    size_t size = value ? std::strlen(value) : 0;
    if (size > 16384) size = 16384;
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(size));
    if (bytes && size) env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(value));
    return bytes;
}
static uint64_t choice(const char* title, const char* const* labels, int count, int selected,
                       WscChoiceCallback callback, uint64_t token) {
    if (!callback || !labels || count < 1 || count > 128 || selected < -1 || selected >= count) return 0;
    JNIEnv* env = nullptr; bool attached = false;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return 0;
        attached = true;
    }
    if (env->PushLocalFrame(count + 8) != JNI_OK) { if (attached) vm->DetachCurrentThread(); return 0; }
    uint64_t id = sequence.fetch_add(1);
    { std::lock_guard<std::mutex> guard(mutex); requests.emplace(id, Request{callback, token, count}); }
    jclass bytesClass = env->FindClass("[B");
    jobjectArray items = bytesClass ? env->NewObjectArray(count, bytesClass, nullptr) : nullptr;
    if (items) for (int i = 0; i < count; i++) env->SetObjectArrayElement(items, i, utf8(env, labels[i]));
    jbyteArray caption = utf8(env, title);
    jboolean accepted = JNI_FALSE;
    if (!env->ExceptionCheck() && items && caption)
        accepted = env->CallStaticBooleanMethod(bridge, showChoice, static_cast<jlong>(id), caption, items, selected);
    if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); accepted = JNI_FALSE; }
    if (!accepted) { std::lock_guard<std::mutex> guard(mutex); requests.erase(id); id = 0; }
    env->PopLocalFrame(nullptr);
    if (attached) vm->DetachCurrentThread();
    return id;
}
static int findGame(dl_phdr_info* info, size_t, void*) {
    const char* name = info->dlpi_name;
    if (name && std::strstr(name, "libWorldSoccerChamps.so")) { host.game_base = info->dlpi_addr; return 1; }
    return 0;
}
static void fail(JNIEnv* env, const std::string& reason) {
    logMessage(ANDROID_LOG_ERROR, reason.c_str());
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type, reason.c_str());
}
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* value, void*) {
    vm = value; JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass local = env->FindClass("dev/wsc/launcher/NativeModBridge");
    if (!local) return JNI_ERR;
    bridge = static_cast<jclass>(env->NewGlobalRef(local));
    showChoice = env->GetStaticMethodID(bridge, "showChoiceFromNative", "(J[B[[BI)Z");
    if (!bridge || !showChoice) return JNI_ERR;
    host.api_version = 1; host.struct_size = sizeof(host);
    host.log = logMessage; host.hook = hook; host.unhook = shadowhook_unhook; host.show_choice = choice;
    return JNI_VERSION_1_6;
}
extern "C" JNIEXPORT void JNICALL Java_dev_wsc_launcher_NativeModBridge_loadNative(JNIEnv* env, jclass, jstring file) {
    try {
        if (shadowhook_init(SHADOWHOOK_MODE_UNIQUE, false) != 0) { fail(env, "Falha ao iniciar ShadowHook."); return; }
        dl_iterate_phdr(findGame, nullptr);
        if (!host.game_base) { fail(env, "Biblioteca original do jogo ainda não está carregada."); return; }
        const char* path = env->GetStringUTFChars(file, nullptr);
        if (!path) return;
        void* module = dlopen(path, RTLD_NOW | RTLD_LOCAL);
        env->ReleaseStringUTFChars(file, path);
        if (!module) { const char* error = dlerror(); fail(env, std::string("dlopen do mod: ") + (error ? error : "erro desconhecido")); return; }
        plugins.push_back(module); // Even failed init must not leave dangling function pointers after dlclose.
        auto init = reinterpret_cast<WscModInit>(dlsym(module, "wsc_mod_init"));
        if (!init) { fail(env, "Mod não exporta wsc_mod_init."); return; }
        int result = init(&host);
        if (result != 0) fail(env, "wsc_mod_init retornou " + std::to_string(result));
    } catch (const std::exception& error) { fail(env, error.what()); }
}
extern "C" JNIEXPORT void JNICALL Java_dev_wsc_launcher_NativeModBridge_deliverNative(JNIEnv*, jclass, jlong id, jint index) {
    Request request{};
    {
        std::lock_guard<std::mutex> guard(mutex);
        auto entry = requests.find(static_cast<uint64_t>(id));
        if (entry == requests.end()) return;
        request = entry->second; requests.erase(entry);
    }
    if (index < -1 || index >= request.count) index = -1;
    request.callback(request.token, index);
}
