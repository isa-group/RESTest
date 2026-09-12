# ADR-0007: The parser sits behind our own interface; OAS scope is 2.0, 3.0.x and 3.1.x

**Status:** Accepted, amended at M0.2, reversed at M1.2
**Date:** 2026-09-11 (amended 2026-09-11, reversed 2026-09-12)

> The Context and Decision below are as originally accepted, with the superseded scope marked. The
> M0.2 amendment put OAS 3.2 in scope; the M1.2 amendment reverses that and is the current state:
> **3.2 is out of scope**, one parser backend covers everything supported. The parser boundary
> itself, which is what this ADR is really about, has never changed.

## Context

RESTest 1.x uses Swagger's parser model as its own domain model, and never declares the dependency —
`swagger-parser` arrives transitively at 2.0.25 or 2.0.27 depending on Maven's resolution order. The
effects are all over the audit: `SchemaManager` states outright that `anyOf` and `oneOf` are
unsupported; `BodyGenerator` throws a null-pointer exception on `allOf` nodes; `setResolveFully(true)`
inlines every `$ref`, which explodes on large specifications and breaks on recursive ones; OAS 3.1 is
not supported at all.

The 3.0 → 3.1 differences that matter to a *generator* fail silently rather than loudly:
`nullable: true` becomes `type: ["string","null"]`; `exclusiveMinimum` and `exclusiveMaximum` change
from booleans to numbers, so a boundary generator that reads them as flags produces quietly incorrect
tests; inside a schema `examples` is a list while for a parameter it is a named map; and `paths`
becomes optional.

OAS 3.2 exists but `swagger-parser` does not support it; the feature request has been open since
November 2025. OAS 4.0 has no specification text and the OAI's own advice is to stay on 3.x.

*(Two claims in the original version of this paragraph were wrong and are corrected in the
amendment: a maintained Java parser for 3.2 does exist, and the `swagger-parser` issue is not
unanswered.)*

## Decision

**Scope: OAS 2.0 (by conversion), 3.0.x and 3.1.x, fully. Not 3.2, not 4.0.**
~~Superseded at M0.2: the scope is OAS 2.0 and every 3.x, 3.2 included.~~ **Reversed at M1.2: this
original scope is the current one again.** See both amendments below.

All parsing sits behind a `SpecificationParser` interface in `restest-core`, with a swagger-parser
implementation in `restest-spec`. `restest-spec` is the only module allowed to reference `io.swagger`,
enforced by an ArchUnit test.

The parser produces a **canonical model of our own**: our schema representation, with composition
folded in, version differences normalised once at the boundary, and references resolved lazily.
Nothing downstream knows which OAS version the document was.

**A malformed or unsupported operation is skipped and reported, never fatal.** The run summary states
how many operations were skipped and why. RESTest scored zero on two of eleven APIs in 2026 for the
want of this rule.

## Consequences

- The version traps are handled once, in one place, with tests, instead of being rediscovered in
  every generator.
- Swapping the parser is a module-sized change. This matters if swagger-parser stalls, but it is not
  the reason for the boundary — owning the model is.
- A hostile specification degrades the run rather than ending it.
- Writing a canonical model costs more than using the library's classes directly. Paid once.

## Alternatives considered

- **Use swagger-parser's model directly.** What 1.x does. Its quirks become our semantics.
- **Support 3.2 now via `openapi-processor/openapi-parser`.** Rejected as out of scope; the interface
  leaves the door open. *(The M0.2 amendment walked through that door; the M1.2 amendment walked back
  out and closed it. The door itself — the interface — was never touched by either.)*
- **Write our own parser**, as RestTestGen does. Rejected: a large, permanently unfinished job.

## Amendment (M0.2)

**Superseded by the M1.2 amendment below.** Kept verbatim except for the markers added at that time,
because the reasoning about the ecosystem (the "Why" section) is still accurate — only the resulting
scope decision was later reversed.

**Date:** 2026-09-11

~~**The scope is OAS 2.0 and every 3.x, 3.2 included.**~~ **Reversed at M1.2.** New 3.x minor versions
are tracked as they are published. 4.x stays out of scope for as long as it has no specification
text.

### Why

The original decision rested on two claims about the state of the ecosystem, and both were wrong.

*"No maintained Java parser supports 3.2."* `openapi-processor/openapi-parser` describes itself as an
OpenAPI 3.2, 3.1 and 3.0 parser and JSON Schema validator for Java, and is actively developed. It is
the same library this ADR listed as a rejected alternative.

