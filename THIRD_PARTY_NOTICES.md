# Third-party components

The application uses these open-source components. Their authors retain their rights.

| Component | Version | License | Project |
| --- | --- | --- | --- |
| OkHttp | 4.12.0 | Apache License 2.0 | https://github.com/square/okhttp |
| Okio (transitive) | resolved by OkHttp | Apache License 2.0 | https://github.com/square/okio |
| Kotlin standard library (transitive) | resolved by OkHttp / AndroidX | Apache License 2.0 | https://github.com/JetBrains/kotlin |
| AndroidX ExifInterface and annotations | 1.4.1 / transitive | Apache License 2.0 | https://android.googlesource.com/platform/frameworks/support/ |

The source package includes the generated Gradle Wrapper (Gradle 8.11.1), distributed under Apache License 2.0: https://github.com/gradle/gradle.

Build-only dependencies include Android Gradle Plugin 8.9.1. Test-only dependencies include JUnit 4.13.2 (Eclipse Public License 1.0), JSON-java 20240303 (public domain), and MockWebServer 4.12.0 (Apache License 2.0); they are not bundled as application features.

The full Apache License 2.0 text accompanies this file as `Apache-2.0.txt`. Component license resources embedded by upstream libraries are retained in the APK. This is an independent companion prototype, not an official M5Stack, Xiaomi, or Xiaozhi application.

## Character artwork

The touch tab uses **Deepseek Chan**, by **Dullsaw**, from [Codex Pets](https://codex-pets.net/#/pets/deepseek-chan). The application bundles the original, unmodified spritesheet and selects animation cells at runtime. Source URLs, checksums, dimensions and animation mappings are retained in `app/src/main/assets/deepseek-chan-source.json` and inside the APK.

This community artwork has a separate rights scope from software dependencies. An explicit license permitting arbitrary redistribution or commercial use has not been verified; no such permission is claimed here. Preserve attribution and source information when sharing; additional rights may need to be obtained from the relevant rights holder.
