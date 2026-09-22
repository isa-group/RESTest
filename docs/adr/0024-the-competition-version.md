# ADR-0024: v2.0 is the version submitted to the 2027 competition, and what that leaves for 2.1

**Status:** Proposed
**Date:** 2026-09-22

## Context

The [2027 edition of the REST League](https://github.com/SeUniVr/RestLeague/blob/main/2027/README.md),
the tool competition RESTest 1.x came fourth and fifth in a year earlier (ADR-0017), takes tool
submissions until **9 October 2026** — seventeen days after this record — announces results on
13 November, and takes a four-page solution paper until 4 December. The maintainer's decision is that
v2.0 is the version submitted: not a snapshot of the `v2` branch, but a closed release with a command
line that has stopped moving, documentation, a manual, a container image, and the standing to replace
`master`.

### What is measured

Ten APIs, five known and five that are "fresh APIs never used in previous studies". One hour per
API, five runs averaged, eight cores and sixteen gigabytes reserved for the tool. Four measurements
per API, each normalised across tools before it is added: unique server failures, told apart by
their error message; operations that answered 2XX at least once; the API's own code coverage; and
the area under the curve of each of those three over the hour. Three rankings: *effectiveness* adds
the first three, *efficiency* adds the three areas, *fault detection* is the first alone.

Two facts about that protocol decide most of what follows.

**The benchmark judges the replies itself.** It counts the 5XX, it counts the operations that
answered 2XX, and it instruments the API for coverage. The tool's own verdict on a reply changes no
score. An oracle that says *this 404 should have been a 400* is worth nothing to the ranking, however
much it is worth to a user.

**What counts is which requests were sent, and when.** A tool that covers fifteen operations in the
first minute beats one that covers fifteen in the last, on three of the six numbers. RESTest 1.x's
failure in 2026 was exactly this: seven times less area under the operations curve than the winner,
against a ceiling only sixty per cent lower.

### Where the tool stands

Twenty-five increments delivered in twelve days. The last campaign against the five known APIs,
before the fix that followed it and before the memory of what the API returned, covered sixteen
operations on flight-search and one on kafka-rest-proxy, found three operations answering 500 on
gestao-hospital and none on flight-search, and tested nothing on pet-clinic because the base path in
the document was discarded. It idled 0% of the budget on every API. The tool is fast and does not
crash; what it lacks is identifiers that exist, requests that create before they read, and requests
built to break things in more than one way.

### What the roadmap had in front of that

In its numbered order the roadmap put, before packaging and evaluation: eight increments of oracles
and reports, six of stateful testing including its oracles and an Arazzo exchange format, five of
IDL and constraint solving, and two of live constraint sources. Of those twenty-one, the ones that
move a competition measurement are the mutation operators of 3.1b and the narrow, identifier-shaped
core of 4.1, 4.2 and 4.4. The rest are the tool's value to a *user* — which is real, and which is
why none of it is dropped — but not to this ranking.

## Decision

### 1. v2.0 is the version submitted, and is closed

The commit tagged `v2.0.0` is the commit submitted. Closed means: the command line frozen and
documented, with its exit codes; a README a stranger can install from; the plan, dictionary and
settings formats documented; a user manual; a container image and a distribution archive published
from the tag; Ctrl-C leaving a report behind; and `master` replaced by it. `ROADMAP.md`'s M12 is
that list as increments.

If the manual is not merged by the submission date, the submission is tagged `v2.0.0-rc.1` and
`v2.0.0` is tagged when the manual lands, with a check that the two commits differ in documentation
only. The maintainer may instead tag `v2.0.0` without the manual and ship it in 2.0.1; the record
recommends the first because it keeps *the submitted version is 2.0* and *2.0 has a manual* both
true.

### 2. What is in v2.0 is what moves a measurement, plus what closes the release

Four milestones are added to the roadmap and taken before anything else:

| Milestone | What it is | Measurement it moves |
|---|---|---|
| M9 Reach | A scheduler that owns the clock and sends every operation its best request first; path parameters filled from the identifier of the resource the path names; a producer sent before a consumer that has nothing to consume; budget withdrawn from operations that only ever answer 401, 404 or 405; an `Accept` header | Operations covered, coverage, and the area under both |
| M10 Break | Mutations of requests the API accepted; bodies of the wrong shape; sequences over real resources — delete then read, create twice | Unique server failures and the error branches of the API |
| M11 Settings | One place for every number decided during development, layered from defaults, a file, the environment and the command line; every lever with a switch (ADR-0025) | None directly. It is what makes the ablation a campaign rather than a branch per variant |
| M12 Closing | The list in §1 | None. It is what makes the submission a release |

Three increments already on the roadmap are taken with them: 2.9 (optional parameters drawn by
count), 7.2a (the container image and release on tag, split from 7.2), and four campaigns added to
M8 — a baseline, a screening after each of M9 and M10, and a dress rehearsal under the competition's
exact protocol from the frozen commit.

### 3. What waits for 2.1, and is not dropped

Every oracle beyond the two that exist (M3, 4.5, 5.5); every report format beyond the console and
JSON (3.5); offline re-checking (3.3); per-operation oracle configuration (3.4); the dependency graph
proper with its synonym table and its measurement against word vectors (the rest of 4.1 and 4.2);
the lifecycle model and Arazzo (the rest of 4.4, 4.6); IDL, the solver and constraint-based
generation, whole (M5); live constraint and flow sources (6.1); the overhead regression test (6.2);
authentication inferred from the document (2.6); the external value provider (2.8); the dictionary
writer and cache (2.7b); Maven Central, Homebrew, SDKMAN, jbang and the native binary (7.1, 7.2b,
7.3). Each keeps its row and its number, and `restest-idl` ships in v2.0 as the module descriptor it
is today.

### 4. What the competition does not change

- **The tool stays general.** Nothing is tuned to the five known APIs: the undisclosed five are new
  APIs, and a lever that helps on five documents and hurts on the fifty of the wider corpus is not
  merged. Every M9 and M10 row is measured on the priority corpus and checked on the rest.
- **The harness stays outside** (ADR-0011). What crosses the boundary is the published command line.
  The container image of 7.2a is the tool's own and knows nothing about being measured.
- **The budget is the whole invocation** (ADR-0015, ADR-0017). The opening lap of 9.1 is charged to
  the clock like everything else. No preparation phase runs off it.
- **The deferred list still needs approval.** This record asks for exactly one item from it — the
  narrowest version of the first open question of ADR-0017, weighted sampling over per-operation
  counters with no reward and no learning rate, as 9.4 — and names the two neighbouring questions as
  not taken. The reward-shaped version stays deferred.
- **The rest of the design principles are untouched**: zero configuration to start, never crash on a
  document, test cases as data, one event stream, no global state, nothing blocking the loop.

### 5. Evaluation is part of the plan, not after it

Every M9 and M10 row is measured before it is merged, on a restarted containerised API from the
priority corpus, switched on against switched off, five seeds, and the number goes in the pull
request. Each milestone is then screened as a whole on the five known APIs with short budgets, one
switch off at a time, and a lever that moves nothing is switched off in the shipped settings. The
dress rehearsal runs the competition's own protocol from the frozen commit for twenty-five hours,
and nothing that changes a request is written while it runs. After the submission, the comparison
against the published field and the grouped ablation produce the paper's tables, and the harness
repository is made public as the replication package.

## Consequences

- **The roadmap is read in the order of work, not in numerical order.** Numbers are names. The file
  says so at the top, and `CLAUDE.md`'s instruction to "take the next increment in order" now means
  that order.
- **Seventeen days, twenty-one increments, three campaigns, one rehearsal.** The pace so far says it
  fits with nothing added. An increment that grows is split, and the half that moves no measurement
  goes to 2.1 — never the other way round.
- **A row's pull request carries a measurement**, not only tests. "Better on pet-clinic, five seeds,
  by this much" is the acceptance criterion for M9 and M10.
- **The tool's own verdicts are weaker in v2.0 than the first roadmap promised.** Two oracles rather
  than forty. The documentation says so plainly, and `docs/DESIGN.md`'s comparison table describes
  v2.0 rather than the tool it will be.
- **`FeedbackListener` acquires a milestone.** ADR-0017 noticed the seam had none; 9.4 is its first
  implementation.
- **The paper is four pages and due eight weeks after the tool.** The field comparison and the
  ablation are sized to that: five known APIs, three runs for the ablation groups, about a week of
  machine time each.

## Alternatives considered

- **Keep the order and submit whatever exists on 9 October.** Rejected. It would submit a tool with
  no mutation operators and no sequences — the two levers the shakedown campaign showed missing — and
  spend the seventeen days on oracles the benchmark does not run.
- **Skip 2027 and aim at 2028 with the full roadmap.** Rejected by the maintainer. The rewrite
  exists to be measured, the 2026 result is the reason it was undertaken, and a year's delay buys
  nothing that a 2.1 release after the competition does not.
- **Build the WFC oracles first, because they are "the other half of the problem".** Rejected for
  v2.0, not refuted. They are the other half for a user; the benchmark supplies its own half.
- **Adopt more of the winner — the learned tables, the word vectors, the preparation phase.**
  Rejected on the grounds ADR-0017 gives, none of which a deadline changes.
- **Take all three open questions while asking for one.** Rejected. Questions 2 and 3 are online
  acceptance prediction and reading error text, each a deferred row of its own with costs ADR-0017
  spells out, and neither has the "no constant to calibrate" property that makes 9.4 defensible.
- **Tag `v2.0.0` on 9 October whatever the state of the manual.** Not rejected; §1 recommends the
  release candidate and leaves the choice to the maintainer.
