# Contributing to RESTest

From version 2.0 on, RESTest is a complete rewrite of RESTest 1.x, built in small, reviewable
changes. Work targets `master`. RESTest 1.x is on the `v1.x` branch and its tags; the record of how
the rewrite was first built — every increment, measurement and decision — is the tag
`history/2.0-development`.

## Before you write anything

Read, in this order:

1. `README.md` — what the tool does, and how to install and run it.
2. `docs/README.md` — the index of the documentation: a page per subject, from the command line to
   every key of `report.json`.
3. `AGENTS.md` — the rules of the project. Short, and binding on people as much as on coding agents.
4. `docs/adr/` — why the system is shaped the way it is, one decision per file.
5. `docs/DESIGN.md` — the architecture, the extension points and the quality gates, with a
   glossary of the terms used throughout the repository.

## What RESTest is

A command-line tool that tests a REST API. It builds requests from the API's OpenAPI document and
from what the API answers, sends them for a fixed time, changes accepted requests one thing at a time,
sends short series around the things it creates, and reports every reply of 500 and every reply that
is not the shape the document promised.

The OpenAPI document is all it needs, and that stays its first requirement: a run with nothing else
must work. A run can also be shaped in three ways, each a plain file handed over on the command line:

- **Settings** (`--settings`, or `--set` for one) — the numbers that say how the tool itself
  behaves, such as how many requests may be in flight or how hard a run pushes
  ([the settings](docs/settings.md)).
- **The campaign file** (`--campaign`) — the plan a run follows: where its values come from, how much
  of the time goes to each kind of request, and which operations it may touch
  ([the campaign file](docs/campaign-format.md)).
- **Dictionaries** (`--dictionary`) — lists of values that are known to suit the API, which a person
  or a coding agent can write before the run ([the dictionary format](docs/dictionary-format.md)).

Changes that keep these three easy to write, by people and by agents, are as welcome as changes to
what a run does. What it does is described in `README.md` and `docs/`, and how it is built in
`docs/DESIGN.md`.

The repository does not keep a plan of what comes next. Before starting something large, open an
issue to talk about it. What is out of scope — the list under "Out of scope" in `docs/DESIGN.md` —
is not started without explicit approval, even where it looks easy.

## The loop

One change = one branch = one pull request into `master`.

```bash
git switch master && git pull
git switch -c feat/container-image
# ... work ...
./mvnw verify
gh pr create --base master
```

Branch names say what kind of change it is and what it does: `feat/…`, `fix/…`, `docs/…`,
`chore/…`. Pull requests are squash-merged.

## Pull requests

Use the template. It asks for four things that are not optional:

- **What you can do now that you could not before** — concrete and user-facing, not a list of classes.
- **Try it yourself** — copy-paste commands from a clean checkout, with the real output. They must
  work on a laptop with only Java and git installed. Run them before you paste them.
- **How it works** — three paragraphs at most, written for someone who does not program in Java.
- **Evidence** — real numbers: tests, overhead, the smoke run, idle time.

Titles say what became possible, not what was edited.

## Review

Continuous integration must be green ([`docs/ci.md`](docs/ci.md) says what it checks). A maintainer
then reads the pull request against the rules in `AGENTS.md`, the decision records it touches and the
template's sections, runs the "Try it yourself" commands, and asks for changes or merges. A finding
you disagree with is answered in "Decisions taken", not ignored.

## Hard rules

They are in [`AGENTS.md`](AGENTS.md#hard-rules), and none is negotiable in a pull request. The build
enforces some of them: the module boundaries, the evaluation platform's name, the Java 21 bytecode,
and the coverage floors of `restest-core` and `restest-oracles`. The mutation threshold is run on
demand, not in the build, and review checks everything else ([`docs/ci.md`](docs/ci.md) lists each
gate). If a rule is wrong, change it deliberately, in its own pull request, with the reasoning.

## Decisions

If a choice has more than one defensible answer and would be expensive to reverse — a dependency
that will spread, a module boundary, a file format — write an ADR (`docs/adr/README.md`) as part of
the same change. Ordinary implementation choices belong in the pull request's "Decisions taken"
section, not in an ADR.

## Running things

The commands are in [`AGENTS.md`](AGENTS.md#commands), and
[`docs/ci.md`](docs/ci.md) says how to reproduce each check continuous integration runs.

Evaluation campaigns are run from a repository of their own, which has its own instructions;
[ADR-0011](docs/adr/0011-evaluation-harness.md) says why it is not this one. Nothing here builds
against it, and the tool never needs it. The maintainers' development tooling is kept outside the
repository too, for the reasons in [ADR-0030](docs/adr/0030-the-process-lives-outside-the-repository.md);
you need none of it to contribute.
