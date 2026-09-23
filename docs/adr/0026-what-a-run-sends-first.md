# ADR-0026: A run opens with every operation once, in five steps, and one part of the tool owns the clock

**Status:** Accepted
**Date:** 2026-09-23

## Context

The 2027 competition scores the area under three curves — operations answered 2XX, distinct server
failures, code covered — which rewards a tool for getting somewhere *early* as well as for getting
there. ADR-0017 measured the tool RESTest 1.x lost to and found that the ceiling was never the
problem: seven times less area on operations covered, for about two thirds of the final count. Roadmap
row 9.1 asks for the fix that costs least: before anything is chosen by chance, send every
operation once, with the request it is most likely to accept.

Until now nothing in the tool could do that. `RunLoop` went round the operations in the order the
document declares them and asked the generator for a test case each time; the generator drew a
strategy per request and filled the request by weighted choice among sources. Nobody owned the
order requests go out in, and nobody knew what time it was except the loop, which said of itself
that it was not a scheduler. ADR-0013 §6 had asked for exactly that component — *the only one that
knows the budget* — and ADR-0006 had listed a *phase finished* event that was never built.

The row as approved on 22 September said: required parameters only, the document's own samples
where it writes them, operations that create before operations that read, by method and then by
path depth, so that `POST /owners` has answered before `GET /owners/{ownerId}` is tried. Reading the
priority corpus against that text turned up three things.

- **kafka-rest-proxy's identifiers cannot be read from its document.** 49 of its 50 operations
  have `{cluster_id}` in their path. The document's sample is `cluster-1`, which does not exist;
  the real identifier is generated when the broker starts, and only `GET /v3/clusters` says what it
  is. Ordering by method first sends every `POST` — at any depth — before that read, and every one
  of them fails for want of an identifier.
- **A sample and a returned value disagree exactly there.** Ranking the document's samples first
  sends `cluster-1` everywhere; the memory of what the API returned (ADR-0021 §6) would have the
  real one once `GET /v3/clusters` has answered and been heard.
- **Most bodies are not marked required.** Across the 50 documents, 110 `POST` operations declare a
  body without `required: true` against 58 that mark it, 64 against 44 for `PUT` and 53 against 1
  for `PATCH`; in the priority corpus, kafka-rest-proxy's 12 write operations all leave it unmarked.
  OpenAPI 3 makes a body optional unless it says otherwise, and most authors never say. "Required
  parameters only", applied to the body, would send those operations nothing to work with.

The maintainer took the decisions below on 23 September, having asked for the ordering to be
simpler than the first proposal and for the whole behaviour to have a switch.

## Decision

### 1. The scheduler decides what is sent next; the loop sends it

`Scheduler`, in `restest-gen`, holds the deadline and the order. It is asked for the next step and
answers with one of four: send a request for this operation, wait for the answers still owed, a
round of every operation is complete, or the time is up. It sends nothing and draws no random
number: which operation comes next depends only on the document, the settings and the clock.
`RunLoop`, in `restest-cli`, keeps what it was good at — the slots, sending without waiting for the
previous answer, dealing with each answer as it lands, the pause while the reports catch up, the
wait for stragglers — and does what the scheduler says. It still reads the clock itself, to bound
its own waits against the scheduler's deadline, and it still stops a run early on its own evidence
— a round that could send nothing, an address where nothing answers — but *when the time is up* and
*what goes next* are the scheduler's. A test case is still built only once there is room to send
it, so a request is filled in from everything the API has said up to the moment it goes out.

After the first round, the scheduler goes round the operations in document order exactly as the
loop did, and asks the generator for an ordinary request, which draws its strategy per request
exactly as before. With the first round switched off, a run builds the test cases it built before
this record, in the same order, from the same seed: `RunOrderTest` pins two rounds of pet-clinic,
every value spelled out, written by the tool before the change. What goes on the wire differs only
by §5's header, on the operations that declare no successful media type.

### 2. The first round goes in five steps, and each step waits for the one before

| Step | Operations | Why here |
|---|---|---|
| 1 | reads whose address has no `{…}` — `GET /owners`, `GET /v3/clusters` | they show what is already there |
| 2 | every `POST` | they create, now able to name what step 1 found |
| 3 | reads whose address has a `{…}` — `GET /owners/{ownerId}` | by now there is something to name |
| 4 | every `PUT` and `PATCH` | changes, to things that exist |
| 5 | every `DELETE` | last, so nothing the round needed is gone before it was used |

Within a step, document order. A step with nothing in it is left out. Between one step and the next
the scheduler asks the loop to wait until every request of the step has been answered **and** until
what came back has been heard by every listener — an answer not yet taken in by the memory of
observed values is, for the next request, no answer at all. The wait is bounded by
`schedule.openingLapPatience`, two seconds by default and never past the deadline, so a request the
API never answers costs the round that long once; zero does not wait. The wait for the listeners is
a mark put on the event queue (`EventStream.awaitDelivery`), not a count of what is still waiting,
because a listener announcing a fault while it is being told something can make the count come out
even with the last reply still queued.

