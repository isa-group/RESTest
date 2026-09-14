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
    --url https://petstore3.swagger.io/api/v3 --budget 10s
```

That last one is somebody else's public demonstration server, so it is worth being a guest there:
keep the budget short, and point `--url` at your own copy for anything longer.

RESTest reads the document, invents requests from it, sends them for as long as you gave it, judges
every reply against what the document promised, and prints each disagreement with a `curl` command
that does it again:

```
RESTest testing Swagger Petstore - OpenAPI 3.0 at https://petstore3.swagger.io/api/v3

17 of 19 operations can be tested, seed 20260914, budget 10s

F101  Received A Response From API With A Structure/Data That Is Not Matching Its Schema
      findPetsByStatus - GET https://petstore3.swagger.io/api/v3/pet/findByStatus?status=sold
      the body does not match the shape the specification declares for it, answering 200 as application/json
        /223: required property 'name' not found
        /228/tags/0: string found, object expected
      curl -i -X GET 'https://petstore3.swagger.io/api/v3/pet/findByStatus?status=sold' -H 'User-Agent: RESTest/2.0'

... more faults are being found; they are all in the run's report, and counted below

895 requests to 17 operations in 10.3s, 14% of it idle
423 faults:
  106 x F101  Received A Response From API With A Structure/Data That Is Not Matching Its Schema
  317 x F100  HTTP Status 500
report written to restest-out/report.json
run stored in restest-out/run.sqlite
```

Two files are left behind: `report.json`, for anything that reads a run rather than looks at it, and
`run.sqlite`, holding every request and reply so the run can be examined again later without asking
the API anything.

Nothing else is required. `--url` is only needed when the document does not name an address you can
reach, and `--budget` defaults to a minute. The whole of that budget is used, reading the document
included — the percentage the run reports is how much of the time RESTest had nothing in flight,
which is the number this project measures itself on.

The command answers `0` when it found nothing wrong, `1` when it found a fault, and something else
when it could not do its job; [ADR-0015](docs/adr/0015-command-line-contract.md) says exactly what
each number means. The `./restest` script runs the build in this checkout — proper packaging comes
later.
