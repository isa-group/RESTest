# RESTest 2.0 — roadmap

**v2.0 is the version submitted to the [2027 REST League](https://github.com/SeUniVr/RestLeague/blob/main/2027/README.md).**
Tools are due on **9 October 2026**, results come on 13 November, and the solution paper is due on
4 December. Everything below is ordered by that calendar: what moves the competition's measurements
comes first, what closes the release comes next, and what does neither waits for 2.1. The reasoning,
and what was set aside to get there, is [ADR-0024](docs/adr/0024-the-competition-version.md).

One increment = one branch = one pull request into `v2`. Take them in [the order of work](#the-order-of-work),
not in numerical order: the numbers are names, kept stable so that earlier pull requests and ADRs
still read true, and the milestones were numbered before the plan was turned round. 86 increments in
14 milestones: 42 delivered, 2 measured and not merged, 9 more in v2.0, 33 after it.

Design rationale: [`docs/DESIGN.md`](docs/DESIGN.md). Decisions: [`docs/adr/`](docs/adr/).

## How to read this file

| Mark | Meaning |
|---|---|
| ✅ | Delivered, beside the pull request that delivered it. An increment is marked in its own pull request, so the table and the branch history never drift apart |
| ▶ | In v2.0. Taken in the order of work below |
| ⏭ | After v2.0. The row stays, numbered as it was, and is not started before the competition version ships |
| 🛑 | Supervision point. Stop there and wait for review instead of starting the next increment |
| → | The row moved. It names where the work now lives, and is no longer an increment of its own |
| ✗ | Measured and not merged. The row was built and measured by its milestone's own rule, and left out because no number it is judged by improved. It names the pull request that recorded why, and its notes say where the code is kept. One row, 9.4, carries the mark without having been built: it was set aside on what recorded runs showed, before any code, and its notes say why that is less than its milestone's rule asks |

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
| M9 | Reach — every operation the API will answer, answered early | all but 9.3, measured and not merged, and 9.4, set aside before it was built; 9.5–9.9 added on 2 October | 5 / 9 |
| M10 | Break — more distinct server failures | all | 3 / 7 |
| M11 | Settings and API keys — every number somebody decided, somewhere one can change it; and the key an API asks for, sent where its document says | all | 3 / 3 ✅ |
| M8 | Evaluation | 8.3–8.6 before submission; 8.1 and 8.2 after it, for the paper | 2 / 6 |
| M12 | Closing v2.0 | all | 3 / 7 |
| M7 | Packaging and distribution | 7.2a only | 0 / 4 |
| M13 | Safeguards — an API that refuses the run, asks it to slow down or stops answering is not flooded | none | 0 / 4 |
| M3 | Oracles, faults and reporting | none; 3.1b's generator half moved to 10.1, 3.7 to 12.1b | 0 / 7 |
| M4 | Stateful testing | none; the narrow version of 4.1 moved to 9.2 and 4.4's sequence operators to 10.3; the narrow versions of 4.2 and 4.4 were tried at 9.3, measured and not merged | 0 / 6 |
| M5 | IDL and constraint-based generation | none | 0 / 5 |
| M6 | Live updates and performance | none | 0 / 2 |

## What the competition measures, and which rows move it

The 2027 edition scores four things per API, one hour per run, five runs averaged, every score
normalised across tools before it is added up. The **Effectiveness** ranking adds the first three;
the **Efficiency** ranking adds the area under the curve of the same three, which rewards a tool for
getting there *early*; the **Fault detection** ranking is the first alone.

| Measured | How | What moves it here |
|---|---|---|
| Unique server failures | Distinct 5XX replies, told apart by their error message | Requests that break things in *different* ways: mutations of accepted requests (10.1), bodies of the wrong shape (10.2), series over things the run created — read after delete, delete twice, create twice (10.3). The tool's own oracles play no part: the benchmark counts the 5XX itself |
| Operations covered | Operations that answered 2XX at least once | Identifiers that exist (2.5b ✅, 9.2 ✅), the required-only request drawn often (2.9), a memory that keeps what operations ask for (9.5), formats the name implies (9.6), values the API accepted sent on (9.7), gaps the memory did not recognise (9.8), and HAL's reserved properties left out of bodies (9.9) |
| Code coverage | Methods, statements and branches the API executed | Everything above, plus variety: values, optional parameters and body properties that change from request to request (2.5a ✅, 2.7c ✅, 10.2) |
| Area under each curve | The same three, integrated over the hour | An opening lap that sends every operation its best request in the first seconds (9.1), and 0% idle time (✅, measured by the benchmark's own clock at 1.9). The `Accept` header ADR-0017 asked for ships since 2.5a |

One thing the call for participation says twice, differently: the Efficiency ranking is defined as
the three areas under the curve, and the badge for winning it is described as coverage "with the
lowest resource consumption footprint". This plan takes the definition rather than the description
— nothing here is optimised for CPU or memory — but 11.1 makes the concurrency range a setting so
the shipped default can be changed in a line if the organisers say the badge reads the other way,
and 8.6 records the container's CPU and memory alongside its results so the question can be
answered with a number. Asking the organisers which reading holds is on the maintainer.

A second thing the call promises without saying how: every tool is handed the document, the address
and "any required authentication material (e.g., API keys)". The benchmark shows one way. Where one
of the five known APIs needs signing in — flight-search wants a bearer token, gestao-hospital a
session cookie — a script the benchmark runs in its own proxy registers a user, signs in, and adds
the credential to every request on its way through, so the tool is handed nothing and needs
nothing. Whether the five undisclosed APIs are signed in the same way, or hand the tool a key, is
not known; **11.3** is there for the second case, and asking the organisers which it is is on the
maintainer too.

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
| Sun 27 Sep | M9 measured | 2.9, 9.1 and 9.2 merged, 9.3 measured and not merged, 9.4 set aside on recorded runs; 8.4 running overnight |
| Thu 1 Oct, night | Variant measured | The shipped plan and the plan with dictionaries, one hour and one run each, on the eleven APIs of the 2026 edition, at the tip of `v2` — the commit the freeze will most likely take. Which output of the manual to publish decided the same day (12.3) |
| Fri 2 Oct | M10 measured; variant chosen; five rows added | 10.1–10.3 and 11.2 merged, 8.5 read. From the night's campaign, the variant the competition image carries, and whether a local model writes dictionaries in it. From the same campaigns, the five rows 9.5–9.9, added by the maintainer |
| Sun 4 Oct, night | 9.5–9.9 measured | Each merged with its own measurement, or measured and not merged. If time allows, one twenty-minute campaign on sixteen APIs that night, the night before the freeze: every lever on, against 9.6, 9.7, 9.8 — both its switches — and 9.9 each switched off. 9.5 has no switch, and is read against the campaign of 1 October |
| When the competition image is ready | **Behaviour freeze**; 8.6 starts | The tool: 2.9, M9 but 9.3 and 9.4, M10, M11, 12.1a, 12.1b and 12.1c merged and measured — of 9.5–9.9, each one merged, or measured and not merged, by then; one not ready by then is left out, as the image is. The competition image: built from that commit with whatever the variant adds, and checked with the benchmark's own tooling. From here neither changes. 8.6 starts on a machine nothing else is built on and runs alongside 7.2a, 12.2, 12.3 and the clean-up for `master`. As early as the image allows, and no later than noon on 6 October, the date it had before 1 October: what the image does not have ready by then is left out of it (the maintainer, 1 October) |
| When 8.6 ends | 8.6 read | Read at once, not on the day of the submission; a run that failed outright is fixed and re-measured on that API alone for one hour |
| Thu 8 Oct | **Submission**; `master` replaced | `master` replaced (12.5); v2.0.0 tagged on it (12.4); the harness repository, pinned to the commit 8.6 rehearsed, submitted one day before the deadline |
| Fri 9 Oct | Deadline | Anywhere on Earth. Nothing is submitted on this day by plan |
| 12 Oct – 1 Nov | The paper's numbers | 8.1 and 8.2 on the machine; 2.1 work in the tree |
| Fri 13 Nov | Results | — |
| Fri 4 Dec | Solution paper | Four pages, IEEE format; the tables come from 8.1 and 8.2 |

Seventeen days from acceptance to submission. M0–M2 delivered twenty-five increments in twelve
days, so the twenty-one ▶ rows fit the calendar with the campaigns running while the code is written,
and with nothing added. **An increment that grows is split, and the half that moves no measurement
goes to 2.1 — never the other way round.** *One row was added since: 11.3, API keys, by the
maintainer on 29 September, a week before the freeze. Its notes say why it is in v2.0 and how it is
kept small.* *Five more were added by the maintainer on 2 October, 9.5 to 9.9, from what the campaigns
of 1-2 October found; their notes say why, and each is measured before it is merged like every row
of M9.* *Replanned by the maintainer on 1 October: 8.6 runs in parallel with what is left
rather than after it, so the freeze moves to 8.6's start; the competition image compiles the tool
itself, so it no longer waits for 7.2a; and `master` is replaced before the submission rather than
after it, with clean documents, while `v2` keeps the record. The rows say how.*

## The order of work

Machine time and desk time are two resources, and a campaign takes the machine for hours. Every
row of M8 names the rows written *while* it runs.

1. **11.1** the settings, first, so that every lever after it lands with its switch. **8.3** runs
   overnight in the meantime.
2. **2.9**, **9.1**, **9.2**, **9.3**, **9.4** — reach; 9.3 was measured and not merged, and 9.4
   set aside on recorded runs before it was built. Each row is measured on a restarted
   containerised pet-clinic before it is merged (the way 2.5b was), and **8.4** measured the
   milestone as a whole on 27-28 September, on sixteen APIs.
3. **10.1**, **10.2**, **10.3**, **11.2** — break, and the list of switches. **8.5** overnight.
4. **11.3**, **12.1a**, **12.1c**, **12.1b** — API keys, the command line frozen, a broken run
   answering as one, what a run cut short leaves behind. 11.3 goes first because it adds to the
   command line that 12.1a freezes. Fixes from 8.4 and 8.5 land here, behind a switch when they
   change behaviour.
5. **9.5**, **9.6**, **9.7**, **9.8**, **9.9** — five levers added by the maintainer on 2 October,
   in that order: 9.7 needs 9.5 under it and 9.6 to show on flight-search. Each is measured on a
   restarted containerised API before it is merged, and, if time allows, all of them together in
   one overnight campaign before the freeze. **10.4-10.7**, added the same evening, are one pull
   request beside them, measured overnight on the eleven 2026 APIs against the commit before it,
   with a third campaign that leaves out the pushing strategy.
6. **Freeze**, then **8.6** 🛑 for twenty-five hours, on a machine nothing else is built on. While
   it runs, everything that changes no request is worked on: **7.2a**, the image; **12.2**, the
   documentation; **12.3**, the manual; and the clean-up `master` receives in 12.5. *Until
   1 October, 7.2a and 12.2 came before the freeze and only 12.3 ran alongside 8.6.*
7. **12.5** 🛑 `master` replaced, then **12.4** 🛑 tag and submit, the same day.
8. After submission: **8.1**, **8.2** 🛑 for the paper, and the ⏭ rows in the order M13, M3, M4,
   M5, M6, M7 unless the results say otherwise. M13 comes first because from the day it is tagged,
   v2.0 can be pointed at anybody's API.

Three things were asked of the maintainer at the replan rather than at the row, and **all three were
approved on 22 September 2026**: **9.4** takes the narrow version of the first of ADR-0017's open
questions, which is on the deferred list (*set aside unbuilt on 27 September; see its notes*);
**12.3** writes the manual once and builds HTML and PDF from it, so that the only decision left —
which output to publish, due 2 October — is a cheap one (*taken on 1 October: the Markdown as GitHub
shows it, and a PDF; see its row*); **12.4** tags `v2.0.0-rc.1` for the submission if the manual is
late and `v2.0.0` when it lands, with a check that the two commits differ in documentation files
only.

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
| 2.6 → [11.3](#m11--settings-and-api-keys), rest ⏭ | Authentication inferred from `securitySchemes`. **API keys went to 11.3.** Still here: a bearer token, and a user name with a password, handed over the way 11.3 hands over a key; OAuth2 client credentials; a sign-in the tool performs itself — register, log in, and carry the token or cookie that comes back, which is what the benchmark's proxy does for flight-search and gestao-hospital; refreshing what expires; and several credentials at once — several users, which the access-control oracles on the deferred list would need, or several keys taken in turn, as RESTest 1.x did. The sign-in and the several users are what the authentication file of Web Fuzzing Commons describes, and four related tools already read it. 11.3's notes say how all of these fit behind the door it builds | Protected APIs stop returning 401 for everything |
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
| 9.3 ✗ [#332](https://github.com/isa-group/RESTest/pull/332) | **Make what you need** (the narrow version of 4.2 and 4.4). When a consumer needs an identifier nobody has — no reply has carried one, or every one carried has since been deleted — the scheduler sends the producer first and the consumer right after, with what came back; a two-step sequence, the second test case naming the exchange its value came from. **Built, measured and not merged**: the pairs, the memory forgetting what a 2XX `DELETE` removed, and a switch for each. On four priority APIs, restarted before every run, five seeds, a minute each, 7 pairs fired in 338,229 requests, and neither lever moved operations covered, branch coverage or distinct server failures - see the notes. The code is kept on the branch `experiment/m9-3-sequence-pairs` | Nothing a person can see, which is why it is not merged. What it taught is recorded instead: on APIs whose every kind of thing has a list, the memory is never empty, and repeating good requests adds little covered code after the first ten seconds |
| 9.4 ✗ [#333](https://github.com/isa-group/RESTest/pull/333) | **Budget hygiene** — the narrow version of the first of [ADR-0017's open questions](#the-three-open-questions), **approved by the maintainer on 22 September 2026** and taken no further. The scheduler was to keep, per operation, a count of what it answered, and an operation whose last *N* answers were all of the kinds that say *this will never work as asked* would have its share shrink towards a floor, never to nothing. **Set aside before it was built**: the maintainer ruled out of the list 404 and every other answer that depends on the requests the tool generates, which leaves 401, 405 and 501, with 403 on the line. Replayed over thirty-one recorded runs, the narrowed rule has nothing to act on in any run of four of the 2027 five, and on the fifth, flight-search, moves under 0.1% of its requests without 403 and 2.6-4.1% with it - which was not measured, see the notes. What is **not** taken either: scoring dependency candidates by what the API answered (question 2) and reading the text of an error reply (question 3) | Nothing a person can see, which is why it was not built. What it taught is recorded instead: on the APIs the tool is measured against, the answers that would say *this will never work* whatever is sent are almost never given, and the one given often - 404 - depends on what was sent |
| 9.5 ✅ [#353](https://github.com/isa-group/RESTest/pull/353) | **A memory of what the API returned that does not fill up** — added by the maintainer on 2 October, with no switch, by the maintainer's choice. A named piece of a reply is kept only when its name is one some operation of the document can be asked for: the name of a parameter, or of a body property at any depth that is not only ever returned. Things kept by their kind (9.2) and whole things kept under the name of their shape (2.5b) are kept as before. `memory.mostNames` stays, as a guard, and when it is reached the name heard of longest ago is let go instead of the new one being refused, for names, shapes and kinds alike. Which names a reply's name answers is one question asked when a value is kept, so that matching names by likeness (4.1) would change one class. **One thing was added to the row as approved**, found by its measurement and approved by the maintainer the same day: a remembered word is only offered where it is as long as the document allows, because flight-search's diagnostic page shows a password as `******` and it had become every invented password - see the notes | A value an operation asks for is kept however much else the API said first. On flight-search, the names in the replies of four actuator endpoints had filled the memory 2.9 to 4.4 seconds into a session, before any login answered, so the `refreshToken` logins return was never kept. Measured there, restarted before every run, five seeds, three minutes each, before against after: under the plan with dictionaries, whose logins are the only ones that answered 2XX, `POST /api/v1/authentication/user/refresh-token` answered 2XX on three seeds of five and logout on four, after none on any seed before; operations answered 2XX 24.8 against 23.6, branch coverage 29.6% against 27.6% on four seeds, the area under the curve up 3.9%. Under the shipped plan, 20.6 operations against 19.4 and the area up 4.4%, from an actuator operation that now gets a logging level that exists; branch coverage level |
| 9.6 ✅ [#354](https://github.com/isa-group/RESTest/pull/354) | **Formats the name or the description implies** — added by the maintainer on 2 October. Where a string declares no `format`, no closed list and no sample, invention sends, half the time, the kind its description or its name implies, and an ordinary word otherwise, at any depth of a body. The description is read first and the name after it, the order ARTE's rules follow (Alonso et al., TSE 2023): a date or time template the description writes out (`YYYY-MM-DD`, `yyyy-MM-dd'T'HH:mm:ss`, `YYYYMMDD`, `dd/MM/yyyy`) or a sample it shows gives a value written exactly that way; a standard it names gives that standard's kind - ISO 639 or BCP 47 a language tag, ISO 3166-1 a country, ISO 4217 a currency, RFC 3339, an ISO 8601 duration, an HTTP date, Unix time, E.164; "*K* code" gives a country, currency or language code when *K* is tied to the name, as `cc` is to "country code"; a description beginning "The UUID", "The email address", "URL" or "Phone number" gives that. Then the name, by the words it ends in: `…At` and `timestamp` a date and time, `birthday` and `dob` a date, `email`, `url`, `uuid`, `phone`, a test card number that passes the Luhn check for `ccNumber` and `cardNumber`, `currency`, and `language`, `lang`, `locale`, `motherTongue` a language tag. A spelling rule and the lengths the document states still hold, and what they refuse is built from them as before. The table is data in one class and is kept against the fifty documents of the corpus, not the competition's APIs: a rule stays only if it agrees nine times in ten where a document declares what it wants, which left out `date` and `country` as names on their own, `cc` and "ISO 8601" alone; the language names are kept below that bar by the maintainer's choice, for the languages an API asks for by name alone. `generation.impliedFormats`, the switch, renamed from the approved `formatsFromNames` because descriptions are read too, and `generation.impliedFormatChance`. **What differs from the row as approved**, each the maintainer's on 2 October: the description is read as well as the name, dates and times in the forms it writes out included; the kind is sent half the time inside invention rather than always; a declared pattern no longer rules a place out, the value only has to match it; the table was measured on the corpus, which took out `cc`, `date` and `country` as names on their own; and the switch was renamed | Operations that refuse a word of random letters where an e-mail address, a card or a code was meant answer instead. Measured on market and flight-search, restarted before every run, five seeds, three minutes each, on against off: market answered 2XX on 12.0 operations against 10.6 and its payments on every seed against none, branch coverage 5.6% against 3.6%, the area up 10% and 60 distinct server failures against 48, better on every seed by every measure; flight-search's registration answered 14 to 24 times a run against once, which is what 9.7 needs to log in. Over the fifty documents of the corpus, where a document declares what a place is, the rules agree with it at 479 places of 488, and they reach 102 request places that declare nothing that settles them |
| 9.7 ✅ [#355](https://github.com/isa-group/RESTest/pull/355) | **What the API accepted, offered to other operations** — added by the maintainer on 2 October. A request the API answers 2XX leaves behind the values it carried — its path, query, header and cookie parameters, and its body's properties at any depth — each under its exact name, beside what replies carried and bounded with them: twenty under one name, and 9.5's guard on names. Which requests teach is the rule `AcceptedRequests` already used for 10.1, now shared: not a change to an accepted request (10.1, 10.2), not built to push at the API, not a step of a series (10.3), and not a deletion, left out whole by the maintainer's choice. A value is offered by the `observed` source to the other operations and not back to its own, says it was sent in a request the API accepted, and is never a key handed over, which a test sends through the door keys go in by to an API that repeats it back. `memory.rememberAcceptedRequests`. **Not taken**: names matched by likeness (4.1), pairings learnt from what the API answered, and closing the cycle the measurement found, left for 2.1 by the maintainer - see the notes | A password goes from the registration that worked to a login, which no reply ever carries. Measured on six APIs, each restarted before every run, five seeds, on against off: on flight-search, operations answered 2XX 23.0 against 19.8 and branch coverage 29.6% against 26.3%, better on every seed, its login answering on every seed for the first time without a dictionary and the token it returns reaching refresh and logout (9.5); on user-management, 12 operations against 11, its creations refused as "already in use" no more often than before; on pet-clinic, kafka-rest-proxy, notebook-manager and gestao-hospital, nothing beyond the spread between seeds. The price: on flight-search, registrations refused as already made doubled, through the cycle the notes describe |
| 9.8 ▶ | **Identifier gaps the memory did not recognise** — added by the maintainer on 2 October, in two halves with a switch each. **A plural gap** — `{ids}`, `{petIds}`, `{user_ids}` — is filled with one identifier of the kind of thing the address names (`/persons/{ids}`, a person) or the gap's name names (`petIds`, a pet), the way 9.2 fills `{petId}`; `memory.pluralIdentifiers`. **A gap for a thing's name** — `{productName}`, `{featureName}`, `{configurationName}`, and `{name}` right after `/products/` — takes the `name` of a thing of that kind the API returned; `memory.namesByResource`. **Not taken**: kinds named in another language (`produto`), and lists written with commas | person-controller's `GET` and `DELETE /api/persons/{ids}` are sent the 24-character ObjectIds its own replies list, instead of invented words they answer 500 to; features-service's products, features and configurations are addressed by names that exist, where one session on 1 October was answered 5,423 times with a 500 `ObjectNotFoundException` for invented ones. Measured on person-controller and features-service, on against off |
| 9.9 ▶ | **HAL's reserved properties left out of request bodies** — added by the maintainer on 2 October. `_links` and `_embedded` are left out of every body sent: the ones the tool invents, whole things the API returned once they are cut down, and the accepted requests a change starts from. `links`, without the underscore, stays. HAL — the convention Spring HATEOAS writes, set down in draft-kelly-json-hal-11, an Internet-Draft that has expired and is widely used all the same — reserves both for the server, and makes `_links` an object keyed by the kind of link, which a document declaring it as a list gets wrong. `generation.omitHalProperties` | Bodies market refused over a property it never meant to receive are accepted. Its document declares `_links` as a list in the bodies of `PUT /customer/contacts`, `PUT /customer/cart` and `POST /register`, and it answered 406 or 500 "Expected relation name" to 4,500-5,000 requests per variant on 1-2 October; those operations answer 2XX when `_links` is left out. notebook-manager accepted `links` 18,814 times of 19,602, and it is not touched. Measured on market, on against off |

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
kept by name, and only once, at the start. 9.3's producer-then-consumer sequence was to carry one
reply to one consumer whenever a consumer needs an identifier nobody has - after a deletion, or
where only a creation makes one - with 10.3's operators built on that unit; the lap does neither.
*9.3 was measured and not merged; see its notes.* The part of 9.3's text the lap already does,
deletes after the reads and updates of the same round, came out of 9.3's row.

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

**9.3 — what was measured, and why it is not merged.** The row was built as it says, with the unit
of work ADR-0013's M9.3 amendment describes rather than the one-worker sequence planned in the notes
below, with two switches, `sequences.pairs` and `memory.forgetWhatWasDeleted`, and one rule the
measurement forced: one creation of each kind awaited at a time, because a round goes out before its
first answer is back and gestao-hospital's refused stock creations otherwise made up a fifth of a
minute's requests. It was then measured the way M9 asks - gestao-hospital, kafka-rest-proxy,
notebook-manager and pet-clinic from the benchmark's own images, restarted before every run, seeds
3, 7, 23, 41 and 99, sixty seconds, three arrangements in rotating order - and, for the first time,
by the benchmark's own measure of code: the JaCoCo report those images write every five seconds.

| API | | both on | pairs off | both off |
|---|---|---:|---:|---:|
| gestao-hospital | operations covered | 17.8 | 17.4 | 18.0 |
| | branches, of 248 | 54.4 | 55.2 | 54.6 |
| kafka-rest-proxy | operations covered | 35.2 | 35.0 | 35.4 |
| | branches, of 4,849 | 832.2 | 838.8 | 841.0 |
| notebook-manager | operations covered | 5.0 | 5.0 | 5.0 |
| | branches, of 32 | 16.0 | 16.0 | 16.0 |
| pet-clinic | operations covered | 33.0 | 33.0 | 33.0 |
| | branches, of 898 | 150.4 | 150.4 | 150.4 |

Every difference sits inside one standard deviation, and distinct server failures did not move
either. Three findings explain it, and they are the reason the row is recorded rather than dropped:

- **Pairs almost never fire**, because every kind of thing these four APIs create also has a list,
  and the list refills the memory every round. A static reading of all 52 distinct APIs in the
  corpus and the 2026 edition found one, 2026's blog, with a gap a `POST` creates for and no list
  of its kind - seven operations behind `{username}`, on an API that needs signing in.
- **Forgetting only moves what is not scored.** It raised the share of by-id requests answered 2XX
  (31.6% against 29.4% on notebook-manager, 19.3% against 18.6% on pet-clinic), and its area was
  about 3% lower on gestao-hospital and kafka-rest-proxy on four seeds of five each - not
  significant, but not a gain. Two ways it can cost: a 2XX to a `DELETE` is not always a deletion
  (kafka's `DELETE .../configs/{name}` resets a setting that still exists), and where the memory is
  thin, forgetting leaves invention, which never works for an identifier, in place of a stale value,
  which fails no more often.
- **Code coverage saturates in the first ten seconds**: by then pet-clinic has 146 of its final 150
  branches, kafka-rest-proxy 805 of 832, notebook-manager all 16 and gestao-hospital 47 of 55. The
  rest of the minute adds nothing to 4% on the first three and 17% on gestao-hospital, and between
  half the branches (notebook-manager) and five sixths (kafka-rest-proxy, pet-clinic) are never
  reached. More of the same requests, better filled, does not reach them. The same data argued
  against an idea raised while reading it, and it was not built - always filling an identifier gap
  from the memory when it has a candidate, rather than two times in three by invention: it would
  turn failing requests into accepted ones, which is not what is scored, and the memory's values are
  already sent thousands of times a run, so any branch they reach is already reached.

What M10 does - bodies of the wrong shape, values that break one rule - and sequences of states
rather than of identifiers, such as gestao-hospital's check-out after a check-in, are where the
unreached code is more likely to be. The two decisions ADR-0013 left to M4 stay untaken, and
ADR-0013's M9.3 amendment says why.

**9.3 — one sequence shape, not a lifecycle model** (as planned; the row was measured and not
merged). 4.4 is create-read-update-delete as a model with its own oracles in 4.5. 9.3 was *producer,
then consumer*, chosen because it is the shape every missing identifier has, and because 10.3 can
build its operators on it — delete then read is producer, consumer, consumer. The lifecycle model,
and the oracles that need it, wait.

**9.3 — the two decisions ADR-0013 left to M4, as they were built and not merged.** ADR-0013
deferred the *unit of work* of a sequence and *interference* between concurrent sequences to M4, and
M4 is now after v2.0, so 9.3 was to take both. This is what was planned; what was built differs in
one respect, recorded in ADR-0013's M9.3 amendment, and since the row was not merged both decisions
stay untaken. The unit of work was to be the sequence: one worker sends the producer, reads its
reply, and sends the consumer with what came back. As built, the loop hands the producer's answer to
the scheduler and the scheduler has the consumer sent next, so that generation stays on one thread.
Either way the consumer's value comes from *its own* producer's reply rather than from the memory of
observed values — which is ADR-0013's rule, *a sequence creates what it needs*, taken literally.
Interference was to be accepted rather than prevented: with the engine's concurrency on, another
unit may delete or recreate the same resource between the two steps, so a 404 on the consumer is not
by itself attributable. That costs nothing the competition scores — the benchmark counts 2XX and
5XX, not our attribution — and it is exactly why 4.5's stateful oracles wait: they need the
attribution, and the attribution needs a decision about concurrency that 9.3 did not take. 9.2 is
not a new borrowing: the plan's `observed` source has filled parameters from what other requests
returned since 2.5b, under ADR-0021's amendment, and 9.2 widens its keying rather than its licence.

**9.4 — the line it stands on.** `docs/DESIGN.md` defers "search-based or reinforcement-learning
scheduling", and ADR-0017 argues that the narrowest useful version of steering by counters is
hygiene rather than learning: no constant that needs a paper to justify it, nothing that has to be
calibrated. It is still a run learning from what it has seen, which is why it is asked for rather
than assumed, and why the two neighbouring questions are named as *not* taken. It costs what 2.5b
already cost: a run using it is reproduced by replaying the stored requests, not from the seed.

**9.4 — the seam it would have proved.** `docs/DESIGN.md` lists `FeedbackListener` — observes
every interaction, may return scheduling hints — among the extension points, and ADR-0017 noticed
that no row delivered it. This one was to: the counters a listener on the event stream, the hint a
weight per operation, the seam proved by being used rather than by a throwaway. Since 9.4 was not
built, the seam has no implementation and no row, and `docs/DESIGN.md` says so.

**9.4 — what was measured, and why it was not built.** The row was approved with 404 in its list,
and the plan written for it kept it there. Asked what each code meant, the maintainer ruled out 404
and every other answer that depends on the requests the tool generates, before any code: a 404 to
`GET /owners/{ownerId}` says the identifier was wrong, which is the tool's doing, and setting the
operation aside for it punishes the operation for the generator's guess. What is left is what should
say the same thing whatever is sent - 401, no credentials; 405, the method is not there; 501, not
implemented - with 403 on the line, since whether something is forbidden can depend on which thing
was asked for.

Before building it, the rule was replayed over what thirty-one runs had recorded: every reply in
the order it arrived, each request matched back to its operation through its API's own document, an
operation set aside once its last ten answers were all in the list and sent a tenth as often from
then on, and every request it would have held back counted as moved to the others. The runs are
8.3's, twenty minutes each at `da17d2ba` - the 2027 five in campaigns of their own, one for each of
its three plans, and the eleven APIs of the 2026 edition under the shipped plan, which counts the
2027 five a fourth time - and the 2027 five again for thirty seconds at `cc415925`, seed 7.

| API | runs | 401, 403, 405, 501 | 401, 405, 501 | with 404, for comparison |
|---|---|---:|---:|---:|
| flight-search | 2027 plans ×3, 20 min | 5 operations, 3.4-4.0% | at most 1 operation, under 0.1% | 19 operations, 22-24% |
| flight-search | 2026 edition, 20 min | 4 operations, 4.1% | none | 18 operations, 24.1% |
| flight-search | `cc415925`, 30 s | 3 operations, 2.6% | none | 16 operations, 20.0% |
| gestao-hospital, kafka-rest-proxy, notebook-manager, pet-clinic | all five runs each | none | none | up to 52.6% (kafka-rest-proxy) |
| project-tracking-system | 2026 edition, 20 min | 13 operations, 2.4% | 13 operations, 2.4% | 13 operations, 2.4% |
| blog, erc20, features-service, market, person-controller | 2026 edition, 20 min | none | none | up to 5.5% (blog) |

No API answered 501 once, and only project-tracking-system answered 405. So for the competition's
five known APIs the narrowed rule has nothing to act on in twenty-three runs of twenty-five without
403, and in the other two, both flight-search's under 8.3's plans, it sets one operation aside and
moves under 0.1% of the requests: switching it on or off would send the same requests, or as good
as, which is the argument 2.9 made about pet-clinic. With 403 in the list, flight-search has
2.6-4.1% of its requests moved in every one of its five runs, and **that was not measured**: whether
it would have changed operations covered, branches or distinct server failures is not known, and
nothing here claims it would not.

The recordings also show that even the narrowed list is not what it was meant to be. On
project-tracking-system every one of the thirteen operations the rule would set aside for 405 also
answers 200 or 400 to other requests - `POST /app/api/locations` answered 405 602 times and 200 404
times - so there 405 depends on what is sent too, and nine of the thirteen had already answered 2XX.
The same is true of the 401s and 403s on flight-search, given by operations that also answer 400 and
404. A list of codes cannot say that an operation will never work; only the operation's answers to
different requests can, and that is a different lever from the one approved.

Building it and running the comparison M9 asks for - a restarted API, five seeds, on against off -
would have measured one API where it moves a few percent of the requests, with M10's gate five days
away. The maintainer chose to record it instead.

What would reopen it: an API where whole operations answer 401, 405 or 501 to every request while
others answer. The competition's own authentication material — which the benchmark's proxy applies
to the known APIs, and which 11.3's keys hand over wherever it comes to the tool instead — a bearer
token or a session cookie among them, given with its place (12.1a) — removes the likeliest source of 401 before this row could.

**9.4 — what is not taken, still.** The reward-shaped version - go where it breaks - stays with
the deferred list, and so do questions 2 and 3.

**9.5 to 9.9 — why five rows four days before the freeze.** The maintainer added them on
2 October, from what the campaigns of the night before found. Two twenty-minute campaigns at
`17369e00`, the shipped plan and the plan with dictionaries, two sessions each on sixteen APIs, were
read request by request, and what kept operations from answering 2XX was sorted by cause. Of the 87
operations no session covered, 49 are out of reach here whatever is sent: the API or its
environment is broken (34), it offers no way to create the state they need (8), or they want an
administrator's sign-in the benchmark's proxy does not give and only a tool carrying a login's token
would reach (7), which is 2.6. Nineteen were blocked on a value the API had returned and the tool
did not send back. And the dictionaries' gains came mostly from two kinds of knowledge a rule can
supply: the same value in two operations, and a format the name implies and the document does not
declare. 9.6 and 9.7 are those two rules; 9.8 fills two kinds of gap among the nineteen; 9.5 mends
the fault in the memory that kept flight-search's `refreshToken` out of it; and 9.9 stops a property
the server keeps for itself from getting thousands of market's requests refused. The analysis is
kept with the campaigns' results in the evaluation harness, under
`results/20261001-225903/analysis/`.

**9.5 to 9.9 — how they are measured, and where.** Each the way every row of M9 is — a restarted
containerised API, five seeds — and one that does not improve its number is not merged. On the API
where its evidence is, which for four of the APIs named in the rows — market, person-controller,
features-service and user-management — is outside the five the competition names: the maintainer
chose them on 2 October, since neither plural gaps nor `_links` occur among the five. Each row also
counts, over the fifty documents of the wider corpus, the places its rule reaches, as 9.2 did,
because a rule written from what sixteen APIs did has to be seen not to reach where it should not.
9.5 is measured on flight-search under both plans, the maintainer's choice: under the shipped one,
to see that it costs nothing, and under the plan with dictionaries, which is the only one whose
logins answered 2XX on 1-2 October — none did under the shipped plan, so there was no
`refreshToken` to keep. 9.7 then measures it again under the shipped plan, once 9.6 lets
flight-search register a real e-mail address. If time allows, one twenty-minute campaign on the
sixteen APIs, the night before the freeze, puts every lever on against 9.6, 9.7, 9.8 and 9.9 each
switched off.

**9.5 to 9.9 — why 9.5 has no switch.** The maintainer's choice, and an exception to the rule that
every lever lands with one, which ADR-0025 is amended to record when 9.5 is built. Refusing every
new name once the guard is reached was not a behaviour anybody chose: it made the first names an API
happened to send the only ones a run could remember, whatever came after them. What the exception
costs is said plainly: no settings file brings back the memory as it was before 9.5, so a campaign
run before it and one run after differ in this as well as in whatever they were run to compare.

**9.5 — what the measurement added.** The first measurement of the filter and the eviction alone
had three seeds of the shipped plan worse than before - 18 operations rather than 19, branch
coverage 25.0% rather than 26.3% - and the cause was a value the memory could now keep:
flight-search's `/actuator/configprops` shows the database's password as `"******"`, `password` is
a name its registration asks for, and inside a body the memory is asked before invention, so every
invented password became six asterisks where the document asks for eight characters at least. The
memory already offered a value only where its kind matched and where it was on a closed list the
document states; the maintainer chose on 2 October to add the lengths the document states, rather
than to refuse values made of asterisks, which would have mended only this case. The figures in the
row are the three changes together; [ADR-0021](docs/adr/0021-how-a-request-body-is-built.md)'s M9.5
amendment has the table.

**9.7 — the cycle the measurement found, left for 2.1.** A value an accepted request carried is
offered to the other operations and not back to its own, so that a creation is not asked for the
same thing twice. But a value seen again replaces what was known of it: an e-mail address goes from
the registration to the login, the login is accepted, the memory now holds the address as the
login's, and the registration is offered it again and refused as already made. On flight-search 251
of the 513 refusals of that kind in five seeds were this cycle, and the registrations that answered
fell from about twelve a run to about two. The maintainer asked the question before it was measured,
chose to measure first, and with the numbers chose to merge the row as it is: what the cycle costs
is registrations, and what the row opens - login, refresh and logout - is worth more. The fix is
recorded for 2.1: a value keeps every operation that has accepted it, replaced neither when it is
sent again nor when a reply shows it, and is offered to none of them again.

**9.5 to 9.9 — what was not taken.** Read in the same analysis and left out by the maintainer on
2 October: a date where a date and time is declared, which one operation in the corpus wants;
small whole numbers first; drawing how many optional body properties to send before which, as 2.9
does for parameters; two more strings for breaking things; signing in from a login's reply, which is
2.6; and matching names by likeness, which is 4.1.

## M10 — Break

*Goal: more distinct server failures, and the error branches of the API executed.* The benchmark
tells 5XX replies apart by their message, so what counts is not how often the API falls over but in
how many different ways — and every row here adds a different way. Measured before merging like M9,
counting distinct 5XX messages and branch coverage rather than operations covered.

| # | Increment | What it enables |
|---|---|---|
| 10.1 ✅ [#336](https://github.com/isa-group/RESTest/pull/336) | **Mutations of accepted requests** — the generator half of 3.1b, under ADR-0013 §4 ([ADR-0027](docs/adr/0027-changing-one-thing-in-an-accepted-request.md)). A strategy that says `mutates: accepted` takes a request the API answered 2XX - kept by a listener, the newest sixteen per operation, never a change, a push or a deletion - and changes exactly one thing in a parameter **or anywhere inside a JSON body**, falling back on its own sources when there is nothing to change. Eleven operators in **two families, each switchable** under a new settings group `mutation.*`: **violations**, which break what the document states - drop a required value, send it in another location (ADR-0017 item 5), the wrong type, one past a bound (2.3 returns here), off the enumeration, against the pattern, `null`, empty where forbidden, oversize beyond a stated most - and **probes**, where the document says nothing: oversize with no limit, empty where nothing forbids it. The test case gains the **intent** of §3, four values rather than three, and a record of the change naming the exchange it was made to. Shipped plan nominal 55, mutation 20, fuzzing 25; **the probes ship off**, chosen by the maintainer on the measurement. *Two things differ from the row as approved, both chosen by the maintainer on 27 September*: operators reach inside bodies, taking two of 10.2's items, and "oversize" and "the empty value" each became two operators, one per family, so that every recorded intent is true | A run asks "what happens when one thing is wrong?" of every request that worked, and reaches the failures behind an API's validation. The five 2027 APIs restarted before every run, five seeds, a minute each: pet-clinic's distinct server failures by message rose from 158.6 to 211.2, and by exception kind from 51.8 to 60.8, better on every seed by either count, the area under that curve up 24% and branch coverage up on every seed; the other four did not move. Nothing was lost - operations covered, idle time and the pairs of operation and status failing are unchanged - and the probes added nothing over the violations; 8.5 removed them |
| 10.2 ✅ [#337](https://github.com/isa-group/RESTest/pull/337) | **Bodies of the wrong shape** ([ADR-0027](docs/adr/0027-changing-one-thing-in-an-accepted-request.md#amendment-m102), amended). Seven operators join 10.1's eleven under `mutation.*`, in its two families. **Violations**: `wrongRoot`, the whole body as another kind the declared shape does not accept - a list holding the accepted object, a word, a number; `emptyBody`, no bytes at all, only where the body is required; `notJson`, the accepted body cut off halfway, or plain words; `wrongContentType`, the accepted body under `text/plain`, `application/xml` or a form's media type, whichever the operation does not take; `beyondItsWidth`, a number past what its `int32`, `int64`, `float` or `double` holds, in a parameter or a body. **Probes**: `deepNesting`, an undeclared member ten thousand lists deep, where undeclared members may hold anything; `extremeNumber`, the edges of every common width where nothing bounds the number. A body can now say the exact text it is sent as, which travels inside the test case. *Three things settled by the maintainer on 28 September, before any code*: deep nesting as an extra member and a probe, rather than the whole body replaced by it, which a JVM reader refuses at the first bracket; the numeric extremes as two operators, one per family; an empty body only where one is required. *A leaf of the wrong kind and arrays far longer than any limit were built at 10.1, as its operators at a place inside a body - see the notes* | The code an API runs before its own - reading a body, checking its media type, fitting a number into a fixed width - is reached, and fails in its own ways. The five 2027 APIs restarted before every run, five seeds, a minute each: pet-clinic's distinct server failures by message rose from 214.0 to 279.4 and by exception kind from 60.8 to 71.8, better on every seed by either count; kafka-rest-proxy, which refuses every broken body as it should, covered twelve more branches, better on every seed; the other three did not move. Nothing was lost - operations covered, requests sent, idle time - and the probes added nothing, so they ship off; 8.5 removed them |
| 10.3 ✅ [#338](https://github.com/isa-group/RESTest/pull/338) | **Series of requests around a thing the run created** ([ADR-0028](docs/adr/0028-sequences-over-things-a-run-creates.md)). A strategy in the plan that says `sends: sequences` - shipped at 10, taken from nominal - turns a creation it is drawn for into the first request of a short series about the thing created. Each later step is built once the answer to the one before is in, with the identifier the API gave the thing, read from the reply's body or, where it has none, its `Location` header; the gaps before it take what the creation was sent. **Six series, one question each**, one switch each under `sequences.*`: `readAfterDelete`, `deleteTwice`, `writeUnderDeleted`, `putTwice`, `safeGet`, `createTwice`. Every step records its series, its place and the exchanges it follows, and expects a refusal only where the API has said it deleted what the step asks about; the verdicts are recorded, not judged. What a series learns stays with it: neither memory hears its steps. *What was built differs from the row as written, all of it chosen by the maintainer on 28 September after a survey of what the related tools do - see the notes*: one question per series, `deleteThenUse` split into three and `crossUpdate` not taken, PUT's idempotency and GET's safety added, a share in the plan and only a creation's turn starting a series | The server failures that only several requests together reach - a thing still there after its deletion, a second deletion that breaks, a write under a thing that is gone, a duplicate key. Measured on the five 2027 APIs, restarted before every run, five seeds, a minute each, the six series off against on: **pet-clinic reached one operation more on every seed** - a pet read under its own owner, which only `safeGet` and `putTwice` pair - and covered 153.0 branches against 151.0; **kafka-rest-proxy 858.6 against 846.0**, better on three seeds of five; the other three did not move. **Distinct 5XX did not move on any API**: every read after a deletion, second deletion and write under a deleted thing was answered 404. Of 7,218 series started, 4,134 began and 3,860 went all the way; none stopped for want of an identifier. Two reads of the same notebook are what showed notebook-manager's creations replacing a notebook whose `id` their body carries |
| 10.4 ▶ | **A value of the wrong kind drawn from a long list** — added by the maintainer on 2 October, after the official results of the 2026 edition and a comparison with the fuzzing values of CATS, Schemathesis, RESTler, EvoMaster, RestTestGen and AutoRestTest ([ADR-0027](docs/adr/0027-changing-one-thing-in-an-accepted-request.md), amendment M10.4-M10.7). `wrongType` draws, inside a body, from every value the lists called `fuzzing` hold - the one RESTest carries and any handed over under that name - and from a few of its own: `1.5` and `0.5`, `"1"`, `"1.5"`, `"true"`, `[[]]`, `[[], []]`, `[{}]`; in a parameter declared a number or a yes-or-no, from every word among them that does not read as one and arrives as written, with `1.5`, ` 1` (not in a header, which loses the space), `0x1A`, `NaN`, `Infinity`. What is sent is judged against the declared kind rather than the class of the accepted value, so `1.5` goes where a whole number is declared. No switch of its own, by the maintainer's choice: `mutation.wrongType` still turns the kind off whole | An API is asked what it does with many values of the wrong kind in one place, not five: each kind its reader refuses differently is a different failure where the API fails on it Measured with 10.5-10.7 on the eleven 2026 APIs (see the notes): unique server failures 154 against 204, erc20 left out, better on six APIs and worse on none; operations covered and branch coverage level |
| 10.5 ▶ | **More ways of a body not being JSON** — added by the maintainer on 2 October, same source. `notJson` also sends the accepted body followed by a word, with a line break written raw inside its first piece of text, with the colon after its first property's name left out, and with a comma after its last item. A number thousands of digits long, which is JSON, joins `beyondItsWidth` instead, inside a body only, as long as `mutation.oversizedLength`. No switch of its own: `mutation.notJson` and `mutation.beyondItsWidth` still turn each kind off whole | Each mistake a reader of JSON stops at is a different message, and an API that answers it with a server error answers each differently Measured with 10.4, see its row and the notes |
| 10.6 ▶ | **A word that only looks like its format** — added by the maintainer on 2 October, same source. A fifteenth kind of change, `breakAFormat`, with its own switch `mutation.breakAFormat`: where the document names a format with fixed rules - `date`, `date-time`, `time`, `email`, `uuid`, `uri` or `url`, `ipv4`, `ipv6`, `hostname` - a word that looks like one and is not: `2021-02-30`, `2021-13-01`, an hour past 23, `a@b.`, an identifier one group short, `256.1.1.1`. Not where the document also states a closed list | An API that checks a format by its shape but not by its rules, or by neither, is asked about the dates and addresses that pass the first and fail the second Measured with 10.4, see its row and the notes |
| 10.7 ▶ | **The list of awkward values RESTest carries, from 48 values to 88** — added by the maintainer on 2 October, same source. A whole number with a fraction and written as a word in the whole numbers; a word and a yes-or-no in the numbers; numbers with a fraction in the yes-or-nos; lists of empty lists and of an empty object; nested objects; `1e-308`, `1e400`; letters that grow when their case changes, Zalgo text, invisible characters, template and format-string markers, one SQL and one script injection, sentences, and ten thousand characters. No switch of its own: a plan without the pushing strategy does without the list, and 10.4 reads it too | Requests that push at the API, and values of the wrong kind, of more shapes than before: more of what careless code falls over on Measured with 10.4, see its row and the notes |

### Notes

**10.4-10.7 — measured together, and the pushing strategy measured beside them.** Three campaigns on
the eleven 2026 APIs, twenty minutes each, one run per variant, sessions in parallel, on 2-3 October
2026: **A**, the commit before these rows (`baf095a6`); **B**, these rows (`15a423aa`); **C**, these
rows with the pushing strategy left out and its quarter shared as nominal 40, mutation 40, series 20,
as the maintainer chose.

- **A against B, what the rows are worth.** Unique server failures by the benchmark's own count,
  erc20 left out: 154 against 204, better on six APIs and worse on none (sign test p = 0.031) -
  pet-clinic 38 to 52, person-controller 54 to 73, market 26 to 34, features-service 15 to 22. By the
  reference paper's count (Kim, Sinha and Orso): 563 against 835. Kinds of exception named in the
  replies: 48 in both - more different messages from the same kinds, which is what is scored.
  Operations covered: 231 in both. Branch coverage: 24.1% against 24.7% on average. What went down
  is one run's chance: features-service's two operations reached once in 6,061 requests in A,
  flight-search's two actuator logger operations reached 4 and 1 times in 500; gestao-hospital's
  three more operations and 10.5 more points are its geocoding service having quota left during B
  and not during A.
- **B against C, what the pushing strategy is worth.** Without it, unique server failures fall from
  204 to 181 (worse on five APIs, better on none, p = 0.062), features-service's from 22 to 10 with
  10.7 points of branch coverage less; operations covered fall from 231 to 228, branch coverage from
  24.7% to 23.8%, and the area under operations covered is smaller on nine APIs of eleven. The
  reference paper's count goes the other way on person-controller alone (403 to 571, with twice the
  share of changed requests), and is not the count the competition scores. **The pushing strategy
  stays.**

**10.1 — what it takes from 3.1b and what it leaves.** 3.1b's notes below M3 still describe why the
operators are shaped as they are — a mutation of a request the API accepted, so that a 2XX afterwards
is attributable to the one change — and 10.1 inherits all of it. What it leaves behind is the pair
of oracles 3.1 supplies, *negative data must be refused* and *positive data must be accepted*. The
original argument for ordering 3.1 first was that without those oracles a broken request "would earn
a 4XX that nothing reads and no finding anybody could act on". For the competition the 4XX is not the
point; the 5XX that a broken request sometimes earns instead is, and `ServerErrorOracle` reads that
already. The intent is recorded anyway, because it is a fact about the request and recording it
costs nothing, and because 3.1 then has a consumer waiting when it arrives.

**10.1 — what the measurement said.** On pet-clinic the new failures are new kinds rather than new
values: a word for a numeric path parameter, a list where a body wants a word, one below a stated
minimum. The other four APIs did not move, for reasons worth knowing before 10.2 and 10.3 are
judged the same way: kafka-rest-proxy's, notebook-manager's and gestao-hospital's few failures are
reached by ordinary requests already, the last two state no bound, enumeration or pattern for a
violation to break, and flight-search accepts so few requests that the strategy changed under 1% of
them. Counting by message is the benchmark's way and the generous one - pet-clinic's error bodies
repeat the address, so the table in ADR-0027 also counts by exception kind, which a value echoed
back cannot inflate, and the gain holds. Between 2% and 28% of the requests expecting a refusal were
accepted; sampled, they are the APIs being lenient, which is what 3.1 will report. The probes cost
kafka-rest-proxy a third of its requests, ten thousand characters taking longer to answer, and found
nothing, so they ship off and 8.5 measures them over twenty minutes.

**10.2 — what 10.1 took from it.** Two of the operators this row listed, a leaf of the wrong kind
and arrays far longer than any limit, turned out at 10.1 to be the same operators as 10.1's wrong
type and oversize, at a place inside a body rather than in a parameter; the maintainer chose on 27
September to let 10.1's operators reach inside bodies, because that is where the priority APIs take
their input. What is left here is what changes the structure of a body as a whole rather than one
value in it.

**10.2 — what the measurement said.** pet-clinic is where both rows of M10 so far have shown,
because it answers 500 to whatever its framework throws: by exception kind the gain is one new
exception, the media type it was never offered, on fifteen operations, and the rest are new
messages of the kind 10.1 already reached - a body missing, JSON cut off, a list for an object, a
number too large for an `int` - which the benchmark counts apart. kafka-rest-proxy is the first API
where an M10 row moved anything: it refuses every broken body correctly, and refusing is code no
request had run. notebook-manager's failures carry no message to tell a new one apart by, and
gestao-hospital's and flight-search's are not in the code that reads a body - which is where 10.3's
sequences look instead. The share of requests changed did not rise, although the whole body is now
a place in nearly every request that has one: on pet-clinic the strategy's turns that fall back are
the twelve operations it never changes in either arm - six deletions, which are never kept to be
changed, and six lists that carry nothing - and none of them takes a body.

**10.2 — why shape and value are two rows.** The fuzzing dictionary is a list of *values*, and 2.7c
made it reach every leaf of a body. What it cannot express is a body whose structure is wrong, because
a dictionary entry is a value at a place and the place is fixed by the schema. That is a generator
concern, and it is one operators do well: each says exactly which structural rule it broke.

**10.3 — what was built differs from the row, and why.** The row named four operators. Before
any code the maintainer asked for a survey of how the related tools build sequences, and for
HTTP's two promises about methods - a safe one changes nothing, an idempotent one leaves the same
state however often it is repeated - to be tested too. The survey is in ADR-0028.
- RESTler, Schemathesis, CATS and EvoMaster all check a thing is gone after its deletion.
- EvoMaster alone checks PUT's idempotency and full replacement.
- WuppieFuzz duplicates requests without comparing the answers.
- Nobody checks a read's safety, a second deletion, or a write under a deleted thing.
- Where a tool combines checks, it guards against their interfering.

On that survey the maintainer settled, on 28 September:
- **One series, one question.** The first proposal read, deleted, read, replaced and deleted again
  in one series. The replacement may create the thing again, and a second deletion after it would
  then ask something else. Reads do not interfere, since a safe method changes nothing.
- **Six series.** `readAfterDelete`, `deleteTwice` and `writeUnderDeleted` are the row's
  `deleteThenUse` and `danglingReference` as three questions. `createTwice` is the row's. `putTwice`
  and `safeGet` are the idempotency and safety asked for. `crossUpdate` is not taken: no tool does
  it, and it applies to few operations.
- **A strategy in the plan with a share of 10**, taken from nominal, that starts a series only on a
  creation's turn. Its own sources build every step. What a series learns stays with it.
- **The verdicts are recorded, not judged**, which leaves the oracles to 3.2 and 4.5.

The rest the survey found - a child through another parent, a failed change or creation leaving a
trace, merge-patch and a PUT that creates - is noted under M4.

**10.3 — where the identifier comes from.** Read from the recorded runs of 8.4:
- pet-clinic answers every creation with the thing and its `id`, and a `Location` without the base
  address its document gives;
- notebook-manager and gestao-hospital send `id` in the body only;
- no priority document declares OpenAPI `links`, and one of the corpus's 46 does.

So the body is read first, the `Location` header only when the body has nothing that fits, and
declared links not at all; they stay 4.3's. A reply is taken for the thing the series made only
where it can be: never a list, which may list things that were there before; never a value the
creation was sent in its own address, which names what it was made under; and never for a `POST`
whose address ends in a gap, which names a thing that already exists and is only sent twice. The
same runs predicted where series would have little to do: kafka-rest-proxy created no topic in
twenty minutes, and flight-search accepted one registration.

**10.3 — the oracles are not here either.** 4.5's stateful oracles - use after free, update
idempotency - and 3.2's HTTP semantics, faults 113, 117 and 118 of the catalogue, would judge these
series. The benchmark judges them for us, by counting the 5XX. Every step names its series, its
place and the exchanges it follows, which is enough for either row to judge a stored run offline.

**10.3 — what 9.3's outcome added to it.** ADR-0013's M9.3 amendment said the trigger, not the unit,
was the open part, and a series that creates its own victim waits for nothing. The unit was built
again rather than brought in, since the experiment branch predates 10.1 and 10.2. Its `followUp` is
not reused either: it drew later steps from the numbers the ordinary requests come from, which would
make them depend on how fast the API answers. The two decisions ADR-0013 left to M4 are taken there.

**10.3 — what the measurement said.** The machinery works: of 7,218 series started over the 25 runs
with series on, 4,134 began - their creation accepted - and 3,860 went all the way, 93% of those
that began. None stopped for want of an identifier. 257 were cut short by a step they needed, 207 of
them on notebook-manager straight after an ordinary request deleted the same notebook, and 16 were
overtaken by the end of the run. What it did not do is reach new failures: these five APIs answer
404 to a read after a deletion, a second deletion and a write under a deleted thing, and 201 to the
same creation twice. The gains are elsewhere.
- pet-clinic reached `GET /owners/{ownerId}/pets/{petId}` in every run, only through `safeGet` and
  `putTwice`, whose reads send a pet's own owner.
- kafka-rest-proxy covered twelve more branches with series whose creations are actions, sent twice.
- notebook-manager's two reads of one notebook disagreed 74 times in 760, and 38 of those are its
  `POST /notebooks` replacing the notebook whose `id` the body carries - recorded, for 3.2 and 4.5
  to judge.

gestao-hospital accepted 4 of 2,255 creations, since its hospital creation stopped answering 2XX on
28 September, and flight-search none. So all six series ship on, `safeGet` and `putTwice` included.
The three that delete reached nothing new here, and are kept for the question the related tools ask
most, at 3-4% of the requests.

## M11 — Settings and API keys

*Goal: every number somebody decided has one place it can be changed, and an experiment can switch
a behaviour off without touching the code or the plan.* [ADR-0025](docs/adr/0025-settings.md). *And,
since 29 September: an API that asks for a key is handed one, sent where its document says, and the
key is never written into anything the run leaves behind.*

| # | Increment | What it enables |
|---|---|---|
| 11.1 ✅ [#320](https://github.com/isa-group/RESTest/pull/320) | **The settings.** One immutable `Settings` in `restest-core`, built from typed records per concern — `EngineSettings` already exists and is the model — assembled once in the command-line module from four layers in this order: the defaults in code, a file given with `--settings`, environment variables named `RESTEST_<GROUP>_<KEY>`, and `--set group.key=value` repeated. `restest run --print-settings` prints the effective values with the origin of each, the way `--print-campaign` prints the plan. Unknown keys are refused with the nearest known one; the effective settings and their origins go into `report.json`, so a run says how it was configured. An architecture test forbids reading the environment or system properties anywhere but `restest-cli`. **First tranche moved from constants:** the concurrency range and its slowdown factor, the retained response bytes, the work-ahead factor and the straggler grace; the depths, lengths, item counts, null rate and attempt counts of invented values; the sizes of the memory of observed values; the bounds of the JSON report; the document size and fetch timeout. **Thirty-nine settings in six groups**; `store` and `sequences` are not created because nothing fills them yet. Two things the building settled: where the engine starts is worked out from the concurrency range in force rather than fixed, so `--set engine.maxConcurrency=1` works as one line; and the pair deciding the room an unbounded number is invented in was found to be misnamed - the second is a width above the first, not a ceiling - and is now `generation.roomAboveIt` | The eighty-odd numbers that were decided during development — how many requests in flight, how long a string, how deep a body, how much of a reply is kept — stop being recompile-only. A person running against a fragile API turns the concurrency down in one line; an experiment turns a behaviour off in one environment variable; and neither touches the plan, which is about the API rather than about the tool |
| 11.2 ✅ [#342](https://github.com/isa-group/RESTest/pull/342) | **Every lever a switch, and the list of them.** From 11.1 on, every row of M9 and M10 shipped with a boolean that turns it off — under `schedule.*`, `generation.*`, `memory.*`, `mutation.*` and `sequences.*`, five groups rather than the three this row first named — and [`docs/switches.md`](docs/switches.md) lists all twenty-six: what each one turns off, what leaving it on costs and what 8.4 and 8.5 found it worth, the files that turn off everything one increment added and each milestone's levers at once, and the plan variants that complete the picture. The memory of observed values is a *source*, so its ablation is a plan, and the page shows the shipped plan without `observed`, its weight shared among the rest in the proportions they had. A test checks that every switch the page names exists and every switch that exists is named, that each file on the page is one the tool reads and uses value for value and does what its label says, that the file for getting the seed back, handed over with the page's plan, leaves a run of the pet clinic with nothing depending on what the API answered, and that the plan on the page is the shipped one without the memory. *Two things settled by the maintainer on 29 September*: no switch for a row as a whole, the page's files instead; and no setting offered as the way to run without the memory, since a plan naming a source a setting had silenced would mislead — see the notes | An ablation is a file handed over with `--settings` or `--campaign` rather than a branch per variant, and the files are on a page the build checks, so the paper's table of what each idea is worth can be produced by a script. Three places that said taking `observed` out gives a run back its seed now say that the changes to accepted requests and the series have to be switched off too |
| 11.3 ✅ [#343](https://github.com/isa-group/RESTest/pull/343) | **API keys** — the narrow version of 2.6, added by the maintainer on 29 September. **Where** a key goes is read from the document: a security scheme of type `apiKey` names a header, a query parameter or a cookie, and the `security` requirements, at the root and on each operation, say which operations ask for it — `security: []` asks for none, and of the alternatives an operation offers, the first made only of keys the run holds is taken. A key the document declares and asks for nowhere, as BigOven and Tumblr do, goes with every operation that does not say `security: []`. **What** the key is comes from the person running the tool, with `--auth` — the name the maintainer chose on 30 September, because the same option is to carry the other credentials later: the key alone for a document that declares one; `<scheme>=<key>` for one of several, read that way only when the document declares the name, so a Base64 key ending in `=` reads as itself; or `header:<name>=<key>`, `query:` or `cookie:` for a document that declares none, sent with every operation. `RESTEST_AUTH` holds what one `--auth` holds; a key typed wins over it; a key typed that cannot be placed answers 2 before anything is sent, one in the variable is a warning. The key is added as the request leaves, by a door in front of the engine, so no test case carries it. An input the document declares under the key's name, in the same part of the request — or a form field, for a key in the query — is filled with the key and taken out of what the generator fills, so nothing is invented for it and no mutation picks it. **Every appearance of the key is hidden in everything the run writes**, before anything sees the exchange: raw, percent- or form-encoded, JSON- or HTML-escaped, and any piece of it eight characters long, replaced by text naming it — `REDACTED-AUTH.api_key` — safe inside an address. The rule checking replies against the document does not judge a reply that was changed. A run whose document asks for a key it was not given says so before its first request, naming the option. [ADR-0029](docs/adr/0029-the-key-an-api-asks-for.md) records the choices and how Web Fuzzing Commons' authentication file comes in by the same door; ADR-0005, ADR-0006 and ADR-0015 are amended. **Not taken**: the rest of 2.6, reading a WFC file, and sending a request without its key to see whether the API notices, which is one of the security oracles on the deferred list | An API that wants a key stops answering 401 to everything: the key is handed over once and sent where the API expects it, on the operations that ask for it, and a report can be shared without it. For the competition it is insurance — the call promises tools "any required authentication material (e.g., API keys)", and nothing says the five undisclosed APIs will be signed in by the benchmark's proxy the way the known ones are |

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

**11.2 — what the audit found, and what it settled.** Every behaviour M9 and M10 added is behind
its switches, and with them off a strategy builds its share the way nominal does, drawing the same
numbers. Three rows take several lines to switch off whole — 10.1's nine changes, 10.2's five and
10.3's six series — and the maintainer chose on 29 September to give none of them a switch of its
own: it would have been three more settings for 12.1 to freeze, and every kind of change answering
to three switches. The page carries the files instead, which is what 8.5's variants were. A setting
proposed as the way to run without the memory, `memory.longestReplyRead: 0`, was refused for the
reason [ADR-0025](docs/adr/0025-settings.md)'s M11.2 amendment gives: a plan that names a source a
setting has silenced says something about the run that is not true. Three older behaviours have no
switch — a whole thing the API returned reused with one value changed (2.5b), the preference for
JSON an `Accept` header states, and the lists for one place asked before the ones for a kind of
value — and move when their code is next touched. The plan without the memory that 8.3 ran, in the
harness repository, predates M10 and has neither of its strategies, so reused as it is it would
switch M10 off along with the memory; which plan 8.2's sources axis runs is for the maintainer to
settle at 8.2.

**11.2 — one setting that decides two things, left for 2.1.** Found while reading the campaigns of
1-2 October: when `generation.optionalParametersBySize` is off, each optional parameter is decided
on a coin weighted by `generation.optionalBodyChance`, the same number that decides whether a
request sends a body it may leave out, so an experiment cannot change one without the other.
[The settings](docs/settings.md) say so. A setting of its own for the coin would correct it; the
maintainer chose on 2 October to leave it documented for v2.0 and to correct it in 2.1, since with
the defaults both are 0.5 and nothing a run sends depends on which is read.

**11.3 — why it is in M11, and why a key is not a setting.** The maintainer put it here. M11 is
the milestone still open before the command line freezes, and it is where a run is told things that
do not come from the API's document. A key is one of those, but it is not a setting, by the test
[`docs/settings.md`](docs/settings.md) gives — *would it mean the same thing for a different API on
the same machine?* — and it is not part of a plan either: a plan is written once for an API and
means the same on every machine, where a key belongs to one deployment and to one person.
Treating it as a setting would also be the surest way to leak it, because every setting is printed
by `--print-settings` and recorded in `report.json` with where it came from. So a key has a way in
of its own beside the settings — the command line or the environment, as a setting can be — with
none of their recording.

**11.3 — with the document, or handed over apart? Both, and the corpus says why.** The maintainer
asked which. The document says *where* a key goes and never *what* it is: OpenAPI's security scheme
has a place and a name and no field for a value, and the Petstore's `special-key` is a sentence in
its description. Nor can the document be relied on for the *where*:

- eight of the corpus's 46 documents declare an API key, four in a header and four in the query,
  and two of those eight ask for it on no operation at all;
- two carry a key as an ordinary parameter, LanguageTool instead of a scheme and the Petstore beside
  one;
- of the seven APIs that RESTest 1.x's own configurations reached with a key, three documents do not
  declare it;
- none of the five priority documents declares a key, and flight-search declares a bearer token and
  asks for it nowhere, since the benchmark's proxy supplies it.

Hence one way in that reads the place from the document, and one where the person says it.

**11.3 — what the related tools do.** Surveyed on 29 September, from each tool's documentation and
source: Schemathesis, EvoMaster, RESTler, CATS, RestTestGen, WuppieFuzz, AutoRestTest, ARAT-RL and
RESTest 1.x.

- **The value always comes from outside the document** — a flag, a configuration file that reads
  environment variables, a script whose output is the credential, or a sign-in the tool performs —
  because OpenAPI has nowhere to hold it. ARAT-RL has no authentication at all.
- **Only Schemathesis reads the document to place a key.** Its configuration names a credential
  after a security scheme, takes the place and the name from the scheme, and sends it only to the
  operations whose `security` the scheme satisfies. Every other tool sends the same credentials on
  every request — RESTest 1.x too, whose configuration never read the document's schemes.
  RestTestGen lets a credential take the place of a parameter of the same name, which is what 11.3
  does for a key a document declares as an ordinary parameter.
- **Elsewhere, the schemes feed oracles.** Schemathesis and CATS send requests without their
  credentials and expect a refusal, EvoMaster reports a 401 from an API that declares no scheme, and
  CATS checks that every path declares one. That is the kind of security oracle 11.3 leaves on the
  deferred list.
- **Several credentials usually means several users**, to reach more operations or to check who may
  do what: EvoMaster, RESTler, Schemathesis. Only RESTest 1.x rotated several keys of one user, one
  per test case.
- **Masking is uneven.** RESTler replaces token values in its logs by default, and Schemathesis
  masks values by the names of the headers and keys that carry them. CATS, where masking is still
  switched on by hand in the released version, writes a name in place of each value in a replay, so
  that the value is read from an environment variable. EvoMaster and RestTestGen write credentials
  into the tests they generate.

What 11.3 takes from that: the place from the document, as Schemathesis does, and the person's
word where the document is silent, as every tool does; and masking that is always on, of every
value the run was handed rather than of names that look secret, because the tool knows which values
those are — with a `curl` command naming what to fill in, the way CATS's replays do.

**11.3 — the door the other mechanisms come through.** A document names each way of signing in as a
*security scheme* with a type. 11.3 hands a credential over under a scheme's name and lets the type
decide how it travels, so a bearer token and a user name with a password — a scheme of type `http`,
with `scheme: bearer` or `scheme: basic` — are one more type each: the same option, masked the same
way, chosen for an operation by the same reading of its requirements. The command line learns no
second vocabulary for them. What does not fit through that door is a credential the tool has to
*obtain*. OAuth2's client credentials and a sign-in the tool performs itself send requests of their
own before the ones that need them, charged to the budget like the opening lap, since ADR-0017
refuses a preparation phase off the clock; and what they obtain expires and has to be fetched again.
That, and several credentials at once, is why the rest of 2.6 stays a row of its own, after v2.0.
When it comes, it has a format to read rather than one to invent: the authentication file of Web
Fuzzing Commons — the project whose fault catalogue the tool already uses — describes users, fixed
headers and sign-ins, and EvoMaster, Schemathesis, CATS and WuppieFuzz read it. Open formats in, as
the eighth design principle asks.

**11.3 — kept small, and how it is checked.** A week before the freeze, the row takes only what a
key needs: one key per scheme, nothing refreshed, nothing rotated. None of the priority APIs asks
the tool for a key, so no measurement of the priority corpus can move, and the check the freeze asks
for is that nothing else does: with no key given, every test case becomes the request it became
before the row. That a key reaches the API is shown against a stub that refuses a request without
it, in the smoke gate; that it reaches nothing the run writes, by a test that searches every file a
run leaves behind for it. And since nothing changes unless a key is given, the row has no switch of
its own: ADR-0025 asks one of every lever, and a key handed over is an instruction rather than a
lever.

**11.3 — what the benchmark's proxy records is not the tool's to mask.** The proxy keeps every
request as it passed through, after its own sign-in script has added what it adds. A key handed to
the tool in the competition is therefore in the organisers' recording of the run, whatever the tool
masks. And the recordings of the harness repository's own campaigns, which 8.2 publishes as raw
data, already hold the credentials the proxy adds for the known APIs: 17,112 of the 19,100 requests
recorded in one twenty-minute run of flight-search carry its bearer token. They are throwaway
credentials for containers that no longer exist, but worth knowing about before the data is made
public. The tool can promise only what it writes itself.


**11.3 — what the building settled.** Eight things differ from the notes above, each argued in
[ADR-0029](docs/adr/0029-the-key-an-api-asks-for.md):
- **The option is `--auth`**, not a word about keys, and the variable is `RESTEST_AUTH`, chosen by
  the maintainer on 30 September.
- **A key fills an input of its name only in its own part of the request**, and form fields for a
  key in the query. Filled wherever declared, a query key called `key` would have gone into any
  field called `key`, where an API might store it.
- **A key left in the variable that cannot be placed is a warning**, where one typed is refused. A
  variable can outlive the command it was set for, and a key that fits nothing is no reason not to
  test an API. One that fits is sent, to whatever API is tested, so the variable is set per run.
- **Pieces of a key are hidden too.** The test that searches every file found WireMock's page for
  an unmatched request repeating a key broken across two lines, so any piece eight characters long
  is hidden, beside the whole key in each of its spellings.
- **What stands in for a key is `REDACTED-AUTH` and a name**, with no angle brackets: the series of
  10.3 read an identifier out of a `Location` header by parsing it as an address.
- **The rule that checks replies against the document lets pass what the hiding changed**, since
  a replacement can break a length the document states or the body's JSON, or make a choice
  between shapes object through another shape; the rest of such a reply is judged, and a run says
  how many replies repeated a key back.
- **A key typed without the name it goes under is refused**, as are one shorter than four
  characters, one whose text before an `=` names no scheme, and one stuck to `--auth` without a
  space. The review found that `--auth query:<a key in Base64>` read the key as the name and a lone
  `=` as the key.
- **The smoke gate's check runs in the plain Java runtime** as the tool's own process with the key
  in its real environment, the one place the variable is read from one. The stand-in API tests run
  in every build, on every row, where the smoke job would have run them on one.
## M12 — Closing v2.0

*Goal: a version somebody can install, run, understand and cite, and that replaces `master`.*

| # | Increment | What it enables |
|---|---|---|
| 12.1a ✅ [#345](https://github.com/isa-group/RESTest/pull/345) | **The command line frozen** (ADR-0015 amended). `restest version`, which adds the Java and the machine to what `--version` printed, on a second line; `restest help`; `--help` complete for every command, with the exit codes, the environment and examples in it — every number it lists the command answers, every option says what it does, and every example is a command the tool reads, printed whole on one line; every file of arguments read once, by the command-line framework's own rules, so that a pipe works and one that cannot be read answers `2` in a sentence rather than `4` with a stack trace; the row for exit code `3` saying what the code always answered it for, a run in which not one request was answered; and [`docs/command-line.md`](docs/command-line.md), every command, option, variable and exit code on one page, checked against the tool's own help on every build. `--settings`, `--set` and `--print-settings` from 11.1, and `--auth` from 11.3, are frozen as they were left. **Not taken: `--header`** — `--auth 'header:Authorization=Bearer …'` and `--auth cookie:…` already carry what it was for, and the maintainer took it out on 30 September (see the notes). Split from 12.1 on 30 September, with Ctrl-C in 12.1b | The surface a user, a script and the benchmark adapter all depend on is complete, written down in one place and checked against the tool, and stops moving: after this row, a change to the command line is a 2.x decision. Somebody holding a bearer token finds how to hand it over in the help, and in what the command says when it refuses one typed alone or under a bearer scheme's name |
| 12.1b ✅ [#347](https://github.com/isa-group/RESTest/pull/347) | **What a run cut short leaves behind** (3.7, moved here; ADR-0015 amended). Ctrl-C, `kill` and `docker stop` stop the run sending, wait a bounded time for what is in flight, and leave the summary, a `report.json` that says the run was cut short, and a closed store behind — **or say plainly that they could not**. That is the evidence of the first of the three answers ADR-0015's M1.8 amendment lists, with the number of the second: the conventional one for an interrupted program, which is Java's own, `130` for Ctrl-C and `143` for `kill`, so that a run cut short is never read as one that passed. Both chosen by the maintainer on 30 September, when 12.1 was split. **How, chosen the same day:** with Java's shutdown hook, the standard way, which takes Ctrl-C, `kill` and a closed terminal alike and already ends with those numbers; since a second Ctrl-C does nothing once it runs, the wait is short — `schedule.interruptGrace`, 2 seconds by default, for the answers still owed, after which they count as never answered and the report is written, well inside the ten seconds `docker stop` allows before it kills. **As built:** the hook is there for the whole run, and one stopped while it reads the document does not begin: it writes nothing, leaves what an earlier run wrote as it was, and says so; the loop hears the stop within fifty milliseconds wherever it waits, and hands the engine nothing more, though what it had already handed over still goes out, never more than 32 by default; an answer that comes after the wait is never announced, at the end of a budget too, so the report and the stored run agree; `report.json` gains `"cutShort"` and is written whole or not at all; writing that has got stuck is given five seconds more, a constant rather than a second setting, before the run says what it did not leave behind - a stored run not closed loses the interactions it had not yet saved, and the look at the directory is itself given a second; and `130` and `143` are in the help, with what a run stopped that way leaves | A run stopped early stops costing everything it had found. Measured on the local pet clinic, twelve seconds into a minute's run with `--store`: Ctrl-C ended it 0.4 seconds later with `130`, `kill` in 0.7 with `143`, `docker stop` on Java 21 in a second with `143`; each left the summary, a report saying `"cutShort": true` and a closed stored run holding the same requests |
| 12.1c ✅ [#346](https://github.com/isa-group/RESTest/pull/346) | **A run in which RESTest itself broke answers `4`** — the two cases ADR-0015's amendment in #344 left open, split from 12.1b on 30 September and taken before it, with the answers the maintainer chose that day (ADR-0015 and ADR-0029 amended). A request RESTest loses on its own thread — an error there, such as running out of memory while a reply is read — counts as RESTest's failure rather than as nothing coming back: the run carries on to the end of its budget, then answers `4` and prints the first such failure with its stack trace. It still counts as unanswered too, so a run in which nothing is answered stops, at the end of the round it is in, once as many requests as may be in flight (32 by default) have ended that way, whatever the reason — and one that lost them says RESTest lost them, adding the address only when the API left other requests unanswered too; stopping at the first lost request was rejected. An exchange kept without its details, because the key could not be picked out of it, answers `4`, where 11.3 only said so. Whether `report.json` says that a run broke stays a question of its own | A run in which RESTest lost a request on its way, or had to keep an exchange without its details, is never read as a clean one. Measured on a stand-in that answers one request with 512 MB, to a run limited to 128 MB and told to keep whole replies of up to a gigabyte (`engine.maxRetainedResponseBytes`; by default it keeps the first megabyte, and the reply is not lost): `v2` said "no faults found" and answered `0`; this answers `4` and shows the `OutOfMemoryError`. Three other ways RESTest's own failures still read as the API's are row 3.8, after v2.0 |
| 12.2 ▶ | **The documentation of a finished tool.** `README.md` as the front door: install, run, read a report, write a plan, write a dictionary, change a setting, the exit codes, the container image. `docs/` consolidated: the plan format, the dictionary format, the settings and their keys, the report's JSON shape, the fault catalogue as we render it. `CONTRIBUTING.md` and `docs/DESIGN.md` say what v2.0 is and what 2.x will be. Every command in every document run from a clean checkout before it is pasted. 2.0 has no site, but its documents are written so that one can be built from them later without rewriting them (decided by the maintainer on 1 October); the row meets that with plain Markdown, one page per subject and relative links. It changes no request and touches nothing under `src/main` — the help text is frozen, and the documents describe it as it is — so it lands after the freeze | Somebody who has never seen the repository can install the tool and get a report in ten minutes, and can find out what any line of that report means without reading Java |
| 12.3 ▶ | **The user manual.** Written once, in Markdown under `docs/manual/`, one chapter per file, so that the format decision is about what is *published*, not about what is written. Chapters: what the tool is for, install, the first run, reading the report, plans, dictionaries, settings, the container image, the exit codes, troubleshooting, and a glossary. The one-source-two-outputs approach was approved on 22 September. **Which output is linked from the README and the release was decided by the maintainer on 1 October**, which passes the supervision point the row carried: the README links the manual as GitHub shows it, so nothing in the repository produces HTML, and a PDF built from the same files by a script is attached to the release. One chapter per file, so that a site, if there is one later, can be built from the same files | A manual a person reads from the beginning, rather than documentation a person searches |
| 12.4 ▶ 🛑 | **v2.0.0 tagged and submitted.** **What the competition receives is the harness repository**, pinned to the commit its image compiles — the commit 8.6 rehearsed, kept on `v2` — with the benchmark-compliant `Dockerfile` that compiles the tool from that commit and wraps it in the loop the benchmark expects, as every campaign's image has been built (until 1 October it was to wrap 7.2a's image instead). It is checked with the benchmark's own compliance tooling before 8.6 starts, and 8.6 runs that image and no other, so the artefact submitted is the artefact rehearsed. It is handed over through the competition's submission system, and made public after the competition, as the replication package of ADR-0024 §5 (decided by the maintainer on 1 October; this row had said before the freeze). `v2.0.0` is tagged on `master`, on the commit 12.5 makes, which differs from the commit the competition image compiles in nothing under `src/main` and in no dependency; it is the version the paper cites. The submission is made on 8 October. If 12.3 is not merged by then, `v2.0.0-rc.1` is tagged on `master` instead, `v2.0.0` follows when the manual lands there, and a check in the pull request that lands it shows the two commits differ in documentation files only — approved on 22 September. 🛑 The tag itself is a supervision point | The competition receives exactly what was rehearsed, and the version the paper cites holds the same code under `src/main` |
| 12.5 ▶ 🛑 | **`master` replaced, before the submission, with clean documents** (replanned by the maintainer on 1 October; it was to follow the tag, on 12 October). RESTest 1.x kept on a `v1.x` branch and under its existing tags, with its README pointing here. **`v2` keeps the record, and `master` gets documents about the tool and nothing else** — no reference to the competition and no trail of amendments, as the maintainer asked. *How, proposed and confirmed by the maintainer on 1 October:* when the last row before the submission is merged, `v2`'s tip is tagged `history/2.0-development` and the branch is kept. The clean-up, drafted from `v2` while 8.6 runs and rebased onto that tag, holds documents only: this file down to what 2.x will be; each decision record restated as it stands, under its own number; `CLAUDE.md` and the reviewer's instructions without the competition; and the four architecture tests that read documents — `DocumentedCommandLineTest`, `DocumentedSettingsTest`, `DocumentedSwitchesTest` and `SourceTreeRulesTest` — following them, labels changed and every assertion kept. It is merged into `master` with a merge commit, not squashed, and a check in its pull request shows that nothing under `src/main` and no dependency differs from the commit the competition image compiles. `master` becomes the default branch; the CI badge and every link move; the harness repository stays pinned to the commit it submitted and moves to `master` after the submission. A 12.3 that lands after the tag lands on `master`. Neither 8.6 nor 12.3 is displaced for any of this: if the clean-up or 7.2a is not ready on 8 October, 12.4 tags `v2.0.0` on `v2`'s tip, which differs from the commit the competition image compiles in nothing under `src/main` and in no dependency, and `master` is replaced as 2.0.1. From here on, work targets `master` | The rewrite is the tool: `git clone` gets v2.0 and documents about the tool, and 1.x and the record of how 2.0 was built are history that is still there |

### Notes

**12.1a — why there is no `--header`.** The row asked for `--header name:value` for authentication
material in a shape 11.3 does not read, a bearer token or a session cookie somebody already holds. It
was written on 22 September, before 11.3, and 11.3 turned out to read both shapes: a value given with
its place — `--auth 'header:Authorization=Bearer …'`, `--auth cookie:JSESSIONID=…` — goes with every
request and is hidden in everything the run writes, which is all `--header` was to do. A second
option would be the second vocabulary ADR-0029 rules out. The maintainer took it out of the row on
30 September; the help and [`docs/command-line.md`](docs/command-line.md) show the two lines, and a
key refused for an HTTP bearer or basic scheme names the line that sends one already held.

**12.1a and 12.1b — why split.** The row held two things of different sizes. The surface — a
command, the help, a page, a mistake answered as a mistake — changes no behaviour a measurement can
see. What a run cut short leaves behind does: it changes what every stopped run writes, the drain,
and the report, and ADR-0015's M1.8 amendment had already said it was an increment with a decision of
its own rather than a hook added in passing. The maintainer split it on 30 September. The row
called the answer to take "the third" of the three that amendment lists, while describing what the
first leaves behind — the summary, the report and a closed store. What 12.1b takes is that, with the
number the second names for an interrupted program.

**12.1b and 12.1c — why split again.** 12.1b had taken in, at the maintainer's choice, the two cases
in which a run that broke did not answer `4`. Read against the code, the two halves shared a number
and not much else: Ctrl-C still needed two decisions — how the signal is caught, and how long a run
waits after it — where the other half's were already taken, and together they came back to the size
12.1a had just been. The maintainer split them on 30 September and took 12.1c first: it is small, its
answers were settled, and it leaves the exit code the way 12.1b will need it.

**12.3 — one source, two outputs.** Writing the manual twice would be the one way to make the format
decision expensive, so it is not written twice. Markdown is the source; a script produces the PDF,
through a converter the build can fetch. *Until 1 October* the same script was to produce the HTML
too — a handful of pages with one stylesheet, no generator with its own configuration language. The
maintainer decided that day to link the manual as GitHub shows it instead, so that a site, if there
is one later, can be generated from the same files by a generator chosen then: a script of our own
that turned Markdown into pages would be a site generator to maintain and then throw away.

**12.4 — the recommendation, if it comes to it.** The competition takes a commit and a Dockerfile,
and does not care what the tag is called. The paper cites v2.0.0. If the manual is late, tagging an
`rc` for the submission and `v2.0.0` for the manual keeps both statements true — *the version
submitted is 2.0* and *2.0 has a manual* — at the price of a check nobody will find hard: the diff
between the two tags touches nothing under `src/`. Tagging `v2.0.0` without the manual and shipping
the manual in 2.0.1 would be the other honest answer, and it is the maintainer's to give.

**12.5 — what is not done to `master`.** Nothing is force-pushed and nothing is rewritten: 1.x's
history stays reachable on its branch and its tags, and `master` gains v2's commits on top of it by
an ordinary merge. Anybody with a 1.x checkout finds it where they left it. The same holds for `v2`:
the clean-up is a commit on top of its record, not a rewrite of it, so every commit and every pull
request that cites a row or an amendment still finds it, at the tag. The tag's name does not start
with `v`, so the release that publishes on every version tag (7.2a) never takes it for one.

## M8 — Evaluation

Campaigns run from the harness repository ([ADR-0011](docs/adr/0011-evaluation-harness.md)), which
pins the benchmark's commit and the tool's; nothing here builds against it or names it outside the
documents that explain the relationship. **Machine time is the constraint**: the competition's
protocol is one hour per API, five runs, eight cores and sixteen gigabytes reserved, one run at a
time — twenty-five hours for the five known APIs — so campaigns are sized to the question they
answer, and each row below names what is written while it runs.

| # | Increment | What it enables |
|---|---|---|
| 8.3 ▶ | **The baseline is an ablation over where values come from.** The tip of `v2` after 2.5b, twenty minutes per run, one run, three plans: **declared and invented** — enumerations, samples and defaults from the document, plus invented values, and nothing with a memory; **plus observed** — the plan RESTest ships, which adds what the API itself has returned; **plus dictionaries** — the shipped plan with a hand-written list of values per API passed with `--dictionary`. The first two on the eleven APIs of the 2026 edition, the third on the 2027 five, since a dictionary has to be written per API. Every variant is a plan file or a command-line flag in the harness repository; nothing here changes. About nine hours, overnight. **Runs alongside 11.1**. *Its dictionaries numbers are not a measurement of dictionaries: on 27 September every one of the files was found to have been refused whole, over an operation written with nothing under it, and the runs measured the shipped plan under the dictionaries' names. The reader now takes such an operation as empty and the report says which lists a run read and refused (ADR-0020, amendment M8.3); whether the third plan is run again before the freeze is the maintainer's call* | What each source is worth is known before any lever is built on top of it, and the third plan gives the ceiling: how good the tool is when somebody who knows the API hands it the values — which the competition will not do |
| 8.4 ✅ [#335](https://github.com/isa-group/RESTest/pull/335) | **Screening M9.** Twenty minutes each, one run: the full tool, then each M9 switch off in turn. A lever that does not move its measurement is switched off in the shipped settings and stays in the code. **As run**, on 27–28 September at `76534ebd`: on sixteen APIs rather than the five - every one the benchmark carries except genome-nexus, which does not start on Apple Silicon - at the maintainer's request; six variants - the tool as shipped, 9.1, 9.2 and 2.9 each switched off, 9.2's order turned back, and **the dictionaries 8.3 never measured**, because RESTest refused all of 8.3's files unread. [Results](https://github.com/isa-group/restgym-restest2/blob/main/results/20260927-085159/README.md). 2.9 stays on, by the maintainer's decision against the rule (see the notes) | What each reach lever is worth: **9.1** on every measure (without it 10 fewer operations covered, and less area under both curves on 13 APIs of 16); **9.2** on gestao-hospital's first minute (5 operations at 10 seconds without it, 15–17 in every other variant) and little elsewhere; **9.2's order** and **2.9** nothing measurable. **A dictionary** is the largest effect: branch coverage +4.6 points on average, better on 11 APIs and worse on 1 |
| 8.5 ✅ [#340](https://github.com/isa-group/RESTest/pull/340) | **Screening M10.** The same shape for the M10 switches, counting distinct 5XX messages and branch coverage, **the probes of 10.1 included**, which ship off on a sixty-second measurement and are measured here switched on. About eight hours, overnight after 10.3 merges. **Runs alongside 12.1 and 12.2**. **As run**, on 28-29 September at `6afcaec3`, the head of #338: on the eleven APIs of the 2026 edition, at the maintainer's request; six variants - the tool as shipped, all of M10 off, 10.1, 10.2 and 10.3 each off, and the probes of 10.1 and 10.2 on. [Results](https://github.com/isa-group/restgym-restest2/blob/7dc5e06/results/20260928-204500/README.md). **The probes are removed**, and 10.3's series stay on for now, by the maintainer's decision on 29 September: both against the rule 8.4 set, that a lever which moves nothing is switched off and stays in the code (see the notes) | What each break lever is worth. **M10 doubles the unique server failures**, 147 against 71 with erc20 left out, better on 4 APIs and worse on none, with coverage level. **10.1** is worth +39 and **10.2** +34, which add up with 10.3's +3 to the whole. **10.3** is worth nothing measurable. **The probes** cost more than they give - 146, and +0.6 branch points - at the cost of two thirds of person-controller's requests and a 29 GB recording |
| 8.6 ▶ 🛑 | **The dress rehearsal.** The competition's protocol exactly: the five known APIs, one hour, five runs, eight cores and sixteen gigabytes, the benchmark-compliant image from the harness repository, which compiles the frozen commit itself — the artefact 12.4 submits. Started when the freeze is taken, as soon as the competition image is ready and no later than noon on 6 October, on a machine nothing else is built on. Twenty-five hours. **Runs alongside everything that changes no request** — 7.2a, 12.2, 12.3 and the clean-up for `master` — and once it starts neither the tool nor the competition image changes (replanned by the maintainer on 1 October; until then it was to start at noon on 6 October and run beside 12.3 alone). 🛑 Its results are read as soon as it ends; the only change they may cause is a fix for a run that failed outright, re-measured on that API alone for one hour before the tag, and the commit that fix makes becomes the one the competition image compiles | The number the tool will post in the competition is known, with its variance, before the tool is submitted — and the image the competition receives is one that has already run for twenty-five hours |
| 8.1 ⏭ | **The field**, after submission, at the 2026 edition's own scale so that its published table is the comparison. RESTest 2.0 on the eleven APIs the 2026 edition ran on, one hour, five runs: fifty-five hours. The 2026 report gives every other tool's numbers on those APIs at that budget, so they are not re-run here; RESTest 1.x alone is re-run on the same machine, three runs, as the calibration point between the organisers' hardware and ours — about thirty-three hours. 12–25 October | The table that goes in the paper: RESTest 2.0 beside the whole 2026 field on the APIs and budget that field was measured on, with the hardware difference bounded by a tool that appears in both columns |
| 8.2 ⏭ 🛑 | **The ablation and the replication package**, after submission, at twenty minutes per run like 8.3. Two axes. *Sources*, 8.3's three plans again on the final tool; *levers*, by group rather than one switch at a time: everything on; reach off; break off; everything off. Six variants — the shipped plan with everything on is in both axes — on the 2027 five, five runs each: about fifty hours, 26 October – 1 November. *Hygiene off was a fifth group until 9.4 was set aside unbuilt, and the count before it was one too many.* The harness repository public with every campaign's pinned commits and raw data, so a reviewer reproduces any number in the paper from two commands | Every claim in the paper is a measurement somebody else can repeat, and the paper says what each idea is worth rather than that the whole is good |

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

**8.4 — what the screening asks of the maintainer.** The numbers are in the
[harness repository](https://github.com/isa-group/restgym-restest2/blob/main/results/20260927-085159/README.md); these follow from them. The maintainer settled the first and the third on 28 September.

- **2.9 by the row's own rule.** It moved nothing, which its row predicted: of the sixteen APIs only
  blog has several operations with more than one optional parameter. The rule says it is switched
  off in the shipped settings. The case for keeping it on is the one it was built on - 232
  operations of the wider corpus have four or more optional parameters, and the five undisclosed
  APIs may be among the ones that do - and that 8.4 measured no cost. **Decided on 28 September: it
  stays on**, an exception to the rule made on those grounds.
- **9.2's order stays** unless the maintainer wants otherwise: name first leads at ten seconds on 6
  APIs and trails at sixty on 7.
- **What a dictionary is worth, when a capable writer has time.** +4.6 branch points, most of it on
  three APIs whose inputs are their domain - scs +37.7 (a calculator's operation names, day and
  month names), languagetool +17.6, ncs +11.3; of the other thirteen, 8 better, 4 unchanged and
  gestao-hospital down 6.5, as it is in every variant. Nine more operations, among them both
  logins and two actuator endpoints that answer only for names that exist. The files were written
  by a large model from each document - name, type, format and description first, public knowledge
  of the application last - with no time limit and no running API consulted. That is the ceiling
  ADR-0024 §5's third plan asks for; it is not what a small model could write inside the hour, and
  whether the competition's entry tries that is a decision for the harness repository. **Left for
  later**, the maintainer decided on 28 September.
- **Not proposed: moving the budget to the break strategy once coverage stops growing.** Operations
  covered is at 95% of its final value after five minutes and branch coverage at 91% after one, so
  the last fifteen minutes of twenty add little reach. Acting on that is 2.10b's shares as stretches
  of time, after v2.0, and at its most useful it is scheduling by feedback, which is deferred.

**8.4 — where the runs stop, and three ideas that are not rows yet.** About thirty of the
operations no variant covered are out of reach as the APIs stand: blog's lists and reads all fail
on the server ("could not extract ResultSet"), user-management documents Spring's `/error` under six
methods, kafka-rest-proxy's ACLs need an authoriser the benchmark does not configure,
project-tracking-system answers 405 to two documented methods, and flight-search's two lists insist
on a body RESTest's HTTP client refuses to put on a `GET`. Three ideas came out of reading the rest;
none has the evidence a row needs, and each would have to be measured on the priority corpus, not on
the APIs it was noticed on. **Left for a version after 2.0**, the maintainer decided on 28
September; they are kept here so that 2.1's plan starts from them.

- *Values an API accepted, offered again.* The dictionary run covered user-management's and
  flight-search's logins by offering the same few usernames and passwords to registration and
  login. Without a dictionary no registration gets far enough to be remembered: flight-search
  accepted one in 437, without the `email` its login is keyed on, and user-management none, its
  password rules and required fields refusing the rest. Close to 4.2's resource pool, after v2.0.
- *Optional body properties by number.* A body's optional properties are still decided one coin
  each. On blog's `POST /api/posts` that is not all that stops it: half the requests carry no body
  at all, and of the 1,430 that do, 551 fail on `tags` sent as the text the document declares, 148
  with the same message without `tags`, and 731 on required fields and lengths the document does
  not declare - so the one case read argues nothing either way.
- *Digits for open text.* project-tracking-system answers "For input string" 20,614 times, spread
  over some 24 operations most of which already answer 2XX; the four that never do take a
  `{commitDate}`, which digits would not fix.

**8.5 — what the screening decided.** The numbers are in the
[harness repository](https://github.com/isa-group/restgym-restest2/blob/7dc5e06/results/20260928-204500/README.md).
The maintainer settled both questions on 29 September.

- **M10's gain is the two rows that change accepted requests.**
  - Their parts add up: without 10.1, 108 unique server failures; without 10.2, 113; without either
    and without 10.3, 71; with everything, 147.
  - Nearly all of it is on three APIs, person-controller, pet-clinic and market.
  - 10.1 also gives branch coverage its early lead: 17.9% at ten seconds against 15.2% without it.
- **10.3's series stay on, for now.** They reached nothing the eleven APIs do not already answer
  correctly - 144 without them, 147 with. Their operations and branches look better without them,
  and that is the shipped run's luck rather than their cost: every variant that switched one row off
  gained on the same three APIs, where none of the rows has much to do.
- **The probes are removed**, from the code and from the settings, rather than left switched off.
  They cost more than they give: 146 unique failures with them against 147, and 0.6 points more
  branch coverage, most of it on two APIs where three of the four other variants gained as well. They cost person-controller
  two thirds of its requests and a recording of 29 GB, and would have been six settings nobody uses.
  ADR-0027's M8.5 amendment says what went.
- **Both decisions depart from 8.4's rule**, that a lever which moves nothing is switched off and
  stays in the code: the probes leave the code, and the series stay on. The maintainer chose both.

**8.6 — the one campaign nothing is allowed to interrupt.** Twenty-five hours. It was planned on the
machine the tool is built on, started with two working days left: if it slipped a day, the
submission slipped to the deadline itself, which the calendar was drawn to avoid. On 1 October the
maintainer moved it to the start of the freeze, as soon as the night's campaign has chosen the
variant, so that it buys the margin to re-run an API that failed outright instead of using it up. It
runs on a machine nothing else is built on, because the work left beside it — the image, the
documents, the manual — needs builds, and a build on the machine a campaign runs on skews it. The
freeze is what protects it, and the freeze is not negotiable for anything that changes a request.
That now includes the competition image: whatever it carries besides the tool — a local model that
writes dictionaries inside the hour, charged to the budget, if the maintainer chooses one after that
campaign — is fixed when 8.6 starts, and 8.6 waits for it rather than rehearsing something else —
until noon on 6 October, after which what is not ready is left out.

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
image and release on tag, which 12.5 needs and which sits in M7 because that is where its siblings
are. The other rows keep their numbers and their notes — the notes are evidence and arguments that
still hold — and are taken after the submission (12.4) in the order M13, M3, M4, M5, M6, M7, unless
the results of 8.1 and 8.2 say otherwise. M13 was added on 29 September and is first for the reason
the order of work gives. Rows marked → have had a narrow version taken into M9, M10 or M12; what the
arrow leaves behind is still here, still numbered, still owed.

## M13 — Safeguards

*Goal: an API that refuses the run, asks it to slow down or stops answering is not flooded by it.*
Added by the maintainer on 29 September 2026, for after v2.0: RESTest is a testing tool, and nothing
in it should make it a convenient way to flood somebody's API. Every row lands with a switch and its
numbers in the settings (ADR-0025), and before any of them is built it is replayed over the recorded
runs, the way 9.4 was, to show it would not have fired on an API of either edition: a safeguard that
stops a benchmark run early protects nobody, and costs the next competition a run.

| # | Increment | What it enables |
|---|---|---|
| 13.1 ⏭ | **Stop when the API refuses the run's credentials.** A rule about the whole API, never about one operation: once every answer over a stretch of the run — the opening lap's, and after it the last *N* — is 401, or 401 and 403, and nothing in it was accepted, the run stops sending, says which credentials it held and that the API refused them, and writes what a run cut short writes (12.1b). The stretch, whether 403 counts, and the switch are settings. Once the rest of 2.6 lets the tool sign itself in, the requests before the sign-in do not count | A mistyped or forgotten key is found out in seconds, rather than after an hour of requests the API refused one by one; and an API whose owner revoked a key stops receiving them |
| 13.2 ⏭ | **Wait when told to wait.** A 429, or a 503 that carries `Retry-After`, pauses every request to the API for as long as the header says — in either of its forms, a number of seconds or a date — or, where there is no header, for a wait that doubles each time up to a ceiling; the engine then starts again from its fewest requests in flight and climbs as it already does. The `RateLimit` fields being drafted at the IETF are read where an API sends them. A run still told to wait after *K* pauses, or told to wait past the end of its budget, stops as 13.1 does. The time spent waiting is reported as idle time, with its cause. Most of the related tools send the request that was turned away again after the wait; whether this one does is the row's to decide | An API that asks the tool to slow down is obeyed. Today a 429 is an ordinary answer, and a quick one, so the engine's limiter reads it as room to send *more* |
| 13.3 ⏭ | **A ceiling on the rate.** The most requests a second the run may send, beside the most it may have in flight, which exists already: a setting, off by default | A person testing somebody else's staging server can promise its owner a rate, in one line |
| 13.4 ⏭ | **Stop when the API stops answering.** When every request over a stretch goes unanswered — refused, reset, timed out — the engine keeps one in flight until one is answered, and the run stops once the API has been silent for a stated time, saying when it went silent. The limiter already falls to one request in flight as unanswered requests pile up; what is new is stopping, and saying so | A run that brought an API down stops making it worse, and the report says at what moment the API went silent — which is a finding in itself when the run is what silenced it |

### Notes

**M13 — what v2.0 already does.** Four things, from M1 and M11. At most sixteen requests are ever
in flight, a ceiling whose own documentation calls it the promise that the tool stays a test tool
and does not turn into a load generator (`engine.maxConcurrency`). The limiter halves that number
whenever a request goes unanswered, and sends fewer as answers slow down. A request names the tool
in its `User-Agent`, `RESTest/2.0`, so that an API's owner can tell the traffic apart and refuse it
(`engine.userAgent`) — unless the document declares that header as a parameter of its own, as
BingWebSearch's does, when what goes is the value built for it. And every run ends when its budget
does.

**M13 — what the safeguards cannot do.** The tool is open source and every lever has a switch, so
none of these stops somebody who means harm: one line of settings, or a fork, turns any of them
off. What they do is make the default safe and turning one off deliberate — and visible, since
`report.json` records every setting and where it came from. Whether the person running the tool is
allowed to test the API is not something any tool can check, and none of these rows pretends to.

**M13 — what the recordings say about the benchmark.** In the runs of the tool as shipped in 8.4
and 8.5 — 27 runs of twenty minutes, sixteen APIs between them — no API answered 429 or 503 even
once. Two answered 401: blog, to
under 0.2% of its requests, and flight-search, which with 403 answered 13% of its requests that way,
spread over operations that answer 400 and 404 to others (9.4's notes). So 13.2 has nothing to act
on in the benchmark and is checked against a stub instead; and 13.1 has to be a rule about the whole
API, because an operation that answers 401 to one request answers something else to the next.

**13.1 — a stop, not a steer.** It stands next to 9.4, which was to take budget away from
operations answering 401, and next to the first of ADR-0017's open questions, which asks whether a
run may steer by what it has been answered. It does neither: it chooses nothing about what is sent,
it only ends a run that can learn nothing more, and it was asked for by the maintainer on 29
September for that reason.

**13.1 — the API to replay it on first.** flight-search, one of the two known APIs the benchmark's
proxy signs in, and the one that answers 401 and 403 most. The proxy signs in when the first request
arrives, and while it does, nothing is answered; if the sign-in fails — an API not yet ready to take
it, say — requests go out unsigned, and it is tried again on the next one. A stretch shorter than an
API takes to become ready would stop a run the benchmark was about to sign in.

**13.3 — why off, and why not by address.** A ceiling on by default would slow every run to protect
the few pointed at somebody else's server. A default that switched on for any address other than
the tool's own machine was considered and is not proposed: the benchmark's APIs run in containers
of their own, so it would switch on in the competition too.

**M13 — what the related tools do.** Surveyed on 29 September, from each tool's documentation and
source: Schemathesis, EvoMaster, RESTler, CATS, RestTestGen, WuppieFuzz, AutoRestTest and ARAT-RL,
with ZAP's API scan for comparison.

- **A ceiling on the rate is common, and off by default.** Schemathesis has `--rate-limit`,
  EvoMaster `--ratePerMinute` — documented as the way not to bombard an external service into
  something equivalent to a denial of service — and ZAP a rate limit in its network options. CATS
  alone keeps one on always, at 10,000 requests a minute.
- **Waiting after a 429 is common; slowing down afterwards is not.** EvoMaster always waits for
  `Retry-After`, or ten seconds without it, and sends the request again; RestTestGen and
  AutoRestTest do the same a bounded number of times, and Schemathesis only when asked. RESTler
  retries every five seconds without reading the header, WuppieFuzz only logs it, and CATS and
  ARAT-RL do nothing. Every one of them returns to its old pace afterwards, none waits on a 503 by
  default, and none reads the `RateLimit` fields, a draft that has not yet gone to the IESG.
- **None stops when its credentials are refused.** Schemathesis warns after the run about an
  operation 90% of whose requests answered 401 or 403, and CATS when half of all its tests did. Only
  EvoMaster stops by itself, when connections are refused.
- **Half name themselves** in the `User-Agent` — Schemathesis, RESTler, CATS and WuppieFuzz — while
  ZAP presents itself as a browser. ZAP, like the load tester k6, asks in writing that only what one
  has permission to test be tested; RESTler and CATS warn that a run can take a service down.

So 13.3 is what Schemathesis, EvoMaster and CATS already have, 13.4 is what EvoMaster does by
exiting, and 13.1 — and 13.2's slowing down after the wait — are what none of them does.

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
| 3.7 → [12.1b](#m12--closing-v20) | What a run writes when it is cut short. Taken into the frozen command line, because a version that replaces `master` cannot lose a run to Ctrl-C | — |
| 3.8 ⏭ | **Every failure of RESTest's own answers `4`** — the three ways the review of 12.1c found in which one still reads as something the API did, put after v2.0 by the maintainer on 30 September. A key that cannot be added to a request: the request is not sent and is recorded as a failure on the way, so a run where every request goes that way answers `3` and points at the address. An exception from RESTest's own code inside the engine — the code that records what went over the wire, say — caught with the network's failures and recorded as one. A failure while the loop deals with an answer — announcing it, handing it to a series — lost with the future it happened in, so the request counts as answered and no report hears of it. ADR-0015's M12.1c amendment describes each | A run's `0`, `1` and `3` mean what they say in every case RESTest can go wrong in, not only in the two 12.1c closed |

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
| 4.2 ⏭ | Runtime resource pool and value-source selection. The pool arrived at 2.5b; the producer-then-consumer choice was built at 9.3 and not merged, and comes back here; what is left is choosing among 4.1's candidates, and whether that choice may learn from what the API answered is ADR-0017's second open question | Identifiers from real responses get reused for every parameter the graph can reach |
| 4.3 ⏭ | Declared OpenAPI `links` consumed when present | Free accuracy on the few specifications that declare them |
| 4.4 → [10.3](#m10--break), rest ⏭ | CRUD lifecycle model and sequence generation. The two-step sequence was built at 9.3 and not merged, and six one-question series went to 10.3; the lifecycle as a model is still here, and so are the series 10.3's survey found and did not take - a child reached through another parent, a failed creation or change leaving a trace, a merge-patch that touches more than it names, a PUT that creates twice | Create-read-update-delete flows are exercised end to end |
| 4.5 ⏭ | Stateful oracles: use-after-free, resource availability, failed update must not change, update idempotency, and a read that changes what it reads. 10.3 records, on every step of a series, which series, which step and the exchanges it follows, which is enough for these to judge a stored run offline | Bugs that only appear across several requests, *named* rather than only counted |
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

**4.2 — the resource pool arrived at 2.5b, the sequence was tried at 9.3, and what is left here is
the choosing.** The row read "runtime resource pool and value-source selection", and ADR-0021 brought
the pool forward: bodies cannot be built well without the memory of what the API returned, and that
memory is one listener and two dictionary keyings. What stays here is the half that needs 4.1's
graph: receiving several candidates and choosing among them. Whether that choice may be scored by
what the API answered is one of [ADR-0017's open questions](#the-three-open-questions), because it
is an online estimate of whether a request will be accepted. 4.2 also has to reconcile the
candidates with ADR-0013's rule that a sequence creates what it needs rather than borrowing an
identifier — 9.3 built that rule and measured it, and did not merge it because the pairs it makes
almost never fire on APIs with lists; the reconciliation starts from that measurement.

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
| 7.2a ▶ | **A container image and a GitHub Release on every tag.** A `Dockerfile` of the tool's own in this repository — the distribution on a Java 21 runtime image, `restest` as the entry point, nothing that knows any benchmark's conventions — published to GitHub's container registry on every tag beside a distribution archive with the launcher, by JReleaser. The smoke job runs the published image, not only the build. It changes how the tool is packaged and published, never what is packaged — nothing under `src/main` and no dependency — so it lands after the freeze; 12.5 needs it, the submission does not (replanned by the maintainer on 1 October) | `docker run ghcr.io/isa-group/restest run <spec> --url <base>` works the day the tag is pushed, holding the same code under `src/main` as the image the competition runs |
| 7.2b ⏭ | Homebrew, SDKMAN, jbang | `brew install restest` |
| 7.3 ⏭ 🛑 | GraalVM native binary with an executing smoke test; GitHub Action; documentation site | Sub-100 ms startup, no Java needed, usable in anyone's CI |

### Notes

**7.2a — two Dockerfiles, on purpose.** The harness repository has one, which builds the tool from a
commit and wraps it in the loop the benchmark expects; that one is what the competition asks for and
what 12.4 submits. This one is the tool's own: it knows nothing about being measured, and it is what
a user pulls. *Until 1 October* the plan was that from 7.2a the harness's Dockerfile would start
from this image rather than building the tool again, so that the image the competition runs and the
image a user pulls would be the same bytes with a loop around them. The maintainer kept the two
apart that day: 8.6 now starts before 7.2a can be relied on to have landed, and the competition
image is what 8.6 rehearses, so it keeps compiling the tool from the frozen commit, as every
campaign's has. The two images hold the same code under `src/main`, built twice; the one submitted
is the one rehearsed.

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
  stays here. *9.4 was set aside on 27 September before it was built: with the answers that
  depend on what the tool sends taken out of its list, recorded runs show it with next to nothing
  to act on across the 2027 five - under 0.1% of flight-search's requests - except 2.6-4.1% of
  flight-search's if 403 stays in, which was not measured.*
- **Predicting acceptance.** Whether M4.2 may score dependency candidates by what the API answered,
  which also costs the seed reproducibility ADR-0013 §7 promises.
- **Error messages.** Whether a warm-up may read the *text* of an error to learn which parameter was
  wrong, as opposed to reading its status code, which is ordinary scheduling.
