# expr glossary

Short dictionary for the offline Path-B experiment under `src/.../expr/`.
SQL table/column names stay as in the DB; Java uses the terms below.

**Dirty** here is not “bad data”. It means a loaded `SuggestedRegion` changed in memory this run (bounds grew and/or new sources attached) and must be written back with `persistDirty`.

---

## Core objects

| Term | What it is |
| --- | --- |
| **activity box** (`ActivityBox`) | One `interaction_counts` row that has a world id and XZ corners. Corners are normalized to `minX/minZ/maxX/maxZ`. This is the unit the discovery loop pages over. |
| **named region** (`NamedRegionBox`) | An admin `regions` row turned into an AABB (`center ± radius`). Used only as hard coverage: activity near it is skipped. |
| **suggested region** (`SuggestedRegion`) | A row already in `suggested_regions`. Can **absorb** nearby activity boxes (grow min/max, attach sources). |
| **new suggested** (`NewSuggestedRegion`) | Same idea as suggested region, but only in memory this run — not inserted yet. Built by **merge** of uncovered activity boxes. |
| **source** | Link `suggested_region_sources`: which activity-box ids (`interaction_count_id` in SQL) belong to a suggested region. |
| **AABB** (`Aabb`) | Axis-aligned XZ box. Merge checks use padded overlap on X and Z, same `wid`, no sqrt. |

---

## Geometry fields

| Term | What it is |
| --- | --- |
| **wid** | CoreProtect world id. Boxes from different worlds never merge. |
| **minX / minZ / maxX / maxZ** | Source of truth for suggested (and activity) geometry. Expand these sides; do not invent separate “radius truth”. |
| **name** (on `NamedRegionBox`) | Display name from `regions.name`. Not used for matching. |
| **status** | Suggested row status in DB (e.g. `pending`). Loaded today; absorb does not filter on it yet. |
| **id** | DB primary key. Activity box id = `interaction_counts.id`. Suggested id = `suggested_regions.id`. |

---

## Actions / verbs

| Term | What it does |
| --- | --- |
| **page** (`findPage`) | Load the next chunk of activity boxes (`LIMIT` / `OFFSET`). |
| **cover / covered by named** | Activity is within merge-distance of a named region → **hard skip** (no absorb, no new suggested). |
| **skip (named)** | Same as cover: count toward `skippedNamed`, do nothing else. |
| **absorb** | Activity is near an existing `SuggestedRegion` → grow that region’s min/max if needed, attach the activity-box id if new. Soft skip for clustering (no new suggested). |
| **merge** | Activity joins an in-memory `NewSuggestedRegion` (or starts one). Same spatial grow idea as absorb, but for not-yet-persisted clusters. |
| **discover** | One full pass: page all activity → skip named → absorb existing → merge/create new → return `DiscoveryResult`. |
| **dirty** | In-memory suggested changed this run (`boundsChanged` and/or `newlyAttachedActivityBoxIds` non-empty). See `isDirty()`. |
| **persist dirty** (`persistDirty`) | `UPDATE` min/max on dirty suggested rows and `INSERT` new source links. |
| **insert new** (`insertNew`) | `INSERT` brand-new `suggested_regions` (+ sources) from `NewSuggestedRegion` list. |
| **ensure schema** | On the DB copy: create suggested tables if missing; drop legacy center/radius/weight columns. |

---

## Result / counters / params

| Term | What it is |
| --- | --- |
| **DiscoveryResult** | Bag from one discover pass: scanned, skippedNamed, dirtySuggested, newSuggested. |
| **scanned** | How many activity boxes were read this run. |
| **skippedNamed** | How many were hard-skipped because of named regions. |
| **dirtySuggested** | Existing suggested regions that need `persistDirty`. |
| **newSuggested** | In-memory clusters to `insertNew`. |
| **uncoveredByNamed** | Pre-stat: activity boxes not near any named region (ignores suggested). Second full scan today. |
| **merge-distance** (`MERGE_DISTANCE`) | Pad (blocks) around AABBs for “near enough” checks. Same idea as prod warning merge. |
| **page size** (`PAGE_SIZE`) | How many activity boxes per SQL page. |
| **TOP_N** | How many new suggested to print at the end. |

---

## SuggestedRegion bookkeeping

| Term | What it is |
| --- | --- |
| **knownActivityBoxIds** | Source ids already linked in DB when the region was loaded. Used so re-runs do not double-attach. |
| **newlyAttachedActivityBoxIds** | Source ids attached this run only — written by `persistDirty`. |
| **boundsChanged** | min/max grew this run. Part of dirty. |

---

## SQL names (do not rename; map to Java)

| SQL | Java term |
| --- | --- |
| `interaction_counts` | activity box table |
| `interaction_count_id` | activity box id in sources |
| `regions` | named regions |
| `suggested_regions` | suggested regions |
| `suggested_region_sources` | sources |
| `region_corner_a_*` / `region_corner_b_*` | corners → activity box min/max |

---

## Path A / Path B (task context)

| Term | What it is |
| --- | --- |
| **Path B** | What `expr` implements: global scan of all activity boxes. |
| **Path A** | Per-player scan (`interactor_player = ?`). Not in this offline tool yet. |
