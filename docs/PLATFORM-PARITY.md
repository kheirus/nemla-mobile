# Parité Android / iOS

L'interface et la logique sont partagées. Écarts connus ou prévisibles, à valider avec le propriétaire :

| Sujet | Android | iOS | Visible ? |
|---|---|---|---|
| Stockage de la session | Keystore + fichier chiffré | Keychain | Non |
| Protection d'écran | `FLAG_SECURE` (capture bloquée) | Voile en arrière-plan (capture non blocable) | Oui : capture d'écran possible sur iOS |
| Retour arrière | Geste / bouton système | Balayage depuis le bord | Comportement natif de chaque OS |
| Permission micro | Dialogue système Android | Dialogue système iOS + texte `NSMicrophoneUsageDescription` | Oui, rendu système |
| Sélecteurs de fichier et de date, notifications | Composants système | Composants système | Oui, rendu système |
| Enregistrement audio | `AudioRecord` | `AVAudioEngine` | Non, même format de sortie |
| IA embarquée | ML Kit GenAI (Gemini Nano) possible | Apple Foundation Models possible | Éviter : préférer whisper.cpp et LiteRT-LM, communs aux deux |
| Icône | Adaptative, transparence admise | Fond opaque obligatoire | Oui : fond à choisir |

État au lot 0 : `FLAG_SECURE` est posé par `MainActivity` en release ; le voile iOS n'existe pas encore (prévu avec `core:security`, lot 1).

État au lot 1 (`core:security`) : `AndroidScreenProtection` (`FLAG_SECURE` sur toutes les activités) et `IosScreenProtection`
(vue opaque `systemBackgroundColor` posée sur chaque fenêtre à `willResignActive`, retirée à `didBecomeActive`) existent, actives hors debug
sur les deux plateformes ; elles ne sont pas encore branchées dans `androidApp` / `iosApp`. Écart visible : sur iOS, la capture d'écran
reste possible au premier plan, et l'aperçu du sélecteur d'applications montre un écran uni au lieu de l'écran vide d'Android.
Tests : le Keychain n'est pas disponible dans l'exécutable de test Kotlin/Native (`errSecNotAvailable`), donc `KeychainSecureStore`
n'a pas de test automatisé ; le magasin Android est testé sur hôte avec un chiffrement logiciel à la place du Keystore.
Branchement (lot 1, 16/11/2026) : `NemlaAndroid.start` (appelé par `NemlaApplication`) installe `AndroidScreenProtection` sur
l'application ; `MainActivity` ne pose plus `FLAG_SECURE` elle-même (vérifié actif dans un APK release sur émulateur).
`startNemla` (appelé par `iOSApp.init`) installe le voile `IosScreenProtection`. Les deux affichent `App()`.

Écarts entre le web et le mobile à trancher (voir `specs/03` et `specs/04`) : graisse 600 absente de DM Sans (rendue en 700 sur le web),
ambre au survol sans équivalent tactile, toasts en haut sur le web, fenêtres modales pleine largeur à coins carrés sur mobile web,
logo absent du dépôt web (hébergé sur le CDN Lovable).

## Lot 1 — interface (16/11/2026)

Écarts Android / iOS (UI 100 % Compose, mêmes écrans) :

| Sujet | Android | iOS | Visible ? |
|---|---|---|---|
| Police | `FontFamily.Default` = Roboto | `FontFamily.Default` = SF Pro | **Oui**, tant que Outfit / DM Sans ne sont pas embarquées (`NemlaFonts.kt`) |
| Autoremplissage (email, mot de passe) | Autofill Android via `ContentType` | Indices `ContentType` transmis par Compose ; prise en charge par le trousseau iOS non vérifiée | À vérifier sur appareil |
| Retour arrière dans le parcours d'authentification | Bouton / geste système | Pas de bouton retour à l'écran ; geste de bord selon Compose Navigation 2.10 | À vérifier sur simulateur |
| Listes de choix (sexe, situation familiale) | Menu Compose Material 3 | Même menu Compose (pas de `UIMenu` natif) | Non entre plateformes ; non natif sur iOS |
| Thème clair / sombre | Suit le réglage système | Suit le réglage système | Non |
| Langue | Ressources `values` (fr) / `values-en` selon la langue système | Idem | Non |
| Vérification visuelle | Émulateur (debug et release R8) | Compilation `xcodebuild` seulement, aucun simulateur lancé | — |

Écarts web / mobile introduits ou constatés au lot 1 (à valider par le propriétaire) :

1. **Thème** : le mobile suit le thème du système ; le web est clair par défaut sans mode « système » (`specs/03` §1).
2. **Polices** : Outfit et DM Sans non embarquées (aucun fichier téléchargé) ; familles définies dans un seul fichier,
   `core/designsystem/.../theme/NemlaFonts.kt`, avec le « semi-gras » du corps déjà mappé sur 700 comme le rendu web.
3. **Logo** : `nemla-logo.png` absent ; monogramme « n » dessiné en Compose (`brand/NemlaLogo.kt`, fonction `PlaceholderLogoMark`).
4. **Hauteur des contrôles** : boutons, champs et listes à 48 dp (cible tactile) au lieu de 36 px sur le web.
5. **Toasts** → bannières dans la carte (erreur, information, succès), annoncées aux lecteurs d'écran.
6. **Messages** : erreurs de validation sous les champs (textes nouveaux, `specs/04` §2.3) ; les messages bruts de Supabase ne sont
   jamais affichés, remplacés par des messages traduits (identifiants incorrects, email non confirmé, email déjà inscrit,
   hors ligne, délai dépassé, trop de tentatives, configuration).