Only part of a wait shows as idle time, and that is right rather than an accident. While a step
waits on a slow answer a request is in flight, which is not the tool wasting time; once every answer
is in and the round waits for the listeners, nothing is, and that is counted. How long the round
took in all is its own line in the reports.

Whether an address "names one thing" is decided by whether it has a gap to fill, and nothing
cleverer. Across the corpus, 907 of 1,141 path gaps sit right after a collection's name
(`/owners/{ownerId}`); most of the rest are still keys (`/repos/{owner}/{repo}`), and a few are a
format or a date. Where the guess is wrong the operation goes one step later in the same first
second, which costs nothing; an identifier outside the path — in the query, in a body — is not seen,
which is 4.1's graph.

The row's own example holds: `POST /owners` is answered before `GET /owners/{ownerId}` is tried.
So does 9.3's rule that deletes follow the reads and updates of the same round.

### 3. The request it sends is the one the API is most likely to accept

`RandomTestCaseGenerator` builds it for any operation:

- **every parameter the API requires and none it does not**, with no draw for how many optional
  parameters to send;
- **a body wherever the document describes one**, required or not, for the reason in the context;
- **but no body a `GET` or a `HEAD` merely accepts**: HTTP gives such a body no agreed meaning, and
  the engine refuses to send one, so forcing it in would spend the operation's one request on a
  request that cannot go out. A `GET` or a `HEAD` that *insists* on a body is not in the round at
  all: RESTest cannot send it, and reports it among the operations that cannot be tested;
- **values from the plan's first strategy that does not push at the API, its sources asked in turn
  rather than chosen among**, in this order: a closed list of accepted values, because nothing
  outside it may be sent; what the API has already handed back; the lists somebody handed over; the
  document's own sample; its default; and last a value invented to fit. Sources the plan names on
  their own keep the place the plan gave them; only its groups are ranked. A source the plan does
  not name is not used — a plan without `observed` gets no memory here either.

Choosing among sources is right for a run that sends thousands of requests, because a value pinned
to one source for a whole run stops being varied: ADR-0023 §8 measured that on pet-clinic. It is
wrong for one request with one chance, which should carry the best value there is. What the API
returned comes before the document's sample because it names something that exists. It has two
costs, both of which the ordinary rounds already accept at a weight of twenty: a name the API uses
for something else, and — for a body — a creation built from a thing the API returned with one value
changed, which an API that keys its records on another field can refuse as a duplicate.

It is built from numbers of its own, a copy of the seed split once, never from the generator's own
source: asking for it must not move the sequence the ordinary requests are drawn from, or switching
the round on would change every request after it. With a plan that has no memory, the ordinary
rounds after the first round are the ones a run without it would have sent; `RunOrderTest` holds
that too.

A plan whose every strategy pushes at the API has no likeliest request and no first round.

### 4. The first round is a stretch of the run of its own, and the reports say what it bought

`RunEvent` gains `PhaseStarted` and `PhaseFinished` — the event ADR-0006 listed. The scheduler
announces the round with its first step, so a run whose time is up before the round began has no
round to report, and announces its end after the last step's wait — or as cut short if the time
runs out first or the run ends any other way, which is what `cutShort` means: ended before it was
done, for whatever reason. A request belongs to the stretch it was sent in,
matched by test case, so a late answer is still counted where it belongs; only requests still in
flight are remembered. Both reports take the tally from one shared place, as they do for server
failures: the console prints one line after the first line of the summary, and `report.json` writes
`phases`, each with when it began and ended, whether it was cut short, how many requests it sent,
for how many operations, how many of those answered 2XX, and its replies by class.

### 5. An operation that declares no successful media type is asked for anything

`Accept: */*` where 2.5a sent no header at all. RFC 9110 says the two mean the same; not every
server agrees, and saying it costs nothing. Not a switch: it changes what a request says, not what
a run does.

### 6. One switch, one number

`schedule.openingLap`, true by default, is set to false to turn the round off;
`schedule.openingLapPatience`, two seconds by default, bounds a wait.
The ranking of sources has no switch of its own: it differs from "samples first" only where a
sample and a returned value compete for the same name, which in the priority corpus is the
generated identifiers the ranking exists for. The round is the lever; how it builds its requests is
part of it.

### 7. What 9.1 does not do: shares as stretches of time

The row also absorbed 2.10b — a strategy's share honoured as a stretch of the budget rather than a
draw per request. It is not built. It moves none of the competition's measurements, and a strategy
chosen by the clock would make even a run with no memory unrepeatable from its seed, because which
request is pushed at the API would depend on how fast the API answered (ADR-0013 §7). The roadmap's
own rule sends the half of a grown row that moves no measurement to 2.1, and it goes there.

## Measurement

