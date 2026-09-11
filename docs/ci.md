# Continuous integration

What runs, how to reproduce it locally, and how to add a rule. The decisions behind it are in
ADR-0003 (Java versions) and ADR-0004 (module boundaries).

Triggers are pull requests against `master` or `v2`, pushes to those two branches, and manual
dispatch. A push to a `feat/*` branch with no open pull request runs nothing, which is deliberate: a
branch under active development would otherwise spend nine runners per commit.

## The matrix

`.github/workflows/ci.yml` builds nine rows: `ubuntu-latest`, `macos-latest` and `windows-latest`
× Java 21, 25 and 26. Every row runs the same command over the whole reactor:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

Three operating systems because path handling, line endings and file locking differ, and RESTest
reads and writes files on all of them. Three Java versions because 21 is the bytecode target we
promise library consumers, 25 is the long-term-support release the build toolchain uses, and 26 is
the current release — the one that tells us about a breakage before a user hits it.

`fail-fast` is off: one red row must not hide the state of the other eight.

The workflow sets `shell: bash` for every step. Windows runners default to PowerShell, which cannot
execute `mvnw` at all — it is a POSIX shell script with no extension, so `CreateProcess` rejects it.
Git Bash is on every runner image, so one default makes the same command work everywhere.
`.gitattributes` keeps `mvnw` checked out with LF endings, because a CRLF copy fails later and less
legibly, with `bad interpreter: /bin/sh^M`.

Every module targets Java 21, the CLI included, so every row builds and tests everything. There is
no toolchain configuration and no row that builds a different subset from the others. ADR-0003's
amendment explains why that beats the alternatives.

## Gates

