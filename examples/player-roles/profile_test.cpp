// Simulated executable memory: verifies fail-closed init and rollback, not Android hooks.
#define WSC_ROLE_TEST
#include "player_roles.cpp"
#include <cassert>
#include <iostream>
#include <algorithm>
namespace role_ui {
bool initialize(const WscHostV1*) { return true; }
std::unique_ptr<Picker> open(void*, const char*, const std::vector<Shared>&,
    const std::vector<std::string>&, int, WscChoiceCallback, uint64_t) { return nullptr; }
}
namespace {
int installed, failAt;
std::vector<void*> removed;
void silent(int, const char*) {}
void* hookFixture(void* target, void* replacement, void** original) {
    ++installed; assert(target && replacement && original);
    if (installed == failAt) return nullptr;
    *original = target; return reinterpret_cast<void*>(uintptr_t(installed));
}
int unhookFixture(void* handle) { removed.push_back(handle); return 0; }
void reset() { installed = 0; removed.clear(); std::fill(std::begin(hooks), std::end(hooks), nullptr); }
void fill(std::vector<unsigned char>& memory, const profile::Binding& binding) {
    for (size_t i=0; i<16; ++i) {
        unsigned byte=0; std::sscanf(binding.signature+i*2, "%2x", &byte);
        memory[(binding.rva & ~uintptr_t(1))+i] = static_cast<unsigned char>(byte);
    }
}
}
int main() {
    std::vector<unsigned char> memory(profile::gameSize + 4096);
    WscHostV1 host{}; host.api_version=1; host.struct_size=sizeof(host);
    host.game_base=reinterpret_cast<uintptr_t>(memory.data());
    host.log=silent; host.hook=hookFixture; host.unhook=unhookFixture;
    for (const auto& binding : profile::bindings) fill(memory,binding);
    for (const auto& evidence : profile::layoutEvidence) fill(memory,evidence);
    for (const auto& check : profile::vtables) {
        uintptr_t target=host.game_base+check.target;
        std::memcpy(memory.data()+check.table+check.slot*sizeof(uintptr_t), &target, sizeof(target));
    }
    assert(profile::validate(&host));
    host.api_version=2; assert(wsc_mod_init(&host)==1 && installed==0); host.api_version=1;
    for (const auto& binding : profile::bindings) {
        size_t address=binding.rva & ~uintptr_t(1); memory[address]^=1;
        assert(wsc_mod_init(&host)==2 && installed==0); memory[address]^=1;
    }
    for (const auto& evidence : profile::layoutEvidence) {
        memory[evidence.rva]^=1; assert(wsc_mod_init(&host)==2 && installed==0); memory[evidence.rva]^=1;
    }
    size_t slot = profile::vtables[0].table + profile::vtables[0].slot * sizeof(uintptr_t);
    memory[slot]^=1; assert(wsc_mod_init(&host)==2 && installed==0); memory[slot]^=1;
    for (failAt=1; failAt<=4; ++failAt) {
        reset(); assert(wsc_mod_init(&host)==3 && installed==failAt);
        assert(removed.size()==size_t(failAt-1));
        for (size_t i=0; i<removed.size(); ++i) assert(removed[i]==reinterpret_cast<void*>(uintptr_t(failAt-1-i)));
    }
    reset(); failAt=0; assert(wsc_mod_init(&host)==0 && installed==4 && removed.empty());
    std::cout << "Profile guards: 23 signatures, layout/vtable rejection, ABI guard and hook rollback: passed\n";
}
