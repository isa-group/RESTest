# ADR-0041: A run stops sending when the API answers 401 to every request of its opening lap, and ends with `5`

**Status:** Accepted
**Date:** 2026-10-28

## Context

A run handed no key, or a key the API will not take, sends its whole budget of requests to an API
that refuses every one of them, and ends as if it had found nothing wrong. Run against arimaa, one
of the APIs of the Web Fuzzing Dataset run locally, without its key, RESTest 2.3.0 sent 337,262
requests in 20 seconds, some 17,000 a second; 336,684 were answered 401, and the run ended with `0`.
That is two failures at once. The API is flooded by a tool that can learn nothing from it, and the
number a build reads says the API passed.

RESTest says before its first request which keys the document asks for and nobody handed over, but
only for API keys, and only as a line among others. [Known limitations](../known-limitations.md)
said it plainly: a run does not stop when every request is refused.

None of the related tools stops a run because its credentials are refused. Schemathesis warns, after
the run, about an operation 90% of whose requests answered 401 or 403. CATS warns when half of all
its tests did. EvoMaster stops by itself only when connections are refused. RESTler and RestTestGen
deal with a key that expires another way: the user gives them a command that fetches a fresh one,
and they run it again from time to time.

## Decision

### 1. The rule: the whole opening lap, every request answered, every answer 401

The opening lap is the first round of a run ([ADR-0026](0026-what-a-run-sends-first.md)): every
operation sent once, each with the request the API is likeliest to accept. The run stops sending
when three things hold:

- every request the lap sent was answered;
- every answer was 401;
- no reply heard by then to a request sent after the lap was anything else.

**401, and not 403.** A 401 says "who are you?", whatever was sent. A 403 can mean "you, but not for
this": familie-ba-sak, signed in as a role with few rights, answers 403 to 92% of its requests with a
key that works.

**A whole lap, never one operation.** One operation that answers 401 to one request may answer
something else to the next. Every operation refusing the request it was likeliest to accept is a
refusal of the whole API. Any other reply rules the stop out, because it means the run can still
learn something. That covers an operation anybody may call, a 400 for a value the API checks before
it asks who is calling, and a 500. Without its key, pay-publicapi answered 500 to 16,583 requests.

**A request of the lap that got no reply rules it out too.** The connection may have been refused or
cut, or the reply may never have come. An API that refuses the first connections while it starts up
would otherwise be stopped on the few 401s that came after them. A reply that never came is a fault
of its own (`F312`), and a run that met one goes on to end as it always has.

**Asked once.** The question is asked when the lap is over and every one of its requests has
settled: answered, or given up on by the engine. It is never asked again.

- **A slow answer still counts.** The lap waits for each of its steps only for
  `schedule.openingLapPatience`, so an operation slower than that answers after the lap has gone on.
  Until it has, the run goes on sending as usual. If its answer is not a 401, the run does not stop.
- **A stop is never decided later in the run.** Asked only once, a run whose lap nothing answered
  cannot be stopped later by the first 401 it hears.

**A 401 that depends on what RESTest invented still counts.** A sign-in operation can answer 401 to
a user name and password RESTest made up. When nothing else in the whole lap was accepted either,
the run has nothing to test that the key would not open.

**A run without an opening lap is never stopped this way.** That covers `schedule.openingLap` off, and
a plan that cannot build the request each operation is likeliest to accept. So is a run whose lap
never ends, because its budget or `schedule.requestsBeforeStopping` runs out first. Each of these
ends as it did before.

**Where it is decided.** `RefusedOpeningLap` answers the question, told by the loop the moment each
request settles. It is never told by listening to the run's announcements, which reach their
listeners later. Each settled request is one call under one lock, saying what came back and that it
settled. So the question cannot see a lap settled before it has heard what the last request of it
brought back.

### 2. What a stopped run does and says

- **What it does.** It stops sending, waits for the answers it is owed as at the end of its time,
  writes what any run writes, and ends with `5`. The requests already on their way when it decided,
  at most as many as may be in flight, are counted with the rest, whatever they bring back.
- **The summary.** Its first line ends with `, stopped: the API answered 401 to every request of the
  opening lap`.
- **The error stream.** A line there, before any other but the one for a run stopped from outside,
  says three things:
  - how many requests the lap sent and how soon the run stopped;
  - which key the run held and where it went, never the key itself, with a reminder that the key may
    be wrong or expired;
  - when the run held no key, or the operations no key went with ask for one, how each such key is
    handed over: an API key with `--auth`, an HTTP bearer token or user and password as the header
    they are, an OAuth 2 or OpenID Connect token as a bearer header. RESTest does not yet sign in by
    itself to get a token, and the line says so.
- **`report.json`.** It writes `"verdict": "unauthorized"`. Beside the verdict, `unauthorized` says
  when the run decided, how many requests the lap sent, and where each key the run held went, in the
  shape `keysNotGiven` already has. For every other run it is `null`.
