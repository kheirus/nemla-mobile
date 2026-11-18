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

## Lot 1 — Fondations ☑
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
- ☑ `core:designsystem` (16/11/2026) : `NemlaTheme` (clair / sombre selon le système) sur Material 3, couleurs de `specs/03` §2 et §13,
  jetons hors Material par `CompositionLocal` (`NemlaTheme.colors`, `.typography`, `.spacing`, `.radius`, `.sizes`), formes, échelle
  typographique ; boutons (primaire, secondaire, contour, fantôme, destructif, texte ; état de chargement), champ de texte (libellé,
  erreur, mot de passe avec bascule, actions clavier, indices d'autoremplissage), liste de choix, carte `card-soft`, bannière,
  état vide, échafaudage d'écran (insets, défilement, clavier), icônes Lucide redessinées en `ImageVector`, aperçus `@Preview`.
  Polices : `NemlaFonts.kt` renvoie `FontFamily.Default` (aucun fichier téléchargé) ; logo : monogramme provisoire.
- ☑ `feature:auth` (16/11/2026) : connexion, inscription (email, mot de passe, pseudo, âge, sexe, situation familiale, encart
  confidentialité), mot de passe oublié ; `Route` / `Screen` / `UiState` / `Action` / `Effect` / ViewModel ; erreurs traduites
  (jamais de texte serveur) ; graphe `authGraph` à routes typées, `featureAuthModule` ; 29 tests (×2 cibles).
- ☑ `feature:home` (16/11/2026) : coquille (salutation par pseudo après `bootstrap_user`, état vide sans données simulées,
  déconnexion), `featureHomeModule`, 8 tests (×2 cibles).
- ☑ `shared` (16/11/2026) : `App()` (démarrage neutre tant que la session est `Unknown`, graphe auth ou accueil ensuite, bascule
  automatique à l'invalidation), écran « configuration manquante » si `BackendConfig` est incomplet, `initKoin`, 5 tests.
- ☑ Coquilles (16/11/2026) : Android `NemlaApplication` (Koin, `AndroidScreenProtection.install`), `MainActivity` edge-to-edge,
  fond de fenêtre clair / sombre ; R8 vérifié (APK release signé en local avec la clé de debug, parcours inscription → connexion →
  mot de passe oublié sans plantage, `FLAG_SECURE` actif) : aucune règle supplémentaire nécessaire. iOS : lecture de
  `Info.plist`, `startNemla` (Koin, voile `IosScreenProtection`), `mainViewController()` plein écran ; `xcodebuild` simulateur arm64 OK.
- Navigation multiplateforme montée en 2.10.0 (stable).
- Reste à faire (reporté) :
  - fichiers `.ttf` Outfit 400/500/600/700 et DM Sans 400/500/700 dans `core/designsystem/src/commonMain/composeResources/font`,
    puis `NemlaFonts.kt` ; logo `nemla-logo.png` (ou SVG) à la place du monogramme ;
  - écran « Nouveau mot de passe » (`/reset-password`) : nécessite App Links / Universal Links ;
  - pseudos disponibles (puces) et remplacement d'un pseudo déjà pris après inscription (`suggest_pseudo`) ;
  - onboarding après inscription (lot 4) ; aujourd'hui l'inscription avec session ouvre l'accueil ;
  - lancement sur simulateur iOS non fait (aucun simulateur démarré) ; affichage vérifié sur émulateur Android uniquement.

## Lot 2 — Dictée ☑
`core:audio` (WAV mono 16 kHz 16 bits, 30 s, arrêt après 3 s de silence), `feature:voice` (bouton, enregistrement, fiche de revue), consentement IA.
Dépend des endpoints dépenses du backend.
- ☑ `core:audio` (16/11/2026) : encodeur WAV, décimation au plus proche, détecteur de silence (RMS < 0,01 par bloc de 4096, 3 s),
  durée max 30 s, rejet < 4096 octets, `VoiceRecorder` (`StateFlow`), Android `AudioRecord` (16 kHz ou natif + décimation),
  iOS `AVAudioEngine`, `MicrophonePermission` + `rememberMicrophonePermissionRequest`, audio en mémoire seulement, `coreAudioModule`. 25 tests.
- ☑ `core:domain` : `ExpenseReviewRepository`, `MerchantKeys` (portage de `normalizeMerchantKey`), `AmountInput` (texte → centimes),
  cas d'usage de dictée, quota, catégories, règles apprises, suppression et confirmation. 17 nouveaux tests.
- ☑ `core:data` : `KtorExpenseReviewRepository` en PostgREST direct (`PATCH expenses` montant / date / catégorie / `confirmed`,
  `DELETE`, `PATCH voice_entries statut=confirmed`, lecture `merchant_rules`, RPC `record_category_correction` sans libellé) ;
  `logCorrection` en `NotSupportedYet`. 11 nouveaux tests.
- ☑ `core:designsystem` : icônes de dictée et de revue, couleurs de crédits, `NemlaDialog`, `NemlaProgressBar`.
- ☑ `feature:voice` : bouton « Ajouter » et menu (dictée avec crédits restants ; saisie manuelle et import désactivés), dialogue de dictée
  (§6.2), consentement IA `PERSONAL` avant la première dictée et sur `ConsentRequired`, erreurs traduites, fiche de revue (`voiceGraph`)
  alimentée par la réponse de `voice/process`. 29 tests (×2 cibles).
- ☑ Branchement : emplacement `floatingAction` dans `feature:home`, `shared` (Koin + navigation), `RECORD_AUDIO` (Android),
  `NSMicrophoneUsageDescription` (iOS).
- Vérifié le 16/11/2026 : `spotlessApply`, tests des modules du lot, `:androidApp:assembleDebug`, `:shared:linkDebugFrameworkIosSimulatorArm64`,
  `xcodebuild` simulateur arm64.
- Reste à faire (reporté) :
  - essai réel de la capture sur appareil ou émulateur et sur simulateur iOS (permissions, silence, interruptions, envoi à `voice/process`) : non fait ;
  - libellé modifiable, `prettyLabel`, détection de doublons, carte « Dépenses à valider » persistante : dépendent de `GET` / `PUT /api/public/expenses` ;
  - journal de correction : dépend de `POST /api/public/classification-log` ;
  - langue de dictée et révocation du consentement dans Mon espace (lot 4) ;
  - à trancher par le propriétaire : `PrivacyInfo.xcprivacy` (données collectées encore vides alors que l'audio part au serveur),
    lien vers les réglages après un refus définitif du micro, traduction anglaise de `NSMicrophoneUsageDescription`.

## Lot 3 — Tableau de bord ☑
Météo du budget, graphique mensuel, « Où part l'argent », dépenses du jour, recherche. Débit différé (`specs/02`).
- ☑ `core:domain` (16/11/2026) : dépenses sans libellé, charges fixes, `PeriodCursor`, débit différé (`specs/02` §8.1), formatage
  monétaire et dates fr/en, ports de lecture / édition / recherche / matérialisation, cas d'usage. 24 nouveaux tests.
- ☑ `core:data` : `KtorExpenseRepository` (lecture PostgREST sans libellé, `PATCH` montant / date / catégorie / moyen de paiement,
  `DELETE`), charges fixes, profil de débit. 10 nouveaux tests.
- ☑ `core:designsystem` : icônes, couleurs météo, couleurs de catégorie, contrôle segmenté, pastille de catégorie.
- ☑ `feature:dashboard` (remplace `feature:home`, supprimé) : en-tête, « Dépenses à valider » (rouvre la fiche de revue), alertes de budget,
  recherche par date, bloc pilote (période, montant, météo, graphique `Canvas`, filtre, moyenne), « Où part l'argent » (anneau) et budgets,
  dépenses du jour paginées avec débit différé, édition réduite, tirer pour rafraîchir, erreurs avec « Réessayer ». 41 tests.
- ☑ `feature:voice` : la fiche de revue s'ouvre aussi depuis les dépenses en attente.
- Vérifié le 16/11/2026 : `spotlessApply`, tests des modules touchés, `:androidApp:assembleDebug`, `:shared:linkDebugFrameworkIosSimulatorArm64`,
  `xcodebuild` ; affichage avec de vraies données sur simulateur iOS (troncature « Mois | Année » corrigée).
- Reste à faire (reporté) :
  - libellés des dépenses et recherche texte : dépendent de `GET /api/public/expenses` (`NotSupportedYet`) ;
  - matérialisation des charges fixes : dépend de `POST /api/public/recurring/materialize` (`NotSupportedYet`) ;
  - lot 4 : `PaymentModeGate`, réglages du débit, lien vers les budgets, « Gérer mon compte », création manuelle et type Fixe ;
  - astuces : non faites ; bandeau de bilan : lot 6 ; notifications et rappel quotidien : lot 7 ;
  - à trancher : incohérence de la météo du web (mois sans filtre, année avec filtre), graduations de l'axe Y, formatage monétaire
    à comparer au navigateur ;
  - essai complet sur Android (édition, suppression, revue en attente) : non fait.

## Lot 4 — Mon espace et onboarding ☑
Budget, charges fixes, profil, préférences, catégories, crédits, suppression de compte, export.
- ☑ `core:domain` (16/11/2026) : préférences (`AppPreferences`, `ThemeMode`), banques et réglage du débit (`Banks`, `DebitSettings`),
  règles de budget (`BudgetRules`, §8.6), palette et suggestions de catégories (`CategoryPalette.pickUnusedColor`, `hslToHex`,
  `CategoryNames`, §8.3 et §8.5), ports `SpeechTranscriber`, `PreferencesRepository`, `DeviceLanguage`, ajouts aux ports profil (devise,
  fin d'onboarding), charges fixes (insertion en lot, suppression) et débit différé (lecture complète, enregistrement + `apply_debit_rule`),
  cas d'usage d'aiguillage, d'onboarding et de Mon espace. 12 nouveaux tests.
- ☑ `core:data` : `RoutedSpeechTranscriber` (`SpeechToText` → `/transcribe`), `SecureStorePreferencesRepository`, PostgREST : insertion en
  lot et suppression de `recurring_expenses`, upsert `{monthly_budget, onboarding_done}`, `devise`, débit différé + RPC. 6 nouveaux tests.
- ☑ `core:designsystem` : en-tête applicatif partagé (`NemlaAppHeader`, `NemlaMenuItem`), zone de texte, interrupteur, carte de choix,
  bouton icône, puce, entrée de liste, icônes (portefeuille, répétition, réglages, lune, baguette, situation familiale…), libellé masquable
  des champs et listes, pastille dans les listes, libellés sur deux lignes du contrôle segmenté.
- ☑ `feature:voice` : `DictationTextButton` (même enregistreur, consentement `PERSONAL`, transcription), dialogue de consentement commun,
  langue de dictée lue dans les préférences. 5 nouveaux tests.
- ☑ `feature:onboarding` : « Vos charges fixes » (§4) avec analyse, dictée, revue, budget et enregistrement, lien « Passer pour
  l'instant » (sans écriture). 10 tests.
- ☑ Correctif (16/11/2026, constaté sur simulateur iOS) : un compte web existant avec `onboarding_done = false` restait bloqué sur
  l'onboarding. L'aiguillage n'envoie plus vers l'onboarding que les comptes vierges (budget 0, aucune charge fixe, aucune dépense) ;
  toute erreur mène au tableau de bord. 3 tests de domaine et 1 test de données ajoutés.
- ☑ `feature:settings` : Mon espace (§12) : onglets, cinq panneaux de « Profil & budget » (budget, charges fixes, profil, préférences,
  carte bancaire §11.3), zone dangereuse, crédits, catégories ; « Bilans » en attente. 14 tests.
- ☑ `feature:dashboard` : « Gérer mon compte », lien « Ajuster mes budgets par catégorie », avatar selon la situation familiale, langue
  relue à chaque rechargement. 2 nouveaux tests.
- ☑ `shared` : aiguillage onboarding / tableau de bord (`AppViewModel`), destinations onboarding et Mon espace, emplacement de dictée,
  thème et langue appliqués depuis les préférences. 2 nouveaux tests.
- Vérifié le 16/11/2026 : `spotlessApply`, `allTests` des modules touchés (JVM et simulateur iOS), `:androidApp:assembleDebug`,
  `:shared:linkDebugFrameworkIosSimulatorArm64`.
- Reste à faire (reporté) :
  - essai sur appareil Android et simulateur iOS : aiguillage, changement de langue à chaud (recomposition avec le même `NavController`),
    thème, dictée des charges fixes, retour système depuis un panneau : non fait ;
  - dialogue `PaymentModeGate` du tableau de bord (§11.2) : le formulaire de débit vit dans `feature:settings` et une feature ne dépend
    pas d'une autre ; à trancher (formulaire dans `core:designsystem` sous forme générique, ou emplacement rempli par `shared`) ;
  - suppression du compte : dépend de `DELETE /api/public/account` (port en `NotSupportedYet`, message affiché) ;
  - export des données : reporté, absent du web et du backend (endpoint à créer, `BACKEND-GAPS.md`) ;
  - rappel quotidien (Préférences) : lot 7 avec les notifications ;
  - onglet « Bilans » : historique fait au lot 6 ;
  - « Créez votre catégorie dans Mon espace » depuis la fiche de revue, création manuelle de dépense et type Fixe : non faits.

## Lot 5 — Import de relevés ☑
Extraction du texte sur l'appareil (PDF, CSV), anonymisation locale, envoi du texte anonymisé. Dépend d'un nouvel endpoint.
- Règle : aucun fichier brut ne quitte l'appareil ; `/import/file` n'est jamais appelé, même en repli.
- ☑ `ai:local` (17/11/2026) : la règle d'adresse s'arrête avant un montant (le [RISQUE] serveur qui détruisait « 12,50 » n'est pas
  reproduit) ; téléphone français sans `+33` masqué. Revue des règles de `specs/01` §6.2 : toutes présentes, plus l'IBAN compact.
  2 nouveaux tests ; le cas de parité « address » est sorti de la comparaison stricte. Reproduits et documentés (pas de correction
  simple et sûre) : ligne « CARREFOUR MARKET » seule masquée en `[NOM]`, mots de l'en-tête masqués partout.
- ☑ `core:domain` : `StatementText` (texte + `StatementFormat` PDF / CSV / TEXT) remplace `StatementFile` (octets) ; `StatementImporter`
  reçoit le texte et signale le nombre de masquages ; cas d'usage `ImportStatement` (quota d'import vérifié d'abord) et
  `ImportedReviews` ; `PendingReview.announce`. 4 tests.
- ☑ `ai:remote-nemla` : `NemlaStatementParsingEngine` sur `POST /api/public/import/text` (proposé) : JSON `{text, lang, today}`, sonde `{}`
  dans `availability()` (404/405 → indisponible avant tout envoi), 404/405 pendant l'appel → `not_supported`, erreurs 413/422/429/400
  traduites. 6 tests (MockEngine : corps = texte anonymisé uniquement).
- ☑ `core:data` : `RoutedStatementImporter` sur le texte (longueur minimale, anonymisation locale obligatoire et vérifiée `ON_DEVICE`,
  rappel du nombre de masquages, parsing distant) ; `not_supported` → `AppError.NotSupportedYet` ; extracteur d'octets supprimé.
  4 nouveaux tests.
- ☑ `core:documents` (nouveau) : `rememberDocumentPicker`, `DocumentTextReader` ; lecture en mémoire, 8 Mo vérifiés avant lecture,
  UTF-8 non strict sans BOM, PDF (PdfRenderer API 35 / extension S 13, PDFKit), `cleanupText`, 90 000 caractères, seuil de 20 caractères,
  tableurs refusés (« exportez en CSV »), octets remis à zéro. 14 tests.
- ☑ `feature:voice` : entrée « Importer un relevé » du menu « Ajouter » active (imports restants, « Crédit épuisé ce mois ») ;
  bannière « {n} dépenses lues… » dans la fiche de revue. 3 nouveaux tests.
- ☑ `feature:import` (nouveau, paquet `app.nemla.feature.statementimport`) : dialogue §9 adapté, consentement `ANONYMIZED`, quota,
  nombre de masquages, erreurs fr/en, fermeture impossible pendant le traitement. 16 tests.
- ☑ `shared` : modules Koin, dialogue ouvert depuis le menu « Ajouter », fiche de revue ouverte avec les dépenses importées.
- ☑ Correctif (17/11/2026, constaté sur simulateur iOS) : avec `Accept: application/json`, `nemla.app` répond à la route inconnue
  `500 {"error":"Only HTML requests are supported here"}` et non 404 ; la sonde laissait donc partir le texte anonymisé (refusé par le
  serveur, « Import impossible » affiché). La sonde est désormais fermée par défaut (texte envoyé seulement sur une réponse JSON
  400/401/413/422/429). 5 tests MockEngine ajoutés (404 HTML sur la sonde et sur l'appel, erreur de route, 5xx, hors ligne), 1 test de données.
- Vérifié le 17/11/2026 : `spotlessApply`, `allTests` des modules touchés (JVM et simulateur iOS), `:androidApp:assembleDebug`,
  `:shared:linkDebugFrameworkIosSimulatorArm64`.
- Reste à faire (reporté) :
  - endpoint `POST /api/public/import/text` côté backend (`BACKEND-GAPS.md`) : tant qu'il manque, l'import s'arrête après
    l'anonymisation locale avec le message « arrive bientôt… Rien n'a été envoyé. » ;
  - Excel (`.xls`, `.xlsx`, `.xlsm`, `.ods`) : aucune bibliothèque au catalogue ; message « exportez en CSV » ;
  - PDF sur Android 9 à 11 (et 12 à 14 sans l'extension SDK 13) : non lisible, message dédié ;
  - pas d'OCR (PDF scanné refusé, comme le web) ;
  - champ `kind` du contrat : non envoyé, `StatementParsingRequest` de `:ai:api` ne porte pas le format (à arbitrer : ajouter un champ
    `format` à la requête, avec mise à jour d'ARCHITECTURE §4) ;
  - essais sur appareil Android (sélecteur, PDF selon la version, Drive) et simulateur iOS (sélecteur, iCloud, PDFKit) : non faits ;
  - partage « Ouvrir avec nemla » depuis une autre application : non construit.

## Lot 6 — Bilans et préparation de la publication ☑
Bilans (calcul serveur, bandeau du tableau de bord, historique dans Mon espace), dossier de publication et brouillon de politique de
confidentialité. Les notifications sont sorties du lot (décision du propriétaire) : voir lot 7.
- ☑ `ai:remote-nemla` (17/11/2026) : `NemlaPeriodReportEngine` (déjà présent depuis le lot 1) refuse une réponse dont la période ou le type
  diffère de la demande, ignore les lignes vides de `highlights` / `advice`. 4 nouveaux tests (forme complète de `specs/01` §2.6,
  nombres en chaîne, période différente, champs obligatoires, page HTML).
- ☑ `core:domain` : `ReportCalendar` (bandeau : jour > dernier jour − 3, plus l'année en décembre ; candidats M−1…M−24 et A−1…A−3 ;
  fenêtre de lecture ; regroupement par année décroissante, bilan annuel en tête ; clés `AAAA-MM` / `AAAA`), `ReportSheet` (bilan + devise
  du profil + écart), port `ReportRepository`, cas d'usage `LoadPeriodReport`, `FindArrivedReports`, `MarkReportViewed`,
  `ListReportHistory`. `PeriodReviewer` devient `fun interface`. 10 tests (mois de 30 et 31 jours, février, février bissextile, décembre).
- ☑ `core:data` : `KtorReportRepository` en PostgREST direct : lecture de `report_views.mois`, insertion `on_conflict=user_id,mois` avec
  `resolution=ignore-duplicates` (un 409 vaut succès), mois ayant une dépense confirmée lus par dates seules (`select=date`), en sautant au
  mois précédent dès qu'une page de 1 000 est pleine. 6 tests.
- ☑ `core:designsystem` : icônes `BellRing`, `TrendingUp`, `TrendingDown`, `History`.
- ☑ `feature:reports` (nouveau) : `ReportDialog` (§10.1 : « Calcul du bilan… », erreur + « Réessayer », total, écart avec icône de tendance,
  « Où part l'argent », points clés et pistes du serveur, « J'ai compris »), `ReportArrivalNotice` (§5.10 : un bandeau par période,
  enregistre la vue puis ouvre le bilan), `ReportHistory` (§10.2). Chaînes fr/en. 9 tests de ViewModel.
- ☑ `feature:dashboard` : emplacement `reportNotice` (après les alertes de budget). `feature:settings` : emplacement `reportHistory`
  pour l'onglet « Bilans » (l'état « bientôt disponibles » du lot 4 est supprimé).
- ☑ `shared` : `featureReportsModule`, emplacements remplis.
- ☑ Documents : `docs/PUBLICATION.md` (liste de contrôle Google Play / App Store, bloquants, fiches fr/en), `docs/PRIVACY-POLICY-DRAFT.md`
  (brouillon à faire relire par un juriste).
- Vérifié le 17/11/2026 : `spotlessApply`, `allTests` (JVM et simulateur iOS), `:androidApp:assembleDebug`,
  `:shared:linkDebugFrameworkIosSimulatorArm64`.
- Reste à faire (reporté) :
  - textes des points clés et des pistes : français seulement, montants en « € » arrondis quelle que soit la devise (`BACKEND-GAPS.md`) ;
    affichés tels quels, avec une mention en anglais « These notes are written in French for now. » ;
  - essais sur appareil Android et simulateur iOS (bandeau les 3 derniers jours, historique, dialogue, devise autre que l'euro) : non faits ;
  - bandeau : calculé à l'affichage du tableau de bord (pas de rafraîchissement à minuit si l'application reste ouverte) ;
  - historique : la liste suit le défilement de Mon espace (pas de zone de 420 px à en-têtes collants, voir `PLATFORM-PARITY.md`) ;
  - astuces du tableau de bord (§5.9) : non faites ;
  - publication : bloquants listés dans `PUBLICATION.md` (suppression de compte, URL de politique de confidentialité, liens de
    réinitialisation, sécurité backend), signature et numéros de version à mettre en place.

## Lot 6b — Interface mobile ☐ (avant le lot 7)
Rendre l'application « mobile d'abord » : composants Material 3 natifs avant tout composant maison, gestes et animations.
Rapport et découpage détaillé : `UI-REVIEW.md`.
1. Fondations : mode sombre plus contrasté, barre d'application, bouton retour propre à chaque plateforme (flèche Android, chevron iOS),
   panneaux du bas, onglets, snackbars, retour prédictif Android et geste de bord iOS, transitions entre écrans.
2. Structure : avatar et panneau « Compte » à la place du menu déroulant « Kheireddine ▾ » (arrêté le 18/11/2026, pas de barre de navigation en bas).
3. Tableau de bord : boutons segmentés Mois / Année, glissement entre les mois, bouton « Ajouter » réductible.
4. Mon espace : onglets défilants avec glissement et indicateur animé, panneaux en vraies destinations.
5. Fenêtres en panneaux du bas, confirmations en snackbars.
6. Animations légères (montants, anneau, barres, jauges, listes).
7. Identité : logo nemla, icônes d'application Android et iOS, écran de lancement, polices Outfit / DM Sans (fichiers à fournir).

État (17/11/2026, branche `claude/lot-6b-mobile-ui`) :
- ☑ Étape 1. Palette sombre de `UI-REVIEW.md` §5 (fond `#0F1712`, cartes `#18231C`, pistes `#223027`, bordures blanc 16 % / 22 %,
  `mutedForeground` `#AEBDB1`, jeton `selectedContainer` plus clair que la piste) et `ColorScheme` Material 3 aligné (paliers
  `surfaceContainer*`, `secondaryContainer`, `inverse*`). Enveloppes `core:designsystem` : `NemlaTopBar` (défilement épinglé),
  `NemlaBackButton` (`expect/actual`), `NemlaSheet` + `NemlaListItem`, `NemlaTabs` (pas encore utilisé, étape 4), `NemlaSnackbarHost` /
  `NemlaSnackbarEffect`, `NemlaScaffold` (sur lequel repose `NemlaScreen`), `NemlaAccountButton` / `NemlaAccountSheet`.
  `Scaffold` racine dans `shared`, transitions du `NavHost` (glissement d'un cinquième de largeur + fondu, 300 ms ; fondu seul depuis
  le démarrage), retour prédictif Android (`enableOnBackInvokedCallback`), geste de bord iOS (natif dans CMP 1.12).
  Mon espace : barre d'application et bouton retour natif à la place de « ← Retour ». Avis du tableau de bord en snackbar.
- ☑ Étape 2. Structure arrêtée par le propriétaire le 18/11/2026 : avatar + panneau « Compte ». Avatar à initiales + panneau « Compte »
  (pseudo, e-mail, Mon espace, Se déconnecter) sur le tableau de bord et l'onboarding ; « Se déconnecter » aussi dans Mon espace › Profil ;
  Bilans reste un onglet de Mon espace. La variante barre de navigation en bas (Accueil / Bilans / Mon espace), essayée derrière une
  constante, a été retirée avec `NemlaNavigationBar`.
- ☑ Étape 3. Tableau de bord : Mois / Année en `NemlaSegmentedButtons` (`SingleChoiceSegmentedButtonRow`, coche et
  `secondaryContainer` sur l'option choisie). Bloc pilote en `HorizontalPager` (une page par période, de janvier 2000 à la
  période du jour, `PeriodPages`) : glisser ou les chevrons (`IconButton`, désactivés aux bornes) changent la période, le libellé
  glisse dans le sens du changement (`AnimatedContent`). Le graphique reste hors du pager (pas de conflit avec le toucher des barres).
  **Écart web** (`specs/04` §5.3, `specs/02` : « aucune borne ») : navigation bornée à la période du jour, un mois futur touché dans le
  graphique annuel ne fait rien. « Ajouter » : `ExtendedFloatingActionButton` réduit à « + » quand on descend (revu après captures, voir plus bas) ;
  menu en `NemlaSheet` (Dicter, Dépenses à valider, Importer, Saisie manuelle « bientôt »). Recherche : `NemlaSearchField`
  (`OutlinedTextField`, icône loupe, bouton calendrier, date choisie en `InputChip`) ; « La recherche par mot-clé arrivera bientôt »
  seulement pendant la saisie (texte non envoyé). `NemlaPullToRefreshBox` aux couleurs Nemla. Sombre : barres et puce « Dépensé » à
  `primary` 72 %, mois choisi plein (clair inchangé). Animations : total qui compte (600 ms), anneau qui se dessine (900 ms), barres
  qui montent (décalage 30 ms), jauges `NemlaProgressBar` qui se remplissent (toutes les jauges de l'app).
- ☑ Étape 4. Mon espace : `NemlaTabs` (`SecondaryScrollableTabRow`, chaque onglet à la largeur de son texte, libellé « Profil &
  budget » gardé) synchronisé avec un `HorizontalPager` : l'indicateur suit le doigt (`tabIndicatorLayout` sur
  `currentPage + currentPageOffsetFraction`), toucher un onglet anime le pager. Chaque page défile seule. Budget, charges fixes,
  profil, préférences et carte bancaire sont des destinations typées (`SettingsBudgetDestination`…) avec `NemlaTopBar` et
  `NemlaBackButton` : retour système, retour prédictif et geste de bord iOS natifs. « Budgets par catégorie » revient à Mon espace
  et ouvre l'onglet Catégories (`savedStateHandle`), le lien d'alerte du tableau de bord ouvre toujours Catégories. Entrées en
  `NemlaListItem` séparés dans une seule carte. Répartition / Budgets (tableau de bord) et Mensuel / Annuel (budget) en
  `NemlaSegmentedButtons` ; `NemlaSegmentedControl` et `NemlaEntryButton` supprimés. Confirmation de suppression du compte en
  `NemlaAlertDialog` (`AlertDialog` natif). Profil rechargé au retour d'un panneau (`ON_RESUME`).
- ☑ Étape 5. `NemlaDialog` supprimé : édition de dépense, dictée, import de relevé et bilan en `NemlaFormSheet` (`ModalBottomSheet`,
  glisser vers le bas pour fermer, bloqué pendant un enregistrement ou une lecture ; bilan déplié d'office, défilement interne).
  Consentements IA, suppression de dépense et de compte en `NemlaAlertDialog`. Avis de réussite et d'information (« Profil
  enregistré », « Catégorie supprimée », avis du bouton « Ajouter », « Rien détecté » de l'onboarding, messages du compte) en
  snackbar ; les erreurs restent en bandeau. Pas d'« Annuler » : les suppressions partent tout de suite au serveur, sans
  restauration possible à bas coût. Champs en `OutlinedTextField` (`NemlaTextField`, `NemlaTextArea`), listes en
  `ExposedDropdownMenuBox` (`NemlaSelectField`), dates en champ en lecture seule (`NemlaPickerField`) qui ouvre `DatePickerDialog`.
  Connexion / inscription : actions clavier, types de clavier et `ContentType` (e-mail, mot de passe, nouveau mot de passe) déjà
  en place ; onboarding : « Suivant » / « Terminé ». Roue de la dictée en `CircularProgressIndicator`.
- ☑ Étape 6 (fin). Fiche de revue en `LazyColumn` : une ligne supprimée glisse et les autres remontent (`Modifier.animateItem`),
  retour haptique sur suppression et validation (aussi à la suppression d'une dépense du tableau de bord). Dépenses du jour et
  dépenses à valider du tableau de bord : `NemlaAnimatedColumn` (`AnimatedVisibility` par ligne, liste non paresseuse). Bandeau
  « Votre bilan… est arrivé » entre par `expandVertically`. Animations de l'étape 3 inchangées, sans doublon.
- ☑ Finitions après captures : contenu des écrans à défilement (tableau de bord, Mon espace, panneaux, `NemlaScreen`) dessiné sous la
  barre système du bas (`edgeToEdgeBody` + `bottomSafeDrawingPadding` : la marge basse est dans le contenu, le clavier reste géré
  par `imePadding`). « Ajouter » déplié à l'arrivée, réduit à « + » au premier défilement ou après 2,5 s, sans se redéplier ; marge
  basse du tableau de bord = 56 + 16 dp + barre système. `NemlaListItem` en `Row` centrée verticalement (fin du mode trois lignes
  de `ListItem` : chevron en haut et vide sous le texte). `NemlaCard` toujours opaque (fond translucide composé sur `background`) :
  l'ombre ne transparaît plus dans « Zone dangereuse ».
- **Écart à la demande** : la fiche de revue reste un écran (destination `VoiceReviewDestination`, ouverte depuis plusieurs
  endroits) et non un panneau du bas ; elle gagne une barre d'application (retour = « Plus tard ») et des boutons en bas d'écran.
- Restes : pages voisines du pager du tableau de bord affichées « – » pendant le glissement ; étape 7 (identité).

## Lot 6c — Retours du propriétaire après essai Android ☐ (branche `claude/lot-6c-polish`)
Retours du 18/11/2026 :
- ☑ Mois / Année sans coche (le « / » vu sur Android était la coche animée de `SegmentedButton`).
- ☑ Le titre de la période glisse avec le montant : un seul `HorizontalPager` porte titre et résumé, les chevrons sont posés par-dessus.
  La période change dès que le pager vise une nouvelle page (`targetPage`), et les pages déjà vues gardent leur résumé
  pendant le glissement : plus de « – » ni de clignotement.
- ☑ Animations seulement à l'arrivée et au passage Mois ↔ Année : total (`animatedCents` avec clé), anneau (balayage tenu par la
  carte), barres (clé de mode, plus d'animation à chaque changement d'année).
- ☑ Filtre des catégories : plus d'effet gris au toucher. Le titre « Filtrer les catégories » s'affiche sur l'émulateur Android
  (Pixel 7, API 34) : à revérifier sur l'appareil du propriétaire.
- ☑ Bouton du compte : icône de personne (selon le foyer) dans un cercle cerclé, à la place de l'initiale.
- ☑ Rotation : seul le tableau de bord tourne (Android `requestedOrientation`, iOS `supportedInterfaceOrientationsFor` piloté par
  `NemlaOrientationLock`) ; en paysage, le graphique mensuel occupe tout l'écran, sans autre action possible. Les panneaux ouverts
  restent affichés.
- ☑ Écran d'accueil vert (logo inversé, « Dictez vos dépenses. », « Commencer » → inscription, « J'ai déjà un compte » → connexion),
  montré tant qu'aucune session n'a existé ; icônes de la barre d'état claires sur ce fond.
- ☑ Bouton du compte : ouvre directement Mon espace (plus de panneau « Compte ») ; « Se déconnecter » reste dans Profil & budget.
- ☑ Libellé « Dépense » retiré : sans libellé (endpoint absent, `BACKEND-GAPS.md`), une ligne affiche sa catégorie et la façon dont elle
  a été saisie (« Dictée », « Saisie ») ; le groupe vocal à valider devient « Dictée à vérifier ».
- ☑ Mise en page adaptative : graphique (55 % de la largeur, 180 à 420 dp) et anneau (55 %, 168 à 320 dp) proportionnels, champs
  de montant et de fréquence en poids ; tableau de bord sur deux colonnes à partir de 840 dp de large. Le graphique plein écran est
  réservé au paysage d'un téléphone (hauteur < 480 dp) ; une tablette en paysage garde le tableau de bord.
- ☑ Paysage interactif : période et total en tête, filtre des catégories, toucher une barre ouvre le mois (vérifié sur iPhone 15).
- Reste : icône d'application (logo `nemla-512.png` du web à récupérer, voir `specs/03` §11).

## Lot 7 — Notifications ☐ (à concevoir)
Choix à faire par le propriétaire : push serveur (FCM pour Android et iOS, ou APNs direct) ou Firebase, notifications locales planifiées.
- Push « Votre bilan est arrivé » (fin de mois, fin d'année), qui ouvrirait le dialogue de bilan de `feature:reports`.
- Rappel quotidien de 20 h (« Bilan de la journée… », `profiles.daily_reminder`) et son interrupteur dans Préférences.
- Alertes de budget de catégorie (« Budget atteint »).
- Bannières in-app de `user_notifications` (`specs/04` §5.11).
- Côté backend : table de jetons d'appareil, émetteur planifié (`BACKEND-GAPS.md`, ligne Notifications).
- Aucun code de notification, permission, FCM / APNs ni rappel local n'existe dans l'application à ce jour.

## Lot 8 — Accessibilité ☐ (plus tard)
Mise en conformité de toute l'application (référentiel visé à arbitrer : WCAG 2.2 niveau AA, base du RGAA et de l'EAA) :
- libellés TalkBack / VoiceOver sur tous les éléments interactifs et graphiques (graphique, anneau, jauges, météo) ;
- tailles de texte système jusqu'à 200 % sans troncature, cibles tactiles de 48 dp minimum ;
- contrastes ≥ 4,5:1 (texte) et 3:1 (éléments graphiques) en clair et en sombre ; information jamais portée par la seule couleur ;
- ordre de lecture et focus logiques, titres de section annoncés ;
- respect du réglage « réduire les animations » ;
- vérification avec Accessibility Scanner (Android) et Accessibility Inspector (iOS), et tests Compose sur la sémantique.

## Lot 9 — Sécurité du compte ☐ (à concevoir)
Demande du propriétaire du 18/11/2026.
- Double authentification : TOTP (Supabase Auth MFA, facteurs `totp`), écran d'inscription du facteur (QR code et code manuel),
  défi à la connexion, codes de secours ; à arbitrer : SMS ou non.
- Verrouillage de l'app par biométrie (BiometricPrompt, LocalAuthentication) et délai de reverrouillage.
- Gestion des sessions : liste des appareils connectés, déconnexion à distance, « se déconnecter partout ».
- Changement d'e-mail et de mot de passe depuis Mon espace, alertes de connexion depuis un nouvel appareil.
- Côté backend : MFA activé dans Supabase, endpoints de sessions (`BACKEND-GAPS.md` à compléter).

## Lot 10 — Encouragements ☐ (à concevoir)
Demande du propriétaire du 18/11/2026 : donner envie de revenir dicter.
- Carte du tableau de bord à l'arrivée : « Vous avez dépensé 120 € de moins que le mois dernier à la même date », comparaison
  calculée à date égale pour rester juste en début de mois.
- Séries : jours consécutifs avec au moins une dépense dictée, petit badge.
- Petites victoires : budget tenu, catégorie en baisse, premier mois complet.
- Ton positif sans culpabiliser quand le mois est moins bon ; masquable dans Préférences.
- Calcul local à partir des montants déjà lus (aucun nouvel endpoint pour la comparaison simple).

## Plus tard
Transcription locale (whisper.cpp), modèle local (LiteRT-LM), cache chiffré hors ligne, autre fournisseur d'IA.
