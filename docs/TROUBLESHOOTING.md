# Resolução de problemas

## Jogo não detectado

Instale World Soccer Champs (`com.monkeyibrow.worldsoccerchamps`) com todos os splits/asset packs e confirme que abre normalmente. O launcher lê a instalação do usuário; não fornece o jogo. A instalação precisa estar acessível no mesmo perfil Android.

## ABI incompatível

O launcher e as bibliotecas do jogo precisam ter a mesma ABI. Instale `WSCLauncher-<versão>-arm64.apk` para ARM64 ou `WSCLauncher-<versão>-arm32.apk` para ARM32; suporte ARM64 do aparelho não comprova a ABI da instalação. Confira `ARQUITETURAS DO JOGO`, `ABI DO PROCESSO` e `DIRETÓRIO NATIVO` no diagnóstico. Player Roles 0.4 inclui as duas ABIs; outros mods podem fornecer somente uma. O universal contém ambas as bibliotecas do launcher, mas normalmente roda em ARM64 num dispositivo ARM64. Para WSC instalado somente em ARM32, use o APK ARM32 do launcher. A Home mostra a incompatibilidade e mantém Jogar desabilitado.

## Versão ou hash incompatível

Um mod declara exatamente o `versionCode` do jogo e o SHA256 de sua biblioteca original por ABI. Player Roles suporta WSC 2026.12 (468) e o hash indicado em seu [README](../examples/player-roles/README.md). Desative mods incompatíveis ou obtenha uma atualização feita para seu perfil. Não troque hashes/offsets apenas para contornar a validação; o mod também confere assinaturas antes de instalar hooks.

Hash incorreto do payload indica pacote corrompido ou biblioteca instalada alterada. Confira o SHA256 do ZIP distribuído e importe novamente uma cópia confiável, removendo primeiro o ID já instalado.

## Formato antigo ou mod já instalado

Somente `native` / formato 2 / Native Mod ABI v1 é aceito. Remova os mods declarativos antigos usando a versão anterior do launcher **antes de atualizar**: manifests persistidos antigos também são rejeitados pela linha 1.x e podem impedir a listagem. Não limpe os dados do aplicativo como primeira tentativa, pois isso também apaga saves do sandbox do launcher.

Para atualizar Player Roles, salve o jogo, remova somente o mod instalado e importe seu ZIP 0.4. O importador não sobrescreve um ID existente. Código-fonte e ZIP ficam em [examples/player-roles](../examples/player-roles/README.md).

## Conflito de claims

Dois mods não podem ocupar o mesmo ponto de integração declarado. Desative o mod conflitante antes de ativar outro. A ativação rejeitada preserva a seleção anterior; veja os nomes envolvidos na mensagem.

## Alterações não aparecem

Salve a partida antes de voltar ao launcher. Na aba **Mods**, ative/desative o mod; depois, na aba **Início**, toque em **Jogar** ou **Reiniciar com os mods**. A seleção só muda num guest novo, sem hot unload. O launcher reinicia o guest quando o fingerprint da seleção muda. Se uma falha deixou o mesmo guest aberto sem mudança de seleção, force a parada do launcher e abra novamente. Abrir o jogo pelo ícone original não carrega os mods do launcher.

## Permissões e serviços

Se o jogo ficar parado na abertura em uma build de release, confira se o diagnóstico registra `PROVIDERS: Firebase concluído` antes de `APPLICATION: attach concluído` e do Cocos. O launcher inicializa o `FirebaseInitProvider` original antes dessas etapas. Inicializar Firebase somente depois de uma requisição HTTP instrumentada do SDKBOX pode bloquear `FirebaseApp` e o inicializador de `SessionManager`, antes de qualquer hook de UI de mod. A linha `bootstrap=context-before-create-v2+firebase-provider-v1` identifica o fluxo corrigido. Caso o problema continue, exporte um diagnóstico novo; não apague os dados ou os saves para testar.

Se o jogo encerrar segundos após abrir quando a rede permite anúncios, procure `FATAL EXCEPTION: GassClient`, `GassDGClient` ou `LooperProvider` e `SecurityException: Unknown calling package name 'com.monkeyibrow.worldsoccerchamps'`. No caso relatado, o jogo funcionava com DNS privado bloqueando anúncios e encerrava cerca de 20 segundos após abrir com esse DNS desativado. Essa exceção indica que o SDK de anúncios apresentou o pacote do jogo a um serviço Android usando o UID do launcher. O hook de `BaseGmsClient` do APK não cobre clientes carregados em outro módulo pelo Google Play Services. O launcher adapta o contexto na inicialização do MobileAds e nos criadores de clientes, antes de enviar esse contexto ao módulo local ou remoto. O usuário confirmou que a build de teste deixou de apresentar o crash ao abrir o jogo com o DNS desativado. As linhas `SDK ADS CONTEXT` registram os pontos instalados; os nomes internos são específicos do SDK de WSC 468 e classes ausentes são registradas. Esse ajuste não trata toda falha de rede ou de anúncios.

