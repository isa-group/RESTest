# RESTest 2.x — roadmap

RESTest 2.0 is released. This file lists what 2.x is planned to add, in the order it is planned to
be taken. How 2.0 itself was built — every increment, the measurement each was judged by, and the
decisions taken on the way — is kept as it was on the tag
[`history/2.0-development`](https://github.com/isa-group/RESTest/blob/history/2.0-development/ROADMAP.md).

One increment = one branch = one pull request into `master`. Increments keep the numbers they had in
2.0's plan, so that the decision records and pull requests that cite them still read true; that is
why the milestones below are not numbered in order. Design rationale: [`docs/DESIGN.md`](docs/DESIGN.md).
Decisions: [`docs/adr/`](docs/adr/).

## How to read this file

| Mark | Meaning |
|---|---|
| ▶ | Next, taken in the order of work below |
| ⏭ | Planned, taken after the ▶ rows |
| 🛑 | Supervision point. Stop there and wait for review instead of starting the next increment |
| ✅ | Delivered, beside the pull request that delivered it. An increment is marked in its own pull request |

A letter after a number — `2.7b`, `2.10b` — marks a row that was split. Where a narrow version of a
row shipped in 2.0, the row says what is left.

Where a milestone needs it, its table is followed by **notes**: why a row is shaped the way it is,
and what it deliberately leaves out. Nothing in the notes is an increment of its own.

## The order of work

1. **7.2a**, the container image and the release archives — and the image of 2.0.0 itself, by
   running the release workflow once against its tag.
2. **M13**, the safeguards: from the day 2.0 was tagged it can be pointed at anybody's API, so what
   keeps it from flooding one comes first.
3. **M3**, **M4**, **M5**, **M6**, then the rest of **M7**, unless what is measured says otherwise.
4. **8.1** and **8.2**, the evaluation, alongside the rows above: they take machine time rather
   than desk time.

The rows carried over from 2.0's own plan — the rest of authentication, the external value provider,
the dictionary cache, a strategy's share as a stretch of time — are in [M2](#m2--carried-over-from-20)
and are taken where the order of work reaches what they serve.

---

## M7 — Packaging and distribution

| # | Increment | What it enables |
|---|---|---|
| 7.2a ▶ | **A container image and a GitHub Release on every tag.** A `Dockerfile` of the tool's own, at the root of the repository, which compiles RESTest from the source in one stage and runs it on a Java 21 runtime in the next, for `linux/amd64` and `linux/arm64`; `restest` as the entry point. On every tag the release workflow publishes it to GitHub's container registry, and JReleaser publishes a GitHub Release with a distribution archive — the jars and a launcher for Unix and for Windows — the PDF of the manual that `docs/manual/pdf.sh` builds, and checksums. The smoke job builds the image and runs it, not only the jars. The README's installation and use, the manual's chapter on installing, and `docs/DESIGN.md`'s distribution and stack gain the image. The workflow is run once by hand against `v2.0.0`, so that 2.0.0 has its image and archives. Nothing under `src/main` and no dependency changes | `docker run ghcr.io/isa-group/restest run <spec> --url <base>` works, and a release can be downloaded and run with nothing but Java |
| 7.1 ⏭ | Maven Central publication through the Central Portal | `restest-core` usable as a dependency |
| 7.2b ⏭ | Homebrew, SDKMAN, jbang | `brew install restest` |
| 7.3 ⏭ 🛑 | GraalVM native binary with an executing smoke test; GitHub Action; documentation site, built from the Markdown the documentation is written in | Sub-100 ms startup, no Java needed, usable in anyone's CI |

### Notes

**7.2a — the image compiles the tool itself.** A Dockerfile that copies a build made beforehand
works only after that build; one that compiles in a first stage works from any checkout with Docker
and nothing else, and the image and the archive of a release are then two builds of the same commit.
The first stage runs on the machine's own processor whatever the image is for, since what it
produces is Java, so building for two processors costs one compilation.

## M13 — Safeguards

*Goal: an API that refuses the run, asks it to slow down or stops answering is not flooded by it.*
RESTest is a testing tool, and nothing in it should make it a convenient way to flood somebody's
API. Every row lands with a switch and its numbers in the settings (ADR-0025), and before any of
them is built it is replayed over recorded runs to show it would not have stopped an ordinary one.

| # | Increment | What it enables |
|---|---|---|
| 13.1 ⏭ | **Stop when the API refuses the run's credentials.** A rule about the whole API, never about one operation: once every answer over a stretch of the run — the opening lap's, and after it the last *N* — is 401, or 401 and 403, and nothing in it was accepted, the run stops sending, says which credentials it held and that the API refused them, and writes what a run cut short writes. The stretch, whether 403 counts, and the switch are settings. Once the rest of 2.6 lets the tool sign itself in, the requests before the sign-in do not count | A mistyped or forgotten key is found out in seconds, rather than after an hour of requests the API refused one by one; and an API whose owner revoked a key stops receiving them |
| 13.2 ⏭ | **Wait when told to wait.** A 429, or a 503 that carries `Retry-After`, pauses every request to the API for as long as the header says — in either of its forms, a number of seconds or a date — or, where there is no header, for a wait that doubles each time up to a ceiling; the engine then starts again from its fewest requests in flight and climbs as it already does. The `RateLimit` fields being drafted at the IETF are read where an API sends them. A run still told to wait after *K* pauses, or told to wait past the end of its budget, stops as 13.1 does. The time spent waiting is reported as idle time, with its cause. Whether the request that was turned away is sent again after the wait is the row's to decide | An API that asks the tool to slow down is obeyed. Today a 429 is an ordinary answer, and a quick one, so the engine's limiter reads it as room to send *more* |
| 13.3 ⏭ | **A ceiling on the rate.** The most requests a second the run may send, beside the most it may have in flight, which exists already: a setting, off by default | A person testing somebody else's staging server can promise its owner a rate, in one line |
| 13.4 ⏭ | **Stop when the API stops answering.** When every request over a stretch goes unanswered — refused, reset, timed out — the engine keeps one in flight until one is answered, and the run stops once the API has been silent for a stated time, saying when it went silent. The limiter already falls to one request in flight as unanswered requests pile up; what is new is stopping, and saying so | A run that brought an API down stops making it worse, and the report says at what moment the API went silent — which is a finding in itself when the run is what silenced it |

### Notes

**M13 — what 2.0 already does.** At most sixteen requests are ever in flight
(`engine.maxConcurrency`). The limiter halves that number whenever a request goes unanswered, and
sends fewer as answers slow down. A request names the tool in its `User-Agent`, `RESTest/2.0`, so
that an API's owner can tell the traffic apart and refuse it (`engine.userAgent`) — unless the
document declares that header as a parameter of its own, when what goes is the value built for it.
And every run ends when its budget does.

**M13 — what the safeguards cannot do.** The tool is open source and every lever has a switch, so
none of these stops somebody who means harm: one line of settings, or a fork, turns any of them
off. What they do is make the default safe and turning one off deliberate — and visible, since
`report.json` records every setting and where it came from. Whether the person running the tool is
allowed to test the API is not something any tool can check, and none of these rows pretends to.

**13.1 — a stop, not a steer.** It chooses nothing about what is sent; it only ends a run that can
learn nothing more. It has to be a rule about the whole API, because an operation that answers 401
to one request may answer something else to the next. An API that signs callers in through a proxy
in front of it answers nothing while the proxy signs in, so a stretch shorter than an API takes to
become ready would stop a run that was about to be let in.

**13.3 — why off, and why not by address.** A ceiling on by default would slow every run to protect
the few pointed at somebody else's server. A default that switched on for any address other than
the tool's own machine was considered and is not proposed: APIs under test often run in containers
of their own, at addresses that are not the machine's.

**M13 — what related tools do.** Surveyed from each tool's documentation and source: Schemathesis,
EvoMaster, RESTler, CATS, RestTestGen, WuppieFuzz, AutoRestTest and ARAT-RL, with ZAP's API scan for
comparison.

- **A ceiling on the rate is common, and off by default.** Schemathesis has `--rate-limit`,
  EvoMaster `--ratePerMinute`, and ZAP a rate limit in its network options. CATS alone keeps one on
  always, at 10,000 requests a minute.
- **Waiting after a 429 is common; slowing down afterwards is not.** EvoMaster always waits for
  `Retry-After`, or ten seconds without it, and sends the request again; RestTestGen and
  AutoRestTest do the same a bounded number of times, and Schemathesis only when asked. RESTler
  retries every five seconds without reading the header, WuppieFuzz only logs it, and CATS and
  ARAT-RL do nothing. Every one of them returns to its old pace afterwards, none waits on a 503 by
  default, and none reads the `RateLimit` fields.
- **None stops when its credentials are refused.** Schemathesis warns after the run about an
  operation 90% of whose requests answered 401 or 403, and CATS when half of all its tests did. Only
  EvoMaster stops by itself, when connections are refused.
- **Half name themselves** in the `User-Agent` — Schemathesis, RESTler, CATS and WuppieFuzz — while
  ZAP presents itself as a browser.

So 13.3 is what Schemathesis, EvoMaster and CATS already have, 13.4 is what EvoMaster does by
exiting, and 13.1 — and 13.2's slowing down after the wait — are what none of them does.

## M3 — Oracles, faults and reporting

2.0 reports two kinds of fault, a reply of 500 and a reply of the wrong shape. This milestone adds
the rest of the shared fault catalogue and the reports that make results usable outside a terminal.

| # | Increment | What it enables |
|---|---|---|
| 3.1 ⏭ | WFC catalogue, first tranche: status-code conformance, content type, response headers, negative-data rejection, positive-data acceptance, missing required header, unsupported method. The last two read the **intent** every request already records: whether it was meant to be accepted, or broken on purpose | Many more kinds of bug detected |
| 3.2 ⏭ | HTTP-semantics and REST-design oracles (WFC 900–909 and 950–965) | Protocol-level bugs nobody else on our side detects |
| 3.3 ⏭ | `CorpusOracle` interface and `restest recheck <run>` | Re-examine a finished run with new oracles, offline, no API calls |
| 3.4 ⏭ | Per-operation oracle configuration + published JSON Schema for the config file | False positives silenced per operation instead of the tool being switched off |
| 3.5 ⏭ | Reports: HTML, JUnit XML, HAR, NDJSON; JUnit 5 + REST-Assured code export; `restest explain`; `restest replay`. Every format renders both classifications of a fault — by catalogue number and by the class of status code that carried it (ADR-0016). Plus the "how to add an oracle, a provider, a report" guide | Results usable in CI, in an IDE, and by a human |
| 3.6 ⏭ | Replies that no rule could judge counted, and said out loud in the summary, the JSON report and the exit code | A clean bill of health stops being ambiguous: a run that could not check something says so, instead of saying nothing was wrong |
| 3.8 ⏭ | **Every failure of RESTest's own answers `4`** — the three ways in which one still reads as something the API did. A key that cannot be added to a request: the request is not sent and is recorded as a failure on the way, so a run where every request goes that way answers `3` and points at the address. An exception from RESTest's own code inside the engine — the code that records what went over the wire, say — caught with the network's failures and recorded as one. A failure while the loop deals with an answer — announcing it, handing it to a series — lost with the future it happened in, so the request counts as answered and no report hears of it. ADR-0015's M12.1c amendment describes each | A run's `0`, `1` and `3` mean what they say in every case RESTest can go wrong in |

### Notes

**3.1 — what is already there for it.** Every request records its intent, and every change made to
an accepted request records what it broke, so the two oracles that judge whether broken data is
refused and correct data accepted have what they need on every stored run.

**3.5 — the reports are raw, and deliberately.** Every fault is counted on its own and none is ever
declared to be the same problem as another. What the JSON report bounds is how many faults of one
kind, on one operation, it quotes at length — an allowance over two fields already recorded, not a
judgement that two faults share a cause. Deduplication and clustering is
[deferred](#deferred--not-in-2x-without-approval), not a gap in 3.5.

## M4 — Stateful testing

2.0 fills identifiers from what the API returned, by name and by the kind of thing an address names,
and sends six short series around the things a run creates. This milestone is the general version.

| # | Increment | What it enables |
|---|---|---|
| 4.1 ⏭ | Operation Dependency Graph inferred from names, types and schemas: the graph over every property, the similarity score, the synonym table as versioned data, and the measurement against word vectors that ends in an ADR (ADR-0017 item 3). 2.0 has the rule for path parameters | The tool knows `POST /pets` must precede `GET /pets/{id}` for every kind of parameter, not only the ones in a path |
| 4.2 ⏭ | Value-source selection among 4.1's candidates, and the producer-then-consumer choice — sending the operation that makes an identifier just before the one that needs it. Whether the choice may learn from what the API answered is ADR-0017's second open question | Identifiers from real responses get reused for every parameter the graph can reach |
| 4.3 ⏭ | Declared OpenAPI `links` consumed when present | Free accuracy on the few specifications that declare them |
| 4.4 ⏭ | CRUD lifecycle model and sequence generation: the lifecycle as a model, and the series 2.0 did not take — a child reached through another parent, a failed creation or change leaving a trace, a merge-patch that touches more than it names, a PUT that creates twice | Create-read-update-delete flows are exercised end to end |
| 4.5 ⏭ | Stateful oracles: use-after-free, resource availability, failed update must not change, update idempotency, and a read that changes what it reads. Every step of a series records which series, which step and the exchanges it follows, which is enough for these to judge a stored run offline | Bugs that only appear across several requests, *named* rather than only counted |
| 4.6 ⏭ 🛑 | Arazzo import/export *(droppable — decide at the end of M4)* | Discovered flows become a standard, shareable document |

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

**4.2 — what was learnt in 2.0.** A two-step sequence, producer then consumer, was built during 2.0
and measured, and not merged: on APIs where every kind of thing also has a list, the memory of what
the API returned is never empty, and the pairs almost never fired. 4.2 starts from that measurement,
and reconciles the candidates with ADR-0013's rule that a sequence creates what it needs rather than
borrowing an identifier.

## M5 — IDL and constraint-based generation

`restest-idl` ships in 2.0 as an empty module, and the documentation says so.

| # | Increment | What it enables |
|---|---|---|
| 5.1 ⏭ | Relicensed IDL assets imported; ANTLR4 parser; differential conformance test over the existing IDL corpus | The language works without dragging in Xtext |
| 5.2 ⏭ | `ConstraintSolver` interface + Choco backend | Solving is replaceable and testable in isolation |
| 5.3 ⏭ | IDL4OAS read/write, constraints carrying provenance and confidence | Dependencies can come from somewhere other than a hand-written file |
| 5.4 ⏭ | Constraint-based generator: valid requests and deliberate dependency violations | RESTest's differentiator, back and usable |
| 5.5 ⏭ 🛑 | Constraint-aware oracles (`2XX_P`, `2XX_D`, `4XX`) + the ICSOC'20 experiment re-run | The two novel oracles work, and we can compare against the published 1.x numbers |

## M6 — Live updates and performance

| # | Increment | What it enables |
|---|---|---|
| 6.1 ⏭ | `ConstraintSource` and `FlowSource` with a watched-directory implementation | Drop an IDL snippet in mid-run and watch the generated requests change |
| 6.2 ⏭ 🛑 | The per-request overhead regression test wired into CI. Idle time is reported on every run since 2.0; the regression test is what is owed | A change that makes RESTest spend its budget computing instead of testing fails the build |

## M2 — Carried over from 2.0

| # | Increment | What it enables |
|---|---|---|
| 2.6 ⏭ | The rest of authentication. API keys, and a bearer token or a cookie somebody already holds, ship in 2.0 through `--auth`. Still to come: a user name with a password, handed over the same way; OAuth2 client credentials; a sign-in the tool performs itself — register, log in, and carry the token or cookie that comes back; refreshing what expires; and several credentials at once, which access-control oracles would need. The sign-in and the several users are what the authentication file of Web Fuzzing Commons describes, and four related tools already read it. ADR-0029 says how these fit behind the door `--auth` opens | Protected APIs stop returning 401 for everything |
| 2.7b ⏭ | Dictionary writer and disk cache. It waits until the tool computes a value at a cost worth saving, which is the solver of 5.2 or the external providers of 2.8 | Good values computed once are kept, rather than worked out again every run |
| 2.8 ⏭ | `ExternalDataProvider` interface: file-based implementation + out-of-process transport, asynchronous, never blocking. A slow provider must not stall the run, and that is proven by its own test | Any program in any language can suggest input values without slowing the run |
| 2.10b ⏭ | **A strategy's share honoured as a stretch of the clock** rather than drawn per request. A run with no memory chosen by the clock would no longer be repeated from its seed alone ([ADR-0026](docs/adr/0026-what-a-run-sends-first.md) §7), which is what the row has to settle | A share of the budget is honoured as one, rather than on average |

## M8 — Evaluation

Campaigns run from a repository of their own ([ADR-0011](docs/adr/0011-evaluation-harness.md));
nothing here builds against it.

| # | Increment | What it enables |
|---|---|---|
| 8.1 ⏭ | **The field.** RESTest 2.0 on the eleven APIs of a published comparison of REST API testing tools, at that comparison's own budget of one hour, five runs, so that its table is the comparison; RESTest 1.x re-run on the same machine, three runs, as the calibration point between the published hardware and ours | A table that puts RESTest 2.0 beside the other tools on the APIs and budget they were measured on, with the hardware difference bounded by a tool that appears in both columns |
| 8.2 ⏭ 🛑 | **The ablation and the replication package.** Two axes, at twenty minutes a run: where values come from — what the document declares, plus what the API returned, plus hand-written dictionaries — and the levers, by group: everything on, reach off, break off, everything off. Every campaign's pinned commits and raw data published, so that any number can be reproduced from two commands | Every claim made about 2.0 is a measurement somebody else can repeat, and says what each idea is worth rather than that the whole is good |

## Deferred — not in 2.x without approval

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

Three of those rows have an open question against them, all raised by
[ADR-0017](docs/adr/0017-what-we-take-from-autoresttest.md) and all of the same kind: each would have
a run learn from what it has already seen. 🛑 None is started without explicit approval.

- **Scheduling.** Rewarding the choice of *operation* for errors while rewarding the choice of
  parameters and values for success — go where it breaks, send requests that work — by weighted
  sampling over per-operation counters, with no learning and no hyper-parameters. Its narrowest
  version, withdrawing budget from operations that answer nothing but 405 or 401, was approved for
  2.0 and set aside before it was built: with the answers that depend on what the tool sends taken
  out of its list, recorded runs showed it with next to nothing to act on.
- **Predicting acceptance.** Whether 4.2 may score dependency candidates by what the API answered,
  which also costs the seed reproducibility ADR-0013 §7 promises.
- **Error messages.** Whether a warm-up may read the *text* of an error to learn which parameter was
  wrong, as opposed to reading its status code, which is ordinary scheduling.

## The golden corpus

The OpenAPI documents the tests run against live in `restest-spec/src/test/resources/specifications/`,
in three directories: a priority corpus of five real APIs, which every increment exercises first, a
wider corpus of real documents, and small fixtures written for one test each. Where each document
comes from, and the commit it was taken at, is in
[that directory's README](restest-spec/src/test/resources/specifications/README.md).
