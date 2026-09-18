# RESTest 2.0 — roadmap

53 increments in 9 milestones, 20 of them delivered. One increment = one branch = one pull request
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
| M2 | Specification fidelity and input generation | 4 / 11 |
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
| 2.4 | Format-aware and pattern-based generators (date, e-mail, UUID, regular expressions) | Values real APIs accept |
| 2.5 | Request bodies: JSON, form encoding, multipart, XML | Write operations become testable |
| 2.6 | Authentication inferred from `securitySchemes` (API key, bearer, basic, OAuth2 client credentials) | Protected APIs stop returning 401 for everything |
| 2.7a ✅ [#311](https://github.com/isa-group/RESTest/pull/311) | The dictionary format and its reader (ADR-0020): YAML, one file, one keying, values that may be whole objects, and no claim about what an API will make of them — which list feeds which kind of request is named in the plan. `--dictionary`, repeatable. The list of values RESTest ships to push at an API with, as the first thing written in that format, sent for the share of the budget that `--fuzzing` sets. Strategies as named shares of the budget, which is ADR-0013 §2's first half | A run finds the server errors that only unexpected input reaches, and good values for an API can be committed next to its specification instead of living in one person's head |
| 2.7b | Dictionary writer and disk cache | Good values computed once are kept, rather than worked out again every run |
| 2.8 | `ExternalDataProvider` interface: file-based implementation + out-of-process transport, asynchronous, never blocking | Any program in any language can suggest input values without slowing the run |
| 2.9 | How many optional parameters to send drawn first, from a distribution favouring small numbers, and only then which ones — replacing the separate coin flip per parameter | The request an API is most likely to accept, the one carrying only what it requires, stops being drawn once in 2ⁿ attempts |
| 2.10 | The scheduler and the campaign file that tells it what to do (ADR-0013 §2 and §6): named strategies, each with a share of the budget and an ordered list of groups over named sources, where a group either stops at the first answer or **samples among the sources that answered, by weight**. The scheduler becomes the one component that knows what time it is, and carries the filters §6 gives it: which HTTP methods to exercise, whether to keep to the ones HTTP calls *safe*, and named operations to restrict a campaign to. The shares and weights 2.7a left as constants move into the file, and ADR-0013's open question gets the campaigns that answer it | A campaign is described rather than compiled in: how much of the time goes on each kind of request, which lists of values are preferred for which kinds of value and how often, and which operations and methods to touch at all — so a run against an API somebody cares about can be told to keep to the methods that only read |

### Notes

**2.3 — deferred to 3.1b, not dropped.** ADR-0013 §4 puts the boundary walk with the mutation
operators: stepping outside a documented bound is a change to a request the API already accepted,
and setting several parameters outside their bounds at once teaches nothing attributable. Both
halves also need M3.1's oracles to pay off at all. Measured before deferring: 327 of 4,916 corpus
parameters declare any limit, and in the priority corpus that is pet-clinic 25, kafka 2,
flight-search 1, the other two none.

**2.5 — an `Accept` header, and the samples a body declares.** ADR-0017 asks for the header, built
from the media types the operation's own 2XX responses declare; we send none today, and one of the
five specifications in the priority corpus serves a versioned media type. The samples come from 2.2,
which read every sample a document writes for a *parameter* and deliberately left the ones on a
request body alone, because bodies are not generated until 2.5 and `RequestBodyModel` has nowhere to
put them (ADR-0019 §5).

**2.7 — one row that became two.** It read "value dictionary format, reader, writer, disk cache",
and the writer and the cache exist to keep values the tool worked out at a cost — of which it
currently computes none. Splitting it was the alternative to shipping half a row silently, and 2.7a
carries the half that has a consumer today. 2.7b waits for something that computes values at a cost
worth saving: the solver of 5.2, or the external providers of 2.8.

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

**2.10 — comes after 2.4, and finishes what 2.7a started.** A weighted group divides one value
between the sources that answered for it, so it needs two sources that answer for the same value
before it means anything. Until 2.4 there is essentially one: measured over the corpus, the only
place two sources compete today is a schema declaring both a `default` and a sample and no
enumeration — 26 parameters of 5,119, every one of them in a single document of the fifty. 2.4's
format dictionary answers for every string with a declared `format`, which is the competitor that
makes weights worth having.

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

| Item | Extension point |
|---|---|
| Re-integrating the LangGraph / small-model data generator | External provider |
| Fine-tuned small models for input values | External provider |
| Refining values from the API's own error messages | External provider + feedback |
| Inferring inter-parameter dependencies and injecting them as IDL during the run | Constraint source |
| Semantic oracles inferred from request/response corpora | Corpus oracles + store + `recheck` |
| Semantic oracles inferred from the specification | Corpus oracles |
| Metamorphic relations | Corpus oracles |
| Failure deduplication and clustering | Interaction store + an event-stream listener |
| Predicting whether a request will be accepted before sending it | Feedback |
| Search-based or reinforcement-learning scheduling | Feedback |
| Surrogate coverage goals for black-box search | Feedback |
| Security oracles (injection, SSRF, authorisation bypass) | Oracle interface |
| Flow discovery from execution traces | Flow source |

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
