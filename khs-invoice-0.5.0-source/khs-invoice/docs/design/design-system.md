# KHS Design System — v1.0

**Direction:** Friendly by design. Bold by nature.

An original, multi-brand-ready system inspired by two Behance case studies: Stay Insurance Brand & Website (soft expressive geometry, pastel category coding) and Pilo Autonomous Taxi App (bold contrast, dark feature surfaces, large typographic hierarchy, action-first UX). This is **not** an official design system for either project. Color values other than explicitly noted Pilo reference values are proposed KHS decisions.

## 1. Design DNA
- **Friendly geometry:** rounded cards (28px), large-feature containers (40px), pill actions. Avoid saturating every element with brand-specific decorative shapes.
- **Bold utility:** decisive primary calls to action, ink-on-lime for actions, white-on-dark for featured stats and maps.
- **Calm canvas:** off-white base with selective pastel surfaces; dark sections make the hierarchy intentional.
- **Content before ornament:** progressive disclosure and short task paths; dense dashboards stay neutral.
- **One identity, many apps:** same primitive tokens and accessible patterns for web, Android, shop, and admin.

## 2. Color roles
| KHS token | HEX | Usage |
|---|---|---|
| primary lime | `#D4FA56` | Primary calls to action, selected controls, focal highlights |
| ink | `#161715` | Primary text, dark feature backgrounds |
| canvas | `#F5F5F2` | App/page background |
| white | `#FFFFFF` | Raised surfaces |
| stone | `#E8E9E4` | Neutral fills |
| mint | `#C9EDCF` | Friendly category surface |
| butter | `#F8E58D` | Secondary promotional surface |
| sky | `#B9DDF5` | Informational category surface |
| lilac | `#DED3F7` | Optional variant |

Pilo source publishes reference colors `#92FE07`, `#F5F5F5`, `#0A0604`, etc. KHS *intentionally* uses softer lime rather than the original neon to preserve the Stay balance.

### Color allocation
Suggested desktop marketing mix: 55–70% neutral, 15–25% ink/dark, 10–20% pastels and lime. For dashboards: 75–90% neutral, smaller amounts of bright accents. These are guidelines, not literal pixel quotas.

### Semantic colors
Use independent tokens for success `#216B43`, warning `#885300`, error `#B42318` and focus `#2A5AC8`. Never use the brand lime alone to communicate a successful transaction. Check WCAG 2.2 contrast on final combinations and every state.

## 3. Typography
- Persian **Estedad (استعداد)** is the official KHS primary UI typeface. Latin-only headings, numbers and technical copy may use **Inter**. Use the Estedad variable weight range 100–900, with 400 for body, 500 for labels, 600 for buttons, 700–800 for headlines. This is a KHS design decision, not a claim about either inspiration project’s original fonts.
- Display desktop 64–88px; mobile 42–52px. H1 48–64px, H2 32–44px, H3 24–32px, body 16px / 1.65–1.75, supporting 14px, caption 12px.
- Default bold 700–750, body 400–500, important labels 600–650.
- Large English display may use negative tracking; **never apply aggressive negative tracking to Persian**.
- Use tabular numerals for money, prices and stats; preserve bi-directional number rendering.

## 4. Spacing, grid and geometry
- 4px step spacing: 4/8/12/16/20/24/32/40/48/64/80.
- Desktop max width 1200px, 12-column grid (24px gutters); tablet 8, mobile 4 (16px gutters).
- Radius controls 12px, form fields 20px, cards 28px, feature sections 40px, action pills 999px.
- Minimum accessible target 44×44px; preferred button height 48–52px.
- Neutral cards rely on surface contrast and quiet borders, with *very subtle* shadow only for elevation.

## 5. Component matrix
| Component | Variants | States / guardrails |
|---|---|---|
| Button | lime-primary, ink-primary, outline, icon, destructive | hover, focus-visible, pressed, disabled, loading |
| Cards | neutral, pastel-category, featured-dark, statistic, product | click entire card only when it navigates; consistent padding |
| Text fields | input, search, select, textarea | label, helper, error, required, focus, disabled |
| Navigation | top bar, sidebar, mobile tabs | active, hover, focus; RTL mirrored where appropriate |
| Status | chip, badge, toast, banner | status semantics separate from brand accents |
| Data | stat tile, chart, sortable table, filter | numeric alignment, text legends, accessible fallback |
| Workflow | stepper, progress, confirmations | clear current step, reversible actions, busy state |
| Overlays | dropdown, drawer, modal | focus management, escape, return focus |
| Feedback | skeleton, no-results, empty, error | meaningful recovery actions; reduce motion |

## 6. Usage recipes
**Landing page:** oversized typographic hero → clear dark/bright CTA → category pastel cards → feature proof → dark feature section → FAQ → final CTA.

**Mobile product:** greeting and high-priority task → bold featured card → lime CTA → rounded bottom navigation → progressive details in secondary surfaces.

**Dashboard:** compact top bar → summary stat strip with at most one dark/bright feature tile → charts/tables on neutral surfaces → filters → secondary content. No giant hero title in a dense workspace.

**Ecommerce:** clean product imagery → floating compact cards → ink text hierarchy → lime add-to-cart button → dark checkout summary with restrained accents.

## 7. RTL, accessibility, motion
- Support RTL at document level and use CSS logical positioning.
- Don't mirror product photos, brand assets, charts or numeric time direction automatically.
- Focus ring visible and independent of lime; WCAG 2.2 AA target (4.5:1 normal text, 3:1 large text and non-text controls where applicable).
- Hover transitions 150ms, panels 240ms; `prefers-reduced-motion` respected.
- Reserve vertical space for Persian multiline headings, validation and localization.
- Every interaction needs keyboard and touch equivalents. Use real semantic elements.

## 8. Brand adaptation
The KHS tokens are the parent **system**. Each product may override `--khs-lime` and select a category accent to establish its own feel. Keep typography, spacing, shape, accessibility and interaction patterns constant across products. Use theme layers rather than duplicating component implementations.

## 9. Implementation
Use `tokens.css` and `.khs-*` primitives. `tokens.json` is a portable source starter (not yet a formal W3C Design Tokens specification). `preview.html` is a static example, not a production application. Before launch, run accessibility review, visual regression checks, RTL testing, responsive testing, and confirm contrast of all token pairings.

### Sources
- https://www.behance.net/gallery/249142927/Stay-Insurance-Brand-Website
- https://www.behance.net/gallery/249780345/Pilo-Autonomous-Taxi-App-UXUI-Branding
- https://dribbble.com/shots/27398978-Pilo-Autonomous-Taxi-App-UX-UI-Branding

### Font loading

The CSS token package imports Estedad from Google Fonts. For production/offline use, install `@fontsource-variable/estedad` and serve the font locally, replacing the external CSS import. Do not redistribute commercial or third-party font files without checking their license; Estedad is licensed under OFL-1.1.
