# RESTest 2.0

[![CI](https://github.com/isa-group/RESTest/actions/workflows/ci.yml/badge.svg?branch=v2)](https://github.com/isa-group/RESTest/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

> **This branch (`v2`) is a complete rewrite.** It is not backwards-compatible with RESTest 1.x.
> The 1.x code and documentation remain on `master`.

A black-box testing tool for REST APIs. Provide an OpenAPI specification; RESTest generates and
executes test cases and reports the failures it finds — with zero configuration to get started.

## Status

Active development. See [`ROADMAP.md`](ROADMAP.md) for the milestone plan and
[`docs/DESIGN.md`](docs/DESIGN.md) for the architecture and design rationale.

## License

Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Building

Requires JDK 21 or later and Git.

```bash
git clone https://github.com/isa-group/RESTest.git
cd RESTest
git switch v2
./mvnw verify
```

That builds all ten modules, runs the test suite and checks the architecture rules. Every push and
pull request runs the same command on Linux, macOS and Windows against Java 21, 25 and 26 — see
[docs/ci.md](docs/ci.md).

## Running it

One command, an OpenAPI document and an address:

```bash
./mvnw -q install -DskipTests
./restest run https://petstore3.swagger.io/api/v3/openapi.json \
    --url https://petstore3.swagger.io/api/v3 --budget 10s --seed 20260914
```

That last one is somebody else's public demonstration server, and RESTest tests an operation that
creates something by creating something: a run leaves pets, orders and users behind wherever it is
pointed. Keep the budget short if you are a guest there, and point `--url` at a copy of your own for
anything longer — or for anything you would mind having written to.

Building needs a JDK; running what was built does not: RESTest runs on a plain Java 21 runtime, an
`eclipse-temurin:21-jre` image included. A build that asks for containers — `./mvnw verify -Pit`, and
the smoke job on every pull request — compiles the tool and runs it inside one of those images to
check that this stays true.

RESTest reads the document, invents requests from it, sends them for as long as you gave it, judges
every reply against what the document promised, and prints each disagreement with a `curl` command
that does it again. This is the shape of what it prints, from one run of the command above; the
numbers in it are that run's and yours will be different:

```
RESTest testing Swagger Petstore - OpenAPI 3.0 at https://petstore3.swagger.io/api/v3

19 of 19 operations can be tested, seed 20260914, budget 10s
  2 of them ask for an API key that was not given (api_key, in the header api_key): --auth <key> gives it
  what it sends depends on the API's own replies, so the seed alone does not repeat this run; --store keeps what it sent

F100  HTTP Status 500
      getInventory - GET https://petstore3.swagger.io/api/v3/store/inventory  ->  500
      the API answered 500, so it fell over while handling this request
      curl -i -X GET 'https://petstore3.swagger.io/api/v3/store/inventory' -H 'Accept: application/json' -H 'User-Agent: RESTest/2.0'

... more faults are being found; every one of them is counted in the run's report and in the total below

567 requests to 19 operations in 10.4s, 13% of it idle
  opening lap: 19 requests in 2.3s, 9 of 19 operations answered 2xx
  171 2xx, 245 4xx, 151 5xx
  112 of them were pushing at the API with values nobody sensible would send, which accounts for some of the 245 refusals above
  34 of them changed one thing in a request the API had accepted, 34 of them breaking what the description states
  11 operation(s) answered 500, 11 answered some 5xx
184 faults:
  151 x F100  HTTP Status 500
  33 x F200  Schema Violation: Received A Response From API With A Structure/Data That Is Not Matching Its Schema
report written to restest-out/report.json (126.1 KiB)
the run itself was not kept; pass --store to keep every request and reply
```

The line under the first one says how the run began. Before anything is chosen by chance, every
operation it can test is sent once, with the request it is most likely to accept — only what the API
requires, a body wherever the document describes one, and for each value the best any source has,
the document's closed list of accepted values where it states one and then a value the API has
already handed back — in steps: the lists of what is there, then what
creates, then what reads one thing, then what changes, and deletions last, each step waiting for the
answers to the one before so that an identifier just handed back can be sent by the next. It is paid
for out of the budget like everything else. Measured against two containerised APIs restarted before
every run, five seeds, a minute each: kafka-rest-proxy had 28.6 of its operations answered 2XX two
seconds in with that round against 14.2 without it, pet-clinic 30.8 against 19.6 five seconds in,
and the area under that curve — which is what the competitions RESTest is measured in reward — rose
17% and 15%. `--set schedule.openingLap=false` switches it off.

The line after it — `171 2xx, 245 4xx, 151 5xx` — is worth a glance even when nothing is wrong. If
almost everything comes back refused, the requests were the problem rather than the API.

The last line before the faults counts operations rather than replies, and that is the number worth
quoting. A run spends its whole budget, so one broken operation asked six hundred times produces six
hundred broken replies; how much of the API is broken is the other number.

This run could try all nineteen operations. When a document has some RESTest cannot test — a file
upload, say, a parameter written in a style requests are not assembled in yet, or an operation it
found but could not read — the count says so (`17 of 19 operations can be tested`), and the summary
names them after its verdict, because "no faults found" says nothing about an operation that was
never tried. The screen names the first five, each with the reason; `report.json` names every one.

One file is left behind: `report.json`, for anything that reads a run rather than looks at it. It
counts every fault exactly, lists every operation and kind of fault that went wrong, says how the
API answered across every attempt, names every operation it found and could not try, and why,
and writes the first few faults of each kind out whole — the request, the reply and a `curl`
command that does it again. Faults are counted twice over: by their catalogue number, which is what
makes a run comparable with another tool's, and by the class of status code that carried them,
which is what a developer looks for first.

Add `--store` and a second file, `run.sqlite`, keeps every request and reply, so the run can be
examined again later without asking the API anything. It is off by default because a minute against
a fast API keeps hundreds of megabytes, and nothing in an ordinary run reads them back. A directory
holds one run: starting another in the same place replaces what is there, so a run worth keeping is
given a directory of its own with `--out`.

A quarter of the requests in a run are not meant to work. They are built from values nobody sensible
would send — an empty word, a number one past the end of a 32-bit integer, text where a number
belongs — because an API that falls over on one of those is a fault whatever was sent, and ordinary
requests never ask. The summary says how many requests were of that kind, so their refusals do not
read as the API turning away ordinary traffic. The quarter is a line of the plan RESTest carries,
which `restest run --print-campaign` writes out: a copy handed back with `--campaign` can give it
another share, or leave that strategy out.

Another fifth take a request the API has already accepted and send it again with exactly one thing
changed: something it requires left out, a number one past the largest it allows, a word where it
wants a number, a value off the closed list it states, a number too large for the kind of number the
document names — or the body broken as a whole: no bytes at all, JSON cut off halfway, a list where
an object belongs, the right body under the wrong media type. An API checks what it is sent
before acting on it, and a request with many things wrong is turned away by the first check; one
with a single thing wrong gets past every check but that one, which is where the failures a correct
request never reaches tend to be. The summary says how many requests were changed this way. Every
kind of change can be switched off, and [docs/settings.md](docs/settings.md#mutation) lists them.
The other way round, the values *you* know are good — real identifiers, the
surnames the API actually holds — go in a YAML file next to the specification and are handed over
with `--dictionary`, which takes a file or a directory and may be repeated;
[docs/dictionary-format.md](docs/dictionary-format.md) is the format.

A run also learns from the API as it goes. When a request comes back with a reply the API was happy
with, what that reply contained is kept, and the next request that needs a value of the same name
sends one the API itself produced — an identifier that exists rather than an invented number that
reaches a 404, and, where the document names the shape of what an operation wants sent, a whole
thing the API returned with one value in it changed (or, when nothing in it can be varied, sent
as it came back and recorded as that). Measured against two containerised APIs,
restarting each before every run: 27.8 of pet-clinic's operations answered 2XX without this and
31.6 with it, better on every one of five seeds.

It costs one promise, and so do two other things a run does. Changing accepted requests costs it,
since which requests were accepted is the API's answer too. So does the tenth of the run that turns
a creation into the first request of a short series about the thing created — delete it and read it
again, create it twice — each step built from the answer to the one before. What such a run sends
depends on what the API answered, so `--seed` on its own no longer repeats it — it makes a similar
run rather than the same one. `--store` keeps every request and reply of the run you actually had,
which is the record to go back to; sending those requests again is a later milestone's job. Taking
`source: observed` out of the plan below, where three of its strategies name it, and switching both
the changes and the series off puts the old promise back exactly:
[docs/switches.md](docs/switches.md#getting-the-seed-back) has the plan and the file of settings
that do it.

Which of those a run prefers, and in what proportion, is itself a file. `restest run
--print-campaign` writes out the plan RESTest follows when it is given none: which sources fill in
a value and in what order, how much of the run pushes at the API, and which operations it may touch
at all. Save it, change a line, hand it back with `--campaign`. One line worth knowing about is the
filter, since a run writes to whatever it is pointed at — `methods: [GET, HEAD, OPTIONS, TRACE]`
keeps it to the requests HTTP calls *safe*. [docs/campaign-format.md](docs/campaign-format.md) is
the format.

That plan is about the API. How the tool itself behaves — how many requests it keeps in flight, how
long an invented word is, how much of a reply it keeps, how long it waits — is a separate thing,
because those numbers would mean the same against a different API on the same machine. `restest run
--print-settings` writes out all seventy-nine of them, each with a line saying what it does and a note
saying where its value came from, and that output is a file you hand back with `--settings`. One of
them without a file: `--set engine.maxConcurrency=1`, which is the answer to an API that falls over
when asked two things at once. The same names work as environment variables,
`RESTEST_ENGINE_MAX_CONCURRENCY=1`, which is how a container is configured. Every run writes the lot
into `report.json`, so a directory of results carries the configuration that produced it.
[docs/settings.md](docs/settings.md) is the list, and it shows the file `--print-settings` writes so
you can see one without building anything. Thirty-two of the settings are switches, `true` or
`false`, each turning off one thing the tool does, so that what it is worth can be measured by
running the same tool with it and without it. [docs/switches.md](docs/switches.md) lists them with
what each was found to be worth, and gives the files that turn off a whole idea at once.

An API that asks for a key answers every request without it with a 401, and a run of refusals
finds nothing behind them. The pet shop's document asks for one on two operations, and the run above
says so under its count of operations. `--auth` hands the key over, and RESTest sends it where the
document says — in a header, the query or a cookie — with the operations that ask for it:

```bash
./restest run https://petstore3.swagger.io/api/v3/openapi.json \
    --url https://petstore3.swagger.io/api/v3 --budget 10s --auth special-key
```

`special-key` on its own is enough when the document declares one key, as the pet shop does. With
several, name the one it is for: `--auth api_key=special-key`. For a document that declares none,
say where the key goes, and it goes with every request: `--auth header:X-API-Key=…`, or `query:` or
`cookie:` in place of `header:`. That is also how a bearer token or a session cookie you already hold
goes: `--auth 'header:Authorization=Bearer …'`, `--auth cookie:JSESSIONID=…`. Repeat `--auth` for
several. `RESTEST_AUTH` holds the same as one `--auth`, so that a key need not be typed where a shell
remembers it. It goes to whatever API a run tests while it is set, so set it for the run it is meant
for — `RESTEST_AUTH=… ./restest run …` — rather than once for every run. Before its first request a run says where each key goes:

```
  the key given with --auth goes with 3 of them, in the header api_key; what the run writes says REDACTED-AUTH in its place
```

The key is written into nothing the run leaves behind. The screen, `report.json` with its `curl`
commands, and the stored run all show `REDACTED-AUTH` where it went, or `REDACTED-AUTH.api_key`
for a named scheme. So a `curl` command copied from a report is run by putting the key back there.
An API that repeats the key back in its replies has it hidden there too, and the run says at the
end how many replies did.
[ADR-0029](docs/adr/0029-the-key-an-api-asks-for.md) has the rules: which operations get a key, what
fills an input the document declares under the key's name, and what is refused.

Nothing else is required. `--url` is only needed when the document does not name an address you
can reach; it says which machine, so an API the document describes as living under a directory is
still tested there unless you give a path of your own. `--budget` defaults to a minute. The whole
of that budget is used, reading the document included — the percentage the run reports is how much
of the time RESTest had nothing in flight, which is the number this project measures itself on.

The command answers `0` when it found nothing wrong, `1` when it found a fault, and something else
when it could not do its job. `restest help run` lists every option and what each number means, and
[docs/command-line.md](docs/command-line.md) has the same on one page, with the variables the tool
reads and what stays the same across every 2.x version; `restest version` says which RESTest and
which Java you have. The `./restest` script runs the build in this checkout — proper packaging comes
later.