| Gate | Where it lives | What it fails on |
|---|---|---|
| Compilation and unit tests | every module | the obvious |
| Java 21 bytecode | `PublishedBytecodeTest` | a class file compiled for a later release |
| Module boundaries | `ProductionArchitectureTest` | a dependency pointing outwards; a class in no module; `io.swagger` outside `restest-spec`; process termination outside `restest-cli` (`System.exit`, `Runtime.exit`/`halt`, this JVM's `ProcessHandle.destroy`, whether called or referenced); a reassignable static field; a network dependency in `restest-core` |
| The rules themselves | `ArchitectureRulesSelfTest` | a rule that no longer reports the violation it exists to report, or that reports something it should permit |
| The harness's reach | `HarnessCoverageTest` | a module whose compiled classes the rules cannot see, so no rule constrains them |
| Source-tree invariants | `SourceTreeRulesTest` | a module without `module-info.java`; a benchmark-platform reference under `src/` or in a POM; an action pinned to a tag, a SHA with no version comment, or no pins found at all |
| Coverage | JaCoCo | nothing yet — see below |

### Coverage

JaCoCo's agent is attached to every module's test run, and `report` runs at `verify`. A module only
produces `target/site/jacoco/` once it has tests: with no tests there is no execution data, so the
report goal skips and says so. At M0.2 that means only `restest-arch-tests` runs any tests, and it
has no main classes to attribute them to — so **no coverage report is produced yet at all**. The
wiring is in place; the output arrives with the first tested production code at M1.1.

The ubuntu / 25 row uploads whatever exists as the `jacoco-report` artifact, with
`if-no-files-found: warn`. Warn rather than ignore on purpose: a silent green upload of nothing
would hide both today's emptiness and a real break later.

There is deliberately **no** blocking threshold yet. At M0.2 no module contains production code, so
any minimum would be an assertion about zero classes. The `check` goal arrives in M1.6 on
`restest-core` and `restest-oracles`, the two modules `docs/DESIGN.md` names under "Quality
gates". The commitment
is recorded as `TODO(M1.6)` beside the JaCoCo block in the root POM.

### Why the rules are tested

The production modules hold nothing but `module-info.java` until M1.1, so there is nothing for the
rules to match. A rule with no subjects passes whether it is correct or broken, which is the failure
mode ADR-0004 was written to prevent, so three things guard against it.

First, no check reports a pass over an empty set. `ProductionArchitectureTest`, the bytecode scan
and the harness-coverage comparison each **skip, with the reason recorded against the skipped
test** in the surefire XML — not merely in a source comment, so it survives into any report that
reads the build. Today `./mvnw verify` says:

```
[WARNING] Tests run: 17, Failures: 0, Errors: 0, Skipped: 7
```

Seven skips, and the emptiness is on the face of the build rather than hidden behind it. Each skip
carries a `TODO(M1.1)` and removes itself the moment the first production class exists.

The skip is inside each test rather than in `@BeforeAll` — deliberately, and the difference is not
cosmetic. An assumption that fails during setup aborts the container, and surefire then records
`tests="0" skipped="0"` with the reason nowhere in the XML: five architecture checks would simply
cease to appear in any report, unexplained. Per-test skipping keeps all five visible and attaches
the reason to each.

Second, `ArchitectureRulesSelfTest` proves the rules independently of whether production code
exists. Every rule is pointed at `io.restest.arch.fixtures.mirror`, a miniature of the nine-module
layout whose classes break the rules on purpose, and asserted to fail *and name the offender*. Where
a rule permits something — `restest-cli` may call `System.exit`, and reaping a child process is not
terminating our own — that is asserted too, so each rule stays a boundary rather than becoming a
blanket ban.

Third, `HarnessCoverageTest` checks that the rules can see what they claim to govern. They read the
module jars off the test classpath, and nothing else verified that list was complete: trim classes
out of a jar and that module leaves every rule silently and permanently, with the build still green.
The test compares how many classes each module compiled into `target/classes` against how many the
rules actually imported. Counts, not presence — "at least one class arrived" would pass a module
that compiled five hundred and shipped one.

`allowEmptyShould(true)` remains on the individual production checks, for a reason that outlasts
M0.2: the modules fill in across different milestones, so a rule scoped to one of them legitimately
has an empty subject set for a while. What it must not excuse is every rule being empty at once, and
the skips above are what rule that out.

## Reproducing a row locally

Any row, given that JDK:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw --batch-mode verify
```

On macOS, `/usr/libexec/java_home -V` lists what is installed and `/usr/libexec/java_home -v 21`
prints the path. Just the rules, without rebuilding everything:

```bash
./mvnw --batch-mode verify -pl restest-arch-tests -am
```

There is no shortcut worth having here: `restest-arch-tests` depends on all nine modules, so `-am`
builds the whole reactor anyway. On this project the full build takes a few seconds; just run
`./mvnw verify`.

Worth knowing where the rules are actually looking. Under `verify` the reactor has reached
`package`, so what the rules read is each module's **jar**, not its `target/classes` — only
`mvn test` leaves them reading the directories. It matters from M1.1 on: anything excluded from a
jar leaves the architecture rules' field of view entirely. `HarnessCoverageTest` exists for that
reason — it compares what each module compiled on disk against what the rules actually imported,
and fails naming any module whose classes the rules cannot see.

Note the converse, which is the real trap: `./mvnw verify -pl restest-core` runs **no** architecture
checks at all while appearing to pass. They live in `restest-arch-tests`, and only a full-reactor
build exercises them.

## Adding a rule

Architecture rules go in `ArchitectureRules`, as a factory taking the root package it reasons about:

```java
static ArchRule myRule(String root) { ... }
```

The root parameter is not decoration. Rules that name module packages in their own text can only be
exercised against fixtures if the fixtures live under a parallel package tree, so production passes
`io.restest` and the self-test passes the mirror root.

Then, in the same commit:

1. A check in `ProductionArchitectureTest`.
2. A fixture under `io.restest.arch.fixtures.mirror` that breaks the rule on purpose.
3. An assertion in `ArchitectureRulesSelfTest` that the rule reports it. Where the rule permits
   something — `restest-cli` may call `System.exit` — assert that too, or the rule is a blanket ban
   rather than a boundary.

Invariants about text rather than bytecode — a file that must exist, a name that must not appear —
belong in `SourceTreeRulesTest` instead. ArchUnit cannot see a string that never became a class.

Note the scope of the benchmark-platform rule if you extend it: it reads the module source trees and
the POMs, and deliberately not `.github` or `evaluation/`. Both exclusions are load-bearing —
ADR-0011 puts the harness in `evaluation/`, and the nightly benchmark workflow at M1.8 will have to
invoke it by name, so scanning either would fail on sanctioned work.

## Dependency updates

`.github/dependabot.yml` opens weekly pull requests for Maven dependencies and for the workflow
actions. Maven updates are grouped, because nine modules share one version property per dependency
and ungrouped pull requests would all have to land together anyway.

Actions are pinned to commit SHAs, not tags. A tag can be repointed at any commit by whoever owns
the action; a SHA cannot. The version in the trailing comment is what lets Dependabot recognise the
pin and bump it, and what tells a human reader which release a SHA is.

`SourceTreeRulesTest` enforces all of that: it fails the build if a pin regresses to a tag, if a SHA
has no trailing version comment, or if no external pin is found at all — the last so the rule cannot
quietly stop matching. It scans every `.yml` under `.github`, so composite actions in
`.github/actions` are covered as well as the workflows.

## Known noise

`restest-arch-tests` has no main sources, so Maven prints `JAR will be empty` on every build.
Silencing it with `skipIfEmpty` would leave `mvn install` without an artifact to install, so the
warning stays.
