# ADR-0019: A document's own sample values are read, sent as written, and named in the record

**Status:** Accepted
**Date:** 2026-09-18

## Context

M2.2's job is one sentence in the roadmap: *declared examples harvested, in both the 3.0 and the 3.1
shapes*. The mechanism is small. Four questions around it are not, and three of them were parked by
earlier records to be answered here.

### What there is to gain, measured

Every `openapi.*` file in the corpus, parsed and asked which parameters gain a sample value:

| | |
|---|---|
| Documents parsed | 50 |
| Documents offering a sample for at least one parameter | 6 |
| Parameters | 4,916 |
| Parameters that gain a sample | 212 (4.3%) |

| Document | Parameters with a sample | Where the sample is written |
|---|---|---|
| `kafka-rest-proxy` | 103 | on the parameter — `cluster_id: cluster-1`, `topic_name: topic-1` |
| FDIC | 65 | on the parameter |
| `pet-clinic` | 26 | on the shape — `ownerId: 1`, `petId: 1`, `lastName: Davis` |
| DHL | 14 | on the shape |
| AmadeusTravelRestrictions | 2 | both |
| GitHub | 2 | on the parameter |

Four in a hundred parameters sounds like little, and the two entries that matter are the two APIs
the tool is measured on. Both write their sample values on *identifiers in the path*. Without them,
`GET /owners/{ownerId}` and `GET /clusters/{cluster_id}/topics/{topic_name}` are sent an invented
number and an invented word, which address nothing; the API answers "not found" correctly, and the
request has tested the routing and nothing else. `pet-clinic` says an owner is numbered `1` and
`kafka-rest-proxy` says the cluster is called `cluster-1`, and those are almost certainly the
identifiers that exist in the instance the author was looking at. It is the difference between
exercising an operation and knocking on a door that is not there.

### The four spellings

Two levels, two spellings each, all four in the corpus:

| Where | 3.0 | 3.1 |
|---|---|---|
| The shape | `example:`, one value | `examples:`, a list — and `example` still accepted |
| The parameter | `example:`, one value, or `examples:`, a map of named Example Objects | the same |

An Example Object may hold its value inline, point at one kept among the document's reusable pieces,
or give a web address to fetch it from.

## Decision

### 1. Sample values live on the shape and on the parameter, separately

`SchemaMetadata` gains `examples`, and `Parameter` gains `examples` of its own. They are not merged
at parse time, for a reason worth stating: a shape a document declares once and names may be used by
dozens of parameters, and folding one parameter's sample into it would offer that sample everywhere
the shape is used. OpenAPI says a parameter's own sample takes precedence over its shape's, and that
rule is applied where the value is chosen, not where the document is read.

So the parameter's samples travel with the question, as a component of `ValueRequest`. They follow
that question into a shape looked up by name — still the same value — and are left behind whenever a
source steps into a *piece* of that value, whether a named property or one element of a list: a
sample owner is not a sample of the owner's first name, and a sample list of three numbers is not a
sample of each number in it.

### 2. Both spellings are read, and a value that cannot be written down exactly is left out

The existing conversion is reused unchanged: it already turns the parser's `Date`, `byte[]`, `UUID`
and document nodes back into what the document wrote, and yields nothing rather than an
approximation (ADR-0012, Amendment M1.8). Repeats between the two spellings collapse to one sample.

Two cases produce nothing and are not reported as problems with the document:

- **A sample kept at a web address** (`externalValue`). Reading a document must not depend on the
  network being there.
- **A value of a kind that cannot be represented exactly.**

Neither costs an operation — the parameter is tested with an invented value exactly as it was
before — so reporting each one would bury the issues that *do* cost an operation.

One honest limit, which the code says too: a parameter carries no flag saying whether its author
wrote a sample at all, only the value, so a parameter whose author really wrote `example: null`
cannot be told from one that wrote nothing and loses that sample. A shape does carry such a flag, so
there `example: null` is kept. No document in the corpus writes either, and a sample that is nothing
is the one sample that teaches nothing.

### 3. A sample is sent as the author wrote it; only a closed list overrides it

