# ADR-0011: The evaluation harness lives in `evaluation/`, outside the Maven build

**Status:** Accepted
**Date:** 2026-09-11

## Context

RESTGym is the infrastructure behind the SBFT REST League: it runs testing tools in Docker against a
fixed set of instrumented APIs and computes comparable metrics. We want to use it for milestone
campaigns and for any paper, because comparability with published results is precisely what it
provides.

We do not want RESTest to depend on it in any way. A tool that has absorbed assumptions from the
benchmark it is measured on is both worse engineering and worse science. We also do not want to
contribute anything to RESTGym's repository for now.

Two earlier drafts got this wrong in opposite directions: one had the tool reading dictionaries
shipped by RESTGym and enforcing a threshold derived from its verification rules; the other removed
the harness from our repository entirely and assumed we would work in theirs.

## Decision

**Nothing under `src/` references RESTGym.** An ArchUnit test and a supervision-point check require
zero hits when searching the source tree for the platform's name or its conventions.

**The harness lives in a top-level `evaluation/` directory that is not a Maven module:**

```
evaluation/
  restgym/
    Dockerfile                 starts from a JRE image, copies the RESTest binary
    entrypoint.sh              ~15 lines: reads $API, $HOST, $PORT, $TIME_BUDGET,
                               builds the restest command, loops until stopped
    restgym-tool-config.yml    enabled: true
    restgym.lock               the RESTGym commit these files were validated against
    README.md                  how to run an evaluation, start to finish
  run-evaluation.sh            clones or updates RESTGym at the pinned commit into a
                               scratch directory, copies evaluation/restgym/ into its
                               tools/restest/, selects APIs, budget and repetitions,
                               launches, collects results back here
  results/                     committed scoreboards; raw output git-ignored
```

`run-evaluation.sh` operates on a **scratch clone**, so nothing is ever committed or pushed to
RESTGym's repository. The clone is pinned to the commit in `restgym.lock`, which is what makes a
campaign reproducible months later.

**All platform-specific knowledge is confined to `entrypoint.sh`.** What the tool exposes for it is
generic and would exist anyway: `--url` to point at a deployed instance, `--budget` to stop after a
given time, documented output formats, and a shell loop supplied by the caller.

**Day-to-day measurement does not use RESTGym.** The per-pull-request smoke run and the nightly run
are ordinary Testcontainers integration tests against public API images, because they must answer in
minutes. RESTGym is for milestone campaigns and papers.

## Consequences

- Deleting `evaluation/` leaves a fully working, fully buildable tool.
- The people who run evaluations find everything they need in the repository they already work in.
- Campaigns are reproducible because the platform version is pinned.
- Changing evaluation platform means rewriting one directory, not touching the tool.
- If we later want RESTGym's own campaigns to include RESTest without us running them, the same three
  files are contributed upstream unchanged. That door stays open.
- Two ways of measuring — local integration tests and RESTGym — which can disagree. That is
  informative rather than a problem, but it has to be interpreted rather than averaged.

## Alternatives considered

- **A `restest-bench` Maven module.** Rejected by the maintainers: a module inside the build is where
  benchmark-shaped assumptions would quietly accumulate.
- **Keeping the adapter in RESTGym's repository.** Rejected: we would have to work in someone else's
  repository for our own evaluations.
- **Using RESTGym for the per-pull-request smoke run.** Too slow for a feedback loop that must close
  in minutes.
