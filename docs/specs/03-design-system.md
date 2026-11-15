# 03 — Design system nemla (spécification pour le portage Compose Multiplatform)

> Source analysée : application web `nemla-main` (React 19 + TanStack Start + Tailwind CSS v4.2 + shadcn/ui style « new-york », icônes `lucide-react` 0.575).
> Fichiers lus : `src/styles.css`, `src/lib/theme.ts`, `src/lib/category-colors.tsx`, `src/lib/situation-icons.ts`, `src/components/brand-logo.tsx`, `components.json`, `public/manifest.webmanifest`, `src/routes/__root.tsx`, `src/components/ui/*` (variantes réellement importées), migrations SQL des catégories par défaut.
> Les conversions oklch → hex ont été calculées par script (`/home/claude/scratch/colors.py`, matrices OKLab de Björn Ottosson, sRGB, écrêtage simple sur [0, 1]). Une valeur marquée **(hors gamut)** dépasse le gamut sRGB : l'hex donné est la valeur écrêtée (ce qu'affiche Chrome sur un écran sRGB). Sur un écran Display P3 le navigateur peut afficher une teinte légèrement plus saturée.
> Les valeurs marquées **(défaut Tailwind)**, **(défaut tw-animate-css)** ou **(défaut sonner)** ne sont pas écrites dans le dépôt : elles viennent des bibliothèques et sont données pour information.

---

## 1. Thème : mécanisme

| Élément | Valeur dans le code |
|---|---|
| Thèmes | `light` et `dark` uniquement (`src/lib/theme.ts`). **Pas de mode « système »** : le thème par défaut est **clair**, quel que soit le réglage de l'OS. |
| Stockage | `localStorage["voxbudget.theme"]` = `"dark"` sinon clair. |
| Application | classe `.dark` sur `<html>` + `color-scheme` CSS. Restauré au démarrage (`restoreTheme()` dans `__root.tsx`). |
| Variante Tailwind | `@custom-variant dark (&:is(.dark *))` |

Note portage : sur mobile natif, conserver le même comportement (clair par défaut, choix explicite Clair/Sombre dans Préférences) ou ajouter « Système » — **à valider avec le produit, c'est une différence fonctionnelle**.

---

## 2. Tokens de couleur

### 2.1 Thème clair (`:root`)

| Token | Valeur d'origine | Hex sRGB | Remarque |
|---|---|---|---|
| `background` | `oklch(0.982 0.012 95)` | `#FBF9F0` | fond ivoire |
| `foreground` | `oklch(0.24 0.03 155)` | `#132419` | texte principal (vert très sombre) |
| `card` | `oklch(1 0.004 95)` | `#FFFFFC` | (hors gamut, écrêté) |
| `card-foreground` | `oklch(0.24 0.03 155)` | `#132419` | |
| `popover` | `oklch(1 0.004 95)` | `#FFFFFC` | (hors gamut) |
| `popover-foreground` | `oklch(0.24 0.03 155)` | `#132419` | |
| `primary` | `oklch(0.45 0.11 158)` | `#00673D` | vert profond — (hors gamut, écrêté) |
| `primary-foreground` | `oklch(0.985 0.01 95)` | `#FCFAF3` | |
| `secondary` | `oklch(0.95 0.02 120)` | `#ECF1E2` | |
| `secondary-foreground` | `oklch(0.32 0.05 158)` | `#1A3B29` | |
| `muted` | `oklch(0.955 0.014 95)` | `#F3F0E6` | |
| `muted-foreground` | `oklch(0.52 0.02 150)` | `#616C63` | |
| `accent` | `oklch(0.7498 0.1407 87.06)` | `#D4A72C` | ambre doré (correspond exactement à `#D4A72C` cité dans la description produit) |
| `accent-foreground` | `oklch(0.24 0.05 81.2)` | `#2B1C00` | (hors gamut) |
| `destructive` | `oklch(0.577 0.19 25)` | `#D2393B` | |
| `destructive-foreground` | `oklch(0.985 0.01 95)` | `#FCFAF3` | |
| `border` | `oklch(0.9 0.015 110)` | `#DEDFD4` | |
| `input` | `oklch(0.9 0.015 110)` | `#DEDFD4` | |
| `ring` | `oklch(0.45 0.11 158)` | `#00673D` | (hors gamut) |
| `chart-1` | `oklch(0.55 0.14 155)` | `#00884B` | (hors gamut) — **non utilisé** dans les écrans |
| `chart-2` | `oklch(0.7 0.16 60)` | `#E58212` | non utilisé |
| `chart-3` | `oklch(0.6 0.13 230)` | `#008DBE` | (hors gamut) — non utilisé |
| `chart-4` | `oklch(0.65 0.17 20)` | `#E45B63` | non utilisé |
| `chart-5` | `oklch(0.72 0.14 100)` | `#B9A624` | non utilisé |
| `sidebar` | `oklch(0.975 0.014 95)` | `#FAF7EC` | **non utilisé** (composant sidebar jamais importé) |
| `sidebar-foreground` | `oklch(0.24 0.03 155)` | `#132419` | non utilisé |
| `sidebar-primary` | `oklch(0.45 0.11 158)` | `#00673D` | non utilisé |
| `sidebar-primary-foreground` | `oklch(0.985 0.01 95)` | `#FCFAF3` | non utilisé |
| `sidebar-accent` | `oklch(0.95 0.02 120)` | `#ECF1E2` | non utilisé |
| `sidebar-accent-foreground` | `oklch(0.32 0.05 158)` | `#1A3B29` | non utilisé |
| `sidebar-border` | `oklch(0.9 0.015 110)` | `#DEDFD4` | non utilisé |
| `sidebar-ring` | `oklch(0.45 0.11 158)` | `#00673D` | non utilisé |

### 2.2 Thème sombre (`.dark`)