7. **Modes de l'écran d'authentification** : trois destinations de navigation (connexion, inscription, mot de passe oublié) au lieu
   d'un écran à modes ; premier lancement sur l'inscription (comme `/auth`), retour sur la connexion après une déconnexion ou
   l'expiration de la session. « Mot de passe oublié » ne reprend pas l'email saisi à la connexion.
8. **Inscription** : pas de puces « Pseudos disponibles » ni de remplacement d'un pseudo déjà pris (`suggest_pseudo` demande une
   session) ; les métadonnées sont recopiées dans le profil par `bootstrap()` à l'ouverture de l'accueil. Les listes de choix
   n'affichent pas les icônes de situation familiale. Pas de toast « Compte créé. Bienvenue ! ».
9. **Après inscription avec session** : accueil (coquille) au lieu de `/onboarding` (lot 4).
10. **Nouveau mot de passe** (`/reset-password`) : non porté, dépend des App Links / Universal Links.
11. **En-tête de l'accueil** : bouton « Se déconnecter » visible directement, sans menu « Mon espace ».
12. **Traductions anglaises** : `en-marketing.ts` n'étant pas dans les spécifications, les textes anglais de l'authentification et
    de l'accueil ont été rédigés pour le mobile (« Pseudo » → « Username », « Femme » → « Woman », « Colocation » →
    « Shared housing » suivent les valeurs effectives de `specs/04` §I.1) ; à relire.


## Lot 2 — dictée (16/11/2026)

Écarts Android / iOS (dialogue, fiche de revue et logique 100 % Compose / `commonMain` ; seuls la capture micro et la demande de
permission sont propres à chaque plateforme, dans `core:audio`) :

| Sujet | Android | iOS | Visible ? |
|---|---|---|---|
| Capture | `AudioRecord`, source `VOICE_RECOGNITION` puis `MIC`, 16 kHz mono PCM16 si accepté (sinon 48 / 44,1 kHz puis décimation) | `AVAudioEngine`, tap à la fréquence native (souvent 48 kHz), canal 0 en `Float`, décimation | Non : même WAV 16 kHz mono 16 bits, même détection de silence. Le traitement du signal du système (gain, réduction de bruit) diffère |
| Session audio | Pas de demande de focus audio : une musique en cours continue | `AVAudioSession` catégorie `record` (mode par défaut) : la lecture des autres applications est interrompue pendant la dictée, puis notifiée à la fin | **Oui** |
| Permission micro | Dialogue système ; après deux refus Android ne le montre plus et la dictée affiche « Accès au micro refusé. » (pas de lien vers les réglages) | Dialogue système une seule fois ; texte `NSMicrophoneUsageDescription` en français uniquement (pas de `en.lproj/InfoPlist.strings`) | **Oui**, rendu et texte système |
| Passage en arrière-plan pendant l'enregistrement | `ON_STOP` : enregistrement annulé, rien n'est envoyé | Idem | Non |
| Sélecteur de date | `DatePickerDialog` Material 3 Compose | Le même composant Compose (pas de `UIDatePicker`) | Non entre plateformes ; non natif sur iOS |
| Menu « Ajouter », dialogues | `DropdownMenu`, `Dialog`, `AlertDialog` Compose | Les mêmes composants Compose | Non entre plateformes ; non natif sur iOS |
| Vérification | Compilation, tests JVM ; aucun enregistrement réel sur émulateur ou appareil | Compilation, tests sur simulateur (logique) ; aucun enregistrement réel | À vérifier sur appareil |

Écarts web / mobile introduits au lot 2 (à valider par le propriétaire) :

1. **Stockage de l'audio** : le WAV n'est pas téléversé dans le bucket `voice-notes` et `audio_url` n'est jamais envoyé ; il part
   directement en multipart vers `voice/process` et ne vit qu'en mémoire (remis à zéro après l'envoi). Rien ne reste côté stockage
   en cas d'échec (le web y laisse l'audio, `specs/01` §2.1 étape 11).
2. **Consentement IA** : dialogue explicite avant la première dictée (niveau `PERSONAL`), absent du web. Refus : rien n'est enregistré
   ni envoyé, message neutre. Révocation prévue dans Mon espace (lot 4).
3. **Langue de dictée** : langue du système (`en` si le système est en anglais, sinon `fr`) en attendant les préférences (lot 4).
4. **Fiche de revue** : écran dédié (destination `voiceGraph`) ouvert après une dictée réussie, au lieu d'un bloc inséré dans le
   tableau de bord (lot 3). « Plus tard » et le retour système reviennent à l'accueil. Les dépenses en attente ne peuvent être
   rouvertes que pendant la session, par l'entrée « Dépenses à valider » ajoutée au menu « Ajouter » (en mémoire, oubliée au
   redémarrage, à la déconnexion et au changement de compte) ; la carte « Dépenses à valider » (`specs/04` §5.3) attend
   `GET /api/public/expenses`.
