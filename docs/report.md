# What a run leaves behind

A run says what it found in three places: on the screen while it runs, in a file called
`report.json` when it ends, and — only when asked with `--store` — in a file called `run.sqlite`
that keeps every request and every reply. This page says what each line on the screen means, what
every key in `report.json` holds, and what `run.sqlite` contains.

The kinds of fault a run reports, and how a fault looks, are on a page of their own:
[The faults RESTest reports](faults.md).

## Where the files go

Into `restest-out/`, under the directory the command was run from, unless `--out` names another. A
directory holds one run: starting another in the same place replaces what is there, and when that
throws away a run kept with `--store` the new run says so. A run worth keeping is given a directory
of its own:

```bash
./restest run openapi.yaml --url http://localhost:8080 --out runs/before-the-fix
```

## On the screen

### Before the first request

```
RESTest testing Swagger Petstore - OpenAPI 3.0 at https://petstore3.swagger.io/api/v3

19 of 19 operations can be tested, seed 20260914, budget 10s
  2 of them ask for an API key that was not given (api_key, in the header api_key): --auth <key> gives it
  what it sends depends on the API's own replies, so the seed alone does not repeat this run; --store keeps what it sent
```

| Line | When | What it says |
|---|---|---|
| `RESTest testing <title> at <address>` | Always | The API, by the title its document gives it, and the address requests go to |
| `<n> of <m> operations can be tested, seed <seed>, budget <budget>` | Always | How many of the document's operations the run can send requests to. Those it cannot test are named at the end of the summary, each with the reason; those the plan leaves alone are counted on a line of their own below. The seed is the one to give `--seed` for a similar run |
| `<who> goes with <k> of them, in <where>; what the run writes says <mask> in its place` | A key was given with `--auth` or `RESTEST_AUTH` | Which operations the key goes with, and where it goes: a header, the query or a cookie. Everything the run writes shows the mask instead of the key |
| `<who> goes with none of them: <reason>` | A key was given that no operation the run can test takes | Why the key will not be sent at all |
| `<k> of them ask for an API key that was not given (<scheme>, in <where>): --auth <key> gives it` | The document asks for a key and none was given | Those operations will most likely be refused, and how to hand the key over. [Handing over a key or a token](command-line.md#handing-over-a-key-or-a-token) has the forms `--auth` takes |
| `the document declares an API key it asks for on no operation (<scheme>, in <where>): --auth <key> sends it with <k> of them` | The document declares a key without saying which operations need it | That a key given with `--auth` would go with every operation |
| `<n> left alone by the plan` | The plan's filter set operations aside | Operations the plan keeps the run away from, on purpose: [Which operations a run may touch](campaign-format.md#which-operations-a-run-may-touch) |
| `what it sends depends on the API's own replies, so the seed alone does not repeat this run` | The plan draws on what the API returned, or changes accepted requests, or sends series | Running the same command again makes a similar run rather than the same one. `--store` keeps the run you had. [Getting the seed back](switches.md#getting-the-seed-back) has the files that make a run repeatable from its seed |
| `<n> setting(s) are not what RESTest does by default; --print-settings lists them with where each came from` | A setting was changed, by a file, the environment or `--set` | That the run is not configured the way RESTest ships, so that a setting given through an environment variable is not invisible. `report.json` lists every setting under `settings` |
| `<n> part(s) of the document could not be read:` | Some of the document could not be read | Up to five of the problems, then how many more. An operation the problem was in is not tested, and is named at the end |

Problems with a file you handed over — a plan, a list of values, a key in `RESTEST_AUTH` — are
written before all of this, on the error stream, each line beginning with `restest:`.

### While it runs

Each fault is printed as it is found: what kind it is, which request, what the API answered, what is
wrong with it, and a `curl` command that sends the request again. [How a fault looks on the
screen](faults.md#how-a-fault-looks-on-the-screen) goes through it line by line. After the first
fifty, the screen stops printing them and says so; every one is still counted.

### The summary

```
567 requests to 19 operations in 10.4s, 13% of it idle
  opening lap: 19 requests in 2.3s, 9 of 19 operations answered 2xx
  171 2xx, 245 4xx, 151 5xx
  112 of them were pushing at the API with values nobody sensible would send, which accounts for some of the 245 refusals above
  34 of them changed one thing in a request the API had accepted
  11 operation(s) answered 500, 11 answered some 5xx
184 faults:
  151 x F100  HTTP Status 500
  33 x F200  Schema Violation: Received A Response From API With A Structure/Data That Is Not Matching Its Schema
```

| Line | When | What it says |
|---|---|---|
| `<n> requests to <m> operations in <time>, <p>% of it idle` | Always | How much was sent, to how many operations, and for how long. *Idle* is the share of that time in which RESTest had no request in flight: time it spent on something other than testing. `, cut short` at the end means the run was stopped from outside before its budget ran out |
| `nothing was tested` | Not one request was sent | In place of everything below. Not the same as `no faults found`: a run that asked the API nothing has no evidence that anything is right |
| `opening lap: <n> requests in <time>, <k> of <m> operations answered 2xx` | The run began with its first round | Before anything is chosen by chance, every operation is sent once with the request it is most likely to accept. This line says how long that took and how many operations it got a success from; `opening lap, cut short:` means the budget ran out, or the run was stopped, before the round was over. [The first round of a run](campaign-format.md#the-first-round-of-a-run) |
| `<n> 2xx, <n> 4xx, <n> 5xx, <n> no reply` | Always | How every request ended, by the first digit of the status code; `no reply` when nothing came back. If almost everything was refused (4xx), the requests were the problem rather than the API |
| `<n> of them were pushing at the API with values nobody sensible would send` | The plan has a strategy that pushes | Requests built on purpose from values an API should turn away. Some of the refusals above are theirs. [How much of a run pushes at the API](campaign-format.md#how-much-of-a-run-pushes-at-the-api) |
| `<n> of them changed one thing in a request the API had accepted` | The plan changes accepted requests | Requests that are an accepted one with exactly one thing broken. [Changing one thing in a request that worked](campaign-format.md#changing-one-thing-in-a-request-that-worked) |
| `<n> of them were steps of <s> series about a thing the run created: <kind> <n>, …` | The plan sends series | Requests sent as steps of a short series after a creation — delete it and read it again, create it twice — counted by the kind of series. Creations meant to begin one that the API did not accept are counted after a `;`. [Series of requests around a thing the run created](campaign-format.md#series-of-requests-around-a-thing-the-run-created) |
| `<k> creation(s) meant to begin a series about a thing the run created were not accepted, so no series began` | The plan sends series and none began | The API refused every creation a series would have started from |
| `<a> operation(s) answered 500, <b> answered some 5xx` | Some reply was a 5xx | How many *operations* the API fell over on, rather than how many replies: a run spends its whole budget, so one broken operation asked six hundred times is six hundred broken replies. This is the number worth quoting |
| `no faults found`, or `<n> faults:` and one line per kind | Always, when something was sent | The verdict: how many faults, by kind, as `<count> x F<code>  <name>`. [The faults RESTest reports](faults.md) says what each kind means |
| `<n> operations could not be tested:` | Some operation could not be tested | Up to five of them, each with the reason — a file upload, say, or a parameter written in a style requests are not built in — then how many more. Said after the verdict because it qualifies it: `no faults found` says nothing about an operation that was never tried. `report.json` names every one |
| `<n> lists of values could not be read, so none of their values were sent:` | A list handed over with `--dictionary` could not be read | Each list and why, so that a run made without the values somebody meant it to have is not mistaken for one made with them |

### The last lines

```
report written to restest-out/report.json (126.1 KiB)
the run itself was not kept; pass --store to keep every request and reply
```

| Line | When | What it says |
|---|---|---|
| `<n> test case(s) could not be built and were skipped` | Some request could not be put together | Usually a value of a kind no request can carry |
| `<n> replies repeated a key back; …` | The API sent a key handed over with `--auth` back in a reply | It is hidden there too, and the check of replies against the document passes over whatever hiding it changed |
| `report written to <file> (<size>)` | The report could be written | Where `report.json` is |
| `run stored in <file> (<size>)` | `--store` was given | Where `run.sqlite` is |
| `the run itself was not kept; pass --store to keep every request and reply` | `--store` was not given | Only the report was written |
| `a run kept in <directory> by an earlier command was replaced` | `--out` pointed at a directory holding a run kept with `--store` | That run is gone |

A few more lines go to the error stream, each beginning with `restest:`:

- **that the run was stopped from outside**, and everything above is what it found until then — the
  run ends with `130` or `143`;
- **that some requests were never answered** and the run stopped waiting for them, so they are
  missing from the counts — said whatever the run ends with, `0` and `1` included;
- **that some requests were lost by RESTest itself**, or **that a rule or a report broke** — the run
  ends with `4`;
- **when nothing at all was tested**, which of three reasons it was — the run ends with `3`.

[The exit codes](command-line.md#exit-codes) says what each number means. Where a line on the screen
counts one thing, it says so in the singular: `1 operation could not be tested:`, `1 list of values
could not be read, so none of its values were sent:`.

## `report.json`

One file for anything that reads a run rather than looks at it: a build server deciding whether to
fail, a spreadsheet counting faults across versions, a script comparing two runs. It is written when
the run ends, under a temporary name first and then renamed, so a file called `report.json` is
always a whole report.

Every key below the top level is described in the tables that follow. A few conventions hold
throughout:

- **Every top-level key is always there**, in the order of the table below. A list with nothing to
  say is empty rather than missing. Below the top level, a key described as *only when* is left out
  when it does not apply.
- **A moment** is written the way `2026-10-03T09:21:07.412Z` is, in UTC, and **a length of time**
  the way `PT10.4S` (10.4 seconds) and `PT1M2.3S` are.
- **Counts are exact.** Nothing in the file is estimated, and nothing is left out of a count because
  the file had no room for it. Only `findings`, which writes faults out whole, has a limit, and the
  limit is written into the file under `limits`.

| Key | Value | What it says |
|---|---|---|
| `tool` | object | Which program wrote the file |
| `faultCatalogue` | object | Which catalogue the fault codes come from, and which version of it |
| `createdAt` | moment | When the report was written |
| `api` | object | Which API was tested, and where |
| `cutShort` | `true` or `false` | `true` when the run was stopped from outside — Ctrl-C, `kill`, `docker stop` — before its budget ran out. Every number in the file is then what the run found until it was stopped |
| `totals` | object | The run in eight numbers |
| `skippedOperations` | list | Every operation the run could not test, each with the reason |
| `dictionaries` | object | The lists of values handed over with `--dictionary`: the ones read, and the ones that could not be |
| `limits` | object | How much of the run this file was allowed to write out whole |
| `settings` | list | Every setting the run used, with its value and where that value came from |
| `engine` | object | How the part that sends requests spent the run |
| `phases` | list | Each stretch of the run with a purpose of its own — today, only the opening lap — and what it achieved |
| `replies` | object | How every request ended: counts of attempts, not of faults |
| `serverErrors` | object | How many operations answered 500, and how many some 5xx |
| `faultsByCategory` | list | Faults counted by kind |
| `faultsByStatus` | list | Faults counted by kind and by the class of status code that carried them |
| `faultsByOperation` | list | Faults counted by operation and kind, with when each pair was first and last seen |
| `findings` | list | The first faults of each kind on each operation, written out whole: request, reply, and a `curl` command |

### `tool`

| Key | Value | What it says |
|---|---|---|
| `name` | text | `RESTest` |
| `version` | text | The version that wrote the file, or `unknown` when RESTest was run straight from a build directory rather than from a packaged build |

### `faultCatalogue`

| Key | Value | What it says |
|---|---|---|
| `name` | text | `Web Fuzzing Commons` |
| `version` | text | The version of its fault catalogue the codes were taken from: `0.8.0` |

### `api`

| Key | Value | What it says |
|---|---|---|
| `title` | text | The title the document gives the API |
| `baseUrl` | text | The address requests were sent to |

### `totals`

| Key | Value | What it says |
|---|---|---|
| `requests` | number | Requests sent |
| `operations` | number | Operations at least one request went to |
| `faults` | number | Faults found, every one |
| `faultKinds` | number | How many different kinds of fault, by catalogue number |
| `operationsWithFaults` | number | How many operations had at least one fault |
| `faultsWrittenInFull` | number | How many of the faults are written out whole under `findings` |
| `faultsCountedOnly` | number | How many are counted and not written out, because their kind on their operation, or the file, had had its share |
| `elapsed` | length of time | How long the run took, reading the document included |

### `skippedOperations[]`

Every operation the run did not try, however many there are, where the screen names only the first
five. Operations the plan's filter left alone on purpose are not here: they were not skipped.

| Key | Value | What it says |
|---|---|---|
| `operation` | text | The operation, by its `operationId`, or by its method and path when it has none |
| `reason` | text | Why it could not be tested |

### `dictionaries`

| Key | Value | What it says |
|---|---|---|
| `read` | list | Every list of values the run read |
| `refused` | list | Every file handed over with `--dictionary` that could not be read, so none of its values were sent |

### `dictionaries.read[]`, `dictionaries.refused[]`

| Key | Value | What it says |
|---|---|---|
| `name` | text | In `read`: the name the plan calls the list by |
| `from` | text | The file it came from, or the list RESTest carries itself |
| `reason` | text | In `refused`: why it could not be read |

### `limits`

How much the file may write out whole. Each is a [setting](settings.md#report) of the same name under
`report.`.

| Key | Value | What it says |
|---|---|---|
| `writeUpsPerOperationAndKind` | number | How many faults of one kind on one operation are written out whole under `findings` |
| `writeUpsInTotal` | number | How many faults are written out whole in the whole file |
| `mostBodyBytesKept` | number | How many bytes of any one body are quoted. A body cut short says how long it was |

### `settings[]`

Every setting there is, changed or not, so that two directories of results can be compared line by
line. [The settings](settings.md) lists them.

| Key | Value | What it says |
|---|---|---|
| `key` | text | The setting's full name, such as `engine.maxConcurrency` |
| `value` | text | Its value, written the way a settings file would |
| `source` | text | Where the value came from: `default`, `worked out` (it follows another setting that was changed), `file`, `environment` or `command line` |

### `engine`

| Key | Value | What it says |
|---|---|---|
| `requestsSent` | number | Requests that went out |
| `wallClock` | length of time | How long the engine was running |
| `idle` | length of time | How much of that it had no request in flight |
| `idleFraction` | number | The same as a share, from `0` to `1`, to three decimals |
| `meanResponseTime` | length of time | How long the API took to answer, on average |
| `peakConcurrency` | number | The most requests that were in flight at once |
| `concurrencyLimit` | number | The most that were allowed to be |

### `phases[]`

| Key | Value | What it says |
|---|---|---|
| `name` | text | `opening lap` |
| `startedAt` | moment | When it began |
| `finishedAt` | moment | When it ended |
| `elapsed` | length of time | How long it took |
| `cutShort` | `true` or `false` | `true` when it was ended before it had sent everything it meant to: the budget ran out, or the run was stopped |
| `requests` | number | Requests it sent |
| `operations` | number | Operations it sent them to |
| `operationsAnswering2xx` | number | Operations that answered at least one of them with a success |
| `replies` | object | `byClass`, counted the way `replies.byClass` is |

### `phases[].replies`

| Key | Value | What it says |
|---|---|---|
| `byClass` | object | Requests by how they ended: `2xx`, `4xx` and so on, and `noReply` when some got none |

### `replies`

How every request of the run ended, whatever the rules made of the reply. This counts *attempts*:
the fault counts below count *faults*, and the two never add up, since one reply can be wrong in two
ways and most replies are wrong in none.

| Key | Value | What it says |
|---|---|---|
| `total` | number | Requests that ended, one way or another |
| `withNothingWrong` | number | Requests no rule found anything wrong with |
| `byClass` | object | Requests by the first digit of their status code — `"2xx": 171`, `"4xx": 245` — and `noReply` for those that got no reply, in that order |
| `commonest` | list | The ten exact status codes answered most, commonest first |

### `replies.commonest[]`

| Key | Value | What it says |
|---|---|---|
| `status` | number | A status code |
| `count` | number | How many replies carried it |

### `serverErrors`

What the API did, rather than what a rule decided: these count whether or not any fault was
reported. They are counted the way other testing tools count them, and the two last keys say how, so
that a number here can be put beside somebody else's.

| Key | Value | What it says |
|---|---|---|
| `operationsAnswering500` | number | Operations that answered 500 at least once |
| `operationsAnsweringAny5xx` | number | Operations that answered any 5xx at least once |
| `countedOver` | text | `replies`: counted over what the API answered, not over faults |
| `distinctBy` | text | `operation`: an operation counts once however many times it answered so |

### Faults

The faults are counted three ways, every one of them, and the first few of each kind on each
operation are written out whole under `findings`. The rules behind the counting are in [How faults
are counted](faults.md#how-faults-are-counted).

### `faultsByCategory[]`

In the order the kinds were first found.

| Key | Value | What it says |
|---|---|---|
| `code` | number | The catalogue number: `100` for `F100` |
| `descriptiveName` | text | The catalogue's name for the kind |
| `testCaseLabel` | text | The name the catalogue suggests for a test that reproduces it |
| `label` | text | Number and name in one: `F100:HTTP Status 500` |
| `count` | number | How many faults of this kind |

### `faultsByStatus[]`

One row for each kind of fault and class of status code that carried it. It shows, for example, how
many faults came back with an ordinary `2xx`.

| Key | Value | What it says |
|---|---|---|
| `code` | number | The catalogue number |
| `label` | text | Number and name in one |
| `statusClass` | text | `2xx`, `5xx` and so on, or `noReply` |
| `count` | number | How many faults |

### `faultsByOperation[]`

One row for each operation and kind of fault. Complete: every pair that went wrong is here.

| Key | Value | What it says |
|---|---|---|
| `operation` | text | The operation |
| `code` | number | The catalogue number |
| `label` | text | Number and name in one |
| `count` | number | How many faults of this kind on this operation |
| `writtenInFull` | number | How many of them are written out under `findings` |
| `countedOnly` | number | How many are only counted |
| `firstSeenAt` | moment | When the first was found |
| `lastSeenAt` | moment | When the last was found, so a kind that only began going wrong late in a run shows as such |

### `findings[]`

The faults written out whole, in the order they were found. A fault is written out while its kind on
its operation has had fewer than `limits.writeUpsPerOperationAndKind` written and the file fewer than
`limits.writeUpsInTotal`; the rest are counted, under `faultsByOperation`, and not written. A room
per operation and kind, rather than the first so many faults, is what keeps a kind first found late
in a run from having nothing written about it.

| Key | Value | What it says |
|---|---|---|
| `category` | object | The kind of fault: `code`, `descriptiveName`, `testCaseLabel` and `label`, as in `faultsByCategory` |
| `operation` | text | The operation |
| `summary` | text | What is wrong, in a sentence |
| `details` | list of text | The particulars, when there are any: for `F200`, each place in the body that disagreed with the document |
| `curl` | text | A command that sends the same request again. A key handed over with `--auth` appears as its mask, a word such as `REDACTED-AUTH.api_key`; put the key back in place of that whole word before running it |
| `status` | number | Only when a status code arrived: what the API answered |
| `statusClass` | text | `2xx`, `5xx` and so on, or `noReply` |
| `interaction` | object | The whole exchange: what was meant, what was sent, what came back |

### `findings[].category`

| Key | Value | What it says |
|---|---|---|
| `code` | number | The catalogue number |
| `descriptiveName` | text | The catalogue's name for the kind |
| `testCaseLabel` | text | The name the catalogue suggests for a test that reproduces it |
| `label` | text | Number and name in one |

### `findings[].interaction`

One exchange with the API. A run kept with `--store` writes every exchange in this same shape, so a
fault in the report and the same fault in `run.sqlite` read the same.

| Key | Value | What it says |
|---|---|---|
| `id` | text | The exchange's own identifier, which other exchanges name when they were built from it |
| `sentAt` | moment | When the request went out |
| `elapsed` | length of time | How long the answer took |
| `testCase` | object | What the request was meant to be: each value, where it came from, and what was expected |
| `request` | object | The request exactly as it was sent |
| `outcome` | object | What came back |

### `findings[].interaction.testCase`

| Key | Value | What it says |
|---|---|---|
| `id` | text | The test case's own identifier |
| `operation` | text | The operation |
| `parameters` | list | Each parameter sent |
| `body` | object | Only when a body was sent: the body as it was chosen |
| `intent` | text | What the request expected: `acceptable` (it should be accepted), `refusalExpected` (something was broken on purpose, so it should be refused), `pushing` (built from values nobody sensible would send) or `unknown` (nothing in particular) |
| `mutation` | object | Only for a request that is an accepted one with one thing changed: what was changed |
| `sequence` | object | Only for a step of a series about a thing the run created: which series, and which step |

### `findings[].interaction.testCase.parameters[]`

| Key | Value | What it says |
|---|---|---|
| `name` | text | The parameter's name |
| `in` | text | Where it goes: `PATH`, `QUERY`, `HEADER` or `COOKIE` |
| `value` | any | The value, as JSON |
| `origin` | object | Where the value came from |

### `findings[].interaction.testCase.body`

| Key | Value | What it says |
|---|---|---|
| `mediaType` | text | The media type it was sent as, such as `application/json` |
| `value` | any | The body, as JSON |
| `origin` | object | Where it came from |
| `sentAs` | text | Only when the body went out on purpose as something no value can be written as — no bytes at all, JSON cut off halfway, the right content under the wrong media type: the exact text that went out. `value` is then what it was made from |
| `sentAsBytes` | number | Only when `sentAs` was cut short: how long the whole of it was |

### `findings[].interaction.testCase.parameters[].origin`, `findings[].interaction.testCase.body.origin`

Where a value came from. Which of these keys are there depends on `kind`.

| Key | Value | What it says |
|---|---|---|
| `kind` | text | `declared`: the document stated it. `generated`: a source made it up or picked it. `derived`: it was taken from an earlier exchange |
| `stated` | text | For `declared`: which of the document's statements — `default`, `enumeration` or `example` |
| `source` | text | For `generated`: the source that produced it — one of the plan's sources, a list of values by name, or the kind of change made to an accepted request |
| `from` | text | For `derived`: the `id` of the exchange it was taken from |
| `description` | text | For `derived`: how it was taken |

### `findings[].interaction.testCase.mutation`

| Key | Value | What it says |
|---|---|---|
| `of` | text | The `id` of the accepted exchange this request changes |
| `operator` | text | The kind of change, such as `dropRequired` or `notJson`: [the table of them](campaign-format.md#changing-one-thing-in-a-request-that-worked) |
| `in` | text | Where the change is: `PATH`, `QUERY`, `HEADER`, `COOKIE` or `BODY` |
| `path` | text | Which value was changed: a parameter's name, or the way down to a property inside the body |
| `description` | text | What was done, in words |

### `findings[].interaction.testCase.sequence`

| Key | Value | What it says |
|---|---|---|
| `shape` | text | The kind of series, such as `readAfterDelete`: [the table of them](campaign-format.md#series-of-requests-around-a-thing-the-run-created) |
| `step` | number | Which step this is; the creation is step 1 |
| `follows` | list of text | The `id`s of the exchanges before it in the series |
| `description` | text | What this step asks, in words |

### `findings[].interaction.request`

| Key | Value | What it says |
|---|---|---|
| `method` | text | `GET`, `POST` and so on |
| `url` | text | The whole address, query included |
| `headers` | list | Every header, in the order sent |
| `body` | object | Only when the request had a body |

### `findings[].interaction.outcome`

What came back, in one of three shapes, told apart by `kind`.

| Key | Value | What it says |
|---|---|---|
| `kind` | text | `answered`: a whole reply came back. `malformed`: something came back that was not a whole reply. `failed`: nothing came back — refused, timed out, unreachable |
| `status` | number | For `answered`, and for `malformed` when a status line arrived: the status code |
| `statusText` | text | The words after the status code, when the API sent any |
| `protocol` | text | The version of HTTP the reply came in, when known |
| `headers` | list | For `answered` and `malformed`: every header of the reply |
| `body` | object | For `answered` and `malformed`, only when the reply had a body |
| `reason` | text | For `malformed` and `failed`: what went wrong |

### `findings[].interaction.request.headers[]`, `findings[].interaction.outcome.headers[]`

| Key | Value | What it says |
|---|---|---|
| `name` | text | The header's name |
| `value` | text | Its value. A key handed over with `--auth` appears as its mask |

### `findings[].interaction.request.body`, `findings[].interaction.outcome.body`

| Key | Value | What it says |
|---|---|---|
| `mediaType` | text | The media type it came under |
| `text` | text | The body, when it is text |
| `base64` | text | The body in Base64, when it is not text |
| `wireLength` | number | Only when the body was cut short — because a run keeps only so much of any reply (`engine.maxRetainedResponseBytes`), or because the report quotes only so much of any body (`limits.mostBodyBytesKept`): how many bytes the whole of it was |

### Reading it with `jq`

Every count, by operation and kind:

```bash
jq -r '.faultsByOperation[] | "\(.count)\t\(.label)\t\(.operation)"' restest-out/report.json
```

The command that sends the first fault's request again:

```bash
jq -r '.findings[0].curl' restest-out/report.json
```

The settings a run did not take from RESTest's own defaults:

```bash
jq '.settings[] | select(.source != "default")' restest-out/report.json
```

## `run.sqlite`

Written only with `--store`, because a minute against a fast API keeps hundreds of megabytes and
nothing in an ordinary run reads them back. It holds every exchange the run had, so the run can be
looked at again later without asking the API anything. It is an ordinary SQLite database, which any
SQLite client opens, with one table, `interaction`, and one row per exchange:

| Column | What it holds |
|---|---|
| `id` | The exchange's identifier, the `id` of its `document` |
| `test_case` | The test case's identifier |
| `operation` | The operation |
| `method` | The method |
| `url` | The whole address |
| `status_code` | The status code, or nothing when none arrived |
| `outcome` | `answered`, `malformed` or `failed` |
| `sent_at` | When the request went out, as a moment |
| `sent_at_nanos` | The same, in nanoseconds, for putting exchanges in order |
| `elapsed_nanos` | How long the answer took, in nanoseconds |
| `document` | The whole exchange, as JSON, in the shape of [`findings[].interaction`](#findingsinteraction) |

The columns other than `document` are there so that a question can be asked without reading every
exchange whole. How the API answered each operation:

```bash
sqlite3 restest-out/run.sqlite "SELECT operation, status_code, COUNT(*) FROM interaction GROUP BY operation, status_code ORDER BY operation, status_code"
```

The first exchange answered 500, whole:

```bash
sqlite3 restest-out/run.sqlite "SELECT document FROM interaction WHERE status_code = 500 ORDER BY sent_at_nanos LIMIT 1" | jq .
```
