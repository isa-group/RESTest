# ADR-0020: A dictionary is a named list of values with one key, and a strategy is a share of the budget

**Status:** Accepted
**Date:** 2026-09-18

## Context

ADR-0013 names eight sources of input values. Five of them are dictionaries: values matching a
declared format, a dictionary keyed by type, a user's curated file, the values a run has observed,
and a list designed to be refused. It says they are "one class with a different key" and that "the
file format is the same for all of them, and it is a published format". It does not say what that
format is.

The format is the expensive thing to get wrong. Four later increments write files in it — format-aware
values (2.4), request bodies (2.5), a user's own file (2.7b), values harvested from replies (4.2) —
and a published format cannot be quietly replaced once anybody's file is written against it. So it is
settled here, before the first file, and the first real use of it ships alongside so that the shape is
tested rather than imagined.

### The boundary walk moved, and this took its place

2.3 was next in order. It is deferred, on two grounds.

ADR-0013 describes it twice and inconsistently: §3 has it sending both sides of every documented edge
in one strategy, and §4 has it as an *operator* that mutates a request the API already accepted —
which needs the memory of accepted test cases that §5 describes and nothing has built. The §4 reading
is the better one, and its reason is the one that settles it: setting several parameters outside their
documented bounds at once teaches nothing attributable, because an API stops reading at the first
thing it does not like.

And both halves of it only pay off once the oracles that judge them exist (M3.1). Sending exactly
`maximum` is a legal request; today it produces a finding only if the server falls over.

Measured, it is also thin where it matters. Of 4,916 parameters in the fifty-document corpus, 327
declare any limit — `enum` 229, `minimum` 76, `maximum` 47, `minItems`/`maxItems` 6 each,
`minLength`/`maxLength` 5 each, `pattern` 3, and `exclusiveMinimum`, `exclusiveMaximum` and
`multipleOf` **none at all**. In the five APIs the tool is measured on: pet-clinic 25, kafka 2,
flight-search 1, the other two nothing.

A list of deliberately awkward values pays off today instead, because the server-error oracle has
existed since M1.6 and needs nothing new to be useful: a 5xx is a fault whatever was sent.

## Decision

### 1. The file declares its own keying, and there is one dictionary per file

```json
{
  "version": 1,
  "name": "petshop-ids",
  "keyedBy": "operationAndParameter",
  "expects": "acceptance",
  "values": { "getOwner": { "ownerId": [1, 2, 3] } }
}
```

`version` is the version of the *format*. Called that rather than `dictionary` because a field named
after the thing it sits in says nothing, and not `format` because that collides with the `format`
keying.

**Five keyings**, one more than ADR-0013 lists: the JSON type, the declared `format`, **the name of
the shape**, the parameter's name, and the operation and parameter together. The shape's name is the
one ADR-0013 missed and ADR-0012 anticipated when it kept `SchemaReference`'s name — *"the value
dictionary at M2.7 … want[s] it back"*. It is how a whole request body is indexed: the thing worth
keeping is the `Owner`, not any field of it.

**A value is any JSON**, objects and arrays included. Without that a dictionary could not hold a body
at all, which is the use 2.5 and 4.2 need it for.

**One dictionary per file**, because a file states one keying and one expectation and letting several
share a file makes both statements meaningless — and because the file RESTest ships, the file
committed beside a specification and somebody's personal one have different owners and different
lifetimes. `--dictionary` is repeatable and takes a directory, which is how to keep several.

### 2. An operation is named the way the tool already names it

`OperationId`: the declared `operationId`, or `GET /pets/{petId}` synthesised from method and path
(ADR-0012). It is the string the report prints, so writing a dictionary means copying it out of the
output. RESTest 1.x needed three fields — path, method and `operationId` — because it had no such
identity.

The sharp edge is named rather than left to be discovered: adding an `operationId` to a document
changes the synthesised name, and entries written against the old one stop matching. **A dictionary
naming operations the document does not have is reported**, because the alternative is a file that
silently does nothing.

A parameter is named by name alone. One name declared in two locations gets the entry in both — a
documented limit, because a qualifier nobody would use is worse than a stated edge.

### 3. `expects` is written down now, and absent means nobody checked

`acceptance`, `refusal` or `unknown`, stated per file. Absent means `unknown`, which is the ordinary
case and an honest answer rather than a placeholder — ADR-0013 §3 defends exactly that value: *"the
honest intent of a value invented from a schema, because an API may refuse it for a rule the document
does not express"*.

This is `intent` as *data*, settled now because the format is published and adding a field to it later
is a change to everybody's files. It is not `intent` on the test case, which stays out: nothing reads
it until M3.1, `--store` is off by default, and the stored layout already moved in 2.2. What a run
recorded in this version cannot distinguish is a 4xx the tool asked for from one it did not, per
request — the summary reports the count, and that is all.

### 4. A share divides the budget; a weight divides one value

ADR-0013 §2 has two mechanisms and its own notation separates them: `share: 40m` on a strategy, and
`weighted: {observed: 30, example: 20}` between sources. They are different units because they divide
different things.

- **A share** divides the testing time between **strategies**. Its unit is the whole request: one
  request is built one way throughout.
- **A weight** divides **one value** between the sources that answered for it. Its unit is a single
  parameter, so two parameters of one request may come from different sources.

