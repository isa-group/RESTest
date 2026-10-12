# The faults RESTest reports

A **fault** is a reply RESTest has a reason to call wrong: the API fell over, or it answered with
something its own document says it would never send. Every fault is named by a number from a
catalogue several testing tools share, described in a sentence of RESTest's own, and printed with a
`curl` command that sends the same request again.

This page lists the kinds of fault this version reports, says what each one means and when it is
reported, how to switch off the check that reports it, and shows how a fault looks on the screen and
in `report.json`. What the rest of a run's
output says is in [What a run leaves behind](report.md).

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
made under another version from 0.9.0 on, match on the key. A report of RESTest 2.0 to 2.2, written
under version 0.8.0, has no key: there `F100` and `F200` have the same numbers and names as under
0.9.0, so their counts compare by number.

## The kinds this version reports

| Code | The catalogue's name | What it means |
|---|---|---|
| `F100` | HTTP Status 500 | The API answered `500 Internal Server Error`: something went wrong inside it that it did not expect, most often an error nobody handled in its code |
| `F200` | Schema Violation: Received A Response From API With A Structure/Data That Is Not Matching Its Schema | The body of a reply is not the shape the document says a reply with that status code and media type has: a member it calls required is missing, a number arrives as text, a value is off the closed list the document gives |

The catalogue has more kinds than these — wrong uses of HTTP status codes, a resource still there
after it was deleted, security problems — and this version reports none of them. A run that finds
nothing wrong has found neither of the two kinds above whose check was switched on, and says nothing
about the others.

### `F100`, a reply of 500

Reported for every reply whose status code is exactly 500, whatever was sent to get it — an ordinary
request, a value nobody sensible would send, a request with one thing changed.

- **Only 500.** 502, 503 and 504 are what a gateway or a load balancer says when something in front
  of the API is unwell, and are often nothing anybody can fix in the API's code. 501, *not
  implemented*, is about what the document promises rather than a crash. The catalogue has a kind of
  its own for a 5xx other than 500 and 501, `F121`, and one for a request that got no reply in time,
  `F312`; this version reports neither. The other 5xx codes are counted under `serverErrors` in
  `report.json` and in the summary's line about 5xx, and requests that got no reply under `noReply` in
  `replies.byClass`.
- **It is a reason to look, not a verdict.** An API can answer 500 because its database is down or
  because another service it calls is not answering, neither of which is a bug in the API itself.

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

## Switching a check off

Each kind of fault is reported by one check, and each check can be switched off for a whole run, in
one line, when it is wrong about an API. A document that describes its error replies badly, say,
makes every one of them a reply of the wrong shape:

```bash
restest run api.yaml --url http://localhost:9966 --set checks.replyOffSchema=false
```

| Switch | Off, the run never reports |
|---|---|
| `checks.internalServerError` | `F100`, a reply of 500 |
| `checks.replyOffSchema` | `F200`, a reply of the wrong shape |

Both are on unless somebody says otherwise. Like every setting, they can also be given in a file of
settings or in the environment ([the settings](settings.md#checks)), and [the
switches](switches.md#switching-off-one-idea) has a file for each, ready to hand over.

A check switched off is not made at all. What that changes, and what it does not:

- **Which requests are built does not change.** The same plan, settings and seed build the same
  requests whichever checks are made. How many of them are sent within the budget can change: the
  checks are made on the replies while the run goes on sending, and when they fall behind it waits
  for them. So with the check of a reply's shape off, the one that takes time, a run may send more.
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

**Every reply is judged by every check switched on, and each check that objects is one fault.** A reply of 500
whose body is also not the shape the document gives for a 500 is two faults, one of each kind. A
broken operation asked six hundred times is six hundred faults. So the total says how much went
wrong across the run, not how many problems the API has; [What a run leaves
behind](report.md#faults) has the numbers that come closer to that — how many operations went
wrong, and in how many kinds of way.

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
with `130` or `143`, and one in which RESTest itself went wrong with `4`, whatever they found.
[The exit codes](command-line.md#exit-codes) has every number a run can end with.
