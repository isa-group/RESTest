# ADR-0005: Test cases are data, executed directly — not generated source code

**Status:** Accepted, amended at M1.1b
**Date:** 2026-09-11 (amended 2026-09-12)

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

## Amendment (M1.1b)

**Date:** 2026-09-12

**A test case is `io.restest.core.execution.TestCase`.** An immutable record naming which operation
it invokes (by `OperationId`, not by holding the `Operation` itself), the chosen value for each
parameter, and an optional body. **An interaction is `Interaction`**: the test case, the exact
request sent, and its outcome — a well-formed response, a malformed one, or none at all, sealed as
`InteractionOutcome` so a fourth case cannot be added without every switch over it being revisited.

### Why this shape

The decision above committed to "a test case is an immutable data structure" and "oracles observe
the resulting interaction afterwards" in prose. This amendment is where those two nouns become
types, plus the third thing the decision implied but did not name: every chosen value needs a
recorded *origin*, because ADR-0006 promises "the provenance of each parameter value" as part of
what gets persisted, and a value with no way to say where it came from cannot honour that promise.

**Provenance is `ValueOrigin`, sealed over three cases** — `Declared` (the schema's own default or
enumeration), `Generated` (a stateless mechanism, named by an open string because M1.5's providers
do not exist yet and ADR-0008 forbids naming specific mechanisms in `core`), and `Derived` (taken
from an earlier test case in this run).

`Derived` is the one that matters beyond the stateless case `ROADMAP.md` describes for M1.1b. The
glossary in `docs/DESIGN.md` defines a stateful test as a *sequence* — create, read, update, delete —
"where each step depends on the previous one", and a dependency between two steps is exactly a value
in one test case pointing at an earlier one. Without `Derived`, no stateful step would be
representable until M4 landed as a whole; a data model that cannot hold the input to a feature four
milestones away, when holding it costs one sealed case with no caller yet, is the wrong economy.

`Derived` points at a `TestCaseId`, not an `InteractionId`. A stateful generator has to commit to a
dependency at the moment it *plans* the second step, which is before the first has been sent; a
`TestCaseId` exists from the instant `TestCase.of` is called, while an `InteractionId` is not minted
until an `Interaction` factory runs, by which time it is too late to have been the value the
generator already committed to.

What `Derived` deliberately does **not** do is specify how a value is extracted from the test case it
depends on. Its `description` is free text — "response body field 'id'" — not a JSONPath, not an
OpenAPI `links` runtime expression. Choosing that grammar is M4.1's (the operation dependency graph),
M4.2's (runtime resource pool and value-source selection) and M4.3's (declared `links`) decision, and
none of the three exist yet. A stateful *test*, in the glossary's sense of a whole sequence, is not a
type this amendment introduces either: it is the transitive closure of `Derived` edges among stored
test cases and interactions, discoverable from the store (M1.4) rather than tracked by a separate
sequence identifier that could drift out of sync with the edges themselves. M4.4 builds the actual
generator; this amendment only had to make its output representable.

`Derived` names one test case, not several. A value computed by combining the responses of two
earlier steps cannot be expressed. Nothing in the roadmap currently needs that, and widening `from`
to a list later is a cheap, additive change with no caller yet depending on the narrower shape -
narrower was chosen deliberately over guessing at a generalisation nothing asks for.

**Resolving a `Derived` edge depends on an invariant, so `Interaction` states it as one: a test case
produces at most one interaction.** This is what `TestCaseId` being the pointer relies on - reading
the dependency back means finding the one interaction whose `testCase().id()` matches, and that only
works if there is exactly one. Two things the engine (M1.3) must therefore do, both recorded on
`Interaction` rather than left implicit:

- **A retry is a new test case**, with a fresh `TestCaseId`, not a second interaction for the one
  that failed. This surfaced as a real gap in review: the first draft let a redirect be "each hop is
  its own interaction" while simultaneously relying on one-test-case-one-interaction for `Derived` to
  resolve, which is a direct contradiction the moment a test case is retried or redirected.
- **A redirect the engine follows is not a second interaction either.** `HttpRequestRecord` is the
  request the test case asked for; the outcome recorded is the final one, exactly as an ordinary HTTP
  client already reports it once it has followed whatever redirects it followed. Capturing the
  intermediate hops in their own right is left to whoever first needs to judge a redirect
  specifically - not decided here, and not needed to fix the contradiction.

**`BodyValue` carries one `ValueOrigin` for the whole body, not one per field.** A body whose fields
come from different places at once - `{"id": <derived>, "name": <generated>}`, the ordinary shape of
a CRUD update - cannot have that distinction recorded; the store can say the body as a whole was
partly derived, but not which field. Solving this needs an annotated-value shape this record does not
have, and belongs with M2.5 (which builds request-body generation) or with M4, not with this
increment. This is a real, acknowledged limitation, not a claim that stateful bodies are fully solved.

**`InteractionOutcome` is sealed over three cases, not two as first drafted.** A well-formed response
(`Answered`) and no response at all (`TransportFailure`) are not the whole of what an HTTP exchange
can produce: bytes can come back that are not a valid HTTP response - broken chunked encoding, a
truncated status line, a body shorter than its own declared `Content-Length`. That is a fault of the
API under test, the exact class WFC reserves codes 900-909 for (M3.2), and it must not be
indistinguishable from a connection nobody answered, which is not the API's fault at all.

