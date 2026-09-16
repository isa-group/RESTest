# ADR-0016: A fault is identified by its catalogue number, described in our own words, and classified twice

**Status:** Accepted, amended at M1.10
**Date:** 2026-09-15

## Context

M1.6 introduced the first two oracles and the WFC fault catalogue. M3.1 and M3.2 add about
thirty-two more categories between them, and M3.5 adds four report formats — HTML, JUnit XML, HAR and
NDJSON — plus `restest explain`. Everything about how a fault is presented is therefore decided
either now, once, or thirty-two categories and five formats from now, five times.

Three questions are open, and they have been answered inconsistently in passing rather than
deliberately.

**What identifies a kind of fault.** The catalogue gives a number, `F101`. The number is what makes a
run comparable with another tool's run, and comparability is the whole reason the catalogue was
adopted. But a number is not what anybody reads.

**What a fault is called.** The catalogue's own wording — *"Received A Response From API With A
Structure/Data That Is Not Matching Its Schema"* — is a category label written for a taxonomy, not a
sentence written for somebody at a terminal at five in the afternoon.

**How faults are grouped for a reader.** Today there is exactly one grouping: by catalogue number.
That is the grouping a *comparison across tools* wants. It is not the grouping a developer wants, and
there is evidence for that in the tool's own output: until M1.7b a reported fault did not print the
status code the API answered with. The single fact a developer looks for first was the one fact
missing, and nobody had noticed, because the report was organised around the taxonomy rather than
around the reader.

## Decision

### The catalogue number is the identity

`F100`, `F101` and the rest are what a fault *is*, in every format, in the stored run, and in
anything a campaign compares. Nothing else is the identity: not our wording, not the status code, not
the operation. A category is an interface anybody can implement, so the number — not the object
describing it — is what is compared and written.

### Our own words are the description

Every fault carries a one-line summary written to be read, in addition to the catalogue's label. The
catalogue's label stays in the file beside it, so the file remains comparable and nobody has to
reverse-engineer which category we meant. A reader gets a sentence; a machine gets a number; the two
never disagree because both are written.

### Every report classifies twice, and says which is which

**By catalogue number**, as now. This is the classification that is comparable across tools and
across time.

**By the class of status code the API answered with**, because that is the vocabulary developers
already work in and the one they will look for first. This classification is *complementary*, never a
replacement: the catalogue number remains the identity.

The second one is two tables, not one, and confusing them is the thing this decision most exists to
prevent:

- **How every attempt ended** — of all the requests sent, how many came back `2xx`, `4xx`, `5xx`, and
  how many got no reply. This classifies *attempts*, not faults, and it does not describe the API's
  bugs at all: it describes the quality of the run. If most replies are refusals, the requests were
  the problem, and a report that cannot say so leaves its reader blaming the wrong thing.
- **Faults crossed with the answer that carried them** — how many of each kind of fault arrived with
  each class of status code. This classifies *faults*. Its value is the crossing rather than the
  list: it shows at a glance how many faults came back as an ordinary `200`, which anybody watching
  their own logs for 500s is not seeing at all.

Three constraints on the second classification, each of which was got wrong somewhere before being
written down:

- **By family, with exact codes beside it.** `2xx`, `4xx`, `5xx` as the grouping; the commonest exact
  codes listed separately. Grouping by exact code turns into a tail of 401, 403, 404, 409, 422 that
  summarises nothing, and M3.1 is what makes that tail long.
- **"No reply" is a class.** A timeout, a closed connection or a reply that was not HTTP has no
  status code, and is among the worst things an API can do. A classification that only understands
  codes loses exactly the worst cases.
- **Each table says what it counts.** One attempt can be found wrong in several ways and most in
  none, so faults-by-status and attempts-by-status never add up, and a reader who assumes they do
  will reach a false conclusion quietly.

## Consequences

- A reported fault answers the first question a developer asks — what did it return — without them
  having to open anything. M1.7b puts the status on the line that names the request, in the console
  and in the JSON.
- The numbers behind both tables are in the JSON from M1.7b; every format M3.5 adds renders them
  rather than deciding them, and `restest explain` describes the same two classifications.
- M3.1 and M3.2 write thirty-two categories knowing they will be crossed by status. The alternative
  was discovering at M3.5 that a category's meaning depends on a status code it never recorded.
- Comparability is untouched. A campaign reads catalogue numbers; the status tables are aggregates
  derived from data already on every interaction, and add no new field to the stored run.
- Two classifications is more to keep correct than one, and a reader could mistake one for the other.
  That is the cost, and it is why "each table says what it counts" is a requirement here rather than
  a matter of taste.

## Alternatives considered

