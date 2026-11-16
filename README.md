# nemla mobile

Application Android et iOS de [nemla](https://nemla.app), en Kotlin Multiplatform et Compose Multiplatform.

- Architecture : `docs/ARCHITECTURE.md`
- Feuille de route : `docs/ROADMAP.md`
- Besoins côté backend : `docs/BACKEND-GAPS.md`
- Parité Android / iOS : `docs/PLATFORM-PARITY.md`
- Spécifications extraites de l'application web : `docs/specs/`

## Configuration

Aucun secret n'est versionné. Sans configuration, l'application démarre et affiche « Configuration manquante » (debug).

- **Android** : copier `local.properties.example` en `local.properties` et renseigner `sdk.dir`, `SUPABASE_URL`,
  `SUPABASE_PUBLISHABLE_KEY` et `API_BASE_URL` (adresses `https://`). Les valeurs passent dans `BuildConfig`.
- **iOS** : copier `iosApp/Config.xcconfig.example` en `iosApp/Config.xcconfig` et renseigner les mêmes clés (dans un xcconfig,
  écrire `https:/$()/…` car `//` ouvre un commentaire) et `DEVELOPMENT_TEAM` pour un appareil. Les valeurs passent par `Info.plist`.

## Construire et lancer

JDK 17 (imposé au daemon par `gradle/gradle-daemon-jvm.properties`), Android SDK 37, Xcode avec le SDK simulateur.

```sh
./gradlew spotlessApply                    # formatage
./gradlew spotlessCheck allTests           # contrôle et tests (hôte Android + simulateur iOS arm64)
./gradlew :androidApp:assembleDebug        # APK debug : androidApp/build/outputs/apk/debug/
./gradlew :androidApp:installDebug         # installation sur l'émulateur ou l'appareil connecté
./gradlew :androidApp:assembleRelease      # APK release (R8), non signé
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

iOS : ouvrir `iosApp/iosApp.xcodeproj` dans Xcode et lancer la cible `iosApp` (la phase « Compile Kotlin Framework » appelle
`:shared:embedAndSignAppleFrameworkForXcode`). En ligne de commande, sans signature :

```sh
cd iosApp && xcodebuild -project iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64 \
  -configuration Debug CODE_SIGNING_ALLOWED=NO build
```

