# ADR-0018: A choice between shapes is one more shape

**Status:** Accepted
**Date:** 2026-09-17

## Context

M2.1a folded `allOf` — a value that has to be several shapes at once — into the shapes the canonical
model already had, because a combination can be worked out once and stored. A choice cannot: when a
document says a value is either a number or a text, no single shape is both, so the alternatives have
to be carried.

The field does not agree on how, which is why this is written down before it is built. Read from
source: EvoMaster holds the alternatives in a gene whose active branch its search mutates, scored by
coverage; RESTler takes the first branch and records supporting the rest as future work; CATS makes
separate payloads per branch; and Schemathesis generates through `hypothesis-jsonschema`, a
dependency whose canonicaliser rewrites `oneOf` into an `anyOf` of `allOf`.

### What it costs us today, measured

`oneOf` and `anyOf` appear in two of the fifty corpus documents — GitHub and Graphhopper — and in
none of the five APIs the tool is measured against. `not` appears in none of the fifty. No choice
offers more than four branches.

The number that decides this record is not how many schemas use a choice but how many requests one
costs. Asking the generator directly:

| Document | Operations | Refused today, and why |
|---|---|---|
| GitHub | 743 | **6**, every one of them the `workflow_id` path parameter, declared `oneOf: [integer, string]` |
| Graphhopper | 4 | none |
| OhsomeDataAggregation | 122 | none |
| BingWebSearch | 1 | none |

Those six — `actions/get-workflow`, `disable-workflow`, `enable-workflow`,
`create-workflow-dispatch`, `list-workflow-runs`, `get-workflow-usage` — are not degraded. They are
not attempted at all, because `RandomTestCaseGenerator` refuses an operation whose required parameter
carries a shape it cannot read. Everything else a choice touches in the corpus is a response body,
and ADR-0014 judges replies against the specification document itself rather than against this model,
so those cost nothing that is sent and nothing that is judged.

**Six operations is the whole demonstrable effect of this increment today.** It is a small claim, and
it is a true one. The rest is a report that stops saying it could not read 179 shapes.

## Decision

One thing changes.

### `ChoiceSchema` joins the sealed `CanonicalSchema`, making eleven shapes

```java
record ChoiceSchema(SchemaMetadata metadata, List<CanonicalSchema> alternatives)
        implements CanonicalSchema { }
```

`oneOf` and `anyOf` both become it. Nothing RESTest does acts on the difference — both mean "a value
of one of these" — and the one place it matters, judging a reply, reads the document rather than this
model. Keeping a word nothing reads would be a component with no consumer, which ADR-0013 records
this project having built twice already at a cost. Nothing persists the distinction, so an increment
that finds a use for it re-reads the document and adds it then.

Branches keep their `SchemaReference` rather than being written out in place, which is the opposite
of what ADR-0012's M2.1a amendment does for a combination's halves, for a plain reason: a combination
cannot be worked out until its halves are known, and a choice is complete without knowing what it
chooses between. It is also what keeps a recursive choice expressible — a comment that is either a
text or a thread of comments.

Three things follow that an implementer would otherwise have to decide alone:

**A choice one of whose branches cannot be read cannot be read either.** Not a choice over the
branches that survive: dropping one is a narrower claim about what the API accepts, made by us and
not by the document. `SchemaConverter` already refuses an enumeration for exactly that reason — "a
shorter list is not no list" — and this is the same rule.

That applies to what reading can see. A branch that is a name resolves later, by design, so a choice
between a readable shape and a name that turns out to be unreadable is still built. Two things catch
it instead: the operation is reported as carrying a shape we could not fully read, because the walk
that decides that follows names; and a provider that draws the unreadable branch tries the others
rather than giving up, so the choice still yields a value. Neither is the same as refusing the choice
outright, and pretending otherwise would be claiming a guarantee the model cannot make.

**What the schema states outside the choice narrows every branch.** A document may list the
properties a body must always carry and, beside them, a choice between the shapes the rest may take.
The value has to satisfy both, so each branch is combined with what was stated outside it, using the
same merge M2.1a wrote. Reading only the branches would leave a shape accepting values the document
does not — which is exactly the trade this record refuses for the discriminator, and it would be no
better here.

