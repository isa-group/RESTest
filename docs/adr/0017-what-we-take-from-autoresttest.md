# ADR-0017: What RESTest 2.0 takes from the tool that won the 2026 competition, and what it refuses

**Status:** Proposed
**Date:** 2026-09-17

## Context

[AutoRestTest](https://github.com/selab-gatech/autoresttest) won all three challenges of the REST
League tool competition at SBFT 2026 — fault detection, efficiency and effectiveness. RESTest 1.x
competed in the same edition and came fourth of five on two of them, and last on the third. Before M2
starts, it is worth knowing exactly what the winner does, because most of it is not what its own
papers advertise.

This record is the result of reading its source at commit
[`18bc429`](https://github.com/selab-gatech/autoresttest/commit/18bc429013213767fc665ed5cf83ced0626580eb)
(11 September 2026), the two papers behind it
([arXiv:2411.07098](https://arxiv.org/abs/2411.07098),
[arXiv:2501.08600](https://arxiv.org/abs/2501.08600)) and the competition report
([doi:10.1145/3786155.3795704](https://doi.org/10.1145/3786155.3795704)).

### What the tool actually is

Its papers describe three components: a semantic dependency graph, multi-agent reinforcement
learning, and a large language model. The source says something more useful — the tool has two
phases, and they are very different.

| Phase | What it does | Counted against the budget |
|---|---|---|
| Preparation | Builds the dependency graph, and for every parameter a pool of about ten candidate values written by the model. Sends two probe requests per operation and feeds the real error replies back into the next prompt | No — the tool's own budget setting applies only to the phase below |
| Testing | A loop over six learned tables: which operation, which parameters, which values, which body properties, which dependency, which credentials | Yes |

Three facts from the source that change how the papers read:

- **The model is never in the request loop.** It writes a dictionary of values before the clock
  starts, and is not consulted again. Cost, as its authors report it, is about two cents per run.
- **The graph is not built by a language model either.** It uses a table of static word vectors —
  a lookup file queried through a library, not a service — to compare names.
- **The testing loop is strictly sequential.** One synchronous request at a time, for the whole
  hour. The only concurrency in the tool is four threads inside the preparation phase.

### What their ablation does and does not say

Their own study, on their own benchmark of twelve APIs rather than the competition's, removing one
component at a time (Table IV of arXiv:2411.07098; the paper's own column labels for line and branch
look transposed, so only the method column is quoted here, which is unambiguous):

| Variant | Method coverage |
|---|---|
| Everything | 58.3% |
| Without the language model | 47.4% |
| Without the dependency graph | 46.7% |
| Without the learned tables | 45.6% |

Two things follow, and a third does not.

Removing the learning hurts most, not removing the model. And every crippled variant still scored
above the tools they compared against in that study — EvoMaster at 43.1%, ARAT-RL at 42.1%. What that
says is mostly about how low the bar was.

What does **not** follow is that a build with no model and no learning would land anywhere near
47.4%. Each row removes exactly one component: the variant without the language model keeps both the
word vectors and the six learned tables. The configuration this record goes on to propose — no model,
no learning, no vectors — is one their study never measured, and nobody should quote 47.4% as though
it were that configuration's score. The honest reading is narrower: each of their three components is
worth ten to thirteen points of method coverage on its own, and the two we decline to copy are not
obviously the ones carrying the tool.

### What the competition measured about us

From the competition report, eleven APIs, one hour each, ten repetitions averaged:

| | AutoRestTest | RESTest 1.x | Our rank |
|---|---|---|---|
| Unique server failures | 67.09 | 11.25 | 4th of 5 |
| Operations covered | 17.27 | 10.89 | 4th of 5 |
| Branch coverage | 21% | 18% | — |
| **Operations covered, area under the curve** | **60,224** | **8,322** | **5th of 5** |
| Branch coverage, area under the curve | 744 | 237 | 5th of 5 |

The ceiling is not the problem: 10.89 operations against 17.27, 18% of branches against 21%, and the
highest branch coverage anyone reached was 23%, by the organisers' own baseline tool, which was
excluded from the ranking. The problem is *when*. Seven times less area under the curve on operations
covered means RESTest 1.x eventually arrives and arrives late, and the competition scores efficiency
as its own challenge. RESTest 1.x was also the only entrant to fail on two APIs — both of them in our
own golden corpus — over an object-typed parameter in a query string or a path.

### One measurement of our own

Asked where our own generation would show the same shape, the answer was in
`RandomTestCaseGenerator`: every optional parameter is included by an independent coin flip at one
half. For an operation with *n* optional parameters, the probability of sending exactly the required
set is therefore at least 2⁻ⁿ — one time in 256 with eight of them, one in 1024 with ten. It is only
"at least" because a parameter no provider can fill is dropped as well, which reaches the same shape
by accident. The request most likely to be accepted is one we send close to never.

That is the same shape as the area-under-the-curve numbers above, in our own code rather than in
1.x's. It is not a proof that fixing it moves the measurement — that has to be measured — but it is
the cheapest candidate anybody has proposed.

## Decision

Five ideas are adopted. Five are refused. Three are left open, because each of them is either a
deferred item in `docs/DESIGN.md` or close enough to one that adopting it quietly is what this
project has agreed not to do.

### Adopted

**1. Optional parameters are chosen by size, not by coin flip.** Draw the *size* of the optional
subset first, from a distribution that favours small sizes, then which parameters make up that size.
The required-only request stops being a lottery ticket. The shape of that distribution is a constant
nobody has calibrated, so it is settled the way ADR-0013 settles such things — by measuring against
the golden corpus — and not by being written into this record.

*Which increment:* none of M2's existing rows has this as its subject. 2.3 is the deterministic
boundary walk, which is an operator under ADR-0013 §4; this is nominal generation. It needs a row of
its own, and adding one is the maintainer's call, not this record's.

**2. The request declares what it will accept.** (M2.5, whose subject is media types.) Build an
`Accept` header from the media types the operation's own 2XX responses declare. We send none today,
which leaves content negotiation to the server's default. An API that serves a versioned media type —
and one of the five specifications in the golden corpus does — can answer 406 to a client that never
said what it wanted.

**3. Dependencies are inferred between *properties*, not between operations.** (M4.1.) This is the
structural idea worth taking. AutoRestTest compares each parameter of one operation against the
parameters, the body properties **and the response properties** of every other, and the edge it
stores is "this parameter can be filled from that property of that reply" rather than "A depends on
B". Two further details are adopted with it: keep the best few candidates even when none passes the
threshold, so no operation is left with nothing to try; and let the graph grow during the run from
properties that appear in real replies and that the document never declared.

The similarity itself is computed without any model, in three layers: split names on case and on
separators and compare the resulting words; gate every candidate on schema and format compatibility,
which AutoRestTest does not do at all; and carry a hand-written table of synonyms and abbreviations
as versioned data.

That gate is cheaper for us than for most, because `CanonicalSchema` already carries the type and,
for strings and numbers, the declared `format`. It is not free: M4.1 has to decide what compatibility
means for a `SchemaReference`, for `UnsupportedSchema` and `NothingSchema`, and for the composed
schemas M2.1 folds in. That decision belongs to the increment.

M4.1 owes this decision one measurement and one record of its own: annotate by hand the correct
matches across the five specifications of the golden corpus, compare what this mechanism finds
against what a word-vector table would find, and write the threshold and the outcome into an ADR. The
direction is settled here; the calibration is not.

**4. The graph proposes; something else disposes.** (M4.2.) Inference by name is imprecise, theirs
included, so the graph's output is a set of *candidates* for where a parameter's value may come from,
not an assertion about the API. M4.2 already owns choosing among them. How that choice is made —
in particular whether it is allowed to learn from what the API answered — is left open below.

M4.2 also has to reconcile this with ADR-0013's rule that a sequence creates what it needs rather
than borrowing an identifier from the dictionary of observed values. This record does not restate,
narrow or reinterpret that rule; if it turns out to need narrowing, that is an amendment to ADR-0013,
argued against the interference scenario ADR-0013 sets out, and not a clause here.

**5. One more mutation operator: send a required parameter in a location it was not declared in.**
(The operator is generator-side, under ADR-0013 §4; the oracle that judges it is M3.1's.) Query to
header to cookie — half of all the mutations AutoRestTest performs. Restricted to *required*
parameters it satisfies §4's contract: the API is then missing something it declared it needs, a
refusal is correct, and a 2XX is attributable to that one change. Applied to optional parameters it
does not, because a 2XX is then the correct answer, so it is not.

### Refused

| Refused | Why |
|---|---|
| Their second mutation operator — sending parameters the document never declared | It breaks ADR-0013 §4's contract: ignoring an unknown query parameter is correct behaviour for most frameworks, so neither answer is a verdict. The only catalogue code it could fire is 206, which would be a false positive on nearly every API in our corpus. It needs an oracle that does not exist before it needs an operator |
| Six tabular learners with value decomposition | Hyper-parameters nobody can calibrate against evidence, a table per operation per parameter per candidate, and runs that cannot be reproduced from a seed. Deferred in `docs/DESIGN.md` |
| A table of static word vectors | Querying one means depending on a model library, which ADR-0008 forbids outright. Shipped instead as a bare file it would be sixty-odd megabytes fetched at startup, hostile to the native binary of M7.3 and to starting with no configuration. ADR-0008 draws no line between hand-written and learned data, and this record does not pretend that it does: the grounds above are the real ones |
| Their fixed catalogue of extreme values | A short hard-coded list. M2.3's deterministic walk over the limits the document actually declares is strictly better |
| A preparation phase outside the budget | It makes a published number mean something other than what a reader assumes. Principle 7 and the honesty M8 requires both point the other way |

Not refused, and worth saying so: their sequential request loop. We keep our concurrency because we
have it and it costs nothing, not because theirs cost them anything — one request at a time, they
posted seven times our area under the curve on the measurement the competition calls efficiency.

## Consequences

- **Two of the five are corrections, not direction.** Items 1 and 2 change no interface and no ADR.
  They are also the two aimed at the measurement RESTest 1.x came last on, and item 1 has no row to
  live in yet.
- **M4.1 and M4.2 acquire a shape before they are written**, which is the point of recording this now
  rather than rediscovering it in four milestones. M4.1 also acquires an obligation: a measurement
  and an ADR of its own.
- **The synonym table is data, and data has a licence.** Whatever ships beside the tool is
  redistributed by it. ADR-0013 already raises this for value dictionaries; the same check applies
  here, before the first line of the file is written.
- **Three things are left for a human to decide rather than assumed.** They are below. The test this
  record was written to pass is that nothing crosses the line in `docs/DESIGN.md` without somebody
  noticing, and two of the three were caught crossing it during review of this very record.
- **`FeedbackListener` is a seam with no milestone.** `docs/DESIGN.md` lists it among the extension
  points and says every seam is proved during its milestone by a throwaway implementation, but no row
  in `ROADMAP.md` delivers it. Two of the open questions below name it as their landing place, so
  whoever answers them has to give it one.
- **The comparison in `docs/DESIGN.md` now includes the tool that beat us.** Leaving the winner out
  of our own map of the field is the kind of omission a reviewer notices first.

## Alternatives considered

- **Adopt the learning as well, as a small on-line component.** Rejected for v2.0 rather than
  refuted. Their ablation says it is the single most valuable of their three components, which is an
  argument for revisiting it once there is a seam to put it in and campaigns of our own to calibrate
  it against. Adopting it now would carry uncalibrated constants into every later measurement.
- **Adopt the word vectors and accept the download.** Rejected on the grounds in the table above. The
  measurement M4.1 owes will say what it actually costs us before anybody reconsiders.
- **Write nothing down and revisit the tool when M4 arrives.** Rejected: that is four milestones of
  drift, and the two cheapest items are due in the next one.
- **One ADR per adopted idea.** Rejected as ceremony. These share one piece of evidence and one
  argument. Item 3 gets a second record at M4.1 because it is the only one with a calibration left to
  settle.

## Open questions

Three, all of the same kind: each would have a run learn from what it has already seen, and
`docs/DESIGN.md` defers that. 🛑 None is started without explicit approval.

**1. May the choice of which operation to call next be steered by what each operation has been
answering?** AutoRestTest's most elegant idea costs almost nothing to copy: its agents do not all want
the same thing. The one choosing *which operation* is rewarded for errors — plus two for a server
failure, plus one for a client error, minus one for success, minus ten for a method the API does not
implement — while the ones choosing parameters, values and credentials are rewarded for success. Go
where it breaks; send requests that work. They keep the signal clean by updating that table only when
the request was not a deliberate mutation. None of this needs reinforcement learning: weighted
sampling over per-operation counters does the same job with no constant that needs a paper to justify
it. The narrowest useful version is pure hygiene — stop spending budget on operations that answer
nothing but 405 or 401. It sits against the deferred row "search-based or reinforcement-learning
scheduling".

**2. May the choice among dependency candidates be scored by what the API answered?** (Item 4 above.)
Raising a candidate's score on a 2XX and lowering it otherwise is an online estimate of whether a
request will be accepted, which is the deferred row "predicting whether a request will be accepted
before sending it". It has two further costs worth weighing: ADR-0013 §2 decided that weights live in
the strategy and never in the source, and a score attached to a candidate source is exactly a weight
in the source; and under ADR-0013 §7 any strategy consulting it stops being reproducible from a seed
and becomes reproducible only by replaying the stored run. Without it, M4.2 needs some other rule for
choosing among candidates — trying each in turn is the obvious one, and is not deferred.

**3. May a warm-up read the API's error messages?** Two probe requests per operation before the main
strategies begin would pay for themselves on status codes alone: which operations need credentials,
which are not implemented, which answer 404 whatever they are sent. That much is ordinary scheduling.
Reading the *text* of the error to learn which parameter was wrong is the deferred row "refining
values from the API's own error messages", and this record does not adopt it. Whichever is chosen,
the warm-up is charged to the budget and appears in the idle-time accounting: **our clock runs from
the first second**, where AutoRestTest's budget setting exempts preparation entirely. No increment
owns it, because no row in `ROADMAP.md` builds the scheduler ADR-0013 §6 describes.

---

## Amendment (M9.1)

**Date:** 2026-09-23

**Open question 3 has a scheduler to land in, and the part of it taken is the ordinary part.** The
question ends by saying no row built the scheduler ADR-0013 §6 describes; roadmap row 9.1 built it.
Its first round sends every operation once before anything is chosen by chance, charged to the
budget, with the part of its waits that has nothing in flight counted in the idle time; that is the
half of a warm-up this record called ordinary scheduling. It reads no error text and steers nothing by status code: what one step learns reaches
the next only through the memory of values the API returned, which ADR-0021 already licensed. The
refusal of a preparation phase outside the budget stands. [ADR-0026](0026-what-a-run-sends-first.md).
