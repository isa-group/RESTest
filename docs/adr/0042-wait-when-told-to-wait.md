# ADR-0042: An operation the API asks to be left alone is left alone, and the whole API once a second one is

**Status:** Accepted
**Date:** 2026-10-29

## Context

An API that is being asked too much can say so. It answers 429, "too many requests", or 503, "not
now", and it may say in a `Retry-After` header when to ask again: either as a number of seconds,
`Retry-After: 30`, or as a moment, `Retry-After: Thu, 29 Oct 2026 10:00:00 GMT`.

Until now RESTest heard such a reply as an ordinary answer. It did not read the header and nothing
paused. Worse, a 429 is usually a quick answer, and the engine's limiter judges only how long answers
take, so a stream of 429s read to it as room to send *more*. A testing tool pointed at somebody
else's API should obey an API that asks it to slow down.

What the dataset of local APIs shows (the Web Fuzzing Dataset's APIs, run on one machine). Every
stored report of its 52 APIs was read, from two campaigns:

- **Two APIs ever answer 429, and each for one operation only, with no `Retry-After`.**
  - cwa-verification's `createTeleTan` has a quota of its own, about 1,000 creations an hour. A
    60-second run spends it at 2.6 s, and the operation answers 429 to 36,440 requests after that,
    with an empty body. The API's other four operations go on answering all the while: 4,171 times
    201 and 4,102 times 200 after that moment.
  - genome-nexus's `fetchGeneXrefsGET` passes on the limit of a service it calls, 27 times in 60 s:
    `You have exceeded the limit of 15 requests per second; please reduce your concurrent
    connections`. Its other 22 operations answer 200 and 404.
- **Three APIs answer 503** — commafeed, jasper and spring-ecommerce — and none of the 503s their
  reports kept carries `Retry-After`. They are server errors, not requests to wait.

HTTP lets a server count requests per resource or for the whole server, and a 429 does not say
which ([RFC 6585](https://www.rfc-editor.org/rfc/rfc6585#section-4)).

Among the related tools, waiting after a 429 is common, and slowing down afterwards is not. EvoMaster
waits for `Retry-After`, or ten seconds without it, and sends the request again. RestTestGen and
AutoRestTest do the same a bounded number of times, and Schemathesis does only when asked. RESTler
sends it again every five seconds without reading the header, and WuppieFuzz only logs it. Each of
them that waits makes the whole API wait, whichever operation was refused; none returns to a slower
pace afterwards, none waits on a 503 by default, and none reads the IETF draft's `RateLimit` fields.

## Decision

### 1. What asks the run to wait, and for how long

- **The replies.** A reply of 429, with or without `Retry-After`. A reply of 503 that carries a
  `Retry-After` the run can read; a 503 without one is a server error like any other. A reply whose
  body broke off counts the same, since its headers came with its status line.
- **Stated.** `Retry-After` as seconds, or as a moment in any of the three forms
  [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110#section-5.6.7) says a reader must accept:
  - the current one, `Sun, 06 Nov 1994 08:49:37 GMT`;
  - the RFC 850 one, with a two-digit year read as RFC 9110 says, so that `94` is 1994 and not 2094;
  - C's `asctime`, `Sun Nov  6 08:49:37 1994`.

  A moment is measured from the reply's own `Date` header when it has one that can be read, so that
  a clock out of step on either side changes nothing; otherwise from the run's clock. A moment
  already past, or `0`, is a wait of no time at all. Of several `Retry-After` headers, the first that
  can be read counts.
- **Guessed.** When the reply gives no length that can be read, the operation waits
  `schedule.firstGuessedWait`, one second. Each time the same operation is given a guessed wait again
  it doubles, up to `schedule.longestGuessedWait`, a minute.
  - A second is the shortest wait that clears a limit counted per second, as genome-nexus's is.
  - A minute clears a limit counted per minute in one wait, and an operation whose quota is spent
    for the hour, as cwa-verification's is, is still tried once a minute.
  - The guess goes back to one second as soon as the operation gives any answer that does not ask
    it to wait — a 2xx, a 400, a 404, a 500, a 503 without `Retry-After` — to a request sent after
    its last wait ended. Decided by the maintainer on 29 October 2026, after going back only on a
    success had been built and measured (see the alternatives).
  - A request that got no reply resets nothing, and a stated wait leaves the guess where it was.
- **Nothing an API writes can break a run.** Every end is cut at the run's deadline before anything
  is worked out on the clock. A wait longer than the run has left is the whole of what it has left;
  a number of seconds too large for any clock is too.

### 2. Who waits: the operation, and the whole API once a second one is

The operation whose request drew the reply has its turns skipped until its wait ends: nothing is
built for it and nothing is sent. Every other operation goes on. That is what both APIs above need:
one operation is limited, the rest are not. Decided by the maintainer on 29 October 2026, from the
replay in the evidence below.

When an operation is told to wait while another operation still waits, the limit is plainly the whole
API's. Then every request waits, both kinds:

- those the run had not yet handed to the part that sends;
- those already handed over and not yet sent, which the engine holds back.

The engine then starts again from its fewest requests in flight (`engine.minConcurrency`) and climbs
as it already does. Requests already in flight come back as usual. The thread that hears the second
refusal tells the engine at once, `HttpEngine.holdFor`, rather than leaving it to the loop that
decides what to send, which might be waiting for room to send and notice only later.

The whole API has a guessed wait of its own, which doubles in the same way, so that an API limited as
a whole and saying nothing is asked a little more slowly each time, rather than once a second for the
whole run. It goes back to the first guess as an operation's does: after any answer that does not ask
to wait, to a request sent once its last wait was over.

When the run stops sending while the whole API still waits — its time is up, or it was stopped — what
the engine holds is never sent: each such request comes back as one that never went out
(`HttpEngine.dropWhatIsHeld`), and is counted with the requests that got no reply. A wait that lasts
to the end of the run holds the engine beyond it, so that nothing reaches the API after the run's time
is up.

### 3. A refusal of a request sent before the wait began

Requests go out several at a time and come back in any order, so a refusal is weighed by when its
request went out. One sent before the wait it belongs to was over had not heard of that wait. Its
refusal begins no wait and doubles nothing; a moment it states only moves the end of a wait still
going, and only later. Any other answer of such a request does not bring the guess back to the start
either.

### 4. The refused request is not sent again

It is recorded as it came back, and judged by the rules like any reply:

- a 429 the document does not declare is a warning, `F920`;
- a 503 is a fault, `F121`.

The operation's next turn after the wait builds a new request, as every turn does. Resending would buy
nothing a new turn does not: every request a run sends is new. EvoMaster resends because it sends one
request at a time, and the refused one is all it had. A step of a series about a thing the run
created, whose operation is waiting, is handed back to its series as one that could not be built.
The series then goes on without it, or ends, and never repeats a step that never went out.

### 5. A round with nothing to send

A round over the operations can send nothing because each of them was waiting or could not be built.
It is then not gone round again at once, which would only skip them all again as fast as the loop can
turn. The loop sits it out until the soonest wait is over, the deadline, or a stop from outside. When
a request of that round could not be built, it looks again after a twentieth of a second instead,
since a request that could not be built once may be built the next time. Such a round is also not
one where "nothing in the document could be sent", which ends a run. An API of one operation that is
told to wait is the commonest case.

### 6. What a run says

- **`report.json`**, `toldToWait`, beside `unauthorized`:
  - `idle`: how much of the run's idle time came while it waited as the API asked — the whole API
    waiting, or every operation it could send waiting;
  - `wholeApi`: how often and how long nothing at all was sent;
  - `operations`: each operation told to wait, with how many waits, how many of them the API stated,
    how long they lasted and how many of its turns were skipped.

  Lengths are counted up to the moment the run stopped sending, and waits that began after it are not
  counted. A run that waits when told to and never was writes zeros and no operation. With the switch
  off, it is `null`.
- **The summary.**
  - The idle share on its first line is followed by how much of it the API asked for, `40% of it idle
    (38% waiting as the API asked)`, so that quiet the API asked for is not read as the tool's own
    slowness ([ADR-0009](0009-non-blocking-engine.md)'s amendment).
  - A line under the replies, `told to wait: …`, names the whole API and up to three operations.
- **The exit code** does not change. A run told to wait ends with the number its findings give.

### 7. One switch, two numbers, and the seed

`schedule.waitWhenToldTo`, on by default, with `schedule.firstGuessedWait` and
`schedule.longestGuessedWait` beside it ([ADR-0025](0025-settings.md)'s amendment). They are in
`schedule.*` because the loop that decides what to send decides the wait; `engine.*` holds what the
HTTP engine itself reads.

The switch is listed among those that are not levers. It is a courtesy to the API rather than a way of
finding more, so it plays no part in an ablation. It changes what is sent only against an API that
asks to wait.

It makes what a run sends depend on the API's replies, though: which turns are skipped depends on
when the API asked. So, while it is on, the line under the seed says the seed alone does not repeat
the run, and the switches page's file for getting the seed back turns it off.

## The evidence

Replayed before it was built, apart from RESTest's code, over runs of the two APIs stored with
RESTest 2.3.0, 60 s each. The replay keeps each request's recorded reply. That suits a quota that
stays spent, and overstates the waits for a limit per second. Each rule was replayed with a first
wait of 1 s doubling to 60 s.

| | Every request to the API waits | Only the operation told to wait waits |
|---|---|---|
| cwa-verification | 57 s of 60 waiting; 2xx replies from 9,275 to 1,002 | no 2xx lost; 54,260 requests that would have drawn a 429 not sent |
| genome-nexus | 27 s waiting | no 2xx lost; 76 requests not sent |

Then built, and run against `master` (which carries
[ADR-0041](0041-stop-when-the-api-refuses-the-whole-opening-lap.md)), with the same seed:

| Run | `master` | This change |
|---|---|---|
| cwa-verification, 60 s: replies of 429 | 34,699 | 62, in 12 waits of `createTeleTan`, its 400s to the values RESTest made up bringing the guess back to a second; 57,728 of its turns skipped |
| cwa-verification, 60 s: 2xx replies, operations answering 2xx | 8,756, 3 of 5 | 9,503, 3 of 5 |
| cwa-verification, 60 s, with `schedule.waitWhenToldTo=false` | — | 34,931 replies of 429, 8,778 2xx, `toldToWait` `null` |
| genome-nexus, 60 s, two runs of `master` and one of this change: replies of 429 | 5 and 1 | 6; `fetchGeneXrefsGET` told to wait 4 times, 32 of its turns skipped |
| genome-nexus, the same runs: 2xx replies, operations answering 2xx | 2,735 and 1,766, 17 and 16 of 23 | 2,214, 17 of 23 |
| blogapi without its key, 20 s: requests, idle | 146,147, 1.9% | 149,242, 1.5% |
| A stand-in limiting the whole API to 20 requests a second, 20 s: requests that reached it inside the quiet it asked for | 1,193,708 | 277, over 20 waits of the whole API |

genome-nexus calls a service of its own over the internet, and its runs vary that much with nothing
changed: its two runs of `master` alone differ by 969 replies of 2xx, and three runs of earlier
versions of this change gave 2,817, 2,355 and 3,128. An operation that answered 2xx on `master` and
not on one of those runs, `fetchPdbHeaderGET`, was never told to wait. jasper's 293 replies of 503
and commafeed's 421, from runs of 20 s with every request kept, carry no `Retry-After`, and no
operation of either waited.

## Alternatives considered

- **Every request to the API waits, at the first 429.** This is what was first proposed, and what
  every related tool that waits does. Against both APIs that answer
  429 it gives the table's first column.
- **Every 503 waits, as a 429 does, with or without `Retry-After`.** A 503 says the API cannot answer
  now, most often because something behind it broke, while a 429 says the caller is going too fast.
  Replayed over the runs of jasper and commafeed with every request kept, 20 s each: jasper's four
  broken operations answer 503 together, so the whole API would wait, and its 4,807 requests and
  1,674 replies of 2xx would fall to 159 and 52; commafeed would send 459 fewer requests to the one
  operation that answers 503 when it cannot read the feed it was given. Those 503s are faults to
  find, not requests to slow down. Kept as first proposed, and confirmed by the maintainer on
  29 October 2026: a 503 waits only when it says for how long.
- **The guess goes back to the start only on a success, 2xx.** A 400 or a 404 is about the values
  RESTest made up, and an API may check those before it counts its quota, as cwa-verification's
  `createTeleTan` does: each of its 400s starts the guess again, so that operation is asked again
  every second or two. Built and measured, going back only on a success gave it 6 waits that double
  and 35 refusals in a minute, against 12 waits and 62 refusals. The maintainer chose to go back on
  any answer that does not ask to wait: a success depends on the values RESTest made up too, and an
  operation for which a valid request is rarely built would otherwise wait the longest guess after
  every 429.
- **The scheduler skips the operations that wait**, rather than the loop skipping what the scheduler
  proposes. It would keep "the scheduler decides what comes next" true to the letter. But it costs a
  new kind of step and a change to every place that reads one, for what the loop does in one check.
- **Generating the request of a skipped turn and throwing it away**, so that every later request
  keeps the values its seed gives. It would cost the generation of every skipped request, 54,260 in
  cwa-verification's minute. It would also not make a run that skips turns repeatable, since which
  turns are skipped depends on when the API asked.
- **A fixed wait without the header**, as EvoMaster's ten seconds. It is too long for a limit per
  second and too short for an hourly quota, which a doubling wait finds either way.
- **Releasing what the engine already holds during a wait of the whole API, and saying so.** It would
  be simpler, but the requests already handed over and not in flight — up to
  `schedule.workAheadFactor` × `engine.maxConcurrency` less those in flight, sixteen by default —
  would reach an API inside the quiet it asked for, and the limiter would climb back while they went.

## Consequences

- **An operation answered 429, or 503 with `Retry-After`, is sent nothing for as long as the API asks,
  and every other operation goes on.** On cwa-verification, the 34,699 429s of a minute become 62.
- **The whole API waits once a second operation is told to.** A limit over the whole API therefore
  costs a refusal from each of the first two operations before it is known.
- **The requests for an operation already handed to the engine when it is told to wait still go out.**
  There are at most `schedule.workAheadFactor` × `engine.maxConcurrency` of them, and in practice that
  operation's share of them. The known limitations say so.
- **`report.json` gains `toldToWait`,** and the summary a line.
- **`HttpEngine` gains `holdFor` and `dropWhatIsHeld`.** On closing, the engine no longer sends a
  request it was handed and had not yet sent: such a request comes back as one that never went out.
  A run that stops sending while the whole API waits has what is held dropped the same way, and
  those requests are counted with the ones that got no reply.
- **A run with the switch on depends on the API's replies,** and says so under its seed.

## Still open

- **Stopping a run that the whole API keeps asking to wait,** or that it asks to wait past the
  end of its budget, with `5` and a verdict word of its own. None of the dataset's APIs asks its whole
  API to wait.
- **The IETF draft's `RateLimit` and `RateLimit-Policy` fields** (draft-ietf-httpapi-
  ratelimit-headers-11, May 2026), which would let a run stay inside an API's quota rather than find
  it by going over. None of the dataset's APIs sends them.
- **The page.** It shows the idle share as before. A section for the waits is a change of layout,
  which needs a mock-up first.
- **An API that answers 429 to too many failed sign-ins for one account.** That is a refusal that
  depends on the values RESTest invented — a wrong password — and a rule that reacts to an answer
  should not count such a refusal. Nothing here tells it apart: the sign-in operation is left alone
  for a while, as any other.
