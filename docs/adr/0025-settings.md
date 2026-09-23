# ADR-0025: The numbers somebody decided live in one settings object, layered from four sources, and never in the plan

**Status:** Accepted
**Date:** 2026-09-22

## Context

Counting the named constants in the production modules — `static final` numbers with a name and a
comment — gives about eighty. Some are facts: `500` is a server error, a file format has a version
`1`, HTTP says what a status class is. Most are **decisions**: how many requests may be in flight and
how fast that number grows, how many bytes of a reply are kept, how deep an invented body goes and
how long an invented string is, how many values the memory of what the API returned keeps under one
name, how many write-ups the JSON report quotes per operation, how long the tool waits for a document
fetched over the network, how many announcements may pile up before the loop slows down.

Every one of them was decided on purpose, with a comment saying why, and none of them can be changed
without recompiling. Three things want that changed.

**A user with an API that is not the one we imagined.** A fragile API wants one request at a time;
`EngineSettings.withoutConcurrency()` exists for exactly that and no command-line option reaches it.
A slow one wants a longer read timeout. An API that returns replies of a megabyte wants more of them
kept, or less.

**An experiment that wants a behaviour off.** ADR-0024 puts nine behavioural levers into v2.0 and
asks for an ablation that measures each. An ablation by branch — one commit per variant — is nine
builds of the container image, nine pinned commits, and a results directory nobody can compare
without reading git. An ablation by switch is one image and one line per variant.

**Two runs in one process.** Design principle 6 says two runs must coexist in one JVM. A setting read
from a system property or an environment variable at the point of use is global state with a
friendly name: the second run reads the first one's answer.

### What the plan file is, and is not

ADR-0023's plan says *where a run's values come from* and *which operations it may touch*. It travels
with an API: somebody writes one per API, and it means the same thing on every machine. The numbers
above are about the *tool* — how hard it pushes, how much it keeps, how deep it goes — and they
travel with a machine or with an experiment. The maintainer's instruction is that they do not go in
the plan, and the reason is the one above: a plan that carried a concurrency would be wrong on the
next machine, and an experiment that changed a concurrency would have to rewrite a file about the API.

### What is already there

`EngineSettings` in `restest-core`: an immutable record of the engine's numbers with `with*` methods
and defaults in code, built in `restest-cli` and handed to the engine by constructor. It is the shape
this record generalises. Nothing reads it from anywhere; the command line does not expose it.

## Decision

### 1. One `Settings`, typed, immutable, in `restest-core`

`Settings` is a record of records, one per concern, each the shape `EngineSettings` already has:

| Group | Key prefix | What it holds |
|---|---|---|
| engine | `engine.*` | timeouts, the concurrency range and its slowdown factor, retained response bytes, redirects, user agent |
| schedule | `schedule.*` | the work-ahead factor, how many announcements may pile up, the straggler grace; from M9, the opening lap, the hygiene window, floor and code list, and the switches of every scheduling lever. Until 9.1 these numbers live in `RunLoop`, in the command-line module; 11.1 creates the group there and 9.1 carries it to the scheduler |
| generation | `generation.*` | depths, string lengths, item counts, the null rate, attempt counts, the optional-parameter distribution; from M10, the switches of every mutation and shape operator |
| memory | `memory.*` | how many observed values are kept under one name, how many names, the longest value kept, the longest reply read, how deep a reply is read |
| sequences | `sequences.*` | from M9 and M10, the switches of the producer-then-consumer sequence and of each sequence operator |
| document | `document.*` | the largest document read, the fetch timeout, the nesting limits |
| report | `report.*` | write-ups per operation and kind, in total, body bytes kept, faults shown on the console |
| store | `store.*` | batch size and the longest an interaction waits before it is written |

Defaults live in code, beside the thing they configure, with the comment that already explains
them. The record is built once and passed by constructor; nothing reads a setting from a static
anywhere, and an architecture test forbids `System.getenv` and `System.getProperty` outside
`restest-cli`.

### 2. Four layers, in a fixed order, assembled in the command-line module

```
defaults in code
  ← a settings file, YAML, given with --settings <path>
    ← environment variables, RESTEST_<GROUP>_<KEY>   (RESTEST_ENGINE_MAX_CONCURRENCY=8)
      ← --set group.key=value, repeated              (--set engine.maxConcurrency=8)
```

