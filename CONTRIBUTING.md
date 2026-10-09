# Contributing to RESTest

RESTest 2 is a rewrite of RESTest 1.x, built in small, reviewable changes. Work targets `master`.
RESTest 1.x is on the `v1.x` branch and its tags; the record of how 2.0 was built — every increment,
measurement and decision — is the tag `history/2.0-development`.

## Before you write anything

Read, in this order:

1. `README.md` — what the tool does, and how to install and run it.
2. `docs/README.md` — the index of the documentation: a page per subject, from the command line to
   every key of `report.json`.
3. `AGENTS.md` — the rules of the project. Short, and binding on people as much as on coding agents.
4. `docs/adr/` — why the system is shaped the way it is, one decision per file.
5. `docs/DESIGN.md` — the architecture, the extension points and the quality gates, with a
   glossary of the terms used throughout the repository.

## What 2.0 is

2.0 is a command-line tool that tests a REST API from its OpenAPI document alone: it builds requests
from the document and from what the API answers, sends them for a fixed time, changes accepted
requests one thing at a time, sends short series around the things it creates, and reports every
reply of 500 and every reply that is not the shape the document promised. What it does is described
in `README.md` and `docs/`, and how it is built in `docs/DESIGN.md`.

The repository does not keep a plan of what comes next. Before starting something large, open an
issue to talk about it. What is out of scope — the list under "Out of scope for v2.0" in
`docs/DESIGN.md` — is not started without explicit approval, even where it looks easy.

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
- **Try it yourself** — copy-paste commands from a clean checkout, with the real output. Run them
  before you paste them.
- **How it works** — three paragraphs at most, written for someone who does not program in Java.
- **Evidence** — real numbers: tests, overhead, the smoke run, idle time.

Titles say what became possible, not what was edited.

## Hard rules

They are in [`AGENTS.md`](AGENTS.md#hard-rules). Most of them fail the build, and none is negotiable
in a pull request. If a rule is wrong, change it deliberately, in its own pull request, with the
reasoning.

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
