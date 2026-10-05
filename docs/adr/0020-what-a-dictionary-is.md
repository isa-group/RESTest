# ADR-0020: A dictionary is a named list of values with one key, and the plan decides what each list is for

**Status:** Accepted, amended at M2.7c, M2.4, M2.10a, M8.3, on 1 October 2026 and at 2.1
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

```yaml
version: 1
name: petshop-ids
keyedBy: operationAndParameter
values:
  getOwner:
    ownerId: [1, 2, 3]
```

**YAML, not JSON.** A dictionary is the part of this tool somebody is most likely to write by hand,
and the specifications it sits beside are written in YAML. JSON is a subset of YAML, so nobody has to
be told which to write and a file produced by a script is read the same way. It costs `restest-gen` a
third-party dependency it did not have, which an architecture rule now names — the same treatment
every other library in this project gets — and it buys one thing beyond legibility: a key written
twice is refused rather than allowed to replace itself quietly, which the JSON reader could not do
because a duplicate key means something different in a stored interaction.

It costs one thing too. YAML has kinds JSON does not, so an unquoted `2026-09-18` is a *date* rather
than a piece of text. Such a value is refused with a message naming the problem, rather than being
turned into something plausible.

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

### 3. A dictionary says nothing about what the API will make of its values

A first version of this record had a field on the file saying whether its values should be accepted
or refused. It was wrong twice over, and both are worth writing down because the mistake is an easy
one.

**A dictionary cannot know.** A value can be perfectly good and the request still refused, for a rule
about some *other* parameter that the value knows nothing about. Whether a request is accepted is a
property of the request, not of one value in it.

**And our own list could not have made the claim honestly.** It was marked as values meant to be
refused, and it contains an empty word, a zero, an empty list and an empty object — every one of
which a great many APIs accept quite happily. Whether an empty word is acceptable depends on the
parameter it fills, which a list indexed by the kind of value does not see.

So there is no such field. What a list is *for* is decided by **the plan that names it**, which is
ADR-0013 §2's own shape: a strategy lists its sources by name, and a dictionary carries a name. The
plan knows what it is building; the file does not.

There is no plan to read yet, so the built-in one names one list — `fuzzing`, the one RESTest carries
and any a user adds under the same name — for the requests that push at the API, and gives every
other list to ordinary requests. M2.10 is where the plan becomes a file, and where one list can feed
two strategies, or two lists one strategy, which is the point of naming them.

**And `intent` still stays out of the test case**, for the same reason as before: nothing reads it
until M3.1b. It is now clearer what it will say. A request built to push at an API is not expecting a
refusal — a refusal is a perfectly good answer and often the right one — so its intent is ADR-0013
§3's third value, *I do not know*. That refines §4, which says such a request's intent "records that
a refusal cannot be attributed to any one parameter": true, and no longer needing a value of its own
to say it.

### 4. What is asked first, and why an enumeration is not asked at all

Among the lists feeding ordinary requests, the order is how much each source knows about **this**
value:

```
the closed list of values the document says it accepts     nothing overrides this
a list keyed by operationAndParameter or by parameter name knows about one value
the document's own samples and stated default
a list keyed by schema, format or type                     knows about a kind of value
whatever can be invented from the shape
```

Two of those positions were each got wrong once and are worth keeping the argument for.

**An enumeration is not ranked against anything.** ADR-0013 §2 wrote it as `exclusive: [enum]  # if
it answers, the choice is made`, and that still holds: where a document names the only values an API
will take, everything else is a value it has said is not allowed - a dictionary somebody wrote
included. A first attempt put dictionaries in front of it, and a list keyed by parameter name then
sent a value outside the enumeration on every request for that parameter.

With one exception, which is the same one every source here makes: an enumeration none of whose
values can be put where the value goes - all of them empty, in a path - is read as no enumeration at
all, and the run falls through to whatever else can answer. The choice is between sending something
the document did not sanction and never testing the operation at all, and the second is worse: the
document has contradicted itself, and the operation is still there.

**A shape is about a kind, not about one value.** A document declares a shape once and however many
parameters refer to it get the same one, so a list written for `Owner` says less about *this*
parameter than the parameter's own sample does. That keeps ADR-0019's rule - a parameter's own sample
is the most particular thing a document says about it - true for dictionaries as well. A first
attempt had `schema` on the other side.

