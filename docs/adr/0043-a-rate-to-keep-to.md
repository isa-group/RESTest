# ADR-0043: A run can be given a rate to keep to, in one line, and keeps to it evenly where it sends

**Status:** Accepted
**Date:** 2026-10-29

## Context

What bounds a run today is how many requests it has in flight: sixteen at most
(`engine.maxConcurrency`), fewer when answers slow down or go missing. Against an API on the same
machine that is thousands of requests a second: blogapi, run without its key for 20 s in
[ADR-0042](0042-wait-when-told-to-wait.md)'s evidence, received 149,242. That is right for an API
somebody runs to test, and wrong for a staging server somebody else runs and lets them test, whose
owner wants to be promised a rate. Nothing in RESTest could make that promise:
[known limitations](../known-limitations.md) said so, and pointed at `engine.maxConcurrency=1` as the
nearest thing, which bounds how many requests wait at once, not how often one goes.

Among the related tools, a rate is common and off by default:

- **Schemathesis**, `--rate-limit 5/s`, `100/m` or `1000/h`, through the pyrate-limiter library's
  sliding window: at most that many in any window of that length, which lets all of them go at once
  and then waits. One limit per host the run sends to.
- **EvoMaster**, `--ratePerMinute 600`, a whole number, zero or less for none: it sleeps a minute
  divided by the number between one action and the next, so the actions go evenly.
- **ZAP** has a rate in its network options. **CATS** alone keeps one always on, at 10,000 requests
  a minute.

## Decision

### 1. Two settings, and naming the rate turns it on

- `engine.limitTheRate`, a switch, off by default: a rate on by default would slow every run to
  protect the few pointed at somebody else's server. A default that switched on for any address
  but the tool's own machine was considered and set aside: APIs under test often run in containers
  at addresses that are not the machine's.
- `engine.mostRequestsPerMinute`, a whole number of at least one, `600` by default.

Per minute and a whole number, as EvoMaster writes it: exact for any limit of so many a minute, and
five a second is `300`. The maintainer chose it on 29 October 2026 over Schemathesis's `5/s`,
`100/m`, `1000/h` and over requests a second with decimals, which writes a limit of a hundred a
minute as `1.6667`, slightly over it. Ten a second by default, of the order of what APIs commonly
allow one client: slow enough for any staging server, quick enough for the opening lap of an API of
thirty-five operations, as pet-clinic is, to take under four seconds.

**Naming a rate limits the rate.** When nobody names the switch, it is on once
`engine.mostRequestsPerMinute` is named in any of the four places a setting is given
([ADR-0025](0025-settings.md)), and recorded as *worked out*, as `engine.callTimeout` is when one of
the waits inside it is named. So `--set engine.mostRequestsPerMinute=300` is the one line that
promises a rate. A rate typed and then ignored because the switch was not typed too would let the run
go as fast as it can while whoever typed it believed the promise made. The switch named outright
still decides, either way: `limitTheRate: false` beside a rate leaves the rate alone, and
`limitTheRate: true` alone keeps to ten a second.

A printed settings file writes the switch out, `limitTheRate: false` beside
`mostRequestsPerMinute: 600`, so changing only the number in it keeps no rate. For that case the run
says so under its first lines whenever the rate is off and the number is not the default:
`engine.mostRequestsPerMinute is 300, but engine.limitTheRate is false, so no rate is kept to`. The
switch is not added to the settings that follow others and are printed as comments: that list holds
settings that follow other settings' values, and this one follows whether another was named.

