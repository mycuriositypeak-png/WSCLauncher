#include "wsc_mod_api.h"
#include "selection_policy.h"
#include "native_picker.h"
#include "profiles/game_profile.h"
#include <memory>
#include <string>
#include <vector>
#include <cstring>
#include <cstdio>
#include <cstdint>
#include <exception>

// Profiles: untouched WSC 2026.12 / 468, independently mapped for ARM64 and ARM32.
// The loader checks the complete per-ABI game SHA256.
// All game-specific addresses and data layouts live in this external module.
namespace {
using Player = role_ui::Player;
using Shared = role_ui::Shared;
#ifndef WSC_ROLE_TEST
static_assert(sizeof(Shared) == profile::sharedSize && sizeof(std::string) == profile::stringSize, "Unsupported libc++ ABI");
#endif
using Click = void (*)(void*, int);
using Next = Shared (*)(void*, int); // Nontrivial return: compiler handles AAPCS64 x8 / AAPCS32 hidden r0 result.
using Name = std::string (*)(Player*);
using Ref = void (*)(void*);
const WscHostV1* api;
Click originalClick;
using Back = void (*)(void*);
Back originalBack;
Next originalGeneral, originalPenalty;
Name playerName;
Ref retain, release;
uintptr_t sceneVtable;
void* hooks[4]{};
thread_local void* overrideScene;
thread_local const Shared* overridePlayer;

template<class T> T field(const void* object, size_t offset) {
    T value; std::memcpy(&value, static_cast<const char*>(object) + offset, sizeof(value)); return value;
}
void log(const char* message) { api->log(4, message); }
int id(const Shared& player) { return field<int>(player.get(), profile::playerId); }
struct Snapshot {
    std::vector<Shared> players;
    roles::State roles{};
};
bool snapshot(void* scene, Snapshot& result) {
    if (!scene || field<uintptr_t>(scene, 0) != sceneVtable) return false;
    uintptr_t begin = field<uintptr_t>(scene, profile::playersBegin), end = field<uintptr_t>(scene, profile::playersEnd);
    if (!begin || end < begin || (begin % alignof(Shared)) || (end - begin) % sizeof(Shared) || end - begin > 128 * sizeof(Shared) || end == begin) return false;
    void* state = field<void*>(scene, profile::roleState);
    if (!state) return false;
    uintptr_t penalties = field<uintptr_t>(state, profile::penaltiesBegin), penaltiesEnd = field<uintptr_t>(state, profile::penaltiesEnd);
    if (!penalties || penaltiesEnd < penalties || penaltiesEnd - penalties < 20
            || penaltiesEnd - penalties > 128 * 4 || (penalties & 3)) return false;
    result.roles.captain = field<int>(state, profile::captain);
    result.roles.freeKick = field<int>(state, profile::freeKick);
    std::memcpy(result.roles.penalties.data(), reinterpret_cast<void*>(penalties), 20);
    auto first = reinterpret_cast<const Shared*>(begin);
    for (size_t i = 0; i < (end - begin) / sizeof(Shared); ++i) {
        if (!first[i]) return false;
        result.players.push_back(first[i]); // Retain the original shared control block.
    }
    return true;
}
struct Pending {
    void* scene;
    int role;
    std::vector<Shared> players;
    std::unique_ptr<role_ui::Picker> picker;
    Pending(void* object, int tag, std::vector<Shared>&& candidates)
        : scene(object), role(tag), players(std::move(candidates)) { retain(scene); }
    ~Pending() { picker.reset(); release(scene); }
};
std::unique_ptr<Pending> pending; // Access only on the cocos GL thread.
Shared nextGeneral(void* scene, int current) {
    if (overrideScene == scene && overridePlayer) return *overridePlayer;
    return originalGeneral(scene, current);
}
Shared nextPenalty(void* scene, int current) {
    if (overrideScene == scene && overridePlayer) return *overridePlayer;
    return originalPenalty(scene, current);
}
struct Override {
    Override(void* scene, const Shared& player) { overrideScene = scene; overridePlayer = &player; }
    ~Override() { overrideScene = nullptr; overridePlayer = nullptr; }
};
void chosen(uint64_t token, int index) {
    if (!pending || reinterpret_cast<uintptr_t>(pending.get()) != token) return;
    auto request = std::move(pending);
    try {
        if (index < 0 || static_cast<size_t>(index) >= request->players.size()) { log("ROLE_PICKER: cancelado; nenhuma alteração."); return; }
        Snapshot now;
        if (!snapshot(request->scene, now)) { log("ROLE_PICKER: tela incompatível após escolha."); return; }
        const Shared& selected = request->players[index];
        int selectedId = id(selected);
        bool eligible = false;
        for (const auto& candidate : now.players) if (id(candidate) == selectedId) eligible = true;
        if (!eligible || !now.roles.allows(request->role, selectedId)) { log("ROLE_PICKER: jogador deixou de ser elegível; escolha descartada."); return; }
        if (now.roles.current(request->role) == selectedId) { log("ROLE_PICKER: mantido jogador atual."); return; }
        char message[160];
        std::snprintf(message, sizeof(message), "ROLE_PICKER: aplicar role=%d player=%d pelo callback original.", request->role, selectedId);
        log(message);
        Override selection(request->scene, selected);
        // The original callback performs the role assignment, card refresh and changed flag.
        originalClick(request->scene, request->role);
        Snapshot after;
        if (snapshot(request->scene, after) && after.roles.current(request->role) == selectedId)
            log("ROLE_PICKER: atribuição confirmada na estrutura original.");
        else api->log(6, "ROLE_PICKER: callback não confirmou a atribuição esperada.");
    } catch (const std::exception& error) { api->log(6, error.what()); }
}
void back(void* scene) {
    if (pending && pending->scene == scene) {
        chosen(reinterpret_cast<uintptr_t>(pending.get()), -1); return;
    }
    originalBack(scene);
}
void click(void* scene, int role) {
    if (pending) { log("ROLE_PICKER: seleção já aberta."); return; }
    try {
        Snapshot before;
        if (role < 0 || role > 6 || !snapshot(scene, before)) {
            log("ROLE_PICKER: layout não reconhecido; mantendo ciclo original."); originalClick(scene, role); return;
        }
        std::vector<Shared> candidates;
        std::vector<std::string> names;
        int current = -1;
        for (const auto& player : before.players) {
            if (!before.roles.allows(role, id(player))) continue;
            if (id(player) == before.roles.current(role)) current = static_cast<int>(candidates.size());
            std::string name = playerName(player.get()); // Original localized name formatter, indirect string result.
            if (name.empty() || name.size() > 256) name = "Jogador " + std::to_string(id(player));
            names.push_back(std::move(name)); candidates.push_back(player);
        }
        if (candidates.empty()) { log("ROLE_PICKER: nenhum jogador elegível."); return; }
        static const char* titles[] = {"Capitão", "Pênaltis", "Faltas", "Pênaltis — #2", "Pênaltis — #3", "Pênaltis — #4", "Pênaltis — #5"};
        pending = std::make_unique<Pending>(scene, role, std::move(candidates));
        char message[120];
        std::snprintf(message, sizeof(message), "ROLE_PICKER: abrir role=%d opções=%zu atual=%d.", role, names.size(), current); log(message);
        pending->picker = role_ui::open(scene, titles[role], pending->players, names, current,
                                        chosen, reinterpret_cast<uintptr_t>(pending.get()));
        if (!pending->picker) { pending.reset(); log("ROLE_PICKER: tabela nativa indisponível; nenhuma alteração."); }
    } catch (const std::exception& error) {
        pending.reset(); api->log(6, error.what());
    }
}

}
extern "C" __attribute__((visibility("default"))) int wsc_mod_init(const WscHostV1* host) {
    if (!host || host->api_version != 1 || host->struct_size < sizeof(WscHostV1)
            || !host->game_base || !host->hook || !host->unhook || !host->log) return 1;
    api = host;
    if (!profile::validate(host)) {
        api->log(6, "ROLE_PICKER: assinaturas/layout do perfil incompatíveis; nenhum hook instalado."); return 2;
    }
    if (!role_ui::initialize(host)) return 2;
    sceneVtable = api->game_base + profile::sceneVtable;
    playerName = reinterpret_cast<Name>(api->game_base + profile::playerName.rva);
    retain = reinterpret_cast<Ref>(api->game_base + profile::retain.rva);
    release = reinterpret_cast<Ref>(api->game_base + profile::release.rva);
    hooks[0] = api->hook(reinterpret_cast<void*>(api->game_base + profile::nextGeneral.rva), reinterpret_cast<void*>(nextGeneral), reinterpret_cast<void**>(&originalGeneral));
    if (hooks[0]) hooks[1] = api->hook(reinterpret_cast<void*>(api->game_base + profile::nextPenalty.rva), reinterpret_cast<void*>(nextPenalty), reinterpret_cast<void**>(&originalPenalty));
    if (hooks[1]) hooks[2] = api->hook(reinterpret_cast<void*>(api->game_base + profile::roleClick.rva), reinterpret_cast<void*>(click), reinterpret_cast<void**>(&originalClick));
    if (hooks[2]) hooks[3] = api->hook(reinterpret_cast<void*>(api->game_base + profile::back.rva), reinterpret_cast<void*>(back), reinterpret_cast<void**>(&originalBack));
    if (!hooks[3]) { for (int i = 2; i >= 0; --i) if (hooks[i]) api->unhook(hooks[i]); return 3; }
    char message[120];
    std::snprintf(message, sizeof(message), "ROLE_PICKER: módulo externo 0.4.0 iniciado; quatro hooks %s; seletor cocos disponível.", profile::architecture);
    log(message); return 0;
}
