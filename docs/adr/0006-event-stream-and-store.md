# ADR-0006: One event stream, and every interaction persisted

**Status:** Accepted, amended at M1.4 and M1.6
**Date:** 2026-09-11 (amended 2026-09-12, 2026-09-13)

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
produced it, and the provenance of each parameter value. ~~SQLite by default, NDJSON as an
alternative.~~ **Amended at M1.4: SQLite, and only SQLite.** See the amendment below.

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

## Amendment (M1.4)

**Date:** 2026-09-12

**One store, not two: SQLite. NDJSON is a report format, not a second container.**

### Why

The original decision named NDJSON "an alternative", and building it at M1.4 would have meant two
implementations of one interface, doing the same job, maintained in parallel for the life of the
project. They do not complement each other: whichever one a run uses, the other is idle.

The roadmap already places NDJSON where it earns its keep — **M3.5**, among the report formats,
beside HTML, JUnit XML and HAR. That is what design principle 8 (open formats out) actually asks for:
that a finished run can leave RESTest in a format anybody can read, not that RESTest keep two kinds
of database.

They are also not equally good at the job the store exists to do. Everything downstream of the store
asks it questions repeatedly — `restest recheck` (M3.3), corpus oracles, reports. SQLite answers them
from an index; a plain-text file answers them by parsing itself from the beginning, every time, and
holds no more than a run's worth in memory while doing it. A run killed halfway leaves a database
consistent and a text file with half a line in it.

This is the same trade ADR-0007 made at M1.2 when it went back to one parser backend, for the same
reason stated there: *"One well-tested backend is simpler to build, simpler to keep passing against
the golden corpus, and simpler for a reader to reason about."*

### How

- `restest-store` has exactly one `InteractionStore`, backed by SQLite: one file per run, the facts
  worth filtering on as indexed columns, and the interaction itself stored beside them as a JSON
  document.
- There is one serialised shape in the project, defined in one class (`InteractionDocument`), and
  M3.5's NDJSON report writes *that* shape rather than inventing a second one. Note what this does
  **not** yet settle: `restest-report` may not depend on `restest-store` under the current layer
  rules, so M3.5 must either take that dependency deliberately (a rule change, argued at the time) or
  move the document shape into `restest-core` — which can hold it without gaining a JSON library,
  because building a `JsonValue` needs none; only turning it into text does. Whichever is chosen, the
  requirement is that no second mapping from an interaction to JSON is written.
- The `InteractionStore` interface stays, with one implementation behind it, for the reason it was
  introduced: it is what M3.3 and the corpus oracles consume, and replacing the container later is a
  change to one module.

### Consequences

- Half the code and half the tests for the same capability, and one on-disk shape to keep correct.
- A stored run is readable without RESTest by anything that reads SQLite — `sqlite3`, a notebook, a
  spreadsheet — and the document column is readable by anything that reads JSON. "Open formats out"
  is satisfied at M1.4, and satisfied again as a file anyone can `grep` at M3.5.
- The tool now depends on a database driver that carries native libraries. The GraalVM native binary
  at M7.3 will need configuration for it. This is routine, and it is the price of not having a
  pure-Java container; it is recorded here so M7.3 does not meet it as a surprise.

## Amendment (M1.6)

**Date:** 2026-09-13

**One way of turning a value into JSON text, and it lives in `restest-core`.** The M1.4 amendment
above left this open in as many words; M1.6 is where it came due, and it came due a milestone earlier
than expected.

### Why

`io.restest.store.Json` was a thin adapter over `jackson-core`, kept at the store's edge because the
store was the only thing that needed it. At M1.6 two more parts of the tool need it and neither can
see `restest-store`: the JSON report has to write a document, and the schema oracle has to write the
location of a schema it hands to the validator. The layer rules are right and were not going to be
relaxed for this.

That leaves the choice the M1.4 amendment already framed — take the dependency deliberately, or move
the shape into `restest-core` — with a third thing now settled by the same move. `InteractionDocument`
goes with it. The amendment's own requirement was that *"no second mapping from an interaction to JSON
is written"*, and M1.6 is the first increment with a reason to write one: a fault in a report quotes
the request that caused it. It quotes it through the same class the stored run uses, so a fault in a
report and the same fault in a stored file are the same text.

### How

- `io.restest.store.Json` becomes `io.restest.core.json.JsonText`, and
  `io.restest.store.InteractionDocument` becomes `io.restest.core.json.InteractionDocument`. Neither
  changes otherwise; they gained a test of their own, which the second of them had never had.
- `io.restest.core.json.JsonException` replaces the store's exception in both.
  `SqliteInteractionStore` catches it and says which stored interaction was damaged, exactly as
  before.
- `restest-core` gains `jackson-core` and `restest-store` loses it. The architecture rule confining
  the JSON library to one module is not dropped; it names a different module.

### Consequences

- `restest-core` is no longer literally dependency-free, which was a stated property of it. What it
  gains is a streaming reader and writer with no transitive dependencies of its own, and no object
  mapper; `CLAUDE.md`'s description of the module says so rather than leaving the line to be found
  false. "No parser" there was always about the OpenAPI parser, and that is still true.
- One answer in the project to "what does this value look like written down", instead of one per
  module that happens to need it. M3.5's NDJSON report writes the same shape rather than a second
  one, which is what the M1.4 amendment asked for.
- Coverage moved with the code: `InteractionDocument` had been covered only through the store's
  tests, and counted against `restest-core` the moment it arrived there. It now has its own test,
  which is where a shape two separate features depend on should have been tested all along.
