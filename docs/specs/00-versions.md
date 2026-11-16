# 00 — Versions épinglées (KMP Android + iOS, Compose Multiplatform)

> Date de référence : **15 novembre 2026**. Règle : uniquement des versions **stables** (aucune alpha / beta / RC / dev / EAP).
> Chaque version a été vue sur une source web. Si ce n'est pas le cas, la ligne est marquée « non » avec une estimation.
> Limite de la méthode : Maven Central / Google Maven n'ont pas été interrogés directement (pas d'accès shell). Les dates « il y a N jours/mois » viennent de pages miroir (newreleases.io, klibs.io, central.sonatype.com) et ont été converties de façon approximative.
> Résolution confirmée le 16/11/2026 (lot 0) : toutes les versions du catalogue se résolvent sur Google Maven, Maven Central et le portail Gradle ; `:androidApp:assembleDebug` et `:shared:linkDebugFrameworkIosSimulatorArm64` réussissent. Aucune version n'a dû être changée.

---

## 1. Tableau des versions

### 1.1 Toolchain et build

| Élément | Version retenue | Date de sortie | Source (URL) | Vérifié |
|---|---|---|---|---|
| Kotlin (KGP, multiplatform, serialization, compose compiler plugin) | **2.4.21** | 8 oct. 2026 | https://kotlinlang.org/docs/releases.html | oui |
| Plugin Compose compiler `org.jetbrains.kotlin.plugin.compose` | **2.4.21** (même version que Kotlin) | 8 oct. 2026 | https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html (« must match the Kotlin Multiplatform plugin version ») | oui |
| KSP (`com.google.devtools.ksp`) | **2.3.14** (KSP2 : numérotation indépendante de Kotlin) | ~nov. 2026 | https://newreleases.io/project/github/google/ksp/release/2.3.14 · https://github.com/google/ksp/releases?page=1 | oui (version vue ; statut « stable » déduit de l'absence de suffixe) |
| Gradle (retenu, dans la matrice KGP) | **9.7.1** | 19 août 2026 | https://docs.gradle.org/9.7.1/release-notes.html | oui |
| Gradle (dernière stable absolue, pour info) | 9.9.1 (9.9.0 sorti le 12 nov. 2026) | nov. 2026 | https://docs.gradle.org/current/release-notes.html | oui |
| Android Gradle Plugin (retenu, dans la matrice KGP) | **9.3.3** | 9.3.0 : juillet 2026 (date des patchs non publiée) | https://developer.android.com/build/releases/agp-9-3-0-release-notes | oui |
| AGP (dernière stable absolue, pour info) | 9.5.0 / 9.5.1 (oct. 2026), Gradle minimum 9.6.0 | oct. 2026 | https://developer.android.com/studio/releases/gradle-plugin.html | oui |
| Plugin `com.android.kotlin.multiplatform.library` | = AGP (**9.3.3**) | — | https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html | oui |
| JDK (build) | **17** (minimum et défaut d'AGP 9.3 / 9.5) | — | https://developer.android.com/build/releases/agp-9-3-0-release-notes | oui |
| Xcode | **26.4** (version supportée par KGP 2.4.20–2.4.21) | — | https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html | oui |
| iOS deployment target minimum | **15.0** (relevé de 14.0 à 15.0 par Kotlin 2.4.0) | — | https://kotlinlang.org/docs/whatsnew24.html | oui |

### 1.2 Android SDK

| Élément | Version | Date | Source | Vérifié |
|---|---|---|---|---|
| compileSdk | **37** (Android 17) ; AGP 9.3 gère au maximum l'API 37 | Android 17 stable le 16 juin 2026 | https://developer.android.com/build/releases/agp-9-3-0-release-notes · https://ecorpit.com/google-play-target-api-37-deadline-august-2027/ | oui (la date de sortie d'Android 17 vient d'une source secondaire) |
| targetSdk | **37** (Play impose 36 depuis le 31 août 2026 et imposera 37 en août 2027) | — | https://ecorpit.com/google-play-target-api-37-deadline-august-2027/ | partiel (source secondaire, à recouper avec la Play Console) |
| minSdk | **28** recommandé (Android 9). Plancher technique Compose MP : 21 | — | https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html | recommandation (pas une version publiée) |
| SDK Build Tools | 36.0.0 (minimum et défaut d'AGP 9.3) | — | page AGP 9.3 | oui |

Pourquoi minSdk 28 : Keystore matériel fiable, BiometricPrompt (API 28), TLS 1.3 par défaut (API 29+ pour l'ensemble des appareils), base installée encore très large. Les API GenAI embarquées (Gemini Nano) ne tournent de toute façon que sur des appareils récents, voir §4.

### 1.3 Compose Multiplatform (JetBrains)

| Élément | Version stable | Date | Source | Vérifié |
|---|---|---|---|---|
| Compose Multiplatform (`org.jetbrains.compose`), runtime/ui/foundation/material/resources | **1.12.2** (basé sur Jetpack Compose 1.12.1) | 15 oct. 2026 | https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html | oui |
| Material 3 CMP (`org.jetbrains.compose.material3:material3`) | **1.9.0** (basé sur Jetpack Material3 1.4.0) | publié avec CMP 1.9 | https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html | oui. ATTENTION : CMP 1.10, 1.11 et 1.12 n'utilisent que des Material3 **alpha** (1.10.0-alpha05, 1.11.0-alpha07, 1.12.0-alpha03). Voir §2 |
| Material 3 Adaptive CMP | **1.3.0** | 15 oct. 2026 | https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html | oui |
| Lifecycle CMP (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-navigation3`) | **2.11.0** | 2026 | https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html | oui |
| Navigation 2 CMP (`org.jetbrains.androidx.navigation:navigation-compose`) | **2.10.0** (stable, publiée le 23 oct. 2026, dépend de lifecycle 2.11.0 et de Compose 1.11+) | 23 oct. 2026 | https://repo1.maven.org/maven2/org/jetbrains/androidx/navigation/navigation-compose/maven-metadata.xml | oui (16/11/2026 : métadonnées Maven Central et fichier `.module` lus, résolution Gradle réussie) |
| Navigation 3 CMP (`org.jetbrains.androidx.navigation3:navigation3-ui`) | **1.1.2** (CMP 1.12.2 référence 1.2.0-rc01, pas stable) | 2026 | https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html · https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html | oui pour la version (sans suffixe). JetBrains n'écrit pas « stable » noir sur blanc |
| SavedState CMP | 1.4.0 | 2026 | page CMP 1.12 | oui |
| Compose resources (`org.jetbrains.compose.components:components-resources`) | **1.12.2** (suit CMP) | 15 oct. 2026 | page CMP 1.12 (versions de la famille compose) | partiel (coordonnée suivant la version CMP, non listée explicitement) |

**Navigation 3 en multiplateforme, novembre 2026** : côté Android, Jetpack Navigation 3 est stable (**1.2.1**, 4 nov. 2026, https://developer.android.com/jetpack/androidx/releases/navigation3?hl=en). Mais l'artefact androidx `navigation3-ui` n'a **pas** de cibles KMP : seul `navigation3-runtime` en a. Le `NavDisplay` multiplateforme vient de JetBrains (`org.jetbrains.androidx.navigation3:navigation3-ui`), supporté depuis CMP 1.10 sur Android, iOS, desktop et web. La dernière version sans suffixe est 1.1.2. Sur iOS, il faut obligatoirement une `SavedStateConfiguration` avec sérialisation polymorphe des `NavKey`. Recommandation initiale : Navigation 3 (JetBrains 1.1.2). **Décision retenue (ARCHITECTURE §1) : Navigation 2 multiplateforme**, Navigation 3 multiplateforme n'étant pas déclarée stable. Montée de 2.9.2 à **2.10.0** le 16/11/2026 (lot 1) : version stable sans suffixe publiée sur Maven Central le 23 oct. 2026, alignée sur CMP 1.12 et lifecycle 2.11.0.

### 1.4 Jetpack côté Android (module `androidApp`)

| Élément | Version stable | Date | Source | Vérifié |
|---|---|---|---|---|
| `androidx.activity:activity-compose` | **1.13.0** | 23 sept. 2026 | https://developer.android.com/jetpack/androidx/versions | oui |
| `androidx.core:core-ktx` | **1.19.1** (désormais artefact vide : les API Kotlin sont fusionnées dans `androidx.core:core`) | 23 sept. 2026 | https://developer.android.com/jetpack/androidx/releases/core?hl=en | oui |
| `androidx.core:core-splashscreen` | **1.2.0** | n.c. | https://developer.android.com/jetpack/androidx/releases/core?hl=en | oui |
| Compose BOM (référence seulement, inutile avec CMP) | **2026.09.00** | sept. 2026 | https://developer.android.com/develop/ui/compose/bom | oui |
| Jetpack Compose ui/foundation/runtime | 1.12.1 | 2026 | https://developer.android.com/jetpack/androidx/versions | oui |
| Jetpack Compose Material3 | 1.4.0 (1.5.0-rc01 en cours) | 2026 | https://developer.android.com/jetpack/androidx/releases/compose-material3 | oui |
| `androidx.datastore` (preferences, KMP) | **1.3.0** | 21 oct. 2026 | https://developer.android.com/jetpack/androidx/versions | oui |
| `androidx.room3` (KMP : Android, iOS, JVM, JS, Wasm) | **3.0.3** | 9 sept. 2026 | https://developer.android.com/jetpack/androidx/releases/room3?hl=en · https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html | oui |
| `androidx.room` 2.x (maintenance) | 2.8.5 | 9 sept. 2026 | https://developer.android.com/jetpack/androidx/versions | oui |
| `androidx.sqlite` (`sqlite-bundled`) | **2.7.1** | 9 sept. 2026 | https://developer.android.com/jetpack/androidx/releases/sqlite?hl=fr | oui |
| `androidx.security:security-crypto` | 1.1.0 (à **ne pas** utiliser, voir §2) | 9 sept. 2026 | https://developer.android.com/jetpack/androidx/versions | oui (version) |

### 1.5 Bibliothèques KMP

| Élément | Version stable | Date | Source | Vérifié |
|---|---|---|---|---|
| Ktor client (core, content-negotiation, serialization-kotlinx-json, okhttp, darwin, mock pour les tests) | **3.6.1** | 30 oct. 2026 | https://ktor.io/docs/releases.html | oui |
| kotlinx-coroutines | **1.11.0** (compilé avec Kotlin 2.2.20) | 9 mai 2026 | https://badges.mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core-tvosarm64/versions · https://newreleases.io/project/github/Kotlin/kotlinx.coroutines/release/1.11.0 | oui |
| kotlinx-serialization-json | **1.13.0** (basé sur Kotlin 2.4.10) | ~11 nov. 2026 | https://central.sonatype.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-json · https://newreleases.io/project/github/Kotlin/kotlinx.serialization/release/v1.13.0 | oui (la page GitHub Releases affichait encore 1.12.0 du 19 oct.) |
| kotlinx-datetime | **0.8.0** (exige une stdlib ≥ 2.3.21) | 7 mai 2026 | https://github.com/kotlin/kotlinx-datetime · https://www.github.com/Kotlin/kotlinx-datetime/releases | oui |
| kotlinx-io | **0.9.1** | ~sept. 2026 | https://klibs.io/project/Kotlin/kotlinx-io | oui |
| Koin (BOM, core, compose, compose-viewmodel, compose-navigation3, android) | **4.2.2** (compilé avec Kotlin 2.3.20) | ~juin 2026 | https://insert-koin.io/docs/support/releases · https://central.sonatype.com/artifact/io.insert-koin/koin-core-macosarm64/4.2.2 | oui |
| supabase-kt (`io.github.jan-tennert.supabase` : bom, auth-kt, postgrest-kt, functions-kt) | **3.9.0** | 3 nov. 2026 | https://klibs.io/package/io.github.jan-tennert.supabase/functions-kt · https://newreleases.io/project/github/supabase-community/supabase-kt/release/3.5.0 | oui pour la version. **Versions Ktor/Kotlin requises : non vérifiées** |
| SQLDelight (`app.cash.sqldelight`) | **2.4.1** | 6 oct. 2026 | https://klibs.io/package/app.cash.sqldelight/runtime · https://www.github.com/cashapp/sqldelight/releases | oui |
| multiplatform-settings (russhwolf) | 1.3.0 (dernière sortie il y a ~2 ans, activité faible) | ~fin 2024 | https://klibs.io/project/russhwolf/multiplatform-settings | oui (version) |
| KVault (Liftric) | 1.12.0 : **non maintenu** (dernière sortie ~3 ans, activité >2 ans) | ~2023 | https://klibs.io/project/Liftric/KVault | oui (version), à **ne pas** utiliser |
| Kermit (`co.touchlab:kermit`) | **2.2.0** | ~sept. 2026 | https://klibs.io/project/touchlab/Kermit | oui (klibs uniquement ; la doc officielle affiche encore 2.0.4) |
| Napier | non retenu. Estimation 2.7.1 | — | — | **non** |
| Coil 3 (`io.coil-kt.coil3`) | **3.6.3** | 18 sept. 2026 | https://github.com/coil-kt/coil/releases | oui |

### 1.6 Tests et qualité

| Élément | Version stable | Date | Source | Vérifié |
|---|---|---|---|---|
| kotlin-test | = Kotlin **2.4.21** | 8 oct. 2026 | https://kotlinlang.org/docs/releases.html | oui |
| kotlinx-coroutines-test | **1.11.0** | 9 mai 2026 | idem coroutines | oui |
| Turbine (`app.cash.turbine`) | **1.2.1** | 11 juin 2025 | https://klibs.io/package/app.cash.turbine/turbine | oui |
| Kotest assertions (`io.kotest:kotest-assertions-core`) | **6.2.6** | 14 oct. 2026 | https://klibs.io/package/io.kotest/kotest-assertions-core | oui |
| detekt stable (`io.gitlab.arturbosch.detekt`) | 1.23.8, construit pour **Kotlin 2.0.21** : incompatible en pratique avec Kotlin 2.4 | — | https://detekt.dev/docs/introduction/compatibility | oui. **Aucune version stable compatible avec Kotlin 2.4** (2.0.0-alpha.6 seulement, Kotlin 2.4.10) |
| ktlint (moteur) | 1.8.0 | ~fin 2025 | https://central.sonatype.com/artifact/com.pinterest.ktlint/ktlint-rule-engine-core | partiel (version vue ; impossible de confirmer qu'aucune version plus récente n'existe) |
| Spotless (`com.diffplug.spotless`) | **8.10.4** | 8 oct. 2026 | https://plugins.gradle.org/plugin/com.diffplug.spotless | oui |
| ktlint-gradle (`org.jlleitschuh.gradle.ktlint`) | 14.2.0 (alternative à Spotless) | 12 mars 2026 | https://plugins.gradle.org/plugin/org.jlleitschuh.gradle.ktlint | oui |

---

## 2. Incompatibilités et pièges connus

1. **Kotlin 2.4.21 ne valide pas officiellement les derniers AGP et Gradle.** La matrice KGP 2.4.20–2.4.21 couvre Gradle 7.6.3 → **9.7.0** et AGP 8.5.2 → **9.3.1** (https://kotlinlang.org/docs/gradle-configure-project.html). Or AGP 9.5.0 est sorti et exige Gradle ≥ 9.6.0, et Gradle 9.9.1 est disponible. **Choix retenu : AGP 9.3.3 + Gradle 9.7.1**, le couple le plus récent qui reste dans la matrice (simple patch au-dessus des bornes testées). Passer à AGP 9.5 / Gradle 9.9 attendra Kotlin 2.5.0, prévu en décembre 2026 (https://kotlinlang.org/docs/releases.html). Kotlin indique que les versions plus récentes « fonctionnent mais peuvent émettre des avertissements ».
2. **AGP 9 + KMP : `com.android.application` et `com.android.library` sont incompatibles avec `org.jetbrains.kotlin.multiplatform`.** Il faut un module `androidApp` séparé (avec `com.android.application`, Kotlin intégré à AGP 9, **sans** `org.jetbrains.kotlin.android`) et des modules partagés en `com.android.kotlin.multiplatform.library`. Ce plugin est le chemin **recommandé et obligatoire** avec AGP 9. Il ne gère pas les build variants : utiliser `androidRuntimeClasspath(...)` au lieu de `debugImplementation(...)` pour `ui-tooling`. Le namespace de la lib doit différer de celui de l'app. Le DSL s'écrit `kotlin { android { … } }` (l'ancien bloc `androidTarget` est à renommer ; certaines docs montrent encore `androidLibrary { }`). Sources : https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html · https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html
3. **AGP 10** (attendu au 2e semestre 2026) supprimera les API legacy (`android.enableLegacyVariantApi`) et rendra le nouveau Variant API obligatoire. Ne pas s'appuyer sur `android.newDsl.optOut`.
4. **Material 3 sur CMP : la seule version stable (1.9.0) est plus ancienne que le runtime CMP 1.12.2.** Les Material3 livrés avec CMP 1.10, 1.11 et 1.12 sont tous en alpha. Compose garantit la compatibilité binaire ascendante, donc 1.9.0 tournera sur foundation/ui 1.12.2 (Gradle remontera les transitives). En revanche, **pas de Material 3 Expressive** (alpha uniquement). Il faut tester tôt sur iOS. Côté Android, Jetpack Material3 est lui aussi en 1.4.0 stable, donc les deux plateformes ont le même niveau fonctionnel (iso Android/iOS respecté).
5. **Navigation 3 CMP alignée sur CMP 1.12.2 = RC.** Épingler les dernières versions sans suffixe (Navigation 3 : 1.1.2 ; Navigation 2 : 2.10.0, stable depuis le 23 oct. 2026), en sachant qu'elles ont été publiées pour CMP 1.11. Sur iOS, Navigation 3 exige une `SavedStateConfiguration` avec `polymorphic(NavKey::class) { subclassesOfSealed<Route>() }`, car la réflexion n'existe pas sur Kotlin/Native.
6. **iOS minimum : Kotlin 2.4 = iOS 15.0**, alors que la doc CMP indique encore iOS 14. La contrainte la plus forte s'applique : `IPHONEOS_DEPLOYMENT_TARGET = 15.0` minimum. On peut viser 16 ou 17 selon les besoins Keychain et biométrie, et **iOS 26 pour Foundation Models** (avec un repli).
7. **Kotlin/Native 2.4 : le GC concurrent mark-and-sweep est activé par défaut** (retour arrière possible avec `kotlin.native.binary.gc=pmcs`). LLVM est passé à la version 21 et Xcode 26.4 est requis.
8. **Kotlin 2.4 a supprimé le compilateur K1** et `-language-version=1.9`. Les options Compose compiler `StrongSkipping` et `IntrinsicRemember` sont en `DeprecationLevel.ERROR` : il faut les retirer de tout `composeCompiler { featureFlags }`.
9. **KSP** : depuis KSP2 (2.3.x), la version est indépendante de Kotlin (fini le format `2.x.y-1.0.z`). La 2.3.12 a relevé l'AGP minimum à 8.12.0. Room 3 exige l'ajout de `room3-compiler` à **chaque** configuration KSP iOS (`kspIosArm64`, `kspIosSimulatorArm64`), et KAPT n'est plus pris en charge.
10. **Room 3 contre Room 2** : nouveau groupe `androidx.room3`, DAO obligatoirement `suspend` ou `Flow`, `@TypeConverter` renommé en `@ColumnTypeConverter`, plus de SupportSQLite, plugin `androidx.room3` avec `room3 { schemaDirectory(...) }`. Le guide KMP officiel montre `room3 = "3.1.0-alpha03"` : **ne pas copier, rester en 3.0.3**.
11. **Base locale : Room 3 ou SQLDelight ?**
    - Room 3.0.3 (stable, KMP-first, iOS supporté, `BundledSQLiteDriver` identique sur les deux OS, `Flow` natif). Room 2.x est en maintenance. **Recommandé pour nemla.**
    - SQLDelight 2.4.1 : SQL-first, sans KSP, mature sur iOS. Avantage : pilotes SQLCipher existants, donc un chiffrement local plus simple à brancher.
    - Pour le **chiffrement au repos** (RGPD), aucun des deux ne chiffre nativement avec le driver bundled. Avec Room 3, il faudra soit chiffrer champ par champ (clé dans Keystore/Keychain), soit un driver SQLCipher compatible `SQLiteDriver` (à évaluer, non vérifié). À trancher dans la spec sécurité.
12. **kotlinx-datetime 0.8.0** : `kotlinx.datetime.Instant` et `Clock` **n'existent plus**. Utiliser `kotlin.time.Instant` et `kotlin.time.Clock` de la stdlib (exige stdlib ≥ 2.3.21, déjà le cas avec 2.4.21). L'artefact `0.7.1-0.6.x-compat` ne sert qu'aux dépendances transitives anciennes. Vérifier qu'aucune lib (supabase-kt, Koin…) n'exige encore l'ancien `kotlinx.datetime.Instant` ; sinon, ajouter l'artefact compat.
13. **supabase-kt 3.9.0** apporte sa propre dépendance Ktor. Si elle est compilée contre une Ktor 3.x plus ancienne, Gradle remontera à 3.6.1 (compatible binaire dans la ligne 3.x, mais **non vérifié**). Utiliser `ktor-bom` et `supabase-bom` ensemble, puis contrôler `./gradlew :shared:dependencies`.
14. **detekt** : aucune version stable ne supporte Kotlin 2.4. La 1.23.8 embarque Kotlin 2.0.21 et lève une erreur de version Kotlin incompatible ; le contournement habituel consiste à forcer `kotlin-compiler-embeddable` 2.0.21 sur la configuration `detekt`, ce qui empêche l'analyse avec résolution de types. **Décision proposée : Spotless 8.10.4 + ktlint 1.8.0 + Android Lint, et detekt 2.0 ajouté plus tard, dès sa sortie stable.** L'utiliser maintenant serait contraire à la règle « pas de pré-version ».
15. **Stockage sécurisé** : KVault est abandonné et multiplatform-settings est peu actif, sans chiffrement intégré. **Recommandation : une petite abstraction maison `expect/actual`** :
    - Android : clé AES-GCM dans `AndroidKeyStore` (StrongBox si disponible), données chiffrées dans DataStore 1.3.0 ;
    - iOS : Keychain (`kSecClassGenericPassword`, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`) via l'interop `platform.Security`.
    Ne pas utiliser `androidx.security:security-crypto` : `EncryptedSharedPreferences` est déprécié (fait connu, statut de la 1.1.0 **non revérifié** ici).
16. **core-ktx 1.19.x est vide.** Dépendre directement de `androidx.core:core`.
17. **Accesseurs `compose.*`** du plugin `org.jetbrains.compose` (`compose.runtime`, `compose.material3`…) : ils sont dépréciés au profit des coordonnées explicites, d'où leur usage dans le TOML ci-dessous (point **non revérifié** précisément pour 1.12).
18. **CMP 1.13** (actuellement en alpha/beta) exigera Kotlin 2.4.20+ et **compileSdk 37.1**. Prévoir un compileSdk 37.1 lors de la montée de version.
19. **Google Play** : target 36 obligatoire depuis le 31 août 2026, target 37 en août 2027. Avec targetSdk 37, l'adaptativité grands écrans devient obligatoire (≥ 600 dp : pas de verrouillage d'orientation ni de redimensionnement).

---

## 3. `gradle/libs.versions.toml`

Catalogue nettoyé au lot 0 : retrait de Navigation 3, du SDK Supabase, de SQLDelight, de Room 3 / SQLite, de KSP et de toutes
les entrées non utilisées par les modules (BOM, Material 3 Adaptive, `ui-test`, splashscreen, `datastore-preferences` Android,
kotlinx-io, Kermit, Coil, Kotest, `koin-test`, `koin-compose-navigation3`). `ktor-client-auth` et `ktor-client-logging` ont aussi été retirés
(lot 1) : l'authentification et le journal HTTP sont des plugins maison de `core:network`. Elles se réintroduisent au besoin, avec la version du §1.

```toml
# nemla-mobile — versions stables vérifiées au 15/11/2026 (voir docs/specs/00-versions.md)
[versions]
kotlin = "2.4.21"
agp = "9.3.3"                     # max validé par KGP 2.4.21 = 9.3.1 (patch au-dessus) ; Gradle wrapper = 9.7.1
android-compileSdk = "37"
android-targetSdk = "37"
android-minSdk = "28"
jvmTarget = "17"
compose-multiplatform = "1.12.2"
compose-material3 = "1.9.0"       # dernière Material3 CMP stable (CMP 1.12.2 livre 1.12.0-alpha03)
androidx-lifecycle-cmp = "2.11.0"
androidx-navigation-cmp = "2.10.0" # Navigation 2 multiplateforme, retenue (ARCHITECTURE §1), alignée sur CMP 1.12 et lifecycle 2.11.0
androidx-activity = "1.13.0"
androidx-core = "1.19.1"
androidx-datastore = "1.3.0"
kotlinx-coroutines = "1.11.0"
kotlinx-serialization = "1.13.0"
kotlinx-datetime = "0.8.0"
ktor = "3.6.1"
koin = "4.2.2"
turbine = "1.2.1"
spotless = "8.10.4"
ktlint = "1.8.0"

[libraries]
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "compose-multiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "compose-multiplatform" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "compose-multiplatform" }
compose-ui-tooling-preview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "compose-multiplatform" }
compose-ui-tooling = { module = "org.jetbrains.compose.ui:ui-tooling", version.ref = "compose-multiplatform" }
compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "compose-multiplatform" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "compose-material3" }

androidx-lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle-cmp" }
androidx-lifecycle-runtime-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidx-lifecycle-cmp" }
androidx-navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "androidx-navigation-cmp" }

androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }
androidx-core = { module = "androidx.core:core", version.ref = "androidx-core" }
androidx-datastore-preferences-core = { module = "androidx.datastore:datastore-preferences-core", version.ref = "androidx-datastore" }

kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }

ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }

koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }

kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
android-application = { id = "com.android.application", version.ref = "agp" }
android-kmp-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
# Pas de org.jetbrains.kotlin.android : Kotlin est intégré à AGP 9 pour androidApp.
# detekt volontairement absent (pas de version stable compatible Kotlin 2.4).
```

`gradle/wrapper/gradle-wrapper.properties` :
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
```

---

## 4. IA embarquée : état en novembre 2026 (pour information, rien à épingler)

| Option | Plateformes | Statut | Contraintes appareils / remarques | Source |
|---|---|---|---|---|
| **LiteRT-LM** (Google AI Edge) | Android, iOS, macOS, Windows, Linux, IoT | **Kotlin, C++, CLI, Python : stables.** Swift et JS : Early Preview | CPU partout, GPU sur Android/iOS/desktop, NPU sur Android uniquement. Modèles : Gemma 4 (E2B/E4B), Gemma 3n, Gemma3-1B, FunctionGemma, Qwen2.5/Qwen3-0.6B, phi-4-mini, EmbeddingGemma 2. **Candidat n°1 pour un moteur commun Android + iOS** (NER / anonymisation, extraction JSON), mais le Swift en preview est un écart iOS à signaler | https://developers.google.com/edge/litert-lm/overview · https://developers.googleblog.com/en/blazing-fast-on-device-genai-with-litert-lm/ (19 mai 2026) |
| **MediaPipe LLM Inference API** | Android, iOS, Web | Prédécesseur de LiteRT-LM, que Google présente comme « la prochaine évolution » de la pile. Statut de dépréciation **non vérifié** (page inaccessible) | À éviter pour un nouveau projet | https://developers.googleblog.com/en/blazing-fast-on-device-genai-with-litert-lm/ |
| **ML Kit GenAI** (Gemini Nano via AICore) | **Android uniquement** | Prompt API : **Beta** (`genai-prompt:1.0.0-beta3`, sortie structurée en Alpha). Résumé, correction, réécriture, description d'image : Beta. Speech Recognition : **Alpha** (mode basique API 31+, mode avancé Pixel 10 uniquement) | Appareils Gemini Nano 2/3/4 seulement (gamme haute). Quota par application, **inférence uniquement au premier plan** (`BACKGROUND_USE_BLOCKED`). Pas d'iOS, donc **différence Android/iOS à signaler** | https://developers.google.com/ml-kit/genai · https://android-developers.googleblog.com/2026/07/android-on-device-inference.html |
| **Apple Foundation Models** | iOS/iPadOS/macOS/visionOS 26+ | Framework système (~3 Md de paramètres) ; guided generation, tool calling ; WWDC 2026 : entrée image, sessions dynamiques, routage serveur optionnel (Private Cloud Compute) | Appareils compatibles Apple Intelligence avec Apple Intelligence **activé**. Le modèle le plus puissant exige iPhone 17 Pro / Air. **Français supporté.** Disponibilité dans l'UE à vérifier pour les fonctionnalités récentes. Swift uniquement : nécessite un pont Swift appelé depuis Kotlin (pas d'interop directe) | https://www.cultofmac.com/news/apple-foundation-models-framework · https://www.implicator.ai/apple-expands-xcode-and-foundation-models-for-wwdc-ai-developers/ |
| **whisper.cpp** | Android (JNI/NDK), iOS/macOS (Metal/Core ML), desktop | Projet open source actif ; dernière version vue **v1.8.5** | Modèles tiny/base/small quantifiés, raisonnables sur mobile pour 30 s d'audio. Licence MIT (non revérifiée). Un seul moteur C/C++ pour les deux OS via cinterop/JNI, donc **meilleur candidat pour une transcription iso Android/iOS** | https://newreleases.io/project/github/ggml-org/whisper.cpp/release/v1.8.5 |
| **WhisperKit** (Argmax) | iOS/macOS (Core ML, Apple Silicon) ; Android via l'**Argmax Pro SDK** payant (GA le 18 mars 2026, basé sur LiteRT) | WhisperKit open source ; Pro SDK 3 annoncé le 23 sept. 2026 | Côté Android : payant (clé API). Licence de WhisperKit **non vérifiée** | https://argmaxinc.com/ |

**Ce qu'il faut en retenir pour l'architecture IA de nemla :**
- La couche `ai` doit exposer des ports neutres, par exemple `Transcriber`, `Anonymizer`, `ExpenseExtractor`, `BudgetAdvisor`, avec des adaptateurs interchangeables :
  - `Remote(Lovable Gateway)`, puis `Remote(IA maison)` ;
  - `OnDevice(LiteRT-LM)` ;
  - `OnDevice(whisper.cpp)` ;
  - `Platform(FoundationModels | MLKitGenAI)` en option, avec détection de capacité à l'exécution.
- L'anonymisation locale (IBAN, noms, adresses) doit rester **déterministe d'abord** (regex IBAN/BIC/téléphone/e-mail, en Kotlin commun pur, identique sur les deux OS). Le NER par LLM local ne vient qu'en complément, car la disponibilité des modèles système varie selon l'appareil.
- Écarts Android/iOS à signaler : ML Kit GenAI n'existe que sur Android et Foundation Models que sur Apple. Seuls LiteRT-LM et whisper.cpp offrent un moteur identique sur les deux, avec le Swift de LiteRT-LM encore en Early Preview.