The new source is asked before the one that reads defaults and enumerations, and it **stands aside
for a value the document restricts to a fixed list**. Every sample it could offer there is already
one of the listed values, and using it would pin the run to that one member of a list the tool is
meant to walk. ADR-0013 said the same in general terms: the enumeration is never mixed with
anything.

The resulting order of preference, all of it still first-answer-wins until the scheduler owns
strategies:

```
a closed list  →  a sample  →  a default  →  invention
```

A sample ahead of a default because a default is what the API uses when the caller sends *nothing*,
while a sample is what the author wrote to show a call that works.

**A sample the document's own shape refuses is still sent.** This is the question that looked
closest and turned out to be answered by measurement. Across the corpus there are six such samples —
FDIC writing `download: false`, a boolean, where the parameter accepts only the *words* `"true"` and
`"false"` — and every one of them is on a parameter restricted to a fixed list, which this source
stands aside from. So the number of values RESTest takes from a sample and that the shape refuses is
**nought**, and a test over all fifty documents now says so and will say the day it changes. That
test asks more of a sample than the equivalent check asks of an invented value — the pattern a shape
states included, which the shared check leaves alone because matching a stated pattern while
*inventing* a value is M2.4's job.

Filtering them out anyway was considered and rejected because when an author's concrete sample
disagrees with the author's own abstract rule, nothing here can tell which of the two they meant,
and the sample is at least as good evidence about what the API accepts.

There is a second argument that reads well and is not yet true, so it is recorded as an argument and
not as a reason: if the API refuses the value its own document tells callers to send, that is a
disagreement worth surfacing. Nothing surfaces it today — such a reply is an ordinary refusal to
every oracle that exists — and no roadmap row owns it. The decision above stands on the first ground
alone.

Three kinds of sample are never offered, and none of them is a judgement about whether the author
was right:

- **Any sample for a value restricted to a fixed list**, as above.
- **Any sample for a shape that accepts no value at all.** There the document contradicts itself
  outright, and every other source already believes the half that says nothing fits.
- **A sample that writes out as nothing, where it belongs in the path.** An empty piece of a path
  closes the gap instead of filling it: `/owners/{ownerId}` becomes `/owners`, a request for every
  owner judged afterwards against the promise made about one, and every request for that operation
  thrown away for the whole budget while the operation is still advertised as testable. The
  generator already refuses to invent nothing there for exactly this reason, and a sample has to
  obey the same rule — the 3.1 spelling makes `examples: [Jane Doe, null]` an ordinary thing to
  write, and this repository's own 3.1 fixture writes one. Elsewhere an empty value is perfectly
  ordinary and is sent.

  The question is put to the very code that writes the value into the path, not answered from the
  value's shape, because the two disagree: a list holding one empty word is a list with something
  in it and writes out as nothing, while a list holding two writes out as the separator between
  them. A first attempt judged by shape and let that case through.

Precedence and this rule interact, and the order matters: a parameter's own samples win only where
it has one that can be used. A parameter writing an unusable sample over a shape that writes a good
one would otherwise throw away the identifier the document gave us and invent one instead, which is
the outcome this whole record exists to prevent.

The narrow exception already in the code stays and is extended to samples: when *we* combine two
halves of an `allOf`, a contradiction we manufactured is removed, exactly as a default in the same
position already was. There the contradiction is ours, not the author's.

### 4. A value the document stated says which statement it came from

`ValueOrigin.Declared` gains the statement it was read from: the default, the enumeration, or a
sample.

ADR-0005 considered this and left it, reasoning that a consumer could compare the value against the
schema's own default and enumeration. ADR-0013 named the hole — `default: available` among
`[available, pending, sold]` is the common shape, and leaves the two indistinguishable — and left
the decision to whoever added the third case. This is that case, and the answer is yes, because the
question M2.2 exists to answer is *did the document's own samples help?*, and a run that cannot say
which values came from a sample cannot answer it at all. ADR-0013's own consequence says provenance
must name the source.

