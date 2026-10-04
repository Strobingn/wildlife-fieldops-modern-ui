# FieldOps design system

Copy this for other Jetpack Compose apps. Values match `2.7.4-home-polish`. Greyscale chrome, one gray accent, red and green only for status. Dynamic Material You is off.

Source of truth (copy these files, do not invent a parallel palette):

| What | File |
| --- | --- |
| Packed hex tokens | `app/src/main/java/com/strobingn/wildlifefieldops/ui/theme/FieldSwatch.kt` |
| Compose `Color` aliases | `app/src/main/java/com/strobingn/wildlifefieldops/ui/theme/Color.kt` |
| `MaterialTheme` schemes | `app/src/main/java/com/strobingn/wildlifefieldops/ui/theme/Theme.kt` |
| Type scale | `app/src/main/java/com/strobingn/wildlifefieldops/ui/theme/Type.kt` |
| Corner radii | `app/src/main/java/com/strobingn/wildlifefieldops/ui/theme/Shape.kt` |
| Spacing and touch size | `app/src/main/java/com/strobingn/wildlifefieldops/ui/theme/FieldMetrics.kt` |
| Contrast checks | `app/src/test/java/com/strobingn/wildlifefieldops/ui/theme/ColorContrastTest.kt` |
| Customer documents | `app/src/main/java/com/strobingn/wildlifefieldops/util/StandardDocument.kt` |

Preview renders (flat mocks, not device screenshots): [light Home](style/home-light-preview.png), [dark Home](style/home-dark-preview.png).

## Rules

- Accent is gray only: `#3A3A3A` in light, `#D0D0D0` in dark. Legacy names `AccentBlue`, `AccentPurple`, `AccentOrange`, `AccentCyan`, `AccentPink`, `AccentAmber` are gray aliases. Do not paint them blue.
- No yellow, amber, gold, orange, or lime text or icon-with-label. Urgent is red.
- Semantic green (`Success`, completed) and red (`Error`, urgent, cancelled) stay chromatic.
- Normal text and icons on their surfaces meet WCAG AA **4.5:1** in both themes. A 15% status wash on a card must pass too.
- Camera, AR, and map HUDs stay light-on-dark (`#F5F5F5` / `#BDBDBD` on scrim `#C8000000`) in either app theme. Invoice paper and signature pads stay white (`#FFFFFF` / `#111111`).

## Color

`Theme.kt` maps these onto `ColorScheme`. Dark cards, search, and `surfaceContainerLow` / `surfaceContainerHigh` are `#333333`. Dark bottom nav is `surfaceDim` `#2A2A2A`. The dark selected pill is `NavIndicator` `#4A4A4A` (solid). Light nav stays `surfaceContainerLow` `#EEEEEE`, and the light pill is primary at 16% alpha over that bar.

### Surfaces and text

| Role | Light | Dark |
| --- | --- | --- |
| background | `#F5F5F5` | `#121212` |
| surface | `#FFFFFF` | `#2C2C2C` |
| surfaceContainerLowest | `#FFFFFF` | `#121212` |
| surfaceContainerLow (light nav, light cards) | `#EEEEEE` | `#333333` |
| surfaceContainer | `#E8E8E8` | `#2C2C2C` |
| surfaceContainerHigh (dark cards, dark search, dark More actions) | `#E3E3E3` | `#333333` |
| surfaceContainerHighest / surfaceBright | `#DCDCDC` / `#FFFFFF` | `#404040` |
| surfaceVariant | `#E6E6E6` | `#333333` |
| surfaceDim (dark nav bar only) | unused | `#2A2A2A` |
| outline | `#74777F` | `#5A5A5A` |
| outlineVariant (card stroke) | `#C4C4C4` | `#5A5A5A` |
| onBackground / onSurface | `#1A1C1E` | `#F5F5F5` |
| onSurfaceVariant (subtitles, unselected tabs) | `#44474F` | `#BDBDBD` |
| onSurfaceMuted | `#575B63` | `#B0B0B0` |

### Accent, buttons, nav pill

