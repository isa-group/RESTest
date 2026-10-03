# Glossary

The words this manual uses, in the sense it uses them.

**Accepted request.** A request the API answered with a success, a `2xx`. RESTest remembers what such
requests carried, and changes one thing in them to see what the API does with it.

**Black-box testing.** Testing an API from the outside, with only its description and its replies,
never its code. RESTest tests only this way.

**Budget.** How long a run tests for, given with `--budget`: a minute unless you say otherwise. The
whole of it is used, reading the document included.

**Catalogue.** The numbered list of kinds of fault that several API testing tools share, the [Web
Fuzzing Commons](https://github.com/WebFuzzing/Commons) fault catalogue. `F100` and `F200` come from
it.

**Cut short.** Said of a run stopped from outside — Ctrl-C, `kill`, `docker stop` — before its budget
ran out. What it reports is what it found until then.

**Dictionary.** A file of values worth sending, handed over with `--dictionary`. See [chapter
6](06-dictionaries.md).

**Exit code.** The number a run ends with: `0` nothing wrong, `1` a fault found, other numbers when
the run could not do its job. See [chapter 9](09-exit-codes-and-ci.md).

**Fault.** A reply RESTest has a reason to call wrong: a reply of 500 (`F100`), or one whose body is
not the shape the document gives it (`F200`). A reason to look, not a verdict.

**Idle.** The share of a run during which RESTest had no request in flight. Reported on every run,
and kept as close to nothing as possible.

**Key.** A string an API wants with a request to know who sent it, handed over with `--auth`. See
[chapter 8](08-an-api-that-asks-for-a-key.md).

**Memory.** What a run keeps of the API's replies — identifiers, names, whole things it returned —
to send in later requests. In a plan, the source called `observed`.

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

**Source.** A place a value can come from: the document's closed list of values, its samples, its
defaults, a value invented to fit, the memory of what the API returned, or a dictionary.

**Status code.** The three-digit number at the start of every reply. `2xx` means success, `3xx` a
redirection to somewhere else, `4xx` that the request was refused, `5xx` that the server failed.

**Stored run.** `run.sqlite`, written with `--store`: every request and reply of a run, to look at
later without asking the API anything.

**Strategy.** One way a plan builds requests — meant to be accepted, pushing, changing an accepted
one, or starting a series — with its share of the run.

**Switch.** A setting that is `true` or `false` and turns off one thing a run does.
