# WSCLauncher

Versão atual: **1.0**

Launcher Android experimental para executar a cópia instalada do **World Soccer Champs** com mods nativos externos. O produto se chama WSCLauncher; 1.0 é sua versão. O jogo é carregado no processo `dev.wsc.launcher:guest`, com dados no sandbox do launcher, separado da instalação original.

Este repositório não inclui o APK original, assets originais nem a biblioteca nativa original do World Soccer Champs. Instale sua própria cópia do jogo, com todos os splits e asset packs.

## Compatibilidade

- Android 7+ (API 24). Builds **Universal** (`arm64-v8a` + `armeabi-v7a`, recomendado), **ARM64** e **ARM32**; o processo do launcher precisa ter a mesma ABI do jogo instalado.
- Em dispositivos ARM64 que possuam uma instalação ARM32 do WSC, pode ser necessário instalar explicitamente a versão ARM32 do WSCLauncher. O universal normalmente executa em ARM64 nesses dispositivos; ele não troca a arquitetura do processo em runtime.
- **Native Mod ABI v1**: interface mínima para carregar módulos, logging e hooks. Sua versão é independente da versão 1.0 do launcher; uma atualização do launcher não exige uma nova ABI.
- Perfil alvo do exemplo: WSC **2026.12**, `versionCode` **468**, com perfis binários e hashes independentes para ARM64 e ARM32.
- **[Player Roles Picker 0.4](examples/player-roles/README.md)** é o exemplo funcional de mod nativo, com código e [ZIP pronto para importar](examples/player-roles/wsc-player-roles-picker-v0.4.zip). A versão 0.4 foi compilada e verificada estaticamente nas duas ABIs; a implementação ARM64 anterior foi validada em Android 15. A nova versão precisa de teste manual em cada arquitetura.

O launcher aceita somente mods executáveis nativos. Importar um ZIP não o ativa: ative o switch na aba **Mods** e inicie/reinicie o jogo pela aba **Início**. Salve a partida antes de reiniciar o guest.

## Interface

A navegação inferior reúne três abas:

- **Início**: instalação, versão e arquitetura do World Soccer Champs, mods ativos e **Jogar**. A detecção é atualizada ao voltar ao aplicativo; o botão fica desabilitado se o jogo estiver ausente ou houver uma incompatibilidade detectada.
- **Mods**: **Adicionar mod**, ativação/desativação, compatibilidade, detalhes e remoção com confirmação.
- **Configurações**: Português (Brasil), Inglês, Espanhol ou idioma do sistema; tema claro, escuro ou sistema; cores dinâmicas no Android 12+; logs selecionáveis, exportação de diagnóstico e licenças.

A interface usa Material 3. O ícone original fornecido para o projeto está em `artwork/wsc-launcher-icon.jpg`; os resources incluem variantes por densidade e ícone adaptativo.

## Compilar e testar

Requisitos: Linux x86_64 para o script nativo, JDK 17+, Android SDK 35, build-tools 35.0.0 e NDK 27.2.12479018. Configure `ANDROID_HOME` para o SDK e `ANDROID_NDK_HOME` para o NDK.

```sh
python3 scripts/build-native.py --ndk "$ANDROID_NDK_HOME"
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/WSCLauncher-1.0-universal.apk
# Alternativas: WSCLauncher-1.0-arm64.apk / WSCLauncher-1.0-arm32.apk.
```

O build nativo gera o host nas duas ABIs e os intermediários do mod nas duas ABIs em `examples/player-roles/build/`. Uma execução de `assembleDebug` gera os três APKs de teste assinados com a chave debug, com o mesmo package, versão e interface. O ZIP versionado do exemplo fica fora dessa pasta e fora do APK do launcher. Instruções de reprodução, atualização do pacote e testes C++ estão no [README do exemplo](examples/player-roles/README.md).

## Documentação

- [Arquitetura](docs/ARCHITECTURE.md): hospedagem, processo guest e Native Mod ABI v1.
- [Modding](docs/MODDING.md): manifest, bibliotecas, hashes e claims.
- [Resolução de problemas](docs/TROUBLESHOOTING.md): compatibilidade, reinício e diagnóstico.

## Limitações atuais

Só a Activity inicial é hospedada. Serviços, providers, jobs e integrações Play/SDKs não são totalmente virtualizados. O UID continua sendo o do launcher; saves privados da instalação original não são acessíveis. Pairip e a inicialização original são executados e podem rejeitar o ambiente.

Não há hot unload nem isolamento entre mods dentro do guest. Mudanças de seleção reiniciam o guest no próximo início pelo launcher. Mods nativos executam com as permissões do launcher; importe código de fontes em que confia. O suporte a bibliotecas em caminhos `apk!/lib/...` depende do linker e do empacotamento do jogo.

O aplicativo usa `targetSdk 28` para hospedagem experimental por sideload; esta versão não está preparada para publicação na Play Store. A validação local de build e testes não substitui testes de renderização, hooks e persistência num aparelho com o jogo instalado.

## Licença e referências

[GPL-3.0](LICENSE). Consulte [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) para dependências, atribuições e a ressalva existente sobre o source correspondente do AAR Pine customizado. A arquitetura usa FusionCore como referência; Pine fornece hooks Java e ShadowHook 1.0.10 fornece hooks nativos. Os bindings de WSC pertencem aos mods externos.