5. **Libellé en lecture seule** dans la fiche (renommer exige `PUT /api/public/expenses/{id}`) ; la majuscule initiale n'est pas
   réappliquée (le serveur l'a déjà fait à l'extraction). `prettyLabel` n'est pas porté (sans effet notable sur un libellé dicté ;
   nécessaire pour l'import, lot 5).
6. **Pas de détection de doublons** (lecture des libellés déchiffrés impossible) ni de **journal de correction**
   (`classification_log_save` : port en `AppError.NotSupportedYet`, appel ignoré).
7. **Enregistrement d'une ligne** : `PATCH /rest/v1/expenses` (`montant` décimal exact, `date`, `category_id`, `confirmed=true`)
   au lieu de `expense_save` ; `moyen_paiement` et `source` ne sont pas réécrits (le web force `carte`, valeur par défaut des
   lignes dictées : même résultat). Statut de la dictée : `PATCH voice_entries statut=confirmed`. Suppression : `DELETE` immédiat.
8. **Apprentissage** : `record_category_correction` pour chaque ligne dont la clé marchand fait au moins 3 caractères, avec
   `p_label` vide (ignoré par le RPC). Clé marchand calculée sur l'appareil par un portage de `normalizeMerchantKey` : le repli des
   accents utilise une table des lettres latines décomposables (équivalent NFD pour le français ; `œ`, `æ`, `ß` deviennent des
   séparateurs comme sur le web). La mention « Catégorie apprise… » vient, comme sur le web, de la lecture de `merchant_rules`.
9. **Menu « Ajouter »** : « Saisie manuelle » et « Importer un relevé » affichés désactivés avec « Bientôt disponible ». Si le quota
   est illisible, l'entrée dictée affiche « 30 s max » sans compteur et reste active (le serveur tranche).
10. **Messages** : jamais de texte serveur. Erreurs traduites (quota 429, pas de parole / pas de dépense 422, trop volumineux 413,
    aucune catégorie, hors ligne, délai, trop de demandes, générique « Traitement impossible »). Les erreurs micro sont traduites
    (le web ne les traduit pas) et un message « Micro indisponible… » est ajouté. Les erreurs de traitement restent dans le dialogue
    (ouvert) ; si l'utilisateur l'a fermé pendant le traitement, elles s'affichent dans une bannière au-dessus du bouton « Ajouter »
    (6 s, touchable pour masquer), comme « Dépenses enregistrées ». « {n} dépense(s) détectée(s) » devient une bannière en tête de la
    fiche. Pluriels réels (`plurals`) au lieu de « (s) ».
11. **Montant** : saisie filtrée (chiffres, une virgule ou un point, deux décimales au plus, signe moins) ; vide = 0 et négatif
    accepté comme sur le web ; séparateur affiché selon la langue (virgule en français). Converti en centimes sans flottant.
12. **Date** : date future ramenée à aujourd'hui (jour local, pas UTC) ; affichée `jj/mm/aaaa` ; sélecteur Material 3.
13. **Catégorie** : liste sans pastille de couleur, libellé « Catégorie » au-dessus du champ ; une catégorie supprimée entre-temps est
    traitée comme absente. « Créez votre catégorie dans Mon espace » n'est pas un lien (Mon espace au lot 4).
14. **Mise en page d'une ligne** : quatre rangées (libellé + montant, avertissements, catégorie, date + corbeille) au lieu de deux,
    pour la largeur d'un téléphone.
15. **Dialogue de dictée** : coins arrondis 16 dp, largeur maximale 384 dp, marge 16 dp (plein écran à coins carrés sur le web mobile) ;
    bouton « Ajouter » en `ExtendedFloatingActionButton` Material 3 (pilule 56 dp, ombre M3). Aucune animation hors le spinner.
16. **Arrière-plan** : l'enregistrement est annulé si l'application passe en arrière-plan (le web continue dans un onglet masqué).

## Lot 3 — tableau de bord (16/11/2026)

Écarts Android / iOS (tableau de bord, graphiques, fiche d'édition et logique 100 % Compose / `commonMain`, aucun code propre à
une plateforme ajouté) :

| Sujet | Android | iOS | Visible ? |
|---|---|---|---|
| Graphiques (barres mois par mois, anneau) | `Canvas` Compose, aucune bibliothèque | Le même `Canvas` | Non |
| Formatage des montants et des dates | Formateur commun en Kotlin (`MoneyFormat`, `DateLabels`), sans ICU | Idem, sans `NSNumberFormatter` | Non entre plateformes ; écart possible avec le navigateur (voir ci-dessous) |
| Tirer pour rafraîchir | `PullToRefreshBox` Material 3 (indicateur M3) | Le même composant Compose (pas de `UIRefreshControl`) | Non entre plateformes ; non natif sur iOS |
| Rafraîchissement au retour au premier plan | `ON_START` du cycle de vie de l'écran | Idem (cycle de vie Compose) | Non |
| Sélecteur de date (recherche, édition) | `DatePickerDialog` Material 3 | Le même composant Compose | Non entre plateformes ; non natif sur iOS |
| Menus (compte, filtre, météo), confirmation de suppression | `DropdownMenu`, `AlertDialog` Compose | Les mêmes composants | Non entre plateformes ; non natif sur iOS |
| Vérification | Compilation, tests JVM ; non lancé sur émulateur | Compilation, tests sur simulateur (logique) ; non lancé | À vérifier sur appareil |

