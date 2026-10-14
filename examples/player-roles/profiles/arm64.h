#pragma once
// Untouched WSC 2026.12 / versionCode 468. Independently mapped binary profile.
// ARM32 function RVAs include the Thumb bit; byte signatures use the canonical address.
constexpr const char* abi = "arm64-v8a";
constexpr const char* architecture = "ARM64";
constexpr const char* gameSha256 = "6fd30552b2b5419b143b06d12c1aa397d6f24e118d59304ac3db8f8a041c98e4";
constexpr size_t gameSize = 23382304;
constexpr size_t sceneVtable = 0x15b5f40;
constexpr size_t playersBegin = 0x700;
constexpr size_t playersEnd = 0x708;
constexpr size_t roleState = 0x718;
constexpr size_t penaltiesBegin = 0x8;
constexpr size_t penaltiesEnd = 0x10;
constexpr size_t captain = 0x30;
constexpr size_t freeKick = 0x0;
constexpr size_t playerId = 0x58;
constexpr size_t playerRating = 0x4c;
constexpr size_t changedFlag = 0x768;
constexpr size_t rendererParent = 0x718;
constexpr size_t rendererSize = 0x720;
constexpr size_t tableStyle = 0x2e8;
constexpr size_t scale = 0x1610e24;
constexpr size_t touchBegin = 0x80;
constexpr size_t touchEnd = 0xe0;
constexpr size_t eventDispatcher = 0x1d0;
constexpr size_t sharedSize = 0x10;
constexpr size_t stringSize = 0x18;
constexpr size_t functionSize = 0x30;
constexpr size_t functionAlign = 0x10;
constexpr size_t headerSize = 0x28;
constexpr size_t skinStringOffset = 0x10;
constexpr size_t skinSize = 0x40;
constexpr Binding roleClick{0x10bc25c, "ff0305d1e9230e6dfd7b0fa9fc6710a9"};
constexpr Binding nextGeneral{0x10bd020, "fd7bbfa9fd0300910a8443f9098043f9"};
constexpr Binding nextPenalty{0x10bd1b4, "fd7bbfa9fd0300910a8443f9098043f9"};
constexpr Binding playerName{0xa76894, "ff0303d1fd7b08a9f74b00f9f6570aa9"};
constexpr Binding retain{0x132115c, "080840b908050011080800b9c0035fd6"};
constexpr Binding release{0x132116c, "080840b908050071080800b940000054"};
constexpr Binding back{0x10bd390, "ff0301d1fd7b02a9f44f03a9fd830091"};
constexpr Binding tableCreate{0x112d448, "fd7bbea9f44f01a9fd030091612600b0"};
constexpr Binding tableInit{0x112d67c, "ffc302d1eb2b036de923046dfd7b05a9"};
constexpr Binding drawCreate{0x12673d8, "e80f1dfcfd7b01a9f44f02a9fd430091"};
constexpr Binding triangle{0x1268a28, "ff4301d1fd7b01a9f85f02a9f65703a9"};
constexpr Binding nodeCreate{0x1281900, "fd7bbea9f44f01a9fd030091c11b00b0"};
constexpr Binding remove{0x1283958, "e80300aa00bc40f9c00000b4090040f9"};
constexpr Binding touchCreate{0x131f34c, "ffc301d1fd7b04a9f65705a9f44f06a9"};
constexpr Binding touchSwallow{0x131f33c, "01600539c0035fd600604539c0035fd6"};
constexpr Binding dispatch{0x1315838, "fd7bbda9f50b00f9f44f02a9fd030091"};
constexpr Binding location{0x1324e04, "fd7bbea9f30b00f9fd030091f30300aa"};
constexpr Binding positionRenderer{0x10cb050, "ff8301d1eb2b016de923026dfd7b03a9"};
constexpr Binding portraitRenderer{0x10cae40, "ff0302d1e923036dfd7b04a9f72b00f9"};
constexpr Binding starsRenderer{0x10caca4, "ff0302d1e923046dfd7b05a9f53300f9"};
constexpr Binding playerAge{0xa75470, "ff8301d1fd7b03a9f52300f9f44f05a9"};
constexpr Binding labelCreate{0x6c2950, "ff0304d1ea4b00fde9230a6dfd7b0ba9"};
constexpr Binding autorelease{0x132118c, "fd7bbea9f30b00f9fd030091f30300aa"};
constexpr Binding bindings[] = {
    roleClick, nextGeneral, nextPenalty, playerName, retain, release, back, tableCreate, tableInit, drawCreate, triangle, nodeCreate, remove, touchCreate, touchSwallow, dispatch, location, positionRenderer, portraitRenderer, starsRenderer, playerAge, labelCreate, autorelease
};
constexpr Binding layoutEvidence[] = {
    {0x10bc2b4, "608e43f908a4c0a9290108cb3f2100f1"},
    {0x10bc344, "788243f905000014a85a40b91f01176b"},
    {0x10bc5f4, "688e43f9153100b9606643f9080040f9"},
    {0x10bc2f0, "295940b9090900b9f41340f9740100b4"},
    {0x10caef0, "22c1221e0101271ee1830091e00314aa"},
    {0x112d524, "60a20b9188e9a772c9eca7720001271e"},
    {0x112d6c0, "fa0301aaf80304aaf90303aaf40302aa"},
    {0x1281774, "68c240f9005940f960ea00f9777e0294"},
};
constexpr VtableCheck vtables[] = {
    {0x15d8fd0, 17, 0x12824a4},
    {0x15d8fd0, 37, 0x1282698},
    {0x15d8fd0, 40, 0x12826f8},
    {0x15d8fd0, 41, 0x12826f0},
    {0x15d8fd0, 57, 0x12838f0},
    {0x15d6690, 136, 0x12857b4},
    {0x15b5f40, 91, 0x10bd390},
};
