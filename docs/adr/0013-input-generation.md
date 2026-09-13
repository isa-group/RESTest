# ADR-0013: Where input values come from, and how a campaign is put together

**Status:** Accepted
**Date:** 2026-09-13

## Context

M1.5 shipped the first mechanism for choosing input values: a list of sources asked in order, where
the first one to answer wins and the order is fixed inside the generator's constructor. It works, and
it cannot express two things the next four increments need.

**It fixes the source, not the value.** If a parameter named `name` has an example in the document and
examples are asked first, then a curated dictionary of fifty good names is dead weight for that
parameter for the whole run. What is wanted is "the example most of the time, the dictionary the
rest", and a list that stops at the first answer cannot say that.

**The preference is global, when it belongs to the parameter.** The order of the list is one decision
that applies to every parameter of every operation. There is no way to say "for identifiers, prefer
what we have actually seen; for everything else, prefer what the document states".

Four more sources arrive in M2 — the document's declared examples, values matching a declared format,
a dictionary curated by hand, and an external program — and each of them would be written against
whatever shape exists at the time. Settling the shape afterwards is four rewrites.

### What the tools we are measured against actually do

Not what they advertise; what their documentation says about the mechanism.

| Tool | How a value is chosen |
|---|---|
| [RESTler](https://github.com/microsoft/restler-fuzzer/blob/main/docs/user-guide/FuzzingDictionary.md) | Two levels: custom payloads keyed **by parameter name or by path inside the body**, and per-type pools as the fallback. Every value in a pool is tried, not sampled |
| [Schemathesis](https://schemathesis.readthedocs.io/en/stable/explanations/data-generation/) | **Separate phases** over the same schema — examples, coverage of boundaries, fuzzing, stateful. Not a priority order: a budget split |
| [EvoMaster](https://github.com/EMResearch/EvoMaster/blob/master/docs/options.md) | **Search.** The value is a variable of an individual, mutated by an evolutionary algorithm with coverage as the fitness; examples and enumerations seed the initial population |
| [CATS](https://github.com/Endava/cats) | `--refData` per path and field, a user dictionary, and a catalogue of per-field fuzzers |
| RESTest 1.x | One generator configured by hand per parameter in `testConf.yaml`. Without the file, there is no tool |

Two observations decided this ADR. **None of them uses a global priority chain as its main
mechanism.** And where there is priority at all — RESTler, CATS — the key is the parameter, not the
position of a source in a list.

### One measurement of our own

Of the eighteen findings the review of M1.5 produced, ten were in the arithmetic of inventing a value
from a schema's constraints: an overflow on `maxLength: 2147483647`, an exclusive bound ignored when
an inclusive one sat beside it, numbers that were never fractional. A dictionary lookup has no
arithmetic to get wrong. That is evidence about where the cost of this component lives.

## Decision

### 1. Sources stay behind one narrow interface

`ValueProvider` in `restest-core` does not change: asked about one value, either suggest something or
say nothing. Eight sources, of which three exist today:

| Source | Answers when |
|---|---|
| enumeration | the schema states a closed list |
| example | the document states an example for this value |
| format dictionary | the schema declares a `format` we have values for |
| default dictionary | always, for a type we have values for |
| custom dictionary | the user's file has an entry for this operation and parameter |
| observed values | this run has seen a value for a parameter of this name |
| fuzzing dictionary | always — values designed to be refused |
| constraint-directed construction | always, as the last resort |

The four dictionaries are **one class with a different key**: the JSON type, the declared `format`,
the parameter's name, or the pair of operation and parameter. The file format is the same for all of
them, and it is a published format (design principle 8).

### 2. A strategy is an ordered list of groups; within a group, sources are sampled

```yaml
strategies:
  - name: nominal-custom
    share: 40m
    sources:
      - exclusive: [enum]           # if it answers, the choice is made
      - weighted:                   # ask all, sample among those that answered
          observed: 30
          example:  20
          custom:   40
          format:   10
```

An **exclusive** group stops at the first answer. A value outside a declared enumeration is invalid by
construction and belongs to a strategy that intends to be refused, so the enumeration is never mixed
with anything.

A **weighted** group asks every source, collects the answers, and samples among them. Every source is
an in-memory lookup, so asking all of them costs nothing worth measuring.

The percentages must sum to 100 and are validated. They are an intention, not a guarantee: **a source
that has nothing to say does not get its share**, which is redistributed among the sources that
answered. Written down here because somebody will measure the outcome and find it does not match the
file.

Weights live in the strategy, never in the source. A source that rates its own confidence is a magic
constant with a friendly name; a percentage in a plan is a decision somebody made on purpose.

### 3. The test case records what was intended; oracles are not attached to it

A test case carries an **intent**: *I believe these values are acceptable*, *I expect this to be
refused, and here is what I broke*, or *I do not know*. Oracles stay where ADR-0006 put them — outside,
observing interactions — and read the intent to decide whether they apply.

Oracles are configured per run, and per operation from M3.4. They are not stored on the test case,
although they could be. The reason is that these are facts and policy, and only the facts have to be
recorded:

| | How many values | How stable |
|---|---|---|
| Intent | about four | does not grow when an oracle is added |
| Oracle catalogue | about forty, and growing (M3.1 adds seven, M3.2 about twenty-five) | changes every milestone |

An oracle reference stored in a run written today names something that may be renamed or removed
tomorrow; `2XX_P` is derivable from *"I believed the values were acceptable"*, and the oracles for HTTP
semantics apply whatever the intent was. Storing the smaller, stabler fact keeps `restest recheck`
able to apply a different oracle set to an old run, which is the whole point of keeping runs.

The intent belongs to the test case rather than to the strategy because **one strategy produces
several intents**: probing a documented limit expects the minimum to be accepted and the minimum minus
one to be refused, in the same strategy, on the same parameter.

The third value, *I do not know*, is not a placeholder. It is the honest intent of a value invented
from a schema, because an API may refuse it for a rule the document does not express. It becomes *I
believe this is acceptable* when the value comes from a source that knows the API.

### 4. A deliberate violation is a mutation of a test case the API accepted

Instead of a generator that builds invalid requests from nothing, an operator takes a test case that
**actually returned a 2xx** and changes one thing: drop a required parameter, send the wrong type, step
outside a documented bound, break an enumeration, break a pattern, and later violate an inter-parameter
dependency. The intent comes from the operator for free, and a 2xx afterwards is a finding attributable
to that one change.

Probing documented limits is therefore an operator, not a strategy of its own.

Fuzzing every parameter at once stays as a separate strategy. It is a legitimate and common way to
hunt for 5xx, and its intent records that a refusal cannot be attributed to any one parameter — which
is exactly the difference between the two.

### 5. The pieces with a memory listen to the event stream; generation never reads the store

Two components need to know what has already happened: the dictionary of observed values, and the
memory of accepted test cases that mutation works from. Both are **listeners on the event stream**
(ADR-0006), keeping their own bounded index in memory. Neither queries the interaction store.

> The store is for looking back. The event stream is for reacting.

This is not only about module boundaries — although it is also that, since `restest-store` may only be
reached from `restest-cli` and this keeps the rule intact with no exception. It is what these two
components actually want: an identifier observed forty minutes ago may have been deleted since, and a
test case that succeeded early may not succeed now. They need *recent*, not *complete*.

It is also the answer to what ADR-0008 means by providers being "asynchronous by default". The
interface stays synchronous and simple: a source answers instantly from an index that a listener fills
in the background. An asynchronous interface would not even be better at the job, because it still lets
a caller wait; a filled index cannot.

### 6. The scheduler is the only component that knows the budget

Which strategy runs, how often, and for how long is decided in one place. Generation does not know what
time it is, and there is no concept of a "phase" inside it: a phase is a share of the budget, and that
is the scheduler's vocabulary.

The plan also carries the filters — the HTTP methods to exercise, whether to keep to methods HTTP calls
*safe*, and named operations to restrict a campaign to. Filtering by method is not the same as
"read-only": an API that searches with `POST` is ordinary, and one of the five specifications in our own
corpus does exactly that. The words *safe* and *idempotent* are HTTP's own and mean something precise;
"read" and "write" would not.

### 7. Randomness stays as a mechanism; the reproducibility promise is made per strategy

The seed is recorded with the run and can be set. What changes is what is promised:

| Strategies | Reproduced by |
|---|---|
| without a memory — nominal from dictionaries, limits, fuzzing | the seed |
| with a memory — observed values, mutation | replaying the stored run |

M1.5 promised more than that, in a Javadoc comment and in a pull request, and the promise breaks the
day the dictionary of observed values arrives. Stating it per strategy is the honest version.

The seed is kept rather than dropped in favour of replay alone, for three reasons that replay does not
cover. A generator whose randomness cannot be controlled has no deterministic tests. A failure that
happens **during generation** leaves nothing in the store to replay, by design — ADR-0005 says a test
case that never became a request produces no interaction — and that is not hypothetical: M1.5's
overflow on `maxLength: 2147483647` threw before any request existed. And M6.2's overhead regression
test needs a fixed workload to compare one commit against another.

Random *invention* of values shrinks to one job: constructing a value that satisfies constraints no
dictionary entry can satisfy, such as a string between twelve and fourteen characters or a multiple of
seven between 1,000,000 and 1,000,010. When M2.3's walk over documented limits lands, that construction
becomes deterministic and the random arithmetic goes away.

## Consequences

- Adding a source of values is one class and one line in a plan. That is the property the next four
  increments were going to test, and it is why this is settled before they are written.
- Dictionaries replace arithmetic with lookups, in the component where the arithmetic produced ten of
  eighteen review findings.
- **Provenance must name the source.** With sampling among sources, the composition of a run is
  probabilistic, and without knowing which source answered, *"did the dictionary I wrote by hand
  help?"* cannot be answered at all — not in a report, and not by the feedback of M6, which would have
  nothing to learn from. Most of what is needed already exists: a value invented or looked up is
  recorded as generated **by a named source**, and a value read out of an earlier reply names the
  interaction it came from. The gap is narrower than it looks and sits in one place — a default and an
  enumerated value are both recorded as *declared*, with nothing to tell them apart, and the declared
  examples of M2.2 would be a third. ADR-0005's amendment considered separating them and left it,
  reasoning that a consumer can compare the value against the schema's own default and enumeration.
  That reasoning has a hole worth naming before M2.2 meets it: a schema whose default is also one of
  its enumerated values — `default: available` among `[available, pending, sold]`, which is the common
  shape — leaves the two indistinguishable by comparison. Whoever adds the third case decides whether
  that is worth a sub-kind.
- **A varied dictionary has an arithmetic problem worth stating.** With twenty entries and a parameter
  that wants an e-mail address, one draw in twenty fits; with three such parameters in one request, all
  three fit once in eight thousand. Two cheap mitigations, both in this decision: index on `format` when
  the document declares one — reading the specification, not inferring anything — and remember what
  produced a 2xx. Without one of them, a varied dictionary is a lottery for anything but a single
  parameter.
- Two increments already planned inherit obligations. **M1.6** publishes the event stream and adds the
  intent to the test case, which is a store layout change — supported, since the file records the layout
  that wrote it. **M1.7** keeps the loop in the command-line module, so that generation never gains a
  dependency on the engine or the store.
- **Evaluation integrity.** The default strategy must not depend on data curated for the five APIs the
  tool is measured on, and campaign results must report "as it comes" separately from "with a curated
  dictionary". Otherwise the comparison against tools that were given nothing is not a comparison.
  M8.2's ablation study is where this is reported, but the constraint is on the design, now.
- **Licensing.** A dictionary shipped with the tool redistributes whatever data is in it — names,
  postal codes, airport codes. That has to be checked before the first file is written, for the same
  reason the golden corpus is not published inside an artefact.
- More vocabulary than there was: source, dictionary, strategy, operator, intent, plan. Six words for
  what used to be one list. The defence is that five of them already existed in the roadmap under other
  names, and the sixth — intent — is what makes two of the oracles possible at all.

### Left to M4, on purpose

Testing that spans several requests needs two decisions this ADR does not take, because taking them
without a single sequence written would be guessing.

**The unit of work.** A sequence cannot be a precomputed list of test cases: the identifier in the
second step comes from the first step's response, and in `create, delete, read` the *expectation* of
the third step depends on the second having succeeded. So the scheduler's unit has to be something that
can be asked for the next step given what has happened, rather than a test case. A shape was sketched
during this discussion and deliberately not adopted: it had nowhere to put the clean-up of a resource
created by a step that later failed, and it did not fit the way RESTler extends sequences across a whole
run. Nothing in the current design prevents it — the loop lives in the command-line module and
generation never sends anything — so M4 can choose the shape when it has a sequence to write.

**Interference between concurrent sequences.** One rule was proposed and refuted during this
discussion, and it is recorded here so that it is not proposed again. The rule was "a sequence may only
assert about resources it created itself". It fails because the dictionary of observed values hands the
same identifier to everybody:

```
unit A   POST /pets           → 201 {"id": 7}        A created 7
                                                     the listener harvests 7
unit B   DELETE /pets/7                              B takes 7 from the dictionary
unit A   GET  /pets/7         → 404                  A reports a use-after-free that never happened
```

The replacement to start from, which makes ownership true instead of merely asserted: **a sequence
never borrows an identifier; it creates what it needs**, and the dictionary of observed values serves
only tests of a single request, where a stale identifier produces a 404 that no oracle treats as a
finding. Its honest limit is that resources which cannot be created through the API — those needing
another actor's permission — leave their sequences untestable, and reported as such.

Until M4, concurrency is safe and stays. Interference needs both a shared identifier and an oracle that
asserts across requests, and neither exists yet. What would not be safe is making the loop sequential
"just in case": against an API answering in 200 ms, twelve requests in flight send about sixty a second
and one at a time sends five, and **our own measurements would not notice**, because idle time reports
the tool's own waste and a sequential loop waiting on the API is never idle.

## Alternatives considered

- **Keep first-answer-wins as the only mechanism.** What M1.5 has. It cannot mix an example with a
  dictionary for the same parameter, and it makes the order of the list a single global bet that cannot
  be right for every parameter at once.
- **Confidence declared by each source** — "I am 0.8 sure about this". Rejected: a number nobody
  calibrates against evidence. The tool that does use weights, EvoMaster, learns them from coverage
  rather than being told.
- **Phases as a first-class concept inside generation**, as Schemathesis has them. Rejected as a
  concept, adopted as an outcome: a phase is a share of the budget, and the scheduler already allocates
  budget. One noun fewer for the same behaviour.
- **Oracles attached to the test case**, as RESTest 1.x effectively did. Possible, and it is the same
  information viewed as policy rather than as fact. Rejected because the catalogue is large and moving
  while the fact is small and still, and a run stored today would name oracles that no longer exist.
- **Semantic dictionaries** — person names for `name`, airport codes for `origin`. Rejected as the
  starting point, because the hard part is not the list, it is deciding what a parameter means, and that
  is inference the tool cannot do reliably without the specification's help. Reading a declared `format`
  is not inference and is adopted instead.
- **Generation reading the interaction store** to find accepted test cases and observed values.
  Rejected: it wants recent rather than complete, and it would put the store inside the loop it is
  supposed to be a record of.
- **Dropping the seed entirely**, on the grounds that replay reproduces a run. Rejected for the three
  cases replay does not cover, listed above. The half that was right — that replay, not the seed, is the
  reproducibility mechanism a user is offered — is adopted.
- **Deciding the unit of work for sequences now.** Rejected as designing a mechanism before its first
  consumer exists, which this project has already done twice at a cost: an unused method added to
  `ValueRequest`, and a provider seam built and then left with no way to plug anything into it.

## Open question

**What a campaign does when nobody configures anything.** The share of a budget between nominal,
mutation and fuzzing is the one number here that cannot be argued into place: it is measured. When the
pieces exist, one afternoon of campaigns against the five specifications in the corpus, with three
different splits, answers it.