- **Only the catalogue number.** What the tool did until M1.7b. Comparable, and it produced a report
  in which the status code — the thing practitioners actually search on — appeared nowhere.
- **Only status codes.** What a developer would design on their own, and it throws away the identity
  that makes a run comparable with RESTler's or Schemathesis's. A `200` that should have been a `400`
  and a `200` whose body breaks its schema are different faults with the same status.
- **Our own wording replacing the catalogue's.** Friendlier and unrepeatable: two tools reporting the
  same fault in different words are two tools nobody can compare. Both are written instead.
- **Grouping by exact status code.** Precise and useless as a summary once thirty-two kinds of fault
  can each arrive with any of a dozen codes.
- **Deciding all this at M3.5, where the formats are.** The formats are where it is *rendered*; the
  categories are written before then, and a category written without knowing it will be crossed by
  status is a category that may not record what the crossing needs.

---

## Amendment (M1.10)

**Date:** 2026-09-16

**The catalogue moves, so the version travels with every number. And how many times an API fell over
is a statistic about the run, not an entry in the fault list.**

### Why the version matters more than it looked

This decision adopted somebody else's fault numbers so that our counts could be put beside theirs.
That works only while both sides mean the same thing by a number, and between the two published
versions of the catalogue the numbers were **rearranged rather than added to**:

| Fault | Faults 0.7.0 | Faults 0.8.0 |
|---|---|---|
| A reply whose shape does not match the specification | F101 | **F200** |
| Every security weakness | F2xx | **F3xx** |
| An API answering 500 | F100 | F100 |
| Non-standard status code | did not exist | **F101** |

So a report that said `F101` under the old catalogue and one that says `F101` under the new one name
different faults. RESTest now ships faults 0.8.0, which is what the tools it is measured against use,
and the test that compares our copy against the published file was refreshed with it.

**The version of the catalogue is not the version of the release it ships in**, and getting that
wrong was the first thing this amendment did. Web Fuzzing Commons publishes four things together -
authentication, faults, a report format and a web report - and they began on one version number and
have since drifted apart. The release tagged `v0.9.0` carries faults **0.8.0**. Naming the tag would
have stated a version the catalogue never had, and anybody putting our count beside somebody else's
would have concluded the two counted under different lists when they did not. The file upstream
declares the four numbers in is now pinned beside our copy, and the test reads the version from it
instead of from a literal repeated in two places.

The decision to have every report state the catalogue's name and version is what made this safe to
find and safe to fix. Reports already written are not wrong; they say which list they counted under.
That was worth the line it cost.

### Where a count of server errors belongs

Benchmarks rank tools on how many server errors they provoke, and that number is **not** a count of
faults from this catalogue. Two reasons, and both matter.

A fault is a judgement some rule made. Which rules run changes between releases and between tools,
and a rule can be switched off. A 5xx is something the API did, and it counts whether or not any rule
had an opinion about it. Counting faults would therefore measure our oracle set as much as the API.

And the catalogue has exactly one code for this, F100, whose description is about the status 500
specifically. There is no code for the 5xx family. Reporting a 503 under F100 would make our reports
incomparable with everybody else's under the same catalogue, which is the one thing this decision
exists to prevent.

So the count lives beside the fault list rather than inside it, and the fault catalogue is untouched:

- **Operations that answered 500**, and **operations that answered any 5xx**, as two numbers.
- Counted **over replies**, not over faults.
- Distinct **by operation**, not by reply and not by error message.
- Both spellings of that criterion are written into the report next to the numbers.

**One consequence is deliberate and worth stating.** The count of operations that answered 500 and
the number of F100 faults can disagree, and they are not measuring the same thing. An API that
answers 500 and then drops the connection mid-body counts here, because the status line arrived and
is complete; it produces no F100, because a reply RESTest could not finish reading is not evidence
about the API and the rule that judges replies says nothing about it. One number is about what the
API did, the other about what our rules could establish. A run can show a server error on an
operation with no fault reported for it, and that is the honest answer rather than a discrepancy.

The criterion is the one the field uses, checked rather than assumed. EvoMaster publishes two
statistics of exactly this shape, one counting endpoints with any 5xx and one counting 500s per
endpoint, and its fault identity deliberately excludes the response message. In black-box testing,
where the last executed line inside the service is unavailable, its count collapses to one per
operation, which is what is implemented here.

One caveat is recorded rather than resolved. The REST League 2027 rules describe uniqueness as
"sufficiently distinct error messages", and no reference implementation found does that: the tool its
footnote cites groups by operation, path and the set of parameter names, and compares no message at
all. The organisers should be asked which they compute. If the answer turns out to be message-based,
this amendment needs a second criterion beside the first, not a replacement: the two answer different
questions and a report can carry both.

