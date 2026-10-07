#include "selection_policy.h"
#include <cassert>
#include <iostream>
int main() {
    roles::State state{11, 12, {12, 13, 14, 15, 16}};
    const int expected[] = {11, 12, 12, 13, 14, 15, 16};
    for (int role = 0; role < 7; ++role) {
        assert(state.current(role) == expected[role]);
        assert(state.allows(role, state.current(role)));
        assert(state.allows(role, 99));
        for (int slot = 0; slot < 5; ++slot) {
            bool allowed = roles::penaltySlot(role) < 0 || roles::penaltySlot(role) == slot;
            assert(state.allows(role, state.penalties[slot]) == allowed);
        }
    }
    assert(state.allows(0, state.freeKick)); // Captain/free kick can share players with other roles.
    assert(state.allows(2, state.captain));
    assert(!state.allows(-1, 11)); assert(!state.allows(7, 11));
    state.penalties[2] = 99; // Revalidation after opening a picker must exclude newly assigned players.
    assert(!state.allows(1, 99)); assert(state.allows(4, 99));
    std::cout << "Seven-role mapping and penalty uniqueness: passed\n";
}
