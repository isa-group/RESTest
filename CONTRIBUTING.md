# Contributing to RESTest 2.0

RESTest 2.0 is a rewrite. It is being built in small, reviewable increments on the `v2` branch;
`master` still holds RESTest 1.x and is not touched until the rewrite is complete.

## Before you write anything

Read, in this order:

1. `README.md` — what the tool does.
2. `CLAUDE.md` — the rules of the project. Short, and binding on humans as much as on assistants.
3. `ROADMAP.md` — what is being built, in what order, and where the review points are.
4. `docs/adr/` — why the system is shaped the way it is. Eleven short files.
5. `docs/DESIGN.md` — the architecture, the extension points and the quality gates, with a
   glossary of the terms used throughout the repository.

## The loop

One increment from `ROADMAP.md` = one branch = one pull request into `v2`.

```bash
git switch v2 && git pull
git switch -c feat/m2-4-format-aware-values
# ... work ...
./mvnw verify
gh pr create --base v2
```

Branch names are `feat/m<milestone>-<n>-<slug>`. Pull requests are squash-merged.

## Pull requests

Use the template. It asks for four things that are not optional:

- **What you can do now that you could not before** — concrete and user-facing, not a list of classes.
- **Try it yourself** — copy-paste commands from a clean checkout, with the real output. Run them
  before you paste them.
- **How it works** — three paragraphs at most, written for someone who does not program in Java.
- **Evidence** — real numbers: tests, overhead, the smoke run, idle time.

Titles say what became possible, not what was edited.

## Hard rules

These fail the build, and they are not negotiable in a pull request:

- English everywhere: code, comments, tests, commits, branches, issues, documentation.
- No AI-specific abstraction anywhere (ADR-0008).
- No reference to any evaluation platform under `src/` (ADR-0011).
- Nothing from "Out of scope for v2.0" in `docs/DESIGN.md` without explicit approval.
- No architecture test, coverage threshold or mutation threshold is ever weakened to make a build
  pass. If a rule is wrong, change it deliberately, in its own pull request, with the reasoning.
- No source copied from RESTest 1.x (ADR-0002). Ideas and the IDL grammar, yes; files, no.

## Decisions

If a choice has more than one defensible answer and would be expensive to reverse — a dependency
that will spread, a module boundary, a file format — write an ADR (`docs/adr/README.md`) as part of
the same increment. Ordinary implementation choices belong in the pull request's
"Decisions taken" section, not in an ADR.

## Running things

```bash
./mvnw verify                       # build and test everything
./mvnw -q verify -pl restest-core   # one module
./mvnw verify -Pit                  # integration tests (needs Docker)
./mvnw verify -Psmoke               # smoke run against two containerised APIs
./mvnw org.pitest:pitest-maven:mutationCoverage -pl restest-oracles
./evaluation/run-evaluation.sh --apis market,scs --budget 10 --repetitions 1
```

The last one is the milestone campaign harness. It is optional, it is not part of the build, and the
tool never needs it (ADR-0011).
