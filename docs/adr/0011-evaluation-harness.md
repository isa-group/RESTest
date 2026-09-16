# ADR-0011: The evaluation harness lives in `evaluation/`, outside the Maven build

**Status:** Accepted, amended at M1.9
**Date:** 2026-09-11 (amended 2026-09-16)

> The title, the Decision and the Consequences below are as originally accepted, with every
> superseded statement struck through and marked where it stands. The M1.9 amendment is the current
> state: **the harness is built in its own repository, `isa-group/restgym-restest2`, and this
> repository contains none of it**. The `evaluation/` directory this record reserved was never
> created — the placement was reconsidered at the increment that would have created it, before any
> of it was written. The separation itself, which is what this ADR is really about, has not changed:
> it has been made stronger, and the rule that enforces it now covers every file here rather than
> the source trees alone.

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

~~**Nothing under `src/` references RESTGym.** An ArchUnit test and a supervision-point check require
zero hits when searching the source tree for the platform's name or its conventions.~~
**Amended at M1.9: the search covers every file in the repository and the names of those files, and
requires zero hits outside five named documents.**

~~**The harness lives in a top-level `evaluation/` directory that is not a Maven module:**~~
**Amended at M1.9: the harness lives in a repository of its own, `isa-group/restgym-restest2`.
The layout below is kept as it was decided; the amendment at the end of this record gives the one
that replaced it, and why.**

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

~~**All platform-specific knowledge is confined to `entrypoint.sh`.**~~ **Amended at M1.9: it is
confined to the evaluation repository, which is the stronger claim. Two files there know the
platform's conventions rather than one - the entry script and the Dockerfile, whose build context is
the platform's own checkout.** What the tool exposes for it is generic and would exist anyway: `--url` to point at a deployed instance, `--budget` to stop after a
given time, documented output formats, and a shell loop supplied by the caller.

**Day-to-day measurement does not use RESTGym.** The per-pull-request smoke run and the nightly run
are ordinary Testcontainers integration tests against public API images, because they must answer in
minutes. RESTGym is for milestone campaigns and papers.

## Consequences

- ~~Deleting `evaluation/` leaves a fully working, fully buildable tool.~~ **Amended at M1.9: the
  directory was never created, so there is nothing to delete.**
- ~~The people who run evaluations find everything they need in the repository they already work
  in.~~ **Amended at M1.9: they find it in one repository, which is not this one.**
- Campaigns are reproducible because the platform version is pinned.
- ~~Changing evaluation platform means rewriting one directory, not touching the tool.~~ **Amended
  at M1.9: it means changing another repository, and touching nothing here.**
- ~~If we later want RESTGym's own campaigns to include RESTest without us running them, the same
  three files are contributed upstream unchanged.~~ **Amended at M1.9: the contribution is a
  submodule reference to the evaluation repository rather than a copy of anything, which is how
  RESTGym takes on every other tool.** That door stays open.
- Two ways of measuring — local integration tests and RESTGym — which can disagree. That is
  informative rather than a problem, but it has to be interpreted rather than averaged.

## Alternatives considered

- **A `restest-bench` Maven module.** Rejected by the maintainers: a module inside the build is where
  benchmark-shaped assumptions would quietly accumulate.
- **Keeping the adapter in RESTGym's repository.** Rejected: we would have to work in someone else's
  repository for our own evaluations.
- **Using RESTGym for the per-pull-request smoke run.** Too slow for a feedback loop that must close
  in minutes.

## Amendment (M1.9)

**Date:** 2026-09-16

**The harness is built in a repository of its own, `isa-group/restgym-restest2`, rather than in the
`evaluation/` directory this record reserved for it. The rule that keeps the benchmark out of the
tool now covers every file here, not only `src/`.**

Nothing moved, and nothing was deleted: `evaluation/` was never created. This record described where
the harness *would* go, M1.9 is the increment that would have put it there, and the maintainer asked
the question before a line of it was written. What follows is why the answer changed.

### Why

Four things were not known when the original decision was written. Two of them are facts about the
benchmark, found by reading its repository rather than its documentation.

**One repository per tool is the benchmark's own convention.** Every tool it measures is a git
submodule under `tools/`, pointing at a repository of that tool's own — and RESTest 1.x is already
one of them. Putting our adapter inside the tool's repository would have been the unusual
arrangement, and contributing it upstream later would have meant extracting it first. Putting it in
a repository of its own makes that contribution a submodule reference and nothing else.

**The adapter is not a thing that stands on its own.** Its Dockerfile is built with the benchmark's
checkout as the build context, so its own `COPY` lines begin `./tools/`; the API's document arrives
as a volume the platform mounts at a path it chooses; and the container must never exit, because the
platform stops it when the budget is up and treats one that ended by itself as a failed run. Every
one of those is a fact about somebody else's tree. A file that only means anything inside another
project's directory layout cannot sit beside our source and be described as independent of it.

