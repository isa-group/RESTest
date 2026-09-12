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
do not exist yet and ADR-0008 forbids naming specific mechanisms in `core`), and `Derived` (read out
of an earlier interaction's response).

`Derived` is the one that matters beyond the stateless case `ROADMAP.md` describes for M1.1b. The
glossary in `docs/DESIGN.md` defines a stateful test as a *sequence* — create, read, update, delete —
"where each step depends on the previous one", and a dependency between two steps is exactly a value
in one test case pointing at an earlier interaction's response. Without `Derived`, no stateful step
would be representable until M4 landed as a whole; a data model that cannot hold the input to a
feature four milestones away, when holding it costs one sealed case with no caller yet, is the wrong
economy.

`Derived` points at an `InteractionId`, and this went through two revisions before settling. The
first draft used `InteractionId`. Review raised a concern that a stateful generator must commit to a
dependency before the interaction it names has been produced, and switched the pointer to
`TestCaseId`, which exists from the moment a test case is planned rather than only once it has been
sent. That revision turned out to be built on a premise that does not hold: `TestCase` carries
concrete, already-resolved values, never a placeholder to fill in later (ADR-0005's own decision) —
so a `ParameterValue` carrying `Derived` cannot be *constructed* until the value it holds is known,
and that value can only be known by having already read the earlier interaction's response. By the
time a stateful generator builds this edge at all, the interaction it depends on already exists;
there never was an earlier moment requiring a `TestCaseId`. Worse, pointing at a `TestCaseId`
introduced a real problem the first design avoided: a retry or a replay of that test case would leave
already-planned `Derived` edges pointing at an identifier whose only interaction may not be the one
that actually produced the data they read, or that has none at all (a `TransportFailure`), with
nothing in the model to say which interaction a reader should look at. `InteractionId` names the one
interaction whose data was actually used, permanently, unaffected by whatever the engine does
afterwards with the test case that produced it. The reversion costs nothing this increment had
already built on the intermediate design.

What `Derived` deliberately does **not** do is specify how a value is extracted from the interaction
it depends on. Its `description` is free text — "response body field 'id'" — not a JSONPath, not an
OpenAPI `links` runtime expression. Choosing that grammar is M4.1's (the operation dependency graph),
M4.2's (runtime resource pool and value-source selection) and M4.3's (declared `links`) decision, and
none of the three exist yet. A stateful *test*, in the glossary's sense of a whole sequence, is not a
type this amendment introduces either: it is the transitive closure of `Derived` edges among stored
interactions, discoverable from the store (M1.4) rather than tracked by a separate sequence
identifier that could drift out of sync with the edges themselves. M4.4 builds the actual generator;
this amendment only had to make its output representable. `Interaction` places no constraint on how
many interactions one test case may end up producing — a retry, a replay, or a redirect the engine
chooses to record as its own attempt are all the engine's and the store's business, not a shape this
record has to anticipate, precisely because `Derived` no longer depends on there being only one.

`Derived` names one interaction, not several. A value computed by combining the responses of two
earlier steps cannot be expressed. Nothing in the roadmap currently needs that, and widening `from`
to a list later is a cheap, additive change with no caller yet depending on the narrower shape -
narrower was chosen deliberately over guessing at a generalisation nothing asks for.

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

`MalformedResponse` carries the whole status line - status code, reason phrase, protocol version -
and the headers when they parsed, separately from the body bytes, rather than folding everything into
one opaque "reason" string. The common shape of this outcome is that the status line and headers
parse cleanly and only the body breaks against its own declared length or encoding; an oracle judging
that needs what the response claimed about itself, including the protocol version, since whether a
given framing failure is even possible - chunked encoding exists only under HTTP/1.1 - depends on it.

The status line is one component, `Optional<StatusLine>`, not three separate optional fields. Two
reasons, both found in review. First, a reason phrase or a protocol version parsing while the status
code did not is not a shape HTTP parsing can ever produce, and three independent optionals would
leave that impossible combination constructable; bundling them makes it unconstructable instead of
merely undocumented. Second, `reasonPhrase` and `protocolVersion` are both `Optional<String>` -
adjacent parameters of the same type - which on a ten-parameter factory
(`Interaction.malformedResponse`) is exactly the transposition a caller can make silently and no
compiler catches; naming them as components of one small type turns that mistake into a type error
at the call site instead of a swapped value nobody notices. `HttpResponseRecord` adopts the same
`StatusLine` for `Answered`, so the two outcomes describe a status line identically rather than each
inventing its own shape for the same three facts.

Whatever body bytes were retained before the exchange broke are declared under
`Payload.UNKNOWN_MEDIA_TYPE` when nothing said what they were meant to be, a named constant rather
than a string literal the engine has to remember and repeat correctly. `MalformedResponse.partial`'s
`Payload` does **not** carry the length the response declared but failed to honour - see the
`Payload.wireLength` entry below for why that is deliberately a different fact, read from `headers`
instead.

Not represented, deliberately: a `TestCase` the engine planned but never attempted - shed by adaptive
concurrency, cut off by the budget. No request went out, so no `Interaction` exists for it; inventing
one would mean fabricating `sentAt`. Counting "planned versus attempted" stays the engine's and the
report's job (M1.3, M3.6).

**`Payload` gained `wireLength`**, replacing an earlier `truncated` boolean, after review. ADR-0006
names "configurable response-body truncation" as the store's answer to storage cost, so a stored
payload is not always the whole body; a boolean alone would say "do not trust this," but a
`Content-Length` conformance oracle (M3.2) needs the actual wire length to compare the retained bytes
against. Keeping `truncated` as a second, independently-settable component would let the two disagree
- a payload claiming to be truncated with no length to check it against, or a wire length shorter
than the bytes actually retained, which the first version of this field accepted through both the
factory and the canonical constructor. `truncated()` is now derived from `wireLength` and `content`,
so there is exactly one fact to get right, and the canonical constructor refuses a `wireLength`
shorter than what was retained regardless of which factory - or none - constructed the payload.

`wireLength` means one specific thing, and review found the first version of this field's Javadoc did
not say which: how much *our own storage* retained, out of a body that genuinely arrived in full -
never how much the response *declared* it would send but failed to deliver. Those are different
events with different culprits: the first is our retention policy; the second is the API's, and it is
exactly the fact `MalformedResponse` exists to report. Conflating them under one field would make a
row written by our own store truncation indistinguishable from a row reporting an API fault, which
`restest recheck` (M3.3) cannot tell apart after the fact. The declared-but-undelivered case does not
need a place on `Payload` at all: `MalformedResponse` already carries `headers`, so the gap between a
`Content-Length` header and the partial body's actual size is visible by comparing the two, without
this field restating it. A body that simply stops arriving with no declared length to compare against
- a chunked stream with no final chunk - is `content` with no `wireLength`: everything retained,
nothing said about whether more was coming, which is the honest answer when nothing else is known.

`Payload` is the first record in `restest-core` holding a mutable component, and the `TODO` on
`ArchitectureRules.noStaticMutableState` — extending the rule to a static final field of a mutable
type — turns out not to be the gap that matters here: `Payload`'s array is an *instance* component,
not a static field, so that rule was never going to reach it. What actually protects it is
convention, checked by test rather than by ArchUnit: the compact constructor clones on the way in,
the accessor clones on the way out, and `equals`/`hashCode` are written by hand, because a record's
generated versions compare an array component by reference and would be silently wrong for two
payloads holding identical bytes in two different arrays. No static-analysis rule can tell "copies
the array" from "keeps the reference" by reading a compact constructor, so this is recorded here
rather than mechanised. The TODO stays open, untagged with a milestone, for the first `static final`
field of a mutable type to actually appear.

`content` and `wireLength` are only comparable when they describe the same representation of the
body - both raw, or both transfer-decoded. An HTTP client ordinarily hands back content already
decoded, and that is the ordinary case here; the record's Javadoc says so explicitly, because a
`Content-Length` counts encoded octets and comparing it against a decoded length is a category error
the type cannot catch on the engine's behalf.

**`HttpRequestRecord` and `HttpResponseRecord` do not print header values in `toString`.** A header
is exactly where an `Authorization` bearer token or an API key (M2.6) travels, and the record-
generated `toString` would print it verbatim into any log line or report. Both override `toString` to
show header *names* only, plus the body's own already-safe summary. This does not solve secret
redaction in general - a credential in a query string or inside a body is not caught by it - which is
left to the reporting work in M3.6; it closes the one leak this increment could close cheaply.

`HttpResponseRecord` also gained the reason phrase and protocol version, both optional, as part of
its `StatusLine` (see above) rather than as fields of its own: M3.2's HTTP-semantics oracles want
them, and they are the same status line the code already came from.

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
- A stateful step costs little extra to store or reason about beyond acknowledging that a whole
  body's provenance is coarser than a whole parameter's: it is a `TestCase` like any other, with one
  `ValueOrigin.Derived` instead of a `Generated` or `Declared`, except where the dependency lands
  inside a body.
- `HttpRequestRecord`/`HttpResponseRecord` keep headers as an ordered, repeatable list rather than a
  map, because HTTP allows a header to repeat and a map cannot.
- The status code on a received response is kept exactly as observed, with no plausibility check: an
  oracle (M3.2) is what judges whether it is a valid HTTP status, and the model must be able to hold
  the observation for the oracle to have something to judge.