**The share is a proportion, not a duration.** `share: 40m` collides with `--budget` — what should a
ten-minute run do with it? — and pins a strategy file to one campaign length. A proportion lets the
same division serve a thirty-second check and the two-hour campaign M8 will need. **This amends
ADR-0013 §2.**

**Every source is named, the last resort included.** ADR-0013's "constraint-directed construction,
always, as the last resort" is a source like any other; being the last resort is where a strategy puts
it, not a property of it.

Two strategies exist today:

```
nominal   share 75   enum, then the document's samples and defaults, then invention
fuzzing   share 25   the awkward values, then invention for anything they do not cover
```

**Weighted groups are not built.** Nothing meaningful competes yet: the nominal chain is exclusive by
design. The one case where two sources already answer for the same value is a schema with both a
`default` and a sample and no enumeration, and that is **26 parameters of 5,119, every one in a single
document of the fifty**; order resolves them deterministically and defensibly. The real second
competitor is 2.4's format dictionary, and that is the increment where a weighted group earns its
keep.

### 5. Three quarters against one, and a way to say otherwise

The split between ordinary and awkward requests is ADR-0013's own open question, and it says plainly
that it "cannot be argued into place: it is measured". A quarter is a starting point, in one constant,
with `--fuzzing <percentage>` to change it and `--fuzzing 0` to send none.

That option exists because writing the test that compares a run with and without awkward values
proved it had to: there was no way for a user to express "do not send those", and a capability a user
cannot turn off is one they cannot manage. Design principle 1 is "zero configuration to start; full
configuration available", and this is the second half. **This amends ADR-0015.**

### 6. The list we ship is ours

Written here, not copied from RESTest 1.x, which keeps the hard rule against copying 1.x intact with
no exception to argue about and redistributes nobody's data. The idea and the shape are taken — keyed
by JSON type, with a bucket that applies to everything — and the content is wider: empty and
whitespace-only text, a very long string, control characters and a line break, emoji and
right-to-left text, quotes and angle brackets; the int32 and int64 edges and one past each, a huge
decimal, a negative zero, text where a number belongs; the wrong-typed neighbours of a boolean; empty
lists and objects.

## Consequences

- **A value that could not be sent is never chosen.** `RequestBuilder.canBeSentFrom`, added in 2.2,
  filters the dictionary's offerings. That matters more here than anywhere: a list of awkward values
  is full of exactly the values that cannot fill a gap in a path or cross into a header, and one of
  them winning the draw would have the whole attempt thrown away.
- **The same rule closed a pre-existing bug.** Invention could produce an unsendable value of its own
  — `maxLength: 0` or `maxItems: 0` on a path parameter — and the operation was counted as testable
  while every request it made was discarded, for the whole run. It now says it has no value to offer,
  and the operation is reported as untestable. A test that pinned this as a known gap now asserts it
  is closed.
- **What a seed means has changed, deliberately.** A run now decides first what kind of request it is
  making, and that decision draws on the run's randomness before anything else. Every number written
  down before this version names a different run. The test that exists to make this impossible to do
  by accident was re-baselined on purpose, which is what taking the decision looks like.
- **The summary gained a line**, because a quarter of the requests being refused on purpose would
  otherwise read as an API turning away ordinary traffic.
- **A dictionary is an interface, not a file reader.** The file-backed one is one implementation; the
  values a run observes in replies (4.2) will be another, filled by a listener on the event stream and
  never asked of the store, as ADR-0013 §5 requires. Nothing that asks a dictionary a question knows
  which kind it holds.
- `ValueRequest` gained the shape's declared name, without which the `schema` keying would be
  decoration. It survives a reference being resolved and is dropped when a source steps into a piece
  of the value, like the samples beside it.
- The shipped file is a resource, which the native image of 7.3 will have to be told about.

## Alternatives considered

- **One file holding several dictionaries.** More comfortable for a user who wants one place to look,
  and it makes `keyedBy` and `expects` per-entry rather than per-file. Rejected: the file RESTest
  ships cannot live inside the user's anyway, so there is more than one file in play regardless, and a
  repeatable option plus a directory covers the comfort without a second shape in a published format.
- **Both shapes accepted.** Rejected for the reason a published format exists: two ways of writing the
  same thing cannot be withdrawn later without breaking files that are not ours.
- **Fuzzing as another link in the chain of sources.** Much the smallest change. It is wrong twice: a
  source that always answers would win every value, and fuzzing one parameter among good ones teaches
  nothing an API's first refusal does not already end.
- **Hard-coding the awkward values in Java** and leaving the file format to 2.7. Faster, and it gives
  up the half of the value that matters — that somebody can add the values their own API falls over on
  without touching the tool. Design principle 8.
- **Copying 1.x's `fuzzing-dictionary.json`.** Same research group, same licence, and it would keep
  continuity with 1.x's published experiments. Rejected because it needs an exception to "no code from
  1.x is copied" that nobody has written, for about forty values that took an hour to better.
- **Adding `intent` to the test case now.** It is ADR-0013's design and this is the first increment
  that deliberately sends values it expects to be refused. Rejected as the mechanism-before-its-
  consumer mistake ADR-0013 names twice: no oracle reads it before M3.1, and the summary's count is
  the honest interim.
- **Weighted groups now.** Half an hour of work, and weights over a list of one.