### 5. A share divides the budget; a weight divides one value

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
nominal   share 75   the lists written for one value, then what the document says,
                     then the lists written for a kind of value, then invention
fuzzing   share 25   the awkward values, then invention for anything they do not cover
```

Shares are counted against each other rather than out of a hundred. Three strategies asking for 1, 1
and 2 divide the budget into quarters exactly as 25, 25 and 50 would, which is what lets several
lists of awkward values share one quarter between them without the arithmetic rounding it into
something else.

**Weighted groups are not built, and this increment is the strongest argument yet for building them.**

At the time this was planned, nothing competed: the nominal chain was exclusive by design, and the one
case where two sources answered for the same value was a schema with both a `default` and a sample and
no enumeration — **26 parameters of 5,119, every one in a single document of the fifty**, resolved by
order deterministically and defensibly.

Reading a user's dictionaries into that chain created a real competitor, which the review of this
increment demonstrated. A `type`-keyed list of values somebody believes in **replaces** invention for
that kind of value, because the chain is exclusive: a file offering two surnames for `string` makes
every string parameter in the API send one of those two, for the whole run. That is literally what an
exclusive chain means and literally what the file asks for, so it is not wrong — but it is not what
somebody adding "a few good names" expects either, and the format document now says so in as many
words.

Left exclusive here all the same. A weighted group would be a second mechanism arriving in the same
increment as the format it configures, with its numbers hard-coded because the file that supplies
them is a later increment; and the honest answer to "how much of the time should your surnames be
used instead of an invented word?" is a number nobody has measured. **M2.10 owns it**, after 2.4
brings the format dictionary that competes for every string with a declared `format`; until this
increment, neither the weighted group nor the file that configures it belonged to any increment at
all.
The advice until then is the one the format document gives: key a list to the parameters you mean,
and a list keyed to a whole kind of value will be the only thing sent for that kind.

### 6. Three quarters against one, and a way to say otherwise

The split between ordinary and awkward requests is ADR-0013's own open question, and it says plainly
that it "cannot be argued into place: it is measured". A quarter is a starting point, in one constant,
with `--fuzzing <percentage>` to change it and `--fuzzing 0` to send none.

That option exists because writing the test that compares a run with and without awkward values
proved it had to: there was no way for a user to express "do not send those", and a capability a user
cannot turn off is one they cannot manage. Design principle 1 is "zero configuration to start; full
configuration available", and this is the second half. **This amends ADR-0015.**

### 7. The list we ship is ours

Written here, not copied from RESTest 1.x, which keeps the hard rule against copying 1.x intact with
no exception to argue about and redistributes nobody's data. The idea and the shape are taken — keyed
by JSON type, with a bucket that applies to everything — and the content is wider: empty and
whitespace-only text, a very long string, control characters and a line break, emoji and
right-to-left text, quotes and angle brackets; the int32 and int64 edges and one past each, a huge
decimal kept to its last digit, text where a number belongs; the wrong-typed neighbours of a
boolean; empty lists and objects. A negative zero was on that list and is not: the value model
normalises it to zero, which the list already carries, so it was a value that could never have
reached the wire as written.

## Consequences

- **A value that could not be sent is never chosen, by any source.** `RequestBuilder.canBeSentFrom`,
  added in 2.2, now filters every source that picks a value from a list somebody else wrote: the
  document's samples, its enumerations and defaults, the dictionaries, and invention. That matters
  most for a list of awkward values, which is full of exactly the values that cannot fill a gap in a
  path or cross into a header — but a document is free to allow an empty word among the values of a
  path parameter too, and a source that did not filter would have every request for that operation
  thrown away.
- **Whether an operation can be tested is no longer a property of the document alone.** Invention is
  asked up to eight times for a value that can be sent, so a shape that usually produces a sendable
  value and occasionally does not — an object in a path with one optional property — is declared
  untestable about once in every few hundred runs, with a message that is then not quite true. The
  alternative is worse in the common case: reasoning about which shapes can only produce unsendable
  values means re-deriving, in the generator, what the request builder decides, and the two would
  drift. The pinned counts in the corpus tests are therefore pinned against a seed, which they
  already were.
- **The same rule closed a pre-existing bug.** Invention could produce an unsendable value of its own
  — `maxLength: 0` or `maxItems: 0` on a path parameter — and the operation was counted as testable
  while every request it made was discarded, for the whole run. It now says it has no value to offer,
  and the operation is reported as untestable. A test that pinned this as a known gap now asserts it
  is closed.
- **What a seed means has changed, deliberately.** Building a request now begins by deciding what
  kind of request it is, and that decision draws on the run's randomness where nothing used to — after
  the operation has been chosen, before any value has been. Every number written down before this
  version names a different run. The test that exists to make this impossible to do by accident was
  re-baselined on purpose, which is what taking the decision looks like.
- **The summary gained a line**, saying how many requests were pushing at the API rather than trying
  to work. It says only that, because that is what is known: whatever those requests earned — a
  refusal, an acceptance, or the API falling over — is not something the tool can claim in advance.
- **Which oracles can judge a request depends on how it was built, and the design already carries
  that — through the intent, not through the strategy.** The question is worth answering here because
  it looks like it needs a new mechanism and does not. A request that pushes at an API cannot be
  judged by the two oracles that turn on whether it deserved to be accepted - though every oracle
  about the *reply* still applies to it, schema conformance included, which is how such a request
  already earns findings today; one built from values somebody believes in can be judged by "this
  should have been accepted"; one built by breaking exactly one thing can be judged by "this should
  have been refused". Those three are exactly ADR-0013 §3's three intents, and
  §3 gives three reasons for carrying the intent rather than a list of oracles: the intent is small
  and still while the catalogue is large and moving, one strategy produces several intents, and
  `restest recheck` has to be able to apply an oracle invented *after* a run to that run — which a
  stored list of oracle names would forbid. Whether a plan should additionally be able to *configure*
  which oracles run per strategy, as M3.4 does per operation, is a separate question and an open one.
- **A dictionary is an interface, not a file reader.** The file-backed one is one implementation; the
  values a run observes in replies (4.2) will be another, filled by a listener on the event stream and
  never asked of the store, as ADR-0013 §5 requires. Nothing that asks a dictionary a question knows
  which kind it holds — which is why a dictionary says for itself whether it is about one value or a
  whole kind of them, rather than having it read off a keying only the file-backed one has. A first
  attempt worked it out by asking what class the dictionary was, and the observed-values dictionary
  would have landed on the right side of the rule by luck.
- `ValueRequest` gained the shape's declared name, without which the `schema` keying would be
  decoration. It survives a reference being resolved and is dropped when a source steps into a piece
  of the value, like the samples beside it.
- The shipped file is a resource, which the native image of 7.3 will have to be told about.

## Alternatives considered

- **One file holding several dictionaries.** More comfortable for a user who wants one place to look,
  and it makes `keyedBy` per-entry rather than per-file. Rejected: the file RESTest
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
- **A field on the file saying whether its values should be accepted or refused.** What the first
  version of this record decided, and wrong for the two reasons in §3. Kept in the alternatives
  because the mistake is natural: the file is where the author is, so it feels like the place to say
  it — but what the author knows is what the values *are*, and what the tool needs to know is what
  they are *for*, which is the plan's business.
- **A field saying what the list is for — `use: ordinary | fuzzing`.** The obvious repair, and it
  puts in the file a decision the plan already owns. Two strategies drawing on one list could not be
  expressed, and the file would have to be edited to change how a run uses it. Naming sources in the
  plan is ADR-0013 §2's design and costs nothing extra.
- **Copying 1.x's `fuzzing-dictionary.json`.** Same research group, same licence, and it would keep
  continuity with 1.x's published experiments. Rejected because it needs an exception to "no code from
  1.x is copied" that nobody has written, for about forty values that took an hour to better.
- **Adding `intent` to the test case now.** It is ADR-0013's design and this is the first increment
  that deliberately sends values nobody sensible would send. Rejected as the mechanism-before-its-
  consumer mistake ADR-0013 names twice: no oracle reads it before M3.1b, and the summary's count is
  the honest interim.
- **Weighted groups now.** Half an hour of work, and weights over a list of one.
- **Recognising a strategy built to be refused by its name.** What the first version did, and wrong:
  a strategy's name is a dictionary's name, which comes out of somebody else's file and may be
  anything at all — including `nominal`. A strategy says for itself whether it is pushing at the
  API, and which lists it draws on is the plan's decision rather than the file's.
- **Accepting members a dictionary file does not define.** The lenient reading, and it re-creates the
  failure this format spends a paragraph preventing elsewhere: a misspelled `keyedBy` would load
  without complaint and then quietly do nothing. Unknown members are refused, which can be relaxed
  later without breaking anybody's file; the reverse cannot. A key written twice is refused for the
  same reason, which is something the YAML reader can be asked to check and the JSON one could not.

---

## Amendment (M2.7c)

**Date:** 2026-09-20

**A list reaches inside a request body, a place is named by the way down to it, an operation answers
to two names, and an entry that could never be used is said out loud before the run starts.**

### Why

The format shipped at 2.7a could only speak about two kinds of place: a parameter, and the request
body as a whole. Measured against the priority corpus, that is under half of what there is to speak
about. Counting every place a request has - each parameter, each body, and every path inside either
of them, which is what `WhereAValueCanGo` now enumerates - the five documents have **553 places**
between them, and a list could fill **247**: the 200 parameter names and the 47 bodies. Everything
else loaded and did nothing. It fills **435** now, the remaining 118 being places the document
settles by itself. It is worst where the parameters are few and the bodies large:
notebook-manager 7 places of 37, gestao-hospital 35 of 111, pet-clinic 41 of 109.

The other half was not refused. It loaded, sat in the file looking useful, and did nothing, which is
the exact failure this format spends paragraphs preventing elsewhere. The cause was one line: what
fills the inside of an object was asked only of the document, never of the dictionaries.

Two things made fixing it urgent rather than tidy.

**M2.5b is built on it.** The memory of what an API returned is described as dictionaries under the
keyings this format already has — "one leaf by its name, a whole resource by its shape" — and the
leaves it would fill are the leaves of request bodies. Landing 2.5b first would have produced a
mechanism that could not reach its consumer.

**And the first real use of this format is a file somebody generates.** A dictionary is the part of
the tool most likely to be written by a model reading the specification, and a model reading a
specification writes entries for properties, because that is where the interesting values are. Every
one of them would have been ignored.

### How

**A place is named by the way down to it.** A parameter by its name, the body by `body`, and a piece
of the body by the path from it: `body.owner.email`, `body.tags[].label`. `[]` stands for every
element of a list, there being no one element a value could be meant for. This is what `keyedBy:
operationAndParameter` matches, and for a parameter or a whole body the path is the name, so every
file written against 2.7a still means exactly what it meant.

`keyedBy: name` matches **the last step alone**, wherever it turns up — a parameter called `email` and
an `email` four levels inside a body. That difference is the point of having both keyings: one means
this value and no other, the other means this kind of name anywhere. It is also what 2.5b needs.

A parameter has pieces too, and they are named the same way: `tags[]` for every element of a list
parameter, `filter.city` for a property of an object one. Nothing here is special to bodies; bodies
are only where most of the pieces are.

**This changes what four of the five keyings do, and the record should say so.** No file means
anything different by what it says - `operationAndParameter` reads exactly as it did, because for a
parameter or a whole body the path is the name - but what four of them cover grows.
A list keyed by `type`, by `format` or by `name`, and the `any` bucket, are now asked for every piece
of every body as well as for the top of it, so a file written against 2.7a reaches further than it
used to — a `type`-keyed list of two surnames fills every piece of text in every body as well as
every text parameter. The version stays 1 and nothing warns on load, because what those files ask
for has not changed, only how much of a request it covers; the format document's warning about a
list keyed to a whole kind of value now says where it reaches.

`ValueRequest` carries both, rather than one being derived from the other. An answerer wants
different ones: a list of good e-mail addresses matches on the name, and somebody who means one
value and no other writes the path. Deriving either from the other at the point of use would put the
same parsing in every answerer, including the ones outside this repository that ADR-0008 invites.

**An operation answers to two names.** Its `operationId`, and `GET /pets/{petId}` — the second
accepted now even where the document declares the first. A file is written from the specification,
often without running anything, and the rule "use the identifier, unless there is none, in which case
build this string" is one step of reasoning that can be got wrong silently. With both accepted there
is no reasoning: whoever writes the file can always use the method and the path. The names are turned
into the one the run prints as the file is read, so nothing downstream has two names to think about.

This is two ways of writing the same thing, which §"Alternatives considered" warns cannot be
withdrawn later. Taken deliberately: the cost is one rename at load, and what it buys is that the
commonest way this format will be produced has no sharp edge in it.

**The whole body beats the pieces of it.** Where a file gives both `body` and `body.city`, the whole
body is sent as written. Somebody who writes a body whole means that object — the reason to write one
is that its fields make sense together — and poking a value into it would destroy exactly that.
Mixing the two is a proportion rather than a precedence, which is M2.10's business.

**An entry that could never be used is reported when the file is read**, in one line per file,
grouped by reason and naming up to three of each:

```
restest: ids.yaml: 5 of its 8 entries will never be used: 1 for no such operation in this API
         (ownerId in getOwnerRenamedSince), 1 for a piece of a body that is supplied whole, which
         is sent instead (body.city in addOwner), 3 for no such parameter or piece of a body in
         that operation (postcode in addOwner, firstName in addOwner, body.nonsense in addVisit)
