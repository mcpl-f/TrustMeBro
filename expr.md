# Offline suggested regions (`expr`)

Path **B** experiment only (global activity-box scan). Not Path A, not decline/name UX.
See also `tasks/extract-regions-for-review.md`.
Term dictionary: **`expr-glossary.md`** (what “dirty” means, verbs, fields).

## Vocab (Java) — one name each

| Term | Java | SQL (unchanged) |
| --- | --- | --- |
| **activity box** | `ActivityBox` | row in `interaction_counts` with wid + corners |
| **named region** | `NamedRegionBox` | row in `regions` → AABB |
| **suggested region** | `SuggestedRegion` / `NewSuggestedRegion` | `suggested_regions` (+ sources) |

Verbs:

| Verb | Meaning |
| --- | --- |
| **cover / skip** | near named → hard skip |
| **absorb** | near existing suggested → expand min/max + attach activity-box id |
| **merge** | grow new in-memory suggested |
| **dirty** | needs UPDATE / source INSERT |

Do not say “page with geometry”, “IC”, “reshape”, “candidate”, “RegionBox” for these.

## Current flow

```text
page activity boxes
    ↓
skip if near named regions (merge-distance)
    ↓
absorb into existing suggested (expand min/max + sources)
    ↓
merge the rest into new suggested
    ↓
persistDirty / insertNew + print top
```

```text
for each activity box:
    if covered by named → hard skip (no write)
    if already in / near existing suggested → absorb
         (expand min/max if needed; attach source if new;
          no new suggested — soft skip for clustering)
    else merge into / create new suggested
```

Named vs suggested: same spatial test (merge-distance), different outcome.
Inside suggested ≠ ignore — still absorb so the box does not spawn a duplicate.

Geometry for suggestions is **min/max only**.

## What exists today

| Piece | Role |
| --- | --- |
| `expr.java` | main |
| `SuggestedRegionDiscoveryService` | page → skip → absorb → merge |
| `ActivityBoxRepository` | page activity boxes from `interaction_counts` |
| `NamedRegionRepository` | load named AABBs |
| `SuggestedRegionRepository` | load / persistDirty / insertNew |
| `ExprSchemaRepository` | CREATE + DROP legacy cols on copy |
| `ActivityBox` / `NamedRegionBox` / `SuggestedRegion` / `NewSuggestedRegion` | models |
| `DiscoveryResult` / `Aabb` / `SuggestedRegionPrinter` | result / util / print |

## How to simplify

Reviews: core fine, packaging fat for an offline script.

### Kill / fold

1. **`countUncoveredByNamed`** — second full scan; fold into `discover` or drop.
2. **`SuggestedRegionPrinter`** — inline in `main`.
3. **`DiscoveryResult`** — thin bag; fold later.
4. **Declines + `based_on_*` DDL** — unused by discover; defer.
5. **`ExprSchemaRepository`** — fold into suggested repo.
6. **Merge `SuggestedRegion` + `NewSuggestedRegion`** — one type, `Long id` null = new.

### Queries

7. Keyset page: `WHERE id > ? … ORDER BY id LIMIT ?` instead of `OFFSET`.
8. Bucket named/suggested by `wid`.
9. Optional: skip loading all sources into RAM; `INSERT OR IGNORE` + dirty on bounds only.

### Correctness when touching

- Load **pending-only** suggested before absorb.
- `ensure*` mutates any passed DB — copy-only.
- Absorb is greedy first-hit.

### Target shape

```text
expr/
  expr.java
  Discovery.java
  GrowingBox.java      // SuggestedRegion + NewSuggestedRegion
  ExprSql.java         // all SQL
  Aabb.java
```

## Run

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=me.fulcanelly.trust.me.expr.expr \
  -Dexec.classpathScope=compile
```

Pass DB path as arg, or put `database.sqlite3` / `.sqlite3` in project root. Treat as a **copy**.
