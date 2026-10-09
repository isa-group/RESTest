# RESTest — rules for contributors and coding agents

Read by people and by coding agents alike, whichever tool they work with. It holds the facts and
rules that stay true whoever works on RESTest and however they work. How a change is proposed and
reviewed is in [`CONTRIBUTING.md`](CONTRIBUTING.md).

This file is also the contract between RESTest and any tool used to develop it: such a tool may rely
on the commands and the files named here, and on nothing else. Changing them is a change to RESTest,
made in a pull request ([ADR-0030](docs/adr/0030-the-process-lives-outside-the-repository.md)).

## What this project is

A complete rewrite of RESTest, a black-box testing tool for REST APIs. Input: an OpenAPI
specification. Output: generated and executed test cases, plus a report of the failures found.
Architecture and rationale: `docs/DESIGN.md`. Decisions: `docs/adr/`. How 2.0 was built: the tag
`history/2.0-development`.

**Design target:** an unknown API, no human configuration, a fixed time budget.

**Releases** are tagged `vX.Y.Z` on `master`. Semantic versioning: a fix is a patch, an addition a
minor version.

## Language

English only — code, comments, tests, commits, branches, issues, pull requests, documentation.
No exceptions.

## Javadoc comments

- Never reference `ADR-*`, `docs/DESIGN.md`, `docs/adr/`, a planning document or a work-item code
  (`M1.3`, `M4.2`...) from a Javadoc comment (`/** ... */`). Javadoc must be self-contained; the only
  references it may carry are `{@link}`s to other code. This does not apply to plain `//` or
  `/* */` comments, or to string literals such as `@DisplayName` or `.as(...)` rule descriptions.
- Keep Javadoc simple and avoid unnecessary technical jargon.
- At least the class-level comment must explain, in plain language a non-programmer could follow,
  what the class is used for when testing a REST API and how it relates to other classes.

## Branches and pull requests

- All work targets `master`. `v2`, where 2.0 was built, is frozen and tagged
  `history/2.0-development`; RESTest 1.x is on the `v1.x` branch and its tags.
- One change = one branch = one pull request, squash-merged into `master`.
- Every pull request uses `.github/PULL_REQUEST_TEMPLATE.md` and fills in every section;
  `CONTRIBUTING.md` says what the sections that matter most must contain, and how a pull request is
  reviewed.

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
9. The tool runs standalone. Evaluation harnesses live in a repository of their own, not in this one.
10. Apache-2.0, semantic versioning, published on every tag.

## Hard rules

- **No AI abstractions.** No `LlmProvider`, no `restest-ai` module, no dependency on any model
  library. "Ready for AI" means open formats, the generic `ExternalDataProvider` interface, and
  constraint and flow sources that can fire mid-run. See ADR-0008.
- **No evaluation platform in this repository.** The harness RESTest is measured with lives in a
  repository of its own, and nothing here builds against it, depends on it or names it — in a file's
  contents or in its name — outside the documents `SourceTreeRulesTest` lists; that list is the
  authority. See ADR-0011.
- **No one's private development tooling in this repository.** The maintainers' plan of what comes
  next, their own way of working beyond what `CONTRIBUTING.md` asks of every contributor, and the
  agents, skills, hooks and reviews of the work they use belong outside RESTest, and nothing here
  names them, their repositories or a path on somebody's machine. What stays is what every
  contributor shares: the conventions in `CONTRIBUTING.md`, the evidence about the product that ADRs
  and pull requests carry, and two files that any user of one coding agent reads — `CLAUDE.md`, which
  only imports this file, and `.claude/settings.json`, which only forbids a few dangerous commands.
  See ADR-0030.
- **Nothing from the deferred backlog** ("Out of scope for v2.0" in `docs/DESIGN.md`) without
  explicit approval, even if it looks easy. That list includes dependency inference,
  semantic-oracle inference, metamorphic relations, response classifiers and search-based
  scheduling.
