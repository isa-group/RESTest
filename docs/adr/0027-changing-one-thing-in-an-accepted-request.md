# ADR-0027: A request the API accepted is sent again with one thing changed, and says what it broke

**Status:** Accepted
**Date:** 2026-09-27

## Context

The 2027 competition counts distinct server failures, told apart by their message. Until now RESTest
built every request from nothing, in one of two ways: meant to work, from what the document and the
user's lists say; or awkward throughout, from the list of values nobody sensible would send. The
first kind rarely breaks anything. The second is usually turned away by the first check it meets,
because an API validates what it is sent before it acts on it, and a request with five awkward
values fails validation on the first of them. The code behind the checks - where the failures a
correct request never reaches tend to live - runs for neither.

ADR-0013 §4 decided the answer in September and nothing had built it: *a deliberate violation is a
mutation of a test case the API accepted*. Take a request that actually returned 2XX, change one
thing, and whatever the API does next is down to that one change. §3 attached a second debt to it:
a test case carries its **intent** - *I believe these values are acceptable*, *I expect this refused
and here is what I broke*, *I do not know* - which M1.6, M2.2 and M2.7a each left out because no
oracle read it. Roadmap row 10.1 takes both, with the operators it lists: drop a required parameter,
send the wrong type, step outside a documented bound by exactly one (the boundary walk M2.3 deferred
here), break an enumeration, break a pattern, send a required parameter where it was not declared
(ADR-0017's fifth adoption), oversize a string or an array, send `null`, send the empty value.

Three questions had more than one defensible answer, and the maintainer settled them on 27 September
before any code was written.

- **Where an operator may change something.** The row speaks of parameters. The priority corpus
  takes most of its input in bodies - fifteen request bodies in pet-clinic, twelve in
  kafka-rest-proxy, ten in flight-search, eight in gestao-hospital - and two of its five APIs state
  no bound, enumeration or pattern anywhere, so an operator confined to parameters would have almost
  nothing to break there. Row 10.2 already lists "a leaf of the wrong kind" and "arrays far longer
  than any limit" for bodies, which are the same operators at a place inside a body.
- **What to do where the document says nothing.** Ten thousand characters where no `maxLength` is
  stated is what reaches a database column's limit and produces a 500; an empty word where nothing
  says a word may not be empty is what reaches code that assumes one is there. Neither breaks what
  the document states, so neither can honestly say *I expect this refused*.
- **Where the switches go.** ADR-0025's table puts "the switches of every mutation and shape
  operator" in `generation.*`, a group whose record already has seventeen numbers about what an
  invented value may look like.

## Decision

### 1. A plan says `mutates: accepted`, and keeps its sources as a fallback

A strategy in a plan may say `mutates: accepted`. Its requests are then made by taking one of the
requests the API accepted for the operation being sent and changing exactly one thing in it. Its
`sources:` stay required, and are what it builds from when it cannot change anything: until the API
has accepted something for that operation, or when nothing in what it accepted can be changed.

The plan RESTest carries becomes three strategies: **nominal 55, mutation 20, fuzzing 25**, the
mutation strategy with nominal's sources as its fallback. The twenty comes out of nominal's
seventy-five, so the share of pushing is untouched and `--fuzzing` still means what it meant. It is
a starting point, screened with the other levers at 8.5.

Keeping the sources has a property worth more than the lines it costs in the file. Nothing is drawn
when nothing has been accepted, so a strategy that falls back draws exactly the numbers the strategy
it copies would; with the operators switched off, the shipped plan sends exactly what the two-
strategy plan sent before, request for request, from the same seed - a test holds that. An ablation
that switches the operators off is therefore measuring the operators and nothing else.

### 2. Four intents, and a record of the change

`TestCase` gains an `Intent` and, when it was made by changing an accepted request, a `Mutation`.

| Intent | Given to |
|---|---|
| `ACCEPTABLE` | nothing yet - see below |
| `REFUSAL_EXPECTED` | a change that breaks what the document states |
| `UNKNOWN` | every request built from nothing, the opening lap's included, and a change the document does not rule on |
| `PUSHING` | a request of a pushing strategy that carries something awkward: a parameter from the list of awkward values, or a body |

§3 said "about four", and the fourth is the one §4 describes for fuzzing: *its intent records that a
refusal cannot be attributed to any one parameter*. It is also what lets the memory of accepted
requests leave awkward requests out, which the value's origin could not do - a body invented from
awkward leaves records one origin for the whole body, and that origin is invention. A request of the
pushing strategy with nothing awkward in it - an operation with no parameters and no body - is the
request any strategy would send, and says `UNKNOWN`; saying `PUSHING` of it would be the false claim
the intent exists to avoid, and it would put requests nothing pushed into the console's count.

`ACCEPTABLE` exists and nothing is given it. §3 says an invented value's *I do not know* becomes *I
believe this is acceptable* "when the value comes from a source that knows the API", and that is
decidable for a parameter from its origin. It is not decidable for a body: a body records one origin
for all of it, and one taken from an earlier reply with one value invented is recorded as derived
from that reply. Claiming acceptability from that would sometimes be claiming something false, and
the oracles that would read it are 3.1's; they can decide what they need when they exist.

`Mutation` names the exchange that accepted the original - its interaction, which is the stored
run's key, rather than the test case, for the reason `ValueOrigin.Derived` gives - the kind of change
by the name its switch has, where it was made, in the same notation a dictionary uses
(`limit`, `body.owner.email`, `body.tags[].label`), and a sentence for a person. `REFUSAL_EXPECTED`
requires one; `ACCEPTABLE` and `PUSHING` forbid one. The changed value's own origin names the
operator; a body keeps the origin it had, since the change is what the record describes.

Both travel in the test case's JSON, which is where the store keeps it, so **the store's layout does
not change**: ADR-0013's Consequences called this "a store layout change", and it is not one. A run
stored before reads back as `UNKNOWN` with no change. The second half is true of every request built
then; the first is the most that can be said of one whose expectation nobody wrote down, a request
that pushed at the API included. The same JSON is what `report.json` quotes beside every fault, so a
server error found by a change now says, in the report, which change found it.

### 3. Two families, and the intent is always true

The operators are divided by what the document says about their result, and each family has a
switch of its own:

| Family | Operators | Intent |
|---|---|---|
| **violations** — the document forbids the result | `dropRequired`, `wrongLocation`, `wrongType`, `outsideABound`, `breakAnEnumeration`, `breakAPattern`, `sendNull`, `sendEmpty`, `oversize` | `REFUSAL_EXPECTED` |
| **probes** — the document does not rule on it | `oversizeWithNoLimit`, `emptyWithNoRule` | `UNKNOWN` |

The row's "oversize" and "send the empty value" each became two operators, one in each family,
split by whether the document states the rule the result breaks. An oversized word is `oversize`
where a `maxLength` is stated and `oversizeWithNoLimit` where it is not; the latter is offered only
where nothing else about the word is stated either - no pattern, no named kind, no closed list -
because ten thousand characters would break those, and the document would then have ruled after
all. `sendEmpty` goes where a fewest length, a pattern, a closed list or a named kind such as `date`
rules the empty word out, where a list has a fewest number of items, where an object has properties
it must have, and outside a body wherever a number or a yes-or-no is declared; `emptyWithNoRule`
goes everywhere else.

That split is what keeps every recorded intent true, which is the one property an intent has to have
for anything to rely on it later. Three things were done to keep it so after review:

- **A list outside the body is never stepped down to nothing.** An empty list in a query string
  disappears from the request, so "one item fewer than the fewest" at a fewest of one would be
  leaving the parameter out and calling it something else; a request the document allows would
  claim to expect a refusal.
- **`null` is not sent where anything on the way to the shape allows it**: the property, the shape a
  name there points at, or any alternative a choice offers. A name that cannot be followed to a
  shape - one pointing nowhere, or round in a circle - is not judged at all, and only
  `dropRequired`, which needs nothing from the shape, may go there.
- **Items that must all differ are not repeated** to make a list one longer, or far longer, since
  that would break a second rule besides the one stepped past.

One exception is known and left: `allowEmptyValue: true` on a query parameter says an empty value
is allowed, and the model does not carry it, so `sendEmpty` on such a parameter would claim a
refusal the document does not promise. No document in the corpus of fifty uses it, and OpenAPI 3.x
recommends against it; it is modelled the day a document needs it.

Some changes can still break a second rule by accident. A word lengthened one past its longest is
lengthened by repeating its own last character, which keeps a pattern or a named kind it satisfied
satisfied as often as anything cheap could, but not always. The intent stays true - two rules broken
is still a refusal expected - and only the attribution to one change is weakened.

### 4. Where an operator goes, and where it never goes

A place is a parameter the accepted request carried, or one property inside its JSON body, found by
walking the body alongside its shape: every property, one element of each list chosen by chance,
as deep as `generation.hardNestingDepth`, and no further than a shape that is a choice between
several or one that allows anything. The body's root is not a place: changing the shape of a whole
body is 10.2.

What never changes, because the change that reached the API would not be the one recorded:

- **a value in the path**, which is never left out, moved or emptied - the address would stop being
  this operation's, and an empty gap is refused before it is sent anyway;
- **a header the client writes itself** - `Accept`, `Content-Type`, `User-Agent`, `Host`,
  `Content-Length` and the rest - which is never left out or moved, because the client would put
  it back;
- **a property the API only ever returns**, which a request does not carry;
- **a body sent as the fields of a web form**, which cannot say `null`;
- **a request that deleted something**, which is never kept to be changed, since what it deleted is
  gone - and a request that was itself a change, or pushed at the API, neither.

`breakAPattern` holds variations of the accepted word against its pattern only when the word is at
most sixteen characters long, and otherwise tries two short fixed words. A pattern is a program the
document wrote, a badly written one - a repetition inside a repetition - can take longer than a run
lasts on an input a few dozen characters long, and the thread it would run on is the one that builds
requests. The sixteen is a safeguard of the same kind as the limits inside the part of the tool that
builds a word to fit a pattern, and stays in the code with them.

`wrongLocation` moves a required query parameter, header or cookie to one of the other two, never
to a place where the operation declares something of that name and never under a name a header or
cookie cannot carry. The request builder learned to write a value the operation does not declare in
that place, in the place's default style; nothing else produces one.

### 5. A kind, then a place, by chance

When the mutating strategy is drawn for an operation, one of the operation's kept accepted requests
is chosen, then a kind of change among those switched on that have somewhere to go in it, then one
of those places. A kind first, so that one with somewhere to go in every value - an empty word -
does not crowd out one with a single place to go. The memory keeps the newest `mutation.acceptedKept`
accepted requests per operation, for the reason the memory of observed values is small: an older one
may name something deleted since.

Nothing is drawn for a change that cannot be made. With every kind of change switched off - both
families, or every operator in them - nothing listens for accepted requests, the mutating strategy
builds every request from its sources, and the seed repeats the run exactly as it did before this
record.

What the API sends back to a changed request is **not learned from**. The memory of observed values
skips those replies: an API that wrongly accepts a name ten thousand characters long and hands it
back would otherwise have it sent again in ordinary requests, which could then be kept as accepted
requests themselves and changed again.

A systematic walk over every place and kind, without repetition, would reach each pair sooner. It
was not built: at the request rates the local measurements see, drawing covers a few hundred pairs
within seconds, and a walk needs state per operation that changes every time a newer accepted request
arrives. If 8.5 shows the draw leaving pairs unvisited, it is the obvious next step.

### 6. The switches are a group of their own

`mutation.*` holds a switch per family, a switch per operator, how many accepted requests are kept,
and how long an oversized word and list are - sixteen settings. ADR-0025's table put the switches
in `generation.*`; this amends it (see ADR-0025, Amendment M10.1). A person looking for "switch the
mutations off" finds them in one place, 10.2's shape operators have somewhere to join them, and
`generation.*` keeps saying what an invented value may look like. With the probes shipped off, one
line - `mutation.violations=false` - switches every change off, and the mutating strategy then
builds every request from its sources.

## Measurement

The five APIs of the 2027 edition, from the benchmark's own images, each restarted before every
run with a fresh results directory; five seeds, sixty seconds, the shipped plan, three arms
alternated seed by seed: both families off, violations only, both families. Measured on 28
September on a machine running nothing else, from the commit this record lands with.

| API | Distinct 5XX by message: off / violations / both | By exception kind | Branches covered | Operations 2XX |
|---|---|---|---|---|
| pet-clinic | 158.6 / **211.2** / 211.2 | 51.8 / **60.8** / 61.4 | 150.2 / **153.4** / 152.4 | 32.8 / 33.0 / 33.2 |
| kafka-rest-proxy | 6.8 / 7.4 / 6.8 | 6.8 / 7.4 / 6.8 | 851.6 / 851.4 / 850.4 | 35.0 / 34.4 / 35.2 |
| notebook-manager | 4 / 4 / 4 | 4 / 4 / 4 | 16 / 16 / 16 | 5 / 5 / 5 |
| gestao-hospital | 3 / 3 / 3 | 1 / 1 / 1 | 55.8 / 54.4 / 58.8 | 18.0 / 17.8 / 18.0 |
| flight-search | 0 / 0 / 0 | 0 / 0 / 0 | 40 / 40 / 40 | 19.6 / 20.0 / 19.8 |

*Distinct by message* keys a failure by operation, status and reply body, with timestamps,
identifiers and runs of digits taken out; *by exception kind* keeps only the body's `title`,
`error` or equivalent, which a value echoed back into the body cannot inflate. Means over five
seeds.

- **pet-clinic** gains a third more distinct server failures by message, and a sixth more by
  exception kind, better on every seed by either count - the lowest seed with violations on beats
  the highest with them off - and the area under the distinct-failure curve rises from 119.5 to
  148.6. The failures are new kinds, not new values: a word sent for a numeric path parameter
  (`MethodArgumentTypeMismatchException`; that kind of change was the first to reach 198 of the
  distinct failures, summed over the five seeds), a list where a body wants a word (`HttpMessageNotReadableException`), one below a stated minimum
  (`ConstraintViolationException`). Branch coverage rises by three, on every seed.
- **The other four** do not move beyond noise. Their failures are few and reached by ordinary
  requests already, and three of them state almost no rule for a violation to break.
- **Nothing is lost**: operations answered 2XX, idle time (1.1-1.7% in every arm) and the count
  of (operation, status) pairs answering 5XX are unchanged, though nominal gives up twenty points of
  share.
- **The probes add nothing over the violations** on any of the five, and on kafka-rest-proxy they
  cost a third of the requests (1,845 a minute against about 2,800), ten thousand characters taking
  longer to answer. **They ship switched off** (`mutation.probes=false`), decided by the maintainer
  on 28 September; the evaluation harness measures them by ablation over longer runs.
- **The intents hold up.** Between 2% (pet-clinic) and 28% (flight-search) of the requests
  expecting a refusal were accepted. Sampled, they are the APIs being lenient - `true` accepted
  where a word is declared, `null` where a field may not be null, a value off a closed list - which
  is what 3.1's *negative data must be refused* will report, not an intent recorded wrongly.
- **How often the mutating strategy's turn changes something depends on the API.** Changed
  requests, violations only, are 15.7% of notebook-manager's requests, 11.7% of pet-clinic's, 6.8%
  of gestao-hospital's, 5.7% of kafka-rest-proxy's and 0.8% of flight-search's, against a share of
  twenty; the rest fell back to building from its sources, because the operation had nothing
  accepted yet or nothing in it that a switched-on kind could change.

## Consequences

- **A run with the shipped plan listens for accepted requests**, as it already listened for observed
  values, so it was already not reproduced from its seed alone; nothing changes about that promise,
  and ADR-0013 §7 already put mutation on the replay side of its table.
- **The console counts requests by what they say they are**: pushing, and changed, the second split
  by how many broke what the document states. It no longer counts pushing by the names of the lists
  values came from, which missed a pushing request whose only awkward value was in its body. That is
  what ADR-0023 §5 anticipated when it declined to publish a key for pushing.
- **3.1 has its consumer waiting.** *Negative data must be refused* reads `REFUSAL_EXPECTED` and the
  change; *positive data must be accepted* needs `ACCEPTABLE`, which it will have to decide how to
  assign.
- **Row 10.2 is narrower**: a leaf of the wrong kind and a list far longer than any limit are 10.1's
  operators at a place inside a body. What stays in 10.2 is structural - the root of the wrong kind,
  an empty body, a body that is not JSON, the wrong `Content-Type`, nesting far deeper than the
  shape, the numeric extremes of every width.
- **Some of what fuzzing finds, a probe finds too.** An API that fails on an empty search term is
  found by the empty word in the list of awkward values and by `emptyWithNoRule`; the test that shows
  fuzzing finding it switches the changes off in its control run.

## Alternatives considered

- **A strategy with `mutates:` and no sources.** Tried in the design and dropped: the plan's
  records, `--fuzzing`, the opening lap and the testability check all assume a strategy has sources,
  and the fallback would have had to borrow some other strategy's, which a plan listing its
  strategies in another order would have changed.
- **Only violations.** Every changed request would then say *I expect this refused*, and the
  oversized word and the empty word where the document is silent - the two changes likeliest to
  reach a database or an unchecked assumption - would not be sent. Chosen instead: both, in two
  families, each switchable.
- **Assigning `ACCEPTABLE` from the origins of the values.** See §2: true for parameters, sometimes
  false for bodies, and read by nothing yet.
- **Mutating requests that pushed.** A 2XX to a request of awkward values is still a 2XX, and a
  change to it is still attributable. Left out because such a request is not one anybody believes in,
  and changing one thing in it would say less than changing one thing in a request built to work.
- **Keeping the switches in `generation.*`.** No amendment, and a group of thirty numbers about two
  different things.

## Amendment (M10.2)

**Date:** 2026-09-28

Row 10.2 adds what §4 left out: changes to the **body as a whole**. An API reads a body and turns it
into something its code can use before any of that code runs, and the reading is code too - a JSON
reader, a binder, a check on the media type - with failures of its own that no change to one value
inside a well-formed body reaches. Seven operators join the eleven, in the same two families, drawn
the same way (§5), switched the same way (§6): eight more settings, seventy in all.

| Family | Operator | Goes to | What is sent |
|---|---|---|---|
| violations | `wrongRoot` | the body | another kind than the declared root, which the shape does not accept: the accepted body inside a list, or the first item of a list; a word, a number, `true` |
| | `emptyBody` | a required body | no bytes at all, under the body's JSON media type |
| | `notJson` | the body | the accepted body's text cut off halfway, or the words `this is not JSON`; a cut that still reads as JSON is not sent |
| | `wrongContentType` | the body | the accepted body's own text under `text/plain`, `application/xml` or `application/x-www-form-urlencoded`, whichever the operation neither offers nor covers with a range |
| | `beyondItsWidth` | any number | past what the format it names holds: one past an `int32`'s or an `int64`'s edges, ten times a `float`'s or a `double`'s |
| probes | `deepNesting` | a body that is an object allowing undeclared members | one more member, holding `mutation.nestingDepth` lists one inside another, ten thousand by default |
| | `extremeNumber` | a number nothing bounds | the largest and smallest its format holds, or where it names none, the edges of `int32`, `int64`, `float` and `double` and just past each, and one past sixty-four unsigned bits; whole numbers only where whole numbers are declared |

Three questions had more than one defensible answer, and the maintainer settled them on 28 September
before any code was written.

- **Where the far too deep value goes.** Replacing the whole body with lists ten thousand deep breaks
  what the document states, so it would be a violation; but a JSON reader bound to a class - Jackson,
  Gson, which is what every API the benchmark runs uses, since it measures coverage with a JVM agent -
  stops at the first bracket with the same complaint `wrongRoot` already earns. A member nobody
  declared is skipped by such a reader, and skipping walks the whole value, which is where a reader
  gives up at its depth limit: a thousand levels for Jackson, two hundred and fifty-five for Gson.
  **Chosen: the extra member.** The document allows members it does not declare and states no depth,
  so it is a probe, and ships off with the others; where `additionalProperties` is `false` or a shape
  of its own, or where the object already has as many members as it may, it is not sent at all.
- **One operator for the edges of numbers, or two.** A number past what its declared format holds
  breaks the document; the largest number an `int32` holds does not, and neither does anything at all
  where the document names no format. **Chosen: two**, split by §3's rule - `beyondItsWidth` a
  violation, `extremeNumber` a probe - so that every recorded intent stays true. A stated bound, a
  closed list or a multiple rules the probe out; a format whose edges the tool does not know
  (`uint8`, `decimal`) rules out both.
- **An empty body where the body may be left out.** No bytes under a JSON media type is read by the
  common readers as no body, and for an optional body the document allows none. **Chosen: only where
  the body is required**, a violation; an optional body sent empty would be the nearest thing to a
  request ordinary generation already sends, and saying it expects a refusal would be false. In the
  five APIs of the 2027 edition that is 34 of the 47 request bodies, and none of kafka-rest-proxy's 12.

**A body can say what text it is sent as.** `BodyValue` gains `sentAs`, the exact text that travels
when it is not the value written out; `value` keeps the accepted body it was made from, so a stored
run shows both. `emptyBody`, `notJson`, `deepNesting` and `wrongContentType` set it. A value ten
thousand levels deep is never built: the writer every value goes through refuses nesting past a
thousand, and everything that compares two values would walk it a level at a time, so the text is
put together from the accepted body's own. `wrongContentType` sets it too, because under a form's
media type the same value written out afresh would travel as the fields of a form - a different
body. The text travels inside the test case's JSON, so, as in §2, **the store's layout does not
change**, and a run stored before reads back with no `sentAs`.

**The body as a whole is a place of its own.** §4's "the body's root is not a place" stays true of
the eleven: the root is offered only to the five operators that change the body as a whole, and to
the two number operators when a whole body is one number. A body sent as the fields of a form is
still left alone by all eighteen, for §4's reason.

**Measured** the way §Measurement measured the eleven: the five APIs of the 2027 edition from the
benchmark's own images, each restarted before every run with a fresh results directory; five seeds,
sixty seconds, the shipped plan; three arms rotated seed by seed - the seven **off**, which is the
tool as 10.1 shipped it, the five **violations** on, which is the default this amendment ships, and
the two probes on as well, with the eleven's two probes still off. On 28 September, with no other
measurement or build running, from the commit this amendment lands with. Means over five seeds:

| API | Distinct 5XX by message: off / violations / +probes | By exception kind | Branches covered | Operations 2XX |
|---|---|---|---|---|
| pet-clinic | 214.0 / **279.4** / 281.2 | 60.8 / **71.8** / 71.6 | 152.4 / 153.2 / 152.0 | 32.8 / 33.0 / 33.0 |
| kafka-rest-proxy | 7.0 / 7.6 / 7.6 | 7.0 / 7.6 / 7.6 | 849.6 / **861.8** / 859.4 | 35.6 / 35.0 / 35.2 |
| notebook-manager | 4 / 4 / 4 | 4 / 4 / 4 | 16 / 16 / 16 | 5 / 5 / 5 |
| gestao-hospital | 3 / 3 / 3 | 1 / 1 / 1 | 35.4 / 36.6 / 36.4 | 15.0 / 15.2 / 15.4 |
| flight-search | 0 / 0 / 0 | 0 / 0 / 0 | 40 / 40 / 40 | 19.6 / 20.2 / 20.4 |

- **pet-clinic** gains a third more distinct server failures by message and a sixth more by
  exception kind, better on every seed by either count - its lowest seed with the violations on,
  270 and 68, beats its highest with them off, 221 and 62 - and the area under the curve by kind
  rises from 53.7 to 61.2. Every one of the five violations earns a 500 nearly every time it is
  sent. By kind the gain is one new exception, `HttpMediaTypeNotSupportedException`, on fifteen
  operations, which `wrongContentType` reaches; the rest are new messages of the kind 10.1 already
  reached, `HttpMessageNotReadableException` - a body missing, JSON cut off, a list for an object,
  a number too large for an `int` - which are distinct failures of the reading code by the
  benchmark's count and one kind by the stricter one.
- **kafka-rest-proxy** answers every broken body with a 400, as it should, and so gains no server
  failure, but covers twelve more branches, better on every seed: the code that reads and refuses a
  body, which no request had reached.
- **The other three do not move.** notebook-manager's failures come with an empty body, so there is
  no message to tell a new one apart by; gestao-hospital's and flight-search's are not in the code
  that reads a body. gestao-hospital covered fewer operations in every arm than in the measurement of
  the eleven that morning - its `POST /v1/hospitais/` never answered 2XX - and the build of 10.1,
  run again that afternoon on one seed, did the same, so what changed is the API's surroundings
  rather than the tool; the three arms here ran under the same conditions as one another.
- **Nothing is lost**: operations answered 2XX, the number of requests, idle time (1.2-1.6%) and the
  (operation, status) pairs answering 5XX are unchanged. The share of requests changed stays where
  it was - 11.4% on pet-clinic, 6.0% on kafka-rest-proxy. What the strategy falls back on is
  operations it has nothing to change in whatever it is allowed - on pet-clinic, six deletions,
  which are never kept, and six lists that carry nothing - and none of them takes a body.
- **The two probes add nothing over the violations** by exception kind on any API, and so they ship
  off with the eleven's; `deepNesting` earns pet-clinic's depth limit on every body it is sent in,
  as a message of a kind already reached.

**Not sent, and why:** `null` as a whole body, which the common readers take for no body, the
failure `emptyBody` already reaches; a body sent as nothing where it may be left out (above); the
far too deep value replacing the whole body (above); and any of the seven in a body sent as the
fields of a form, which cannot be nested, cut short as JSON is, or say `null`, and which the tool
builds only where JSON is not offered.

## Amendment (M8.5)

**Date:** 2026-09-29

**The probes are removed**, by the maintainer's decision on 29 September: they added complexity,
and configuration nobody will use.
- **What goes:** the four probes - `oversizeWithNoLimit`, `emptyWithNoRule`, `deepNesting` and
  `extremeNumber` - their family's switch, `mutation.probes`, and `mutation.nestingDepth`, which only
  `deepNesting` read. Six settings, seventy left.
- **What becomes of this record:**
  - §3's two families become one. Every change breaks something the document states, and every
    changed request expects to be refused.
  - §6's `violations` is the one switch for all fourteen kinds.
  - §2's four intents stay as they are: `UNKNOWN` is still what most requests expect, a step of a
    series among them.
- **A settings file that names a removed key is refused**, like any name that is not a setting. A
  plan written for the probes fails at once rather than quietly running without them.

**The evidence** is row 8.5's screening: the eleven APIs of the 2026 edition, twenty minutes each,
one run per variant, with the probes switched on against the tool as shipped.
- **Unique server failures:** 146 with them and 147 without, erc20 left out. They gained 9 on
  pet-clinic and lost 13 on person-controller.
- **Branch coverage:** 23.8% against 23.2%, better on 5 APIs, worse on none.
- **What they cost person-controller**, which answers them with enormous bodies:
  - it sent 41,000 requests, against 102,000-123,000 in every other variant;
  - its recording grew to 29 GB, against 1-1.5 GB for any other run, and took the benchmark's
    analysis about three and a half hours.
- **features-service** sent 28% fewer requests.
- **Before that**, the sixty-second measurements of 10.1 and 10.2 had found nothing by exception kind
  that the violations had not.
