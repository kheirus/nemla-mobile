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

## Une conversation par lot (règle du propriétaire)
- Chaque lot, ou demi-lot (B5a, B5b…), se fait dans **une conversation neuve**, dans son worktree et sa branche. Le contexte utile est
  dans le dépôt (ce fichier, `docs/`), pas dans l'historique d'une conversation précédente.
- À la fin d'un lot, après les vérifications, les commits et la demande d'essai : **prévenir le propriétaire qu'il doit ouvrir une nouvelle
  conversation** pour le lot suivant, et lui donner la phrase de départ (par exemple « Lot B3 selon docs/BACKEND-PLAN.md »).
  Ne jamais enchaîner le lot suivant dans la même conversation, même si on le demande en passant : le rappeler d'abord.
- Garder le contexte petit : code écrit par des sous-agents, sorties de Gradle filtrées (`--quiet`, erreurs seulement),
  lecture des seules parties utiles des gros fichiers.

## État
Avancement, restes et prochaine étape : `docs/ROADMAP.md` (lots mobiles 0 à 6 terminés) et `docs/BACKEND-PLAN.md` (backend Kotlin, lots B0 à B12).
JDK 17 requis : `gradle/gradle-daemon-jvm.properties` l'impose au daemon.
