# ADR-0040: RESTest on Homebrew, from a tap of its own

**Status:** Accepted
**Date:** 2026-10-27

## Context

Since 2.2.0, RESTest is installed with Docker, or with Java 21 or later beside a download, an
unpack and a folder added to the `PATH` ([ADR-0032](0032-how-a-release-is-published.md)). Java is
the part people most often lack: a developer's Mac seldom has a Java 21, and a Linux machine often
has an older one. Of the tools that test REST APIs and are still maintained, none asks for a runtime
of its own: CATS and WuppieFuzz ship native programs, Schemathesis is Python, and EvoMaster's Python
package downloads a Java the first time it runs. Three of them are on Homebrew: CATS from a tap of
its own (`Endava/homebrew-tap`), WuppieFuzz in homebrew-core, and Schemathesis through a formula in
homebrew-core that its community keeps.

Homebrew, on macOS and Linux, installs a program and what it needs with one command, puts it on the
`PATH`, and moves it to a newer version with `brew upgrade`. It reads how from a **formula**, a short
Ruby file kept in a git repository it calls a **tap**. homebrew-core is the tap Homebrew comes with;
anybody may publish another, as a repository named `homebrew-<name>`.

ADR-0032 asked three things of a later installer: that it install the release archive as it is and
call its launcher (§5), and that it pass `check-restest.sh` (§6); and it supposed that JReleaser
would be "the obvious way to publish to Homebrew or SDKMAN later". It also considered a second
workflow started by a release's publication and did not build it (§2), and it froze, at a tag,
everything a release is made from (§3).

What Homebrew's own code says, read in Homebrew 7.0.11:

- A formula is installed only from a tap. One given as a path is refused unless Homebrew is in
  developer mode.
- `openjdk@21` is keg-only: Homebrew installs it but puts no `java` on the `PATH`. On macOS its Java
  is in `libexec/openjdk.jdk/Contents/Home`, on Linux in `libexec`.
- `Language::Java.java_home_env("21")` gives a formula the folder of that Java, and
  `write_env_script` writes a two-line script, `JAVA_HOME="…" exec "<target>" "$@"`, which hands the
  exit code on unchanged.
- Its `openjdk` was 27.0.1, a version RESTest's continuous integration, on Java 21, 25 and 26, does
  not test.

JReleaser's formula for a Java program (`templates/java-binary/brew/formula.rb.tpl`) puts the
program's folder in `libexec`, links its launcher into `bin` and sets no `JAVA_HOME`. With RESTest's
launcher and a keg-only Java, the command it installs answers 127 on a machine with no Java of its
own, and the formula's test, which runs `--version`, fails there for that reason.

## Decision

### 1. A tap of RESTest's own

`isa-group/homebrew-tap`, holding `Formula/restest.rb`. A person types
`brew install isa-group/tap/restest`, and Homebrew fetches the tap the first time.

### 2. The formula installs the archive as it is, with a Java 21 it brings

- It downloads the release's `restest-<v>.tgz`, checked against its SHA-256, and depends on
  `openjdk@21`. It puts the archive's folder in `libexec`, without `bin/restest.cmd`, and writes
  `bin/restest`, which sets `JAVA_HOME` to that Java and starts the archive's launcher. The launcher
  stays the one place that knows how RESTest starts.
- **`openjdk@21`**: the Java the image runs on, the oldest RESTest supports and one its continuous
  integration tests. `openjdk` moves to every new Java as it comes out, before RESTest is tested on
  it.
- **`JAVA_HOME` is set, not offered as a default** (`java_home_env`, not
  `overridable_java_home_env`). A `JAVA_HOME` naming a Java 17, which many developers have, would
  otherwise make the launcher refuse to start with 127, although Homebrew installed a Java 21 beside
  it. The command Homebrew installs ignores `JAVA_HOME`; whoever wants RESTest on another Java uses
  the archive.
- Its test, which `brew test` runs, checks the version it says, and that a run against an address
  where nothing listens answers 3 and leaves its report.
- A tap holds one version, the latest. The documentation says to pin a version with the image or the
  archive.

### 3. The formula is kept in RESTest, and filled in for each archive

`dist/homebrew/restest.rb` is the formula with `@URL@` and `@SHA256@` in place of the archive's
address and checksum, and `dist/homebrew/formula.sh` fills them in, refusing an address or a
checksum that is not one. The archive does not carry it.

The formula and the scripts that check it are those of the commit a workflow runs on; the archive is
the release's. A tag freezes the image and the archives it releases (ADR-0032 §3), not the formula,
which a later commit can correct for a release already published - as long as that commit's
`check-restest.sh` still passes on the release, since the same run checks it. ADR-0032 §7 refused to put files
from a later commit over an older release; nothing here touches a release, since the formula only
points at its archive. So a release made before this record, 2.2.0, reaches the tap when the
workflow below is started by hand from `master`.

### 4. Checked before a release is drafted, and again before the tap is written

