# ADR-0045: RESTest with jbang, from a catalog of the ISA group's own

**Status:** Accepted
**Date:** 2026-11-01

## Context

RESTest is installed with Docker, with the release archive and a Java 21 or later, with Homebrew on
macOS and Linux, and its modules are on Maven Central ([ADR-0032](0032-how-a-release-is-published.md),
[ADR-0040](0040-homebrew.md), [ADR-0044](0044-maven-central.md)). Nothing of that reaches a Windows
machine with no Java, and none of it is one command on all three systems.

**jbang** runs a Java program from its Maven coordinates, on macOS, Linux and Windows, and downloads a
Java first when the machine has none. What it runs can be named in a **catalog**: a file
`jbang-catalog.json` whose **aliases** each say which coordinates to run, which class to start, which
Java and which options to give it. `jbang <alias>@<organisation>` reads the catalog of the repository
`<organisation>/jbang-catalog` on GitHub, `jbang <alias>@<address>` the catalog at that address, and
`jbang app install` with either writes a command of the alias's name which runs it.

What jbang 0.142.0's own code says (its scripts in `src/main/scripts/`, its Java in
`src/main/java/dev/jbang/`, and devkitman 0.4.9, which finds and installs its Javas):

- An alias has `script-ref`, `main`, `java` and `runtime-options`, among others (`Alias.java`).
  `restest-cli`'s jar names no main class, so the alias has to.
- `restest@isa-group` first looks for a catalog named `isa-group` in any `jbang-catalog.json` of the
  current folder or a folder above it, and for a folder `isa-group/` there (`CatalogRef.getLocal`).
  Only then does it read `https://github.com/isa-group/jbang-catalog/blob/HEAD/jbang-catalog.json`,
  and when that answers with no catalog - before the catalog exists, or on a network that cannot reach
  GitHub - `gitlab.com/isa-group/jbang-catalog` and `bitbucket.org/isa-group/jbang-catalog` in turn
  (`ImplicitCatalogRef`). On 1 November 2026 no group `isa-group` existed on GitLab, so anybody could
  have made one, and a public workspace "ISA Group" had existed on Bitbucket since 2018, whose owner
  this record does not know.
- A catalog's address on github.com, gitlab.com or bitbucket.org, `raw.githubusercontent.com`
  included, is turned back into its organisation's name (`Catalog.simplifyRef`) and looked for the same
  way. Any other `https://` address is read as it is, with neither the local lookup nor the other hosts
  (`Util.isRemoteRef`).
- A catalog read from the network is kept for 12 hours (`cache-evict`, `PT12H`); `--fresh` reads it
  again. The libraries resolved for an alias are kept by the text of its coordinates
  (`DependencyCache`), so an alias naming a version range, or `RELEASE`, would run the first version a
  person ever resolved, for good.
- jbang's script starts jbang only on a JDK: it looks for `javac`, and with none downloads a JDK 17
  for itself. For a program asking `21+`, jbang uses any Java 21 or later it finds through
  `JAVA_HOME`, the `PATH` or its own folder, and with none downloads the newest.
- The first `jbang app install` puts jbang's `bin` on the `PATH` - in `~/.bashrc`, `~/.bash_profile`,
  `~/.zshrc` and fish's configuration, or in the user's `Path` on Windows - whenever that `bin` is not
  on the `PATH` already (`AppSetup.needsSetup`), and on a machine with no Java of its own also sets
  `JAVA_HOME` there to jbang's JDK 17 (`AppSetup.guessWithJava`).
- jbang's scripts hand on the number the program ends with: `jbang` with `eval "exec …"`, `jbang.cmd`
  with `exit /b %ERRORLEVEL%`, after turning delayed expansion off so that a `!` in an argument
  survives. PowerShell runs `jbang.ps1` and the `.ps1` of an installed command rather than the
  `.cmd`; it runs the program through `Invoke-Expression`. jbang's own failures end with numbers of
  its own: 1 when it cannot fetch a library, 2 when it can neither find nor install a Java, or finds no
  catalog of the name it was given, 3 when it cannot download a catalog it knows, 4 on an internal
  error; its script ends with curl's number when its own download fails.

What was measured on 1 November 2026, on a Mac with Apple silicon, with an alias naming
`es.us.isa.restest:restest-cli:2.4.0`, `io.restest.cli.Restest`, `--enable-native-access=ALL-UNNAMED`
and `21+`, against Maven Central:

- The class path jbang ran RESTest on was the 2.4.0 archive's `lib/`, 71 jars, byte for byte. The
  POMs [ADR-0044](0044-maven-central.md) publishes serve jbang as they are.
- `version` answered 0, a wrong option 2, and a run against an address where nothing listens 3 with
  its files: `check-restest.sh --own-java` passed every step but the one with no Java, whose `PATH`
  held five tools, among which neither jbang's script nor jbang itself could start.
- With no Java at all - no `JAVA_HOME`, no `java` or `javac` on the `PATH`, and a jbang that had never
  run - jbang downloaded a JDK 17 for itself, 309 MB, and the newest, Java 27, 296 MB, for RESTest,
  whose continuous integration tests 21, 25 and 26; asked for `21` instead, a Java 21, 335 MB. The
  jars took 37 MB. Each took some 20 seconds. With `JBANG_DEFAULT_JAVA_VERSION=21`, one JDK 21 served
  both.
