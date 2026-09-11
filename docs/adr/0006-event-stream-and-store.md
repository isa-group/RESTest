# ADR-0006: One event stream, and every interaction persisted

**Status:** Accepted
**Date:** 2026-09-11

## Context

Two requirements point at the same mechanism.

Reporting: RESTest 1.x implements oracles as REST-Assured filters injected into generated code, and
exports results by shelling out to a 19 MB Allure distribution committed into the repository. Adding
an output format means touching the execution path. Schemathesis, by contrast, ships six output
formats without the engine knowing about any of them, because the engine emits one uniform stream of
events and every reporter is an independent consumer.

Future semantic oracles: the maintainers intend to add oracles inferred from *sets* of requests and
responses, or from the specification. An oracle that only ever sees one interaction at a time cannot
express an invariant or a metamorphic relation. Retrofitting that later would mean rewriting the
oracle engine.

## Decision

**One event stream.** The engine publishes typed events — test case planned, request sent, response
received, failure detected, phase finished, run finished. Reporters, metrics collectors, feedback
listeners and oracles are all independent subscribers. The engine knows none of them.

**Two oracle kinds.**
- `Oracle` examines a single `Interaction`.
- `CorpusOracle` examines a queryable *set* of interactions — the whole run, one operation's slice,
  or a sliding window.

Each oracle declares what it consumes, so the engine can schedule it correctly and `restest explain`
can describe it.

**Every interaction is persisted** — exact request and response bytes, timings, the test case that
produced it, and the provenance of each parameter value. SQLite by default, NDJSON as an alternative.

**`restest recheck <run>`** re-evaluates a stored run against a different oracle set, offline, with no
API calls.

## Consequences

- Adding an output format is a new class implementing one interface.
- A researcher can try a new oracle against a corpus they already have, in seconds, reproducibly,
  without touching the API under test. This is the workbench the deferred semantic-oracle work needs,
  and it is built years before that work starts.
- Post-hoc analysis becomes honest: the raw evidence is on disk, not summarised away.
- Storage cost. A one-hour run against a chatty API produces a large database. Mitigated by
  configurable response-body truncation and by the store being replaceable.
- A small runtime cost per interaction for publishing and writing. Measured by the overhead
  regression test (ADR-0009).

## Alternatives considered

- **Reporters called directly by the engine.** What 1.x does. Every new format touches the engine.
- **Only single-interaction oracles, with corpus analysis bolted on later.** Rejected on the evidence
  of how expensive "later" was for 1.x's stateful support.
- **Keeping results only in memory.** Cheaper, and forecloses `recheck`, corpus oracles and any
  post-hoc analysis.