- **Tuning numbers live in the settings, never in the campaign file, and every behaviour lever
  lands with a switch** (ADR-0025), so an experiment can turn it off without a code change. A
  constant that is a decision rather than a fact does not stay a constant when the code around it is
  touched.
- **Never weaken an architecture test, a coverage threshold or a mutation threshold to make a build
  pass.** If a rule is wrong, say so and propose changing it deliberately.
- **No code from RESTest 1.x is copied.** Ideas, the IDL grammar and the test corpus, yes. Source
  files, no. See ADR-0002.

## Technical baseline

- Every module compiles with `--release 21`, the CLI included (ADR-0003, amended at M0.2). The
  build toolchain is JDK 25. CI tests 21, 25 and 26 on Linux, macOS and Windows.
- No preview features in any published API — in particular no Structured Concurrency and no Lazy
  Constants.
- Maven with the wrapper (`./mvnw`). Every production module has a `module-info.java`.
- OpenAPI scope: 2.0 (by conversion), 3.0.x and 3.1.x, via a single swagger-parser backend. Not 3.2
  (too recent, adopted by almost nothing yet) and not 4.x, which has no specification text
  (ADR-0007, reversed at M1.2).
- Stack: swagger-parser (behind our own interface), networknt json-schema-validator (confined to
  `restest-oracles`, ADR-0014), rgxgen (confined to `restest-gen`, ADR-0022), picocli, OkHttp,
  virtual threads, ANTLR4, Choco, SQLite, JUnit 6, AssertJ, Testcontainers, WireMock, ArchUnit, PIT,
  JaCoCo.

## Module boundaries

```
restest-core      domain model + interfaces. No network, no OpenAPI parser, no heavy dependencies:
                  the two third-party libraries it carries are the streaming JSON reader and writer
                  and the YAML reader, both behind `io.restest.core.json` (ADR-0006, amended at
                  M1.6 and M11.1).
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
./mvnw -q verify -pl restest-arch-tests -am -Dtest='io.restest.arch.**' -Dsurefire.failIfNoSpecifiedTests=false
                                   # the architecture and documentation rules alone
./mvnw verify -Pit                 # everything, container tests included (needs Docker)
./mvnw verify -Psmoke              # just the smoke run against two containerised APIs (needs Docker)
./restest run <spec> --url <base> --budget 30s   # the tool itself, from this checkout
./restest version                  # which RESTest and which Java
./mvnw org.pitest:pitest-maven:mutationCoverage -pl restest-oracles
```

## Where things are

| What | Where |
|---|---|
| What the tool does, installing it, a first run | `README.md` |
| The manual, read from beginning to end | `docs/manual/` |
| One page per subject: command line, report, faults, plan, dictionaries, settings, switches | `docs/README.md` and the pages it lists |
| The command line and its exit codes, which a build checks against the tool | `docs/command-line.md` |
| What the tool does not do yet | `docs/known-limitations.md` |
| The documents the evaluation platform may be named in | `BENCHMARK_MAY_BE_NAMED_IN` in `restest-arch-tests/src/test/java/io/restest/arch/SourceTreeRulesTest.java` |
| Architecture, glossary, quality gates, what is out of scope | `docs/DESIGN.md` |
| Why the tool is shaped as it is, one decision per file | `docs/adr/` |
| Continuous integration and how to reproduce it | `docs/ci.md` |
| How a change is proposed and reviewed | `CONTRIBUTING.md` and `.github/PULL_REQUEST_TEMPLATE.md` |

## Working style

- Prefer the smallest change that makes a change demonstrable end to end.
- Write the test first when the behaviour is specified; write it alongside when exploring.
- When a design question has more than one defensible answer, write an ADR rather than deciding
  silently in the code.
- Say what you chose and why in the pull request's "Decisions taken" section.
- If a change turns out to be bigger than it looked, split it and say so — do not deliver half of it
  silently.