- In a folder holding `isa-group/jbang-catalog.json` whose alias `restest` named a program of its
  own, `jbang restest@https://github.com/isa-group/jbang-catalog/blob/HEAD/jbang-catalog.json` ran
  that program; given the catalog at an address of another host, a local server, jbang ran RESTest.
- `restest version` took 0.32 seconds through the command `jbang app install` wrote, against 0.15
  through the archive's launcher, on the same Java 25, the median of ten each: jbang starts a Java of
  its own before RESTest's.

## Decision

### 1. A catalog of the ISA group's own, with one alias, read from GitHub Pages

`isa-group/jbang-catalog`, holding `jbang-catalog.json`, with the alias `restest`, and published by
GitHub Pages as `https://isa-group.github.io/jbang-catalog/jbang-catalog.json`. The documentation gives
that address: a person types
`jbang app install restest@https://isa-group.github.io/jbang-catalog/jbang-catalog.json` for the
command `restest`, or `jbang` and the same alias to run it once. jbang reads it as it is, so neither a
`jbang-catalog.json` in the folder RESTest is run from nor an account on another host can stand in
for it. `restest@isa-group` reaches the same catalog through the lookups above, and the documentation
says so; the plan named that form, and the maintainer chose it until the lookups were found, on
1 November 2026, the day of this record.

### 2. The alias names what the archive's launchers name

```json
{
  "script-ref": "es.us.isa.restest:restest-cli:<version>",
  "main": "io.restest.cli.Restest",
  "java": "21+",
  "runtime-options": ["--enable-native-access=ALL-UNNAMED"]
}
```

- **The coordinates of `restest-cli`** at one release's version, whose POM names every library it
  runs with ([ADR-0044](0044-maven-central.md) §3): the catalog names each release, since a range
  would not move anybody on.
- **The main class and the option** are the launchers' ([ADR-0032](0032-how-a-release-is-published.md)
  §5). Without the option, Java 24 and later print four warnings before RESTest sends anything.
- **`21+`**, what the launchers accept: jbang uses any Java 21 or later the machine has, and downloads
  nothing then. On a machine with none it downloads the newest, Java 27 on the day of this record,
  which RESTest's continuous integration does not test; RESTest is Java 21 bytecode, which a later
  Java runs. Chosen by the maintainer on 1 November 2026 against `21`, which Homebrew's formula does
  for the reason [ADR-0040](0040-homebrew.md) §2 gives.

### 3. Kept in RESTest, filled in for each version, and held to the launchers

`dist/jbang/jbang-catalog.json` is the catalog with `@VERSION@` in place of the version, and
`dist/jbang/catalog.sh` fills it in, refusing anything but a version such as `2.5.0` or
`2.5.0-SNAPSHOT`. The archive does not carry it. The main class, the option and the Java are now said
in two places, so `JbangAliasTest`, among the architecture and documentation rules, fails whenever the
alias and the two launchers disagree on any of them, and when the class has no `main`.

### 4. Checked before a release is drafted, and on three systems before the catalog is written

- `.github/scripts/check-jbang.sh` runs a catalog with jbang 0.142.0, pinned by its SHA-256, in a
  folder of its own: jbang's Javas, its libraries and the home folder its Java writes in are all
  there, and gone when it ends. It checks that with no Java at all, a jbang that has never run starts
  RESTest of that version, having downloaded two Javas; that `jbang app install` makes the command;
  that jbang runs RESTest on the archive's `lib/`, byte for byte; that the command passes
  `check-restest.sh --own-java`, on Windows as `cmd.exe` runs it and through PowerShell's `.ps1`
  too, through Windows PowerShell, `powershell.exe`, which every Windows has; and that the first
  `app install` wrote no start-up file and, on Windows, did not change the user's `Path` or
  `JAVA_HOME`.
- `check-restest.sh --own-java` runs its steps with no Java with the `PATH` as it is, every `java`
  and `javac` taken out by `.github/scripts/without-java.sh`, in place of the five tools a launcher
  needs: a command with its own Java may need more than a launcher, and jbang does.
- `release.yml`'s `check-jbang` runs it on Linux for the version just built, with RESTest's modules
  taken from what Maven Central gets and every other library from Central, and the draft waits for
  it. What depends on the build is the POMs and the jars, the same on every system.
- `.github/workflows/jbang.yml` runs it on Linux, macOS and Windows, against Maven Central and the
  release's archive, before it writes the catalog.

### 5. Written into the catalog once the release is published

jbang can only fetch what Maven Central serves, so the alias goes into the catalog from `jbang.yml`,
started by a release's publication, which follows Central's ([ADR-0044](0044-maven-central.md) §5),
or by hand with a tag. It checks that Central serves the version, fills in the catalog, downloads the
release's archive with no token and checks it against `checksums_sha256.txt`, runs the check above on
three systems, writes RESTest's alias - and nothing else, so that other aliases the group adds stay -
never over a higher version, and over the same version only when the alias changed; then, once
GitHub Pages serves the new catalog, which may take some minutes, runs the check again on Linux from
the address a person types, which is what it adds and the same on every system. A run whose alias is
in the catalog already checks it there again, which is how a failed check of the published catalog is
made again. A pull request that touches the catalog or its scripts runs the first two steps against
the latest published release, and writes nothing.

