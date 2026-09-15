# ADR-0006: One event stream, and every interaction persisted

**Status:** Accepted, amended at M1.4, M1.6 and M1.7
**Date:** 2026-09-11 (amended 2026-09-12, 2026-09-13, 2026-09-15)

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

~~**Every interaction is persisted**~~ **Amended at M1.7: every interaction is persisted *when a run
is asked to keep them*. A run keeps nothing by default.** What is kept, when it is kept, is unchanged
— exact request and response bytes, timings, the test case that produced it, and the provenance of
each parameter value. ~~SQLite by default, NDJSON as an alternative.~~ **Amended at M1.4: SQLite, and
only SQLite.** See the amendments below.

**`restest recheck <run>`** re-evaluates a stored run against a different oracle set, offline, with no
API calls.

## Consequences

- Adding an output format is a new class implementing one interface.
- A researcher can try a new oracle against a corpus they already have, in seconds, reproducibly,
  without touching the API under test. This is the workbench the deferred semantic-oracle work needs,
  and it is built years before that work starts.
- Post-hoc analysis becomes honest: the raw evidence is on disk, not summarised away.
- Storage cost. A one-hour run against a chatty API produces a large database. ~~Mitigated by
  configurable response-body truncation and by the store being replaceable.~~ **Amended at M1.7:
  measured. Body truncation is not the mitigation — bodies are 6% of what is stored. Not writing the
  file unless asked is.**
- A small runtime cost per interaction for publishing and writing. ~~Measured by the overhead
  regression test (ADR-0009).~~ **Amended at M1.7: measured there instead, because the whole pipeline
  ran for the first time at M1.7 and M6.2's regression test is milestones away. As first built it was
  not small — a third of a run — and that turned out to be the write path rather than the cost of
  persisting.**

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

## Amendment (M1.7)

**Date:** 2026-09-15

**A run keeps nothing by default. `--store` turns keeping on, and then everything is kept, in full.**

This reverses, for the default case, the sentence this ADR is named after. The capability is
unchanged and the promise is unchanged when it is asked for; what changes is that it is now asked
for. The reasoning is below, because a reader two years from now will find the title and the default
disagreeing and deserves to know that was deliberate.

### Why

M1.7 assembled the whole pipeline for the first time, which is the first time any of this could be
measured rather than assumed. Every number below comes from `restest run` against the pinned
`webfuzzing/wfd-swagger-petstore` image the smoke test uses, in Docker on the same machine (macOS,
Apple silicon), seed fixed:

```bash
docker run -d -p 18080:8080 webfuzzing/wfd-swagger-petstore@sha256:\
26951cb671d013c44ea978aa659afe89d438d0fb431411c127ead2633dc876a4
./restest run http://localhost:18080/api/v3/openapi.json --url http://localhost:18080/api/v3 \
  --budget 60s --seed 20260914 --out /tmp/run
```

**What a run costs on disk.**

| Budget | Requests | Faults | `run.sqlite` | `report.json` |
|---|---|---|---|---|
| 10 s | 67,761 | 3,989 | 97 MiB | 1.8 MiB |
| 60 s — the default budget | 462,033 | 27,181 | **661 MiB** | 1.8 MiB |

The report does not grow with the budget, because M1.7 already caps it. **The store is 98.5% of what
a ten-second run writes and 99.7% of what a sixty-second run writes.** Whatever the storage problem
is, the report is not it.

**Where the store's bytes go.** Measured rather than assumed, and the assumption would have been
wrong. Of the 97 MiB, 89 MiB is the table, 8 MiB the five indexes, and 14.2 MiB of the table's pages
is slack — space inside 4 KiB pages too small to hold another 1.4 KiB row. The documents are 60.7
MiB, a mean of 940 bytes per interaction: response headers 22.8%, request headers 19.9%, JSON keys
and punctuation 18.3%, parameters with their provenance 9.0%, method and URL 6.8%, **response body
6.2%**, and the rest the two ids, the times, the status line and the operation.

