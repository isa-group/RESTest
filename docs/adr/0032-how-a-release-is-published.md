# ADR-0032: A release is a draft until its image and archives are checked, and RESTest starts through the archive's launcher

**Status:** Accepted, amended on 13, 23, 27 and 30 October 2026
**Date:** 2026-10-11

## Context

Until now RESTest was installed one way: clone the repository, build it with Maven, run `./restest`.
2.0.0 and 2.1.0 are GitHub Releases whose only file was the manual's PDF, which
[ADR-0031](0031-the-manual-is-markdown-only.md) has since dropped.
[ADR-0024](0024-the-competition-version.md) counted a container image and a distribution archive
published from the tag as part of what a closed version is, and left them to follow.

What they have to be:

- **One command, with nothing but Docker or Java.** A person, a build server or a coding agent
  should get a working RESTest from a URL, pinned to a version, with no account and no clone.
- **RESTest's exit codes, unchanged.** [ADR-0015](0015-command-line-contract.md) gives each number a
  meaning a build server acts on: `1` is "the API has faults", `3` is "nothing could be tested".
  Whatever stands between a person and Java - a launcher, a container - must hand the number on.
- **Both processors.** Continuous integration is `linux/amd64`; the laptops RESTest is developed on
  are `linux/arm64` when they run containers.
- **Something later installers can take as it is.** Homebrew, SDKMAN, or a package for a language's
  own installer would each want a file to download and a command to run.

How other tools that test REST APIs from an OpenAPI document are released, read in their
repositories on 11 October 2026:

- **Schemathesis** (commit `ca01bab8`): a signed tag starts one workflow, which publishes the GitHub
  Release at once, with notes taken from its `CHANGELOG.md`, beside the package and the images. For
  v4.30.1 the image jobs failed and were run again, and the images arrived about ten hours after the
  release that announced them (Actions run 37994644700). Its image runs as a user of its own, and
  its Docker guide asks for the folders a run writes to to be created first, for that reason.
- **EvoMaster** (v6.2.0, `efad6f2b`): a tag starts one workflow that publishes everything at once.
  Its tag v6.1.0 has an image and no GitHub Release (Actions run 28924054236), and the Python
  package published from it downloads a jar that does not exist. Its image runs as root.
- **WuppieFuzz** (v1.8.0, `06c96f26`): cargo-dist publishes the release as soon as the tag arrives,
  with a SHA-256 for every file.
- **Tcases** (`tcases-4.1.1`, `dbabf908`): a folder with launchers written by hand in its
  repository, `bin/tcases` and `bin/tcases.bat`, whose `.bat` files end on the `java` line and so
  return Java's exit code.
- **RESTest 1.x**: the releases 1.5.0 and 1.6.0 tell the reader to download a zip they do not carry.

JReleaser, the tool usually reached for to package a Java program, was tried. Version 1.26.0 builds
the right folder, but its Windows launcher
(`META-INF/jreleaser/templates/java-archive/java-archive/bin/launcher.bat.tpl`) ends with `if
%ERRORLEVEL% equ 0 goto mainEnd` and then `exit /b 1`: every number but `0` becomes `1`, so a run
that could test nothing would read as one that found faults. Its Unix launcher answers `1` when
there is no Java.

## Decision

### 1. What a release carries

| What | Where |
|---|---|
| The container image | `ghcr.io/isa-group/restest:<v>`, for `linux/amd64` and `linux/arm64`, made of `:<v>-amd64` and `:<v>-arm64`; `:latest` is the highest version tagged |
| The archive for any machine with Java 21 or later | `restest-<v>.zip` and `restest-<v>.tgz` on the GitHub Release, both unpacking into one folder `restest-<v>/` with `bin/restest`, `bin/restest.cmd`, `lib/`, `LICENSE`, `NOTICE` and `README.md` |
| Their checksums | `checksums_sha256.txt` on the GitHub Release, in the form `sha256sum` writes and checks |

