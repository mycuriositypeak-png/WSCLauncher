#pragma once
#include "wsc_mod_api.h"
#include <cstddef>
#include <cstdio>
#include <cstring>

// These profiles belong to this mod, not to Native Mod ABI v1.
namespace profile {
struct Binding { uintptr_t rva; const char* signature; };
struct VtableCheck { uintptr_t table; size_t slot; uintptr_t target; };
#if defined(__aarch64__) || defined(WSC_ROLE_TEST)
#include "arm64.h"
#elif defined(__arm__)
#include "arm32.h"
#else
#error Unsupported Player Roles architecture
#endif

inline bool signatureMatches(const WscHostV1* host, const Binding& binding) {
    // Keep the Thumb bit for calls/hooks, clear it only when reading instructions.
    const auto* bytes = reinterpret_cast<const unsigned char*>(host->game_base + (binding.rva & ~uintptr_t(1)));
    for (size_t i = 0; i < 16; ++i) {
        unsigned expected = 0;
        std::sscanf(binding.signature + i * 2, "%2x", &expected);
        if (bytes[i] != expected) return false;
    }
    return true;
}
inline bool validate(const WscHostV1* host) {
    // The loader verifies the complete per-ABI game SHA256 before init.
    for (const auto& binding : bindings) if (!signatureMatches(host, binding)) return false;
    for (const auto& evidence : layoutEvidence) if (!signatureMatches(host, evidence)) return false;
    for (const auto& check : vtables) {
        uintptr_t actual;
        std::memcpy(&actual, reinterpret_cast<const void*>(host->game_base + check.table + check.slot * sizeof(uintptr_t)), sizeof(actual));
        if (actual != host->game_base + check.target) return false;
    }
    return true;
}
}
