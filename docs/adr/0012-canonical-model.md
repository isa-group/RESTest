# ADR-0012: The canonical model is immutable records and sealed types, and says so when it cannot represent something

**Status:** Accepted
**Date:** 2026-09-12

## Context

Every part of the tool after the parser works on a representation of the API: the scheduler picks an
operation, the generator fills in its parameters, the engine sends the request, an oracle judges the
response against the declared shape, the report names what failed. That representation is the widest
interface in the project, so how it is built decides how much else is forced.

RESTest 1.x has no such representation. It passes `io.swagger.v3.oas.models.*` objects around
directly — mutable POJOs from a third-party library, with nullable fields, no OpenAPI 2.0/3.0/3.1
reconciliation, and no way to say "this construct was not understood". Three consequences followed
and are all visible in the audit: the parser version became load-bearing for code that has nothing to
do with parsing; every consumer re-implemented its own null checks; and a construct the tool could
not read was indistinguishable from one the document did not contain.

ADR-0007 already fixes the boundary — the parser sits behind our own interface. This ADR fixes what
comes *through* that boundary.

## Decision

**Records and sealed interfaces.** `ApiModel`, `Operation`, `Parameter`, `RequestBodyModel`,
`ResponseModel`, `Server`, `SpecificationIssue` and the ten schema variants are records.
`CanonicalSchema` and `JsonValue` are sealed, so a generator or oracle that handles all but one case
fails to compile rather than falling through a `default` and producing nothing.

**No nulls, and no shared mutability.** Mandatory components are null-checked in the compact
constructor; absent scalars are `Optional`; collections are copied on the way in and handed back
unmodifiable. Maps and sets that carry document order — an object's properties, a body's media types
— are copied through `LinkedHashMap`/`LinkedHashSet` rather than `Map.copyOf`, whose iteration order
is randomised per JVM and would make two runs of the same specification generate different requests.

**`restest-core` carries its own JSON value model.** `JsonValue` is sealed over null, boolean,
number, string, array and object. Numbers are `BigDecimal`, normalised so `1` and `1.0` are the same
value.

**The model refuses what contradicts itself or could never be sent, and the parser catches it.**
`minLength` above `maxLength`, a negative count, a non-positive `multipleOf`, an exclusive bound that
leaves no room, two shapes for one media type, two names for one header, one parameter declared
twice in one location, a path template with no parameter to fill it, a required body with no media
type, two operations under one identifier: all `IllegalArgumentException`. The rule behind the list
is that the model never holds a value whose own lookups would have to answer "whichever was declared
first", and never one that describes a request nobody could assemble. From M1.2 the parser turns that
exception into a `SpecificationIssue`, skips the operation and reads the next one.

The promise stops at what a constructor can compare, and deliberately so. `type: integer,
minimum: 1.2, maximum: 1.8` is accepted here and satisfied by no value; deciding that needs the
solver that arrives at M5.2, not a compact constructor. `SchemaChecks` says as much, so that nothing
downstream mistakes the check for a satisfiability guarantee.

**What cannot be represented is recorded, not lost.** `UnsupportedSchema` carries a printable reason;
`AnySchema` is the different, legitimate case of a schema that declares no type, and `NothingSchema`
the third case of a document that was understood and said no value is acceptable —
`additionalProperties: false`, which is one of the most common things a strict API states about
itself. `ApiModel.issues()` carries everything the document said that we could not use, and travels
into every report.

**A schema may be referred to by name rather than copied.** `SchemaReference` names a shape that
`ApiModel.schemas()` holds. This is what makes a *recursive* shape representable at all — held by
value, a comment with replies or a category with sub-categories would have to contain itself — and it
is what keeps the name, which inlining destroys and which the operation dependency graph at M4.1, the
value dictionary at M6.1 and `restest explain` all want back. Resolution is explicit and one step
deep: a document may point one name at another, and a model that followed chains silently would loop
for ever on one that points at itself.

**Media types are resolved the way HTTP resolves them, not compared as strings.** Exact type first,
then `type/*`, then `*/*`. The wildcard is not exotic: it is what a Swagger 2.0 document's default
`produces` becomes, and a literal comparison would have an oracle quietly validate nothing for a
large share of real specifications.

