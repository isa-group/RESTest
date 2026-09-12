# RESTest 2.0 — design

What RESTest 2.0 is, how it is put together, and which rules the build enforces. Written for
somebody arriving at the repository for the first time, including readers who do not program in
Java.

Individual decisions and the reasoning behind them live in [`docs/adr/`](adr/). The work breakdown
lives in [`ROADMAP.md`](../ROADMAP.md). Continuous integration and the architecture rules are
described in [`docs/ci.md`](ci.md).

---

## What it is

A black-box testing tool for REST APIs. Its only mandatory input is an OpenAPI specification and a
base URL; from those it generates test cases, executes them, and reports the failures it finds.

The design target is deliberately narrow and deliberately hard: **an unknown API, no human
configuration, a fixed time budget.** Everything below follows from those three constraints.

v2.0 is a rewrite rather than a refactor of RESTest 1.x. The reasoning is recorded in
[ADR-0002](adr/0002-rewrite-not-refactor.md); no source files carry over, though ideas, the IDL
grammar and the test corpus do.

## Scope

| | |
|---|---|
| Specification formats | OpenAPI 2.0 and every 3.x, 3.2 included. New 3.x minors are tracked as they are published. Not 4.x, which has no specification text |
| Testing style | Black-box only: the specification and the API's responses, never its source |
| Test kinds | Stateless single requests, and stateful sequences across several operations |
| Distribution | Command-line tool, library dependency, container image, and native binary |

Black-box is a property of the *tool*, not of the evaluation. Measuring how much of an API's code a
run exercises requires instrumenting that API, which a benchmark harness does from outside; the tool
itself never sees it.

## Glossary

Terms used throughout the repository, in commit messages and in pull requests.

| Term | Plain-language meaning |
|---|---|
| **OAS** (OpenAPI Specification) | The standard file — YAML or JSON — that describes an API: its operations, their parameters, and the shape of their responses. Our only mandatory input. |
| **Black-box testing** | Testing an API from the outside, using only its description and its responses. We never look at the API's source code. |
| **Test oracle** | The rule that decides whether a response is right or wrong. "A valid request must not return a server error" is an oracle. Generating requests is half the problem; oracles are the other half. |
| **Stateless / stateful testing** | Stateless: each test is a single, independent request. Stateful: a test is a *sequence* — create a resource, read it, update it, delete it — where each step depends on the previous one. |
| **Inter-parameter dependency** | A rule constraining how parameters can be combined, e.g. "if `location` is given, `radius` must be given too". Not expressible in OAS, which is why IDL exists. |
| **IDL** (Inter-parameter Dependency Language) | A language for writing those rules down formally, so that a machine can reason about them. |
| **IDL4OAS** | The extension that embeds IDL rules inside an OAS document, under `x-dependencies`. |
| **CSP** (Constraint Satisfaction Problem) | The mathematical form an IDL specification is translated into, so that a *solver* can compute combinations of parameter values satisfying every rule at once. |
| **Solver** | The component that does that computation. Behind an interface, so it is replaceable. |
| **SPI** (Service Provider Interface) | A deliberately small Java interface that the core defines and *somebody else* implements. The core says "give me values for this parameter" without knowing who answers. Adding a capability then means writing one small class and putting it on the classpath — no change to the core. This is the mechanism that makes the tool extensible. |
| **Event stream** | An internal broadcast: the engine announces what it is doing ("request sent", "response received", "failure found") and any number of independent listeners react. Adding an eighth report format does not touch the engine. |
| **ODG** (Operation Dependency Graph) | A map, inferred automatically, of which operations produce data that other operations need. It tells the tool that `POST /pets` should run before `GET /pets/{id}`. |
| **AUC** (Area Under the Curve) | A measure of *how fast* a tool achieves something, not just how much it achieves in the end. A tool that covers 15 operations in the first minute scores far better than one covering 15 in the last minute. |
| **Idle time** | The fraction of the test budget during which the tool had no request in flight — time spent computing instead of testing. Reported on every run. |
| **Fuzzing** | Sending deliberately malformed or extreme inputs to see whether the API handles them correctly. |
| **WFC** (Web Fuzzing Commons) | A shared, numbered catalogue of API fault types, already adopted by EvoMaster and Schemathesis. Using the same codes makes our fault reports directly comparable with theirs, instead of each tool inventing its own taxonomy. |
| **RESTGym** | The Docker-based infrastructure behind the SBFT REST League: it runs testing tools against a fixed set of instrumented APIs and computes comparable metrics. We drive it for milestone campaigns; the tool itself never references it. |
| **ANTLR4** | A library for turning a grammar — the formal definition of a language such as IDL — into a parser. |
| **ArchUnit** | A library for writing *tests about the structure of the code itself*, for example "no class in the core may depend on the network layer", so architectural rules fail the build instead of eroding silently. |
| **Native image** | Compiling the tool into a standalone executable that starts in well under a second, with no Java installation required. |