Écarts web / mobile introduits au lot 3 (à valider par le propriétaire) :

1. **Libellés** : `description_enc` n'est lisible que par `expenses_view(p_key)`. Les dépenses sont lues en PostgREST sans libellé :
   lignes affichées « Dépense », groupes en attente « Dépense vocale… » (au lieu de « {description}… »), sous-lignes de « Où part
   l'argent » « {date courte} · Dépense ». Dépend de `GET /api/public/expenses`.
2. **Recherche** : par date exacte seulement (dépenses confirmées, 50 au plus, date puis création décroissantes). Pas de champ texte
   (la recherche par mot-clé dépend du backend, port en `NotSupportedYet`) : carte « Chercher un jour précis » avec un bouton date
   (pilule, libellé affiché même sur téléphone) qui ouvre directement le sélecteur de date (pas de popover), bouton « Effacer la
   recherche » et la mention « La recherche par mot-clé arrivera bientôt. ». Erreur de recherche affichée (« Recherche impossible
   pour le moment. »), absente du web.
3. **Charges fixes** : `materializeRecurring` n'est pas appelable (port `NotSupportedYet`, ignoré comme un échec sur le web) ; les
   occurrences ne sont donc créées que si le web est ouvert. « Charges fixes : x / mois » et les barres « prévu » viennent de
   `recurring_expenses` lu en PostgREST, comme sur le web.
4. **États** : indicateur discret « Chargement du tableau de bord… » au premier chargement (les blocs n'apparaissent qu'ensuite),
   données précédentes conservées pendant un changement d'année (pas de retour à 0) avec une fine barre de progression dans le bloc
   pilote, bannière d'erreur traduite (hors ligne, délai, générique) avec « Réessayer », tirer pour rafraîchir et rafraîchissement au
   retour au premier plan et après la fiche de revue. Le web n'affiche ni chargement ni erreur. Un changement de mois dans la même
   année ne refait aucune requête (l'année est chargée en une fois).
5. **Météo** : ratio simple dépensé / budget, quatre états, seuils stricts, reproduit tel quel avec l'incohérence du web (mois : total
   du mois sans filtre de catégories ; année : total filtré). Popover remplacé par un menu ancré sous la pastille.
6. **Graphique** : dessiné en `Canvas` ; graduations de l'axe Y réparties régulièrement de 0 au maximum (max(données empilées, budget
   mensuel) × 1,1) au lieu des graduations « arrondies » de Recharts ; graphique sans débord de 16 dp sur les côtés de la carte ;
   info-bulle « {mois} {total} » au-dessus du graphique pendant l'appui ; initiales des mois sous 640 dp, mois courts au-delà. Sans
   budget ni dépense, l'axe n'a qu'une graduation « 0 ». L'anneau n'a pas d'info-bulle (les montants sont dans la liste) et tourne
   dans le sens antihoraire à partir de 3 h comme Recharts.
7. **Filtre de catégories** : une sélection explicite de toutes les catégories laisse passer les dépenses sans catégorie (comme
   « toutes »). Menu au lieu d'un popover ; lignes de 48 dp.
8. **Groupe « Autre »** : traduit « Other » en anglais (non traduit sur le web).
9. **Alertes de budget** : carte in-app seulement (notification système au lot 7) ; lien « Ajuster mes budgets par catégorie » omis
   tant que Mon espace (lot 4) n'existe pas.
10. **Édition d'une dépense** (version réduite, PostgREST) : montant (obligatoire, positif ou nul), date (au plus aujourd'hui),
    catégorie (« Sans catégorie » possible), moyen de paiement, via `PATCH expenses` ; suppression après confirmation via `DELETE`.
    Libellé en lecture seule avec une mention ; ni type Variable / Fixe, ni création manuelle, ni renommage groupé, ni apprentissage
    de catégorie. Moyens de paiement traduits en anglais (non traduits sur le web). Date affichée en format long. Le trigger serveur
    recalcule `date_prelevement`. « Dépense modifiée » / « Dépense supprimée » en bannière (4 s) au lieu d'un toast.
