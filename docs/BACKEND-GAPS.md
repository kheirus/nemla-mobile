# Ce que le backend doit fournir au mobile

Détail et références de code : `specs/01-api-contracts.md` (§3.3, §5) et `specs/02-data-model.md`.

## Bloquant pour le mobile
| Besoin | Aujourd'hui | À créer |
|---|---|---|
| Lire, créer, modifier, renommer des dépenses (libellés chiffrés) | server functions TanStack, non appelables | `GET/POST /api/public/expenses`, `PUT /api/public/expenses/{id}`, `POST /api/public/expenses/rename`, `GET /api/public/expenses/same-label` |
| Afficher les libellés au tableau de bord et chercher par mot-clé | `listExpenses` (server function, déchiffrement en mémoire) | `GET /api/public/expenses` avec `search`, `dateEq`, `dateFrom`/`dateTo`, `confirmed`, `limit` (déjà demandé ci-dessus) ; lot 3 : montants et dates lus en PostgREST, libellé de repli « Dépense », titre de groupe « Dépense vocale… », recherche par date seule |
| Lire la transcription d'une dictée | server function | `GET /api/public/voice/{id}/transcript` (lot 2 : contourné, la transcription vient de la réponse de `voice/process`) |
| Retrouver les dépenses en attente de revue après un redémarrage, et détecter les doublons pendant la revue | aucun accès aux libellés | `GET /api/public/expenses?confirmed=false` et `?confirmed=true&dateFrom=` (déjà demandé ci-dessus) |
| Modifier le libellé d'une dépense dictée pendant la revue | `expense_save` exige la clé serveur | `PUT /api/public/expenses/{id}` (déjà demandé ci-dessus) ; lot 2 : libellé en lecture seule |
| Dater une dictée selon le jour local de l'utilisateur | `voice/process` prend la date UTC du serveur | champ facultatif `today` (AAAA-MM-JJ) dans le multipart de `voice/process` |
| Journal de correction | server function | `POST /api/public/classification-log` |
| Générer les occurrences des charges fixes | server function | `POST /api/public/recurring/materialize` (lot 3 : port `RecurringChargeMaterializer` en `NotSupportedYet`, appel ignoré ; les charges fixes ne sont donc comptées dans les totaux que si le web les a déjà générées) |
| Supprimer son compte (exigé par App Store et Google Play) | server function | `DELETE /api/public/account` (lot 4 : bouton et confirmation présents dans Mon espace, message « pas encore disponible depuis l'application », rien n'est supprimé) |
| Exporter ses données (droit à la portabilité, RGPD art. 20) | absent du web et du backend | `GET /api/public/account/export` (JSON ou CSV : profil, catégories, dépenses avec libellés déchiffrés, charges fixes, quotas) ; lot 4 : rien n'est construit côté mobile |
| Lire un relevé déjà anonymisé sur l'appareil | `/import/file` ne prend que le fichier brut | `POST /api/public/import/text` (texte anonymisé), contrat ci-dessous ; lot 5 : tant qu'il répond 404/405, l'application affiche « L'import de relevé depuis l'application arrive bientôt… Rien n'a été envoyé. » |
| Extraire des dépenses d'un texte (pour la transcription locale) | absent | `POST /api/public/expenses/extract` |
| Bilans dans la langue et la devise de l'utilisateur | `report/monthly` rédige `highlights` et `advice` en français, avec des montants en « € » arrondis à l'euro (formateur `fr-FR` codé en dur), quels que soient `lang` et `profiles.devise` | version structurée : codes et valeurs (`{"code":"top_category","category":"Courses","share":32,"amount":400.25}`…) que l'application rédige elle-même en fr/en, ou au minimum un paramètre `lang` et la devise du profil ; lot 6 : textes affichés tels quels, mention « These notes are written in French for now. » en anglais |
| Phrase de variation de `report/monthly` à écart nul | `previous_total>0` et écart arrondi à 0 : « Vous avez dépensé -0 € de moins que le mois précédent. Beau travail. » (vu sur septembre 2026) | ne pas produire la phrase quand l'écart arrondi vaut 0, ou une phrase « autant que » ; lot 6 : affichée telle quelle |
| Valider le paramètre `month` de `report/monthly` | non validé (bornes `NaN`, 500 ou résultat vide) ; défaut = mois UTC | `400` sur un mois malformé ; lot 6 : le mobile envoie toujours une période explicite calculée sur la date locale et la vérifie dans la réponse |
| Notifications | aucune notification serveur | table de jetons, envoi FCM / APNs planifié (lot 7, à concevoir : push serveur ou Firebase) |
| Lien de réinitialisation du mot de passe | redirection web | App Links / Universal Links déclarés dans Supabase |
| URL de l'API, URL et clé publishable Supabase | absentes du dépôt | à fournir |

## Contrat proposé : `POST /api/public/import/text` (lot 5)

Le mobile extrait le texte du fichier et l'anonymise **sur l'appareil** (règles de `specs/01` §6.2, plus l'IBAN compact et le téléphone
français sans `+33`). Il n'appelle jamais `/import/file`. L'endpoint reprend le pipeline de `importStatementForUser` (`specs/01` §6.1)
**à partir de l'étape 4** : pas d'extraction, pas d'anonymisation serveur obligatoire (une seconde passe `anonymizeStatement` reste
permise et sans effet sur un texte déjà masqué).

