#pragma once
// Untouched WSC 2026.12 / versionCode 468. Independently mapped binary profile.
// ARM32 function RVAs include the Thumb bit; byte signatures use the canonical address.
constexpr const char* abi = "armeabi-v7a";
constexpr const char* architecture = "ARM32";
constexpr const char* gameSha256 = "a219f831a937e59af2891be9f0d50b6b157bdda1d624220833af3b37db3ab531";
constexpr size_t gameSize = 15951256;
constexpr size_t sceneVtable = 0xedc940;
constexpr size_t playersBegin = 0x43c;
constexpr size_t playersEnd = 0x440;
constexpr size_t roleState = 0x448;
constexpr size_t penaltiesBegin = 0x4;
constexpr size_t penaltiesEnd = 0x8;
constexpr size_t captain = 0x18;
constexpr size_t freeKick = 0x0;
constexpr size_t playerId = 0x50;
constexpr size_t playerRating = 0x44;
constexpr size_t changedFlag = 0x474;
constexpr size_t rendererParent = 0x44c;
constexpr size_t rendererSize = 0x450;
constexpr size_t tableStyle = 0x214;
constexpr size_t scale = 0xf0dfb4;
constexpr size_t touchBegin = 0x40;
constexpr size_t touchEnd = 0x70;
constexpr size_t eventDispatcher = 0x184;
constexpr size_t sharedSize = 0x8;
constexpr size_t stringSize = 0xc;
constexpr size_t functionSize = 0x18;
constexpr size_t functionAlign = 0x8;
constexpr size_t headerSize = 0x18;
constexpr size_t skinStringOffset = 0xc;
constexpr size_t skinSize = 0x24;
constexpr Binding roleClick{0xb4108d, "f0b503af2de900072ded0c8bb4b08146"};
constexpr Binding nextGeneral{0xb41c25, "80b56f46d1f83ce401f23c4cd1f84034"};
constexpr Binding nextPenalty{0xb41d25, "f0b503af2de9000f81b0d1f83c3401f2"};
constexpr Binding playerName{0x6660a1, "f0b503af4df8048d8eb0804670480e46"};
constexpr Binding retain{0xd154cf, "416801314160704741680139416018bf"};
constexpr Binding release{0xd154d7, "41680139416018bf7047016849680847"};
constexpr Binding back{0xb41e61, "d0b502af84b004461b48784400680068"};
constexpr Binding tableCreate{0xb9bedd, "b0b502af1348784405684ff446702946"};
constexpr Binding tableInit{0xb9c059, "f0b503af2de9000f81b02ded068b8eb0"};
constexpr Binding drawCreate{0xc8f6e9, "f0b503af4df804bd0646144878440568"};
constexpr Binding triangle{0xc909a5, "f0b503af2de9000782b004463a488a46"};
constexpr Binding nodeCreate{0xca3485, "b0b502af1148784405684ff402702946"};
constexpr Binding remove{0xca4bfd, "0a460146d0f85801002808bf70470368"};
constexpr Binding touchCreate{0xd14141, "f0b503af4df804bd88b0314831497844"};
constexpr Binding touchSwallow{0xd14135, "80f8ac10704790f8ac007047f0b503af"};
constexpr Binding dispatch{0xd0d285, "f0b503af4df804bd054608680c461646"};
constexpr Binding location{0xd17fad, "b0b502af0c460546eef778f804f11802"};
constexpr Binding positionRenderer{0xb4d0ed, "f0b503af4df8048d2ded0c8b84b04448"};
constexpr Binding portraitRenderer{0xb4cf65, "f0b503af2de9000f8fb081465b489846"};
constexpr Binding starsRenderer{0xb4ce41, "f0b503af4df804bd2ded048b88b00446"};
constexpr Binding playerAge{0x6651dd, "b0b502af86b004462d48784400680068"};
constexpr Binding labelCreate{0x3a5e11, "f0b503af2de9000f81b02ded068b9cb0"};
constexpr Binding autorelease{0xd154e7, "d0b502af0446e7f7d6fee8f7b8f82146"};
constexpr Binding bindings[] = {
    roleClick, nextGeneral, nextPenalty, playerName, retain, release, back, tableCreate, tableInit, drawCreate, triangle, nodeCreate, remove, touchCreate, touchSwallow, dispatch, location, positionRenderer, portraitRenderer, starsRenderer, playerAge, labelCreate, autorelease
};
constexpr Binding layoutEvidence[] = {
    {0xb9c068, "0a930446dff828049346d7f814a00d46"},
    {0xb9c23c, "d7f8289008ee100a954894eda12a7844"},
    {0x3a5e26, "9846154678448946d7f808a000680068"},
    {0xb410c8, "d9f848044946826923a800f0a7fddde9"},
    {0xb410d6, "dde92305046d9db1281dbff35b8f50e8"},
    {0xb41118, "d9f83c64d9f84044a64200f05383d9f8"},
    {0xb41e06, "d8e90146b4420fd08a4523d953f83120"},
    {0xb41cfc, "5ef8323049680029c0e9003108bf80bd"},
    {0xb4d01e, "d9f84c044ff6fe78c7f6ff7842460168"},
    {0xb4ce5c, "1068406c1ff734f4054618205ef372ef"},
    {0xb9c0d2, "04f5057049468422c4f804520cf3c2ea"},
    {0xb9c0f2, "d8e90012531adb106b4301f016f8be69"},
    {0xb9c10e, "dae90012531adb106b4301f044f9d7f8"},
    {0xb9c574, "06f10c0015f134f8054606f1180015f1"},
    {0xb4d254, "05f1400100903046cde90346c8f7dadb"},
    {0xb4d28a, "05f1700100903046cde9034628f663f9"},
    {0xca3374, "d4f85c01c06dc4f8840172f0a6f83f48"},
    {0xb4d174, "284878440568206895ed00aad0f8a410"},
};
constexpr VtableCheck vtables[] = {
    {0xeee28c, 17, 0xca3e7d},
    {0xeee28c, 37, 0xca3fbd},
    {0xeee28c, 40, 0xca4011},
    {0xeee28c, 41, 0xca400d},
    {0xeee28c, 57, 0xca4bbf},
    {0xeecde0, 136, 0xca634f},
    {0xedc940, 91, 0xb41e61},
};