The manual of a release is `docs/manual/` at its tag
([ADR-0031](0031-the-manual-is-markdown-only.md)).

### 2. A release is a draft until everything in it has been checked

A version tag starts `.github/workflows/release.yml`, which compiles RESTest, assembles the
archives, checks them on Linux, macOS and Windows, builds and checks the image on an `amd64` and on
an `arm64` machine, pushes the image, and leaves a **draft** release holding the archives and the
checksums. A person writes the notes in the draft and publishes it. `docs/ci.md` has the steps.

So a published release always holds every file its notes name. Every tool read above publishes the
moment its tag arrives, and three of them, RESTest 1.x included, left a release published without
what it announced.

The image is public from the tag, before the notes: `:latest` moves then, when no higher version is
tagged. Holding it back until the release is published would take a second workflow started by the
publication, which was considered and not built.

### 3. Nothing published is replaced, and a tag never moves

A tag is never moved or deleted; a release whose draft cannot be published as it is is put right in
the next patch. A run of the workflow for a tag that has run before replaces nothing: an image
already pushed is left as it is, a draft keeps the files it has and takes its checksums from them,
and a published release is not touched. When what failed is the workflow itself, the same release
is made by starting the workflow on `master` by hand with the tag; everything else it uses - the
`Dockerfile`, `dist/`, the check - is still the tag's, and a fault in those is put right in the next
patch.

### 4. The image

- **Built from the checkout, in two stages.** The first compiles RESTest with JDK 25 and assembles
  the archive's folder; the second copies that folder onto a Java 21 runtime. The image needs
  nothing but Docker to build, from any checkout, and holds what a release archive holds. The first
  stage runs on the building machine's own processor; the second only copies files.
- **Java 21**, the oldest release RESTest supports.
- **Built and checked on each processor, then pushed as it is.** Each of the two machines builds the
  image for its own processor, checks it, and pushes that very image; the registry then joins the
  two under one name, without building anything. The image a release names is made of the images
  that were checked.
- **Started through the archive's own launcher**, `ENTRYPOINT ["/opt/restest/bin/restest"]`, which
  ends with `exec java`: Java is the container's own process, so `docker stop` reaches it and the
  run stops as it does on Ctrl-C, answering `143`, as the M12.1b amendment of ADR-0015 asks.
- **As root, in `/work`.** A container running as root can write into any folder mounted at `/work`,
  whoever owns it; one running as a user of its own can write only into a folder that user may write
  to, which on Linux is seldom the folder of the person running it. On Linux, where files written as
  root then belong to root, the documentation passes `--user "$(id -u):$(id -g)"`; Docker without
  root, and Podman, map root to the person already.
- **Pinned by digest**, both base images, so that one commit always builds on the same Java. Moving
  them to a newer patch of Java is a step of every release while Dependabot is switched off.
- **Labelled with what it is**: its version, its commit and when that commit was made, set by the
  release workflow and empty in a build of any other checkout, rather than the base image's own,
  which name a version of Ubuntu. No provenance attestation, which registries show as an image for
  an unknown platform; that comes back with signatures, if they come.

### 5. The archive's launchers are the one place that knows how RESTest starts

`dist/bin/restest`, for macOS, Linux and Git Bash, and `dist/bin/restest.cmd`, for Windows, are
written by hand. They hold everything RESTest needs to start: the main class, the class path
`lib/*`, `--enable-native-access=ALL-UNNAMED`, and Java 21 or later. The image starts the first;
anything that installs RESTest later installs the archive and calls them, rather than saying the
same things again. `./restest` stays as the checkout's own, whose jars are elsewhere.

- They hand Java's exit code on unchanged. The Windows one runs every line on its own, so that the
  number is read after Java has run; uses no delayed expansion, which would remove every `!` from a
  key handed over with `--auth`; and passes the arguments on as they were typed.
