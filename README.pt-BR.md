<p align="center">
  <a href="README.md">English</a> · <strong>Português (Brasil)</strong> · <a href="README.es.md">Español</a>
</p>

<p align="center">
  <img src="artwork/wsc-launcher-icon.jpg" width="180" alt="Ícone do WSCLauncher">
</p>

<h1 align="center">WSCLauncher</h1>

<p align="center">
  Um launcher Android para executar <strong>World Soccer Champs</strong> com mods nativos.
</p>

<p align="center">
  <strong>Versão 1.0.1</strong> · Android 7+ · ARM64 / ARM32
</p>

## ✨ Sobre

O **WSCLauncher** executa a cópia instalada do World Soccer Champs em um processo próprio e permite carregar mods nativos externos.

O projeto **não inclui o APK, assets ou bibliotecas originais do jogo**. Você precisa ter sua própria cópia instalada.

> O WSCLauncher é um projeto comunitário não oficial e não possui afiliação, endosso ou patrocínio dos desenvolvedores ou publicadores de World Soccer Champs.

## 📦 Compatibilidade

- ✅ Android 7+ (API 24)
- ✅ ARM64 (`arm64-v8a`)
- ✅ ARM32 (`armeabi-v7a`)
- ✅ APK Universal com as duas arquiteturas
- ✅ APKs específicos ARM64 e ARM32
- ✅ Native Mod ABI v1

> Em aparelhos ARM64 com uma instalação ARM32 do WSC, use o APK ARM32 do WSCLauncher. Um processo 64-bit não consegue carregar bibliotecas 32-bit.

## 🧩 Mods

Os mods são pacotes ZIP nativos e podem oferecer suporte a uma ou às duas arquiteturas.

O projeto inclui como exemplo funcional o **[Player Roles Picker 0.4](examples/player-roles/README.md)**, testado em **ARM64 e ARM32**.

Ele adiciona uma interface para selecionar jogadores nas funções de:

- Capitão
- Faltas
- Pênaltis

Cada mod é responsável por declarar quais versões e arquiteturas do jogo suporta. O launcher valida essas informações antes de carregar o módulo.

## 🚀 Como usar

1. Instale o World Soccer Champs.
2. Instale o WSCLauncher compatível com a arquitetura do jogo.
3. Abra **Mods → Adicionar mod** e selecione o ZIP.
4. Ative o mod e inicie o jogo pela aba **Início**.

As builds oficiais serão distribuídas pela seção **Releases** do GitHub.

## 🎨 Interface

O launcher possui três áreas principais:

- 🏠 **Início** — status do jogo, compatibilidade e botão Jogar
- 🧩 **Mods** — importar, ativar, desativar, ver detalhes e remover mods
- ⚙️ **Configurações** — idioma, tema, logs, diagnóstico e licenças

A interface usa Material 3, temas claro/escuro e cores dinâmicas quando disponíveis.

## 🛠️ Compilar

Requisitos principais:

- JDK 17+
- Android SDK 35
- Android NDK 27.2.12479018

```sh
python3 scripts/build-native.py --ndk "$ANDROID_NDK_HOME"
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

O build gera:

```text
WSCLauncher-<versão>-universal.apk
WSCLauncher-<versão>-arm64.apk
WSCLauncher-<versão>-arm32.apk
```

## 📚 Documentação

- 📐 [Arquitetura](docs/ARCHITECTURE.md)
- 🧩 [Criando mods](docs/MODDING.md)
- 🩺 [Solução de problemas](docs/TROUBLESHOOTING.md)
- ⚽ [Player Roles Picker](examples/player-roles/README.md)

## ⚠️ Estado do projeto

O WSCLauncher é experimental e distribuído por sideload. O carregamento do jogo depende da versão, arquitetura e comportamento da instalação original do WSC.

Mods nativos executam dentro do processo do launcher. Instale apenas mods de fontes em que você confia.

## 📄 Licença

Distribuído sob **GPL-3.0**.

Dependências, atribuições e avisos de terceiros estão em [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
