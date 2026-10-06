@AGENTS.md

- Inspect the relevant issue and package tree before multi-component changes.
- Prefer the smallest coherent diff.
- Do not introduce speculative abstractions.

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
- Use `@RequiredArgsConstructor` for constructors that only assign `final` fields; hand-write one only when it validates or computes something.
- Bukkit world/player mutations stay on the server thread. No blocking DB/file work in hot event handlers. Background work gets UUIDs/immutable values, not live Bukkit objects.
- Prefer moving/renaming existing code over building a parallel abstraction. Do not silently change behavior during a refactor. Keep the diff scoped to the issue.

### Tests
- Write only necessary tests: the happy path plus edge cases that can actually break. No test per getter, delegation or trivial branch, none "for coverage".
- Tests mirror production packages. A test must be readable without opening production code; prefer explicit scenarios over clever helpers.
- Start each group of related tests with one small ASCII flow diagram of the behavior (not the implementation).
- Tests must not pin shipped `config.yml` defaults; use a hardcoded minimal fixture.

### Verify
- `mvn test`
- no new dependency cycle between top-level packages
- no orphan tasks/resources
