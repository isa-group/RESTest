# ADR-0007: The parser sits behind our own interface; OAS scope is 2.0, 3.0.x and 3.1.x

**Status:** Accepted
**Date:** 2026-09-11

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

OAS 3.2 exists but no maintained Java parser supports it — the swagger-parser issue has been open
since November 2025 with no response. OAS 4.0 has no specification text and the OAI's own advice is
to stay on 3.x.

## Decision

**Scope: OAS 2.0 (by conversion), 3.0.x and 3.1.x, fully. Not 3.2, not 4.0.**

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
  leaves the door open.
- **Write our own parser**, as RestTestGen does. Rejected: a large, permanently unfinished job.
