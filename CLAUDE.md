@AGENTS.md

- Inspect the relevant issue and package tree before multi-component changes.
- Prefer the smallest coherent diff.
- Do not introduce speculative abstractions.
- An issue states intent, not a design. Classes, services or DTOs it proposes are suggestions: re-decide each against the rules below instead of copying them. If the user says "simple" / "without complexity", that means the fewest new classes.

## Asking questions
- Doubt your own understanding. Ask when you are not sure you understood the idea correctly, when the request is genuinely ambiguous, or when a wrong guess risks a big change or bugs (edge cases count).
- A "dumb but important" question is fine. The aim is to confirm you got the idea right, not to hand decisions back.
- Prefer several small, plain questions (yes/no where possible) over one big one.
- Do not ask for the sake of asking: skip anything with an obvious default, or that the code or the issue already answers.
- Ask through the `AskUserQuestion` tool (a quiz the user clicks through), not as a text list in the reply: it is easier to read. Keep options short, ask in the language the user is currently writing in, and if a free-text "Other" answer is unclear, ask a follow-up quiz instead of guessing.

## How to write code
Readability > cleverness > theoretical purity. The package tree should explain what the plugin does.

- Organize by domain/component; keep nesting shallow. Add a subpackage only when it groups several related classes. Before adding a class or package, decide which domain owns it.
- `TrustSysBootstrap` is the composition root: explicit constructor injection. `TrustSysRuntime` owns cleanup of tasks/resources; it is not a service locator.
- Listeners and commands are thin adapters: normalize input, delegate, render the result. Keep core logic independent of message/config presentation.
- No abstraction without clear ownership. A strategy only for behavior that really has several implementations. Do not add a class just to shrink a constructor. Avoid generic `Manager`, `Utils`, `Common` and global mutable context.
- A new Telegram/Minecraft command reads the repositories directly and formats with `LocalizationService` (see `TrustStatsCommand`, `MyProfileCommand`). Add a service only when two or more entry points share the logic (`TrustDecisionService` serves Telegram callbacks and Minecraft commands). Add a message builder only when formatting has real logic (lists, regions, escaping), not for one template with a few placeholders.
- No DTO / value class that only carries values from one call straight into the next (a `Profile` of six ints built and then unpacked into six placeholders). Value classes are for domain data that several parts pass around or persist.
- Package map: `listener/` thin adapters; `service/core/` flows shared by several entry points; `service/text/` message builders; `service/activitybox/` activity box grouping; `database/repository/local/` SQL; `database/repository/model/` row/result models; `bootstrap/` wiring only. If a new class does not clearly fit one of these, ask instead of dropping it into `service/core/`.
- Use `@RequiredArgsConstructor` for constructors that only assign `final` fields; hand-write one only when it validates or computes something.
- Bukkit world/player mutations stay on the server thread. No blocking DB/file work in hot event handlers. Background work gets UUIDs/immutable values, not live Bukkit objects.
- Prefer moving/renaming existing code over building a parallel abstraction. Do not silently change behavior during a refactor. Keep the diff scoped to the issue.

### Comments
- Put a comment where a reader gets confused, not where you happened to be editing. Name the reader and the line where they stop and ask "why?", and put the comment there.
- A contract comment goes on the declaration; a "why this value here" comment goes on the call site. A bare literal argument (`null`, `false`, `0`) is almost always a call-site comment. If only one place fits, pick the confusing line. Move a comment instead of duplicating it.
- After adding a parameter that only some callers use, re-read every caller before calling the change done: does the line passing the neutral value explain itself? (Example: `SuspicionDetectionService.recordContainerAction` passes `null` material, so the "no material" comment belongs there, not on the shared `record(...)`.)

### Tests
- Write only necessary tests: the happy path plus edge cases that can actually break. No test per getter, delegation or trivial branch, none "for coverage".
- Tests mirror production packages. A test must be readable without opening production code; prefer explicit scenarios over clever helpers.
- Start each group of related tests with one small ASCII flow diagram of the behavior (not the implementation).
- Tests must not pin shipped `config.yml` defaults; use a hardcoded minimal fixture.

### Verify
- `mvn test`
- no new dependency cycle between top-level packages
- no orphan tasks/resources
