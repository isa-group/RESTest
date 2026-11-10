# Installing RESTest

RESTest comes five ways: with Homebrew, which brings the Java RESTest runs on; with jbang, which
downloads one when there is none, on Windows too; as a container image, which needs nothing but
Docker; as an archive, which needs nothing but Java; and as its source code, which you build
yourself. All five run the same RESTest, and each ends with `restest` as a command on your `PATH`.
Pick the one that suits what your machine already has, and skip the other sections.

## With Homebrew

**What you need** is [Homebrew](https://brew.sh/), on macOS or Linux. It installs a Java 21 for
RESTest, so you need no Java of your own:

```bash
brew install isa-group/tap/restest
```

`isa-group/tap` is RESTest's own *tap*, the place Homebrew reads how to install it from, and Homebrew
fetches it the first time. With the Java and what that Java needs, the first installation keeps
about 628 MB on macOS and 824 MB on Ubuntu 24.04. On a Linux whose C library is older than
Homebrew's, Homebrew brings its own, and a compiler with it, and the total comes to some 1.5 GB.
Homebrew lists what it installs as it goes. Then:

```bash
restest version
```

```
RESTest 2.5.0
Java 21.0.13.0.3 (Homebrew), Mac OS X 26.7.2 aarch64
```

The `restest` Homebrew installs always runs on that Java 21, whatever `JAVA_HOME` says; for RESTest on
a Java of your choosing, use [the archive](#with-java). Homebrew installs the latest release only:
for a version of your choosing, use the image or the archive.

## With jbang

**What you need** is [jbang](https://www.jbang.dev/download/), on macOS, Linux or Windows. It runs
Java programs from Maven Central and downloads a Java when there is none, so you need no Java of your
own:

```bash
jbang app install restest@https://isa-group.github.io/jbang-catalog/jbang-catalog.json
```

`restest@` and an address name the alias `restest` in the ISA group's catalog, which says where
RESTest is on Maven Central and how to start it: its main class, the option Java needs, and Java 21
or later. The command writes `restest` into jbang's folder, `~/.jbang/bin`, as `restest.cmd` and
`restest.ps1` too on Windows. Then, in a new terminal:

```bash
restest version
```

```
RESTest 2.5.0
Java 27.0.1.0.2 (Eclipse Adoptium), Mac OS X 27.0.1 aarch64
```

and on Windows, in the command prompt or in PowerShell:

```
RESTest 2.5.0
Java 27.0.1.0.2 (Eclipse Adoptium), Windows Server 2025 10.0 amd64
```

Those are machines with no Java, where jbang downloads two into `~/.jbang`: a Java 17 for jbang
itself, and the newest Java for RESTest, some 640 MB together, as well as RESTest's libraries, 37 MB,
into `~/.m2/repository`. On a machine with a JDK 21 or later — a JDK, which jbang looks for, not a
plain Java runtime — jbang downloads the libraries alone and runs RESTest on that Java. With
`JBANG_DEFAULT_JAVA_VERSION` set to `21` before the first jbang command of all — `export` it in a
shell, `set` it in the Windows command prompt, `$env:` in PowerShell — jbang downloads one Java 21 for
both, some 340 MB; it is then the Java jbang runs every program on, not only RESTest.

**What the first `jbang app install` changes.** When `~/.jbang/bin` is not on the `PATH` yet, jbang
adds it in your shell's start-up files — `~/.zshrc` and `~/.bash_profile` on macOS, `~/.bashrc` and
`~/.zshrc` on Linux, and a file of its own, `~/.config/fish/conf.d/jbang.fish`, on both; the user's
`Path` on Windows — which is why a new terminal is needed. On a machine with no Java it also sets
`JAVA_HOME` there to the Java 17 it downloaded for itself, which [the archive](#with-java) refuses as
too old. Remove those lines, and jbang's fish file, to undo it.

**In PowerShell**, typing `restest` runs `restest.ps1`, which a Windows that refuses to run scripts —
a fresh one does — refuses; `restest.cmd` runs either way.

**Without installing**, `jbang` and the alias take the place of `restest`:

```bash
jbang restest@https://isa-group.github.io/jbang-catalog/jbang-catalog.json version
```

**Which version.** jbang runs the latest release. It reads the catalog again twelve hours after it
last did, so a new release reaches you within a day of it, or at once when `--fresh` follows `jbang`.
For a version of your choosing, use the image or the archive.

**Why the address, rather than `restest@isa-group`.** jbang also takes `restest@isa-group` for the
same alias, but then looks for a catalog of that name first in any `jbang-catalog.json` of the folder
it runs in, or of a folder above it, and, when GitHub does not answer with one, on GitLab and
Bitbucket, where `isa-group` is not RESTest's: a project you test, or somebody else's account, could
then decide what runs. It does the same with the catalog's address on github.com, which it turns back
into `isa-group`. An address on GitHub Pages, which jbang does not take for GitHub's, it reads as it
is.

**When jbang itself fails** — it cannot reach Maven Central, say, or download a Java — it ends with
`1`, `2`, `3` or `4`, numbers RESTest gives other meanings, says why in lines beginning `[jbang]`,
and leaves no `report.json`: one of those numbers with no report is jbang's, not RESTest's ([the
numbers RESTest does not choose](../command-line.md#numbers-restest-does-not-choose)).

**If you have run `./mvnw install`** in a checkout of RESTest at the same version, which puts its
modules into `~/.m2/repository`, jbang runs those jars rather than Maven Central's, with the libraries
their build names; the build this chapter gives, `./mvnw package`, puts nothing there. Remove
`~/.m2/repository/es/us/isa/restest/`, which jbang fills again from Maven Central.

`jbang app uninstall restest` removes the command. `~/.jbang` holds jbang's Javas, and
`~/.m2/repository` RESTest's libraries, beside whatever else Maven or jbang put there.

## With Docker

**What you need** is Docker:

```bash
docker --version
```

```
Docker version 29.8.2, build 7fc2dff
```

The image holds RESTest and the Java it runs on, for Intel and AMD processors and for ARM ones such
as Apple silicon. The first command downloads it, about 145 MB:

```bash
docker run --rm ghcr.io/isa-group/restest:2.5.0 version
```

```
RESTest 2.5.0
Java 21.0.13 (Eclipse Adoptium), Linux 7.0.14-linuxkit aarch64
```

A run reads its document and its other files from a folder, and writes its report into it. Inside
the container that folder is `/work`, and it has to be lent from your machine: `-v "$PWD:/work"`
lends the folder you are in. What you would give `restest` then goes after the image's name:

```bash
docker run --rm -v "$PWD:/work" ghcr.io/isa-group/restest:2.5.0 run openapi.yaml --url https://api.example.com --budget 30s
```

That reads `openapi.yaml` from the folder you are in and leaves `restest-out/`, with the report, in
it. Without `-v`, what the run finds still appears on the screen, but the report is written inside
the container, and goes with it when `--rm` removes it; nor can a document on your disk be read.

Typing all of that each time is long, and a run may need more of it. These lines write a small
script, `restest`, into a folder for your own commands, and put that folder on the `PATH`; the script
runs the image with the folder you are in, and the rest a run needs, every time:

```bash
mkdir -p ~/.local/bin
printf '%s\n' '#!/bin/sh' 'exec docker run --rm -v "$PWD:/work" --user "$(id -u):$(id -g)" -e RESTEST_AUTH --add-host=host.docker.internal:host-gateway ghcr.io/isa-group/restest:2.5.0 "$@"' > ~/.local/bin/restest
chmod +x ~/.local/bin/restest
export PATH="$HOME/.local/bin:$PATH"
```

What the script hands `docker run`:

- `-v "$PWD:/work"` lends the folder you are in when you run it.
- `--user "$(id -u):$(id -g)"` makes what the run writes there yours rather than the system's, which
  matters on Linux.
- `-e RESTEST_AUTH` hands over a key left in that variable ([chapter 8](08-an-api-that-asks-for-a-key.md)).
  Any other variable RESTest reads, such as a setting ([chapter 7](07-settings.md)), needs an `-e`
  of its own in the script.
- `--add-host=host.docker.internal:host-gateway` lets the container reach your own machine by that
  name.

That last one matters for any API on your own machine, such as the practice API of the next chapter:
inside the container, `localhost` is the container itself, and nothing answers there. In the
addresses this manual gives `restest`, write `host.docker.internal` in place of `localhost`:

```bash
restest run http://host.docker.internal:9966/petclinic/v3/api-docs --budget 30s
```

The practice API names its own address after the one its document was asked for at, so nothing else
is needed. A document that gives `http://localhost:8080` as its API's address needs
`--url http://host.docker.internal:8080` as well.

**On Linux, an API that listens only on `127.0.0.1`** — what many frameworks do while an API is being
written — cannot be reached as `host.docker.internal`, which is another address of the machine. Put
`--network host` in the script in place of `--add-host=…`: the container then shares the machine's
own network, and `localhost` is the machine, as in the rest of this manual. On a Mac or on Windows,
Docker Desktop reaches such an API as `host.docker.internal` already.

## With Java

**What you need** is Java 21 or later. A plain Java runtime is enough. Check which one you have:

```bash
java -version
```

```
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)
```

The first line has to say 21 or a higher number. If it says something lower, or the command is not
found, install a current Java — [Eclipse Temurin](https://adoptium.net/) is one free choice — and
open a new terminal.

Every release carries an archive with RESTest and every library it runs with, in two forms,
`restest-2.5.0.tgz` and `restest-2.5.0.zip`, on its [page on
GitHub](https://github.com/isa-group/RESTest/releases/tag/v2.5.0). Download one and unpack it:

```bash
curl -fLO https://github.com/isa-group/RESTest/releases/download/v2.5.0/restest-2.5.0.tgz
tar -xzf restest-2.5.0.tgz
restest-2.5.0/bin/restest version
```

```
RESTest 2.5.0
Java 25.0.4.1 (Eclipse Adoptium), Mac OS X 27.0.1 aarch64
```

The release also carries `checksums_sha256.txt`, which says whether the archive arrived whole.
Downloaded beside it,

```bash
curl -fLO https://github.com/isa-group/RESTest/releases/download/v2.5.0/checksums_sha256.txt
sha256sum --ignore-missing -c checksums_sha256.txt
```

answers `restest-2.5.0.tgz: OK`; on macOS, write `shasum -a 256` in place of `sha256sum`.

The folder `restest-2.5.0` holds `bin/`, with the command, `lib/`, with the libraries, `schemas/`,
with the JSON Schemas of a dictionary and a plan ([chapter 6](06-dictionaries.md)), `skills/`, with
the skill that teaches a coding agent to use RESTest ([RESTest and coding
agents](../for-coding-agents.md)), and the licences.
It can live anywhere. This line puts its `bin/` on the `PATH`, which makes `restest` the command,
from any folder:

```bash
export PATH="$PWD/restest-2.5.0/bin:$PATH"
```

On Windows, unpack `restest-2.5.0.zip` and run `restest-2.5.0\bin\restest.cmd` from the ordinary
command prompt, or `restest-2.5.0/bin/restest` from Git Bash.

When the command finds no Java it can use, it says so and ends with `127`:

```
restest: RESTest needs Java 21 or later, and /opt/java/openjdk/bin/java is Java 17.0.20.1.
Set JAVA_HOME to a Java 21 or later.
```

It uses the Java that `JAVA_HOME` names, and otherwise the first `java` on the `PATH`.

## From the source

**What you need** is a JDK 21 or later, the edition of Java that can compile programs — `java -version`
says which Java you have, and a JDK's `javac -version` answers too — and git, to fetch the source:

```bash
git --version
```

```
git version 2.54.0 (Apple Git-157)
```

Then:

```bash
git clone --branch v2.5.0 https://github.com/isa-group/RESTest.git
cd RESTest
./mvnw -q package -DskipTests
```

`./mvnw` is Maven, the build tool, which the checkout carries so that you need not install it. The
first build downloads everything RESTest is built from and takes a few minutes; later ones take less
than one. `-q` keeps it quiet, so it prints nothing when it works, and `-DskipTests` leaves out
RESTest's own tests, which take longer than the build and which you do not need to use the tool.

`--branch v2.5.0` fetches the release; without it, `git clone` fetches what is being built next, whose
version ends in `-SNAPSHOT`. `./restest`, at the root of the checkout, runs what was built, and this
line puts the checkout on the `PATH`, which makes it `restest`, from any folder:

```bash
export PATH="$PWD:$PATH"
```

On Windows, run the commands in Git Bash, which comes with [Git for
Windows](https://git-scm.com/download/win). `./restest` is a shell script, and Git Bash is the shell
that runs it; it hands Java the paths the way Windows expects. `mvnw.cmd` builds from the ordinary
Windows command prompt too, but `./restest` needs Git Bash.

If `./restest` says *RESTest has not been built in this checkout yet*, the build did not finish: run
it again and read what it prints.

## Checking that it works

```bash
restest version
```

The first line says which RESTest you have, and the second which Java it runs on and on what
machine. They are the two lines to paste at the top of a report of something that went wrong.

`restest help` lists the commands, and `restest help run` every option of a run, with the numbers a
run can end with.

**The rest of this manual writes `restest`** for the command, as each section above makes it. An
`export PATH=…` lasts as long as the terminal; to keep it, add the line to your shell's start-up
file, such as `~/.bashrc` or `~/.zshrc`, with the folder written out in full in place of `$PWD`.
Since `restest` is then a command like any other, scripts, build servers and coding agents started
from that shell find it too.

## Updating

- **Homebrew:** `brew upgrade restest` installs the latest release.
- **jbang:** nothing: it runs the latest release within twelve hours of it, or at once after
  `jbang --fresh restest@https://isa-group.github.io/jbang-catalog/jbang-catalog.json version`.
- **Docker:** change `2.5.0` in the script to the version you want.
- **The archive:** download and unpack the new one, and point the `PATH` at its `bin/`.
- **The source:** from the root of the checkout, fetch the new release's tag, move to it, and build
  again:

  ```bash
  git fetch --tags
  git checkout v2.5.0
  ./mvnw -q clean package -DskipTests
  ```

  with the new version in place of `2.5.0`. `clean` throws away what the last build left behind, so
  that the jars of the version you had are not found beside the ones of the version you now have.

The [next chapter](03-a-first-run.md) starts a practice API and runs RESTest against it.
