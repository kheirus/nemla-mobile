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

Écarts entre le web et le mobile à trancher (voir `specs/03` et `specs/04`) : graisse 600 absente de DM Sans (rendue en 700 sur le web),
ambre au survol sans équivalent tactile, toasts en haut sur le web, fenêtres modales pleine largeur à coins carrés sur mobile web,
logo absent du dépôt web (hébergé sur le CDN Lovable).
