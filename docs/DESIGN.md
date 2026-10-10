# RESTest — design

What RESTest is, how it is put together, and which rules the build enforces. Written for
somebody arriving at the repository for the first time, including readers who do not program in
Java.

Individual decisions and the reasoning behind them live in [`docs/adr/`](adr/), and the rules every
contributor follows in [`AGENTS.md`](../AGENTS.md). Continuous integration and the architecture rules
are described in [`docs/ci.md`](ci.md).

---

## What it is

A black-box testing tool for REST APIs. Its only mandatory input is an OpenAPI specification and a
base URL; from those it generates test cases, executes them, and reports the failures it finds.

The design target is deliberately narrow and deliberately hard: **an unknown API, no human
configuration, a fixed time budget.** Everything below follows from those three constraints.

What RESTest does with them: it reads the document into a model of its own, skipping and
naming what it cannot test; sends every operation once with the request it is most likely to accept;
then, until the budget runs out, builds requests from a plan of weighted sources — the values the
document states, what the API has already returned, values invented to fit what the document says,
lists of values a person hands over — while part of the run pushes at the API with values nobody
sensible would send, changes accepted requests one thing at a time, and sends short series around the
things it creates. Every reply is judged by two rules, a reply of 500 and a reply whose body is not
the shape the document promised, and reported on the screen and in `report.json` with a `curl`
command that repeats it. What a user sees of all this is in [`docs/`](README.md); how it is built is
the rest of this page.

From version 2.0 on, RESTest is a rewrite rather than a refactor of RESTest 1.x. The reasoning is
recorded in [ADR-0002](adr/0002-rewrite-not-refactor.md); no source files carry over, though ideas,
the IDL grammar and the test corpus do.

## Scope

| Dimension | Decision |
|---|---|
| Specification formats | OpenAPI 2.0 (by conversion), 3.0.x and 3.1.x, through a single parser backend. Not 3.2, which is too recent to justify a second backend, and not 4.x, which has no specification text |
| Testing style | Black-box only: the specification and the API's responses, never its source |
| Test kinds | Stateless single requests, and stateful sequences across several operations |
| Distribution | A command-line tool, built from source |

Black-box is a property of the *tool*, not of the evaluation. Measuring how much of an API's code a
run exercises requires instrumenting that API, which an evaluation harness does from outside; the tool
itself never sees it.

## Glossary

Terms used throughout the repository, in commit messages and in pull requests.