So the question this amendment was opened to answer — full bodies, truncated bodies, or bodies
de-duplicated by hash — was the wrong question. 88.2% of interactions carry a body; there are 4,022
distinct bodies among 59,790; storing each distinct one once saves 3.4 MiB, **3.5% of the file**. The
repetition is in the *headers*: 26 MiB of the 60.7 MiB is header lists, of which there are 2,164
distinct in 67,761 interactions — 41,873 of them the same four request headers, byte for byte.
Stored once, all of them are 0.5 MiB.

**What a run costs in time.** Six configurations of the same ten-second run, three repetitions each,
medians below. Differences under about 5% are noise at this sample size:

| Listeners | Requests | Idle |
|---|---|---|
| everything, as built today | 75,750 | 18.5% |
| everything, **with the write path fixed** | 111,850 | 0.9% |
| no store; oracles and reports only | 114,060 | 0.5% |
| store only, as built today | 92,185 | 13.0% |
| store only, with the write path fixed | 114,897 | 0.4% |
| no listeners at all | 114,951 | 0.3% |

Three things fall out of that table, and the second was a surprise.

1. **As built, the store costs a third of the run** — 75,750 against 114,951 — and is essentially all
   of the idle time. ADR-0015 says where that idle comes from: *"Pausing shows up as idle time, which
   is the honest place for it: the tool was not testing, and it was our own work that stopped it."*
   This is that work. Every oracle and every report together cost about 4%.
2. **Once the write path is fixed, the store costs nothing measurable.** 111,850 with everything
   including the store, against 114,060 with no store at all: the same number. The third of a run was
   never the price of persisting; it was the price of committing a transaction per row.
3. **Fixing the time therefore makes the disk problem worse, not better.** Per interaction the file
   shrinks 12%, from 1.46 KiB to 1.29 KiB. But the tool now tests 47% more in the same budget, so a
   ten-second file grows from 108 MiB to 141 MiB, and the default budget goes from 661 MiB to roughly
   **850 MiB**.

**What the report keeps.** The cap works; it is on the wrong axis. Those thousand findings, measured,
are **two distinct summaries across two operations** — 99.8% of the file is near-identical copies of
the same two faults, because the allowance is spent in arrival order and the first fault to repeat
takes all of it. A fault kind first seen in the ninth second is not in the file at all. It is also a
bound on count and not on bytes: a `Finding` holds its whole `Interaction`, and a thousand of those
against an API answering with large bodies is up to a gigabyte held until the run ends.

**Why a default of keeping nothing is defensible.** Because nothing in an ordinary run consumes the
store. ADR-0013's fifth decision already settled that generation never reads it — the dictionary of
observed values and the memory mutation works from are listeners with their own bounded in-memory
index, and `restest-gen`'s `module-info.java` cannot even see `restest-store`. Everything that does
consume a stored run — `restest recheck`, `restest replay`, corpus oracles over a whole run — is
something a person asks for deliberately, after the fact. A default that keeps 850 MiB for the
benefit of a command nobody ran is the wrong way round.

### How

**Keeping is off unless asked for.** A flag on `restest run` turns it on; without it no database is
opened and no file is written. The flag's spelling and the file contract belong to ADR-0015, which is
amended alongside this.

**When it is on, everything is kept, in full — the original decision, unchanged.** Exact request and
response bytes, timings, the test case, and the provenance of every value. No store-level truncation
and no de-duplication:

- **Bodies are stored whole**, with the engine's existing ceiling the only one. The engine already
  keeps at most 1 MiB of a reply and records how long the whole thing was (`Payload.partial`,
  `Payload.truncated()`), so a stored run already says "we kept 1 MiB of 12 MB" rather than
  pretending. A store-level cap low enough to matter would cut ordinary bodies, and a truncated JSON
  body is not a smaller fact but a false one: `recheck` at M3.3 re-runs the schema oracle over what
  is stored, and half an object parses as broken JSON. It would invent F101 faults the live run never
  reported, which is the opposite of what re-checking a run is for.