11. **En-tête** : pastille avec avatar générique (`UserRound`, pas l'icône de situation familiale), pseudo ou « Mon espace », menu
    avec le titre et « Se déconnecter » ; « Gérer mon compte » et le raccourci administrateur absents (lot 4).
12. **Dépenses à valider** : un toucher ouvre la fiche de revue du lot 2 (écran dédié) avec ces lignes, sans libellé ni transcription ;
    titre « Vérifiez les dépenses lues dans le fichier » pour le groupe d'import. Après Valider, Supprimer ou Plus tard, le tableau
    de bord se recharge au retour.
13. **Formatage** : montants mis en forme par un formateur commun (espace fine insécable U+202F pour les milliers en fr-FR,
    `1.234,50 MAD` pour fr-MA, `F CFA` et décimales facultatives pour XOF, `$` après le montant pour CAD) ; mois courts anglais
    « Sept » (CLDR en-GB). À comparer avec le navigateur.
14. **Hors périmètre** : bannière d'astuces (§5.9), bandeau « bilan arrivé » (§5.10), notifications in-app et rappel quotidien
    (§5.11, §5.12 → lot 7 ; §5.10 fait au lot 6), dialogue « débit immédiat ou différé » `PaymentModeGate` et réglages du débit (§11.2, §11.3 → lot 4).
15. **Ligne période** : contrôle « Mois | Année » en pleine largeur, navigateur de période dessous, au lieu d'une seule ligne
    `[1fr, auto]` : sur 393 pt de large, les libellés se tronquaient (« M… | Y… »), vu sur simulateur iOS.

## Lot 4 — Mon espace et onboarding (16/11/2026)

Écarts Android / iOS (écrans, logique et préférences 100 % Compose / `commonMain` ; seul code propre à une plateforme :
`ProvideAppLanguage`, `expect/actual` dans `shared`) :

| Sujet | Android | iOS | Visible ? |
|---|---|---|---|
| Changement de langue de l'application | `Locale.setDefault` + `LocalConfiguration` surchargée, puis recomposition sous `key(langue)` (même `NavController`) | Écriture de `AppleLanguages` dans `NSUserDefaults`, puis même recomposition | À vérifier sur appareil. Sur iOS, `AppleLanguages` est persistant et ne change les textes rendus par le système (dialogue du micro…) qu'au prochain lancement ; sur Android ces textes suivent la langue du système. L'API « langue par application » d'Android 13 n'est pas utilisée (pas d'AppCompat). |
| Thème clair / sombre | `NemlaTheme(darkTheme)` | Idem | Non. Barres système non recolorées à chaud sur Android (fond de fenêtre défini au lancement). |
| Retour depuis un panneau de Mon espace | Bouton / geste système (`NavigationBackHandler`) | Geste de bord via `navigationevent` de Compose | À vérifier sur simulateur iOS |
| Claviers numériques (montants, jours, âge) | `KeyboardType.Decimal` / `Number` | Pavé décimal iOS ; le séparateur dépend de la région de l'appareil | Oui, rendu système ; la virgule et le point sont acceptés |
| Interrupteur « Visible », sélecteur de date (carte), menus | Composants Material 3 Compose | Les mêmes (pas de `UISwitch` ni de `UIDatePicker`) | Non entre plateformes ; non natif sur iOS |
| Bouton « Dicter » (onboarding, charges fixes) | Même enregistreur que le FAB (`AudioRecord`) | `AVAudioEngine` | Non ; capture jamais essayée sur appareil |
| Vérification | Compilation, tests JVM, APK debug | Compilation du framework, tests sur simulateur (logique) | Écrans non lancés sur émulateur ni simulateur |

Écarts web / mobile introduits au lot 4 (à valider par le propriétaire) :

