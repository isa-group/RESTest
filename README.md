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
  values from the API's own replies, so the seed alone does not repeat this run; --store keeps what it sent

F100  HTTP Status 500
      deleteOrder - DELETE https://petstore3.swagger.io/api/v3/store/order/207  ->  500
      the API answered 500, so it fell over while handling this request
      curl -i -X DELETE 'https://petstore3.swagger.io/api/v3/store/order/207' -H 'User-Agent: RESTest/2.0'

... more faults are being found; every one of them is counted in the run's report and in the total below

816 requests to 19 operations in 10.3s, 12% of it idle
  196 2xx, 332 4xx, 288 5xx
  4 operation(s) answered 500, 5 answered some 5xx
384 faults:
  288 x F100  HTTP Status 500
  96 x F200  Schema Violation: Received A Response From API With A Structure/Data That Is Not Matching Its Schema
report written to restest-out/report.json (217.9 KiB)
the run itself was not kept; pass --store to keep every request and reply
```

The line under the totals — `196 2xx, 332 4xx, 288 5xx` — is worth a glance even when nothing is
wrong. If almost everything comes back refused, the requests were the problem rather than the API.

The line after it counts operations rather than replies, and that is the number worth quoting. A run
spends its whole budget, so one broken operation asked six hundred times produces six hundred broken
replies; how much of the API is broken is the other number.

One file is left behind: `report.json`, for anything that reads a run rather than looks at it. It
counts every fault exactly, lists every operation and kind of fault that went wrong, says how the API
answered across every attempt, and writes the first few faults of each kind out whole — the request,
the reply and a `curl` command that does it again. Faults are counted twice over: by their catalogue
number, which is what makes a run comparable with another tool's, and by the class of status code
that carried them, which is what a developer looks for first.

Add `--store` and a second file, `run.sqlite`, keeps every request and reply, so the run can be
examined again later without asking the API anything. It is off by default because a minute against
a fast API keeps hundreds of megabytes, and nothing in an ordinary run reads them back. A directory
holds one run: starting another in the same place replaces what is there, so a run worth keeping is
given a directory of its own with `--out`.

A quarter of the requests in a run are not meant to work. They are built from values nobody sensible
would send — an empty word, a number one past the end of a 32-bit integer, text where a number
belongs — because an API that falls over on one of those is a fault whatever was sent, and ordinary
requests never ask. The summary says how many requests were of that kind, so their refusals do not
read as the API turning away ordinary traffic. `--fuzzing 40` changes the share and `--fuzzing 0`
sends none of them. The other way round, the values *you* know are good — real identifiers, the
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

It costs one promise, and this is the only place in the tool that costs it: what such a run sends
depends on what the API answered, so `--seed` on its own no longer repeats it — it makes a similar
run rather than the same one. `--store` keeps every request and reply of the run you actually had,
which is the record to go back to; sending those requests again is a later milestone's job. Taking
`source: observed` out of the plan below puts the old promise back exactly.

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
--print-settings` writes out all forty-two of them, each with a line saying what it does and a note
saying where its value came from, and that output is a file you hand back with `--settings`. One of
them without a file: `--set engine.maxConcurrency=1`, which is the answer to an API that falls over
when asked two things at once. The same names work as environment variables,
`RESTEST_ENGINE_MAX_CONCURRENCY=1`, which is how a container is configured. Every run writes the lot
into `report.json`, so a directory of results carries the configuration that produced it.
[docs/settings.md](docs/settings.md) is the list, and it shows the file `--print-settings` writes so
you can see one without building anything.

Nothing else is required. `--url` is only needed when the document does not name an address you
can reach; it says which machine, so an API the document describes as living under a directory is
still tested there unless you give a path of your own. `--budget` defaults to a minute. The whole
of that budget is used, reading the document included — the percentage the run reports is how much
of the time RESTest had nothing in flight, which is the number this project measures itself on.

The command answers `0` when it found nothing wrong, `1` when it found a fault, and something else
when it could not do its job; [ADR-0015](docs/adr/0015-command-line-contract.md) says exactly what
each number means. The `./restest` script runs the build in this checkout — proper packaging comes
later.