- **Nothing is de-duplicated by hash** — not bodies, where there is nothing to win, and not the
  headers, where there is. Interning the header lists would take about 26% off the file at the price
  of a row that is no longer one interaction: read by `sqlite3`, a notebook or a spreadsheet it would
  become a fragment needing a join, and M3.5's NDJSON and HAR reports would each have to do the
  joining. It is also competing with `gzip`, which takes the measured file from 101.8 MB to **14.3
  MB, 7.1 times**, precisely because the redundancy is across rows. Spending the self-contained row
  to claw back a quarter of something that compresses sevenfold anyway is a bad trade. Per-row
  compression of the document column was measured too and is worse than it looks: 54% row by row
  against 10% over the column as a stream, and it would cost the property the M1.4 amendment put in
  writing, that the column is readable by anything that reads JSON.

**The write path is fixed whether or not the default changed**, because a run that did ask to keep
its evidence should not pay a third of its budget for it:

- Page size 16 KiB, issued as a plain statement on a new file **before the journal mode**. This one
  fails silently otherwise: a page size cannot be changed once a database is in WAL mode, and
  `SQLiteConfig.setPageSize` is applied after the driver has turned WAL on, so it is accepted and
  discarded with no error. Verified both ways — 16384 before WAL, 4096 through the settings object.
  Whoever implements this should assert the page size the file actually has rather than trust the
  setting.
- One prepared statement, reused, rather than one prepared and discarded per interaction.
- Inserts batched inside a transaction, flushed **by count or by elapsed time, whichever comes
  first**, and always before any query and on close. The count bound keeps memory flat. The time
  bound keeps the store's own promise that a run can be read while it is still going on — and its
  exact strength is worth stating, because the obvious reading is wrong: the deadline is noticed when
  the *next* interaction arrives, so a reader outside the process is always at most one interaction
  behind, which is a quarter of a second against a busy API and a minute against one answering once a
  minute. A background thread would tighten that, at the price of a thread per store to start, join
  and reason about, for the benefit of somebody watching a run that has already gone quiet. What is
  also given up is that a run killed outright — not closed, `kill -9` — loses the interactions since
  the last flush instead of none.

**The report keeps a quota per operation and per fault kind, not the first thousand of anything.**
This is now a change about usefulness rather than size: the file is 1.8 MiB either way.

- **Exact counts for every (operation, fault kind) pair, all of them written.** This is the part that
  must be complete, and it can be: the table has at most one row per pair the specification allows,
  so it does not grow with the budget. Nothing is counted approximately and no fault disappears from
  the totals.
- **Full evidence for the first few of each pair** — the request, the reply, the provenance and the
  `curl` command, exactly as today.
- **Beyond that, faults are named one by one, and that naming is bounded too.** Two corrections to an
  earlier draft of this amendment, both found while designing the change and both worth recording
  because the draft was self-contradictory:
  - *Naming every fault is not a bound.* At 27,181 faults in the default run, one line each is about
    5.7 MiB — three times the whole file today — and it grows with the budget, which is the property
    this amendment exists to remove. It also sat two sentences from "the bound is on bytes as well as
    on count". Names get a quota and a byte budget of their own, and the file says when it stopped
    naming.
  - *An interaction id does not identify anything in a default run.* The draft said a named fault
    carries "the id of the interaction that produced it, which is one lookup away in `run.sqlite`" —
    but this same amendment makes `run.sqlite` optional and off by default, so by default there is
    nothing to look it up in. `InteractionId` is a random UUID and is not derived from the seed, so
    it is not stable across a rerun either: a report from one run cannot be joined to the store of
    the next. The id stays, because it is the right join key in the case it was written for, but what
    makes a named fault worth reading is the request that caused it — method, URL and the status that
    came back, all already recorded, none of them inferred.

The bound is on bytes as well as on count, because a bound on the number of unbounded things is not a
bound — and a URL is an unbounded thing just as a response body is.

