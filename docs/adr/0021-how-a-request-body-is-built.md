# ADR-0021: A request body is one more value, and what the API returns is reused leaf by leaf

**Status:** Accepted, amended at M2.5b
**Date:** 2026-09-18

## Context

A quarter of the operations in our corpus take a request body, and RESTest tests none of them. The
generator skips every operation whose body is required, by name, with the reason written out: *"it
requires a request body, and bodies are not invented yet"*. M2.5 is where that stops, and three
things are owed to it before a line is written:

| Debt | Where it comes from |
|---|---|
| Bodies generated at all — the roadmap row says JSON, form encoding, multipart and XML | `ROADMAP.md` row 2.5 |
| An `Accept` header built from the media types the operation's own 2XX responses declare | ADR-0017, adopted item 2 |
| The samples a document writes down for a request body, deliberately left unread | ADR-0019 §5 |

The question that forced this record is narrower and more interesting than any of the three. Bodies
are structured values, and there are two obvious ways to produce one: build it from the schema the
document declares, or take a resource the API has already handed back and change it. The second is
attractive — a resource that came out of the API is valid by construction, including in the ways the
document never wrote down — and it is what several of the tools we are measured against are built
around. Which of the two is the mechanism and which is the refinement is a decision that shapes the
generator, the dictionaries and M4, so it is taken here rather than in a pull request.

### What the corpus says

Measured over the 46 documents of the golden corpus, 5 of them the priority corpus.

| | Priority | Community | Total |
|---|---|---|---|
| Operations | 150 | 1,267 | 1,417 |
| Take a body | 47 (31%) | 293 (23%) | 340 (24%) |
| **Body required — untestable today** | **34 (23% of the API surface)** | 69 | 103 |
| Bodies declaring a sample of their own | 12 | 137 | at least 149 |
| Nesting depth: median / p90 / max | 1 / 3 / 5 | 1 / 3 / 5 | — |
| Properties reachable: median / p90 / max | 5 / 14 / 23 | 3 / 12 / 97 | — |

Bodies are shallow and small. The p90 body is three levels deep with a dozen properties, which is
well inside what the value provider chain already builds for an object-typed parameter.

**Which media types a body is actually offered in**, counted once per operation:

| Media types offered | Operations |
|---|---|
| JSON alone | 300 |
| form + JSON + XML | 21 |
| form alone | 9 |
| JSON + XML | 7 |
| text alone, `*/*` alone, multipart alone | 1 each |

XML never appears without JSON beside it. Supporting it wins no operation anywhere in the corpus.
Multipart wins exactly one, `POST /pet/{petId}/uploadImage`. Form wins nine, and costs a
serialisation rather than a generator, because a form body is an object with a different way of
being written down.

**And what a resource the API returns is worth as an input**, which is the question above, measured
two ways over the same bodies:

| Granularity | How much of the corpus it reaches |
|---|---|
| The whole resource — the body's named shape is also returned by some 2XX | 16 of 44 named bodies in the priority corpus (36%); 50 of 87 in the community one |
| One leaf — a property name inside the body also appears inside some 2XX response | **1,789 of 1,952 leaves (92%)**; every leaf of 218 of 294 bodies (74%) |

Per priority API, the whole-resource figure is: gestao-hospital 6 of 6, notebook-manager 2 of 2,
pet-clinic 8 of 15, **flight-search 0 of 9, kafka-rest-proxy 0 of 12**. A mechanism built on whole
resources tests nothing new on two of the five APIs we care about most. The same two are fully
covered at the level of the leaf.

Two facts sit beside that measurement. A resource an API returns is not a resource it accepts:
identifiers, timestamps and link blocks are read back and refused or ignored on the way in, so a
returned resource has to be projected onto the request schema before it can be sent — which means
having the request schema anyway. And nothing is observed until something has been created, and on a
freshly started deployment the thing that creates it is a body built from the schema.

