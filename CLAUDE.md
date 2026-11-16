# nemla-mobile — consignes pour Claude Code

Application Android + iOS de nemla (budget à saisie vocale), portage de l'application web nemla.app.
Kotlin Multiplatform, interface 100 % partagée en Compose Multiplatform.

## À lire avant d'écrire du code
1. `docs/ARCHITECTURE.md` — fait foi (modules, règles de dépendance, conventions, couche IA, sécurité).
2. `docs/ROADMAP.md` — lots de travail, dans l'ordre, avec leur état.
3. `docs/BACKEND-GAPS.md` et `docs/PLATFORM-PARITY.md`.
4. `docs/specs/` — spécifications extraites du code web (API, modèle de données, design, écrans, versions).
   Ce sont elles la référence fonctionnelle : le code web n'est pas dans ce dépôt.

## Répartition des modèles (règle du propriétaire)
- Le modèle le plus puissant (Fable 5.2) sert uniquement à l'architecture, aux contrats entre modules, aux arbitrages
  et aux revues de sécurité. Plafond : 30 % de la capacité hebdomadaire.
- Tout le reste du code est délégué à des sous-agents Opus 5.5, un lot à la fois, avec `docs/ARCHITECTURE.md`
  et la spec concernée dans leur consigne.

## Règles non négociables
- Versions : uniquement celles de `gradle/libs.versions.toml` (stables, vérifiées le 15/11/2026). Pas d'alpha, beta ni RC.
  Retirer du catalogue les entrées non utilisées (Navigation 3, Supabase SDK, SQLDelight, Room) : voir ARCHITECTURE §1.
- Réseau : Ktor seulement. Pas de SDK Supabase.
- `:ai:api` est déjà écrit et constitue le contrat IA. Ne pas en changer les signatures sans mettre à jour ARCHITECTURE §4.
  `@OptIn(PrivacyBoundary::class)` n'est autorisé que dans `:ai:local` et ses tests.
- Aucune donnée utilisateur dans les journaux. Aucun secret dans le dépôt. Aucun SDK d'analyse ou de suivi.
- Interface identique sur Android et iOS : tout écart, même mineur, est consigné dans `docs/PLATFORM-PARITY.md`
  et signalé au propriétaire.
- Chaque lot se termine par : `./gradlew spotlessApply`, tests du lot, `./gradlew :androidApp:assembleDebug`,
  et compilation du framework iOS (`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`).
- Commits atomiques (un par module ou par préoccupation, jamais un lot entier en un seul commit), messages en anglais.
- Pas de commentaires dans le code (ni KDoc ni commentaires de ligne) : le code se lit seul, le reste va dans `docs/`.

## Branches, tests et fusion (règle du propriétaire)
- Dès qu'un travail se fait sur une autre branche que `main` (worktree compris), le dire au propriétaire en nommant la branche,
  et rappeler qu'un build lancé depuis le dépôt principal ne contient pas ce travail.
- À la fin d'un travail sur une branche, demander au propriétaire de tester (appareil Android, simulateur iOS) en indiquant quoi essayer.
- Ne jamais fusionner dans `main` sans son accord explicite : lui demander à chaque fois s'il autorise la fusion.

## État
Lot 0 (socle de build) terminé le 16/11/2026 : wrapper Gradle 9.7.1, `build-logic` (plugins `nemla.kmp.library`, `nemla.compose`,
`nemla.feature`, `nemla.android.application`, `nemla.spotless`), tous les modules d'ARCHITECTURE §2 configurés, `androidApp`,
`iosApp`, Spotless + ktlint, CI GitHub Actions. JDK 17 requis : `gradle/gradle-daemon-jvm.properties` l'impose au daemon.

Lot 1 (fondations) terminé le 16/11/2026 : `core:*`, `ai:*`, `core:designsystem` (thème clair/sombre, jetons, composants),
`feature:auth` (connexion, inscription, mot de passe oublié), `feature:home` (coquille), `shared` (App, navigation pilotée par la
session, assemblage Koin), coquilles Android (`NemlaApplication`, R8 vérifié) et iOS (`startNemla` + `mainViewController`).
Restes et écarts : `docs/ROADMAP.md` (lot 1) et `docs/PLATFORM-PARITY.md`. Polices Outfit / DM Sans et logo à fournir
(fichier unique `NemlaFonts.kt`, monogramme provisoire dans `brand/NemlaLogo.kt`).

Lot 2 (dictée) terminé le 16/11/2026 : `core:audio` (WAV 16 kHz mono, silence, 30 s, `AudioRecord` / `AVAudioEngine`, permission micro),
`feature:voice` (bouton « Ajouter », dialogue de dictée, consentement IA `PERSONAL`, crédits, fiche de revue), confirmation des dépenses
en PostgREST direct (libellé non modifiable faute d'endpoint). Capture réelle jamais essayée sur appareil. Restes et écarts : `docs/ROADMAP.md`
(lot 2) et `docs/PLATFORM-PARITY.md`. Prochaine étape : lot 3.
