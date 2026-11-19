# nemla-mobile — consignes pour Claude Code

Application Android + iOS de nemla, portage de l'application web (`../nemla-web`).
Kotlin Multiplatform, interface 100 % partagée en Compose Multiplatform.

Les consignes communes aux deux projets (modèles, une conversation par lot, branches et fusion, secrets) sont dans `../CLAUDE.md`.
Dans les documents, `../docs/…` désigne le dossier de documents commun, à la racine du dossier `Nemla`.

## À lire avant d'écrire du code
1. `docs/ARCHITECTURE.md` — fait foi (modules, règles de dépendance, conventions, couche IA, sécurité).
2. `docs/ROADMAP.md` — lots de travail mobiles, dans l'ordre, avec leur état ; `../docs/LOVABLE-EXIT-PLAN.md` pour les lots M7 à M11.
3. `../docs/BACKEND-GAPS.md` et `docs/PLATFORM-PARITY.md`.
4. `../docs/specs/` — spécifications extraites du code web (API, modèle de données, design, écrans) : la référence fonctionnelle.
   En cas de doute, le code web est lisible dans `../nemla-web`, sans le modifier depuis ce projet.
   `docs/specs/00-versions.md` (versions épinglées du mobile) reste dans ce dépôt.

## Règles non négociables
- Versions : uniquement celles de `gradle/libs.versions.toml` (stables, vérifiées le 15/11/2026). Pas d'alpha, beta ni RC.
  Retirer du catalogue les entrées non utilisées (Navigation 3, Supabase SDK, SQLDelight, Room) : voir ARCHITECTURE §1.
- Réseau : Ktor seulement. Pas de SDK Supabase.
- `:ai:api` est déjà écrit et constitue le contrat IA. Ne pas en changer les signatures sans mettre à jour ARCHITECTURE §4.
  `@OptIn(PrivacyBoundary::class)` n'est autorisé que dans `:ai:local` et ses tests.
- Interface identique sur Android et iOS : tout écart, même mineur, est consigné dans `docs/PLATFORM-PARITY.md`
  et signalé au propriétaire.
- Chaque lot se termine par : `./gradlew spotlessApply`, tests du lot, `./gradlew :androidApp:assembleDebug`,
  et compilation du framework iOS (`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`).
- Essais demandés au propriétaire : appareil Android et simulateur iOS.
- Pas de commentaires dans le code (ni KDoc ni commentaires de ligne) : le code se lit seul, le reste va dans `docs/`.

## État
Avancement, restes et prochaine étape : `docs/ROADMAP.md` (lots mobiles 0 à 6 terminés, 6b et 6c en cours) et
`../docs/LOVABLE-EXIT-PLAN.md` (reprise du backend Lovable et mise en production, lots R0 à R5 et M7 à M11).
Le plan Kotlin `../docs/BACKEND-PLAN.md` (lots B0 à B12) est suspendu.
JDK 17 requis : `gradle/gradle-daemon-jvm.properties` l'impose au daemon.