- `check-restest.sh` gains `--own-java`, for a command that starts RESTest on a Java of its own
  whatever Java the machine has. It is checked for what every way of starting RESTest is checked
  for, for starting through a symbolic link, and for starting - rather than answering 127 - with no
  Java on the `PATH` and a `JAVA_HOME` that holds none. The mode is named for that difference, not
  for Homebrew: an installer that brings no Java is checked as a launcher.
- `.github/scripts/check-homebrew.sh` installs a formula from a local tap, runs its test, and checks
  it with `brew style`, or with `brew audit --strict --online` when asked. It then compares what was
  installed with the archive Homebrew downloaded - every file but `bin/restest.cmd`, the JSON Schemas
  and `THIRD-PARTY.txt` among them, and nothing more - and runs `check-restest.sh --own-java` on the
  command. Homebrew moves `LICENSE`, `NOTICE` and `README.md` from `libexec` to the top of what it
  installs, as it does for every formula, and the comparison allows for that. The script refuses to
  run where RESTest is installed with Homebrew already, removes only what it installed, leaving the
  Java and what that needs, and turns Homebrew's developer mode off again where it was off, since
  `brew test`, `brew style` and `brew audit` turn it on for good.
- `release.yml` runs it on macOS and Linux for the formula filled in with the archive just built,
  and drafts the release only once it passes, as for the archives and the image.

### 5. Written into the tap once the release is published

Homebrew cannot download a draft's files, so the formula goes into the tap from
`.github/workflows/homebrew.yml`, started by a release's publication, or by hand with a tag. A
workflow of the kind ADR-0032 §2 considered for the image, started by the publication, now exists for
the tap alone; the image is still public from the tag. In order, it:

1. downloads the release's `checksums_sha256.txt` with no token, as anybody's Homebrew downloads
   the archive, and fills in the formula with the archive's address and its checksum from there;
2. checks that formula with `brew audit --strict --online`, and installs and checks it on macOS and
   Linux - Homebrew downloading the archive and checking it against that checksum;
3. writes it into the tap - never over a higher version, and over the same version only when the
   formula changed, which is how a fix to it reaches the tap. A formula's `revision`, which makes
   Homebrew install a version again where it is installed already, is raised by hand when what the
   formula installs changes;
4. installs RESTest from the tap and checks it once more.

A pull request that touches the formula or its scripts runs the first two steps against the latest
published release, and writes nothing.

The tap is written with a **deploy key**, which can write to that repository and no other and
belongs to no person. It is kept in an environment of RESTest's, `homebrew-tap`, which only `master`
and version tags may use, so a workflow pushed to another branch cannot read it. Where an
organisation forbids deploy keys, a GitHub App does the same with no person behind it.

## Consequences

- On macOS and Linux, `brew install isa-group/tap/restest` installs RESTest and the Java it runs on,
  and `brew upgrade` moves it to the latest release. Homebrew does not serve Windows. With every
  formula they need, a first installation kept 625 MB on macOS and 823 MB on Ubuntu 24.04 in the
  continuous integration of the pull request that brought this record, and 1,506 MB in Homebrew's own
  Linux image, whose C library is older than Homebrew's: there Homebrew brings its own, and a
  compiler with it.
- The formula reaches the tap some minutes after the release is published, and not at all when its
  check fails: the tap stays as it was until the workflow is started again by hand. `release.yml`
  checked the formula with `brew style` and a `file://` address before the draft; `homebrew.yml`
  checks it with `brew audit --strict --online` and the release's own address, which reads the
  project's page and the release on GitHub, and applies any rule a newer Homebrew has added. A
  failure there can be the formula's, as well as Homebrew's or the network's.
- Publishing a release now also writes the tap. The release steps in `docs/ci.md` say so, and what
  the workflow tries from outside.
- An owner of the organisation sets it up once: the tap, its deploy key, and the environment that
  holds the key.
- A tag no longer freezes everything a release is installed from: the formula of a release already
  published can change.

## Alternatives considered

- **homebrew-core**, where the command would be `brew install restest` and Homebrew's own bot would
  follow each release. Its maintainers review a formula and set its terms - they prefer `openjdk` to
  a versioned Java - and take a project that submits itself once it has 225 stars on GitHub, 90 forks
  or 90 watchers; RESTest had 232 stars, 34 forks and 15 watchers on 27 October 2026. A tap is under
  RESTest's own control and checked in its own continuous integration, and the same formula would
  serve in homebrew-core.
- **JReleaser's formula**, for the reason in the context: a command that answers 127 on a machine
  with no Java of its own. A formula of RESTest's own is some twenty lines.
- **Written into the tap by hand** at each release, with no workflow and no secret: one more step of
  every release that can be forgotten, and a check run on a person's machine.
- **`openjdk`, and a `JAVA_HOME` the person may override**: §2.
- **The formula kept in the tap alone**: it would be checked only once a release exists, never in a
  pull request of RESTest's.
- **A personal access token** in place of the deploy key: it ties the tap to one person, and expires.
