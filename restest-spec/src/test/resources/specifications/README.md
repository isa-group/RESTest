# Golden corpus of specifications

Documents used to test `SpecificationParser` and everything downstream of it. Each API gets one
directory, holding nothing but its specification document — no test-suite configuration, no sample
data, no credentials. Three sub-directories, in the order new tests should reach for them:

## `restleague-2027/` — test against this first

The five APIs named in the [2027 REST League benchmark](https://seunivr.github.io/RestLeague/2027/):
`flight-search`, `gestao-hospital`, `kafka-rest-proxy`, `notebook-manager` and `pet-clinic`. These are
what the tool is actually evaluated against, so this is the priority corpus — new parser, generation
and oracle tests should exercise it before reaching into `community/`. All five declare
`openapi: 3.0.x`. Exact provenance (upstream project, pinned commit, fetch date) is recorded in
`ROADMAP.md` rather than duplicated here, so there is one place to update if a file is re-fetched.

One licensing note worth carrying alongside the files themselves: of the five upstream projects, only
`spring-petclinic/spring-petclinic-rest` (`pet-clinic`) is Apache-2.0. `confluentinc/kafka-rest`
carries the Confluent Community License Agreement, a source-available but non-OSI license; the
remaining three (`flightsearchapi`, `GestaoHospital`, `NoteBookManager`) carry no explicit license at
all. All five specification documents are used here purely as interface descriptions for internal
test fixtures, never redistributed as software — the same basis the rest of this corpus already
stands on — but a license this specific is worth naming rather than folding into a blanket "unclear"
statement.

## `community/` — the wider corpus

One directory per public API, carried over from RESTest 1.x's test suite and stripped down to just
the specification: no `testConf*`, `fullConf*`, `.properties`, CSV/JSON sample data or credentials —
those were 1.x's own test-suite wiring and have no equivalent format in v2. Real, and consequently
uneven: several of these documents were themselves trimmed by 1.x (large stretches commented out to
narrow what a particular old test suite exercised), so a directory's size is not a reliable proxy for
how much of the live API it actually describes. `GitHub/openapi.yaml` (473 paths) is the one genuinely
large document that survived intact; treat it, not any of the trimmed ones, as this corpus's "large,
real" fixture.

## `fixtures/` — hand-written, not carried over from any real API

Small documents written for this corpus, each isolating one thing `SpecificationParser` must handle
that no single real-world document in `community/` was found to exercise cleanly:

- `malformed/openapi.yaml` — one valid operation, one whose request body references a schema that is
  never defined. For asserting that a dangling reference degrades a run rather than crashing it
  (design principle 2), without also losing the operations around it.
- `unsupported-version/openapi.yaml` — an otherwise ordinary document declaring `openapi: 3.2.0`. For
  the "unsupported version, skipped and reported" path specifically (ADR-0007, reversed at M1.2),
  distinct from a malformed document within a supported version.
- `oas31-traps/openapi.yaml` — the 3.0-to-3.1 differences ADR-0007 names as silent traps: `nullable`
  as a JSON Schema type-array member instead of a sibling keyword, and a numeric `exclusiveMinimum`
  instead of a boolean flag.
- `unparseable/openapi.yaml` — not valid YAML at all (an unclosed flow mapping). For the loader-level
  failure that happens *before* per-operation skipping is even reachable: a document that never
  becomes an `OpenAPI` object at all, as distinct from one that parses fine but has a malformed
  operation in it.

Each file says so in its own `info.description` (`unparseable/` cannot, since it never parses far
enough to have one — its own comment says so instead). `CorpusSanityTest`, alongside this directory,
pins the properties this README claims about the corpus as a whole (every file declares a version;
exactly one declares 3.2; the malformed fixture's reference stays dangling) — as plain-text checks,
independent of `SpecificationParser` itself, which has its own, much more thorough test suite in
`restest-spec/src/test/java/io/restest/spec/` covering exactly these fixtures end to end.