A rate of `0` is refused, saying that `engine.limitTheRate=false` is how a rate is left alone: zero
is what somebody tries for "none", and a number set to zero says nothing about switching
([ADR-0025](0025-settings.md)'s M11.2 amendment).

### 2. The promise: never two requests closer than a minute divided by the rate

At `600`, never two closer than a tenth of a second. The first request goes at once, and each turn
is counted from the request before it, never from the turn that was due, so a quiet spell is never
made up for with a burst: never six hundred in the first seconds and then nothing, which is what a
sliding window allows and what the maintainer asked about. The promise is not "at most so many in
any one minute": a window that includes both of its ends holds one more.

### 3. One schedule, in the engine, and a loop that builds a request when it can go

- **The engine keeps the turns.** `ConcurrencyLimiter.acquire()` gives no slot to a request before
  its turn, however many slots are free, under the same lock as ADR-0042's hold, which comes first.
  A request waiting for its turn holds no slot and earns nothing for the limiter, as one waiting out a
  hold does. The first turn is the moment the limiter is built, so the first request goes at once
  wherever the clock happens to begin.
- **It says how long one more request would wait.** `HttpEngine.untilItsTurn()`: until the next turn,
  and one turn more for each request handed over and not yet given a slot. The engine counts a
  request handed over on the thread that hands it over, before it leaves, so that the next question
  already counts it. Room among the requests in flight is not counted: that depends on answers
  nobody can foresee.
- **The loop asks, right before it builds anything**, beside the whole API's quiet: if the answer is
  more than nothing, it gives its slot back, sits the wait out, to the deadline or a stop at most, and
  looks again. Requests are then built when they can go, and do not pile up inside the engine with
  values that were fresh when they were made. When the API answers more slowly than the rate allows,
  requests wait in the engine for room among those in flight, as they do with no rate; the loop's
  answer counts them, so it hands over one more only a turn per request already waiting, and that
  queue stays about as long as the API is behind, up to `schedule.workAheadFactor` ×
  `engine.maxConcurrency`. It empties once the API catches up.

### 4. At the end of the run, what waits for its turn is not sent

When the run stops sending, `HttpEngine.dropWhatIsHeld()` lets go every request waiting for a turn
that has not come, now or later, as it already lets go what a hold of the whole API holds, so that
nothing the rate held back reaches the API after the run's time is up. Each comes back as one that
never went out, and is counted with the requests that got no reply. A request waiting only for a
slot, its turn already come, still goes, as it does today. With the loop of §3 there is hardly ever
more than one. Decided by the maintainer on 29 October 2026.

### 5. What a run says

- **The engine counts the idle time the rate made**: the time with nothing in flight after a request
  went out and before the next was allowed to, in which nothing could have been sent however quick
  the tool was. Idle time after the turn came is the tool's own.
- **`report.json`**: `idleKeepingToTheRate`, under `engine`, `PT0S` with no rate given.
- **The summary**: the idle share on its first line is followed by that part, beside what the API
  asked for. Against pet-clinic for 30 s at 300 a minute it read
  `145 requests to 43 operations in 30.0s, 85% of it idle (81% keeping to the rate it was given)`;
  with both, `(<q>% waiting as the API asked, <r>% keeping to the rate it was given)`. A part that
  rounds to none is left out. The reasoning is
  [ADR-0009](0009-non-blocking-engine.md)'s amendment of 29 October: idle time exists to catch a slow
  tool, and quiet somebody asked for is not that. The two parts can overlap, by at most one turn for
  each time the run sits out the API's asking.
- **The page** shows the idle share as before.
- **The exit code** does not change.

### 6. A switch, but not a lever

The switch is listed among those that are not levers: a rate is a courtesy to the API, not a way of
finding more, so it plays no part in an ablation. It changes when requests go, and so how many fit
in a run's time, and makes nothing a run sends depend on the API's replies, so the file for getting
the seed back leaves it as it is.

## The evidence

Off by default, so no run changes unless somebody names a rate, and there were no recorded runs
with a rate to replay before it was built. Measured once built, on one
machine, against two APIs of the dataset of local APIs, run from scratch for 20 s each with the
same seed and every request stored. The gaps are between the moments RESTest recorded sending one
request and the next:

| Run | Requests | Shortest gap | Most in any one second | Idle share, and the part the rate made | Last request sent, against the time being up | Never sent at the end |
|---|---|---|---|---|---|---|
| blogapi, 30 a minute | 10 | 2,000.6 ms | 1 | 98% (96%) | 1.448 s before | 0 |
| blogapi, 300 a minute | 95 | 200.7 ms | 5 | 92% (87%) | 0.205 s before | 0 |
| blogapi, 3,000 a minute | 810 | 20.3 ms | 43 | 75% (56%) | 0.003 s before | 0 |
| petclinic-rest, 300 a minute | 95 | 201.2 ms | 5 | 92% (86%) | 0.174 s before | 0 |
| blogapi, nothing named | 70,196 | under 0.1 ms | 4,388 | 3% | 0.003 s after | — |
| petclinic-rest, nothing named | 98,112 | under 0.1 ms | 7,722 | 5% | 0.003 s after | — |

At 3,000 a minute blogapi got 810 of the 1,000 turns its time held: the opening lap waits for the
answers to each of its steps, and some answers took longer than a turn.

With nothing named, the run is as it was. Against `master`, in pairs with the same seed and
alternating order:

| | `master` | This change |
|---|---|---|
| petclinic-rest, seeds 1, 2, 3: requests | 115,061; 107,686; 104,623 | 104,566; 105,596; 110,442 |
| petclinic-rest: idle share | 2.3%; 2.3%; 2.1% | 2.3%; 2.2%; 2.3% |
| blogapi, seeds 1, 2, 3: requests | 70,008; 75,433; 68,661 | 59,958; 74,322; 68,663 |
| blogapi: idle share | 1.9%; 1.9%; 2.1% | 1.6%; 1.7%; 2.2% |

Requests vary between runs of one version as much as between the two: petclinic-rest's three runs
of `master` span 104,623 to 115,061. blogapi's first pair is the widest apart, and its third the
closest, two requests in 68,661. An engine with no rate takes no lock it did not take before, and
reads the clock once more, when it is built.

**Why the engine keeps the turns, and not the loop alone.** A stand-in with four requests in flight
held their answers for 0.3 s and then gave them all at once, to an engine keeping to 600 a minute
with eight requests handed over (the engine's own test, `KeepingToARateTest`). With the engine's turn, the four that had waited went out 102 to
106 ms apart. With the turn taken out of the engine, as a rate kept by the loop alone would leave
it, they went out 12 to 157 microseconds apart.

**Where the API sees them.** The rate is kept where RESTest sends. Seen from a stand-in that records
when each request arrives, the first two of a run kept to 600 a minute arrived 41 ms apart, every
later pair 101 to 107 ms: the first request opens the connection and arrives late, and the second,
sent a turn after it, does not.

## Alternatives considered

- **One number and no switch, with `0` or a negative number for "none"**, as EvoMaster does. One line
  and no trap, but the only setting of the hundred and seven with a value that means "off", against
  the rule given with the switches on 29 September 2026, and missing from the page of switches,
  whose test finds a switch by its kind.
- **A plain switch and a number, as `schedule.stopAfterRequests` is**, where the number alone does
  nothing. Two `--set` options for one promise, and a rate typed without the switch would be ignored.
- **Both lines printed as comments until somebody names one**, so that a printed file whose number is
  changed keeps the rate too. A new rule in how settings are printed, for a case the line under the
  first lines already catches.
- **`N/s`, `N/m`, `N/h`, as Schemathesis writes it**, which copies a limit as an API publishes it. A
  new kind of value in the settings, with its own reading and writing. **Requests a second with
  decimals**, which needs no new kind but writes a limit of a hundred a minute as `1.6667`. The
  maintainer chose per minute.
- **A sliding window, as Schemathesis's**, or a token bucket. Either lets requests go together — a
  window all of them at once, a bucket up to twice the rate across the edge of a minute — and a rate
  promised to an owner should not.
- **The engine alone.** The loop works up to `schedule.workAheadFactor` × `engine.maxConcurrency`
  requests ahead, thirty-two by default, and they would wait inside the engine for their turns: built
  up to thirty-two turns before they go, and, at the end, sent after the run's time or dropped by the
  dozen.
- **The loop alone.** Requests handed over evenly still go out together when every slot was taken
  and several answers come back at once — an API that stalls and recovers. Measured on a stand-in that
  does that (above), the engine's turn is what keeps them apart.
- **A turn in the loop and another in the engine, each with its own clock.** Proposed first, and
  found wrong in review: after a stall both sides run at the rate, so a queue that formed inside the
  engine never drains, and every request after it is built long before it goes.

## Consequences

- **`--set engine.mostRequestsPerMinute=300` keeps a run to five requests a second, evenly.** With
  nothing named, nothing changes.
- **`HttpEngine` gains `untilItsTurn()`**, which an engine with no rate answers with nothing, and
  `dropWhatIsHeld()` also lets go what waits for a turn.
- **`EngineStatistics` gains `idleKeepingToTheRate`**, `report.json` its field and the summary its
  part of the brackets.
- **Two settings join `engine.*`**: one hundred and seven in nine groups.
- **The known limitations** lose "there is no ceiling on requests per second" and gain what the rate
  does not do.

## Still open

- **The page.** It shows the idle share without saying how much of it the rate made: a change of
  layout needs a mock-up first, as ADR-0042 left its own section.
- **A rate per operation**, or one taken from the `RateLimit` fields an API may send, which RESTest
  does not read.
- **Redirections.** With `engine.followRedirects` on, which it is not by default, a redirection is
  followed inside the request that drew it, without a turn of its own, so two requests can reach
  the API closer together than the rate allows.
- **The document fetched over the network** is one request the rate does not count.
- **Requests waiting for their turn are not served in order**: several may wake at once and the lock
  decides which takes it. With the loop of §3 there is hardly ever more than one.
