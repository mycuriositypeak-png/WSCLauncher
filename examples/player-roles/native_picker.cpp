#include "native_picker.h"
#include "profiles/game_profile.h"
#include <array>
#include <cmath>
#include <cstring>
#include <functional>
#include <stdexcept>
#include <cstddef>
#include <cstdio>
#include <algorithm>
#include <utility>

// Game-specific, read-only bindings for the SHA256-pinned per-ABI libraries.
// We reuse view components, never the recruiting/selling scene controllers.
namespace role_ui {
namespace {
using Node = void;
struct Vec2 { float x, y; };
struct Size { float width, height; };
struct Rect { float x, y, width, height; };
struct Color3 { unsigned char r, g, b; };
struct Color4 { float r, g, b, a; };
struct Header {
    std::string title;
    float width;
    int anchorAlign, textStyle;
};
struct HeaderSkin {
    int mode = 0;
    Color3 start{55, 55, 55}, end{55, 55, 55};
    std::string unusedImage, unusedFrame;
};
using Renderer = std::function<void(Node*, int, const Rect&)>;
using RowClick = std::function<void(int)>;
using Predicate = std::function<bool(int)>;
using TouchBegin = std::function<bool(void*, void*)>;
using TouchEnd = std::function<void(void*, void*)>;
static_assert(sizeof(std::string) == profile::stringSize && sizeof(Shared) == profile::sharedSize);
static_assert(sizeof(Header) == profile::headerSize && offsetof(Header, width) == profile::stringSize);
static_assert(offsetof(HeaderSkin, unusedImage) == profile::skinStringOffset && sizeof(HeaderSkin) == profile::skinSize);
static_assert(sizeof(Renderer) == profile::functionSize && alignof(Renderer) == profile::functionAlign);
static_assert(sizeof(TouchBegin) == profile::functionSize && sizeof(TouchEnd) == profile::functionSize);

const WscHostV1* api;
template<class T> T field(const void* object, size_t offset) {
    T value; std::memcpy(&value, static_cast<const char*>(object) + offset, sizeof(value)); return value;
}
template<class T> T bind(uintptr_t offset) { return reinterpret_cast<T>(api->game_base + offset); }
template<class T> T method(Node* node, size_t offset) {
    return reinterpret_cast<T>(field<uintptr_t>(field<void*>(node, 0), offset));
}
void add(Node* parent, Node* child, int z = 0) {
    if (!child) throw std::runtime_error("ROLE_PICKER: componente nativo não foi criado.");
    method<void(*)(Node*, Node*, int)>(parent, 57 * sizeof(uintptr_t))(parent, child, z);
}
void position(Node* node, Vec2 point) { method<void(*)(Node*, const Vec2&)>(node, 17 * sizeof(uintptr_t))(node, point); }
void anchor(Node* node, Vec2 point) { method<void(*)(Node*, const Vec2&)>(node, 37 * sizeof(uintptr_t))(node, point); }
Size size(Node* node) { return *method<const Size*(*)(Node*)>(node, 41 * sizeof(uintptr_t))(node); }
Node* label(Node* parent, const std::string& text, Vec2 point, Vec2 pivot,
            Color3 color, float width = 0, int font = 4) {
    // ARM32: width is r3, font size is the first stack argument. ARM64 uses s0/x3.
    Node* result = bind<Node*(*)(const std::string&, int, int, float, int)>(profile::labelCreate.rva)(text, 0, font, width, 0x17);
    if (!result) throw std::runtime_error("ROLE_PICKER: fonte nativa indisponível.");
    method<void(*)(Node*, const Color3&)>(result, 136 * sizeof(uintptr_t))(result, color);
    anchor(result, pivot); position(result, point); add(parent, result); return result;
}
void rectangle(Node* draw, const Rect& rect, Color4 color) {
    auto triangle = bind<void(*)(Node*, const Vec2&, const Vec2&, const Vec2&, const Color4&)>(profile::triangle.rva);
    Vec2 a{rect.x, rect.y}, b{rect.x + rect.width, rect.y},
         c{rect.x + rect.width, rect.y + rect.height}, d{rect.x, rect.y + rect.height};
    triangle(draw, a, b, c, color); triangle(draw, a, c, d, color);
}
bool contains(Rect r, Vec2 p) { return p.x >= r.x && p.x <= r.x+r.width && p.y >= r.y && p.y <= r.y+r.height; }
struct State {
    Node* root = nullptr;
    std::vector<Shared> players;
    std::vector<std::string> names;
    WscChoiceCallback callback;
    uint64_t token;
    Rect body{}, cancel{};
    Vec2 down{};
    bool pressed = false;
    void close() {
        if (!root) return;
        Node* removed = root; root = nullptr; // Make callbacks inert before detaching.
        bind<void(*)(Node*, bool)>(profile::remove.rva)(removed, true);
        // Defer our retained reference until the current native touch callback returns.
        // Immediate release here could destroy the table's executing std::function.
        bind<Node*(*)(Node*)>(profile::autorelease.rva)(removed);
        api->log(4, "ROLE_PICKER: tabela cocos fechada.");
    }
    ~State() { close(); }
};
struct NativePicker final : Picker {
    std::shared_ptr<State> state;
    explicit NativePicker(std::shared_ptr<State> value) : state(std::move(value)) {}
    ~NativePicker() override { state->close(); }
};
}

bool initialize(const WscHostV1* host) {
    api = host;
    return true;
}

std::unique_ptr<Picker> open(void* scene, const char* title,
    const std::vector<Shared>& players, const std::vector<std::string>& names,
    int current, WscChoiceCallback callback, uint64_t token) {
    const Size screen = size(scene);
    const float scale = field<float>(reinterpret_cast<void*>(api->game_base+profile::scale), 0);
    if (!std::isfinite(scale) || scale <= 0 || !std::isfinite(screen.width) || !std::isfinite(screen.height)
            || screen.width < 600*scale || screen.height < 400*scale || players.empty() || names.size() != players.size()) {
        api->log(6, "ROLE_PICKER: dimensões cocos incompatíveis; nenhuma alteração."); return nullptr;
    }
    auto state = std::make_shared<State>();
    auto result = std::make_unique<NativePicker>(state);
    state->players = players; state->names = names; state->callback = callback; state->token = token;
    Node* root = bind<Node*(*)()>(profile::nodeCreate.rva)();
    if (!root) return nullptr;
    bind<void(*)(Node*)>(profile::retain.rva)(root); state->root = root;
    anchor(root, {0, 0}); position(root, {0, 0});
    method<void(*)(Node*, const Size&)>(root, 40 * sizeof(uintptr_t))(root, screen);
    state->body = {0, 0, screen.width, screen.height - 90*scale};
    const float width = std::min(740*scale, screen.width - 160*scale);
    const float left = (screen.width-width)*0.5f;
    const float top = state->body.height - 100*scale;
    state->cancel = {(screen.width-160*scale)*0.5f, 15*scale, 160*scale, 45*scale};
    // The native table draws below its reported node bounds by about one row
    // (observed on the 0.2 device screenshot). Reserve that extent as well as a
    // visible gap above Cancel; the remaining players stay accessible by scroll.
    const float nativeRowExtent = 40*scale;
    const float cancelGap = 24*scale;
    const float tableHeight = top - (state->cancel.y + state->cancel.height + cancelGap) - nativeRowExtent;
    Node* draw = bind<Node*(*)(float)>(profile::drawCreate.rva)(2.f); add(root, draw);
    rectangle(draw, state->body, {0.03f, 0.03f, 0.22f, 1});
    rectangle(draw, state->cancel, {0, 0.64f, 0.32f, 1});
    label(root, title, {screen.width*0.5f, state->body.height - 28*scale}, {0.5f,0.5f}, {255,255,255}, 0, 5);
    label(root, "Selecione o jogador para esta função.", {left, state->body.height-67*scale}, {0,0.5f}, {255,255,255});
    label(root, "CANCELAR", {screen.width*0.5f, state->cancel.y+state->cancel.height*0.5f}, {0.5f,0.5f}, {255,255,255});

    Node* table = bind<Node*(*)()>(profile::tableCreate.rva)();
    if (!table) return nullptr;
    alignas(8) std::array<unsigned char,132> style{};
    std::memcpy(style.data(), static_cast<char*>(table)+profile::tableStyle, style.size());
    // Native defaults already contain alternating grey rows and selected-row colors.
    // Keep numeric headers centered over their values, with wider cells and a
    // separate empty right inset, like the game's transfer tables.
    const float fixed = 20+30+60+110+100+90+24;
    std::vector<Header> headers{
        {"",30*scale,0,0}, {"",60*scale,0,0}, {"JOGADOR",width-fixed*scale,0,0},
        {"",110*scale,1,0}, {"AVAL.",100*scale,1,0}, {"IDADE",90*scale,1,0},
        {"",24*scale,0,0}};
    std::vector<Renderer> renderers;
    for (size_t column=0; column<headers.size(); ++column) renderers.emplace_back([state,column](Node* parent, int index, const Rect& cell) {
        if (column == 6 || !state->root || index < 0 || static_cast<size_t>(index)>=state->players.size()) return;
        const Shared& player = state->players[index];
        const float center = cell.y+cell.height*0.5f;
        // These two original renderers read ONLY the profiled destination Node field.
        alignas(16) std::array<unsigned char,profile::rendererSize> destination{};
        std::memcpy(destination.data()+profile::rendererParent, &parent, sizeof(parent));
        if (column == 0)
            bind<void(*)(void*,Node*,void*,const Shared&,float,float)>(profile::positionRenderer.rva)(nullptr,parent,nullptr,player,cell.x,center);
        else if (column == 1)
            bind<void(*)(void*,const Shared&,float,float)>(profile::portraitRenderer.rva)(destination.data(),player,cell.x,center);
        else if (column == 3)
            bind<void(*)(void*,void*,const Shared&,float,float)>(profile::starsRenderer.rva)(destination.data(),nullptr,player,cell.x+cell.width*0.5f,center);
        else {
            std::string text = column == 2 ? state->names[index] : std::to_string(column == 4
                ? field<int>(player.get(),profile::playerRating) : bind<int(*)(Player*)>(profile::playerAge.rva)(player.get()));
            label(parent,text,{cell.x+(column==2?0:cell.width*0.5f),center},
                {column==2?0.f:0.5f,0.5f},{0,0,0},column==2?cell.width:0);
        }
    });
    RowClick answer = [state](int index) {
        if (!state->root) return;
        api->log(4,"ROLE_PICKER: escolha recebida pela tabela cocos.");
        state->callback(state->token,index);
    };
    Predicate selected = [current](int index) { return index==current; };
    Predicate disabled = [](int) { return false; };
    HeaderSkin skin;
    // The real argument order matters on AAPCS32: width/height occupy r2/r3;
    // skin/style and the remaining pointers start on the stack. AAPCS64 passes
    // these floats in s0/s1 independently of the integer-register arguments.
    using InitTable = void(*)(Node*,Node*,float,float,const HeaderSkin&,const void*,const std::vector<Header>&,
        const std::vector<Renderer>&,int,RowClick*,Predicate*,Predicate*,Node*);
    bind<InitTable>(profile::tableInit.rva)(table,scene,width,tableHeight,skin,style.data(),headers,renderers,
        static_cast<int>(players.size()),&answer,&selected,&disabled,nullptr);
    position(table,{left,top-size(table).height}); add(root,table,1);

    Node* listener = bind<Node*(*)()>(profile::touchCreate.rva)();
    if (!listener) return nullptr;
    bind<void(*)(Node*,bool)>(profile::touchSwallow.rva)(listener,true);
    auto location = bind<Vec2(*)(void*)>(profile::location.rva);
    const Rect backButton{screen.width-260*scale,screen.height-90*scale,260*scale,90*scale};
    const Rect bounds{0,0,screen.width,screen.height};
    *reinterpret_cast<TouchBegin*>(static_cast<char*>(listener)+profile::touchBegin) = [state,location,backButton,bounds](void* touch,void*) {
        if (!state->root) return false;
        state->down=location(touch); state->pressed=contains(state->cancel,state->down);
        // Keep the original BACK reachable; prevent navigation through the menu
        // while the retained role selection is open.
        return contains(bounds,state->down) && !contains(backButton,state->down);
    };
    *reinterpret_cast<TouchEnd*>(static_cast<char*>(listener)+profile::touchEnd) = [state,location,scale](void* touch,void*) {
        if (!state->root) return;
        Vec2 up=location(touch);
        const float distance=std::hypot(up.x-state->down.x,up.y-state->down.y);
        if (state->pressed && contains(state->cancel,up) && distance<25*scale) state->callback(state->token,-1);
        state->pressed=false;
    };
    bind<void(*)(Node*,Node*,Node*)>(profile::dispatch.rva)(field<Node*>(root,profile::eventDispatcher),listener,root);
    add(scene,root,100000); // Below the unchanged game header; above Player Roles cards.
    api->log(4,"ROLE_PICKER: tabela cocos aberta; posição, retrato, estrelas, avaliação e idade nativos.");
    return result;
}
}
