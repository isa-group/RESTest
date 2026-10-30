# ADR-0044: RESTest's modules on Maven Central, uploaded with a release and published by a person

**Status:** Accepted
**Date:** 2026-10-30

## Context

RESTest is installed with Docker, with the release archive and Java 21 or later, and with Homebrew
([ADR-0032](0032-how-a-release-is-published.md), [ADR-0040](0040-homebrew.md)). jbang, which
downloads a Java when there is none and runs on Windows without a shell of its own, runs a program
from one jar or from Maven coordinates, and resolves a program's libraries as Maven coordinates too
(`Alias.java`, jbang 0.142.0). It cannot read a release archive. What it can read is Maven Central.

**Maven Central** is the public repository Maven, Gradle and jbang fetch Java libraries from. It is
run by Sonatype, and published to through Sonatype's Central Portal, `central.sonatype.com`. What it
asks of every release, in its pages "Requirements" and "Publishing By Uploading a Bundle":

- beside every `<artifactId>-<version>.jar`, a `-sources.jar` and a `-javadoc.jar`;
- for every file, an `.md5` and a `.sha1`, and an `.asc` signature made with a key published on a
  public key server; signatures need no checksums;
- a POM with a name, a description, an address, the licences, the developers and the source
  repository, and a version that does not end in `-SNAPSHOT`;
- a groupId within a namespace the publisher has verified, which covers "any groupId starting with"
  it.

**Nothing published there can be changed or removed.** The Portal takes a bundle - a zip laid out as
a Maven repository - at `POST /api/v1/publisher/upload`. With `publishingType=USER_MANAGED` it
validates the bundle and keeps it, `VALIDATED`, until a person publishes it on the Portal or drops
it; `POST /api/v1/publisher/status` says which state a deployment is in, and why one `FAILED`.

**The coordinates.** [ADR-0002](0002-rewrite-not-refactor.md) chose `es.us.isa.restest:restest-*`;
the POMs had said `io.restest`, which nothing but the POMs reads. The ISA group holds `es.us.isa`,
where RESTest 1.x is `es.us.isa:restest`, 0.1.0 to 1.4.0, the last in November 2023. `io.restest`
would mean proving that `restest.io` is RESTest's, and it is not registered; `io.github.<name>` is
given to people, not to organisations. The Java packages can stay `io.restest`, as picocli's, OkHttp's
and SQLite JDBC's are on another domain than their groupId.

**How the related tools publish there**, read on 11 October 2026: EvoMaster publishes the libraries
its white-box drivers use, not the tool, by hand with `mvn -Pdeployment deploy`; Tcases publishes its
libraries and a shell distribution by hand with Sonatype's Maven plugin; Web Fuzzing Commons by hand
with `mvn -P release deploy`; RESTest 1.x published a jar holding every library, of 97 MB at first
and some 1.25 GB from 1.1.3, by hand. None publishes from a workflow.

**What a program depending on a module gets.** Maven, and jbang with it, fetches the libraries a POM
names and the ones those name in turn, settles a version on the nearest path, and applies only the
dependency management of the project being built - never that of a dependency's own POM. RESTest's
root POM pins Jackson 2's and 3's versions, among others, through its management, and those pins
reach no one else. Installed into a local repository of its own and fetched as any program would
fetch it, `restest-cli` brought 15 of its 63 libraries at other versions than the release archive
carries - Jackson 3.2.1, which the pin had replaced for its denial-of-service flaws, among them.
Three of the 63 the other way round: the archive carried Guava 33.5.0-jre, commons-codec 1.19.0 and
JetBrains annotations 17.0.0 only because two test libraries, WireMock and Testcontainers, sit nearer
in `restest-cli`'s tree than the OpenAPI parser that asks for older ones.

flatten-maven-plugin, which writes a POM that names every library with its version, left out of
`restest-cli`'s the five libraries Guava 33.5.0-jre needs, in version 1.8.0, so a program built from
it would have lacked classes Guava loads. Sonatype's own Maven plugin, `central-publishing-maven-plugin`
0.12.0, makes and uploads the bundle at `mvn deploy`, inside a build that holds the signing key and
the token, and makes no bundle of a `-SNAPSHOT` version, so a pull request cannot try it.

