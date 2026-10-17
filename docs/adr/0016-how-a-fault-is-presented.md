# ADR-0016: A fault is identified by its catalogue number, described in our own words, and classified twice

**Status:** Accepted, amended at M1.10 and on 10, 12, 15 and 17 October 2026
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

## Amendment (10 October 2026)

**What changed in the catalogue and among the tools, checked against Web Fuzzing Commons release
0.11.0 and each tool's own source.**

- **Faults 0.9.0**, first published in release 0.10.0 on 5 October 2026, adds five kinds, among them
  `F121`, a 5xx other than 500 and 501, and `F312`, *Call Timeout*, no reply within a time limit. So
  the 5xx statuses other than 500 and 501 now have a code. RESTest still ships Faults 0.8.0 and still
  counts server errors beside the fault list, as the M1.10 amendment decided: the first of the two
  reasons it gives, that a fault is a rule's judgement and a 5xx is something the API did, still
  holds; the second, that the catalogue had no code for the family, no longer does. The 39 kinds of
  0.8.0, `F100` and `F200` among them, keep their codes and names in 0.9.0, which adds an `id` field
  as the stable key.
- **The tools RESTest is measured against no longer all use Faults 0.8.0.** Schemathesis 4.30.1
  writes Faults 0.9.0 codes in its WFC report; EvoMaster 6.2.0 uses Faults 0.8.0, and its development
  branch has moved to release 0.10.0, which carries Faults 0.9.0.
- **The caveat about distinct error messages, revisited.** Its "no reference implementation found
  does that" no longer holds. The 2027 rules count a 5xx as unique when its message is sufficiently
  distinct, citing ARAT-RL's paper, and that paper's own criterion (§IV-A) is a unique stack trace,
  or, where there is none, a unique response text once timestamps are removed. ARAT-RL's evaluation
  script, `parse_log.py`, implements it. What the tool writes in its own report groups by operation,
  path and parameter names, which is what the caveat found. So "the criterion is the one the field
  uses", above, holds for EvoMaster, not for the work the rules cite. ARAT-RL's README warns that the
  script's count has a bug, and recommends counting the operations that answered 500 instead, one
  of the two counts this record keeps. What the organisers compute has still not been asked;
  whether RESTest's report should carry a message-based count, as the caveat foresaw, remains a
  decision of its own.

Moving to Faults 0.9.0 is a change of its own: the test that compares RESTest's copy of the
catalogue with the published file will report the new `id` field until RESTest's copy carries it, and
the ids 0.9.0 gives faults 301 and 306 differ from the names `WfcFault` uses for them.

## Amendment (12 October 2026): Faults 0.9.0, and the key beside the number

**RESTest ships Faults 0.9.0**, copied from Web Fuzzing Commons release v0.11.0, and `report.json`
says `0.9.0` under `faultCatalogue`. The 39 kinds of 0.8.0 keep their numbers, names and labels, so a
count of `F100` or `F200` made by RESTest 2.0 to 2.2 compares with one made now as it is. The five
kinds 0.9.0 adds — `F121`, `F122`, `F207`, `F311` and `F312` — are in RESTest's copy, and no rule
reports them yet.

**Every kind now has two names, and each has a role.** 0.9.0 gives each kind an `id`, such as
`HTTP_STATUS_500`, which the catalogue says is meant never to change and would change only in a new
major version, while it says a number "might" change between minor versions, as numbers did at
0.8.0. This record's decision stands: within one
version of the catalogue the number is a fault's identity, what is counted by, and what a person
reads and writes. The id is written beside the number wherever `report.json` writes one, as the key
for matching a count with one made under another version of the catalogue from 0.9.0 on; a report of
RESTest 2.0 to 2.2 has none, and its `F100` and `F200` compare by number. A kind RESTest might
define for itself, in the 900s the catalogue leaves to tools, has no key in the catalogue; its key is
`RESTEST_` followed by its number unless it gives one, as Schemathesis prefixes its own. That is what the tools
that use the catalogue do, checked on 12 October 2026: EvoMaster, whose authors keep the catalogue,
switches its rules off by number (`--disabledOracleCodes`), and its export in the catalogue's report
format writes the number and no id; Schemathesis 4.30.1 tells faults apart by number and a context
naming the operation and the failure, and writes both in that export. The console shows the number and the name, as before. In RESTest's
copy each kind is named by its id, which gave two of them new names.