- Authentification : `Authorization: Bearer <access_token>`, comme les autres `/api/public/*` (§1).
- Corps `application/json` :
  ```json
  { "text": "<texte anonymisé, 20 à 90 000 caractères>", "lang": "fr|en", "today": "AAAA-MM-JJ", "kind": "pdf|csv|texte" }
  ```
  - `text` : requis ; texte déjà nettoyé (`cleanupText`) et tronqué à 90 000 caractères par l'appareil.
  - `lang` : `normalizeLang` (tout sauf `"en"` donne `"fr"`).
  - `today` : date locale de l'utilisateur ; remplace la date UTC du serveur pour les lignes sans date et le contrôle de période.
    Valeur invalide ou absente : date UTC du serveur.
  - `kind` : **facultatif**, défaut `"texte"` ; sert seulement à `usage_logs.metadata`. Le mobile ne l'envoie pas aujourd'hui
    (`StatementParsingRequest` de `:ai:api` ne porte pas le format ; à arbitrer).
- Contrôles, dans cet ordre, **avant tout appel IA** :
  1. corps sans `text` (y compris la sonde `{}`), `text` non textuel : `400 {"error":"Texte manquant"}` en JSON, **sans** décompter le
     quota ni appeler l'IA. Le mobile envoie `{}` avant chaque import et n'envoie le texte que si la sonde reçoit une réponse JSON
     400, 401, 413, 422 ou 429. Toute autre réponse garde le texte sur l'appareil : 404, 405, corps non JSON, et
     `500 {"error":"Only HTML requests are supported here"}` (réponse actuelle de l'hébergeur web à une route inconnue quand le client
     envoie `Accept: application/json`) valent « pas encore disponible » ; une autre 5xx, un délai dépassé ou l'absence de réseau
     valent « import impossible » ;
  2. `text` de plus de 90 000 caractères ou corps de plus de 8 Mo : `413 {"error":"Texte trop volumineux"}` ;
  3. `text` trimé de moins de 20 caractères : `422 {"error":"Impossible de lire ce fichier (PDF scanné ou vide ?). Essayez un CSV."}` ;
  4. quota : `ensure_month_quota`, puis `429` avec le message actuel de `/import/file` (`"Quota d'import de relevé atteint pour ce mois…"`,
     doit commencer par `Quota`) ;
  5. catégories : `400 {"error":"Aucune catégorie configurée"}`.
- Traitement : étapes 6 à 13 de §6.1 à l'identique (découpage, extraction parallèle, déduplication, `422 "Aucune dépense détectée dans ce
  fichier"`, `422 "Ce fichier couvre <n> mois…"` au-delà de 2 mois, 400 lignes, `expense_save` avec `confirmed=false`, `source="manual"`,
  `usage_logs` avec `action "import.text"`).
- **Quota décompté après le succès seulement** (`increment_import_quota`), comme `/import/file`. Une 404/405, une 4xx de contrôle ou
  une erreur IA ne consomme rien.
- Réponse 200 identique à `/import/file` :
  ```json
  { "kind": "texte", "redactions": 0,
    "expenses": [ { "id": "uuid", "montant": 12.5, "description": "Carrefour market", "date": "AAAA-MM-JJ",
                    "category_id": "uuid|null", "source": "manual" } ] }
  ```
  `redactions` compte les masquages de la seconde passe serveur (0 attendu) ; le mobile affiche son propre compte.
- Erreurs : toujours `{"error": string}` en JSON (pas de page HTML) ; les messages 413/422/429 ci-dessus sont reconnus par leur début
  (`Quota`, `Aucune dépense`, `Impossible de lire`, `Ce fichier couvre`, `Aucune catégorie`) et affichés traduits par l'application.
- Le texte n'est jamais stocké ni journalisé côté serveur ; il part chez le sous-traitant IA (Google via Lovable) à déclarer.

## Sécurité et RGPD à corriger avant publication
- RLS : un utilisateur peut modifier ses propres quotas (`user_quotas`) et lever sa suspension (`profiles.suspended`).
- `/transcribe` et `/parse/recurring` : ni quota ni limite de taille. `/transcribe` renvoie un objet au lieu d'une chaîne
  (le mobile lit `transcript.text` ou une chaîne ; lot 4 : utilisés par l'onboarding et « Mes charges fixes »).
- RLS `profiles` : `onboarding_done` est modifiable par l'utilisateur (l'aiguillage vers l'onboarding n'est donc pas une garantie serveur).
- Statuts 401/402/429 de la passerelle IA renvoyés tels quels ; certaines erreurs arrivent en HTML.
- Anonymisation des relevés (`anonymizeStatement`) : un IBAN écrit sans espaces (`FR7630006000011234567890189`, courant dans les CSV)
  n'est capté par aucune règle (ni IBAN, ni carte, ni numéro, faute de frontière de mot) et part en clair vers la passerelle IA.
  L'anonymiseur mobile ajoute une règle pour ce cas ; `/import/file` doit être corrigé de la même façon.
- Purge de compte : audio du bucket `voice-notes` non supprimé ; email conservé en clair dans `deleted_accounts`.
- Pas d'export des données, pas de politique de confidentialité, pas de recueil de consentement.
- Sous-traitants à déclarer : l'audio et les transcriptions partent chez OpenAI, les relevés anonymisés chez Google, via Lovable.
- Chiffrement : symétrique, clé unique pour tous, limité aux libellés et transcriptions (montants, dates, noms des charges fixes en clair).
- Dépendance `xlsx` 0.18.5 vulnérable côté serveur.