Para validar uma correção desse crash, compare o mesmo aparelho e instalação com DNS privado ativo e desativado, primeiro com os mods desativados e depois com a seleção habitual. Aguarde além do tempo em que ocorria a falha, abra uma partida e exporte o diagnóstico de qualquer novo encerramento. Confira também o funcionamento dos anúncios quando a rede permitir. A ausência de `Unknown calling package` em testes JVM não substitui a execução real do módulo Google no aparelho.

O guest usa as permissões e UID do launcher. O manifest mantém `VIBRATE`, necessária para evitar `SecurityException` na vibração do jogo; é uma permissão normal, sem pedido em runtime. Os clientes Google observados recebem contexto do launcher, mas serviços/providers/jobs não são virtualizados por completo. Se aparecer `Unknown calling package`, falha de agendamento ou outra permissão negada, exporte o diagnóstico; não suponha que permissões da instalação original valham para o guest.

## Exportar dados do jogo

Em **Configurações → Dados do jogo → Exportar dados do jogo**, escolha **Completa** ou **Leve**, toque em **Escolher destino** e salve o ZIP pelo seletor de arquivos do Android. A exportação é feita em segundo plano, com barra e porcentagem, e continua durante a rotação da tela. A etapa inicial de levantamento dos arquivos usa uma barra indeterminada; a porcentagem acompanha os bytes e entradas processados. 100% só é emitido depois de fechar o ZIP e seu destino.

Salve a partida antes de exportar. Depois que o destino é escolhido, o launcher encerra somente seu próprio processo guest e aguarda seu encerramento antes de ler os arquivos. Isso evita copiar saves enquanto o jogo escreve neles. Uma falha ou alteração dos arquivos durante a cópia é apresentada como erro; a exportação não modifica os saves.

- **Completa:** inclui todos os arquivos regulares e pastas acessíveis dentro da pasta privada desta instalação, normalmente `/data/user/0/dev.wsc.launcher/`, incluindo recursos baixados, mods, logs e caches. Links simbólicos e arquivos especiais são omitidos, sem seguir destinos externos.
- **Leve:** preserva todos os arquivos dos slots `files/savegame_slot_*`, inclusive `data.bin.old` e o `datapack.db` de cada slot; configurações e arquivos de dados na raiz de `files` (`.db` e seus auxiliares, `.bin`, `.csv` e outros formatos de configuração); `shared_prefs`, `databases`, dados de `monkey_ranks` e DataStore fora das pastas Firebase. Omite logos, adboards, pacotes de recursos ZIP, mods, logs, caches e relatórios de crash. O filtro foi conferido contra o exemplo de WSC 468; uma versão futura que mude a organização dos saves pode exigir atualização. Para uma cópia de toda a árvore, escolha Completa.

O ZIP contém a árvore original dentro de **`data/`** e um **`wsc-export.json`** na raiz, formato 2, com pacote de origem, modo, quantidade, caminhos relativos, tamanhos e SHA256 dos arquivos. O prefixo evita conflito com um arquivo de metadados já existente na pasta exportada. Não há importação/restauração automática nesta opção.

Cada instalação exporta seu próprio sandbox. Uma build de teste com outro applicationId exporta os dados dessa build, e não os dados privados do launcher original ou do aplicativo original do jogo. O launcher acessa sua própria pasta sem root e usa o seletor do Android para gravar o ZIP, sem solicitar acesso geral ao armazenamento.

## Exportar diagnóstico

Depois de reproduzir, reabra o launcher e acesse **Configurações → Diagnóstico → Exportar diagnóstico**. O arquivo contém metadados, log do bootstrap/ciclo de vida, logcat do guest com rotação, seleção de mods instalada/carregada e registros/traces de encerramento quando disponíveis (Android 11+ para `ApplicationExitInfo`). Procure `NATIVE MOD`, `WSCMod` e `ROLE_PICKER` para o exemplo. Logs podem conter caminhos e dados da sessão; revise antes de compartilhar.

A captura embutida é limitada ao acesso permitido ao aplicativo. Para capturar também mensagens disponíveis via ADB, conecte um aparelho autorizado a um computador e execute da raiz:

```sh
scripts/capture-test.sh /tmp/wsc-test
```

O script força a parada do launcher, abre sua tela e aguarda que você reproduza o problema e pressione Enter. Salve antes de executá-lo. Requer ADB no PATH. `onResume` nos logs não prova renderização correta; confira visualmente o menu, uma partida e o comportamento do mod.
