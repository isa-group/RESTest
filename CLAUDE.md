# RESTest 2.0 — project memory

This file is loaded at the start of every Claude Code session in this repository.
Keep it under 200 lines. Facts and rules belong here; procedures belong in `docs/`.

## What this project is

A complete rewrite of RESTest, a black-box testing tool for REST APIs. Input: an OpenAPI
specification. Output: generated and executed test cases, plus a report of the failures found.
Architecture and rationale: `docs/DESIGN.md`. Decisions: `docs/adr/`.
Work breakdown: `ROADMAP.md`.

**Design target:** an unknown API, no human configuration, a fixed time budget.

## Language

English only — code, comments, tests, commits, branches, issues, pull requests, documentation.
No exceptions.

## Javadoc comments

- Never reference `ADR-*`, `docs/DESIGN.md`, `docs/adr/`, `ROADMAP.md` or a milestone code
  (`M1.3`, `M4.2`...) from a Javadoc comment (`/** ... */`). Javadoc must be self-contained; the
  only references it may carry are `{@link}`s to other code. This does not apply to plain `//` or
  `/* */` comments, or to string literals such as `@DisplayName` or `.as(...)` rule descriptions.
- Keep Javadoc simple and avoid unnecessary technical jargon.
- At least the class-level comment must explain, in plain language a non-programmer could follow,
  what the class is used for when testing a REST API and how it relates to other classes.

## Branching and increments

- `master` is untouched until v2.0 is complete. All work targets the long-lived `v2` branch.
- One increment = one branch = one pull request, named `feat/<milestone>-<n>-<slug>`,
  e.g. `feat/m1-2-specification-parser`.
- Squash-merge into `v2`. Never merge into `master`.
- Take the next increment from `ROADMAP.md`, in order, unless told otherwise.

## Pull requests

Use `.github/PULL_REQUEST_TEMPLATE.md` and fill in every section. The two that matter most:

- **"What you can do now that you could not before"** — concrete and user-facing. Not
  "added the ValueProvider chain" but "the tool now fills in dates and e-mails in a format real
  APIs accept, so operations that used to return 400 return 200".
- **"Try it yourself"** — copy-paste commands from a clean checkout, with the expected output.
  Must work on a laptop with only Java and git installed.

"How it works" is written for a reader who does not program in Java. Define any unavoidable term
inline or point at the glossary in `docs/DESIGN.md`. Titles say what became possible, not what
was edited.

## The ten design principles

1. Zero configuration to start; full configuration available.
2. Never crash on a bad specification — skip the offending operation and report it.
3. Interpret, don't generate. Test cases are data. Emitting JUnit, REST-Assured, curl or overlay
   documents is a *report*, never the execution path.
4. One event stream, many listeners.
5. Narrow interfaces, discovered implementations, enforced module boundaries.
6. No global mutable state. Two runs must coexist in one JVM.
7. The request loop is never blocked by computation; idle time is measured and reported.
8. Open formats in, open formats out.
9. The tool runs standalone. Evaluation harnesses live in `evaluation/`, outside the build.
10. Apache-2.0, semantic versioning, published on every tag.

## Hard rules

- **No AI abstractions.** No `LlmProvider`, no `restest-ai` module, no dependency on any model
  library. "Ready for AI" means open formats, the generic `ExternalDataProvider` interface, and
  constraint and flow sources that can fire mid-run. See ADR-0008.
- **No benchmark platform inside `src/`.** Searching `src/` for "restgym" must return zero hits.
  All of it lives in `evaluation/`, which is not a Maven module. See ADR-0011.
- **Nothing from the deferred backlog** ("Out of scope for v2.0" in `docs/DESIGN.md`) without
  explicit approval, even
  if it looks easy. That list includes dependency inference, semantic-oracle inference, metamorphic
  relations, response classifiers and search-based scheduling.
- **Never weaken an architecture test, a coverage threshold or a mutation threshold to make a build
  pass.** If a rule is wrong, say so and propose changing it deliberately.
- **No code from RESTest 1.x is copied.** Ideas, the IDL grammar and the test corpus, yes. Source
  files, no. See ADR-0002.
- **Stop and ask at supervision points** (marked in `ROADMAP.md`) instead of proceeding on
  assumption.

## Technical baseline

- Every module compiles with `--release 21`, the CLI included (ADR-0003, amended at M0.2). The
  build toolchain is JDK 25. CI tests 21, 25 and 26 on Linux, macOS and Windows.
- No preview features in any published API — in particular no Structured Concurrency and no Lazy
  Constants.
- Maven with the wrapper (`./mvnw`). Every production module has a `module-info.java`.
- OpenAPI scope: 2.0 (by conversion), 3.0.x and 3.1.x, via a single swagger-parser backend. Not 3.2
  (too recent, adopted by almost nothing yet) and not 4.x, which has no specification text
  (ADR-0007, reversed at M1.2).
- Stack: swagger-parser (behind our own interface), networknt json-schema-validator, picocli,
  OkHttp, virtual threads, ANTLR4, Choco, SQLite, JUnit 6, AssertJ, Testcontainers, WireMock,
  ArchUnit, PIT, JaCoCo.

## Module boundaries

```
restest-core      domain model + interfaces. No network, no parser, no heavy dependencies.
restest-spec      the only module allowed to reference io.swagger.
restest-idl       IDL language, constraints, solver interface.
restest-gen       generation phases, value providers, scheduler.
restest-exec      HTTP engine.
restest-store     interaction store.
restest-oracles   oracles and fault classification.
restest-report    event listeners producing output.
restest-cli       command line. The only module allowed to terminate the process.
```

Dependencies point inwards, towards `restest-core`. Architecture tests enforce this.

A tenth module, `restest-arch-tests`, holds those tests. It has no `src/main`, depends on all nine
at test scope and is never published — ArchUnit reads bytecode, so the rules can only run somewhere
that sees every module at once. See ADR-0004, Amendment (M0.2), and `docs/ci.md`.

## Commands

```bash
./mvnw verify                      # build and test everything
./mvnw -q verify -pl restest-core  # one module
./mvnw verify -Pit                 # integration tests (needs Docker)
./mvnw verify -Psmoke              # smoke run against two containerised APIs
./mvnw org.pitest:pitest-maven:mutationCoverage -pl restest-oracles
./evaluation/run-evaluation.sh --apis market,scs --budget 10 --repetitions 1
```

## Working style

- Prefer the smallest change that makes the increment demonstrable end to end.
- Write the test first when the behaviour is specified; write it alongside when exploring.
- When a design question has more than one defensible answer, write an ADR rather than deciding
  silently in the code.
- Say what you chose and why in the pull request's "Decisions taken" section.
- If an increment turns out to be bigger than it looked, split it and say so — do not deliver
  half of it silently.