| Token | Valeur d'origine | Hex sRGB | Remarque |
|---|---|---|---|
| `background` | `oklch(0.145 0.038 150)` | `#000E03` | |
| `foreground` | `oklch(0.975 0.008 95)` | `#F8F7F1` | |
| `card` | `oklch(0.195 0.038 152)` | `#061A0C` | |
| `card-foreground` | `oklch(0.975 0.008 95)` | `#F8F7F1` | |
| `popover` | `oklch(0.195 0.038 152)` | `#061A0C` | |
| `popover-foreground` | `oklch(0.975 0.008 95)` | `#F8F7F1` | |
| `primary` | `oklch(0.74 0.105 152)` | `#76BE8A` | vert clair |
| `primary-foreground` | `oklch(0.145 0.038 150)` | `#000E03` | |
| `secondary` | `oklch(0.235 0.045 152)` | `#0B2413` | |
| `secondary-foreground` | `oklch(0.975 0.008 95)` | `#F8F7F1` | |
| `muted` | `oklch(0.235 0.045 152)` | `#0B2413` | |
| `muted-foreground` | `oklch(0.72 0.03 150)` | `#98AA9B` | |
| `accent` | `oklch(0.72 0.13 87.06)` | `#C79E32` | |
| `accent-foreground` | `oklch(0.97 0.01 81.2)` | `#F9F5EE` | |
| `destructive` | `oklch(0.704 0.191 22.216)` | `#FF6467` | (hors gamut) |
| `destructive-foreground` | `oklch(0.984 0.003 247.858)` | `#F8FAFC` | |
| `border` | `oklch(1 0 0 / 10%)` | `#FFFFFF` alpha 10 % = `#1AFFFFFF` (ARGB) | composé : `#1A261C` sur background, `#1F3124` sur card |
| `input` | `oklch(1 0 0 / 15%)` | `#FFFFFF` alpha 15 % = `#26FFFFFF` | composé : `#273229` sur background, `#2B3C31` sur card |
| `ring` | `oklch(0.65 0.08 152)` | `#699E77` | |
| `chart-1…5` | identiques au clair | `#00884B`, `#E58212`, `#008DBE`, `#E45B63`, `#B9A624` | non utilisés |
| `sidebar` | `oklch(0.195 0.038 152)` | `#061A0C` | non utilisé |
| `sidebar-foreground` | `oklch(0.975 0.008 95)` | `#F8F7F1` | non utilisé |
| `sidebar-primary` | `oklch(0.74 0.105 152)` | `#76BE8A` | non utilisé |
| `sidebar-primary-foreground` | `oklch(0.145 0.038 150)` | `#000E03` | non utilisé |
| `sidebar-accent` | `oklch(0.235 0.045 152)` | `#0B2413` | non utilisé |
| `sidebar-accent-foreground` | `oklch(0.975 0.008 95)` | `#F8F7F1` | non utilisé |
| `sidebar-border` | `oklch(1 0 0 / 10%)` | `#1AFFFFFF` | non utilisé |
| `sidebar-ring` | `oklch(0.65 0.08 152)` | `#699E77` | non utilisé |

> Attention : `accent-foreground` est **sombre en clair** (`#2B1C00`) mais **clair en sombre** (`#F9F5EE`). En sombre, un texte `accent-foreground` posé sur `accent` (`#C79E32`) a un contraste faible. C'est le comportement du web, à reproduire tel quel ou à corriger (à décider).

### 2.3 Variantes d'opacité réellement utilisées (composées)

Tailwind v4 implémente `bg-primary/10` par `color-mix(in oklab, var(--primary) 10%, transparent)`, soit la couleur avec un alpha de 10 %. En Compose : `MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)`. Valeurs composées (sRGB) pour contrôle visuel :