- When there is no Java they can use - none, a `JAVA_HOME` with none in it, or one older than 21 -
  they say so and answer `127`, a number RESTest never answers with. An older Java would otherwise
  refuse to start RESTest and answer `1`. They read the line of `java -version` that gives the
  version, not its first line, which is a notice when `JAVA_TOOL_OPTIONS` is set.
- Their line endings are set by `.gitattributes` - LF for `bin/restest`, CRLF for `bin\restest.cmd`
  - and `dist/assemble.sh` copies them as they are.

### 6. One check for every way of starting RESTest

`.github/scripts/check-restest.sh` starts RESTest the way it is given - a launcher, an image, or a
checkout's `./restest` - and checks that it says which version it is, that a wrong option answers
`2`, and that a run against an address where nothing listens answers `3` and leaves its files. A
launcher and an image are also checked for answering `127` with no Java they can use, and an image
for answering `143` when `docker stop` ends a run. Every pull request runs it on the image and on
`./restest`; the release workflow on the archive's launchers on three operating systems, and on the
image on both processors before anything is pushed, and on the published image once more. A later
installer passes the same check.

### 7. 2.0.0 and 2.1.0 get no image and no archive

Neither tag holds a `Dockerfile` or a launcher. Making their images would mean building old code
with files from a later commit, adding files to releases already published, and a first run of the
workflow against a public release. 2.2.0 is the first release that carries them;
[ADR-0024](0024-the-competition-version.md) is amended to say so.

## Consequences

- RESTest can be run with one command and nothing but Docker - `docker run
  ghcr.io/isa-group/restest:<v>`
  - or nothing but Java, from 2.2.0 on. Building from the source stays as it was.
- A release is made with one tag and one publication, each by a person, with the steps around them
  that `docs/ci.md` lists: the base images moved while Dependabot is off, the version and the pages
  that name it prepared beforehand, the notes written, and the installation tried from outside.
- Sizes, measured at 2.1.1-SNAPSHOT: each archive is 34 MB; the image is 145 MB compressed on
  `amd64` and 143 MB on `arm64`, of which 32 MB is RESTest and the rest the Java runtime on Ubuntu.
- The registry shows two more tags per version, `<v>-amd64` and `<v>-arm64`, which the version's own
  tag is made of.
- The first image pushed creates the package `ghcr.io/isa-group/restest`, which GitHub makes
  private; an owner of the organisation makes it public, once.
- Between the tag and the publication, `:<v>` and `:latest` are public while the release is a draft.
- Users of 2.0.0 and 2.1.0 build them from the source, as before.
- The archive and the image hand on the libraries RESTest runs with, where until now Maven fetched
  them from their own sources. Half of the jars carry no licence text of their own, and some
  licences, such as MIT, ask that their notice travel with every copy. A file listing every library
  with its version and its licence, with the texts those licences ask for, goes into the archive and
  the image before the first release that carries them, 2.2.0.

## Alternatives considered

- **Publish the release as the tag arrives**, as every tool read above does. Simpler by one step; it
  is how releases came to be published without the files they named.
- **JReleaser.** Its launchers change the exit code, as above. With launchers of RESTest's own in
  its place, what was left for it to do was a zip and a tar. It remains the obvious way to publish
  to Homebrew or SDKMAN later, and it can take this archive as it is.
- **One executable jar**, as CATS and EvoMaster ship. ADR-0015 rejected a shaded jar, for a second
  copy of every class in a jar the architecture rules read; and two versions of the OpenAPI parser's
  module, and files of the same name in several libraries, would have to be merged into one.
- **A user of its own in the image**, as Schemathesis has. A folder mounted from Linux could then be
  written to only if made writable for that user first.
- **Both processors built at once with Buildx**, under emulation or by splitting one build
  afterwards. Either way the image checked is not, without more machinery, the image pushed.
- **Docker Hub as well as GitHub's registry.** It needs an account and a secret; GitHub's registry
  takes the workflow's own token. It can be added later without changing anything here.
