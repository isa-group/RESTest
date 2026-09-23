# ADR-0007: The parser sits behind our own interface; OAS scope is 2.0, 3.0.x and 3.1.x

**Status:** Accepted, amended at M0.2, in #303 and in #328, reversed at M1.2
**Date:** 2026-09-11 (amended 2026-09-11, reversed 2026-09-12, amended 2026-09-16 and 2026-09-23)

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

## Amendment (#303)

**Date:** 2026-09-16

Not tied to a milestone: a correction made when the symptom surfaced, recorded here because it is a
standing constraint on how this module may use the parser library, not a one-off fix.

**`restest-spec` does not name the package `io.swagger.parser`.** It uses the readers behind that
package's front door instead, and its module declaration requires `swagger.parser.v3` and
`swagger.parser.core` rather than `swagger.parser`. Everything else in this ADR stands: the scope,
the boundary, the canonical model, the skip-and-report rule.

### Why

This ADR's Context opens by describing what happens when a project lets `swagger-parser` arrive on
its own terms: it "arrives transitively at 2.0.25 or 2.0.27 depending on Maven's resolution order".
The same shape of problem exists one level further down, and it is worth writing where the first one
is written.

`swagger-parser` is not one jar. Three of the jars it brings put classes into a single package,
`io.swagger.parser`: the current parser, which contributes `OpenAPIParser`, and two 1.x-era jars kept
for reading documents written in the older format. A package supplied by more than one automatic
module is ambiguous, and the two Java compilers in common use disagree about what to do with that.
The one Maven runs picks a jar and carries on. The one the Eclipse-based editors run refuses the
import:

```
The package io.swagger.parser is accessible from more than one module:
swagger.compat.spec.parser, swagger.parser, swagger.parser.safe.url.resolver
```

So `./mvnw verify` was green while an editor showed errors on the same file, and the editor was
right — about the library, not about this project. Two attempts to settle it in the build failed
before the cause was understood, and both failures are instructive. Holding the older jar at
`runtime` scope changes nothing: the module path the compiler receives is identical with and without
it. Excluding that jar changes nothing either: the package comes from three jars, not two, and
removing one of them leaves the ambiguity intact. No arrangement of scopes or exclusions can fix a
package that several jars insist on sharing.

### How

`OpenAPIParser` is a façade with no logic of its own: it asks `OpenAPIV3Parser` for the readers the
library has registered and gives the document to each until one returns something. `restest-spec`
does that itself, over classes in packages that only one jar contributes to, so the ambiguous package
is never mentioned. Which reader handles which document is unchanged, because it is the same list in
the same order.

The constraint this leaves behind, for whoever next upgrades the library: **check whether
`io.swagger.parser` is still split before importing anything from it.** A package can stop being
split, and this restriction would then be pointless; it can also spread to another package, and the
same symptom would come back somewhere new. Compiling `restest-spec` with the Eclipse compiler is
what answers the question — the Maven build will not, by design, because it resolves the ambiguity
silently.

### Consequences

- The boundary this ADR exists for did its job. A library shipping a package from three jars at once
  is exactly the kind of thing that, in 1.x, would have spread; here it reached one class in one
  module and stopped.
- One rule to remember at the boundary that the build does not enforce. It is written in a comment
  beside the code and here; an architecture test forbidding any reference to `io.swagger.parser`
  would enforce it, and has not been written.
- A green build is not evidence that the module graph is sound. It was green throughout.
- The remaining untidiness is the library's, not ours: `swagger-parser-core` and `swagger-parser-v3`
  still have module names derived from their filenames, which the build warns about, and which
  remains a ceiling on ever publishing `restest-spec` as a strict JPMS artifact.

## Amendment (the operations the parser drops)

**Date:** 2026-09-23

**An operation the parser found but could not read is counted among the operations a run cannot
test, and named with them.** The decision above says a malformed operation is skipped and reported,
and that "the run summary states how many operations were skipped and why". What was built kept
the first half: the parser drops the operation and records why, and the screen names it among the
parts of the document that could not be read - if it is among the first five of them. The second
half was not true of it. The count a run begins with, `N of M operations can be tested`, counted
only the operations the parser produced, so a dropped one was not among the M; and the list of
operations a run could not test, which
[ADR-0006's amendment naming what a run skips](0006-event-stream-and-store.md#amendment-naming-what-a-run-skips)
puts in the summary and in `report.json`, held only the ones whose request could not be built.

Now the command counts them and names them, first, with what reading said and where in the
document, from the issues that say an operation was skipped: in the count, after the summary's
verdict, in `report.json`, and in the reason it quotes when nothing at all can be tested - which
used to say that the document described no operation, of a document describing one it could not
read. `ApiModel.unreadableOperations()` is the one place that picks those issues out.

**Kept apart from the generator's list, not added to it.** The generator's list of operations it
cannot test is also what it checks before building a request, and an operation it can build may
share its name with one that could not be read: a document can give two operations one name - an
operation copied under a deeper path, say, whose copy has a gap nothing fills - and the parser
renames duplicates only among the operations it built. Added to that list, the unreadable one
stopped the readable one from ever being sent while it was counted as testable. So the command
reads the parser's issues itself, and the generator's list is what it was. The place in the
document goes with the reason for the same cause: it is what tells the unreadable copy from the
readable original that shares its name.

**The plan is not asked about them, and says so where it matters.** What a plan's filter matches
is an operation, and these never became one, so they are named whatever the plan says. When the
plan leaves nothing to test, it is still the plan that is blamed - the choice is made on what could
be read - with how many more could not be read said beside it; when the operations that were read
were all refused, one of their reasons is the one quoted, with the unreadable ones counted beside
it. A plan naming an operation that could not be read by its identifier is not told the API has no
such operation, and neither is a list of values written for one - it is told the operation could not
be read. By method and path it still is: only the identifier of an operation the parser dropped is
known.

**What is still not counted.** Operations under a path item written as a reference to another part
of the document are not found at all - the parser records one issue for the whole path item - so
they are in neither the count nor `report.json`. And the screen may name an operation that was found
twice: once among the parts of the document that could not be read, which is what it is, and once
among the operations that could not be tested, which is what it cost.

No document of the corpus has such an operation: none of its forty-six, nor of the four
hand-written fixtures.