| Usage | Clair sur background | Clair sur card | Sombre sur background | Sombre sur card |
|---|---|---|---|---|
| `primary/5` | `#EFF2E7` | `#F2F7F3` | `#06170A` | `#0C2212` |
| `primary/10` (sélection, encarts) | `#E2EBDE` | `#E6F0E9` | `#0C2011` | `#112A19` |
| `primary/18` (barres « prévues ») | `#CEDFD0` | `#D1E4DA` | `#162E1B` | `#1A3723` |
| `primary/20` (piste Progress) | `#C9DCCC` | `#CCE1D6` | `#18311E` | `#1C3B25` |
| `primary/35` (pastille légende) | `#A3C6B1` | `#A6CAB9` | `#2A4C32` | `#2D5338` |
| `primary/38` (barres mois non sélectionnés) | `#9CC2AC` | `#9EC5B3` | `#2D5136` | `#31583C` |
| `primary/40` (contour barres prévues, bordure import) | `#97BFA8` | `#99C2B0` | `#2F5539` | `#335B3E` |
| `accent/10` | `#F8F1DD` | `#FBF6E7` | `#141D08` | `#192710` |
| `accent/15` (avatar header) | `#F6EDD3` | `#F9F2DD` | `#1E240A` | `#232E12` |
| `accent/20` (pastilles d'icône bandeaux) | `#F4E9C9` | `#F6EDD2` | `#282B0D` | `#2D3414` |
| `accent/40` (bordures bandeaux) | `#ECD8A2` | `#EEDCA9` | `#504816` | `#534F1B` |
| `accent/50` (bordure « Dépenses à valider ») | `#E8D08E` | `#EAD394` | `#64561B` | `#675C1F` |
| `muted/30` | `#F9F7ED` | `#FBFBF5` | `#041508` | `#081D0E` |
| `muted/40` | `#F8F6EC` | `#FAF9F3` | `#05170A` | `#081E0F` |
| `muted/50` | `#F7F5EB` | `#F9F8F1` | `#06190B` | `#091F10` |
| `muted/60` | `#F6F4EA` | `#F8F6EF` | `#071B0D` | `#092010` |
| `destructive/5` | `#F9F0E7` | `#FDF5F2` | `#0D1308` | `#121D11` |
| `destructive/10` | `#F7E6DE` | `#FBEBE9` | `#1A170D` | `#1F2115` |
| `destructive/40` | `#EBACA8` | `#EDB0AF` | `#66302B` | `#6A3730` |
| `background/85` (header sticky + flou) | — | — | — | — |
| `card/95` (tooltip graphique) | — | — | — | — |

### 2.4 Couleurs codées en dur (hors tokens)

| Où | Valeur | Hex | Rôle |
|---|---|---|---|
| Météo « Au beau fixe » (`dashboard.tsx`) | `#16a34a` | `#16A34A` | icône Sun + texte |
| Météo « Vigilance » | `#d97706` | `#D97706` | icône Cloud + texte |
| Météo « Tendu » | `#ea580c` | `#EA580C` | icône CloudRain + texte |
| Météo « Dépassement » | `#dc2626` | `#DC2626` | icône CloudLightning + texte |
| Crédits OK (`credits-panel`, `voice-fab`) | `hsl(150 60% 32%)` | `#218352` | compteur restant « vert » |
| Crédits bas (`credits-panel`, `voice-fab`) | `hsl(35 92% 42%)` | `#CE7C09` | compteur restant « ambre » |
| Avertissement doublon / catégorie inconnue (`voice-review`) — bordure | `amber-400` (défaut Tailwind v4 `oklch(0.828 0.189 84.429)`) | `#FFB900` | |
| — fond clair | `amber-50/60` (`oklch(0.987 0.022 95.277)` à 60 %) | `#FFFBEB` alpha 60 % | |
| — fond sombre | `amber-950/20` (`oklch(0.279 0.077 45.635)` à 20 %) | `#461901` alpha 20 % | |
| — texte clair | `amber-700` (`oklch(0.555 0.163 48.998)`) | `#BB4D00` | |
| — texte sombre | `amber-400` | `#FFB900` | |
| Groupe « Autre » (catégories masquées) dans « Où part l'argent » | `#94a3b8` | `#94A3B8` | pastille + part du donut |
| Pastille par défaut d'une charge fixe sans catégorie | `#64748b` | `#64748B` | |
| Overlay des dialogues et feuilles | `bg-black/80` | `#000000` alpha 80 % (`#CC000000`) | scrim |
| Ombre douce `--shadow-soft` | `oklch(0.24 0.03 155 / 0.04)` et `/ 0.06` | `#132419` alpha 4 % et 6 % | voir §4 |
| `theme-color` (meta HTML + manifest) | `#14532d` | `#14532D` | **≠ primary** (`#00673D`) |
| `background_color` (manifest) | `#fdfbf5` | `#FDFBF5` | **≠ background** (`#FBF9F0`) |

Les valeurs amber de Tailwind ne sont pas écrites dans le dépôt (valeurs par défaut de Tailwind v4, converties par le même script).

---

## 3. Rayons (`--radius: 1rem` = 16 px)

| Classe Tailwind | Définition | Valeur |
|---|---|---|
| `rounded-sm` | `calc(var(--radius) - 4px)` | **12 px** |
| `rounded-md` | `calc(var(--radius) - 2px)` | **14 px** — rayon de tous les boutons, inputs, selects, popovers, menus, badges shadcn |
| `rounded-lg` | `var(--radius)` | **16 px** — lignes de liste, encarts internes, TabsList |
| `rounded-xl` | `calc(var(--radius) + 4px)` | **20 px** — `card-soft`, tuiles internes (`rounded-xl border p-3`) |
| `rounded-2xl` | `+ 8px` | 24 px (non vu dans les écrans de l'app) |
| `rounded-3xl` | `+ 12px` | 28 px |
| `rounded-4xl` | `+ 16px` | 32 px |
| `rounded-full` | — | pilule / cercle : FAB, barre de recherche, navigation de période, chips, pastilles |
| `rounded` (sans suffixe) | défaut Tailwind | 4 px (1 seul usage : sous-ligne du drill-down catégorie) |
| Barres du graphique | `radius={[5, 5, 0, 0]}` | 5 px en haut seulement |

> Particularité : à cause de `--radius: 1rem`, les « petits » rayons shadcn sont très arrondis (bouton 36 px de haut avec 14 px de rayon). C'est un trait identitaire du design à conserver.

---

## 4. Ombres et élévation

| Nom | Valeur | Usage |
|---|---|---|
| `--shadow-soft` | `0 1px 2px oklch(0.24 0.03 155 / 0.04), 0 8px 24px oklch(0.24 0.03 155 / 0.06)` → `#132419` à 4 % (y=1, blur 2) + `#132419` à 6 % (y=8, blur 24) | utilitaire `card-soft` (toutes les cartes). Défini uniquement dans `:root` : **même ombre en sombre** (quasi invisible). |
| `shadow` (défaut Tailwind) | `0 1px 3px 0 rgb(0 0 0 / .1), 0 1px 2px -1px rgb(0 0 0 / .1)` | bouton `default`, onglet actif |
| `shadow-sm` (défaut Tailwind) | idem `shadow` en v4 | boutons outline/secondary/destructive, inputs, selects, switch |
| `shadow-md` (défaut Tailwind) | `0 4px 6px -1px rgb(0 0 0/.1), 0 2px 4px -2px rgb(0 0 0/.1)` | popover, select |
| `shadow-lg` (défaut Tailwind) | `0 10px 15px -3px rgb(0 0 0/.1), 0 4px 6px -4px rgb(0 0 0/.1)` | FAB, dialog, sheet, dropdown, toast |

Utilitaire **`card-soft`** (la « carte » de toute l'app) :
```
background: card; border: 1px solid border; border-radius: 20px; box-shadow: shadow-soft
```
Compose : `Surface(shape = RoundedCornerShape(20.dp), color = card, border = BorderStroke(1.dp, border))` + ombre douce custom (deux couches) — `tonalElevation = 0`. Les bordures des cartes peuvent être surchargées : `border-accent/40` (bandeaux astuce/bilan/notification), `border-accent/50` (dépenses à valider), `border-destructive/40` + fond `destructive/5` (alertes budget).

---

## 5. Espacements récurrents (unité Tailwind = 4 px)

| Contexte | Classes | Valeur |
|---|---|---|
| Conteneur de page | `mx-auto max-w-3xl px-5 py-6 space-y-5` | largeur max 768 px, marges latérales 20 px, haut/bas 24 px, 20 px entre sections |
| Bas de page dashboard | `pb-28` | 112 px (libère la place du FAB) |
| Bas de page autres écrans | `pb-16` | 64 px |
| En-tête | `px-5 py-3`, `gap-2` | 20 / 12 px |
| Carte standard | `p-5` | 20 px |
| Bloc pilote | `p-4` (mobile), `sm:p-5` | 16 / 20 px |
| Carte recherche | `p-3` | 12 px |
| Bandeaux (astuce) | `p-4`, `gap-3` | 16 / 12 px |
| Bandeaux (bilan, notification) | `p-3`, `gap-3` | 12 px |
| Tuile interne | `rounded-xl border p-3` | 12 px |
| Ligne de liste | `py-3` + séparateur `divide-y divide-border` | 12 px vertical, filet 1 px |
| Champs de formulaire | `space-y-2` label→champ, `space-y-4` entre champs | 8 / 16 px |
| Groupe de boutons | `gap-2` | 8 px |
| Séparateur interne du bloc pilote | `mt-4 border-t border-border/70 pt-4` | 16 px + filet `border` à 70 % |

Points de rupture : le web est « mobile first » ; le préfixe `sm:` s'applique à partir de **640 px**. **Pour le mobile natif, retenir les valeurs sans préfixe `sm:`** (sauf tablette).

---

## 6. Typographie

### 6.1 Familles et chargement

| Rôle | Variable CSS | Pile | Chargement |
|---|---|---|---|
| Titres / chiffres clés | `--font-display` | `"Outfit", ui-sans-serif, system-ui, sans-serif` | Google Fonts (`__root.tsx`) : `Outfit:wght@400;500;600;700` |
| Corps | `--font-body` | `"DM Sans", ui-sans-serif, system-ui, sans-serif` | Google Fonts : `DM+Sans:wght@400;500;700`, `display=swap` |

Règles de base (`styles.css`) :
- `body` : DM Sans, `-webkit-font-smoothing: antialiased`.
- `h1, h2, h3, .font-display` : Outfit + `letter-spacing: -0.02em`.
- Utilitaire `tabular` : `font-variant-numeric: tabular-nums` (tous les montants). En Compose : `fontFeatureSettings = "tnum"`.

> **Écart à connaître** : DM Sans est chargé **sans graisse 600**. Tout texte de corps en `font-semibold` (68 occurrences, ex. montants des lignes, libellés en gras) est donc rendu par le navigateur avec la graisse disponible la plus proche au-dessus, **700**. Les `h1/h2/h3` en `font-semibold` utilisent bien Outfit 600. Pour un rendu identique sur mobile : embarquer DM Sans 400/500/700 et mapper `SemiBold` du corps sur **700**, ou embarquer DM Sans 600 (léger écart assumé — à décider).
> Polices à embarquer dans `composeResources/font/` : Outfit 400/500/600/700, DM Sans 400/500/700 (fichiers non présents dans le dépôt, à récupérer chez Google Fonts — licence OFL).

### 6.2 Échelle (valeurs par défaut Tailwind v4 : taille / interligne)

| Classe | Taille | Interligne |
|---|---|---|
| `text-[10px]` | 10 px | hérité |
| `text-[11px]` | 11 px | hérité |
| `text-xs` | 12 px | 16 px |
| `text-sm` | 14 px | 20 px |
| `text-base` | 16 px | 24 px |
| `text-lg` | 18 px | 28 px |
| `text-xl` | 20 px | 28 px |
| `text-2xl` | 24 px | 32 px |
| `text-3xl` | 30 px | 36 px |
| `text-5xl` | 48 px | 1 (48 px) |

### 6.3 Tailles par rôle (relevées dans les écrans)

| Rôle | Police | Taille / graisse | Exemple |
|---|---|---|---|
| Titre d'écran (auth, onboarding) | Outfit (h1) | 24 px / 600 | « Créer mon compte », « Vos charges fixes » |
| Titre « Mon espace » | Outfit | 20 px / 600 | settings |
| Titre de carte (h2) | Outfit | 16 px / 600 | « Où part l'argent », « Dépenses du jour » |
| Titre de sous-bloc (h2 du graphique, h3 bilan) | Outfit | 14 px / 600 | « Dépenses mois par mois — 2026 » |
| Titre de dialogue | Outfit si h2 (Radix rend `h2`) | 18 px / 600, interligne 1 | DialogTitle / SheetTitle |
| Logo « nemla » | Outfit | 24 px / 700 (en-tête), 20 px / 700 (auth) | |
| **Montant principal** (bloc pilote) | Outfit | **30 px / 600**, tabulaire (48 px au-delà de 640 px) | « Dépensé · mois » |
| Montant total bilan | Outfit | 30 px / 600, tabulaire | ReportDialog |
| Corps | DM Sans | 14 px / 400 | descriptions |
| Libellé de ligne | DM Sans | 14 px / 500 | description de dépense |
| Montant de ligne | DM Sans | 14 px / 600 (rendu 700) tabulaire | |
| Méta-données / aides | DM Sans | 12 px / 400, `muted-foreground` | « 12 août · Courses » |
| Sur-titre | DM Sans | 12 px, MAJUSCULES, `tracking-wider` (0.05em) | « DÉPENSÉ · MOIS » |
| Légendes graphique, axes | DM Sans | 11 px (légende), 11 px (mois), 10 px (axe Y, label ligne budget) | |
| Badge « Année » (historique) | DM Sans | 10 px / 600, MAJUSCULES | |
| Bouton | DM Sans | 14 px / 500 (12 px en `size=sm`) | |
| Label de champ | DM Sans | 14 px / 500, interligne 1 | |
| Input | DM Sans | **16 px sur mobile** (`text-base`), 14 px ≥ 768 px | |

Pages d'erreur web (404 / erreur) : 72 px / 700 (« 404 »), textes en anglais en dur — spécifique web.

---

## 7. Composants de base (variantes réellement utilisées)

Composants `ui/` importés par les écrans (nombre de fichiers) : button (25), input (12), label (10), select (8), dialog (6), progress (3), toggle-group (2), textarea (2), tabs (2), popover (2), tooltip, toggle, switch, sonner, skeleton, sheet, separator, dropdown-menu, carousel (1 chacun). **Badge, Card, Sheet, Skeleton, Carousel, Tooltip ne sont pas utilisés dans les écrans de l'app** (landing / admin uniquement ou pas du tout).

### 7.1 Button

Base : `inline-flex, gap 8 px, rounded-md (14 px), 14 px / 500, transition-colors`, icône 16 px, désactivé = opacité 50 %.

| Variante | Fond | Texte | Bordure / ombre | Survol (web) |
|---|---|---|---|---|
| `default` | `primary` | `primary-foreground` | `shadow` | `primary` 90 % |
| `destructive` | `destructive` | `destructive-foreground` | `shadow-sm` | 90 % |
| `outline` | `background` | hérité | 1 px `input` + `shadow-sm` | **fond `accent` (or) + texte `accent-foreground`** |
| `secondary` | `secondary` | `secondary-foreground` | `shadow-sm` | 80 % |
| `ghost` | transparent | hérité | — | **fond `accent` (or)** |
| `link` | — | `primary`, souligné au survol | — | |

| Taille | Hauteur | Padding |
|---|---|---|
| `default` | 36 px | 16 px horizontal |
| `sm` | 32 px | 12 px, texte 12 px |
| `lg` | 40 px | 32 px |
| `icon` | 36 × 36 px | — |

Surcharges notables : FAB `h-14 rounded-full pl-5 pr-6 shadow-lg` (56 px) ; bouton micro `size-20 rounded-full` (80 px) ; flèches de période `size-9 rounded-full` variante `secondary`.
> Mobile : le survol n'existe pas ; l'état « pressé » natif (ripple / highlight) remplace `hover`. Décider si l'état pressé des boutons ghost/outline doit être doré comme le survol web.

### 7.2 Input / Textarea / Select (déclencheur)

- Hauteur 36 px (Input, SelectTrigger), Textarea min 60 px ; `rounded-md` 14 px ; bordure 1 px `input` ; fond transparent ; `shadow-sm` ; padding horizontal 12 px.
- Placeholder `muted-foreground`. Focus : anneau 1 px `ring`.
- Taille de texte 16 px (mobile). Select : chevron 16 px opacité 50 %.
- Liste Select / Popover / Dropdown : fond `popover`, bordure, `rounded-md`, `shadow-md` (dropdown `shadow-lg`), padding 4 px ; item 14 px, `py 6 px`, **item focalisé = fond `accent` (or) + `accent-foreground`**, coche 16 px à droite.
- Barre de recherche : Input surchargé `h-11 rounded-full` (44 px pilule) avec icône loupe à gauche (padding 36 px).

### 7.3 Dialog (fenêtre modale)

- Overlay `black/80`, fondu entrant/sortant.
- Contenu : centré, `max-w-lg` (512 px) par défaut, padding 24 px, `gap 16 px`, fond **`background`** (pas `card`), bordure, `shadow-lg`, `rounded-lg` (16 px) **seulement au-delà de 640 px — sur mobile web la fenêtre a des coins carrés et occupe toute la largeur**.
- Animation : fondu + zoom 95 % → 100 %, `duration-200` (200 ms).
- Bouton fermer : X 16 px en haut à droite (16 px du bord), opacité 70 %.
- Header : centré sur mobile, aligné à gauche ≥ 640 px. Footer : colonne inversée sur mobile.
> Recommandation portage : `ModalBottomSheet` ou `Dialog` plein-largeur selon l'écran — **c'est une différence visuelle à valider** ; pour être iso web mobile, une boîte pleine largeur à coins carrés centrée verticalement.

### 7.4 Sheet (feuille latérale)

Composant présent mais **non utilisé dans les écrans de l'app** (`budget-sheet` est en réalité un panneau en ligne, `import-sheet` est un Dialog). Pour mémoire : overlay `black/80`, côté droit par défaut, largeur 75 % (max 384 px), ouverture 500 ms, fermeture 300 ms, `ease-in-out`.

### 7.5 Tabs (segmenté)

- Liste : hauteur 36 px, fond `muted`, `rounded-lg` (16 px), padding 4 px, texte `muted-foreground`.
- Onglet : 14 px / 500, `rounded-md`, padding 12 × 4 px ; **actif = fond `background`, texte `foreground`, `shadow`**.
> Compose : ne pas utiliser `TabRow` M3 (indicateur souligné) mais un contrôle segmenté custom.

### 7.6 ToggleGroup (choix exclusif)

Items `h-9`, `rounded-md`, 14 px / 500, fond transparent ; **sélectionné = fond `accent` (or `#D4A72C` / `#C79E32`) + texte `accent-foreground`** ; survol `muted`. Utilisé pour « Par mois / Par an » et « Variable / Fixe ».

### 7.7 Switch

36 × 20 px, piste `primary` (coché) / `input` (décoché), pouce 16 px fond `background`, `shadow-lg`, translation 16 px.

### 7.8 Progress

Hauteur 8 px, `rounded-full`, piste `primary/20`, remplissage `primary`, `transition-all` (150 ms par défaut Tailwind). Utilisé : météo, bloc pilote, budgets par catégorie, crédits, compte à rebours de dictée. **La barre reste verte même en dépassement** (seuls les textes passent en `destructive`).

### 7.9 Label

14 px / 500, interligne 1 (`leading-none`).

### 7.10 Toast (sonner)

- Position **haut-centre**, `richColors` activé.
- Classes : fond `background`, texte `foreground`, bordure `border`, `shadow-lg` ; description `muted-foreground` ; bouton d'action `primary`.
- Types utilisés : `toast.success`, `toast.error`, `toast.info` (+ `description` pour le nombre de dépenses replacées par la règle de débit).
- Avec `richColors`, sonner applique ses propres couleurs vert/rouge/bleu (défaut sonner, non présentes dans le dépôt) ; l'interaction exacte avec les classes `bg-background` n'est pas déterminable sans exécuter le code — **à vérifier visuellement sur le web avant de figer le rendu mobile**.
> Compose : `SnackbarHost` placé en haut (M3 le place en bas par défaut) — différence à signaler si on garde le bas.

### 7.11 Dialogues de confirmation natifs du navigateur

`window.confirm()` est utilisé pour : suppression d'une dépense, propagation d'un renommage, suppression du compte. Rendu = boîte système du navigateur. Sur mobile natif : `AlertDialog` M3 (Android) / équivalent iOS — **l'apparence diffère forcément**.

### 7.12 Pastille de catégorie (`CategoryDot`)

Cercle plein `rounded-full` de la couleur de la catégorie (défaut `#7A7F87`), tailles utilisées : 12 px (`size-3`), 14 px (`size-3.5`), 16 px (`size-4`). **C'est le seul marqueur visuel de catégorie dans l'app** (voir §9).

---

## 8. Animations et transitions

| Élément | Définition | Durée / courbe |
|---|---|---|
| Dialog / Popover / Select / Dropdown | `animate-in fade-in-0 zoom-in-95` (tw-animate-css) ; popovers + `slide-in-from-*-2` (8 px) | Dialog 200 ms ; autres : durée par défaut tw-animate-css (150 ms, défaut du paquet) ; courbe `ease` |
| Sheet | slide depuis le bord | ouverture 500 ms, fermeture 300 ms, `ease-in-out` |
| Panneaux de « Mon espace » | `animate-in fade-in duration-200` | 200 ms fondu |
| Spinner | `animate-spin` (icône `Loader2`) | 1 s linéaire, infini (défaut Tailwind) |
| Skeleton | `animate-pulse` | 2 s (défaut Tailwind) — non utilisé dans l'app |
| Progress | `transition-all` | 150 ms (défaut Tailwind) |
| Switch | `transition-transform` / `transition-colors` | 150 ms |
| Boutons, lignes | `transition-colors` / `transition` | 150 ms (survol web) |
| Défilement vers la fiche de revue | `scrollIntoView({ behavior: "smooth" })` | natif |
| `mic-pulse` (anneau pulsant `primary` 45 % → 0 %, rayon 0 → 10 px) | `@keyframes nemla-pulse` | **2.4 s `ease-out` infini — utilisé uniquement sur la landing (`voice-demo`)**, pas sur le FAB ni le bouton micro de l'app |
| `.reveal` (landing) | opacité 0 → 1, translateY 18 px → 0 | 0.6 s `ease` |
| `.float-soft:hover` (landing) | translateY −6 px | 0.4 s `ease` |
| `.pop-in` (landing) | opacité 0 → 1, translateY 8 px → 0 | 0.5 s `ease`, `forwards` |
| `prefers-reduced-motion: reduce` | désactive reveal, pop-in, mic-pulse, float-soft | à mapper sur le réglage d'accessibilité « réduire les animations » de l'OS |

> Le FAB, le bouton micro et le compte à rebours **n'ont aucune animation dédiée** dans l'app web (seul le spinner `Loader2` pendant le traitement). Toute animation ajoutée sur mobile (pulsation pendant l'écoute, onde sonore) serait une **différence par rapport au web**.

---

## 9. Palette des catégories

### 9.1 Palette de sélection automatique (`CATEGORY_COLORS`, `src/lib/category-colors.tsx`)

Ordre exact (sans vert ni violet, volontairement) :

| # | Hex | Nom (commentaire du code) |
|---|---|---|
| 1 | `#E4572E` | orange |
| 2 | `#F2B701` | jaune |
| 3 | `#D01C4F` | rouge framboise |
| 4 | `#B5179E` | magenta |
| 5 | `#2E86AB` | bleu |
| 6 | `#17395E` | bleu nuit |
| 7 | `#4361EE` | indigo |
| 8 | `#00A5A5` | turquoise |
| 9 | `#8C5E3C` | brun |
| 10 | `#7A7F87` | gris (= `DEFAULT_CATEGORY_COLOR`) |

Algorithme `pickUnusedColor(usedColors)` (création d'une catégorie dans « Mon espace ») : première couleur de la liste non utilisée (comparaison en majuscules) ; si toutes sont prises, parcourt les teintes `hue = (step × 37) mod 360` pour `step = 0…359` et retourne la première `hsl(hue, 62 %, 45 %)` (convertie en hex majuscule) non utilisée ; sinon `#7A7F87`.

Palette étendue utilisée **uniquement côté serveur** (migration `20260823002037…`, dédoublonnage des couleurs) : les 10 ci-dessus puis `#F4A259`, `#6B2737`, `#0B7A75`, `#9A031E`, `#3D348B`, `#5F6F52`, `#C1440E`, `#1B4965`.

### 9.2 Catégories par défaut (fonction SQL `bootstrap_user`, dernière version : migration `20260823192932…`)

| Ordre | Nom stocké (FR) | Libellé EN dans le code | Couleur | `icone` stockée | Type | Icône Lucide **proposée** (non présente dans le code) |
|---|---|---|---|---|---|---|
| 1 | Courses | aucun dans l'app (*« Groceries » existe dans `en-marketing.ts` pour la landing seulement*) | `#E4572E` | `shopping` | variable | `ShoppingCart` |
| 2 | Restaurants | idem (*« Restaurants »* landing) | `#F2B701` | `utensils` | variable | `Utensils` |
| 3 | Transport | idem (*« Transport »* landing) | `#2E86AB` | `bus` | variable | `Bus` |
| 4 | Logement | — | `#17395E` | `home` | fixe | `House` |
| 5 | Énergie | — | `#F4A259` | `more` | variable | (pas de clé dédiée) |
| 6 | Assurances | — | `#6B2737` | `more` | fixe | (pas de clé dédiée) |
| 7 | Santé | — | `#D01C4F` | `heart` | variable | `Heart` |
| 8 | Loisirs | — | `#00A5A5` | `gamepad` | variable | `Gamepad2` |
| 9 | Shopping | — | `#B5179E` | `shirt` | variable | `Shirt` |
| 10 | Abonnements | — | `#8C5E3C` | `repeat` | fixe | `Repeat` |
| 11 | Divers | — | `#7A7F87` | `more` | variable | `Ellipsis` |
| 12 | Épargne & investissement | — | `#4361EE` | `piggy` | variable | `PiggyBank` |
| 13 | Impôts | — | `#9E2B25` | `more` | variable | (pas de clé dédiée) |

Constats importants :
1. **Les noms de catégories ne sont jamais traduits dans l'app** : ils sont affichés tels que stockés en base (`{category.nom}`, sans `t()`). En anglais, l'utilisateur voit « Courses », « Logement »… **Écart avec la promesse bilingue — à signaler.**
2. **La colonne `icone` est stockée mais jamais affichée.** Le web représente une catégorie uniquement par une pastille de couleur. La promesse « chaque catégorie possède une couleur distincte et une icône dédiée » n'est **pas** tenue par le web. Si le mobile affiche des icônes, c'est une **différence** (la correspondance Lucide ci-dessus est une proposition). Valeur par défaut de la colonne : `'Tag'` (migration initiale).
3. La description produit cite « Alimentation/Courses » ; le nom réel par défaut est « Courses ».
4. Une catégorie est « non budgétable » (aucun champ budget dans Réglages) si `type = 'fixe'` **ou** si son nom contient `impôt`, `impot`, `assurance`, `loyer` ou `taxe`.
5. Couleurs canoniques forcées par migration aussi pour « Autre » (`#7A7F87`).
6. Renommage encadré (`category-suggestions.ts`) : clé normalisée (minuscules, sans accents) → suggestions :
   - `courses` → Courses, Alimentation, Supermarché
   - `restaurants` → Restaurants, Restos & bars, Sorties food
   - `transport` → Transport, Déplacements, Mobilité
   - `logement` → Logement, Loyer & charges, Habitation
   - `loisirs` → Loisirs, Sorties, Divertissement
   - `sante` → Santé, Santé & bien-être, Soins
   - `abonnements` → Abonnements, Abonnements & services, Forfaits
   - `shopping` → Shopping, Vêtements, Achats perso
   - `epargne & investissement` et `epargne` → Épargne & investissement, Épargne, Argent mis de côté
   - `autre` → Autre, Divers, Imprévus
   (le nom courant est retiré, 3 suggestions maximum ; correspondance exacte puis « contient »). Ces suggestions sont **uniquement en français**.

---

## 10. Situation familiale (`src/lib/situation-icons.ts`)

| Valeur stockée | Libellé FR | Libellé EN (dictionnaire effectif) | Icône Lucide |
|---|---|---|---|
| `celibataire` | Célibataire | Single | `UserRound` |
| `couple` | En couple | In a relationship | `HeartHandshake` |
| `famille` | Famille avec enfants | Family with children | `Baby` |
| `colocation` | Colocation | Shared housing (*« Flatshare » dans `en-core.ts` est écrasé par `en-marketing.ts`*) | `HousePlus` |
| (vide / inconnu) | — | — | `UserRound` (défaut) |

Rendu :
- Dans les listes de choix (inscription, profil) : icône 16 px couleur `accent` (or) + libellé.
- Avatar de l'en-tête : cercle 28 px fond `accent/15`, icône 16 px `accent`.
- Fiche profil (lecture) : icône 16 px `accent` devant la valeur.

Sexe : valeurs `femme` / `homme` uniquement (libellés EN effectifs « Woman » / « Man »). Les clés « Autre » et « Je préfère ne pas dire » existent dans les dictionnaires mais **ne sont proposées nulle part**.

---

## 11. Logo et images

### 11.1 Rendu (`src/components/brand-logo.tsx`)

`Row(gap 10 px)` = image + texte :
- Image `nemla-logo.png` (tête de fourmi verte), `object-contain`, taille par défaut 32 px ; **36 px dans l'en-tête**, **40 px sur les écrans auth / reset**.
- Texte `nemla` (minuscules) en Outfit, `leading-none`, décalé de **+4 px vers le bas** (`translate-y-1`) ; par défaut 24 px / 700 / `tracking-tight` (−0.025em) ; 20 px sur auth/reset. Couleur héritée : `text-primary` (lien parent) dans l'en-tête et l'auth.

### 11.2 Fichiers

| Fichier | Présent dans le dépôt ? | Détails |
|---|---|---|
| `public/favicon.png` | oui | 64 × 64, PNG RGBA, fond transparent |
| `public/nemla-192.png` | oui | 192 × 192, RGBA, transparent — icône PWA + `apple-touch-icon` |
| `public/nemla-512.png` | oui | 512 × 512, RGBA, transparent — icône PWA |
| `src/assets/nemla-logo.png` | **non** : seul `nemla-logo.png.asset.json` existe, pointant vers le CDN Lovable (`/__l5e/assets-v1/42a4e67c-…/nemla-logo.png`, 378 850 octets) | **à récupérer** (ou utiliser `nemla-512.png`) |
| `src/assets/landing-persona-student.png` | oui | 1024 × 1024, RGB — landing uniquement |
| `src/assets/*.asset.json` (12 autres) | références CDN seulement | captures `shot3-*` et illustrations `landing-*` : landing uniquement |

Couleurs dominantes mesurées dans `nemla-512.png` (échantillonnage par script, approximatives) : vert foncé ≈ `#1C4632` (tête), vert moyen ≈ `#347442` (bouts des antennes / iris) ; yeux blancs. **Le vert du logo n'est pas le `primary` (`#00673D`)** ni le `theme-color` (`#14532D`).

Pour le mobile : fournir l'icône d'application Android (adaptive icon : premier plan = tête de fourmi, fond à définir — le PNG est transparent) et iOS (pas de transparence autorisée) → **un fond devra être choisi (ex. ivoire `#FBF9F0`), différence obligatoire par rapport au web**. Idéalement obtenir un SVG source.

### 11.3 Manifest PWA

`name`/`short_name` « nemla », `description` « Dictez vos dépenses, l'IA les classe et alimente votre budget. », `start_url` `/dashboard`, `display` standalone, `background_color #fdfbf5`, `theme_color #14532d`.

---

## 12. Icônes (Lucide) utilisées dans l'app

`Plus`, `Mic`, `Pencil`, `FileUp`, `Square`, `Loader2`, `Check`, `Trash2`, `Sparkles`, `AlertTriangle`, `X`, `Search`, `CalendarDays`, `Filter`, `ChevronLeft`, `ChevronRight`, `ChevronDown`, `Sun`, `Cloud`, `CloudRain`, `CloudLightning`, `Moon`, `CreditCard`, `Info`, `Repeat`, `Wand2`, `ListTree`, `Wallet`, `User`, `UserRound`, `Settings`, `ShieldCheck`, `LogOut`, `ArrowLeft`, `Lightbulb`, `BellRing`, `ListChecks`, `History`, `TrendingUp`, `TrendingDown`, `Eye`, `EyeOff`, `HeartHandshake`, `Baby`, `HousePlus`.
Tailles : 12 px (`size-3`), 14 px (`size-3.5`), 16 px (défaut boutons), 20 px (`size-5`), 24 px (météo grande), 28 px (micro dans le bouton 80 px), 24 px (`Square` en enregistrement).
Compose : utiliser les vecteurs Lucide (ex. bibliothèque `composables/icons` « Lucide » ou import SVG → ImageVector) pour rester iso Android/iOS ; **ne pas substituer les Material Icons** (formes différentes).

---

## 13. Proposition de correspondance Material 3 (`ColorScheme`)

| Rôle M3 | Token web (clair → hex) | Token web (sombre → hex) | Commentaire |
|---|---|---|---|
| `primary` | `primary` `#00673D` | `primary` `#76BE8A` | |
| `onPrimary` | `primary-foreground` `#FCFAF3` | `primary-foreground` `#000E03` | |
| `primaryContainer` | `primary/10` composé `#E2EBDE` | `primary/10` `#0C2011` | sélection (choix débit, thème, filtre) |
| `onPrimaryContainer` | `primary` `#00673D` | `primary` `#76BE8A` | |
| `secondary` | `secondary-foreground` `#1A3B29` | `secondary-foreground` `#F8F7F1` | pas d'équivalent direct ; rôle peu utilisé |
| `onSecondary` | `secondary` `#ECF1E2` | `secondary` `#0B2413` | |
| `secondaryContainer` | `secondary` `#ECF1E2` | `secondary` `#0B2413` | boutons `secondary` |
| `onSecondaryContainer` | `secondary-foreground` `#1A3B29` | `secondary-foreground` `#F8F7F1` | |
| `tertiary` | `accent` `#D4A72C` | `accent` `#C79E32` | or de marque |
| `onTertiary` | `accent-foreground` `#2B1C00` | `accent-foreground` `#F9F5EE` | |
| `tertiaryContainer` | `accent/20` composé `#F4E9C9` | `accent/20` `#282B0D` | pastilles des bandeaux |
| `onTertiaryContainer` | `accent-foreground` `#2B1C00` | `accent-foreground` `#F9F5EE` | |
| `background` | `background` `#FBF9F0` | `background` `#000E03` | |
| `onBackground` | `foreground` `#132419` | `foreground` `#F8F7F1` | |
| `surface` | `background` `#FBF9F0` | `background` `#000E03` | dialogues (fond `background` sur le web) |
| `onSurface` | `foreground` `#132419` | `foreground` `#F8F7F1` | |
| `surfaceContainerLow` | `card` `#FFFFFC` | `card` `#061A0C` | cartes ; en clair la carte est plus claire que le fond, en sombre plus claire aussi : ne suit pas la logique tonale M3 → recommandé en couleur étendue `card` |
| `surfaceContainer` | `popover` `#FFFFFC` | `popover` `#061A0C` | menus, listes déroulantes |
| `surfaceContainerHighest` / `surfaceVariant` | `muted` `#F3F0E6` | `muted` `#0B2413` | TabsList, tuiles |
| `onSurfaceVariant` | `muted-foreground` `#616C63` | `muted-foreground` `#98AA9B` | textes secondaires |
| `outline` | `input` `#DEDFD4` | `input` `#FFFFFF` 15 % (`#26FFFFFF`) | contours de champs |
| `outlineVariant` | `border` `#DEDFD4` | `border` `#FFFFFF` 10 % (`#1AFFFFFF`) | bordures, séparateurs |
| `error` | `destructive` `#D2393B` | `destructive` `#FF6467` | |
| `onError` | `destructive-foreground` `#FCFAF3` | `destructive-foreground` `#F8FAFC` | |
| `errorContainer` | `destructive/10` composé `#F7E6DE` | `destructive/10` `#1A170D` | |
| `onErrorContainer` | `destructive` `#D2393B` | `destructive` `#FF6467` | |
| `scrim` | `#000000` | `#000000` | appliquer alpha 0.80 (web) au lieu de 0.32 (M3) |
| `surfaceTint` | `primary` | `primary` | ou `Color.Transparent` pour neutraliser la teinte tonale M3 (recommandé : le web n'en a pas) |
| `inverseSurface` / `inverseOnSurface` / `inversePrimary` | — | — | **aucun équivalent web** (le toast utilise `background`) |
| `surfaceDim`, `surfaceBright`, `surfaceContainerLowest`, `surfaceContainerHigh` | — | — | **aucun équivalent** : à dériver ou à aligner sur `background`/`card` |

### Tokens sans équivalent M3 → couleurs étendues (`NemlaExtendedColors` via `CompositionLocal`)

| Couleur étendue | Clair | Sombre | Usage |
|---|---|---|---|
| `card`, `cardForeground` | `#FFFFFC`, `#132419` | `#061A0C`, `#F8F7F1` | fond / texte de `card-soft` |
| `popover`, `popoverForeground` | `#FFFFFC`, `#132419` | `#061A0C`, `#F8F7F1` | |
| `ring` | `#00673D` | `#699E77` | anneau de focus (accessibilité clavier) |
| `shadowSoft` | `#132419` @4 % + @6 % | idem | ombre `card-soft` |
| `weatherSun` / `weatherCloud` / `weatherRain` / `weatherStorm` | `#16A34A` / `#D97706` / `#EA580C` / `#DC2626` | identiques (non thémées) | météo budget |
| `creditOk` / `creditLow` | `#218352` / `#CE7C09` | identiques | compteurs de crédits |
| `warningBorder`, `warningContainer`, `onWarningContainer` | `#FFB900`, `#FFFBEB`@60 %, `#BB4D00` | `#FFB900`, `#461901`@20 %, `#FFB900` | doublons / catégorie inconnue |
| `chartBarActive`, `chartBar`, `chartBarPlanned`, `chartBarPlannedStroke`, `chartBudgetLine` | `primary`, `primary`@38 %, `primary`@18 %, `primary`@40 %, `destructive` | idem en sombre | graphique mensuel |
| `chart1…chart5` | voir §2 | | **non utilisés** — à ne pas porter sauf besoin |
| `categoryPalette[10]`, `categoryFallback` `#7A7F87`, `otherGroup` `#94A3B8`, `recurringFallbackDot` `#64748B` | | | catégories |
| `sidebar*` | | | **non utilisés — ne pas porter** |

---

## 14. Écarts et alertes (récapitulatif design)

1. Pas de thème « système » sur le web (clair par défaut).
2. DM Sans 600 non chargé → le « semi-gras » du corps est rendu en 700 sur le web.
3. Hover web doré (`accent`) sur ghost/outline/items de liste ; pas d'équivalent tactile direct.
4. Dialogues web à coins carrés pleine largeur sur mobile (< 640 px).
5. Toasts en haut au centre (Snackbar M3 en bas par défaut).
6. `window.confirm()` (boîte du navigateur) → dialogue natif : apparence différente.
7. Catégories : pastilles couleur seulement, pas d'icônes ; noms non traduits.
8. Couleurs météo/crédits/avertissements codées en dur, non thémées (identiques en clair et sombre).
9. `theme-color` `#14532D`, `background_color` `#FDFBF5` et couleur du logo `#1C4632` ≠ tokens `primary`/`background`.
10. Logo principal absent du dépôt (référence CDN Lovable).
11. L'icône d'app iOS ne supporte pas la transparence → fond à définir.
12. Aucune animation sur le FAB / micro dans l'app (la pulsation n'existe que sur la landing).