**Facts the document supplies are kept, even when using them comes later.** Server variables carry
the defaults OpenAPI requires, so `Server.resolvedUrl()` can produce a base URL without asking the
user — design principle 1. An operation carries its own `servers` override, so requests for an API
that serves uploads from another host do not go to the wrong origin. A response header carries
`required`, without which "the API did not send the header it promised" cannot be told from "the API
never promised it". A parameter carries the media type of a `content`-declared value, so a
JSON-in-query parameter does not cost us the whole operation. A `SpecificationIssue` carries the
operation it concerns and what it cost — skipped, degraded, or a problem with the document itself —
because "this operation is not being tested" and "it is being tested with less than the document
says" are different facts, M1.2 has to report both, and a reader who cannot tell them apart cannot
judge the run.

**Exclusive numeric bounds are numbers, not flags.** That is the JSON Schema 2020-12 and OpenAPI 3.1
shape, and it is lossless in both directions: a 3.0 document's `minimum: 5` plus
`exclusiveMinimum: true` converts into it, while the reverse modelling gives a 3.1 document writing
`exclusiveMinimum: 5` with no `minimum` nowhere to put the number.

**Every operation has an identifier.** The declared `operationId` when there is one; otherwise
`GET /pets/{petId}`, synthesised from method and path.

## Consequences

- Two runs, two APIs or two versions of one API coexist in one JVM with no interference, which is
  design principle 6 at the level of the data and what the library use case needs.
- A consumer depending on `restest-core` gets the model and nothing else: no parser, no HTTP client,
  no JSON library, no solver, no database driver.
- Exhaustive `switch` gives us a compile error, in every downstream module, the day a schema variant
  is added. That is the mechanism by which M2.1's composition support will be prevented from being
  half-adopted.
- A run against a partly unreadable document is honest: the report can say which operations were
  skipped and which shapes were guessed at.
- It costs conversion work in `restest-spec`, once, in the module whose job that is.
- No convenience accessor collapses the four numeric bounds into a lower and an upper one. A bound
  without its strictness is the wrong answer to the question M2.3's boundary walk asks, so the
  caller reads the components that say which it is.
- Records with many components are verbose to construct. Mitigated with a few factories and
  `with…` methods; if M1.2 shows the parser wants builders, they arrive there rather than being
  guessed at now.
- `UnsupportedSchema` is a standing invitation to leave gaps unfilled. The mitigation is that its
  reason is printed in reports, so an unread construct is visible to users rather than only to us.
- Rejecting duplicate operation identifiers is the one refusal scoped to the whole document rather
  than to one operation, which puts a real obligation on M1.2: the parser must make identifiers
  unique — recording a `SpecificationIssue` when it does — before it constructs the model. The
  alternative, a model that answers its own lookups with whichever of two operations came first,
  would corrupt per-operation settings, stored interactions and `restest recheck` silently, which is
  worse than a loud failure the parser is required to prevent.

## Alternatives considered

- **Pass the parser's own types around.** What 1.x does. Free today, and it makes the parser
  version part of everyone's contract, leaves the 2.0/3.0/3.1 differences in every consumer, and has
  nowhere to put "not understood". Rejected by ADR-0007 already.
- **One `Schema` record with every field nullable.** Shorter to write and it moves the whole burden
  onto readers: "if `items` is set it is probably an array" is a convention, not a type. No compiler
  help, and no exhaustiveness when a variant is added.
- **Classes with builders instead of records.** More construction comfort, at the price of writing
  `equals`, `hashCode` and immutability by hand in forty places, which is exactly where value-type
  bugs live.
- **Inlining every `$ref` instead of a `SchemaReference` variant.** Simpler to consume — every
  schema is complete where you find it — and it cannot express recursion at all, throws away the
  name, and multiplies the size of a model whose schemas are shared by dozens of operations. Adding
  the variant later would break every exhaustive `switch` in every downstream module, which is the
  cost this ADR claims to be avoiding.
- **Jackson's `JsonNode` instead of our own `JsonValue`.** Tempting, and it would put a JSON library
  in the published surface of every consumer of `restest-core`, to be kept in step with whatever
  version they already use. `JsonNode` is also mutable, which would put a hole in the middle of an
  otherwise immutable model.
- **Throw nothing; accept contradictory constraints.** Keeps the model permissive and moves the
  contradiction downstream, where each generator and oracle rediscovers it with less context to
  report it from. Design principle 2 asks for the failure to be *reported*, which needs it to be
  detected somewhere first.
