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
- Commits atomiques par lot, messages en français.

## État
Rien n'a encore été compilé : ce dépôt a été amorcé dans un environnement sans accès à Maven ni au SDK Android.
`:ai:api` (5 fichiers) est donc à compiler en premier, dès que le lot 0 a posé le build.
