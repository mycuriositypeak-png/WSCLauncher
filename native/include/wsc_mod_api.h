#pragma once
// Native Mod ABI v1 is the minimal interface between the launcher and native modules.
// It is not a World Soccer Champs gameplay API. Its version is independent of
// the WSCLauncher release version; preserve the WscHostV1 layout and field order.
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef void (*WscChoiceCallback)(uint64_t token, int index); // -1: cancel. Called on the cocos GL thread.
typedef struct WscHostV1 {
    uint32_t api_version;
    uint32_t struct_size;
    uintptr_t game_base;
    void (*log)(int priority, const char* message);
    void* (*hook)(void* target, void* replacement, void** original);
    int (*unhook)(void* handle);
    uint64_t (*show_choice)(const char* title, const char* const* labels, int count, int selected,
                            WscChoiceCallback callback, uint64_t token);
} WscHostV1;
// Loaded only after the original game library. Return 0 on success; undo installed hooks on failure.
typedef int (*WscModInit)(const WscHostV1* host);
__attribute__((visibility("default"))) int wsc_mod_init(const WscHostV1* host);
#ifdef __cplusplus
}
#endif
