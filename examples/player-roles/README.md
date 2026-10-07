# Player Roles Picker

Versão atual: **0.4** (manifest `0.4.0`). ID: `wsc.player-roles-picker`.

Exemplo funcional de mod nativo para WSCLauncher, usando **Native Mod ABI v1**. As setas de Player Roles abrem uma tabela no próprio Cocos do jogo, com posição, retrato, estrelas, avaliação e idade. A lógica compartilhada mantém o callback original de atribuição e salvamento.

| Arquitetura | Build e perfil estático | Runtime desta versão |
| --- | --- | --- |
| ARM64 (`arm64-v8a`) | ✓ | Pendente de teste manual |
| ARM32 (`armeabi-v7a`) | ✓ | Pendente de teste manual |

Perfil alvo nas duas ABIs: **World Soccer Champs 2026.12, versionCode 468**, package `com.monkeyibrow.worldsoccerchamps`. O comportamento da versão anterior foi validado em Android 15/ARM64. A 0.4 foi compilada e verificada contra as bibliotecas reais das duas arquiteturas; isso não equivale a executar seus hooks em dispositivo.

Cada arquitetura possui seu próprio [perfil binário](profiles/README.md), offsets, layouts e assinaturas em `profiles/arm64.h` e `profiles/arm32.h`. O código de seleção e renderização permanece compartilhado. Esses bindings pertencem ao mod; não dependem de APIs de gameplay como `wsc.players` ou `wsc.stats`. A ABI do launcher continua v1, e este mod usa a UI Cocos diretamente, sem chamar `show_choice`.

Hashes SHA256 completos exigidos para a biblioteca original do jogo:

```text
ARM64: 6fd30552b2b5419b143b06d12c1aa397d6f24e118d59304ac3db8f8a041c98e4
ARM32: a219f831a937e59af2891be9f0d50b6b157bdda1d624220833af3b37db3ab531
```

Antes dos hooks, o launcher confere o hash do jogo para a ABI selecionada. O mod também confere suas 23 funções, evidências de layout e sete slots de vtable. Uma divergência impede a instalação dos hooks; uma falha durante a instalação desfaz os hooks já instalados. RVAs Thumb preservam o bit de modo para chamadas e ShadowHook, retirando-o somente para ler assinaturas.

## Instalar o pacote pronto

1. Instale sua cópia completa do WSC e um WSCLauncher compatível com a arquitetura dela. Em dispositivo ARM64 com jogo ARM32, use explicitamente o APK ARM32 do launcher.
2. Baixe [wsc-player-roles-picker-v0.4.zip](wsc-player-roles-picker-v0.4.zip) e confira o SHA256 abaixo.
3. Salve a partida. Para substituir a versão anterior, remova **somente o Player Roles** da aba Mods; o importador não sobrescreve IDs existentes. Não apague os dados do launcher.
4. Em **Mods → Adicionar mod**, selecione o ZIP sem extrair.
5. Ative seu switch. O launcher seleciona automaticamente a biblioteca da ABI do processo.
6. Inicie/reinicie o jogo **pelo launcher**. Abrir o ícone original do jogo não carrega o mod.

As setas de Capitão, Faltas e dos cinco cobradores abrem a lista. Escolher o jogador atual ou CANCELAR mantém os dados. BACK fecha primeiro o seletor; com ele fechado, mantém o fluxo original. Cobradores não podem repetir jogadores em outras posições de pênalti. Use BACK e o salvamento normal do jogo para persistir as alterações.

O ZIP contém **exatamente**:

```text
mod.json
lib/arm64-v8a/libplayer_roles.so
lib/armeabi-v7a/libplayer_roles.so
```

Não contém APK, assets, saves nem bibliotecas proprietárias do World Soccer Champs. Nenhum binário do jogo é necessário para compilar.

SHA256 do ZIP:

```text
4d9040a68c8884d71d515aeee88ab143bd24357206892eef43882d228955afb0
```

SHA256 dos payloads:

```text
ARM64: fd3f1ebd22e0f5fad77ad6481e94245edb1b78c9cd77689939818ed8ca9cf885
ARM32: 4dac264d5974ca495504a91810527f6033549eaabf06991714a9f22b41934e49
```

## Compilar e testar

Na raiz, em Linux x86_64 com Android NDK **27.2.12479018**:

```sh
python3 scripts/build-native.py --mod-only --ndk "$ANDROID_NDK_HOME"
```

O script usa C++17, libc++ estática, API Android 24 e os targets `aarch64-linux-android24` / `armv7a-linux-androideabi24`. Compila com `-Wall -Wextra -Werror`, aplica `exports.map`, verifica que somente `wsc_mod_init` está público, remove símbolos desnecessários e gera o manifest com hashes reais e um ZIP dual-ABI. Intermediários ficam em `build/<abi>/`; o ZIP final fica nesta pasta. Sem `--mod-only`, compila também o host nas duas ABIs. Atualize os hashes deste README após recompilar um pacote diferente.

A interface mínima do launcher permanece em [wsc_mod_api.h](../../native/include/wsc_mod_api.h).

Testes C++ host-side, executados da raiz:

```sh
mkdir -p examples/player-roles/build
g++ -std=c++17 -Wall -Wextra -Werror -I native/include \
  examples/player-roles/callback_test.cpp -o examples/player-roles/build/callback-test
examples/player-roles/build/callback-test
g++ -std=c++17 -Wall -Wextra -Werror \
  examples/player-roles/selection_policy_test.cpp -o examples/player-roles/build/policy-test
examples/player-roles/build/policy-test
g++ -std=c++17 -Wall -Wextra -Werror -I native/include \
  examples/player-roles/profile_test.cpp -o examples/player-roles/build/profile-test
examples/player-roles/build/profile-test
./gradlew :app:testDebugUnitTest
```

Os testes C++ cobrem as sete funções, elegibilidade, cancelamento, seleção atual, revalidação, BACK, proteção de init e rollback. Os testes JVM importam o ZIP real, conferem ELF/ABI, payloads, claims e ativação. Testes com fixtures cobrem seleção por processo, hashes independentes, arquivos alterados e mods single-ABI. Eles não carregam o jogo nem executam hooks Android.

Com cópias locais autorizadas das bibliotecas originais **fora do repositório**:

```sh
python3 scripts/verify-player-roles-profile.py /caminho/arm64/libWorldSoccerChamps.so
python3 scripts/verify-player-roles-profile.py /caminho/arm32/libWorldSoccerChamps.so
```

O script detecta ELF64/AArch64 ou ELF32/ARM e verifica o perfil correspondente, incluindo relocations de vtable. Não extrai nem empacota o jogo. Outra versão exige nova análise, não troca de hashes.

## Limitações e diagnóstico

Somente os dois perfis acima são suportados. A versão 0.4 ainda precisa de regressão em dispositivos ARM64 e ARM32: capitão, falta, cinco pênaltis, retrato/posição/avaliação/idade, rolagem, nomes longos, CANCELAR, BACK e persistência após reinício. Mudança programática de cena enquanto a seleção está aberta continua sem suporte; encerrar o guest libera a sessão.

Em falha, exporte o diagnóstico e procure `NATIVE MOD` / `ROLE_PICKER`. Consulte [TROUBLESHOOTING](../../docs/TROUBLESHOOTING.md) e [MODDING](../../docs/MODDING.md). O exemplo segue a [licença do projeto](../../LICENSE), com notices em [THIRD_PARTY_NOTICES](../../THIRD_PARTY_NOTICES.md).
