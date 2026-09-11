# RESTest 2.0 — Analysis and Implementation Proposal

**Status:** v6 — decisions settled (§14), no code written yet
**Date:** 11 September 2026
**Audience:** RESTest maintainers (ISA/SCORE Lab, Universidad de Sevilla)

**Changes since v3**, after the maintainer's review:
- New **§0 Glossary** in plain language. Every acronym used in this document — SPI, AUC, ODG, CSP,
  oracle, and the rest — is defined there, and the rule now applies to pull requests too (§12.2).
- **The request-throughput floor is removed.** It assumed we know the API's response time in advance,
  which we do not. Replaced by a *non-blocking invariant* plus an **idle-time** metric and adaptive
  concurrency (§5.1, §6.3). The CI regression test measures **our own overhead** against a local stub
  with fixed latency, which is a controlled comparison rather than an arbitrary threshold.
- **OAS scope fixed: 2.0, 3.0.x and 3.1.x, fully.** OAS 3.2 and 4.0 removed from the plan (§7.2).
- **§7.7 rewritten.** No `restest-ai` module, no `LlmProvider` interface, no AI-specific abstractions
  in v2.0. "Ready for AI" now means exactly the three things you named: extensible input/output
  formats, **external data providers**, and the ability to add new inter-parameter dependencies and
  operation flows **during** the generation-and-execution loop.
- **RESTGym coupling removed from the tool, harness kept in our repository.** No borrowed
  dictionaries, no derived thresholds, no assumptions about its layout anywhere in `src/`. The
  evaluation harness — Dockerfile, entry script, pinned RESTGym commit, launcher — lives in an
  optional top-level `evaluation/` directory that is outside the Maven build and referenced by no Java
  code. Nothing is contributed to RESTGym's repository (§8.1).
- **Pull request format rewritten** (§12.2): what you can do now that you could not before, how to
  try it yourself with copy-paste commands, and an explanation of the implementation aimed at a
  reader who does not program in Java.

---

## 0. Glossary

Terms used throughout this document and, from now on, in every pull request.

| Term | Plain-language meaning |
|---|---|
| **OAS** (OpenAPI Specification) | The standard file — YAML or JSON — that describes an API: its operations, their parameters, and the shape of their responses. Our only mandatory input. |
| **Black-box testing** | Testing an API from the outside, using only its description and its responses. We never look at the API's source code. |
| **Test oracle** | The rule that decides whether a response is right or wrong. "A valid request must not return a server error" is an oracle. Generating requests is half the problem; oracles are the other half. |
| **Stateless / stateful testing** | Stateless: each test is a single, independent request. Stateful: a test is a *sequence* — create a resource, read it, update it, delete it — where each step depends on the previous one. |
| **Inter-parameter dependency** | A rule constraining how parameters can be combined, e.g. "if `location` is given, `radius` must be given too". Not expressible in OAS, which is why IDL exists. |
| **IDL** (Inter-parameter Dependency Language) | The language, created by this group, for writing those rules down formally so a machine can reason about them. |
| **IDL4OAS** | The extension that embeds IDL rules inside an OAS document, under `x-dependencies`. |
| **CSP** (Constraint Satisfaction Problem) | The mathematical form an IDL specification is translated into, so that a *solver* can compute combinations of parameter values that satisfy every rule at once. |
| **Solver** | The component that does that computation. We use Choco, a Java constraint-programming library. |
| **SPI** (Service Provider Interface) | A deliberately small Java interface that the core of the tool defines and *somebody else* implements. The core says "give me values for this parameter" without knowing or caring who answers. Adding a new capability then means writing one small class and putting it on the classpath — no change to the core, no recompilation of anything else. This is the single mechanism that makes the tool extensible, and it is why the word appears so often below. |
| **Event stream** | An internal broadcast: the engine announces what it is doing ("request sent", "response received", "failure found") and any number of independent listeners — reports, metrics, feedback — react. Adding an eighth report format does not touch the engine. |
| **ODG** (Operation Dependency Graph) | A map, inferred automatically, of which operations produce data that other operations need. It tells the tool that `POST /pets` should run before `GET /pets/{id}`. |
| **AUC** (Area Under the Curve) | A measure of *how fast* a tool achieves something, not just how much it achieves in the end. A tool that covers 15 operations in the first minute scores far better than one that covers 15 in the last minute. RESTest's weakest number in 2026. |
| **Idle time** | Our own metric, introduced in this plan: the fraction of the test budget during which the tool had no request in flight — that is, time spent thinking instead of testing. |
| **Fuzzing** | Sending deliberately malformed or extreme inputs to see whether the API handles them correctly. |
| **WFC** (Web Fuzzing Commons) | A shared, numbered catalogue of API fault types, already used by EvoMaster and Schemathesis. Adopting it makes our results directly comparable with theirs. |
| **RESTGym** | The Docker-based infrastructure used by the SBFT competition to run testing tools against a fixed set of APIs and measure them. We drive it from our own repository for milestone campaigns; the tool itself never mentions it. |
| **ANTLR4** | A widely used library for turning a grammar (the formal definition of a language such as IDL) into a parser. |
| **ArchUnit** | A library that lets us write *tests about the structure of the code itself* — for example "no class in the core may depend on the network layer" — so architectural rules fail the build instead of eroding silently. |
| **Native image** | Compiling the tool into a standalone executable that starts in under a tenth of a second, with no Java installation required on the user's machine. |

---

## 1. Executive summary

RESTest's ideas are still ahead of the field. Its *engineering* is not. The SBFT 2026 REST League
placed RESTest 4th of 5 in fault detection, 4th in effectiveness and **last in efficiency**, and it
crashed on 2 of 11 APIs.

| | RESTest | RestTestGen (baseline) | AutoRestTest (winner) |
|---|---|---|---|
| Operations covered (final) | 10.89 | 16.33 | 17.27 |
| **Operations covered, AUC** | **8,322.85** | 56,590.65 | 60,224.11 |
| Branch coverage (final) | 18% | 23% | 21% |
| **Branch coverage, AUC** | **236.66** | 780.10 | 744.41 |
| Unique faults | 11.25 | 22.90 | 67.09 |

Read those rows together. RESTest's *final* operation coverage (10.89) beats Schemathesis (10.61) and
its branch coverage (18%) is within 5 points of the winner — but its **AUC is 6.8× below the
baseline**. RESTest arrives at a respectable place far too slowly.

Two facts from your own competition paper reframe this. First, because the APIs were not known in
advance, RESTest ran **a purely random strategy with constraint-based testing switched off** — its
principal differentiator did not compete, because IDL has to be written by hand. Second, the entry
added an experimental data generator: a LangGraph multi-agent system over a locally deployed Llama
3.2 3B. On 8 cores and 16 GB with no GPU, that generator consumed a large share of the one-hour
budget while no requests were being sent. The generate → write Java source → in-process `javac` →
JUnit 4 → REST-Assured pipeline sits on top of both.

So the 2026 table is not an indictment of RESTest's ideas. It indicts three engineering properties:
(a) the good strategy needs hand-written configuration, so it cannot be deployed on an unknown API;
(b) expensive computation happens *instead of* testing rather than *alongside* it; (c) the execution
pipeline is slow. The design target for v2.0 follows directly: **an unknown API, no human
configuration, a fixed time budget.**

