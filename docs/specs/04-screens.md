# 04 — Écrans, feuilles et dialogues de nemla (spécification pour le portage Compose Multiplatform)

> Source : `nemla-main` (web). Fichiers lus en entier : `src/routes/auth.tsx`, `reset-password.tsx`, `_authenticated/route.tsx`, `onboarding.tsx`, `dashboard.tsx`, `settings.tsx`, tous les composants métier de `src/components` (hors `ui/` et `landing/`), `src/hooks/use-recorder.ts`, `use-quotas.ts`, `src/lib/{i18n,lang,format,debit,wav,push,pseudo,profile-sync,api-client,category-suggestions}.ts`, dictionnaires `src/lib/i18n/*`.
> Les tokens de couleur (`primary`, `muted-foreground`, `accent`…) et les tailles (`text-sm` = 14 px…) sont définis dans `03-design-system.md`.
> Convention : « Clés i18n » = chaînes **françaises** passées à `t()` (elles servent de clés, voir §I). La liste exhaustive par fichier est en **Annexe A**.
> Les mentions **ÉCART** signalent une différence entre la description produit et le code web, ou entre le web et ce qui est faisable à l'identique en natif.

---

## 0. Carte de navigation

```
/ (landing, web)  ──►  /auth?mode=signup (défaut) | ?mode=signin  ──► (mode interne « forgot »)
                            │ inscription avec session immédiate ──► /onboarding ──► /dashboard
                            │ connexion ─────────────────────────────────────────► /dashboard
                            │ inscription avec email à confirmer ──► (lien email) ► /dashboard
/reset-password  (ouvert depuis le lien email)  ──► /dashboard
/_authenticated (garde) : sans utilisateur ──► redirection /auth
   ├─ /onboarding
   ├─ /dashboard  ◄──► /settings (« Mon espace »)
   └─ /admin (rôle admin uniquement — web/back-office)
```