| Term | Plain-language meaning |
|---|---|
| **OAS** (OpenAPI Specification) | The standard file — YAML or JSON — that describes an API: its operations, their parameters, and the shape of their responses. Our only mandatory input. |
| **Black-box testing** | Testing an API from the outside, using only its description and its responses. We never look at the API's source code. |
| **White-box testing** | Testing that also instruments the API's source code or bytecode, usually to get coverage feedback and steer the search with it. Some tools offer both modes; RESTest is black-box only. |
| **Test oracle** | The rule that decides whether a response is right or wrong. "A valid request must not return a server error" is an oracle. Generating requests is half the problem; oracles are the other half. |
| **Stateless / stateful testing** | Stateless: each test is a single, independent request. Stateful: a test is a *sequence* — create a resource, read it, update it, delete it — where each step depends on the previous one. |
| **Inter-parameter dependency** | A rule constraining how parameters can be combined, e.g. "if `location` is given, `radius` must be given too". Not expressible in OAS, which is why IDL exists. |
| **IDL** (Inter-parameter Dependency Language) | A language for writing those rules down formally, so that a machine can reason about them. |
| **IDL4OAS** | The extension that embeds IDL rules inside an OAS document, under `x-dependencies`. |
| **CSP** (Constraint Satisfaction Problem) | The mathematical form an IDL specification is translated into, so that a *solver* can compute combinations of parameter values satisfying every rule at once. |
| **Solver** | The component that does that computation. Behind an interface, so it is replaceable. |
| **SPI** (Service Provider Interface) | A deliberately small Java interface that the core defines and *somebody else* implements. The core says "give me values for this parameter" without knowing who answers. Adding a capability then means writing one small class and putting it on the classpath — no change to the core. This is the mechanism that makes the tool extensible. |
| **Event stream** | An internal broadcast: the engine announces what it is doing ("request sent", "response received", "failure found") and any number of independent listeners react. Adding an eighth report format does not touch the engine. |
| **AUC** (Area Under the Curve) | A measure of *how fast* a tool achieves something, not just how much it achieves in the end. A tool that covers 15 operations in the first minute scores far better than one covering 15 in the last minute. |
| **Idle time** | The fraction of the test budget during which the tool had no request in flight — time spent computing instead of testing. Reported on every run. |
| **Opening lap** | The first round of a run: every operation sent once, each with the request it is most likely to accept, in steps — lists, then creations, then reads of one thing, then changes, then deletions — each waiting for the answers to the one before. It is what gets operations answered in the first seconds, which is what AUC rewards. Charged to the budget, reported as a phase of its own, and switched off with `schedule.openingLap=false`. |
| **Scheduler** | The part of the tool that holds the deadline and decides which operation is sent next. It sends nothing itself: the request loop asks it what to do. |
| **Fuzzing** | Sending deliberately malformed or extreme inputs to see whether the API handles them correctly. |
| **Mutation** (of a request) | Taking a request the API *accepted* and sending it again with exactly one thing changed — a required value left out, a number one past the largest allowed, a body that is not JSON at all — so that whatever the API does next can be put down to that one change. Not to be confused with *mutation testing* below, which changes our own code. |
| **Intent** | What a test case says was expected of the API when it was built: that it would be accepted, that it would be refused (and what was broken to make it so), that it is pushing at the API with awkward values, or nothing in particular. Recorded with every request, so that a rule judging the reply later knows which answer would have been right. |
| **WFC** (Web Fuzzing Commons) | A shared, numbered catalogue of API fault types, already adopted by EvoMaster and Schemathesis. Using the same codes makes our fault reports directly comparable with theirs, instead of each tool inventing its own taxonomy. The same project publishes a file format for authentication — users, headers sent with every request, a sign-in whose token later requests carry — which four related tools read ([ADR-0029](adr/0029-the-key-an-api-asks-for.md) §11). |
| **Security scheme, API key** | How a document says an API wants callers to prove who they are. It declares each way under a name — a *key* in a header, the query or a cookie; a bearer token; OAuth 2 — and says which operations need which. The document never holds the key itself: the person running the tool hands it over with `--auth`, RESTest sends it where the document says, and hides it in everything a run writes. |
| **ArchUnit** | A library for writing *tests about the structure of the code itself*, for example "no class in the core may depend on the network layer", so architectural rules fail the build instead of eroding silently. |

## Design principles

