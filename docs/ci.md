# Continuous integration

What runs, how to reproduce it locally, how to add a rule, and how a release is made. The decisions
behind it are in ADR-0003 (Java versions), ADR-0004 (module boundaries), ADR-0032 (releases),
ADR-0040 (Homebrew) and ADR-0044 (Maven Central).

Three workflows. `ci.yml` builds and tests every change; `release.yml` makes a release from a version
tag, uploading RESTest's modules to Maven Central with it, and rehearses it on a pull request that
touches how RESTest is packaged ([Releases](#releases)); `homebrew.yml` writes the Homebrew formula
into its tap once a release is published ([Homebrew](#homebrew)). `ci.yml`'s triggers are pull
requests against `master`, pushes to it, and manual dispatch. A push to a `feat/*` branch with no open pull request runs
nothing, which is deliberate: a branch under active development would otherwise spend nine runners
per commit.

## The matrix

`.github/workflows/ci.yml` has three jobs. The first builds nine rows: `ubuntu-latest`,
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

The second job runs the tool itself — `restest run` — against two open-source APIs
in containers, runs it inside a plain Java 21 runtime — once as it is, and once with an API
key in its environment — and then checks that the launcher of the checkout starts and answers with
RESTest's own exit codes:

```bash
./mvnw --batch-mode --no-transfer-progress verify -Psmoke
.github/scripts/check-restest.sh "$(dist/version.sh)" --checkout ./restest
```

`check-restest.sh` is the one check every way of starting RESTest passes: the checkout's launcher
here, the image in the job below, a release's archives and image in [`release.yml`](#releases). Its
header lists what it checks.

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

### The image job

The third job builds the container image from the checkout, as a release builds it, and checks it as
a release checks it:

```bash
docker build --provenance=false --tag restest:check .
.github/scripts/check-restest.sh "$(dist/version.sh)" --image restest:check
```

A job of its own beside the smoke job, rather than a step after it: the image compiles RESTest
itself, in its first stage, and needs nothing the smoke run built, so the two run at the same time.

## Gates

| Gate | Where it lives | What it fails on |
|---|---|---|
| Compilation and unit tests | every module | the obvious |
| Java 21 bytecode | `PublishedBytecodeTest` | a class file compiled for a later release; a published jar that does not say which version of RESTest it is |
| Module boundaries | `ProductionArchitectureTest` | a dependency pointing outwards; a class in no module; `io.swagger` outside `restest-spec`; the HTTP client outside `restest-exec`; the schema validator outside `restest-oracles`; the JSON library outside `restest-core`; the YAML reader outside `restest-core` and `restest-spec`; the regular-expression builder outside `restest-gen`; the command-line framework outside `restest-cli`; the database driver outside `restest-store`; process termination outside `restest-cli` (`System.exit`, `Runtime.exit`/`halt`, this JVM's `ProcessHandle.destroy`, whether called or referenced); the environment or the system properties read outside `restest-cli`; a random generator asked for by name; a reassignable static field; a network dependency in `restest-core` |
| The published JSON Schemas | `PublishedSchemasTest` | a schema and RESTest's reader disagreeing about a file: a whole example in `README.md` or `docs/`, the plan RESTest carries, or a file with one thing broken; a member or a word one allows and the other does not; a keyword the engines that hold a language model to a schema do not all accept; an `$id` that does not name the version being prepared |
| The rules themselves | `ArchitectureRulesSelfTest`, and for the source-tree rules the self-tests in `SourceTreeRulesTest` | a rule that no longer reports the violation it exists to report, or that reports something it should permit |
| The harness's reach | `HarnessCoverageTest` | a module whose compiled classes the rules cannot see, so no rule constrains them |
| Source-tree invariants | `SourceTreeRulesTest` | a module without `module-info.java`; a benchmark-platform reference in any file of this repository, or in the name of one, outside the documents the rule names; one of those no longer needing to be named there; an action pinned to a tag, a SHA with no version comment, or no pins found at all |
| Coverage | JaCoCo | `restest-core` or `restest-oracles` falling below 90% of lines or 85% of branches; measured everywhere else without blocking |
| Mutation score | PIT | `restest-oracles` scoring below 85%, on the `ubuntu-latest` / Java 25 row (`-Pmutation`) and whenever it is run by hand |
| What Maven Central gets | `dist/central.sh`, on the `ubuntu-latest` / Java 25 row and in `release.yml` | a module's Javadoc or sources that do not build; a file Central asks for missing, or a POM without a name; a module whose POM would name a library at another version than `restest-cli` runs with; a program depending on `restest-cli` getting any jar but those the release archive carries |
| End-to-end smoke run | `SmokeRunTest`, in the `smoke` job | the command failing against two real containerised APIs, or answering 2, 3 or 4 rather than "ran, and here is what I found". Runs only where a container runtime exists, and fails rather than skips when it was asked for |
| How RESTest starts | `check-restest.sh`, in the `smoke` and `image` jobs, in `release.yml` and, through `check-homebrew.sh`, in `homebrew.yml` | the image, a launcher or Homebrew's command failing to start, saying the wrong version, or answering with a number other than RESTest's own: `2` for a wrong option, `3` when nothing answers, `127` from a launcher or the image when there is no Java it can use — and anything but `0` from Homebrew's command, which brings its Java — and `143` when `docker stop` ends a run in the image; the image, or a release's folder, without the JSON Schemas of a dictionary and a plan, or without `THIRD-PARTY.txt`; Homebrew's formula failing `brew style` or, once a release is published, `brew audit --strict --online`, failing to install or its own test, or installing anything but the archive as it is |
| Documentation held to the tool | `DocumentedCommandLineTest`, `DocumentedSettingsTest`, `DocumentedSwitchesTest`, `DocumentedReportTest`, `DocumentedLinksTest` | a reference page that disagrees with the tool: the command line, its options and exit codes against the help; the settings template against `--print-settings`; a switch, a file of settings or a plan on the switches page the tool does not have or does not accept; a key of `report.json`, a kind of fault, its title, severity or advice, or a reason a reply was not checked, against what a run writes and the code; a relative link that leads nowhere |
| Runs on a plain runtime | `StockJreRunTest`, in the `smoke` job | the tool reaching for a part of Java that a runtime carrying only the compulsory modules does not have, which stops it before its first request; a seed meaning a different run there than it does here; and a key left in `RESTEST_AUTH` failing to reach an API that refuses every request without it, or being written into any file the run leaves behind — the one place the variable is read from a real environment rather than handed to the command by a test. Pinned to Java 21, the oldest release supported and one of the two where the generators RESTest once named are optional; the gate refuses to run on an image where they are not |

### Coverage

JaCoCo's agent is attached to every module's test run, and `report` runs at `verify`. A module only
produces `target/site/jacoco/` once it has tests: with no tests there is no execution data, so the
report goal skips and says so. `restest-core` has both, so an HTML report is written
under its `target/site/jacoco/` on every build.

The ubuntu / 25 row uploads whatever exists as the `jacoco-report` artifact, with
`if-no-files-found: warn`. Warn rather than ignore on purpose: a silent green upload of nothing
would hide both an empty build and a real break later.

There is a blocking threshold on the two modules `docs/DESIGN.md` names under "Quality
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

It re-runs the module's tests once per mutation, which takes about ten seconds, so one row of the
matrix - `ubuntu-latest` with Java 25 - runs it on every change, as a second step after the build:

```bash
./mvnw --batch-mode --no-transfer-progress -Pmutation verify -pl restest-oracles -am -Djacoco.skip=true
```

The `mutation` profile, in `restest-oracles/pom.xml`, binds the score to that module's `verify`;
the ordinary `./mvnw verify` does not run it, so a laptop pays for the score only when it asks. The
threshold is **85%**, against 98% measured when it was set and 97% (294 of 304) measured on
22 October 2026.

Seven survive. Four are equivalent: each replaces with `null` a value returned inside
`Optional.map`, in the rules for a status code and a content type the document does not declare, and
`Optional` turns that `null` into the same empty result. The other three are named here rather than
called equivalent, which they are not quite. All three are a `<` moved to a `<=` inside the schema
oracle: how many pointers it will follow before
deciding a document points at itself, where a character stops being one it can write into a web
address unchanged, and whether a media type carries a semicolon at all. Both sides of each behave
the same way for every document anyone would write, and separating them would mean a test built
around the exact number, which would assert the number rather than the behaviour.

### What Maven Central would get

The same row runs, after the build, what a release uploads to Maven Central
([ADR-0044](adr/0044-maven-central.md)), short of signing and uploading it:

```bash
./mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=true -Pcentral package -pl restest-cli -am
dist/central.sh target/central
```

The `central` profile, in the root `pom.xml`, adds each module's sources and Javadoc, its POM
flattened, and the list of libraries Maven resolved for it; `dist/central.sh` lays them out as
Central takes them and checks them, as the Gates table says. It takes about fifteen seconds. On
every pull request, rather than in `release.yml`'s rehearsal alone, because a Javadoc an ordinary
change breaks, or a library that moves to another version in the tree, would otherwise first be met
at a tag, which never moves. When it refuses for a library, the fix is a version pinned in the root
POM's `dependencyManagement`, as Guava, commons-codec and JetBrains annotations are, so that every
module and the archive agree again.

### Why the rules are tested

A rule with no subjects passes whether it is correct or broken, which is the failure mode ADR-0004
was written to prevent. While the production modules held nothing but `module-info.java`, that was
the state of every rule here, and three things guard against it.

First, no check reports a pass over an empty set. `ProductionArchitectureTest`, the bytecode scan
and the harness-coverage comparison each skipped, **with the reason recorded against the skipped
test** in the surefire XML rather than only in a source comment, so the emptiness was on the face of
the build rather than hidden behind it. Since the domain model arrived those three read real
classes, the skips are gone, and what stood in their place is now an assertion: an empty scan means
the reactor was not built, and says so. `./mvnw verify` says:

```
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
```

Second, `ArchitectureRulesSelfTest` proves the rules independently of whether production code
exists. Every rule is pointed at `io.restest.arch.fixtures.mirror`, a miniature of the modules'
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

`allowEmptyShould(true)` remains on the individual production checks, for a reason that
outlasts the first weeks: the modules fill in at different times, so a rule scoped to one of them — the parser
confinement rule, while `restest-core` held classes and `restest-spec` did not yet —
legitimately had an empty subject set for a while. What it must not excuse is every rule being empty
at once, and that cannot happen quietly: the inward-dependency and no-network rules both
have subjects, and `HarnessCoverageTest` fails if a module ever leaves the import.

### One rule narrowed, on evidence

`noStaticMutableState` ignores *synthetic* fields. A `switch` over an enum declared in
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
prints the path. Just the rules — the architecture and documentation tests, after building what
they read:

```bash
./mvnw -q verify -pl restest-arch-tests -am -Dtest='io.restest.arch.**' -Dsurefire.failIfNoSpecifiedTests=false
```

The smoke job, given a running container runtime:

```bash
./mvnw verify -Psmoke
```

That runs **only** the container-backed tests. `-Pit` runs everything, those included. An ordinary
`./mvnw verify` runs neither: the tests carrying the `smoke` tag are excluded by default, so nobody
pays a container pull for a build they did not ask one for.

The image and the launchers, as `ci.yml`'s smoke and image jobs and `release.yml` check them:

```bash
version=$(dist/version.sh)
docker build --tag restest .
.github/scripts/check-restest.sh "$version" --image restest
./mvnw -q package -DskipTests
dist/assemble.sh target/dist --archives
.github/scripts/check-restest.sh "$version" --launcher "target/dist/restest-$version/bin/restest"
.github/scripts/check-restest.sh "$version" --checkout ./restest
```

The Homebrew formula, as `release.yml` checks it, on a machine with Homebrew where RESTest is not
installed with it already. It installs `openjdk@21` and what that needs, which stay, as do Homebrew
updated and the Ruby gems `brew test` and `brew style` install; RESTest and the local tap it is
installed from are removed again, and developer mode is turned off again if it was off:

```bash
tgz="$PWD/target/dist/restest-$version.tgz"
dist/homebrew/formula.sh "file://$tgz" "$(shasum -a 256 "$tgz" | cut -d ' ' -f 1)" > restest.rb
.github/scripts/check-homebrew.sh restest.rb "$version"
```

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

## Releases

A release is made from a version tag, `vX.Y.Z`, on a commit of `master` whose `pom.xml` says `X.Y.Z`.
Pushing the tag starts `.github/workflows/release.yml`, which leaves everything the release carries
in a **draft**, and RESTest's modules on the Central Portal, validated and not published; the notes
are written in the draft, and a person publishes both. What a release carries, and why it is made
this way, is [ADR-0032](adr/0032-how-a-release-is-published.md); Maven Central's part is
[ADR-0044](adr/0044-maven-central.md).

| Job | Where | What it does |
|---|---|---|
| `archive` | Linux | Checks the tag against `pom.xml` and `master`, compiles RESTest with the `central` profile, assembles `restest-<v>.zip` and `restest-<v>.tgz` with `dist/assemble.sh`, and lays out what Maven Central gets with `dist/central.sh`, from the same build; on a pull request, rehearses its signing with a key made for that run |
| `check-archive` | Linux, macOS, Windows | Unpacks the archive into a folder with a space in its name and runs `check-restest.sh` on `bin/restest` — also through a symbolic link, also with Java 17 to be refused — and on Windows on `bin\restest.cmd`, with a `JAVA_HOME` that has a space in it |
| `check-homebrew` | Linux, macOS | Fills in the Homebrew formula, `dist/homebrew/restest.rb`, for the `.tgz` and runs `check-homebrew.sh` on it: `brew style`, installed from a local tap, its own test, the folder it installs compared with the archive, and `check-restest.sh --own-java` on the command it installs |
| `image` | an `amd64` and an `arm64` machine | Builds the image on each machine's own processor, runs `check-restest.sh` on it, and on a tag pushes that very image as `ghcr.io/isa-group/restest:<v>-amd64` or `-arm64` |
| `central` | Linux, on a tag only, once a maintainer approves it | Signs what Maven Central gets with `sign-for-central.sh` and uploads it with `upload-to-central.sh` to the Central Portal, which validates it and publishes nothing; leaves it alone when Central has the version already ([Maven Central](#maven-central)) |
| `publish` | Linux, on a tag only | Joins the two into `:<v>` in the registry, checks it as published, moves `:latest` when no higher version is tagged, and drafts the release with the archives and `checksums_sha256.txt` |

The order is what keeps a broken release from being seen: nothing is pushed before it has been
checked, the image a release names is made of the images that were checked, the release is drafted
only once the Homebrew formula for its archive installs and passes and the Central Portal has
validated what Maven Central gets, and the files appear only when the draft is published. A Portal
that is down holds back the draft, and the image's `:<v>` and `:latest` with it. A rerun of the same
tag never replaces anything: an image already pushed is left as it is, a draft keeps the files it
has, a version Maven Central has is not uploaded again, and a published release is not touched. A pull
request that changes `Dockerfile`, `.dockerignore`, `dist/`, `.github/scripts/`, the workflow,
`.gitattributes`, the root `pom.xml` or `restest-cli/pom.xml` rehearses every job but `central` and
`publish`, and pushes nothing.

When what failed is the workflow itself, `workflow_dispatch` makes the same release with the
workflow as it is on `master`: Actions → Release → Run workflow, with the tag. Everything else it
uses — the `Dockerfile`, `dist/`, `check-restest.sh` — is still the tag's, except in the `central`
job, which takes what it uploads from the `archive` job and checks out the workflow's own commit, so
that its signing and its upload are `master`'s. A tag is never moved or deleted; a fault in what the
tag holds, or a draft that cannot be published as it is, is put right in the next patch. When the
Portal refuses an upload for a reason of its own — a token it does not take, a key it cannot find on
the key servers — put that right where it is and run the failed `central` job again from the run's
page ("Re-run failed jobs"), which uploads the files of that same run. A tag made before Maven
Central was, v2.3.0 or older, holds no `dist/central.sh`, so `master`'s workflow can no longer make
its release again.

The steps of every release, in order, each taken by a maintainer:

1. **The base images.** While Dependabot is switched off, move the `Dockerfile`'s two digests to the
   newest patch of `eclipse-temurin:25-jdk` and `eclipse-temurin:21-jre`
   (`docker buildx imagetools inspect eclipse-temurin:21-jre` prints the digest).
2. **The pull request that prepares the release**: the version in every `pom.xml`; the version in the
   `$id` of both files in `schemas/`, which `PublishedSchemasTest` requires to equal it on a release;
   and the pages that name the version to install — the README's installation section and chapters 2
   and 9 of the manual — with output from the image and the archive built from that commit, and, for
   chapter 2's Homebrew section, what `check-homebrew` prints in that pull request's run of
   `release.yml`.
3. **The tag**, pushed once that pull request is merged; then, when the run asks, approve the
   `central` job (*Review deployments*).
4. **The draft**: its notes written and read.
5. **Maven Central**: on the Portal's [Deployments](https://central.sonatype.com/publishing/deployments)
   page, publish the deployment `RESTest <v> run <run>` the `central` job named on the run's page, and
   wait until the Portal says it is *Published*. Should two runs of the tag have left two validated
   deployments, publish the one of the run that drafted the release and drop the other.
6. **The release published**, once Maven Central has the version, since its notes may name it.
7. **From outside**: the README's installation commands, run against the published release with no
   one signed in to GitHub or to the registry, and
   `https://repo1.maven.org/maven2/es/us/isa/restest/restest-cli/<v>/` answering. Publishing the
   release has started `homebrew.yml` (below), which installs RESTest from the tap on macOS and Linux
   once it has written the formula there; check that its run is green.
8. **The pull request that moves `master` on**: every `pom.xml` to the next version's snapshot, and
   the `$id` of both schemas to the next release, since the schemas on `master` name the release
   being prepared; `PublishedSchemasTest` requires them to be no older than that snapshot.

### Homebrew

`brew install isa-group/tap/restest` reads RESTest's formula from the repository
`isa-group/homebrew-tap`, which holds that one file, `Formula/restest.rb`. The formula is kept here,
as `dist/homebrew/restest.rb`, and `.github/workflows/homebrew.yml` writes it into the tap once a
release is published, since Homebrew cannot download a draft's files.
[ADR-0040](adr/0040-homebrew.md) says why it is done this way.

| Job | Where | What it does |
|---|---|---|
| `formula` | Linux | Checks the tag, downloads the release's `checksums_sha256.txt` with no token, and fills in the formula with the archive's address and checksum, with `dist/homebrew/formula.sh` |
| `check` | Linux, macOS | `check-homebrew.sh --online` on that formula |
| `push` | Linux | Writes it into the tap — never over a higher version, and over the same version only when the formula changed — with the deploy key of the environment `homebrew-tap` |
| `check-published` | Linux, macOS | Once `push` wrote something: `brew install isa-group/tap/restest`, as anybody would, and `check-restest.sh --own-java` on it |

The formula and the scripts are those of the commit the workflow runs on; the archive is the
release's. Starting it by hand — Actions → Homebrew → Run workflow, from `master`, with a tag — puts
a release already published in the tap, one made before the workflow existed included, and is how a
failed run is repeated. A pull request that changes the formula, its scripts or the workflow runs
`formula` and `check` against the latest published release, and writes nothing. Runs that may write
the tap wait for one another; GitHub keeps only one waiting, so three started within minutes of one
another cancel the middle one, which is started again by hand.

A tap holds one version. A fix to the formula of a version already in the tap reaches it by the next
run for that version; when the fix changes what is installed, raise the formula's `revision` in the
same commit, so that Homebrew installs it again where it is installed already.

**Set up once**, by an owner of the organisation, before the workflow first runs:

1. Create `isa-group/homebrew-tap`, public, with a README, so that it has a branch to write to.
2. Make an SSH key with no passphrase (`ssh-keygen -t ed25519 -N '' -f homebrew-tap`), and add its
   public half to that repository as a deploy key with write access.
3. In this repository's settings, create the environment `homebrew-tap`, whose deployment branches
   and tags are `master` and `v*` only, and add the private half to it — not to the repository's
   secrets — as `HOMEBREW_TAP_KEY`. Then delete both files. An environment the workflow names that
   does not exist yet is created without those limits, so it comes first.

Where the organisation does not allow deploy keys, a GitHub App installed on the tap alone does the
same. That changes `homebrew.yml`: a step that makes the App's token, and the tap's checkout given
`token:` in place of `ssh-key:`.

### Maven Central

RESTest's eight modules are published as `es.us.isa.restest:<module>:<v>` through Sonatype's Central
Portal, `central.sonatype.com`. Nothing published there can be changed or removed, so `release.yml`
only uploads them, and a person publishes them on the Portal ([ADR-0044](adr/0044-maven-central.md)).

| Step | Where | What it does |
|---|---|---|
| `dist/central.sh` | the `archive` job, and the `ubuntu-latest` / Java 25 row of `ci.yml` | Lays out, from the build the archives come from, `restest-cli` and every module it runs with, each with its jar, sources, Javadoc and POM and an `.md5` and a `.sha1` beside each, and checks them ([What Maven Central would get](#what-maven-central-would-get)) |
| `.github/scripts/sign-for-central.sh` | the `central` job, with RESTest's key; the `archive` job on a pull request, with a key made for that run | Signs every file but the checksums, checks each signature, and zips the result into the bundle the Portal takes |
| `.github/scripts/upload-to-central.sh` | the `central` job, and by hand for the trial upload below | Sends the bundle as `USER_MANAGED`, waits up to half an hour for the Portal to validate it, and fails, with the Portal's reasons, when the Portal refuses it; the job then writes the deployment's name, its id and each jar's SHA-256 on the run's page |

Each published POM names every library its module runs with, at the version the archive carries,
and nothing below them; no parent POM is published. ADR-0044 §3 says why.

**Set up once**, by the maintainer, before the first tag the workflow uploads for. None of the
secrets below is ever written into a file of this repository, an issue or a chat:

1. **The namespace.** An account of the Central Portal that can publish under `es.us.isa`, the
   namespace RESTest 1.x is published in: its *Namespaces* page lists it as verified. The account's
   token can publish under every namespace the account can - `es.us.isa` and the group's other
   ones - so name whose account it is.
2. **The signing key**, made for RESTest's releases alone, with GnuPG (`brew install gnupg` on a
   Mac). A primary key that only certifies, and a signing subkey, which is all GitHub holds: should
   that secret leak, the subkey is revoked and another one added, and RESTest's key keeps its
   fingerprint.

   ```bash
   gpg --quick-gen-key 'RESTest releases <address>' rsa4096 cert 5y
   ```

   ```bash
   gpg --quick-add-key <fingerprint> rsa4096 sign 2y
   ```

   ```bash
   gpg --list-keys --with-subkey-fingerprints <fingerprint>
   ```

   The first asks for a passphrase, which is `SIGNING_PASSPHRASE`, and prints the key's
   fingerprint, forty hexadecimal digits; the third shows the subkey's, under the line marked
   `[S]`. Then the public key goes to the two key servers the Portal checks signatures against, the
   second of which answers with a link to confirm the address:

   ```bash
   gpg --keyserver keyserver.ubuntu.com --send-keys <fingerprint>
   ```

   ```bash
   gpg --export <fingerprint> | curl -T - https://keys.openpgp.org
   ```

   What `SIGNING_KEY` holds is the subkey alone, the `!` naming exactly that one:

   ```bash
   gpg --armor --export-secret-subkeys '<subkey fingerprint>!' > restest-signing-subkey.asc
   ```

   That it holds the subkey alone shows only in a keyring that holds nothing else, as the job's
   does: imported there, `gpg --homedir <empty folder> --list-secret-keys` lists `sec#`, the `#`
   saying the primary's secret is not there. `gpg --show-keys` asks the machine's own keyring
   instead, which still holds the primary's secret, and lists `sec` either way.

   Keep `gpg --armor --export-secret-keys <fingerprint>` and the revocation certificate GnuPG wrote
   under `~/.gnupg/openpgp-revocs.d/` off any machine that runs builds, and remove the exported file
   once it is a secret. Before the subkey expires, `gpg --quick-set-expire <fingerprint> 2y
   '<subkey fingerprint>'`, send the key to both servers again, and replace `SIGNING_KEY` with the
   subkey exported again.
3. **The token.** On the Portal, the account's page, *Generate User Token*: a username and a
   password, which the Portal shows once, so it is made when it is about to be kept as the secrets
   `CENTRAL_USERNAME` and `CENTRAL_PASSWORD` below.
4. **The environment.** In this repository's settings, the environment `maven-central`: the
   maintainer as its required reviewer, the branch `master` and the tags `v*` - added as a tag rule,
   not a branch rule - as the only refs that may deploy to it, and the four secrets as its own, not
   the repository's. As for Homebrew's, an environment the workflow names that does not exist yet is
   created without those limits, so it comes first.
5. **Who may push a version tag.** A tag ruleset on `v*` that restricts creating, updating and
   deleting matching tags to the maintainers: a tag runs the tag's own `release.yml`, and the
   environment's limit to `v*` would not stop a tag pushed on a commit that never reached `master`.
6. **One trial upload**, with the scripts the `central` job runs and a version that will never be
   published, so that the first tag runs nothing that has not run before. In a clone of its own:

   ```bash
   ./mvnw -q versions:set -DnewVersion=0.0.0-trial -DgenerateBackupPoms=false
   ```

   ```bash
   ./mvnw -q package -Pcentral -DskipTests -pl restest-cli -am && dist/central.sh target/central
   ```

   The passphrase and the token's two halves are typed, or pasted, where nothing shows them:

   ```bash
   printf 'Passphrase: '; read -rs SIGNING_PASSPHRASE; echo; export SIGNING_PASSPHRASE
   ```

   ```bash
   SIGNING_KEY=$(cat restest-signing-subkey.asc) \
     .github/scripts/sign-for-central.sh target/central/maven-central trial.zip
   ```

   ```bash
   printf 'Token username: '; read -rs CENTRAL_USERNAME; echo; export CENTRAL_USERNAME
   ```

   ```bash
   printf 'Token password: '; read -rs CENTRAL_PASSWORD; echo; export CENTRAL_PASSWORD
   ```

   ```bash
   .github/scripts/upload-to-central.sh trial.zip 'RESTest trial'
   ```

   ```bash
   unset SIGNING_PASSPHRASE CENTRAL_USERNAME CENTRAL_PASSWORD
   ```

   Once it says the deployment is `VALIDATED`, **drop** it on the Portal's *Deployments* page, or
   read there why it failed. Never publish it.

## Dependency updates

`.github/dependabot.yml` describes weekly pull requests for Maven dependencies, for the workflow
actions and for the `Dockerfile`'s two base images, but **they are switched off for now**: each
entry's `open-pull-requests-limit` is `0`, which is how GitHub stops version updates while keeping
the configuration. Giving each entry its limit again switches them back on. GitHub reads that file
from the repository's default branch, `master`. The pairs that must move together have groups of
their own — `jackson-core` with `jackson-annotations`, and `swagger-parser` with `swagger-core` —
beside the test libraries and the build plugins. Until the updates are back, check what is out of
date by hand:

```bash
./mvnw -q versions:display-dependency-updates versions:display-plugin-updates
```

A library that a release carries and that is added, dropped or moved to another version also
changes a line of `dist/THIRD-PARTY.txt`, which lists each one with its licence: `dist/assemble.sh`
refuses to assemble a release until the list names exactly the jars in `restest-cli/target/lib`,
and says which differ, so the image job fails on any pull request that forgets it. Read the new
version's licence before writing its line; a new version can change it
([ADR-0032](adr/0032-how-a-release-is-published.md)).

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
ten-second runs rather than by the download; the plain-runtime gate adds about seventeen seconds,
most of it spent copying the compiled class path into the container and on the three-second run
handed a key.

The image job downloads the JDK the image is compiled with and everything Maven needs on every run,
inside the build: the runner's own Maven cache is not shared with Docker. A build on a laptop keeps
its own cache between builds.
