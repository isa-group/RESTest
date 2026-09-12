# RESTest 2.0 — roadmap

43 increments in 9 milestones. One increment = one branch = one pull request into `v2`.
Take them in order unless told otherwise. Design rationale in `docs/DESIGN.md`.

**Supervision points** are marked 🛑. At those, stop and wait for review rather than continuing.

**Delivered increments** carry ✅ and the pull request that delivered them. An increment is marked in
its own pull request, so the table and the branch history never drift apart.

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
| 1.4 | Interaction store (SQLite + NDJSON) and its query API | Every run is inspectable afterwards |
| 1.5 | Value provider chain and random providers; random test-case generator | The tool invents its own inputs |
| 1.6 | Oracles: server error, response schema conformance. WFC fault codes, event stream, console and JSON reports | Real failures are reported, each with a `curl` command to reproduce it |
| 1.7 | `restest run <spec> --url <base> --budget <duration>`; smoke integration test against two containerised APIs | The whole thing works from one command; regressions caught on every PR |
| 1.8 | 🛑 `evaluation/` harness: Dockerfile, entry script, pinned RESTGym commit, `run-evaluation.sh` | First campaign-comparable numbers: v2 vs RESTest 1.x vs the published 2026 field |

The golden corpus of specifications used throughout M1 and M2 lives at
`restest-spec/src/test/resources/specifications/`, in three directories: `restleague-2027/`,
`community/` and `fixtures/`. `restleague-2027/` — the five APIs named in the
[2027 REST League benchmark](https://seunivr.github.io/RestLeague/2027/) — is the priority corpus:
these are the APIs the tool is actually evaluated against, so exercise them first in every
increment's tests, before the wider `community/` corpus. Exact provenance, so the five files can be
re-fetched or checked for drift:

| Directory | Upstream project | `openapi.yaml` pinned at |
|---|---|---|
| `flight-search/` | github.com/Rapter1990/flightsearchapi | github.com/restgym/flight-search-api@2838238, `specifications/flight-search.yaml`, fetched 2026-09-12 |
| `gestao-hospital/` | github.com/ValchanOficial/GestaoHospital | github.com/restgym/gestao-hospital-api@d4cb6c7, `specifications/gestao-hospital.yaml`, fetched 2026-09-12 |
| `kafka-rest-proxy/` | github.com/confluentinc/kafka-rest | github.com/restgym/kafka-rest-proxy-api@26d839b, `specifications/kafka-rest-proxy.yaml`, fetched 2026-09-12 |
| `notebook-manager/` | github.com/birddevelper/NoteBookManager | github.com/restgym/notebook-manager-api@d4f29e4, `specifications/notebook-manager.yaml`, fetched 2026-09-12 |
| `pet-clinic/` | github.com/spring-petclinic/spring-petclinic-rest | github.com/restgym/pet-clinic-api@e8250db, `specifications/pet-clinic.yaml`, fetched 2026-09-12 |

Each of those upstream projects packages its own specification inconsistently or not at all; the
pinned fork is simply where a ready, single specification file per API could be fetched from — no
other coupling to that infrastructure is implied or intended (ADR-0011 still holds: nothing under
`src/` depends on it, builds against it, or assumes its layout).

## M2 — Specification fidelity and input generation

| # | Increment | What it enables |
|---|---|---|
| 2.1 | `oneOf` / `anyOf` / `allOf` / discriminators folded into the canonical schema | Specifications that use composition stop being ignored |
| 2.2 | Declared examples harvested, in both the 3.0 and the 3.1 shapes | The specification's own sample values get used |
| 2.3 | Deterministic boundary walk: every documented limit probed exactly | Reproducible edge-case tests, not luck |
| 2.4 | Format-aware and pattern-based generators (date, e-mail, UUID, regular expressions) | Values real APIs accept |
| 2.5 | Request bodies: JSON, form encoding, multipart, XML | Write operations become testable |
| 2.6 | Authentication inferred from `securitySchemes` (API key, bearer, basic, OAuth2 client credentials) | Protected APIs stop returning 401 for everything |
| 2.7 | Value dictionary format, reader, writer, disk cache | Good values computed once, reused for ever, committed next to the specification |
| 2.8 | `ExternalDataProvider` interface: file-based implementation + out-of-process transport, asynchronous, never blocking | Any program in any language can suggest input values without slowing the run |

2.8's own "never blocking" guarantee is proved at that increment, by its own test (a slow provider
must not stall the run) — not by waiting for M6.2's overhead regression test, which lands much later
and checks the tool's overall per-request overhead, not any one extension point.

## M3 — Oracles, faults and reporting

| # | Increment | What it enables |
|---|---|---|
| 3.1 | WFC catalogue, first tranche: status-code conformance, content type, response headers, negative-data rejection, positive-data acceptance, missing required header, unsupported method | Many more kinds of bug detected |
| 3.2 | HTTP-semantics and REST-design oracles (WFC 900–909 and 950–965) | Protocol-level bugs nobody else on our side detects |
| 3.3 | `CorpusOracle` interface and `restest recheck <run>` | Re-examine a finished run with new oracles, offline, no API calls |
| 3.4 | Per-operation oracle configuration + published JSON Schema for the config file | False positives silenced per operation instead of the tool being switched off |
| 3.5 | Reports: HTML, JUnit XML, HAR, NDJSON; JUnit 5 + REST-Assured code export; `restest explain`; `restest replay`. Plus the "how to add an oracle, a provider, a report" guide | Results usable in CI, in an IDE, and by a human |

v2.0's own reports are raw: one finding per operation, not grouped. Deduplication and clustering is
deferred (see below), not a gap in 3.5 — a milestone campaign's cross-tool comparison is RESTGym's
job, not this report's.

## M4 — Stateful testing

| # | Increment | What it enables |
|---|---|---|
| 4.1 | Operation Dependency Graph inferred from names, types and schemas | The tool knows `POST /pets` must precede `GET /pets/{id}` |
| 4.2 | Runtime resource pool and value-source selection | Identifiers from real responses get reused instead of invented |
| 4.3 | Declared OpenAPI `links` consumed when present | Free accuracy on the few specifications that declare them |
| 4.4 | CRUD lifecycle model and sequence generation | Create-read-update-delete flows are exercised end to end |
| 4.5 | Stateful oracles: use-after-free, resource availability, failed update must not change, update idempotency | Bugs that only appear across several requests |
| 4.6 | 🛑 Arazzo import/export *(droppable — decide at the end of M4)* | Discovered flows become a standard, shareable document |

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

## Deferred — not in v2.0

Do not start any of these without explicit approval. Each names the extension point it will use,
so none of them requires re-architecting. Full table under "Out of scope for v2.0" in
`docs/DESIGN.md`.

- Re-integrating the LangGraph / small-model data generator → external provider
- Fine-tuned small models for input values → external provider
- Refining values from the API's own error messages → external provider + feedback
- Inferring inter-parameter dependencies and injecting them as IDL during the run → constraint source
- Semantic oracles inferred from request/response corpora → corpus oracles + store + `recheck`
- Semantic oracles inferred from the specification → corpus oracles
- Metamorphic relations → corpus oracles
- Failure deduplication and clustering → interaction store + an event-stream listener
- Predicting whether a request will be accepted before sending it → feedback
- Search-based or reinforcement-learning scheduling → feedback
- Surrogate coverage goals for black-box search → feedback
- Security oracles (injection, SSRF, authorisation bypass) → oracle interface
- Flow discovery from execution traces → flow source