- Garde `_authenticated/route.tsx` : `supabase.auth.getUser()` ; erreur ou pas d'utilisateur → `redirect /auth`. Rendu côté client uniquement (`ssr: false`).
- `/auth` : si une session existe déjà au montage → `navigate /dashboard`.
- **ÉCART** : aucun écran ne force l'onboarding. `profiles.onboarding_done` est lu par le dashboard mais jamais utilisé pour rediriger. Un utilisateur qui confirme son email (lien → `/dashboard`) ou se reconnecte **ne passe jamais par l'onboarding**. À décider pour le mobile.
- Déconnexion (menu de l'en-tête) → `signOut()` puis `/` (landing). Sur mobile : retour à l'écran d'auth.
- Liens internes : bannières d'alerte budget et fiche de revue → `/settings` ; en-tête logo → `/dashboard` ; bouton « Retour » de Mon espace → `/dashboard`.

---

## 1. En-tête applicatif (`AppHeader`) — présent sur onboarding, dashboard, settings

Structure (hauteur ≈ 60 px) :
- Conteneur `sticky top-0`, z 20, bordure basse `border`, fond `background` à 85 % + flou d'arrière-plan (`backdrop-blur`). Contenu centré `max-w 768 px`, `px 20 py 12`, `Row SpaceBetween`.
- Gauche : `BrandLogo` (image 36 px + « nemla » Outfit 24/700, couleur `primary`) — lien vers `/dashboard` (connecté) ou `/` (déconnecté).
- Droite (connecté), `gap 8` :
  - **Si rôle admin** (`user_roles.role = 'admin'`) : bouton icône ghost `ShieldCheck` 16 px → `/admin` (aria « Administration »).
  - Bouton « Mon espace » : `secondary`, `size sm`, pilule, `gap 8` : avatar rond 28 px fond `accent/15` avec icône de situation familiale 16 px `accent` ; pseudo (14/500, tronqué max 96 px) ou « Mon espace » ; `ChevronDown` 16 px opacité 60 %.
  - Menu déroulant (aligné à droite, largeur 224 px) : titre = pseudo ou « Mon espace » ; séparateur ; « Gérer mon compte » (icône `Settings`) → `/settings` ; « Se déconnecter » (icône `LogOut`).
- Droite (déconnecté) : « Connexion » (ghost sm → `/auth?mode=signin`) et « Créer un compte » (default sm → `/auth`). N'apparaît que sur la landing en pratique.
- Données : profil `pseudo, situation_familiale` (requête `header-profile`, invalidée après inscription/sauvegarde du profil) ; écoute `onAuthStateChange`.
- Clés i18n : `Connexion`, `Créer un compte`, `Administration`, `Mon espace`, `Gérer mon compte`, `Se déconnecter`. (aria-label « Mon espace » en dur, non traduit.)

---

## 2. Authentification — route `/auth`

### 2.1 Structure (de haut en bas)
- Plein écran, colonne centrée verticalement et horizontalement, `px 20 py 40`.
- Logo (image 40 px + « nemla » 20/700, couleur `primary`), lien vers `/`, marge basse 32 px.
- Carte `card-soft`, largeur max 384 px, padding 24 :
  - Titre h1 Outfit 24/600 : « Créer mon compte » | « Mot de passe oublié » | « Bon retour ».
  - Sous-titre 14 px `muted-foreground`, marge haute 4 : selon mode.
  - Formulaire, marge haute 24, champs espacés de 16 :
    1. **Email** (label + input `type=email`, `autocomplete=email`).
    2. **Mot de passe** (sauf mode forgot) : input avec bouton œil à droite (`Eye`/`EyeOff` 16 px `muted-foreground`), bascule texte/masqué.
    3. Mode signin : lien texte « Mot de passe oublié ? » (14 px `primary`) → mode forgot.
    4. Mode signup :
       - Grille 2 colonnes `gap 12` : **Pseudo** (texte libre) | **Âge** (nombre, min 14, max 120).
       - Si suggestions de pseudo : ligne « Pseudos disponibles : » (12 px muted) + chips pilules (bordure, 12 px, `px 8 py 4`) ; clic → remplit le pseudo et masque les chips.
       - **Sexe** (Select, placeholder « Choisir ») : Femme / Homme.
       - **Situation familiale** (Select) : 4 options avec icône 16 px `accent` (voir design §10).
       - Encart confidentialité : fond `muted/60`, `rounded-lg`, `p 12`, 12 px muted, icône `ShieldCheck` 16 px `primary` : « Vos données sont protégées… jamais revendues. »
    5. Bouton principal pleine largeur (`default`) : « Créer mon compte » | « Envoyer le lien » | « Se connecter » ; pendant l'envoi : « Un instant… » et désactivé.
  - Lien bas (14 px muted, souligné au survol, marge haute 20, pleine largeur) : « J'ai déjà un compte : me connecter » ↔ « Pas encore de compte ? Créer un compte ». (En mode forgot, ce lien renvoie vers signup.)

### 2.2 Modes et déclencheurs
- Mode initial = `?mode` de l'URL, sinon **signup** (parcours mis en avant).
- **forgot** : `resetPasswordForEmail(email, redirectTo = <origin>/reset-password)` → toast succès « Email envoyé : suivez le lien… » → retour en mode signin.
- **signup** : `signUp(email, password, data = {pseudo, age, sexe, situation_familiale})`, `emailRedirectTo = <origin>/dashboard`.
  - Si une session est créée immédiatement : `saveProfile` → vérifie le pseudo (`rpc suggest_pseudo`, insensible à la casse) ; s'il est pris, le **remplace par la 1re suggestion** et affiche un toast info « Le pseudo « {pseudo} » est déjà pris : nous avons choisi « {replacement} »… » (ou « Ce pseudo est déjà pris : choisissez-en un autre dans Mon espace. » sans suggestion) ; upsert du profil ; toast « Compte créé. Bienvenue ! » ; navigation **/onboarding**.
  - Sinon (confirmation email requise) : toast info « Vérifiez votre boîte mail pour confirmer votre adresse. » ; on reste sur l'écran. Les métadonnées seront recopiées dans le profil à la 1re ouverture du dashboard (`syncProfileFromSignup`).
- **signin** : `signInWithPassword` → `/dashboard`.
- Erreur : toast erreur avec le message brut de Supabase (souvent en anglais), sinon « Connexion impossible ».

### 2.3 Validation (uniquement HTML5)
| Champ | Règle |
|---|---|
| Email | requis, format email |
| Mot de passe | requis, **≥ 6 caractères** (signin et signup) |
| Âge | nombre entre 14 et 120, optionnel |
| Pseudo, sexe, situation | optionnels |
Aucun message d'erreur custom : ce sont les bulles natives du navigateur. **ÉCART natif** : il faudra des messages d'erreur sous les champs (textes à créer).

### 2.4 États
Chargement = bouton « Un instant… » désactivé. Erreur/succès = toasts. Pas d'état vide.

Clés i18n : voir Annexe A (`auth.tsx`, 31 clés).

---

## 3. Nouveau mot de passe — route `/reset-password`

- Même gabarit que l'auth (logo 40 px, carte 384 px).
- Titre « Nouveau mot de passe ». Sous-titre : si une session de récupération est présente → « Choisissez un mot de passe d'au moins 6 caractères. », sinon « Ouvrez cette page depuis le lien reçu par email pour continuer. »
- Champ « Mot de passe » (min 6, requis, œil).
- Bouton « Enregistrer mon mot de passe » (désactivé tant que pas de session ou pendant l'envoi ; « Un instant… »).
- Lien centré « Retour à la connexion » → `/auth?mode=signin`.
- Logique : `getSession` + `onAuthStateChange` (la session arrive par le lien) → `updateUser({password})` → toast « Mot de passe mis à jour » → `/dashboard`. Erreur : message ou « Mise à jour impossible ».
- **ÉCART natif** : nécessite un deep link / App Link / Universal Link pour ouvrir l'app depuis l'email.

---

## 4. Onboarding — route `/onboarding` (« Vos charges fixes »)

**ÉCART** : il n'y a **pas d'étapes** (pas de carrousel ni de stepper) : c'est **une seule page** de 3 blocs. Pas d'indicateur de progression.

Au montage : `rpc bootstrap_user` (crée profil + 13 catégories par défaut si absent), puis lecture des catégories triées par nom.

Structure (`AppHeader` puis conteneur standard, sections espacées de 20 px) :
1. Titre h1 « Vos charges fixes » (24/600) + texte 14 muted « Tapez ou dictez en vrac : « Netflix 15 €, Spotify 10 €, internet 30 €, loyer 750 € le 5 ». »
2. Carte saisie (`card-soft p-5`, `space 12`) :
   - Textarea 4 lignes, placeholder « Netflix 15€, Spotify 10€, internet 30€… ».
   - Boutons : **Analyser** (default, icône `Sparkles`, ou `Loader2` en rotation si occupé ; désactivé si occupé ou texte < 3 caractères) ; **Dicter** (secondary, icône `Mic`) qui devient **« Arrêter ({seconds}/{maxSeconds}s) »** (destructive, icône `Square`) pendant l'enregistrement ; désactivé si occupé.
   - Dictée : même enregistreur que le FAB (30 s, silence 3 s, voir §6.3) → `POST /api/public/transcribe` → la transcription est **ajoutée** au texte (séparée par « , ») → analyse automatique de la transcription seule.
   - Analyse : `POST /api/public/parse/recurring` `{text, lang}` → **remplace** la liste des charges (contrairement à Mon espace qui ajoute).
3. Carte « Vérifiez avant d'enregistrer » (visible si ≥ 1 charge) : pour chaque charge, tuile `rounded-lg border p-3` :
   - Ligne 1 : Input nom (flex) + Input montant (nombre, pas 0.01, 96 px, tabulaire).
   - Ligne 2 : Select catégorie (flex, **sans pastilles** ici) + Select fréquence (144 px : Mensuelle / Annuelle) + bouton icône ghost `Trash2` (« Retirer »).
   - Pas de champ « jour de prélèvement » ici (valeur renvoyée par l'IA conservée).
4. Carte budget : label « Budget mensuel disponible (€) » (**ÉCART** : symbole « € » codé en dur, ignore la devise), Input nombre (min 0, pas 1, placeholder « 2000 ») ; bouton pleine largeur « Enregistrer et voir mon tableau de bord ».

Enregistrement : insertion des charges (`recurring_expenses`) puis upsert profil `{monthly_budget: Number(budget || 0), onboarding_done: true}` → toast « Configuration enregistrée » → `/dashboard`. Erreur → toast message ou « Enregistrement impossible ».

Validation : **aucune** (budget vide = 0 ; une charge sans nom ou à 0 peut être enregistrée). Erreurs d'analyse/transcription : toasts « Analyse impossible » / « Transcription impossible ».

---

## 5. Tableau de bord — route `/dashboard`

### 5.1 Chargements et données
Séquence : `bootstrap` (`rpc bootstrap_user` + recopie des métadonnées d'inscription dans le profil + invalidation `header-profile`) → en parallèle : catégories (`id, nom, couleur, visible, budget_mensuel, budget_annuel`, tri `ordre` puis `nom`), matérialisation des charges fixes de l'année affichée (fonction serveur `materializeRecurring`, cache 5 min, un échec n'empêche rien), dépenses de la période, dépenses confirmées de l'année, quotas, charges fixes, profil (`monthly_budget, annual_budget, budget_periode, daily_reminder, devise` — applique la devise), dépenses confirmées **du jour** (date d'achat = aujourd'hui local), dépenses **en attente** (`confirmed = false`), recherche.
- Les libellés sont chiffrés en base : la lecture passe par une fonction serveur qui déchiffre (le client mobile devra passer par une API équivalente).
- Les données précédentes restent affichées pendant un changement de mois/année (`keepPreviousData`) → pas de « flash » à 0.
- **États de chargement / erreur** : le dashboard **n'affiche ni squelette ni message d'erreur** pour ses requêtes ; les blocs sont simplement vides/à 0 tant que les données n'arrivent pas. Seule la fiche de revue a un état de chargement. **À décider pour le mobile** (un état de chargement explicite serait une différence).

Racine : colonne, fond `background`, `pb 112 px` (place du FAB). `AppHeader`, puis conteneur standard (`max-w 768`, `px 20 py 24`, `space-y 20`).

### 5.2 Ordre vertical des blocs
1. Carte « Dépenses à valider » (conditionnelle)
2. Alertes de budget par catégorie (conditionnelle) — §5.8
3. Bannière d'astuce (conditionnelle) — §5.9
4. Bandeau « Votre bilan de … est arrivé » (conditionnel) — §5.10
5. Bannières de notifications in-app (conditionnelles) — §5.11
6. (Rappel quotidien : invisible, logique seule) — §5.12
7. Fiche de revue (`VoiceReview`, conditionnelle) — §7
8. Carte Recherche — §5.4
9. **Bloc pilote** (période, montant, météo, graphique, filtre) — §5.5
10. Carte « Où part l'argent » / « Budgets par catégorie » — §5.6
11. Carte « Dépenses du jour » — §5.7
12. FAB « Ajouter » (fixe en bas à droite) — §6
13. Dialogue d'édition de dépense — §8 ; dialogue débit différé (`PaymentModeGate`) — §11.2

### 5.3 Carte « Dépenses à valider »
- Visible si au moins une dépense non confirmée existe **et** qu'aucune fiche de revue n'est ouverte.
- `card-soft p-5`, bordure `accent/50`. Titre h2 16/600 « Dépenses à valider » ; texte 14 muted « Ces dépenses (dictées ou lues dans un fichier) attendent votre confirmation — touchez-les pour vérifier et ajuster leur catégorie. »
- Liste (marge haute 12, séparateurs) : un élément par groupe = par `voice_entry_id` ; toutes les dépenses importées (sans dictée) forment **un seul groupe « import »**.
  - Ligne cliquable `py 12`, `Row SpaceBetween` : à gauche titre 14/500 tronqué — « Dépenses importées d'un fichier » (import) ou « {description de la 1re dépense}… » (ou « Dépense vocale… ») ; dessous 12 px muted « {count} dépense(s) · {total} ».
  - À droite pastille pilule fond `primary`, texte `primary-foreground` 12/600, `px 12 py 6`, icône `Check` 16 + « Vérifier ».
- Clic → ouvre la fiche de revue avec ce groupe, défilement doux jusqu'à elle.

### 5.4 Recherche
- `card-soft p-3`. Ligne `gap 8` :
  - Champ pilule hauteur 44 px (`rounded-full`), icône `Search` 16 px muted à 12 px du bord gauche, placeholder « Rechercher une dépense… ». Si texte ou date présents : bouton rond 24 px `X` à droite (« Effacer la recherche ») qui vide les deux.
  - Bouton date pilule 44 px : `outline` sans date, `secondary` avec date ; icône `CalendarDays` ; libellé (date courte ou « Date ») **masqué sur mobile** (< 640 px, icône seule). Ouvre un popover 256 px : « Chercher un jour précis » (12/500 muted), sélecteur de date natif (max = aujourd'hui ; une date future est ramenée à aujourd'hui), bouton ghost sm pleine largeur « Retirer la date » si une date est choisie.
- Recherche **active** si ≥ 2 caractères **ou** une date. Requête serveur : dépenses **confirmées**, filtre « description contient » (insensible à la casse, **sensible aux accents**), et/ou date d'achat exacte ; **50 résultats max** ; tri date décroissante puis création décroissante.
- Résultats : ligne 12 px muted tabulaire « {count} résultat(s) · {total} », puis liste de lignes de dépense (§5.7.1). Vide (et pas en cours de chargement) : « Aucune dépense trouvée. »
- **ÉCART** : la description produit annonce une recherche « par mot-clé, date spécifique **ou catégorie** » : la recherche par catégorie **n'existe pas** (le filtre de catégories du bloc pilote n'agit pas sur la recherche). La recherche n'est pas non plus instantanée côté client (aller-retour serveur à chaque frappe, sans anti-rebond).

### 5.5 Bloc pilote (période, montant, météo, graphique)
`card-soft`, padding 16 (20 ≥ 640 px), contenu non débordant.

**a) Ligne période** (grille `[1fr, auto]`, centrée, `gap 12`) :
- Contrôle segmenté (Tabs) « Mois » | « Année ».
- Navigateur de période : pilule bordée, fond `muted/40`, padding 4, `gap 4` : bouton rond 36 px `secondary` `ChevronLeft` 20 px (« Mois précédent »/« Année précédente ») ; libellé 14/600 centré, largeur min 96 px, **première lettre en majuscule** (« Août 2026 » / « 2026 ») ; bouton `ChevronRight` (« Mois suivant »/« Année suivante »). **Aucune borne** : on peut naviguer dans le futur et le passé sans limite.

**b) Montant** (séparateur haut 1 px `border` 70 %, `mt 16 pt 16`), grille `[1fr, auto]` :
- Sur-titre 12 px MAJUSCULES `tracking-wider` muted : « Dépensé · mois » / « Dépensé · année ».
- Montant Outfit **30 px / 600** tabulaire (48 px ≥ 640 px) : somme des dépenses **confirmées** de la période **filtrées par le filtre de catégories**.
- À droite : **pastille météo** (§5.5.1), seulement si (mode mois) budget mensuel > 0 **et** dépenses du mois > 0, ou (mode année) budget annuel > 0 **et** dépenses de l'année > 0.
- Si budget > 0 : barre de progression (marge haute 12) = min(100 %, dépensé / budget).
- Si charges fixes > 0 : ligne 12 px muted « Charges fixes : {montant mensuel} / mois » (charges annuelles comptées pour 1/12).
- Budget utilisé : mois = `monthly_budget` ; année = `annual_budget` sinon `monthly_budget × 12`.

#### 5.5.1 Météo du budget (`BudgetWeather`, variante compacte — seule variante affichée)
Ratio = dépensé / budget (mois : toutes les dépenses confirmées du mois **sans** filtre de catégories ; année : total annuel **avec** filtre de catégories — incohérence du web à connaître).

| Condition | État | Icône Lucide | Texte FR / EN | Couleur |
|---|---|---|---|---|
| ratio > 1 | Orage | `CloudLightning` | « Dépassement » / « Over budget » | `#DC2626` |
| 0.9 < ratio ≤ 1 | Pluie | `CloudRain` | « Tendu » / « Tight » | `#EA580C` |
| 0.7 < ratio ≤ 0.9 | Nuage | `Cloud` | « Vigilance » / « Caution » | `#D97706` |
| ratio ≤ 0.7 | Soleil | `Sun` | « Au beau fixe » / « All clear » | `#16A34A` |
| budget ≤ 0 | — | — | « Budget non défini » (branche jamais atteinte : la pastille est masquée sans budget) | muted |

- Pastille : pilule bordée, fond `muted/40`, `px 10 py 6`, icône 20 px + texte 12/600, tous deux dans la couleur d'état.
- Clic → popover 256 px aligné à droite, `p 12`, `space 8` : libellé « Météo de {periode} » / « Météo de l'année {year} » (12 muted) ; icône 20 + état (600, couleur) ; barre de progression ; 12 px muted tabulaire : « Dépensé : {x} », « Budget cible : {x} », puis « {amount} restants sur {budget} » ou « Dépassement de {amount} sur {budget} ».
- **ÉCART important** : la description produit parle d'une météo **prédictive** basée sur le **rythme** de consommation (« prévient dès le milieu du mois »). Le code web calcule uniquement le **ratio dépensé / budget**, sans projection ni prise en compte du jour du mois. Il y a **4 états** (Soleil, Nuage, Pluie, Orage), pas 3.

**c) Graphique « Dépenses mois par mois »** (séparateur, `mt 16 pt 16`) :
- En-tête (`Row SpaceBetween`, wrap) : h2 14/600 « Dépenses mois par mois — {année} » ; légende 11 px muted, `gap 12` : pastille 10 px `primary/35` « Dépenses » ; si charges mensuelles > 0 : pastille `primary/18` « Charges prévues » ; si budget mensuel > 0 : trait 16 × 2 px `destructive` « Budget cible ».
- Zone : hauteur **208 px** (224 px ≥ 640), déborde de 16 px de chaque côté sur mobile (pleine largeur de la carte). Marges internes du graphique : haut 8, droite 8, bas 4, gauche 8.
- Données : 12 mois de l'année du curseur ; total = somme des dépenses **confirmées** par **mois d'achat** (filtre de catégories appliqué).
- **Barres** (largeur max 28 px, coins hauts 5 px, empilées) :
  - « total » : mois sélectionné (mode mois uniquement) = `primary` plein + contour 2 px `primary` ; autres mois = `primary` à 38 %.
  - **« prévu » (barres prévisionnelles)** empilées au-dessus : pour les mois **futurs** (année > année courante, ou mois > mois courant de l'année courante), valeur = **somme des charges fixes mensuelles** (les annuelles sont exclues), uniquement si **toutes** les catégories sont sélectionnées et que l'année ≥ année courante. Remplissage `primary` 18 %, contour **pointillé** (3 3) `primary` 40 %.
- **Ligne de budget** : si budget mensuel > 0, ligne horizontale à `y = budget mensuel` (même en vue année), `destructive`, épaisseur 2, pointillés (5 4), étiquette en haut à droite « Cible {montant} » 10 px `destructive`. L'axe Y s'étend pour toujours inclure la ligne : max = max(données, budget) × 1.1.
- Axe X : sans trait ; sur mobile **initiale du mois en majuscule** (« J F M A … »), ≥ 640 px mois court (« janv. ») ; 11 px muted.
- Axe Y : largeur 46 px, 5 graduations, 10 px muted, format compact (`compactAmount` : « 0 € », « 800 € », « 1,2 k€ », « 12 k€ »).
- Grille : lignes horizontales seules, couleur `border`, pointillés 4 4, opacité 60 %.
- Info-bulle au toucher/survol : pilule (bordure, fond `card` 95 %, flou, 12/500) « {mois} {total} » (total = réel + prévu). Curseur : bande `muted` 40 %.
- **Drill-down** : toucher une barre (ou sa colonne) → passe en mode **Mois** sur ce mois. Le détail du mois apparaît alors dans « Où part l'argent » (§5.6), où un toucher sur une catégorie déplie ses tickets, et un toucher sur un ticket ouvre l'édition (§8).
- Sous le graphique, sur mobile uniquement : « Touchez une barre pour ouvrir le mois » (11 px muted centré).

**d) Pied du bloc** (séparateur, `mt 16 pt 12`) :
- Bouton `outline` `sm` avec icône `Filter` : « Toutes catégories » | « Aucune catégorie » | « {count} catégorie(s) ».
- Popover 256 px, `p 8` : en-tête « Filtrer les catégories » (12/500 muted) + bouton ghost 24 px « Tout désélectionner » / « Tout sélectionner » ; si aucune sélection : encart `destructive/10`, texte 12 px `destructive` « Sélectionnez au moins une catégorie pour voir vos dépenses. » ; liste défilante (max 256 px) : chaque catégorie = bouton `px 8 py 6`, pastille 14 px, nom tronqué, coche à droite si sélectionnée ; sélectionnée = fond `primary/10`, texte `primary` 500.
- Logique : `null` = toutes (état par défaut, persistant) ; décocher depuis « toutes » crée une sélection explicite de toutes sauf une ; une nouvelle catégorie est ajoutée automatiquement à une sélection personnalisée. Le filtre agit sur le montant, la progression, le graphique, « Où part l'argent » ; **pas** sur la météo mensuelle, les budgets par catégorie, la recherche ni les dépenses du jour.
- À côté : « Moy. {total annuel / 12}/mois » 11 px muted tabulaire (division **toujours par 12**, même en cours d'année).

### 5.6 « Où part l'argent » / « Budgets par catégorie »
`card-soft p-5`.
- En-tête : h2 16/600 = « Où part l'argent » ou « Budgets par catégorie » selon l'onglet ; à droite, **seulement si au moins une catégorie a un budget**, Tabs « Répartition » | « Budgets ».
- **Onglet Répartition** (par défaut) :
  - Groupes = dépenses confirmées de la période (filtre de catégories appliqué) regroupées par catégorie ; les catégories **masquées** (`visible = false`) **et les dépenses sans catégorie** tombent dans un groupe « Autre » (couleur `#94A3B8`, libellé « Autre » **non traduit**). Tri par montant décroissant.
  - Vide : « Aucune dépense sur cette période. »
  - Disposition mobile : **anneau (donut) au-dessus**, liste en dessous (2 colonnes ≥ 640). Donut : hauteur 192 px, rayon intérieur 58 %, extérieur 88 %, couleur de chaque part = couleur de catégorie, sans contour ; info-bulle par défaut de la bibliothèque (montant formaté).
  - Liste (`space 8`) : ligne cliquable hauteur min 48 px, `px 12 py 12`, `rounded-lg` : pastille 16 + nom (14) | montant muted tabulaire + chevron droit (fermé) / bas (ouvert). Un seul groupe ouvert à la fois.
  - Groupe ouvert : sous-liste indentée 24 px avec filet gauche, `pl 12`, éléments hauteur min 40 px, `py 10`, 12 px : « {date courte} · {description} » | montant. Toucher → édition (§8).
- **Onglet Budgets** : catégories avec budget > 0 (mensuel = `budget_mensuel` sinon `budget_annuel / 12` ; annuel = `budget_annuel` sinon mensuel × 12 ; selon la période affichée), triées par taux d'utilisation décroissant. Ligne : pastille 16 + nom | « {utilisé} / {alloué} » 12/500 tabulaire muted, ou en `destructive` avec « · +{dépassement} » si dépassé ; barre de progression (marge 6). Le filtre de catégories ne s'applique pas ici.
- **ÉCART** : la description produit évoque un « regroupement interactif des achats d'une même catégorie… avec vue détaillée au clic » dans la saisie vocale : dans le web, ce regroupement n'existe **qu'ici** (dépliage par catégorie), pas dans la fiche de revue.

### 5.7 « Dépenses du jour » (liste paginée 5 par 5)
`card-soft p-5`.
- En-tête : h2 « Dépenses du jour » | « {count} dépense(s) · {total} » 12 muted tabulaire.
- Si débit différé actif : encart `primary/10`, `rounded-lg`, `px 12 py 8`, 12 px `foreground` : **« Débit différé actif »** (600) — « un achat par carte fait aujourd'hui sera prélevé le {date longue}. » « Virements, espèces et chèques restent à leur date. »
- Liste séparée par des filets (marge 12) : dépenses **confirmées** dont la date d'achat = aujourd'hui (heure locale), tri date puis création décroissante, **5 par page**.
- Vide : « Aucune dépense aujourd'hui. »
- Pagination (si > 5) : `Row SpaceBetween` : « Précédent » (secondary sm, `ChevronLeft`, désactivé page 1) | « Page {current} / {total} » 12 muted | « Suivant » (`ChevronRight`, désactivé dernière page).

#### 5.7.1 Ligne de dépense (réutilisée par la recherche)
Bouton pleine largeur, `py 12`, `rounded-lg`, `gap 12` : pastille 16 px | colonne : description (14/500 tronquée, défaut « Dépense ») ; dessous 12 px muted : icône 12 px `Mic` (source vocale) ou `Pencil` (sinon) + « {date courte} · {catégorie ou « Sans catégorie »} » | montant 14/600 tabulaire. Toucher → édition (§8).

### 5.8 Alertes de budget par catégorie (`CategoryBudgetAlerts`)
- Calcul indépendant : mois **calendaire courant** (pas le mois affiché), dépenses confirmées par date d'achat ; alloué = `budget_mensuel` sinon `budget_annuel / 12` ; alerte si alloué > 0 et utilisé ≥ alloué ; tri par taux décroissant.
- `card-soft p-4`, bordure `destructive/40`, fond `destructive/5`. Titre 14/600 avec `AlertTriangle` 16 `destructive` : « Budget de catégorie atteint » (1) ou « {count} budgets de catégorie atteints ».
- Lignes (fond `background/70`, `rounded-lg`, `p 10`) : pastille 14 + nom 14/500 | à droite « {utilisé} » 14/600 `destructive` et dessous 11 px muted « +{dépassement} / {alloué} prévus » (préfixe seulement si dépassement > 0).
- Lien 12/600 `primary` « Ajuster mes budgets par catégorie » → `/settings` (onglet par défaut, **pas** l'onglet Catégories).
- Notification système : une fois par mois et par catégorie (mémoire locale `voxbudget.category-alerts`), si la permission est accordée : titre « Budget atteint », corps « {nom} : {used} dépensés sur {allocated} prévus ce mois-ci. »

### 5.9 Bannière d'astuces (`TipsBanner`)
- 19 astuces FR + 19 EN (tableaux séparés dans le composant, pas dans les dictionnaires) ; `{sym}` remplacé par le symbole de devise. Liste complète FR : voir Annexe B.
- **Conditions d'apparition** (mémoire locale `voxbudget.tip` = nombre d'astuces vues, `voxbudget.tip.day` = dernier jour affiché) :
  - Toutes vues (≥ 19) → plus jamais affichée.
  - Moins de 5 vues (**phase de prise en main**) : affichée à chaque ouverture ; bouton « Astuce suivante » enchaîne les astuces 1→5 dans la même session ; après la 5e, la bannière disparaît.
  - À partir de la 6e : **une seule astuce par jour** (si le jour du dernier affichage = aujourd'hui, rien) ; bouton « Compris ».
  - Le bouton `X` (« Masquer l'astuce ») marque l'astuce comme vue et masque la bannière.
- Rendu : `card-soft p-4`, bordure `accent/40`, `Row wrap gap 12` : pastille ronde `accent/20` (p 6) avec `Lightbulb` 16 `accent-foreground` ; texte 14 : « **Astuce** · » (500) + astuce ; à droite bouton `secondary sm` + bouton icône ghost 32 px `X`.
- **ÉCART natif** : l'astuce n°2 (installation PWA sur l'écran d'accueil) n'a pas de sens sur mobile natif — à retirer/remplacer.
- Note : le « jour » est calculé en UTC (`toISOString`), pas en heure locale.

### 5.10 Bandeau « bilan arrivé » (`MonthlyReportNotice`)
- Apparaît pendant les **3 derniers jours du mois** (jour > dernier jour − 3) pour le mois courant ; en décembre, également pour l'année. Masqué si la période figure dans `report_views` (déjà vu).
- Bouton `card-soft p-3`, bordure `accent/40`, `gap 12` : pastille ronde 36 px `accent/20` avec `BellRing` 16 ; titre 14/600 « Votre bilan de {Août 2026 | l'année 2026} est arrivé » ; sous-titre 12 muted « Où est parti votre argent, et quoi améliorer ce mois-ci. » / « … cette année, et quoi améliorer. » ; à droite « Voir » 12/500 `primary`.
- Toucher → enregistre la vue, ouvre `ReportDialog` (§10.1) avec le calcul serveur `GET /api/public/report/monthly?month=YYYY-MM` (ou `?year=YYYY`).

### 5.11 Bannières de notifications (`NotificationCenter`)
- Interroge `user_notifications` non vues **toutes les 30 s**.
- `channel = in_app` → bannière : `card-soft p-3` bordure `accent/40` : pastille 36 px `accent/20` avec `ListChecks` (rappel) ou `BellRing` (bilan) ; titre 14/600 + corps 12 muted (textes fournis par le serveur) ; « Voir » (sauf rappel) ; bouton ghost `X` (« Masquer la notification ») → marque vue.
- Toucher : rappel → marque vue ; bilan → ouvre `ReportDialog` avec le bilan contenu dans la notification (`payload`) + marque vue. Sans `payload` → message d'erreur du dialogue.
- `channel = push` non délivrées → affichées en **notification système** via le service worker (si permission), puis marquées délivrées et vues.

### 5.12 Rappel quotidien (`DailyReminder`, invisible)
Si le rappel est activé et **aucune dépense confirmée aujourd'hui** : vérifie toutes les 5 min (et à l'ouverture) ; après **20 h** heure locale, une fois par jour (mémoire `voxbudget.reminder`), si permission accordée : notification « nemla » / « Bilan de la journée : dictez vos dépenses du jour en 30 secondes. » **ÉCART natif majeur** : sur le web, ce rappel ne fonctionne **que si l'app est ouverte** ; sur mobile il faut une notification locale programmée (voir §J).

---

## 6. Bouton flottant « Ajouter » (FAB) et dictée (`VoiceFab`)

### 6.1 FAB
- Position fixe en bas à droite : 20 px du bas et de la droite (32 px ≥ 640), z 30. Pilule hauteur **56 px**, `pl 20 pr 24`, fond `primary`, `shadow-lg`, icône `Plus` 20 px + « Ajouter » 14/600. (Le web applique `env(safe-area-inset-bottom)` en **padding** interne — en natif, utiliser l'inset de navigation comme **marge**.) aria « Ajouter une dépense ».
- Toucher → menu popover **au-dessus**, aligné à droite, largeur 224 px, `p 4`, 3 entrées (`px 12 py 10`, `gap 12`, icône 16 `primary`) :
  1. **« Dicter mes dépenses »** (`Mic`) ; sous-texte 12 px : si crédits épuisés → « Crédits épuisés ce mois » (600 `destructive`) et entrée **désactivée** (opacité 50 %) ; sinon « 30 s max — » (muted) + « {n} dictée{s} restante{s} » (600) en **ambre `#CE7C09` si ≤ 3**, sinon **vert `#218352`**.
  2. **« Saisie manuelle »** (`Pencil`) ; sous-texte « Illimitée » (muted). → dialogue de dépense en création (§8).
  3. **« Importer un relevé »** (`FileUp`) ; sous-texte : épuisé → « Crédit épuisé ce mois » (`destructive`, désactivé) ; sinon « PDF, Excel ou CSV — » + « {n} import restant » (vert). → §9.
- Quotas (`rpc ensure_month_quota`) : dictées = **nombre de jours du mois** (28 à 31), imports = **1 par mois**, saisie manuelle illimitée ; remise à zéro le 1er. **ÉCART** : la description produit parle de « 10 crédits/mois par défaut ».

### 6.2 Dialogue de dictée
- Dialog largeur max 384 px. Titre « Dicter mes dépenses » ; description « 30 secondes maximum. Énumérez librement vos dépenses : montant, lieu et date si besoin. »
- Colonne centrée, `gap 16`, `py 8` :
  - **Bouton rond 80 px** :
    | État | Variante / couleur | Icône | Texte sous le bouton |
    |---|---|---|---|
    | Repos | `default` (`primary`) | `Mic` 28 px | « Appuyez pour démarrer l'enregistrement. » |
    | Enregistrement | `destructive` | `Square` 24 px | « J'écoute — appuyez pour arrêter. » |
    | Traitement (désactivé) | `default` | `Loader2` 28 px en rotation | « Transcription et classement en cours… » |
    aria : « Démarrer l'enregistrement » / « Arrêter l'enregistrement ».
  - Texte d'état 14 muted centré (ci-dessus).
  - **Pendant l'enregistrement uniquement** : barre de progression pleine largeur = secondes écoulées / 30 ; dessous « {s}s / 30s » 12 muted tabulaire. **C'est un compteur croissant (0 → 30), pas un compte à rebours.**
  - Erreur d'enregistrement : texte 14 `destructive` (« Accès au micro refusé. » ou « Enregistrement trop court ou micro muet. Réessayez. » — **non traduits**).
- Fermeture **impossible pendant l'enregistrement** (X et clic extérieur ignorés). Possible pendant le traitement (le traitement continue).
- **Aucune animation** (pas d'onde, pas de pulsation) en dehors du spinner.

### 6.3 Enregistreur (`useRecorder`) — à reproduire à l'identique
| Paramètre | Valeur |
|---|---|
| Durée max | **30 s** ; arrêt automatique quand le compteur (incrémenté chaque seconde) atteint 30 |
| Capture | micro mono, blocs de 4096 échantillons à la fréquence native (souvent 48 kHz) |
| **Détection de silence** | RMS du bloc < **0.01** (≈ −40 dBFS) → les échantillons sont comptés comme silence ; tout bloc non silencieux remet le compteur à 0 ; vérification toutes les **500 ms** ; arrêt automatique dès **3 s de silence continu** — **y compris au tout début** (si l'utilisateur ne parle pas dans les 3 premières secondes, l'enregistrement s'arrête) |
| Encodage | **WAV PCM 16 bits mono 16 kHz** (décimation par échantillon le plus proche, sans filtre), en-tête RIFF 44 octets |
| Rejet | fichier < **4096 octets** → erreur « Enregistrement trop court ou micro muet. Réessayez. » |
| Permission refusée | erreur « Accès au micro refusé. » |

### 6.4 Traitement après l'arrêt
1. Envoi du WAV dans le stockage Supabase `voice-notes/{userId}/{timestamp}.wav` (si échec : on continue sans).
2. `POST /api/public/voice/process` (multipart : `audio`, `audio_url`, `lang` = langue de dictée). Le serveur transcrit, **supprime l'audio** du stockage, extrait les dépenses (non confirmées) et renvoie `voice_entry_id`, `transcript`, `expenses`.
3. Succès : toast « {n} dépense(s) détectée(s) », fermeture du dialogue, ouverture de la fiche de revue (§7) avec ce `voice_entry_id`.
4. Erreur : toast avec le message serveur (en français) ou « Traitement impossible ». Dans tous les cas : rafraîchissement des quotas.

---

## 7. Fiche de revue vocale / import (`VoiceReview`)

Insérée dans le dashboard (pas un dialogue), défilement doux jusqu'à elle.

### 7.1 Chargement
- Dépenses non confirmées : celles de la dictée (`voice_entry_id`) ou la liste d'identifiants (import), triées par création ; description reformatée par `prettyLabel` (retire CB/CARTE/PRLV/VIR…, références, dates jj/mm, met en minuscules si > 70 % de majuscules, majuscule initiale).
- Transcription de la dictée (vide pour un import).
- Dépenses confirmées depuis **aujourd'hui − 2 mois** (pour les doublons).
- Règles marchands existantes (`merchant_rules`) pour les clés marchands des lignes.

### 7.2 Structure
`card-soft p-5` :
- Titre h2 16/600 : « Vérifiez les dépenses détectées » (dictée) ou « Vérifiez les dépenses lues dans le fichier » (import).
- Transcription en italique 14 muted entre guillemets « … » (si dictée).
- Liste défilante interne (hauteur max **60 % de l'écran**), `space 12`. Pour chaque ligne, tuile `rounded-lg border p-3` :
  - **Doublon** (si détecté) : bordure ambre `#FFB900`, fond ambre clair (`#FFFBEB` 60 % / sombre `#461901` 20 %) ; message 12/500 ambre (`#BB4D00` / sombre `#FFB900`) avec `AlertTriangle` 14 px :
    - même date : « Attention : « {description} » existe déjà à la même date pour le même montant. Doublon probable. »
    - même mois : « Déjà enregistré ce mois-ci : « {description} » pour un montant similaire. Vérifiez avant de valider. »
    - **Règle** : libellés « identiques » si, après normalisation (minuscules, sans accents, caractères non alphanumériques → espace, mots de plus de 2 lettres), ils sont égaux ou l'un contient l'autre ; **et** |écart de montant| < 0.5. Priorité au même jour, sinon même mois (AAAA-MM).
  - Ligne 1 : Input description (placeholder « Description ») + Input montant (nombre, pas 0.01, 96 px, tabulaire).
  - Si **pas de catégorie** : avertissement ambre « Libellé peu clair : l'IA n'a pas pu deviner la catégorie, choisissez-la ci-dessous. » + ligne 12 muted « Aucune ne convient ? **Créez votre catégorie dans Mon espace** (lien `primary` souligné → `/settings`) puis revenez la choisir ici. »
  - Si une règle apprise existe pour ce marchand : 12 muted avec `Sparkles` 12 `accent` : « Catégorie apprise de vos corrections précédentes ».
  - Ligne 2 : Select catégorie (flex, items avec pastille 12 px, placeholder « Catégorie ») + sélecteur de date (160 px, max aujourd'hui, date future ramenée à aujourd'hui) + bouton icône ghost `Trash2` (« Supprimer »).
- États : chargement « Chargement des dépenses… » avec spinner ; vide « Aucune dépense à valider. »
- Pied (`gap 8`, marge 16) : **Valider** (default, `Check`, désactivé si enregistrement en cours ou liste vide) ; **Plus tard** (ghost).

### 7.3 Interactions
- Éditer description / montant / catégorie / date : local.
- **Supprimer** : suppression **immédiate en base, sans confirmation**.
- **Plus tard** : ferme la fiche ; les dépenses restent dans « Dépenses à valider ».
- **Valider** : pour chaque ligne, enregistrement serveur (montant, description avec majuscule initiale, date, catégorie, `confirmed = true`, `source = "voice"` — **même pour un import**) ; statut de la dictée → `confirmed` ; apprentissage : règle marchand mise à jour pour chaque ligne ; journal de correction si catégorie ou montant modifié par rapport à la proposition IA. Toast « Dépenses enregistrées », fermeture, rafraîchissement global. Erreur : toast message ou « Enregistrement impossible ».
- Validation des champs : **aucune** (montant vide → 0, négatif accepté).
- **ÉCART** : pas de « regroupement » des achats d'une même catégorie dans cette fiche (voir §5.6).

Après la première dépense (confirmée du jour ou en attente), le dialogue de choix du débit (§11.2) peut s'ouvrir.

---

## 8. Dialogue de dépense (`ExpenseDialog`) — création et édition

Ouvert par : FAB « Saisie manuelle » (création), toucher d'une ligne de dépense (édition).

Dialog standard (max 512 px). Titre « Nouvelle dépense » / « Modifier la dépense ». Formulaire `space 16` :
1. **Montant ({symbole})** : nombre, pas 0.01, min 0, **requis**.
2. **Catégorie** : Select ; 1re option « Sans catégorie », puis catégories avec pastille 12 px. Valeur par défaut en création : **la première catégorie** (ordre `ordre`, donc « Courses »), pas « Sans catégorie ».
3. **Description** : texte libre.
4. **Date** : sélecteur, max aujourd'hui (future ramenée à aujourd'hui), défaut aujourd'hui.
5. **Moyen de paiement** : Carte bancaire (défaut) / Virement / prélèvement / Espèces / Chèque (**libellés non traduits**). Si carte **et** débit différé : 12 muted avec `CreditCard` 14 : « Débit différé : sera réellement prélevé le {date longue}. »
6. **Type de dépense** : ToggleGroup pleine largeur « Variable » | « Fixe » (icône `Repeat`) — l'élément actif est **doré** (`accent`). Si « Fixe » : encadré bordé `p 12` : « Elle sera comptée automatiquement chaque période, sans la redicter. » + Select « Chaque mois » / « Chaque année ».
7. Boutons : en édition, bouton ghost icône `Trash2` texte `destructive` à gauche ; **Enregistrer** (default, flex, désactivé pendant l'envoi).

Ouverture en édition : recharge le moyen de paiement ; si une charge fixe porte le même nom (insensible à la casse), le type s'ouvre sur « Fixe » avec sa fréquence.

À l'enregistrement :
1. Sauvegarde serveur (`confirmed = true`, `source = "manual"`, description avec majuscule initiale).
2. **Propagation du renommage** (édition, libellé changé) : compte les autres dépenses au même ancien libellé ; si > 0, confirmation système : « {n} autres dépenses portent le libellé « {previousLabel} ». Renommer aussi en « {newLabel} » ? » (ou « Une autre dépense porte… ») → renommage groupé.
3. Apprentissage : si catégorie choisie et (création ou catégorie changée) → règle marchand.
4. Toast « Dépense ajoutée » / « Dépense modifiée ». Journal de correction si montant/catégorie modifiés.
5. Type **Fixe** → crée la charge fixe (nom = description ou « Charge fixe », jour = jour de la date borné 1–28) ou met à jour celle de même nom ; toast « Ajoutée aux charges fixes mensuelles » / « … annuelles ». Repassage de Fixe à Variable → suppression de la charge de même nom, toast info « Retirée de vos charges fixes ».
6. Fermeture + rafraîchissement.
- Suppression : confirmation système « Supprimer cette dépense ? » → toast « Dépense supprimée ».
- Erreurs : « Enregistrement impossible », « Ajout impossible », message brut.

---

## 9. Import de relevé (`ImportSheet` — en réalité un dialogue)

- Dialog max 448 px. Titre « Importer un relevé de compte ». Description « Un seul fichier à la fois : PDF, Excel (.xlsx) ou CSV. Les dépenses détectées vous seront proposées avant enregistrement. »
- Encart confiance : bordure `primary/40`, fond `primary/5`, `rounded-xl`, `p 12`, `ShieldCheck` 20 `primary` ; titre 14/600 « Votre fichier est anonymisé avant lecture » ; texte 12 « IBAN, numéros de compte ou de carte, nom du titulaire, email, téléphone et adresse sont supprimés avant tout envoi à l'IA. Le fichier n'est jamais conservé : seules les dépenses lues sont enregistrées. »
- Ligne : bouton `secondary` « Choisir un fichier » (`FileUp`) + nom du fichier choisi (12 muted tronqué) ou « — ». Types acceptés : `.pdf, .csv, .txt, .xls, .xlsx, .xlsm, .ods`.
- Bouton principal « Lire le fichier » (désactivé sans fichier ou pendant le traitement ; pendant : spinner + « Lecture et anonymisation en cours… »).
- Fermeture impossible pendant le traitement.
- `POST /api/public/import/file` (multipart `file`, `lang`). Contrôles **serveur** : fichier manquant (400), **> 8 Mo** (413), quota d'import atteint (429, message expliquant la remise à zéro le 1er), aucune dépense détectée (422), **plus de 2 mois calendaires** couverts (422 : « Ce fichier couvre N mois (…). Un import traite une seule période à la fois (2 mois maximum)… »), 400 lignes max traitées.
- Succès : toast « {n} dépense(s) lue(s) — vérifiez et validez leur catégorie » (**non traduit**), fermeture, ouverture de la fiche de revue (§7) avec les identifiants importés. Erreur : toast message ou « Import impossible » (**non traduit**).
- **ÉCART** : le « glisser-déposer » annoncé n'existe pas dans l'app (sélecteur de fichier uniquement). L'anonymisation est faite **côté serveur** (le fichier brut transite par le serveur).

---

## 10. Bilans

### 10.1 Dialogue de bilan (`ReportDialog`)
- Dialog max 448 px, hauteur max 85 % avec défilement. Titre « Bilan de {période} » (première lettre majuscule).
- États : « Calcul du bilan… » (muted) ; erreur « Bilan indisponible pour le moment. » (`destructive`).
- Contenu (`space 16`) :
  1. Encadré `muted/50`, `rounded-xl`, `p 16` : « Total dépensé » (12 muted) ; montant Outfit 30/600 tabulaire ; si total précédent > 0 : 12 muted tabulaire avec `TrendingUp` 14 `destructive` (hausse) ou `TrendingDown` 14 `primary` (baisse) : « +{delta} vs période précédente » / « {delta} vs période précédente ».
  2. « Où part l'argent » (h3 14/600) : lignes « {nom} » | « {montant} · {part} % » (12 muted).
  3. « Ce qu'il faut retenir » : liste à puces 14 muted (textes **générés côté serveur en français uniquement**).
  4. « Pistes d'amélioration » : idem.
  5. Bouton pleine largeur « J'ai compris ».

### 10.2 Historique (`ReportHistory`, onglet « Bilans » de Mon espace)
- `card-soft p-5`. Titre avec `History` 16 `primary` : « Historique des bilans » ; texte « Retrouvez le bilan d'un mois ou d'une année passée à tout moment. »
- Périodes candidates : les **24 derniers mois écoulés** + les **3 dernières années écoulées**, ne gardant que celles qui contiennent au moins une dépense confirmée.
- États : « Chargement… » ; vide « Aucun bilan disponible pour l'instant : vos bilans apparaîtront ici à la fin du premier mois rempli. »
- Liste défilante (max 420 px) groupée par année décroissante : en-tête d'année **collant** 12/600 MAJUSCULES muted ; groupe bordé `rounded-xl` avec séparateurs ; élément `px 12 py 10` : libellé 14/500 (« Août 2026 » / « l'année 2025 ») + badge « Année » (fond `accent/20`, 10/600 MAJUSCULES) pour un bilan annuel + `ChevronRight`. Dans un groupe : bilan annuel en premier, puis mois décroissants.
- Toucher → `ReportDialog`.

---

## 11. Débit différé

### 11.1 Formulaire commun (`DebitForm`)
- Deux cartes de choix côte à côte (grille 2 colonnes, `gap 8`), `rounded-xl border p-12` : « Débit immédiat » / « Chaque achat part du compte tout de suite. » et « Débit différé » / « Vos achats carte sont prélevés en une fois. » ; sélectionnée = bordure `primary` + fond `primary/10`.
- Si différé, encadré bordé `p 12` :
  - **Ma banque** (Select, placeholder « Choisir ma banque ») + aide 12 muted. Banques et règles préremplies :
    | id | Nom | Arrêté | Prélèvement |
    |---|---|---|---|
    | bnp | BNP Paribas | 31 | 5 |
    | sg | Société Générale | 31 | 3 |
    | credit-agricole | Crédit Agricole | 25 | 1 |
    | lcl | LCL | 31 | 5 |
    | cic | CIC | 31 | 5 |
    | credit-mutuel | Crédit Mutuel | 31 | 5 |
    | caisse-epargne | Caisse d'Épargne | 25 | 1 |
    | banque-populaire | Banque Populaire | 25 | 1 |
    | banque-postale | La Banque Postale | 31 | 1 |
    | hsbc | HSBC | 31 | 5 |
    | autre | Autre banque | 31 | 5 |
  - Grille 2 colonnes : **Arrêté le** (nombre 1–31) | **Prélevé le** (nombre 1–28).
  - « À partir de quand appliquer cette règle ? » : 3 cartes (même style) : « À partir d'aujourd'hui » / « Les dépenses déjà enregistrées gardent leur date. » ; « Toutes mes dépenses » / « La règle s'applique aussi aux dépenses passées. » ; « À partir d'une date précise » / « Utile si vous avez changé de carte en cours d'année. » (+ sélecteur de date, max aujourd'hui).
  - Exemple 12 muted avec `Info` 14 : « Exemple : un achat carte fait aujourd'hui sera prélevé le {date}. Seuls les paiements par carte sont différés — virements, espèces et chèques restent à leur date. »
- Règle de calcul (`prelevementDate`) : si immédiat ou date < date d'application → date d'achat ; sinon, arrêté = min(jour d'arrêté, nb de jours du mois) ; si jour d'achat > arrêté → mois suivant ; puis +1 mois ; jour = min(max(1, jour de prélèvement), nb de jours du mois cible).
- Enregistrement : mise à jour du profil (`banque`, `debit_differe`, jours, `debit_depuis`, `debit_choix_fait = true`) puis `rpc apply_debit_rule` (recalcule la date de prélèvement des dépenses carte, renvoie le nombre déplacé).

### 11.2 Dialogue « Votre carte : débit immédiat ou différé ? » (`PaymentModeGate`)
- S'ouvre sur le dashboard dès qu'il existe une dépense (confirmée aujourd'hui ou en attente) **et** que le choix n'a jamais été fait.
- Dialog max 448 px, hauteur max 92 %, défilement. Titre avec `CreditCard` 20 `primary`. Description « Une seule question avant de valider votre première dépense… modifiable à tout moment dans Mon espace. » + formulaire + bouton pleine largeur **Continuer** (désactivé si différé sans banque).
- Succès : toast « Choix enregistré — modifiable dans Mon espace », invalidation de toutes les données. Peut être fermé sans choisir (réapparaît plus tard).

### 11.3 Section « Ma carte bancaire » (Mon espace)
Même formulaire + bouton **Enregistrer**. Toast : « Débit immédiat enregistré » | « Règle appliquée aux dépenses à partir du {date} » | « Règle appliquée à toutes vos dépenses, passées et à venir » ; description si > 0 : « {count} dépense(s) carte replacée(s) à sa date de prélèvement. »

---

## 12. Mon espace — route `/settings`

`AppHeader`, puis conteneur standard (`pb 64`).

### 12.1 En-tête et onglets
- Ligne : bouton ghost sm `ArrowLeft` « Retour » (→ `/dashboard`, ou ferme le panneau ouvert) | titre Outfit 20/600 « Mon espace ».
- Tabs pleine largeur, 4 onglets égaux : **« Profil & budget »** | **« Bilans »** | **« Crédits »** (non traduit) | **« Catégories »**. Changer d'onglet ferme le panneau ouvert.

### 12.2 Onglet « Profil & budget » — liste d'entrées
Chaque entrée = bouton `card-soft p-4`, `gap 12` : pastille ronde `primary/10` (p 8) avec icône 20 `primary` ; titre 14/600 + sous-titre 12 muted ; `ChevronRight` 20 muted. Toucher → **remplace la liste par le panneau** (fondu 200 ms), « Retour » revient à la liste.

| Entrée | Icône | Sous-titre | Panneau |
|---|---|---|---|
| Gérer mon budget | `Wallet` | « Un montant, mensuel ou annuel. » (non traduit) + si budget > 0 : « {montant} / mois » (12/500, « / mois » non traduit) | §12.3 |
| Mes charges fixes | `Repeat` | « Loyer, abonnements, assurances — reportés chaque mois. » (non traduit) | §12.4 |
| Mon profil | `User` | « Pseudo, âge, sexe, situation familiale. » (non traduit) | §12.5 |
| Préférences | `Settings` | « Devise, langue, thème et rappels. » (non traduit) | §12.6 |
| Ma carte bancaire | `CreditCard` | « Débit immédiat ou différé : cela change la date… » (non traduit) | §11.3 |

Puis **Zone dangereuse** : encadré `rounded-xl`, bordure `border` 60 %, fond `muted/30`, `p 20` : titre 14/500 muted « Zone dangereuse » ; 12 muted « Supprimer votre compte et vos données est irréversible. » ; bouton ghost sm `Trash2` « Supprimer mon compte ».
- Suppression : confirmation système « Supprimer définitivement votre compte et toutes vos données ? » → fonction serveur `deleteMyAccount` (purge : dépenses, occurrences ignorées, charges fixes, rattrapages, votes marchands, dictées, catégories, archive `deleted_accounts`, puis compte Auth) → déconnexion → `/`. Erreur : « Un compte administrateur ne peut pas être supprimé » ou « Suppression impossible ».

### 12.3 Panneau « Gérer mon budget » (`BudgetSheet` section budget)
`card-soft p-5`, `space 16` : titre « Gérer mon budget » + « Un seul montant à indiquer : ce que vous ne voulez pas dépasser, par mois ou par an. »
- Label « Montant du budget ({symbol}) » ; Input nombre (min 0, pas 10, clavier décimal) ; placeholder « Estimé par l'IA : {value} / mois » (si historique) sinon « Aucun budget ».
- ToggleGroup « Par mois » | « Par an » (actif doré).
- Récapitulatif (grille 3 colonnes, fond `muted/40`, bordure, `rounded-xl`, `p 12`, centré) : « Budget / mois » | « Charges fixes » | « Reste » (11 px muted / 14/600 tabulaire ; « — » sans budget ; reste négatif en `destructive`). Budget/mois = montant (ou montant / 12 si « Par an ») ; charges = mensuelles + annuelles / 12.
- Boutons (1 colonne mobile) : **Enregistrer** (`Check`, désactivé si montant ≤ 0) ; **Estimer pour moi** (secondary, `Wand2`, désactivé sans historique). Aide 12 muted : « « Estimer pour moi » calcule une proposition… » ou « Pas encore assez de données pour une estimation fiable. »
- Bouton outline pleine largeur `ListTree` « Gérer mon budget par catégorie » → onglet Catégories.
- Enregistrer : stocke mensuel + annuel (l'un dérivé de l'autre, arrondi au centime) + `budget_periode` ; toast « Budget enregistré ». **On ne peut pas remettre le budget à 0** (validation > 0).
- Estimer : `GET /api/public/budget/suggest` → passe en « Par mois » avec la valeur ; toast « Estimation : {value} / mois » ; sans résultat : « Pas encore assez d'historique pour estimer votre budget. » ; erreur « Estimation impossible ».

### 12.4 Panneau « Mes charges fixes » (`BudgetSheet` section charges → `RecurringSection`)
Titre « Mes charges fixes » + « Loyer, abonnements, assurances… déclarés une fois, recomptés chaque mois. » puis 3 cartes :
1. **Ajouter mes charges fixes** : texte d'aide ; Textarea 3 lignes (placeholder « Netflix 15€, Spotify 10€, loyer 750€ le 5… ») ; boutons **Analyser** / **Dicter** ↔ **Arrêter ({seconds}/{maxSeconds}s)** / ghost **Saisie manuelle** (`Plus`, bascule un encadré contenant « Nouvelle ligne à remplir » qui ajoute une ligne vide : nom vide, 0, mensuelle, jour 1, 1re catégorie). L'analyse **ajoute** aux brouillons ; aucune charge → toast info « Aucune charge fixe détectée ».
2. **Vérifiez avant d'enregistrer** (si brouillons) : tuile par charge : Input nom (placeholder « Nom ») + montant (min 0) ; Select catégorie (avec pastilles, min 160 px) + Select fréquence (Mensuelle/Annuelle) + « Le » + Input jour (1–28, 64 px) + `Trash2` (« Retirer »). Bouton pleine largeur **Enregistrer mes charges fixes**, désactivé si une ligne a un nom vide ou un montant ≤ 0. Succès : toast « Charges fixes enregistrées ».
3. **Mes charges fixes** : titre + « {count} charge(s) · {total} / mois en moyenne » ; pagination **6 par page** en haut à droite (boutons outline 32 px, « {page}/{total} ») ; récapitulatif 2 colonnes « Total / mois » | « Total / an » ; liste (max 352 px) : pastille 12 (défaut `#64748B`) | nom 14/500 + « Mensuelle|Annuelle · le {jour} · {catégorie} » 12 muted | montant 14/600 | bouton `Trash2` `destructive` (« Supprimer {nom} », **sans confirmation**) → toast « Charge fixe supprimée ». Vide : « Aucune charge fixe pour le moment. »

### 12.5 Panneau « Mon profil »
`card-soft p-5` : titre + texte avec `ShieldCheck` 16 `primary` « Ces informations restent privées, servent uniquement à personnaliser vos bilans, et ne sont jamais revendues. » ; bouton secondary sm `Pencil` « Modifier mes informations ».
- **Lecture** : grille de 4 tuiles (`rounded-xl border p-12`) : Pseudo, Âge, Sexe, Situation familiale (avec icône `accent`) ; valeur vide → « Non renseigné ».
- **Édition** : Pseudo (+ chips « Suggestions : » si pris), Âge (14–120), Sexe (Femme/Homme), Situation (4 options + icônes) ; boutons « Enregistrer mon profil » et ghost « Terminé ».
- Enregistrer : vérifie l'unicité du pseudo → si pris : toast erreur « Ce pseudo est déjà pris. Choisissez-en un autre. » + suggestions, rien n'est enregistré ; sinon mise à jour (y compris la devise courante) → toast « Profil enregistré », l'avatar de l'en-tête se met à jour.

### 12.6 Panneau « Préférences »
`card-soft p-5`, titre « Préférences » ; grille (1 colonne mobile) :
- **Devise (pays)** : Select « {pays} · {symbole} » (10 devises, §I.5). Changement → toast « Devise mise à jour », enregistrement profil, **rechargement complet de l'app**. Aucun montant n'est converti (affichage seul).
- **Langue de l'application** : Français / English → stockage local + **rechargement complet**. Non enregistrée côté serveur.
- **Langue de dictée** : Français / English → stockage local ; toast « Langue de dictée : français » ou « Dictation language: English » (en dur).
- **Apparence** (section, marge 16) : « Apparence » 14/600 ; 2 boutons en grille : `Sun` « Clair » | `Moon` « Sombre » ; sélectionné = bordure `primary`, fond `primary/10`, texte `primary`. Application immédiate.
- **Rappel quotidien** : ligne bordée `rounded-xl p-12` : « Rappel quotidien à 20 h » + « Uniquement si aucune dépense n'a encore été saisie ce jour-là. » | Switch. Activation → demande de permission de notification ; « Votre navigateur ne gère pas les notifications. » / « Notifications refusées par le navigateur. » ; sinon mise à jour profil + toast « Rappel activé pour 20 h » / « Rappel désactivé ».
- Encadré `accent/40` bordure, fond `accent/10`, 12 px : « **Important pour recevoir les notifications :** installez nemla sur votre écran d'accueil… » (**spécifique web — à supprimer en natif**).

### 12.7 Onglet « Bilans »
`ReportHistory` (§10.2).

### 12.8 Onglet « Crédits » (`CreditsPanel`)
`card-soft p-5` : « Mes crédits du mois » + « Remis à zéro le 1er de chaque mois : vous recevez autant de dictées que le mois compte de jours. » ; 3 tuiles (1 colonne mobile, `rounded-xl border`, fond `card/60`, `p 12`) :
- « Dictées 30 s » | « {utilisé}/{limite} » ; progression (8 px) ; « {left} {unit}(s) restant(s) » ou « Crédits épuisés ce mois ».
- « Import de relevé » | idem (unité « import »).
- « Saisie manuelle » | « ∞ » ; progression vide ; « Illimitée » (vert).
- Couleur du texte restant : 0 → `destructive` ; ≤ 34 % de la limite → ambre `#CE7C09` ; sinon vert `#218352`.
- Chargement : « Chargement… ».

### 12.9 Onglet « Catégories »
`card-soft p-4` : titre « Catégories » + « {count} catégories. Choisissez celles qui apparaissent sur le tableau de bord (les autres sont regroupées dans « Autre ») et fixez un budget mensuel ou annuel par catégorie. »
- Ajout : Input « Nouvelle catégorie » (Entrée valide) + bouton icône `Plus` (« Ajouter une catégorie », désactivé si vide). Couleur attribuée automatiquement (`pickUnusedColor`), type `variable`, ordre = max + 1. Toast « Catégorie ajoutée ». Pas de contrôle de doublon de nom.
- Liste (`space 12`), tuile par catégorie `rounded-xl border p-12` :
  - Ligne : pastille 16 + nom 14/500 | à droite « Visible » (12 muted) + Switch (sauvegarde silencieuse) | bouton `Trash2` `destructive` 32 px **uniquement pour les catégories non par défaut** (sans confirmation ; toast « Catégorie supprimée »).
  - Si suggestions de nom : « Renommer en : » + chips secondary pilules 28 px → renomme (toast « Catégorie mise à jour »).
  - Si catégorie non budgétable (type fixe ou nom contenant impôt/impot/assurance/loyer/taxe) : « Charge fixe : le montant est imposé, il n'y a pas de budget à définir ici. »
  - Sinon : grille [1fr, auto] : « Budget ({symbol}) » Input (min 0, pas 10, placeholder « Estimé par l'IA : {value} / mois » ou « Aucun budget »), **sauvegarde à la perte du focus** (vide = supprime le budget) | « Période » Select 112 px « / mois » | « / an » (sauvegarde immédiate si montant). Bouton secondary 32 px `Wand2` « Estimer » (désactivé sans historique ou estimation ; remplit mensuel ou ×12 et sauvegarde) + « Pas encore assez de données pour une estimation fiable. » si indisponible.
  - Un seul des deux budgets est stocké (l'autre mis à `null`).

---

## H. Récapitulatif des états par écran

| Écran | Chargement | Vide | Erreur | Succès |
|---|---|---|---|---|
| Auth | bouton « Un instant… » | — | toast message/« Connexion impossible » | toast + navigation |
| Reset | bouton désactivé | texte « Ouvrez cette page depuis le lien… » | toast « Mise à jour impossible » | toast + `/dashboard` |
| Onboarding | spinner sur « Analyser » | carte de revue masquée | toasts | toast « Configuration enregistrée » |
| Dashboard | aucun indicateur (données précédentes conservées) | textes « Aucune dépense… » par bloc | **non affichée** | — |
| Recherche | — | « Aucune dépense trouvée. » | non affichée | compteur résultats |
| Dictée | spinner + texte | — | texte rouge / toast | toast + fiche de revue |
| Fiche de revue | spinner + texte | « Aucune dépense à valider. » | toast | toast « Dépenses enregistrées » |
| Import | spinner + texte | « — » (pas de fichier) | toast (messages serveur) | toast + fiche de revue |
| Bilan | « Calcul du bilan… » | sections masquées | « Bilan indisponible pour le moment. » | contenu |
| Historique | « Chargement… » | texte dédié | non affichée | liste |
| Crédits | « Chargement… » | — | non affichée | tuiles |
| Mon espace (requêtes) | aucun indicateur | — | toasts sur actions | toasts |

---

## I. Internationalisation

### I.1 Mécanisme
- Fonction `t(fr, vars?)` (`src/lib/i18n.ts`) : **la chaîne française est la clé**. En FR, la chaîne est renvoyée telle quelle ; en EN, recherche dans le dictionnaire fusionné, **repli sur le français** si absente.
- Langues : **fr** (défaut) et **en**. Langue d'interface stockée localement (`voxbudget.ui`), **non synchronisée avec le compte** ; un changement recharge l'app.
- Langue de dictée (IA) **distincte** : `voxbudget.lang` (`fr`/`en`), envoyée en paramètre `lang` aux endpoints IA.
- Dictionnaires anglais (`src/lib/i18n/`), fusionnés dans cet ordre (le dernier gagne) :
  | Fichier | Domaine | Entrées |
  |---|---|---|
  | `en-core.ts` | en-tête, navigation, réglages, import | 68 |
  | `en-dashboard.ts` | tableau de bord, graphiques, bilans, notifications | 96 |
  | `en-expenses.ts` | dictée, revue, dépense | 62 |
  | `en-budget.ts` | budget, charges fixes, catégories, crédits, débit | 118 |
  | `en-marketing.ts` | landing, auth, onboarding | 175 |
  | **Total** | | **519 entrées, 485 clés uniques** |
- **9 clés définies plusieurs fois avec des traductions différentes** (la dernière l'emporte) : « Enregistrement impossible » → *Save failed* (aussi « Saving failed », « Could not save ») ; « Charges fixes » → *Fixed expenses* (vs « Fixed costs ») ; « Pseudo » → *Username* (vs « Nickname ») ; « Femme » → *Woman* ; « Homme » → *Man* ; « Colocation » → *Shared housing* (vs « Flatshare ») ; « Vérifiez avant d'enregistrer » → *Review before saving* ; « Annuelle » → *Yearly* (vs « Annual »). Pour le mobile : une clé = une traduction (retenir la valeur effective ci-dessus).
- Code : **458 chaînes littérales distinctes** passées à `t()` ; **36** entrées de dictionnaire non utilisées ; **9 chaînes utilisées sans traduction EN** : « Crédits », « Devise, langue, thème et rappels. », « Débit immédiat ou différé : cela change la date à laquelle vos achats carte quittent votre compte. », « Loyer, abonnements, assurances — reportés chaque mois. », « Ma carte bancaire », « Pseudo, âge, sexe, situation familiale. », « Un montant, mensuel ou annuel. », « suffisent pour savoir où passe votre argent. » (landing, espace final manquant), « Bonjour {nom} » (exemple en commentaire).
- Appels dynamiques : `t(label)` (sexe, situation), `t(error.message)` (suppression de compte).
- **Chaînes visibles jamais passées à `t()` (restent en français en anglais)** : noms de catégories (base), suggestions de renommage, groupe « Autre » du donut, moyens de paiement, noms de pays des devises, « / mois » (entrée budget), toasts et erreur d'import, erreurs de l'enregistreur micro, messages d'erreur serveur, textes du bilan (points clés et conseils), aria « Mon espace ». Les pages 404/erreur sont en anglais en dur. Les astuces ont leurs propres tableaux FR/EN.
- Recommandation portage : générer des clés stables (ex. `dashboard_today_title`) dans `composeResources/values/strings.xml` et `values-en/strings.xml`, à partir de la table FR→EN effective (script de conversion possible depuis ces fichiers).

### I.2 Interpolation
`{nom}` remplacé par `vars[nom]` (regex `\{(\w+)\}`) ; variable inconnue laissée telle quelle. Variables utilisées : `allocated, amount, budget, count, current, date, description, jour, left, maxSeconds, n, newLabel, nom, periode, previousLabel, pseudo, replacement, s, seconds, symbol, total, unit, used, value, year`.

### I.3 Pluriels
**Pas de moteur de pluriel.** Pratiques observées :
- Notation « (s) » figée : « {count} dépense(s) », « {count} résultat(s) », « {count} charge(s) », « {left} {unit}(s) restant(s) », EN « {count} categor(y/ies) ».
- Pluriel manuel : « {n} dictée{s} restante{s} » avec `s = "s"` si n > 1 (règle française : 0 et 1 au singulier ; EN « dictation{s} left » applique la même règle, donc « 0 dictation left »).
- Choix 1 / plusieurs : alertes budget (« Budget de catégorie atteint » vs « {count} budgets… »), renommage (« Une autre dépense… » vs « {n} autres dépenses… »).
- « {n} import restant » : jamais au pluriel.
Recommandation : utiliser des `plurals` (Compose Resources `pluralStringResource`) — **le rendu changera légèrement** (« 2 dépenses » au lieu de « 2 dépense(s) ») : à valider.

### I.4 Dates
- Locale des dates = langue d'interface : `fr-FR` ou `en-GB`.
- `shortDate` : jour 2 chiffres + mois abrégé (« 05 août » / « 05 Aug »).
- `monthLabel` : mois long + année (« août 2026 » / « August 2026 »), première lettre mise en majuscule par le style dans les titres.
- `longDate` : jour + mois long + année (« 5 octobre 2026 » / « 5 October 2026 »).
- Graphique : mois abrégé (`month: "short"`), initiale sur mobile.
- Dates manipulées en chaînes `AAAA-MM-JJ`, interprétées en heure locale. Attention : quelques calculs utilisent l'UTC (`toISOString`) au lieu de l'heure locale : jour du rappel quotidien, jour des astuces, « aujourd'hui » du formulaire de débit différé, date de départ (aujourd'hui − 2 mois) de la recherche de doublons → décalage possible autour de minuit. Le portage devrait tout calculer en heure locale (`kotlinx-datetime`, `TimeZone.currentSystemDefault()`).

### I.5 Monnaie
- `euro(value)` : `Intl.NumberFormat(locale de la devise, {style: "currency", currency, maximumFractionDigits: 2})`. **La locale vient de la devise, pas de la langue** (en anglais avec EUR : « 1 234,50 € »).
- `compactAmount` (axes) : « 0 {sym} », « {arrondi} {sym} » sous 1 000, « {x,x} k{sym} » (une décimale avec virgule, sans « ,0 ») sous 10 000, « {arrondi} k{sym} » au-delà.
- Devise stockée localement (`voxbudget.currency`) et dans `profiles.devise` ; défaut EUR ; aucune conversion de montants.
- **Devises supportées** (ordre d'affichage) :
  | Code | Pays (libellé FR, non traduit) | Symbole | Locale de formatage |
  |---|---|---|---|
  | EUR | France / Zone euro | € | fr-FR |
  | CHF | Suisse | CHF | fr-CH |
  | GBP | Royaume-Uni | £ | en-GB |
  | USD | États-Unis | $ | en-US |
  | CAD | Canada | $ CA | fr-CA |
  | MAD | Maroc | DH | fr-MA |
  | TND | Tunisie | DT | fr-TN |
  | DZD | Algérie | DA | fr-DZ |
  | XOF | Afrique de l'Ouest (FCFA) | FCFA | fr-SN |
  | AED | Émirats arabes unis | AED | fr-FR |
- **ÉCART natif** : le formatage ICU d'Android (`java.text`/`NumberFormat`) et d'iOS (`NSNumberFormatter`) ne produit pas toujours exactement la même chaîne que le navigateur (espaces insécables fines U+202F, symbole « MAD » vs « DH », position du symbole pour fr-CA/fr-MA…). Pour un rendu **identique Android/iOS**, implémenter un formateur commun en Kotlin (commonMain) plutôt que de déléguer à chaque plateforme, et le valider sur des exemples relevés dans le navigateur.

---

## J. Ce qui est spécifique au web

### J.1 Sans objet sur mobile natif
- **Landing** `/` (hero, démo vocale animée, carrousel « Découvrir » 7 étapes, personas, comparatif, illustrations et captures CDN) et ses animations (`reveal`, `pop-in`, `float-soft`, `mic-pulse`).
- **PWA** : `manifest.webmanifest`, service worker `public/sw.js` (affichage des notifications + ouverture de `/dashboard` au clic), métadonnées `apple-mobile-web-app-*`, `theme-color`.
- **Guide d'installation sur l'écran d'accueil** : astuce n°2 de la bannière et encadré « Important pour recevoir les notifications… » des Préférences.
- **Console d'administration** `/admin` et bouton `ShieldCheck` de l'en-tête (back-office : à garder sur le web).
- Pages 404 / erreur de routeur, balises SEO / Open Graph / Twitter, `robots.txt`.
- Rechargements complets de page (changement de devise ou de langue) → recomposition de l'UI en natif.
- Survols (`hover:`), anneaux de focus clavier (à garder seulement pour l'accessibilité clavier/TalkBack/VoiceOver).
- Réponses HTML d'erreur de déploiement (gérées par `readJson`).

### J.2 Équivalent natif différent
| Fonction | Web | Natif (Android / iOS) — à alerter |
|---|---|---|
| Notifications (rappel 20 h, budget atteint, bilans push) | API `Notification` + service worker, **uniquement quand l'app est ouverte** (vérification toutes les 5 min / 30 s) ; iOS exige l'installation PWA | Notifications **locales programmées** (WorkManager / AlarmManager ; `UNUserNotificationCenter`) pour le rappel et les alertes ; **push** serveur via FCM / APNs pour les bilans. Permissions : `POST_NOTIFICATIONS` (Android 13+), autorisation iOS. Comportement **meilleur** que le web (fonctionne app fermée) : différence à valider. |
| Permission micro | `getUserMedia`, message « Accès au micro refusé. » | `RECORD_AUDIO` (Android, demande runtime) ; `NSMicrophoneUsageDescription` (iOS, texte à rédiger). Gérer « refus définitif » → renvoi vers les réglages système (pas d'équivalent web). |
| Enregistrement audio | Web Audio `ScriptProcessor`, WAV 16 kHz | `AudioRecord` (Android) / `AVAudioEngine` (iOS) via `expect/actual` ; reproduire RMS 0.01, 3 s de silence, 30 s, WAV 16 bits 16 kHz mono. |
| Choix de fichier (import) | `<input type="file">` (pas de glisser-déposer dans l'app) | Storage Access Framework / `ActivityResultContracts.OpenDocument` (Android), `UIDocumentPickerViewController` (iOS) ; types MIME PDF, CSV, XLS/XLSX/XLSM, ODS, TXT. Partage « Ouvrir avec nemla » possible en plus (différence). |
| Sélecteurs de date | `<input type="date">` natif du navigateur | `DatePicker` M3 (Android) — sur iOS, un DatePicker Compose n'a pas l'aspect natif iOS : **différence visuelle Android/iOS à décider** (Compose partout = iso, mais non natif iOS). |
| Select / menus | Radix Select (popover) | `ExposedDropdownMenu` M3 ou feuille de sélection. |
| Confirmations | `window.confirm()` | `AlertDialog` Compose. |
| Toasts | sonner, en haut au centre | Snackbar (en bas par défaut M3) ou composant custom en haut pour rester iso. |
| Stockage local (thème, langue, devise, astuces, alertes vues, rappel) | `localStorage` (clés `voxbudget.*`) | DataStore Preferences (KMP) ; les valeurs sensibles éventuelles en stockage chiffré (EncryptedSharedPreferences / Keychain). |
| Session | Supabase JS (cookies / localStorage) | supabase-kt ou Ktor + stockage sécurisé du jeton (Keystore / Keychain). |
| Liens email (confirmation, mot de passe oublié) | redirection vers `/dashboard`, `/reset-password` | Android App Links + iOS Universal Links (ou schéma custom) : **configuration supplémentaire obligatoire**. |
| Zone sûre | `env(safe-area-inset-bottom)` sur le FAB | `WindowInsets` (navigationBars) ; mode edge-to-edge. |
| Graphiques | recharts (SVG) | Canvas Compose custom (ou bibliothèque KMP) : reproduire barres empilées, ligne pointillée, info-bulle. |
| Défilement vers la fiche de revue | `scrollIntoView({behavior: "smooth"})` | `LazyListState.animateScrollToItem`. |
| Mise à jour périodique (notifications 30 s) | `refetchInterval` | requête au retour au premier plan / push. |

### J.3 Points RGPD / sécurité observés dans le web (à traiter dans le portage)
- L'audio brut est **téléversé dans le stockage Supabase** (`voice-notes/`) avant traitement, puis supprimé par le serveur après transcription (s'il n'y a pas d'erreur avant).
- La transcription et le texte brut sont journalisés lors des corrections (`logClassification` avec `rawTranscript`).
- L'archive de suppression `deleted_accounts` conserve **l'email** (le commentaire parle d'« archive anonyme »).
- La purge ne cite pas explicitement : profil, quotas, règles marchands, notifications, vues de bilans, journaux de classification (supprimés seulement si la base les rattache en cascade à `auth.users` — à vérifier dans les migrations).
- L'anonymisation des relevés est faite **sur le serveur** : le fichier non anonymisé quitte l'appareil.
- Le profil collecte âge, sexe et situation familiale (optionnels).

---

## Annexe A — Clés i18n par fichier (chaînes françaises passées à `t()`, ordre d'apparition)

Généré par script (`/home/claude/scratch/keys_by_file.py`) à partir du code source.

### src/routes/auth.tsx (31)
`Le pseudo « {pseudo} » est déjà pris : nous avons choisi « {replacement} ». Vous pouvez le changer dans Mon espace.` · `Ce pseudo est déjà pris : choisissez-en un autre dans Mon espace.` · `Email envoyé : suivez le lien pour choisir un nouveau mot de passe.` · `Compte créé. Bienvenue !` · `Vérifiez votre boîte mail pour confirmer votre adresse.` · `Connexion impossible` · `Créer mon compte` · `Mot de passe oublié` · `Bon retour` · `Quelques secondes, et vous dictez votre première dépense.` · `Indiquez votre email : nous vous envoyons un lien de réinitialisation.` · `Accédez à votre budget.` · `Email` · `Mot de passe` · `Masquer le mot de passe` · `Afficher le mot de passe` · `Mot de passe oublié ?` · `Pseudo` · `Âge` · `Pseudos disponibles :` · `Sexe` · `Choisir` · `Femme` · `Homme` · `Situation familiale` · `Vos données sont protégées, visibles de vous seul, modifiables à tout moment dans votre espace et jamais revendues.` · `Un instant…` · `Envoyer le lien` · `Se connecter` · `J'ai déjà un compte : me connecter` · `Pas encore de compte ? Créer un compte`

### src/routes/reset-password.tsx (11)
`Mot de passe mis à jour` · `Mise à jour impossible` · `Nouveau mot de passe` · `Choisissez un mot de passe d'au moins 6 caractères.` · `Ouvrez cette page depuis le lien reçu par email pour continuer.` · `Mot de passe` · `Masquer le mot de passe` · `Afficher le mot de passe` · `Un instant…` · `Enregistrer mon mot de passe` · `Retour à la connexion`

### src/routes/_authenticated/onboarding.tsx (17)
`Analyse impossible` · `Transcription impossible` · `Configuration enregistrée` · `Enregistrement impossible` · `Vos charges fixes` · `Tapez ou dictez en vrac : « Netflix 15 €, Spotify 10 €, internet 30 €, loyer 750 € le 5 ».` · `Netflix 15€, Spotify 10€, internet 30€…` · `Analyser` · `Arrêter ({seconds}/{maxSeconds}s)` · `Dicter` · `Vérifiez avant d'enregistrer` · `Catégorie` · `Mensuelle` · `Annuelle` · `Retirer` · `Budget mensuel disponible (€)` · `Enregistrer et voir mon tableau de bord`

### src/routes/_authenticated/dashboard.tsx (64)
`Dépense` · `Budget non défini` · `Dépassement` · `Tendu` · `Vigilance` · `Au beau fixe` · `Dépensé` · `Budget cible` · `{amount} restants sur {budget}` · `Dépassement de {amount} sur {budget}` · `Dépenses à valider` · `Ces dépenses (dictées ou lues dans un fichier) attendent votre confirmation — touchez-les pour vérifier et ajuster leur catégorie.` · `Dépenses importées d'un fichier` · `Dépense vocale` · `{count} dépense(s)` · `Vérifier` · `Rechercher une dépense…` · `Rechercher une dépense` · `Effacer la recherche` · `Filtrer par date` · `Date` · `Chercher un jour précis` · `Rechercher par date` · `Retirer la date` · `{count} résultat(s)` · `Sans catégorie` · `Aucune dépense trouvée.` · `Mois` · `Année` · `Année précédente` · `Mois précédent` · `Année suivante` · `Mois suivant` · `année` · `mois` · `Météo de {periode}` · `Météo de l'année {year}` · `Charges fixes` · `Dépenses mois par mois` · `Dépenses` · `Charges prévues` · `Cible {amount}` · `Touchez une barre pour ouvrir le mois` · `Toutes catégories` · `Aucune catégorie` · `{count} catégorie(s)` · `Filtrer les catégories` · `Tout désélectionner` · `Tout sélectionner` · `Sélectionnez au moins une catégorie pour voir vos dépenses.` · `Moy.` · `Où part l'argent` · `Budgets par catégorie` · `Répartition` · `Budgets` · `Aucune dépense sur cette période.` · `Dépenses du jour` · `Débit différé actif` · `un achat par carte fait aujourd'hui sera prélevé le {date}.` · `Virements, espèces et chèques restent à leur date.` · `Aucune dépense aujourd'hui.` · `Précédent` · `Page {current} / {total}` · `Suivant`

### src/routes/_authenticated/settings.tsx (72)
`Apparence` · `Clair` · `Sombre` · `Ce pseudo est déjà pris. Choisissez-en un autre.` · `Profil enregistré` · `Votre navigateur ne gère pas les notifications.` · `Notifications refusées par le navigateur.` · `Rappel activé pour 20 h` · `Rappel désactivé` · `Supprimer définitivement votre compte et toutes vos données ?` · `Suppression impossible` · `Catégorie ajoutée` · `Catégorie mise à jour` · `Catégorie supprimée` · `Retour` · `Mon espace` · `Profil & budget` · `Bilans` · `Crédits` · `Catégories` · `Gérer mon budget` · `Un montant, mensuel ou annuel.` · `Mes charges fixes` · `Loyer, abonnements, assurances — reportés chaque mois.` · `Mon profil` · `Pseudo, âge, sexe, situation familiale.` · `Préférences` · `Devise, langue, thème et rappels.` · `Ma carte bancaire` · `Débit immédiat ou différé : cela change la date à laquelle vos achats carte quittent votre compte.` · `Zone dangereuse` · `Supprimer votre compte et vos données est irréversible.` · `Supprimer mon compte` · `Ces informations restent privées, servent uniquement à personnaliser vos bilans, et ne sont jamais revendues.` · `Modifier mes informations` · `Pseudo` · `Âge` · `Sexe` · `Situation familiale` · `Non renseigné` · `Suggestions :` · `…` · `Enregistrer mon profil` · `Terminé` · `Devise (pays)` · `Devise mise à jour` · `Langue de l'application` · `Français` · `Langue de dictée` · `Langue de dictée : français` · `Rappel quotidien à 20 h` · `Uniquement si aucune dépense n'a encore été saisie ce jour-là.` · `Important pour recevoir les notifications : ` · `installez nemla sur votre écran d'accueil. Sur Android / Chrome : les 3 petits points en haut à droite → « Installer l'application » (ou « Ajouter à l'écran d'accueil »). Sur iPhone / Safari : bouton Partager → « Sur l'écran d'accueil ». Sans ce raccourci, le téléphone ne peut pas afficher les rappels.` · `{count} catégories. Choisissez celles qui apparaissent sur le tableau de bord (les autres sont regroupées dans « Autre ») et fixez un budget mensuel ou annuel par catégorie.` · `Nouvelle catégorie` · `Ajouter une catégorie` · `Visible` · `Afficher {nom} sur le tableau de bord` · `Supprimer {nom}` · `Renommer en :` · `Charge fixe : le montant est imposé, il n'y a pas de budget à définir ici.` · `Budget ({symbol})` · `Estimé par l'IA : {value} / mois` · `Aucun budget` · `Budget de {nom}` · `Période` · `Période du budget de {nom}` · `/ mois` · `/ an` · `Estimer` · `Pas encore assez de données pour une estimation fiable.`

### src/components/voice-fab.tsx (20)
`{n} dépense(s) détectée(s)` · `Traitement impossible` · `Ajouter une dépense` · `Ajouter` · `Dicter mes dépenses` · `Crédits épuisés ce mois` · `30 s max — ` · `{n} dictée{s} restante{s}` · `Saisie manuelle` · `Illimitée` · `Importer un relevé` · `Crédit épuisé ce mois` · `PDF, Excel ou CSV — ` · `{n} import restant` · `30 secondes maximum. Énumérez librement vos dépenses : montant, lieu et date si besoin.` · `Arrêter l'enregistrement` · `Démarrer l'enregistrement` · `Transcription et classement en cours…` · `J'écoute — appuyez pour arrêter.` · `Appuyez pour démarrer l'enregistrement.`

### src/components/voice-review.tsx (18)
`Dépenses enregistrées` · `Enregistrement impossible` · `Vérifiez les dépenses détectées` · `Vérifiez les dépenses lues dans le fichier` · `Attention : « {description} » existe déjà à la même date pour le même montant. Doublon probable.` · `Déjà enregistré ce mois-ci : « {description} » pour un montant similaire. Vérifiez avant de valider.` · `Description` · `Libellé peu clair : l'IA n'a pas pu deviner la catégorie, choisissez-la ci-dessous.` · `Aucune ne convient ?` · `Créez votre catégorie dans Mon espace` · `puis revenez la choisir ici.` · `Catégorie apprise de vos corrections précédentes` · `Catégorie` · `Supprimer` · `Chargement des dépenses…` · `Aucune dépense à valider.` · `Valider` · `Plus tard`

### src/components/budget-sheet.tsx (22)
`Budget enregistré` · `Enregistrement impossible` · `Pas encore assez d'historique pour estimer votre budget.` · `Estimation : {value} / mois` · `Estimation impossible` · `Gérer mon budget` · `Mes charges fixes` · `Un seul montant à indiquer : ce que vous ne voulez pas dépasser, par mois ou par an.` · `Loyer, abonnements, assurances… déclarés une fois, recomptés chaque mois.` · `Montant du budget ({symbol})` · `Estimé par l'IA : {value} / mois` · `Aucun budget` · `Par mois` · `Par an` · `Budget / mois` · `Charges fixes` · `Reste` · `Enregistrer` · `Estimer pour moi` · `« Estimer pour moi » calcule une proposition à partir de vos dépenses déjà enregistrées.` · `Pas encore assez de données pour une estimation fiable.` · `Gérer mon budget par catégorie`

### src/components/recurring-section.tsx (31)
`Aucune charge fixe détectée` · `Analyse impossible` · `Transcription impossible` · `Charges fixes enregistrées` · `Enregistrement impossible` · `Charge fixe supprimée` · `Ajouter mes charges fixes` · `Dictez ou tapez en vrac : « Netflix 15 €, internet 30 €, loyer 750 € le 5 ». Elles sont ensuite reportées automatiquement chaque mois.` · `Netflix 15€, Spotify 10€, loyer 750€ le 5…` · `Analyser` · `Arrêter ({seconds}/{maxSeconds}s)` · `Dicter` · `Saisie manuelle` · `Nouvelle ligne à remplir` · `Vérifiez avant d'enregistrer` · `Nom` · `Catégorie` · `Mensuelle` · `Annuelle` · `Le` · `Retirer` · `Enregistrer mes charges fixes` · `Mes charges fixes` · `{count} charge(s) · {total} / mois en moyenne` · `Charges précédentes` · `Charges suivantes` · `Total / mois` · `Total / an` · `Aucune charge fixe pour le moment.` · `le {jour}` · `Supprimer {nom}`

### src/components/debit-settings.tsx (28)
`Débit immédiat` · `Chaque achat part du compte tout de suite.` · `Débit différé` · `Vos achats carte sont prélevés en une fois.` · `Ma banque` · `Choisir ma banque` · `Le nombre de jours de différé change d'une banque à l'autre : on préremplit sa règle habituelle, vous pouvez l'ajuster si votre contrat diffère.` · `Arrêté le` · `Prélevé le` · `À partir de quand appliquer cette règle ?` · `À partir d'aujourd'hui` · `Les dépenses déjà enregistrées gardent leur date.` · `Toutes mes dépenses` · `La règle s'applique aussi aux dépenses passées.` · `À partir d'une date précise` · `Utile si vous avez changé de carte en cours d'année.` · `Date d'application de la règle` · `Exemple : un achat carte fait aujourd'hui sera prélevé le {date}. Seuls les paiements par carte sont différés — virements, espèces et chèques restent à leur date.` · `Choix enregistré — modifiable dans Mon espace` · `Enregistrement impossible` · `Votre carte : débit immédiat ou différé ?` · `Une seule question avant de valider votre première dépense : elle nous permet d'afficher la vraie date de prélèvement de vos achats. Vous pourrez changer ce choix à tout moment dans Mon espace.` · `Continuer` · `Débit immédiat enregistré` · `Règle appliquée aux dépenses à partir du {date}` · `Règle appliquée à toutes vos dépenses, passées et à venir` · `{count} dépense(s) carte replacée(s) à sa date de prélèvement.` · `Enregistrer`

### src/components/import-sheet.tsx (7)
`Importer un relevé de compte` · `Un seul fichier à la fois : PDF, Excel (.xlsx) ou CSV. Les dépenses détectées vous seront proposées avant enregistrement.` · `Votre fichier est anonymisé avant lecture` · `IBAN, numéros de compte ou de carte, nom du titulaire, email, téléphone et adresse sont supprimés avant tout envoi à l'IA. Le fichier n'est jamais conservé : seules les dépenses lues sont enregistrées.` · `Choisir un fichier` · `Lecture et anonymisation en cours…` · `Lire le fichier`

### src/components/monthly-report.tsx (7)
`l'année {periode}` · `Votre bilan de` · `est arrivé` · `Où est parti votre argent cette année, et quoi améliorer.` · `Où est parti votre argent, et quoi améliorer ce mois-ci.` · `Voir` · `Bilan de {periode}`

### src/components/report-dialog.tsx (8)
`Calcul du bilan…` · `Bilan indisponible pour le moment.` · `Total dépensé` · `vs période précédente` · `Où part l'argent` · `Ce qu'il faut retenir` · `Pistes d'amélioration` · `J'ai compris`

### src/components/report-history.tsx (6)
`Historique des bilans` · `Retrouvez le bilan d'un mois ou d'une année passée à tout moment.` · `Chargement…` · `Aucun bilan disponible pour l'instant : vos bilans apparaîtront ici à la fin du premier mois rempli.` · `Année` · `Bilan de {periode}`

### src/components/expense-dialog.tsx (29)
`{n} autres dépenses portent le libellé « {previousLabel} ». Renommer aussi en « {newLabel} » ?` · `Une autre dépense porte le libellé « {previousLabel} ». Renommer aussi en « {newLabel} » ?` · `Dépense modifiée` · `Dépense ajoutée` · `Enregistrement impossible` · `Charge fixe` · `Ajoutée aux charges fixes mensuelles` · `Ajoutée aux charges fixes annuelles` · `Ajout impossible` · `Retirée de vos charges fixes` · `Supprimer cette dépense ?` · `Dépense supprimée` · `Modifier la dépense` · `Nouvelle dépense` · `Montant` · `Catégorie` · `Choisir` · `Sans catégorie` · `Description` · `Date` · `Moyen de paiement` · `Débit différé : sera réellement prélevé le {date}.` · `Type de dépense` · `Variable` · `Fixe` · `Elle sera comptée automatiquement chaque période, sans la redicter.` · `Chaque mois` · `Chaque année` · `Enregistrer`

### src/components/tips-banner.tsx (4)
`Astuce` · `Astuce suivante` · `Compris` · `Masquer l'astuce`

### src/components/app-header.tsx (6)
`Connexion` · `Créer un compte` · `Administration` · `Mon espace` · `Gérer mon compte` · `Se déconnecter`

### src/components/notification-center.tsx (3)
`Voir` · `Masquer la notification` · `Bilan de {periode}`

### src/components/category-alerts.tsx (6)
`Budget atteint` · `{nom} : {used} dépensés sur {allocated} prévus ce mois-ci.` · `Budget de catégorie atteint` · `{count} budgets de catégorie atteints` · `prévus` · `Ajuster mes budgets par catégorie`

### src/components/credits-panel.tsx (11)
`{left} {unit}(s) restant(s)` · `Crédits épuisés ce mois` · `Mes crédits du mois` · `Remis à zéro le 1er de chaque mois : vous recevez autant de dictées que le mois compte de jours.` · `Dictées 30 s` · `dictée` · `Import de relevé` · `import` · `Saisie manuelle` · `Illimitée` · `Chargement…`

### src/components/daily-reminder.tsx (1)
`Bilan de la journée : dictez vos dépenses du jour en 30 secondes.`


## Annexe B — Astuces de la bannière (FR, ordre d'affichage ; `{sym}` = symbole de devise)

Les 5 premières forment la phase de prise en main. Les versions anglaises (même ordre) sont dans `TIPS_EN` de `src/components/tips-banner.tsx`.

1. Bienvenue ! Appuyez sur « Ajouter » en bas à droite pour dicter vos dépenses : « 12 {sym} de café, 40 {sym} de courses ».
2. Important pour les notifications : installez nemla sur votre écran d'accueil. Sur Android/Chrome, ouvrez les 3 petits points en haut à droite → « Installer l'application » ou « Ajouter à l'écran d'accueil ». Sur iPhone/Safari, appuyez sur le bouton Partager → « Sur l'écran d'accueil ». Sans ce raccourci, le téléphone ne peut pas afficher les rappels. — **spécifique web (PWA), à remplacer en natif**
3. Nouveau : « Ajouter » → « Importer un relevé » lit un PDF, un Excel ou un CSV de votre banque et en extrait vos dépenses (un fichier à la fois).
4. Vos relevés sont anonymisés avant lecture par l'IA (IBAN, numéros, nom, adresse supprimés) et le fichier n'est jamais conservé : seules les dépenses lues sont enregistrées.
5. Votre budget et vos charges fixes (loyer, abonnements) se renseignent dans Mon espace → Profil & budget.
6. Les charges fixes se reportent automatiquement chaque mois : plus besoin de les redicter.
7. Vous pouvez dicter une dépense passée : dites simplement « 40 {sym} de courses mardi dernier » ou « le 3 mai, 12 {sym} de train ».
8. Précisez la catégorie à voix haute si vous voulez la choisir : « 25 {sym} chez le boucher, catégorie courses ».
9. Vous pouvez dicter en français ou en anglais : choisissez la langue dans Mon espace, à côté de la devise.
10. La devise s'adapte à votre pays : changez-la dans Mon espace, l'app affiche tous les montants dedans.
11. Pas envie de parler ? Le bouton « Ajouter » propose aussi la saisie manuelle : montant, catégorie et date.
12. Une dépense mal classée ? Corrigez-la : l'app retient votre choix pour ce commerçant la prochaine fois.
13. Dans Mon espace, chaque catégorie peut être renommée parmi quelques suggestions, sans tout réinventer.
14. Masquez une catégorie dans Mon espace : ses dépenses restent comptées, mais regroupées dans « Autre ».
15. Fixez un budget mensuel ou annuel par catégorie pour voir d'un coup d'œil si vous dépassez.
16. Cliquez sur une barre du graphique annuel pour ouvrir le détail du mois, puis sur une catégorie pour voir chaque achat.
17. La ligne rouge sur le graphique, c'est votre budget cible : au-dessus, le mois a débordé.
18. Le rappel du soir vous évite d'oublier vos dépenses du jour : activez-le dans Mon espace.
19. Vos bilans de fin de mois et de fin d'année restent disponibles dans Mon espace → Bilans.
