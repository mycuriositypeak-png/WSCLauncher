# Mods nativos

WSCLauncher 1.0 aceita exclusivamente **ZIPs de módulos nativos executáveis**, com **manifest formato 2**, `type: native` e **Native Mod ABI v1** (`api: 1`). O formato antigo `plist-patch` foi removido e seus manifests são rejeitados.

Native Mod ABI v1 é a interface mínima entre o launcher e módulos nativos. Ela não é uma API de gameplay do World Soccer Champs. A versão da ABI é independente da versão do launcher. O [header](../native/include/wsc_mod_api.h) continua com o layout `WscHostV1` usado pelo [Player Roles Picker 0.4](../examples/player-roles/README.md).

## Pacote e manifest

O ZIP contém `mod.json` na raiz e somente as bibliotecas declaradas, sem entradas de diretório. Exemplo de estrutura:

```text
mod.json
lib/arm64-v8a/libplayer_roles.so
lib/armeabi-v7a/libplayer_roles.so
```

Exemplo do manifest (substitua os hashes pelos SHA256 reais, em hexadecimal minúsculo de 64 caracteres):

```json
{
  "format": 2,
  "type": "native",
  "api": 1,
  "id": "wsc.example",
  "name": "Example",
  "version": "1.0",
  "description": "Módulo nativo externo",
  "game": {
    "package": "com.monkeyibrow.worldsoccerchamps",
    "versionCode": 468
  },
  "libraries": {
    "arm64-v8a": {
      "path": "lib/arm64-v8a/libexample.so",
      "sha256": "<SHA256 da biblioteca do mod>",
      "gameLibrarySha256": "<SHA256 da biblioteca original do jogo>"
    }
  },
  "claims": ["Example.integrationPoint"]
}
```

`id` aceita 2–64 caracteres minúsculos, números, ponto, hífen e underscore, começando com letra/número. Nome tem até 80 caracteres, versão até 32 e descrição até 400. Package e `versionCode` positivo são obrigatórios.

O exemplo acima de manifest é single-ABI; adicione uma entrada `armeabi-v7a` em `libraries` para um pacote dual-ABI, com seu próprio caminho e hashes. Mods existentes não precisam fornecer ambas.

`libraries` declara uma ou ambas as ABIs `arm64-v8a` / `armeabi-v7a`. Caminhos devem seguir `lib/<abi>/lib[a-z0-9_]+.so`. A biblioteca deve ser ELF compartilhado little-endian da arquitetura correta. O SHA256 do payload é verificado na importação e no início; o SHA256 completo da biblioteca do jogo é verificado antes do carregamento. Modificar apenas um hash não torna hooks/offsets compatíveis com outro jogo. O launcher seleciona somente a biblioteca da ABI do processo; a ausência dessa ABI é incompatibilidade. O hash do jogo também é independente por ABI.

Um mod com bindings internos deve analisar e validar cada binário separadamente. ARM32 pode usar Thumb, outros offsets, layouts de objetos, vtables e convenções de chamada. Recompilar o perfil ARM64 para ARM32 não constitui um port. Player Roles 0.4 compartilha a lógica, mas mantém perfis separados em `examples/player-roles/profiles/`. Native Mod ABI continua v1.

`claims` exige 1–32 nomes únicos de pontos de integração, com até 100 caracteres (`A–Z`, `a–z`, números, ponto, underscore, hífen). Dois mods ativos não podem declarar o mesmo claim. Declare os hooks usados pelo mod; claims não detectam hooks omitidos pelo autor.

Limites: ZIP 16 MiB, manifest UTF-8 64 KiB, cada biblioteca 4 MiB e até 128 mods instalados. O importador verifica CRC, entradas duplicadas, bibliotecas ausentes e arquivos não declarados. Instalar não ativa e não sobrescreve IDs existentes; remova a versão anterior para atualizar. Não inclua APKs, assets ou bibliotecas proprietárias do jogo.

## Inicialização e ABI

Exporte com visibilidade pública e ligação C:

```cpp
#include "wsc_mod_api.h"
extern "C" int wsc_mod_init(const WscHostV1* host) {
    if (!host || host->api_version != 1 || host->struct_size < sizeof(WscHostV1)) return 1;
    host->log(4, "Example iniciado");
    return 0;
}
```

O jogo já teve sua biblioteca carregada quando init é chamado, no thread principal Android. Não suponha uma cena existente durante init. A ABI fornece base da biblioteca do jogo, logging, hook/unhook e diálogo genérico `show_choice` com resposta no GL (`-1` cancela). Preserve ordem/tamanho dos campos do header; callbacks e funções específicos do jogo devem respeitar o thread e a ABI do perfil. Retorne zero em sucesso e desfaça hooks instalados antes de retornar erro.

Não há descarregamento a quente nem dependências entre mods. Bibliotecas ficam carregadas pela vida do guest; alterações exigem reinício pelo launcher. Use [scripts/build-native.py](../scripts/build-native.py) e os testes de [Player Roles](../examples/player-roles/README.md) como referência. Cada mod implementa seus próprios bindings de gameplay e UI.

## Alterar textos em runtime

Remover `plist-patch` não impede mods de alterar textos. Um mod nativo pode interceptar funções de localização, substituir strings retornadas pelo jogo, interceptar criação de elementos da UI ou alterar labels/nodes do Cocos em runtime. Isso é implementado pelo código do mod e validado para o perfil do jogo; não existe mais um formato declarativo genérico de substituição de recursos.