- **The page.** It gives the number and the reason from the verdict, as for every other verdict.
- **`cutShort`** keeps its meaning, a run stopped from outside. A refused run that is then stopped
  from outside, while it waits for its last answers, is `stopped` and ends with `130`, and still
  carries `unauthorized`.

### 3. `5`: the API stopped the run

[ADR-0015](0015-command-line-contract.md) freezes what every exit code means for 2.x, and lets a minor
version add "a number for something that has none yet". This had none.

**Why not `3`.** `3` says nothing was tested. A refused run had every request answered, and widening
`3` would change what it means.

**What `5` means.** The maintainer gave it a broad meaning on 28 October 2026: *the API stopped the
run before its time was up — it would not let the run test it*. The verdict says why. Today the one
reason is `unauthorized`. A later reason will be a new verdict word ending with `5`, not a new number:
an API that keeps asking the run to wait, or that stops answering.

**Order of the questions.** The verdict is asked right after `stopped`, as a stop from outside is:
whatever the run found until it was stopped is in its reports, and the number says why it ended.

### 4. One switch

`schedule.stopWhenTheOpeningLapIsRefused`, on by default. It changes when a run ends, not what it
sends, so it plays no part in an ablation. It sits beside `schedule.stopAfterRequests`, which also
decides when a run ends ([ADR-0025](0025-settings.md)). The rule has no number to tune: the stretch
is the lap, and 401 is a fact, not a choice.

## The evidence

Before it was built, the rule was replayed apart from RESTest's code over stored runs of the APIs of
the Web Fuzzing Dataset run locally, in the order their replies arrived, with RESTest 2.3.0.

| Runs | How many | The opening lap refused whole |
|---|---|---|
| With their keys, 60 s each | 52 | none |
| The last two evaluation campaigns' runs | 32, of 16 APIs | none: no request of their first round was answered 401 |
| Without their keys, 20 s each | 23 | 2: arimaa (38 of 38 answered 401, stopped at 0.06 s) and quartz-manager (9 of 9, at 0.18 s) |
| With every operation, signing out included, 60 s each | 4 | none |

The other 21 runs without their keys go on. Their operations that anybody may call still answer 2XX,
or the API answers some requests with 400, 404, 500 or a redirection to its sign-in page, and those
runs can still learn something.

## Alternatives considered

- **A stretch of the last *N* replies, judged after the lap as well**, as the plan first said, to
  stop a run whose key stops working part-way. It was measured.
  - *N*=100 stops komga with its keys after 6.7 s. That run changed its own password in its opening
    lap, a real loss. But it also stops, by chance, three runs without keys whose public operations
    still answered 2XX: tiltaksgjennomforing at 0.23 s, familie-ba-sak at 2.85 s and komga at 14.7 s.
  - *N*=500 stops only runs where nothing is accepted any more. testing-system, signing itself out,
    stops at 0.66 s and commafeed, changing its own password, at 0.87 s. It no longer stops komga,
    whose sign-out operation answers 2XX to anybody.

  The longest stretch of 401s in an ordinary run was 29. The maintainer chose the version without it.
- **A looser stretch: a window with no 2XX and most replies refused.** It would also have stopped
  lovemining and pay-publicapi without their keys. pay-publicapi had found 16,583 replies of 500 by
  then, and the loose rule would have cut it short at 0.63 s.
- **Counting 403.** familie-ba-sak, above.
- **Widening `3`.** Above.
- **Only a warning at the end**, as Schemathesis and CATS give. It costs nothing to a run that should
  go on, but it leaves the flood and the passing number in place. It would still help a run without
  its key whose public operations answered, which this rule rightly lets go on. 8 of the 23 runs
  without keys had 80% to 94% of 401s and ended with `0` or `1`.

## Consequences

- **A run whose whole opening lap is answered 401 ends with `5` and the verdict `unauthorized`.**
  With 2.3.0 it ended with `0`, or with `1` when it reported the undeclared 401s (`F920`) and
  `checks.failOnWarnings` was on. `schedule.stopWhenTheOpeningLapIsRefused=false` gives the old
  behaviour back.
- **`report.json` gains a key, `unauthorized`,** and a verdict word.
- **An experiment that turns the opening lap off turns this stop off with it**
  ([ADR-0026](0026-what-a-run-sends-first.md)'s amendment of today). Against an API that refuses the
  run, that experiment measures this stop as well as the lap.
- **An API that lets a caller in only after a while is stopped.** Examples: a proxy in front of it
  that signs callers in, or a key that takes a few seconds to become valid. The switch is the remedy,
  until RESTest can sign itself in.

## Still open

- **A key that stops working part-way.** It may be revoked, may expire, or may be taken away by
  something the run sends, such as signing out or changing its own password. testing-system, run
  with every operation, signs itself out after 0.43 s and receives 701,152 refusals in a row until
  its time is up. The stretch of *N* above is measured and waiting.
- **403**, the 302 to a sign-in page, and a run without a key whose public operations still answer.
  None is stopped, and none says at the end that most of its requests were refused.
- **Signing in.** Once RESTest can sign itself in, the requests before the sign-in should not count.
