# Reprise du backend Lovable et mise en production mobile

Rédigé le 19/11/2026. Statut : **proposé, en attente de validation du propriétaire**.
Ce plan passe avant `BACKEND-PLAN.md` (réécriture Kotlin), qui est **suspendu** : il sera réévalué après les essais en production.

## 1. Objectif

- Aller en production (test fermé puis public) **avant** toute migration de backend, pour tester l'application en conditions réelles.
- **Plus aucune dépendance à Lovable** : code sur le GitHub du propriétaire, hébergement, base et IA sur des comptes du propriétaire.
- **Même technologie** : TanStack Start (React 19, Node), `@supabase/supabase-js`, Postgres Supabase, fonctions SQL et chiffrement
  `app_encrypt` / `app_decrypt` existants. Le code est gardé tel quel ; on ne touche que :
  1. ce qui dépend de Lovable (passerelle IA, aperçu, plugins de build, hébergement) ;
  2. les correctifs de sécurité indispensables à la production (`BACKEND-GAPS.md`, « Sécurité et RGPD ») ;
  3. les endpoints `/api/public/*` qui manquent au mobile (`BACKEND-GAPS.md`, « Bloquant pour le mobile ») ;
  4. les pages légales (CGU, confidentialité, mentions légales, suppression de compte).
