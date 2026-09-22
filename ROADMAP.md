# RESTest 2.0 — roadmap

56 increments in 9 milestones, 24 of them delivered. One increment = one branch = one pull request
into `v2`. Take them in order unless told otherwise.

Design rationale: [`docs/DESIGN.md`](docs/DESIGN.md). Decisions: [`docs/adr/`](docs/adr/).

## How to read this file

| Mark | Meaning |
|---|---|
| ✅ | Delivered, beside the pull request that delivered it. An increment is marked in its own pull request, so the table and the branch history never drift apart |
| 🛑 | Supervision point. Stop there and wait for review instead of starting the next increment |
| → | The row moved. It names where the work now lives, and is no longer an increment of its own |

A letter after the number — `1.1a`, `1.7b`, `2.7a`, `3.1b` — marks a row that was split after it was
written. The letters keep the original number, so earlier pull requests and the ADRs that cite them
still read true.

Where a milestone needs it, its table is followed by **notes**: why a row is ordered where it is,
what it inherits from an ADR, what it deliberately leaves out. Notes run in increment order, and
nothing in them is an increment of its own.

## Where the work stands

| Milestone | Goal | Delivered |
|---|---|---|
| M0 | Foundations | 3 / 3 ✅ |
| M1 | Walking skeleton | 13 / 13 ✅ |
| M2 | Specification fidelity and input generation | 8 / 14 |
| M3 | Oracles, faults and reporting | 0 / 8 |
| M4 | Stateful testing | 0 / 6 |
| M5 | IDL and constraint-based generation | 0 / 5 |
| M6 | Live updates and performance | 0 / 2 |
| M7 | Packaging and distribution | 0 / 3 |
| M8 | Evaluation | 0 / 2 |

---

## M0 — Foundations

