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
| `PUSHING` | a request built from the list of awkward values |

§3 said "about four", and the fourth is the one §4 describes for fuzzing: *its intent records that a
refusal cannot be attributed to any one parameter*. It is also what lets the memory of accepted
requests leave awkward requests out, which the value's origin could not do - a body invented from
awkward leaves records one origin for the whole body, and that origin is invention.

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
stored before reads back as `UNKNOWN` with no change, which is what every request built then was.

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
for anything to rely on it later.

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

A systematic walk over every place and kind, without repetition, would reach each pair sooner. It
was not built: at the request rates the local measurements see, drawing covers a few hundred pairs
within seconds, and a walk needs state per operation that changes every time a newer accepted request
arrives. If 8.5 shows the draw leaving pairs unvisited, it is the obvious next step.

### 6. The switches are a group of their own

`mutation.*` holds a switch per family, a switch per operator, how many accepted requests are kept,
and how long an oversized word and list are - sixteen settings. ADR-0025's table put the switches
in `generation.*`; this amends it (see ADR-0025, Amendment M10.1). A person looking for "switch the
mutations off" finds them in one place, 10.2's shape operators have somewhere to join them, and
`generation.*` keeps saying what an invented value may look like. Switching both families off is
two lines, and it makes the mutating strategy build every request from its sources.

## Measurement

*To be completed from the measurement below before merging; see the roadmap's 10.1 notes.*

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
