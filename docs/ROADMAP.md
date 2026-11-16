# Feuille de route

État : ☐ à faire · ◐ en cours · ☑ fait

## Lot 0 — Socle de build ☑
- Wrapper Gradle 9.7.1, `settings.gradle.kts`, `build-logic/convention` (plugins : bibliothèque KMP, Compose, feature, application Android).
- `build.gradle.kts` de tous les modules d'ARCHITECTURE §2, selon les règles de dépendance.
- `androidApp` (manifeste sécurisé : pas de sauvegarde, pas de trafic en clair, écran protégé en release), `iosApp` (projet Xcode, `PrivacyInfo.xcprivacy`).
- Spotless + ktlint, GitHub Actions (Linux : contrôle, tests, assemblage Android ; macOS : framework iOS).
- Critère : `:ai:api` compile sur Android et iOS.
- Fait le 16/11/2026 : `spotlessApply`, `:ai:api:allTests` (aucun test encore), `:androidApp:assembleDebug` et `assembleRelease` (R8),
  `:shared:linkDebugFrameworkIosSimulatorArm64` et la compilation de `iosApp` pour le simulateur réussissent (Xcode 15.4 local).

## Lot 1 — Fondations ◐
- `core:common`, `core:domain` (session, profil, catégories, quotas, consentements IA, ports IA métier), `core:network`, `core:security`, `core:data` (GoTrue, PostgREST, API nemla, passerelle IA).
  - ☑ `core:common` (16/11/2026) : `AppResult` et ses opérateurs, `runCatchingApp` (relance `CancellationException`), `AppError` + `ValidationCode`,
    `AppDispatchers`, `AppBuildInfo`, `AppLogger` typé (événements = constantes d'enum, attributs numériques / booléens / enum uniquement),
    Logcat / NSLog en debug, `NoOpLogger` en release, `coreCommonModule`.
  - ☑ `core:domain` (16/11/2026) : entités (session, identifiants, email / mot de passe validés, profil, devise, catégories, quota mensuel,
    consentements IA `ANONYMIZED` / `PERSONAL`, brouillons de dépense / charge fixe, relevé, estimation de budget, bilan, export),
    ports (`AuthRepository`, `ProfileRepository`, `CategoryRepository`, `QuotaRepository`, `AiConsentRepository`, `AccountRepository`,
    `VoiceExpenseCapturer`, `StatementImporter`, `RecurringChargeParser`, `BudgetAdvisor`, `PeriodReviewer`), cas d'usage d'authentification
    et de consentement, 26 tests.
  - ☑ `core:security` (16/11/2026) : `SecureStore` (Android : AES-256-GCM Android Keystore + DataStore dans `noBackupFilesDir` ;
    iOS : Keychain `AfterFirstUnlockThisDeviceOnly`, non synchronisé), `InMemorySecureStore`, `ScreenProtection`
    (`AndroidScreenProtection.install(application)`, `IosScreenProtection.install()`), `coreSecurityModule`. Branchement dans les coquilles : à faire.
  - ☑ `core:network` (16/11/2026) : `BackendConfig`, `createHttpClient` (OkHttp / Darwin, JSON, délais, HTTPS uniquement, sans redirection),
    plugin d'authentification (`apikey` Supabase, `Bearer`, un rejeu après 401 de session, jamais après un 401 de la passerelle IA),
    `TokenProvider` (rafraîchissement anticipé et sérialisé) au-dessus de `SessionTokenStore`, `safeRequest` (GoTrue, PostgREST, `/api/public`, HTML),
    `coreNetworkModule`, 25 tests.
  - ☑ `core:data` (16/11/2026) : session chiffrée (`SessionStorage` sur `SecureStore`, `SessionManager` = `SessionTokenStore`,
    rafraîchissement GoTrue `refresh_token`, passage à `SignedOut` à l'invalidation), `AuthRepository` (connexion, inscription avec
    métadonnées, déconnexion locale même hors ligne, mot de passe oublié, `Flow<AuthState>` restauré au démarrage), `ProfileRepository`
    (`bootstrap_user` + recopie des métadonnées d'inscription manquantes, profil, identité, budget en décimal exact, rappel, `suggest_pseudo`
    optimiste), `CategoryRepository`, `QuotaRepository` (`ensure_month_quota`), consentements IA dans `SecureStore` (effacés à la
    déconnexion et à l'invalidation), `AccountRepository` en `NotSupportedYet`, passerelle IA (`AiContextSource`, `ConnectivityMonitor`
    remplaçable, `AiRouter` + `LocalFirstPolicy`, trace journalisée sans donnée utilisateur, ports dictée / relevé / charges / budget /
    bilan ; relevé : CSV et texte seulement, anonymisation locale obligatoire, `NotSupportedYet` sans moteur `StatementParsing`),
    `coreDataModule`, 53 tests.
- ☑ `ai:local` (16/11/2026) : `RuleBasedStatementAnonymizer`, porté des règles de `specs/01` §6.2 (classes de caractères explicites pour
  reproduire `\b`, `\s`, `\d` de JavaScript sur JVM, ICU et Kotlin/Native), `aiLocalModule`. 16 tests dont 22 cas de parité générés
  avec une transcription JavaScript des règles serveur. Écarts volontaires : IBAN compact masqué, `knownNames`, idempotence (voir rapport du lot).
- ☑ `ai:remote-nemla` (16/11/2026) : les cinq moteurs du tableau d'ARCHITECTURE §4, montants JSON → centimes sans flottant,
  `/transcribe` lu en objet ou en chaîne, erreurs traduites en `AiError` sans contenu serveur, `aiRemoteNemlaModule`. 31 tests.
- ☑ Tests de `AiRouter` (16/11/2026) : 33 tests.
- `core:designsystem` d'après `specs/03`. Polices Outfit et DM Sans embarquées.
- `feature:auth` d'après `specs/04`, `feature:home` (coquille), `shared` (navigation, Koin).

## Lot 2 — Dictée ☐
`core:audio` (WAV mono 16 kHz 16 bits, 30 s, arrêt après 3 s de silence), `feature:voice` (bouton, enregistrement, fiche de revue), consentement IA.
Dépend des endpoints dépenses du backend.

## Lot 3 — Tableau de bord ☐
Météo du budget, graphique mensuel, « Où part l'argent », dépenses du jour, recherche. Débit différé (`specs/02`).

## Lot 4 — Mon espace et onboarding ☐
Budget, charges fixes, profil, préférences, catégories, crédits, suppression de compte, export.

## Lot 5 — Import de relevés ☐
Extraction du texte sur l'appareil (PDF, CSV), anonymisation locale, envoi du texte anonymisé. Dépend d'un nouvel endpoint.

## Lot 6 — Bilans, notifications, publication ☐
Bilans, notifications (FCM / APNs, à construire côté serveur), fiches des stores, politique de confidentialité.

## Plus tard
Transcription locale (whisper.cpp), modèle local (LiteRT-LM), cache chiffré hors ligne, autre fournisseur d'IA.