| Role | Light | Dark |
| --- | --- | --- |
| primary (accent, New Job fill) | `#3A3A3A` | `#D0D0D0` |
| onPrimary | `#FFFFFF` | `#111111` |
| primaryDark | `#2A2A2A` | `#B8B8B8` |
| primaryLight / secondary | `#5C5C5C` | `#E8E8E8` |
| primaryContainer | `#E6E6E6` | `#333333` |
| onPrimaryContainer | `#1A1A1A` | `#E8E8E8` |
| secondaryContainer (Dictate fill, selected chips) | `#E0E0E0` | `#B8B8B8` |
| onSecondaryContainer | `#1A1A1A` | `#111111` |
| selected nav pill | primary @ 16% on `#EEEEEE` | `#4A4A4A` |
| selected nav label/icon | `#3A3A3A` | `#D0D0D0` |

Gray accent steps (all achromatic): light `#2E2E2E` `#3A3A3A` `#454545` `#4A4A4A` `#5A5A5A` `#5C5C5C`. Dark `#A8A8A8` `#B0B0B0` `#B8B8B8` `#C0C0C0` `#D0D0D0` `#E0E0E0` `#E8E8E8`.

### Status

| Role | Light | Dark |
| --- | --- | --- |
| pending | `#585C61` | `#D6D6D6` |
| in progress | `#3A3A3A` | `#C4C4C4` |
| completed / success | `#1B5E20` | `#A5D6A7` / `#81C784` |
| cancelled / error | `#9B1B1B` | `#FFB4B4` |
| urgent on cards | `#93000A` | `#FFB0A8` |
| urgent on the inverse hero | `#FFCDD2` | `#4A0C0C` |
| error container | `#FFDAD6` / `#410002` | `#5C1A1A` / `#FFDAD6` |

Status chips use the status color for the label and the same color at 14–15% alpha for the fill. On dark cards that fill still has to clear 4.5:1, which is why dark error and urgent are the brighter reds above.

## Typography

`FontFamily.Default` (system sans). Do not load a brand face.

| Style | Size / line | Weight | Use |
| --- | --- | --- | --- |
| displayLarge | 36 / 44 | Bold | Rare hero numbers |
| displayMedium | 30 / 38 | Bold | |
| displaySmall | 26 / 34 | SemiBold | |
| headlineLarge | 24 / 32 | SemiBold | |
| headlineMedium | 20 / 28 | SemiBold | Screen title. Home "FieldOps" is Bold |
| headlineSmall / titleLarge | 18 / 24 | SemiBold | Header name, hero count |
| titleMedium | 16 / 22 | Medium | Section headers (drawn SemiBold), More row titles |
| titleSmall | 14 / 20 | Medium | Job card titles (drawn SemiBold) |
| bodyLarge | 16 / 24 | Normal | Empty states |
| bodyMedium | 14 / 20 | Normal | Subtitles, greeting |
| bodySmall | 12 / 16 | Normal | Card secondary lines |
| labelLarge | 14 / 20 | SemiBold | |
| labelMedium / labelSmall | 12 / 16 | Medium | Nav labels, sync line, chips. Selected nav label is SemiBold |

## Spacing and radii

Spacing: 4, 8, 12, 16, 20, 24 dp. Screen and card padding 16 dp. Minimum touch 48 dp. Primary actions 56 dp tall.

| Shape | Radius |
| --- | --- |
| extraSmall / small / medium / large / extraLarge | 8 / 12 / 16 / 20 / 28 |
| card, cardLarge | 20 |
| button | 14 |
| search | 16 |
| fab | 18 |
| hero | 24 |
| chip | pill (100) |
| bottom sheet | 28 top corners only |

Home and More use 12 dp between rows. Screen padding is 16 dp. More tool rows and Home job rows keep the 20 dp card radius and use 8 dp vertical padding so more fit; a row is still at least 48 dp tall. The Home Today strip is the same card surface as the header (`surfaceContainerLow`, 1 dp `outlineVariant`), with `onSurface` / `onSurfaceVariant` text and `secondaryContainer` actions. Home uses the same brand header as More, including the sync chip.

## Components

**Header card** (More). `FieldShapes.hero` (24 dp). Fill is the card surface `surfaceContainerLow` (light `#EEEEEE`, dark `#333333`) with a 1 dp `outlineVariant` stroke. 16 dp horizontal and 12 dp vertical padding. Logo circle 40 dp. Title `titleLarge` Bold in `onSurface`. Location `bodySmall` in `onSurfaceVariant` on the next line. Version is `labelSmall` in `onSurfaceMuted` under the location, not beside the name.

