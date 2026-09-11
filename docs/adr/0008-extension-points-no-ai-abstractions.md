# ADR-0008: Extension points, and no AI-specific abstractions

**Status:** Accepted
**Date:** 2026-09-11

## Context

The tool must be ready to integrate artificial intelligence later without depending on it now. An
earlier draft of the plan proposed an optional `restest-ai` module and an `LlmProvider` interface.
The maintainers rejected that framing, and correctly: it ties the architecture to today's assumptions
about what a model is and how it is called, and it invents a special case where a general one
already does the job.

What "ready for AI" actually means here is three capabilities, each useful with no model involved:
open formats, external sources of input values, and the ability to add knowledge while the tool is
running.

There is also concrete evidence about *where* expensive computation must happen. In the SBFT 2026
competition, RESTest's local multi-agent value generator consumed a large share of a one-hour budget
on 8 cores with no GPU, during which no requests were sent.

## Decision

**No AI-specific abstraction exists in v2.0.** No `LlmProvider`, no `restest-ai` module, no dependency
on any model library. An ArchUnit test fails the build if one appears.

Instead, three general mechanisms:

**1. Open formats in, open formats out.** Everything the tool consumes or produces is a documented
file: the OAS, OpenAPI Overlays, IDL4OAS documents, our value dictionary format, the interaction
store, the run report. Any future component participates by reading and writing those files.

**2. `ExternalDataProvider`.** Given an operation and its parameter schemas, return candidate values
with provenance. Nothing in the interface mentions models. Implementations may run in-process or
**out of process** over a small protocol, so a provider written in Python plugs in as a sidecar.
Providers are **asynchronous by default** and results are cached on disk, keyed by specification hash
and provider identity.

**3. `ConstraintSource` and `FlowSource`.** New inter-parameter dependencies and new operation flows
can be contributed **while the generation-and-execution loop is running**. The constraint model is
mutable and per-operation; the solver re-solves and subsequent test cases respect the new rules
immediately. Everything carries provenance and confidence.

v2.0 ships trivial implementations of all three — a file-based dictionary, an out-of-process echo
provider used in tests, and a watched-directory constraint source — as proof that the seams are real.

## Consequences

- Plugging a model in later means writing a program that emits dictionary entries or IDL snippets. It
  does not mean modifying RESTest.
- The same seams serve a curated dictionary, a corporate test-data service, or a human watching a run
  and typing a dependency they just noticed.
- The core never gains a dependency on a fast-moving ecosystem.
- Expensive providers cannot slow the run, because they are asynchronous by construction (ADR-0009).
- Slightly more indirection than calling a model directly would need — the price of not betting the
  architecture on one integration style.

## Alternatives considered

- **An optional `restest-ai` module with an `LlmProvider` interface.** Rejected by the maintainers.
  It is a special case of `ExternalDataProvider` that buys nothing and dates quickly.
- **Direct integration with a specific model library.** Rejected: turns a research tool into a client
  of one vendor's API shape.
