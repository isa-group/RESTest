# ADR-0021: A request body is one more value, and what the API returns is reused leaf by leaf

**Status:** Accepted
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

There is no body generator, no body grammar, and no second interface. Adding one would be the third
place in the tool that knows how to turn a shape into a value.

### 2. What is sent, and what the request says it accepts

One media type is chosen per request, from the ones the body declares, JSON preferred where it is
offered. `Content-Type` states it. `Accept` is built from the media types the operation's own 2XX
responses declare, which is ADR-0017's item 2 and is owed whether a body is sent or not.

### 3. Two families are sent, two are refused, and the roadmap row is narrowed to say so

JSON and `application/x-www-form-urlencoded`. Together they reach 337 of the corpus's 340
body-taking operations.

XML is not generated. It wins no operation in the corpus, because no operation offers it without
JSON, and it costs a serialiser that has to honour OpenAPI's `xml` annotations — element names,
attributes, wrapping — to produce a document an API would accept. Multipart is not generated either:
one operation in the corpus, against boundary framing and binary parts. Both are written here with
the measurement beside them so that reversing either is a decision somebody takes on evidence, and
the roadmap row that promised four families is amended rather than quietly half-delivered.

### 4. The samples a body declares are read, and completed rather than sent bare

`RequestBodyModel` gains somewhere to put them, and they are offered for the body the way a
parameter's samples are offered for a parameter. A sample that does not cover the whole shape is
completed from the chain rather than discarded, which is what Schemathesis's examples phase does and
what makes a partial sample worth having at all.

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
  whole surface, and the largest single unlock left in M2. The smoke run gains write operations
  against both containerised APIs, which is where the claim gets checked rather than asserted.
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
