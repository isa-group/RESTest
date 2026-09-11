# ADR-0005: Test cases are data, executed directly — not generated source code

**Status:** Accepted
**Date:** 2026-09-11

## Context

RESTest 1.x turns each abstract test case into Java source text, assembled by string concatenation
in a 618-line writer, then compiles it in-process with `ToolProvider.getSystemJavaCompiler()` and
runs it through JUnit 4 and REST-Assured. A committed example output is 3,348 lines for a single API.

Measured consequences:

- The boolean returned by `getTask(...).call()` is ignored, so compilation failures are silent.
- A full JDK is required at runtime; `.class` files are written next to sources.
- The set of oracles is frozen when the code is emitted. Adding an oracle means regenerating.
- In the SBFT 2026 competition RESTest's operations-covered AUC was 8,322 against a baseline of
  56,590 — the same final coverage reached far more slowly. This pipeline is a large part of why.

## Decision

A test case is an immutable data structure. An HTTP engine executes it directly. Oracles observe the
resulting interaction afterwards.

Emitting artefacts — JUnit 5 + REST-Assured source, `curl` commands, HAR files, Arazzo documents,
IDL4OAS overlays — is the job of a *reporter* listening to the event stream (ADR-0006). Those
artefacts are outputs for humans and for regression suites; they are never how the tool runs its own
tests.

## Consequences

- No compiler, no class loading, no temporary source files in the execution path.
- Oracles can be added, removed or reconfigured per operation without regenerating anything, and can
  even be re-applied to a finished run (`restest recheck`).
- The tool runs on a JRE, which makes the container image and the native binary far smaller.
- Failures are reported as data — the exact request and response — rather than as a stack trace from
  generated code.
- We lose the property that "the tool's own execution is the artefact the user keeps". Deliberate:
  the code export gives the same artefact without paying for it on every run.

## Alternatives considered

- **Keep code generation but use a template engine or JavaPoet.** Fixes the string-concatenation
  fragility and nothing else: still a compiler in the loop, still frozen oracles, still slow.
- **Generate once, execute many.** This is what 1.x calls offline testing, and it is still available
  through the code-export reporter. It just is not the execution path.
