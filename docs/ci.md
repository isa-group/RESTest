# Continuous integration

What runs, how to reproduce it locally, and how to add a rule. The decisions behind it are in
ADR-0003 (Java versions) and ADR-0004 (module boundaries).

Triggers are pull requests against `master` or `v2`, pushes to those two branches, and manual
dispatch. A push to a `feat/*` branch with no open pull request runs nothing, which is deliberate: a
branch under active development would otherwise spend nine runners per commit.

## The matrix

`.github/workflows/ci.yml` has two jobs. The first builds nine rows: `ubuntu-latest`,
`macos-latest` and `windows-latest` × Java 21, 25 and 26. Every row runs the same command over the
whole reactor:

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

### The smoke job

The second job, added at M1.7, runs the tool itself — `restest run` — against two open-source APIs
in containers, runs it once more inside a plain Java 21 runtime (M1.11), and then checks that the
launcher starts:

```bash
./mvnw --batch-mode --no-transfer-progress verify -Psmoke
./restest --version
```

It is its own job rather than a tenth row of the matrix, for two reasons. It needs a Linux container
runtime, which only the `ubuntu-latest` runners have — the macOS images ship no container runtime at
all and the Windows ones run Windows containers — so nine rows would mean six permanent skips, and a
build full of routine skips is a build nobody reads. And whether the tool still works end to end is a
different question from whether it compiles everywhere, so the two get separate answers on the pull
request rather than sharing one green tick.

Asking for `-Psmoke` is asking for the containers: the profile sets `restest.smoke.required`, and
with that property set a machine with no container runtime **fails** rather than skipping. A gate
that reports success because it never ran is worse than no gate, because it looks like one.

The two images are pinned by content rather than by name (`webfuzzing/wfd-swagger-petstore` and
`webfuzzing/wfd-scout-api`, each by digest). Both are published for two kinds of processor, which
matters because continuous integration is `linux/amd64` and the maintainer's laptop is
`linux/arm64`; the other names those images are published under are built for one of the two only.
Pinning by digest also means the API under test is the same one next month, so a change in the
numbers is a change in RESTest.

Neither API is asked about a description kept in this repository. Each is asked for its own, over
HTTP, as it runs — so the description and the software can never drift apart, and the run exercises
reading a document from a web address, which nothing else does.

## Gates

