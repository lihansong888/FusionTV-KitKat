# Unified Android App Development Plan

## Product decision

Build **one APK and one maintained application line**. Do not create separate standard and legacy APKs.

Target device range:
- Android 4.4 / API 19 as the compatibility floor, subject to dependency and device validation.
- Android 5.1–6.0 legacy TV boxes and Android 7+ phones, tablets, TVs and boxes.
- ARMv7 (32-bit) and ARM64 (64-bit).
- Phone portrait/landscape, tablet adaptive layouts, TV landscape and remote-control navigation.

Preserve the traditional TVBox/FongMi/OK-style information architecture and visual language: home, categories, search, detail, source/line selection, episode list, player and settings. Do not replace it with an unrelated generic streaming-app design.

## Repository safety

- main is the untouched upstream-derived baseline.
- All development changes go to unified-app-dev.
- Do not use or modify the separate ys repository.
- Do not add private subscription tokens, signing keys, passwords or production backend credentials to Git.

## Current baseline (audited)

- Android Gradle Plugin 8.7.3; Gradle Wrapper 8.9; compile SDK 35.
- App minSdk 19, targetSdk 28, Java 8 source compatibility.
- Only one leanback flavor exists today; its manifest locks screens to landscape.
- The app UI source is under app/src/leanback; app/src/main contains shared/core code and CrashActivity. There is no mobile UI source set in this repository.
- The app currently filters native packaging to armeabi-v7a.
- IJK, Jianpian and Thunder include ARMv7 and ARM64 libraries. ForceTech and ZLive only include ARMv7 native libraries in this repository. Their use on ARM64 must be disabled safely or supplied with verified ARM64 builds before enabling ARM64 packaging. TVBus loads a native library from a runtime path and needs separate audit.
- API 19 support cannot be achieved by merely changing minSdk: every dependency, Android API call, manifest feature and native module must be checked.

## Security work started on this branch

- Removed the unconditional OkHttp hostname verifier override.
- Replaced the trust-all X509TrustManager with the platform system trust manager.
- Legacy TLS 1.2 continues to pass the peer certificate chain through that trust manager; OkHttp's default hostname verification is now retained.
- These edits are not yet compiled or device-tested. Do not connect production credentials until the TLS behavior is verified on API 19 and modern Android.

## Implementation sequence

1. **Baseline verification:** build the untouched app variant, record dependency/API constraints and inspect current playback/config behavior.
2. **Transport security:** verify certificate-chain and hostname rejection for both modern platform TLS and the API 19 legacy TLS socket; test trusted and invalid certificates.
3. **ABI coverage:** produce a single APK containing ARMv7 and ARM64. Gate optional native-only features that lack an ABI; never allow a missing library to crash app startup.
4. **Unified UI:** consolidate on one Activity/business-logic implementation and adapt layouts for touch and remote control. Keep TV focus behavior and add phone portrait/landscape layouts using matching view-binding IDs. Do not duplicate entire activity classes into one variant.
5. **Compatibility validation:** build and install on API 19, API 21/22, API 24+, current Android, ARMv7 and ARM64; test search, detail, playback, remote keys, touch, rotation, network and storage.
6. **Test backend:** use non-sensitive test configuration first. Connect the user's own CMS/backend only after the UI and network layers pass validation.

## Release gate

No APK is described as ready until Gradle build succeeds, APK ABI/minSdk metadata is checked, and real-device smoke tests cover both old and new device classes. A successful build alone does not prove every source or codec works.