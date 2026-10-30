<img src="RESTLogo_Black.png" alt="RESTest" width="200">

[![CI](https://github.com/isa-group/RESTest/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/isa-group/RESTest/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

> **RESTest 2 is a complete rewrite.** From version 2.0 on, RESTest shares no code with RESTest 1.x
> and is not compatible with it: the command line, the files it reads and the reports it writes are
> all new, and nothing written for 1.x works with it. The last version of RESTest 1.x is
> [1.6.0](https://github.com/isa-group/RESTest/releases/tag/restest-1.6.0), and its code is on the
> [`v1.x`](https://github.com/isa-group/RESTest/tree/v1.x) branch.

# RESTest

A black-box testing tool for REST APIs. Give it an OpenAPI document and the address of the API it
describes; RESTest invents requests from the document, sends them for as long as you allow, checks
every reply against what the document promised, and reports what it found wrong — each fault with a
`curl` command that sends the same request again.

Nothing has to be configured first. RESTest reads OpenAPI 2.0, 3.0 and 3.1, and it never looks at
the API's code: the document and the replies are all it needs, so the API can be written in any
language and run anywhere.

## Installation and use

### What you need

On macOS and Linux, Homebrew, which installs the Java RESTest runs on; or Docker; or Java 21 or
later, of which a plain Java runtime is enough. Building RESTest from its source needs a JDK 21 or
later, and git.

### Install

Each way ends with `restest` as a command on your `PATH`, which the rest of this page, the manual,
your scripts and a coding agent can all call.

**With Homebrew**, on macOS and Linux, which installs a Java 21 for RESTest as well:

```bash
brew install isa-group/tap/restest
```

It installs the latest release, and `brew upgrade restest` the next one; for a version of your
choosing, use the image or the archive below. The `restest` it installs runs on that Java 21,
whatever `JAVA_HOME` says. With that Java, the first installation keeps about 625 MB on macOS and
823 MB on Ubuntu 24.04.

**With Docker** there is nothing to install: the image holds RESTest and the Java it runs on.

```bash
docker run --rm ghcr.io/isa-group/restest:2.4.0 version
```

A run reads its document and writes its report in the folder it is started from, which the
container sees as `/work`. These lines write a `restest` command that runs the image with the
folder you are in lent to it, makes what is written there yours, hands over a key left in
`RESTEST_AUTH`, and lets the container reach your own machine as `host.docker.internal`:

```bash
mkdir -p ~/.local/bin
printf '%s\n' '#!/bin/sh' 'exec docker run --rm -v "$PWD:/work" --user "$(id -u):$(id -g)" -e RESTEST_AUTH --add-host=host.docker.internal:host-gateway ghcr.io/isa-group/restest:2.4.0 "$@"' > ~/.local/bin/restest
chmod +x ~/.local/bin/restest
export PATH="$HOME/.local/bin:$PATH"
```

Inside the container, `localhost` is the container itself: in an address you give `restest`, write
`host.docker.internal` for your own machine. On Linux, an API that listens only on `127.0.0.1`
cannot be reached that way; put `--network host` in the script in place of `--add-host=…`, and keep
`localhost`.

**With Java 21 or later**, from the archive every release carries, which holds RESTest and every
library it runs with:

```bash
curl -fLO https://github.com/isa-group/RESTest/releases/download/v2.4.0/restest-2.4.0.tgz
tar -xzf restest-2.4.0.tgz
export PATH="$PWD/restest-2.4.0/bin:$PATH"
```

On Windows, unpack `restest-2.4.0.zip` and run `restest-2.4.0\bin\restest.cmd`.

**From the source**, with a JDK 21 or later and git:

```bash
git clone --branch v2.4.0 https://github.com/isa-group/RESTest.git
cd RESTest
./mvnw -q install -DskipTests
export PATH="$PWD:$PATH"
```

The first build downloads what RESTest is built from and takes a few minutes; the ones after it, less
than one. On Windows, run it from Git Bash.

An `export PATH=…` lasts as long as the terminal. To keep it, add the line to your shell's start-up
file, such as `~/.bashrc` or `~/.zshrc`, with the folder written out in full.

However it was installed, `restest version` says which RESTest and which Java you have:

```
RESTest 2.4.0
Java 21.0.12.1 (Eclipse Adoptium), Linux 7.0.14-linuxkit aarch64
```

### Your first run

One command, a document and an address. Start a pet shop on your own machine — the demonstration API
the OpenAPI project publishes as a Docker image — and test it for ten seconds:

```bash
docker run -d --name petstore -p 8080:8080 swaggerapi/petstore3:1.0.28
restest run http://localhost:8080/api/v3/openapi.json \
    --url http://localhost:8080/api/v3 --budget 10s
```

The shop takes a few seconds to start: once `curl -s http://localhost:8080/api/v3/pet/1` prints a
pet, it is ready. Its image is built for Intel and AMD processors, so on a Mac with Apple silicon
Docker runs it in emulation and warns that the platforms do not match. Its document gives its address
as a path alone, `/api/v3`, which is why `--url` says where it is running. With RESTest's own Docker
image, write `host.docker.internal` in place of `localhost` in the second command, as
[Install](#install) says.

RESTest tests an operation that creates something by creating something, so a run leaves pets,
orders and users behind. `docker rm -f petstore` and the same `docker run` give you a shop as good
as new.

What it prints begins like this, saying what it will test and then printing each fault as it finds
it. The numbers are one run's, and yours will be different:

```
RESTest testing Swagger Petstore - OpenAPI 3.0 at http://localhost:8080/api/v3

19 of 19 operations can be tested, seed -8087786691817110837, budget 10s
  2 of them ask for an API key that was not given (api_key, in the header api_key): --auth <key> gives it
  what it sends depends on the API's own replies, so the seed alone does not repeat this run; --store keeps what it sent

warning  Reply off schema  (F200)
         loginUser - GET http://localhost:8080/api/v3/user/login  ->  200
         the body is not JSON at all, answering 200 as application/json
           Stored JSON could not be read back: Unrecognized token 'Logged': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
         curl -i -X GET 'http://localhost:8080/api/v3/user/login' -H 'Accept: application/json, application/xml;q=0.5' -H 'User-Agent: RESTest/2.0'
```

Later in the same run, a request RESTest broke on purpose, with `null` where the document says a
value may not be null, was accepted, which is an error:

```
error    Invalid data accepted  (F206)
         placeOrder - POST http://localhost:8080/api/v3/store/order  ->  200
         the API accepted a request it should have refused: sent null for body.complete, which may not be null
         curl -i -X POST 'http://localhost:8080/api/v3/store/order' -H 'Accept: application/json' -H 'User-Agent: RESTest/2.0' -H 'Content-Type: application/json' --data-binary '{"id":10,"petId":198772,"quantity":0,"status":"delivered","complete":null}'
```

After fifty faults the screen stops printing them, and the run ends with a summary:

```
... more faults are being found; every one of them is counted in the run's report and in the total below

5384 requests to 19 operations in 10.0s, 4% of it idle
  opening lap: 19 requests in 710ms, 13 of 19 operations answered 2xx
  2106 2xx, 1743 4xx, 1535 5xx
  431 of them were pushing at the API with values nobody sensible would send, which accounts for some of the 1743 refusals above
  722 of them changed one thing in a request the API had accepted
  214 of them were steps of 75 series about a thing the run created: deleteTwice 41, createTwice 38, readAfterDelete 62, safeGet 49, writeUnderDeleted 24; 153 creation(s) meant to begin one were not accepted
  16 operation(s) answered 500, 16 answered some 5xx
1625 errors, on 16 operations:
  1535 x Internal server error  (F100)
        look in the API's log for what failed while it handled the request; the document is not what to fix
  90 x Invalid data accepted  (F206)
        make the API refuse what its document forbids; where the document forbids more than the API should, loosen the document instead
376 warnings, on 8 operations, which do not fail the run (checks.failOnWarnings):
  376 x Reply off schema  (F200)
        the API and its document disagree about the reply's body: compare the places listed with the document, and fix whichever is wrong
some replies could not be checked; report.json names their operations:
  the shape of 3844 replies, on 18 operations: the document gives no shape
  the content type of 2267 replies, on 17 operations: the document declares the status code with no content type
report written to restest-out/report.json (313.1 KiB)
page written to restest-out/report.html (393.9 KiB)
the run itself was not kept; pass --store to keep every request and reply
```

### Reading what it says

- **The top** says what will be tested: how many of the document's operations RESTest can send
  requests to, the seed and the budget, and anything worth knowing before the first request — here,
  that two operations want a key nobody gave.
- **Each fault** is printed as it is found: whether it is an *error*, the API failing at its job, or
  a *warning*, the API answering something its document does not describe; what kind of fault it
  is, in RESTest's words and by its number in a catalogue other testing tools share (`F100` is a
  reply of 500); the request and what the API answered; what is wrong; and a `curl` command that
  does it again.
- **The summary** says how much was sent and how it was answered. `2106 2xx, 1743 4xx, 1535 5xx` is
  worth a glance even when nothing is wrong: if almost everything was refused, the requests were the
  problem rather than the API. `16 operation(s) answered 500` counts operations rather than replies,
  and is the number worth quoting, since one broken operation asked six hundred times is six hundred
  broken replies. Then come the errors and the warnings, each kind with a line on what to look at,
  and what the run could not check, and why.
- **`report.json`**, in `restest-out/`, has all of it for a program to read: every error and warning
  counted, every operation the document describes and what happened to it, the first few faults of
  each kind written out whole, every operation that could not be tested and why, and every setting
  the run used.
- **`report.html`**, beside it, is the same run as a page to open in a browser, with no network: the
  errors and warnings on every operation, the request behind each kind of fault with a button that
  copies its `curl` command, what could not be tested, and the number the run ended with. The page
  of the run above, in light and dark colours:

| Light | Dark |
|---|---|
| ![The top of report.html for the run above, in light colours: four cards counting the replies by class of status code, the errors and the warnings by kind, and the operations by what was found on them, then the timeline of the run](docs/images/petstore-report-light.png) | ![The same page in dark colours](docs/images/petstore-report-dark.png) |

[What a run leaves behind](docs/report.md) goes through every line and every key, and [The faults
RESTest reports](docs/faults.md) says what each kind of fault means.

## What a run does

1. **It reads the document**, and sets aside what it cannot test — a file upload the API insists on,
   say — naming each
   operation and the reason, so that `no faults found` is never read as covering an operation that
   was never tried.
2. **It sends every operation once**, with the request it is most likely to accept, before anything
   is left to chance: lists first, then creations, reads of one thing, changes and deletions, each
   step waiting for the answers to the one before so that an identifier just handed back can be
   used by the next. [The first round of a run](docs/campaign-format.md#the-first-round-of-a-run).
3. **Then, until the budget runs out, it draws requests**, by the plan RESTest carries:
   - nearly half of the draws build a request meant to be accepted, from the values the document
     states, from what the API has already returned — identifiers that exist rather than invented
     ones — from values it makes up to suit what the document says, and from lists of your own;
   - nearly a third take a request the API accepted and send it again with exactly one thing broken,
     which gets past every check the API makes but one; until the API has accepted something that
     can be changed, such a draw builds a request meant to be accepted instead, so fewer requests
     than that are sent this way
     ([changing one thing](docs/campaign-format.md#changing-one-thing-in-a-request-that-worked));
   - about one in seven may turn a creation into the first step of a short series about the thing
     created — delete it and read it again, create it twice — and builds an ordinary request when
     what it drew is not a creation
     ([series](docs/campaign-format.md#series-of-requests-around-a-thing-the-run-created));
   - a tenth push at the API with values nobody sensible would send — an empty word, a number one
     past the largest 32-bit integer, text where a number belongs — since an API that falls over on
     one of those has a fault whatever was sent
     ([how much of a run pushes](docs/campaign-format.md#how-much-of-a-run-pushes-at-the-api)).
4. **It judges every reply**: a 500 is a fault, and so are another server error such as 503, a reply
   whose body is not the shape the document promised, a 501 to an operation the document declares, a
   success in answer to a request it broke on purpose, which the document says the API must refuse,
   a request the API took and never answered before RESTest stopped waiting, and a reply whose
   status code or content type the document does not declare.

The whole budget is used, reading the document included. The share of it in which RESTest had
nothing in flight is reported as *idle*, and kept as close to nothing as possible.

Because a run learns from the API's replies, running the same command twice makes two similar runs
rather than the same one, even with the same `--seed`. `--store` keeps every request and reply of the
run you had, in `restest-out/run.sqlite`, and [Getting the seed
back](docs/switches.md#getting-the-seed-back) has the files that make a run repeatable from its seed.

## Making a run yours

Every one of these is optional.

**Values you know are good** — identifiers that exist, the names the API actually holds — go in a
YAML file, each under its operation and the place in its request, and are handed over with
`--dictionary`, which takes a file or a directory and may be repeated:

```yaml
version: 1
name: pet-ids
keyedBy: operationAndParameter
values:
  getPetById:
    petId: [1, 2, 3]
```

```bash
restest run http://localhost:8080/api/v3/openapi.json \
    --url http://localhost:8080/api/v3 --budget 10s --dictionary pet-ids.yaml
```

[The dictionary format](docs/dictionary-format.md). A dictionary and a plan each have a [JSON
Schema](docs/schemas.md), for an editor, or a language model, to get the shape of one right first.

**The plan** — where values come from, how much of a run pushes at the API, which operations it may
touch — is a file. `--print-campaign` writes out the one RESTest follows; save it, change a line,
and hand it back with `--campaign`.

```bash
restest run --print-campaign > plan.yaml
restest run openapi.yaml --url http://localhost:8080 --campaign plan.yaml
```

A block worth knowing, since a run writes to whatever it is pointed at: added to the plan, this keeps
it to the requests HTTP calls *safe*, the ones that change nothing.

```yaml
operations:
  methods: [GET, HEAD, OPTIONS, TRACE]
```

[The campaign file](docs/campaign-format.md).

**How the tool behaves** — how many requests it keeps in flight, how long it waits, how much of a
reply it keeps — is a setting. `--set` changes one, `--settings` reads a file of them, and each is
also an environment variable, which is how a container is configured: with Docker, name it in the
`restest` script with an `-e` of its own, beside `-e RESTEST_AUTH`. `--print-settings` writes every
one out, with what it does and where its value came from:

```bash
restest run openapi.yaml --url http://localhost:8080 --set engine.maxConcurrency=1
RESTEST_ENGINE_MAX_CONCURRENCY=1 restest run openapi.yaml --url http://localhost:8080
```

That one is the answer to an API that falls over when it is asked two things at once;
`--set engine.mostRequestsPerMinute=300` keeps a run to five requests a second, evenly spaced, for a
server whose owner wants a rate. [The settings](docs/settings.md), and [the
switches](docs/switches.md) — the settings that each turn off one thing a run does.

**An API that asks for a key** refuses every request without it. `--auth` hands it over, and RESTest
sends it where the document says, with the operations that ask for it:

```bash
restest run http://localhost:8080/api/v3/openapi.json \
    --url http://localhost:8080/api/v3 --budget 10s --auth special-key
```

A bearer token or a session cookie you already hold goes the same way:
`--auth 'header:Authorization=Bearer …'`, `--auth cookie:JSESSIONID=…`. The key is written into
nothing the run leaves behind: the screen, `report.json`, `report.html` and their `curl` commands
show `REDACTED-AUTH` where it went — or, for a key given with its name or its place, a longer word
such as `REDACTED-AUTH.api_key`, which the run names before its first request. A `curl` command copied from a
report is run by putting the key back in place of that word. [Handing over a key or a token](docs/command-line.md#handing-over-a-key-or-a-token).

## Exit codes

A run ends with `0` when it found no error, `1` when it found at least one, and another number when
it could not do its job — a wrong command line, nothing it could test, RESTest itself breaking, or a
run stopped from outside, with Ctrl-C, `kill` or `docker stop`. A build server can go red on anything but `0`. An error is the API failing
at its job; a warning, the API answering something its document does not describe, is counted and
said and fails a run only with `--set checks.failOnWarnings=true`.
[The exit codes](docs/command-line.md#exit-codes) says what each number means.

`restest help run` lists every option, and [the command line](docs/command-line.md) has the same on
one page; `restest version` says which RESTest and which Java you have.

## Documentation

[The manual](docs/manual/README.md) is the place to start: it is read from beginning to end, and
takes you from a first run against a practice API on your own machine to RESTest in your continuous
integration.

[`docs/`](docs/README.md) has a page for each subject: the command line, what a run leaves behind,
the faults, the plan, dictionaries, the JSON Schemas of a dictionary and a plan, the settings, the
switches and the known limitations — and, for working on RESTest itself, the design, continuous
integration and the decision records.

## Contributing

[`CONTRIBUTING.md`](CONTRIBUTING.md) says how a change is proposed, built and reviewed.

## License

Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE). The libraries a release
carries are listed, with their licences, in `THIRD-PARTY.txt` beside this file in the archive and
the image, and in [`dist/THIRD-PARTY.txt`](dist/THIRD-PARTY.txt) in the source.
