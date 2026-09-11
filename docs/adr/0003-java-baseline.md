# ADR-0003: Library modules target Java 21; the CLI and the toolchain use JDK 25

**Status:** Accepted
**Date:** 2026-09-11

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
- The CLI module and the build toolchain use JDK 25.
- CI runs the test suite on 21, 25 and 26, across Linux, macOS and Windows.
- No preview feature appears in any published API. Concurrency uses plain virtual threads and
  `Executors.newVirtualThreadPerTaskExecutor()`.

## Consequences

- Anyone on Java 21 or later can depend on `restest-core`.
- The 21 column of the CI matrix is what makes that promise real rather than aspirational.
- We forgo Scoped Values in the library (final in 25). Where per-request context is needed, it is
  passed explicitly — which is better design anyway.
- Two compilation targets in one build. Handled by the Maven toolchain; a small, one-off cost.

## Alternatives considered

- **JDK 25 everywhere.** Simpler build, and enables Scoped Values and the AOT startup cache
  throughout. Rejected: it excludes a large share of potential users for no expressive gain.
- **JDK 26 or newer.** Would give HTTP/3 in the JDK's own HTTP client. Rejected: not an LTS,
  supported for six months, and almost nobody could depend on the library.
- **Java 17.** Rejected: virtual threads are the mechanism behind the entire concurrency design.