The proposal:

1. **A new, clean multi-module Java codebase** with an interpreted test model (no code generation in
   the hot path), an event stream, a durable interaction store, and narrow SPIs (§0) everywhere.
2. **Zero-configuration first run**: `restest run openapi.yaml --url http://localhost:8080`.
3. **Never crash on a bad spec** — skip the offending operation and report it. Two of eleven APIs
   scored zero in 2026 for this reason alone.
4. **Keep IDL as the differentiator**, reusing the existing (relicensed) IDL assets rather than
   reinventing them, with the solver behind an SPI.
5. **No RESTGym inside the tool; the evaluation harness inside the repository.** The published tool
   runs from a specification and a base URL and never mentions the platform. The harness that drives
   a RESTGym campaign is an optional `evaluation/` directory, outside the build (§8.1).
6. **Research novelty is explicitly deferred** (§11). What v2.0 must deliver is the extension points
   (§5) that make those additions cheap later.
7. **No AI abstractions in v2.0.** "Ready for AI" means open input/output formats, external data
   providers, and a model that can be updated mid-run (§7.7) — all of which are useful without any
   model involved.

Estimated effort: **41 reviewable increments across 9 milestones**. A walking skeleton beating
RESTest 1.x on a real API is achievable in the first ~9 increments.

---

## 2. Evidence base

**Papers (provided)**
- Martin-Lopez, Segura, Ruiz-Cortés. *RESTest: Black-Box Constraint-Based Testing of RESTful Web
  APIs.* ICSOC 2020.
- Martin-Lopez, Segura, Ruiz-Cortés. *Online Testing of RESTful APIs: Promises and Challenges.*
  ESEC/FSE 2022.
- Barakat, Martin-Lopez, Müller, Segura, Ruiz-Cortés. *The IDL tool suite.* SoftwareX 29 (2025) 101998.
- Mimbrero, Alonso Valenzuela, Martin-Lopez, Segura. *RESTest at the REST League 2026 Tool
  Competition.* SBFT '26.

**Competition**
- Pasqua, Corradini, Mari, Ceccato. *SBFT Tool Competition 2026 — REST League.* SBFT '26, Rio de
  Janeiro. 11 APIs / 317 operations, 1 h budget, 10 repetitions, 8 cores + 16 GB per tool.

**Also consulted**
- Kim, Sinha, Orso. *LlamaRestTest: Effective REST API Testing with Small Language Models.*
  PACMSE / FSE 2025 — the closest published evidence that model-generated input values are worth the
  engineering investment (+9.2–18.7 branch-coverage points over EvoMaster, ARAT-RL, RESTler and
  MoRest; 204 unique 500s over 10 runs vs 130–160).
- Zhang & Arcuri. *Open Problems in Fuzzing RESTful APIs.* TOSEM 2023.

**Code read directly**
- `isa-group/RESTest` @ `cab2edd` (6 Dec 2025) — full audit, 123 main classes, ~18k LOC.
- `webfuzzing/evomaster`, `schemathesis/schemathesis` @ 4.26.1, `SeUniVr/RestTestGen` @ 25.12,
  `Endava/cats`, `selab-gatech/AutoRestTest`, `restgym/restgym` @ v2.0.1.

**Technical baseline** verified against upstream release pages as of 11 Sep 2026. Appendix A.

---

## 3. Where RESTest stands today

### 3.1 The three structural problems (from the code audit)

**(a) Configuration cost is the adoption killer.** Two files per API in two formats. Bikewise:
4 operations → a 421-line `fullConf.yaml` (≈105 lines *per operation*) plus a ~60-line `.properties`
file. Ice & Fire: a 1,032-line OAS → 380-line test config + 82-line properties. The auto-generator
exists but its own docstring says the output "should be manually updated before testing"; defaults
are crude (every unformatted string becomes `RandomEnglishWord{maxWords:1}`, every number becomes
1..100, `binary` becomes the literal string `"path/to/file"`, authentication is never inferred from
`securitySchemes`). In the FSE'22 online-testing study you wrote **26k lines of test configuration**
for 75 bots, ~95% of it copy-pasted. That number is the strongest possible argument for v2.

**(b) Code generation in the hot path.** `RESTAssuredWriter` builds JUnit 4 source by
`content += "..."` (618 lines of string concatenation), then `util/ClassLoader` compiles it
in-process with `ToolProvider.getSystemJavaCompiler()` **and ignores the boolean returned by
`getTask(...).call()`**, so compilation errors are silent. A committed example output is 3,348 lines
for a single API. This requires a full JDK at runtime, writes `.class` files next to sources, and
freezes the oracle set at generation time.

**(c) Static global state and hardcoded paths.** `PropertyManager` is entirely static;
`RESTestLoader.userPropertiesFilePath` is `static` but assigned from an instance constructor;
`OASAPIValidator` is a broken singleton whose cache check relies on an `equals` that is never
overridden; `FuzzingDictionary` reads `new File("src/main/resources/fuzzing-dictionary.json")` in a
static initialiser. 19 hardcoded `"src/..."` paths in `src/main/java`. Consequence: the jar is not
relocatable, two configurations cannot coexist in one JVM, and RESTest cannot be embedded as a
library — which is precisely requirement #5.

**Secondary but load-bearing:**
- **OAS 3.0 only.** `swagger-parser` arrives *undeclared*, transitively, at 2.0.25/2.0.27 — the
  effective version depends on Maven nearest-wins. `SchemaManager.java:51`: *"No support for anyOf,
  oneOf."* `BodyGenerator` throws a null-pointer exception on `allOf` nodes. `setResolveFully(true)`
  inlines every reference, which explodes on large specifications and breaks on recursive ones.
- **No real stateful model.** `StatefulFilter` flattens successful response bodies into a
  `stateful_data.json` file; `DataMatching` re-reads it and matches by name heuristics with Stanford
  CoreNLP lemmatisation. Ordering comes from `@FixMethodOrder(NAME_ASCENDING)` over a *random* test-id
  prefix — i.e. effectively arbitrary. No resource lifecycle, no teardown. `OPERATIONS_FLOW` coverage
  is commented out.
- **Dependency rot.** REST-Assured 4.2.0 (Nov 2019), Jackson 2.11.4, JUnit 4 at compile scope, a
  **19 MB Allure distribution committed into the repository**, `apache-jena-libs` and
  `stanford-parser` declared but unused, publication configured against OSSRH which was **shut down
  on 30 June 2025**. Last artifact on Maven Central: 1.4.0, Nov 2023. No GitHub Actions; all README
  badges commented out. 531 MB working tree. Bus factor 1–2 since the principal author left in 2022.
- **IDL integration cost.** `IDLReasoner-choco` pulls Choco 4.10.6 **plus Eclipse Xtext 2.22.0**,
  Guice, Guava, swagger-parser 2.0.27 and log4j-core 2.13.3. A broken IDL specification only logs a
  warning and silently degrades constraint-based testing to unconstrained random.

### 3.2 What the 2026 result actually says

Four causes, in order of impact:

1. **The best strategy could not be deployed.** Constraint-based testing needs IDL dependencies, and
   IDL must be written by hand. With the API set unknown in advance, the entry fell back to purely
   random generation. RESTest competed without the thing that makes RESTest RESTest. *This is the
   configuration-cost problem showing up as a score.*
2. **Expensive computation replaced testing instead of accompanying it.** The multi-agent generator
   over a local Llama 3.2 3B produced good values, but on 8 cores / 16 GB with no GPU it consumed
   wall-clock during which nothing was being sent. The strategy is right; its placement inside the
   measured window was wrong.
3. **Robustness.** RESTest crashed on `kafka-rest-proxy` and `flight-search` ("unsupported parameter
   errors"). Two of eleven APIs scored zero. These are the cheapest points on the table.
4. **Throughput of the execution pipeline.** Generate → emit Java source → compile in-process → JUnit
   → REST-Assured. Even with the model pass removed, this pipeline spends most of its time not
   talking to the API.

Corroborating evidence that this is an engineering story rather than an algorithm-quality story:
RESTest's *final* operation and branch coverage are mid-field while its AUC is last by a wide margin.
The algorithms arrive; they arrive late, and the best one never left the bench.

---

## 4. What the field looks like in 2026

| | EvoMaster (black-box) | Schemathesis | RestTestGen | CATS | AutoRestTest |
|---|---|---|---|---|---|
| Language | Kotlin/Java | Python | Java 17 | Java 25 | Python |
| Licence | **LGPL-3.0** | MIT | Apache-2.0 | Apache-2.0 | — |
| Minimal command | `--schema <url>` | `st run <url>` (zero flags) | 3 config files | `--contract ... --server ...` | config + API key |
| Install | pip, Docker, jpackage | pip/uv, Docker, **GitHub Action** | **build from source** | **brew**, native binary | poetry |
| OAS | 2.0/3.0 (3.1 via parser) | **2.0/3.0/3.1** | 3.0 only, hand-written parser | 2.0/3.x | 3.0 only |
| Oracles | **~35, incl. security and HTTP semantics** | 14, **configurable per operation** | 5 | status classes + 41 linters | server errors only |
| Stateful | resource sampling, declared links, data pool | link state machine + resource pool | **Operation Dependency Graph** | none | semantic dependency graph |
| Extensibility | fork it | **hooks + custom checks** | **interface + class name in YAML** | **annotation-based discovery** | — |

### Ten things worth taking

1. **Time-to-first-test under 30 seconds** (Schemathesis). Zero required flags, and a copy-pasteable
   `curl` command printed for every failure so anyone can reproduce it by hand.
2. **A specification-agnostic engine plus one event stream** (Schemathesis). Every report format is an
   independent listener; adding one never touches the engine.
3. **Four-phase generation**: declared examples → a deterministic walk of every constraint boundary →
   random fuzzing → stateful sequences. The deterministic phase is cheap, reproducible, and finds
   boundary bugs that random search misses.
4. **The WFC fault catalogue** — numbered fault types already used by EvoMaster and Schemathesis.
   Adopting it gives instant comparability and a ready-made oracle backlog.
5. **Surrogate goals for black-box search** (EvoMaster): one goal per followed link, per
   query-parameter presence combination, per distinct value per parameter, per status class per
   endpoint. A sense of progress without looking at the API's code.
6. **Operation Dependency Graph** (RestTestGen) combined with a **runtime resource pool**
   (Schemathesis). Declared links are rare in the wild; inference must be the default.
7. **"Mutate only what already worked"** (RestTestGen): run nominal tests first, keep the successful
   ones, then corrupt *those*.
8. **Annotation-based oracle discovery** (CATS): a one-method interface plus a marker, 215
   implementations, a new one is ~30 lines. A large catalogue without a large codebase.
9. **Per-operation oracle configuration with overridable expected statuses** (Schemathesis).
   Unfixable false positives are the documented reason teams switch fuzzers off.
10. **Value dictionaries computed once and cached** (AutoRestTest). Whatever produces the values, the
    result is a reusable artifact rather than a cost paid on every run.

### Six mistakes to avoid

- 330 command-line options (EvoMaster). Three tiers of documentation does not fix it.
- "Run it for 1–24 hours." The tool must be useful at 60 seconds.
- LGPL — blocks embedding in commercial pipelines.
- A hand-rolled parser plus eager reference expansion (RestTestGen), and *crashing* on a malformed
  operation instead of skipping it.
- A well-designed framework nobody can install (RestTestGen).
- Spending the budget on thinking instead of testing (RESTest 2026).

---

## 5. Extension points that must exist in v2.0

These are architectural requirements, not features. Each is verified during its milestone by writing
a throwaway implementation, demonstrating it, and then deleting it — so we know the seam is real.

### 5.1 The engine never blocks, and we measure it honestly

Your objection to a fixed requests-per-minute floor is right: throughput depends on the API's own
response time, which we do not know in advance. A hard threshold would be arbitrary and would punish
us on slow APIs for something that is not our fault. What we actually want to guarantee is narrower
and fully under our control:

- **Invariant — the request loop never waits on computation.** Anything that can be slow (an external
  data provider, a solver call, a large schema analysis) runs on its own thread and publishes results
  when ready. The generator always has something valid to send in the meantime, and improved values
  are swapped in as they arrive. This is a structural property, checked by an architecture test, not
  a tuning parameter.
- **Metric — idle time.** Every run reports the fraction of the budget during which no request was in
  flight. On a slow API, throughput is low and idle time is still near zero: the tool is waiting for
  the API, which is correct. On a tool with RESTest 1.x's problem, idle time is high regardless of the
  API. This separates *our* inefficiency from *theirs*, which a throughput floor cannot do.
- **Adaptive concurrency.** Observed response times drive the number of in-flight requests, within a
  bounded range, so a slow API is absorbed by concurrency rather than by waiting. The user can cap it
  when testing something fragile.
- **CI regression test on overhead, not on speed.** A fixed specification against a local stub with
  constant, known latency. That measures the tool's own overhead per request and fails if we make it
  worse. It says nothing about real APIs, and does not pretend to.

### 5.2 External data providers

*What this is for:* letting something outside the tool supply input values — a curated dictionary
committed alongside the specification, a corporate test-data service, a colleague's script, or, later,
a model. The tool must not care which.

- **`ExternalDataProvider` SPI:** given an operation and its parameter schemas, return candidate
  values with their provenance. Nothing in the interface mentions models or intelligence.
- **A value-provider chain with explicit priority:** `fixed → declared example → overlay →
  dictionary → external provider → producer response (resource pool) → format/pattern generator →
  random`. Adding a source is inserting one link.
- **Our own portable dictionary format** — one JSON file, keyed by operation and parameter path, with
  provenance and the specification hash it was derived from. Generated by whatever the user likes,
  committed next to the specification, reusable across runs. No external tool's format is adopted.
- **An out-of-process transport.** A provider may be a separate process or an HTTP endpoint, so an
  implementation written in Python — your existing LangGraph prototype, for instance — plugs in as a
  sidecar without being rewritten in Java.
- **Asynchronous by default, with a disk cache** keyed on the specification hash and the provider's
  identity, so repeated runs and repeated experiment repetitions pay nothing.

### 5.3 Live model updates during the generation-and-execution loop

*What this is for:* the capability you asked for directly — adding new inter-parameter dependencies
and new operation flows **while the loop is running**. v2.0 ships the mechanism; what is deferred is
the *inference* that would decide what to add.

- **The constraint model is mutable and per-operation**, not parsed once and frozen. A
  **`ConstraintSource`** can contribute IDL dependencies at any moment; the solver re-solves and
  subsequent test cases respect them immediately.
- **The flow model is mutable too.** A **`FlowSource`** can contribute new operation sequences
  mid-run, and the scheduler picks them up on the next planning cycle.
- **Every constraint and flow carries provenance and confidence**, so anything added later can be
  distinguished from what the specification declared, weighted differently, and audited.
- **v2.0 ships two trivial sources**: the specification itself, and a watched file — drop an IDL
  snippet into a directory during a run and watch the generated requests change. That file-watch
  implementation *is* the demonstration that the seam works, and it is genuinely useful to a human
  tester who spots a dependency mid-run.
- **`restest export overlay`** writes the current constraint set back out as an IDL4OAS document.

### 5.4 Oracles that can see more than one interaction

*What this is for:* the semantic oracles you want later — invariants and relations inferred from sets
of requests and responses, or from the specification.

- **Two oracle kinds.** `Oracle` examines a single interaction. **`CorpusOracle` examines a queryable
  set** — the whole run, one operation's slice, or a sliding window. Almost every semantic-oracle
  technique needs the second, and retrofitting it later would mean rewriting the oracle engine.
- **A durable interaction store.** Every request and response is persisted with exact bytes, timings,
  the test case that produced it, and the provenance of each parameter value. SQLite by default.
- **`restest recheck <run>`** re-evaluates a stored run against a different oracle set, offline, with
  no API calls. This is the workbench a semantic-oracle researcher actually needs: try a new oracle
  against a corpus you already have, in seconds, reproducibly, on your laptop.
- **Oracles declare what they consume** (one interaction / one operation / the whole corpus /
  specification only), so the engine schedules them correctly and `restest explain` can describe them.

### 5.5 Feedback

The event stream is the substrate. A **`FeedbackListener`** observes every interaction and may return
scheduling hints — operation weights, parameter weights, budget shifts. v2.0 ships one trivial
implementation: deprioritise operations that have returned only client errors for N consecutive
attempts. Everything in §11 that needs feedback uses this seam.

---

## 6. Proposed architecture

### 6.1 Modules

```
restest-core        Domain model and SPIs. No network, no heavy dependencies.
                    ApiModel, Operation, Parameter, CanonicalSchema, TestCase,
                    TestSequence, Interaction, Budget, Event.
                    SPIs: SpecificationParser, ValueProvider, ExternalDataProvider,
                    TestCaseGenerator, Oracle, CorpusOracle, ConstraintSource,
                    FlowSource, ConstraintSolver, Reporter, HttpEngine,
                    FeedbackListener.

restest-spec        OAS 2.0 / 3.0.x / 3.1.x adapter behind SpecificationParser.
                    Lazy reference resolution; oneOf/anyOf/allOf folded into a
                    canonical schema model; per-operation diagnostics instead of
                    exceptions.

restest-idl         IDL language and IDL4OAS reader/writer; ConstraintSolver SPI
                    with a Choco implementation; the analysis operations
                    (isValidRequest, randomValid, randomInvalid, deadParameter,
                    falseOptional, consistency). Mutable constraint set with
                    provenance.

restest-gen         Generation phases and the scheduler: examples -> boundary walk
                    -> constraint-based -> random/fuzzing -> stateful sequences.
                    Value provider chain, dictionary format, Operation Dependency
                    Graph, runtime resource pool.

restest-exec        HTTP engine, virtual threads, exact wire capture, authentication,
                    adaptive concurrency, idle-time accounting, replay.

restest-store       Durable interaction store (SQLite / NDJSON) and query API.
                    Enables `restest recheck` and every corpus-level oracle.

restest-oracles     Oracle and CorpusOracle catalogue with WFC fault codes,
                    per-operation configuration, failure deduplication.

restest-report      Event listeners: console, JSON/NDJSON, JUnit XML, HTML, HAR,
                    curl reproduction, IDL4OAS overlay, JUnit 5 + REST-Assured
                    code export, coverage criteria.

restest-cli         Command line: run, lint, replay, recheck, report, explain, export.
```

That is the complete list of modules. The repository also holds an `evaluation/` directory that is
deliberately *not* a module — not in the build, not published, referenced by no Java source (§8.1).

### 6.2 Execution pipeline

```
OAS ──► SpecificationParser ──► ApiModel (canonical)
                                   │
                                   ├─► static analysis: ODG, IDL4OAS constraints,
                                   │   security schemes, declared examples
                                   ▼
                              Scheduler  ◄──── FeedbackListener hints
                                   │      ◄──── ConstraintSource / FlowSource
                                   │             (may fire at any moment)
        ┌──────────┬───────────────┼───────────────┬──────────────┐
        ▼          ▼               ▼               ▼              ▼
    Examples   Boundary        Constraint       Random        Stateful
     phase     walk            (IDL/CSP)        fuzzing       sequences
        └──────────┴───────────────┴───────────────┴──────────────┘
                                   │   TestCase / TestSequence (data, not code)
                                   ▼
                    HttpEngine — adaptive concurrency, never blocked
                                   │   Interaction (exact bytes captured)
                    ┌──────────────┼───────────────────┐
                    ▼              ▼                   ▼
             Interaction      Event stream        Resource pool
                store              │
                    │        ┌─────┴──────┬───────────────┐
                    │        ▼            ▼               ▼
                    │    Oracles      Reporters    FeedbackListener
                    │        │
                    └───► CorpusOracles (windowed, or at end of run)
                             │
                          Fault (WFC code) + deduplication bucket

     (asynchronous, never in the way)
     ExternalDataProvider ──► dictionary entries ──► value provider chain
```

### 6.3 Key decisions

1. **Interpret, don't generate.** Test cases are data executed by an HTTP client. Emitting JUnit,
   REST-Assured, curl or overlay documents is a *report*, not the execution path.
2. **One event stream, many listeners.** Reports, metrics, feedback and oracles all subscribe.
3. **Persist everything.** The interaction store is what makes `recheck`, corpus oracles and honest
   post-hoc analysis possible, and it costs almost nothing.
4. **No global mutable state.** Two runs coexist in one JVM. Enforced by an architecture test.
5. **The loop is never blocked; idle time is reported.** Replaces the throughput floor (§5.1).
6. **Narrow SPIs, service discovery, module boundaries.** A new oracle or provider is one class.
7. **The tool is standalone.** A specification and a base URL are all it ever requires. Evaluation
   harnesses live in `evaluation/`, outside the build, optional, and invisible to the Java code. §8.1.

### 6.4 What carries over from RESTest 1.x

The component taxonomy in your competition paper — test data generators, test case generators, test
writers, test reporters, test runners, plus feedback collection — is sound and survives almost
unchanged. The SPI set above *is* that taxonomy, made narrower, with three additions 1.x lacks: a
`SpecificationParser` boundary (so the parser is replaceable and the canonical model is ours), a
`CorpusOracle` kind, and constraint and flow sources with provenance.

What changes is not the taxonomy but four implementation choices: test writers move off the execution
path and become reports; generators produce data rather than Java source; configuration stops being
mandatory; and feedback becomes a real event stream rather than a post-hoc CSV read. The catalogue of
15+ test data generators, the `ObjectPerturbator`, the mutator that turns valid test cases into faulty
ones, and the IDL assets are all worth porting — as implementations behind the new interfaces, not as
code moved wholesale.

---

## 7. Review of your stated requirements

| # | Requirement | Verdict | Note |
|---|---|---|---|
| 1 | Java, newest JDK | **Adjusted** | §7.1 |
| 2 | Support recent OAS versions | **Scoped: 2.0, 3.0.x, 3.1.x** | §7.2 |
| 3 | Black-box only | **Agreed** | §7.3 |
| 4 | Simplest possible CLI; the OAS is enough | **Agreed, strongly** | Highest-value requirement |
| 5 | Installable as a Maven/Gradle dependency | **Agreed, and more** | §7.4 |
| 6 | Minimise configuration options | **Agreed, one exception** | §7.5 |
| 7 | Stateless and stateful testing | **Agreed** | §7.6 |
| 8 | Ready to integrate AI, but not AI-dependent | **Agreed, reinterpreted** | §7.7 |
| 9 | Fundamental design principles | **Agreed, plus mechanical enforcement** | Architecture tests, mutation testing |
| 10 | Code and documentation in English | **Agreed** | Including commits, issues and design records |

### 7.1 Java version — build on JDK 25, compile everything for Java 21

Latest JDK is 26; latest long-term-support release is 25. Everything this tool needs — records, sealed
interfaces, pattern matching, virtual threads — is final in 21. Targeting 25 buys nothing semantically
and excludes the large installed base still on 21, which matters precisely because requirement #5 asks
for a consumable dependency.

**Every module targets Java 21, the command-line module included; the build toolchain is 25; CI
tests 21, 25 and 26.** The CLI was originally to target 25. ADR-0003 was amended at M0.2: a JDK 21
CI row cannot compile a module targeting 25, and each way around that hid something — either one
matrix row building a different subset, or a toolchain that `surefire` would honour too, quietly
running every row's tests on 25.

Avoid Structured Concurrency and Lazy Constants in any published interface — both are still preview
features and their APIs have changed between releases.

### 7.2 OAS versions — 2.0, 3.0.x and 3.1.x, fully

Scope fixed. Swagger 2.0 is supported by conversion; 3.0.x and 3.1.x are first-class. OAS 3.2 and
4.0 are out of scope and out of the plan.

The 3.0 → 3.1 differences that matter to a *generator* are traps worth naming, because getting them
wrong is silent: `nullable: true` becomes `type: ["string","null"]`; `exclusiveMinimum` and
`exclusiveMaximum` change from true/false flags to actual numbers (get this wrong and every boundary
test is quietly incorrect); inside a schema, `examples` is a plain list, while for a parameter or a
media type it is a named map — same word, two shapes; and `paths` becomes optional, so a valid
document may declare no operations at all.

The parser stays behind the `SpecificationParser` interface anyway, because it is the only way to own
our canonical model rather than inheriting a third-party library's — not because of any future version.

### 7.3 Black-box only — agreed, with one clarification

Agreed. Note that the benchmark platform reports source-code coverage of the API under test, but that
instrumentation lives in the API's container, not in our tool. Using those numbers as a success metric
does not make the tool white-box. Worth stating explicitly in any paper, since it is a predictable
reviewer question.

### 7.4 Distribution — a Maven dependency is necessary but not sufficient

RESTest's publication configuration still points at OSSRH, shut down on 30 June 2025. v2 publishes
through the Central Portal. Beyond that, packaging *is* adoption: RestTestGen has the best framework
design of the five tools surveyed and near-zero users, because you have to build it yourself. From one
release configuration, ship Maven Central, Homebrew, SDKMAN, Docker, jbang, a native binary, a plain
jar fallback, and a GitHub Action — which only Schemathesis currently has.

### 7.5 Minimise configuration — with one deliberate exception

The one place to *add* expressiveness is **per-operation oracle configuration** — for example,
telling the tool that a particular API answers invalid input with 422 rather than 400. Both
Schemathesis and EvoMaster had to add exactly this, because unfixable false positives are why teams
disable testing tools. Keep it optional, keep it in one file, and publish a schema for that file so
editors can autocomplete and validate it.

Also worth adopting: **OpenAPI Overlays**, so users can inject test fixtures and example values
without editing their production specification. It is the standards-based answer to the question
RESTest 1.x answered with a 400-line configuration file.

### 7.6 Stateless and stateful

Internal model: an Operation Dependency Graph inferred from parameter names, types and schemas; a
runtime resource pool fed by successful responses; declared links when the specification has them; and
a create-read-update-delete lifecycle model that enables the stateful oracles (a deleted resource must
not still be readable; a failed update must not change anything; repeating the same update must give
the same result).

**Arazzo**, the OAI workflow specification, is a candidate *interchange format* for discovered flows —
accept hand-written scenarios, emit discovered ones. There is no Java library for it, so it is real
work; it is scheduled last in its milestone and is droppable.

### 7.7 Ready for AI — reinterpreted per your clarification

v2.0 declares **no AI module, no `LlmProvider`, and no AI-specific abstraction of any kind.** What it
provides instead are three capabilities, each independently useful with no model involved:

1. **Open input and output formats.** Everything the tool consumes or produces is a documented file
   anyone can generate or read: the OAS itself, OpenAPI Overlays, IDL4OAS documents, our value
   dictionary format, the interaction store, and the run report. A future component — a model, a
   script, a colleague — participates by reading and writing those files. Nothing needs to know how
   they were produced.
2. **External data providers** (§5.2). A generic interface for "something outside this tool suggests
   input values", with an out-of-process transport so the implementation can be in any language. Your
   LangGraph prototype fits here unchanged, and so does a hand-written dictionary.
3. **Live model updates during the loop** (§5.3). New inter-parameter dependencies and new operation
   flows can be contributed while generation and execution are running, with provenance, and take
   effect on the next test case.

The consequence is that when you do want to plug a model in, the work is writing the component that
produces IDL snippets or dictionary entries — not modifying RESTest. And the same seams serve a
corporate test-data service, a curated dictionary, or a human watching the run. This is a better
position than an "AI module", which would tie the architecture to today's assumptions about what
models are and how they are called.

### 7.8 IDL

With relicensing available, reinventing the grammar is unnecessary. The plan:

1. **Relicense `isa-group/IDL` and `IDLReasoner-choco` to Apache-2.0**, adding the LICENSE file the
   latter currently lacks. Administrative lead time — start now, it blocks milestone M5.
2. **Reuse the IDL-to-CSP mapping and the analysis operations** from IDLReasoner. That is the real
   intellectual asset and it is well tested.
3. **Keep the `.xtext` grammar as the normative definition of the language, but port the runtime
   parser to ANTLR4**, with a *differential conformance test*: run the whole existing IDL corpus
   through both the old and the new parser and require identical results. This makes the port
   demonstrably behaviour-preserving. The reason is not licensing but weight: Xtext 2.22 drags in the
   Eclipse modelling framework, Guice and Guava, pins old versions of other libraries, and prevents
   compiling to a native binary.
4. **The solver sits behind an interface**, with Choco as the default implementation. One item to
   confirm before M5: Choco's exact licence terms.
5. **Constraints become a mutable set with provenance** (§5.3).

### 7.9 Licence and name

**Licence: Apache-2.0** across v2 and the IDL assets — matching CATS and RestTestGen, and removing a
real redistribution hazard (IDLReasoner-choco ships no licence file; the older MiniZinc IDLReasoner is
GPL-3.0). Requires agreement from the 1.x copyright holders; carrying over no 1.x source keeps that
conversation narrow.

**Name: keep "RESTest".** It carries the citations and the competition already names it. Change the
Maven coordinates and state plainly in the README that this is a rewrite, not an upgrade path.

---

## 8. Evaluation, benchmarking and quality

### 8.1 RESTGym evaluation, configured from our own repository

The distinction that matters is between the **tool** and the **repository**.

- **The tool** — every module in `src/`, everything published to Maven Central, the command-line
  binary — contains no reference to RESTGym of any kind. It takes a specification and a base URL.
  Someone who installs RESTest never encounters the word.
- **The repository** also contains an evaluation harness, because that is where the people who
  evaluate RESTest work, and because we are not touching RESTGym's repository.

So there is a top-level directory, deliberately outside the Maven build:

```
evaluation/
  restgym/
    Dockerfile                 FROM a JRE image, copies the RESTest binary
    entrypoint.sh              ~15 lines: reads $API, $HOST, $PORT, $TIME_BUDGET,
                               builds the restest command, loops until stopped
    restgym-tool-config.yml    enabled: true
    restgym.lock               the RESTGym commit these files were validated against
    README.md                  how to run an evaluation, start to finish
  run-evaluation.sh            clones or updates RESTGym at the pinned commit into a
                               scratch directory, copies evaluation/restgym/ into its
                               tools/restest/, selects APIs, budget and repetitions,
                               launches, and collects the results back here
  results/                     committed scoreboards (small CSVs and charts), git-ignored
                               raw output
```

`run-evaluation.sh` works against a **scratch clone** of RESTGym, so nothing is ever committed or
pushed to that repository — we only populate a local working copy. The clone is pinned to a specific
commit recorded in `restgym.lock`, which is what makes an evaluation reproducible months later and
what protects us from upstream changes landing in the middle of a campaign.

**Four properties keep this from becoming coupling:**

1. **It is not part of the build.** `evaluation/` is not a Maven module, is not in the reactor, is not
   published, and no Java source references it. Deleting the directory leaves a fully working tool.
2. **All platform-specific knowledge is in `entrypoint.sh`** — that the specification is mounted at a
   particular path, that host and port arrive as environment variables, that the process must not exit
   before the harness stops it. None of that reaches the Java side.
3. **What the tool exposes for it is generic.** The harness needs `--url` to point at a deployed
   instance, `--budget` to stop after a given time, documented output formats, and a shell `while`
   loop supplied by the caller. Every one of those exists because users want it, not because a
   benchmark does.
4. **It is optional and configurable.** Using RESTest requires nothing from `evaluation/`. Running an
   evaluation is one script with arguments for which APIs, what budget and how many repetitions.

A check at every supervision point keeps property (2) honest: search `src/` for the platform's name
and for anything resembling its conventions, and require zero hits.

If we later want the files upstream — so that RESTGym's own campaigns include RESTest without us
running them — the same three files are contributed to their repository unchanged. That door stays
open; we are just not walking through it now.

### 8.2 Three measurement tiers

| Tier | When | What | How |
|---|---|---|---|
| Smoke | every pull request | 2 containerised open-source APIs, short budget, 1 repetition | Ordinary integration test using Testcontainers — fast, no external infrastructure |
| Nightly | every night | 5 containerised APIs, 10 min, 3 repetitions, scoreboard vs. the previous night | The same integration tests, longer budget, scheduled job |
| Campaign | end of each milestone, and for any paper | The full published API set, 60 min, 10 repetitions | `./evaluation/run-evaluation.sh`, i.e. RESTGym, for numbers comparable with the published ones |

The first two tiers stay lightweight on purpose: they must give an answer in minutes on every pull
request, and running a full Docker-in-Docker harness for that would make the feedback loop too slow
to be used. The campaign tier is where RESTGym earns its place, because comparability with the other
tools is exactly what it provides.

The split is a default, not a constraint — `run-evaluation.sh` takes any subset of APIs and any
budget, so promoting a weekly RESTGym run on two or three APIs is a one-line change to a scheduled
job if you want an earlier warning signal.

Baselines plotted from day one: RESTest 1.x, and the published 2026 figures for RestTestGen,
Schemathesis, EvoMaster, CATS and AutoRestTest.

### 8.3 Quality gates in continuous integration

All mechanical, all blocking:

- Unit tests with coverage thresholds on the core and the oracles.
- **Architecture tests**: no global mutable state; nothing outside the specification module touches
  the third-party parser; nothing blocks the request loop; no process termination outside the
  command-line module.
- **Mutation testing** on the oracle and constraint packages — deliberately corrupt our own code and
  check the tests notice. "Our tool finds bugs" is far more credible when our own oracle logic has a
  measured score, and nobody in this research area does this.
- **Integration tests** against containerised open-source APIs.
- **Stub-based tests** for the HTTP layer and for fault injection (malformed bodies, delays, errors).
- **A golden corpus of specifications** — the largest and ugliest real ones, recursive references,
  composition chains, OAS 3.1 type arrays, and the two APIs that crashed RESTest in 2026 — asserting
  "parses without throwing, and reports exactly N skipped operations".
- **Per-request overhead regression test** against a local stub with fixed latency (§5.1).
- **Native binary smoke test that actually runs the binary**, because a native build that succeeds and
  then dies on first use is the classic failure mode.

---

## 9. Incremental roadmap (v2.0)

41 increments across 9 milestones. Each increment is **one pull request** into the `v2` branch, with
tests green and the evidence described in §12.2. Nothing here is a research contribution; everything
in §11 is.

### M0 — Foundations (3)

| # | Increment | What it enables |
|---|---|---|
| 0.1 | Multi-module skeleton, Java 21 target, licence, ownership, design-record directory | The project builds |
| 0.2 | Continuous integration: 3 operating systems × 3 Java versions, coverage, architecture-test harness, dependency updates | Every later change is checked automatically |
| 0.3 | Design records 001–010 (parser, HTTP client, interpreted test model, event stream, interaction store, SPI set, IDL strategy, licence, Java baseline, no-AI-abstractions) | **Supervision point** — the decisions written down for review |

### M1 — Walking skeleton (8) — *goal: beat RESTest 1.x on a real API*

| # | Increment | What it enables |
|---|---|---|
| 1.1 | Canonical API and schema model | — |
| 1.2 | Specification parser for 2.0 / 3.0.x / 3.1.x, lazy references, skip-and-report on bad operations | Point it at any real specification without it crashing |
| 1.3 | HTTP engine: virtual threads, exact wire capture, adaptive concurrency, idle-time accounting | Requests actually get sent, fast, and we can see where time goes |
| 1.4 | Interaction store and query API | Every run is inspectable afterwards |
| 1.5 | Value provider chain and random providers; random test-case generator | The tool invents inputs on its own |
| 1.6 | Server-error and response-schema oracles, WFC codes, event stream, console and JSON reports | The tool reports real failures, with a `curl` command to reproduce each one |
| 1.7 | `restest run <spec> --url <base>` and `--budget`; the smoke integration test against two containerised APIs | Run the tool end to end from one command; regressions caught on every pull request |
| 1.8 | `evaluation/` harness: Dockerfile, entry script, pinned RESTGym commit, launcher | **Supervision point** — first campaign-comparable numbers: v2 vs RESTest 1.x and the published 2026 field, on the same APIs and budget |

### M2 — Specification fidelity and input generation (6)

Composition keywords (`oneOf`, `anyOf`, `allOf`, discriminators) → declared examples, in both the 3.0
and 3.1 shapes → the deterministic boundary walk → format-aware and pattern-based generators → request
bodies, form encodings, file uploads, XML → authentication inferred from the specification.

*What it enables:* the tool produces inputs an API will actually accept, and systematically probes
every documented limit, without anyone configuring anything.

### M3 — Oracles, faults and reporting (6)

WFC catalogue, first tranche → HTTP-semantics and REST-design oracles → **`CorpusOracle` interface and
`restest recheck`** → per-operation oracle configuration with a published schema → failure
deduplication → HTML, JUnit XML, HAR and NDJSON reports, JUnit 5 + REST-Assured code export,
`restest explain`, `restest replay`.

*What it enables:* far more kinds of bug are detected; false positives can be silenced per operation;
a finished run can be re-examined with new oracles without touching the API again.

### M4 — Stateful testing (6)

Operation Dependency Graph → runtime resource pool and value-source selection → declared links →
lifecycle model and sequence generation → stateful oracles → Arazzo import/export *(droppable)*.

*What it enables:* operations that need an existing resource stop failing. This is where the operation
coverage number moves. **Supervision point.**

### M5 — IDL and constraint-based generation (5)

Relicensed assets imported → ANTLR4 parser with the differential conformance test → solver interface
and Choco backend, IDL4OAS read/write with provenance → constraint-based generator → the
constraint-aware oracles.

*What it enables:* RESTest's differentiator returns, and this time it is usable on an API nobody has
configured by hand. Evidence: the ICSOC'20 experiment re-run against the published 1.x numbers.
**Supervision point.**

### M6 — External data and live model updates (4)

Value dictionary format, reader, writer and cache → `ExternalDataProvider` interface with a file-based
implementation and an out-of-process transport, asynchronous and never blocking → `ConstraintSource`
and `FlowSource` with a watched-directory implementation → idle-time reporting and the overhead
regression test wired into CI.

*What it enables:* anyone — a script, a service, a person, later a model — can feed the tool better
values or newly discovered dependencies *while it runs*, and it costs the run nothing.
**Supervision point.**

### M7 — Packaging and distribution (3)

Maven Central → release automation for Homebrew, SDKMAN, Docker, jbang and GitHub Releases → native
binary with an executing smoke test, GitHub Action, documentation site.

*What it enables:* `brew install restest && restest run petstore.yaml`, on a machine with no Java.

### M8 — Evaluation (2)

Full campaign against the 2026 field → ablation study and replication package.

**Final supervision point for v2.0.**

---

## 10. Design principles

1. Zero configuration to start; full configuration available.
2. Never crash on a bad specification — skip and report.
3. Interpret, don't generate.
4. One event stream, many listeners.
5. Narrow interfaces, discovered implementations, enforced module boundaries.
6. No global mutable state.
7. The request loop is never blocked, and idle time is reported.
8. Open formats in, open formats out.
9. The tool runs standalone; evaluation harnesses live outside the build and are optional.
10. English everywhere; Apache-2.0; semantic versioning; published on every tag.

---

## 11. Deferred backlog (not in v2.0)

Out of scope until v2.0 is functional and measured. Each entry names the §5 seam it will use, so none
of them requires re-architecting.

| Item | Seam |
|---|---|
| Re-integrating your LangGraph / small-model data generator | §5.2 external provider, out-of-process |
| Fine-tuned small models for input values | §5.2 |
| Refining values from the API's own error messages | §5.2 + §5.5 |
| **Inferring inter-parameter dependencies** and injecting them as IDL during the run | §5.3 — the natural v2 paper |
| **Semantic oracles inferred from request/response corpora** | §5.4 corpus oracles + store + `recheck` |
| Semantic oracles inferred from the specification | §5.4 |
| Metamorphic relations | §5.4 |
| Predicting whether a request will be accepted before sending it | §5.5 |
| Search-based or reinforcement-learning scheduling | §5.5 |
| Surrogate coverage goals for black-box search | §5.5 |
| Security oracles (injection, server-side request forgery, authorisation bypass) | `Oracle` interface + WFC codes |
| Flow discovery from execution traces | §5.3 |

---

## 12. Working method

### 12.1 Where the work happens

**Claude Code, inside the repository.** This Cowork session is the right place for what we have been
doing — reading papers, surveying tools, drafting and revising this plan — and for reviews. The daily
implementation loop belongs in Claude Code: it works in the git repository, runs the tests after every
change, keeps project memory in a `CLAUDE.md` file, and integrates with GitHub Actions.

Set-up, once:

- Long-lived `v2` branch off `main`; one short-lived branch per increment, squash-merged into `v2`.
  `main` untouched until the end, as you asked.
- A project memory file (≤200 lines) carrying the design principles, the module boundaries, how to run
  the tests and the smoke benchmark, "one increment = one pull request", "English only", "never weaken
  an architecture test to make a build pass", "nothing from the deferred backlog without approval",
  and **the pull request format below**.
- A read-only reviewer agent that audits every pull request before it reaches you.
- A hook that runs the affected module's tests after every code change, so a broken build never
  reaches a pull request.
- GitHub Actions for the test matrix and the nightly benchmark, and `@claude` on pull request comments
  so your review notes are acted on directly in the thread.

### 12.2 Pull request format

Every pull request follows this structure. The first two sections are for you; the third is for anyone
on the team; the fourth is for whoever reviews the code.

```markdown
## What you can do now that you could not before
One or two sentences, concrete and user-facing. Not "added the ValueProvider chain" but
"the tool now fills in dates, e-mails and UUIDs in a format real APIs accept, instead of
random text — so operations that used to return 400 now return 200".

## Try it yourself
Copy-paste commands from a clean checkout, with the expected output shown. Always
runnable on a laptop with nothing set up beyond Java and git. For example:

    git fetch && git checkout feat/m2-3-boundary-walk
    ./mvnw -q install -DskipTests
    ./restest run examples/petstore.yaml --url https://petstore3.swagger.io/api/v3

    Expected: 47 test cases, 3 failures, a report at ./restest-report/index.html.
    Look at the "Boundary values" section — it is new.

If the change is not directly visible from the command line, say so and give the next
best thing: a test to run, a file to open, a number to compare.

## How it works
Plain language, for a reader who does not program in Java. Explain the idea, not the
syntax. Define any unavoidable term inline, or link to the glossary. Three short
paragraphs at most. A small diagram is welcome.

## What changed in the code
For the code reviewer: the files touched, the key classes, the design decisions, and
anything deliberately left for later.

## Evidence
- Tests: N added, all green (link)
- Architecture and mutation tests: unchanged or improved
- Per-request overhead: X ms (previous: Y ms)
- Smoke run (two containerised APIs): operations covered X (previous Y), unique faults Z (previous W)
- Idle time: X% of budget

## Decisions taken
Anything I chose that you might want to overrule.

## Open questions
Anything I need from you.
```

Pull request titles say what became possible, not what was edited: *"Generate values that match
declared formats"*, not *"Add FormatAwareValueProvider"*.

### 12.3 Cadence

Default: **1–3 increments per day**, each a pull request. You review when it suits you; comments on
the pull request drive the next iteration. Nothing merges into `v2` without your approval during M0
and M1 and at every supervision point; afterwards, green increments inside a milestone may merge and
be reviewed in batch, at your discretion.

The **seven supervision points** (0.3, 1.8, M4, M5, M6, M7, M8) are where I stop and wait rather than
proceed on assumption.

### 12.4 Cost control

The expensive things are the benchmark campaigns, not the coding. Smoke on every pull request, nightly
on a schedule, full campaigns only at milestone boundaries and only after you approve.

---

## 13. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Deferred items drift into v2.0 | **High** | §11 exists for this; the pull request template asks; the project memory file forbids it |
| Repeating 2026: computation crowds out testing | High | The non-blocking invariant is an architecture test, not a convention; idle time is reported on every run; overhead regression test in CI |
| The tool accidentally acquires benchmark-specific behaviour | Medium | All platform knowledge is confined to `evaluation/entrypoint.sh`; `evaluation/` is outside the build and referenced by no Java code. Checked at every supervision point by searching `src/` for the platform's name and conventions, requiring zero hits |
| An upstream RESTGym change invalidates a campaign mid-flight | Low | The clone is pinned to the commit recorded in `evaluation/restgym/restgym.lock`; updating it is a deliberate, reviewed change |
| The IDL parser port changes behaviour subtly | Medium | Differential conformance test over the whole existing IDL corpus |
| Licence: Choco's terms, 1.x copyright holders, the missing licence file | Medium | Resolve before M5; start the relicensing conversation now; carry over no 1.x source |
| Native binary breaks on reflection | Medium | Tracing over the full test suite, metadata committed, executing smoke test in CI |
| Single-maintainer bus factor repeats | High | Design records, a short project memory file, one-class-per-oracle conventions, and a "how to add an oracle, a provider, a report" guide delivered in M3 |

---

## 14. Decisions

Settled with the maintainer, 11 September 2026. These become design records 001–010 in increment 0.3.

| # | Decision |
|---|---|
| D1 | **Repository:** long-lived `v2` branch in `isa-group/RESTest`; one short-lived branch per increment; `main` untouched until the end |
| D2 | **Licence: Apache-2.0** for v2 and for the relicensed IDL assets; add the missing licence file to IDLReasoner-choco; carry over no 1.x source. *Administrative lead time — start now, it blocks M5* |
| D3 | **Java:** every module targets 21, CLI included (ADR-0003 amended at M0.2); build toolchain on 25; CI matrix 21/25/26; no preview features in published interfaces |
| D4 | **IDL parser:** port the grammar to ANTLR4 with a differential conformance test; the `.xtext` grammar remains normative; reuse the IDL-to-CSP mapping as-is |
| D5 | **Priority:** benchmark metrics first; no external deadline drives the schedule |
| D6 | **OAS scope:** 2.0, 3.0.x and 3.1.x, fully. 3.2 and 4.0 are out of scope |
| D7 | **No throughput floor.** Non-blocking invariant, idle-time metric, adaptive concurrency, and a per-request overhead regression test against a fixed local stub |
| D8 | **No AI abstractions in v2.0.** "Ready for AI" = open formats + external data providers + live constraint and flow updates during the loop |
| D9 | **No RESTGym in the tool; the evaluation harness in our repository.** Nothing under `src/` references the platform. An optional top-level `evaluation/` directory — outside the Maven build, published nowhere, referenced by no Java code — holds the Dockerfile, entry script, pinned RESTGym commit and launcher, and drives campaigns against a scratch clone. Nothing is contributed to RESTGym's repository |
| D10 | **Pull requests** follow the §12.2 format: what is now possible, how to try it yourself, and a plain-language explanation |
| D11 | Emit JUnit 5 + REST-Assured code as a *report*; drop Allure in favour of a self-contained HTML report |

**Still open, non-blocking:** Choco's exact licence terms (confirm before M5); whether Arazzo export
earns its place (decide at the end of M4).

---

## Appendix A — Recommended stack

| Component | Choice | Version (11 Sep 2026) |
|---|---|---|
| Library bytecode | Java 21 | — |
| Build toolchain | JDK 25 (long-term support) | 25 |
| Build | Maven with wrapper | 3.9.16 |
| OAS parser | `io.swagger.parser.v3:swagger-parser`, behind our own interface | 2.1.47 |
| JSON Schema validation | `com.networknt:json-schema-validator` | 3.0.7 |
| Command-line framework | picocli (with its code generator, for native binaries) | 4.7.7 |
| HTTP client | OkHttp — network interceptors give exact request/response capture | 5.5.0 |
| Concurrency | Virtual threads | JDK 21+ |
| IDL parser | ANTLR4, grammar ported from `.xtext`, differential test | 4.13.x |
| Constraint solver | Choco, behind an interface | 4.10.x — *licence to verify* |
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

## Appendix B — Sources

- SBFT Tool Competition 2026, REST League — https://doi.org/10.1145/3786155.3795704
- RESTest at the REST League 2026 — https://doi.org/10.1145/3786155.3795700
- RESTGym — https://github.com/restgym/restgym ; ICST 2025 paper https://ieeexplore.ieee.org/document/10988956/
- LlamaRestTest — https://dl.acm.org/doi/10.1145/3715737 · https://arxiv.org/html/2501.08598v2
- AutoRestTest — https://github.com/selab-gatech/AutoRestTest · https://arxiv.org/abs/2411.07098
- EvoMaster — https://github.com/webfuzzing/evomaster ; Web Fuzzing Commons — https://github.com/WebFuzzing/Commons
- Schemathesis — https://github.com/schemathesis/schemathesis
- RestTestGen — https://github.com/SeUniVr/RestTestGen
- CATS — https://github.com/Endava/cats
- Zhang & Arcuri, *Open Problems in Fuzzing RESTful APIs*, TOSEM 2023 — https://arxiv.org/pdf/2205.05325
- OpenAPI Specification — https://spec.openapis.org/oas/ ; Arazzo — https://spec.openapis.org/arazzo/
- OSSRH sunset / Central Portal — https://central.sonatype.org/pages/ossrh-eol/