Against two containerised APIs of the priority corpus, each restarted before every run, five seeds,
sixty seconds a run, the round switched on against switched off on the same build, the two
alternating seed by seed so that neither always ran on the warmer machine. Operations answering 2XX
are counted from the moment the command was launched, which is the competition's clock too, and the
area is under that curve over the sixty seconds.

| API | Round | Area, 60 s | At 2 s | At 5 s | At 60 s | Idle |
|---|---|---|---|---|---|---|
| kafka-rest-proxy | off | 1,662 | 14.2 | 27.6 | 29.6 | 1.1% |
| kafka-rest-proxy | on | **1,938** (+17%) | **28.6** | 32.6 | **34.2** | 1.4% |
| kafka-rest-proxy | on, `openingLapPatience=0s` | 1,891 | 19.2 | 30.8 | 34.2 | 1.0% |
| pet-clinic | off | 1,579 | 0.8 | 19.6 | 30.8 | 1.6% |
| pet-clinic | on | **1,808** (+15%) | 6.0 | **30.8** | **32.2** | 1.7% |

Better on every seed for kafka-rest-proxy, by 155 to 388; better on four of five for pet-clinic, and
53 worse on the fifth, which ended its minute on 31 operations against 33 - a difference the ordinary
rounds decide, not the round.

On kafka-rest-proxy the round took about a second and had 28 to 30 of the API's 50 operations
answer it with a success. Without the waits it took a fifth of a second and had 11 to 18 answer,
because every request after `GET /v3/clusters` went out before that reply had been heard: the wait
is where the round's value comes from, which is why it is kept. On pet-clinic - an x86 image run on
an ARM machine, which is why its first second is empty in both columns - the round took about three
seconds and had 29 to 32 of 35 answer.

The round also raised where a minute ends up: 29.6 operations to 34.2 on kafka-rest-proxy, 30.8 to
32.2 on pet-clinic. The likeliest request reaches operations the ordinary rounds had not reached in
that minute - the ones that answer only when every optional thing is left out, or only with a body
the document never said was required.

What this does not show: the undisclosed half of the competition's APIs, the full hour, and the other
two curves. With the round switched off the tool is not quite the tool as it was before this record,
since §5's header changed what some requests say; the comparison is the round against no round.

## Consequences

- **ADR-0013 §6 is built**: the scheduler is the one component that knows the budget. The share
  between strategies is still drawn per request, inside the generator, until 2.1.
- **ADR-0015's description of the loop stops being true** — it said the loop was "not a scheduler:
  no weights, no phases, no strategy selection". The loop is still not the scheduler; there now is
  one.
- **ADR-0006's phase event exists**, and a listener that switches over every event handles two
  more.
- **ADR-0017's refusal of a preparation phase outside the budget stands.** The round is the first
  seconds of the budget, the part of its waits with nothing in flight is counted in the idle time
  like every other pause, and the reports say how long it took.
- **A run's first requests depend on the API's first answers** whenever the plan names `observed`,
  which the shipped plan does. That was already true of every request after the first; the seed
  still repeats a run whose plan has no memory, round included.
- **9.3 has a place to stand.** Its producer-then-consumer sequence is a step the scheduler can
  hand the loop; the round's ordering and waiting are indirect — the shared memory by name — and do
  not replace a sequence that carries one reply to one consumer, survives deletions and underlies
  10.3's operators.

## Alternatives considered

- **By method first, then by path depth**, as the row was written. Rejected on kafka-rest-proxy:
  every `POST` goes before the read that yields the identifier they all need.
- **Groups by how many gaps the path has, fewest first**, with create, read and update inside each
  and deletes last — the first proposal. It handles every shape the corpus shows, at the price of
  nine groups for pet-clinic and thirteen for kafka-rest-proxy, each with its wait. The maintainer
  judged it more mechanism than the gain warrants; the five steps keep what mattered — the reads
  that reveal identifiers go first, the reads that need them go after the creations — in a rule one
  sentence explains.
- **Four steps: every read, then every write, update, delete**, the maintainer's own simpler
  version. It sends kafka-rest-proxy's 32 reads that need `{cluster_id}` beside `GET /v3/clusters`,
  before its answer, and notebook-manager's read of one notebook before any notebook exists. Splitting
  the reads in two by whether the address has a gap was taken instead.
- **The document's samples first**, as the row was written. Rejected on kafka-rest-proxy's
  `cluster-1`; on pet-clinic, whose samples are real, the two rankings send the same thing.
- **A switch for the ranking.** Proposed, then withdrawn: it would change a request only where a
  sample and a returned value compete, and the round's own switch already makes the round
  measurable.
- **Order without waiting.** The loop sends concurrently, so without a wait a step's requests are
  built before the step before it has answered, and ordering buys almost nothing. The wait is kept
  and bounded; `openingLapPatience=0s` is the variant without it.
- **The round as a strategy in the plan file.** The plan says where values come from; the round is
  about when requests go out, which is the scheduler's. It uses the plan's sources and adds nothing
  to the format.
- **Shares as stretches of time now.** See §7.