**A campaign produces data, and this repository holds source.** The original layout committed a
scoreboard per campaign, and M8.2 adds a replication package on top of that. A replication package
wants its own tag and its own archived snapshot with a citable identifier. A subdirectory of a tool
that keeps being released can give it neither, and every campaign would make the tool's clone bigger
for readers who only ever wanted the tool.

**The separation would have been a promise rather than a fact.** "Not a Maven module" is true, and
invisible: what somebody opening the repository would have seen is a top-level directory named after
the benchmark. The strongest sentence this record could write was that nothing under `src/` mentions
the platform, because the harness was to live in the same checkout. With it built elsewhere, the same
sentence can be said about every file in the repository, and is.

What has *not* changed is everything else recorded above, and it is worth repeating, because a
decision revisited is exactly where the parts nobody revisited get quietly lost. The tool still exposes nothing for the
benchmark's benefit. The adapter still uses only the published command-line contract of
[ADR-0015](0015-command-line-contract.md) — `--url`, `--budget`, `--out`, the exit codes — all of
which would exist with or without a benchmark. Day-to-day measurement is still the per-pull-request
Testcontainers smoke run, because a campaign answers in hours and a feedback loop has to answer in
minutes.

### How

The other repository holds what `evaluation/` was going to hold, in the shape the benchmark expects
of a contributed tool:

```
isa-group/restgym-restest2
  tool/
    Dockerfile                 builds RESTest from source at a commit, onto a JRE image
    entrypoint.sh              the one file that knows the benchmark's conventions
    restest                    runs the built distribution
    restgym-tool-config.yml    enabled: true
  run-evaluation.sh            clones the benchmark at the pinned commit into a scratch
                               directory, stages tool/ into its tools/, selects APIs, budget
                               and repetitions, launches, collects results back
  results/                     committed scoreboards; raw output ignored
  restgym.lock                 the two commits a campaign was run with
  ci/                          a stub API document for the contract check
  README.md                    how to run a campaign, start to finish
```

The image builds RESTest from source at a commit rather than carrying a binary somebody built, so
it can say what it contains. No specification is shipped: the platform mounts each API's document
into the container itself.

**Two pins rather than one.** The lock file records the benchmark commit *and* the RESTest commit or
tag the image was built from. A campaign that cannot say which version of the tool produced its
numbers is not reproducible, and that half of the pin had no home in the original layout because the
harness was in the same history as the tool.

**The rule grows rather than shrinking.** `SourceTreeRulesTest` searched every module's `src/` and the
build files for the platform's name. It now searches the whole repository — `.git` and build output
aside — including the names of the files themselves, since a directory called after the benchmark
couples the two whatever its contents say. Five documents are exempt, each in full rather than by
section: this record, `docs/DESIGN.md`, `ROADMAP.md`, `CLAUDE.md` and the reviewer's checklist. A
mention in any other file fails the build, and a second rule fails when one of the five stops naming
the platform, so an exemption cannot outlive its reason.

The `src/` trees keep being read from disk as well as from git's list, so a file somebody has written
but not yet committed still fails their own build rather than waiting for CI to say so. Everywhere
else the list is git's, because a walk would sweep up scratch checkouts, run output and private notes
that are not part of the repository at all, and failing somebody's build over a file they never
committed is how a rule teaches people to distrust it.

### Consequences

- "Deleting `evaluation/` leaves a fully working tool" becomes vacuous, which is the point: there is
  nothing to delete, and nothing to explain to a reader who wonders what that directory is for.
- Whoever runs a campaign now works in two repositories instead of one. The evaluation repository's
  README carries every command, and pins the version of the tool it measured, so the extra step is
  "clone this too" rather than "reconstruct what was run".
- The adapter can go stale against a change to the command line and nobody would notice until the
  next campaign — which is the worst possible moment to find out. A weekly job in the evaluation
  repository builds the image against the tip of `v2` and runs one API for a minute. When it goes
  red, the contract moved.
- Contributing the adapter to the benchmark upstream becomes a submodule reference rather than a
  copy, because the repository already has the shape their `tools/` expects. It stays deferred until
  v2.0 is published: offering them an adapter for a tool still under construction asks them to
  maintain our work in progress. Note that RESTest 1.x is already a tool there, under `restest`,
  which is why ours is `restest2` - the two can be measured side by side in one campaign.
- The evaluation repository starts private, so the two paragraphs above describe work nobody
  reading this record can open. They are anchored as well as they can be: the first campaign is
  `results/20260916-155435/` there, and the weekly job is `.github/workflows/contract-check.yml`.
  M8.2 has to make the repository public, or it is not a replication package.

### Alternatives considered

- **A long-lived branch in this repository.** Rejected. It is invisible to the working tree, to CI and
  to Dependabot; no pull request against `v2` makes sense for it; it rots precisely because nobody
  sees it; and it still lives here, so a reader still finds the benchmark's name in the branch list.
  It has the costs of both options and the benefits of neither.
- **Keeping `evaluation/` as originally decided.** Rejected for the reasons above. Note that the
  alternatives the original decision rejected — a Maven module, working in the benchmark's own
  repository, using the benchmark for the per-pull-request smoke run — are all still rejected, and
  for the same reasons.
