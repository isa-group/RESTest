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
request sent, and its outcome — a response, or a transport failure, sealed as `InteractionOutcome`
so a third case cannot be added without every switch over it being revisited.

### Why this shape

The decision above committed to "a test case is an immutable data structure" and "oracles observe
the resulting interaction afterwards" in prose. This amendment is where those two nouns become
types, plus the third thing the decision implied but did not name: every chosen value needs a
recorded *origin*, because ADR-0006 promises "the provenance of each parameter value" as part of
what gets persisted, and a value with no way to say where it came from cannot honour that promise.

**Provenance is `ValueOrigin`, sealed over three cases** — `Declared` (the schema's own default or
enumeration), `Generated` (a stateless mechanism, named by an open string because M1.5's providers
do not exist yet and ADR-0008 forbids naming specific mechanisms in `core`), and `Derived` (taken
from an earlier interaction in this run).

`Derived` is the one that matters beyond the stateless case `ROADMAP.md` describes for M1.1b. The
glossary in `docs/DESIGN.md` defines a stateful test as a *sequence* — create, read, update, delete —
"where each step depends on the previous one", and a dependency between two steps is exactly a value
in one test case pointing at an interaction that ran earlier. Without `Derived`, no stateful step
would be representable until M4 landed as a whole; a data model that cannot hold the input to a
feature four milestones away, when holding it costs one sealed case with no caller yet, is the wrong
economy.

What `Derived` deliberately does **not** do is specify how a value is extracted from the interaction
it depends on. Its `description` is free text — "response body field 'id'" — not a JSONPath, not an
OpenAPI `links` runtime expression. Choosing that grammar is M4.1's (the operation dependency graph),
M4.2's (runtime resource pool and value-source selection) and M4.3's (declared `links`) decision, and
none of the three exist yet. A stateful *test*, in the glossary's sense of a whole sequence, is not a
type this amendment introduces either: it is the transitive closure of `Derived` edges among stored
interactions, discoverable from the store (M1.4) rather than tracked by a separate sequence
identifier that could drift out of sync with the edges themselves. M4.4 builds the actual generator;
this amendment only had to make its output representable.

**`Payload` is the first record in `restest-core` holding a mutable component**, and the `TODO(M1.1)`
this left on `ArchitectureRules.noStaticMutableState` — extending the rule to a static final field of
a mutable type — turns out not to be the gap that matters here: `Payload`'s array is an *instance*
component, not a static field, so that rule was never going to reach it. What actually protects it is
convention, checked by test rather than by ArchUnit: the compact constructor clones on the way in,
the accessor clones on the way out, and `equals`/`hashCode` are written by hand, because a record's
generated versions compare an array component by reference and would be silently wrong for two
payloads holding identical bytes in two different arrays. No static-analysis rule can tell "copies
the array" from "keeps the reference" by reading a compact constructor, so this is recorded here
rather than mechanised.

### Consequences

- `TestCase` and `Interaction` reference identifiers (`OperationId`, `TestCaseId`, `InteractionId`),
  never live model instances, so a stored run can be read back and re-examined (`restest recheck`,
  M3.3) without the `ApiModel` that produced it.
- A stateful step costs nothing extra to store or to reason about: it is a `TestCase` like any other,
  with one `ValueOrigin.Derived` instead of a `Generated` or `Declared`.
- `HttpRequestRecord`/`HttpResponseRecord` keep headers as an ordered, repeatable list rather than a
  map, because HTTP allows a header to repeat and a map cannot.
- The status code on a received response is kept exactly as observed, with no plausibility check: an
  oracle (M3.2) is what judges whether it is a valid HTTP status, and the model must be able to hold
  the observation for the oracle to have something to judge.
