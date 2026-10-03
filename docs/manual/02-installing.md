# Installing RESTest

RESTest is built from its source code, which takes one command once Java and git are in place.

## What you need

**Java 21 or later.** Building needs a JDK, the edition of Java that can compile programs. Check
which one you have:

```bash
java -version
```

```
openjdk version "25.0.4.1" 2026-08-18
OpenJDK Runtime Environment Homebrew (build 25.0.4.1)
OpenJDK 64-Bit Server VM Homebrew (build 25.0.4.1, mixed mode, sharing)
```

The first line has to say 21 or a higher number. If it says something lower, or the command is not
found, install a current JDK — [Eclipse Temurin](https://adoptium.net/) is one free choice — and open
a new terminal.

**git**, to fetch the source:

```bash
git --version
```

**Docker**, only for the practice API the rest of this manual runs its examples against. RESTest
itself does not need it.

```bash
docker --version
```

## Building from source

```bash
git clone https://github.com/isa-group/RESTest.git
cd RESTest
./mvnw -q install -DskipTests
```

`./mvnw` is Maven, the build tool, which the checkout carries so that you need not install it. The
first build downloads everything RESTest is built from and takes a few minutes; later ones take less
than one. `-q` keeps it quiet, so it prints nothing when it works, and `-DskipTests` leaves out
RESTest's own tests, which take longer than the build and which you do not need to use the tool.

## Checking that it works

`./restest`, at the root of the checkout, runs what was just built:

```bash
./restest version
```

```
RESTest 2.0.0
Java 25.0.4.1 (Homebrew), Mac OS X 26.6.2 aarch64
```

The first line says which RESTest you have, and the second which Java it runs on and on what
machine. They are the two lines to paste at the top of a report of something that went wrong.

`./restest help` lists the commands, and `./restest help run` every option of a run, with the
numbers a run can end with.

If `./restest` says *RESTest has not been built in this checkout yet*, the build above did not
finish: run it again and read what it prints.

## On Windows

Run the commands in Git Bash, which comes with [Git for Windows](https://git-scm.com/download/win).
`./restest` is a shell script, and Git Bash is the shell that runs it; it hands Java the paths
the way Windows expects. `mvnw.cmd` builds from the ordinary Windows command prompt too, but
`./restest` needs Git Bash.

## Updating

From the root of the checkout:

```bash
git pull
./mvnw -q clean install -DskipTests
```

`clean` throws away what the last build left behind, so that the jars of the version you had are not
found beside the ones of the version you now have.

The [next chapter](03-a-first-run.md) starts a practice API and runs RESTest against it.