**Action buttons.** 56 dp tall, `FieldShapes.button` (14 dp), label SemiBold, one line. New Job: `primary` / `onPrimary`. Dictate job: `secondaryContainer` / `onSecondaryContainer`. On Home and on More they sit side by side as equal weights (12 dp gap, 16 dp screen inset). New Job stays the manual path.

**Search.** `OutlinedTextField`, single line, `FieldShapes.search` (16 dp). Container is `surfaceContainerLow` (light `#EEEEEE`, dark `#333333`). Unfocused border `outline`. Text and icons `onSurface` / `onSurfaceVariant`. Placeholder "Search tools" on More.

**List card.** `FieldCard`: fill `surfaceContainerLow`, 1 dp `outlineVariant` stroke, radius 20, padding 16, min row height 48 dp. Leading icon 24 dp in `primary`, title `titleMedium` SemiBold `onSurface`, subtitle `bodyMedium` `onSurfaceVariant` (max 2 lines), trailing chevron `onSurfaceVariant`. Job rows add a 4 dp status stripe.

**Section header.** `titleMedium` SemiBold `onBackground`. Optional trailing action is a text button in `primary` ("Schedule", "View all", "Today").

**Bottom nav.** Five tabs, in order: Home, Jobs, Inspections, Customers, More. Bar fill is light `surfaceContainerLow` `#EEEEEE` or dark `surfaceDim` `#2A2A2A`. Tonal elevation 0. Selected icon and label use `primary`; unselected use `onSurfaceVariant`. Indicator is the light 16% primary wash, or the dark solid pill `#4A4A4A`.

**More grouping.** One scroll under the header and the two create buttons. Groups, in order: Today, Money, Records, Field, AI, App. Group label matches the section header: `titleMedium` SemiBold `onBackground`. Each tool is one list card.

**Sync status.** Copy: "Synced", "Syncing…", "Pending sync · N", "Sync failed" (a failure detail may follow "Sync failed"). Failed uses `error`. Pending uses muted. The rest use `onSurfaceVariant`. On More this is a chip in the header: `labelSmall` on solid `surface`, 1 dp `outline` stroke, sitting on the version line. Other tabs keep the sync line: `labelSmall`, one line, full width, on `surfaceContainerLow`, 12 dp horizontal / 4 dp vertical padding.

## Layout

- The manual **New Job** button is always beside the **Dictate job** button, including on More. Do not fold one into the other.
- Do not remove a screen, button, or tool. Extra tools live on More, at most two taps from a tab.
- A job page shows the customer (name, phone, company, address, preferred contact) and a link to the inspection. Home shows schedule, route, and job counts. Home does not show dollar amounts; money lives under More → Money.

## Customer documents

One PDF template, `wildlife-whisperer-standard-v1`, in `StandardDocument.kt`. Letter page 612×792 pt, margin 48 pt, logo 88 pt. Section order: logo, header, title, meta, customer, body, totals, notes, terms, signature, footer.

Letterhead is the business profile (defaults in `WildlifeWhispererIdentity.kt`): Wildlife Whisperer LLC, 210 Willow Avenue, Cornwall, New York 12518, (845) 751-8448, austin@wildlifewhispererllc.com. A cleared settings field is omitted; an unset field uses the default.

A full-bleed title band (44 pt, white bold title) sits under the letterhead. A left edge stripe uses the same band color. A white badge on the band carries a one-letter mark. The rest of the band uses a lightened pattern so types stay distinct in grayscale. A top-right stamp (150 pt wide, white text, double frame) uses the band color unless a status fill is set. White on every band below is at least 4.5:1. Band colors are dark and print-friendly: no blue, no yellow or amber.

| Document | Band | Badge | Pattern |
| --- | --- | --- | --- |
| ESTIMATE | `#006660` | E | horizontal stripes |
| INVOICE | `#80202C` | I | diagonal |
| RECEIPT | `#0E6B38` | R | dots |
| INSPECTION REPORT | `#543468` | P | vertical stripes |
| SERVICE CONTRACT | `#683A18` | C | cross |
| EXCLUSION / REPAIR | `#465412` | X | chevron |
| WARRANTY | `#782456` | W | dashes |
| NYS DEC NWCO LOG | `#141416` | L | frame |
| EARNINGS & NY SALES TAX | `#3E3E42` | T | grid |

Paid / zero-balance green is `#0E6B38`. Customer block title is "BILL TO / OWNER".