| # | Increment | What it enables |
|---|---|---|
| 0.1 ✅ [#280](https://github.com/isa-group/RESTest/pull/280) | Multi-module Maven skeleton, `--release 21`, `module-info.java`, LICENSE (Apache-2.0), NOTICE, CODEOWNERS, `.gitignore`, `docs/adr/` | The project builds and has a shape |
| 0.2 ✅ [#281](https://github.com/isa-group/RESTest/pull/281) | CI: 3 operating systems × Java 21/25/26, JaCoCo, the ArchUnit harness, Dependabot, actions pinned to commit SHAs | Every later change is checked automatically |
| 0.3 ✅ `2326ffa9` | 🛑 ADRs 0001–0011 committed and reviewed | The decisions are written down where they can be challenged |

## M1 — Walking skeleton — *goal: beat RESTest 1.x on a real API*

| # | Increment | What it enables |
|---|---|---|
| 1.1a ✅ [#284](https://github.com/isa-group/RESTest/pull/284) | Specification model: `ApiModel`, `Operation`, `Parameter`, `CanonicalSchema` and `JsonValue` as records and sealed types; named and recursive schemas; unreadable constructs recorded rather than lost | A representation of an API that is ours, not a library's |
| 1.1b ✅ [#285](https://github.com/isa-group/RESTest/pull/285) | Execution model: `TestCase`, value provenance, exact request and response payloads, `Interaction` and its outcome | A representation of what we did to the API, and what came back |
| 1.2 ✅ [#287](https://github.com/isa-group/RESTest/pull/287), [#289](https://github.com/isa-group/RESTest/pull/289) | `SpecificationParser` interface + a single swagger-parser backend; OAS 2.0 (by conversion), 3.0.x and 3.1.x; lazy `$ref`; malformed operations, and any OAS 3.2 (or later) document, skipped and reported rather than failing the run | Point the tool at any real specification without it crashing |
| 1.3 ✅ [#290](https://github.com/isa-group/RESTest/pull/290) | `HttpEngine` interface + OkHttp backend; virtual threads; exact wire capture; adaptive concurrency; idle-time accounting | Requests get sent, fast, and we can see where the time went |
| 1.4 ✅ [#291](https://github.com/isa-group/RESTest/pull/291) | Interaction store (one SQLite file per run) and its query API | Every run is inspectable afterwards |
| 1.5 ✅ [#292](https://github.com/isa-group/RESTest/pull/292) | Value provider chain and random providers; random test-case generator | The tool invents its own inputs |
| 1.6 ✅ [#295](https://github.com/isa-group/RESTest/pull/295) | Oracles: server error, response schema conformance. WFC fault codes, event stream, console and JSON reports | Real failures are reported, each with a `curl` command to reproduce it |
| 1.7 ✅ [#296](https://github.com/isa-group/RESTest/pull/296) | `restest run <spec> --url <base> --budget <duration>`; smoke integration test against two containerised APIs | The whole thing works from one command; regressions caught on every PR |
| 1.7b ✅ [#297](https://github.com/isa-group/RESTest/pull/297) | `--store` (off by default); batched SQLite write path; the JSON report bounded per operation and fault kind; one directory per run | A default run tests far more in the same budget and writes kilobytes instead of hundreds of megabytes; keeping a run costs disk but no longer costs time |
| 1.8 ✅ [#298](https://github.com/isa-group/RESTest/pull/298) | Audit of the branch acted on: faults printed as they are found rather than in batches; a reply RESTest cannot check is never reported as a fault; untyped object schemas, declared defaults and dangling references read correctly; the same command explains itself the same way twice; an unwritable `--out` answers 3 | What the tool says about a document, and about a reply, is true — and the whole 50-document corpus is parsed by a test for the first time |
| 1.9 ✅ [#304](https://github.com/isa-group/RESTest/pull/304) | 🛑 The evaluation harness built in a repository of its own rather than in `evaluation/` here, which is therefore never created. **Here:** the amendment to ADR-0011 recording that, the documentation that follows from it, and the rule against naming the benchmark widened from `src/` to every file and file name. **In [`isa-group/restgym-restest2`](https://github.com/isa-group/restgym-restest2)** (private for now): benchmark adapter, campaign script, both commits pinned, and the first campaign — v2 alone, two APIs, ten minutes | The tool is measured the way the field measures tools, by a harness this repository does not carry, does not build and does not name |
| 1.10 ✅ [#301](https://github.com/isa-group/RESTest/pull/301) | The shared fault catalogue brought up to date, and server errors counted the way benchmarks count them: over replies rather than faults, distinct by operation, beside the fault list rather than inside it. Delivered before 1.9 | Our numbers can be put beside another tool's without a footnote explaining why they are not comparable |
| 1.11 ✅ [#305](https://github.com/isa-group/RESTest/pull/305) | The run's randomness taken from the part of Java every runtime carries, so the tool starts on a plain JRE instead of dying before its first request; an architecture rule and a containerised gate keep it that way | RESTest runs anywhere Java runs — official JRE container images included — and one seed means one run on every runtime |

The specifications these increments are tested against: see [the golden corpus](#the-golden-corpus).
The comparison against RESTest 1.x and the published field is not part of 1.9; it stays in 8.1.

## M2 — Specification fidelity and input generation

| # | Increment | What it enables |
|---|---|---|
| 2.1a ✅ [#307](https://github.com/isa-group/RESTest/pull/307) | `allOf` folded into the canonical schema: halves combined, the stricter bound kept, a combination nothing satisfies and one we cannot work out each said plainly | The inheritance idiom real APIs describe their resources with stops being ignored |
| 2.1b ✅ [#308](https://github.com/isa-group/RESTest/pull/308) | `oneOf` / `anyOf` as a shape of their own (ADR-0018). Discriminators deliberately not included: they cost no request, and reading the corpus's one real hierarchy as a plain object would produce a shape quietly missing the property that identifies it | An operation whose parameter may be a number *or* a text stops being skipped |
| 2.2 ✅ [#310](https://github.com/isa-group/RESTest/pull/310) | Declared examples harvested, in both the 3.0 and the 3.1 shapes, on the shape and on the parameter (ADR-0019). A value the document stated now names which of its statements it came from — default, allowed list or sample — closing the question ADR-0005 parked and ADR-0013 reopened | The specification's own sample values get used: the two APIs in the priority corpus that write sample identifiers now send those identifiers instead of inventing ones |
| 2.3 → [3.1b](#m3--oracles-faults-and-reporting) | The deterministic boundary walk. Deferred at 2.7a, not dropped: it returns as the mutation operator that steps outside a documented bound by exactly one | Reproducible edge-case tests, not luck |
| 2.4 ✅ [#316](https://github.com/isa-group/RESTest/pull/316) | What a document says about the **characters** of a value, read where what it says about its length already is (ADR-0022). The kind it names — `date-time`, `date`, `email`, `uuid`, `uri`, `ipv4` and the thirteen others, nineteen in all — built from the platform's own parsers rather than looked up in a list, so every value is different and every value is correct; and the spelling it states as a `pattern` built by a library, with every candidate held against the rule again before it is sent and the length the same shape demands honoured alongside it. **No format dictionary is shipped**, which reverses that much of ADR-0013 §1 and ADR-0020 §5: a list sees only its key, while a shape states its kind, its spelling and its lengths at once and all three have to hold together | A date parameter gets a date instead of a random word, and an operation is no longer refused before anything worth testing happens: 225 places across the corpus name a kind of value, 25 of them in the priority corpus, and the 10 spelling rules in pet-clinic are satisfied rather than broken |
| 2.5a ✅ [#313](https://github.com/isa-group/RESTest/pull/313) | Request bodies, built from the shape the document declares and the samples it writes down (ADR-0021): JSON and form encoding, the media type stated in `Content-Type`, an `Accept` header built from the operation's own 2XX responses, and a property the API only ever returns never sent. XML and multipart deferred with the measurement beside them — XML wins no operation in the corpus and multipart wins one | Write operations become testable: 103 operations of the corpus, 34 of them in the priority corpus, which is 23% of its whole surface |
| 2.5b *(after 2.10a)* | The memory of what the API has returned, as dictionaries under the two keyings the format already has — one leaf by its name, a whole resource by its shape — filled by a listener on the event stream (ADR-0021 §6). With it, the operator that changes one leaf of an observed resource and sends it back. **Brings forward the runtime resource pool of 4.2**, and is the first strategy in the tool reproduced by replay rather than from the seed | A body stops being invented from nothing wherever the API has already shown what a real one looks like: 92% of the leaves in the corpus's bodies carry a name some reply also carries |
| 2.6 | Authentication inferred from `securitySchemes` (API key, bearer, basic, OAuth2 client credentials) | Protected APIs stop returning 401 for everything |
| 2.7a ✅ [#311](https://github.com/isa-group/RESTest/pull/311) | The dictionary format and its reader (ADR-0020): YAML, one file, one keying, values that may be whole objects, and no claim about what an API will make of them — which list feeds which kind of request is named in the plan. `--dictionary`, repeatable. The list of values RESTest ships to push at an API with, as the first thing written in that format, sent for the share of the budget that `--fuzzing` sets. Strategies as named shares of the budget, which is ADR-0013 §2's first half | A run finds the server errors that only unexpected input reaches, and good values for an API can be committed next to its specification instead of living in one person's head |
| 2.7b | Dictionary writer and disk cache. **Not taken in its numbered place:** it waits until the tool computes a value at a cost worth saving, which is the solver of 5.2 or the external providers of 2.8. Skip it and go on to 2.8 | Good values computed once are kept, rather than worked out again every run |
| 2.7c ✅ [#315](https://github.com/isa-group/RESTest/pull/315) | **A dictionary reaches inside a request body** (ADR-0020, amended): a place is named by the way down to it — `body.owner.email`, `body.tags[].label` — and the same ordered list of sources that fills a parameter now fills every piece of a body. An operation answers to its `operationId` **or** to `GET /pets/{petId}`, so a file can be written from the specification with no reasoning about which. Every entry that could never be used is named when the file is read, before a request is sent. **Taken out of order, before 2.5b**, which is built on the keyings this fixes | About half of what anybody writes in a dictionary stops being ignored: of the 553 places the priority corpus has, a list could fill 247 and now fills 435 — 415 of them end to end — while the other 118 are ones the document settles by itself, which the run now says out loud. A file generated from an OpenAPI document — the way most of them will be — is checked against that document before any API is touched |
| 2.8 | `ExternalDataProvider` interface: file-based implementation + out-of-process transport, asynchronous, never blocking | Any program in any language can suggest input values without slowing the run |
| 2.9 | How many optional parameters to send drawn first, from a distribution favouring small numbers, and only then which ones — replacing the separate coin flip per parameter | The request an API is most likely to accept, the one carrying only what it requires, stops being drawn once in 2ⁿ attempts |
| 2.10a ✅ | The campaign file (ADR-0023): named strategies with a share of the run, an ordered list of named sources with weighted groups among them, and the operation filter ADR-0013 §6 asks for. The shares and the order that were constants in `RandomTestCaseGenerator`'s constructor move into a file RESTest ships, prints with `--print-campaign` and reads back with `--campaign`. Three of §2's ideas dropped as saying what the structure already said, and §6's `safeOnly` answered by `methods` | Where a run's values come from stops being a decision somebody took once for everybody: it is a file you print, change one line of and hand back — and the source M2.5b adds has somewhere to be asked for |
| 2.10b | The scheduler takes the budget: time-keeping moves out of `RunLoop`, and a phase becomes a stretch of the clock rather than a share drawn per request | A share of the budget is honoured as one, rather than on average |

### Notes

**2.3 — deferred to 3.1b, not dropped.** ADR-0013 §4 puts the boundary walk with the mutation
operators: stepping outside a documented bound is a change to a request the API already accepted,
and setting several parameters outside their bounds at once teaches nothing attributable. Both
halves also need M3.1's oracles to pay off at all. Measured before deferring: 327 of 4,916 corpus
parameters declare any limit, and in the priority corpus that is pet-clinic 25, kafka 2,
flight-search 1, the other two none.

**2.4 — a generator rather than the dictionary two records expected, and the one library here
without a module descriptor.** The row said "generators" and ADR-0013 §1 said "format dictionary";
they cannot both be honoured, and ADR-0022 takes the row's word. The argument is that a list filed
under `date-time` cannot see the `maxLength` on the shape it is answering for, that an exclusive
chain would make every one of the corpus's 569 web addresses one of half a dozen fixed strings for a
whole run, and that a fresh identifier cannot come out of a file. The `format` **keying** is
untouched and is still a user's to fill — for an account number or a book's identifier, which is
where a written list beats anything the tool could invent.

Building a string backwards from a regular expression is a compiler, so it is not written here:
`rgxgen`, Apache-2.0, no dependencies of its own, confined to `restest-gen` by a rule of its own, and
taking the run's own seeded source of numbers so that repeating a run exactly needed no change. It is
the one library in this project whose jar carries no module descriptor, which costs `restest-gen` a
`requires` on a name derived from a file name; ADR-0022 weighs that against writing the engine here
and says why the packaging step it would block is not the one M7.3 uses. Every value it builds is
held against the rule again by the platform's own machinery before it is offered, so a disagreement
between two readings of a dialect costs a value rather than producing a wrong one.

What this increment also did, and is worth knowing when reading a later diff: **the shared check that
every generated value satisfies its shape now covers spelling rules**, which tightens every
generation test in the module at once. It had been left out on purpose while invention ignored
patterns, and `DeclaredSamplesAcrossTheCorpusTest` carried its own stricter copy for the samples it
judged. That copy is gone, because nothing is excused any more.

**2.7c — taken before 2.5b, and why it is a row rather than a footnote.** Measured on the priority
corpus: writing one entry per parameter, one per body and one per property inside those bodies gives
553 places - every parameter, every body, and every path inside either - of which the format as
shipped at 2.7a could fill the 200 parameter names and the 47 bodies. Entries for everything else
loaded and did nothing. 2.5b keeps what an API returned as dictionaries keyed by name and by shape, and what it
would fill is the leaves of request bodies — so landing it first would have built a mechanism that
could not reach its consumer.

**2.5 — one row that became two, and a promise narrowed on evidence.** ADR-0021 settles how a body
is built and splits the row: 2.5a builds bodies from the schema and the document's own samples, with
no memory, so a run of it is still reproduced from its seed; 2.5b gives the tool the memory of what
the API returned, which is what changes that promise. They are in that order because there is nothing
to observe until bodies are being sent.

The row promised four media types. Measured over the 46 documents of the corpus, 300 of the 340
body-taking operations offer JSON alone, 9 offer only a form, 1 only multipart, and **XML is never
offered without JSON beside it** — so a generator for it wins no operation anywhere in the corpus.
2.5a sends JSON and form encoding, which between them reach 337 of the 340, and the two that are
left out are named in ADR-0021 with the numbers, so that reversing either is a decision somebody
takes on evidence.

Both halves of the original note survive. ADR-0017 asks for the `Accept` header, built from the media
types the operation's own 2XX responses declare; we send none today, and one of the five
specifications in the priority corpus serves a versioned media type. The samples come from 2.2, which
read every sample a document writes for a *parameter* and deliberately left the ones on a request
body alone, because bodies were not generated yet and `RequestBodyModel` had nowhere to put them
(ADR-0019 §5).

**2.5b — why the mutation is of a leaf and not of a resource.** The question it answers is whether a
body is better built from the shape the document declares or from a resource the API has already
handed back. Measured: the body's named shape is also returned by some 2XX for 16 of the 44 named
bodies in the priority corpus — and for **none** of the 9 in flight-search or the 12 in
kafka-rest-proxy — while 92% of the leaves inside those bodies carry a property name that some reply
also carries. So what is observed is reused leaf by leaf, whole resources are one source among
several rather than the mechanism, and neither needs a new interface: both are the dictionary of
ADR-0020 under a keying it already has.

**2.7 — one row that became two.** It read "value dictionary format, reader, writer, disk cache",
and the writer and the cache exist to keep values the tool worked out at a cost — of which it
currently computes none. Splitting it was the alternative to shipping half a row silently, and 2.7a
carries the half that has a consumer today. Every value the tool has now is either already on disk
or free to work out again, so a cache would keep things that cost nothing — which is why 2.7b waits
for a consumer rather than being taken in its numbered place.

**2.10 — one row that became two, one feature designed and dropped, and a default settled by
measurement.** The row carries two separable subjects: *which values go into a request*, which is
the file, and *which requests are built and when*, which is the scheduler taking the budget from
`RunLoop`. Only the first is what 2.5b needed, so only the first was taken.

A third subject was designed, written into a plan and dropped before any code: **a strategy serving
only some kinds of operation**, so that writes could be pushed at harder than reads. Its one real
use is that split, and it was the sole cause of every awkward thing in the format — shares that
summed to 175, redistribution, per-operation validation, operations no strategy served. A strategy
without a scope already means every operation, so it can be added later without breaking a file
anybody has written, and 2.5b will say how the budget should actually be split.

**What the shipped plan does with the document's own samples was decided three times, and the
third time it was measured properly.** Asked in turn, a source that answers stops the ones behind
it, so a parameter whose document offers one sample gets that value for a whole run — true of 125
places in the priority corpus against 13 enumerations and no declared defaults at all. Ranking the
sample first looked right on a unit-level figure: on `getOwner` it sends the documented identifier
every time, against about a third when weighted. End to end it is worse. A run deletes rows, so the
identifier a document names stops existing partway through, and a plan that cannot vary it sends
the same 404 until the budget runs out. Against a containerised pet-clinic **restarted before every
run**, five seeds each: ranked 16.8 operations answered 2XX, weighted 19.6. So the shipped plan
weights. The lesson is about the metric — how often the tool quotes the document is not how much of
the API it reaches — and 2.5b removes the tension entirely with identifiers that are real and
varied.

**2.8 — "never blocking" is proved here, by its own test.** A slow provider must not stall the
run. Not by waiting for M6.2's overhead regression test, which lands much later and checks the
tool's overall per-request overhead, not any one extension point.

**2.9 — last in the table, dependent on nothing in it, and owed to ADR-0017.** It can be taken
whenever, and what makes it worth taking early is this: `RandomTestCaseGenerator` decides each
optional parameter with its own coin at one half, so for an operation with *n* of them the request
carrying only what the API requires — the one most likely to be accepted — is drawn about once in 2ⁿ
attempts. Eight optional parameters is once in 256. It is nominal generation rather than a
deliberate violation, so it is not 2.3's subject. Whoever takes it measures the effect on how
quickly operations are covered rather than assuming it: ADR-0017 is explicit that the assumption is
untested, and being wrong about it is the cheapest thing in M2 to find out.

**2.10 — finishes what 2.7a started, and has to make its own case for weights now.** A weighted
group divides one value between the sources that answered for it, so it needs two sources that
answer for the same value before it means anything. Measured over the corpus, the only place two
sources compete without anybody writing a file is a schema declaring both a `default` and a sample
and no enumeration — 26 parameters of 5,119, every one of them in a single document of the fifty.

This note used to say that 2.4's shipped format dictionary would be the competitor that makes
weights worth having. **It was not built** (ADR-0022): a value of a named kind is invented rather
than looked up, and invention is last in the chain by definition, so it never competes with
anything. What is left is the case ADR-0020 §5 already documents and the paragraph below repeats —
a list somebody writes keyed to a whole kind of value replaces invention for that kind, for the
whole run, when what they wanted was to be one voice among several. That is a smaller case than the
one promised here, and whoever takes 2.10 should say so rather than inheriting a sentence that is no
longer true.

2.7a built the first half of ADR-0013 §2 — strategies with shares — because a run had to divide its
time between ordinary requests and requests built to be refused before it could send either. It left
the numbers as constants in code, and 2.10 is where they become a file somebody can edit. It also
left a footgun that 2.10 closes: with only exclusive groups, a list of values keyed to a kind of
value *replaces* what the tool would otherwise have invented for that kind rather than being sent
alongside it, which is what a weighted group is for (ADR-0020 §5).

## M3 — Oracles, faults and reporting

| # | Increment | What it enables |
|---|---|---|
| 3.1 | WFC catalogue, first tranche: status-code conformance, content type, response headers, negative-data rejection, positive-data acceptance, missing required header, unsupported method | Many more kinds of bug detected |
| 3.1b | **Deliberate violations, as mutations of requests the API accepted** (ADR-0013 §4). The test case gains the *intent* §3 describes — I believe these values are acceptable, I expect this refused and here is what I broke, or I do not know. A listener on the event stream keeps a bounded index of the test cases that actually returned 2XX (§5), and operators take one of those and change exactly one thing: drop a required parameter, send the wrong type, step outside a documented bound, break an enumeration, break a pattern, and send a required parameter in a location it was not declared in. **M2.3's deterministic boundary walk returns here**, as the operator that steps outside a documented limit by exactly one. The third share of the budget arrives with it | The tool stops only being able to ask "does this work?" and starts being able to ask "does it refuse what it should?" — and when an API accepts a request RESTest deliberately broke, the fault names the one thing that was changed |
| 3.2 | HTTP-semantics and REST-design oracles (WFC 900–909 and 950–965) | Protocol-level bugs nobody else on our side detects |
| 3.3 | `CorpusOracle` interface and `restest recheck <run>` | Re-examine a finished run with new oracles, offline, no API calls |
| 3.4 | Per-operation oracle configuration + published JSON Schema for the config file | False positives silenced per operation instead of the tool being switched off |
| 3.5 | Reports: HTML, JUnit XML, HAR, NDJSON; JUnit 5 + REST-Assured code export; `restest explain`; `restest replay`. Every format renders both classifications of a fault — by catalogue number and by the class of status code that carried it (ADR-0016). Plus the "how to add an oracle, a provider, a report" guide | Results usable in CI, in an IDE, and by a human |
| 3.6 | Replies that no rule could judge counted, and said out loud in the summary, the JSON report and the exit code | A clean bill of health stops being ambiguous: a run that could not check something says so, instead of saying nothing was wrong |
| 3.7 | What a run writes when it is cut short: Ctrl-C leaves the summary, the report and a closed store behind, or says plainly that it could not (ADR-0015 lists the three candidate answers) | Stopping a long run early stops costing you everything it had already found |

### Notes

**3.1b — why it sits in M3 although the code is generator-side.** It is numbered after 3.1 rather
than given a number of its own because that is the order it has to be taken in: 3.1 supplies the two
oracles that make a broken request worth sending at all — negative data must be refused, positive
data must be accepted. Built before them, every deliberate violation would earn a 4XX that nothing
reads and no finding anybody could act on.

**3.1b — the two debts it pays.** The **intent** of ADR-0013 §3 was assigned to M1.6 and never
built; M2.2 and M2.7a each left it out again for the same reason, that no oracle reads it — so 3.1b
is the first increment where it is not a mechanism waiting for a consumer. And **M2.3's boundary
walk** was deferred here: §4 says stepping outside a documented bound is a change to a request the
API already accepted, so the walk needs the memory of accepted requests that this increment builds.

**3.1b — the one promise that changes shape**, and ADR-0013 §7 already says how. A strategy with a
memory is not reproduced from the seed — what gets mutated depends on what the API answered and when
— so a run using it is reproduced by replaying the stored requests instead. The seed keeps its other
three jobs: deterministic tests, reproducing a failure that happens before any request exists, and
the fixed workload M6.2's overhead test compares commits against.

**3.1b — the operator ADR-0017 adds**, under ADR-0013 §4: send a *required* parameter in a location
it was not declared in — query, header, cookie. The API is then missing something it said it needs,
so a refusal is correct and a 2XX is attributable to that one change. Restricting it to required
parameters is what keeps it inside §4's contract, and it is why ADR-0017 refuses the companion
operator that sends parameters the document never declared: there, both answers are defensible and
no oracle can call it.

**3.5 — the reports are raw, and deliberately.** Every fault is counted on its own and none is ever
declared to be the same problem as another. What the JSON report bounds (M1.7b) is how many faults
of one kind, on one operation, it quotes at length — an allowance over two fields already recorded,
not a judgement that two faults share a cause. Deduplication and clustering is
[deferred](#deferred--not-in-v20), not a gap in 3.5, and a milestone campaign's cross-tool
comparison is the evaluation harness's job, not this report's.

## M4 — Stateful testing

| # | Increment | What it enables |
|---|---|---|
| 4.1 | Operation Dependency Graph inferred from names, types and schemas | The tool knows `POST /pets` must precede `GET /pets/{id}` |
| 4.2 | Runtime resource pool and value-source selection | Identifiers from real responses get reused instead of invented |
| 4.3 | Declared OpenAPI `links` consumed when present | Free accuracy on the few specifications that declare them |
| 4.4 | CRUD lifecycle model and sequence generation | Create-read-update-delete flows are exercised end to end |
| 4.5 | Stateful oracles: use-after-free, resource availability, failed update must not change, update idempotency | Bugs that only appear across several requests |
| 4.6 | 🛑 Arazzo import/export *(droppable — decide at the end of M4)* | Discovered flows become a standard, shareable document |

### Notes

**4.1 — ADR-0017 gives it its shape before it is written.** It matches *properties* rather than
operations — a parameter against the parameters, the body properties and the response properties of
every other operation — keeps the best few candidates even when none is convincing so that no
operation is left with nothing to try, and lets the graph grow during a run from properties that
appear in real replies and that the document never declared. Similarity is computed with no model:
names split on case and separators, a gate on schema and format compatibility, and a hand-written
table of synonyms carried as versioned data. 4.1 owes one measurement and one ADR of its own:
annotate the correct matches across the golden corpus by hand, compare this mechanism against a
table of word vectors, and record the threshold and the outcome.

**4.2 — the resource pool arrives at 2.5b, and what is left here is the choosing.** The row reads
"runtime resource pool and value-source selection", and ADR-0021 brings the pool forward: bodies
cannot be built well without the memory of what the API returned, and that memory is one listener and
two dictionary keyings. What stays here is the half that needs 4.1's graph.

**4.2 — it receives those candidates and chooses among them.** Whether that choice may be scored by
what the API answered is one of [ADR-0017's open questions](#the-three-open-questions), because it
is an online estimate of whether a request will be accepted. 4.2 also has to reconcile the
candidates with ADR-0013's rule that a sequence creates what it needs rather than borrowing an
identifier — and narrowing that rule would be an amendment to ADR-0013.

## M5 — IDL and constraint-based generation

| # | Increment | What it enables |
|---|---|---|
| 5.1 | Relicensed IDL assets imported; ANTLR4 parser; differential conformance test over the existing IDL corpus | The language works without dragging in Xtext |
| 5.2 | `ConstraintSolver` interface + Choco backend | Solving is replaceable and testable in isolation |
| 5.3 | IDL4OAS read/write, constraints carrying provenance and confidence | Dependencies can come from somewhere other than a hand-written file |
| 5.4 | Constraint-based generator: valid requests and deliberate dependency violations | RESTest's differentiator, back and usable |
| 5.5 | 🛑 Constraint-aware oracles (`2XX_P`, `2XX_D`, `4XX`) + the ICSOC'20 experiment re-run | The two novel oracles work, and we can compare against the published 1.x numbers |

## M6 — Live updates and performance

| # | Increment | What it enables |
|---|---|---|
| 6.1 | `ConstraintSource` and `FlowSource` with a watched-directory implementation | Drop an IDL snippet in mid-run and watch the generated requests change |
| 6.2 | 🛑 Idle-time reporting and the per-request overhead regression test wired into CI | We can prove the 2026 failure mode cannot recur |

## M7 — Packaging and distribution

| # | Increment | What it enables |
|---|---|---|
| 7.1 | Maven Central publication through the Central Portal | `restest-core` usable as a dependency |
| 7.2 | Release automation: Homebrew, SDKMAN, Docker, jbang, GitHub Releases | `brew install restest` |
| 7.3 | 🛑 GraalVM native binary with an executing smoke test; GitHub Action; documentation site | Sub-100 ms startup, no Java needed, usable in anyone's CI |

## M8 — Evaluation

| # | Increment | What it enables |
|---|---|---|
| 8.1 | Full campaign against the 2026 field | The table that goes in the paper |
| 8.2 | 🛑 Ablation study and replication package | Every claim is reproducible by a reviewer |

---

## The golden corpus

The specifications used throughout M1 and M2 live at
`restest-spec/src/test/resources/specifications/`, in three directories: `restleague-2027/`,
`community/` and `fixtures/`.

`restleague-2027/` — the five APIs named in the [2027 REST League
benchmark](https://seunivr.github.io/RestLeague/2027/) — is the **priority corpus**: these are the
APIs the tool is actually evaluated against, so exercise them first in every increment's tests,
before the wider `community/` corpus.

Exact provenance, so the five files can be re-fetched or checked for drift:

| Directory | Upstream project | `openapi.yaml` pinned at |
|---|---|---|
| `flight-search/` | github.com/Rapter1990/flightsearchapi | github.com/restgym/flight-search-api@2838238, `specifications/flight-search.yaml`, fetched 2026-09-12 |
| `gestao-hospital/` | github.com/ValchanOficial/GestaoHospital | github.com/restgym/gestao-hospital-api@d4cb6c7, `specifications/gestao-hospital.yaml`, fetched 2026-09-12 |
| `kafka-rest-proxy/` | github.com/confluentinc/kafka-rest | github.com/restgym/kafka-rest-proxy-api@26d839b, `specifications/kafka-rest-proxy.yaml`, fetched 2026-09-12 |
| `notebook-manager/` | github.com/birddevelper/NoteBookManager | github.com/restgym/notebook-manager-api@d4f29e4, `specifications/notebook-manager.yaml`, fetched 2026-09-12 |
| `pet-clinic/` | github.com/spring-petclinic/spring-petclinic-rest | github.com/restgym/pet-clinic-api@e8250db, `specifications/pet-clinic.yaml`, fetched 2026-09-12 |

Each of those upstream projects packages its own specification inconsistently or not at all; the
pinned fork is simply where a ready, single specification file per API could be fetched from — no
other coupling to that infrastructure is implied or intended (ADR-0011 still holds, and since M1.9
holds more strongly: nothing in this repository depends on it, builds against it, or assumes its
layout).

## Deferred — not in v2.0

Do not start any of these without explicit approval, even where one looks easy. Each names the
extension point it will use, so none of them requires re-architecting. The full table, with the seam
named for each, is under "Out of scope for v2.0" in [`docs/DESIGN.md`](docs/DESIGN.md).

- Re-integrating the LangGraph / small-model data generator → external provider
- Fine-tuned small models for input values → external provider
- Refining values from the API's own error messages → external provider + feedback
- Inferring inter-parameter dependencies and injecting them as IDL during the run → constraint
  source
- Semantic oracles inferred from request/response corpora → corpus oracles + store + `recheck`
- Semantic oracles inferred from the specification → corpus oracles
- Metamorphic relations → corpus oracles
- Failure deduplication and clustering → interaction store + an event-stream listener
- Predicting whether a request will be accepted before sending it → feedback
- Search-based or reinforcement-learning scheduling → feedback
- Surrogate coverage goals for black-box search → feedback
- Security oracles (injection, SSRF, authorisation bypass) → oracle interface
- Flow discovery from execution traces → flow source

### The three open questions

Three of those rows have an open question against them, all raised by ADR-0017 and all of the same
kind: each would have a run learn from what it has already seen. 🛑 None is started without explicit
approval.

- **Scheduling.** The winner of the 2026 competition rewards its choice of *operation* for errors
  while rewarding its choice of parameters and values for success — go where it breaks, send
  requests that work — and weighted sampling over per-operation counters would do the same job with
  no learning and no hyper-parameters. At its narrowest it is hygiene: stop spending budget on
  operations that answer nothing but 405 or 401.
- **Predicting acceptance.** Whether M4.2 may score dependency candidates by what the API answered,
  which also costs the seed reproducibility ADR-0013 §7 promises.
- **Error messages.** Whether a warm-up may read the *text* of an error to learn which parameter was
  wrong, as opposed to reading its status code, which is ordinary scheduling.