- **A Java runtime cut down with `jlink`, or an image without a shell.** Smaller, but the launcher
  is a shell script, and RESTest's libraries are not all modules.
- **Images and archives for 2.0.0 and 2.1.0** (§7).

## Amendment (13 October 2026): the JSON Schemas

**Date:** 2026-10-13

The archive's folder also carries `schemas/`, with the JSON Schemas of a dictionary and a plan
([ADR-0035](0035-json-schemas-of-the-dictionary-and-the-plan.md)), and the image has them in
`/opt/restest/schemas/`; `check-restest.sh` checks that the image, and a launcher in a release's
folder, carry both.

## Amendment (23 October 2026): the libraries and their licences

**Date:** 2026-10-23

What the consequences above asked for before 2.2.0 is `dist/THIRD-PARTY.txt`, which `dist/assemble.sh`
puts beside `LICENSE` and `NOTICE` in the archive's folder, and so in the image's
`/opt/restest/`. It lists the 63 libraries in `lib/`, each under the licence it is used under - 49
under the Apache License, Version 2.0; 8 under that licence or the LGPL, used under the first; 3
under MIT; 2 under the Eclipse Distribution License; and Rhino under the Mozilla Public License,
Version 2.0 - and gives the texts that MIT, the Eclipse Distribution License, the Mozilla Public
License and the notices Rhino and SQLite JDBC carry ask to travel with every copy. The Apache
License's text is `LICENSE` itself.

It is written by hand, from the licences each library's own Maven description declares, and
`dist/assemble.sh` refuses to assemble a release whose `lib/` holds a library the file does not
list, or lacks one it does. So a dependency that is added, dropped or moved to another version
fails the image job of continuous integration until its line is written, and its licence read: a
new version of a library can change its licence, and no tool can say what a licence asks of a copy.
`check-restest.sh` checks that the image and a release's folder carry the file.

## Amendment (23 October 2026): the package was public from its first image

**Date:** 2026-10-23

The consequences above expected the first image to create `ghcr.io/isa-group/restest` as a private
package, for an owner of the organisation to make public. The first image, pushed for 2.2.0 by
`release.yml`, created it public, linked to this repository: on the day of the tag, before the
release was published, `docker pull ghcr.io/isa-group/restest:2.2.0` worked with no one signed in to
the registry, for `linux/amd64` and `linux/arm64`. So the step that made it public is gone from
`docs/ci.md`'s release steps; trying the installation from outside, with no one signed in, is still
one of them, and would show a package that was not public.

## Amendment (27 October 2026): Homebrew

**Date:** 2026-10-27

RESTest is also installed with Homebrew, from a tap of its own, by a formula that installs the
archive as it is and calls its launcher, as §5 asks ([ADR-0040](0040-homebrew.md)). Three things
above no longer hold as written. A workflow started by the publication, of the kind §2 considered
and did not build for the image, exists for the tap alone; the image is still public from the
tag. A tag does not freeze that formula, which is taken from the commit the
workflow runs on (§3). And §6's check has a fourth way of starting RESTest, `--own-java`, for a
command that brings its own Java. JReleaser, named above as the obvious way to publish to Homebrew,
writes a formula whose command finds no Java when Homebrew's is the only one; ADR-0040 says why it
was not used.

## Amendment (30 October 2026): Maven Central

**Date:** 2026-10-30

A release also carries RESTest's eight modules on Maven Central ([ADR-0044](0044-maven-central.md)).
§1's table gains a row: `es.us.isa.restest:restest-*:<v>`, each module with its jar, its sources,
its Javadoc and their signatures. §2's draft is made only once the Central Portal has validated what
the release uploads there, and a person now publishes twice - the deployment on the Portal first,
then the release - with an approval of the upload before either. The jars Central gets come from the
build the archives are assembled from, and are checked with them. §3 holds on Central too: a version
Central has is never uploaded again, and nothing published there is replaced. §5 is unchanged:
nothing on Central starts RESTest.