`readOnly` is how a document says which properties those are, and it is rare: 17 properties across
12 bodies in the whole corpus. `SchemaMetadata.Access` already carries it; `RandomValueProvider`
does not yet read it, which has cost nothing so far because objects were only ever built for
parameters.

### What the tools we are measured against do

Not what they advertise; what their documentation and their source say about the mechanism.

| Tool | How the body is built | Where observed data enters |
|---|---|---|
| [RESTler](https://github.com/microsoft/restler-fuzzer) | A grammar derived from the schema; custom payloads keyed **by path inside the body**; example payloads as the base | Dynamic objects: an identifier from an earlier reply is substituted **into one leaf** of the body |
| [Schemathesis](https://schemathesis.readthedocs.io/en/stable/explanations/data-generation/) | Property-based over the schema, with a serialiser per media type | The examples phase sends a declared sample **and fills what it does not cover with generated data**; the stateful phase follows declared links |
| [EvoMaster](https://dl.acm.org/doi/10.1145/3321707.3321815) | The body is a tree of genes; resource-based sampling and resource-based mutation inside an evolutionary search | Mutation is of its own population of test cases, not of resources the server returned |
| [RestTestGen](https://profs.scienze.univr.it/~ceccato/papers/2020/icst2020api.pdf) | Schema, examples, random | A response dictionary of observed values, consulted per input; error tests are mutations of nominal test cases that succeeded |
| [MINER](https://www.usenix.org/system/files/sec23fall-prepub-129-lyu.pdf) | Request templates | Collects parameter-value pairs from requests that were accepted and learns which values to propose |
| [Pythia](https://arxiv.org/abs/2005.11498) | Grammar, seeded with valid requests | Mutates valid requests by injecting noise that preserves syntactic validity |
| RESTest 1.x | **Walks the request schema** and looks up each leaf in a file of values observed in earlier runs, falling back to the schema's sample, the body's sample, then a value for the type | Observed values, **per leaf**, keyed by name and path |

One pattern runs through all of them, and it is the opposite of the obvious reading: **no tool uses
an observed resource as the unit of construction.** Every one of them builds the shape from the
schema and lets what was observed enter at the leaves. The tools that do mutate whole structures
mutate their own population of requests, not representations the API returned. The measurement above
says why: the leaf is where the reuse is, by a factor of nearly three.

## Decision

### 1. A body is asked for the way a parameter is asked for

`ValueProvider` does not change and gains no sibling. A body is a `ValueRequest` whose schema is the
body's schema and whose `shape` is the name the document gave it, put to the same chain that fills
parameters. `RandomValueProvider` already walks an object property by property, asking the chain
about each one by name — which is exactly the mechanism every tool above arrives at, and we have it
already.

`ValueRequest` carries a location, and a body is not in the path, the query, a header or a cookie.
It gains one: a body is the most permissive place a value can go — it can be null, nested, or an
object — and the rules that depend on location, such as a value that cannot be written into a path,
answer accordingly rather than by exception.

The constant goes on `ParameterLocation`, the enum the specification model already uses, rather than
on a new type of generation's own. What that costs is worth writing down, because it is a real cost:
three of the things in the model that consume the enum refuse the new case outright — a `Parameter`
cannot be declared in the body, a `ParameterValue` cannot record one, and a body has a media type
rather than a parameter style — so the model now carries a member that most of its own users reject,
and every later exhaustive `switch` over a location has to write a case that can never occur. What
it buys is that there is one answer to "where is this value going" rather than two that have to be
kept in step, and that the compiler names every place that has to decide. A second type would be the
alternative if a third consumer ever has to refuse it.

There is no body generator, no body grammar, and no second interface. Adding one would be the third
place in the tool that knows how to turn a shape into a value.

### 2. What is sent, and what the request says it accepts

One media type is chosen per request, from the ones the body declares, JSON preferred where it is
offered. `Content-Type` states it. `Accept` is built from the media types the operation's own 2XX
responses declare, which is ADR-0017's item 2 and is owed whether a body is sent or not.

**The ones we can read back are asked for first, and the rest at a lower quality.** This is not
politeness, it is the difference between a reply that gets judged and one that does not. Several
documents in the corpus list XML before JSON — the pet shop the smoke gate starts is one of them,
on seven operations — and a server that honours the client's order would answer XML to an `Accept`
that merely repeated the document's order. `ResponseSchemaOracle` judges a reply only when its
content type is JSON, so those replies would stop being checked at all, which is worse than the
server default this header was added to improve on. Hence: the JSON-ish types first, everything else
after them at `q=0.5`. An API that serves nothing we can read is still asked for what it does serve,
because a request asking only for JSON earns a 406 from an API that never offered any.

### 3. Two families are sent, two are refused, and the roadmap row is narrowed to say so

JSON and `application/x-www-form-urlencoded`. Together they reach 337 of the corpus's 340
body-taking operations.

XML is not generated. It wins no operation in the corpus, because no operation offers it without
JSON, and it costs a serialiser that has to honour OpenAPI's `xml` annotations — element names,
attributes, wrapping — to produce a document an API would accept. Multipart is not generated either:
one operation in the corpus, against boundary framing and binary parts. Both are written here with
the measurement beside them so that reversing either is a decision somebody takes on evidence, and
the roadmap row that promised four families is amended rather than quietly half-delivered.

### 4. The samples a body declares are read, and sent as the author wrote them

`RequestBodyModel` gains somewhere to put them, and they are offered for the body the way a
parameter's samples are offered for a parameter — by the same source, under the same rule ADR-0019
settled: a sample is sent as written, even where the document's own rules about the value would
refuse it, because when a concrete sample and an abstract rule disagree there is no telling which
the author meant.

An earlier draft of this record said a sample that does not cover the whole shape would be completed
from the chain, which is what Schemathesis's examples phase does. It is not built, and the reason is
that it would contradict the rule above for the sake of nothing measurable: of the 134 media types
in the corpus that carry a body sample, **not one** writes a sample missing a required property the
API would have to be sent. The day a document does, completing it is a change to one source and this
paragraph is the record of why it was not made earlier.

### 5. A property the API only ever returns is never sent

`Access.READ_ONLY` is honoured when a body is built. The model has carried it since M1.1a and
nothing has read it; a body is the first consumer, and it is also what makes item 6 safe.

### 6. What is observed is reused leaf by leaf; a whole resource is one source among several

This is the decision the record exists for. The memory of what the API has returned enters as
**dictionaries with the two keyings the format already has** (ADR-0020): `name`, which answers for a
leaf, and `schema`, which answers with a whole object for a named shape. Both are filled at run time
by a listener on the event stream, never by reading the store, which is ADR-0013 §5 unchanged.

It follows that reusing a whole resource is not a different mechanism. It is the `schema`-keyed
dictionary answering for the body as a whole, and it competes with the other sources in a weighted
group like any of them. Changing one leaf of such a resource and sending it back is an operator on
top of that, worth having where a document reuses one shape for the request and the reply — a third
of the bodies in the corpus — and not worth building the generator around.

Nothing here is a new interface, a new file format or a new extension point. That is the test this
decision had to pass.

### 7. M2.5 splits in two, and only the second half changes what a seed promises

**2.5a** is items 1 to 5: bodies built from the schema and the document's own samples, media types,
the two headers, `readOnly`. It has no memory, so a run of it is reproduced from its seed exactly as
today.

**2.5b** is item 6: the listener, the index it fills, and the operator that changes one leaf of an
observed resource. A strategy that consults it is reproduced by replaying the stored run rather than
from the seed, which ADR-0013 §7 already decided for every source with a memory.

They are two increments and two pull requests, in that order, because there is nothing to observe
until bodies are being sent.

### 8. Sending bodies makes the tool write, and that is said out loud

Until now a run could create resources only through the few write operations that need no body.
After 2.5a it creates them continuously: an API under test will end a run with hundreds of objects
in it, and a resource created early changes what later requests do. The filter that contains this —
which HTTP methods a campaign may use, and whether it keeps to the ones HTTP calls *safe* — is
ADR-0013 §6's, and lives in the scheduler of 2.10. Whoever takes 2.5a says so in the release notes
and in `--help`; whoever takes 2.10 knows it became more urgent here.

## Consequences

- **103 operations of the corpus become testable, 34 of them in the priority corpus** — 23% of its
  whole surface, and the largest single unlock left in M2. That a body really reaches a server, with
  the media type declared and the property the document insists on inside it, is checked against a
  running HTTP server rather than asserted about the model; the containerised gate exercises the
  same path against two real APIs on every pull request.
- **ADR-0013's claim that "adding a source of values is one class and one line in a plan" gets its
  second test**, and this time the source is not new at all: an observed-value dictionary is the
  existing dictionary class with a different filling. If that turns out to need an interface change,
  the claim is wrong and the amendment says so, as ADR-0019 did when it found the claim half-true.
- **`RandomValueProvider` reads `Access` for the first time.** A property marked `readOnly` was
  previously included in an object-typed parameter, which was harmless and wrong; it stops.
- **2.7b acquires the consumer it has been waiting for**, if 2.5b writes what it observed to a
  dictionary file at the end of a run. A resource the API accepted is precisely "a value computed at
  a cost worth saving", which is the condition the roadmap set for the writer and the cache. Whether
  to do it is 2.7b's decision, not this record's; what this record settles is that the value exists.
- **M4.2 loses part of its subject.** "Runtime resource pool and value-source selection" is the row
  that owned the memory of observed values, and 2.5b builds it four increments early. M4.2 keeps the
  choice among dependency candidates, which is the part that needs M4.1's graph. The roadmap says so
  where the rows are, rather than leaving two increments believing they own the same index.
- **A run that uses 2.5b is reproduced by replay, not by its seed.** Already decided, now real for
  the first time: every strategy in the tool today is seed-reproducible, and this is the one that
  ends that.
- **A quarter of every body is drawn from the list of awkward values**, not built from the shape.
  That follows from ADR-0013 §2 and the shares 2.7a set: a body is one more value, so the strategy
  that pushes at the API replaces the whole of it — a `null`, an empty object, an object with one
  empty name. It is deliberate and it is where server errors on write operations will come from, but
  it means the nominal bodies this record is about are three quarters of what a default run sends.
  Whoever measures the effect of bodies on coverage measures that split too.
- **The tool starts writing to the API under test in earnest**, with no way yet to tell it not to.
- **The roadmap row for 2.5 is narrowed and split**, with the numbers above beside it.

## Alternatives considered

- **Build bodies by mutating observed resources, with schema construction as the fallback.** This is
  the shape the question arrived in, and the measurement is what rejected it: whole resources reach
  36% of bodies and nothing at all on two of the five priority APIs, against 92% of leaves. It also
  needs the request schema anyway, to project a returned representation onto what the API accepts,
  and it cannot start on an empty deployment. Adopted at the granularity the evidence supports
  rather than refused.
- **A `BodyProvider` interface of its own, separate from `ValueProvider`.** Rejected: a body is a
  value with a shape, the chain already builds values with shapes, and a second interface means two
  places that know how to turn a schema into JSON and one of them going stale.
- **Send every media type a body declares, rather than choosing one.** Rejected for now as an
  untested assumption about where the budget is best spent: 28 operations in the corpus offer more
  than one family, and whether the second is worth a request is a campaign's measurement. Left as an
  open question rather than decided here.
- **Deliver the roadmap row as written, XML and multipart included.** Rejected on the measurement:
  zero operations and one operation respectively. Writing the numbers down is what makes this a
  decision rather than a shortcut.
- **Defer bodies until after M3.** Rejected: a quarter of the corpus is unreachable, and every
  oracle in M3 would be measured against three quarters of an API.
- **Let generation query the interaction store for observed resources** instead of listening to the
  event stream. Rejected by ADR-0013 §5, unchanged: the store is for looking back, the stream is for
  reacting, and `restest-store` stays reachable from the command line alone.

## Open questions

- **Which media type to prefer when several are offered, and whether one operation is worth testing
  in more than one.** 28 corpus operations offer a second family. A campaign can answer it once the
  scheduler of 2.10 can express it.
- **Whether the observed index is keyed by the leaf's name alone or also by its path inside the
  body.** RESTler keys custom payloads by path, and a path distinguishes `owner.id` from `pet.id`
  where a bare name does not. 2.5b decides it with a measurement over the corpus rather than by
  argument, and the dictionary format takes a sixth keying only if the measurement asks for one.
- **How much of a returned resource survives projection onto the request schema.** `readOnly` is
  declared on 17 properties in the whole corpus, which is too few to be the real answer: APIs drop
  or refuse fields they never marked. Worth measuring against a running deployment in 2.5b, because
  it decides whether whole-resource reuse earns its operator.

---

## Amendment (M2.5b)

**Date:** 2026-09-22

**The memory is built, both open questions are answered by measurement, and the source it adds is
one the plan RESTest ships names — so a default run stops being reproducible from its seed alone.**

### The keying is the name, and the format takes no sixth one

The open question was whether the observed index should be keyed by a leaf's name alone or also by
its path inside the body, RESTler keying its custom payloads by path. Measured over the corpus,
counting every single value reachable inside a request body against what that same API's own 2XX
replies carry:

| | Whole corpus | Priority corpus |
|---|---:|---:|
| Single values inside request bodies | 2,125 | 256 |
| …whose **name** some 2XX also carries | **1,886 (89%)** | **223 (87%)** |
| …at an **address** some 2XX also carries | 1,700 (80%) | 204 (80%) |

The path reaches strictly less, and it would additionally need a rule for turning a reply's own
addresses into a request body's — a reply starts at the reply and a body starts at the body, so
`body.owner.email` and a reply's `owner.email` only match under a convention somebody has to invent.
The name needs no such convention.

What the path would have bought is telling apart two properties that share a name. That is **57 of
the 1,886**, 3%, and 2 of the 223 in the priority corpus. Of the 57, **47 agree about the kind of
value** and differ only in a declared form — `uri` beside plain text, `int32` beside `int64`,
`date-time` beside plain text. The remaining **10 disagree about the kind**, and those are refused
already, because a value is only offered where its kind matches what is being asked for. So the
format takes no sixth keying, and `ObservedValuesAcrossTheCorpusTest` holds the numbers.

One thing the measurement taught that nobody asked: **a reply is JSON by what it announces, not by
what the document spells.** flight-search declares every one of its responses under `*/*`, and
reading only entries written `application/json` found nothing at all for it — 0 of its 32 values
rather than 17. `ResponseModel.schemaFor` already looks a content type up exact-then-`type/*`-then-
`*/*`, and that is what the memory uses.

### An observed value is `Derived`, and saying so cost a second class

ADR-0013's claim that "adding a source of values is one class and one line in a plan" gets its
second test, and the answer is **two classes, one line in a plan**, with neither `ValueProvider` nor
`Dictionary` changed:

- `ObservedValues` — the listener and the memory. It holds the two dictionaries this record
  described, under `Keying.NAME` and `Keying.SCHEMA`, exactly as predicted.
- `ObservedValueProvider` — the source that asks them.

The second exists for one reason worth recording. `Dictionary.valuesFor` hands back bare values, and
a value read out of a reply should say **which exchange** it was read out of: `ValueOrigin.Derived`
is the case `restest-core` has carried since M1.1a for precisely this, and "observed" is not an
answer anybody can follow up. So the memory offers the provider a richer question of its own. That
is a smaller correction than ADR-0019's was, and it is the honest version of the claim.

### What is sent back is cut down first, and one value in it is changed

A resource an API returns is not a resource it accepts, so before a remembered thing is offered:
properties the document marks `readOnly` go, properties the operation's own shape does not declare
go, and properties whose kind does not match go. If something the operation insists on did not
survive, nothing is offered and another source builds a complete body instead.

Then **one single value inside it is replaced by a different one**, which is this record's operator.
The reason is mechanical rather than aesthetic: an unchanged copy asks a `POST` to create a
duplicate and asks a `PUT` to change nothing, while one value changed asks the API to accept
something genuinely new that is otherwise exactly as real as what it sent. The replacement comes
from **the whole strategy**, not from invention alone — fitting a shape and being on the closed list
a document states are different things, and asking invention outright would send a value the
document says is not allowed. It has to be *different* from what it replaces, which is not a
formality and is the subject of one of the corrections below; where nothing different can be had the
thing is offered as it came back and recorded as unchanged.

Two statements a document makes are honoured when choosing a value, and the rest are not, which is
deliberate. **The kind must match**, which is what stops a word seen under one name filling a number
of the same name elsewhere. **A closed list is obeyed**, because that list is the whole set of
values the API accepts and a value is not made acceptable by having been seen somewhere else. Bounds
and lengths are *not* checked, because they describe a shape rather than enumerate acceptability,
and a value the API itself has just produced is evidence about the API that a document's stale
`maximum` does not override. Re-deriving the whole of schema validation inside one source would be
the second implementation of it in this repository.

### The plan RESTest ships names it, and that ends what a seed promises for a default run

`source: observed` sits in the shipped plan's weighted group at 20, with `example` and `random`
giving up five each and `dictionaries: given` ten. Measured the way ADR-0023 §8 measured ranking,
with two things added: **two** containerised APIs rather than one, and the two plans **alternating
seed by seed** rather than one after the other, because the budget is a stretch of the clock and
anything drifting on the machine would otherwise land on one arm only. Each API is restarted before
every run; five seeds; 30-second budget.

**Operations that answered 2XX**, which is the metric §8 used:

| Seed | pet-clinic without | pet-clinic with | gestão-hospital without | gestão-hospital with |
|---|---:|---:|---:|---:|
| 3 | 29 | 33 | 6 | 7 |
| 7 | 30 | 32 | 6 | 5 |
| 23 | 27 | 32 | 4 | 6 |
| 41 | 26 | 30 | 6 | 6 |
| 99 | 27 | 31 | 6 | 10 |
| **mean** | **27.8** | **31.6** | **5.6** | **6.8** |

Better on every one of pet-clinic's five seeds, out of 35 operations it can test. On gestão-hospital
it is better on three, level on one and worse on one, out of 20 — and that is a coarse metric on an
API where fewer than a third of the operations ever succeed at all, so one operation either way is
most of the difference.

**Replies that were 2XX**, which is the finer metric and the one that moves everywhere:

| | without | with |
|---|---:|---:|
| pet-clinic, mean over five seeds | 11,292 | **12,487** |
| gestão-hospital, mean over five seeds | 397 | **541** |

Better on all ten paired runs. Throughput does not explain it: both arms sent between 40,000 and
46,000 requests on pet-clinic and between 5,000 and 8,900 on gestão-hospital.

**And a third API said something the first two could not: this source needs time to fill.** The
containerised pet shop the smoke gate starts is an API invention already does well on — small
integer identifiers, statuses it declares as closed lists — so it is the hard case for a memory.
Five seeds, the same alternating method, at two budgets:

| Budget | operations without | operations with | 2XX replies without | 2XX replies with |
|---|---:|---:|---:|---:|
| 10 seconds | 15.0 | 15.6 | 19,097 | **17,448** |
| 60 seconds | 14.6 | 15.6 | 106,939 | **135,925** |

At ten seconds it covers slightly more operations and earns **fewer** 2XX replies: the memory has
barely filled, so its share of each value is mostly spent declining, and what it does offer is drawn
from a handful of things seen once. At sixty seconds — which is what `--budget` defaults to — it is
ahead on both, by 27% on the replies, better on four of the five seeds and worse on none of them by
operations.

That is worth knowing before anybody measures a ten-second run and concludes the source does not
work. It also says where to look next: a source that pays off with time is one whose *share* might
reasonably grow as a run goes on, which nothing in the plan format can express today.

Four APIs and three budgets is more evidence than §8 had and still not much, and anybody
re-measuring it should expect to be adding to this rather than contradicting it.

Weighted rather than asked first, for the reason §8 of ADR-0023 established and one of its own: a
source that answers stops the ones behind it, and this one knows nothing at all until the API has
answered something.

**What this costs is stated wherever a seed is, and stated as what is true today.** ADR-0013 §7's
table said that a strategy with a memory is reproduced by replaying the stored run rather than by
its seed; the first half of that is now true of the default run and the second half is not built.
There is no `restest replay`: it is an M3.5 row. So `--seed`'s help, the line the run prints under
its seed, the README and the format document all say the same true thing instead - the same number
gets a *similar* run rather than the same one, and `--store` keeps every request and reply of the
run that did happen. Saying "replay the stored run" would have sent somebody looking for a command
that is not there, which the first draft of all four did. Taking the line out of the plan puts the
old promise back exactly, which is why the source is a line in a file rather than something switched
on inside the tool.

### The operator earns its place, and on one of the two APIs it is most of the mechanism

The open question this record left was how much of a returned resource survives projection onto the
request schema, and therefore whether whole-resource reuse earns its operator. Counted over one
30-second run of each API, by what the stored run records about where each value came from:

| | pet-clinic | gestão-hospital |
|---|---:|---:|
| Requests sent | 39,935 | 8,879 |
| …carrying something read out of an earlier reply | 6,554 | 1,074 |
| …of those, carrying a whole thing the API returned | 2,954 | 748 |
| …of those, carrying one value found by its name | 3,757 | 326 |
| Requests carrying something observed that were accepted | 1,776 | 155 |

The split reverses between the two, and that is the finding. On pet-clinic the leaf is the larger
half, as this record's corpus measurement predicted. On gestão-hospital the whole resource is: its
bodies are mostly shapes the API also returns, so a projected resource answers where a single value
would only have filled one of its properties. Neither API is served by the other's mechanism alone,
which is exactly the argument for having both.

### What the review of this increment changed, and why it is worth writing down

Reading an API's replies is the first time this tool has read something written by the thing it is
testing, and the review found that nothing in the code was treating it that way. Four of the five
findings are the same mistake in different clothes.

**A reply is read under limits now, and a listener that runs out of room no longer takes the run's
reporting with it.** `JsonText` said in as many words that "nothing an API under test sends back is
read here", and this increment made that false without noticing. A 2XX reply of nine thousand open
brackets - eighteen kilobytes, costing the API nothing - ran the event stream's one delivery thread
out of stack. The thread died, every later fault went unreported and unstored, and the counter that
exists to say a listener failed stayed at nought. So: a second reader with a nesting limit, a number
limit and a text limit, for text that came from outside; and `EventStream` now counts running out of
room as a broken listener rather than as a machine in trouble, which is what its neighbouring
comment already argued for `LinkageError`.

**Nothing too large to send is sent.** `1e9999999` is ten characters in a reply and ten million in a
web address; a reply can carry a word of any length; and a number of eight characters,
`1e-10000`, cannot be written out in full at all, so a body carrying one ended the run rather than
costing it a request. Single values are measured as they are kept, at the same length invention will
build up to. That was not enough on its own and a second review said so: a whole thing filed under
the name of its shape is kept in one piece, so the values inside it arrive never having been
measured. They are measured again where a thing is cut down to what an operation accepts, which is
the one place every one of them passes through.

Beside it, one repair that is not this increment's to claim and is its fault for making reachable:
the run loop lost a whole run to a value that could not be written down, where it was written to
lose one request. A list of values somebody wrote by hand could always do that too.

**And the same mistake once more, which is the one worth learning from.** There are two ways to ask
this source for something - by a value's own name, and by the name of its shape - and every rule
about what may be sent had been written into the second. Asked by name, a whole object was handed
back exactly as the API sent it: the identifier the API allots itself included, the properties the
operation never declared included, nothing measured. 156 of 300 bodies carried a `readOnly` property
in a probe, which is the opposite of what §5 of this record says and of what this amendment says
above it. It was found twice - the shape-keyed door in one review, this one in the next - because
each time the fix went where the defect was rather than where the rule was. Both doors now cut a
value down through the same code, and a container is measured by what is in it all the way down
rather than being waved through for being a container.

**A document whose two shapes point at each other no longer ends the run.** `A: {$ref: B}`,
`B: {$ref: A}` parses cleanly and reports no issue, and following it to decide whether a remembered
value fits had no hop limit - so the run died on a document that is nobody's mistake. Design
principle 2 says an operation is skipped and reported; it does not admit exceptions for documents
that parse.

**"One value changed" was not true, and the record said it was.** What supplies the replacement is
the rest of the strategy, and the rest of the strategy contains this very source - which holds,
under that name, the value already in that place. A third of the bodies measured were exact copies
of what the API had returned, each carrying a stored statement that a value had been changed. Now a
replacement equal to what it replaces is refused, each value is asked several times before the next
is tried, and a thing that genuinely could not be varied is offered *and recorded* as unchanged.
Against the public pet shop with the shipped plan, 157 bodies were built this way afterwards and
every one of them had a value replaced.

**And one thing the fix taught, which is about plans rather than about this source.** A plan whose
only source besides invention is this memory cannot vary anything inside a remembered thing: the
memory is asked for the replacement and answers with what is already there. The plan RESTest ships
does not have that shape - it asks the closed list a document states first, which answers for
exactly the values worth varying - but a hand-written plan can, and it then gets a source that
correctly does nothing. Worth knowing before somebody writes `[observed, random]` and measures it.

### Recorded rather than fixed

- **The memory learns from replies to requests built to push at the API.** A request from the
  pushing strategy that earns a 201 puts whatever it sent into the memory, from where the ordinary
  strategy may send it as a value somebody believed in. Nothing records which strategy produced an
  interaction, and adding that is `intent` on the test case, which ADR-0013 §3 assigns to M3.1b.
  A value the API accepted is at least a value the API accepts; the measurement above was taken with
  this behaviour in it.
- **The exchange an observed value names can only be looked up when `--store` is on**, which is off
  by default. The origin still says more than the word "observed" - it names which value of which
  reply - but "go and read that interaction" is advice that needs the run to have been kept.
- **A thing is measured by its parts and not by its total.** "Sendable when everything in it is"
  is what the check asks, so a reply carrying forty thousand short words under one name goes back
  into a body at its full size. For a body that is the right answer - the API produced that
  structure and a body may be large - and every other place a value can go is capped at ten
  thousand written characters anyway.
- **The two keyings are fused inside one source.** §6 above says they "compete with the other
  sources in a weighted group like any of them"; in the code the shape-keyed answer pre-empts the
  name-keyed one inside a single provider, and a plan cannot weigh one against the other. Nothing
  in the corpus measurement asks for them to be separable, and separating them later is two words in
  the table rather than a change to anything else.

### What was not built

**Nothing writes what was observed to a dictionary file.** This record's consequences said 2.7b
would acquire its consumer here if it did. It does not, and the reason is that the values are
deliberately kept *recent rather than complete* — the twenty most recent under any one name — so
what there is to write is a sample of a moving window, and an identifier saved to disk is one the
next run's API may never have heard of. 2.7b still waits for a value that cost something to compute.

**Nested things are not kept under the name of their shape**, only under their own name. A reply the
document names is kept whole under that name, and so is every element of a list of them; an object
*inside* one contributes its values but is not itself filed as a named shape. Naming it would mean
walking the document's shapes alongside the reply, and the measurement says whole-resource reuse is
the minority mechanism — the leaf is where the 89% is.
