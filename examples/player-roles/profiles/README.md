# Perfis binários de Player Roles 0.4

Os dois perfis foram mapeados separadamente a partir de WSC 2026.12 / 468, package `com.monkeyibrow.worldsoccerchamps`. As bibliotecas de análise ficam fora do projeto e não são distribuídas.

| Perfil | ELF | Bytes | SHA256 do jogo |
| --- | --- | ---: | --- |
| ARM64 | ELF64 / AArch64 | 23.382.304 | `6fd30552b2b5419b143b06d12c1aa397d6f24e118d59304ac3db8f8a041c98e4` |
| ARM32 | ELF32 / ARM EABI5, Thumb | 15.951.256 | `a219f831a937e59af2891be9f0d50b6b157bdda1d624220833af3b37db3ab531` |

`arm64.h` preserva os 23 endereços/assinaturas da implementação anterior. `arm32.h` contém os alvos equivalentes identificados por RTTI, vtables, limites de funções `.ARM.exidx`, fluxos de chamada e leitura/escrita de campos no código Thumb. As assinaturas e evidências foram conferidas contra os binários originais. `game_profile.h` seleciona o perfil em compilação e verifica tudo antes de instalar hooks.

Algumas diferenças comprovadas na análise:

| Dado | ARM64 | ARM32 |
| --- | --- | --- |
| Vtable PlayerRolesScene | `0x15b5f40` | `0xedc940` |
| Callback das setas | `0x10bc25c` | `0xb4108d` (Thumb) |
| Próximo jogador / pênalti | `0x10bd020` / `0x10bd1b4` | `0xb41c25` / `0xb41d25` (Thumb) |
| BACK (slot 91 da cena) | `0x10bd390` | `0xb41e61` (Thumb) |
| Vetor de jogadores da cena | `+0x700` / `+0x708` | `+0x43c` / `+0x440` |
| Estado das funções | `+0x718` | `+0x448` |
| ID / avaliação do jogador | `+0x58` / `+0x4c` | `+0x50` / `+0x44` |
| Capitão no estado | `+0x30` | `+0x18` |
| Destino dos renderers de retrato/estrelas | `+0x718` | `+0x44c` |
| Estilo da tabela (132 bytes) | `+0x2e8` | `+0x214` |
| Node: event dispatcher | `+0x1d0` | `+0x184` |
| Touch: begin / end | `+0x80` / `+0xe0` | `+0x40` / `+0x70` |
| `shared_ptr` / `string` | 16 / 24 bytes | 8 / 12 bytes |
| `function` / alinhamento | 48 / 16 bytes | 24 / 8 bytes |
| Cabeçalho de coluna | 40 bytes | 24 bytes |
| Skin: strings / tamanho | `+16` / 64 bytes | `+12` / 36 bytes |

Os slots virtuais usados por Node/Label foram conferidos por função em ambos os binários, antes de calcular o deslocamento com `sizeof(uintptr_t)`. Não se aplicou uma divisão genérica de offsets de objetos.

A convenção de chamada também exige atenção: resultados não triviais `shared_ptr`/`string` usam x8 no AAPCS64 e um argumento de resultado em r0 no AAPCS32; o compilador produz isso pelas assinaturas C++. `Touch::getLocation` retorna uma estrutura por memória no ARM32. Na criação de labels, a largura é r3 e o tamanho da fonte está na pilha; na inicialização da tabela, largura/altura são r2/r3, seguidas pelos ponteiros na pilha. Esses argumentos float têm banco próprio de registradores no ARM64, por isso a ordem incorreta poderia passar despercebida numa implementação exclusiva daquela ABI.

As evidências ARM32 incluem essas ordens, strides das duas vectors, campos de funções, renderers, listener, dispatcher e acesso ao scale. ShadowHook 1.0.10 oferece suporte ARM/Thumb; os ponteiros de funções mantêm o bit Thumb, retirado somente ao ler instruções. Endereços usam `uintptr_t`; tokens de callback continuam `uint64_t` conforme Native Mod ABI v1.

O verificador usa relocations REL/R_ARM_RELATIVE no ARM32 e RELA/R_AARCH64_RELATIVE no ARM64 para comparar vtables. O tamanho e SHA256 completos são obrigatórios. A verificação estática e os asserts de layout do NDK passaram; execução real da 0.4 ainda precisa de teste manual nas duas ABIs.
