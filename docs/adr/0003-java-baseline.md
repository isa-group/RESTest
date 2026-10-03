# ADR-0003: Every module targets Java 21; the build toolchain uses JDK 25

**Status:** Accepted, amended at M0.2
**Date:** 2026-09-11 (amended 2026-09-11)

> The Decision and Consequences below are as originally accepted, with the two superseded points
> marked. The amendment at the end of this document is the current state: the CLI targets Java 21
> like every other module. Nothing else changed.

## Context

As of September 2026 the latest JDK is 26 and the latest long-term-support release is 25, supported
until 2030 (extended, 2033). The next LTS is 29, in September 2027.

Requirement #5 asks for RESTest to be consumable as a Maven or Gradle dependency. A library's
bytecode version is a hard gate: a project on Java 21 cannot use a library compiled for 25,
regardless of what the library actually does. Java 21 remains the centre of gravity in production.

Everything this tool needs is final in 21: records, sealed interfaces, pattern matching for switch
and record patterns, and virtual threads. Nothing in 25 changes what we can express.

Two features that look attractive are not usable in a published API. Structured Concurrency is still
a preview feature — its seventh preview lands in JDK 27 and the API changed materially between
rounds. Stable Values was previewed in 25 and renamed to Lazy Constants with a different API in 26.

## Decision

- Library modules compile with `maven.compiler.release=21`.
- ~~The CLI module and the build toolchain use JDK 25.~~ **Superseded at M0.2:** the build toolchain
  uses JDK 25, but the CLI compiles with `release=21` like every other module. See the amendment.
- CI runs the test suite on 21, 25 and 26, across Linux, macOS and Windows.
- No preview feature appears in any published API. Concurrency uses plain virtual threads and
  `Executors.newVirtualThreadPerTaskExecutor()`.

## Consequences

- Anyone on Java 21 or later can depend on `restest-core`.
- The 21 column of the CI matrix is what makes that promise real rather than aspirational.
- We forgo Scoped Values in the library (final in 25). Where per-request context is needed, it is
  passed explicitly — which is better design anyway.
- ~~Two compilation targets in one build. Handled by the Maven toolchain; a small, one-off cost.~~
  **Superseded at M0.2:** there is one compilation target, and therefore no toolchain configuration
  in the build at all. See the amendment.

## Alternatives considered

- **JDK 25 everywhere.** Simpler build, and enables Scoped Values and the AOT startup cache
  throughout. Rejected: it excludes a large share of potential users for no expressive gain.
- **JDK 26 or newer.** Would give HTTP/3 in the JDK's own HTTP client. Rejected: not an LTS,
  supported for six months, and almost nobody could depend on the library.
- **Java 17.** Rejected: virtual threads are the mechanism behind the entire concurrency design.

## Amendment (M0.2)

**Date:** 2026-09-11

The command-line module now compiles with `--release 21` like every other module. The build
toolchain and the CI matrix are unchanged.

### Why

The decision above set the CLI to `--release 25`, which makes the JDK 21 row of the CI matrix
unable to compile the reactor: `javac` on 21 cannot target 25. Two ways out were considered.

Excluding `restest-cli` from the 21 row (`-pl '!restest-cli'`) works, needs nothing installed, and
states a true fact — the CLI is not consumable on 21 by design. It also means one row of the matrix
builds something different from the other eight, which is a footnote every future reader has to
carry.

Registering a JDK 25 toolchain so that every row compiles the CLI is the literal reading of "the
build toolchain uses JDK 25". It has a trap: `maven-surefire-plugin` honours the session toolchain
too, so without an explicit `<jvm>` override the tests would run on JDK 25 on every row and the
matrix would silently stop testing 21 and 26 at runtime. A gate that looks like it tests three
Java versions while testing one is worse than no gate.

`--release 21` everywhere avoids both. Every row builds and tests the whole reactor, the build
needs no toolchain configuration, and nothing in the CLI requires a 25-only language feature —
picocli does not, and this ADR already forbids preview features in published APIs.

### What it costs

The CLI forgoes Scoped Values, final in 25, and the JDK 25 AOT startup cache. Neither is in use.
Startup time is answered properly by the GraalVM native binary at M7.3, not by a bytecode target.

### What is unchanged

- Library bytecode is Java 21, which is the promise this ADR exists to make.
- The build toolchain is JDK 25. `--release` and the JDK running the build are independent.
- CI runs the test suite on 21, 25 and 26 across Linux, macOS and Windows.
- No preview features in any published API.

`PublishedBytecodeTest` in `restest-arch-tests` checks this claim by reading the class file headers
the build actually produced. It excludes `module-info.class`, which `javac` emits at the compiling
JDK's class file version regardless of `--release`; a JDK 21 runtime resolves and runs such a module
regardless, which was verified rather than assumed.

Being exact about its present state: at M0.2 the modules contain nothing *but* module descriptors,
so the scan has nothing to examine and skips, with the reason recorded against the skipped test. It
begins asserting on every build at M1.1, with the first class. What is checked today is the
instrument rather than the claim — a companion test calibrates the header reader against bytes whose
version is known.
