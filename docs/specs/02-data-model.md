# nemla — Spécification 02 : modèle de données (état final) et règles métier à porter en Kotlin

> Source :
> - les 43 fichiers de `supabase/migrations/` lus dans l'ordre chronologique (du `20260818120902` au `20260823234536`) ;
> - `src/integrations/supabase/types.ts`, cohérent avec les migrations : aucune divergence constatée ;
> - les fichiers TS cités dans chaque section.
>
> Ce document décrit l'**état final** du schéma. Les fonctions redéfinies plusieurs fois (`bootstrap_user`, `ensure_month_quota`, etc.) sont décrites dans leur **dernière** version, avec la migration d'origine.
>
> Marqueurs : **[AMBIGU]**, **[ABSENT]**, **[RISQUE]**, **[ÉCART]** (écart avec la description produit).
>
> Migrations très courtes : `…145612` (DROP de `increment_voice_quota(uuid,text)`), `…085524` (ADD `profiles.devise`), `…224923` (ADD `profiles.debit_depuis`), `…212949` (DROP `user_feature_flags`). `…234238` contient `REVOKE EXECUTE ON record_category_correction(...) FROM anon`. Ces fichiers sont comptés « 0 ligne » par `wc` faute de saut de ligne final, mais ils ne sont pas vides.

---

## 1. Types énumérés (schéma `public`)

| Enum | Valeurs (ordre exact) |
|---|---|
| `category_type` | `'fixe'`, `'variable'` |
| `expense_source` | `'voice'`, `'manual'` |
| `recurrence` | `'mensuelle'`, `'annuelle'` |
| `voice_status` | `'uploaded'`, `'transcribing'`, `'extracting'`, `'ready'`, `'confirmed'`, `'error'` |
| `app_role` | `'admin'`, `'user'` |

« Pseudo-enums » en `text` (sans contrainte en base, sauf mention contraire) :

| Colonne | Valeurs utilisées par le code |
|---|---|
| `expenses.moyen_paiement` | **CHECK** `IN ('carte','virement','especes','cheque')`, défaut `'carte'` |
| `user_notifications.channel` | **CHECK** `IN ('in_app','push')` |
| `user_notifications.kind` | **CHECK** `IN ('bilan_mois','bilan_annee','rappel_jour')` |
| `profiles.budget_periode` | `'mois'` ou `'annee'` (budget-sheet) — pas de CHECK |
| `categories.budget_periode` | défaut `'mois'`. **Non utilisée par le front** : la période d'un budget de catégorie se déduit de la colonne remplie (voir §8.6) |
| `profiles.sexe` | `'femme'`, `'homme'` ou NULL |
| `profiles.situation_familiale` | `'celibataire'`, `'couple'`, `'famille'`, `'colocation'` ou NULL |
| `profiles.devise` | `EUR`, `CHF`, `GBP`, `USD`, `CAD`, `MAD`, `TND`, `DZD`, `XOF`, `AED` (liste de `format.ts`) — pas de CHECK, défaut `'EUR'` |
| `profiles.banque` | id de banque : `bnp`, `sg`, `credit-agricole`, `lcl`, `cic`, `credit-mutuel`, `caisse-epargne`, `banque-populaire`, `banque-postale`, `hsbc`, `autre`, ou NULL |
| `report_notifications.kind` | défaut `'mois'` (table inutilisée) |
| `usage_logs.action` | `'voice.process'`, `'import.file'`, `'import.file.admin'` |
| `categories.icone` | `shopping`, `utensils`, `bus`, `home`, `heart`, `gamepad`, `shirt`, `repeat`, `more`, `piggy` (défaut de colonne : `'Tag'`) |

---

## 2. Tables (état final)

Notation : `NN` = NOT NULL, `PK` = clé primaire, `FK→` = clé étrangère. Le comportement de suppression est entre parenthèses. Toutes les tables ont la **RLS activée**.

### 2.1 `profiles`
| Colonne | Type | NN | Défaut | Notes |
|---|---|---|---|---|
| `id` | uuid | NN | — | PK, FK→`auth.users` (CASCADE) |
| `monthly_budget` | numeric(12,2) | NN | `0` | |
| `onboarding_done` | boolean | NN | `false` | |
| `created_at` | timestamptz | NN | `now()` | |
| `updated_at` | timestamptz | NN | `now()` | **[RISQUE]** aucun trigger `touch_updated_at` : jamais mis à jour |
| `annual_budget` | numeric | — | NULL | |
| `pseudo` | text | — | NULL | index unique `profiles_pseudo_unique_idx` sur `lower(pseudo)` `WHERE pseudo IS NOT NULL AND btrim(pseudo) <> ''` |
| `sexe` | text | — | NULL | |
| `age` | integer | — | NULL | **[ÉCART]** âge exact, pas une « tranche d'âge » ; l'UI autorise 14 à 120 |
| `situation_familiale` | text | — | NULL | |
| `budget_periode` | text | NN | `'mois'` | |
| `daily_reminder` | boolean | NN | `true` | |
| `devise` | text | NN | `'EUR'` | |
| `banque` | text | — | NULL | |
| `debit_differe` | boolean | NN | `false` | |
| `debit_jour_arrete` | smallint | NN | **`30`** | **[AMBIGU]** le client utilise 31 par défaut (`DEFAULTS`, `BANQUES`) |
| `debit_jour_prelevement` | smallint | NN | `5` | |
| `debit_choix_fait` | boolean | NN | `false` | passe à true après la boîte de dialogue « débit immédiat/différé » |
| `debit_depuis` | date | — | NULL | NULL = la règle s'applique à toutes les dépenses |
| `suspended` | boolean | NN | `false` | |

### 2.2 `categories`
| Colonne | Type | NN | Défaut | Notes |
|---|---|---|---|---|
| `id` | uuid | NN | `gen_random_uuid()` | PK |
| `user_id` | uuid | NN | — | FK→`auth.users` (CASCADE) |
| `nom` | text | NN | — | **UNIQUE(`user_id`, `nom`)**, sensible à la casse |
| `couleur` | text | NN | `'#64748b'` | hex |
| `icone` | text | NN | `'Tag'` | |
| `type` | category_type | NN | `'variable'` | |
| `created_at` | timestamptz | NN | `now()` | |
| `is_default` | boolean | NN | `false` | les catégories par défaut ne sont pas supprimables (filtre côté client uniquement) |
| `ordre` | integer | NN | `0` | |
| `visible` | boolean | NN | `true` | |
| `budget_mensuel` | numeric | — | NULL | |
| `budget_annuel` | numeric | — | NULL | |
| `budget_periode` | text | NN | `'mois'` | inutilisée |

### 2.3 `voice_entries`
| Colonne | Type | NN | Défaut | Notes |
|---|---|---|---|---|
| `id` | uuid | NN | `gen_random_uuid()` | PK |
| `user_id` | uuid | NN | — | FK→`auth.users` (CASCADE) |
| `audio_url` | text | — | NULL | jamais renseignée par le code actuel |
| `statut` | voice_status | NN | `'uploaded'` | |
| `error_message` | text | — | NULL | |
| `created_at` | timestamptz | NN | `now()` | |
| `input_tokens` | integer | — | NULL | |
| `output_tokens` | integer | — | NULL | |
| `estimated_cost_eur` | numeric(10,6) | — | NULL | |
| `transcript_enc` | bytea | — | NULL | **chiffré** (la colonne `transcript` en clair a été supprimée) |

### 2.4 `expenses`
| Colonne | Type | NN | Défaut | Notes |
|---|---|---|---|---|
| `id` | uuid | NN | `gen_random_uuid()` | PK |
| `user_id` | uuid | NN | — | FK→`auth.users` (CASCADE) |
| `category_id` | uuid | — | NULL | FK→`categories` (SET NULL) |
| `voice_entry_id` | uuid | — | NULL | FK→`voice_entries` (SET NULL) |
| `montant` | numeric(12,2) | NN | — | **en clair** ; aucun CHECK de positivité |
| `date` | date | NN | `CURRENT_DATE` | date d'achat |
| `source` | expense_source | NN | `'manual'` | les imports de relevé sont aussi `'manual'` |
| `created_at` | timestamptz | NN | `now()` | |
| `confirmed` | boolean | NN | `true` | `false` = en attente de validation (dictée ou import) |
| `recurring_expense_id` | uuid | — | NULL | FK→`recurring_expenses` (SET NULL) |
| `moyen_paiement` | text | NN | `'carte'` | CHECK (voir §1) |
| `date_prelevement` | date | NN | `CURRENT_DATE` | calculée par le trigger `expenses_date_prelevement` |
| `description_enc` | bytea | — | NULL | **chiffré** |
| `raw_transcript_enc` | bytea | — | NULL | **chiffré** |

