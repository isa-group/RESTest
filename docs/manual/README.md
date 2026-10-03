# The RESTest manual

This manual takes you from never having used RESTest to testing your own API with it, and to having
your continuous integration do it for you. It is meant to be read from the beginning: each chapter
builds on the one before, and every example in it is one you can run yourself.

The [reference pages](../README.md) are the other half of the documentation. They are where to look
something up — every option, every line a run prints, every key of its report — and the chapters
here point to them rather than repeat them.

## Chapters

1. [What RESTest is for](01-what-restest-is-for.md) — the problem it solves, what it finds, and what
   it does not.
2. [Installing RESTest](02-installing.md) — what you need, and building it from source.
3. [A first run](03-a-first-run.md) — a practice API on your own machine, and the first run against
   it.
4. [Reading what a run says](04-reading-what-a-run-says.md) — the screen, the report, and the
   questions you can ask of them.
5. [Plans](05-plans.md) — where a run's values come from, how hard it pushes, and which operations it
   may touch.
6. [Dictionaries](06-dictionaries.md) — handing over values you know are good.
7. [Settings](07-settings.md) — how the tool itself behaves, and switching off what it does.
8. [An API that asks for a key](08-an-api-that-asks-for-a-key.md) — handing over a key or a token.
9. [Exit codes and continuous integration](09-exit-codes-and-ci.md) — what a run ends with, and
   using that in a build.
10. [Troubleshooting](10-troubleshooting.md) — what to do when a run does not do what you expected.
11. [Glossary](11-glossary.md) — the words this manual uses.

## What you need

A computer with Java 21 or later, git, and — for the practice API the examples run against — Docker.
Chapter 2 says how to check each. Nothing else: RESTest needs no account, no server of its own and
no configuration.

## Conventions

Commands are written for a shell such as bash or zsh, run from the root of the RESTest checkout:

```bash
./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s
```

Output is shown the way it appears, in a block of its own. The numbers in it are one run's; yours
will be different, because a run sends values chosen by chance and learns from what the API answers.
