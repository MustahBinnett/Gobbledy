# Gobbledy

*Formerly called Pocket Dictate.*

Offline hold-to-dictate for Android. A small floating button sits over your apps: hold it, speak, let go, and your words are typed at the cursor in whatever app you're using (Gmail, Messages, Chrome, Google Docs…).

- **Fully offline.** Speech is transcribed on the phone with [transcribe.cpp](https://github.com/handy-computer/transcribe.cpp) and NVIDIA's Parakeet Unified EN 0.6B model. No accounts, no analytics, no cloud. The internet is used only once, to download the model (about 731 MB).
- **Australian spelling.** An editable word list changes US spellings to Australian ones (color → colour, organize → organise). You can turn it off.
- Inspired by [Handy](https://github.com/cjpais/Handy), the open-source desktop dictation app. Gobbledy is a separate project and isn't affiliated with Handy.

## Install (recommended: Obtainium)

Google Play Protect blocks apps that use Accessibility when they're installed from a downloaded file, so install through **Obtainium**, a free app store that installs apps straight from GitHub and keeps them updated.

1. Install **Obtainium** from [F-Droid](https://f-droid.org/packages/dev.imranr.obtainium.fdroid/) or [its GitHub releases](https://github.com/ImranR98/Obtainium/releases).
2. Open Obtainium, tap **Add app**, paste `https://github.com/MustahBinnett/Gobbledy` and tap **Add**.
3. Tap **Install**. Allow Obtainium to install apps if Android asks.
4. Open Gobbledy and work down the setup list on its screen.

Other ways to install: download the APK from [Releases](https://github.com/MustahBinnett/Gobbledy/releases) and install it over USB with `adb install Gobbledy.apk`. Opening the APK from a browser or file manager will be blocked by Play Protect.

Requirements: Android 10 or newer, 64-bit ARM (any recent phone), about 1 GB free storage. Built and tested on a Galaxy S25 Ultra.

## Setup on the phone

The app's main screen walks you through it: microphone, notifications, "Display over other apps", Accessibility (used only to type your words into the focused text box), battery ("Never sleeping apps" on Samsung), then the one-off model download. Step-by-step instructions with exact button names are in [SETUP-GUIDE.md](SETUP-GUIDE.md).

## Building from source

```
git clone --recurse-submodules https://github.com/MustahBinnett/Gobbledy.git
cd Gobbledy
./gradlew assembleRelease
```

Needs JDK 17+, Android SDK Platform 37, NDK 30.0.16248370 and CMake 3.31.6. transcribe.cpp is pinned as a git submodule at v0.2.4, the version Handy uses.

## Licences

- Gobbledy: [MIT](LICENSE)
- Bundled open-source code (transcribe.cpp, ggml, llamafile sgemm, miniz, Kotlin standard library, libc++): full notices in [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt), also shown in the app under About > Open-source licences
- Parakeet Unified EN 0.6B (downloaded at first run, not included): [NVIDIA Open Model License](https://www.nvidia.com/en-us/agreements/enterprise-software/nvidia-open-model-license/), GGUF conversion by handy-computer