Index : `expenses_user_date_idx (user_id, date DESC)` ; `expenses_recurring_expense_idx (user_id, recurring_expense_id) WHERE recurring_expense_id IS NOT NULL` ; `expenses_user_date_prelevement_idx (user_id, date_prelevement)`.

### 2.5 `recurring_expenses`
| Colonne | Type | NN | Défaut | Notes |
|---|---|---|---|---|
| `id` | uuid | NN | `gen_random_uuid()` | PK |
| `user_id` | uuid | NN | — | FK→`auth.users` (CASCADE) |
| `category_id` | uuid | — | NULL | FK→`categories` (SET NULL) |
| `nom` | text | NN | — | **en clair** **[RISQUE]** (incohérent avec le chiffrement des libellés) ; pas d'unicité (le client dédoublonne par `ilike`) |
| `montant` | numeric(12,2) | NN | — | |
| `frequence` | recurrence | NN | `'mensuelle'` | |
| `jour_prelevement` | smallint | NN | `1` | le client borne entre 1 et 28 ; aucun CHECK en base |
| `created_at` | timestamptz | NN | `now()` | **sert de référence** : mois de départ et mois anniversaire des charges annuelles (voir §6) |

### 2.6 `recurring_skips`
| Colonne | Type | NN | Défaut | Notes |
|---|---|---|---|---|
| `id` | uuid | NN | `gen_random_uuid()` | PK |
| `user_id` | uuid | NN | — | FK→`auth.users` (CASCADE) |
| `recurring_expense_id` | uuid | NN | — | FK→`recurring_expenses` (CASCADE) |
| `mois` | text | NN | — | `'YYYY-MM'` |
| `created_at` | timestamptz | NN | `now()` | |
| UNIQUE | (`user_id`, `recurring_expense_id`, `mois`) | | | |

### 2.7 `year_catchup` (inutilisée par le front, sauf la purge)
`id` uuid PK `gen_random_uuid()` ; `user_id` uuid NN (**pas de FK**) ; `year` integer NN ; `month` integer NN `CHECK (month BETWEEN 1 AND 12)` ; `montant` numeric(12,2) NN défaut 0 ; `created_at`, `updated_at` timestamptz NN `now()` ; UNIQUE(`user_id`,`year`,`month`). Trigger `update_year_catchup_updated_at`.

### 2.8 `user_quotas`
| Colonne | Type | NN | Défaut | Notes |
|---|---|---|---|---|
| `id` | uuid | NN | `gen_random_uuid()` | PK |
| `user_id` | uuid | NN | — | FK→`auth.users` (CASCADE) |
| `month` | text | NN | — | `'YYYY-MM'` (calculé en UTC par la base) |
| `voice_count` | integer | NN | `0` | |
| `voice_limit` | integer | NN | **`10`** (défaut de colonne) | `ensure_month_quota` crée la ligne avec le nombre de jours du mois |
| `import_count` | integer | NN | `0` | |
| `import_limit` | integer | NN | `1` | |
| UNIQUE | (`user_id`, `month`) | | | |

### 2.9 `usage_logs`
`id` uuid PK ; `user_id` uuid NN FK→`auth.users` (CASCADE) ; `action` text NN ; `model` text ; `input_tokens` int ; `output_tokens` int ; `estimated_cost_eur` numeric(10,6) ; `metadata` jsonb ; `created_at` timestamptz NN `now()`.

