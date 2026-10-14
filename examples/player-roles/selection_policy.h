#pragma once
#include <array>
#include <algorithm>

namespace roles {
// Tags in the original PlayerRolesScene, independent of the selection UI.
inline int penaltySlot(int role) {
    if (role == 1) return 0;
    if (role >= 3 && role <= 6) return role - 2;
    return -1;
}
struct State {
    int captain, freeKick;
    std::array<int, 5> penalties;
    int current(int role) const {
        if (role == 0) return captain;
        if (role == 2) return freeKick;
        int slot = penaltySlot(role);
        return slot >= 0 ? penalties[slot] : -1;
    }
    bool allows(int role, int player) const {
        if (role < 0 || role > 6) return false;
        int slot = penaltySlot(role);
        if (slot < 0) return true;
        for (int i = 0; i < 5; ++i) if (i != slot && penalties[i] == player) return false;
        return true;
    }
};
}
