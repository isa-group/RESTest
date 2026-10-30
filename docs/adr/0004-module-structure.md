# ADR-0004: Multi-module structure with dependencies pointing inwards

**Status:** Accepted, amended at M0.2 and on 14 and 30 October 2026
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

ArchUnit tests enforce: the dependency direction; that `io.swagger` appears only in `restest-spec`;
that no module has static mutable state; that `System.exit` appears only in `restest-cli`; and that
nothing in the repository references a benchmark platform (ADR-0011, amended at M1.9).

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

## Amendment (14 October 2026): `restest-idl` leaves the build until it holds code

**Date:** 2026-10-14

`restest-idl` held a `module-info.java` and nothing else. It was compiled, packaged as an empty jar,
copied into every release archive and image, depended on by `restest-gen` and `restest-cli`, and
listed in `AGENTS.md` as the home of "IDL language, constraints, solver interface" while nothing
reads IDL. A reader of the repository took the module for a feature; a user of 1.x, for whom
dependencies between parameters were the point, could find no word saying they are not read.

The module leaves the reactor, and the two dependencies on it go. Eight production modules, plus
the verification module. What does not change is the architecture: the layered rule keeps its
`Idl` layer, which `withOptionalLayers` already allowed to be empty, so the day a module holding
IDL comes back - with the first code that reads it, milestone M5 of the plan of 10 October 2026 -
its place in the dependency graph is the one decided above, enforced from its first class.
[ADR-0010](0010-idl-strategy.md) is accepted and not yet built, and says so; the known limitations
say dependencies between parameters are not read.

Considered and not taken: keeping the empty module. It costs a jar in every release and a line in
every list of modules, and it says something about the tool that is not true yet.

## Amendment (30 October 2026): what keeps `restest-arch-tests` off Maven Central

**Date:** 2026-10-30

The M0.2 amendment says that `maven.deploy.skip` keeps `restest-arch-tests` off Maven Central. Since
[ADR-0044](0044-maven-central.md) a release never runs `mvn deploy`: `dist/central.sh` lays out
`restest-cli` and the modules it runs with, and `restest-arch-tests`, on which nothing depends, is
not among them. `maven.deploy.skip` still stops `mvn deploy` from publishing it anywhere.
