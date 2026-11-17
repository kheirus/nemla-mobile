# Politique de confidentialité de nemla — BROUILLON

> **BROUILLON NON VALIDÉ. Relecture juridique obligatoire avant toute publication.**
> Rédigé à partir du code et des spécifications (`specs/01`, `specs/02`, `ARCHITECTURE.md` §5, `BACKEND-GAPS.md`) au 17/11/2026.
> Les éléments entre crochets sont à compléter par le responsable du traitement. Les passages marqués **[À DÉCIDER]** décrivent un
> comportement actuel qui pose question ou une décision à prendre. Ce texte ne vaut pas avis juridique.

Dernière mise à jour : [date]

## 1. Qui est responsable de vos données

[Raison sociale], [forme juridique], [numéro d'immatriculation], [Adresse].
Contact pour toute question sur vos données : [Email de contact].
Délégué à la protection des données : [nom ou « non désigné »], [email].

nemla est une application de gestion de budget (Android, iOS et web nemla.app) qui permet de saisir ses dépenses à la voix.

## 2. Les données que nous traitons

| Données | D'où elles viennent | Pourquoi | Base légale (à valider) |
|---|---|---|---|
| Adresse email, mot de passe (stocké sous forme hachée par notre prestataire d'authentification) | inscription | créer et sécuriser votre compte | exécution du contrat |
| Pseudo, âge, sexe, situation familiale | inscription, « Mon profil » (âge, sexe et situation sont facultatifs) | personnaliser l'application et vos bilans | exécution du contrat / consentement **[À DÉCIDER]** |
| Devise d'affichage, budgets, banque et jours du débit différé | réglages | calculer vos totaux et la météo du budget | exécution du contrat |
| Dépenses : montant, date d'achat, date de débit, catégorie, moyen de paiement, libellé | dictée, saisie, validation | tenir votre budget | exécution du contrat |
| Charges fixes : nom, montant, fréquence, jour de prélèvement | onboarding, réglages | reporter vos charges chaque mois | exécution du contrat |
| Enregistrement vocal (30 secondes au plus) et sa transcription | dictée | transformer votre dictée en dépenses | consentement (demandé avant la première dictée) |
| Texte d'un relevé bancaire, **anonymisé sur votre téléphone** | import d'un relevé | lire les dépenses du relevé | consentement (demandé avant le premier import) |
| Corrections de catégorie (clé marchand normalisée, catégorie proposée et choisie) | validation des dépenses | améliorer le classement automatique | intérêt légitime **[À DÉCIDER]** |
| Bilans déjà consultés, quotas de dictée et d'import du mois | utilisation | afficher les bilans et appliquer les quotas | exécution du contrat |
| Journaux techniques d'appels IA (action, modèle, nombre de jetons, coût estimé) | serveur | suivre les coûts et les abus | intérêt légitime |

Nous ne collectons pas : votre position, vos contacts, vos photos, des identifiants publicitaires, des statistiques d'usage ou des
rapports de plantage. L'application ne contient **aucun outil de publicité, d'analyse ou de suivi**.

Sur votre téléphone ne sont conservés que votre session (chiffrée par le système : Keystore Android, Trousseau iOS), vos préférences
(langues, thème) et vos accords pour l'IA. Aucune donnée financière n'y est stockée ; l'application mobile n'enregistre jamais l'audio
sur le téléphone ni dans un espace de stockage en ligne. Le fichier d'un relevé n'est ni copié ni envoyé : seul un texte dont l'IBAN,
les numéros de compte ou de carte, le nom du titulaire, l'email, le téléphone et l'adresse ont été masqués quitte l'appareil.

Les libellés des dépenses et les transcriptions sont chiffrés dans notre base de données. Les montants, dates, catégories et les noms des
charges fixes ne le sont pas.

## 3. Intelligence artificielle

Certaines fonctions s'appuient sur des modèles d'IA d'entreprises tierces, appelés par notre serveur via la passerelle de Lovable :

- **transcription de la dictée** : l'enregistrement vocal est envoyé à OpenAI (modèle de transcription) ;
- **lecture des dépenses** : la transcription, le texte des charges fixes et le texte anonymisé des relevés sont envoyés à Google
  (modèle Gemini) avec la liste de vos catégories et, pour la dictée, des indices de classement tirés des marchands déjà catégorisés.

Ces envois n'ont lieu qu'après votre accord explicite, que vous pouvez retirer dans Mon espace › Préférences (« Retirer mon accord »).
Sans accord, la dictée et l'import ne sont pas utilisés ; le reste de l'application fonctionne.
Aucune décision produisant des effets juridiques n'est prise automatiquement : vous validez chaque dépense proposée.
[Préciser les engagements contractuels des fournisseurs d'IA : absence d'entraînement sur vos données, durée de conservation chez eux.]
**[À DÉCIDER]** : les journaux et la facturation de la passerelle IA ne sont pas sous notre contrôle.

Les bilans mensuels et annuels sont calculés par notre serveur par des règles fixes, sans IA.

## 4. Destinataires et sous-traitants

| Prestataire | Rôle | Données | Localisation |
|---|---|---|---|
| Supabase | base de données, authentification | toutes les données du compte | [région du projet Supabase] |
| Lovable | hébergement du service web et passerelle IA | requêtes de l'application, données transmises à l'IA | [à compléter] |
| OpenAI | transcription | enregistrements vocaux | [États-Unis ?] |
| Google | lecture des dépenses (Gemini) | transcriptions, textes de charges fixes, relevés anonymisés | [à compléter] |
| Apple, Google (stores) | distribution de l'application | selon leurs propres politiques | — |

Transferts hors de l'Union européenne : [mécanisme : décision d'adéquation, clauses contractuelles types, Data Privacy Framework].
Nous ne vendons pas vos données et ne les partageons pas à des fins publicitaires. Une équipe d'administration restreinte peut voir
les montants, dates, catégories, charges fixes et le profil (email masqué), mais pas les libellés déchiffrés ; ses actions sont
journalisées. **[À DÉCIDER]** certaines actions d'administration (quotas, notifications de test) ne sont pas encore journalisées.

## 5. Durées de conservation

**[À DÉCIDER] Aucune durée de conservation n'est définie aujourd'hui.** État actuel :

- les données du compte sont conservées tant que le compte existe ;
- les bilans ne sont pas stockés : ils sont recalculés à chaque ouverture ; seule la liste des bilans déjà vus est conservée ;
- à la suppression du compte, sont effacés : dépenses, charges fixes, catégories, dictées et transcriptions, profil, quotas, règles et
  corrections de catégorie, journaux, bilans vus ;
- **subsistent après suppression** : l'adresse email dans une table d'archive, sans limite de durée ; les éventuels fichiers audio
  déposés par la version web dans l'espace de stockage ; l'identifiant (pseudonyme) dans le journal d'administration ; des statistiques
  agrégées de marchands (mutualisées à partir d'au moins trois utilisateurs, sans lien avec vous).

Durées proposées à arrêter : [compte inactif : X ans], [archive email : X mois, pour prévenir la fraude], [journaux techniques : X mois].

## 6. Vos droits

Vous disposez des droits d'accès, de rectification, d'effacement, de limitation, d'opposition et de portabilité, ainsi que du droit de
retirer votre consentement à tout moment et de définir des directives sur le sort de vos données après votre décès.

- **Rectification** : directement dans Mon espace (profil, budget, catégories, charges fixes) et sur chaque dépense.
- **Retrait du consentement IA** : Mon espace › Préférences.
- **Effacement** : la suppression du compte depuis l'application **n'est pas encore disponible** (le bouton affiche un message) ; en
  attendant, écrivez à [Email de contact]. [Décrire le délai de traitement.]
- **Portabilité / export** : **aucun export n'existe aujourd'hui**, ni dans l'application ni sur le web. En attendant, adressez votre
  demande à [Email de contact]. [Délai.]
- Pour toute autre demande : [Email de contact]. Vous pouvez introduire une réclamation auprès de la CNIL (www.cnil.fr) [ou de
  l'autorité compétente].

## 7. Sécurité

Connexions chiffrées (HTTPS uniquement), session chiffrée sur l'appareil, libellés et transcriptions chiffrés en base, accès aux données
limité à votre compte par des règles de sécurité de la base. Sur Android, les captures d'écran de l'application sont bloquées ; sur iOS,
l'écran est masqué dans le sélecteur d'applications.
**[À DÉCIDER]** : des corrections de sécurité côté serveur sont en cours (`BACKEND-GAPS.md`) ; ne pas publier avant leur résolution.

## 8. Mineurs

L'inscription demande un âge compris entre 14 et 120 ans. [Préciser l'âge minimum retenu et la règle applicable au consentement des
mineurs (15 ans en France pour les services de la société de l'information).]

## 9. Notifications

L'application mobile n'envoie aucune notification à ce jour. [À compléter quand le lot 7 sera conçu.]

## 10. Modifications

Nous vous informerons de toute modification importante de cette politique dans l'application. [Modalités.]
