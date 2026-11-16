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
| Supprimer son compte (exigé par App Store et Google Play) | server function | `DELETE /api/public/account` |
| Lire un relevé déjà anonymisé sur l'appareil | `/import/file` ne prend que le fichier brut | `POST /api/public/import/text` (texte anonymisé) |
| Extraire des dépenses d'un texte (pour la transcription locale) | absent | `POST /api/public/expenses/extract` |
| Notifications | aucune notification serveur | table de jetons, envoi FCM / APNs planifié |
| Lien de réinitialisation du mot de passe | redirection web | App Links / Universal Links déclarés dans Supabase |
| URL de l'API, URL et clé publishable Supabase | absentes du dépôt | à fournir |

## Sécurité et RGPD à corriger avant publication
- RLS : un utilisateur peut modifier ses propres quotas (`user_quotas`) et lever sa suspension (`profiles.suspended`).
- `/transcribe` et `/parse/recurring` : ni quota ni limite de taille. `/transcribe` renvoie un objet au lieu d'une chaîne.
- Statuts 401/402/429 de la passerelle IA renvoyés tels quels ; certaines erreurs arrivent en HTML.
- Anonymisation des relevés (`anonymizeStatement`) : un IBAN écrit sans espaces (`FR7630006000011234567890189`, courant dans les CSV)
  n'est capté par aucune règle (ni IBAN, ni carte, ni numéro, faute de frontière de mot) et part en clair vers la passerelle IA.
  L'anonymiseur mobile ajoute une règle pour ce cas ; `/import/file` doit être corrigé de la même façon.
- Purge de compte : audio du bucket `voice-notes` non supprimé ; email conservé en clair dans `deleted_accounts`.
- Pas d'export des données, pas de politique de confidentialité, pas de recueil de consentement.
- Sous-traitants à déclarer : l'audio et les transcriptions partent chez OpenAI, les relevés anonymisés chez Google, via Lovable.
- Chiffrement : symétrique, clé unique pour tous, limité aux libellés et transcriptions (montants, dates, noms des charges fixes en clair).
- Dépendance `xlsx` 0.18.5 vulnérable côté serveur.