## Design principles

Ten, in priority order. They are quoted in `CLAUDE.md` and several are enforced mechanically; the
enforcement is listed under [Quality gates](#quality-gates).

1. Zero configuration to start; full configuration available.
2. Never crash on a bad specification — skip the offending operation and report it.
3. Interpret, don't generate. Test cases are data; emitting code is a *report*, never the execution
   path.
4. One event stream, many listeners.
5. Narrow interfaces, discovered implementations, enforced module boundaries.
6. No global mutable state. Two runs must coexist in one JVM.
7. The request loop is never blocked by computation; idle time is measured and reported.
8. Open formats in, open formats out.
9. The tool runs standalone; evaluation harnesses live outside the build and are optional.
10. English everywhere; Apache-2.0; semantic versioning; published on every tag.

## Architecture

Nine production modules, each with a `module-info.java`, plus one unpublished verification module.
Dependencies point inwards, towards `restest-core`, and [ADR-0004](adr/0004-module-structure.md)
explains why that is compiled rather than conventional.

```
restest-core      domain model + interfaces. No network, no parser, no heavy dependencies.
restest-spec      the only module allowed to reference the third-party OAS parser.
restest-idl       IDL language, constraints, solver interface.
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
are captured verbatim, oracles judge them, and every step is announced on the event stream. Everything
observed is persisted, which is what makes offline re-analysis possible.

### Key decisions

1. **Interpret, don't generate.** Test cases are data executed by an HTTP client. Emitting JUnit,
   REST-Assured, `curl` or overlay documents is a *report*, not the execution path.
   [ADR-0005](adr/0005-interpreted-test-model.md)
2. **One event stream, many listeners.** Reports, metrics, feedback and oracles all subscribe.
   [ADR-0006](adr/0006-event-stream-and-store.md)
3. **Persist everything.** The interaction store is what makes offline re-checking, corpus oracles
   and honest post-hoc analysis possible, and it costs almost nothing.
4. **No global mutable state.** Two runs coexist in one JVM. Enforced by an architecture test.
5. **The loop is never blocked; idle time is reported.** [ADR-0009](adr/0009-non-blocking-engine.md)
6. **Narrow SPIs, service discovery, module boundaries.** A new oracle or provider is one class.
7. **The tool is standalone.** A specification and a base URL are all it ever requires. Evaluation
   harnesses live in `evaluation/`, outside the build, optional, and invisible to the Java code.
   [ADR-0011](adr/0011-evaluation-harness.md)

## Extension points

These are architectural requirements rather than features, and they are the whole of what
"extensible" means here. Each is verified during its milestone by writing a throwaway
implementation, demonstrating it, and deleting it — so that the seam is known to be real rather than
assumed.

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
  termination outside the command-line module.
- **Mutation testing** on the oracle and constraint packages — deliberately corrupt our own code and
  check that the tests notice. "Our tool finds bugs" is more credible when our own oracle logic has
  a measured score.
- **Integration tests** against containerised open-source APIs.
- **Stub-based tests** for the HTTP layer and for fault injection: malformed bodies, delays, errors.
- **A golden corpus of specifications** — large, real, ugly ones, with recursive references,
  composition chains and OAS 3.1 type arrays — asserting "parses without throwing, and reports
  exactly N skipped operations".
- **Per-request overhead regression test** against a local stub with fixed latency. The comparison
  is against our own measured overhead, not an arbitrary throughput floor, because throughput depends
  on the API's response time and a fixed threshold would punish us for slow APIs.
- **Native binary smoke test that actually runs the binary**, because a native build that succeeds
  and then dies on first use is the classic failure mode.

## Evaluation

The quality gates above answer "does the tool work". They do not answer "is it any good", which
needs a comparison against other tools on the same APIs with the same budget.

That comparison runs on **RESTGym**, the Docker-based infrastructure behind the SBFT REST League: it
executes testing tools against a fixed set of instrumented APIs and computes comparable metrics.
Comparability with published results is exactly what it provides, which is why we use it rather than
inventing a private benchmark. Milestone campaigns are driven from this repository, against a pinned
RESTGym commit, so a campaign can be re-run and get the same numbers.

Two boundaries make that a measurement rather than a dependency, and both are enforced rather than
intended:

- **The harness is not part of the build.** It lives in a top-level `evaluation/` directory that is
  not a Maven module. The tool builds, ships and runs without it.
- **Nothing under `src/` references it.** No dictionaries shipped by the benchmark, no thresholds
  derived from its verification rules, no assumptions about its layout. A test fails the build if
  the platform's name appears anywhere in the source tree.

A tool that has absorbed assumptions from the benchmark it is measured on is both worse engineering
and worse science. [ADR-0011](adr/0011-evaluation-harness.md) records the reasoning and the layout.

Fault reports use the WFC codes rather than a taxonomy of our own, for the same reason: a fault
count is only meaningful next to somebody else's fault count.

## Related tools

This section maps the REST API testing landscape for readers new to the field. The tools listed
here are those most frequently compared with RESTest in academic evaluations and benchmarks. Brief
descriptions are followed by a comparison table that uses the same dimensions as the table in the
[Evaluation](#evaluation) section above.

**Glossary for this section.** *Stateless testing* — techniques applied at the level of a single
HTTP operation in isolation. *Stateful testing* — techniques that chain multiple operations to model
resource creation, retrieval, update and deletion flows. *Black-box (B)* — the tool uses only the
API specification and the API's HTTP responses; no access to the application source code is needed.
*White-box (W)* — the tool additionally instruments the application's source code or bytecode to
obtain coverage feedback and steer the search.

### EvoMaster

[EvoMaster](https://github.com/EMResearch/EvoMaster) applies evolutionary search to REST API
testing. Its MIO (Many-Independent-Objective) algorithm treats each coverage target as an independent
optimisation objective, which avoids the stalling that affects single-objective search. A white-box
mode instruments the application at the bytecode level and feeds coverage feedback directly into the
search; a black-box mode operates on the specification alone. Stateful testing works by inferring
which operations produce resources that others consume and constructing call sequences from that
dependency graph. EvoMaster adopts the WFC fault catalogue and participates in the SBFT REST League.

### RESTler

[RESTler](https://github.com/microsoft/restler-fuzzer), from Microsoft Research, introduced
coverage-guided stateful REST fuzzing. It infers *producer-consumer* relationships from the
specification — observing that `POST /orders` produces an order identifier that `DELETE /orders/{id}`
later consumes — and uses those relationships to chain operations automatically. The fuzzer maintains
a dictionary of type-appropriate values, extends it with values extracted from live API responses,
and replays sequences to surface 500 errors and resource-state inconsistencies.

### Schemathesis

[Schemathesis](https://github.com/schemathesis/schemathesis) is a Python library and CLI built on
[Hypothesis](https://hypothesis.readthedocs.io/), a property-based testing framework. It generates
inputs from the OpenAPI schema and automatically shrinks failing cases to their minimal form. It
supports OAS 2.0, 3.0.x and 3.1.x, integrates as a pytest plugin, classifies findings using WFC
fault codes, and follows OAS 3.x `links` for stateful testing.

### RestTestGen

[RestTestGen](https://github.com/SeUniVr/RestTestGen), from the University of Verona, focuses on
*nominal* and *error* flow testing. It constructs CRUD sequences (create a resource, retrieve it,
update it, delete it) and systematically mutates valid inputs to trigger error responses. It supports
IDL-based inter-parameter constraints and emits results as JUnit 5 tests.

### CATS

[CATS](https://github.com/Endava/cats) (Contract Assured Testing Suite, from Endava) offers a large
catalogue of *fuzzers*, each targeting a specific class of input anomaly: boundary values, special
characters, Unicode edge cases, oversized payloads, missing required fields, and extra unexpected
fields. It is designed for repeatable contract testing in CI pipelines rather than for finding deep
behavioural bugs.

### Dredd

[Dredd](https://github.com/apiaryio/dredd) is a JavaScript contract-testing tool. It executes the
examples embedded in an OAS document against the running API and checks that the responses match.
Stateful sequences require hand-written hook scripts (JavaScript or Python). Dredd is well suited to
regression-testing documented behaviour but is not designed to discover undocumented bugs.

### RESTest 1.x

[RESTest 1.x](https://github.com/isa-group/RESTest/tree/master), also from the ISA Research Group,
implements constraint-based testing (CBT) driven by IDL, random testing, and ART in a single
configurable pipeline. Limited stateful support is available via hand-written test flow
configurations. RESTest 2.0 is a ground-up rewrite; the reasoning is in
[ADR-0002](adr/0002-rewrite-not-refactor.md).

### Comparison

The columns *stateless techniques* and *stateful techniques* name the core algorithmic strategy, not
every configuration option. **OAS** — specification versions the tool accepts. **BB/WB** — B =
black-box only; B+W = black-box and white-box modes both available. The last row shows the design
target for RESTest 2.0.

| Tool | Language | OAS | BB/WB | Stateless techniques | Stateful techniques | Test data types | Oracle types |
|---|---|---|---|---|---|---|---|
| [EvoMaster](https://github.com/EMResearch/EvoMaster) | Kotlin/Java | 2.0, 3.0.x | B+W | Evolutionary (MIO), random | Resource-dependency sequence construction | Evolutionary, random, adaptive | 5xx detection, schema validation |
| [RESTler](https://github.com/microsoft/restler-fuzzer) | Python | 2.0, 3.0 | B | Coverage-guided fuzzing, random | Producer-consumer chains (spec-inferred) | Random + response-extracted dictionary | 5xx detection, resource-state inconsistency |
| [Schemathesis](https://github.com/schemathesis/schemathesis) | Python | 2.0, 3.0.x, 3.1.x | B | Property-based (Hypothesis), shrinking | OAS link following | Schema-driven, property-based | 5xx detection, schema validation, WFC codes |
| [RestTestGen](https://github.com/SeUniVr/RestTestGen) | Java | 2.0, 3.0 | B | Random, IDL-constrained | CRUD nominal flows, error flows | Random, example-based, IDL-constrained | Status code classification, schema validation |
| [CATS](https://github.com/Endava/cats) | Java | 2.0, 3.0.x | B | Fuzzing catalogue (BVA, special chars, Unicode, oversized, field mutation) | — | Fuzzing patterns, boundary values | Status codes, schema validation |
| [Dredd](https://github.com/apiaryio/dredd) | JavaScript | 2.0, 3.0 | B | Example-based contract testing | Scripted hooks (manual) | Spec examples | Status codes, response schema |
| [RESTest 1.x](https://github.com/isa-group/RESTest/tree/master) | Java | 2.0, 3.0 | B | CBT (IDL), random, ART | Hand-written test flows | Random, IDL-constrained, example-based | Status code classification, schema validation |
| **RESTest 2.0** (this tool) | Java | 2.0, 3.x | B | CBT (IDL), random, ART, feedback-guided | ODG-based sequence construction | Random, IDL-constrained, external providers | Status code classification, schema validation, corpus oracles, WFC codes |

## Out of scope for v2.0

Deferred until v2.0 is functional and measured. Nothing here is started without explicit approval,
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
| Predicting whether a request will be accepted before sending it | `FeedbackListener` |
| Search-based or reinforcement-learning scheduling | `FeedbackListener` |
| Surrogate coverage goals for black-box search | `FeedbackListener` |
| Security oracles (injection, server-side request forgery, authorisation bypass) | `Oracle` + WFC codes |
| Flow discovery from execution traces | `FlowSource` |

## Stack

Versions are pinned here and in the root POM; the two are expected to agree.

| Component | Choice | Version |
|---|---|---|
| Library bytecode | Java 21 — every module, CLI included ([ADR-0003](adr/0003-java-baseline.md)) | — |
| Build toolchain | JDK 25 (long-term support) | 25 |
| Build | Maven with wrapper | 3.9.16 |
| OAS parser | `io.swagger.parser.v3:swagger-parser`, behind our own interface | 2.1.47 |
| JSON Schema validation | `com.networknt:json-schema-validator` | 3.0.7 |
| Command-line framework | picocli, with its code generator for native binaries | 4.7.7 |
| HTTP client | OkHttp — network interceptors give exact request and response capture | 5.5.0 |
| Concurrency | Virtual threads | JDK 21+ |
| IDL parser | ANTLR4 | 4.13.x |
| Constraint solver | Choco, behind an interface | 4.10.x |
| Interaction store | SQLite (`org.xerial:sqlite-jdbc`) plus NDJSON | — |
| Unit tests | JUnit Jupiter | 6.1.3 |
| Assertions | AssertJ | 3.27.7 |
| Container-based integration tests | Testcontainers | 2.0.5 |
| HTTP stubbing and fault injection | WireMock | 3.13.2 |
| Mutation testing | PIT | 1.30.0 |
| Architecture tests | ArchUnit | 1.5.0 |
| Coverage | JaCoCo | 0.8.15 |
| Release automation | JReleaser | 1.26.0 |
| Maven Central publication | `central-publishing-maven-plugin` (Central Portal) | 0.11.0 |

## External references

Standards:

- OpenAPI Specification — https://spec.openapis.org/oas/
- Arazzo Specification — https://spec.openapis.org/arazzo/
- Web Fuzzing Commons, the shared fault catalogue — https://github.com/WebFuzzing/Commons

Evaluation:

- RESTGym, the benchmark infrastructure used for milestone campaigns — https://github.com/restgym/restgym

## Contributing

Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first. In short: work targets the `v2` branch, one
increment from [`ROADMAP.md`](../ROADMAP.md) per branch per pull request, English throughout, and a
design question with more than one defensible answer becomes an ADR rather than a silent choice in
the code.