Later layers win. Keys are the same words in every layer — `engine.maxConcurrency` in the file and
on the command line, `RESTEST_ENGINE_MAX_CONCURRENCY` in the environment, the camel case split on
upper-case letters — so a person learns one name. No file is required and no file is looked for in a
default location: zero configuration means the tool starts with nothing beside it, and a file found
by accident in a working directory is a run nobody can explain.

An unknown key is refused, with the nearest known key named. A value of the wrong type or outside
its range is refused with the range. Refused means the exit code a plan file that cannot be read
already gets — `2`, *the command line was wrong*, since a file the command line named is part of
what it asked for — before a request is sent, and ADR-0015's table says so from 12.1.

### 3. The effective settings are printable and recorded

`restest run --print-settings` prints every key, its effective value and where it came from —
*default*, *file*, *environment*, *command line* — and stops, the way `--print-campaign` prints the
plan. The same table goes into `report.json` under `settings`, so a run says how it was configured
and an ablation's results directory carries its own variant with it.

### 4. Every behavioural lever has a switch, and the switches are listed

From this record on, an increment that adds a behaviour a run can do without — an opening lap, a
mutation operator, a sequence shape, a scheduling rule — ships a boolean under its group that turns
it off, default on unless the screening campaign says otherwise. A documented page lists every
switch, what it turns off and what it costs, and names the plan variants that complete an ablation
(the memory of observed values is a *source*, so switching it off is a plan without the `observed`
line, not a setting). A test checks that the page and the code agree.

### 5. What moves and what stays

A constant moves into the settings if a reasonable user or a reasonable experiment might want a
different value. A constant that is a fact — a status code, a format version, a unit conversion —
stays a constant. The first tranche is the groups above; the rest move when the code around them is
next touched, never in a sweep, and the comment that justified the constant becomes the comment on
the default.

## Consequences

- **ADR-0015 is amended**: `--settings`, `--set` and `--print-settings` join the surface, and are
  frozen with it at 12.1; exit code `2` is stated to cover a settings or plan file the command line
  named and the tool could not accept.
- **`report.json` grows a `settings` block.** Readers of the report that do not know it ignore it.
- **The plan file does not change.** It is about the API; this is about the tool. A key that could
  go in either goes here, and the test for which is whether it would mean the same thing for a
  different API on the same machine.
- **An ablation is one image and one variable per variant**, and its result names its own variant.
- **Two runs in one JVM keep different settings**, because there is no static to share.
- **Every M9 and M10 pull request is one row longer**: the switch, and its line on the page.
- **A first user-facing knob for the engine.** `--set engine.maxConcurrency=1` is the answer to "my
  API falls over when asked two things at once", and it did not exist.

## Alternatives considered

- **System properties alone** (`-Drestest.engine.maxConcurrency=8`). No file to explain, and every
  Java user knows them. Rejected: they are invisible from `--help`, awkward through the launcher and
  hostile to the native binary of 7.3, and read at the point of use they are global state. They are
  not one of the four layers, on purpose.
- **Environment variables alone.** Natural in a container, which is where the benchmark runs the
  tool. Rejected as the *only* layer: a flat namespace of eighty upper-case names is not a document
  a person reads, and there is no way to hand a colleague "the settings I used" but a shell script.
  They are the third layer because containers are real.
- **One command-line option per number.** What CATS and EvoMaster do. Rejected: eighty options in
  `--help`, and a command-line contract (ADR-0015) that changes every time a constant moves. `--set`
  gives the command line the reach without the surface.
- **A file alone, in a default location** (`restest.yaml` beside the document, or under the user's
  home). Rejected for the default location: a file found by accident changes a run nobody can
  explain, and the tool is supposed to start with nothing beside it. Accepted as a layer when named
  with `--settings`.
- **Settings inside the plan file, under a `settings:` key.** One file, one format, one option.
  Rejected by the maintainer, for the reason in the context: the two travel with different things.
- **Leave the constants where they are and add switches only.** Cheapest. Rejected: the same
  mechanism carries both, the switches need the layering and the printing anyway, and the constants
  are the part a user asks for.

---

## Amendment (M11.1)

**Date:** 2026-09-22

What building the first tranche settled, and one place where this record's own example did not work
as written.

### The record's own example was refused, so a default is now worked out rather than fixed

