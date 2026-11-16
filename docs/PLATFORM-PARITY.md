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
9. **Alertes de budget** : carte in-app seulement (notification système au lot 6) ; lien « Ajuster mes budgets par catégorie » omis
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
    (§5.11, §5.12 → lot 6), dialogue « débit immédiat ou différé » `PaymentModeGate` et réglages du débit (§11.2, §11.3 → lot 4).