*"The swagger-parser issue has been open since November 2025 with no response."* The issue is open,
but it has attracted discussion and was last active in July 2026.

What remains true is the part that matters for the work: `swagger-parser` does not support 3.2 today.

Independently of the ecosystem, the scope itself was too narrow. OAS 3.2.0 was published in
September 2025 and 3.2.1 in September 2026. Excluding it means not supporting the current version of
the only input format the tool requires, which is not a defensible position for a tool whose design
target is "an unknown API".

Note for precision: "every 2.x" is 2.0 alone. There is no 2.1.

### How

The `SpecificationParser` interface does not change. This is the case it was designed for — this ADR
says so in as many words: *"Swapping the parser is a module-sized change. This matters if
swagger-parser stalls."*

- `swagger-parser` remains the backend for 2.0 (by conversion), 3.0.x and 3.1.x, where it is the
  better-tested option and the only one of the two that handles 2.0 at all.
- ~~A second `SpecificationParser` implementation covers 3.2, selected from the document's own
  declared version. `openapi-processor/openapi-parser` is the candidate, to be confirmed against the
  golden corpus at M1.2 rather than on the strength of its README.~~ **This is exactly what M1.2
  reconsidered, and decided against — see that amendment.**
- If `swagger-parser` gains 3.2 support, the second backend can be dropped. That decision costs one
  module and no interface change, which is the whole point of the boundary.
- Until the second backend lands, a 3.2 document must still not be fatal. Design principle 2 and the
  skip-and-report rule below already require that: the run degrades and states what it skipped.

### Consequences

- ~~The tool accepts the current version of the specification format, not the previous two.~~ **No
  longer true after M1.2: see that amendment's Consequences.**
- Two parser backends to keep working, and a version-selection rule at the boundary. That is real
  cost, and it is the cost the interface exists to bound.
- The canonical model absorbs 3.2's additions the same way it already absorbs the 3.0 to 3.1
  differences: once, at the boundary, with tests. Nothing downstream learns which version it was.
- A claim about a third-party library's capabilities has now been wrong once in this repository. The
  amendment states which library and which version were checked, and when, so the next reader can
  re-check rather than inherit.

## Amendment (M1.2)

**Date:** 2026-09-12

**This reverses the M0.2 amendment.** The scope is OAS 2.0 (by conversion), 3.0.x and 3.1.x, one
parser backend (`swagger-parser`). **3.2 is out of scope.** A 3.2 document is still never fatal — it
is skipped and reported, per design principle 2 — it is simply not parsed.

### Why

The M0.2 amendment was right that a maintained 3.2 parser exists. It did not weigh two things heavily
enough:

- **3.2 is new and barely adopted.** 3.2.0 shipped September 2025, 3.2.1 a year later. An "unknown
  API" encountered today overwhelmingly declares 3.0.x or 3.1.x; building and maintaining a second
  parser backend for a version almost nothing emits yet is cost paid well ahead of any benefit.
- **The M0.2 amendment's own stated cost was real, not hypothetical.** It said plainly: "Two parser
  backends to keep working, and a version-selection rule at the boundary. That is real cost." One
  well-tested backend is simpler to build, simpler to keep passing against the golden corpus, and
  simpler for a reader to reason about.

This is a product-scope call, not a correction of a factual error (unlike M0.2, which did correct
one) — 3.2 support can come back the moment adoption justifies it, at the cost of one module, exactly
as the original ADR promised.

### How

- `restest-spec` has exactly one `SpecificationParser` implementation, backed by `swagger-parser`,
  covering 2.0 (by conversion), 3.0.x and 3.1.x.
- A document that declares `openapi: 3.2` (or anything not in that list, including a future 4.x) is
  treated as unsupported: the operations in it are skipped, the run reports how many and why, and the
  run itself does not fail. No version-selection rule is needed because there is only one backend.
- The golden corpus (`restest-spec/src/test/resources/specifications/`) carries exactly one 3.2
  document, `fixtures/unsupported-version/openapi.yaml`, hand-written for this purpose: real-world
  specifications in the corpus predate 3.2, so nothing else exercises the skip-and-report path for an
  unsupported version.

### Consequences

- One parser backend to build, test and keep working, not two.
- The tool does not accept the very latest point release of the input format. This is a deliberate,
  named trade-off, not an oversight — revisit when 3.2 adoption changes the calculus.
- `openapi-processor/openapi-parser`, evaluated for M0.2, is not adopted. Nothing else in this ADR
  changes: the interface remains the boundary, and adding a second backend later is still a
  module-sized change, not a rearchitecture.
