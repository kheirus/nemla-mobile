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

## Lot 1 — Fondations ☐
- `core:common`, `core:domain` (session, profil, catégories, quotas, consentements IA, ports IA métier), `core:network`, `core:security`, `core:data` (GoTrue, PostgREST, API nemla, passerelle IA).
- `ai:local` : `RuleBasedStatementAnonymizer`, porté des règles de `specs/01` §6.2, avec tests de parité.
- `ai:remote-nemla` : moteurs du tableau d'ARCHITECTURE §4. Tests de `AiRouter`.
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