**A choice that is one half of a combination is reported as unread, and `AllOfMerger` must say so
explicitly.** Its shape-merging is a chain of `instanceof` tests rather than a switch over the sealed
interface, so an eleventh shape does not break it: a choice would fall through every arm to the last
one, which answers `NothingSchema` — *the document says no value is acceptable* — about a document
that said nothing of the kind. That arm needs a guard and a test before the variant exists, because
no document in the corpus would catch it.

**A provider asked for a value picks one alternative and generates from it,** recorded as provenance
like every other value. Not the first, which would make what gets tested depend on the order a
document was written in. Whether to go further and spend budget on each branch deliberately, as CATS
does, is a share of the budget and therefore the scheduler's, under ADR-0013 §6.

## What is deliberately not in this increment

**The discriminator.** Three schemas in the corpus carry one and all three stand alone, on an
inheritance base rather than beside a choice. Making them readable would change **no request, no
value and no verdict**: all 115 operations they affect are responses, which ADR-0014 judges against
the document. It would only stop a report saying so.

And it would not be free. Ohsome's `GeoJsonObject` names `type` as its discriminating property and
**does not declare `type` among its own properties** — neither does any of its nine subtypes. Reading
it as an ordinary object therefore produces a GeoJSON shape with no `type` member, which every
consumer of GeoJSON rejects. That turns an honest "this shape could not be fully read" into a shape
that is quietly wrong, which is the wrong direction for design principle 2.

What would make it worth doing is building the choice the document implies — `GeoJsonObject` really
is one of a dozen geometries — but the subtypes are found by searching the document for the schemas
whose `allOf` names this one, rather than by reading a list, and that changes what a named shape
means. It needs its own measurement first: point the tool at an API with such a hierarchy and count
how many replies refuse the base. Until then a discriminator keeps costing its shape, which is
honest, and this record says why rather than leaving the omission to be rediscovered.

**`not`.** No document in the corpus uses it, expressing "anything except this" needs a shape the
model does not have, and every consumer would be left asking what to send for *not* a string.

## Consequences

- **Eleven shapes.** `CanonicalSchema`'s own documentation, `SchemaConverter.hasUnsupportedConstruct`,
  `RandomValueProvider`, the two sealed switches in `AllOfMerger`, `CanonicalSchemaTest` and
  `SchemaSatisfaction` all stop compiling until they handle it. `AllOfMerger`'s `instanceof` chain
  does not, which is why the decision above names it explicitly.
- **Six operations become testable**, in a document outside the priority corpus. Operations reported
  as carrying a shape we cannot fully read should fall from 299 to about 120, the remainder being the
  115 discriminator cases this increment declines and five that are not composition at all. Measured
  in the pull request rather than predicted here.
- **A named shape can now be a choice**, so anything walking `ApiModel.schemas()` has one more case —
  the dependency graph at M4.1 will want to match property names through one.
- **A choice between a single shape is allowed**, because nothing stops a document writing one and
  refusing it would cost the shape. Only a choice between *no* shapes is refused, by the constructor:
  it describes nothing.
- **Nothing here needs the deferred backlog opened.** A choice is read from the document, not
  inferred; a branch is picked at random, not learned.

## Alternatives considered

- **Keeping `oneOf` and `anyOf` apart,** as two shapes or as one with a kind. Rejected: no consumer.
  The precedent for a kind exists — `NumberSchema` holds `integer` and `number` that way — but there
  the kind decides what value to generate, and here it would decide nothing.
- **Shipping the discriminator half first,** on the grounds that it is cheaper. Rejected on the
  measurement above: it changes nothing that is sent, and its premise is false for the document
  supplying 114 of its 115 operations.
- **No new shape at all: picking a branch while reading the document,** as RESTler does. The cheapest
  possible change, and rejected because the model would then state something the document did not.
- **A payload per branch,** as CATS does. Not rejected — but it is a strategy, and a strategy needs a
  representation first.
- **Rewriting `oneOf` into an `anyOf` of `allOf`,** as `hypothesis-jsonschema` does. Correct and
  clever: each branch becomes itself-and-not-the-others. Rejected because it needs the negation this
  record declines to represent, and because it produces shapes nobody wrote, which is what makes a
  failure hard to explain.
- **Carrying the discriminator on the choice.** Rejected: no document in the corpus pairs them, so it
  would be a place built before anything exists to put in it. If one turns up, RESTest may generate a
  value whose discriminating property contradicts the branch it came from, and that is the amendment.