This record says `--set engine.maxConcurrency=1` *is the answer to "my API falls over when asked two
things at once", and it did not exist*. Built as written, it was refused: the engine starts at four
requests in flight, and four is outside the range somebody had just asked for, so the compact
constructor turned the line down.

Weakening that check was not an option — it is the range-checking this record asks for. So the
*default* for where the engine starts is now worked out from the range in force rather than fixed at
four: it is four, held inside whatever the fewest and the most turn out to be. Lowering the most to
one lowers the start to one; raising the fewest to eight raises the start to eight. A starting number
somebody names outright and that lies outside the range is still refused, saying what the range is,
because that is a value stated rather than a default worked out.

The general point is worth keeping: a group whose values constrain one another needs its defaults to
be functions of the values in force, not constants, or the simplest line anybody types is the one
that fails.

### What `--print-settings` writes is a file, and the file is the documentation

The obvious reading of §3 is a table. What is written instead is a settings file: every setting there
is, grouped, each with a comment saying what it does and a note saying which of the four places
decided its value. It is what `--print-campaign` does, and it means the round trip — print, change
one line, hand back — is the way to change a setting rather than a thing one could do. A test asserts
that what comes out reads back as the same values.

Two consequences. Handing the printed file back makes every setting's source *file*, because the
file states every one of them; that is honest rather than a defect, and the values are unchanged.
And lengths of time are quoted in what is printed, since `30s` unquoted is a word and `10` unquoted
is a number, and only one of the two survives a reader that does not know which setting it is
reading.

### What moved, and what did not

Thirty-nine settings in six groups: `engine`, `schedule`, `generation`, `memory`, `document`,
`report`. The `store` and `sequences` groups of §1 are not created, because nothing fills them yet —
`sequences` waits for M9 and M10 by design, and the store's two numbers are not in the tranche the
roadmap names. An empty group would be a promise printed to every user with nothing behind it.

Inside `generation`, what moved is every number that bounds *what a request may look like*: the
depths, the word and list lengths, the room an unbounded number is invented in, the decimal places,
the rate at which something optional is included, and the attempt counts. What stayed, and is named
here so the next person does not have to rediscover it, is the safety limits inside the helpers that
build a string from a spelling rule and a value from a declared kind — how far a repetition with no
end may run, how many characters may be built while looking for one value, how deep a rule is read.
Those are backstops against the tool exhausting its own stack or memory rather than shapes of a
request, they sit behind static calls with no object to hang settings on, and §5's rule is that the
rest move when the code around them is next touched rather than in a sweep.

One number was **found to be misnamed while it was moved**. The pair that decides the room an
unbounded number is invented in reads as a bottom and a top, and the second is in fact a width added
to the first — a description stating a bottom of its own gets the same room above *that*. It is now
`generation.lowestNumber` and `generation.roomAboveIt`, because a printed file that names a setting
wrongly is worse than no printed file.

### One thing outside this record had to move: the reader of hand-written files

The settings file is YAML, and it is read in `restest-cli`, which held no YAML reader and should not
hold a second copy of one. The small class that already read the other two hand-written files moved
into `restest-core` beside the JSON reader, which is where the M1.6 amendment to
[ADR-0006](0006-event-stream-and-store.md) put the same argument the first time. That amendment
records it.

### A setting is only accepted if it can be written back out

Three ordinary things to type got past every check and then killed the run with a Java stack trace
and exit `4`, where this record promises exit `2` before a request is sent: a number larger than the
machine can hold (`1e400`, which becomes infinity and satisfies *greater than one*), a length of time
longer than milliseconds can count, and a number written in eleven characters that is a thousand
million characters written out.

The rule that closes all three, and that whoever adds a setting should keep: **a value is accepted
only if the tool can write it back out in the spelling it reads.** Lengths of time finer than a
millisecond and longer than about 292 million years are refused, because the spelling has no unit
below `ms` and none above what milliseconds can count. A number that is not finite is refused where
it is bounded. A number taking more than a thousand characters to write out is refused, measured in
characters rather than magnitude, since the two are different questions.

That rule is not tidiness. Printing the settings and recording them in the report are promises made
in §3, and a value that cannot be printed breaks both — long after the line that caused it.

The escaping of what is printed follows from the same rule. A value with a line break in it was
written into the file as a line break, which split one setting across two lines and came back with
the break turned into a space. Text is now escaped the way a double-quoted YAML scalar is, and the
round trip is asserted by reading the printed file back rather than by comparing the string it
produced.