`MalformedResponse` carries the status code and headers when they parsed, separately from the body
bytes, rather than folding everything into one opaque "reason" string. The common shape of this
outcome is that the status line and headers parse cleanly and only the body breaks against its own
declared length or encoding; an oracle judging that needs what the response claimed about itself, not
only a description of how it failed. Whatever body bytes were retained before the exchange broke are
declared under `application/octet-stream` when nothing said what they were meant to be - the standard
media type for "unknown binary content," not a guess at a `Content-Type` the response may never have
sent.

Not represented, deliberately: a `TestCase` the engine planned but never attempted - shed by adaptive
concurrency, cut off by the budget. No request went out, so no `Interaction` exists for it; inventing
one would mean fabricating `sentAt`. Counting "planned versus attempted" stays the engine's and the
report's job (M1.3, M3.6).

**`Payload` gained `truncated` and `wireLength`** after review. ADR-0006 names "configurable
response-body truncation" as the store's answer to storage cost, so a stored payload is not always
the whole body; a boolean alone would say "do not trust this," but a `Content-Length` conformance
oracle (M3.2) needs the actual wire length to compare the retained bytes against, not merely a
warning that one is missing. `Payload.partial(bytes, mediaType, wireLength)` sets both together.
`Payload` is the first record in `restest-core` holding a mutable component, and the `TODO` on
`ArchitectureRules.noStaticMutableState` — extending the rule to a static final field of a mutable
type — turns out not to be the gap that matters here: `Payload`'s array is an *instance* component,
not a static field, so that rule was never going to reach it. What actually protects it is
convention, checked by test rather than by ArchUnit: the compact constructor clones on the way in,
the accessor clones on the way out, and `equals`/`hashCode` are written by hand, because a record's
generated versions compare an array component by reference and would be silently wrong for two
payloads holding identical bytes in two different arrays. No static-analysis rule can tell "copies
the array" from "keeps the reference" by reading a compact constructor, so this is recorded here
rather than mechanised. (The TODO itself is left untagged with a milestone, having now missed one: it
stays open for the first `static final` field of a mutable type to actually appear, whenever that is.)

**`HttpRequestRecord` and `HttpResponseRecord` do not print header values in `toString`.** A header
is exactly where an `Authorization` bearer token or an API key (M2.6) travels, and the record-
generated `toString` would print it verbatim into any log line or report. Both override `toString` to
show header *names* only, plus the body's own already-safe summary. This does not solve secret
redaction in general - a credential in a query string or inside a body is not caught by it - which is
left to the reporting work in M3.6; it closes the one leak this increment could close cheaply.

`HttpResponseRecord` also gained `reasonPhrase` and `protocolVersion`, both optional: they are part of
the same status line as the code already kept, and M3.2's HTTP-semantics oracles want them.

**Deliberately left open.** Two considerations surfaced in review that this increment does not
settle, because settling them now would mean guessing at a design that belongs to a later milestone:

- *Ordering interactions within a run.* `sentAt` is the only ordering key here, and `Instant` is not
  guaranteed to be distinct under concurrent dispatch. Establishing a reliable order - an
  engine-assigned sequence, or the store's own insertion order - is the non-blocking engine's
  concern (ADR-0009, M1.3) once adaptive concurrency exists to reason about, not something to invent
  as a field on `Interaction` today.
- *A run's own identity.* Nothing here names which run an `Interaction` belongs to; two runs
  coexisting in one JVM (design principle 6) are kept apart by which store, or which portion of one,
  they are written to (M1.4), not by a component on this record.

**Considered and not changed: giving `Declared` a sub-kind.** A schema's stated default and its first
enumerated member are both `Declared` today, with nothing to tell them apart, and M2.2's declared
examples will be a third case the document can supply. Left alone for now: the schema itself, already
reachable from the operation, still holds the default and the enumeration, so a consumer wanting the
distinction can compare the chosen value against them directly; nothing is lost, only left for a
consumer to derive rather than being restated here.

### Consequences

- `TestCase` and `Interaction` reference identifiers (`OperationId`, `TestCaseId`, `InteractionId`),
  never live model instances, so a stored run can be read back and re-examined (`restest recheck`,
  M3.3) without the `ApiModel` that produced it.
- `TestCase` has no `withX` method: everything it carries is decided once, at construction, so its
  identifier cannot end up attached to two different sets of parameter values or bodies.
- The engine (M1.3) must uphold "one test case, at most one interaction": retries and redirects are
  the engine's business, not a reason to multiply interactions per test case, because `Derived`'s
  resolution depends on the join staying unambiguous.
- A stateful step costs little extra to store or reason about beyond acknowledging that a whole
  body's provenance is coarser than a whole parameter's: it is a `TestCase` like any other, with one
  `ValueOrigin.Derived` instead of a `Generated` or `Declared`, except where the dependency lands
  inside a body.
- `HttpRequestRecord`/`HttpResponseRecord` keep headers as an ordered, repeatable list rather than a
  map, because HTTP allows a header to repeat and a map cannot.
- The status code on a received response is kept exactly as observed, with no plausibility check: an
  oracle (M3.2) is what judges whether it is a valid HTTP status, and the model must be able to hold
  the observation for the oracle to have something to judge.
