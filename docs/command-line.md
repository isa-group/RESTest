# The command line

Everything a person types to run RESTest, and everything a script can rely on afterwards: the
commands, the options of a run, the environment variables, and the number a command ends with. It
is what `restest --help` and `restest help run` say, in one place. A test reads the tool's own help
on every build (`DocumentedCommandLineTest`) and fails when this page disagrees with it about the
commands, the name, value and default of an option, the variables, or an exit code and what it
means.

**The command line is frozen from v2.0.** [What that means](#what-frozen-means) is at the end.

## Commands

| Command | What it does |
|---|---|
| `restest run <specification> [options]` | Tests an API described by an OpenAPI document, for as long as it was given, and reports what is wrong with it |
| `restest version` | Says which RESTest this is, and which Java and which machine it runs on — the two lines to paste at the top of a report of something that went wrong |
| `restest help [<command>]` | Shows how a command is used: `restest help run` is `restest run --help` |

Every command also takes `-h` or `--help`, and `restest`, `run` and `version` take `-V` or
`--version`, which prints what `restest version` prints. `restest` on its own shows the list of
commands and answers `2`, since it was asked to do nothing.

## The options of `restest run`

`<specification>` is the only thing a run needs: a file, a web address, or something on the class
path. Every option has a default, and the defaults are the tool's zero-configuration run.

| Option | Value | Default | What it does |
|---|---|---|---|
| `--url` | `<base>` | the first usable address the document declares | Which machine the API runs on. Given without a path, the directory the document declares is kept; given with one, that path is used instead |
| `--auth` | `<key>` | none | A key or a token the API asks for — see [below](#handing-over-a-key-or-a-token). Repeat for several |
| `--budget` | `<duration>` | `60s` | How long to keep testing: `500ms`, `30s`, `5m`, `2h`, a plain number of seconds, or ISO-8601 such as `PT1M30S`. All of it is used, reading the document included |
| `--seed` | `<number>` | one chosen and printed | With the plan RESTest carries, the same number gives a similar run rather than the same one, because what a run sends also depends on what the API answers; `--store` keeps the run you had. The number fixes every random choice. [Getting the seed back](switches.md#getting-the-seed-back) says how to make it repeat a run exactly |
| `--out` | `<directory>` | `restest-out` | Where this run's files go: `report.json`, and `run.sqlite` with `--store`. What an earlier run wrote there is replaced; nothing else in the directory is touched |
| `--dictionary` | `<file-or-directory>` | none | A list of values to send, or a directory of them. Repeat for several. [The format](dictionary-format.md) |
| `--campaign` | `<file>` | the plan RESTest carries | A plan saying where values come from and which operations the run may touch. [The format](campaign-format.md) |
| `--print-campaign` | — | — | Writes out the plan RESTest carries, and stops. Not with `--campaign` or `--print-settings` |
| `--settings` | `<file>` | none | A file of settings, saying how the tool itself behaves. [The settings](settings.md) |
| `--set` | `<group.key=value>` | none | One setting. Repeat for several |
| `--print-settings` | — | — | Writes out the settings this command would use, each with where its value came from, and stops |
| `--store` | — | off | Keeps every request and reply in `run.sqlite`. Off unless asked for: a minute against a fast API keeps hundreds of megabytes |

Where a setting is given in more than one place, the command line wins over the environment, which
wins over `--settings`, which wins over what the tool does when nobody says otherwise.

### Handing over a key or a token

`--auth` is the one way in for anything that signs a request. RESTest hides every value handed over
this way in everything the run writes — the screen, `report.json` and its `curl` commands, the stored
run — and writes `REDACTED-AUTH` where it went: followed by the key's name, `REDACTED-AUTH.api_key`,
when it was given with one, and by its place, `REDACTED-AUTH.header.X-API-Key`, when it was given
with that. The run says which before its first request.
[ADR-0029](adr/0029-the-key-an-api-asks-for.md) has the rules.

```bash
restest run api.yaml --auth special-key                              # the document declares one key
restest run api.yaml --auth api_key=special-key                      # it declares several: name one
restest run api.yaml --auth header:X-API-Key=k3y-4-lt                # it declares none: say where
restest run api.yaml --auth query:apiKey=k3y-4-lt
restest run api.yaml --auth 'header:Authorization=Bearer eyJhbGciOi...'   # a token you hold
restest run api.yaml --auth cookie:JSESSIONID=8F3A2C...              # a session cookie you hold
```

A key the document declares goes with the operations that ask for it. One given with its place —
`header:`, `query:` or `cookie:` — goes with every request, which is what a bearer token or a
session cookie somebody already holds needs. RESTest 2.0 does not obtain a token itself: signing in,
OAuth 2 and refreshing what expires come after it.

## The environment

| Variable | What it holds |
|---|---|
| `RESTEST_AUTH` | What one `--auth` holds, so that a key is not typed where a shell's history keeps it. One typed with `--auth` for the same place wins over it. It is not a setting, and is never printed or recorded with them |
| `RESTEST_<GROUP>_<KEY>` | One setting, named after it: `RESTEST_ENGINE_MAX_CONCURRENCY=1` is `engine.maxConcurrency=1`. [Every one of them](settings.md) |

A variable set to nothing counts as not set.

## Files of arguments

A long command can be kept in a file and named with `@`: `restest run @petclinic.args` reads the
file's words as if they had been typed there, and a file may name another one. Each file is read
once, so a pipe works too: `restest run api.yaml @<(./print-my-arguments)`.

Inside the file, words are separated by spaces and line breaks, and a `#` begins a comment to the
end of its line. A quotation mark, `'` or `"`, keeps what it holds together as one word, spaces and
`#` included, and a backslash inside it begins an escape such as `\n` — so a backslash meant as
itself is written twice there. Outside quotation marks a backslash is an ordinary character. A
quotation mark also ends the word it follows: `--auth='…'` in a file is two words, an empty
`--auth=` and a stray one. So a key goes in a file like this, or in `RESTEST_AUTH` instead:

```
--auth k3y\path                         # a backslash, and no space, # or quotation mark: as it is
--auth 'k3y\\path#1'                    # a # or a space: in quotes, each backslash written twice
--auth
'header:Authorization=Bearer eyJhbGciOi...'
```

On the command line, `@@` stands for a word that begins with `@`, and a name that is not a file
RESTest may read is taken as a word as it is. A file that is there and cannot be read — a directory
named by mistake — answers `2`, with a sentence saying which.

## Stopping a run

Ctrl-C, `kill` and `docker stop` stop a run without losing what it found. The run makes no new
requests, waits for the answers to the requests it had already made for up to
`schedule.interruptGrace` — 2 seconds unless [set](settings.md) otherwise — and then writes what it
found, as it would at the end of its budget: the summary, `report.json` with `"cutShort": true`, and
with `--store` a closed `run.sqlite`. It ends with `130` for Ctrl-C and `143` for the other two.
Requests it had made and not yet sent, waiting their turn behind the ones in flight, still go out
while it waits: never more than may be waiting for an answer at once, 32 unless set otherwise.

```
$ restest run openapi.yaml --url http://localhost:9966/petclinic/api --budget 60s --store
...
^C
16306 requests to 35 operations in 11.9s, 9% of it idle, cut short
...
restest: the run was stopped from outside after 11.8s of its 60s budget; what is reported above, and in report.json, is what it found until then
report written to restest-out/report.json (382.8 KiB)
run stored in restest-out/run.sqlite (117.8 MiB)
$ echo $?
130
```

An answer that has not come back by the end of that wait counts as never answered, and the run
says how many there were. A second Ctrl-C does nothing: the run is already stopping, and is done in
a few seconds. If it has not finished writing `schedule.interruptGrace` and five seconds after it
was stopped — a report that got stuck, say — it says what it did not leave behind, and ends.
`report.json` is only ever there whole. A `run.sqlite` that was not closed has lost the last
interactions it had not yet saved, and keeps some of what it did save in `run.sqlite-wal` beside
it, so the three files go together. If the directory itself does not answer, the run says that
instead, a second later.

A run stopped while it is still reading the document writes nothing, since it has found nothing
yet, and says so: the directory it would have written to is left as it was, with whatever an
earlier run left there. `kill -9`, and `docker stop` once its ten seconds are up, end the program on
the spot, with nothing written. So does Ctrl-C or `kill` to a Java started with `-Xrs`. On
Windows, Ctrl-C and closing the console stop a run this way, and `taskkill /F` is `kill -9`.

## Exit codes

The number the command ends with, which is what a build server or a script acts on.

| Code | Meaning |
|---|---|
| `0` | The run finished and found nothing wrong. `--print-campaign`, `--print-settings`, `version` and `--help` answer `0` too |
| `1` | The run finished and found at least one fault in the API |
| `2` | The command line was wrong, or a plan, a settings file or a key it named could not be used. Nothing was sent. A file of arguments that cannot be read is one of these |
| `3` | Nothing was tested: the document describes no operation that can be tried, there is no address to send requests to or nowhere to write the results, or not one request was answered - none could be built, the budget ran out before the first, or nothing at the address replied. The run says which. A script that starts the API and RESTest together meets the last one when RESTest is quicker |
| `4` | RESTest itself went wrong - it lost requests on their way, had to keep exchanges without their details, or broke outright - so what it printed may be incomplete. The message and the stack trace are what to report, with what `restest version` says. A run in which RESTest lost requests answers `4` even when nothing at all was answered; if the address looks wrong as well, the run says so beside it. An exchange kept without its details is one a key could not be picked out of, so everything in it that could hold the key was blanked: the key is hidden, not leaked |
| `130` | The run was stopped with Ctrl-C. What it found until then was printed and written, and report.json says it was cut short - or the run says what it did not leave behind: a run stopped while it still read the document wrote nothing, and one that could not finish writing in time says what is missing. [Stopping a run](#stopping-a-run) has the details. The number is Java's, the one every shell reads as "interrupted", and it is kept whatever the run found |
| `143` | The run was stopped with kill or docker stop. What it found until then was printed and written, and report.json says it was cut short - or the run says what it did not leave behind, the same as for `130`. Java gives this number itself |

A fault found is `1` rather than `0` because the usual use is a gate that should go red when the API
is broken. An operation RESTest had to skip is not an error: a run says what it skipped and carries
on, and only a run in which nothing at all was tested answers `3`. A run stopped from outside never
answers `0` or `1`, so that one stopped half-way never passes for one that finished.
[ADR-0015](adr/0015-command-line-contract.md) has the reasoning.

### Numbers RESTest does not choose

| Number | When |
|---|---|
| `127` | The `restest` script at the root of a checkout, when the checkout has not been built yet at the version it declares |
| `129` | The terminal the run was started from was closed. The run stops as it does for Ctrl-C and writes what it found; what it prints has nowhere to go |
| `137` | The run was killed outright — `kill -9`, a container's memory limit, or `docker stop` once its ten seconds are up — and nothing of RESTest ran after it |
| `1` | Java itself could not start: no RESTest on its class path, or an option it does not know. Nothing of RESTest ran, so this `1` says nothing about the API |
| `3` | Java was started with `-XX:+ExitOnOutOfMemoryError`, as some container images do through `JAVA_TOOL_OPTIONS`, and ran out of memory |

## What frozen means

From v2.0, and for every 2.x version:

- no command, option or environment variable is removed or renamed, and none changes what it means;
- no exit code changes what it means;
- the names of the files a run writes, `report.json` and `run.sqlite`, stay what they are.

A minor version may **add** — a command, an option, a setting, a number for something that has none
yet — because nothing written for 2.0 breaks when something is added. Anything that would break it
waits for 3.0. What the tool prints for people to read, the wording of a summary or of this page, is
not part of the promise and gets better whenever it can.
