# RESTest 2.0 — roadmap

**v2.0 is the version submitted to the [2027 REST League](https://github.com/SeUniVr/RestLeague/blob/main/2027/README.md).**
Tools are due on **9 October 2026**, results come on 13 November, and the solution paper is due on
4 December. Everything below is ordered by that calendar: what moves the competition's measurements
comes first, what closes the release comes next, and what does neither waits for 2.1. The reasoning,
and what was set aside to get there, is [ADR-0024](docs/adr/0024-the-competition-version.md).

One increment = one branch = one pull request into `v2`. Take them in [the order of work](#the-order-of-work),
not in numerical order: the numbers are names, kept stable so that earlier pull requests and ADRs
still read true, and the milestones were numbered before the plan was turned round. 73 increments in
13 milestones: 29 delivered, 16 more in v2.0, 28 after it.

Design rationale: [`docs/DESIGN.md`](docs/DESIGN.md). Decisions: [`docs/adr/`](docs/adr/).

## How to read this file

| Mark | Meaning |
|---|---|
| ✅ | Delivered, beside the pull request that delivered it. An increment is marked in its own pull request, so the table and the branch history never drift apart |
| ▶ | In v2.0. Taken in the order of work below |
| ⏭ | After v2.0. The row stays, numbered as it was, and is not started before the competition version ships |
| 🛑 | Supervision point. Stop there and wait for review instead of starting the next increment |
| → | The row moved. It names where the work now lives, and is no longer an increment of its own |

A letter after the number — `1.1a`, `1.7b`, `2.7a`, `3.1b` — marks a row that was split after it was
written. The letters keep the original number, so earlier pull requests and the ADRs that cite them
still read true.

Where a milestone needs it, its table is followed by **notes**: why a row is ordered where it is,
what it inherits from an ADR, what it deliberately leaves out. Notes run in increment order, and
nothing in them is an increment of its own.

## Where the work stands

| Milestone | Goal | In v2.0 | Delivered |
|---|---|---|---|
| M0 | Foundations | all | 3 / 3 ✅ |
| M1 | Walking skeleton | all | 13 / 13 ✅ |
| M2 | Specification fidelity and input generation | 2.10b came back from 9.1 for 2.1; four rows wait | 10 / 14 |
| M9 | Reach — every operation the API will answer, answered early | all | 2 / 4 |
| M10 | Break — more distinct server failures | all | 0 / 3 |
| M11 | Settings — every number somebody decided, somewhere one can change it | all | 1 / 2 |
| M8 | Evaluation | 8.3–8.6 before submission; 8.1 and 8.2 after it, for the paper | 0 / 6 |
| M12 | Closing v2.0 | all | 0 / 5 |
| M7 | Packaging and distribution | 7.2a only | 0 / 4 |
| M3 | Oracles, faults and reporting | none; 3.1b's generator half moved to 10.1, 3.7 to 12.1 | 0 / 6 |
| M4 | Stateful testing | none; the narrow versions of 4.1, 4.2 and 4.4 moved to 9.2 and 9.3 | 0 / 6 |
| M5 | IDL and constraint-based generation | none | 0 / 5 |
| M6 | Live updates and performance | none | 0 / 2 |

## What the competition measures, and which rows move it

The 2027 edition scores four things per API, one hour per run, five runs averaged, every score
normalised across tools before it is added up. The **Effectiveness** ranking adds the first three;
the **Efficiency** ranking adds the area under the curve of the same three, which rewards a tool for
getting there *early*; the **Fault detection** ranking is the first alone.

| Measured | How | What moves it here |
|---|---|---|
| Unique server failures | Distinct 5XX replies, told apart by their error message | Requests that break things in *different* ways: mutations of accepted requests (10.1), bodies of the wrong shape (10.2), sequences over real resources — delete then read, create twice (10.3). The tool's own oracles play no part: the benchmark counts the 5XX itself |
| Operations covered | Operations that answered 2XX at least once | Identifiers that exist (2.5b ✅, 9.2 ✅), a producer sent before its consumer (9.3), the required-only request drawn often (2.9), no budget spent on operations that can never answer (9.4) |
| Code coverage | Methods, statements and branches the API executed | Everything above, plus variety: values, optional parameters and body properties that change from request to request (2.5a ✅, 2.7c ✅, 10.2) |
| Area under each curve | The same three, integrated over the hour | An opening lap that sends every operation its best request in the first seconds (9.1), 0% idle time (✅, measured by the benchmark's own clock at 1.9), and the identifiers a lap needs before the lap needs them (9.3). The `Accept` header ADR-0017 asked for ships since 2.5a |

One thing the call for participation says twice, differently: the Efficiency ranking is defined as
the three areas under the curve, and the badge for winning it is described as coverage "with the
lowest resource consumption footprint". This plan takes the definition rather than the description
— nothing here is optimised for CPU or memory — but 11.1 makes the concurrency range a setting so
the shipped default can be changed in a line if the organisers say the badge reads the other way,
and 8.6 records the container's CPU and memory alongside its results so the question can be
answered with a number. Asking the organisers which reading holds is on the maintainer.

Two things follow for the plan. **Nothing that only improves the tool's verdicts is in v2.0** — the
WFC oracles of M3, the stateful oracles of 4.5, the constraint oracles of 5.5 — because the benchmark
judges the replies itself and our judgement of them changes no score. And **the five undisclosed APIs
are "fresh APIs never used in previous studies"**, so nothing here may be tuned to the five known
ones: every lever is measured on the priority corpus and *checked* on the fifty documents of the
wider corpus, and the settings of M11 are the place a number goes, never a special case in code.

## The calendar

| When | Gate | What has to be true |
|---|---|---|
| Tue 22 Sep | Replan accepted | This file, ADR-0024 and ADR-0025 reviewed. 8.3 launched the same night, its three plans and the five dictionaries written first |
| Wed 23 Sep | Registered | The competition's submission system holds the tool's name and authors; the entry is updated freely until the deadline, so registering costs nothing and removes one thing that can go wrong on 8 October |
| Sun 27 Sep | M9 measured | 2.9 and 9.1–9.4 merged; 8.4 running overnight |
| Fri 2 Oct | M10 measured | 10.1–10.3 and 11.2 merged; 8.5 running overnight; which output of the manual to publish decided (12.3) |
| Tue 6 Oct, noon | **Behaviour freeze** | 2.9, M9, M10, M11, 12.1, 12.2 and 7.2a merged and measured; the harness repository's compliant image built from that commit and checked with the benchmark's own tooling; 8.6 starts |
| Thu 8 Oct | **Submission** | 8.6 read; v2.0.0 tagged from the frozen commit (12.4); tool submitted, one day before the deadline |
| Fri 9 Oct | Deadline | Anywhere on Earth. Nothing is submitted on this day by plan |
| Mon 12 Oct | `master` replaced | 12.5, once the tag stands |
| 12 Oct – 1 Nov | The paper's numbers | 8.1 and 8.2 on the machine; 2.1 work in the tree |
| Fri 13 Nov | Results | — |
| Fri 4 Dec | Solution paper | Four pages, IEEE format; the tables come from 8.1 and 8.2 |

Seventeen days from acceptance to submission. M0–M2 delivered twenty-five increments in twelve
days, so the twenty-one ▶ rows fit the calendar with the campaigns running while the code is written,
and with nothing added. **An increment that grows is split, and the half that moves no measurement
goes to 2.1 — never the other way round.**

## The order of work

Machine time and desk time are two resources, and a campaign takes the machine for hours. Every
row of M8 names the rows written *while* it runs.

1. **11.1** the settings, first, so that every lever after it lands with its switch. **8.3** runs
   overnight in the meantime.
2. **2.9**, **9.1**, **9.2**, **9.3**, **9.4** — reach. Each row is measured on a
   restarted containerised pet-clinic before it is merged (the way 2.5b was), and **8.4** measures
   the milestone as a whole, overnight, on the five.
3. **10.1**, **10.2**, **10.3**, **11.2** — break, and the list of switches. **8.5** overnight.
4. **12.1**, **7.2a**, **12.2** — the command line frozen, the image, the documentation. Fixes from
   8.4 and 8.5 land here, behind a switch when they change behaviour.
5. **Freeze**, then **8.6** 🛑 for twenty-five hours, during which only **12.3** — the manual — is
   worked on, because it changes no behaviour.
6. **12.4** 🛑 tag and submit. **12.5** 🛑 `master` replaced.
7. After submission: **8.1**, **8.2** 🛑 for the paper, and the ⏭ rows in the order M3, M4, M5, M6,
   M7 unless the results say otherwise.

Three things were asked of the maintainer at the replan rather than at the row, and **all three
were approved on 22 September 2026**: **9.4** takes the narrow version of the first of ADR-0017's
open questions, which is on the deferred list; **12.3** writes the manual once and builds HTML and
PDF from it, so that the only decision left — which output to publish, due 2 October — is a cheap
one; **12.4** tags `v2.0.0-rc.1` for the submission if the manual is late and `v2.0.0` when it
lands, with a check that the two commits differ in documentation files only.

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
| 2.3 → [10.1](#m10--break) | The deterministic boundary walk. Deferred at 2.7a, not dropped: it returns as the mutation operator that steps outside a documented bound by exactly one | Reproducible edge-case tests, not luck |
| 2.4 ✅ [#316](https://github.com/isa-group/RESTest/pull/316) | What a document says about the **characters** of a value, read where what it says about its length already is (ADR-0022). The kind it names — `date-time`, `date`, `email`, `uuid`, `uri`, `ipv4` and the thirteen others, nineteen in all — built from the platform's own parsers rather than looked up in a list, so every value is different and every value is correct; and the spelling it states as a `pattern` built by a library, with every candidate held against the rule again before it is sent and the length the same shape demands honoured alongside it. **No format dictionary is shipped**, which reverses that much of ADR-0013 §1 and ADR-0020 §5: a list sees only its key, while a shape states its kind, its spelling and its lengths at once and all three have to hold together | A date parameter gets a date instead of a random word, and an operation is no longer refused before anything worth testing happens: 225 places across the corpus name a kind of value, 25 of them in the priority corpus, and the 10 spelling rules in pet-clinic are satisfied rather than broken |
| 2.5a ✅ [#313](https://github.com/isa-group/RESTest/pull/313) | Request bodies, built from the shape the document declares and the samples it writes down (ADR-0021): JSON and form encoding, the media type stated in `Content-Type`, an `Accept` header built from the operation's own 2XX responses, and a property the API only ever returns never sent. XML and multipart deferred with the measurement beside them — XML wins no operation in the corpus and multipart wins one | Write operations become testable: 103 operations of the corpus, 34 of them in the priority corpus, which is 23% of its whole surface |
| 2.5b ✅ [#318](https://github.com/isa-group/RESTest/pull/318) | The memory of what the API has returned (ADR-0021 §6): one listener on the event stream filling the two dictionaries the format already has — one value under its own name, a whole resource under the name of its shape — and `observed`, the word a plan names to draw on them. A resource is cut down to what the operation accepts - `readOnly` gone, what the operation never declared gone, what nobody could send gone - and has **one value in it replaced by a different one** before it goes back, because an unchanged copy asks a POST for a duplicate; where nothing in it can be varied it goes as it came and the run records that. Keyed by the name and not by the way down to it, which answers ADR-0021's open question on a measurement rather than on RESTler's example. **Brings forward the runtime resource pool of 4.2**, and is the first source in the tool whose runs are reproduced by replay rather than from the seed | A request stops inventing what the API has already shown it. In the shipped plan, measured on two containerised APIs restarted before every run with the plans alternating seed by seed: 27.8 of pet-clinic's operations answered 2XX without it and 31.6 with it, better on every one of five seeds, and 5.6 against 6.8 on gestão-hospital. Counting replies rather than operations it is better on all ten runs. It needs time to fill: against the pet shop the smoke gate starts, a ten-second run earns 9% fewer 2XX replies with it and a sixty-second run 27% more. 89% of the 2,125 values inside the corpus's request bodies carry a name some reply of the same API also carries |
| 2.6 ⏭ | Authentication inferred from `securitySchemes` (API key, bearer, basic, OAuth2 client credentials) | Protected APIs stop returning 401 for everything |
| 2.7a ✅ [#311](https://github.com/isa-group/RESTest/pull/311) | The dictionary format and its reader (ADR-0020): YAML, one file, one keying, values that may be whole objects, and no claim about what an API will make of them — which list feeds which kind of request is named in the plan. `--dictionary`, repeatable. The list of values RESTest ships to push at an API with, as the first thing written in that format, sent for the share of the budget that `--fuzzing` sets. Strategies as named shares of the budget, which is ADR-0013 §2's first half | A run finds the server errors that only unexpected input reaches, and good values for an API can be committed next to its specification instead of living in one person's head |
| 2.7b ⏭ | Dictionary writer and disk cache. **Not taken in its numbered place:** it waits until the tool computes a value at a cost worth saving, which is the solver of 5.2 or the external providers of 2.8. Skip it and go on to 2.8 | Good values computed once are kept, rather than worked out again every run |
| 2.7c ✅ [#315](https://github.com/isa-group/RESTest/pull/315) | **A dictionary reaches inside a request body** (ADR-0020, amended): a place is named by the way down to it — `body.owner.email`, `body.tags[].label` — and the same ordered list of sources that fills a parameter now fills every piece of a body. An operation answers to its `operationId` **or** to `GET /pets/{petId}`, so a file can be written from the specification with no reasoning about which. Every entry that could never be used is named when the file is read, before a request is sent. **Taken out of order, before 2.5b**, which is built on the keyings this fixes | About half of what anybody writes in a dictionary stops being ignored: of the 553 places the priority corpus has, a list could fill 247 and now fills 435 — 415 of them end to end — while the other 118 are ones the document settles by itself, which the run now says out loud. A file generated from an OpenAPI document — the way most of them will be — is checked against that document before any API is touched |
| 2.8 ⏭ | `ExternalDataProvider` interface: file-based implementation + out-of-process transport, asynchronous, never blocking | Any program in any language can suggest input values without slowing the run |
| 2.9 ✅ [#321](https://github.com/isa-group/RESTest/pull/321) | How many optional parameters to send drawn first, from a distribution favouring small numbers, and only then which ones — replacing the separate coin flip per parameter | The request an API is most likely to accept, the one carrying only what it requires, stops being drawn once in 2ⁿ attempts and is drawn about half the time instead, whatever the count. Of the corpus's 1,420 operations, 232 have four or more optional parameters and 71 have eleven; of the five priority APIs' 150 operations, only three have more than one, so a live measurement on pet-clinic could not show it either way (proved by construction: at most one optional parameter draws identically under both mechanisms) and was not expected to |
| 2.10a ✅ [#317](https://github.com/isa-group/RESTest/pull/317) | The campaign file (ADR-0023): named strategies with a share of the run, an ordered list of named sources with weighted groups among them, and the operation filter ADR-0013 §6 asks for. The shares and the order that were constants in `RandomTestCaseGenerator`'s constructor move into a file RESTest ships, prints with `--print-campaign` and reads back with `--campaign`. Three of §2's ideas dropped as saying what the structure already said, and §6's `safeOnly` answered by `methods` | Where a run's values come from stops being a decision somebody took once for everybody: it is a file you print, change one line of and hand back — and the source M2.5b adds has somewhere to be asked for |
| 2.10b ⏭ | **A strategy's share honoured as a stretch of the clock** rather than drawn per request. The row's other half - time-keeping moving out of `RunLoop` - was absorbed into 9.1, which built the scheduler; this half came back from it, because it moves none of the competition's measurements and a strategy chosen by the clock would make a run with no memory unrepeatable from its seed ([ADR-0026](docs/adr/0026-what-a-run-sends-first.md) §7) | A share of the budget is honoured as one, rather than on average |

### Notes

**2.3 — deferred to 3.1b, now 10.1, not dropped.** ADR-0013 §4 puts the boundary walk with the mutation
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
loaded and did nothing. 2.5b keeps what an API returned as dictionaries keyed by name and by shape,
and what it fills is the values inside request bodies — so landing it first would have built a
mechanism that could not reach its consumer.

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
types the operation's own 2XX responses declare; 2.5a sends it, and one of the five
specifications in the priority corpus serves a versioned media type. The samples come from 2.2, which
read every sample a document writes for a *parameter* and deliberately left the ones on a request
body alone, because bodies were not generated yet and `RequestBodyModel` had nowhere to put them
(ADR-0019 §5).

**2.5b — why the mutation is of a leaf and not of a resource, and what the increment settled.** The
question it answers is whether a body is better built from the shape the document declares or from a
resource the API has already handed back. Measured: the body's named shape is also returned by some
2XX for 16 of the 44 named bodies in the priority corpus — and for **none** of the 9 in
flight-search or the 12 in kafka-rest-proxy — while about 90% of the leaves inside those bodies carry
a property name that some reply also carries. So what is observed is reused leaf by leaf, whole
resources are one source among several rather than the mechanism, and neither needs a new interface:
both are the dictionary of ADR-0020 under a keying it already has.

Taking it settled three things, all in [ADR-0021's amendment](docs/adr/0021-how-a-request-body-is-built.md#amendment-m25b).
**The keying is the name, and the format takes no sixth one:** counted over the corpus, 1,886 of the
2,125 values inside request bodies carry a name some 2XX of the same API also carries and 1,700 sit
at an address one carries, so the way down to a value reaches strictly less — and it would need a
rule for turning a reply's own addresses into a body's, which the name does not. What it would have
told apart is 57 names, 47 of which differ only in a declared form and 10 of which disagree about
the kind of value and are refused anyway. **Adding the source cost two classes rather than one**,
because a value read out of a reply should name the exchange it came from and a dictionary hands
back bare values; neither `ValueProvider` nor `Dictionary` changed. And **the shipped plan names
it**, on the end-to-end measurement in the row, which is what makes a default run one that `--seed`
no longer repeats on its own — said in `--help`, in the plan's own comments, and in a line the run
prints under the seed.

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
anybody has written. 2.5b was expected to supply the evidence for how the budget should be split and
**did not**: what it measured was where a value comes from, not how much of a run goes on which kind
of operation, and those are different questions. The scope stays unbuilt and the question stays open.

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
the API it reaches — and 2.5b loosens the tension rather than removing it, by putting identifiers
that are real *and* varied into the same group: measured the same way, 27.8 of pet-clinic's
operations answered 2XX without that source and 31.6 with it.

**2.8 — "never blocking" is proved here, by its own test.** A slow provider must not stall the
run. Not by waiting for M6.2's overhead regression test, which lands much later and checks the
tool's overall per-request overhead, not any one extension point.

**2.9 — last in the table, dependent on nothing in it, and owed to ADR-0017.** It can be taken
whenever, and what makes it worth taking early is this: `RandomTestCaseGenerator` decided each
optional parameter with its own coin at one half, so for an operation with *n* of them the request
carrying only what the API requires — the one most likely to be accepted — was drawn about once in 2ⁿ
attempts. Eight optional parameters was once in 256. It is nominal generation rather than a
deliberate violation, so it is not 2.3's subject.

Measured rather than assumed, as ADR-0017 asks: of the corpus's 1,420 operations, 232 have four or
more optional parameters and 71 have exactly eleven — where the old mechanism was worst, since an
operation with eleven used to include each one 50% of the time and the required-only request about
once in 2,048 attempts. Of the five priority APIs' 150 operations, only three have more than one
optional parameter at all — flight-search's `scrape` (2), kafka-rest-proxy's `getKafkaAcls` (7) and
`deleteKafkaAcls` (3) — so a live measurement against a restarted containerised pet-clinic, five
seeds, showed a wash (27.8 operations answering 2XX before this row, 26.4 after, within the noise of
a 30-second budget): pet-clinic has exactly one operation with any optional parameter, which draws
identically either way by construction, so it could not have shown a difference. Kafka-rest-proxy is
the one priority API this row has room to matter on; measuring it live needs a Kafka broker behind
the REST proxy, which was not attempted here (recorded in [#321](https://github.com/isa-group/RESTest/pull/321)'s "Decisions
taken" rather than assumed away).

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


## Towards v2.0

The five milestones below are the competition version, listed M9, M10, M11, M12 and then M8;
[the order of work](#the-order-of-work) says when each is taken — M11 first so that every lever
lands with its switch, M9 and M10 because they move the measurements, M8's campaigns between them
because a lever nobody measured is a guess, and M12 to close. Two rows outside them are also in
v2.0: **2.9** in M2 and **7.2a** in M7.

## M9 — Reach

*Goal: every operation the API will answer is answered, and answered early.* Every row here is
measured before it is merged, the way 2.5b was — a containerised API from the priority corpus,
restarted before every run, five seeds, the row switched on against the row switched off — and the
number goes in the pull request. A row whose number is not better is not merged.

| # | Increment | What it enables |
|---|---|---|
| 9.1 ✅ [#322](https://github.com/isa-group/RESTest/pull/322) | **A scheduler of its own, and an opening lap.** The choice of *what to send next* left `RunLoop`: a `Scheduler` holds the deadline and the order, and the loop does what it says ([ADR-0026](docs/adr/0026-what-a-run-sends-first.md)). Its first job is an **opening lap**, before anything is drawn: every operation once, with the request it is most likely to accept - the parameters it requires and no others, a body wherever one is described, the plan's own sources asked in turn, a closed list of accepted values first and then what the API has already returned - in five steps, lists, creations, reads of one thing, changes, deletions, each waiting at most `schedule.openingLapPatience` for the answers to the one before to be heard. Charged to the budget, announced as a phase of its own and reported in the summary and in `report.json`; `schedule.openingLap` switches it off. An operation that declares no 2XX media type gets `Accept: */*`. **Three things differ from the row as approved, on the corpus's evidence**: the order goes by step rather than by method and then path depth, a value the API returned ranks above the document's sample, and a body goes wherever one is described rather than only where it is marked required - see the notes. **Not taken**: shares as stretches of time, back in 2.10b for 2.1 | The first seconds of a run cover what the tool can cover on its own, which is what the area under the curve rewards. Measured against two containerised APIs restarted before every run, five seeds, a minute each, the lap switched on against off: kafka-rest-proxy had 28.6 operations answered 2XX two seconds in against 14.2, and the area under that curve rose 17%, better on every seed; pet-clinic had 30.8 five seconds in against 19.6, the area up 15%, better on four seeds of five. Both ended the minute higher too: 34.2 operations against 29.6, and 32.2 against 30.8 |
| 9.2 ✅ [#331](https://github.com/isa-group/RESTest/pull/331) | **Identifiers by resource** (the narrow version of 4.1). A path parameter is filled from the identifier of the things its kind of address returned: `{petTypeId}` under `/pettypes/{petTypeId}` from the `id` of what `GET /pettypes` lists. The kind is the fixed part before the gap, or the one the gap's own name names, plural and singular spelt alike; a reply's things are a list's elements, an object, or what a wrapper with no identifier holds; nothing is kept from a `DELETE`. Four steps, first answer wins: the gap's exact name within those things, then `id` or the kind followed by id, then 2.5b's name anywhere, then anything written like an identifier - the second and fourth only for a gap itself named like one. Every candidate gated on kind, closed list, `uuid`/`int32`/`int64` and sendability. **One thing differs from the row as approved**: the kind is asked before the exact name, chosen by the maintainer on 26 September, with its own switch so the order can be measured - see the notes. `memory.identifiersByResource` and `memory.identifiersByResourceFirst`. No synonym table and no similarity score, which stay in 4.1 | The operations behind a gap the replies call something else get identifiers that exist. Four priority APIs restarted before every run, five seeds, a minute each: gestao-hospital covered 18.0 operations against 9.0 with it off, 16.8 of them within five seconds against 2.8, better on every seed; pet-clinic 33.0 against 31.8, the area up 3.8%, better on four seeds of five; notebook-manager's reads, changes and deletes by id answered 2XX 29.4% of the time against 10.3%; kafka-rest-proxy, whose gaps its replies already name, within noise. Over the corpus's declared replies, 325 of 1,838 gaps are reachable only this way |
| 9.3 ▶ | **Make what you need** (the narrow version of 4.2 and 4.4). When a consumer needs an identifier nobody has — no reply has carried one, or every one carried has since been deleted — the scheduler sends the producer first and the consumer right after, with what came back; a two-step sequence, recorded as one, the second test case naming the exchange its value came from (which 2.5b's provenance already does). This is ADR-0013's rule that a sequence *creates* what it needs, built rather than restated | An API whose identifiers cannot be guessed — kafka-rest-proxy's generated cluster and notebook-manager's posted notebooks — is covered instead of answering 404 for an hour; and the identifier a lap needs is there before the lap needs it |
| 9.4 ▶ | **Budget hygiene** — the narrow version of the first of [ADR-0017's open questions](#the-three-open-questions), **approved by the maintainer on 22 September 2026** and taken no further. The scheduler keeps, per operation, a count of what it answered. An operation whose last *N* answers were all of the kinds that say *this will never work as asked* — the shipped list is 401, 403, 404, 405 and 501, and the list is a setting — has its share shrink towards a floor; an operation that has never answered 2XX is never starved of attempts. Weighted sampling over those counters, three settings — *N*, the floor and the list — no learning rate, no reward. What is **not** taken: scoring dependency candidates by what the API answered (question 2) and reading the text of an error reply (question 3) | An hour is not spent on the operations the API will never answer — the ones behind a login the tool does not have, the methods it does not implement — and goes instead to the ones it might |

### Notes

**9.1 — why the scheduler and the lap are one row.** The scheduler alone changes nothing a person
can see, and a pull request here has to say what became possible. The lap is what makes it visible,
and the lap cannot be built without something that owns the order requests go out in. 2.10b was
"the scheduler takes the budget", and this is that row with a first customer.

**9.1 — the lap is charged to the budget.** ADR-0017 refuses a preparation phase outside the clock,
and this is no exception: the lap is the first seconds of the hour, reported as its own phase in the
summary and in `report.json`, so that a reader knows how long it took and what it covered. The
competition's clock starts when the container starts; so does ours.

**9.1 — what was built differs from the row as approved, and why.** Reading the priority corpus
against the row found that 49 of kafka-rest-proxy's 50 operations need a `{cluster_id}` that only
`GET /v3/clusters` can supply, and that the document's sample for it, `cluster-1`, does not exist.
Ordering by method and then by depth sends every `POST` before that read, and ranking samples first
sends `cluster-1` everywhere. On 23 September the maintainer chose five steps - lists, creations,
reads of one thing, changes, deletions - each waiting for the one before: over a finer grouping by
how many gaps a path has, judged more mechanism than the gain warrants, and over a simpler four
steps, reads, creations, changes and deletions, which sends the reads that need a cluster beside the
read that yields one. And a ranking that puts what the API returned before the document's samples,
with no switch of its own, since it only differs where the two compete. The body goes wherever one
is described because most documents never mark one required: 110 `POST` bodies unmarked against 58
marked, across the corpus. Without its waits the lap had 11 to 18 of kafka-rest-proxy's operations
answer it rather than 28 to 30. [ADR-0026](docs/adr/0026-what-a-run-sends-first.md) records the
alternatives and the measurement.

**9.1 — 2.10b's other half went back.** The row absorbed "a phase becomes a stretch of the clock
rather than a share drawn per request". That half moves none of the competition's measurements and
would cost a run with no memory its seed, so it went back to 2.10b, for 2.1, and the share is still
drawn per request.

**9.1 — what 9.3 still owes.** The lap links requests only indirectly, through the memory of values
kept by name, and only once, at the start. 9.3's producer-then-consumer sequence carries one reply to
one consumer whenever a consumer needs an identifier nobody has - after a deletion, or where only a
creation makes one - and 10.3's operators are built on that unit; the lap does neither. The part of
9.3's text the lap already does, deletes after the reads and updates of the same round, came out of
9.3's row.

**9.2 — what it is not.** 4.1 is a graph over every parameter, body property and response property
of every operation, with a similarity score, a synonym table carried as data, and a measurement
against word vectors that ends in an ADR. None of that is here. 9.2 is a rule about *paths*, because
paths are where identifiers live and where the shakedown campaign of 1.9 showed the tool going wrong;
a rule that reads `/pets/{petId}` is small, testable against the whole corpus without an API, and
leaves 4.1 everything it promised.

**9.2 — the kind before the name, and why it has a switch of its own.** The row said the exact name
first, which is what 2.5b already did. The maintainer chose the kind first, before any code, because
a generic name is the worse guide where it is commonest - `{id}` under `/flights/{id}` would take an
airport's `id`. The review asked that a departure from the approved row be measurable, so
`memory.identifiersByResourceFirst` turns it back. Measured, the order matters far less than the rule
itself: kind first leads name first on gestao-hospital's and pet-clinic's area, ties on
notebook-manager, and trails on kafka-rest-proxy's mean through one seed. Kept as the default and
left for 8.4 to confirm. [ADR-0021](docs/adr/0021-how-a-request-body-is-built.md)'s M9.2 amendment
has the numbers.

**9.3 — one sequence shape, not a lifecycle model.** 4.4 is create-read-update-delete as a model
with its own oracles in 4.5. 9.3 is *producer, then consumer*, chosen because it is the shape every
missing identifier has, and because 10.3 can build its operators on it — delete then read is
producer, consumer, consumer. The lifecycle model, and the oracles that need it, wait.

**9.3 — the two decisions ADR-0013 left to M4, taken here and recorded there.** ADR-0013 deferred
the *unit of work* of a sequence and *interference* between concurrent sequences to M4, and M4 is
now after v2.0, so 9.3 takes both and amends ADR-0013 in its pull request. The unit of work is the
sequence: one worker sends the producer, reads its reply, and sends the consumer with what came
back, and the consumer's value comes from *its own* producer's reply rather than from the memory of
observed values — which is ADR-0013's rule, *a sequence creates what it needs*, taken literally.
Interference is accepted rather than prevented: with the engine's concurrency on, another unit may
delete or recreate the same resource between the two steps, so a 404 on the consumer is not by
itself attributable. That costs 9.3 nothing the competition scores — the benchmark counts 2XX and
5XX, not our attribution — and it is exactly why 4.5's stateful oracles wait: they need the
attribution, and the attribution needs a decision about concurrency that 9.3 does not take. 9.2 is
not a new borrowing: the plan's `observed` source has filled parameters from what other requests
returned since 2.5b, under ADR-0021's amendment, and 9.2 widens its keying rather than its licence.

**9.4 — the line it stands on.** `docs/DESIGN.md` defers "search-based or reinforcement-learning
scheduling", and ADR-0017 argues that the narrowest useful version of steering by counters is
hygiene rather than learning: no constant that needs a paper to justify it, nothing that has to be
calibrated. It is still a run learning from what it has seen, which is why it is asked for rather
than assumed, and why the two neighbouring questions are named as *not* taken. It costs what 2.5b
already cost: a run using it is reproduced by replaying the stored requests, not from the seed.

**9.4 — the seam it proves.** `docs/DESIGN.md` lists `FeedbackListener` — observes every
interaction, may return scheduling hints — among the extension points, and ADR-0017 noticed that no
row delivered it. This one does: the counters are a listener on the event stream, and the hint is a
weight per operation. The seam gets its throwaway-free proof by being used.

## M10 — Break

*Goal: more distinct server failures, and the error branches of the API executed.* The benchmark
tells 5XX replies apart by their message, so what counts is not how often the API falls over but in
how many different ways — and every row here adds a different way. Measured before merging like M9,
counting distinct 5XX messages and branch coverage rather than operations covered.

| # | Increment | What it enables |
|---|---|---|
| 10.1 ▶ | **Mutations of accepted requests** — the generator half of 3.1b, under ADR-0013 §4. A listener on the event stream keeps a bounded index of the test cases that actually returned 2XX (§5), and operators take one and change exactly one thing: drop a required parameter, send the wrong type, step outside a documented bound by exactly one (2.3 returns here, as promised), break an enumeration, break a pattern, send a required parameter in a location it was not declared in (ADR-0017 item 5), oversize a string or an array, send `null`, send the empty value. A third strategy in the shipped plan with a share of its own. The test case gains the **intent** of §3 — *I believe these values are acceptable*, *I expect this refused and here is what I broke*, *I do not know* — as data; the oracles that read it are 3.1's and wait | The tool stops only asking "does this work?" and starts asking "what happens when one thing is wrong?" — which is where the 500s that a correct request never reaches live, one code path per operator |
| 10.2 ▶ | **Bodies of the wrong shape.** 2.7a's fuzzing changes *values*; this changes the *shape*: a leaf of the wrong kind, the root of the wrong kind — an array where an object was declared — an empty body, a body that is not JSON at all, the wrong `Content-Type` for a valid body, nesting far deeper than the schema, arrays far longer than any limit, the numeric extremes of every width. Each one a named operator, each one attributable | The parsing and binding layers of the API — the code that runs *before* the operation's own — are exercised, and those layers fail in their own distinct ways |
| 10.3 ▶ | **Sequence operators over real resources**, built on 9.3's sequences and kept inside one unit of work each: create a resource, delete it, then read it, update it and delete it again; create the same thing twice; create two resources and update one with the other's identifier; create, delete, and send the deleted identifier to every consumer that takes one. Each is a named sequence with an intent, every identifier it uses is one the same sequence created, and what a unit knows about what it deleted stays in that unit | The server failures that only appear across several requests — the dangling reference, the double delete, the duplicate key — which single requests never reach and which the stateful tools we are measured against do reach |

### Notes

**10.1 — what it takes from 3.1b and what it leaves.** 3.1b's notes below M3 still describe why the
operators are shaped as they are — a mutation of a request the API accepted, so that a 2XX afterwards
is attributable to the one change — and 10.1 inherits all of it. What it leaves behind is the pair
of oracles 3.1 supplies, *negative data must be refused* and *positive data must be accepted*. The
original argument for ordering 3.1 first was that without those oracles a broken request "would earn
a 4XX that nothing reads and no finding anybody could act on". For the competition the 4XX is not the
point; the 5XX that a broken request sometimes earns instead is, and `ServerErrorOracle` reads that
already. The intent is recorded anyway, because it is a fact about the request and recording it
costs nothing, and because 3.1 then has a consumer waiting when it arrives.

**10.2 — why shape and value are two rows.** The fuzzing dictionary is a list of *values*, and 2.7c
made it reach every leaf of a body. What it cannot express is a body whose structure is wrong, because
a dictionary entry is a value at a place and the place is fixed by the schema. That is a generator
concern, and it is one operators do well: each says exactly which structural rule it broke.

**10.3 — the oracles are not here either.** 4.5's stateful oracles — use after free, update
idempotency — would judge these sequences. The benchmark judges them for us, by counting the 5XX;
4.5 waits, and 10.3 records enough on each sequence for 4.5 to judge it offline later.

**10.3 — why every operator creates its own victim.** 9.3 accepts that concurrent units interfere.
An operator that borrowed a deleted identifier from another unit's memory would then be sending
something whose state it cannot know, and "delete then read" would mean nothing. Keeping each
operator to resources its own sequence created is what lets its name stay true: the one thing the
sequence did to that resource is the one thing the sequence knows about it.

## M11 — Settings

*Goal: every number somebody decided has one place it can be changed, and an experiment can switch
a behaviour off without touching the code or the plan.* [ADR-0025](docs/adr/0025-settings.md).

| # | Increment | What it enables |
|---|---|---|
| 11.1 ✅ [#320](https://github.com/isa-group/RESTest/pull/320) | **The settings.** One immutable `Settings` in `restest-core`, built from typed records per concern — `EngineSettings` already exists and is the model — assembled once in the command-line module from four layers in this order: the defaults in code, a file given with `--settings`, environment variables named `RESTEST_<GROUP>_<KEY>`, and `--set group.key=value` repeated. `restest run --print-settings` prints the effective values with the origin of each, the way `--print-campaign` prints the plan. Unknown keys are refused with the nearest known one; the effective settings and their origins go into `report.json`, so a run says how it was configured. An architecture test forbids reading the environment or system properties anywhere but `restest-cli`. **First tranche moved from constants:** the concurrency range and its slowdown factor, the retained response bytes, the work-ahead factor and the straggler grace; the depths, lengths, item counts, null rate and attempt counts of invented values; the sizes of the memory of observed values; the bounds of the JSON report; the document size and fetch timeout. **Thirty-nine settings in six groups**; `store` and `sequences` are not created because nothing fills them yet. Two things the building settled: where the engine starts is worked out from the concurrency range in force rather than fixed, so `--set engine.maxConcurrency=1` works as one line; and the pair deciding the room an unbounded number is invented in was found to be misnamed - the second is a width above the first, not a ceiling - and is now `generation.roomAboveIt` | The eighty-odd numbers that were decided during development — how many requests in flight, how long a string, how deep a body, how much of a reply is kept — stop being recompile-only. A person running against a fragile API turns the concurrency down in one line; an experiment turns a behaviour off in one environment variable; and neither touches the plan, which is about the API rather than about the tool |
| 11.2 ▶ | **Every lever a switch, and the list of them.** From 11.1 on, every row of M9 and M10 ships with a boolean under `schedule.*`, `generation.*` or `sequences.*` that turns it off, and a documented page lists the switches, what each one turns off, and which plan variants complete the picture (the memory of observed values is a *source*, so its ablation is a plan without the `observed` line). A test checks that every switch the page names exists and every switch that exists is named | An ablation is a campaign with one line changed, and the paper's Table of what each idea is worth can be produced by a script rather than by a branch per variant |

### Notes

**11.1 — why not the plan file.** The plan says where a run's values come from and which operations
it may touch; it travels with an API, and a person writes one per API. The settings say how the tool
behaves — how hard it pushes, how much it keeps, how deep it goes — and they travel with a machine or
an experiment. One file for both would make every plan carry numbers that have nothing to do with the
API in it, and every experiment rewrite a plan to change a concurrency. ADR-0025 argues it out and
weighs the alternatives: system properties alone, environment alone, one command-line option per
number, and a file alone.

**11.1 — what does not move.** A constant that is a fact rather than a decision stays a constant:
`500` is a server error, a document's format has a version, a reply's status class is what HTTP
says it is. The rule for moving one is that a reasonable user or a reasonable experiment might want
a different value. The first tranche is the ones the ablation needs plus the sizes; the rest move
when the code around them is next touched, never in a sweep.

## M12 — Closing v2.0

*Goal: a version somebody can install, run, understand and cite, and that replaces `master`.*

| # | Increment | What it enables |
|---|---|---|
| 12.1 ▶ | **The command line frozen** (ADR-0015 amended). `restest version`; `--header name:value`, repeatable, for authentication material handed over out of band — the competition hands tools "any required authentication material", and a header is the shape most of it takes; `--settings`, `--set` and `--print-settings` from 11.1; **Ctrl-C leaves the summary, the report and a closed store behind, or says plainly that it could not** (3.7, moved here — the third of the answers ADR-0015 lists is the one taken); `--help` complete and in plain words for every option; the exit codes as a table in the documentation. After this row, a change to the command line is a 2.x decision | The surface a user, a script and the benchmark adapter all depend on is complete and stops moving; and a run stopped early stops costing everything it had found |
| 12.2 ▶ | **The documentation of a finished tool.** `README.md` as the front door: install, run, read a report, write a plan, write a dictionary, change a setting, the exit codes, the container image. `docs/` consolidated: the plan format, the dictionary format, the settings and their keys, the report's JSON shape, the fault catalogue as we render it. `CONTRIBUTING.md` and `docs/DESIGN.md` say what v2.0 is and what 2.x will be. Every command in every document run from a clean checkout before it is pasted | Somebody who has never seen the repository can install the tool and get a report in ten minutes, and can find out what any line of that report means without reading Java |
| 12.3 ▶ 🛑 | **The user manual.** Written once, in Markdown under `docs/manual/`, and built to both HTML and PDF from that one source by a script in the repository, so that the format decision is about what is *published*, not about what is written. Chapters: what the tool is for, install, the first run, reading the report, plans, dictionaries, settings, the container image, the exit codes, troubleshooting, and a glossary. The one-source-two-outputs approach was approved on 22 September; 🛑 **which output is linked from the README and the release — HTML, PDF, or both — is the maintainer's decision, due 2 October** | A manual a person reads from the beginning, rather than documentation a person searches |
| 12.4 ▶ 🛑 | **v2.0.0 tagged and submitted.** The tag is on the commit 8.6 measured. **What the competition receives is the harness repository** — the tool's source at that commit and the benchmark-compliant `Dockerfile` that wraps 7.2a's image in the loop the benchmark expects — made public and checked with the benchmark's own compliance tooling before the freeze, not after; 8.6 runs that image and no other, so the artefact submitted is the artefact rehearsed. The submission is made on 8 October. If 12.3 is not merged by then, `v2.0.0-rc.1` is tagged and submitted instead, `v2.0.0` follows when the manual lands, and a check in the pull request that lands it shows the two commits differ in documentation files only — approved on 22 September. 🛑 The tag itself is a supervision point | The competition receives exactly what is published, under exactly the version the paper will cite |
| 12.5 ▶ 🛑 | **`master` replaced.** RESTest 1.x kept on a `v1.x` branch and under its existing tags, with its README pointing here; `v2` merged into `master`; `master` the default branch; the CI badge, the harness repository's pin and every link in the documentation moved; `v2` left in place until the harness repository has repinned, then deleted. From here on, work targets `master` | The rewrite is the tool: `git clone` gets v2.0, and 1.x is history that is still there |

### Notes

**12.1 — `--header` is a user's option, not a benchmark's.** 2.6 — authentication inferred from
the document's `securitySchemes` — waits for 2.1, because the benchmark's proxy signs the tool in
where an API needs it and nothing measured in 2027 needs the inference. A header a person can pass
is different: it is the smallest honest answer to "my API wants a key", it is what every HTTP client
offers, and it is how authentication material handed over out of band reaches the tool without the
tool knowing what it is.

**12.3 — one source, two outputs.** Writing the manual twice would be the one way to make the
format decision expensive, so it is not written twice. Markdown is the source; a script produces the
HTML — a handful of pages with one stylesheet, no generator with its own configuration language —
and the PDF, through a converter the build can fetch. The maintainer decides which output is linked
from the README and the release; the other costs nothing to keep producing.

**12.4 — the recommendation, if it comes to it.** The competition takes a commit and a Dockerfile,
and does not care what the tag is called. The paper cites v2.0.0. If the manual is late, tagging an
`rc` for the submission and `v2.0.0` for the manual keeps both statements true — *the version
submitted is 2.0* and *2.0 has a manual* — at the price of a check nobody will find hard: the diff
between the two tags touches nothing under `src/`. Tagging `v2.0.0` without the manual and shipping
the manual in 2.0.1 would be the other honest answer, and it is the maintainer's to give.

**12.5 — what is not done to `master`.** Nothing is force-pushed and nothing is rewritten: 1.x's
history stays reachable on its branch and its tags, and `master` gains v2's commits on top of it by
an ordinary merge. Anybody with a 1.x checkout finds it where they left it.

## M8 — Evaluation

Campaigns run from the harness repository ([ADR-0011](docs/adr/0011-evaluation-harness.md)), which
pins the benchmark's commit and the tool's; nothing here builds against it or names it outside the
documents that explain the relationship. **Machine time is the constraint**: the competition's
protocol is one hour per API, five runs, eight cores and sixteen gigabytes reserved, one run at a
time — twenty-five hours for the five known APIs — so campaigns are sized to the question they
answer, and each row below names what is written while it runs.

| # | Increment | What it enables |
|---|---|---|
| 8.3 ▶ | **The baseline is an ablation over where values come from.** The tip of `v2` after 2.5b, twenty minutes per run, one run, three plans: **declared and invented** — enumerations, samples and defaults from the document, plus invented values, and nothing with a memory; **plus observed** — the plan RESTest ships, which adds what the API itself has returned; **plus dictionaries** — the shipped plan with a hand-written list of values per API passed with `--dictionary`. The first two on the eleven APIs of the 2026 edition, the third on the 2027 five, since a dictionary has to be written per API. Every variant is a plan file or a command-line flag in the harness repository; nothing here changes. About nine hours, overnight. **Runs alongside 11.1** | What each source is worth is known before any lever is built on top of it, and the third plan gives the ceiling: how good the tool is when somebody who knows the API hands it the values — which the competition will not do |
| 8.4 ▶ | **Screening M9.** The 2027 five, twenty minutes each, one run: the full tool, then each M9 switch off in turn. About ten hours, overnight after 9.4 merges. **Runs alongside 10.1.** A lever that does not move its measurement is switched off in the shipped settings and stays in the code | What each reach lever is worth is known before the break levers are built on top of it, and a lever that costs more than it earns is found before the dress rehearsal |
| 8.5 ▶ | **Screening M10.** The same shape for the M10 switches, counting distinct 5XX messages and branch coverage. About eight hours, overnight after 10.3 merges. **Runs alongside 12.1 and 12.2** | The same, for the break levers |
| 8.6 ▶ 🛑 | **The dress rehearsal.** The competition's protocol exactly: the five known APIs, one hour, five runs, eight cores and sixteen gigabytes, the benchmark-compliant image from the harness repository, built from the frozen commit on top of 7.2a's image — the artefact 12.4 submits — started at noon on 6 October. Twenty-five hours. **Runs alongside 12.3 only** — nothing that changes behaviour is written while it runs. 🛑 Its results are read on 8 October; the only change they may cause is a fix for a run that failed outright, re-measured on that API alone for one hour before the tag | The number the tool will post in the competition is known, with its variance, before the tool is submitted — and the image the competition receives is one that has already run for twenty-five hours |
| 8.1 ⏭ | **The field**, after submission, at the 2026 edition's own scale so that its published table is the comparison. RESTest 2.0 on the eleven APIs the 2026 edition ran on, one hour, five runs: fifty-five hours. The 2026 report gives every other tool's numbers on those APIs at that budget, so they are not re-run here; RESTest 1.x alone is re-run on the same machine, three runs, as the calibration point between the organisers' hardware and ours — about thirty-three hours. 12–25 October | The table that goes in the paper: RESTest 2.0 beside the whole 2026 field on the APIs and budget that field was measured on, with the hardware difference bounded by a tool that appears in both columns |
| 8.2 ⏭ 🛑 | **The ablation and the replication package**, after submission, at twenty minutes per run like 8.3. Two axes. *Sources*, 8.3's three plans again on the final tool; *levers*, by group rather than one switch at a time: everything on; reach off; break off; hygiene off; everything off. Eight variants — the shipped plan with everything on is in both axes — on the 2027 five, five runs each: about sixty-five hours, 26 October – 1 November. The harness repository public with every campaign's pinned commits and raw data, so a reviewer reproduces any number in the paper from two commands | Every claim in the paper is a measurement somebody else can repeat, and the paper says what each idea is worth rather than that the whole is good |

### Notes

**8.3 — why eleven and not five.** The undisclosed half of the benchmark is APIs "never used in
previous studies", so the six from 2026 that are not in 2027 are not candidates for it. They are the
next best thing: real APIs the tool has never been pointed at, with adapters that already work. A
lever that helps on the five and hurts on the six is a lever tuned to five documents, and 8.3 is
where that would show.

**8.3 — the three plans, and why the third is a ceiling rather than a baseline.** The first two
differ by one line: the `observed` source in the shipped plan's weighted group, which is the only
source with a memory (ADR-0021 §6). Taking it out is what ADR-0023 built the plan file for, so the
first variant is a file the harness repository holds and the second is the tool with no arguments.
The third adds lists of values written for each API by hand — or by a capable model, working from
the document and from a look at the running API, with the model, the prompt and the date written
into the campaign's README, because a number nobody can reproduce is not a number. It is the most
favourable case, and **it is not the competition's case**: there, the documents of five APIs are
unseen until the container starts, and the most a submission could do is have the adapter generate
a dictionary inside the hour with a small model running on the benchmark's machine, charged to the
budget like everything else (ADR-0017, ADR-0024 §4). Whether that is worth doing is a decision for
the harness repository, taken on the gap 8.3 measures between the second plan and the third — and
nothing in this repository would change for it: the dictionary is a file in a published format
handed over with `--dictionary`, and the tool neither knows nor cares who wrote it.

**8.3, 8.4, 8.5 — twenty minutes, on purpose.** An ablation ranks variants; it does not post a
number. The area under the curve is decided in the first minutes of an hour and operations covered
plateaus early, so twenty minutes per run keeps a whole ablation to one night, which is what lets
development continue at the same pace. The hour is for the campaigns that are compared with
somebody else's — 8.6 and 8.1.

**8.6 — the one campaign nothing is allowed to interrupt.** Twenty-five hours on the machine the
tool is built on, started with two working days left. If it slips a day, the submission slips to the
deadline itself, which the calendar was drawn to avoid. The freeze on 6 October is what protects it,
and the freeze is not negotiable for anything that changes a request.

**8.1 and 8.2 — after the submission, not before.** Neither changes what is submitted, both take
days of machine time, and the paper is due eight weeks after the tool. Running them first would cost
the calendar its margin for nothing the competition sees.

**8.1 — why the hour, and why the eleven.** The 2026 report is the one table of published numbers
for the tools we are compared with, and it was measured on eleven APIs at one hour. Matching its
scale is what makes the comparison a comparison; a shorter run or a different set would leave every
row with a footnote. The report averaged ten runs where 8.1 averages five, and ran on the
organisers' machine where 8.1 runs on ours — the re-run of RESTest 1.x is there so that the paper
can say by how much the machines differ, with a number.

**8.2 — the sources are an axis, not a footnote.** 8.3 will have measured them on the tool as it was
in September; the paper needs them on the tool as it shipped, because the levers of M9 and M10
change what a memory is worth — a producer sent on purpose is a different source of identifiers
from one remembered by luck.

---

## After v2.0

Nothing below is in the competition version, with one exception marked ▶: **7.2a**, the container
image and release on tag, which the freeze needs and which sits in M7 because that is where its
siblings are. The other rows keep their numbers and their notes — the notes are evidence and
arguments that still hold — and are taken after 12.5 in the order M3, M4, M5, M6, M7, unless the
results of 8.1 and 8.2 say otherwise. Rows marked → have had a narrow version
taken into M9, M10 or M12; what the arrow leaves behind is still here, still numbered, still owed.

## M3 — Oracles, faults and reporting

| # | Increment | What it enables |
|---|---|---|
| 3.1 ⏭ | WFC catalogue, first tranche: status-code conformance, content type, response headers, negative-data rejection, positive-data acceptance, missing required header, unsupported method. The last two read the **intent** 10.1 records | Many more kinds of bug detected |
| 3.1b → [10.1](#m10--break) | **Deliberate violations, as mutations of requests the API accepted** (ADR-0013 §4). The generator half — the bounded index of accepted test cases, the operators, the intent on the test case, 2.3's boundary walk — went to 10.1. What is left here is nothing: the oracles that read the intent are 3.1's | — |
| 3.2 ⏭ | HTTP-semantics and REST-design oracles (WFC 900–909 and 950–965) | Protocol-level bugs nobody else on our side detects |
| 3.3 ⏭ | `CorpusOracle` interface and `restest recheck <run>` | Re-examine a finished run with new oracles, offline, no API calls |
| 3.4 ⏭ | Per-operation oracle configuration + published JSON Schema for the config file | False positives silenced per operation instead of the tool being switched off |
| 3.5 ⏭ | Reports: HTML, JUnit XML, HAR, NDJSON; JUnit 5 + REST-Assured code export; `restest explain`; `restest replay`. Every format renders both classifications of a fault — by catalogue number and by the class of status code that carried it (ADR-0016). Plus the "how to add an oracle, a provider, a report" guide | Results usable in CI, in an IDE, and by a human |
| 3.6 ⏭ | Replies that no rule could judge counted, and said out loud in the summary, the JSON report and the exit code | A clean bill of health stops being ambiguous: a run that could not check something says so, instead of saying nothing was wrong |
| 3.7 → [12.1](#m12--closing-v20) | What a run writes when it is cut short. Taken into the frozen command line, because a version that replaces `master` cannot lose a run to Ctrl-C | — |

### Notes

**3.1b — why it sat in M3 although the code is generator-side.** It was numbered after 3.1 rather
than given a number of its own because that was the order it had to be taken in: 3.1 supplies the two
oracles that make a broken request worth sending at all — negative data must be refused, positive
data must be accepted. Built before them, every deliberate violation would earn a 4XX that nothing
reads and no finding anybody could act on. *10.1 takes it anyway, and its notes say why: for the
competition the 5XX a broken request sometimes earns is the finding, and that oracle exists.*

**3.1b — the two debts it pays.** The **intent** of ADR-0013 §3 was assigned to M1.6 and never
built; M2.2 and M2.7a each left it out again for the same reason, that no oracle reads it — so 10.1
is the first increment where it is not a mechanism waiting for a consumer. And **M2.3's boundary
walk** was deferred here: §4 says stepping outside a documented bound is a change to a request the
API already accepted, so the walk needs the memory of accepted requests that 10.1 builds.

**3.1b — the one promise that changes shape**, and ADR-0013 §7 already says how. A strategy with a
memory is not reproduced from the seed — what gets mutated depends on what the API answered and when
— so a run using it is reproduced by replaying the stored requests instead. The seed keeps its other
three jobs: deterministic tests, reproducing a failure that happens before any request exists, and
the fixed workload 6.2's overhead test compares commits against.

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
| 4.1 → [9.2](#m9--reach), rest ⏭ | Operation Dependency Graph inferred from names, types and schemas. 9.2 took the rule for path parameters; the graph over every property, the similarity score, the synonym table as versioned data, and the measurement against word vectors that ends in an ADR (ADR-0017 item 3) are still here | The tool knows `POST /pets` must precede `GET /pets/{id}` for every kind of parameter, not only the ones in a path |
| 4.2 → [9.3](#m9--reach), rest ⏭ | Runtime resource pool and value-source selection. The pool arrived at 2.5b, the producer-then-consumer choice at 9.3; what is left is choosing among 4.1's candidates, and whether that choice may learn from what the API answered is ADR-0017's second open question | Identifiers from real responses get reused for every parameter the graph can reach |
| 4.3 ⏭ | Declared OpenAPI `links` consumed when present | Free accuracy on the few specifications that declare them |
| 4.4 → [9.3](#m9--reach), rest ⏭ | CRUD lifecycle model and sequence generation. The two-step sequence went to 9.3 and the sequence operators to 10.3; the lifecycle as a model, with sequences longer than two, is still here | Create-read-update-delete flows are exercised end to end |
| 4.5 ⏭ | Stateful oracles: use-after-free, resource availability, failed update must not change, update idempotency. 10.3 records enough on each sequence for these to judge it offline | Bugs that only appear across several requests, *named* rather than only counted |
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
table of word vectors, and record the threshold and the outcome. *9.2 is the first two of those
ideas — best few kept, gate on type and format — applied to paths alone.*

**4.2 — the resource pool arrived at 2.5b, the sequence at 9.3, and what is left here is the
choosing.** The row read "runtime resource pool and value-source selection", and ADR-0021 brought
the pool forward: bodies cannot be built well without the memory of what the API returned, and that
memory is one listener and two dictionary keyings. What stays here is the half that needs 4.1's
graph: receiving several candidates and choosing among them. Whether that choice may be scored by
what the API answered is one of [ADR-0017's open questions](#the-three-open-questions), because it
is an online estimate of whether a request will be accepted. 4.2 also has to reconcile the
candidates with ADR-0013's rule that a sequence creates what it needs rather than borrowing an
identifier — 9.3 builds that rule, so the reconciliation has something to be reconciled with.

## M5 — IDL and constraint-based generation

Entirely after v2.0. The competition's APIs declare no inter-parameter dependencies, and the
constraint-based generator's differentiator — a valid request where the dependencies are subtle, and
a deliberate violation of one — is judged by oracles the benchmark does not run. `restest-idl` ships
in v2.0 as the module descriptor it is today, and the documentation says so.

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
| 6.2 ⏭ 🛑 | Idle-time reporting and the per-request overhead regression test wired into CI. The reporting exists since 1.3 and has been measured by the benchmark's clock at 1.9; the regression test is what is owed | We can prove the 2026 failure mode cannot recur |

## M7 — Packaging and distribution

| # | Increment | What it enables |
|---|---|---|
| 7.1 ⏭ | Maven Central publication through the Central Portal | `restest-core` usable as a dependency |
| 7.2a ▶ | **A container image and a GitHub Release on every tag.** A `Dockerfile` of the tool's own in this repository — the distribution on a Java 21 runtime image, `restest` as the entry point, nothing that knows any benchmark's conventions — published to GitHub's container registry on every tag beside a distribution archive with the launcher, by JReleaser. The smoke job runs the published image, not only the build | `docker run ghcr.io/isa-group/restest run <spec> --url <base>` works the day the tag is pushed, and the image the competition receives is the image users get |
| 7.2b ⏭ | Homebrew, SDKMAN, jbang | `brew install restest` |
| 7.3 ⏭ 🛑 | GraalVM native binary with an executing smoke test; GitHub Action; documentation site | Sub-100 ms startup, no Java needed, usable in anyone's CI |

### Notes

**7.2a — two Dockerfiles, on purpose.** The harness repository has one, which builds the tool from a
commit and wraps it in the loop the benchmark expects; that one is what the competition asks for and
what 12.4 submits. This one is the tool's own: it knows nothing about being measured, and it is what
a user pulls. From 7.2a the harness's Dockerfile starts from this image rather than building the tool
again, so that the image the competition runs and the image a user pulls are the same bytes with a
loop around them — and 8.6 rehearses with the wrapped one.

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
approval; the first was asked for by this plan and approved on 22 September 2026, in its narrow form only.

- **Scheduling.** The winner of the 2026 competition rewards its choice of *operation* for errors
  while rewarding its choice of parameters and values for success — go where it breaks, send
  requests that work — and weighted sampling over per-operation counters would do the same job with
  no learning and no hyper-parameters. At its narrowest it is hygiene: stop spending budget on
  operations that answer nothing but 405 or 401. **That narrowest version is [9.4](#m9--reach)**,
  asked for at the replan of 22 September and approved the same day; the reward-shaped version
  stays here.
- **Predicting acceptance.** Whether M4.2 may score dependency candidates by what the API answered,
  which also costs the seed reproducibility ADR-0013 §7 promises.
- **Error messages.** Whether a warm-up may read the *text* of an error to learn which parameter was
  wrong, as opposed to reading its status code, which is ordinary scheduling.