The statement is optional, and absent means *"the document said so, and this run did not record
which statement"*. That is what a run recorded before this change reads back as, so an older stored
run still opens rather than meeting a version wall. Nothing generating values produces it.

### 5. Samples on a request body wait for M2.5

A media type may carry samples too, and they are not read. Bodies are not generated at all until
M2.5, `RequestBodyModel` holds a bare map from media type to shape with nowhere to put them, and
ADR-0013 is explicit that this project has twice paid for building a mechanism before its consumer
existed. M2.5 reads them, beside the `Accept` header it already owes.

## Consequences

- The two APIs in the priority corpus that write sample identifiers now send those identifiers
  instead of invented ones. Whether the resources they name exist in a given deployment, and what
  that is worth, is a campaign's measurement against a running instance, not this record's — and
  neither of those two APIs is one the smoke run starts.
- 212 parameters across the corpus change what they are sent. No operation becomes testable or
  untestable: every one of them already had *some* value.
- **ADR-0013's claim that "adding a source of values is one class and one line in a plan" is
  half-true, and this is the first test of it.** The source itself is one class and one line. What
  it also cost: a component on `ValueRequest` and a second form of `about` to carry the parameter's
  own samples without letting them reach a piece of the value, and a rule that depends on where the
  value is going. The interface did not move and no existing source changed, which is the part of
  the claim that held.
- **A stored run's layout changed**, compatibly: a declared value may carry `stated`. The store's
  own version did not have to move, because a document without the field still reads.
- `SchemaMetadata.examples()` and `Parameter.examples()` are what the document offered, not what is
  guaranteed usable. A consumer that wants only usable ones asks the source, which is where the
  enumeration rule lives.
- **"Does this value fit this shape" now has two and a half homes** — `AllOfMerger` holds a private
  one, a test helper in `restest-gen` holds another, and the corpus test above leans on the second
  to check the first's subject. None of them is wrong and none is shared. That is fine at two; the
  third consumer should move it into `restest-core` beside `SchemaChecks` rather than write a
  fourth. Named here so the decision is deliberate when it comes.
- Reading a sample is reading the document, not inferring anything, which is the line ADR-0013 drew
  when it rejected semantic dictionaries.
- **`ValueRequest.about` had to grow a second form.** A parameter's samples follow the question into
  a shape looked up by name but must not follow it into one *element* of a list — a sample list of
  three numbers is a sample of the list, not of each number. Objects already had the distinction,
  because a property is reached by name; elements had nothing, and the invariant the record's
  documentation states was broken by its only caller until this was added.

## Alternatives considered

- **Fold a parameter's samples into its shape at parse time.** Much the smallest change, and no
  parameter in the corpus combines a sample with a shape declared elsewhere by name, so it would
  work today. Rejected because "works on the fifty documents we happen to have" is not the same as
  correct: the first document that did combine them would quietly offer one parameter's sample to
  every other user of that shape.
- **Widen `CanonicalSchema` with a `withMetadata`, and apply OpenAPI's precedence rule by rebuilding
  the shape.** Keeps `ValueRequest` untouched, at the price of eleven new methods across the schema
  variants and a shape that claims the document said something it did not. `ValueRequest`'s own
  description already promises to carry "everything a knowledgeable answerer might use".
- **Check every sample against its shape and drop the ones that do not fit.** Rejected on the
  measurement in §3: nought would be dropped, and the mechanism would hide a real disagreement
  between a document and its API.
- **Record a sample as generated by a source called "example".** It is not generated; the document
  wrote it. It would also leave the record saying a value was invented when it was not, which is
  precisely the distinction `ValueOrigin` exists to keep.
- **Leave `Declared` as one undifferentiated case**, as ADR-0005 did. Cheapest, and it makes the
  increment's own question unanswerable. The comparison against a schema that ADR-0005 offered as
  the substitute does not work for the commonest shape, which is why ADR-0013 reopened it.
- **Read samples from responses as well**, which many documents carry far more of. They are not
  inputs. They become interesting at M4.2, where a value seen in a real reply is worth more than one
  the document imagined, and that is a different mechanism with a memory.