## Decision

### 1. The coordinates ADR-0002 chose

`es.us.isa.restest:<module>:<version>`, each module's name its artifactId: `restest-core`,
`restest-spec`, `restest-gen`, `restest-exec`, `restest-store`, `restest-oracles`, `restest-report`
and `restest-cli`. The Java packages stay `io.restest`, and the modules' names stay as their
`module-info.java` gives them.

### 2. The eight modules, and nothing else

Each with its jar, its sources and its Javadoc. What is published is `restest-cli` and the modules
it runs with, as `dist/central.sh` reads them from the libraries Maven resolved for it: all eight
today, since `restest-cli` needs the other seven. `restest-arch-tests` is never among them, since
nothing depends on it; the `maven.deploy.skip` its POM says ([ADR-0004](0004-module-structure.md))
stops `mvn deploy`, which a release never runs. No parent POM is published, because no published
POM has one (§3). The root POM's `central` profile makes the sources and the Javadoc, each jar saying
which RESTest it is as every published jar does, so that an ordinary build writes no Javadoc.

### 3. Each POM names exactly what the release archive carries

- **The root POM pins Guava, commons-codec and JetBrains annotations** at the versions the archive
  already carried, so that what it carries depends on nothing but what RESTest runs with. The
  archive is unchanged, jar for jar.
- **Each published POM is written from two files the `central` profile makes**: the module's POM
  flattened by flatten-maven-plugin, which gives the name, the address, the licence, the developers
  and the source repository with nothing inherited; and the list of libraries Maven resolved for the
  module, the list that fills `restest-cli`'s `target/lib`, written by maven-dependency-plugin.
  `dist/central.sh` puts the list in place of the dependencies the module declares, each with an
  exclusion of everything below it, so that nothing beyond the list is fetched. `restest-cli`'s POM
  names 70 libraries.
- **`dist/central.sh` refuses any difference.** Every module must name each library at the version
  `restest-cli` runs with: a module built on its own, without `restest-cli`'s test libraries, would
  otherwise name the older Guava, which is how the next library a test library decides is found.
  And Maven, fetching each module from a copy of the folder offline as a program depending on it
  would, must get the libraries the module's POM is written from - which also refuses a POM Maven
  cannot read, which Maven itself only warns about - and for `restest-cli` exactly the jars of
  `restest-cli/target/lib`, byte for byte. It refuses too when a file
  Central asks for is missing. `ci.yml` runs it on every pull request, so a difference is seen
  there, not at a tag, which never moves.

### 4. Laid out from the archive's build, and signed apart

`release.yml`'s `archive` job builds RESTest with the `central` profile, with the commit's own time
stamped into every jar so that a second run of the same tag builds the same jars, and runs
`dist/assemble.sh` and `dist/central.sh` on that one build: the jars Central gets are the jars the
next jobs check on Linux, macOS and Windows. A job of its own, `central`, signs them with
`.github/scripts/sign-for-central.sh` and uploads them with `.github/scripts/upload-to-central.sh`.
The signing key - a subkey made for signing RESTest's releases, whose primary key stays off any
machine that runs builds, so that a leaked secret costs the subkey and not the key's fingerprint -
and the Portal's token are kept in the environment `maven-central`, which only `master` and version
tags may use and which waits for a maintainer to approve the job; they never enter a Maven build. That job checks out its workflow's own commit, not the tag's, since what it uploads comes from
the archive job: a fault in the signing or the upload found at a tag is put right on `master`, and
the release made again by hand, rather than in the next patch. A pull request rehearses the signing
with a key made for that run.

### 5. Uploaded on a tag and validated; published by a person, before the release

