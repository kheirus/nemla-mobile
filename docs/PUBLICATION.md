# Préparer la publication (Google Play et App Store)

Ce document ne contient aucun secret. Clés de signature, mots de passe, identifiants de comptes développeur et jetons d'API restent
hors du dépôt (variables d'environnement, propriétés Gradle de l'utilisateur, trousseau macOS, secrets de la CI).
Références : `ARCHITECTURE.md` §5, `../docs/BACKEND-GAPS.md`, `../docs/specs/01` §2.7 et §5.

## 1. Ce que l'application envoie réellement (base des déclarations)

| Donnée | Quand | Destinataire | Stockée côté serveur ? |
|---|---|---|---|
| Email, mot de passe (authentification) | inscription, connexion, mot de passe oublié | Supabase Auth (GoTrue) | email oui ; mot de passe haché par Supabase |
| Pseudo, âge, sexe, situation familiale, devise, budget, réglage du débit différé (banque, jours) | inscription, Mon espace | Supabase (PostgREST, RLS) | oui, table `profiles` |
| Dépenses : montant, date, catégorie, moyen de paiement, statut | dictée, revue, tableau de bord | Supabase | oui ; libellés chiffrés côté serveur |
| Charges fixes : nom, montant, fréquence, jour | onboarding, Mon espace | Supabase | oui, **nom en clair** |
| Catégories, budgets par catégorie | Mon espace | Supabase | oui |
| Audio de la dictée (WAV 16 kHz, 30 s max, en mémoire sur l'appareil) | dictée, après accord IA | backend nemla (Lovable), puis **OpenAI** (`gpt-4o-transcribe`) via la passerelle IA Lovable | audio : non (jamais écrit dans le bucket `voice-notes` par le mobile) ; transcription : chiffrée dans `voice_entries` |
| Transcription de la dictée | dictée | **Google** (`gemini-3.7-flash`) via Lovable, pour extraire les dépenses | chiffrée |
| Texte libre des charges fixes (tapé ou dicté) | onboarding, Mon espace | Google via Lovable | non (seules les charges validées sont enregistrées) |
| Texte d'un relevé bancaire **anonymisé sur l'appareil** | import, après accord IA | Google via Lovable (endpoint `POST /api/public/import/text`, encore absent du backend) | non ; seules les dépenses lues |
| Clés marchand normalisées (sans libellé brut) | validation d'une catégorie | Supabase (`record_category_correction`) | oui |
| Vues des bilans (`report_views`) | ouverture d'un bilan depuis le bandeau | Supabase | oui |

Jamais envoyé : le fichier du relevé, l'audio vers un stockage, la position, les contacts, les identifiants publicitaires, des données
d'usage ou de plantage. Aucun SDK d'analyse, de publicité, de suivi ni de rapport de plantage. Aucune notification (lot 7).
Stocké sur l'appareil : session chiffrée (Keystore / Keychain) et préférences non sensibles (langues, thème, accords IA) ; aucune
donnée financière.

## 2. Liste de contrôle d'une version

### Commun
- [ ] `./gradlew spotlessCheck allTests` vert ; `./gradlew :androidApp:assembleRelease` ; framework iOS en Release.
- [ ] `local.properties` / `iosApp/Config.xcconfig` de production fournis par la machine de build ou la CI, jamais commités.
- [ ] URL d'API et clé publishable Supabase de **production** ; aucune autre clé dans le binaire (`strings` sur l'APK / l'IPA).
- [ ] Journal HTTP désactivé en release (`NoOpLogger`) ; `FLAG_SECURE` actif ; voile iOS actif.
- [ ] Essai complet sur appareil Android et sur iPhone (inscription, dictée, revue, import, Mon espace, bilans, suppression de compte).
- [ ] Textes fr/en relus ; captures d'écran fr et en, clair et sombre.

### Android (Google Play)
- [ ] Numéros : `versionCode` (entier strictement croissant) et `versionName` dans `androidApp/build.gradle.kts`
      (aujourd'hui `1` / `0.1.0`). Les dériver d'une propriété Gradle ou de la CI plutôt que de les modifier à la main.
- [ ] Signature : configuration `signingConfigs.release` lue depuis des propriétés Gradle de l'utilisateur
      (`~/.gradle/gradle.properties`) ou des variables d'environnement (`NEMLA_KEYSTORE_PATH`, `NEMLA_KEYSTORE_PASSWORD`,
      `NEMLA_KEY_ALIAS`, `NEMLA_KEY_PASSWORD`, noms proposés). Keystore d'envoi hors dépôt ; Play App Signing activé.
      **À construire** : la configuration de signature n'existe pas encore dans le projet.
- [ ] Format : Android App Bundle (`:androidApp:bundleRelease`).
- [ ] R8 : `isMinifyEnabled` et `isShrinkResources` déjà actifs ; garder `mapping.txt` de chaque version (hors dépôt) et l'envoyer à la
      Console ; relancer le parcours complet sur l'AAB minifié.
- [ ] `targetSdk` à jour de l'exigence Google Play de l'année ; permission `RECORD_AUDIO` seule permission sensible.
- [ ] Formulaire **Data safety** (réponses ci-dessous), classification du contenu, public cible (adultes ; pas d'enfants), coordonnées,
      URL de politique de confidentialité, URL de suppression de compte (exigée par Google Play).

### iOS (App Store)
- [ ] Numéros : `MARKETING_VERSION` (version affichée) et `CURRENT_PROJECT_VERSION` (numéro de build, strictement croissant pour une même
      version) dans `iosApp/Base.xcconfig` (aujourd'hui `0.1.0` / `1`).
- [ ] Signature : équipe et profils gérés par Xcode (signature automatique) ou par la CI avec une clé App Store Connect API stockée dans
      les secrets ; jamais de certificat ni de `.p8` dans le dépôt.
- [ ] `PrivacyInfo.xcprivacy` à **compléter** avant l'envoi : il déclare aujourd'hui aucune donnée collectée et aucune API à motif requis.
      - `NSPrivacyAccessedAPITypes` : l'application lit et écrit `NSUserDefaults` (`AppleLanguages`, langue de l'interface) : déclarer
        `NSPrivacyAccessedAPICategoryUserDefaults` avec le motif `CA92.1`. Vérifier les autres API à motif requis utilisées par le
        framework Kotlin et Compose (date de fichier, espace disque, heure de démarrage) avec le rapport de confidentialité d'Xcode
        (Product › Archive › Generate Privacy Report).
      - `NSPrivacyCollectedDataTypes` : reprendre les catégories de la section 3, toutes `Linked = true`, `Tracking = false`,
        finalité « App Functionality ».
      - `NSPrivacyTracking` = `false`, aucun domaine de suivi.
- [ ] `NSMicrophoneUsageDescription` traduite en anglais (`InfoPlist.strings`), encore à faire (ROADMAP lot 2).
- [ ] Réponses **App Privacy** dans App Store Connect (section 3), URL de politique de confidentialité, URL d'assistance, catégorie
      Finance, classement par âge, compte de démonstration pour la revue Apple (créé sur un environnement de test, identifiants saisis
      dans App Store Connect, jamais dans le dépôt).
- [ ] Suppression de compte depuis l'application (règle 5.1.1(v)) : fonctionnelle, pas seulement un bouton.

## 3. Réponses proposées aux formulaires (à valider)

Aucun suivi publicitaire, aucune donnée vendue, aucun partage à des fins publicitaires. Chiffrement en transit : oui (HTTPS seulement).
Suppression à la demande : à cocher **seulement** quand `DELETE /api/public/account` existera.

| Catégorie (Google Data safety / Apple App Privacy) | Collectée | Finalité | Optionnelle | Remarque |
|---|---|---|---|---|
| Informations personnelles : email | oui | gestion du compte | non | Supabase Auth |
| Informations personnelles : nom d'utilisateur (pseudo), âge, sexe, situation familiale | oui | fonctionnalité (personnalisation) | âge, sexe, situation : oui | `profiles` |
| Informations financières : autres (dépenses, budgets, charges fixes, banque du débit différé) | oui | fonctionnalité | non | pas de numéro de carte ni de compte ; relevé anonymisé avant envoi |
| Audio : enregistrements vocaux | oui (traitement) | fonctionnalité | oui (la saisie vocale est facultative) | traité par OpenAI via Lovable ; non stocké par le mobile |
| Contenu utilisateur : autre (transcriptions, texte des charges fixes) | oui | fonctionnalité | oui | Google via Lovable |
| Fichiers et documents | non | — | — | le relevé ne quitte pas l'appareil, seul un texte anonymisé part |
| Identifiants (appareil, publicité) | non | — | — | |
| Données d'utilisation, diagnostics, plantages | non | — | — | aucun SDK |
| Position, contacts, photos, santé | non | — | — | |

Partage avec des tiers (Google) : les prestataires agissant pour le compte de nemla (Supabase, Lovable, OpenAI, Google) relèvent en
principe de la « collecte » et non du « partage » au sens de Google Play ; à confirmer par le conseil juridique avec les contrats de
sous-traitance.

## 4. Bloquants avant soumission

1. **Suppression de compte** : `DELETE /api/public/account` (`../docs/BACKEND-GAPS.md`) ; le bouton existe, le port renvoie `NotSupportedYet`.
   Corriger aussi la purge (audio du bucket `voice-notes`, email en clair dans `deleted_accounts`). Exigé par les deux stores, ainsi
   qu'une page web de demande de suppression pour Google Play.
2. **Politique de confidentialité** publiée à une URL stable (brouillon : `../docs/PRIVACY-POLICY-DRAFT.md`, relecture juridique requise) et
   liée depuis l'application (Mon espace) et les fiches.
3. **Lien de réinitialisation du mot de passe** : App Links (Android) et Universal Links (iOS) déclarés dans Supabase, écran
   « Nouveau mot de passe » dans l'application (ROADMAP lot 1).
4. **Sécurité backend** (`../docs/BACKEND-GAPS.md`) : RLS `user_quotas` et `profiles.suspended`, quotas et limites de taille de `/transcribe`
   et `/parse/recurring`, statuts 401/402/429 de la passerelle IA, réponses HTML, dépendance `xlsx`, anonymisation serveur de l'IBAN
   compact.
5. **Sous-traitants** déclarés (registre RGPD, contrats, transferts hors UE vers OpenAI et Google) ; mention dans la politique.
6. **Export des données** (droit à la portabilité) : endpoint absent ; à construire ou à justifier dans la politique.
7. **Polices et logo** définitifs (Outfit, DM Sans, `nemla-logo`), icônes Android (adaptative) et iOS (fond opaque).
8. **Import de relevé** : `POST /api/public/import/text` absent ; soit l'endpoint est livré, soit la fiche ne promet pas l'import.

## 5. Fiches des stores (brouillons)

Seules des fonctions présentes dans l'application sont citées. Pas d'import de relevé dans les fiches tant que l'endpoint manque
(phrase entre crochets à ajouter ensuite). Pas de notifications (lot 7).

### Français

- **Titre** (30 caractères max) : `nemla – budget à la voix`
- **Description courte** (80 caractères max) : `Dictez vos dépenses, nemla les classe et montre où part votre argent.`
- **Description complète** :

  > nemla, c'est le budget sans saisie fastidieuse. Appuyez sur « Ajouter », dites ce que vous avez dépensé (« boulangerie 4 euros,
  > essence 60 euros hier ») : nemla comprend les montants, les dates et propose une catégorie. Vous vérifiez, vous validez.
  >
  > • Dictée de 30 secondes, en français ou en anglais, avec vérification avant enregistrement.
  > • Tableau de bord du mois ou de l'année : montant dépensé, météo du budget, graphique, « Où part l'argent » par catégorie.
  > • Budgets globaux et par catégorie, avec alertes quand un budget est atteint.
  > • Charges fixes (loyer, abonnements, assurances) saisies ou dictées en vrac.
  > • Carte à débit différé prise en compte dans vos totaux.
  > • Bilans mensuels et annuels : total, évolution, postes principaux et pistes d'amélioration, et tout l'historique.
  > • Dix devises d'affichage, thème clair ou sombre.
  > [• Import d'un relevé PDF ou CSV, anonymisé sur votre téléphone avant toute lecture.]
  >
  > Confidentialité : aucune publicité, aucun traceur. L'audio n'est jamais stocké par l'application et n'est envoyé pour la transcription
  > qu'avec votre accord, que vous pouvez retirer à tout moment. Votre session est chiffrée sur l'appareil.

- **Mots-clés / tags** : budget, dépenses, dictée vocale, gestion de budget, finances personnelles, suivi des dépenses, bilan mensuel,
  charges fixes, économies, débit différé.

### English

- **Title**: `nemla – voice budget`
- **Short description**: `Speak your expenses in 30 seconds; nemla sorts them and shows where money goes.`
- **Full description**:

  > nemla is budgeting without tedious typing. Tap "Add", say what you spent ("bakery 4 euros, petrol 60 euros yesterday"): nemla
  > understands amounts and dates and suggests a category. You check, you confirm.
  >
  > • 30-second dictation, in English or French, reviewed before anything is saved.
  > • Monthly or yearly dashboard: amount spent, budget weather, chart, and where the money goes by category.
  > • Overall and per-category budgets, with alerts when a budget is reached.
  > • Fixed costs (rent, subscriptions, insurance) typed or dictated in one go.
  > • Deferred-debit cards taken into account in your totals.
  > • Monthly and yearly reports: total, change, main spending areas and ways to improve, with the full history.
  > • Ten display currencies, light or dark theme.
  > [• Import a PDF or CSV bank statement, anonymised on your phone before anything is read.]
  >
  > Privacy: no ads, no trackers. Audio is never stored by the app and is only sent for transcription with your consent, which you can
  > withdraw at any time. Your session is encrypted on the device.

- **Keywords** (App Store, 100 characters): `budget,expenses,voice,dictation,spending tracker,personal finance,monthly report,savings`

À noter pour l'anglais : les points clés des bilans sont encore rédigés en français par le serveur (`../docs/BACKEND-GAPS.md`).