**Server errors stay beside the list.** `F121` now names a 5xx other than 500 and 501, so the second
reason the M1.10 amendment gives for counting them apart, that the catalogue had no code for them, is
gone, as the amendment of 10 October said. The first still holds: a fault is the judgement of a rule
that can be switched off, a 5xx is something the API did. `serverErrors` keeps counting operations
that answered 500 and any 5xx whichever rules run, and a rule that reports `F121` would add faults
beside it, not replace it.

## Amendment (15 October 2026): a server error other than 500, and a broken request accepted

**Date:** 2026-10-15

**`F121` is reported, beside `serverErrors` and not instead of it.** A rule reports *HTTP Status 5xx
Other Than 500 And 501* for every reply from 502 to 599, with a switch of its own,
`checks.otherServerError`, on by default. The 12 October amendment said such a rule would add faults
beside the count of server errors rather than replace it, and that is what it does: `serverErrors`
still counts every operation that answered any 5xx, whichever rules run. 500 stays `F100`'s, so no
reply is both; 501 is left unreported, as the catalogue's name keeps it apart, and a code of 600 or
more is no server error whatever its first digit.

The codes are the ones Schemathesis 4.30.1 writes in its export to the catalogue's report format —
500 as `F100`, 501 as `F205`, the other 5xx as `F121` — whose maintainer added `F121` to the
catalogue. CATS, RESTler, RestTestGen, AutoRestTest and RESTest 1.x report the other 5xx too, all
but RESTler under the same name as 500; EvoMaster reports only 500. Checked in each tool's code on
15 October 2026.