```

Four things earn a line: an operation the document does not have; a place the operation does not
have; a place the document settles by itself; and a piece of a body that some list this run holds
supplies whole. All four are knowable from the document, so they are said before a request is sent
rather than after a run has been spent on them.

A document settles a place three ways, and the third is the commonest by far: it declares the whole
list of values that place accepts, it says the API only ever sends that property back, or **it writes
the whole body out in full** as a sample - in which case that body is sent as the author wrote it and
nothing inside it is ever asked for. Of the 553 places in the priority corpus, **118 are settled**,
and 92 of those are the pieces of kafka-rest-proxy's twelve bodies, every one of which the document
exemplifies. Somebody generating a file for that API learns that before the run rather than after.

A fifth line says when one file writes one operation under both of the names it answers to. That is
what accepting two spellings costs: the duplicate-key check cannot see it, because the two keys are
different strings. Where both give a value for the same place the one under the identifier is kept,
whichever the file wrote first.

Two things are left unjudged, because saying nothing is safe here and saying the wrong thing is not.
Where the document runs out — a shape the parser could not read, or the point at which one starts
repeating itself — nothing below that is judged, though everything above it still is. And a name the document declares twice in one operation, where
one entry feeds both, is not judged either: what settles one of them need not settle the other.

An object that merely allows properties it does not name is not such a case: a value is only ever
asked for under a name the document writes down, so an entry for any other name goes unused however
willing the API would be to receive it.

### Consequences

- **A run's summary says nothing about this**, and that is deliberate. The alternative considered was
  a tally at the end of every run of which entries were actually drawn on. It reports one thing the
  check above cannot — a valid entry for an optional parameter that a short run never happened to
  include — and that is a fact about the budget rather than about the file. It would also have to be
  kept per value inside a body, where nothing today records which source filled which leaf.
- **A body's recorded origin still names only whoever assembled the top level.** A body whose every
  leaf came from a dictionary is recorded as invented, because a body is one value with one origin.
  Saying otherwise means an origin per leaf, which is a change to what a stored interaction is; it is
  worth doing when something reads it, and nothing does yet.
- **An enumeration now beats a sample inside a body too**, as it already did at the top level: what
  fills the inside of an object is now the same ordered list of sources that fills a parameter, minus
  invention. One order, one explanation, rather than two that have to be kept in agreement.
- **A request built to push at the API still fills the inside of a body from the document.** Which
  values such a request should push with below the top level is a real question — today a body sent
  by the fuzzing share is whatever the type-keyed list holds for an object, which is `{}` — and it is
  the plan's to answer, not this amendment's.
- **What the document says can be filled and what a run fills are not quite the same number, and
  the difference is an old ceiling this made visible.** Putting a value at each of the 435 and
  counting the requests that carry it gives **415**. All 20 of the others are in one document,
  gestao-hospital, under `body.location.location` and `body.location.position`: invention stops
  adding optional properties at the depth where it stops building, and this walks deeper than that.
  Teaching the check those limits would mean re-deriving in one place what the generator decides in
  another, and the two would drift - the same argument this record already makes about which values
  can be sent. The 20 are left unreported and written down here instead.
- **A place the document settles is now named as such wherever it is**, inside a body as well as on
  a parameter, because what fills the inside of an object is the same ordered list of sources that
  fills a parameter.
- **A pointer to a shape is followed before anybody is asked about it.** Asking first handed every
  source a shape with nothing in it, so a closed list of allowed values on the far side of a pointer
  looked like no list at all and a dictionary answered over it - which is the one thing §4 says
  nothing may do. Found by review, with the check and the behaviour disagreeing about it, which is
  what made it visible.
- **A choice between shapes settles nothing**, however closed one of its branches is: what is asked
  about is the choice, whose own list of values is empty, so a list somebody wrote is used there and
  is not reported as dead.
- **Being too deep to build is not a reason to stop asking.** Following a pointer before the
  question is put meant a shape the document named and the same shape written out where it is used
  behaved differently at the depth where invention stops: the first declined outright, so a body
  with a required property that deep became untestable and an entry written for it was ignored.
  Nothing in the fifty-document corpus reaches that depth through a name, which is why it took a
  review to find; at that depth the sources are now asked and only invention gives up.
- **The shape of a generated file changes.** Anybody producing one from a specification should write
  `body.city` where they used to write `city`, and `body` only when the object has to be coherent as a
  whole. The format document says so in as many words.

## Amendment (M2.4)

**Date:** 2026-09-20

**RESTest ships no list keyed by `format`. The keying is a user's to fill.**

§5 says weighted groups wait for M2.10, *"after 2.4 brings the format dictionary that competes for
every string with a declared `format`"*. That file was not built.
[ADR-0022](0022-the-characters-a-value-is-made-of.md) §2 gives the reasoning; in one line, the chain
is exclusive until M2.10, so a list filed under `uri` would make every one of the corpus's 569 web
addresses one of half a dozen fixed strings for a whole run, while a value built from the shape is
different every time and correct every time.

**Nothing in this format changes.** `keyedBy: format` is one of the five keyings, it works exactly as
written, and a file of real account numbers for `format: iban` still beats anything RESTest would
invent. The only difference is that the list RESTest carries is still just the one, `fuzzing`.

What §5 loses is its example. The competitor that makes a weighted group worth having is now the one
this record already documents - a `type`-keyed list of somebody's own replacing invention wholesale
for a whole kind of value - rather than a shipped file competing for every declared format. M2.10
still owns the mechanism, and still has a case to answer.


## Amendment (M2.10a)

**Date:** 2026-09-22

**§5 described an order that is now written in a file, and the order it described is not the one
the file ships with.** [ADR-0023](0023-the-campaign-file.md) is where that lives now; this records
what changed for anybody reading §5 and expecting the code to match.

§5 says the lists written for one value in particular are asked before the document has its say and
the lists written for a whole kind of value after it. That ranking was worked out from each list's
keying and hard-wired. A plan names sources instead, so:

- **The ranking survives, but only among the lists themselves.** `dictionaries: given` asks the
  particular before the general, which is the one piece of ordering no plan has to state.
- **Where they sit against the document's own samples is the plan's to say.** The plan RESTest
  ships puts every handed-over list in the same weighted group as the document's samples, at half
  the weight of the group. A list keyed to a whole kind of value therefore competes with the
  document's sample of a parameter, which under §5 it would have lost to outright. Somebody who
  wants §5's arrangement writes it: name the list before `source: example` or after it.

**§6's "three quarters against one" is now the `share` of the plan's two strategies**, and
`--fuzzing` adjusts them rather than being the source of the number. §5's distinction between a
share and a weight is untouched and is exactly what the format is built on: a share divides the
run's time between strategies, a weight divides one value between the sources that offered one.

## Amendment (M8.3)

**Date:** 2026-09-27

**An operation with nothing under it is an empty operation, and a run says in its report which
lists it read and which it refused.** Found when 8.3's dictionaries campaigns turned out to have
measured the shipped tool under the dictionaries' names.

### What happened

All eleven dictionary files in the harness repository had at least one operation written like this:

```yaml
values:
  GET /oops:
    # nothing to fill: every place is settled by the document
