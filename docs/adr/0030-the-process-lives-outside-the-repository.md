# ADR-0030: The process RESTest is built with lives outside the repository, and AGENTS.md is the contract it relies on

**Status:** Accepted
**Date:** 2026-10-09

## Context

Until October 2026 this repository held, beside the tool, the maintainers' means of building it:
the plan of what came next (`ROADMAP.md`), a description of their process (increments, supervision
points, the house format of a pull request), and the configuration of the coding agents they work
with — a reviewer agent, two commands and a hook under `.claude/` — with about thirty lines of process
inside the `CLAUDE.md` that also held the project's rules.

That mixture had costs that grew with the project:

- **The product's history recorded the process.** During 2.0, 60 commits changed `ROADMAP.md`, 12
  `CLAUDE.md` and 6 `.claude/`, most of them to adjust how work was done rather than what the tool
  does.
- **Contributors were asked for a process they could not follow.** The pull-request template asked
  which increment of the plan a change delivered and whether "the reviewer subagent" had reviewed
  it, and the documentation sent readers to the plan eleven times.
- **The rules spoke to one tool.** The project's rules were in `CLAUDE.md`, which only one family of
  coding agents reads. And every Claude Code session here ran a hook: after each edit of a `.java`
  file or a `pom.xml`, the tests of that module ran, offline, whether or not the person wanted it.
- **The same rules were written in four places**: `CLAUDE.md`, `CONTRIBUTING.md`, the reviewer's
  instructions and the template's checklist.

ADR-0011 had already taken the evaluation harness out of the repository, for reasons of the same
kind. The maintainers want the same for development: a process that can change at its own pace, that
another team could replace with its own, and a repository about the product.

## Decision

**The maintainers' plan, their own process, their agent configuration and their reviews of the work
live outside this repository. This repository keeps what is true of the product however it is
built, and states its rules in `AGENTS.md`.**

1. **What stays here** is what a contributor needs to understand, build, test or change RESTest
   without anybody's private tooling, and everything a user of RESTest reads: the requirements (the
   README, the manual, the reference pages, the known limitations), the design (`docs/DESIGN.md`),
   the decisions (`docs/adr/`) with the evidence about the product they rest on, the constraints and
   the tests that enforce them, and the conventions every contributor follows (`CONTRIBUTING.md` and
   the pull-request template).
2. **`AGENTS.md` holds the project's rules**, in the file most coding agents read. `CLAUDE.md` is one
   line that imports it, which is how Claude Code shares one file with other agents. People read the
   same file.
3. **`AGENTS.md` is the contract.** A development tool, whoever's, may rely on the commands and the
   files it names and on nothing else. A change to them is a change to RESTest, made in a pull
   request.
4. **Nothing here names anybody's private tooling**: no plugin, marketplace or private repository,
   and no path on somebody's machine — in the files of the repository, in its settings, or in the
   descriptions of its pull requests, which a merge can carry into `master`'s history. The decision
   records are history and keep saying what was used when they were written, this one included. Two files that any user of one coding agent reads stay, because
   they hold nothing private: `CLAUDE.md`, which only imports `AGENTS.md`, and `.claude/settings.json`,
   which only denies a few dangerous commands in their usual spelling — pushing to `master`, a forced
   push, a Maven deploy, reading `.env` files and `secrets/` — as a guard rail rather than a
   guarantee. The list of commands that settings file used to pre-approve (`./mvnw`, several `git`
   and `gh` commands) goes: what to pre-approve is each person's choice, in their own settings.
   Personal files of a maintainer are ignored through that person's own git configuration, not
   through `.gitignore`.
5. **A check that needs no judgement belongs in this build.** The documentation checks and the
   architecture rules stay here. A check that needs a model's judgement belongs to the development
   tooling, which also keeps every dependency on a model out of RESTest (ADR-0008). Two checks the
   removed reviewer made by searching — no model library or AI-named type, no idiom carried over from
   RESTest 1.x — are not tests yet; until they are, a reviewer makes them.
6. **The scope stays here.** What RESTest will not do, and why, is "Out of scope" in
   `docs/DESIGN.md`; the open questions about it are argued in ADR-0017 and its amendments.

## Consequences

- The repository no longer says what will be built next. Somebody who wants to contribute something
  large opens an issue first, as in most open-source projects. What the tool does not do yet, which
  the plan used to be the only place to say, is in `docs/known-limitations.md`.
- Records written while 2.0 was built cite its plan, its `CLAUDE.md` and its reviewer: milestone codes
  such as `M1.9`, rows of `ROADMAP.md`. That plan is at the tag `history/2.0-development`; records
  written after the tag cite the plan as it was when this repository last held it, at commit
  `0b17e7b7`. The index of the records gives both. Records written from now on cite no plan.
- Contributors get the same rules whichever coding agent they use, or none, and no session in this
  repository runs anything because of where it was opened.
- Claude Code users here are asked for permission where the old settings pre-approved a command,
  until they choose to allow it in their own settings.
- A tool that relies on something `AGENTS.md` does not list may break when RESTest changes. That is
  the tool's to fix; keeping the list short is what makes it a contract.
- Which version of the maintainers' tooling produced a pull request is recorded by that tooling, on
  its side, not in the pull request.

## Alternatives considered

- **Keeping the plan here and moving only the agent configuration.** A public plan is useful to
  outsiders, but this one changed with every pull request and named the process at every row. A
  public summary for users can be written when there is a site to put it on.
- **A long-lived branch of this repository for the tooling.** ADR-0011 rejected it for the evaluation
  harness: invisible to the working tree and to CI, left behind, and still inside the repository.
- **A submodule holding the tooling.** It would make this repository name a private one that its
  readers cannot open, and every clone would carry a reference it cannot fetch.
- **Declaring the maintainers' tooling in the project's `.claude/settings.json`.** One fewer step for
  each maintainer, at the cost of this repository naming a private one and asking every clone to
  install something it cannot reach.
- **Keeping `CLAUDE.md` as the rules file.** It works for one agent; `AGENTS.md` works for most, and
  the one-line `CLAUDE.md` keeps Claude Code reading the same rules.