What the M1.10 amendment says about 502, 503 and 504 still holds: they are often what a gateway or a
load balancer says, or what an API says when a service it calls is down. The rule's sentence says
so, and [the faults](../faults.md#f121-a-server-error-other-than-500) call it weaker evidence than a
500. A 503 that carries `Retry-After`, asking the run to wait, is reported like any other, decided by
the maintainer on 15 October 2026: no tool checked exempts it, and until RESTest waits when it is
told to, [the known limitations](../known-limitations.md) say that a 503 the run's own pace provoked
is reported too. On fifteen APIs of the Web Fuzzing Dataset run locally, a minute each, none answered
a 5xx other than 500 and 501, and one answered 501 16,743 times; that is the catalogue's `F205`,
which no rule reports yet.

**`F206` names in its sentence the change that was accepted.** A request broken on purpose and
answered with a success is reported as *Received Success Response When Sending Wrong Data*, and the
sentence under it is the change, as the request recorded it: *the API accepted a request it should
have refused: left out the required body.photoUrls*. No word is written in a fault's `context` for
it: the change is already in every fault `report.json` writes out, under the request, and nothing
yet reads a context. [ADR-0027](0027-changing-one-thing-in-an-accepted-request.md)'s amendment of the
same day says which requests the rule judges.

**A correction to the 12 October amendment.** Its "a report of RESTest 2.0 to 2.2 has none" means
2.0 and 2.1: Faults 0.9.0 came after 2.1.0, so 2.2.0 is the first release whose reports write the key.

## Amendment (17 October 2026): a request that got no reply in time

**Date:** 2026-10-17

**`F312`, *Call Timeout*, is reported for a request sent on a connection open to the API that got no
reply, not even its status line and headers, before RESTest stopped waiting** — a `TransportFailure`
whose wait is
`REPLY` ([ADR-0005](0005-interpreted-test-model.md)'s amendment of the same date). The rule has a
switch of its own, `checks.noReplyInTime`, on by default, decided by the maintainer on 15 October
2026. Its sentence says how long the attempt took: *no reply within 30.0s: a connection to the API
was open, and RESTest stopped waiting before a reply's status line and headers came back*. A request is one fault, as
for every kind; such a fault has no status code, so its class is `noReply`, which `faultsByStatus`
already knew, and the screen says `no reply` where the status code goes.

Not reported: a connection that could not be made in time, since the request never reached the API
and a wrong or firewalled address, whose packets go nowhere, looks the same; a connection refused or
cut; and a reply whose status line and headers arrived and whose body never finished, which is what a
stream looks like. The first two are counted under `noReply` in `replies.byClass`, as every request
with no reply always was; the last is counted under its status code, as every reply that broke off
always was.

**What the catalogue and the tools say, read in their code on 15 October 2026.** The catalogue says
`F312` "might point to a denial-of-service vulnerability", that the environment can slow an API too,
so a person has to check, and that the limit should fit the API's expected performance; it was added
by Schemathesis's maintainer, and the catalogue's maintainer has said its security family may be
reworked before its version 1.0. The tools differ:

- **CATS** gives a read, a write and a connection timeout codes of its own, 954 to 956, and reports
  them as errors; **RESTler** turns a timeout into the status code 599 and reports it as a bug.
- **EvoMaster** does not use `F312`. Its own experimental code 960, *Request Timeout*, reports a call
  that ran out of its 30-second read timeout, in black-box runs only, and is off by default.
- **Schemathesis 4.31.1** reports a request that got no reply as an error of the run, not a failed
  check, and never as `312`; its export to the catalogue's report format writes `312` for its
  `max_response_time` check — a reply that came, but slower than a limit given — which is off unless
  a limit is given.

So `312` means two things among the tools that use the catalogue, and RESTest's meaning is the one
its name says: the call ran out of time. A reply that arrives, slower than a limit, is a different
rule, which RESTest does not have.

**On by default**, as CATS and RESTler report it, because a request an API sits on for half a minute
is something its developer wants to know. On fifteen APIs of the Web Fuzzing Dataset run locally for
a minute each, their stored runs read back, two APIs had requests that ran out of time with a
connection open and no reply: 42 requests on six operations, every one at the 30-second read
timeout. One API's `fetch` operation gathers data from GitHub and waits on it; the other hung on
operations about a cluster that does not exist, instead of answering 404. Run again with the rule, a
minute each, the two reported 30 and 15 `F312` on seven operations: the first on its `fetch`, the
second on the same five operations and on one that sends a batch of records, which hung before any
reply in this run. The second's streaming endpoint, which answered 200 and then sent nothing, and its
connections cut with nothing sent back, were not reported. Both APIs also answered 500, so neither
run changes its exit code; a run whose only fault is one operation that hangs now ends with `1`
where it ended with `0`.

**It is not one of the security oracles [`docs/DESIGN.md`](../DESIGN.md#out-of-scope) keeps out of
scope**, although the catalogue numbers it among its security kinds. Those send requests made to
attack an API — an injection, a forged request, a bypassed authorisation. This rule sends nothing and
judges an outcome every run already records.

**The exit codes are unchanged.** A run in which nothing at the address answered still ends with
`3`, even when it reported `F312` for every request: something that accepts connections and never
speaks HTTP does exactly that. Its closing line then says how many of the requests were sent on an
open connection that never got a reply, beside the advice to check the address, so that neither
message contradicts the other. The run counts them itself, as it counts the requests answered, so the
line says so whether or not the check that reports them was made.

## Amendment (17 October 2026): a 501, and two kinds RESTest numbers itself

**Date:** 2026-10-17

**Three more kinds are reported, each with a switch of its own and on by default**, decided by the
maintainer on 17 October 2026:

- **`F205`, *no-501-if-implemented***: a reply of 501 to an operation the document declares, whether
  or not the document lists 501 among its replies, as EvoMaster reports it and Schemathesis exports
  it (`checks.notImplemented`). Its sentence: *the API answered 501, not implemented, to an operation
  its document declares*.
- **`F920`, a status code the document does not declare**: below 500, for an operation that declares
  at least one reply, a code counting as declared when the document names it, its range or a
  `default` (`checks.undeclaredStatusCode`). Its sentence names what the document declares, in its
  order: *the API answered 404, which the document does not declare for this operation; it declares
  200, 400, 401, 403, 429, 5XX*.
- **`F921`, a content type the document does not declare**: a reply with a body, with a status code
  below 500 the document declares with at least one media type other than `*/*`, whose
  `Content-Type` is not covered — exact, `type/*` or `*/*`, case and parameters aside — or is missing
  (`checks.undeclaredContentType`). `application/problem+json` is not `application/json`. The value
  is quoted cut at 100 characters, with control characters and invisible formatting characters
  replaced, since the API writes it.

`F920` and `F921` are RESTest's own numbers, in the range the catalogue leaves to tools
([ADR-0036](0036-kinds-of-fault-restest-numbers-itself.md)); their keys start with `RESTEST_`.

**No reply counts twice over its status code.** Neither new rule judges a 5xx, which `F100`, `F205`
and `F121` report, so no reply is both a server error and an undeclared status code or content type.
EvoMaster and Schemathesis do count an undeclared 500 twice. A reply can still be two faults where
two rules ask different questions: `F100` and `F200`, as before; `F206` and `F920`, a request broken
on purpose accepted with a success code the document does not declare; `F206` and `F921`. `F920`
and `F921` never meet, nor `F200` and `F921`: the content type is judged only under a declared
status code, and the shape only under a declared content type.

**What is left alone, so that nothing uncertain is claimed:** an operation the document does not
declare; one that declares no reply at all; one RESTest read only in part — any operation a
`DEGRADED` issue names, since a reply it declares and RESTest could not read would otherwise look
undeclared; a reply to a request whose body went under a media type the operation does not accept
([ADR-0027](0027-changing-one-thing-in-an-accepted-request.md)'s amendment of the same day); and for
`F921`, a reply with no body. The first is coarser than it needs to be: an operation renamed because
the document repeats its identifier is `DEGRADED` too, and loses the check with nothing wrong in its
replies. And a reply the parser drops on its own, written as something other than an object, is not
known to be missing at all, and looks undeclared.

**What one minute on each of fifteen APIs of the Web Fuzzing Dataset said**, seed 42, 3,553,050
requests, each run's counts checked against a replay of the three rules written apart from
RESTest's code, which agreed on every operation of fourteen APIs and on the fifteenth but for the
eight operations the coarseness above skipped:

| Kind | Faults | APIs | What there is to fix | Read by hand |
|---|---|---|---|---|
| `F920` | 1,443,634 | 13 | 205 pairs of operation and status code: 400 in 93, 404 in 59, 204 in 14, 422 in 9, 201 in 7, 302 in 6, 403 in 6, 406 in 4, 414 in 3, 415 in 3, 409 in 1 | 20 of 20 real: refusals the document leaves out, a 204 where a generated document says 200 |
| `F921` | 16,933 | 2 | 44 pairs of operation, status code and type: `application/problem+json` for `application/json` in 25, `text/html` error pages in 19 | 20 of 20 real |
| `F205` | 21,419 | 1 | 1 operation, which answers 501 when it has nothing to return | 5 of 5 real |

One run's exit code changed: bibliothek's only faults are `F920`, 580,699 of them on 14 pairs, so it
ends with `1` where it ended with `0`. Every other run already ended with `1`. The new kinds are up to
87% of a run's requests, and the screen writes out the first fifty faults of any kind, so on one API
the fifty were all `F920` and `F205` and none of its 10,902 replies of 500
([the known limitations](../known-limitations.md#what-a-run-says) say so).

**What they cost is below what a minute's run can measure.** On bibliothek, where 87.5% of the
requests met one of the new kinds, three runs with the three checks on and three with them off,
alternated, a minute each, sent 0.3% more requests on average with them on (pair by pair +1.7%,
-0.7% and -0.3%), and were idle about 1% of the time either way. The same on swagger-petstore, where
none was met, moved by -1.5%, -15.4% and -0.5%: a difference of that size is the noise of one-minute
runs, not the checks. A single run of each of the fifteen APIs with the checks off sent 6.9% more
requests in all, swagger-petstore again among the largest differences with nothing to report.

**A correction the same day: an operation renamed is judged.** Of the two caveats under *what is
left alone*, the first no longer holds. An operation RESTest renames, because the document gives its
identifier to another one RESTest reads first, is recorded with an effect of its own and counts as
read whole, so `F920` and `F921` judge it; and whatever RESTest could not read about one of two
operations sharing an identifier is said of that one, never of the other
([ADR-0012](0012-canonical-model.md)'s amendment of the same day). The fifteen APIs run again with
the change, a minute each, seed 42, 3,760,792 requests: the replay agrees with every run,
scout-api's included, and no exit code changes. scout-api, the only one of the fifteen that repeats
identifiers, gets `F920` on the eight operations that had been left alone, four `POST` and four
`PUT`, on 20 pairs of operation and status code — 400 in 8, 403 in 8, 404 in 4 — 33,726 of its
65,924 `F920`; run alone, 29,872 in 184,016 requests. Elsewhere the pairs moved only as runs of a
minute do: market's from 6 to 10, with 67% more requests sent, and pet-clinic's from 27 to 26. In
all, `F920` came to 1,539,973 faults on 228 pairs; the table above is left as it was measured. On
scout-api no renamed operation is also one RESTest read in part, so the replay's agreement says the
two rules judge those eight operations as they are defined to; that what could not be read is said
of the right operation is shown by RESTest's tests, not by this run.
