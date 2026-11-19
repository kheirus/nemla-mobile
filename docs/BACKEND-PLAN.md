# Backend nemla en Kotlin : plan de réécriture et sortie de Lovable

Rédigé le 19/11/2026. Statut : **suspendu le 19/11/2026** au profit de `LOVABLE-EXIT-PLAN.md` (reprise du code Lovable, production d'abord) ;
validé auparavant par le propriétaire le 19/11/2026, architecture, plan et arbitrages A1 à A8 (§9) compris.
Ce plan remplace, une fois validé, la colonne « À créer » de `BACKEND-GAPS.md` : on ne complète pas le backend Lovable, on le remplace.

## 1. Objectif et périmètre

- **Plus aucune dépendance à Lovable** : ni hébergement, ni passerelle IA (`LOVABLE_API_KEY`), ni code TanStack. Tout le code est sur le GitHub du propriétaire.
- **Backend réécrit en Kotlin**, avec le même domaine que les applications : les règles métier de `core:domain` (débit différé, budgets,
  calendrier des bilans, matérialisation des charges fixes) et l'anonymiseur de `ai:local` sont **compilés pour la JVM et exécutés tels quels
  par le serveur**. Une règle n'existe qu'une fois.
- **Supabase conservé**, mais réduit à deux services : **Postgres géré** et **Auth (GoTrue)**. Aucun SDK Supabase, ni côté serveur ni côté mobile.
  PostgREST, Storage, Edge Functions et Realtime ne sont **pas utilisés** ; l'API de données Supabase est désactivée.
- **Mobile d'abord, mobile seulement** : seules les applications Android et iOS se connectent. Le site `nemla.app` devient une page vitrine
  statique (présentation, boutons des stores, politique de confidentialité, mentions légales, page « supprimer mon compte », fichiers
  `.well-known` des liens d'application). Aucune connexion web, aucune console d'administration web.
- **Pas de reprise de données** : aucun vrai utilisateur. Le schéma est refait de zéro (v2), l'ancien projet Supabase est archivé puis supprimé.

### Supabase et « standards Kotlin » : compatible

Oui, garder Supabase est conforme à une pile Kotlin standard, à condition de le traiter comme un fournisseur d'infrastructure :
- le serveur parle à Postgres en **JDBC** (pilote PostgreSQL officiel, pool HikariCP, migrations Flyway) ; rien de spécifique à Supabase ;
- il vérifie les jetons d'accès Supabase Auth comme n'importe quel JWT : signature asymétrique (clés de signature JWT de Supabase, ES256),
  clés publiques lues sur le JWKS du projet, contrôle de `iss`, `aud`, `exp` ; plugin `ktor-server-auth-jwt` ;
- le mobile garde ses appels REST GoTrue écrits à la main (`feature:auth`, déjà fait) ;
- sortir de Supabase plus tard reste possible : Postgres se déplace par `pg_dump`, et GoTrue est libre et auto-hébergeable.

## 2. Architecture cible

```
Android / iOS (KMP)  ──HTTPS──►  API nemla (Ktor, JVM, conteneur UE)  ──TLS──►  Postgres Supabase (UE)
        │                               │   ├──►  KMS (UE) : clés de chiffrement des clés
        └──HTTPS──►  Supabase Auth      │   ├──►  IA (UE) : transcription, extraction
                     (inscription,      │   └──►  FCM / APNs : notifications
                      connexion, jetons)│
nemla.app (statique) : vitrine, politique de confidentialité, suppression de compte, assetlinks.json, apple-app-site-association
```

- **Une seule porte d'entrée pour les données** : l'API nemla. Le mobile n'appelle plus PostgREST. Postgres n'est joignable que par le rôle
  de l'API (`nemla_api`, sans `BYPASSRLS`, sans droits DDL) ; les rôles `anon` et `authenticated` n'ont plus aucun droit sur le schéma.
- **RLS gardée en défense en profondeur** : chaque transaction de l'API pose `SET LOCAL app.user_id = '<uuid du JWT>'` et chaque table porte
  une politique `user_id = current_setting('app.user_id')::uuid`. Un bogue d'autorisation dans le code Kotlin ne suffit donc pas à lire les
  données d'un autre utilisateur.
- Migrations par un rôle distinct (`nemla_migrator`), utilisé seulement par la CI de déploiement.

### Nouveaux modules

```
api/contract        KMP (commonMain) : DTO kotlinx.serialization, chemins des routes, codes d'erreur. Partagé serveur ↔ core:data.
server/app          Ktor (JVM) : démarrage, configuration, plugins (auth, limites, en-têtes), routes, assemblage Koin
server/data         accès Postgres (JDBC, Flyway), dépôts serveur, chiffrement des champs (Tink), contexte RLS
server/ai           moteurs IA serveur (implémentations de AiEngine de ai:api), quotas, consentements
server/notifications jetons d'appareil, envoi FCM / APNs, tâches planifiées
web/landing         page vitrine statique (HTML/CSS sans framework ni traceur)
```

Règles de dépendance ajoutées (à reporter dans `ARCHITECTURE.md` §2 au lot B1) :

```
core:common, core:domain, ai:api, ai:local   + cible jvm()     (aucun autre changement : Kotlin pur)
api:contract     -> kotlinx.serialization uniquement
core:data        -> + api:contract                              (remplace les DTO PostgREST écrits à la main)
server:app       -> server:data, server:ai, server:notifications, api:contract, core:domain, core:common
server:data      -> core:domain, core:common
server:ai        -> ai:api, ai:local, core:domain, core:common  (@OptIn(PrivacyBoundary) reste réservé à ai:local)
feature:*        -> inchangé
```

`ai:remote-nemla` reste le module des moteurs mobiles qui appellent l'API nemla ; seuls ses chemins et DTO changent (via `api:contract`).
Le contrat `:ai:api` ne change pas ; si un changement de signature s'imposait, il passerait d'abord par `ARCHITECTURE.md` §4.

## 3. Choix techniques

Uniquement des versions **stables**, vérifiées et ajoutées à `gradle/libs.versions.toml` au lot B1 (même règle que le mobile).
Une bibliothèque sans version stable compatible Kotlin 2.4 est écartée au profit de la solution de repli indiquée.

| Sujet | Choix | Repli | Raison |
|---|---|---|---|
| Serveur HTTP | Ktor 3 (moteur Netty), déjà au catalogue | — | Même pile que le client, JetBrains, coroutines |
| Sérialisation | kotlinx.serialization | — | DTO partagés avec le mobile |
| Injection | Koin 4 | — | Identique au mobile |
| Accès SQL | JDBC + HikariCP, requêtes SQL paramétrées écrites à la main dans les dépôts | Exposed si une 1.x stable est retenue en B1 | Aucune injection SQL possible, SQL lisible et revu, pas de génération de code |
| Migrations | Flyway | — | Standard JVM, versionné dans le dépôt |
| JWT | `ktor-server-auth-jwt` (java-jwt + jwks-rsa d'Auth0) | Nimbus JOSE+JWT | Bibliothèques éprouvées, JWKS avec cache et rotation |
| Chiffrement | Google Tink (AES-256-GCM, chiffrement d'enveloppe) | — | API difficile à mal utiliser ; clé maîtresse dans le KMS retenu en A4 |
| Tests | kotlin-test, Ktor `testApplication`, Testcontainers (Postgres réel) | — | Les règles RLS et SQL sont testées contre un vrai Postgres |
| Analyse | Spotless + ktlint (déjà là), OWASP Dependency-Check ou Dependabot, scan de l'image (Trivy) | — | Dépendances vulnérables bloquées en CI |
| Image | Conteneur JRE 17 distroless, utilisateur non root, système de fichiers en lecture seule | — | Surface d'attaque minimale |

Hébergement (arbitrage A5, §9) : conteneur dans l'UE, de préférence chez un hébergeur français (Scaleway Serverless Containers ou
Clever Cloud). Projet Supabase dans une région UE (`eu-west-3` Paris ou `eu-central-1` Francfort).

## 4. Sécurité

Référentiels visés : **OWASP ASVS 5.0 niveau 2** pour l'API, **OWASP API Security Top 10**, **OWASP MASVS** (L1, et L2 pour le stockage et le
réseau) pour les applications. Chaque lot se termine par une revue de sécurité (Fable 5.2) contre la liste de l'ASVS concernée.

| Exigence | Mise en œuvre |
|---|---|
| Authentification | JWT Supabase vérifié à chaque requête (signature asymétrique, `iss`, `aud`, `exp`, horloge ±30 s) ; aucune clé `service_role` dans l'API ; mots de passe : politique Supabase relevée (12 caractères, contrôle des mots de passe compromis), confirmation d'email obligatoire |
| Autorisation | `user_id` toujours tiré du JWT, jamais du corps ou de l'URL ; RLS en seconde barrière ; tests d'accès croisé (utilisateur A contre données de B) sur chaque route |
| Données sensibles | Chiffrement applicatif des libellés de dépense, transcriptions, noms des charges fixes et clés marchands personnelles : clé de données par utilisateur (AES-256-GCM, Tink), elle-même chiffrée par une clé maîtresse dans un KMS de l'UE ; l'API ne voit jamais la clé maîtresse |
| Effacement | Suppression de compte = suppression des lignes **et** de la clé de données de l'utilisateur (effacement cryptographique : même une sauvegarde restaurée reste illisible) ; compte GoTrue supprimé par l'API d'administration Auth (seul usage d'une clé d'administration, isolé dans un service dédié) |
| Montants et dates | En clair dans Postgres (nécessaires aux sommes et aux filtres), protégés par la RLS, le chiffrement au repos de Supabase et TLS ; arbitrage A3 |
| Recherche par mot-clé | Déchiffrement en mémoire des libellés du seul utilisateur, avec plafond de lignes ; pas d'index de recherche sur des données en clair |
| Entrées | Validation stricte des DTO (bornes de montant, dates, longueurs, énumérations) ; taille de corps plafonnée par route (audio 2 Mo, texte 90 000 caractères) ; types MIME vérifiés |
| Abus | Limitation de débit par utilisateur et par IP (plugin `RateLimit`), quotas mensuels côté serveur uniquement (non modifiables par l'utilisateur), délais d'attente sur chaque appel sortant |
| Transport | TLS 1.2+ partout, HSTS ; Postgres en `sslmode=verify-full` ; épinglage de certificat côté mobile une fois le domaine `api.nemla.app` figé |
| En-têtes | Pas de CORS (aucun client web), `Cache-Control: no-store` sur toutes les réponses de données, en-têtes de sécurité standard |
| Erreurs | Toujours `{"error": code}` en JSON, codes stables traduits par l'application ; jamais de trace, de SQL ou de message de fournisseur IA renvoyé ; statuts IA remappés (un 401 du fournisseur n'est jamais un 401 client) |
| Journaux | Identifiant de requête, route, statut, durée ; **aucun** corps, jeton, libellé, montant, email ni IP complète ; conservation 30 jours |
| Secrets | Gestionnaire de secrets de l'hébergeur ; rien dans le dépôt ni dans l'image ; rotation documentée |
| Chaîne logicielle | Versions figées, dépendances vérifiées en CI, image reconstruite et scannée à chaque déploiement, branche `main` protégée |
| Sauvegardes | Sauvegardes Supabase quotidiennes (et PITR si offre payante), restauration testée une fois avant publication |

## 5. RGPD

| Obligation | Mise en œuvre |
|---|---|
| Minimisation (art. 5, 25) | Audio jamais stocké : il transite en mémoire vers la transcription puis est oublié ; plus de bucket `voice-notes`. Transcription chiffrée et conservée 30 jours (correction des catégories), puis effacée par une tâche planifiée. Pas d'email dans les tables applicatives (il reste dans Supabase Auth) |
| Hébergement | Toutes les données dans l'UE (Postgres, API, KMS) ; sous-traitants IA de l'UE ou avec engagement contractuel UE (arbitrage A1) |
| Sous-traitants (art. 28) | DPA signés : Supabase, hébergeur, fournisseur(s) IA, Google (FCM), Apple (APNs) ; liste publiée dans la politique de confidentialité |
| Registre (art. 30) | `docs/RGPD-REGISTRE.md` : finalités, catégories de données, durées, destinataires |
| AIPD (art. 35) | Recommandée (données financières + traitement par IA) : `docs/RGPD-AIPD.md`, rédigée au lot B1 et revue au lot B11 |
| Consentement | Consentement IA par niveau, horodaté et versionné **côté serveur** (table `ai_consents`), vérifié par l'API avant tout appel IA ; retrait immédiat |
| Droits | Accès et portabilité : `GET /v1/account/export` (JSON complet, libellés déchiffrés). Effacement : `DELETE /v1/account` (immédiat, effacement cryptographique). Rectification : dans l'application. Page web de demande de suppression (exigée par Google Play) |
| Conservation | Comptes inactifs 24 mois : avertissement puis suppression ; journaux 30 jours ; journaux d'usage IA sans contenu, 12 mois |
| Violation (art. 33) | Procédure de notification CNIL sous 72 h dans `docs/RGPD-REGISTRE.md` |
| Traceurs | Aucun SDK d'analyse, côté serveur comme côté mobile |
| Corpus marchands mutualisé | Uniquement des clés marchands normalisées, sans lien avec l'utilisateur, et seulement après N votes distincts (seuil d'anonymat, N ≥ 5) |

## 6. Modèle de données v2 (mobile d'abord)

Principes : UUID générés **par l'appareil** (création idempotente, prête pour le hors ligne), `updated_at` maintenu par trigger, suppression
logique (`deleted_at`) le temps de la synchronisation puis purge, montants en `numeric(12,2)`, dates d'achat en `date` locale de l'utilisateur
(jamais la date UTC du serveur), fuseau horaire IANA dans le profil.

| Table | Contenu |
|---|---|
| `profiles` | pseudo, langue, devise, fuseau horaire, débit différé (banque, arrêté, prélèvement, depuis), budget global, préférences de rappel, onboarding |
| `user_keys` | clé de données chiffrée par le KMS, version de clé maîtresse |
| `categories` | nom, couleur, icône, budget mensuel, ordre |
| `expenses` | montant, libellé chiffré, date, date de prélèvement (calculée par la règle de `core:domain`), moyen de paiement, catégorie, origine (`manual`, `voice`, `import`, `recurring`), statut (`pending`, `confirmed`), charge fixe d'origine, dictée d'origine |
| `recurring_charges` | nom chiffré, montant, fréquence, jour, catégorie, début, fin |
| `recurring_skips` | occurrences sautées |
| `voice_captures` | transcription chiffrée, statut, date d'expiration (30 jours) |
| `merchant_rules` | clé marchand chiffrée + empreinte HMAC pour la recherche, catégorie, confirmations |
| `merchant_corpus` | clés normalisées mutualisées (sans `user_id`), compteurs |
| `quotas` | compteurs mensuels (dictées, imports) ; écrits par l'API seulement |
| `ai_consents` | niveau, version du texte, date d'accord, date de retrait |
| `ai_usage` | capacité, fournisseur, durée, statut, coût ; **aucun contenu** |
| `report_views` | périodes vues |
| `device_tokens` | plateforme, jeton chiffré, dernière activité |
| `notification_log` | type, période, date d'envoi (anti-doublon) |

Disparaissent : `year_catchup`, `report_notifications`, `user_notifications`, `deleted_accounts` (plus d'email conservé), `user_roles` et
`admin_audit_logs` (pas de console d'administration, arbitrage A6), `classification_logs` et `category_corrections` (remplacés par
`merchant_rules` et `ai_usage`).

## 7. API v1

Préfixe `/v1`, JSON, authentification `Bearer` sur tout sauf `/v1/health`. Le détail (DTO, erreurs) vit dans `api:contract`, qui fait foi.

| Domaine | Routes |
|---|---|
| Compte | `POST /v1/account/bootstrap`, `GET/PATCH /v1/profile`, `GET /v1/account/export`, `DELETE /v1/account`, `GET /v1/pseudo/availability` |
| Catégories | `GET/POST /v1/categories`, `PATCH/DELETE /v1/categories/{id}` |
| Dépenses | `GET /v1/expenses` (période, statut, recherche, curseur), `PUT /v1/expenses/{id}` (création ou modification idempotente), `DELETE /v1/expenses/{id}`, `POST /v1/expenses/rename`, `POST /v1/expenses/confirm` |
| Tableau de bord | `GET /v1/dashboard?month=` (totaux, budgets, répartition, calculés avec `core:domain`) |
| Charges fixes | `GET/POST /v1/recurring`, `PATCH/DELETE /v1/recurring/{id}`, `POST /v1/recurring/{id}/skip` ; occurrences générées par le serveur (plus d'appel client) |
| Débit différé | `PUT /v1/profile/debit` (réapplique la règle aux dépenses concernées) |
| IA | `POST /v1/voice/capture` (audio → dépenses en attente), `POST /v1/voice/transcribe`, `POST /v1/expenses/extract` (texte → dépenses), `POST /v1/import/text` (texte anonymisé), `POST /v1/recurring/parse`, `GET /v1/budget/suggestion` |
| Consentement | `GET/PUT /v1/consents` |
| Bilans | `GET /v1/reports?period=` (version **structurée** : codes et valeurs, textes rédigés par l'application en fr/en), `POST /v1/reports/views` |
| Quotas | `GET /v1/quotas` |
| Notifications | `PUT/DELETE /v1/devices/{token}`, `PATCH /v1/profile` (préférences) |

Toutes les dates « aujourd'hui » viennent du fuseau du profil ou du champ `today` envoyé par l'appareil, jamais de l'horloge UTC du serveur.

## 8. Lots, ordre et coût en ressources Claude

### Méthode de chiffrage

Calibrage sur la consommation **réelle** de ce projet, lue dans les transcriptions Claude Code des lots 0 à 6 (15 au 18/11/2026) :

| Mesure | Lots 0 à 6 |
|---|---|
| Code produit | ≈ 37 700 lignes Kotlin, dont ≈ 12 100 de tests ; specs et docs en plus |
| Jetons traités | ≈ 465 M, dont ≈ 452 M de lectures de cache, ≈ 10 M d'écritures de cache, ≈ 2,2 M de sortie |
| Coût équivalent API | ≈ 220 $ (Opus 5.5 ≈ 216 $, Fable 5.2 ≈ 4 $) |
| Ratio | ≈ 2,1 M jetons par dollar ; ≈ 6 $ pour 1 000 lignes livrées, tests et docs compris |

Tarifs API utilisés (par million de jetons) : Opus 5.5 entrée 4 $, sortie 20 $, lecture de cache 0,20 $ ; Fable 5.2 entrée 8 $, sortie 40 $,
lecture de cache 0,20 $ ; écriture de cache comptée au double du prix d'entrée (cache d'une heure). Le coût est dominé par la relecture du
contexte (lectures de cache), pas par le code écrit.

### Lots

Répartition des modèles conforme à `CLAUDE.md` : Fable 5.2 pour l'architecture, les contrats et les revues de sécurité ; Opus 5.5 pour le code.

| Lot | Contenu | Modèle | Jetons (M) | Coût équivalent API |
|---|---|---|---|---|
| B0 | Sortie de Lovable : synchronisation GitHub du code web (archive en lecture seule), nouveau projet Supabase UE, comptes hébergeur, KMS, IA, FCM, APNs (actions du propriétaire, guidées) | Opus | 5–10 | 3–5 $ |
| B1 | Conception : `ARCHITECTURE.md` (serveur), `api:contract` v1, schéma v2, modèle de menace, AIPD, registre, catalogue de versions serveur | Fable | 30–50 | 15–25 $ |
| B2 | Socle : cibles `jvm()` sur `core:common`, `core:domain`, `ai:api`, `ai:local` ; modules `server:*` et `api:contract` ; plugin `nemla.jvm.server` ; configuration, santé, journaux expurgés, Docker, CI | Opus | 30–50 | 15–25 $ |
| B3 | Base : migrations Flyway, rôles, RLS, chiffrement Tink + KMS, contexte de transaction, tests Testcontainers d'accès croisé | Opus | 45–65 | 20–30 $ |
| B4 | Comptes : vérification JWT, bootstrap, profil, pseudo, consentements, export, suppression (effacement cryptographique) | Opus | 30–50 | 15–25 $ |
| B5a | Dépenses : catégories, création et modification idempotentes, suppression, renommage groupé, recherche sur libellés déchiffrés, confirmation | Opus | 30–45 | 15–20 $ |
| B5b | Calculs : tableau de bord, budgets, charges fixes et génération des occurrences, débit différé (règles de `core:domain`) | Opus | 35–50 | 15–25 $ |
| B6a | IA, socle et dictée : moteurs du fournisseur retenu, contrôle du consentement, quotas, dictée (audio → dépenses en attente), transcription seule | Opus | 35–50 | 15–25 $ |
| B6b | IA, textes : extraction depuis un texte, import de texte anonymisé, charges fixes dictées, suggestion de budget, mémoire marchands | Opus | 30–45 | 15–20 $ |
| B7 | Bilans structurés et leur rédaction fr/en dans l'application | Opus | 20–30 | 10–15 $ |
| B8 | Notifications (ancien lot 7) : jetons, FCM HTTP v1, APNs par jeton `.p8`, rappel de 20 h dans le fuseau de l'utilisateur, bilan de fin de mois, alerte de budget ; côté mobile : permission, réception, ouverture du bon écran | Opus | 50–75 | 25–35 $ |
| B9a | Mobile, données : `core:data` sur `api:contract` (bootstrap, profil, catégories, dépenses avec libellés, tableau de bord, charges fixes), suppression de PostgREST | Opus | 35–50 | 15–25 $ |
| B9b | Mobile, IA et compte : `ai:remote-nemla` sur la nouvelle API, import, bilans, suppression et export du compte, fin des `NotSupportedYet`, liens d'application du mot de passe oublié | Opus | 30–45 | 15–20 $ |
| B10 | Page vitrine `nemla.app`, politique de confidentialité finale, mentions légales, page de suppression de compte, `.well-known` | Opus | 10–20 | 5–10 $ |
| B11 | Audit final : ASVS niveau 2, API Top 10, MASVS, revue des dépendances, AIPD finale, essai de restauration | Fable | 40–70 | 20–35 $ |
| B12 | Mise en production : déploiement, secrets, sauvegardes, alertes de disponibilité, suppression de l'ancien projet Supabase | Opus | 15–25 | 8–12 $ |
| | **Total** | | **≈ 470–730 M** | **≈ 225–350 $** |

Dont Fable 5.2 : ≈ 35–60 $ (≈ 15 % du total). Prévoir **20 % de marge** pour les reprises après vos essais sur appareil :
**enveloppe réaliste ≈ 270–420 $ d'équivalent API**. Jetons de sortie (le code et les documents réellement écrits) : ≈ 3 à 4 M.

Ordre : B0 → B1 → B2 → B3 → B4 → B5a → B5b → B6a → B6b → B7 → B9a → B9b → B8 → B10 → B11 → B12. B10 peut se faire n'importe quand après B1.
**Une conversation neuve par ligne du tableau** (voir `CLAUDE.md`) : chaque ligne reste ainsi sous ≈ 200 à 300 k jetons de contexte.
Chaque lot se termine comme les lots mobiles : `spotlessApply`, tests du lot, `assembleDebug`, framework iOS, plus `./gradlew :server:app:test`
et l'image Docker construite ; commits atomiques ; revue Fable des points de sécurité du lot.

### Ce que cela représente sur le forfait Pro

Relevé de l'application le 19/11/2026 : forfait **Pro**, limite hebdomadaire utilisée à 48 % (remise à zéro le 24/11), crédit supplémentaire
activé (18,81 € dépensés sur un plafond de 40 € par mois). Sur la semaine en cours, ≈ 115 $ d'équivalent API ont été consommés : une semaine
complète de Pro couvre donc **de l'ordre de 150 à 200 $ d'équivalent API** avec ce type d'usage (estimation empirique : Anthropic ne publie
pas la correspondance, et la fenêtre de 5 heures freine avant la limite hebdomadaire).

| Rythme | Durée | Coût en plus de l'abonnement Pro |
|---|---|---|
| Dans le forfait | 2 à 3 semaines de capacité, soit 3 à 4 semaines calendaires en laissant de la place à vos autres usages | ≈ 0 € (le crédit supplémentaire n'absorbe que les pointes) |
| Accéléré | ≈ 1 semaine et demie | ≈ 100 à 200 € de crédit supplémentaire, au-delà du plafond actuel de 40 € |

Points à vérifier dans la carte d'usage de l'application avant B1 : Fable 5.2 peut n'être utilisable sur Pro que par le crédit supplémentaire
(non confirmé par une source officielle) ; dans ce cas les ≈ 35–60 $ de Fable sont facturés en plus, au tarif API.

Coûts hors Claude, à prévoir par le propriétaire : Supabase (gratuit pour le développement, offre Pro ≈ 25 $/mois pour la production
avec sauvegardes), hébergement de l'API (≈ 5 à 20 €/mois au départ), KMS (quelques euros par mois), IA à l'usage, compte Apple Developer
(99 $/an), compte Google Play (25 $ une fois).

## 9. Arbitrages (validés le 19/11/2026)

| # | Question | Décision |
|---|---|---|
| A1 | Fournisseur IA (transcription et extraction) | Mistral (société française, hébergement UE, transcription Voxtral et modèles d'extraction) comme moteur principal ; un second fournisseur peut être branché plus tard par `ai:api` sans toucher aux écrans |
| A2 | Durée de conservation des transcriptions | 30 jours, puis effacement |
| A3 | Montants et dates en clair dans Postgres | Oui (sommes et filtres côté base), protégés par RLS et chiffrement au repos ; tout chiffrer obligerait à calculer en mémoire, possible mais plus lent |
| A4 | KMS | Scaleway Key Manager (région `fr-par`), l'API étant chez Scaleway. Tink n'a pas d'intégration Scaleway native : un adaptateur `Aead` appelle les opérations de chiffrement et de déchiffrement du Key Manager (lot B3, revue Fable). Disponibilité et conditions à vérifier en B0 ; repli : Google Cloud KMS `europe-west9` (Paris), pris en charge nativement par Tink |
| A5 | Hébergement de l'API | Scaleway Serverless Containers (Paris) |
| A6 | Administration (suspension, quotas, support) | Pas de console : scripts SQL versionnés et exécutés par le propriétaire ; une console pourra venir plus tard |
| A7 | Notifications | Push serveur : FCM pour Android, APNs direct pour iOS (pas de SDK Firebase sur iOS), contenu sans montant ni libellé |
| A8 | Rythme | Dans le forfait Pro, 3 à 4 semaines |

## 10. Ce qui reste à faire par le propriétaire

- Activer la synchronisation GitHub dans Lovable pour archiver le code web actuel, puis résilier Lovable une fois B12 terminé.
- Créer et posséder les comptes : Supabase (projet UE), hébergeur, KMS, fournisseur IA, Firebase (FCM), clé APNs, domaine `api.nemla.app`.
- Signer les DPA, faire relire la politique de confidentialité et l'AIPD.
- Tester chaque lot sur appareil Android et simulateur iOS (liste d'essais fournie à la fin de chaque lot).