Ten, in priority order. They are quoted in `AGENTS.md` and several are enforced mechanically; the
enforcement is listed under [Quality gates](#quality-gates).

| # | Principle | Recorded in |
|---|---|---|
| 1 | Zero configuration to start; full configuration available | |
| 2 | Never crash on a bad specification — skip the offending operation and report it | [ADR-0007](adr/0007-specification-parser-boundary.md), [ADR-0012](adr/0012-canonical-model.md) |
| 3 | Interpret, don't generate. Test cases are data; emitting code is a *report*, never the execution path | [ADR-0005](adr/0005-interpreted-test-model.md) |
| 4 | One event stream, many listeners — reports, metrics, feedback and oracles all subscribe | [ADR-0006](adr/0006-event-stream-and-store.md) |
| 5 | Narrow interfaces, discovered implementations, enforced module boundaries | [ADR-0004](adr/0004-module-structure.md), [ADR-0008](adr/0008-extension-points-no-ai-abstractions.md) |
| 6 | No global mutable state. Two runs must coexist in one JVM | An architecture test |
| 7 | The request loop is never blocked by computation; idle time is measured and reported | [ADR-0009](adr/0009-non-blocking-engine.md) |
| 8 | Open formats in, open formats out | |
| 9 | The tool runs standalone; evaluation harnesses live in a repository of their own, not in this one | [ADR-0011](adr/0011-evaluation-harness.md), amended at M1.9 |
| 10 | English everywhere; Apache-2.0; semantic versioning; published on every tag | [ADR-0002](adr/0002-rewrite-not-refactor.md) |

## Architecture

Nine production modules, each with a `module-info.java`, plus one unpublished verification module.
Dependencies point inwards, towards `restest-core`, and [ADR-0004](adr/0004-module-structure.md)
explains why that is compiled rather than conventional.

```
restest-core      domain model + interfaces. No network, no OpenAPI parser, no heavy
                  dependencies beyond a streaming JSON reader and writer and a YAML reader.
restest-spec      the only module allowed to reference the third-party OAS parser.
restest-idl       IDL language, constraints, solver interface. Empty so far.
restest-gen       generation phases, value providers, scheduler.
restest-exec      HTTP engine.
restest-store     interaction store.
restest-oracles   oracles and fault classification.
restest-report    event listeners producing output.
restest-cli       command line. The only module allowed to terminate the process.

restest-arch-tests   architecture rules. No main sources, never published.
```

### Execution pipeline

A run is a loop, not a batch. The specification is parsed into a canonical model of our own — not
the parser library's types — operations are scheduled, requests are generated and sent, responses
are captured verbatim, oracles judge them, and every step is announced on the event stream. The one
thing never captured is a key the run was handed: it is added to a request as it leaves, and replaced
in everything that comes back before anything sees it.

### The interaction store

The one architectural choice the principles above do not state. A run may persist everything it
observed, which is what makes honest post-hoc analysis possible: one run is one SQLite file,
readable by anything that reads SQLite, and there is no second store.

It is off unless `--store` asks for it. Measured when it was built, keeping a run costs 661 MiB at the default
budget and nothing in an ordinary run reads it back. Generation never reads it either way: the parts
of the tool that need a memory listen to the event stream and keep their own bounded index, because
they want what happened *recently* rather than everything, and querying the store would put a
database inside the request loop. The store is for looking back; the event stream is for reacting
([ADR-0006](adr/0006-event-stream-and-store.md), amended at M1.4 and M1.7;
[ADR-0013](adr/0013-input-generation.md) §5).

## Extension points

These are architectural requirements rather than features, and they are the whole of what
"extensible" means here. A seam is known to be real rather than assumed when something is built on
it. So far two are: the non-blocking engine, with its idle-time accounting, and `Oracle`, with the
two rules a run judges replies by. The other four are design constraints the rest of the code keeps
room for, and RESTest has no code for them yet; the items under [Out of
scope](#out-of-scope) name which of them each would use.

There are deliberately **no abstractions for particular kinds of extension** — no provider interface
named after any technology, and no dependency on any model library.
[ADR-0008](adr/0008-extension-points-no-ai-abstractions.md) sets out why: open formats and a generic
interface outlast a specific integration.

| Seam | What it is for |
|---|---|
| **Non-blocking engine** | Computation never stalls the request loop, and idle time is reported on every run, so a regression is visible rather than inferred. |
| **`ExternalDataProvider`** | Lets anything outside the tool supply input values — a dictionary committed beside the specification, a corporate test-data service, a script in any language. Asynchronous and out-of-process, so a slow provider cannot stall the run. |
| **`ConstraintSource` / `FlowSource`** | The constraint model is mutable and per-operation rather than parsed once and frozen, so new dependencies and new operation flows can arrive *while the loop is running*. |
| **`Oracle` / `CorpusOracle`** | `Oracle` judges a single interaction; `CorpusOracle` judges a queryable *set* — a whole run, one operation's slice, a sliding window. The second is what invariant- and relation-based techniques need, and retrofitting it would mean rewriting the oracle engine. |
| **`FeedbackListener`** | Observes every interaction and may return scheduling hints: operation weights, parameter weights, budget shifts. |

## Quality gates

All mechanical, all blocking. [`docs/ci.md`](ci.md) describes what runs, where each gate lives and
how to reproduce it locally.

- **Unit tests with coverage thresholds** on the core and the oracles.
- **Architecture tests**: dependencies point inwards; no global mutable state; nothing outside the
  specification module touches the third-party parser; nothing blocks the request loop; no process
  termination outside the command-line module; nothing outside the command-line module reads the
  environment or the system properties, so that every number a run uses arrives by constructor and
  two runs in one program can differ.
- **Mutation testing** on the oracles — deliberately corrupt our own code and
  check that the tests notice. "Our tool finds bugs" is more credible when our own oracle logic has
  a measured score.
- **Integration tests** against containerised open-source APIs.
- **Stub-based tests** for the HTTP layer and for fault injection: malformed bodies, delays, errors.
- **A golden corpus of specifications** — large, real, ugly ones, with recursive references,
  composition chains and OAS 3.1 type arrays — asserting "parses without throwing, and reports
  exactly N skipped operations".

## Evaluation

The quality gates above answer "does the tool work". They do not answer "is it any good", which
needs a comparison against other tools on the same APIs with the same budget.

That comparison runs on a public benchmark infrastructure that executes testing tools against a
fixed set of instrumented APIs and computes comparable metrics. Comparability with published results
is exactly what it provides, which is why it is used rather than a private benchmark of our own. A
campaign pins the exact version of both the benchmark and the tool, so it can be re-run months later
and get the same numbers.

Two boundaries make that a measurement rather than a dependency, and both are enforced rather than
intended:

- **The harness is not in this repository at all.** It lives in a repository of its own, which
  packages the tool for the benchmark and drives the campaigns. Deleting it changes nothing here.
- **Nothing here references the benchmark.** No dictionaries shipped by it, no thresholds derived
  from its verification rules, no assumptions about its layout. A test fails the build if the
  platform's name appears in any file of this repository other than the one decision record that
  explains the relationship.

A tool that has absorbed assumptions from the benchmark it is measured on is both worse engineering
and worse science. [ADR-0011](adr/0011-evaluation-harness.md) records the reasoning and the layout.

Fault reports use the WFC codes rather than a taxonomy of our own, for the same reason: a fault
count is only meaningful next to somebody else's fault count.

Every behaviour a run can do without that was added since the tool has had settings — an opening
lap, a mutation operator, a series of requests — has a switch in them
([ADR-0025](adr/0025-settings.md)), and a run records which switches it ran with. An ablation is
therefore a file handed over with `--settings` or `--campaign` rather than a branch per variant, and
its results say what they measured. [The switches](switches.md) lists every one, gives the files
that turn off a whole idea or family of ideas at once and the plan without the memory of what the
API returned, which is a source rather than a switch, and names the three older behaviours that
have no switch yet. A test holds that page to the tool.

## Related tools

This section maps the REST API testing landscape for readers new to the field: the tools most often
compared with RESTest in academic evaluations and benchmarks, one paragraph each, followed by a
[comparison table](#comparison) whose columns are explained above it. The terms it uses — stateless
and stateful testing, black-box and white-box — are in the [glossary](#glossary). The descriptions
of RESTler, EvoMaster, Schemathesis, RestTestGen and CATS were corrected on 10 October 2026 where
their code no longer agreed: RESTler at commit `6d984dee`, EvoMaster at release 6.2.0 and its
development branch at commit `b72feb25`, Schemathesis 4.30.1, RestTestGen v25.12 (commit
`63c19634`) and CATS 14.0.0. Tools change, and a later version may do more.

### AutoRestTest

[AutoRestTest](https://github.com/selab-gatech/autoresttest), from Georgia Tech, came first in
fault detection, efficiency and effectiveness in a 2026 public comparison of black-box REST API
testing tools. It works in two phases. Before testing begins it builds a dependency graph by
comparing the *names* of parameters, body properties and response properties across operations —
with a table of static word vectors, not a language model — and it asks a language model for a pool
of candidate values for every parameter, refining them with the error replies of a couple of probe
requests. The testing phase is then a sequential loop over six learned tables, one for each decision
a request needs: which operation, which parameters, which values, which body properties, which
dependency, which credentials. Its own ablation study removes one component at a time and reports
that removing the learning costs it more than removing the language model.
[ADR-0017](adr/0017-what-we-take-from-autoresttest.md) records what RESTest takes from it, what
it refuses, and what that ablation does and does not establish.

### EvoMaster

[EvoMaster](https://github.com/WebFuzzing/EvoMaster) applies search to REST API testing. Its
white-box mode instruments a JVM application at the bytecode level and feeds coverage into MIO
(Many-Independent-Objective), an evolutionary algorithm that treats each coverage target as an
independent objective, which avoids the stalling that affects single-objective search. Its
black-box mode, the default since 6.0.0, needs only the specification and the running API, and
samples requests rather than evolving them. Call sequences come from the path hierarchy — a creation
before a read of what it created — with identifiers chained from replies; the white-box mode also
infers which resources depend on which. EvoMaster's authors maintain the WFC fault catalogue, and
EvoMaster reports by it, in 37 categories on its development branch.

### RESTler

[RESTler](https://github.com/microsoft/restler-fuzzer), from Microsoft Research, introduced
stateful REST fuzzing guided by the API's replies. A compiler written in F# turns the specification
into a *grammar*: a description of every request RESTler may send, with a slot for each value. While
compiling, it infers *producer-consumer* relationships, for example that `POST /orders` produces an
order identifier that `DELETE /orders/{id}` later consumes. An engine written in Python then chains
operations from the grammar, extending the sequences that worked. Values come from a fixed
dictionary of type-appropriate values, which a user can extend per parameter name; identifiers come
from live replies. Every 5xx counts as a bug, and checkers replay sequences to surface
resource-state problems, such as a resource still usable after it was deleted. Guidance by code
coverage came later, in an extension named Pythia.

### Schemathesis

[Schemathesis](https://github.com/schemathesis/schemathesis) is a Python library and CLI built on
[Hypothesis](https://hypothesis.readthedocs.io/), a property-based testing framework. It generates
inputs from the OpenAPI schema and automatically shrinks failing cases to their minimal form. It
supports OAS 2.0, 3.0.x, 3.1.x and 3.2, integrates as a pytest plugin, classifies findings using WFC
fault codes, and for stateful testing follows OAS `links` and links it infers from the document's
names and from `Location` headers.

### RestTestGen

[RestTestGen](https://github.com/SeUniVr/RestTestGen), from the University of Verona, focuses on
*nominal* and *error* testing. An Operation Dependency Graph, built from the names of parameters and
response fields, orders the operations, and the requests that worked are then mutated to trigger
error responses. Sequences over one resource — create then read, update then read — appear only in its
mass-assignment strategy. It reads OpenAPI 3.0 with a parser of its own, supports IDL-based
inter-parameter constraints, and emits results as JUnit 5 tests.

### CATS

[CATS](https://github.com/Endava/cats) (Contract API Testing and Security, from Endava) offers a
large catalogue of *fuzzers*, each targeting a specific class of input anomaly: boundary values,
special characters, Unicode edge cases, oversized payloads, missing required fields, and extra
unexpected fields. It is designed for repeatable contract testing in CI pipelines rather than for
finding deep behavioural bugs. In release 14.0.0 its stateful checks are two: a `DELETE` takes its
identifier from an earlier `POST`'s reply, and a deleted resource is read again to check it is gone;
its development branch, which its documentation site follows, adds a pool of identifiers and more.

### Dredd

[Dredd](https://github.com/apiaryio/dredd) is a JavaScript contract-testing tool. It executes the
examples embedded in an OAS document against the running API and checks that the responses match.
Stateful sequences require hand-written hook scripts (JavaScript or Python). Dredd is well suited to
regression-testing documented behaviour but is not designed to discover undocumented bugs.

### RESTest 1.x

[RESTest 1.x](https://github.com/isa-group/RESTest/tree/v1.x), also from the ISA Research Group,
implements constraint-based testing (CBT) driven by IDL, random testing, and ART in a single
configurable pipeline. Limited stateful support is available via hand-written test flow
configurations. From version 2.0 on, RESTest is a ground-up rewrite; the reasoning is in
[ADR-0002](adr/0002-rewrite-not-refactor.md).

### Comparison

The columns *stateless techniques* and *stateful techniques* name the core algorithmic strategy, not
every configuration option. **OAS** — specification versions the tool accepts. **BB/WB** — B =
black-box only; B+W = black-box and white-box modes both available. The last row shows RESTest
as it is now.

| Tool | Language | OAS | BB/WB | Stateless techniques | Stateful techniques | Test data types | Oracle types |
|---|---|---|---|---|---|---|---|
| [AutoRestTest](https://github.com/selab-gatech/autoresttest) | Python | 3.0.x | B | Tabular reinforcement learning over operation, parameter and value choices; mutation | Property-level dependency graph from name similarity, scored at run time | Language-model value pools, response-derived, random | 5xx detection |
| [EvoMaster](https://github.com/WebFuzzing/EvoMaster) | Kotlin/Java | 2.0, 3.0.x, 3.1.x | B+W | Evolutionary (MIO) in white box, random sampling in black box | Sequences from the path hierarchy with identifiers chained from replies; resource dependencies in white box | Evolutionary, random, examples, reply values | 5xx detection, HTTP semantics, schema validation, security (37 WFC categories on its development branch) |
| [RESTler](https://github.com/microsoft/restler-fuzzer) | Python, F# | 2.0, 3.0 | B | Grammar-based fuzzing guided by replies | Producer-consumer chains (spec-inferred), extended breadth first | Fixed dictionary per type or parameter name; identifiers from replies | 5xx detection, resource-state checkers (use after delete, leakage, hierarchy) |
| [Schemathesis](https://github.com/schemathesis/schemathesis) | Python | 2.0, 3.0.x, 3.1.x, 3.2 | B | Property-based (Hypothesis), shrinking | OAS link following, links inferred from names and `Location` headers | Examples, boundary values, random within the schema, invalid values from a mutated schema (on by default), values from replies | 5xx detection; status code, content type, header and schema conformance; HTTP semantics (unsupported method, `Allow`, missing required header); use after free, resource not available after creation; invalid data accepted or valid data refused; ignored authentication; WFC codes |
| [RestTestGen](https://github.com/SeUniVr/RestTestGen) | Java | 3.0 | B | Random, IDL-constrained; mutation of requests that worked | Operation Dependency Graph ordering; create-then-read and update-then-read sequences in the mass-assignment strategy only | Random, example-based, IDL-constrained | Status code classification |
| [CATS](https://github.com/Endava/cats) | Java | 2.0, 3.0.x, 3.1.x | B | Fuzzing catalogue (BVA, special chars, Unicode, oversized, field mutation) | `DELETE` after `POST`; read after delete | Fuzzing patterns, boundary values | Status codes, schema validation |
| [Dredd](https://github.com/apiaryio/dredd) | JavaScript | 2.0, 3.0 | B | Example-based contract testing | Scripted hooks (manual) | Spec examples | Status codes, response schema |
| [RESTest 1.x](https://github.com/isa-group/RESTest/tree/v1.x) | Java | 2.0, 3.0 | B | CBT (IDL), random, ART | Hand-written test flows | Random, IDL-constrained, example-based | Status code classification, schema validation |
| **RESTest** (this tool, 2.0 on) | Java | 2.0, 3.0.x, 3.1.x | B | Random with a plan of weighted sources; mutation of accepted requests; shape fuzzing | Identifier reuse from replies, by name and by the resource a path names; one-question series around a thing the run created: read after delete, delete twice, write under a deleted thing, the same PUT twice, reading around a read, the same creation twice | Document samples, dictionaries, response-derived, random, format-aware | 5xx detection, schema validation, WFC codes |

## Out of scope

Nothing here is started without explicit approval,
even where it looks easy. Each entry names the seam it will use, so none of them requires
re-architecting — which is the point of listing them at all.

| Item | Seam |
|---|---|
| External value generators, including model-based ones | `ExternalDataProvider`, out-of-process |
| Refining values from the API's own error messages | `ExternalDataProvider` + `FeedbackListener` |
| **Inferring inter-parameter dependencies** and injecting them as IDL during the run | `ConstraintSource` |
| **Semantic oracles inferred from request/response corpora** | `CorpusOracle` + store + offline re-check |
| Semantic oracles inferred from the specification | `CorpusOracle` |
| Metamorphic relations | `CorpusOracle` |
| Failure deduplication and clustering | Interaction store + an event-stream listener |
| Predicting whether a request will be accepted before sending it | `FeedbackListener` |
| Search-based or reinforcement-learning scheduling | `FeedbackListener` |
| Surrogate coverage goals for black-box search | `FeedbackListener` |
| Security oracles (injection, server-side request forgery, authorisation bypass) | `Oracle` + WFC codes |
| Flow discovery from execution traces | `FlowSource` |

Three of these rows have an open question against them, all raised by
[ADR-0017](adr/0017-what-we-take-from-autoresttest.md) after studying AutoRestTest, and all of the
same kind: each would have a run learn from what it has already seen.

- Whether the choice of which operation to call next may be steered by counters over what each
  operation has been answering. *Its narrowest version — withdrawing budget from operations whose
  recent answers all say the request can never work as asked, with no reward and no learning rate —
  was approved during the work on 2.0 and set aside before it was built, once its list was narrowed to the answers
  that do not depend on what the tool sends; the reward-shaped version stays here.*
- Whether the choice among inferred dependency candidates may be scored by what the API answered.
- Whether a warm-up may read the *text* of an error reply rather than only its status code.

None is taken here, and none is started without explicit approval. What each would cost, and how
narrow a version of it would still be worth having, is argued under
[Open questions](adr/0017-what-we-take-from-autoresttest.md#open-questions) in ADR-0017; its
[M9.4 amendment](adr/0017-what-we-take-from-autoresttest.md#amendment-m94) records why the narrowest
version of the first was set aside.

## Stack

Versions are pinned here and in the root POM; the two are expected to agree.

| Component | Choice | Version |
|---|---|---|
| Library bytecode | Java 21 — every module, CLI included ([ADR-0003](adr/0003-java-baseline.md)) | — |
| Build toolchain | JDK 25 (long-term support) | 25 |
| Build | Maven with wrapper | 3.9.16 |
| OAS parser | `io.swagger.parser.v3:swagger-parser`, behind our own interface | 2.1.47 |
| JSON Schema validation | `com.networknt:json-schema-validator`, confined to `restest-oracles` ([ADR-0014](adr/0014-response-conformance.md)) | 3.0.7 |
| Command-line framework | picocli | 4.7.7 |
| HTTP client | OkHttp — network interceptors give exact request and response capture | 5.5.0 |
| Concurrency | Virtual threads | JDK 21+ |
| Interaction store | SQLite (`org.xerial:sqlite-jdbc`), one file per run ([ADR-0006](adr/0006-event-stream-and-store.md), amended at M1.4) | 3.50.3.0 |
| JSON reader and writer | `com.fasterxml.jackson.core:jackson-core`, confined to `restest-core` ([ADR-0006](adr/0006-event-stream-and-store.md), amended at M1.6) | 2.22.1 |
| YAML reader for the files a person writes | `org.yaml:snakeyaml`, confined to `restest-core` ([ADR-0006](adr/0006-event-stream-and-store.md), amended at M11.1) | 2.6 |
| Strings matching a regular expression | `com.github.curious-odd-man:rgxgen`, confined to `restest-gen` ([ADR-0022](adr/0022-the-characters-a-value-is-made-of.md)) | 3.0 |
| Unit tests | JUnit Jupiter | 6.1.3 |
| Assertions | AssertJ | 3.27.7 |
| Container-based integration tests | Testcontainers | 2.0.5 |
| HTTP stubbing and fault injection | WireMock | 3.13.2 |
| Mutation testing | PIT | 1.30.0 |
| Architecture tests | ArchUnit | 1.5.0 |
| Coverage | JaCoCo | 0.8.15 |

## External references

Standards:

- OpenAPI Specification — https://spec.openapis.org/oas/
- Web Fuzzing Commons, the shared fault catalogue — https://github.com/WebFuzzing/Commons

## Contributing

Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first. In short: work targets `master`, one change per
branch per pull request, English throughout, and a design question with more than one defensible
answer becomes an ADR rather than a silent choice in the code.
