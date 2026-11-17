# Revue de l'interface mobile (17/11/2026)

État constaté sur la branche `main` (c685116), au simulateur iOS en clair et en sombre, et dans le code de `core:designsystem`
et des `feature:*`. Ce document propose ; rien n'est encore modifié. Les choix marqués **[à arbitrer]** attendent le propriétaire.

## 1. Constat général

L'interface reprend fidèlement le web (cartes, menus déroulants, boîtes de dialogue centrées), d'où l'impression de « site » :

- aucun `Scaffold`, `TopAppBar`, `NavigationBar`, `TabRow`, `HorizontalPager`, `ModalBottomSheet`, `SegmentedButton` ni `SnackbarHost` ;
  tout est construit à la main dans `core:designsystem` (`NemlaAppHeader`, `NemlaSegmentedControl`, `NemlaDialog`, `NemlaBanner`…) ;
- la navigation se fait par boutons textuels (« ← Retour ») et par menus déroulants ; aucun geste (glisser entre mois, entre onglets) ;
- 9 fenêtres sont des `Dialog` centrés (édition de dépense, bilan, import, dictée, consentement IA, sélecteurs de date…) ;
- les confirmations (« Profil enregistré ») sont des bandeaux dans la page, pas des messages éphémères ;
- le mode sombre a un fond quasi noir (`#000E03`) et des cartes (`#061A0C`) presque de la même couleur : les blocs se confondent ;
- ni logo, ni icône d'application (Android : pas de `mipmap`, iOS : pas d'`AppIcon`), ni polices Outfit / DM Sans.

## 2. Principes proposés

1. **Composant Material 3 d'abord** (Compose Multiplatform, `material3` 1.9.0 du catalogue), habillé par `NemlaTheme`. Un composant maison
   seulement si Material 3 n'a pas d'équivalent (graphique, anneau, carte météo).
2. **Pas d'API « Expressive »** ni de version non stable. Certaines API Material 3 stables dans leur version restent marquées
   `@ExperimentalMaterial3Api` (`ModalBottomSheet`, `TopAppBar`, `DatePicker`, déjà utilisé). **Décidé le 17/11/2026** : opt-in autorisé par le propriétaire,
   comme pour `DatePicker`.
3. **`core:designsystem` garde la main** : les écrans n'appellent pas Material 3 directement, ils passent par des enveloppes `Nemla*`
   minces (`NemlaTopBar`, `NemlaSheet`, `NemlaTabs`…) qui fixent couleurs, formes et typographie.
4. **Écarts Android / iOS volontaires** (retour, gestes) consignés dans `PLATFORM-PARITY.md`, comme l'exige CLAUDE.md.

## 3. Navigation et structure

