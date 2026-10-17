# The faults RESTest reports

A **fault** is a reply RESTest has a reason to call wrong: the API fell over, it answered with
something its own document says it would never send, it accepted a request its document says it
must refuse, or it took a request and never answered at all. Every fault is named by a number from a
catalogue several testing tools share,
described in a sentence of RESTest's own, and printed with a `curl` command that sends the same
request again.

This page lists the kinds of fault this version reports, says what each one means and when it is
reported, how to switch off the check that reports it, and shows how a fault looks on the screen and
in `report.json`. What the rest of a run's output says is in [What a run leaves behind](report.md).

## The catalogue

Fault kinds are numbered by the [Web Fuzzing Commons](https://github.com/WebFuzzing/Commons) fault
catalogue, **version 0.9.0**, which other API testing tools use as well. A number from it — `F100` —
is what a fault *is*: it is what `report.json` counts by, and what makes a count from RESTest
comparable with a count from another tool that uses the same catalogue. The catalogue's own name
for the kind, `HTTP Status 500`, is written beside the number. The sentence under it, `the API
answered 500, so it fell over while handling this request`, is RESTest's, written to be read rather
than filed.

`report.json` says which catalogue it counted by, and which version of it, in `faultCatalogue`, since
a number only means something next to the list it was taken from: the catalogue has renumbered its
kinds once, and under its version 0.7.0 `F101` meant what `F200` means now. Since its version 0.9.0
the catalogue also gives every kind a key — `HTTP_STATUS_500` for `F100` — which it says is meant
never to change, and would change only in a new major version. `report.json` writes that key beside
every number. Within one version, the number is what tells two kinds apart; to match a count with one
made under another version from 0.9.0 on, match on the key. A report of RESTest 2.0 or 2.1, written
under version 0.8.0, has no key: there `F100` and `F200` have the same numbers and names as under
0.9.0, so their counts compare by number.

## The kinds this version reports

| Code | The catalogue's name | What it means |
|---|---|---|
| `F100` | HTTP Status 500 | The API answered `500 Internal Server Error`: something went wrong inside it that it did not expect, most often an error nobody handled in its code |
| `F121` | HTTP Status 5xx Other Than 500 And 501 | The API answered a server error other than 500 and 501, most often `502`, `503` or `504`: something in it, or in front of it, failed while handling the request |
| `F200` | Schema Violation: Received A Response From API With A Structure/Data That Is Not Matching Its Schema | The body of a reply is not the shape the document says a reply with that status code and media type has: a member it calls required is missing, a number arrives as text, a value is off the closed list the document gives |
| `F206` | Received Success Response When Sending Wrong Data | The API accepted a request RESTest broke on purpose, with one thing in it the document forbids: a required value left out, a word where a number goes, a value off a closed list |
| `F312` | Call Timeout | The API had a request on an open connection and sent no reply, not even its first line, before RESTest stopped waiting: whatever handles it is waiting on something that does not answer |

The catalogue has more kinds than these — wrong uses of HTTP status codes, a resource still there
after it was deleted, security problems — and this version reports none of them. A run that finds
nothing wrong has found none of the kinds above whose check was on, and says nothing about the rest.

### `F100`, a reply of 500

Reported for every reply whose status code is exactly 500, whatever was sent to get it — an ordinary
request, a value nobody sensible would send, a request with one thing changed.

- **Only 500.** A server error other than 500 and 501 is a kind of its own, `F121`, below. 501, *not
  implemented*, is about what the document promises rather than a crash, and no check reports it.
- **It is a reason to look, not a verdict.** An API can answer 500 because its database is down or
  because another service it calls is not answering, neither of which is a bug in the API itself.

### `F121`, a server error other than 500

Reported for every reply whose status code is from 502 to 599 — most often 502, 503 or 504 —
whatever was sent to get it, and whether or not the document declares that status code: a 503 the
document promises is still an API that could not serve the request, as a 500 it promises is still
one that fell over.

- **It is weaker evidence of a bug than a 500.** 502 and 504 are what a gateway or a load balancer in
  front of the API says when what it passed the request on to answered nonsense or nothing in time,
  and 503 what an API says when it cannot serve requests just now, often because a database or
  another service it calls is down. Often the thing to fix is the API's surroundings rather than its
  code. It is reported anyway: an operation that answers 503 is worth knowing about, and until this
  check existed such replies were only counted, under `serverErrors`. Where the surroundings are
  known to be unwell, switch the check off in one line (`checks.otherServerError`).
- **A 503 that asks the run to wait is reported too.** An API that cannot keep up may answer 503
  with a `Retry-After` header; RESTest does not wait yet ([known
  limitations](known-limitations.md#pointed-at-somebody-elses-api)), so each such reply is a fault.
- **501 is left alone**, as for `F100`, and so is a code of 600 or more, which is no server error
  whatever its first digit.

### `F200`, a reply of the wrong shape

Reported when the body of a reply does not match the shape the document declares for that operation,
that status code and that media type. The reply is checked against the document exactly as it was
handed over, so everything the document can say counts — shapes built from other shapes, choices
between shapes, references from one to another — and the details lines under the fault say which
part of the body disagreed and how.

Nothing uncertain is reported. The check ends quietly, and reports nothing, when:

- the document says nothing about what that operation returns with that status code or that media
  type;
- the reply does not say it is JSON — `application/json`, or a media type ending in `+json` — or
  has no body, or says it is written in a character set other than UTF-8;
- the body was too large to keep whole, so there is no whole body to judge;
- the disagreement could have been caused by a key the run handed over with `--auth` being hidden in
  the reply rather than by the API;
- the shape the document gives is one the checker cannot finish judging.

A property the document marks `writeOnly` is one the API is sent and never hands back, so a reply
without it is not wrong for that, even where the document calls it required.

One thing a document can say is never checked: `format`. Saying a piece of text is a `date` or an
`email` is, in the standard, a note to the reader rather than a rule, and tools disagree about which
ones to enforce.

### `F206`, a broken request accepted

Reported when the API accepts — answers 2xx to — a request RESTest broke on purpose. A strategy of
kind `mutation` takes a request the API has just accepted and sends it again with one thing changed
so that the document forbids it: a required value left out, a word where a number is declared, a
value off a closed list, one past a limit, a body that is not JSON, a date that does not exist
([the switches](switches.md#breaking-things-in-more-ways) list every kind of change). Such a request
records that a refusal was expected and what was changed, and the fault says which change was
accepted:

```
F206  Received Success Response When Sending Wrong Data
      createRoleUsingPOST - POST http://127.0.0.1:57518/users/rbac/roles  ->  201
      the API accepted a request it should have refused: sent the words 'this is not JSON' as the body, which is not JSON
      curl -i -X POST 'http://127.0.0.1:57518/users/rbac/roles' -H 'Accept: */*' -H 'User-Agent: RESTest/2.0' -H 'Content-Type: application/json' --data-binary 'this is not JSON'
```

Every kind of change counts, one that breaks only the `format` the document names included — a
`date` of `2021-02-30`, a number past what an `int32` holds — since the document forbids it like any
other. Only a success counts: any refusal was expected, and a server error is reported by the checks
above.

- **It is not always a bug in the API's own code.** The library an API reads its requests with often
  accepts more than the document allows, and the API then accepts it too. In Java services, Jackson
  and Spring commonly:
  - turn a number or `true` sent where a word is declared into the word, so that
    `{"name": -2147483648}` creates something called `-2147483648`;
  - read a body up to the end of its JSON and ignore whatever follows;
  - trim spaces around a number in an address, and read `0x1A` as 26;
  - read an empty value of an optional parameter, or `null` for an optional member, as though it had
    not been sent;
  - read a `date-time` with no time zone as a local time.

  Each is still a disagreement between the API and its document, which is what the fault says, and
  the change it names tells these apart from a check the API is missing altogether: a required member
  left out and accepted, a value off a closed list stored, `this is not JSON` answered `201`. Either
  way the fix is to make the API stricter or its document looser.
- **A step of a series is not judged here.** Reading again a thing the API said, a moment before,
  that it had deleted also expects a refusal; a success there says something about deleting, a kind
  of fault this version does not report.

### `F312`, no reply in time

Reported for every request sent on a connection open to the API that got no reply — not even the
first line of one — before RESTest stopped waiting. It waits `engine.readTimeout` for anything to
arrive, half a minute by default, and `engine.callTimeout` for the whole request, a minute
([the settings](settings.md#engine)). From a run against an API that calls a service on the internet
from a machine with no connection to it:

```
F312  Call Timeout
      fetchUsingGET - GET http://127.0.0.1:52200/fetch  ->  no reply
      no reply within 30.0s: a connection to the API was open, and no status line came back before RESTest stopped waiting
      curl -i -X GET 'http://127.0.0.1:52200/fetch' -H 'Accept: application/json' -H 'User-Agent: RESTest/2.0'
```

- **It is often a real problem.** An operation that sits on a request is usually waiting on
  something that does not answer — a database, another service, a lock — with no limit of its own.
  And since what was sent is what made it wait, it may be a way to tie the API up on purpose, which
  is why the catalogue files it among the security problems.
- **It may also be the API's surroundings, or the run.** Something the API calls may be slow, or
  out of reach from where the API runs. The run's own pace may be more than the API can take
  ([troubleshooting](manual/10-troubleshooting.md#the-api-falls-over-under-the-run)). And a connection
  kept open between requests can be dropped without a word by something in between, such as a load
  balancer, so that a request sent on it reaches nobody. An API known to be slow gets longer with
  `--set engine.readTimeout=2m`, and the check switches off in one line (`checks.noReplyInTime`).
- **What is not reported.** A reply whose first line arrived, however long the rest of it took: that
  API answered, and an endpoint that sends a reply bit by bit for as long as somebody listens, as a
  stream of events does, is doing what it is for. A connection that could not be made in time, since
  the request never reached the API, and an address that swallows whatever it is sent looks exactly
  the same. A connection refused, or cut before any reply. Each of these, like every request that
  got no reply, is still counted under `noReply` in `replies.byClass`.
- **A request still being sent counts too.** RESTest cannot tell an API that stopped reading a
  request from one that read it and never answered. It is rare: what RESTest sends is far smaller
  than what the computer holds back for a connection, so sending stalls only when the API stops
  reading a connection it keeps open.
- **The `curl` waits as long as the API does.** It has no limit of its own: add `--max-time 35` to
  try it without waiting for ever.
- **In `report.json`**, the request's outcome is `failed`, with `timedOutWaitingFor` set to `reply`
  ([What a run leaves behind](report.md#findingsinteractionoutcome)).

## Switching a check off

Each kind of fault is reported by one check, and each check can be switched off for a whole run, in
one line, when it is wrong about an API. A document that describes its error replies badly, say,
makes every one of them a reply of the wrong shape:

```bash
restest run api.yaml --url http://localhost:9966 --set checks.replyOffSchema=false
```

The run says, under the line that gives its seed, that a setting is not what RESTest does by
default, and its summary counts no reply of the wrong shape. From a 20-second run of pet-clinic with
that check off:

```
  1 setting(s) are not what RESTest does by default; --print-settings lists them with where each came from
...
16492 faults:
  16201 x F100  HTTP Status 500
  291 x F206  Received Success Response When Sending Wrong Data
```

Each switch is named for what is wrong with what came back, or did not, and [the
switches](switches.md#judging-what-came-back) lists them all, with the kind of fault each turns off
and a file for each, ready to hand over. All five are on unless somebody says otherwise; like every
setting, they can also be given in a file of settings or in the environment ([the
settings](settings.md#checks)).

A check switched off is not made at all. What that changes, and what it does not:

- **No check decides what is sent.** A check only reads what came back. But the checks are made
  while the run goes on sending, on the same thread that hands each reply to the memory of what the
  API returned, and when they fall behind the run waits for them. So with the check of a reply's
  shape off, the one that takes time, a run may send more requests in the same budget, and learn
  from a reply sooner. Two runs of the tool as shipped differ anyway; one that has [its seed
  back](switches.md#getting-the-seed-back) builds the same requests whichever checks are made, and
  only how many fit in the budget can change.
- **What the API did is still counted.** `serverErrors` in `report.json`, and the summary's line
  about 500 and 5xx, count every operation that answered so, whichever checks were made.
- **The exit code follows the faults reported.** A run whose only faults would have been replies of
  500 ends with `0` when `checks.internalServerError` is off, since it reported none.
- **The run records it.** `report.json` lists every setting under `settings`, with its value and
  where it came from, so a report says which checks its run made. The screen says that some
  settings are not what RESTest does by default, and `--print-settings` lists which.

A switch is for the whole run, never for one operation ([known
limitations](known-limitations.md#what-a-run-says)).

## How a fault looks on the screen

Faults are printed as they are found, while the run is still going:

```
F100  HTTP Status 500
      getInventory - GET https://petstore3.swagger.io/api/v3/store/inventory  ->  500
      the API answered 500, so it fell over while handling this request
      curl -i -X GET 'https://petstore3.swagger.io/api/v3/store/inventory' -H 'Accept: application/json' -H 'User-Agent: RESTest/2.0'
```

1. The catalogue number and the catalogue's name for the kind.
2. The operation, by the name the document gives it (its `operationId`), the method and address the
   request went to, and what the API answered: a status code, `no reply`, or a reply that could not
   be read.
3. What is wrong, in a sentence.
4. Up to ten lines of details under it, indented further, when there are any — for `F200`, each
   place in the body that disagreed with the document. More than ten end with `... and 4 more`.
5. For a request that was one step of a series about a thing the run created — delete it and read
   it again, say — a line saying which step, of which series, and that the command below sends this
   step alone, without the steps before it, so it may not answer the same way.
6. A `curl` command that sends the same request again. A key handed over with `--auth` appears as
   `REDACTED-AUTH`, or a longer word such as `REDACTED-AUTH.api_key` that the run names before its
   first request: put the key back in place of that whole word before running it.

The first fifty faults are printed in full. After that the screen says
`... more faults are being found; every one of them is counted in the run's report and in the total
below`, and prints no more of them; `report.faultsShownOnTheConsole` changes the fifty
([the settings](settings.md#report)).

At the end of the run, the summary counts them by kind:

```
336 faults:
  288 x F100  HTTP Status 500
  48 x F200  Schema Violation: Received A Response From API With A Structure/Data That Is Not Matching Its Schema
```

## How faults are counted

**Every request is judged by every check switched on, and each check that objects is one fault.** A
reply of 500 whose body is also not the shape the document gives for a 500 is two faults, one of
each kind. A broken operation asked six hundred times is six hundred faults. So the total says how
much went wrong across the run, not how many problems the API has; [What a run leaves
behind](report.md#faults) has the numbers that come closer to that — how many operations went wrong,
and in how many kinds of way.

**Faults are classified twice.** By catalogue number, which is what makes a run comparable with
another tool's, and by the class of status code that carried them — `5xx`, `4xx`, `2xx` and so on
— which is what a developer looks for first. The second makes one thing visible that the first does
not: how many faults came back with an ordinary `200`. Both are in `report.json`
(`faultsByCategory` and `faultsByStatus`).

**Server errors are counted separately from faults**, under `serverErrors` in `report.json` and in
the summary line `11 operation(s) answered 500, 11 answered some 5xx`. Those two numbers count what
the API did rather than what a rule decided: every operation that answered 500 at least once, and
every one that answered any 5xx at least once, whether or not a fault was reported for it.

**Not one of these decides that two faults are the same problem.** Two faults of the same kind on
the same operation are counted as two, however alike they look.

## What a fault does to the exit code

A run that ran to the end and found at least one fault ends with `1`, and one that found none ends
with `0`, so that a build server can go red when the API is broken. A run stopped from outside ends
with `130` or `143`, and one in which RESTest itself went wrong with `4`, whatever they found. A run
in which nothing at the address answered ends with `3`, even when it reported requests that got no
reply in time: something that takes connections and never speaks HTTP does exactly that, so the run
says how many such requests there were beside the address.
[The exit codes](command-line.md#exit-codes) has every number a run can end with.