1. **Onboarding imposé aux seuls comptes vierges** : après connexion, `bootstrap_user`, puis onboarding seulement si `onboarding_done`
   est faux **et** que le compte est vierge (budget mensuel à 0, aucune charge fixe, aucune dépense : une lecture `limit=1` sans libellé).
   Sinon, ou en cas d'erreur, tableau de bord. Le web ne force jamais l'onboarding ; les comptes web déjà utilisés n'y passent donc pas.
   Pendant cette vérification, l'écran de connexion reste affiché (ou l'écran de démarrage au lancement).
   **Ajout** : lien « Passer pour l'instant » sous « Enregistrer et voir mon tableau de bord », qui ouvre le tableau de bord sans rien
   écrire (`onboarding_done` reste faux : l'onboarding revient au prochain lancement tant que le compte est vierge). Absent du web.
2. **Onboarding** : libellé « Budget mensuel disponible ({symbole}) » avec la devise du profil (ÉCART web corrigé : « € » en dur) ; menu
   de l'en-tête réduit à « Se déconnecter » (pas de « Gérer mon compte », qui permettrait de contourner l'onboarding) ; « Aucune charge
   fixe détectée » affiché aussi ici ; pas de toast « Configuration enregistrée » (le tableau de bord s'ouvre directement) ; erreurs en
   bannière (5 s) au lieu de toasts. Aucune validation, comme le web (montant illisible = 0, budget vide = 0).
3. **Dictée des charges fixes** : lit `transcript.text` (le web passe l'objet et échoue, `specs/01` §2.2). La transcription est ajoutée
   au texte (« , ») puis seule analysée, dans l'onboarding comme dans Mon espace. Bouton « Transcription… » pendant l'envoi.
4. **Toasts** : bannières en haut du panneau ouvert (4 s, touchables pour fermer), comme aux lots 2 et 3.
5. **En-têtes** : avatar selon la situation familiale (aussi sur le tableau de bord) ; menu du tableau de bord : « Gérer mon compte »
   puis « Se déconnecter » ; Mon espace et onboarding : « Se déconnecter » seul. Raccourci administrateur absent (back-office web).
6. **Onglets** : contrôle segmenté pleine largeur, libellés sur deux lignes au besoin. Onglet « Bilans » : historique des bilans
   depuis le lot 6.
7. **Textes non traduits sur le web** (sous-titres des entrées, « Crédits », « / mois », noms de pays des devises) : traduits en
   anglais. Sous-titre des Préférences : « Devise, langue, thème et accord IA. » (pas de rappel, voir 8).
8. **Préférences** : rappel quotidien absent (notifications au lot 7) ; encadré « installez nemla sur votre écran d'accueil » supprimé
   (propre au web) ; **ajout** d'un bloc « Dictée et intelligence artificielle » avec « Retirer mon accord » (promis par le texte de
   consentement du lot 2, absent du web) ; devise enregistrée dans le profil et appliquée sans rechargement (le tableau de bord la relit
   au retour) ; langue de l'application appliquée à chaud sans rechargement, langue de dictée annoncée dans la langue de l'interface
   (le web affiche « Dictation language: English » en dur). Thème : tant qu'aucun choix n'est fait, l'application suit le système (lot 1)
   et la carte cochée reflète le thème effectif ; le web est clair par défaut.
9. **Langues stockées localement** (comme le web), dans `SecureStore` ; par défaut la langue du système pour l'interface **et** la dictée
   (le web prend « fr » pour la dictée).
10. **Mon profil** : âge contrôlé localement (14 à 120) avec message ; reste en mode édition après « Enregistrer mon profil » comme le
    web ; puces « Suggestions : » si le pseudo est pris.
11. **Gérer mon budget** : montant décimal accepté (affiché sans décimales s'il est rond) ; « Estimer pour moi » désactivé sans
    historique ; contrôle « Par mois | Par an » segmenté.
12. **Mes charges fixes** : récapitulatif « Total / mois | Total / an » en tuiles, pagination 6 par page avec boutons 32 dp, suppression
    sans confirmation (comme le web), jour borné à 1–28 à l'enregistrement.
13. **Catégories** : budget enregistré à la perte du focus ou sur « OK » du clavier ; un échec de l'interrupteur « Visible » rétablit la
    valeur et affiche « Enregistrement impossible » (le web est silencieux) ; suppression sans confirmation (catégories non par défaut).
14. **Ma carte bancaire** : portée initiale « À partir d'aujourd'hui » si aucun choix n'a été fait ou si le débit est immédiat, « À partir
    d'une date précise » si une date est enregistrée, sinon « Toutes mes dépenses » ; le nombre de dépenses replacées s'affiche sur une
    seconde ligne de la bannière ; sélecteur de date Material 3. Noms des banques non traduits (dont « Autre banque »).
15. **Crédits** : « Crédits indisponibles pour le moment. » en cas d'erreur (le web n'affiche rien).
16. **Zone dangereuse** : confirmation dans un `AlertDialog`, puis message « La suppression du compte n'est pas encore disponible depuis
    l'application… » (`DELETE /api/public/account` manquant). Export des données : non construit (absent du web et du backend).
17. **Hors périmètre** : dialogue `PaymentModeGate` du tableau de bord (§11.2, voir ROADMAP), rappel quotidien (lot 7, notifications) ; bilans faits au lot 6.

## Lot 5 — import de relevés (17/11/2026)

Écarts Android / iOS (dialogue 100 % Compose ; seul le sélecteur de fichier système est natif) :

| Sujet | Android | iOS | Visible ? |
|---|---|---|---|
| Sélecteur de fichier | `ActivityResultContracts.OpenDocument` (types MIME PDF, CSV, texte, XLS/XLSX/XLSM, ODS) | `UIDocumentPickerViewController` en mode ouverture sans copie (`asCopy = false`), types `UTTypePDF`, CSV, texte brut, tableurs | **Oui**, rendu système de chaque OS |
| Lecture du PDF | `PdfRenderer.Page.textContents` (Android 15, API 35) ou `PdfRendererPreV` (Android 12 à 14 avec l'extension SDK S ≥ 13, mise à jour Google Play) | PDFKit (`PDFPage.string`, pages jointes par une ligne vide) | **Oui** : sur Android 9 à 11, et 12 à 14 sans l'extension, message « Ce PDF ne peut pas être lu sur cette version d'Android. Utilisez un export CSV » |
| Découpage du texte PDF | blocs de texte de PdfRenderer joints par un saut de ligne | texte de page PDFKit | Possible : ordre et sauts de ligne peuvent différer pour un même PDF ; sans effet attendu sur la lecture IA, à vérifier sur de vrais relevés |
| Fichier iCloud / Drive non téléchargé | lecture par le fournisseur de documents | lecture directe ; pas de `NSFileCoordinator` | Possible sur iOS : « Impossible d'ouvrir ce fichier » si le fichier n'est pas sur l'appareil (non vérifié) |
| Taille | `OpenableColumns.SIZE` puis lecture plafonnée à 8 Mo ; PDF : `statSize` | `NSURLFileSizeKey` puis `NSData` | Non |
| Message PDF non lisible | mentionne Android | jamais affiché (PDFKit toujours présent) | Non |

Écarts avec le web (§9 de `specs/04`), à valider :

1. **Formats** : Excel (`.xls`, `.xlsx`, `.xlsm`, `.ods`) non lu (aucune bibliothèque au catalogue) ; le fichier reste sélectionnable et
   l'application demande d'exporter en CSV. Textes adaptés : « PDF ou CSV » (description, sous-texte du menu « Ajouter »).
2. **Encart de confiance** : « Votre fichier est anonymisé sur votre téléphone » ; le fichier ne quitte jamais l'appareil (le web
   l'envoie au serveur, qui l'anonymise).
3. **Consentement** : dialogue « Relevé et intelligence artificielle » (niveau `ANONYMIZED`) avant la première lecture ; absent du web.
4. **Après anonymisation** : ligne « {n} éléments personnels masqués sur votre téléphone » pendant la lecture des dépenses et quand le
   serveur ne peut pas recevoir le texte ; le bouton passe de « Lecture et anonymisation en cours… » à « Lecture des dépenses en cours… ».
5. **Quota** : « {n} import restant ce mois » dans le dialogue ; « Lire le fichier » désactivé à 0 avec le message de remise à zéro le 1er
   (adapté : « la dictée reste disponible », la saisie manuelle n'existant pas encore dans l'application).
6. **Erreurs** : affichées dans le dialogue (bannière) et traduites ; les messages serveur 413/422/429 sont reconnus par leur code et
   rendus en fr/en au lieu d'être affichés bruts. Perte : le détail « Ce fichier couvre N mois (… → …) » devient « plus de 2 mois ».
7. **Succès** : bannière de la fiche de revue « {n} dépenses lues — vérifiez et validez leur catégorie » (traduite ; non traduite sur le web).
8. **Anonymisation plus stricte que le serveur** : IBAN compact et téléphone français sans `+33` masqués ; la règle d'adresse s'arrête avant
   un montant (« PARKING PLACE DE LA GARE 12,50 » devient « PARKING [ADRESSE] 12,50 » au lieu de « PARKING [ADRESSE],50 »).

## Lot 6 — bilans (17/11/2026)

Écarts Android / iOS : aucun. Bandeau, dialogue et historique sont 100 % Compose ; aucun composant système (pas de notification).

Écarts avec le web (§5.10, §10.1, §10.2 de `specs/04`), à valider :

1. **Période envoyée** : toujours explicite (`?month=AAAA-MM` ou `?year=AAAA`) et calculée sur la date **locale** ; le web laisse parfois le
   serveur prendre le mois UTC. Une réponse pour une autre période est refusée (« Bilan indisponible pour le moment. »).
2. **Montants** : total, écart et lignes « Où part l'argent » formatés sur l'appareil dans la devise du profil (formateur commun) ; les
   points clés et pistes restent ceux du serveur, en français avec « € » arrondi (voir `BACKEND-GAPS.md`). En anglais, une ligne
   « These notes are written in French for now. » est ajoutée sous ces textes.
3. **Erreur du dialogue** : « Bilan indisponible pour le moment. » plus un bouton « Réessayer » (absent du web). « J'ai compris » n'apparaît
   qu'une fois le bilan chargé ; la croix ferme dans tous les états.
4. **Historique** : erreur « Historique indisponible pour le moment. » avec « Réessayer » (le web n'affiche rien) ; la liste suit le
   défilement de Mon espace au lieu d'une zone défilante de 420 px à en-têtes d'année collants (pas de liste défilante imbriquée).
   Lignes de 48 dp minimum.
5. **Bandeau** : un bandeau par période (mois, puis année en décembre) ; il disparaît dès le toucher, avant la confirmation de
   l'insertion dans `report_views` (si l'insertion échoue, il revient à la prochaine ouverture du tableau de bord). Recalculé à chaque
   retour au premier plan du tableau de bord.
6. **Titres** : « Bilan de {période} » repris tel quel du web (« Bilan de Octobre 2026 », sans élision) ; anglais « Report for October 2026 »,
   « Report for the year 2025 ».
7. **Hors périmètre** : notifications push, bannières `user_notifications` et rappel quotidien (lot 7, à concevoir).

## Lot 7 — notifications locales, partie A : `core:notifications` (19/11/2026)

Écarts Android / iOS (notifications système de chaque OS ; textes identiques, résolus depuis `NotificationCopy` dans la langue de
l'interface au moment de la planification) :

| Sujet | Android | iOS | Visible ? |
|---|---|---|---|
| Permission | `POST_NOTIFICATIONS` demandée seulement sur Android 13+ (API 33) ; en dessous, aucune demande : la réponse est « notifications activées » (`NotificationManagerCompat.areNotificationsEnabled`) | `requestAuthorization` (alerte + son), dialogue système une seule fois | **Oui**, rendu système ; sous Android 13, aucun dialogue |
| Lecture de l'état (`access()`) | activées → `GRANTED` ; Android 13+ sans la permission → `UNDETERMINED` (Android ne distingue pas « jamais demandée » de « refusée » sans l'activité ; une nouvelle demande après deux refus échoue aussitôt sans dialogue) ; permission accordée mais notifications coupées dans les réglages, ou Android < 13 coupées → `DENIED` | `authorized` / `provisional` / `ephemeral` → `GRANTED`, `denied` → `DENIED`, `notDetermined` → `UNDETERMINED` | **Oui** : sur Android, « refusé » peut apparaître comme « pas encore demandé » |
| Planification | `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP)`, sans permission d'alarme exacte : l'heure peut glisser de quelques minutes en veille profonde (Doze) | `UNCalendarNotificationTrigger` non répétitif, à l'heure près | **Oui** : rappel de 20 h parfois légèrement en retard sur Android |
| Redémarrage et mise à jour | le système efface les alarmes ; le plan (heures, textes génériques du rappel et des bilans, jamais d'alerte de budget) est gardé dans des `SharedPreferences` privées (`nemla.notifications`) et reprogrammé par un récepteur `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` | iOS conserve lui-même les notifications planifiées | Non (même résultat) |
| Écran verrouillé | « Budget atteint » et messages du serveur en `VISIBILITY_PRIVATE`, version publique titrée « nemla » sans texte ; rappel et bilans visibles | réglage système « Afficher les aperçus » (par défaut : quand déverrouillé), identique pour toutes les notifications | **Oui** : contenu masqué différemment |
| Application au premier plan | notification affichée normalement par le système | affichée grâce au délégué `UNUserNotificationCenterDelegate` (`installNotificationPresenter`, bannière + centre de notifications + son) | Non, une fois le délégué installé par `startNemla` |
| Canaux | 4 canaux créés à la première notification, noms fr/en : « Rappel quotidien », « Bilans », « Alertes de budget », « Messages nemla » (renommés si la langue change) ; réglables un par un dans les réglages Android | pas de canaux : un seul interrupteur pour l'application | **Oui**, réglages système |
| Icône | icône monochrome vectorielle (`nemla_notification_icon`, monogramme provisoire) | icône de l'application | **Oui** |
| Toucher | ouvre l'application par son intention de lancement (tableau de bord) | ouvre l'application (comportement par défaut, pas de lien profond) | Non |

Écarts avec le web (`specs/01` §5.5, `specs/04` §5.8, §5.11, §5.12), à valider :

1. **Rappel quotidien** : planifié 14 jours d'avance à 20 h locale, application fermée (le web vérifie toutes les 5 minutes, application
   ouverte). Le jour courant est sauté dès qu'une dépense confirmée **datée d'aujourd'hui** existe (lecture `expenses` `date=eq.<jour>`,
   `confirmed=is.true`, `limit=1`), ou après 20 h. Une dépense confirmée retire le rappel du jour (replanification après confirmation).