| Gate | Where it lives | What it fails on |
|---|---|---|
| Compilation and unit tests | every module | the obvious |
| Java 21 bytecode | `PublishedBytecodeTest` | a class file compiled for a later release; a published jar that does not say which version of RESTest it is |
| Module boundaries | `ProductionArchitectureTest` | a dependency pointing outwards; a class in no module; `io.swagger` outside `restest-spec`; the schema validator outside `restest-oracles`; the JSON library outside `restest-core`; the database driver outside `restest-store`; process termination outside `restest-cli` (`System.exit`, `Runtime.exit`/`halt`, this JVM's `ProcessHandle.destroy`, whether called or referenced); a reassignable static field; a network dependency in `restest-core` |
| The rules themselves | `ArchitectureRulesSelfTest`, and for the source-tree rules the self-tests in `SourceTreeRulesTest` | a rule that no longer reports the violation it exists to report, or that reports something it should permit |
| The harness's reach | `HarnessCoverageTest` | a module whose compiled classes the rules cannot see, so no rule constrains them |
| Source-tree invariants | `SourceTreeRulesTest` | a module without `module-info.java`; a benchmark-platform reference in any file of this repository, or in the name of one, outside the five documents the rule names; one of those five no longer needing to be named there; an action pinned to a tag, a SHA with no version comment, or no pins found at all |
| Coverage | JaCoCo | `restest-core` or `restest-oracles` falling below 90% of lines or 85% of branches; measured everywhere else without blocking |
| Mutation score | PIT | run on demand, not in the build: `restest-oracles` scoring below 85% |
| End-to-end smoke run | `SmokeRunTest`, in the `smoke` job | the command failing against two real containerised APIs, or answering 2, 3 or 4 rather than "ran, and here is what I found". Runs only where a container runtime exists, and fails rather than skips when it was asked for |
| Runs on a plain runtime | `StockJreRunTest`, in the `smoke` job | the tool reaching for a part of Java that a runtime carrying only the compulsory modules does not have, which stops it before its first request; and a seed meaning a different run there than it does here. Pinned to Java 21, the oldest release supported and one of the two where the generators RESTest once named are optional; the gate refuses to run on an image where they are not |

### Coverage

JaCoCo's agent is attached to every module's test run, and `report` runs at `verify`. A module only
produces `target/site/jacoco/` once it has tests: with no tests there is no execution data, so the
report goal skips and says so. Since M1.1a `restest-core` has both, so an HTML report is written
under its `target/site/jacoco/` on every build — the first coverage output the project has had.

The ubuntu / 25 row uploads whatever exists as the `jacoco-report` artifact, with
`if-no-files-found: warn`. Warn rather than ignore on purpose: a silent green upload of nothing
would hide both an empty build and a real break later.

Since M1.6 there is a blocking threshold, on the two modules `docs/DESIGN.md` names under "Quality
gates" and on no others: **90% of lines and 85% of branches**, enforced by the `check` goal
configured in `restest-core/pom.xml` and `restest-oracles/pom.xml`. Both clear it with
room to spare — `restest-core` 96% of lines and 94% of branches, `restest-oracles` 97% and 95%, when
the gate was set — so the figures are floors that fail on a real loss of testing rather than on
rounding. They are never lowered to make a build pass.

The gate is off when tests are: `-DskipTests` and `-Dmaven.test.skip` each activate a profile that
turns coverage off, because "nothing was covered" is not news when nothing was run. Continuous
integration never skips tests.

### Mutation score

`restest-oracles` is mutation-tested, which is a different question from coverage: it corrupts the
module's own code - flips a comparison, removes a call - and fails if the tests still pass. Coverage
says the tests executed a line; this says they would have noticed if the line were wrong. "Our tool
finds bugs in your API" is a claim worth more when the code making it has been shown to notice bugs
in itself.

```
./mvnw org.pitest:pitest-maven:mutationCoverage -pl restest-oracles
```

It is not bound to a build phase: it re-runs the tests once per mutation, so it costs minutes where
the rest of the build costs seconds. The threshold is **85%**, against 98% measured when it was set.

Three survive, and they are named here rather than called equivalent, which they are not quite. All
three are a `<` moved to a `<=` inside the schema oracle: how many pointers it will follow before
deciding a document points at itself, where a character stops being one it can write into a web
address unchanged, and whether a media type carries a semicolon at all. Both sides of each behave
the same way for every document anyone would write, and separating them would mean a test built
around the exact number, which would assert the number rather than the behaviour.

### Why the rules are tested

A rule with no subjects passes whether it is correct or broken, which is the failure mode ADR-0004
was written to prevent. Until M1.1a the production modules held nothing but `module-info.java`, so
that was the state of every rule here, and three things guard against it.

First, no check reports a pass over an empty set. `ProductionArchitectureTest`, the bytecode scan
and the harness-coverage comparison each skipped, **with the reason recorded against the skipped
test** in the surefire XML rather than only in a source comment, so the emptiness was on the face of
the build rather than hidden behind it. Since the domain model arrived those three read real
classes, the skips are gone, and what stood in their place is now an assertion: an empty scan means
the reactor was not built, and says so. `./mvnw verify` says:

```
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
```

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
M0.2: the modules fill in across different milestones, so a rule scoped to one of them — the parser
confinement rule, now that `restest-core` holds classes and `restest-spec` does not yet —
legitimately has an empty subject set for a while. What it must not excuse is every rule being empty
at once, and since M1.1a that cannot happen quietly: the inward-dependency and no-network rules both
have subjects, and `HarnessCoverageTest` fails if a module ever leaves the import.

### One rule narrowed, on evidence

`noStaticMutableState` ignores *synthetic* fields since M1.1a. A `switch` over an enum declared in
another class file makes the compiler generate a lookup table nobody wrote, and the two compilers
this repository meets disagree about it. Both were checked on the same source:

| Compiler | What it emits | Final? |
|---|---|---|
| `javac 25 --release 21` | `static final int[] $SwitchMap$…` on a synthetic nested class | yes |
| Eclipse (JDT) | `private static volatile int[] $SWITCH_TABLE$…` on the enclosing class | **no** |

The build of record is Maven with `javac`, and it is green either way. The Eclipse compiler is not
hypothetical here: an IDE with the Java extension open on this repository writes its own class files
into the same `target/classes` the rules read, and that is exactly how the rule came to fire on
`io.restest.core.model.ParameterStyle` — naming a field that is in no source file and that no run
can reach.

Nothing is given up by the exclusion: `synthetic` is a modifier only a compiler can set, so every
static field written in Java source is still subject to the rule, and `GenWithStaticMutableState`
proves it still catches one. Be precise about what guards it, though: `GenSwitchingOverAnEnum` fails
without the exclusion **under a compiler that emits the non-final form**. On CI, which is `javac`
throughout, that assertion is trivially satisfied and guards nothing — the self-test says so in its
own failure message rather than implying a protection the nine CI rows do not provide.

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

The smoke job, given a running container runtime:

```bash
./mvnw verify -Psmoke
```

That runs **only** the container-backed tests. `-Pit` runs everything, those included. An ordinary
`./mvnw verify` runs neither: the tests carrying the `smoke` tag are excluded by default, so nobody
pays a container pull for a build they did not ask one for.

There is no shortcut worth having here: `restest-arch-tests` depends on all nine modules, so `-am`
builds the whole reactor anyway. On this project the full build takes a few seconds; just run
`./mvnw verify`.

Worth knowing where the rules are actually looking. Under `verify` the reactor has reached
`package`, so what the rules read is each module's **jar**, not its `target/classes` — only
`mvn test` leaves them reading the directories. It matters now that the modules hold code: anything excluded from a
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

Note the scope of the benchmark-platform rule if you extend it. It reads every file git tracks, the
build files, and every module's `src/` from disk whether git tracks it or not — so a file written and
not yet committed fails its author's own build rather than waiting for CI. A checkout with no history
at all, an unpacked source release, is walked instead; a checkout whose git cannot answer is not,
because its working directory holds things that are not part of the repository. The rule matches a
file's name as well as its contents, and permits the platform's name only in the documents its own
exemption list names. Adding a workflow, a script or a note that names the platform means adding it
to that list, in the same commit, with a reason. Both of its gates have a test of their own in the
same class, which is where to add one if you widen it again.

## Dependency updates

`.github/dependabot.yml` asks for weekly pull requests for Maven dependencies and for the workflow
actions, but **it is not in effect yet**. GitHub reads that file only from the repository's default
branch, which is `master`, and `master` stays untouched until v2.0 replaces it (roadmap row 12.5).
Until then nothing watches v2's dependencies: no update pull requests and no security alerts. Check
for updates by hand before a freeze instead:

```bash
./mvnw -q versions:display-dependency-updates versions:display-plugin-updates
```

When the file takes effect, the pairs that must move together need groups of their own:
`jackson-core` with `jackson-annotations`, and `swagger-parser` with `swagger-core`.

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

The first `-Psmoke` run on a machine downloads about 280 MB of container images: about 160 MB for
the two APIs — less than they add up to, because they share their base layers — and about 120 MB
more for the Java 21 runtime the plain-runtime gate uses. Afterwards the job is dominated by the two
ten-second runs rather than by the download; the plain-runtime gate adds about six seconds, most of
it spent copying the compiled class path into the container.