**This is not deduplication or clustering, and the distinction is the whole point.** That work,
deferred out of v2.0 in `docs/DESIGN.md`, means *deciding that two failures are the same underlying
bug* — inference, with a notion of sameness that has to be argued for and evaluated. A quota fills a
fixed allowance per pair of fields already recorded on every finding. It asserts nothing about
whether two findings share a cause, merges no counts, and changes no total. If that inference is
built later it reads the stored run, as the deferred list says; it is not started here.

**A run says what it wrote, and how big it is.** One line beside the two it already prints.

### Consequences

- **A default run is 51% more productive**: 114,951 requests against 75,750 in the same ten seconds,
  with idle falling from 18.5% to 0.3%. That is the headline number M1.8 and M8 are compared on, and
  it arrives from deleting work rather than adding any.
- **A run that asks to keep its evidence now pays nothing measurable for it** — 111,850 against
  114,060 without a store — and writes 12% less per interaction than before.
- **The sharp edge, stated rather than buried: a default run cannot be re-examined afterwards.** If
  something surprising turns up, it has to be run again with the flag. Today that works, because the
  seed reproduces the run exactly. **From M2.7 it stops working** for any strategy with a memory:
  ADR-0013's seventh decision says those are reproduced *"by replaying the stored run"*, not by the
  seed, and `RandomTestCaseGenerator`'s own comment says the same. A surprising result in a default
  run will then be irreproducible by any means. M2.7 owns that problem and should decide it
  deliberately — the cheapest answer is for a run using such a strategy to say so when no store was
  asked for, rather than letting the evidence be discovered missing.
- Nothing in generation is affected, now or as planned: ADR-0013's fifth decision, the layering rule
  confining `Store` to `Cli`, and `restest-gen`'s module declaration all say the same thing three
  ways. The one place this could still leak is M2.7's dictionary disk cache, where *what fills it* is
  not decided anywhere; if it is filled by harvesting a stored run rather than by a listener, this
  default becomes load-bearing for it.
- `restest recheck` at M3.3 re-examines exactly what the live run examined, because no oracle is ever
  handed a body the live run did not have — the thing truncation would have quietly taken away.
- A stored run stays one row per interaction, readable by `sqlite3` and by anything that reads JSON,
  and compresses about sevenfold with tools everybody already has. Design principle 8 is unchanged
  rather than traded for a quarter.
- **The title of this ADR is now about a capability, not about every run.** "Every interaction is
  persisted" remains true of a run that asks; it is false of a run that does not. This amendment is
  the record of that, rather than a later reader finding the code and the title disagreeing.
- M1.8's campaign problem largely dissolves: an evaluation loop that does not pass the flag writes
  nothing and goes faster, and one that wants evidence opts in per invocation and owns the disk.
- Interning the repeated headers is now measured rather than guessed: 26% of the file, at the price
  of a row that no longer stands alone. If a later milestone wants it, the number and the objection
  are both here.

### Alternatives considered

- **Keep persisting by default and make it smaller.** What this amendment set out to do. Every
  mechanism measured — truncation, body de-duplication, column compression — is worth between 3% and
  a quarter, against a default cost of 850 MiB. None of them is the difference between a tolerable
  default and an intolerable one; not writing the file is.
- **Truncate response bodies in the store.** Addresses 6.2% of the problem while making `recheck`
  report faults that never happened.
- **De-duplicate identical bodies by hash.** 3.5% of the file. The intuition — that thousands of
  replies are byte-identical — is right; the bodies are simply not where the bytes are.
- **Keep only failing interactions.** 5.9% of the interactions, so about 8 MiB per ten seconds. It
  survives as a coherent middle option, and it forecloses every corpus oracle on the roadmap: an
  invariant is a statement about what the API did *in general*, and cannot be checked against a
  corpus filtered down to what already looked wrong.
- **Leave the default on and rely on `--out` plus housekeeping.** Puts the cost on everyone by
  default to spare the minority who want it a flag. The reverse is cheaper for both.
- **Write every finding to the report.** What M1.6 did. At 27,181 faults in the default run, each
  carrying its whole attempt, the report becomes the reason the run ends.