2. **Bilan arrivé** : nouvelle notification système à 10 h le premier jour du bandeau (dernier jour du mois − 2), mois courant et
   2 suivants, plus l'année en décembre ; absente du web (bandeau seulement). Texte anglais « Your report for {période} is ready »,
   aligné sur le bandeau de `feature:reports` (« Your {période} report is ready » donnerait « Your the year 2026 report »).
3. **Messages du serveur** (`channel = push`) : affichés en notification locale au retour au premier plan, puis `delivered_at` et
   `seen_at` ; titre vide remplacé par « nemla ». Ils restent en attente sans permission (le web ne les marque pas non plus).
4. **Mémoire des alertes de budget** : `SecureStore` (`notifications.v1`), clés `AAAA-MM:<catégorie>` du mois courant et du précédent
   seulement ; effacée à la déconnexion (`ClearNotifications`), comme toutes les notifications planifiées et affichées.

## Lot 7 — notifications locales, partie B : rappel, alertes, bannières, coordinateur (19/11/2026)

Écarts Android / iOS (écrans et logique 100 % Compose / `commonMain` ; seuls la demande de permission et l'affichage restent propres
à chaque OS, voir partie A) :

| Sujet | Android | iOS | Visible ? |
|---|---|---|---|
| Activation du rappel sans permission | Android 13+ : dialogue système ; après deux refus, Android ne l'affiche plus et la réponse est immédiate → « Notifications refusées… » ; Android < 13 : pas de dialogue, réponse selon l'état des notifications de l'application | dialogue système la première fois seulement ; ensuite réponse immédiate → « Notifications refusées… » | **Oui**, rendu système |
| Interrupteur « Rappel quotidien à 20 h » | `Switch` Material 3 (`NemlaSwitch`) | le même (pas de `UISwitch`) | Non entre plateformes ; non natif sur iOS |
| « Premier plan » du coordinateur | `Lifecycle.State.STARTED` de l'activité | `STARTED` du `ComposeUIViewController` (retour d'arrière-plan) | À vérifier sur simulateur iOS |