### 2.10 `deleted_accounts`
`id` uuid PK (**pas de FK** : l'ancien `auth.users.id`) ; `email` text (**en clair**) ; `deleted_at` timestamptz NN `now()`.

### 2.11 `merchant_rules` (règles personnelles)
`id` uuid PK ; `user_id` uuid NN FK→`auth.users` (CASCADE) ; `merchant_key` text NN ; `category_id` uuid FK→`categories` (**CASCADE**) ; `confirmations` int NN défaut 1 ; `created_at`, `updated_at` timestamptz NN `now()` ; **UNIQUE(`user_id`,`merchant_key`)**. Trigger `merchant_rules_touch`.

### 2.12 `category_corrections` (journal)
`id` uuid PK ; `user_id` uuid NN FK→`auth.users` (CASCADE) ; `expense_id` uuid (**pas de FK**) ; `label` text NN défaut `''` (toujours `''` depuis la migration `…120447`) ; `merchant_key` text NN ; `suggested_category` text ; `chosen_category` text ; `was_correction` boolean NN défaut true ; `created_at` timestamptz NN. Index `(user_id, created_at DESC)`.

### 2.13 `merchant_votes` (votes anonymes pour le corpus)
`id` uuid PK ; `user_id` uuid NN FK→`auth.users` (CASCADE) ; `merchant_key` text NN ; `category_nom` text NN ; `created_at` timestamptz NN ; UNIQUE(`user_id`,`merchant_key`).

### 2.14 `merchant_corpus` (dictionnaire mutualisé)
`merchant_key` text **PK** ; `category_nom` text NN ; `votes` int NN défaut 0 ; `updated_at` timestamptz NN `now()`.

### 2.15 `classification_logs`
`id` uuid PK ; `user_id` uuid NN FK→`auth.users` (CASCADE) ; `expense_id` uuid FK→`expenses` (SET NULL) ; `voice_entry_id` uuid FK→`voice_entries` (SET NULL) ; `category_suggested_by_ai` text ; `category_final` text ; `amount_suggested_by_ai` numeric ; `amount_final` numeric ; `was_corrected` boolean NN défaut false ; `created_at` timestamptz NN ; `raw_transcript_enc` bytea (**chiffré**). Index `(user_id, created_at DESC)` et `(expense_id)`.

### 2.16 `report_views`
`id` uuid PK ; `user_id` uuid NN FK→`auth.users` (CASCADE) ; `mois` text NN (contient `'YYYY-MM'` **ou** `'YYYY'` pour les bilans annuels) ; `created_at` timestamptz NN ; UNIQUE(`user_id`,`mois`).

### 2.17 `user_roles`
`id` uuid PK ; `user_id` uuid NN FK→`auth.users` (CASCADE) ; `role` app_role NN ; `created_at` timestamptz NN ; UNIQUE(`user_id`,`role`).

### 2.18 `report_notifications` (**inutilisée par le code**)
`id` uuid PK ; `user_id` uuid NN FK (CASCADE) ; `kind` text NN défaut `'mois'` ; `periode` text NN ; `payload` jsonb ; `is_demo` boolean NN défaut false ; `seen_at` timestamptz ; `created_at`, `updated_at` timestamptz NN. Index unique `report_notifications_unique (user_id, kind, periode)`. Trigger `report_notifications_touch`.

### 2.19 `user_notifications`
`id` uuid PK ; `user_id` uuid NN FK (CASCADE) ; `channel` text NN défaut `'in_app'` CHECK ; `kind` text NN défaut `'rappel_jour'` CHECK ; `title` text NN ; `body` text NN ; `periode` text ; `payload` jsonb (format `MonthlyReport`, voir doc 01 §2.6, plus un champ `demo:true` pour les démonstrations) ; `is_demo` boolean NN défaut false ; `delivered_at` timestamptz ; `seen_at` timestamptz ; `created_at`, `updated_at` timestamptz NN. Index `(user_id, created_at DESC)`. Trigger `user_notifications_touch`.

### 2.20 `admin_audit_logs`
`id` uuid PK ; `admin_user_id` uuid NN (pas de FK) ; `target_user_id` uuid (pas de FK) ; `action` text NN (`user.suspend`, `user.unsuspend`, `expense.delete`, `user.delete`) ; `details` jsonb NN défaut `'{}'` ; `created_at` timestamptz NN.

### 2.21 Table supprimée
`user_feature_flags` : créée dans `…141757`, **supprimée** dans `…212949`.

### 2.22 Storage
- Bucket `voice-notes` : **il n'est créé par aucune migration** **[ABSENT]**. Il a probablement été créé via la console. Son caractère public ou privé, sa taille max et ses types MIME sont inconnus.
- Politiques `storage.objects` (rôle `authenticated`), toutes conditionnées par `bucket_id='voice-notes' AND (storage.foldername(name))[1] = auth.uid()::text` :
  - `voice notes read own` (SELECT) ;
  - `voice notes insert own` (INSERT, WITH CHECK) ;
  - `voice notes delete own` (DELETE).
- Pas de politique UPDATE.

### 2.23 Vues
**Aucune vue SQL.** La lecture déchiffrée passe par la fonction table `expenses_view(p_key)`.

---

## 3. Droits (GRANT) et politiques RLS par table

| Table | GRANT `authenticated` | Politiques (toutes `TO authenticated`) |
|---|---|---|
| `profiles` | S, I, U, D | `own profile` FOR ALL USING/CHECK `auth.uid() = id` |
| `categories` | S, I, U, D | `own categories` FOR ALL `auth.uid() = user_id` (le doublon « Users can manage their own categories » a été supprimé) |
| `voice_entries` | S, I, U, D | `own voice entries read` SELECT `uid=user_id` ; `insert` CHECK `uid=user_id AND NOT is_suspended(uid)` ; `update` USING+CHECK idem ; `delete` USING `uid=user_id` |
| `expenses` | S, I, U, D | `own expenses read` / `insert` (non suspendu) / `update` (non suspendu) / `delete` (sans contrôle de suspension) |
| `recurring_expenses` | S, I, U, D | `own recurring read` / `insert` (non suspendu) / `update` (non suspendu) / `delete` |
| `recurring_skips` | S, I, D | `Users manage their own recurring skips` FOR ALL `user_id = auth.uid()` |
| `year_catchup` | S, I, U, D | `Users manage their own catchup` FOR ALL |
| `user_quotas` | S, I, U | `Users can manage their own quotas` FOR ALL `auth.uid()=user_id` — **[RISQUE CRITIQUE]** l'utilisateur peut modifier ses compteurs et ses limites |
| `usage_logs` | S, I | `view own` SELECT ; `insert own` INSERT CHECK — **[RISQUE]** journaux falsifiables |
| `deleted_accounts` | aucun | `No user access to deleted_accounts` FOR ALL USING false / CHECK false |
| `merchant_rules` | S, I, U, D | `own merchant rules` FOR ALL |
| `category_corrections` | S, I | `own corrections read` SELECT ; `own corrections insert` INSERT |
| `merchant_votes` | aucun | `no direct access to merchant votes` FOR ALL false/false |
| `merchant_corpus` | S | `corpus readable by authenticated` SELECT USING true (lisible par tous les utilisateurs) |
| `classification_logs` | S, I | `own classification logs read` / `insert` |
| `report_views` | S, I, D | `own report views` FOR ALL |
| `user_roles` | S | `read own roles` SELECT `uid=user_id` ; `admins read all roles` SELECT `has_role(uid,'admin')` |
| `report_notifications` | S, U | `read own report notifications` SELECT ; `mark own report notifications as seen` UPDATE (toutes colonnes) |
| `user_notifications` | S, U | `read own notifications` SELECT ; `update own notifications` UPDATE (toutes colonnes) |
| `admin_audit_logs` | S | `admins read audit logs` SELECT `has_role(uid,'admin')` |

- `service_role` a `ALL` sur toutes les tables et contourne la RLS.
- **[AMBIGU]** Les privilèges par défaut du rôle `anon` relèvent de la configuration Supabase et ne sont pas visibles ici. Aucune politique ne cible `anon`, donc `anon` ne voit aucune ligne.
- **[RISQUE CRITIQUE]** `profiles` en FOR ALL permet à l'utilisateur de faire `UPDATE suspended=false` sur sa propre ligne, ce qui neutralise la suspension.

---

## 4. Colonnes chiffrées

| Table.colonne | Écrite par | Lue par |
|---|---|---|
| `expenses.description_enc` | `expense_save`, `expenses_rename`, `materialize_recurring_expenses` | `expenses_view`, `expenses_rename` (comparaison) |
| `expenses.raw_transcript_enc` | `expense_save` (à l'insertion seulement) | **personne** (jamais relue) |
| `voice_entries.transcript_enc` | `voice_entry_save` | `voice_entry_transcript` |
| `classification_logs.raw_transcript_enc` | `classification_log_save` | personne (journal interne) |

- Algorithme : `extensions.pgp_sym_encrypt(text, key)` / `pgp_sym_decrypt`.
- La clé `APP_ENCRYPTION_KEY` (au moins 16 caractères) est passée en argument depuis le serveur applicatif.
- Détail du fonctionnement : doc 01 §3.4.

---

## 5. Fonctions SQL / RPC (version finale)

Colonnes du tableau :
- **Sécu.** : `DEFINER` ou `INVOKER` (par défaut INVOKER, donc RLS appliquée).
- **EXEC** : rôles autorisés à exécuter. « PUBLIC (défaut) » signifie qu'aucun REVOKE n'a été fait : tout rôle, y compris `anon`, peut l'appeler.

Toutes ont `SET search_path TO 'public'` (plus `'extensions'` pour `app_*`).

| Fonction (signature exacte) | Retour | Sécu. | Volatilité | EXEC | Migration finale |
|---|---|---|---|---|---|
| `bootstrap_user()` | void | DEFINER | volatile | authenticated, service_role | `…192932` |
| `days_in_current_month()` | integer | INVOKER | STABLE (sql) | authenticated, service_role | `…234225` |
| `ensure_month_quota()` | `user_quotas` (ligne) | DEFINER | volatile | authenticated, service_role | `…223804` |
| `increment_import_quota()` | void | DEFINER | volatile | authenticated, service_role | `…235530` |
| `increment_voice_quota()` | trigger | DEFINER | — | aucun (trigger seulement) | `…235530` |
| `touch_updated_at()` | trigger | INVOKER | — | PUBLIC (défaut) | `…173016` |
| `skip_recurring_occurrence()` | trigger | DEFINER | — | révoqué pour anon et authenticated | `…134701` |
| `expenses_set_date_prelevement()` | trigger | DEFINER | — | révoqué | `…233343` |
| `debit_prelevement_date(p_date date, p_arrete integer, p_prelevement integer)` | date | INVOKER | IMMUTABLE | PUBLIC (défaut) | `…233233` |
| `apply_debit_rule()` | integer | INVOKER | volatile | PUBLIC (défaut) | `…233233` |
| `materialize_recurring_expenses(p_key text, p_year integer DEFAULT NULL)` | integer | INVOKER | volatile | **PUBLIC (défaut)** (aucun GRANT ni REVOKE après recréation) | `…234536` |
| `record_category_correction(p_merchant_key text, p_label text, p_category_id uuid, p_suggested_category text, p_chosen_category text, p_was_correction boolean DEFAULT true, p_expense_id uuid DEFAULT NULL)` | void | DEFINER | volatile | authenticated | `…120447` |
| `has_role(_user_id uuid, _role app_role)` | boolean | DEFINER | STABLE (sql) | authenticated, service_role | `…000841` |
| `suggest_pseudo(p_pseudo text)` | jsonb | DEFINER | STABLE (utilise `random()`) | authenticated | `…083343` |
| `is_suspended(_user_id uuid)` | boolean | DEFINER | STABLE (sql) | authenticated, service_role | `…115413` |
| `app_encrypt(p_text text, p_key text)` | bytea | INVOKER | IMMUTABLE (sql) | authenticated, service_role | `…115413` |
| `app_decrypt(p_data bytea, p_key text)` | text | INVOKER | IMMUTABLE (plpgsql) | authenticated, service_role | `…115413` |
| `expenses_view(p_key text)` | TABLE(id uuid, user_id uuid, category_id uuid, voice_entry_id uuid, recurring_expense_id uuid, montant numeric, description text, date date, date_prelevement date, source expense_source, moyen_paiement text, confirmed boolean, created_at timestamptz) | INVOKER | STABLE (sql) | authenticated, service_role | `…115601` |
| `expense_save(p_key text, p_montant numeric, p_description text, p_date date, p_category_id uuid DEFAULT NULL, p_id uuid DEFAULT NULL, p_confirmed boolean DEFAULT true, p_moyen_paiement text DEFAULT 'carte', p_source expense_source DEFAULT 'manual', p_voice_entry_id uuid DEFAULT NULL, p_raw_transcript text DEFAULT NULL, p_user_id uuid DEFAULT NULL)` | uuid | INVOKER | volatile | authenticated, service_role | `…115601` |
| `expenses_rename(p_key text, p_old text, p_new text)` | integer | INVOKER | volatile | authenticated, service_role | `…115601` |
| `voice_entry_save(p_key text, p_transcript text DEFAULT NULL, p_statut voice_status DEFAULT 'ready', p_id uuid DEFAULT NULL, p_input_tokens integer DEFAULT NULL, p_output_tokens integer DEFAULT NULL, p_estimated_cost_eur numeric DEFAULT NULL, p_error_message text DEFAULT NULL, p_user_id uuid DEFAULT NULL)` | uuid | INVOKER | volatile | authenticated, service_role | `…115601` |
| `voice_entry_transcript(p_key text, p_id uuid)` | text | INVOKER | STABLE (sql) | authenticated, service_role | `…115601` |
| `classification_log_save(p_key text, p_expense_id uuid, p_voice_entry_id uuid, p_raw_transcript text, p_category_suggested_by_ai text, p_category_final text, p_amount_suggested_by_ai numeric, p_amount_final numeric, p_was_corrected boolean, p_user_id uuid DEFAULT NULL)` | void | INVOKER | volatile | authenticated, service_role | `…115601` |

Fonctions supprimées : `increment_voice_quota(uuid, text)` (dans `…145612`), `materialize_recurring_expenses(integer)` (dans `…234536`), `encrypt_existing_rows(text)` (dans `…120447`).

### 5.1 Comportement détaillé

**`bootstrap_user()`**
- `uid := auth.uid()`. **[RISQUE]** La version finale ne vérifie pas `uid IS NULL`. Comme `anon` n'a pas le droit EXEC, c'est sans effet en pratique.
- Si l'utilisateur a déjà au moins une catégorie, la fonction insère seulement les catégories manquantes parmi les suivantes (comparaison `lower(nom)`), avec l'icône `'more'` et `is_default=true`, puis s'arrête :

  | nom | couleur | type | ordre |
  |---|---|---|---|
  | Énergie | #F4A259 | variable | 11 |
  | Assurances | #6B2737 | fixe | 12 |
  | Impôts | #9E2B25 | variable | 13 |

- Sinon :
  - `INSERT profiles(id, monthly_budget=0, onboarding_done=false) ON CONFLICT DO NOTHING` ;
  - insertion des **13 catégories par défaut** (`is_default=true`) :

  | ordre | nom | couleur | icone | type |
  |---|---|---|---|---|
  | 1 | Courses | #E4572E | shopping | variable |
  | 2 | Restaurants | #F2B701 | utensils | variable |
  | 3 | Transport | #2E86AB | bus | variable |
  | 4 | Logement | #17395E | home | fixe |
  | 5 | Énergie | #F4A259 | more | variable |
  | 6 | Assurances | #6B2737 | more | fixe |
  | 7 | Santé | #D01C4F | heart | variable |
  | 8 | Loisirs | #00A5A5 | gamepad | variable |
  | 9 | Shopping | #B5179E | shirt | variable |
  | 10 | Abonnements | #8C5E3C | repeat | fixe |
  | 11 | Divers | #7A7F87 | more | variable |
  | 12 | Épargne & investissement | #4361EE | piggy | variable |
  | 13 | Impôts | #9E2B25 | more | variable |

  **[ÉCART]** La description produit mentionne « Alimentation/Courses ». Le nom réel est « Courses ». Des comptes anciens peuvent encore avoir « Autre » (couleur #7A7F87) au lieu de « Divers ».

**`ensure_month_quota()`**
- Si `uid` est NULL : exception `'Non authentifié'`.
- `m := to_char(now(),'YYYY-MM')` et `days := days_in_current_month()`.
- `INSERT (uid, m, voice_count 0, voice_limit days, import_count 0, import_limit 1) ON CONFLICT DO NOTHING`.
- Puis `UPDATE voice_limit = days WHERE voice_limit = 10 AND days <> 10`.
- Renvoie la ligne : `{id, user_id, month, voice_count, voice_limit, import_count, import_limit}`.

**`days_in_current_month()`** : `EXTRACT(day FROM date_trunc('month', now()) + interval '1 month - 1 day')`.

**`increment_voice_quota()`** (trigger AFTER INSERT sur `voice_entries`) : `INSERT (NEW.user_id, mois courant, voice_count 1, voice_limit 10) ON CONFLICT (user_id, month) DO UPDATE voice_count = voice_count + 1`.

**`increment_import_quota()`**
- Si `uid` est NULL : exception.
- `INSERT (uid, m, 0, 10, 1, 1) ON CONFLICT DO UPDATE import_count = import_count + 1`.

**`record_category_correction(...)`**
1. Si `auth.uid()` est NULL : exception `'Non authentifié'`. Si `p_merchant_key` est NULL ou que `length(trim(key)) < 3` : RETURN.
2. `INSERT category_corrections (user_id, expense_id, label = '', merchant_key, suggested_category, chosen_category, was_correction = coalesce(p,true))`. `p_label` est **ignoré**.
3. Si `p_category_id` ou `p_chosen_category` est NULL : RETURN. Si la catégorie n'appartient pas à l'utilisateur : RETURN.
4. Upsert dans `merchant_rules (user_id, merchant_key)` : `category_id = nouvelle` ; `confirmations = +1` si c'est la même catégorie, sinon `1` ; `updated_at = now()`.
5. Upsert dans `merchant_votes (user_id, merchant_key)` avec `category_nom = p_chosen_category`.
6. Choix de la catégorie majoritaire : `category_nom` ayant le plus de `count(DISTINCT user_id)` pour cette clé (`ORDER BY count DESC LIMIT 1`, **départage non déterministe** en cas d'égalité).
7. Si `v_top_votes >= 3` (constante `v_threshold`) : upsert dans `merchant_corpus (merchant_key, category_nom, votes, updated_at)`.
- **[AMBIGU]** Le corpus est indexé par **nom** de catégorie. Il ne sert donc qu'aux utilisateurs qui ont une catégorie portant exactement ce nom (comparaison insensible à la casse).

**`has_role(_user_id, _role)`** : `EXISTS (SELECT 1 FROM user_roles WHERE user_id=_user_id AND role=_role)`.

**`is_suspended(_user_id)`** : `COALESCE((SELECT suspended FROM profiles WHERE id=_user_id), false)`.

**`suggest_pseudo(p_pseudo)`**
- `base := btrim(coalesce(p,''))`.
- Si `base` est vide : `{"available":false,"suggestions":[]}`.
- Si le pseudo est libre (`lower(pseudo)=lower(base)` pour un autre `id` que `auth.uid()`) : `{"available":true,"suggestions":[]}`.
- Sinon, candidats `base||2`, `base||3`, … jusqu'à obtenir 3 suggestions libres, en s'arrêtant quand `i > 50`. On ajoute ensuite `base || (100..999 aléatoire)` s'il est libre. Résultat : `{"available":false,"suggestions":[…]}`.
- **[RISQUE]** mineur : permet d'énumérer les pseudos existants.

**`debit_prelevement_date(p_date, p_arrete, p_prelevement)`**
- Si `p_date` est NULL : NULL.
- `cut := LEAST(GREATEST(COALESCE(p_arrete,31),1), dernierJour(mois de p_date))`.
- `target := 1er du mois de p_date + 1 mois`. Si `jour(p_date) > cut` : `target += 1 mois`.
- Renvoie `1er(target) + (LEAST(GREATEST(COALESCE(p_prelevement,5),1), dernierJour(target)) − 1)`.
- C'est la même règle que `prelevementDate()` en TS (voir §8.1).

**`apply_debit_rule()`**
- Si `uid` est NULL : exception.
- Lit `debit_differe, debit_jour_arrete, debit_jour_prelevement, debit_depuis` du profil. Sans profil : 0.
- `UPDATE expenses SET date_prelevement = CASE WHEN debit_differe AND moyen_paiement='carte' AND (debit_depuis IS NULL OR date >= debit_depuis) THEN debit_prelevement_date(date, arrete, prelevement) ELSE date END`, uniquement sur les lignes de l'utilisateur dont la valeur change (`IS DISTINCT FROM`).
- Renvoie `ROW_COUNT`.

**`expenses_view(p_key)`** : `SELECT … COALESCE(app_decrypt(description_enc, p_key), '') AS description … FROM expenses`, sans filtre, sans pagination et sans ordre. La RLS limite le résultat aux lignes de l'utilisateur. **[RISQUE]** Performance : tout l'historique est déchiffré à chaque appel.

**`expense_save(...)`**
- `uid := COALESCE(p_user_id, auth.uid())`. Si NULL : exception `'Non authentifié'`. (En INVOKER, la RLS empêche un utilisateur d'utiliser un autre `p_user_id`.)
- Si `p_id` est fourni : `UPDATE` de `montant`, `description_enc = app_encrypt(p_description)`, `date`, `category_id = p_category_id` (**NULL efface la catégorie**), `confirmed`, `moyen_paiement`, `WHERE id = p_id AND user_id = uid`. Renvoie l'id, ou **NULL si aucune ligne** (pas d'erreur).
- Sinon : `INSERT (user_id, montant, description_enc, date, category_id, confirmed, moyen_paiement, source, voice_entry_id, raw_transcript_enc)`. Renvoie le nouvel id.
- `date_prelevement` est recalculée par le trigger.

**`expenses_rename(p_key, p_old, p_new)`**
- Si `uid` est NULL ou que `p_old` est vide : 0.
- `UPDATE description_enc = app_encrypt(p_new) WHERE user_id = uid AND lower(btrim(app_decrypt(description_enc))) = lower(btrim(p_old))`.
- Renvoie le nombre de lignes modifiées.

**`voice_entry_save(...)`**
- `uid` est résolu comme dans `expense_save`.
- Si `p_id` est fourni : `UPDATE` avec `transcript_enc = COALESCE(app_encrypt(p_transcript), transcript_enc)`, `statut = p_statut`, `error_message = p_error_message` (**écrasé, y compris par NULL**), et les tokens et le coût en `COALESCE`.
- Sinon : `INSERT`, ce qui déclenche l'incrément de quota.
- Renvoie l'id.

**`voice_entry_transcript(p_key, p_id)`** : `SELECT app_decrypt(transcript_enc, p_key) FROM voice_entries WHERE id = p_id`, avec RLS.

**`classification_log_save(...)`** : si `uid` est NULL, RETURN silencieux. Sinon `INSERT` dans `classification_logs`, avec `was_corrected = COALESCE(p, false)`.

**`materialize_recurring_expenses(p_key, p_year)`** : voir §6.

---

## 6. Triggers (état final)

| Trigger | Table | Moment | Fonction | Effet |
|---|---|---|---|---|
| `trg_increment_voice_quota` | `voice_entries` | AFTER INSERT, FOR EACH ROW | `increment_voice_quota()` | +1 sur `voice_count` du mois courant |
| `expenses_skip_recurring` | `expenses` | AFTER DELETE, FOR EACH ROW | `skip_recurring_occurrence()` | si `OLD.recurring_expense_id` n'est pas NULL **et** que l'utilisateur et la charge existent encore : `INSERT recurring_skips(user_id, recurring_expense_id, to_char(OLD.date,'YYYY-MM')) ON CONFLICT DO NOTHING`. Supprimer une occurrence générée empêche donc sa régénération. |
| `expenses_date_prelevement` | `expenses` | BEFORE INSERT OR UPDATE OF `date`, `moyen_paiement` | `expenses_set_date_prelevement()` | `NEW.date_prelevement := debit_prelevement_date(NEW.date, arrete, prelevement)` si `debit_differe` **et** `moyen_paiement='carte'` **et** (`debit_depuis` est NULL ou `NEW.date >= debit_depuis`) ; sinon `NEW.date` |
| `update_year_catchup_updated_at` | `year_catchup` | BEFORE UPDATE | `touch_updated_at()` | `updated_at = now()` |
| `merchant_rules_touch` | `merchant_rules` | BEFORE UPDATE | `touch_updated_at()` | idem |
| `report_notifications_touch` | `report_notifications` | BEFORE UPDATE | `touch_updated_at()` | idem |
| `user_notifications_touch` | `user_notifications` | BEFORE UPDATE | `touch_updated_at()` | idem |

### Matérialisation des charges fixes — `materialize_recurring_expenses(p_key, p_year)` (formule exacte)

```
uid := auth.uid(); si NULL → RETURN 0
y := COALESCE(p_year, année(current_date))
pour chaque r dans recurring_expenses où user_id = uid :
  pour m de 1 à 12 :
    SORTIR de la boucle des mois si make_date(y, m, 1) > date_trunc('month', current_date)
      (on ne génère jamais un mois futur, seulement les mois écoulés et le mois en cours)
    si r.frequence = 'annuelle' et m ≠ mois(r.created_at) → mois suivant
    last_day := dernier jour du mois (y, m)
    d := make_date(y, m, LEAST(GREATEST(COALESCE(r.jour_prelevement,1),1), last_day))
    si d < date_trunc('month', r.created_at) → mois suivant
      (rien n'est généré avant le mois de création de la charge)
    si un recurring_skips (uid, r.id, 'YYYY-MM' de d) existe → mois suivant
    si une dépense (uid, recurring_expense_id = r.id, même 'YYYY-MM' que d) existe → mois suivant
    INSERT expenses(user_id=uid, montant=r.montant, description_enc=app_encrypt(r.nom,p_key),
                    date=d, source='manual', category_id=r.category_id, confirmed=true,
                    recurring_expense_id=r.id)
    created += 1
RETURN created
```

Conséquences à reproduire :
- Pour le mois en cours, l'occurrence est générée **même si le jour de prélèvement n'est pas encore passé** (par exemple le loyer du 28 est généré dès le 1er).
- `moyen_paiement` prend la valeur par défaut `'carte'`. En débit différé, la `date_prelevement` d'une charge fixe générée est donc **décalée**. **[AMBIGU]** Ce n'est probablement pas voulu pour un prélèvement SEPA.
- Changer le montant d'une charge ne modifie pas les occurrences déjà générées.
- `current_date` et `created_at` sont évalués dans le fuseau de la base (UTC).
- Le client web appelle cette fonction (via la SF) pour **l'année affichée**, à chaque chargement du dashboard (`staleTime` de 5 min). Côté mobile, il faudra appeler le même traitement via un nouvel endpoint (doc 01 §3.3).

---

## 7. Index, contraintes et clés d'unicité (récapitulatif)

- `categories` : UNIQUE(`user_id`,`nom`).
- `user_quotas` : UNIQUE(`user_id`,`month`).
- `recurring_skips` : UNIQUE(`user_id`,`recurring_expense_id`,`mois`).
- `year_catchup` : UNIQUE(`user_id`,`year`,`month`) et CHECK `month` entre 1 et 12.
- `merchant_rules` : UNIQUE(`user_id`,`merchant_key`).
- `merchant_votes` : UNIQUE(`user_id`,`merchant_key`).
- `merchant_corpus` : PK `merchant_key`.
- `report_views` : UNIQUE(`user_id`,`mois`).
- `user_roles` : UNIQUE(`user_id`,`role`).
- `report_notifications` : UNIQUE INDEX(`user_id`,`kind`,`periode`).
- `profiles` : UNIQUE INDEX partiel `lower(pseudo)`.
- `expenses` : CHECK `moyen_paiement`.
- `user_notifications` : CHECK `channel` et `kind`.
- Index de performance : voir §2.4, §2.12, §2.15 et §2.19.

---

## 8. Règles métier calculées côté client web, à porter en Kotlin

> Convention de dates du web : la plupart des calculs d'affichage utilisent l'**heure locale** (`new Date()`, `getFullYear()`, `getMonth()`). Plusieurs autres utilisent l'**UTC** (`toISOString()`) :
> - le rappel quotidien ;
> - la fenêtre de 2 mois pour les doublons ;
> - `currentMonth()` du dashboard (inutilisée) ;
> - les endpoints serveur.
>
> **[RISQUE]** Ce mélange crée des décalages autour de minuit. Recommandation mobile : `kotlinx-datetime` avec `TimeZone.currentSystemDefault()` pour l'UI, et des dates `LocalDate` envoyées explicitement au serveur.

### 8.1 Débit différé (`src/lib/debit.ts`)

Moyens de paiement : `carte` « Carte bancaire », `virement` « Virement / prélèvement », `especes` « Espèces », `cheque` « Chèque ». **Seule `carte` est soumise au différé.**

Banques (`BANQUES`, `id` / nom / `arrete` / `prelevement` / `differe`) :

| id | nom | arrêté | prélèvement |
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

Toutes les banques ont `differe: true`. Choisir une banque préremplit l'arrêté et le prélèvement, que l'utilisateur peut modifier (UI : arrêté `min=1 max=31`, prélèvement `min=1`).

`prelevementDate(dateISO, {differe, arrete, prelevement, depuis})` :
```
si !differe → dateISO
si depuis non vide et dateISO < depuis (comparaison de chaînes ISO) → dateISO
(y, m0, d) = date (m0 indexé à partir de 0) ; si une partie est invalide → dateISO
cut = min(arrete, joursDansMois(y, m0))
si d > cut → m0 += 1
m0 += 1            // le total de l'arrêté est prélevé le mois suivant
normaliser m0 > 11 (y += 1)
jour = min(max(1, prelevement), joursDansMois(y, m0))
→ ISO(y, m0, jour)
```
Exemples :
- arrêté 31, prélèvement 5 : achat du 10/01 prélevé le 05/02 ; achat du 31/01 prélevé le 05/02.
- arrêté 25, prélèvement 1 : achat du 26/01 prélevé le 01/03.

Cette règle est **identique** à la fonction SQL `debit_prelevement_date`. **La base fait foi** (trigger). Le client ne l'utilise que pour l'aperçu (« prélevé le … »).

Portée (`debit_depuis`) dans l'UI, trois choix :
- `all` : `debit_depuis = null` ;
- `now` : aujourd'hui (`todayISO()` local) ;
- `date` : une date choisie.

Après l'enregistrement, `apply_debit_rule()` est appelée. La date d'achat n'est jamais modifiée.

Boîte de dialogue `PaymentModeGate` : elle s'ouvre une fois, quand l'utilisateur a au moins une dépense du jour ou en attente **et** que `debit_choix_fait = false`.

Défauts côté client quand le profil est absent : `differe=false`, `arrete=31`, `prelevement=5`, `depuis=null`.

`longDate(iso)` : « 5 octobre 2026 » (`day numeric, month long, year numeric`), locale `en-GB` ou `fr-FR` selon la langue de l'UI.

### 8.2 Formatage (`src/lib/format.ts`)

**Devises** (`CURRENCIES`, dans cet ordre) :

| code | pays | symbole | locale |
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

- `setCurrency(code)` : un code inconnu donne `EUR`. La devise est mémorisée dans `localStorage["voxbudget.currency"]` et dans `profiles.devise`.
- **Aucune conversion** : la devise ne sert qu'à l'affichage.

**Fonctions** :
- `euro(v)` : `Intl.NumberFormat(locale, {style:"currency", currency:code, maximumFractionDigits:2})`. **[ALERTE iso]** Le rendu exact (espaces insécables, position du symbole, symbole de certaines devises en `fr-MA`, `fr-DZ`, `fr-SN`) dépend des données ICU ; Android (ICU), iOS (CLDR via Foundation) et les navigateurs peuvent différer. Il faut des tests visuels.
- `currencySymbol()` : le symbole de la table ci-dessus.
- `dateLocale()` : `"en-GB"` si la langue de l'UI est `en`, sinon `"fr-FR"`.
- `shortDate(iso)` : `{day:"2-digit", month:"short"}` sur `new Date(iso+"T00:00:00")` en heure locale (par exemple « 05 oct. »).
- `monthLabel(y, m0)` : `{month:"long", year:"numeric"}` (par exemple « octobre 2026 »).
- `todayISO()` : `YYYY-MM-DD` en heure locale. C'est la borne maximale des sélecteurs de date : **on ne peut pas saisir de date future**.
- `capitalizeFirst(s)` : trim, puis première lettre en majuscule (le reste est inchangé).
- `compactAmount(v)` (axes du graphique) :
  - si `v` n'est pas fini ou vaut 0 : `"0 " + symbole` ;
  - si `|v| ≥ 1000` : `k = v/1000`. Le texte vaut `round(k)` si `k ≥ 10`, sinon `k.toFixed(1)` avec la virgule comme séparateur et `",0"` supprimé. On ajoute ensuite `" k" + symbole` (par exemple « 1,2 k€ », « 12 k€ ») ;
  - sinon : `round(v) + " " + symbole`.
- `prettyLabel(s)` (libellés de relevés) :
  1. `[_*]+` remplacé par une espace ;
  2. suppression des mots `\b(?:CB|CARTE|PAIEMENT|PAIMT|PRLV|PRELEVEMENT|PRÉLÈVEMENT|VIR|VIREMENT|ACHAT|FACTURE\s*CARTE)\b` (`gi`) ;
  3. suppression de `\b(?:REF|RÉF|N°|NO|MANDAT|ID)\s*[:.]?\s*[A-Z0-9-]{3,}\b` (`gi`) ;
  4. suppression des dates `\b\d{2}[/.-]\d{2}(?:[/.-]\d{2,4})?\b` ;
  5. réduction des espaces multiples, puis trim ;
  6. si le résultat est vide : `capitalizeFirst(original trimé)` ;
  7. si la part de majuscules parmi les lettres `[A-Za-zÀ-ÿ]` (majuscules = `[A-ZÀ-Ý]`) est **> 0.7** : mise en minuscules (`toLocaleLowerCase("fr-FR")`) ;
  8. enfin `capitalizeFirst`.
  
  Exemple : « CB CARREFOUR MARKET 12/08 REF 123 » donne « Carrefour market ». « CB » est retiré à l'étape 2, « REF 123 » à l'étape 3 (au moins 3 caractères après REF), « 12/08 » à l'étape 4, puis le texte, entièrement en majuscules, passe en minuscules.
  
  `prettyLabel` est appliqué côté serveur à l'import **et** côté client à l'affichage dans l'écran de validation.

### 8.3 Suggestions de noms de catégorie (`src/lib/category-suggestions.ts`)

`normalize(v)` : minuscules, NFD, suppression des diacritiques, trim.

Table `SUGGESTIONS` (clé normalisée, puis liste) :

| clé | suggestions |
|---|---|
| courses | Courses, Alimentation, Supermarché |
| restaurants | Restaurants, Restos & bars, Sorties food |
| transport | Transport, Déplacements, Mobilité |
| logement | Logement, Loyer & charges, Habitation |
| loisirs | Loisirs, Sorties, Divertissement |
| sante | Santé, Santé & bien-être, Soins |
| abonnements | Abonnements, Abonnements & services, Forfaits |
| shopping | Shopping, Vêtements, Achats perso |
| epargne & investissement | Épargne & investissement, Épargne, Argent mis de côté |
| epargne | Épargne & investissement, Épargne, Argent mis de côté |
| autre | Autre, Divers, Imprévus |

`nameSuggestions(current)` :
1. clé exacte ;
2. sinon, la **première** entrée (ordre d'insertion ci-dessus) dont la clé est **contenue** dans `normalize(current)` ;
3. sinon `[]`.

On retire ensuite le nom actuel (comparaison normalisée) et on garde au plus 3 suggestions.

Il n'y a pas d'entrée pour Énergie, Assurances, Impôts ni Divers (« divers » ne correspond à aucune clé). Ces catégories n'ont donc pas de suggestion.

### 8.4 Pseudo (`src/lib/pseudo.ts`)

`checkPseudo(p)` :
- valeur trimée vide : `{available:true, suggestions:[]}` ;
- sinon, RPC `suggest_pseudo` ;
- **en cas d'erreur RPC : `{available:true, suggestions:[]}`** (optimiste) ;
- sinon `{available: Boolean(r.available), suggestions: r.suggestions ?? []}`.

À l'inscription, un pseudo pris est remplacé automatiquement par `suggestions[0]` (ou `null`). Dans les réglages, un pseudo pris bloque l'enregistrement (« Ce pseudo est déjà pris. Choisissez-en un autre. »). L'unicité finale est garantie par l'index `lower(pseudo)`.

### 8.5 Couleurs de catégorie (`src/lib/category-colors.tsx`)

- Palette `CATEGORY_COLORS` (ordre exact) : `#E4572E, #F2B701, #D01C4F, #B5179E, #2E86AB, #17395E, #4361EE, #00A5A5, #8C5E3C, #7A7F87`. La palette exclut volontairement le vert (couleur principale de l'app) et le violet.
- `DEFAULT_CATEGORY_COLOR = #7A7F87`.
- `pickUnusedColor(used)` :
  - première couleur de la palette non utilisée (comparaison en majuscules) ;
  - sinon, teintes `hue = (step*37) % 360` pour `step = 0..359`, converties avec `hslToHex(hue, 62, 45)`, en prenant la première libre.
  
  - si les 360 teintes sont prises : `DEFAULT_CATEGORY_COLOR`.
- `hslToHex(h, s, l)` (formule exacte) :
  ```
  sat = s/100 ; lum = l/100
  k(n) = (n + h/30) mod 12
  a = sat * min(lum, 1 − lum)
  f(n) = lum − a * max(−1, min(k(n) − 3, min(9 − k(n), 1)))
  hex(v) = round(v*255) en hexadécimal sur 2 chiffres, en MAJUSCULES
  résultat = "#" + hex(f(0)) + hex(f(8)) + hex(f(4))
  ```
- Couleur du groupe « Autre » dans le graphique en anneau : `#94a3b8`.

### 8.6 Budgets globaux et par catégorie

**Budget global** (`budget-sheet.tsx`) :
- saisie d'un montant `value` et d'une période (`'mois'` ou `'annee'`) ;
- si la période est `'annee'` : `monthly = round2(value/12)`, `annual = value` ;
- sinon : `monthly = value`, `annual = round2(value*12)` ;
- enregistrement par `update {monthly_budget, annual_budget, budget_periode}` ;
- affichage : si `budget_periode = 'annee'`, le montant affiché est `annual_budget`, sinon `monthly_budget`.

**Budget de catégorie** (`settings.tsx`, `CategoryRow`) :
- un seul des deux champs est renseigné :
  - période « an » : `{budget_annuel: x, budget_mensuel: null}` ;
  - période « mois » : `{budget_mensuel: x, budget_annuel: null}` ;
  - montant vide : les deux à `null` ;
- période initiale affichée : « an » si `budget_annuel != null && budget_mensuel == null`, sinon « mois » ;
- estimation proposée (issue de `per_category` de `budget/suggest`) : en mode « an », `round(estimate*12)`.

**Budget alloué à une catégorie** (dashboard, onglet « Budgets », et alertes) :
```
yearlyRaw = budget_annuel ?? 0
monthly   = budget_mensuel ?? (yearlyRaw / 12)
yearly    = yearlyRaw || monthly * 12
allocated = (période == année) ? yearly : monthly      // les alertes utilisent toujours monthly
used      = Σ montant des dépenses CONFIRMÉES de la période pour cette catégorie
```
- On ne garde que `allocated > 0`, triés par `used/allocated` décroissant.
- Barre : `min(100, used/allocated*100)`. Dépassement si `used > allocated` (affichage `+(used−allocated)`).
- **Alerte** : `allocated > 0 && used >= allocated`, sur le mois civil courant, en heure locale.
- Note : `used` dans l'onglet « Budgets » **ignore le filtre de catégories**.

### 8.7 Dashboard (`src/routes/_authenticated/dashboard.tsx`)

**Séquence de chargement** :
1. `bootstrap_user()` puis `syncProfileFromSignup` (une fois, `staleTime ∞`).
2. Catégories triées par `ordre`, puis `nom`.
3. `materializeRecurring({year: année affichée})` (`staleTime` 5 min, sans nouvel essai). Les dépenses ne sont chargées **qu'après** son succès ou son échec.
4. En parallèle :
   - `listExpenses({dateFrom, dateTo})` pour la période affichée (**date d'achat**, y compris les non confirmées, filtrées ensuite) ;
   - `listExpenses({dateFrom: Y-01-01, dateTo: Y-12-31, confirmed:true})` pour l'année ;
   - `listExpenses({dateEq: aujourd'hui local, confirmed:true})` ;
   - `listExpenses({confirmed:false})` (en attente, toutes dates) ;
   - charges récurrentes (`montant, frequence`) ;
   - profil ;
   - quotas.

**Période** :
- `period ∈ {month, year}` et `cursor` (Date locale) ;
- mois : `from = Y-MM-01`, `to = Y-MM-<dernier jour>` ; année : `Y-01-01` à `Y-12-31` ;
- flèches : ±1 mois ou ±1 an. **La navigation vers le futur n'est pas bornée.**

**Filtre multi-catégories** :
- `categoryFilter = null` signifie « toutes » ;
- `allSelected` est vrai si le filtre est `null`, ou si sa liste contient exactement tous les ids ;
- un filtre vide (`[]`) donne « Aucune catégorie » : aucune dépense affichée, avec le message « Sélectionnez au moins une catégorie… » ;
- une dépense **sans catégorie** est exclue dès qu'un filtre partiel est actif ;
- une catégorie nouvellement créée est ajoutée automatiquement à une sélection partielle.

**Montant dépensé et budget** :
```
filteredConfirmed = confirmées de la période (∩ filtre)
spent        = Σ filteredConfirmed.montant
monthlyBudget = profiles.monthly_budget
annualBudget  = profiles.annual_budget || monthlyBudget*12
budget        = (period == year) ? annualBudget : monthlyBudget
ratioBarre    = budget > 0 ? min(100, spent/budget*100) : 0
monthlyRecurring = Σ (annuelle ? montant/12 : montant)      // texte « Charges fixes : X / mois »
plannedMonthly   = Σ (annuelle ? 0 : montant)               // projection des barres futures
```

**Météo du budget** (`BudgetWeather`) :
- `ratio = spent/budget`, **sans aucune projection du rythme**. **[ÉCART]** La description annonce une « météo prédictive qui prévient dès le milieu du mois si le rythme mènera au dérapage » : **ce n'est pas implémenté**. La météo ne reflète que le ratio consommé.
- Seuils exacts (comparaisons strictes) :

| Condition | État | Icône (lucide) | Couleur |
|---|---|---|---|
| `budget <= 0` | « Budget non défini » (variante non compacte uniquement) | — | — |
| `ratio > 1` | « Dépassement » | CloudLightning | #dc2626 |
| `ratio > 0.9` | « Tendu » | CloudRain | #ea580c |
| `ratio > 0.7` | « Vigilance » | Cloud | #d97706 |
| sinon | « Au beau fixe » | Sun | #16a34a |

- Le commentaire du code (« sunny under 70 %, cloudy, rainy, stormy ») correspond.
- Texte : `remaining = budget − spent`. Si `remaining >= 0` : « {remaining} restants sur {budget} », sinon « Dépassement de {−remaining} sur {budget} ». Barre : `min(100, ratio*100)`.
- **Conditions d'affichage** (une seule météo, en version compacte, sous forme de pastille avec popover) :
  - en vue mois : si `monthlyBudget > 0 && monthSpentRaw > 0`, avec `spent = monthSpentRaw = Σ confirmées du mois **sans filtre de catégorie**` ;
  - en vue année : si `annualBudget > 0 && yearSpent > 0`, avec `yearSpent = Σ monthlyTotals.total`, qui **tient compte du filtre de catégorie**.
  - **[AMBIGU]** Ces deux vues sont incohérentes entre elles : à reproduire tel quel, ou à arbitrer.

**Graphique mois par mois** (année du `cursor`) :
```
pour index 0..11 : total[index] = Σ montant (confirmées de l'année, ∩ filtre) dont mois(date) == index+1
currentYear = année locale courante
projectionActive = allSelected && year >= currentYear
isFuture(index) = year > currentYear || (year == currentYear && index > moisCourant0)
point = { index, mois: Intl {month:"short"}, total: round2(total),
          prevu: (projectionActive && isFuture) ? round2(plannedMonthly) : 0 }
```
- Barres empilées `total` + `prevu` (`stackId "mois"`), coins arrondis de 5 px, `maxBarSize` 28.
- Le mois sélectionné (vue mois) est en couleur `primary` pleine. Les autres sont en `primary` à 38 %. Le `prevu` est à 18 % avec un contour pointillé.
- Les **charges annuelles ne sont jamais projetées**. Les mois passés et le mois en cours ne sont pas projetés : ils sont déjà matérialisés en dépenses.
- Axe Y : `domain [0, max(maxDonnées, monthlyBudget) * 1.1]`, 5 graduations, `compactAmount`.
- Ligne de référence rouge `y = monthlyBudget`, en pointillés `5 4`, épaisseur 2, étiquette « Cible {montant} », si `monthlyBudget > 0`, **y compris en vue année** (budget mensuel par barre).
- Axe X : une seule initiale sur mobile, le mois court sur desktop (classe `sm`).
- **Clic sur une barre** : passage en vue mois sur ce mois.
- Moyenne affichée : `Σ total / 12`. Le diviseur est **toujours 12**, même pour une année en cours.

**« Où part l'argent »** : graphique en anneau des `groups` (doc 01 §6.4), légende cliquable qui déplie les tickets du groupe (date courte et description). Si des budgets de catégorie existent, un onglet « Répartition / Budgets » apparaît (§8.6).

**Dépenses du jour et pagination** :
```
todayExpenses = confirmées avec date == aujourd'hui (local)
todayTotal    = Σ montant
todayPages    = max(1, ceil(n / 5))
page          = min(recentPage, todayPages − 1)
pagedToday    = todayExpenses.slice(page*5, page*5 + 5)
```
- Boutons Précédent et Suivant désactivés aux bornes, texte « Page {page+1} / {todayPages} ».
- Ordre : celui de `listExpenses` (date desc, puis `created_at` desc).
- Si le débit différé est actif, encart « Débit différé actif : un achat par carte fait aujourd'hui sera prélevé le {longDate(prelevementDate(today))}. Virements, espèces et chèques restent à leur date. »

**Autres paginations** : charges fixes (`recurring-section.tsx`) : `PAGE_SIZE = 6`, même formule.

**Recherche globale** :
- active si `searchTerm.trim().length >= 2` **ou** si une date est choisie ;
- appel `listExpenses({confirmed:true, limit:50, search?, dateEq?})` ;
- la recherche porte sur la **description uniquement** (sous-chaîne, insensible à la casse ; voir doc 01). Il n'y a **pas** de recherche par catégorie dans la barre. **[ÉCART]** La description annonce une « recherche par mot-clé, date ou catégorie » ; la catégorie passe uniquement par le filtre du bloc pilote ;
- date de recherche bornée à aujourd'hui ;
- résultat : « {n} résultat(s) · {Σ montant} ».

**Dépenses à valider** : affichées en tête si au moins une dépense non confirmée existe. Regroupement décrit en doc 01 §6.4. Libellé du groupe : « Dépenses importées d'un fichier », ou `description de la 1re ligne + "…"`, ou « Dépense vocale ».

**Bandeau des bilans** (`monthly-report.tsx`) :
- affiché si `jour > dernierJourDuMois − 3` (les 3 derniers jours) ;
- en décembre, sous la même condition, ajout du bilan annuel ;
- masqué si la période est dans `report_views`. À l'ouverture : `insert report_views`.

**Historique des bilans** (`report-history.tsx`) :
- les 24 mois écoulés (M−1 à M−24) et les 3 années écoulées (A−1 à A−3) ;
- on ne garde que les périodes ayant au moins une dépense confirmée ;
- groupés par année, en ordre décroissant ; dans chaque année, le bilan annuel apparaît en premier.

**Astuces** (`tips-banner.tsx`) : une astuce par jour au plus (`localStorage` `voxbudget.tip`, l'index, et `voxbudget.tip.day`, la date UTC), parcourues séquentiellement jusqu'à épuisement.

**Rappel quotidien et alertes** : voir doc 01 §5.5.

### 8.8 Saisie et validation d'une dépense (règles UI à reproduire)

- **Saisie manuelle** (`ExpenseDialog`) :
  - champs : montant, description, catégorie (par défaut, la 1re catégorie de la liste), date (≤ aujourd'hui), moyen de paiement (défaut `carte`), nature `variable` ou `fixe` (+ fréquence si fixe) ;
  - enregistrement : `saveExpense(confirmed:true, source:"manual")`, description passée par `capitalizeFirst`.
- **Renommage groupé** : si le libellé d'une dépense existante change et que `countSameLabel(ancien) > 0`, l'app demande de renommer aussi les autres, puis appelle `renameExpenses`.
- **Nature « fixe »** :
  - recherche d'une charge existante par `ilike(nom)` : mise à jour si elle existe, création sinon ;
  - `jour_prelevement = min(28, max(1, jour de la date))` ;
  - repasser une dépense en « variable » supprime les charges de même nom.
- **Validation d'une dictée ou d'un import** (`VoiceReview`) :
  - lignes modifiables : montant, description, date, catégorie ;
  - « Valider » appelle `saveExpense(confirmed:true)` sur chaque ligne, passe la voice entry en `confirmed`, enregistre l'apprentissage et la journalisation (doc 01 §6.3) ;
  - « Plus tard » laisse les lignes non confirmées ;
  - une poubelle supprime la ligne.
  - Libellé « Catégorie apprise de vos corrections précédentes » : affiché si une `merchant_rules` existe pour la clé normalisée de la description.

### 8.9 Profil, langue et thème

- Deux réglages de langue distincts :
  - **langue de l'UI** : `voxbudget.ui`, `getUiLang()`, `fr` ou `en` ;
  - **langue de dictée IA** : `voxbudget.lang`, `getLang()`, envoyée comme `lang` aux endpoints.
  
  Ils sont stockés en `localStorage` uniquement, **pas en base**. **[AMBIGU]** À persister côté profil pour le mobile ?
- Thème clair ou sombre : `voxbudget.theme` (`localStorage`).
- Avatar : icône selon `situation_familiale` (`celibataire` → UserRound, `couple` → HeartHandshake, `famille` → Baby, `colocation` → HousePlus ; défaut UserRound).
- Couleurs de marque du manifest PWA : fond `#fdfbf5`, thème `#14532d`. **[AMBIGU]** L'ambre `#D4A72C` cité dans la description n'apparaît pas dans les fichiers lus ici. Les tokens sont dans `src/styles.css`, non couvert par ce document.

---

## 9. Écarts entre la description produit et le code (synthèse)

| Description | Code réel |
|---|---|
| Whisper + Claude 3.5 Sonnet/Haiku | `openai/gpt-4o-transcribe` + `google/gemini-3.7-flash` (passerelle Lovable) |
| Chiffrement asymétrique, montants chiffrés | Symétrique (`pgp_sym_*`). Montants, dates, catégories, noms de charges fixes et clés marchands **en clair** |
| 10 crédits par mois par défaut | `voice_limit` = nombre de jours du mois (28 à 31). Import : 1 par mois |
| Météo prédictive à mi-mois | Simple ratio dépensé/budget, seuils 0,7 / 0,9 / 1 |
| 2 mois consécutifs max par import | Au plus 2 mois **distincts** (pas forcément consécutifs), contrôlés après l'IA, lignes datées seulement |
| Tranche d'âge | Âge exact (entier) |
| Recherche par catégorie | Pas dans la recherche, seulement via le filtre du bloc pilote |
| Regroupement « marché + boucher » | Regroupement par catégorie à l'affichage seulement |
| Bilingue FR/EN | Bilans serveur, prompts, stopwords et messages d'erreur serveur **en français uniquement** |
| Notifications quotidiennes (PWA) | Locales, app ouverte uniquement. Pas de push serveur |
| Conformité RGPD | Purge partielle (audio, email archivé), pas d'export, pas de politique de confidentialité ni de consentement dans le code |