`central` uploads as `USER_MANAGED`, named `RESTest <v> run <run>` after the workflow's run, waits
until the Portal has validated the deployment, and writes its name, its id and the SHA-256 of each
jar on the run's page; when the Portal refuses it, its reasons are shown there. The draft release, and
the image's `:<v>` and `:latest`, wait for it (ADR-0032 §2). A person publishes the deployment on the
Portal, waits until it is published, and only then publishes the GitHub release, so that a published
release never names what Central lacks. A run for a version Central already has uploads nothing;
when two runs of the same tag leave two validated deployments, the one kept is the one from the run
that drafted the release, and the other is dropped. ADR-0032 §3 holds on Central too.

### 6. What it promises

The promise of every 2.x version is what [`docs/command-line.md`](../command-line.md)'s "What frozen
means" lists - its commands, options and environment variables, its exit codes, and the names of the
files a run writes - and Java 21 bytecode ([ADR-0003](0003-java-baseline.md)). **The modules' Java
code is not part of it**: any version may change, add or remove a class, a method or a module. The
modules are on Central so that jbang can run RESTest, and so that a program can use one at a version
it names exactly.

## Consequences

- From the first release that carries this record, `es.us.isa.restest:restest-cli:<v>` and the seven
  other modules are on Maven Central. Each version adds some 4 MB of RESTest's own; the libraries are
  Central's already.
- A release has two more steps, each by a person: approving the `central` job, and publishing the
  deployment on the Portal before publishing the release. `docs/ci.md` lists them with the others.
- A Portal that is down holds back the draft, and with it the image's `:<v>` and `:latest`.
- The published POMs are long and unusual: each names every library its module runs with, at one
  version, excluding everything below. A program that uses a module gets those versions unless it
  names others itself, as it may.
- The root POM pins three more libraries, and a library that changes version in the tree fails
  `ci.yml` until the archive and the POMs agree again.
- Set up once, by the maintainer: access to `es.us.isa` on the Portal and a token, which reaches every
  namespace its account does; the signing key and its subkey, on public key servers; the
  environment, its secrets and its required reviewer; a rule on who may push a version tag; and one
  upload of a version never to be published, made by hand with the two scripts the job runs and
  dropped once validated, so that the first tag runs nothing that has not run before.
- On Central, `es/us/isa/restest/` is RESTest 1.x's folder, `es.us.isa:restest`, and the modules of
  `es.us.isa.restest` are folders inside it, beside 1.x's versions. Maven, Gradle and jbang ask for
  exact paths, so the two never meet, but a person browsing the folder sees both.
- Nothing published on Central is ever taken back: a fault there is put right in the next version.

## Alternatives considered

- **Sonatype's Maven plugin**, with maven-gpg-plugin, at `mvn deploy`: the usual way, and less shell of
  RESTest's own. The key and the token would sit inside a Maven build, with every plugin it runs; no
  pull request could make a bundle to try; and the jars would come from a build of their own.
- **Publication left to the Portal** (`AUTOMATIC`), **or made from a workflow started by the GitHub
  release's publication**, through the Portal's API: one step fewer for the person. Publishing on
  Central is the step that cannot be taken back, and it is taken by a person looking at the
  deployment, before a release names it.
- **The POMs as they are, with the parent published**: 15 of 63 libraries at other versions, as above.
- **The pinned libraries declared in each module that brings them**, the usual remedy for a library:
  POMs as Maven writes them, with about ten libraries declared in modules whose code does not use
  them, kept by hand.
- **flatten-maven-plugin naming every library itself**: it leaves out Guava's.
- **One jar holding every library**, as CATS, EvoMaster and RESTest 1.x ship: ADR-0015 and ADR-0032
  turned it down. Joining `restest-cli`'s jar and the 70 of its `lib/` into one means dropping 15
  `module-info.class`, merging nine licence and notice files and five service files - one of them how
  the OpenAPI parser reads OpenAPI 2.0, which RESTest would stop reading without saying so if it were
  lost - and keeping 23 jars' classes for particular Java versions. Such a jar is no use as a
  dependency of another program.
- **JReleaser's deployer**, which signs and uploads from a configuration of its own: one more tool for
  two steps that are a few lines of shell here.
- **A Maven repository of RESTest's own**, on GitHub Pages: unusual, and less trusted than Central.
- **`restest-cli` alone**: it needs the other seven.
