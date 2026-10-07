#pragma once
#include "wsc_mod_api.h"
#include <memory>
#include <string>
#include <vector>

namespace role_ui {
struct Player {};
using Shared = std::shared_ptr<Player>;
// Owned by the pending request; all calls and callbacks are on the cocos GL thread.
class Picker {
public:
    virtual ~Picker() = default;
};
bool initialize(const WscHostV1* host);
std::unique_ptr<Picker> open(void* scene, const char* title,
    const std::vector<Shared>& players, const std::vector<std::string>& names,
    int current, WscChoiceCallback callback, uint64_t token);
}