The catalog is written with a **deploy key**, kept in an environment of RESTest's, `jbang-catalog`,
which only `master` and version tags may use, as Homebrew's tap is
([ADR-0040](0040-homebrew.md) §5).

### 6. What it promises

The latest release: a catalog names one version, which a person's jbang reads again within 12 hours
of a release, or at once with `--fresh`. A version of one's choosing is the image or the archive. On
Windows, `cmd.exe` and PowerShell are checked; the shell script jbang also writes there, for Git Bash,
is not.

## Consequences

- jbang runs RESTest from the catalog on macOS, Linux and Windows, with no Java installed first.
  The first time, on a machine with no Java, jbang downloads some 600 MB of Java into `~/.jbang` and
  37 MB of libraries into `~/.m2/repository`; on one with a JDK 21 or later, the libraries alone.
  Every start then costs jbang's own Java, some 0.17 seconds, nothing beside a run's budget.
- **jbang's own failures end with 1, 2, 3 or 4**, before RESTest starts, numbers RESTest gives other
  meanings: a build that cannot reach Maven Central ends as one that found faults. jbang says why in
  lines beginning `[jbang]` and writes no `report.json`. `docs/command-line.md` lists them among the
  numbers RESTest does not choose.
- **`restest@isa-group` can run something else**: what a `jbang-catalog.json` in the folder it runs
  in, or above it, names, and, whenever GitHub does not answer with the catalog, what a catalog of
  `isa-group` on GitLab or Bitbucket names. The documentation gives the address on GitHub Pages, and
  says why. Claiming `isa-group` on GitLab, and learning whose the workspace on Bitbucket is, are the
  maintainer's.
- **A build of RESTest installed with `./mvnw install`** at a release's version puts that version's
  modules into `~/.m2/repository` with the build's own POMs, which name fewer libraries than the
  published ones; jbang then runs those, and fetches nothing from Central for them.
- **The first `jbang app install` changes the shell's start-up files**, or the user's `Path` on
  Windows, when jbang's `bin` is not on the `PATH` yet; on a machine with no Java it points `JAVA_HOME`
  at jbang's JDK 17, which the archive's launcher refuses with 127 in every shell started afterwards.
  The documentation says so, and how to undo it.
- A release now also waits on GitHub for jbang itself and on the servers jbang downloads Java from,
  foojay and Adoptium. jbang's version is moved by hand, in `check-jbang.sh`.
- A tag does not freeze the catalog, as it does not freeze Homebrew's formula: a fix to the alias
  reaches the catalog by the next run for that version.
- An owner of the organisation sets it up once: the catalog's repository, GitHub Pages for it, its
  deploy key, and the environment that holds the key. Until `jbang.yml` first runs, by hand for 2.4.0
  once this record is merged, the catalog does not exist.

## Alternatives considered

- **A version range, or `RELEASE`, in the alias**, so that the catalog never changes and needs no
  workflow and no key: jbang keeps what it resolved by the text of the coordinates, so nobody would
  move to a later version.
- **`21`**, as Homebrew's formula has: RESTest would always run on the Java its continuous integration
  and its image use, for the same download on a machine with no Java; but somebody whose only Java is
  22 or later would download a Java 21, 335 MB. The maintainer chose `21+`.
- **A catalog in RESTest's own repository**, `jbang-catalog.json` at its root, as
  `jbang restest@isa-group/RESTest`: no second repository and no key, but a file at the root of
  RESTest, a longer command, and a catalog naming a release only once the pull request that moves
  `master` on has been merged.
- **`restest@isa-group` in the documentation**, the command the plan named: short, but open to a
  local `jbang-catalog.json` and to GitLab and Bitbucket, as above. The maintainer chose it with a
  warning, while the catalog's address on github.com was believed to avoid the local lookup; it does
  not, since jbang turns it back into `isa-group`.
- **Maven coordinates in place of a catalog**, `jbang app install --name restest` with the main
  class, the option, the Java and `es.us.isa.restest:restest-cli:<version>`: no catalog to look up at
  all, but some 150 characters, and one version for good, installed again by hand at each release.
- **A main class in `restest-cli`'s jar**: jbang would find it, but the option and the Java would
  still be the alias's, and the jar would say a third time what the launchers say.
- **Written into the catalog by hand** at each release: one more step that can be forgotten, checked
  on a person's machine.
- **The latest jbang in the checks**: what a person installs, but a download that changes under every
  run; a jbang pinned by its checksum, moved by hand, instead.
- **The three systems in `release.yml` too**: each of them downloading two Javas before every draft,
  for what does not depend on the build.
- **One jar holding every library**, as [ADR-0015](0015-command-line-contract.md) and
  [ADR-0032](0032-how-a-release-is-published.md) turned down.
