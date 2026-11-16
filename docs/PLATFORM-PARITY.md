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

