# Lot 6d — Tableau de bord en trois onglets

Plan rédigé le 19/11/2026 à partir de la maquette validée par le propriétaire (`docs/maquettes/dashboard-onglets.html`,
à ouvrir dans un navigateur ; elle montre les onglets en haut, la décision finale les met **en bas**).
Ce lot n'a besoin d'aucun nouvel endpoint : tout se calcule sur le téléphone à partir des dépenses déjà lues.

## Décision du propriétaire (19/11/2026)
- Le tableau de bord se découpe en trois destinations : **Aujourd'hui**, **Dépenses**, **Analyse**.
- Elles sont réunies dans une **barre de navigation en bas** (`NavigationBar` Material 3).
  Cette décision **remplace celle du 18/11/2026** (lot 6b, étape 2 : « pas de barre de navigation en bas »).
  Mon espace reste derrière le bouton du compte, en haut à droite. Bilans reste un onglet de Mon espace.
- Les dépenses passent avant les catégories : le total du jour ouvre l'application, l'anneau et le graphique partent dans Analyse.

## Hors périmètre
- Tout ce qui demande les noms d'enseigne (libellés absents, lot M8) : comparaison d'un panier Carrefour dans le temps,
  nombre de commandes chez une enseigne, comparaison entre enseignes, recherche par mot-clé, historique d'une enseigne dans la fiche.
- « Refaire une dépense fréquente » : demande la saisie manuelle (lot M8).
- Comparaison de prix avec d'autres magasins : demande des prix par article et une base de prix externe (plus tard).
- Masquage des infos dans Préférences, petites victoires et badges : lot 10.
- Widget d'écran d'accueil, notifications (lot M7).

## Structure retenue
- Une seule destination de navigation (`DashboardDestination`), un seul `DashboardViewModel` : les trois onglets partagent les données,
  la fiche d'édition de dépense et le rafraîchissement. Pas de nouvelle route dans `shared`.
- `DashboardTab { TODAY, EXPENSES, ANALYSIS }` dans `DashboardUiState`, changé par `DashboardAction.TabSelected(tab)`.
  Onglet de départ : `TODAY` à chaque ouverture de l'application.
- Retour système (Android, retour prédictif) et geste de bord (iOS) depuis Dépenses ou Analyse : retour à Aujourd'hui.
  Depuis Aujourd'hui : comportement actuel.
- Pas de glissement horizontal entre les onglets (règle Material de la barre du bas, et pas de conflit avec le pager de période
  et le toucher des barres du graphique). Changement d'onglet en fondu court (`AnimatedContent`, 150 à 200 ms).
- Chaque onglet garde sa position de défilement (un `ScrollState` / `LazyListState` mémorisé par onglet).
- Bouton « Ajouter » : sur Aujourd'hui et Dépenses, au-dessus de la barre du bas (le `Scaffold` le place). Absent sur Analyse.
- Marges : la barre du bas consomme l'inset système du bas ; revoir `edgeToEdgeBody` / `bottomSafeDrawingPadding` pour ne pas
  compter deux fois la barre système. `FloatingActionClearance` reste sous le contenu des onglets qui ont le bouton.
- Paysage d'un téléphone (hauteur < 480 dp) : le graphique plein écran actuel reste, quel que soit l'onglet.
- Largeur ≥ 840 dp : barre du bas aussi dans ce lot. `NavigationRail` sur tablette : reste à décider avec le propriétaire, à noter
  dans la feuille de route.

## Étapes (un commit par étape)

### 1. `core:designsystem` : `NemlaNavigationBar`
- Enveloppe de `NavigationBar` / `NavigationBarItem` : icône, libellé toujours visible, indicateur `secondaryContainer`,
  couleurs du thème clair et sombre, `@Preview`. Elle avait été créée puis retirée au lot 6b : la recréer.