Écarts avec le web (`specs/04` §5.8, §5.11, §12.6), à valider :

1. **Interrupteur du rappel** : allumé seulement si `profiles.daily_reminder` **et** permission accordée (le web montre le seul champ
   du profil) ; permission retirée dans les réglages du téléphone → interrupteur éteint, profil inchangé. Message de refus propre au
   mobile : « Notifications refusées. Autorisez-les dans les réglages du téléphone. » / « Notifications blocked. Allow them in your
   phone settings. » (pas de « Votre navigateur ne gère pas les notifications. »). Messages en bannière (4 s) au lieu d'un toast.
2. **Alertes « Budget atteint »** : vérifiées après chaque chargement du tableau de bord (ouverture, retour au premier plan, tirer pour
   actualiser, après une modification), pour le mois calendaire courant quelle que soit la période affichée ; une seule fois par
   catégorie et par mois.
3. **Bannières in-app** : relues au retour au premier plan et à chaque affichage du tableau de bord, pas toutes les 30 s ; carte entière
   cliquable, bouton X de 32 px. Une bannière masquée disparaît aussitôt, même si l'écriture de `seen_at` échoue (elle reviendra au
   prochain lancement).
4. **Bilan d'une notification sans `payload`** : message « Bilan indisponible pour le moment. » ; « Réessayer » seulement si la
   notification porte une période (recalcul par `GET /api/public/report/monthly`), sinon « J'ai compris » ; titre « Votre bilan » si
   aucune période n'est connue.
5. **Changement de langue** : les notifications planifiées sont replanifiées dans la nouvelle langue (sans objet sur le web).
