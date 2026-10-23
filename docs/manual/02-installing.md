# Installing RESTest

RESTest comes three ways: as a container image, which needs nothing but Docker; as an archive, which
needs nothing but Java; and as its source code, which you build yourself. All three run the same
RESTest. Pick the one that suits what your machine already has, and skip the other two sections.

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
docker run --rm ghcr.io/isa-group/restest:2.2.0 version
```

```
RESTest 2.2.0
Java 21.0.12.1 (Eclipse Adoptium), Linux 7.0.14-linuxkit aarch64
```

A run reads its document and its other files from a folder, and writes its report into it. Inside
the container that folder is `/work`, and it has to be lent from your machine. This line makes
`restest` a command that does that, and the rest a run needs, every time:

```bash
alias restest='docker run --rm -v "$PWD:/work" --user "$(id -u):$(id -g)" -e RESTEST_AUTH --add-host=host.docker.internal:host-gateway ghcr.io/isa-group/restest:2.2.0'
```

- `-v "$PWD:/work"` lends the folder you are in when you run it.
- `--user "$(id -u):$(id -g)"` makes what the run writes there yours rather than the system's, which
  matters on Linux.
- `-e RESTEST_AUTH` hands over a key left in that variable ([chapter 8](08-an-api-that-asks-for-a-key.md)).
  Any other variable RESTest reads, such as a setting ([chapter 7](07-settings.md)), needs an `-e`
  of its own.
- `--add-host=host.docker.internal:host-gateway` lets the container reach your own machine by that
  name.

That last one matters for any API on your own machine, such as the practice API of the next chapter:
inside the container, `localhost` is the container itself, and nothing answers there. Write
`host.docker.internal` wherever this manual writes `localhost`:

```bash
restest run http://host.docker.internal:9966/petclinic/v3/api-docs --budget 30s
```

The practice API names its own address after the one its document was asked for at, so nothing else
is needed. A document that gives `http://localhost:8080` as its API's address needs
`--url http://host.docker.internal:8080` as well.

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
`restest-2.2.0.tgz` and `restest-2.2.0.zip`, on its [page on
GitHub](https://github.com/isa-group/RESTest/releases/tag/v2.2.0). Download one and unpack it:

```bash
curl -fLO https://github.com/isa-group/RESTest/releases/download/v2.2.0/restest-2.2.0.tgz
tar -xzf restest-2.2.0.tgz
restest-2.2.0/bin/restest version
```

```
RESTest 2.2.0
Java 25.0.4.1 (Eclipse Adoptium), Mac OS X 27.0.1 aarch64
```

The release also carries `checksums_sha256.txt`. Downloaded beside the archive, it says whether the
archive arrived whole: `sha256sum --ignore-missing -c checksums_sha256.txt` on Linux, or
`shasum -a 256 --ignore-missing -c checksums_sha256.txt` on macOS, answers `restest-2.2.0.tgz: OK`.

The folder `restest-2.2.0` holds `bin/`, with the command, `lib/`, with the libraries, `schemas/`,
with the JSON Schemas of a dictionary and a plan ([chapter 6](06-dictionaries.md)), and the licences.
It can live anywhere. This line makes `restest` the command, from any folder:

```bash
alias restest="$PWD/restest-2.2.0/bin/restest"
```

On Windows, unpack `restest-2.2.0.zip` and run `restest-2.2.0\bin\restest.cmd` from the ordinary
command prompt, or `restest-2.2.0/bin/restest` from Git Bash.

When the command finds no Java it can use, it says so and ends with `127`:

```
restest: RESTest needs Java 21 or later, and /opt/java/openjdk/bin/java is Java 17.0.20.1.
Set JAVA_HOME to a Java 21 or later.
```

It uses the Java that `JAVA_HOME` names, and otherwise the first `java` on the `PATH`.

## From the source

**What you need** is a JDK 21 or later, the edition of Java that can compile programs, which
`java -version` above also shows, and git, to fetch the source:

```bash
git --version
```

```
git version 2.54.0 (Apple Git-157)
```

Then:

```bash
git clone https://github.com/isa-group/RESTest.git
cd RESTest
./mvnw -q install -DskipTests
```

`./mvnw` is Maven, the build tool, which the checkout carries so that you need not install it. The
first build downloads everything RESTest is built from and takes a few minutes; later ones take less
than one. `-q` keeps it quiet, so it prints nothing when it works, and `-DskipTests` leaves out
RESTest's own tests, which take longer than the build and which you do not need to use the tool.

`./restest`, at the root of the checkout, runs what was just built, and this line makes it
`restest`, from any folder:

```bash
alias restest="$PWD/restest"
```

`git clone` fetches what is being built next, whose version ends in `-SNAPSHOT`; add
`--branch v2.2.0` after `clone` for the release.

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

**The rest of this manual writes `restest`** for the command, as the `alias` line in each section
above defines it. An alias lasts as long as the terminal; to keep it, add the line to your shell's
start-up file, such as `~/.bashrc` or `~/.zshrc`. In the lines for the archive and the source, write
the folder out in full in place of `$PWD`; the line for Docker stays as it is, since its `$PWD` is
meant to be the folder you are in each time you run it.

## Updating

- **Docker:** change `2.2.0` in the alias to the version you want.
- **The archive:** download and unpack the new one beside the old, and point the alias at it.
- **The source:** from the root of the checkout,

  ```bash
  git pull
  ./mvnw -q clean install -DskipTests
  ```

  `clean` throws away what the last build left behind, so that the jars of the version you had are
  not found beside the ones of the version you now have.

The [next chapter](03-a-first-run.md) starts a practice API and runs RESTest against it.
