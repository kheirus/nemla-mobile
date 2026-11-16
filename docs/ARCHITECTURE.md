# nemla mobile — architecture

Application Android et iOS de nemla, en Kotlin Multiplatform avec interface partagée en Compose Multiplatform.
Ce document fait foi : toute contribution (humaine ou agent) s'y conforme ou le modifie d'abord.

## 1. Décisions structurantes

| Sujet | Décision | Raison |
|---|---|---|
| Interface | Compose Multiplatform, 100 % partagée (`commonMain`) | Iso Android/iOS par construction. Sur Android c'est Jetpack Compose. |
| Composants | Material 3 (1.9.0 stable), thème nemla par-dessus | Dernière version stable commune aux deux plateformes. |
| Architecture | Clean architecture, UDF (état immuable + événements), un ViewModel par écran | Guidelines Android ; testable sans plateforme. |
| Navigation | `org.jetbrains.androidx.navigation:navigation-compose` (Navigation 2 multiplateforme), routes `@Serializable` typées | Stable sans ambiguïté ; Navigation 3 multiplateforme n'est pas déclarée stable. |
| Injection | Koin 4 | Multiplateforme, sans génération de code. |
| Réseau | Ktor 3 (OkHttp sur Android, Darwin sur iOS), kotlinx.serialization | Demande du projet. Aucune autre pile HTTP. |
| Supabase | Appels REST GoTrue et PostgREST écrits à la main sur Ktor, pas de SDK | Surface d'audit réduite, maîtrise du stockage des jetons, aucune dépendance à compatibilité incertaine. |
| IA | Module `:ai:api` autonome ; moteurs interchangeables ; routeur « local d'abord » | Voir §4. |
| Données au repos | Lot 1 : aucune donnée financière persistée sur l'appareil. Seuls la session (chiffrée) et des préférences non sensibles le sont | RGPD : minimisation. Un cache chiffré viendra avec le mode hors ligne. |
| Qualité | Spotless + ktlint (detekt n'a pas de version stable compatible Kotlin 2.4) | |
| Identifiants | `applicationId` / bundle id : `app.nemla` ; paquet racine Kotlin : `app.nemla` | Domaine nemla.app. |

Versions : `gradle/libs.versions.toml` (source : `docs/specs/00-versions.md`). Uniquement des versions stables.
Contraintes connues : Kotlin 2.4.21 impose AGP 9.3.x et Gradle 9.7.x ; AGP 9 interdit `com.android.library` dans un module KMP
(on utilise `com.android.kotlin.multiplatform.library`) et impose un module `androidApp` séparé.

## 2. Modules

```
build-logic/convention     plugins de convention Gradle
androidApp/                application Android (Activity, manifeste, démarrage Koin)
iosApp/                    projet Xcode (coquille SwiftUI qui héberge l'UI Compose)
shared/                    racine Compose (App, navigation, assemblage Koin), framework iOS « Shared »
core/common                AppResult, AppError, dispatchers, journalisation expurgée
core/domain                entités, ports (interfaces de dépôts), cas d'usage — Kotlin pur
core/network               fabrique HttpClient, gestion des jetons, correspondance des erreurs
core/security              stockage sécurisé (Keystore / Keychain), protection d'écran
core/data                  implémentations des dépôts, API Supabase et nemla, passerelle domaine ↔ IA
core/designsystem          thème, jetons, typographie, composants nemla
core/audio                 enregistreur (WAV mono 16 kHz 16 bits en mémoire, silence, durée max), micro et permission
ai/api                     contrats IA : capacités, moteurs, routeur, types de confidentialité
ai/local                   moteurs embarqués (anonymisation par règles aujourd'hui, modèles locaux demain)
ai/remote-nemla            moteurs adossés au backend nemla actuel (Lovable)
feature/auth               connexion, inscription, mot de passe oublié
feature/home               écran d'accueil authentifié (lot 1 : coquille ; reçoit le bouton « Ajouter » par un emplacement)
feature/voice              bouton « Ajouter », dialogue de dictée, consentement IA, fiche de revue
```

Lots suivants : `feature/dashboard`, `feature/onboarding`, `feature/settings`,
`feature/recurring`, `feature/import`, `feature/reports`, `ai/remote-<autre fournisseur>`.

### Règles de dépendance (vérifiées en revue)

```
feature:*        -> core:domain, core:common, core:designsystem          (jamais core:data, jamais ai:*)
feature:voice    -> + core:audio                                         (seule feature autorisée à l'utiliser)
core:audio       -> core:common (+ Compose runtime ; androidx activity-compose sur Android, AVFAudio sur iOS)
core:data        -> core:domain, core:common, core:network, core:security, ai:api
core:domain      -> core:common                                          (rien d'autre : ni Ktor, ni Compose, ni IA)
core:network     -> core:common
core:security    -> core:common
core:designsystem-> (Compose uniquement)
ai:api           -> (coroutines, kotlinx-datetime uniquement)
ai:local         -> ai:api
ai:remote-nemla  -> ai:api, core:network, core:common
shared           -> tout (c'est le seul endroit qui assemble)
androidApp       -> shared
```

Une feature ne dépend jamais d'une autre : `feature:home` expose un emplacement (`floatingAction`) que `shared` remplit avec
`VoiceCaptureButton` de `feature:voice`, et `shared` déclare la destination `voiceGraph` (fiche de revue).

Le domaine ne connaît pas l'IA : il expose des ports métier (`VoiceExpenseCapturer`, `StatementImporter`…) que
`core:data` implémente en s'appuyant sur `AiRouter`. Les écrans ne connaissent que des cas d'usage.

### Conventions de code

- Paquet d'un module : `app.nemla.<chemin du module>` (`app.nemla.core.domain`, `app.nemla.feature.auth`, `app.nemla.ai.local`).
- Chaque module qui fournit des objets expose un module Koin public nommé `<nom>Module` (`coreDataModule`, `featureAuthModule`…).
- Écran : `XxxRoute` (branché au ViewModel, sans paramètre d'état) qui délègue à `XxxScreen` (sans état, prévisualisable).
  État : `data class XxxUiState` immuable. Événements : `sealed interface XxxAction`. Effets ponctuels : `Flow` d'`XxxEffect`.
- ViewModel : `androidx.lifecycle.ViewModel` multiplateforme, `StateFlow`, `viewModelScope`, collecte avec `collectAsStateWithLifecycle`.
- Erreurs : pas d'exception à travers les couches. Les dépôts renvoient `AppResult<T>` ; `CancellationException` est toujours relancée.
- Montants : `Long` en centimes (`amountCents`). Jamais de `Double` pour de l'argent, sauf aux frontières JSON.
- Dates : `kotlinx.datetime.LocalDate` ; instants : `kotlin.time.Instant`.
- Textes : aucune chaîne en dur dans l'UI ; ressources Compose (`composeResources/values/strings.xml` et `values-en`), français par défaut.
- Aucun `println`. Journalisation via `AppLogger` (core:common) qui n'accepte jamais de donnée utilisateur (voir §5).
- Visibilité : `internal` par défaut ; `public` uniquement pour ce qui traverse le module.

## 3. Couches et flux

```
UI (Compose)  ──action──▶  ViewModel  ──▶  cas d'usage  ──▶  port (domaine)
     ▲                         │                                   │ implémenté par
     └────────état─────────────┘                              core:data ──▶ Ktor (Supabase / API nemla)
                                                                    └────▶ AiRouter ──▶ moteurs IA
```

### Accès au backend

L'analyse du code web (`docs/specs/01-api-contracts.md`) montre trois canaux, dont un inutilisable par le mobile :

1. **Supabase Auth (GoTrue)** : inscription, connexion, rafraîchissement, mot de passe oublié. Utilisable tel quel.
2. **PostgREST + RLS** : profil, catégories, charges fixes, quotas, montants et dates des dépenses. Utilisable tel quel.
3. **Endpoints `/api/public/*`** : dictée, transcription, import, charges fixes, suggestion de budget, bilan. Utilisables tels quels.
4. **Server functions TanStack** : libellés de dépenses (chiffrés), transcription, matérialisation des charges fixes,
   suppression de compte. **Non appelables par le mobile.** Des endpoints REST équivalents doivent être ajoutés au backend
   (liste dans `docs/BACKEND-GAPS.md`). Tant qu'ils n'existent pas, les fonctions concernées restent derrière leurs ports
   avec une implémentation qui renvoie `AppError.NotSupportedYet`.

Configuration (`BackendConfig`) : `supabaseUrl`, `supabasePublishableKey`, `apiBaseUrl`. Fournie au démarrage par chaque
plateforme (BuildConfig Android, Info.plist iOS) à partir de `local.properties` / `Config.xcconfig`, jamais versionnée.
La clé publishable est publique par nature ; aucun autre secret n'entre dans l'application.

## 4. Couche IA

Objectif : pouvoir changer de fournisseur, en combiner plusieurs et faire tourner un maximum de choses sur l'appareil,
sans toucher au reste de l'application.

### Contrats (`:ai:api`)

- `AiCapability<I, O>` : une chose demandée à une IA, avec ses types d'entrée/sortie et la **sensibilité** de l'entrée.
  Capacités : `SpeechToText`, `ExpenseExtraction`, `VoiceExpenseCapture` (opération combinée du backend actuel),
  `StatementAnonymization`, `StatementParsing`, `RecurringParsing`, `BudgetSuggestion`, `PeriodReport`.
- `AiEngine<I, O>` : l'implémentation d'une capacité par un fournisseur. C'est la **seule** interface à écrire pour brancher
  une nouvelle IA. Un moteur déclare où il s'exécute (`ON_DEVICE` / `REMOTE`), son fournisseur et son coût en crédits.
- `AiRouter` : point d'entrée unique. Il choisit les moteurs, applique les règles de confidentialité, bascule d'un moteur
  au suivant en cas d'échec et produit une trace (`AiTrace`) sans donnée utilisateur.
- `AiRoutingPolicy` : stratégie d'ordre, remplaçable. Par défaut `LocalFirstPolicy` : appareil d'abord, puis distant le moins cher.
- `AiContextSource` : faits fournis par l'application (réseau, crédits restants, consentements).

### Garanties de confidentialité

1. **Par le typage** : un relevé bancaire brut est un `RawStatement` ; le seul type accepté par un moteur distant de lecture de
   relevé est `AnonymizedText`, dont le constructeur exige `@OptIn(PrivacyBoundary::class)`. Seul `:ai:local` y a droit.
   Envoyer un relevé non anonymisé au réseau ne compile pas.
2. **À l'exécution** : `AiRouter` refuse au démarrage tout moteur distant enregistré pour une capacité `RAW_FINANCIAL`.
3. **Par le consentement** : un moteur distant ne reçoit une donnée `PERSONAL` ou `ANONYMIZED` que si l'utilisateur a consenti
   à ce niveau (`AiRoutingContext.remoteConsent`) ; sinon l'appel échoue en `ConsentRequired` et l'UI demande l'accord.

### Chaînes de traitement (dans `core:data`, paquet `ai`)

```
Dictée      : [SpeechToText local] → [ExpenseExtraction]           si un moteur de transcription local existe
              sinon VoiceExpenseCapture (backend nemla : audio → dépenses)
Relevé      : extraction du texte sur l'appareil → StatementAnonymization (appareil, obligatoire)
              → StatementParsing (distant, texte anonymisé uniquement)
Charges     : RecurringParsing
Budget/Bilan: BudgetSuggestion / PeriodReport (calcul serveur aujourd'hui ; calcul local possible plus tard)
```

### Moteurs présents au lot 1

| Module | Moteur | Capacité | Lieu |
|---|---|---|---|
| `ai:local` | `RuleBasedStatementAnonymizer` | `StatementAnonymization` | appareil |
| `ai:remote-nemla` | `NemlaVoiceCaptureEngine` | `VoiceExpenseCapture` | distant |
| `ai:remote-nemla` | `NemlaTranscriptionEngine` | `SpeechToText` | distant |
| `ai:remote-nemla` | `NemlaRecurringParsingEngine` | `RecurringParsing` | distant |
| `ai:remote-nemla` | `NemlaBudgetSuggestionEngine` | `BudgetSuggestion` | distant |
| `ai:remote-nemla` | `NemlaPeriodReportEngine` | `PeriodReport` | distant |

`StatementParsing` n'a pas encore de moteur : le backend actuel n'accepte que le fichier brut (`/import/file`), ce que les
règles ci-dessus interdisent. Il faut un endpoint qui reçoit le texte déjà anonymisé (voir `docs/BACKEND-GAPS.md`).

### Ajouter une IA

1. Créer `ai/remote-<nom>` (ou enrichir `ai/local`), implémenter `AiEngine` pour les capacités servies.
2. Exposer un module Koin qui déclare ces moteurs ; l'ajouter dans `shared`.
3. Rien d'autre : ni le domaine, ni les écrans ne changent. Pour changer l'ordre de préférence, fournir une autre `AiRoutingPolicy`.

## 5. Sécurité et RGPD

| Exigence | Mise en œuvre |
|---|---|
| Jetons de session | `SecureStore` : Android Keystore (AES-256-GCM, clé non exportable) ; iOS Keychain (`AfterFirstUnlockThisDeviceOnly`). Jamais en clair, jamais dans les sauvegardes. |
| Sauvegardes | Android : `allowBackup=false` + règles d'extraction qui excluent tout. iOS : Keychain non synchronisé. |
| Transport | HTTPS uniquement (`usesCleartextTraffic=false`, ATS par défaut). Épinglage de certificat prévu quand le domaine d'API sera figé. |
| Journaux | `AppLogger` n'accepte que des messages constants et des identifiants techniques. Le journal HTTP est désactivé en release et n'affiche jamais corps ni en-têtes. Les types sensibles redéfinissent `toString()`. |
| Écran | `FLAG_SECURE` sur Android en release (pas de capture ni d'aperçu dans les applications récentes) ; voile sur iOS quand l'application passe en arrière-plan. |
| Minimisation | Aucune donnée financière au repos (lots 1 et 2). Audio en mémoire uniquement : jamais écrit sur disque ni téléversé dans le bucket `voice-notes` (le WAV part directement en multipart vers `voice/process`), tampons remis à zéro après envoi, refus ou annulation. Enregistrement annulé quand l'application passe en arrière-plan. La dictée en attente de revue reste en mémoire et est oubliée à la déconnexion ou au changement de compte. |
| Consentement | Envoi de données à une IA distante soumis à un accord explicite, par niveau de sensibilité, révocable dans les réglages. La dictée exige le niveau `PERSONAL` : dialogue avant la première dictée et à chaque `ConsentRequired` du routeur ; refus = rien n'est enregistré ni envoyé. |
| Apprentissage | `record_category_correction` reçoit la clé marchand normalisée et un `p_label` vide (le RPC l'ignore) : le libellé ne quitte pas l'appareil pour l'apprentissage. |
| Droits | Suppression de compte dans l'application (obligatoire App Store / Google Play) et export des données : ports prévus, dépendants du backend. |
| Traceurs | Aucun SDK d'analyse, de publicité ou de suivi. Manifeste de confidentialité iOS (`PrivacyInfo.xcprivacy`) déclaré sans collecte tierce. |
| Code | R8 activé en release. Aucun secret dans le dépôt ni dans le binaire. |

Les points qui relèvent du backend (RLS, conservation, sous-traitants) sont dans `docs/BACKEND-GAPS.md`.

## 6. Parité Android / iOS

Toute l'interface et toute la logique sont partagées. Les seuls codes propres à une plateforme sont derrière `expect/actual`
ou des interfaces, et listés dans `docs/PLATFORM-PARITY.md` avec, pour chacun, l'écart visible éventuel pour l'utilisateur.

## 7. Tests et intégration continue

- Tests unitaires en `commonTest` (kotlin-test, coroutines-test, Turbine) : routeur IA, anonymiseur, cas d'usage, mappers, ViewModels.
- GitHub Actions : `spotlessCheck`, tests, assemblage Android (debug) sur Linux ; compilation du framework iOS sur macOS.
