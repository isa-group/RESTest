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

That last one is somebody else's public demonstration server, so it is worth being a guest there:
keep the budget short, and point `--url` at your own copy for anything longer.

RESTest reads the document, invents requests from it, sends them for as long as you gave it, judges
every reply against what the document promised, and prints each disagreement with a `curl` command
that does it again:

```
RESTest testing Swagger Petstore - OpenAPI 3.0 at https://petstore3.swagger.io/api/v3

17 of 19 operations can be tested, seed 20260914, budget 10s

F100  HTTP Status 500
      getInventory - GET https://petstore3.swagger.io/api/v3/store/inventory
      the API answered 500, so it fell over while handling this request
      curl -i -X GET 'https://petstore3.swagger.io/api/v3/store/inventory' -H 'User-Agent: RESTest/2.0'

... more faults are being found; every one of them is counted in the run's report and in the total below

918 requests to 17 operations in 10.3s, 11% of it idle
432 faults:
  324 x F100  HTTP Status 500
  108 x F101  Received A Response From API With A Structure/Data That Is Not Matching Its Schema
report written to restest-out/report.json (788.5 KiB)
the run itself was not kept; pass --store to keep every request and reply
```

One file is left behind: `report.json`, for anything that reads a run rather than looks at it. It
counts every fault exactly, lists every operation and kind of fault that went wrong, and writes the
first few of each kind out whole — the request, the reply and a `curl` command that does it again.

Add `--store` and a second file, `run.sqlite`, keeps every request and reply, so the run can be
examined again later without asking the API anything. It is off by default because a minute against
a fast API keeps hundreds of megabytes, and nothing in an ordinary run reads them back. A directory
holds one run: starting another in the same place replaces what is there, so a run worth keeping is
given a directory of its own with `--out`.

Nothing else is required. `--url` is only needed when the document does not name an address you can
reach, and `--budget` defaults to a minute. The whole of that budget is used, reading the document
included — the percentage the run reports is how much of the time RESTest had nothing in flight,
which is the number this project measures itself on.

The command answers `0` when it found nothing wrong, `1` when it found a fault, and something else
when it could not do its job; [ADR-0015](docs/adr/0015-command-line-contract.md) says exactly what
each number means. The `./restest` script runs the build in this checkout — proper packaging comes
later.