- Côté mobile : notifications **locales** (pas de push serveur pour l'instant), libellés et recherche par mot-clé, traitement local des
  relevés, conformité (CGU, RGPD, suppression et export du compte), préparation des stores.
- CGV : plus tard, quand il y aura un modèle économique (aucun paiement dans l'application d'ici là).

## 2. Architecture cible (provisoire)

```
Android / iOS (KMP)  ──HTTPS──►  nemla.app (TanStack Start, Node, conteneur)  ──►  Supabase du propriétaire (Postgres + Auth, UE)
        │                               │
        └──► GoTrue (Auth) en REST       └──► Mistral (transcription Voxtral, extraction), API compatible OpenAI
        └──► PostgREST (lectures sans libellé, déjà fait aux lots 3 à 6)
```

- **Code** : synchronisation GitHub de Lovable vers un dépôt du propriétaire (`nemla-web`), puis Lovable n'est plus utilisé.
  Les lots R1 à R5 se font **dans ce dépôt**, cloné sur le Mac, avec sa propre conversation Claude Code.
- **Base** : nouveau projet Supabase du propriétaire en région UE, schéma recréé à partir de `supabase/migrations` du dépôt web.
  Pas de reprise de données (aucun vrai utilisateur ; les comptes de test sont recréés).
- **Hébergement** : image Docker Node de TanStack Start sur Scaleway Serverless Containers (Paris), comme l'arbitrage A5 du plan Kotlin.
  Repli : Cloudflare Workers, si le code généré par Lovable s'avère lié à cette cible (à constater en R1).
- **IA** : la passerelle `ai.gateway.lovable.dev` est remplacée par l'API Mistral (A1), compatible OpenAI pour `chat/completions`
  avec outils et pour `audio/transcriptions` : seuls l'URL, la clé et les noms de modèles changent. Les prompts restent identiques ;
  la qualité d'extraction est à vérifier sur un jeu d'essai en R2.
- **Secrets** : `APP_ENCRYPTION_KEY`, clé Mistral, clé service Supabase dans les secrets de l'hébergeur, jamais dans un dépôt.
  La clé de chiffrement reste une variable d'environnement (pas de KMS à ce stade, accepté pour la phase de test).
- **Notifications** : locales, planifiées par l'application (A7 du plan Kotlin reporté). Pas de FCM, d'APNs ni de jeton d'appareil.

## 3. Lots

Règles inchangées (`CLAUDE.md`) : une conversation neuve par lot, code écrit par Opus 5.5, Fable 5.2 pour la conception, les contrats et
les revues de sécurité. Lots R : dépôt `nemla-web` (fin de lot : lint, tests, build, image Docker). Lots M : ce dépôt (fin de lot habituelle).

| Lot | Contenu | Modèle | Jetons (M) | Coût équivalent API |
|---|---|---|---|---|
| R0 | Sortie de Lovable, actions du propriétaire guidées : synchro GitHub, projet Supabase UE et migrations appliquées, comptes Scaleway et Mistral, URL de redirection Auth, comptes Apple Developer et Google Play | Opus | 5–10 | 3–5 $ |
| R1 | Inventaire du code web (dépendances Lovable, cible de build, variables, routes), contrats des nouveaux endpoints, section serveur d'`ARCHITECTURE.md`, liste des correctifs de sécurité | Fable | 15–25 | 8–12 $ |
| R2 | Autonomie : retrait de l'aperçu et des plugins Lovable, passerelle IA vers Mistral, Dockerfile, déploiement Scaleway, CI GitHub Actions, domaine `nemla.app` | Opus | 25–40 | 12–20 $ |
| R3 | Sécurité production : RLS `user_quotas`, `profiles.suspended`, `onboarding_done` ; quotas et tailles de `/transcribe` et `/parse/recurring` ; statuts IA remappés ; erreurs toujours en JSON ; IBAN compact ; purge complète à la suppression ; journaux sans donnée utilisateur ; revue Fable | Opus + Fable | 25–45 | 13–22 $ |
| R4 | Endpoints mobile : `GET/POST /expenses` (recherche, filtres), `PUT /expenses/{id}`, `rename`, `same-label`, `recurring/materialize`, `voice/{id}/transcript`, `classification-log`, `today` pour `voice/process`, `DELETE /account`, `GET /account/export`, `POST /import/text` | Opus | 30–45 | 15–22 $ |
| R5 | Conformité web : pages CGU, politique de confidentialité, mentions légales, demande de suppression de compte (Google Play), `.well-known` (App Links, Universal Links), table et endpoint d'acceptation des CGU versionnées | Opus | 15–25 | 8–12 $ |
| M7 | Notifications locales : permission (Android 13+, iOS), rappel de 20 h et son interrupteur (`profiles.daily_reminder`), « Votre bilan est arrivé » planifié (`ReportCalendar`) qui ouvre le bilan, alerte « Budget atteint » après enregistrement, bannières in-app de `user_notifications` | Opus | 35–50 | 15–25 $ |
| M8 | Dépenses complètes : libellés au tableau de bord, recherche par mot-clé, création manuelle et type Fixe, édition du libellé, renommage groupé, matérialisation des charges fixes, `PaymentModeGate`, création de catégorie depuis la revue, astuces (§5.9) | Opus | 40–60 | 20–30 $ |
| M9 | Relevés en local : lecture des CSV bancaires courants et des lignes de PDF texte dans `ai:local` (dates, libellés, montants, débit et crédit), catégorisation locale (mots-clés et historique des libellés), revue puis enregistrement par `POST /expenses` ; repli `POST /import/text` (texte anonymisé) pour les formats non reconnus, avec accord explicite | Opus | 35–50 | 15–25 $ |
| M10 | Compte et conformité : acceptation des CGU à l'inscription et à chaque nouvelle version, liens CGU, confidentialité et mentions dans Mon espace, révocation des consentements IA, suppression et export du compte, écran « Nouveau mot de passe » par lien d'application | Opus | 30–45 | 15–22 $ |
| M11 | Publication : signature et versions, icônes et polices définitives, fiches des stores, TestFlight et test fermé Google Play, questionnaires Data Safety et App Privacy, revue de sécurité mobile Fable (MASVS, journaux, stockage) | Opus + Fable | 25–40 | 13–20 $ |
| | **Total** | | **≈ 280–435 M** | **≈ 140–215 $** |

Dont Fable 5.2 : ≈ 20–30 $ (≈ 14 %). Avec **20 % de marge** pour les reprises après tes essais : **≈ 170–260 $ d'équivalent API**.

Ordre : R0 → R1 → R2 → R3 → R4 → R5, puis M8 → M9 → M10 → M11. **M7 ne dépend d'aucun lot R** et peut se faire tout de suite, en
parallèle de R0 à R5. M8 et M9 attendent R4 ; M10 attend R4 et R5 ; M11 vient en dernier.

### Sur le forfait Pro

Avec la calibration de `BACKEND-PLAN.md` §8 (une semaine de Pro ≈ 150 à 200 $ d'équivalent API pour ce type d'usage) :

| Rythme | Durée | Coût en plus de l'abonnement Pro |
|---|---|---|
| Dans le forfait | ≈ 1,5 semaine de capacité, soit 2 à 3 semaines calendaires | ≈ 0 € |
| Accéléré | ≈ 1 semaine | ≈ 50 à 100 € de crédit supplémentaire |

Comparaison : le plan Kotlin était chiffré à ≈ 270–420 $ et 3 à 4 semaines ; ce plan coûte à peu près deux fois moins et mène en
production plus tôt, au prix d'un code TypeScript qui sera remplacé si la réécriture Kotlin reprend.

### Coûts hors Claude (à vérifier en R0)

| Poste | Ordre de grandeur |
|---|---|
| Supabase | gratuit pour le test fermé ; offre Pro ≈ 25 $/mois en production (sauvegardes) |
| Scaleway Serverless Containers | quelques euros à ≈ 15 €/mois au départ |
| Mistral | à l'usage, faible pour un test fermé |
| Apple Developer | 99 $/an |
| Google Play | 25 $ une fois |
| Relecture juridique des CGU, de la politique et du registre | à chiffrer avec un avocat ou un service spécialisé |

## 4. Conformité pour aller en production

| Exigence | Où | Lot |
|---|---|---|
| CGU versionnées, acceptées à l'inscription, redemandées à chaque nouvelle version | web (texte, table) + mobile (écran) | R5, M10 |
| Politique de confidentialité à une URL stable, liée dans l'app et les fiches | web + mobile | R5, M10 |
| Mentions légales (éditeur, hébergeur) | web | R5 |
| Suppression de compte dans l'app et page web de demande (Google Play) | R4 + R5 + M10 | R4, R5, M10 |
| Export des données (art. 20) | R4 + M10 | R4, M10 |
| Consentements IA séparés et révocables | mobile (existant) | M10 |
| Sous-traitants déclarés (Supabase, Scaleway, Mistral) : registre, DPA, politique ; plus de transfert hors UE si Mistral seul | propriétaire, documents dans `docs/` | R1, R5 |
| AIPD (données financières, voix) | document, relu par le propriétaire | R1, M11 |
| Aucun traceur, journaux expurgés | web + mobile | R3, M11 |
| Data Safety (Google Play) et App Privacy (Apple) | fiches | M11 |
| CGV | plus tard, avec le modèle économique | — |

Les textes juridiques sont rédigés par Claude comme brouillons ; leur relecture par un juriste reste nécessaire avant la publication publique
(le test fermé peut commencer avec des brouillons, à la responsabilité du propriétaire).

## 5. Arbitrages à valider

| # | Question | Proposition |
|---|---|---|
| P1 | Hébergement du code web | Scaleway Serverless Containers (Node), repli Cloudflare Workers selon R1 |
| P2 | IA | Mistral seul (transcription et extraction), comme A1 |
| P3 | Relevés | Lecture locale d'abord ; envoi du texte anonymisé au serveur seulement en repli, avec accord explicite |
| P4 | Notifications | Locales uniquement ; le push serveur reviendra avec le backend définitif |
| P5 | Site web | L'application web Lovable reste en ligne telle quelle sur `nemla.app`, avec les pages légales ajoutées |
| P6 | Plan Kotlin | Suspendu ; réévalué après les essais en production |

## 6. Ce qui reste à faire par le propriétaire

- Activer la synchronisation GitHub dans Lovable, puis résilier Lovable une fois R2 déployé et vérifié.
- Créer et posséder les comptes : Supabase (UE), Scaleway, Mistral, Apple Developer, Google Play.
- Signer les DPA ; faire relire CGU, politique de confidentialité, mentions légales et AIPD.
- Tester chaque lot sur appareil Android et simulateur iOS (liste d'essais fournie à la fin de chaque lot).
