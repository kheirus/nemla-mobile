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
Points d'entrée : `MainActivity` (Android) et `mainViewController()` de `shared/iosMain` (iOS) affichent tous deux un écran Compose vide.

Écarts entre le web et le mobile à trancher (voir `specs/03` et `specs/04`) : graisse 600 absente de DM Sans (rendue en 700 sur le web),
ambre au survol sans équivalent tactile, toasts en haut sur le web, fenêtres modales pleine largeur à coins carrés sur mobile web,
logo absent du dépôt web (hébergé sur le CDN Lovable).
