# ADR-0004: Multi-module structure with dependencies pointing inwards

**Status:** Accepted
**Date:** 2026-09-11

## Context

RESTest 1.x is a single Maven module. The consequences are visible in the audit: `swagger-parser`
arrives undeclared and transitively, so the effective version depends on Maven's nearest-wins rule;
`TestGenerationAndExecution` is a 435-line near-copy of `RESTestLoader` that has silently drifted
from it; JUnit 4 is at compile scope and therefore part of the public surface; and there is no
boundary that could have prevented any of it.

Several v2.0 requirements are boundary statements rather than feature statements: "usable as a
library dependency", "no AI in the core", "no benchmark platform in the tool", "the parser is
replaceable". A boundary that is not compiled is not a boundary.

## Decision

Nine Maven modules, each with a `module-info.java`:

```
restest-core      domain model and interfaces. No network, no parser, no heavy dependencies.
restest-spec      the only module permitted to reference io.swagger.
restest-idl       IDL language, constraint model, solver interface.
restest-gen       generation phases, value providers, scheduler.
restest-exec      HTTP engine.
restest-store     interaction store.
restest-oracles   oracles and fault classification.
restest-report    event listeners producing output.
restest-cli       command line. The only module permitted to terminate the process.
```

Dependencies point inwards, towards `restest-core`. `restest-core` depends on nothing of ours.

The repository also contains `evaluation/`, which is **not** a Maven module (ADR-0011).

ArchUnit tests enforce: the dependency direction; that `io.swagger` appears only in `restest-spec`;
that no module has static mutable state; that `System.exit` appears only in `restest-cli`; and that
nothing in `src/` references a benchmark platform.

## Consequences

- A consumer can depend on `restest-core` plus the two or three modules they need, rather than on
  everything including a solver and a database driver.
- Replacing the parser, the HTTP client or the solver is a module-sized change with a compiler-checked
  blast radius.
- Nine modules is more ceremony than one: more POM files, a longer reactor build, and occasional
  friction when something genuinely belongs in two places.
- `module-info.java` is strict about split packages and reflective access. Most of that pain lands
  once, at increment 0.1, which is the cheapest possible moment.

## Alternatives considered

- **One module with package conventions.** Free, and worth exactly what it costs: 1.x had package
  conventions too.
- **Three modules (core / engine / cli).** Not enough separation to express "no AI in the core" or
  "only one module may see the parser" as compiled facts.

## Amendment (M0.2)

**Date:** 2026-09-11

The repository holds **nine production modules**, exactly as decided above, plus **one verification
module**, `restest-arch-tests`, which is not published.

### Why

This ADR ends by listing the rules ArchUnit must enforce. Writing them exposed a mechanical problem:
ArchUnit reads bytecode, so a rule such as "`io.swagger` appears only in `restest-spec`" can only
run somewhere whose classpath holds every module's classes at once. Inside `restest-core` that rule
would see one module and pass whether it is correct or broken — the vacuous green build this ADR
exists to prevent.

`restest-arch-tests` has no `src/main`. It depends on all nine modules at test scope, and nothing
depends on it, so it is a leaf of the dependency graph and cannot distort the architecture it
polices. `maven.deploy.skip` keeps it off Maven Central.

The alternative was `restest-cli/src/test/java`, whose classpath already sees the other eight. It
needed no new module and no amendment, but it would put project-wide rules inside the command-line
module, where nobody looks for them, and `./mvnw verify -pl restest-core` would run no architecture
checks at all while appearing to pass.

### Consequences

- The nine-module production structure, and the rule that dependencies point inwards, are unchanged.
- `restest-arch-tests` is exempt from "each module has a `module-info.java`" because it has no main
  sources. The exemption is named in `SourceTreeRulesTest` rather than implied.
- The module produces an empty jar, and Maven says so on every build. Suppressing it with
  `skipIfEmpty` would leave `mvn install` without an artifact to install, so the warning stays.
