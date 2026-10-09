# ADR-0030: The process RESTest is built with lives outside the repository, and AGENTS.md is the contract it relies on

**Status:** Accepted
**Date:** 2026-10-09

## Context

Until 2.1.0 this repository held, beside the tool, the means of building it: the plan of what came
next (`ROADMAP.md`), a description of the process (increments, supervision points, the house format
of a pull request), and the configuration of the coding agents the maintainers work with (a reviewer
agent, two commands and a hook under `.claude/`), with about thirty lines of process inside the
`CLAUDE.md` that also held the project's rules.

That mixture had costs that grew with the project:

- **The product's history recorded the process.** During 2.0, 60 commits changed `ROADMAP.md`, 12
  `CLAUDE.md` and 6 `.claude/`, most of them to adjust how work was done rather than what the tool
  does.
- **Contributors were asked for a process they could not follow.** The pull-request template asked
  which increment of the plan a change delivered and whether "the reviewer subagent" had reviewed
  it, and the documentation sent readers to the plan eleven times.
- **The rules spoke to one tool.** The project's rules were in `CLAUDE.md`, which only one family of
  coding agents reads, and a hook made every session in this repository run Maven after each edit,
  whether or not the person had asked for it.
- **The same rules were written in four places**: `CLAUDE.md`, `CONTRIBUTING.md`, the reviewer's
  instructions and the template's checklist.

ADR-0011 had already taken the evaluation harness out of the repository, for reasons of the same
kind. The maintainers want the same for development: a process that can change at its own pace, that
another team could replace with its own, and a repository about the product.

## Decision

**The plan, the process, the agent configuration and the evaluations of the work live outside this
repository, with whoever does the work. This repository keeps what is true of the product however it
is built, and states it in `AGENTS.md`.**

1. **What stays here** is what a contributor needs to understand, build, test or change RESTest
   without anybody's development tooling, and everything a user of RESTest reads: the requirements
   (the README, the manual, the reference pages), the design (`docs/DESIGN.md`), the decisions
   (`docs/adr/`), the constraints and the tests that enforce them, and the conventions for
   contributing (`CONTRIBUTING.md` and the pull-request template).
2. **`AGENTS.md` holds the project's rules**, in the file most coding agents read. `CLAUDE.md` is one
   line that imports it, which is how Claude Code shares one file with other agents. People read the
   same file.
3. **`AGENTS.md` is the contract.** A development tool, whoever's, may rely on the commands and the
   files it names and on nothing else. A change to them is a change to RESTest, made in a pull
   request.
4. **Nothing here names a development tool.** No plugin, marketplace, private repository or path on
   somebody's machine appears in a file of this repository or in its settings. The one file of agent
   configuration that stays, `.claude/settings.json`, only forbids a few dangerous actions (pushing to
   `master`, forcing a push, deploying, reading secrets) to anyone working here with Claude Code.
5. **A check that needs no judgement is a test here.** The documentation checks and the architecture
   rules stay in this build. A check that needs a model's judgement belongs to the development
   tooling, which also keeps every dependency on a model out of RESTest (ADR-0008).
6. **The scope stays here.** What RESTest will not do, and why, is "Out of scope" in
   `docs/DESIGN.md`; the open questions about it are argued in ADR-0017.

## Consequences

- The repository no longer says what will be built next. Somebody who wants to contribute something
  large opens an issue first, as in most open-source projects.
- Records written while 2.0 was built cite its plan: milestone codes such as `M1.9`, and rows of
  `ROADMAP.md`. That plan is kept, as it was, at the tag `history/2.0-development`, and the index of
  the records says so. Records written from now on cite no plan.
- Contributors get the same rules whichever coding agent they use, or none, and no session in this
  repository runs anything because of where it was opened.
- A tool that relies on something `AGENTS.md` does not list may break when RESTest changes. That is
  the tool's to fix; keeping the list short is what makes it a contract.
- Pull requests may still say, in their description, which version of the maintainers' tooling
  produced them. That is a note about the work, not a dependency of the product.

## Alternatives considered

- **Keeping the plan here and moving only the agent configuration.** A public plan is useful to
  outsiders, but this one changed with every pull request and named the process at every row. A
  public summary for users can be written when there is a site to put it on.
- **A submodule, or a branch of this repository, holding the tooling.** Both keep the tooling inside
  the repository, and a submodule would make it name a repository its readers cannot open.
  ADR-0011 rejected both for the evaluation harness.
- **Declaring the maintainers' tooling in the project's `.claude/settings.json`.** One fewer step for
  each maintainer, at the cost of this repository naming a private one and asking every clone to
  install something it cannot reach.
- **Keeping `CLAUDE.md` as the rules file.** It works for one agent; `AGENTS.md` works for most, and
  the one-line `CLAUDE.md` keeps Claude Code reading the same rules.
