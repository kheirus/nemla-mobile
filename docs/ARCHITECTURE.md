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
core/documents             sélecteur de fichier système, lecture en mémoire et extraction du texte (CSV, TXT, PDF) sur l'appareil
ai/api                     contrats IA : capacités, moteurs, routeur, types de confidentialité
ai/local                   moteurs embarqués (anonymisation par règles aujourd'hui, modèles locaux demain)
ai/remote-nemla            moteurs adossés au backend nemla actuel (Lovable)
feature/auth               connexion, inscription, mot de passe oublié
feature/dashboard          tableau de bord, écran authentifié principal (lot 3 ; remplace la coquille feature/home du lot 1)
feature/voice              bouton « Ajouter », dialogue de dictée, consentement IA, fiche de revue, bouton « Dicter » réutilisable
feature/onboarding         « Vos charges fixes » après la première connexion (lot 4)
feature/settings           Mon espace : profil & budget (5 panneaux), bilans, crédits, catégories, zone dangereuse (lot 4)
feature/import             dialogue « Importer un relevé de compte » (lot 5 ; paquet `app.nemla.feature.statementimport`,
                           `import` étant un mot réservé de Kotlin et de Java)
feature/reports            bilans (lot 6) : dialogue de bilan, bandeau « Votre bilan de … est arrivé », historique des bilans ;
                           bannières in-app de `user_notifications` (lot 7)
core/notifications         notifications locales (lot 7) : permission, planification, affichage immédiat
                           (AlarmManager + NotificationManager sur Android, UNUserNotificationCenter sur iOS)
```

Lots suivants : push serveur (après le lot 7, quand le backend saura émettre), `ai/remote-<autre fournisseur>`.

### Règles de dépendance (vérifiées en revue)

```
feature:*        -> core:domain, core:common, core:designsystem          (jamais core:data, jamais ai:*)
feature:voice    -> + core:audio                                         (seule feature autorisée à l'utiliser)
core:audio       -> core:common (+ Compose runtime ; androidx activity-compose sur Android, AVFAudio sur iOS)
feature:import   -> + core:documents                                     (seule feature autorisée à l'utiliser)
core:documents   -> core:common (+ Compose runtime et ui ; activity-compose et PdfRenderer sur Android, UIKit et PDFKit sur iOS)
feature:settings -> + core:notifications                                 (demande de permission seulement)
core:notifications -> core:domain, core:common (+ Compose runtime ; androidx core et activity-compose sur Android,
                      UserNotifications sur iOS). Il implémente le port `LocalNotifier` du domaine.
core:data        -> core:domain, core:common, core:network, core:security, ai:api
core:domain      -> core:common                                          (rien d'autre : ni Ktor, ni Compose, ni IA)
core:network     -> core:common
core:security    -> core:common
core:designsystem-> (Compose uniquement)
ai:api           -> (coroutines, kotlinx-datetime uniquement)
ai:local         -> ai:api
ai:remote-nemla  -> ai:api, core:network, core:common                   (tests : + ai:local, pour obtenir un AnonymizedText)
shared           -> tout (c'est le seul endroit qui assemble)
androidApp       -> shared
```

Une feature ne dépend jamais d'une autre : `feature:dashboard` expose un emplacement (`floatingAction`) que `shared` remplit avec
`VoiceCaptureButton` de `feature:voice`, et `shared` déclare la destination `voiceGraph` (fiche de revue). L'entrée « Importer un relevé »
du menu « Ajouter » appelle le rappel `onImportStatement` de `VoiceCaptureButton` ; `shared` affiche alors `StatementImportDialog` de
`feature:import`, qui rend un `PendingReview` (origine `IMPORT`, `announce = true`) ; `shared` le confie à `PendingReviewOpener` puis ouvre
`voiceGraph`, comme pour les dépenses en attente. Pour rouvrir des dépenses
en attente depuis le tableau de bord, `feature:dashboard` émet un `PendingReview` (`core:domain`) par le rappel `onReviewPending` ;
`shared` le confie au port `PendingReviewOpener` (implémenté par `feature:voice` au-dessus de `VoiceReviewStore`) puis ouvre `voiceGraph`.

Les bilans (lot 6) suivent le même principe : `feature:dashboard` expose l'emplacement `reportNotice` (rendu juste après les alertes
de budget, position 4 de `specs/04` §5.2) que `shared` remplit avec `ReportArrivalNotice`, et `feature:settings` expose `reportHistory`
(onglet « Bilans ») que `shared` remplit avec `ReportHistory`. Les deux ouvrent `ReportDialog` de `feature:reports`, qui ne connaît que
des cas d'usage (`LoadPeriodReport`, `FindArrivedReports`, `MarkReportViewed`, `ListReportHistory`).

La dictée de texte libre (onboarding, panneau « Mes charges fixes ») suit le même principe : `feature:onboarding` et
`feature:settings` exposent un emplacement composable (`OnboardingDictationSlot`, `SettingsDictationSlot`, de signature
`(enabled, onTranscript, onActiveChange, onMessage) -> Unit`) que `shared` remplit avec `DictationTextButton` de `feature:voice`.
Ce bouton reprend l'enregistreur du FAB (30 s, 3 s de silence, « Arrêter ({s}/{max}s) »), le consentement `PERSONAL` et transcrit via
le port `SpeechTranscriber` (implémenté dans `core:data` par la capacité `SpeechToText`, `POST /api/public/transcribe`). La feature hôte
reçoit le texte, l'état actif (pour désactiver « Analyser ») et les messages d'erreur déjà traduits.

Aiguillage après connexion (`shared`, `AppViewModel`) : à chaque nouvel utilisateur connecté, `ResolveStartDestination` appelle
`bootstrap_user` puis lit le profil : onboarding seulement si `onboarding_done` est faux et que le compte est vierge (budget mensuel 0,
aucune charge fixe, aucune dépense lue avec `limit=1`) ; sinon, ou en cas d'erreur, tableau de bord. L'onboarding peut être passé. Mon espace s'ouvre depuis
le menu de l'en-tête du tableau de bord (« Gérer mon compte ») ou le lien des alertes de budget (onglet Catégories) ; « Retour » dépile.

Préférences locales (langue de l'application, langue de dictée, thème) : port `PreferencesRepository` (`StateFlow`), implémenté dans
`core:data` sur `SecureStore` (clé `preferences.v1`, conservée à la déconnexion). La devise reste dans `profiles.devise`. `shared` applique
le thème (`NemlaTheme(darkTheme)`) et la langue (`ProvideAppLanguage`, `expect/actual` : `Locale.setDefault` + `LocalConfiguration`
sur Android, `AppleLanguages` sur iOS, puis recomposition sous `key(langue)` avec le même `NavController`). `DeviceLanguage` (langue
du système au démarrage) sert de valeur par défaut ; `CurrentLanguages` donne aux ViewModels la langue d'interface et de dictée.

Le domaine ne connaît pas l'IA : il expose des ports métier (`VoiceExpenseCapturer`, `StatementImporter`…) que
`core:data` implémente en s'appuyant sur `AiRouter`. Les écrans ne connaissent que des cas d'usage.

### Conventions de code

- Paquet d'un module : `app.nemla.<chemin du module>` (`app.nemla.core.domain`, `app.nemla.feature.auth`, `app.nemla.ai.local`).
- Chaque module qui fournit des objets expose un module Koin public nommé `<nom>Module` (`coreDataModule`, `featureAuthModule`…).
- Écran : `XxxRoute` (branché au ViewModel, sans paramètre d'état) qui délègue à `XxxScreen` (sans état, prévisualisable).
  Mon espace compte un ViewModel hôte (onglet, panneau ouvert, en-tête, suppression du compte) et un couple Route / ViewModel par
  panneau ou onglet (budget, charges fixes, profil, préférences, carte, crédits, catégories), tous liés à la même entrée de navigation.
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

### Notifications (lot 7)

Décision du propriétaire (19/11/2026) : notifications **locales** d'abord, sans Firebase, sans SDK de push et sans changement du
backend. Le push serveur (FCM / APNs) viendra quand le backend saura émettre (`BACKEND-GAPS.md`).

| Notification | Déclenchement | Condition |
|---|---|---|
| Rappel quotidien « Bilan de la journée… » | planifiée à 20 h locale, 14 jours d'avance | `profiles.daily_reminder`, permission accordée ; aujourd'hui sauté si une dépense confirmée existe aujourd'hui ou s'il est déjà 20 h passées |
| « Votre bilan de {mois} est arrivé » | planifiée à 10 h le premier jour du bandeau (dernier jour du mois − 2), pour le mois courant et les 2 suivants ; en décembre, une seconde pour l'année | permission accordée |
| « Budget atteint » | immédiate, quand le tableau de bord calcule les alertes du mois courant | permission accordée ; une seule fois par catégorie et par mois (`NotificationMemory`) |
| `user_notifications` `channel = push` non délivrées | immédiate, à chaque retour au premier plan | permission accordée ; puis `delivered_at` et `seen_at` |
| `user_notifications` `channel = in_app` non vues | bannières du tableau de bord (`specs/04` §5.11) | lues au retour au premier plan (pas de sondage à 30 s) |

Contrats (`core:domain`) :
- modèle `Notifications.kt` : `NotificationAccess { GRANTED, DENIED, UNDETERMINED }` ; `LocalNotice` (scellé : `DailyReminder`,
  `ReportArrived(period: ReviewPeriod)`, `BudgetReached(categoryName, usedCents, allocatedCents, currency)`, `ServerMessage(title, body)`,
  `toString()` masqué pour les deux derniers) ; `ScheduledNotice(notice, at: LocalDateTime)` ; `NoticeGroup { DAILY_REMINDER, REPORT_ARRIVAL }` ;
  `NotificationPlanner` (objet pur : `reminders(now, confirmedToday)`, `reportArrivals(now)`, `budgetKey(month, categoryId)`) ;
  `InboxItem` (id, canal `IN_APP` / `PUSH`, type `MONTH_REPORT` / `YEAR_REPORT` / `DAILY_REMINDER`, titre, corps, période, `report: ReportSheet?`
  lu dans `payload`, `delivered`).
- ports : `LocalNotifier` (`access()`, `schedule(group, notices, language)` qui **remplace** le groupe, `show(notice, tag, language)`,
  `cancelAll()`) ; `NotificationMemory` (`wasAlerted(key)`, `remember(key)`, `clear()`) ; `InboxRepository` (`unseen()`, `markSeen(id)`,
  `markDelivered(id)`).
- cas d'usage : `RefreshScheduledNotifications` (replanifie les deux groupes ; groupe vidé si la permission manque ou si le rappel est
  coupé ; profil illisible → groupe du rappel laissé tel quel), `SetDailyReminder` (profil puis replanification), `AlertReachedBudgets`,
  `DeliverPushInbox`, `ListInAppNotices`, `DismissInboxItem`, `ClearNotifications` (déconnexion : `cancelAll` + `clear`).
  `ConfirmReviewedExpenses` appelle `RefreshScheduledNotifications` après succès (le rappel du jour disparaît dès la première dépense).
- signatures retenues (19/11/2026) : les ports renvoient `AppResult` (`access()` renvoie directement `NotificationAccess`,
  `wasAlerted` renvoie `AppResult<Boolean>` : mémoire illisible → pas d'alerte) ; `RefreshScheduledNotifications(notifier, profiles,
  expenses, languages, clock = Clock.System, timeZone)` lit l'heure locale lui-même (`kotlin.time.Clock`, comme `core:data`) et la
  dépense du jour par `ExpenseRepository` (`date` = aujourd'hui, `confirmed`, `limit=1`) ; `AlertReachedBudgets(month, budgets:
  List<ReachedBudget>, currency): Int` (nombre affiché), où `ReachedBudget(categoryId, categoryName, usedCents, allocatedCents)` est
  construit par le tableau de bord depuis ses alertes (seules les lignes `allocated > 0 && used >= allocated` comptent) ;
  `DeliverPushInbox(): AppResult<Int>`. Sans permission, `AlertReachedBudgets` et `DeliverPushInbox` ne font rien et ne marquent rien.
  `GetNotificationAccess` (ajouté au lot 7, partie B) expose `LocalNotifier.access()` à `feature:settings`. Tous les cas d'usage de
  notification sont enregistrés une seule fois, dans `coreNotificationsModule` (inclus par `shared`) ; les tests de module des
  features (`feature:voice`, `feature:settings`, `feature:dashboard`, `feature:reports`) les déclarent eux-mêmes avec des faux.

Textes : les notifications sont affichées application fermée ; `core:notifications` les résout au moment de la planification, dans la
langue de l'interface, depuis une table fr/en (`NotificationCopy`). Un changement de langue replanifie. Aucun contenu de notification
dans les journaux. Android garde le plan (heures et textes génériques, jamais d'alerte de budget) dans des `SharedPreferences` privées
pour le reprogrammer après redémarrage (`BOOT_COMPLETED`) ; iOS conserve lui-même les notifications planifiées. Alerte de budget sur
Android en `VISIBILITY_PRIVATE` (version publique « nemla »), de même que les messages du serveur (`ServerMessage`). Toucher une notification ouvre l'application (tableau de bord), sans lien
profond au lot 7. Sur iOS, un délégué `UNUserNotificationCenterDelegate` affiche les notifications reçues application ouverte.

Déclencheurs (`shared`, `NotificationCoordinator`) : à la connexion et à chaque passage au premier plan (`Lifecycle.State.STARTED` de la
racine), `RefreshScheduledNotifications` puis `DeliverPushInbox` ; au changement de langue, replanification ; à la déconnexion,
`ClearNotifications`. La permission n'est demandée que par l'interrupteur du rappel (Mon espace › Préférences), jamais au démarrage.
`feature:dashboard` appelle `AlertReachedBudgets` après chaque chargement (initial, retour au premier plan, tirer pour actualiser,
après une modification), avec les alertes du mois **calendaire** courant quelle que soit la période affichée (comme `specs/04` §5.8),
si le profil, les catégories et les dépenses du mois ont été lus ; il expose l'emplacement `notificationBanners`
(position 5 de `specs/04` §5.2), rempli par `shared` avec `InboxBanners` de `feature:reports`. `InboxBanners` relit
`ListInAppNotices` à chaque `STARTED` ; toucher un bilan ouvre `ReportDialog` sur le `ReportSheet` du `payload`
(`ReportRequest.Delivered`, sans appel serveur ; sans `payload`, état d'erreur, « Réessayer » seulement si la période est connue,
qui recalcule alors par `LoadPeriodReport`). `NotificationCoordinator` est un ViewModel de `shared` (racine de `App`, hors de
`key(langue)`), qui suit `ObserveAuthState` (une synchronisation par utilisateur connecté, pas à chaque rafraîchissement de jeton) et
`ObservePreferences`.

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
Relevé      : core:documents (sélection, lecture en mémoire, extraction, nettoyage, 90 000 caractères) dans feature:import
              → ImportStatement (quota d'import) → StatementImporter (core:data) → StatementAnonymization (appareil, obligatoire)
              → StatementParsing (distant, texte anonymisé uniquement : POST /api/public/import/text, proposé)
Charges     : RecurringParsing
Budget/Bilan: BudgetSuggestion / PeriodReport (calcul serveur aujourd'hui ; calcul local possible plus tard)
```

Bilans (lot 6, arbitrage) : le calcul passe par la capacité `PeriodReport` (`PeriodReviewer` → `RoutedPeriodReviewer` → `AiRouter` →
`NemlaPeriodReportEngine`, `GET /api/public/report/monthly`), bien qu'il n'y ait pas d'IA côté serveur : cela garde la porte ouverte à
un calcul sur l'appareil sans toucher aux écrans. Sensibilité `NONE` (aucun consentement), 0 crédit. La période est **toujours**
explicite (`?month=AAAA-MM` ou `?year=AAAA`), calculée sur la date **locale** de l'appareil ; le moteur refuse une réponse dont la période
ou le type diffère de la demande (`invalid_response`). Les vues (`report_views`) et les mois ayant au moins une dépense confirmée
(historique) ne sont pas de l'IA : port `ReportRepository`, implémenté en PostgREST direct (`KtorReportRepository`). Les règles de
calendrier (bandeau des 3 derniers jours, décembre, candidats M−1…M−24 et A−1…A−3) sont dans `ReportCalendar` (`core:domain`).

### Moteurs présents au lot 1

| Module | Moteur | Capacité | Lieu |
|---|---|---|---|
| `ai:local` | `RuleBasedStatementAnonymizer` | `StatementAnonymization` | appareil |
| `ai:remote-nemla` | `NemlaVoiceCaptureEngine` | `VoiceExpenseCapture` | distant |
| `ai:remote-nemla` | `NemlaTranscriptionEngine` | `SpeechToText` | distant |
| `ai:remote-nemla` | `NemlaRecurringParsingEngine` | `RecurringParsing` | distant |
| `ai:remote-nemla` | `NemlaBudgetSuggestionEngine` | `BudgetSuggestion` | distant |
| `ai:remote-nemla` | `NemlaPeriodReportEngine` | `PeriodReport` | distant |
| `ai:remote-nemla` | `NemlaStatementParsingEngine` (lot 5) | `StatementParsing` | distant |

`StatementParsing` (lot 5) : le mobile n'appelle **jamais** `/import/file`, même en repli, car cet endpoint reçoit le fichier brut.
`NemlaStatementParsingEngine` envoie le seul texte anonymisé à l'endpoint **proposé** `POST /api/public/import/text`
(contrat dans `docs/BACKEND-GAPS.md`). Son `availability()` sonde l'endpoint avec un corps vide `{}` et ne laisse partir le texte que sur
une réponse JSON 400, 401, 413, 422 ou 429 (sonde « fermée par défaut ») : 404, 405, corps non JSON ou erreur de route de l'hébergeur web
(`500 Only HTML requests are supported here`) rendent le moteur indisponible (`not_supported`) avant tout envoi du texte ; hors ligne,
délai dépassé et autres 5xx le rendent aussi indisponible ; et `core:data` traduit cet échec en `AppError.NotSupportedYet` (l'interface
affiche « … Rien n'a été envoyé. »). Le moteur coûte 0 crédit pour le routeur (les crédits du routeur sont ceux de la dictée) ; le quota
d'import est contrôlé par `ImportStatement` avant l'extraction, puis par le serveur. `StatementParsingRequest` ne porte pas le format du
fichier : le champ `kind` n'est donc pas envoyé (point à arbitrer, voir ROADMAP lot 5).

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
| Minimisation | Aucune donnée financière au repos (lots 1 à 3 : le tableau de bord garde ses données en mémoire, dans le ViewModel). Audio en mémoire uniquement : jamais écrit sur disque ni téléversé dans le bucket `voice-notes` (le WAV part directement en multipart vers `voice/process`), tampons remis à zéro après envoi, refus ou annulation. Enregistrement annulé quand l'application passe en arrière-plan. La dictée en attente de revue reste en mémoire et est oubliée à la déconnexion ou au changement de compte. Relevés (lot 5) : le fichier n'est ni copié ni mis en cache (lecture du flux du sélecteur, iOS en mode ouverture sans copie), les octets lus sont remis à zéro après décodage, le texte extrait vit dans le ViewModel le temps de l'import ; seul le texte anonymisé quitte l'appareil ; ni nom de fichier, ni contenu, ni texte anonymisé dans les journaux (type d'erreur, format et longueur seulement). Bilans (lot 6) : calculés par le serveur à chaque ouverture et gardés dans le ViewModel du dialogue ; seules les dates des dépenses confirmées sont lues pour l'historique, sans montant ni libellé. |
| Consentement | Envoi de données à une IA distante soumis à un accord explicite, par niveau de sensibilité, révocable dans Mon espace › Préférences (lot 4 : « Retirer mon accord » retire tous les niveaux). La dictée (FAB et bouton « Dicter ») exige le niveau `PERSONAL` : dialogue avant la première dictée et à chaque `ConsentRequired` du routeur ; refus = rien n'est enregistré ni envoyé. L'import de relevé exige `ANONYMIZED` (couvert aussi par `PERSONAL`) : dialogue propre à l'import avant la première lecture et sur `ConsentRequired` ; refus = rien n'est lu ni envoyé. |
| Apprentissage | `record_category_correction` reçoit la clé marchand normalisée et un `p_label` vide (le RPC l'ignore) : le libellé ne quitte pas l'appareil pour l'apprentissage. |
| Droits | Suppression de compte : bouton et confirmation dans Mon espace, port `AccountRepository.deleteAccount` en `NotSupportedYet` (message explicite, rien n'est supprimé) tant que `DELETE /api/public/account` n'existe pas. Export des données : absent du web et du backend, non construit (voir `BACKEND-GAPS.md`). |
| Traceurs | Aucun SDK d'analyse, de publicité ou de suivi. Manifeste de confidentialité iOS (`PrivacyInfo.xcprivacy`) déclaré sans collecte tierce. |
| Code | R8 activé en release. Aucun secret dans le dépôt ni dans le binaire. |

Les points qui relèvent du backend (RLS, conservation, sous-traitants) sont dans `docs/BACKEND-GAPS.md`.

## 6. Parité Android / iOS

Toute l'interface et toute la logique sont partagées. Les seuls codes propres à une plateforme sont derrière `expect/actual`
ou des interfaces, et listés dans `docs/PLATFORM-PARITY.md` avec, pour chacun, l'écart visible éventuel pour l'utilisateur.

## 7. Tests et intégration continue

- Tests unitaires en `commonTest` (kotlin-test, coroutines-test, Turbine) : routeur IA, anonymiseur, cas d'usage, mappers, ViewModels.
- GitHub Actions : `spotlessCheck`, tests, assemblage Android (debug) sur Linux ; compilation du framework iOS sur macOS.
