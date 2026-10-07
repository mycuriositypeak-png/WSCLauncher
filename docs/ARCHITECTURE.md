# Arquitetura

**WSCLauncher 1.0** é o aplicativo Android (`dev.wsc.launcher`). **Native Mod ABI v1** é a interface entre o launcher e módulos nativos, versionada independentemente. Uma futura versão do aplicativo pode continuar usando ABI v1. Ela não é uma API de gameplay do World Soccer Champs.

## Hospedagem do jogo

A tela principal detecta `com.monkeyibrow.worldsoccerchamps` instalado pelo usuário. `GuestRuntime` usa o contexto desse pacote para obter classes, recursos, APK base, splits e bibliotecas compatíveis com a ABI do processo. Os arquivos originais permanecem intactos e não são redistribuídos.

`GuestStubActivity` inicia numa tarefa própria, no processo `dev.wsc.launcher:guest`. Hooks Java Pine instanciam a Activity original e adaptam o contexto/ciclo de vida. A Application original e sua proteção são inicializadas normalmente. A resolução de bibliotecas aponta para os arquivos instalados ou entradas `apk!/lib/...`.

`GuestContext` combina código/recursos do jogo com armazenamento do launcher. Alguns clientes Google recebem `SdkContext`, que identifica o pacote e UID reais do launcher. Isso não virtualiza todos os serviços Android.

O guest tem o mesmo UID e permissões do launcher. Seu sandbox é separado do sandbox do jogo original, mas compartilhado com o processo principal do launcher. Saves privados do jogo original não são importados. Mods carregados no guest não têm sandbox individual.

## Mods nativos

`ModStore` importa ZIPs, valida manifest formato 2, CRC, limites, ELF/ABI e SHA256 dos payloads, e instala as bibliotecas somente leitura. A seleção persiste em disco, sem cache de preferências entre processos. A ativação verifica versão do jogo e conflitos de claims; o início verifica também a ABI e o hash completo da biblioteca original.

O bootstrap registra em `mods-runtime.txt` o fingerprint da seleção efetivamente preparada. `GuestSession` compara esse fingerprint com a seleção atual e reinicia somente o guest quando necessário no próximo início pelo launcher.

Depois de `Cocos2dxActivity.onLoadNativeLibraries`, `NativeModBridge` carrega **`libwscmodhost.so`** e os módulos `.so` selecionados. O host usa `dlopen(RTLD_NOW | RTLD_LOCAL)`, resolve o export obrigatório **`wsc_mod_init`** e passa `WscHostV1` de [wsc_mod_api.h](../native/include/wsc_mod_api.h). Init deve retornar zero; em falha, o mod deve desfazer seus hooks.

A ABI preserva `api_version`, `struct_size`, base da biblioteca original, logging, hook/unhook e o auxiliar `show_choice`. O host fornece ShadowHook em modo UNIQUE. O auxiliar copia strings UTF-8, mostra um diálogo Android e entrega a resposta no thread GL quando disponível. Player Roles 0.4 usa sua própria UI Cocos e não chama esse auxiliar.

Não há **hot unload**: mesmo um módulo cuja inicialização falhou permanece mapeado até o guest encerrar, evitando ponteiros pendentes. Ativar, desativar ou remover mods exige reinício do guest. Hooks, offsets, assinaturas, estruturas e comportamento específicos de WSC pertencem ao código de cada mod, como [Player Roles](../examples/player-roles/README.md).

## Diagnóstico e limites

O processo principal continua disponível após uma falha do guest. O arquivo de diagnóstico inclui ciclo de vida, seleção instalada/carregada, logcat com rotação e registros/traces de encerramento disponíveis no Android. `onResume` comprova apenas uma etapa do bootstrap; sucesso funcional exige conferir menu e partida no aparelho.

O launcher suporta Android 7+, ARM32 e ARM64 com APK universal e APKs específicos. O processo não troca de ABI; a UI detecta incompatibilidade com a instalação do jogo antes do início. Player Roles 0.4 possui perfis ARM64 e ARM32 de WSC 2026.12 (468), compilados e verificados estaticamente, com validação manual da nova versão ainda pendente. Não há garantia de compatibilidade com versões diferentes, serviços Play ou todos os mecanismos de proteção do jogo.
