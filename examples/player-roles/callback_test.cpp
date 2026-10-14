// Desktop integration test with a simulated game state. Does not validate Android hooking/rendering.
#define WSC_ROLE_TEST
#include "player_roles.cpp"
#include <array>
#include <cassert>
#include <iostream>
namespace {
std::array<char, 0x800> sceneBytes{};
std::array<char, 0x80> stateBytes{};
std::array<int, 5> penaltyIds{2, 3, 4, 5, 6};
std::vector<Shared> squad;
int refCount, calls, pickerCount, selectedIndex, backCalls, closeCount;
std::vector<std::string> shown;
uint64_t callbackToken;
WscChoiceCallback callback;
template<class T> void put(void* object, size_t offset, T value) { std::memcpy(static_cast<char*>(object) + offset, &value, sizeof(value)); }
void quiet(int, const char*) {}
void fakeRetain(void*) { ++refCount; }
void fakeRelease(void*) { --refCount; }
std::string fakeName(Player* player) { return "João " + std::to_string(field<int>(player, 0x58)); }
Shared fakeNext(void*, int) { return squad.back(); }
void fakeBack(void*) { ++backCalls; }
void fakeOriginal(void* scene, int role) {
    ++calls;
    Snapshot old; assert(snapshot(scene, old));
    Shared selected = roles::penaltySlot(role) < 0 ? nextGeneral(scene, old.roles.current(role)) : nextPenalty(scene, old.roles.current(role));
    int chosenId = id(selected);
    if (role == 0) put(stateBytes.data(), 0x30, chosenId);
    else if (role == 2) put(stateBytes.data(), 0, chosenId);
    else penaltyIds[roles::penaltySlot(role)] = chosenId;
    put(scene, 0x768, uint8_t(1));
}
}
namespace role_ui {
struct FakePicker : Picker { ~FakePicker() override { ++closeCount; } };
bool initialize(const WscHostV1*) { return true; }
std::unique_ptr<Picker> open(void*, const char*, const std::vector<Shared>&,
        const std::vector<std::string>& names, int selected, WscChoiceCallback answer, uint64_t token) {
    ++pickerCount; shown = names; selectedIndex = selected; callback = answer; callbackToken = token;
    return std::make_unique<FakePicker>();
}
}
int main() {
    WscHostV1 service{}; service.log = quiet; api = &service;
    originalBack = fakeBack; originalClick = fakeOriginal; originalGeneral = originalPenalty = fakeNext; playerName = fakeName;
    retain = fakeRetain; release = fakeRelease; sceneVtable = 0x12345678;
    for (int i = 1; i <= 11; ++i) {
        auto bytes = std::make_shared<std::array<char, 0x100>>(); put(bytes->data(), 0x58, i);
        squad.emplace_back(bytes, reinterpret_cast<Player*>(bytes->data()));
    }
    auto scene = sceneBytes.data();
    put(scene, 0, sceneVtable); put(scene, 0x700, reinterpret_cast<uintptr_t>(squad.data()));
    put(scene, 0x708, reinterpret_cast<uintptr_t>(squad.data() + squad.size())); put(scene, 0x718, static_cast<void*>(stateBytes.data()));
    put(stateBytes.data(), 8, reinterpret_cast<uintptr_t>(penaltyIds.data()));
    put(stateBytes.data(), 16, reinterpret_cast<uintptr_t>(penaltyIds.data() + 5));
    put(stateBytes.data(), 0x30, 1); put(stateBytes.data(), 0, 2);
    for (int role = 0; role < 7; ++role) {
        calls = 0; put(scene, 0x768, uint8_t(0));
        Snapshot before; assert(snapshot(scene, before));
        click(scene, role); assert(calls == 0 && refCount == 1 && pending);
        assert(selectedIndex >= 0 && shown[selectedIndex] == "João " + std::to_string(before.roles.current(role)));
        int opened = pickerCount; click(scene, role); assert(pickerCount == opened); // Only one modal request.
        callback(callbackToken, -1); assert(calls == 0 && refCount == 0 && !pending && field<uint8_t>(scene, 0x768) == 0);
        click(scene, role); callback(callbackToken, selectedIndex); assert(calls == 0 && refCount == 0); // Same player.
        click(scene, role);
        int index = -1, desired = 0;
        for (size_t i = 0; i < pending->players.size(); ++i) if (id(pending->players[i]) != before.roles.current(role)) { index = static_cast<int>(i); desired = id(pending->players[i]); break; }
        assert(index >= 0); callback(callbackToken, index);
        Snapshot after; assert(snapshot(scene, after)); assert(after.roles.current(role) == desired);
        assert(calls == 1 && refCount == 0 && !pending && !overridePlayer && field<uint8_t>(scene, 0x768) == 1);
    }
    calls = 0; click(scene,0); int closed = closeCount; back(scene);
    assert(!pending && calls==0 && backCalls==0 && closeCount==closed+1 && refCount==0);
    back(scene); assert(backCalls==1); // BACK closes the picker first, then delegates normally.
    callback(callbackToken,0); assert(calls==0); // Callback arriving after close is inert.
    assert(nextGeneral(scene, 0) == squad.back()); // Outside a selection, original cycle still runs.
    calls = 0; click(scene, 1);
    int index = static_cast<int>(pending->players.size()) - 1;
    penaltyIds[1] = id(pending->players[index]); // Another role took the candidate while the picker was open.
    callback(callbackToken, index); assert(calls == 0 && refCount == 0 && !pending);
    std::cout << "Callback integration: 7 roles, cancel, unchanged, single assignment, modal exclusion, stale choice, BACK cancellation and UI disposal: passed\n";
}