| Aujourd'hui | Proposition | Composant |
|---|---|---|
| En-tête maison : logo + pastille « Kheireddine ▾ » qui ouvre un menu déroulant (« Gérer mon compte », « Se déconnecter ») | Barre d'application native : logo à gauche, **avatar rond seul** à droite. Le toucher ouvre un **panneau du bas** « Compte » (pseudo, Mon espace, Se déconnecter). | `TopAppBar` (`CenterAligned` ou `Small`), `IconButton`, `ModalBottomSheet` |
| **[à arbitrer]** variante | Barre de navigation en bas à 3 onglets : **Accueil**, **Bilans**, **Mon espace** ; la déconnexion passe dans Mon espace › Profil. Plus « app » encore, et sort les bilans de leur onglet caché. | `NavigationBar` + `NavigationBarItem` |
| « ← Retour » textuel en haut de Mon espace | Flèche de retour dans la barre d'application, différente selon la plateforme (voir ci-dessous) | `TopAppBar` `navigationIcon` |
| Panneaux de Mon espace (budget, charges fixes, profil, préférences, carte) affichés dans le même écran via `AnimatedContent` | Vraies destinations de navigation, chacune avec sa barre et son titre ; le retour système et le geste fonctionnent nativement | `NavHost` + routes typées |
| Retour système : `NavigationBackHandler` dans Mon espace seulement | Android : **retour prédictif** (aperçu de l'écran précédent en glissant), `android:enableOnBackInvokedCallback="true"` dans le manifeste. iOS : **glisser depuis le bord gauche** pour revenir (support du geste par navigation-compose sur iOS, réglage `ComposeUIViewController` à vérifier) | navigation-compose 2.10, `navigationevent` |
| Pas de transition entre écrans | Glissement horizontal court entre écrans (entrée par la droite, sortie par la gauche) | `enterTransition` / `exitTransition` du `NavHost` |

### Bouton retour : spécificité de chaque plateforme
- **Android** : icône `ArrowBack` (flèche), sans texte, à gauche de la barre ; titre à côté.
- **iOS** : chevron `‹` suivi du titre de l'écran précédent (ou « Retour »), comme UIKit.
- Une seule enveloppe `NemlaBackButton` avec une implémentation `expect/actual` minime (icône et libellé). **Écart à consigner** dans
  `PLATFORM-PARITY.md`. Le reste de l'interface reste identique.

## 4. Écran par écran

### Tableau de bord
| Problème | Proposition | Composant |
|---|---|---|
| Mois / Année : contrôle segmenté maison ; en sombre, l'option choisie est **plus foncée** que le fond (inversé) | Boutons segmentés Material 3, coche sur l'option choisie | `SingleChoiceSegmentedButtonRow` + `SegmentedButton` |
| Changement de mois par deux chevrons ronds seulement | **Glisser à gauche / à droite** sur le bloc pilote pour changer de mois (ou d'année). Les chevrons restent, plus petits, pour l'accessibilité. Le libellé « Novembre 2026 » glisse dans le sens du geste. | `HorizontalPager` (une page par période, page initiale = période courante, bornée à aujourd'hui), `AnimatedContent` avec `slideInHorizontally` |
| Le graphique annuel ne suit pas le geste | Le pager couvre le résumé du mois ; le graphique garde le toucher sur ses barres (pas de conflit de geste) | — |
| Bouton « Ajouter » qui cache les initiales O / N / D du graphique et le bas des listes | Bouton flottant étendu qui **se réduit à l'icône « + »** quand on fait défiler, et marge basse sur le contenu | `ExtendedFloatingActionButton(expanded = …)`, `contentPadding` |
| Menu « Ajouter » (Dicter / Importer) en menu déroulant | Panneau du bas avec deux grandes entrées illustrées | `ModalBottomSheet` + `ListItem` |
| Barre de recherche maison et « La recherche par mot-clé arrivera bientôt » toujours visible | Champ de recherche Material 3 ; message seulement quand l'utilisateur tape du texte | `SearchBar` / `DockedSearchBar` |
| En-tête figé sans réaction au défilement | La barre prend une teinte de surface quand le contenu passe dessous | `TopAppBarDefaults.pinnedScrollBehavior()` |
| Tirer pour rafraîchir déjà présent | Garder, vérifier que l'indicateur prend les couleurs Nemla | `PullToRefreshBox` |

### Mon espace
| Problème | Proposition | Composant |
|---|---|---|
| Onglets maison à largeur égale : « Profil & budget » et « Catégories » sont **coupés au milieu du mot** | Onglets natifs **défilants** (chaque onglet a la largeur de son texte), indicateur souligné qui **glisse** d'un onglet à l'autre | `SecondaryScrollableTabRow` (ou `PrimaryScrollableTabRow`) + `Tab` |
| Pas de geste entre onglets | **Glisser entre les onglets**, l'indicateur suit le doigt en continu | `HorizontalPager` synchronisé avec `pagerState.currentPage` / `animateScrollToPage` |
| Libellé long « Profil & budget » | **[à arbitrer]** raccourcir en « Profil » | — |
| Entrées (Gérer mon budget, Mes charges fixes…) en cartes séparées | Liste groupée à séparateurs, plus native, moins de bordures | `ListItem` dans une seule `Card` |
| Zone dangereuse | Garder, avec confirmation en `AlertDialog` natif | `AlertDialog` |

### Fenêtres et retours d'information
| Aujourd'hui | Proposition | Composant |
|---|---|---|
| Édition de dépense, bilan, import de relevé, dictée, fiche de revue, choix de catégorie en `Dialog` centrés | **Panneaux du bas** qu'on ferme en glissant vers le bas ; le bilan, long, en panneau plein écran | `ModalBottomSheet` (`skipPartiallyExpanded` selon le cas) |
| Confirmations et suppressions | Garder des boîtes de dialogue, en version native | `AlertDialog` |
| « Profil enregistré », « Catégorie supprimée » en bandeau dans la page | Message éphémère en bas, avec « Annuler » pour les suppressions | `SnackbarHost` dans le `Scaffold` |
| Sélecteurs de date | Déjà `DatePicker` Material 3 : garder, les présenter en `DatePickerDialog` | `DatePickerDialog` |
| Indicateurs de chargement maison | Indicateur Material 3 | `CircularProgressIndicator` / `LinearProgressIndicator` |

### Connexion, inscription, onboarding
- Champs `OutlinedTextField` natifs avec action clavier (`ImeAction.Next` / `Done`), types de clavier (email, mot de passe) et
  remplissage automatique des mots de passe (`ContentType.Password`, `ContentType.EmailAddress` pour le trousseau iOS et Google).
- Onboarding : indicateur d'étapes et glissement entre étapes (`HorizontalPager`) si plusieurs étapes s'ajoutent.

## 5. Mode sombre

Le fond `#000E03` et les cartes `#061A0C` ont un écart de luminance quasi nul ; les bordures `#FFFFFF` à 10 % disparaissent ;
les barres du graphique et les puces de légende sont trop sombres. Proposition (valeurs à valider au contrôle de contraste) :

| Jeton | Aujourd'hui | Proposé |
|---|---|---|
| `background` | `#000E03` | `#0F1712` (vert très sombre, pas noir) |
| `card` / `popover` | `#061A0C` | `#18231C` (surface nettement plus claire que le fond) |
| `secondary` / `muted` (pistes, champs) | `#0B2413` | `#223027` |
| `border` / `input` | blanc 10 % / 15 % | blanc 16 % / 22 % |
| `mutedForeground` | `#98AA9B` | `#AEBDB1` (contraste ≥ 4,5:1 sur `card`) |
| Barres du graphique, puces de légende | `primary` très atténué | `primary` à 70 % au moins ; mois courant plein |
| Option choisie (boutons segmentés, onglets) | plus foncée que la piste | `secondaryContainer` plus clair que la piste |

Les cartes en sombre perdent leur ombre (invisible) : les distinguer par la surface plus claire et la bordure, comme Material 3
(« tonal elevation »). Les couleurs de catégories restent, elles se lisent bien.

## 6. Animations peu coûteuses

Toutes avec les API Compose de base (`animate*AsState`, `AnimatedVisibility`, `AnimatedContent`, `Modifier.animateItem`), sans
bibliothèque ni image animée :

1. **Montant qui défile** : le total du mois compte jusqu'à sa valeur à l'ouverture et au changement de mois (`animateFloatAsState`,
   ~600 ms). Fort effet, coût négligeable.
2. **Anneau « Où part l'argent » qui se dessine** : balayage de 0 à 360° à la première apparition (`Animatable` sur l'angle du `Canvas`).
3. **Barres du graphique qui montent** l'une après l'autre (décalage de 30 ms par barre).
4. **Changement de mois** : le libellé et les chiffres glissent dans le sens du geste (`AnimatedContent` + `slideInHorizontally`).
5. **Bouton « Ajouter »** qui se réduit / s'étend au défilement, et pulsation douce du micro pendant la dictée (déjà en partie là).
6. **Validation d'une dépense** : la ligne disparaît en glissant et les autres remontent (`Modifier.animateItem` dans une `LazyColumn`),
   avec un retour haptique léger (`LocalHapticFeedback`).
7. **Jauges de budget et crédits** qui se remplissent à l'apparition (`animateFloatAsState` sur la progression).
8. **Bandeau de bilan** qui entre par le haut (`AnimatedVisibility` + `expandVertically`).
9. **Transitions entre écrans** : glissement court natif (voir §3), retour prédictif sur Android.

À respecter : pas d'animation infinie hors dictée ; respect du réglage système « réduire les animations » (prévu au lot accessibilité).

## 7. Logo et identité

- **Logo nemla** : `brand/NemlaLogo.kt` affiche un monogramme provisoire « n ». Il faut le **fichier du logo** (SVG de préférence,
  sinon PNG 1024 px fond transparent) pour l'intégrer en vecteur (`ImageVector` ou ressource `drawable` Compose).
- **Icône d'application** absente sur les deux plateformes : icône adaptative Android (premier plan + fond, icône monochrome pour
  Android 13+) et `AppIcon` iOS 1024 px (variantes claire, sombre et teintée d'iOS 18).
- **Écran de lancement** : Android `windowSplashScreen*` dans le thème (API 31+, sans dépendance), iOS `UILaunchScreen` dans `Info.plist`.
- **Polices** Outfit et DM Sans toujours absentes (`NemlaFonts.kt` retombe sur la police système).

## 8. Découpage proposé (lot « UI mobile », avant le lot 7)

Chaque étape est livrable seule, dans cet ordre :

1. **Fondations** : thème sombre corrigé ; enveloppes `NemlaTopBar`, `NemlaBackButton` (Android / iOS), `NemlaSheet`, `NemlaTabs`,
   `NemlaSnackbar` ; `Scaffold` dans l'écran racine ; retour prédictif Android et geste iOS ; transitions du `NavHost`.
2. **Structure** : barre d'application avec avatar et panneau « Compte » (ou barre de navigation en bas, selon l'arbitrage).
3. **Tableau de bord** : boutons segmentés, glissement entre mois, bouton « Ajouter » réductible et panneau du bas, marges.
4. **Mon espace** : onglets défilants + pager avec glissement, panneaux en destinations, liste groupée.
5. **Fenêtres** : passage des `Dialog` en panneaux du bas, snackbars.
6. **Animations** du §6.
7. **Identité** : logo, icônes d'application, écran de lancement, polices (dès réception des fichiers).

Chaque étape : captures avant / après en clair et en sombre, Android et iOS, et mise à jour de `PLATFORM-PARITY.md`.

## 9. Hors périmètre ici
- **Accessibilité** (lecteurs d'écran, tailles de texte, contrastes, cibles tactiles, réduction des animations) : lot dédié,
  voir `ROADMAP.md`.
- Écrans encore non construits côté fonctionnel (astuces du tableau de bord, notifications).
