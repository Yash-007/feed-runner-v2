# Feed Runner

Android app for replying and posting on X. A floating bubble grabs whatever post
is on screen, reads it, and drafts replies in my voice. Same bubble captions a
post or writes a quote of it.

The backend it banks ideas into is a separate repo, `feed-runner-backend`. They
only talk over HTTP, and the app works fine with it switched off — ideas queue
locally and drain later.

## Running

Put `anthropic.apiKey` in `local.properties`, then:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Two permissions on first launch, both prompted on the setup screen: draw over
other apps, and the accessibility service that takes the captures. Neither can be
granted from code.

`local.properties` isn't a Gradle input, so after changing the key delete
`app/build/generated/source/buildConfig` or the old one stays baked in.

## Notes

- `task.txt` — every flow in the app, as a checklist
- `design.txt` — the visual design and why