```

YAML reads a key with only a comment under it as `null`, not as an empty object. The reader wanted an
object there, so each file was refused whole, with `the parameters of 'GET /oops' is not an object`.
The run went on without it, as §1 and the format page promise, and the refusal was one line on the
standard error of each run. Nothing else — not the summary, not `report.json` — said a list was
missing, and a run made without the list finishes exactly like a run made with one that did not
help. The campaign scored the tool with its shipped plan under each dictionary's name.

### Decision

1. **`null` where a set of entries belongs reads as empty**: under `values`, and under an operation
   in a file keyed by `operationAndParameter`. Nothing else could have been meant there, since a
   value only ever appears inside a list, and it is the natural way to write "nothing here" in YAML.
   `values: {}` and `GET /oops: {}` were already accepted, so this makes the two spellings agree.
2. **`null` where a list of values belongs is still refused**, and the refusal now says how to write
   what was probably meant: `[]` for no values, `[null]` to send null. There the two readings are
   both plausible — `any:` in a file keyed by type could mean either — and guessing would be wrong
   about half the time without anybody being told.
3. **A file that cannot be read is still refused whole, and it still never ends the run.** Half-reading
   a file is what §1 and the section on the file refuse, and nothing here argues for it: the
   problem was never that the file was refused but that nobody could see it had been.
4. **A run says which lists it holds and which it refused, in both reports.** Two events,
   `DictionaryRead` and `DictionaryRefused`, are published beside the operations the run will skip.
   The summary on the screen repeats every refusal after the list of skipped operations, and
   `report.json` carries a `dictionaries` object: `read`, one `{name, from}` for every list the run
   holds — RESTest's own included — and `refused`, one `{from, reason}` for every file or directory
   named that gave the run nothing, and for RESTest's own list should this build fail to read it. A
   harness that means to run with a file checks that it is under `read` and that `refused` is empty.
   *Held* is deliberately not *drawn on*: which lists each kind of request asks is the plan's
   business, and a run told `--fuzzing 0` holds the list RESTest carries and never asks it. Saying
   which lists the plan will draw on is a larger question and is left for 2.1; what this settles is
   the failure 8.3 met, a file that was never read.
5. **Two quiet cases are said.** A file that is read and holds not one value is named as adding
   nothing, and an operation written with nothing under it that the API does not have is named as
   stale — the count of unused entries could not see either, since neither has any entries.

### Alternatives considered

- **Refusing a run whose dictionary cannot be read.** It would have caught 8.3 on the first night.
  It also turns a typo in advice into no run at all, which is the opposite of principle 2, and a
  harness that wants that strictness can have it by reading `refused`. Not taken.
- **Skipping only the operation that cannot be read, keeping the rest of the file.** Kinder, and
  it would have saved 8.3 as well. It is a larger change to a published format a week before the
  freeze, it reopens §1's argument against half-read files, and with refusals now in the report it
  buys little. Left for 2.1 if a real file ever needs it.
- **A switch.** ADR-0025 asks for one on every behaviour lever. This is not one: it decides whether
  a file is read, not how a request is made, and there is no experiment in which refusing an empty
  operation is the variant worth measuring.

## Amendment (`--fuzzing` removed)

**Date:** 2026-10-01

**§6's way to say otherwise is now the plan, not an option.** §6 gave the share of awkward values a
command-line option, `--fuzzing`, because there was then no other way to stop sending them. The plan
file of [ADR-0023](0023-the-campaign-file.md) has since become that way, and the option is removed
before v2.0 is tagged, for the reasons in [ADR-0015](0015-command-line-contract.md)'s amendment of
the same date. The quarter itself stands, as a line of the plan RESTest carries. Where the M8.3
amendment's fourth point gives "a run told `--fuzzing 0`" as a run that holds a list and never asks
it, a plan with no strategy that pushes at the API is now the example.

## Amendment (2.1): what a body made by a POST is filled with

**Date:** 2026-10-04

**In the body of a `POST`, three times in ten, a value the API returned is passed over for every
property that is not an identifier, and that property is invented instead.**

### Why

Inside a body, RESTest asks the sources that know something first — the closed list the document
states, its samples, what the API has returned, the lists somebody handed over, the default — and
invents a value only when none of them answers
([ADR-0021](0021-how-a-request-body-is-built.md) §1). For most of a body that is right. For a body
that makes something it is often wrong, because what the API returned names something that already
exists. A registration asked for a user name and an e-mail address is given the name and the
address of a user the API has just listed, or of one a registration was accepted with
([ADR-0021](0021-how-a-request-body-is-built.md), M9.7 amendment), and the API answers that the
user already exists. The request never reaches the code it was sent to test. An analysis of
campaigns on ten APIs found APIs on which, without dictionaries, no creation was ever accepted, and
this was one of three reasons; the other two are the defaults below.

### What changes

- **Decided once for each body.** When invention builds the body of a `POST`, it draws once, with
  probability `generation.freshWhereMadeChance` — 0.3 by default — whether to fill this body
  afresh. Filled afresh, every property inside it is still asked of the same sources in the same
  order, but an answer that is a value the API returned — in a reply, or in a request it accepted —
  is passed over, and the property is invented. Where its name or description implies a kind
  ([ADR-0022](0022-the-characters-a-value-is-made-of.md), M9.6 and 2.1 amendments) and the
  document gives it no sample, it is invented as that kind as often as
  `generation.impliedFormatChance` says: a fresh e-mail address where an e-mail address goes, half
  the time by default.
- **Identifiers are untouched.** A property named the way one identifier or several are written —
  `id`, `ownerId`, `pet_id`, `tagIds` — is given what the API returned, exactly as before, because
  it names the existing thing the new one belongs to: a pet made for an owner the API has never
  heard of is refused for the opposite reason.
- **Every other source answers as it did**: a closed list, a sample the document wrote, a list
  somebody handed over, a default. None of them is a copy of something the API holds. One
  consequence is left as it is: where the draw among the sources falls on the memory and its answer
  is passed over, the property is invented rather than drawn again among the others, so in a body
  filled afresh a sample or a handed-over list is chosen a little less often than in another body.
  Drawing again among the rest was weighed and left for after the measurement.
- **Nothing else is touched.** The body of any other method and a parameter of a `POST` are filled
  as they were. So is a `POST`'s body when the answer chosen for it as a whole is the memory's — one
  the API returned, with one value changed — rather than invention's. With the switch off, or for a
  request it does not apply to, nothing is drawn, so a run started from the same seed draws exactly
  what it drew before.
- **`generation.freshWhereMade`, on by default, switches it off**, as
  [ADR-0025](0025-settings.md) §4 asks, and [the switches](../switches.md) list it with the ideas
  that reach operations.

### Why only a share, and only a `POST`

Some of what a creation needs is a value that exists without being an identifier — the name of a
category a product has to be filed under, a code the API checks against a list of its own — and the
memory is where those come from. Passing it over every time would trade one refusal for another;
three bodies in ten leave seven to be filled as before. The share is a number in the settings,
because it is a decision rather than a fact. `POST` because it is the method a request that makes
something nearly always uses. A `PUT` replaces a thing, and the values of the thing it replaces are
the right ones to send it. A `POST` that makes nothing - a login, a search, a token refreshed - is
filled afresh as often, and loses a value it needed three times in ten at most; telling the two
kinds of `POST` apart from the document was weighed and left for after the measurement.

### Two defaults change with it

`generation.optionalPropertyChance` goes from 0.5 to 0.8, and `generation.optionalBodyChance` from
0.5 to 0.9, for the same reason: bodies that make something were refused for what they lacked.
Documents under-declare `required`, so a property an API cannot do without is often written as
optional, and it was left out of half the bodies. And a body is optional unless the document says
otherwise, in OpenAPI 3 as in Swagger 2, though almost no API accepts a request without the body it
describes, so half the requests to such an operation were refused before anything was tested. The
body's chance applies to every method that can carry one - `POST`, `PUT`, `PATCH`, `DELETE` - and to
none that cannot.
[ADR-0025](0025-settings.md)'s 2.1 amendment records both.

### Measured

Together with the five name rules of [ADR-0022](0022-the-characters-a-value-is-made-of.md)'s 2.1
amendment, not on its own: campaign `20261004-142720` against `20261004-000542` at 2.0.0, in the
configuration submitted to the competition, sixteen APIs, 20 minutes, two runs each. On the ten APIs
that get no dictionary, operations answered with a 2XX went from 160.5 to 168, unique server
failures from 706 to 821.5 and branch coverage from 31.1% to 32.1%; four operations of
user-management and market's registration, never accepted in a run of 2.0.0, were accepted in both
runs. flight-search's logins, a `POST` that makes nothing, were accepted about as often as before:
263 and 240 times, against 257 and 257. A campaign with the two defaults back at a half, which would
have told the switch from the defaults, was prepared and not run, by the maintainer's decision, so
no figure here belongs to any one of the changes alone.
[ADR-0024](0024-the-competition-version.md)'s amendment of 5 October gives the whole result.
