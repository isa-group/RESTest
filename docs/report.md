# What a run leaves behind

A run says what it found in four places: on the screen while it runs, in a file called
`report.json` when it ends, in `report.html` beside it — the same run as a page to open in a
browser — and, only when asked with `--store`, in a file called `run.sqlite` that keeps every
request and every reply. This page says what each line on the screen means, what every key in
`report.json` holds, what the page is, and what `run.sqlite` contains.

The kinds of fault a run reports, and how a fault looks, are on a page of their own:
[The faults RESTest reports](faults.md).

## Where the files go

Into `restest-out/`, under the directory the command was run from, unless `--out` names another. A
directory holds one run: starting another in the same place replaces what is there, and when that
throws away a run kept with `--store` the new run says so. A run worth keeping is given a directory
of its own:

```bash
restest run openapi.yaml --url http://localhost:8080 --out runs/before-the-fix
```

## On the screen

### Before the first request

```
RESTest testing Swagger Petstore - OpenAPI 3.0 at https://petstore3.swagger.io/api/v3

19 of 19 operations can be tested, seed 2692467261078506747, budget 10s
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
| `what it sends depends on the API's own replies, so the seed alone does not repeat this run` | The plan draws on what the API returned, or changes accepted requests, or sends series, or the run leaves alone what the API asks it to (`schedule.waitWhenToldTo`) | Running the same command again makes a similar run rather than the same one. `--store` keeps the run you had. [Getting the seed back](switches.md#getting-the-seed-back) has the files that make a run repeatable from its seed |
| `<n> setting(s) are not what RESTest does by default; --print-settings lists them with where each came from` | A setting was changed, by a file, the environment or `--set` — counting those worked out from it, such as the longest wait for a whole request once `engine.readTimeout` is changed | That the run is not configured the way RESTest ships, so that a setting given through an environment variable is not invisible. `report.json` lists every setting under `settings` |
| `engine.mostRequestsPerMinute is <n>, but engine.limitTheRate is false, so no rate is kept to` | A rate was given, but the switch says not to keep to it: a rate other than the default with the switch off, or any rate given in the environment or with `--set` over a file that turns the switch off, as a printed settings file does | That the run will go as fast as it can, not at the rate. Take `limitTheRate: false` out of the file, or add `--set engine.limitTheRate=true`. [The settings](settings.md#five-things-worth-knowing) say why |
| `<n> part(s) of the document could not be read:` | Some of the document could not be read | Up to five of the problems, then how many more. An operation a problem was in is tested with what could be read, or, when nothing could be built from it, not tested and named at the end. One whose name the document gives to an operation RESTest reads first is called by its method and path, which costs it nothing else |

Problems with a file you handed over — a plan, a list of values, a key in `RESTEST_AUTH` — are
written before all of this, on the error stream, each line beginning with `restest:`.

### While it runs

Each fault is printed as it is found: whether it is an error or a warning, what kind it is, which
request, what the API answered, what is wrong with it, and a `curl` command that sends the request
again. [How a fault looks on the screen](faults.md#how-a-fault-looks-on-the-screen) goes through it
line by line. After the first fifty, of which at most ten may be warnings, the screen stops printing
them and says so; every one is still counted.

### The summary

From a minute's run of the pet store's own code, run in a container of the Web Fuzzing Dataset
(`webfuzzing/wfd-swagger-petstore`), with `--budget 1m --seed 42`:

```
514920 requests to 19 operations in 1m 0s, 1% of it idle
  opening lap: 19 requests in 185ms, 12 of 19 operations answered 2xx
  119648 2xx, 2 3xx, 139923 4xx, 245169 5xx, 10178 no reply
  40833 of them were pushing at the API with values nobody sensible would send, which accounts for some of the 139923 refusals above
  64658 of them changed one thing in a request the API had accepted
  6680 of them were steps of 2296 series about a thing the run created: readAfterDelete 2047, deleteTwice 1740, safeGet 1564, writeUnderDeleted 279, createTwice 1050; 21902 creation(s) meant to begin one were not accepted
  16 operation(s) answered 500, 16 answered some 5xx
247271 errors, on 16 operations:
  245169 x Internal server error  (F100)
        look in the API's log for what failed while it handled the request; the document is not what to fix
  2102 x Invalid data accepted  (F206)
        make the API refuse what its document forbids; where the document forbids more than the API should, loosen the document instead
27559 warnings, on 8 operations, which do not fail the run (checks.failOnWarnings):
  27559 x Reply off schema  (F200)
        the API and its document disagree about the reply's body: compare the places listed with the document, and fix whichever is wrong
some replies could not be checked; report.json names their operations:
  the shape of 414777 replies, on 19 operations: the document gives no shape
  the content type of 165743 replies, on 18 operations: the document declares the status code with no content type
```

| Line | When | What it says |
|---|---|---|
| `<n> requests to <m> operations in <time>, <p>% of it idle` | Always | How much was sent, to how many operations, and for how long. *Idle* is the share of that time in which RESTest had no request in flight: time it spent on something other than testing. ` (<q>% waiting as the API asked)` after it says how much of that share was the API's asking rather than the tool's: time in which nothing was sent because the API answered 429, or 503 with `Retry-After` ([`toldToWait`](#toldtowait)). ` (<r>% keeping to the rate it was given)` says how much was the rate the run was given, `engine.mostRequestsPerMinute` ([`idleKeepingToTheRate`](#engine), under `engine`); with both, they share the brackets, `(<q>% waiting as the API asked, <r>% keeping to the rate it was given)`. `, cut short` at the end means the run was stopped from outside before its budget ran out; `, stopped: the API answered 401 to every request of the opening lap` means the API refused the whole opening lap and the run stopped sending there, ending with `5` |
| `nothing was tested` | Not one request was sent | In place of everything below. Not the same as `no faults found`: a run that asked the API nothing has no evidence that anything is right |
| `opening lap: <n> requests in <time>, <k> of <m> operations answered 2xx` | The run began with its first round | Before anything is chosen by chance, every operation is sent once with the request it is most likely to accept. This line says how long that took and how many operations it got a success from; `opening lap, cut short:` means the budget ran out, or the run was stopped, before the round was over. [The first round of a run](campaign-format.md#the-first-round-of-a-run) |
| `<n> 2xx, <n> 4xx, <n> 5xx, <n> no reply` | Always | How every request ended, by the first digit of the status code; `no reply` when nothing came back. If almost everything was refused (4xx), the requests were the problem rather than the API |
| `<n> of them were pushing at the API with values nobody sensible would send` | The plan has a strategy that pushes | Requests built on purpose from values an API should turn away. Some of the refusals above are theirs. [How much of a run pushes at the API](campaign-format.md#how-much-of-a-run-pushes-at-the-api) |
| `<n> of them changed one thing in a request the API had accepted` | The plan changes accepted requests | Requests that are an accepted one with exactly one thing broken. [Changing one thing in a request that worked](campaign-format.md#changing-one-thing-in-a-request-that-worked) |
| `<n> of them were steps of <s> series about a thing the run created: <kind> <n>, …` | The plan sends series | Requests sent as steps of a short series after a creation — delete it and read it again, create it twice — counted by the kind of series. Creations meant to begin one that the API did not accept are counted after a `;`. [Series of requests around a thing the run created](campaign-format.md#series-of-requests-around-a-thing-the-run-created) |
| `<k> creation(s) meant to begin a series about a thing the run created were not accepted, so no series began` | The plan sends series and none began | The API refused every creation a series would have started from |
| `told to wait: the whole API <n> times, <time> in all; <operation> <n> times, <time> in all, <k> turns skipped; …` | The API answered 429, or 503 with `Retry-After` | Who the API asked the run to leave alone, how often and for how long: the whole API first, when nothing at all was sent, then up to three operations, each with how many of its turns were skipped while it waited, then how many more. [`toldToWait`](#toldtowait) has every one |
| `<a> operation(s) answered 500, <b> answered some 5xx` | Some reply was a 5xx | How many *operations* the API fell over on, rather than how many replies: a run spends its whole budget, so one broken operation asked six hundred times is six hundred broken replies. This is the number worth quoting |
| `no faults found` | Always, when something was sent and nothing was found | The verdict, when it is clean. A run in which nothing at the address answered says it too, and then says why on the error stream and ends with `3`: a run that was answered by nothing has found nothing wrong only because it heard nothing |
| `<n> errors, on <m> operations:` and two lines per kind | Some fault was an error | How many errors, on how many operations, then each kind as `<count> x <title>  (F<code>)`, with what to look at on the line under it. Errors are what end a run with `1`. [Errors and warnings](faults.md#errors-and-warnings) says which kinds are which |
| `<n> warnings, on <m> operations, which do not fail the run (checks.failOnWarnings):` and two lines per kind | Some fault was a warning | The same for warnings, after the errors. A run that found only warnings ends with `0`; with `checks.failOnWarnings` on, the line ends `which fail the run, as checks.failOnWarnings asks:` and so does the run, with `1` |
| `some replies could not be checked; report.json names their operations:` and one line per way and reason | A check could not judge some reply | Each line is `<what> of <n> replies, on <m> operations: <why>` — `the shape of 287676 replies, on 22 operations: the document gives no shape` — one for each check and each reason it gave, most replies first. Said after the verdict because it qualifies it: `no faults found` says nothing about a reply nobody could judge. [What the run could not check](#notchecked) lists every reason, and [the faults](faults.md) say, check by check, when one is given |
| `<n> operations could not be tested:` | Some operation could not be tested | Up to five of them, each with the reason — a file upload, say, or a parameter written in a style requests are not built in — then how many more. Said after the verdict because it qualifies it: `no faults found` says nothing about an operation that was never tried. `report.json` names every one |
| `<n> lists of values could not be read, so none of their values were sent:` | A list handed over with `--dictionary` could not be read | Each list and why, so that a run made without the values somebody meant it to have is not mistaken for one made with them |

### The last lines

```
report written to restest-out/report.json (1.5 MiB)
page written to restest-out/report.html (1.5 MiB)
the run itself was not kept; pass --store to keep every request and reply
```

| Line | When | What it says |
|---|---|---|
| `<n> test case(s) could not be built and were skipped` | Some request could not be put together | Usually a value of a kind no request can carry |
| `<n> replies repeated a key back; …` | The API sent a key handed over with `--auth` back in a reply | It is hidden there too, and the check of replies against the document passes over whatever hiding it changed. A reply the hiding made into something that is not JSON at all could not be checked, and is counted under `notChecked` as `keyHidden` |
| `report written to <file> (<size>)` | The report could be written | Where `report.json` is |
| `page written to <file> (<size>)` | The report could be written, and [`report.html`](settings.md#report) is on, as it is unless set otherwise | Where [`report.html`](#reporthtml) is |
| `run stored in <file> (<size>)` | `--store` was given | Where `run.sqlite` is |
| `the run itself was not kept; pass --store to keep every request and reply` | `--store` was not given | Only the report, and the page, were written |
| `a run kept in <directory> by an earlier command was replaced` | `--out` pointed at a directory holding a run kept with `--store` | That run is gone |

A few more lines go to the error stream, each beginning with `restest:`:

- **that the run was stopped from outside**, and everything above is what it found until then — the
  run ends with `130` or `143`;
- **that the API answered 401 to every request of the opening lap**, so the run stopped sending, after
  how long, and what to do about the key: the one it held, which the API refused, or, when it held
  none, how the one the document asks for is handed over with `--auth` — the run ends with `5`,
  unless it was also stopped from outside;
- **that some requests were never answered** and the run stopped waiting for them, so they are
  missing from the counts — said whatever the run ends with, `0` and `1` included;
- **that some requests were lost by RESTest itself**, or **that a rule or a report broke** — the run
  ends with `4`;
- **when nothing at all was tested**, which of three reasons it was — the run ends with `3`.

[The exit codes](command-line.md#exit-codes) says what each number means.

The lines that name what could not be tested or read, and the counts of errors and warnings, say one
thing in the singular: `1 operation could not be tested:`, `1 list of values could not be read, so
none of its values were sent:`, `1 warning, on 1 operation, which does not fail the run`. Others count
as they always have: `1 of 1 operations can be tested`.

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
| `faultCatalogue` | object | Which catalogue the fault codes come from, and which version of it. A code from 900 to 999 is RESTest's own instead, for a kind the catalogue has no number for |
| `createdAt` | moment | When the report was written |
| `api` | object | Which API was tested, and where |
| `run` | object | How the run was started: which plan and which file of settings, and whether every request and reply was kept |
| `cutShort` | `true` or `false` | `true` when the run was stopped from outside — Ctrl-C, `kill`, `docker stop` — before its budget ran out. Every number in the file is then what the run found until it was stopped |
| `verdict` | text | What the run amounts to, in one word: whether it found errors, found none, or could not say. The number the command ends with follows from the same answer |
| `unauthorized` | object or `null` | When the API answered 401 to every request of the opening lap and the run stopped sending there: when, how many requests that lap sent, and where each key the run held went. `null` for every other run |
| `toldToWait` | object or `null` | What the API asked the run to leave alone, and for how long: each operation answered 429, or 503 with `Retry-After`, and the waits of the whole API. `null` when `schedule.waitWhenToldTo` is off |
| `totals` | object | The run in twelve numbers |
| `operations` | list | Every operation the document describes, with what happened to it and how its requests ended |
| `skippedOperations` | list | Every operation the run could not test, each with the reason |
| `documentIssues` | list | Every part of the document that could not be used as it is written |
| `keysNotGiven` | list | Every key the document asks for that nobody handed over |
| `notChecked` | list | How many replies each check could not judge, and why, with the operations they came from |
| `dictionaries` | object | The lists of values handed over with `--dictionary`: the ones read, and the ones that could not be |
| `limits` | object | How much of the run this file was allowed to write out whole |
| `settings` | list | Every setting the run used, with its value and where that value came from |
| `engine` | object | How the part that sends requests spent the run |
| `phases` | list | Each stretch of the run with a purpose of its own — today, only the opening lap — and what it achieved |
| `timeline` | object | How the run went over the time it was given: what came back in each equal slice of it |
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
| `version` | text | The version of its fault catalogue the codes were taken from: `0.9.0` |

The catalogue leaves the codes from 900 to 999 to each tool. The two RESTest uses, `920` and `921`,
are its own: their names are RESTest's, their keys start with `RESTEST_`, and they mean nothing to
another tool ([the faults](faults.md#the-catalogue)).

### `api`

| Key | Value | What it says |
|---|---|---|
| `title` | text | The title the document gives the API |
| `baseUrl` | text | The address requests were sent to |

### `run`

What the command was handed beyond its settings. Never the command line itself: it can carry a key
or a token, in an address for one, where nothing would know to hide it.

```json
"run": {"plan": null, "settingsFile": null, "stored": false}
```

| Key | Value | What it says |
|---|---|---|
| `plan` | text or `null` | The plan, as it was named after `--campaign`; `null` when the run followed the plan RESTest carries |
| `settingsFile` | text or `null` | The file of settings, as it was named after `--settings`; `null` when none was given. Where each setting came from is under [`settings`](#settings) |
| `stored` | `true` or `false` | Whether every request and reply was kept, with `--store`, in `run.sqlite` beside this file |

### `verdict`

What the run amounts to, in one word. The questions are asked in this order, and the first that
applies gives the word: a run stopped from outside was stopped, whatever it found until then; so was
a run the API stopped by answering 401 to every request of its opening lap; a run
that sent nothing, or was answered by nothing, tested nothing, so its faults say nothing about the
API — an API that accepts connections and never answers is counted thousands of times as a reply
that never came, and is still `nothingAnswered`; only then do errors, and warnings when
`checks.failOnWarnings` is on, decide.

The number the command ends with is worked out by the same rule, so the two agree, with one
exception: whether RESTest itself went wrong is only known after this file is written, and only
the number says it, with `4`. [The exit codes](command-line.md#exit-codes) gives each number.

| Word | Ends with | When |
|---|---|---|
| `stopped` | `130` or `143` | The run was stopped from outside before its time ran out |
| `unauthorized` | `5` | The API answered 401 to every request of the opening lap, so the run stopped sending: it held no key the API would take. [`unauthorized`](#unauthorized) says when, and which key it held |
| `nothingTested` | `3` | Not one request went out: none could be built from the document, or the time ran out before the first. The screen says which |
| `nothingAnswered` | `3` | Requests went out and not one of them was answered |
| `errorsFound` | `1` | At least one error was found in the API |
| `failedOnWarnings` | `1` | No error, but warnings, and `checks.failOnWarnings` is on |
| `noErrorsFound` | `0` | No error was found; there may have been warnings |

### `unauthorized`

Said when the API answered 401 — "who are you?" — to every request of the opening lap, and the run
stopped sending there rather than spend its budget on the same refusal. The opening lap sends every
operation once, each with the request it is likeliest to accept; when each of those requests was
answered and every answer was 401, the API refused all of itself to the run, for want of a key or
because the key is wrong, and the run ends with `5`. A request of the lap that got no reply, or any
reply other than 401 — a 403 included — means the run can still learn something, and it goes on.
`schedule.stopWhenTheOpeningLapIsRefused=false` lets such a run go on as well. `null` for every run
the API did not stop. From a run handed a key the API would not take:

```json
"unauthorized": {"at": "2026-10-28T12:00:00.181Z", "lapRequests": 9, "keysHeld": [{"in": "header", "name": "Authorization"}]}
```

| Key | Value | What it says |
|---|---|---|
| `at` | moment | When the run decided to stop sending. The requests already on their way then were waited for, as at the end of a run's time, and are counted with the rest, whatever they brought back |
| `lapRequests` | number | How many requests the opening lap sent, every one of them answered 401 |
| `keysHeld` | list | Where each key the run held went, as [`keysNotGiven`](#keysnotgiven) writes a key nobody handed over, and never the key itself. Empty when it held none, or none that went with an operation it could test: the keys the document asks for and nobody handed over are under `keysNotGiven` |

### `unauthorized.keysHeld[]`

| Key | Value | What it says |
|---|---|---|
| `in` | text | Which part of a request the key went in, by the words [`keysNotGiven[].in`](#keysnotgivenin) lists |
| `name` | text | The name it went under there |

### `toldToWait`

What the API asked the run to leave alone. An API asked too much answers 429, "too many requests",
or 503 with a `Retry-After` header saying when to ask again. The operation whose request drew it is
then sent nothing for as long as the header says — or, when it says nothing that can be read, for
`schedule.firstGuessedWait`, doubled each time the same operation is told again up to
`schedule.longestGuessedWait` — while every other operation goes on. When a second operation is told
to wait while another still waits, the limit is the whole API's, and nothing at all is sent until its
wait is over: what had been handed to the part that sends is held back too, and it then starts again
from one request at a time; what it still holds when the run stops sending is never sent, and is
counted under `noReply`. The request that drew the answer is not sent again; the operation's next
turn after the wait builds a new one. Every length is counted up to the moment the run stopped
sending, and a wait that began after it is not counted. `null` when `schedule.waitWhenToldTo` is off.
From a minute's run of an API whose one operation had spent its quota for the hour, and answered
429 without saying for how long:

```json
"toldToWait": {"idle": "PT0S", "wholeApi": {"times": 0, "waited": "PT0S"}, "operations": [{"operation": "createTeleTan", "times": 12, "stated": 0, "waited": "PT41S", "turnsSkipped": 57728}]}
```

A run that waits when told to and never was writes
`{"idle": "PT0S", "wholeApi": {"times": 0, "waited": "PT0S"}, "operations": []}`.

| Key | Value | What it says |
|---|---|---|
| `idle` | length of time | How much of the run's idle time — the [engine's](#engine) `idle` — came while it waited as the API asked: the whole API waiting, or every operation it could send waiting. The rest is the tool's own |
| `wholeApi` | object | How often and how long nothing at all was sent |
| `operations` | list | Each operation that was told to wait, in the order they first were |

### `toldToWait.wholeApi`

| Key | Value | What it says |
|---|---|---|
| `times` | number | How many waits of the whole API began |
| `waited` | length of time | How long they lasted, in all |

### `toldToWait.operations[]`

| Key | Value | What it says |
|---|---|---|
| `operation` | text | The operation, by the name the document gives it |
| `times` | number | How many waits it was given. The refusals of requests already on their way when a wait began begin no other |
| `stated` | number | How many of them lasted as long as the API said in `Retry-After`; the others were guessed |
| `waited` | length of time | How long they lasted, in all |
| `turnsSkipped` | number | How many times its turn came while it waited, and nothing was sent. A turn that came while the whole API waited is not counted here: nothing at all was sent then |

### `totals`

| Key | Value | What it says |
|---|---|---|
| `requests` | number | Requests sent |
| `operations` | number | Operations at least one request went to |
| `faults` | number | Faults found, every one |
| `faultKinds` | number | How many different kinds of fault, by number |
| `operationsWithFaults` | number | How many operations had at least one fault |
| `errors` | number | How many of the faults were errors: the API failing at its job. A run that found one ends with `1` |
| `warnings` | number | How many were warnings: the API answering something its document does not describe. `errors` and `warnings` add up to `faults` |
| `operationsWithErrors` | number | How many operations had at least one error |
| `operationsWithWarnings` | number | How many had at least one warning. An operation with both is counted in both, so this and `operationsWithErrors` can add up to more than `operationsWithFaults` |
| `faultsWrittenInFull` | number | How many of the faults are written out whole under `findings` |
| `faultsCountedOnly` | number | How many are counted and not written out, because their kind on their operation, or the file, had had its share |
| `elapsed` | length of time | How long the run took, reading the document included |

### `operations[]`

Every operation the document describes, whether or not anything went wrong with it, so that one that
answered every request well can be told from one the run never sent anything to. First the ones that
could be read, path by path in the order the document gives the paths; then the ones that could not be read, in the
order the run named them, with no method or path, which is what could not be read. From a run of
pet-clinic, with the list cut to its first two:

```json
"operations": [
  {"operation": "getVisit", "method": "GET", "path": "/api/visits/{visitId}", "summary": "Get a visit by ID",
   "state": "tested", "requests": 148, "replies": {"byClass": {"2xx": 10, "4xx": 86, "5xx": 52}}},
  {"operation": "updateVisit", "method": "PUT", "path": "/api/visits/{visitId}", "summary": "Update a visit by ID",
   "state": "tested", "requests": 147, "replies": {"byClass": {"2xx": 1, "4xx": 110, "5xx": 36}}}
]
```

The requests of every operation add up to `totals.requests`, and their classes to
`replies.byClass`. What went wrong on each is under [`faultsByOperation`](#faultsbyoperation), by
the same name.

| Key | Value | What it says |
|---|---|---|
| `operation` | text | The operation, by the name every other part of the file calls it |
| `method` | text | Only when it could be read: its method, such as `GET` |
| `path` | text | Only when it could be read: its path as the document writes it, such as `/api/visits/{visitId}` |
| `summary` | text | Only when the document gives one: what the document says it does |
| `state` | text | What happened to it: one of the words below |
| `reason` | text | Only when it could not be tested: why, in the words of `skippedOperations` |
| `requests` | number | How many requests went to it |
| `replies` | object | How they ended |

### `operations[].state`

| Word | What it means |
|---|---|
| `tested` | At least one request went to it |
| `notReached` | It could have been tested, and no request went to it: the run ended first, or no request could be built for it from what the document says. A clean record here is no news at all |
| `couldNotBeTested` | It was not tried: the document could not be read for it, or it asks for something RESTest cannot send yet, such as a file upload. It is also under `skippedOperations` |
| `leftAloneByThePlan` | The plan asked the run to leave it alone, on purpose. It is not under `skippedOperations`, since nothing failed |

### `operations[].replies`

| Key | Value | What it says |
|---|---|---|
| `byClass` | object | How many of its requests ended in each class of status code, `noReply` among them, as `replies.byClass` counts them for the whole run |

### `skippedOperations[]`

Every operation the run did not try, however many there are, where the screen names only the first
five. Operations the plan's filter left alone on purpose are not here: they were not skipped.

| Key | Value | What it says |
|---|---|---|
| `operation` | text | The operation, by its `operationId`, or by its method and path when it has none or the document gives the same one to an operation RESTest reads first |
| `reason` | text | Why it could not be tested |

### `documentIssues[]`

Every part of the document that could not be used as it is written, as the screen counts them under
`part(s) of the document could not be read` before the run begins. From a run of pet-clinic:

```json
"documentIssues": [
  {"location": "info", "message": "attribute info.license.extensions is unexpected", "effect": "document"}
]
```

| Key | Value | What it says |
|---|---|---|
| `location` | text | Where in the document, written the way the document is navigated, such as `paths./pets.get.parameters[2]` |
| `message` | text | What could not be used and why |
| `operation` | text | Only when it belongs to one: the operation it concerns |
| `effect` | text | What it cost: one of the words below |

### `documentIssues[].effect`

| Word | What it means |
|---|---|
| `operationSkipped` | The operation it belongs to is not tested at all, and is under `skippedOperations` |
| `degraded` | The operation is tested with less than the document holds |
| `renamed` | The operation is tested with everything the document says, under a name of RESTest's own, because the document gives its name to another operation too |
| `leftOut` | The operation is tested with everything a request to it can carry; what the document declares beyond that, a path parameter its path has no `{…}` for, is left out, since no request could send it |
| `document` | It is about the document as a whole, not one operation |

### `keysNotGiven[]`

Every key the document asks for that nobody handed over with `--auth`, as the screen says it before
the run begins, with how many of the operations that can be tested ask for it. Those operations are
expected to be refused, which is worth knowing before their refusals are read as the API's doing.
Empty when every key asked for was given, or when none is asked for. A key the document declares
and asks for on no operation is not here, since nothing is expected to be refused for want of it;
the screen still names it. From a run of pet-clinic, with its document asking for an API key on
three operations:

```json
"keysNotGiven": [{"scheme": "api_key", "in": "header", "name": "X-API-Key", "operations": 3}]
```

| Key | Value | What it says |
|---|---|---|
| `scheme` | text | The name the document gives the key |
| `in` | text | Where it would go: one of the words below |
| `name` | text | The name it would go under there |
| `operations` | number | How many of the operations that can be tested ask for it |

### `keysNotGiven[].in`

| Word | Where the key would go |
|---|---|
| `header` | A header of the request |
| `query` | A parameter in the query of the address |
| `cookie` | A cookie |
| `formField` | A field of a body written as a web form |

### `notChecked[]`

What the run could not check: one row for each check that could not judge some replies and each
reason it gave, most replies first, and an empty list when every reply was checked in every way it
could have been. A check switched off is not here: it judged nothing, and `settings` says so. A run
that found nothing wrong found nothing wrong in these replies only because nothing could look at them
that way: a document that gives no shape for a reply leaves its body unchecked, whatever the API
sends in it.

From a minute's run of restcountries, whose document describes every reply only in words —
`"200": {"description": "Successful response"}` — with each list of operations cut to its first two
here:

```json
"notChecked": [
  {"code": 200, "id": "SCHEMA_INVALID_RESPONSE",
   "label": "F200:Schema Violation: Received A Response From API With A Structure/Data That Is Not Matching Its Schema",
   "what": "the shape", "reason": "noShapeDeclared",
   "why": "the document gives no shape",
   "replies": 287676,
   "operations": [{"operation": "v1All", "replies": 13077}, {"operation": "v1Alphacode", "replies": 13077}]},
  {"code": 921, "id": "RESTEST_UNDECLARED_CONTENT_TYPE",
   "label": "F921:A Content Type The Document Does Not Declare",
   "what": "the content type", "reason": "noContentTypeDeclared",
   "why": "the document declares the status code with no content type",
   "replies": 286159,
   "operations": [{"operation": "v1All", "replies": 13077}, {"operation": "v1Alphacode", "replies": 13077}]}
]
```

| Key | Value | What it says |
|---|---|---|
| `code` | number | The kind of fault the check reports, which tells the checks apart: `200` is the check of a reply's shape, `920` of its status code, `921` of its content type |
| `id` | text | The key of that kind |
| `label` | text | The code and the name of that kind |
| `what` | text | What the check looks at: `the shape`, `the status code`, `the content type` |
| `reason` | text | Why it could not, in one word; [the reasons](#notcheckedreason) |
| `why` | text | The same, in the words the screen prints |
| `replies` | number | How many replies it could not judge for that reason |
| `operations` | list | Every operation those replies came from, most first |

### `notChecked[].operations[]`

| Key | Value | What it says |
|---|---|---|
| `operation` | text | The operation, named as in `skippedOperations` |
| `replies` | number | How many of the row's replies came from it |

### `notChecked[].reason`

Each reason belongs to one check, except `readInPart`, which two can give. A reply gets at most one
reason from each check: the first that applies in that check's own order, which [the
faults](faults.md) give check by check. The table lists them in the order RESTest keeps them, not in
that one. What is not here is not counted at all: a reply with no body has no shape to check, and
one whose status code or content type the document does not declare is reported by the checks of
those, `F920` and `F921`.

| Reason | Check | When |
|---|---|---|
| `noShapeDeclared` | shape | The document declares the reply, and describes it only in words, or names its media type and gives no shape for its body |
| `notJson` | shape | The document gives a shape for a body that is not JSON — text, XML — and RESTest compares only JSON with a shape |
| `cutShort` | shape | The body was longer than RESTest keeps whole (`engine.maxRetainedResponseBytes`), so there was no whole body to judge |
| `notUtf8` | shape | The reply names a character set other than UTF-8, which JSON is always written in |
| `shapeUnusable` | shape | The document gives a shape RESTest cannot use: one pointing at another file, at something the document does not define, or round in a circle, one with a pattern the checker cannot read, or a document that could not be kept to check against |
| `keyHidden` | shape | A key handed over with `--auth` came back in the body and was hidden there, and the body could then no longer be read as JSON, so what is wrong with it may be the hiding's doing |
| `noReplyDeclared` | status code | The document declares no reply at all for the operation, so it promised none |
| `readInPart` | status code, content type | RESTest could not read a reply the document declares for the operation — one given as a reference to a reply it never defines, say — and what came back is not among what it could read, so it may be declared in the reply it could not. What it could read is held to as on any other operation |
| `noContentTypeDeclared` | content type | The document declares the status code with no content type, describing that reply only in words |

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
| `idle` | length of time | How much of that it had no request in flight. Part of it may be quiet the API asked for, which [`toldToWait.idle`](#toldtowait) says, and part quiet the rate it was given made, which `idleKeepingToTheRate` says; the rest is the tool's own |
| `idleFraction` | number | The same as a share, from `0` to `1`, to three decimals |
| `idleKeepingToTheRate` | length of time | How much of the idle time came while the run kept to the rate it was given (`engine.mostRequestsPerMinute`): after each request went out and before the next was allowed to, when nothing could have been sent however quick the tool was. `PT0S` with no rate given |
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

### `timeline`

How the run went, rather than how it ended: the time it was given cut into equal slices — sixty,
unless [`report.timelineIntervals`](settings.md#report) says otherwise — and what came back in
each, by class of status code. A reply counts in the slice its request was sent in, so one answered
after the time was up counts where it was asked, and the slices add up to `replies.byClass`. Only
the slices that began before the run ended are written: a run of a minute stopped after 15 seconds
has fifteen, not sixty. The first slice begins when the command started, so the first slices hold
little or nothing while the document is read; the opening lap's moments, under `phases`, fall on the
same time.

From a minute's run of pet-clinic, with the list of slices cut to its first three of sixty:

```json
"timeline": {
  "startedAt": "2026-10-25T06:29:29.371167Z",
  "budget": "PT1M",
  "interval": "PT1S",
  "intervals": [
    {"byClass": {"2xx": 9, "3xx": 2}},
    {"byClass": {"2xx": 7, "3xx": 1, "5xx": 1}},
    {"byClass": {"2xx": 29, "3xx": 5, "4xx": 18, "5xx": 6}}
  ]
}
```

| Key | Value | What it says |
|---|---|---|
| `startedAt` | moment | When the run began, which is where the first slice begins: the moment the command started, before the document was read |
| `budget` | length of time | The time the run was given with `--budget`, reading the document included |
| `interval` | length of time | How long each slice is: the budget divided by the number of slices, rounded up to a whole millisecond, so the last slice can end after the budget does |
| `intervals` | list | One entry for each slice, in order, from the first to the last that began before the run ended |

### `timeline.intervals[]`

| Key | Value | What it says |
|---|---|---|
| `byClass` | object | What came back to the requests sent in that slice: `2xx`, `4xx` and so on, and `noReply` for those that got none, as `replies.byClass` counts them. Empty when no request was sent in it |

### `replies`

How every request of the run ended, whatever the rules made of the reply. This counts *attempts*:
the fault counts below count *faults*, and the two never add up, since one reply can be wrong in two
ways and most replies are wrong in none.

| Key | Value | What it says |
|---|---|---|
| `total` | number | Requests that ended, one way or another |
| `withNothingWrong` | number | Requests no rule found anything wrong with, those some rule could not judge included. `notChecked` counts, check by check, the replies each check could not judge, whether or not another found something wrong with them, so its counts and this one do not add up |
| `byClass` | object | Requests by the first digit of their status code — `"2xx": 186`, `"4xx": 242` — and `noReply` for those that got no reply, in that order |
| `commonest` | list | The exact status codes answered most, commonest first: as many as `report.commonestStatusCodesListed` says, ten unless set otherwise |

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
| `code` | number | The catalogue number: `100` for `F100`; or, from 900 to 999, RESTest's own: `920` for `F920` |
| `id` | text | The catalogue's key for the kind, `HTTP_STATUS_500` for `F100`, which it says is meant never to change; the key to match on when comparing with a report made under another version of the catalogue from 0.9.0 on. Reports written under 0.8.0, by RESTest 2.0 and 2.1, have none. For one of RESTest's own kinds, a key of RESTest's starting with `RESTEST_`: `RESTEST_UNDECLARED_STATUS_CODE` |
| `descriptiveName` | text | The catalogue's name for the kind, or RESTest's for one of its own |
| `testCaseLabel` | text | The name the catalogue suggests for a test that reproduces it, or RESTest's for one of its own |
| `label` | text | Number and name in one: `F100:HTTP Status 500` |
| `severity` | text | `error` or `warning` ([errors and warnings](faults.md#errors-and-warnings)) |
| `title` | text | What RESTest calls the kind, the words the screen prints: `Internal server error` |
| `advice` | text | What to look at, and whether the document may be what needs fixing, as the summary says it under the kind: `look in the API's log for what failed while it handled the request; the document is not what to fix`. Said here, once for each kind, and in no other row |
| `count` | number | How many faults of this kind |

### `faultsByStatus[]`

One row for each kind of fault and class of status code that carried it. It shows, for example, how
many faults came back with an ordinary `2xx`.

| Key | Value | What it says |
|---|---|---|
| `code` | number | The catalogue number, or RESTest's own from 900 to 999 |
| `id` | text | The catalogue's key for the kind, or RESTest's, starting with `RESTEST_` |
| `label` | text | Number and name in one |
| `severity` | text | `error` or `warning` |
| `title` | text | What RESTest calls the kind |
| `statusClass` | text | `2xx`, `5xx` and so on, or `noReply` |
| `count` | number | How many faults |

### `faultsByOperation[]`

One row for each operation and kind of fault. Complete: every pair that went wrong is here.

| Key | Value | What it says |
|---|---|---|
| `operation` | text | The operation |
| `code` | number | The catalogue number, or RESTest's own from 900 to 999 |
| `id` | text | The catalogue's key for the kind, or RESTest's, starting with `RESTEST_` |
| `label` | text | Number and name in one |
| `severity` | text | `error` or `warning` |
| `title` | text | What RESTest calls the kind |
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
| `category` | object | The kind of fault: `code`, `id`, `descriptiveName`, `testCaseLabel`, `label`, `severity` and `title`, as in `faultsByCategory`; what to look at is said there, once for the kind |
| `context` | text | Only when the check that found it gave one: a word telling apart two faults of the same kind, where one kind covers more than one way of going wrong, as the catalogue's own report format tells them apart. No check of this version gives one |
| `operation` | text | The operation |
| `summary` | text | What is wrong, in a sentence |
| `details` | list of text | The particulars, when there are any: for `F200`, each place in the body that disagreed with the document |
| `curl` | text | A command that sends the same request again. A key handed over with `--auth` appears as its mask — `REDACTED-AUTH`, or a longer word such as `REDACTED-AUTH.api_key`, as the first lines of the run say — so put the key back in place of that whole word before running it |
| `status` | number | Only when a status code arrived: what the API answered |
| `statusClass` | text | `2xx`, `5xx` and so on, or `noReply` |
| `interaction` | object | The whole exchange: what was meant, what was sent, what came back |

### `findings[].category`

| Key | Value | What it says |
|---|---|---|
| `code` | number | The catalogue number, or RESTest's own from 900 to 999 |
| `id` | text | The catalogue's key for the kind, or RESTest's, starting with `RESTEST_` |
| `descriptiveName` | text | The catalogue's name for the kind, or RESTest's for one of its own |
| `testCaseLabel` | text | The name the catalogue suggests for a test that reproduces it, or RESTest's for one of its own |
| `label` | text | Number and name in one |
| `severity` | text | `error` or `warning` |
| `title` | text | What RESTest calls the kind |

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
| `timedOutWaitingFor` | text | For `failed`, only when RESTest stopped waiting because one of its time limits ran out: `reply` when a connection to the API was open and no reply's status line and headers had come back, the kind [`F312`](faults.md#f312-no-reply-in-time) reports; `connection` when no connection could be made in time. Reports and stored runs written by RESTest 2.0 and 2.1 never have it |

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
jq -r '.faultsByOperation[] | "\(.count)\t\(.severity)\t\(.title)\t\(.operation)"' restest-out/report.json
```

The errors alone, the faults that end a run with `1`:

```bash
jq -r '.faultsByOperation[] | select(.severity == "error") | "\(.count)\t\(.title)\t\(.operation)"' restest-out/report.json
```

The command that sends the first fault's request again:

```bash
jq -r '.findings[0].curl' restest-out/report.json
```

The settings a run did not take from RESTest's own defaults:

```bash
jq '.settings[] | select(.source != "default")' restest-out/report.json
```

## `report.html`

The same run as `report.json`, as a page to open in a browser: one file, written beside
`report.json` once it is whole, that needs nothing beside it and fetches nothing, so that it opens
from what a build kept, from an e-mail or on a laptop with no network. It shows the replies,
errors and warnings in all and on every operation, the timeline, the first request written out
whole for each kind of fault on each operation with buttons that copy its `curl` command or the
whole of it as an issue in Markdown, what could not be tested or read, how the run was started, and
the number it ended with, worked out from [`verdict`](#verdict). [Chapter 4 of the manual](manual/04-reading-what-a-run-says.md#the-page) shows it.

- **It shows nothing `report.json` does not hold.** The whole of `report.json` is inside it, as it
  was written, and the page draws itself from that in the browser, with JavaScript. A page opened
  where JavaScript is turned off says so and points at `report.json`.
- **A program reads `report.json`, not the page.** The run sits in the page inside
  `<script type="application/json" id="report">`, with every `<` written as `\u003c` so that nothing
  quoted from a reply can end that element early; read as JSON, it is `report.json` exactly. How the
  rest of the page is laid out is not part of what stays the same from one version to the next.
- **It fetches nothing.** The page tells the browser so, in its own `Content-Security-Policy`: no
  address is visited, no script but its own is run, and the only pictures are the ones inside it.
  What came from the API is put on the page only as text.
- **A key handed over is hidden in it** as it is in `report.json`, since it is the same text.
- **`--set report.html=false` writes no page**, for a run whose results only a program will read.
  A page left by an earlier run in the same directory is removed either way, so that it is never
  taken for this run's.

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
