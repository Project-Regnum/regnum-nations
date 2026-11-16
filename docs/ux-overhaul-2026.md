# UX audit and implementation conventions

Existing work: Nations feat/regnum-chat-ui and Economy feat/hybrid-currency-nations-api have local changes. Agriculture is unchanged. No branches or dependency pins are changed.

| Journey | Audit / priority | Implementation direction |
| --- | --- | --- |
| City / kingdom overview | Existing atlas composition already supplies identity and section destinations | Preserve terrain, heraldry and snapshot requests |
| Administration | Five equal tabs compete with search; actions precede selection | Compact previous/current/next section navigation; contextual actions |
| Citizens | Owner controls imply impossible operations; UUID tooltips; destructive initial focus | Selection-first actions, owner protection, safe confirmation focus |
| Territory | Raw dimension identifiers; ambiguous return label | Friendly dimension labels and Back; retain abandon confirmation |
| Warfare | Six equally weighted navigation cells; raw references; generic empty state | Compact section navigation, meaningful empty guidance, retain action confirmations |
| Diplomacy | Return says Close although it returns to parent | Back |
| Treasury | Existing permission snapshot and wallet balance; vanilla blur can obscure custom content | Preserve permissions and clarify disabled transfers |
| Trade | Bright grey panel, tiny scaled labels, no explicit available balance | Regnum palette, readable fitted labels and contextual amount tooltip |
| Balance slot / wallet | Active system, not legacy Money Pouch | Preserve conversion protocol and inventory layout |

## Conventions
Logical pixels, 20px controls, 6px gaps; charcoal surfaces, warm muted gold, restrained focus indicators. Back returns to parent; Close exits. Section controls show the current destination and previous/next destinations in tooltips. Lists precede contextual operations. Destructive confirmations never receive initial focus. Full labels remain available to native narration and tooltips. No new runtime dependency, packet or persistence schema.

## Placement
Native Kingdom Obelisks may be founded anywhere in claimed territory owned by the intended capital city, in its dimension. City and state anchors can relocate across that city's claims. Ownership, one-domain, affiliation, distance-to-player, canonical identity and recovery checks remain authoritative. Existing adjacent pairs and historical combined anchors remain supported. No migration is required.

## Verification
Build and manual verification results are recorded after implementation. Required manual matrix: 320x240 and larger logical sizes, English/Ukrainian, long names, keyboard focus, empty/error/permission states, multiplayer trade and relocation. Automated builds do not establish visual correctness.
