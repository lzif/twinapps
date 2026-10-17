# TwinApps

Run two copies of any app on one phone — no root required. TwinApps uses
Android's work profile to give you a second, isolated space: clone WhatsApp,
games, or anything else and use both accounts side by side.

## How it works

1. TwinApps provisions a managed work profile on your device.
2. You pick apps to clone into the profile — they install as fresh copies
   with their own data.
3. Cloned apps get badged launcher shortcuts, so they launch like normal apps.
4. Freeze apps you are not using; share files between the two spaces.

## Methodology

TwinApps is a clean-room implementation: it is written from an independent
functional specification describing observable behavior only. No code from
any existing app was referenced or reused.

## Roadmap

- [ ] **M1 — Provisioning:** create the work profile (setup wizard)
- [ ] **M2 — Clone:** install app copies into the profile
- [ ] **M3 — Launcher:** badged shortcuts for cloned apps
- [ ] **M4 — Freeze:** suspend/unfreeze apps without uninstalling
- [ ] **M5 — Sharing:** file/intent sharing across profiles

## Building

Open this project in Android Studio (2024.2+) and press Run, or:

```sh
./gradlew assembleDebug
```

Requires Android SDK with API 35. `minSdk` is 26.

## License

MIT — see [LICENSE](LICENSE).