Two more values turned out to be in the same class once the rule was written down, and both were
found by pointing the rule at the settings rather than by review. A letter YAML will not carry as
itself - half of a surrogate pair that never got its other half - was accepted and then produced a
printed file the tool refused to read; those are written as escapes now. And a number written to a
particular number of places did not come back with them: `1E+2` was printed as `100`, which is the
same number written differently. That one is settled the way this project already
settles it everywhere else - a number is kept with its trailing zeros stripped, exactly as
`JsonValue.JsonNumber` has always done, so `1000`, `1E+3` and `1000.00` are one value rather than
three - rather than by quoting numbers in the printed file, which would have made every line of it
read like text.

A third round of review found the rule had a hole in the layer that matters most. Every layer but
one hands over text that is already as long as it is going to be; a *file* hands over what a YAML
reader made of it, and turning `1e999999999` back into text to check it is the thing that fills the
machine's memory. So the tool hung and then died with an out-of-memory failure and exit `1` -
*faults found* - for the one input the round before had made safe everywhere else. A number is now
asked how long it is before anything writes it out, from its own bookkeeping rather than by writing
it out, and the answer is one rule in one place that both the file layer and the generation settings
ask.

Two smaller things went with it. A length of time too long to count, written in the formal spelling,
was refused with advice about spelling — because the platform reports "not a length of time" and
"too long to count" identically, and only the failure underneath tells them apart. And a description
cut short by `document.mostBytesRead` was handed to the reader half-written, which reported it as a
document somebody had written wrongly; one byte more than the bound is read now, so *the description
ended* and *I stopped reading* can be told apart and the second says so.

### The template is generated, and the page that shows it is checked

RESTest carries the plan it follows as a real file, so that anybody can print it, copy it and change
a line. The settings are not carried that way and should not be: §1 puts the defaults in code beside
the thing they configure, so a file of them shipped alongside would be a second statement of the same
numbers, free to disagree with the first. It also could not do the half of the job that matters -
saying where each value came from - because a file sitting in a repository does not know what the
environment said.

What was missing is smaller and real: somebody reading this project without building it could see
the plan RESTest ships and could not see what a file of settings looks like. So the page documents
the template in full, and a test compares what the page shows with what the tool writes, along with
every row of its list of settings - name, value and explanation. A page that is a copy of something
that changes is a promise somebody breaks by accident; checking it is what makes writing it down
safe. The test caught its first disagreement on the commit that introduced it.

### A value nobody gave, and that is not a default either, says so

The derived starting concurrency above created a third case for §3's *where did this come from*
column: nobody named it, and it is not what the code says by default. Recorded as `default` it would
have put two different values under one word in two results directories, with nothing in either to
explain the difference — which is the one thing that column exists to prevent. `SettingSource` gains
a fifth member, *worked out*: a value nobody named that differs from the built-in default was worked
out from one that was named. Any future derived default is labelled correctly without anybody
remembering to.

Where that is decided matters, and the first attempt put it in the wrong place. Deciding it inside
the thing that holds the settings meant guessing, because that thing is handed settings rather than
what somebody gave — so a program embedding RESTest and passing its own settings got a report
calling its choices *worked out*, which is false. It is decided where the four layers are known,
which is the one place that can tell a value nobody gave from a value somebody gave.

### The architecture rule is in place and proved

`System.getenv`, `System.getProperty` and `System.getProperties` are forbidden outside
`restest-cli`, checked against the real code and against fixtures that break the rule on purpose —
all three of them, since a rule naming only the first two would let the third hand back the whole map
and be the way round both. No production code read any of them before this increment, so the rule
starts out as a fence rather than a repair.

## Amendment (M9.1)

**Date:** 2026-09-23

**The first lever, and its switch.** `schedule.openingLap` turns the first round of a run off and
`schedule.openingLapPatience` bounds how long one step of it waits; forty-two settings in all. The
`schedule` group stays in `restest-core`, read by the scheduler in `restest-gen` and by the loop in
`restest-cli`: "9.1 carries it to the scheduler" in §1 meant the numbers, which the scheduler is
handed by constructor like every other part. The page of switches §4 promises is 11.2's; until it
exists, this switch is documented in the settings page beside every other setting.
