# GrokAIV5 — Field desk, not a chatbot

Branch: `GrokAIV5`

The Microsoft Agent Framework sidecar and the 20 office-chat tools are not the product.
They do not run on a job and they do not price a hole.

## What the phone does now

On-device engines in `ai/field/`:

| Engine | What you get |
|---|---|
| `SeasonRules` | NY holds for bat maternity, kits, MBTA, heat trap checks |
| `SignScorer` | Ranked species from droppings, hole size, time of day |
| `RepairCalc` | Cloth, screws, flashing, hours for replace/repair |
| `QuoteBuilder` | Line-item quote + work order at Wildlife Whisperer rates |

Open **Field** on the bottom nav. Everything is on one screen. No extra tap.

Copy the quote. Radio can be off.

## Language

- Remove
- Replace / Repair
- Exclusion

Never “take off.”

## Rates (`PriceBook`)

- Labor $95/hr
- Tax 8.125%
- Inspection $125
- Trip $65

Edit `PriceBook.kt` if your book changes.

## Grok

Optional. Only if `XAI_API_KEY` is in the APK. The field desk does not wait for it.

The Python sidecar under `agents/fieldops-maf/` is leftover. Do not start it for field work.