- Icônes existantes : `CalendarDays` (Aujourd'hui), `ListTree` (Dépenses), `BarChart` (Analyse).
- Brancher dans `DashboardScreen` via `NemlaScaffold(bottomBar = …)` (ajouter le paramètre s'il manque).

### 2. Données : historique court
- Une requête de plus dans `fetchAll`, en parallèle des autres :
  `listExpenses(ExpenseQuery(dateFrom = min(today − 56 j, 1er du mois précédent), dateTo = today, confirmed = true))`.
- Gardée dans le ViewModel (`history`). Un échec **n'affiche pas** de bandeau d'erreur : les infos qui en dépendent sont simplement masquées.
  Ne pas l'ajouter à `LoadResults.firstError()`.
- Rechargée avec le reste (tirer pour rafraîchir, retour au premier plan, après modification ou suppression).

### 3. Logique pure dans `logic/` (avec tests, aucun code d'interface)
Ajouter à `ExpenseRow` : `paymentMethod`, `isRecurring` (= `recurringChargeId != null`).

`TodayCalculator` (ou extension de `DashboardCalculator`), produit `TodayView` enrichi :
- `totalCents`, `rows` : **toutes** les dépenses confirmées du jour, triées par montant décroissant. Supprimer la pagination
  (`page`, `PAGE_SIZE`, `TodayPreviousPage`, `TodayNextPage`, chaînes `page_*` si elles ne servent plus ailleurs).
- `dailyBudget` : `(budget mensuel − dépensé du mois avant aujourd'hui) / jours restants, aujourd'hui compris`, plancher à 0.
  `null` si aucun budget mensuel. Reste du jour = budget du jour − total du jour (négatif = dépassement).
- `weekdayComparison` : moyenne des totaux des 8 mêmes jours de semaine précédents (jours sans dépense comptés à 0),
  **hors charges fixes** (`isRecurring`). `null` si la première dépense connue a moins de 28 jours. Écart = total du jour − moyenne.

`InsightsCalculator`, produit au plus **3** `Insight` triés par priorité :
1. `Projection` : charges fixes du mois déjà passées + charges mensuelles dont `dayOfMonth` est encore à venir
   + dépenses variables / jour du mois × nombre de jours du mois. Seulement à partir du 5 du mois et si un budget existe.
   Écart au budget en clair, et effort par jour si dépassement (`écart / jours restants`).
2. `CategoryRise` : pour chaque catégorie, dépensé du 1er à aujourd'hui contre le mois précédent du 1er au même jour
   (borné au dernier jour du mois précédent). Garder la plus forte hausse si ≥ +20 % **et** ≥ 20 € d'écart.
3. `MonthToDate` : total du mois à ce jour contre le mois précédent au même jour (« 120 € de moins que le mois dernier à la même date »).
   Le ton reste positif quand c'est moins bon (« un peu plus que le mois dernier à la même date »), jamais culpabilisant.
4. `Streak` : jours consécutifs avec au moins une dépense confirmée, en partant d'aujourd'hui, ou d'hier si rien n'est encore saisi aujourd'hui.
   Affiché à partir de 3 jours. Plafonné à la fenêtre d'historique (56 jours) : afficher « 56 jours et plus ».
- Un `Insight` masqué d'un toucher sur la croix ne revient pas avant le lendemain (mémoire du ViewModel suffit dans ce lot).

`ExpenseListBuilder`, produit `ExpenseListView` pour l'onglet Dépenses :
- Source : dépenses confirmées de la période du curseur (`yearExpenses` filtrées par `cursor.span`).
- `ExpenseSort { AMOUNT_DESC (défaut), AMOUNT_ASC, DATE_DESC, CATEGORY }`.
- `ExpenseListFilter(categories: Set<CategoryId>` vide = toutes`, payment: PaymentMethod?, source: ExpenseSource?)`.
  Filtre **distinct** du filtre de catégories du graphique.
- Sections : par jour avec sous-total (tri par date), par catégorie avec sous-total (tri par catégorie), liste à plat sinon.
- `totalCents` et `count` du résultat filtré.
- `top3` : les trois plus gros montants de la période hors charges fixes, seulement quand aucun filtre n'est actif.

Tests (`commonTest`, ×2 cibles) : budget du jour (début, milieu, dernier jour du mois, budget dépassé, sans budget), moyenne du jour
de semaine (historique trop court, jours vides), projection (avant le 5, charges à venir), hausse de catégorie (seuils, mois plus court),
série (aujourd'hui vide, trou), tri et filtres combinés, sections et sous-totaux, top 3 sans charges fixes.

### 4. Onglet « Aujourd'hui »
De haut en bas :
1. Date du jour (surtitre), **total du jour en grand** (animé à l'arrivée, `animatedCents`), « n dépenses », pastille d'écart
   avec le jour de semaine habituel (verte ↓ / rouge ↑).
2. Carte « Budget du jour » : montant, jauge `NemlaProgressBar` (rouge si dépassée), « Il vous reste X aujourd'hui » ou
   « X au-dessus aujourd'hui », jours restants.
3. Dépenses à valider (`PendingCard` actuelle, ou bandeau compact qui l'ouvre).
4. Infos : `HorizontalPager` de cartes (icône, titre, une phrase, croix), indicateur de pages. Rien si la liste est vide.
5. Carte « Aujourd'hui » : toutes les lignes (`ExpenseRowItem`), bandeau de débit différé actuel, état vide.
6. Alertes de budget (`AlertsCard` actuelle).
7. Bandeau du bilan arrivé (`reportNotice`).
8. Ligne du mois : dépensé / budget, pastille météo, jauge fine ; un toucher ouvre Analyse sur le mois en cours.

### 5. Onglet « Dépenses »
- `LazyColumn` (une année peut compter des centaines de lignes), tirer pour rafraîchir.
- En tête : sélecteur de période compact (‹ Novembre 2026 ›, Mois / Année), **partagé avec Analyse** (même `cursor`).
- Recherche par date (`SearchCard` actuelle, déplacée ici ; « recherche par mot-clé bientôt » inchangé).
- Puces de tri (`FilterChip`, un seul choix), puces de catégories (plusieurs choix + « Toutes »), puces de paiement et de saisie,
  chacune en rangée défilante (`LazyRow`).
- Ligne « n dépenses · total ». Top 3 en trois cartes quand aucun filtre n'est actif.
- Sections avec en-tête collant (`stickyHeader`) et sous-total. Toucher une ligne ouvre la fiche d'édition existante.
- État vide avec « Effacer les filtres ».

### 6. Onglet « Analyse »
- `PilotCard` (période, total, météo, graphique, filtre des catégories) et `SpendingCard` (répartition / budgets) actuelles, sans changement de comportement.
- Nouvelle carte « Par rapport au mois précédent » (mode Mois uniquement) : écart par catégorie à date égale, trié par hausse,
  flèche et couleur (hausse en `destructive`, baisse en vert météo).
- Lien vers Dépenses : dans le panneau d'une catégorie de la répartition (`CategorySheet`), bouton « Voir les dépenses »
  → `DashboardAction.ShowCategoryExpenses(id)` : onglet Dépenses, filtre sur cette seule catégorie, tri par montant décroissant.

### 7. Textes, nettoyage, documents
- Chaînes FR et EN (`values/strings.xml`, `values-en/strings.xml`), pluriels avec `pluralStringResource`. Pas de texte en dur.
- Supprimer ce qui ne sert plus (pagination du jour, ordre de cartes de `DashboardContent`, `TodayCard` si remplacée).
- `docs/ROADMAP.md` : état du lot 6d, la décision du 19/11 qui remplace celle du 18/11, la question `NavigationRail` sur tablette.
- `docs/PLATFORM-PARITY.md` : la barre du bas est la même `NavigationBar` Material 3 sur iOS, sans `UITabBar` natif. C'est un écart
  visuel avec les usages iOS, à consigner et à signaler au propriétaire.
- Aucun commentaire dans le code.

## Fin de lot
- `./gradlew spotlessApply`
- Tests du lot : `./gradlew :feature:dashboard:allTests :core:designsystem:allTests` (et `:shared:allTests` si `App` change).
- `./gradlew :androidApp:assembleDebug`
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
- Essais demandés au propriétaire sur appareil Android et simulateur iOS :
  - les trois onglets et le retour système ;
  - le bouton « Ajouter » au-dessus de la barre ;
  - la rotation ;
  - le mode sombre ;
  - une journée sans dépense ;
  - un compte sans budget ;
  - un compte de moins de 4 semaines.
