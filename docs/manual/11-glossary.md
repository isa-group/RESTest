# Glossary

The words this manual uses, in the sense it uses them.

**Accepted request.** A request the API answered with a success, a `2xx`. RESTest remembers what such
requests carried, and changes one thing in them to see what the API does with it.

**Black-box testing.** Testing an API from the outside, with only its description and its replies,
never its code. RESTest tests only this way.

**Budget.** How long a run tests for, given with `--budget`: a minute unless you say otherwise. The
whole of it is used, reading the document included, unless `schedule.stopAfterRequests` ends the run
sooner.

**Catalogue.** The numbered list of kinds of fault that several API testing tools share, the [Web
Fuzzing Commons](https://github.com/WebFuzzing/Commons) fault catalogue. `F100`, `F121`, `F200`,
`F205`, `F206` and `F312` come from it. It leaves the numbers from 900 to 999 to each tool, and
`F920` and `F921` are RESTest's own, for kinds it has no number for.

**Check.** One rule a run applies to what came back, or did not, such as whether a reply's body is
the shape the document gives. Each check reports one kind of fault and has a switch of its own. See
[the faults](../faults.md).

**Clinic.** The pet clinic, an open-source API the manual's examples run against, started in Docker
as [chapter 3](03-a-first-run.md) shows.

**Cut short.** Said of a run stopped from outside — Ctrl-C, `kill`, `docker stop` — before its budget
ran out. What it reports is what it found until then.

**Dictionary.** A file of values worth sending, handed over with `--dictionary`. See [chapter
6](06-dictionaries.md).

**Error.** A fault that says the API failed at its job: it fell over (`F100`, `F121`), never answered
(`F312`), or accepted what its own document forbids (`F206`). A run that finds one ends with `1`. See
[errors and warnings](../faults.md#errors-and-warnings).

**Exit code.** The number a run ends with: `0` no error in what could be checked, `1` an error found
— or a warning, when `checks.failOnWarnings` asks — other numbers when the run could not do its job.
See [chapter 9](09-exit-codes-and-ci.md).

**Fault.** A reply RESTest has a reason to call wrong: a reply of 500 (`F100`), another server error
(`F121`), one whose body is not the shape the document gives it (`F200`), a 501 to an operation the
document declares (`F205`), a success in answer to a request broken on purpose (`F206`), none at all
to a request the API took (`F312`), or a status code (`F920`) or content type (`F921`) the document
does not declare. Each is an error or a warning. A reason to look, not a verdict.

**Idle.** The share of a run during which RESTest had no request in flight. Reported on every run,
and kept as close to nothing as possible.

**Key.** A string an API wants with a request to know who sent it, handed over with `--auth`. See
[chapter 8](08-an-api-that-asks-for-a-key.md).

**Memory.** What a run keeps of the API's replies — identifiers, names, whole things it returned —
to send in later requests. In a plan, the source called `observed`.

**Not checked.** Said of a reply a check could not judge: the shape of a body the document gives no
shape for, say. Not a fault, and it never fails a run; a run counts such replies, says why, and lists
them under `notChecked` in its report. See [What a run leaves behind](../report.md#notchecked).

**OpenAPI document.** The file, in YAML or JSON, that describes an API: its operations, what each
accepts and what each answers. The one thing a run needs.

**Opening lap.** The first round of a run: every operation sent once, with the request most likely
to be accepted, before anything is left to chance.

**Operation.** One thing an API can be asked to do, named by a method and a path, such as
`GET /api/owners/{ownerId}`, and usually by an `operationId` the document gives it, such as `getOwner`.

**Plan.** The file that says how a run builds its requests, where their values come from, and which
operations it may touch. Also called a campaign file. See [chapter 5](05-plans.md).

**Pushing.** Sending values nobody sensible would send — an empty word, a number too large to hold —
to see whether the API falls over. One of the strategies of the plan RESTest carries.

**Report.** `report.json`, the file a run writes when it ends, for programs to read. See [What a run
leaves behind](../report.md).

**Seed.** The number every choice a run makes by chance comes from. Printed by every run, and given
with `--seed`.

**Series.** A few requests sent one after another about something a run has just created: delete it
and read it again, create it twice. Some faults only show across several requests.

**Setting.** A number, or a yes or a no, that says how the tool behaves — how many requests it keeps
in flight, how long it waits. See [chapter 7](07-settings.md).

**Share.** The part of a run a strategy is given, as a number in the plan: of RESTest's own plan,
45 for requests meant to be accepted, 15 for series, 30 for changing accepted requests and 10 for
pushing. See [chapter 5](05-plans.md).

**Source.** A place a value can come from: the document's closed list of values, its samples, its
defaults, a value invented to fit, the memory of what the API returned, or a dictionary.

**Status code.** The three-digit number at the start of every reply. `2xx` means success, `3xx` a
redirection to somewhere else, `4xx` that the request was refused, `5xx` that the server failed.

**Stored run.** `run.sqlite`, written with `--store`: every request and reply of a run, to look at
later without asking the API anything.

**Strategy.** One way a plan builds requests, with its share of the run. Its kind says which:
`nominal`, meant to be accepted; `fuzzing`, pushing; `mutation`, changing an accepted one;
`sequences`, starting a series.

**Switch.** A setting that is `true` or `false`. Nearly every one turns off one thing a run does; a
few, off by default, turn something on, such as `checks.failOnWarnings`.

**Title.** What RESTest calls a kind of fault, in its own words — *Internal server error*, *Reply off
schema* — printed before the catalogue's number. It is also the name of the check's switch, written
as one word: `checks.internalServerError`.

**Warning.** A fault that says the API answered something its document does not describe — a body
off its shape (`F200`), a 501 to an operation the document declares (`F205`), a status code
(`F920`) or content type (`F921`) the document does not declare — where the document may be the one
out of date. Counted and said; it ends a run with `1` only when `checks.failOnWarnings` asks. See
[errors and warnings](../faults.md#errors-and-warnings).
