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
| schedule | `schedule.*` | the work-ahead factor, how many announcements may pile up, the straggler grace; from M9, the opening lap, the hygiene window, floor and code list, and the switches of every scheduling lever. 9.4 was set aside before it was built, so the hygiene settings do not exist. Until 9.1 these numbers live in `RunLoop`, in the command-line module; 11.1 creates the group there and 9.1 carries it to the scheduler |
| generation | `generation.*` | depths, string lengths, item counts, the null rate, attempt counts, the optional-parameter distribution; from M10, the switches of every mutation and shape operator |
| memory | `memory.*` | how many observed values are kept under one name, how many names, the longest value kept, the longest reply read, how deep a reply is read; since M9.2, whether a gap in an address is filled from the things of its kind and whether those are asked before the name |
| sequences | `sequences.*` | from M10, the switches of the producer-then-consumer sequence and of each sequence operator. 9.3 built the first, `sequences.pairs`, and was not merged, so the group is still not created |
| document | `document.*` | the largest document read, the fetch timeout, the nesting limits |
| report | `report.*` | write-ups per operation and kind, in total, body bytes kept, faults shown on the console; since the fix naming what a run skips, the operations that could not be tested named on the console |
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

## Amendment (M10.1)

**Date:** 2026-09-27

**A seventh group, `mutation.*`, holds the switches of the mutation operators**, which §1's table
put in `generation.*`. Sixteen settings: a switch for each of the two families of operator, one for
each of the eleven operators, how many of each operation's accepted requests are kept, and how long
an oversized word and list are. The reasons are in
[ADR-0027](0027-changing-one-thing-in-an-accepted-request.md) §6: `generation.*` is about what an
invented value may look like, a person switching mutations off should find every switch in one
place, and 10.2's shape operators join the same group. Sixty-two settings in all.

## Amendment (M10.3)

**Date:** 2026-09-28

**The `sequences` group §1 named is created,** with one switch for each of the six series
[ADR-0028](0028-sequences-over-things-a-run-creates.md) sends: `readAfterDelete`, `deleteTwice`,
`writeUnderDeleted`, `putTwice`, `safeGet` and `createTwice`. They are all on. It has no switch for
the group as a whole, because the plan's strategy that sends series is that switch. §1's
`sequences.pairs` was 9.3's, and 9.3 was not merged. Seventy-six settings in eight groups.

## Amendment (M8.5)

**Date:** 2026-09-29

**Six mutation settings are removed with the probes they switched**:
- `mutation.probes`;
- `mutation.oversizeWithNoLimit`, `mutation.emptyWithNoRule`, `mutation.deepNesting` and
  `mutation.extremeNumber`;
- `mutation.nestingDepth`.

[ADR-0027](0027-changing-one-thing-in-an-accepted-request.md)'s M8.5 amendment says why. Seventy
settings in eight groups.

## Amendment (M11.2)

**Date:** 2026-09-29

**The page §4 promised is [`docs/switches.md`](../switches.md).** It lists every switch with what
turning it off does, what leaving it on costs and what the screenings of 8.4 and 8.5 found it
worth. It carries, ready for `--settings`, the files that turn off everything one increment added,
and each milestone's levers at once. And it says what a run can do without that is not a switch,
because it belongs to the plan. `DocumentedSwitchesTest` holds the page to the tool:
- every switch is on the page, and every switch on the page exists;
- each is said to be on or off as it is by default;
- every setting named anywhere on the page exists;
- every file on it is one the command line reads, and a run handed it uses every value in it;
- every switch that is on by default is turned off by one of those files;
- each file does what its label says. The file for 10.3 turns off every series, the files for 10.1
  and 10.2 between them every kind of change and none twice, and a file for one increment only
  switches the table says came with it. The file for reach is its three increments' files put
  together, the file for break is the series' file with every change off, and the file for both is
  those two together;
- the file for getting the seed back turns off every change and every series. Handed over with the
  plan on the page, it leaves a run of the pet clinic with nothing it sends depending on what the
  API answered, where leaving either the changes or the series on would not;
- the plan on it is the shipped plan with the memory taken out.

**A switch is a setting that is true or false.** Twenty-six of the seventy are. The test takes the
kind of value as the definition rather than a list somebody keeps, so a yes-or-no setting added
later is on the page or the build fails. One of the twenty-six is not a lever:
`engine.followRedirects` decides what a redirection means rather than turning off something a run
could do without. The page says so in a table of its own, rather than the test making an exception.

**No increment gets a switch of its own.** Three of M10's increments take several lines to switch
off whole: the nine changes to one value of 10.1, the five of 10.2 (bodies of the wrong shape, and
numbers too wide for their format), and the six series of 10.3. `mutation.violations` switches off
10.1 and 10.2 together, and the M10.3 amendment gave the series no switch for the group because the
plan's strategy is one. A
switch per increment was considered and, on 29 September, not taken by the maintainer. It would
have meant three more settings for 12.1 to freeze, and every kind of change answering to three
switches rather than two. The page carries those files instead, which is what the screening of 8.5
had written by hand, and the test checks that each of those files switches off what its label
says.

**Taking the memory out stays a matter for the plan, and the page shows how.** §4 says that
switching off the memory of observed values is a plan without the `observed` line, not a setting.
Since M10 the shipped plan names that source in three strategies. Each time it sits in a group whose
weights add up to a hundred, so taking it out means three lines removed and three groups divided
again by hand. And a plan variant written before M10 has neither of M10's strategies, so reusing one
would switch M10 off along with the memory.

A shortcut was proposed and refused: `memory.longestReplyRead` at zero would silence the source in
one line. But a plan that still names a source that a setting has silenced says something about the
run that is not true. And a limit on the size of a reply is not a switch, whatever it is called.

So the page shows the shipped plan with the memory taken out and its weight shared among the rest in
the proportions they already had, and the test compares it with the shipped plan, so it changes
when that plan does. The comment on `MemorySettings` used to offer zero as the way an experiment
switches the memory off, and said, wrongly, that any of its five limits at zero would do it. It now
says what zero does for each limit, and that the plan is where the memory is taken away.
[ADR-0028](0028-sequences-over-things-a-run-creates.md), which repeated the claim, gains a note
correcting it.

**Three places were wrong about what gives the seed back.** The README, the campaign file's page and
the shipped plan's own comment said that taking `observed` out gives a run back its seed, and the
README added `mutation.violations`. Since 10.1 and 10.3, the changes to accepted requests and the
series cost the same promise, and the source is named in three strategies. All three places now say
so, and the page gives the plan and the file.

**Some levers are older than the rule.** The audit behind the page found every behaviour M9 and
M10 added behind its switch. It also found three older behaviours with no switch:
- a whole thing the API returned, reused with one value changed (2.5b);
- the preference for JSON that an `Accept` header states;
- lists written for one place, asked before lists written for a whole kind of value.

§5 says they move when the code around them is next touched. The page does not list them as
switches, because they are not.

## Amendment (M12.1b)

**Date:** 2026-09-30

**`schedule.interruptGrace`, two seconds, is how long a run stopped from outside waits for the
answers it is owed.** It is not a switch: a run always writes what it found when it is stopped, and
zero means it writes at once. The five seconds a stopped run is then given to write stay a constant,
under §5's test: nobody wants a different value for a wait that only runs out when writing is stuck,
and raising the grace raises the whole wait. [ADR-0015](0015-command-line-contract.md)'s M12.1b
amendment says why. Seventy-one settings in eight groups.

## Amendment (1 October 2026)

**Date:** 2026-10-01

**`engine.callTimeout`, one minute by default, is the longest one request may take, its whole reply
included.** The engine had a limit on connecting, on sending and on each wait for the next bytes of
a reply, and none on the whole. A reply that never ends - an endpoint that streams, or sends a byte
every few seconds - kept its place among the requests in flight for as long as the run lasted, and
enough of them stopped the run sending anything while it reported no idle time.

This bounds what such an endpoint costs; it does not avoid the cost. Each request to it still holds
its place for a whole `callTimeout`, and the engine keeps fewer requests in flight while replies are
that slow, so a run against an API with one such endpoint goes on sending, slowly, where before it
stopped. Sending less to an operation whose replies never end would be a decision about what to
send next, of the kind roadmap row 9.4 was set aside from, and is not taken here.

There were two defensible ways to choose its value: a fixed minute, refusing any wait inside a
request that is longer; or a value worked out from those waits. The second is taken, following the
M11.1 amendment's rule for where the engine starts: a default that would contradict a value somebody
named is worked out from it, rather than refusing the line they typed. Unnamed, `callTimeout` is
twice the longest of the connect, read and write timeouts - one minute by default, so a reply that
has started arriving gets as long again to finish - and raising `engine.readTimeout` for a slow API
raises it too. Named, it shortens the three waits nobody named to fit inside it, so
`--set engine.callTimeout=5s` is one line. A wait named outright that is longer than a
`callTimeout` named outright is refused, naming both. It is not a switch: a limit on how long a
request may take is not a behaviour an experiment turns off.

**A printed file leaves a worked-out setting to follow the others.** `--print-settings` writes every
value, so a printed file would name `callTimeout` outright, and raising `readTimeout` in it would be
refused - which breaks M11.1's own way of changing a setting: print, change a line, hand it back.
The same was already true of `initialConcurrency`: `maxConcurrency: 1` typed into a printed file was
refused because the file also said `initialConcurrency: 4`. Both are now written as comments while
nobody has given them and leaving them out gives the same value, and the file's header says what
such a line means. Handing the file straight back still changes nothing.

**The wait at the end of a run is unchanged**: the read timeout plus `schedule.stragglerGrace`,
which is what [ADR-0015](0015-command-line-contract.md) promises a run goes past its budget by. A
reply still arriving after that is counted as never answered, as before. Waiting `callTimeout`
instead would let a run with defaults go seventy seconds past its budget rather than forty, and
would still not cover requests waiting for their turn in the engine, which have not been sent.

**No wait can be longer than the HTTP engine can count to**, about 24 days, and is refused, naming
it, rather than ending the run as a failure of RESTest's own. Twice a long wait stops there.
Seventy-two settings in eight groups.

## Amendment (M9.5)

**Date:** 2026-10-02

**One row lands without a switch, by the maintainer's choice, and §4 says so.** Roadmap row 9.5
changes what the memory of observed values keeps: a value from a reply only under a name some
request of the document asks for, and, when `memory.mostNames` names are kept, a new one makes room
by letting go of the name heard of longest ago instead of being turned away. Its measurement added a
third change the maintainer approved the same day: a remembered word is only offered where it is
as long as the document allows, which is a check of what the document states, as the checks of a
value's kind and of a closed list already were ([ADR-0021](0021-how-a-request-body-is-built.md),
M9.5 amendment). Before it, the memory
kept every name a reply carried and refused every new one once full, so on flight-search the names
of the classes its diagnostic pages list took all two thousand places within three seconds and the
token a login returned was never kept.

§4 asks every behaviour a run can do without to ship with a switch, and this one could have had one.
The maintainer chose on 2 October to give it none: refusing new names once full was not a behaviour
anybody chose, and a switch would keep it available as though it had been. The cost is stated
where an ablation is planned ([the switches](../switches.md), *Some with no switch, by choice*): no
settings file brings back the memory as it was before 9.5, so a campaign run before it and one run
after differ in this as well as in whatever they were run to compare. `memory.mostNames` keeps its
name and its default, and says what happens when it is reached. Seventy-two settings in eight
groups.

## Amendment (M9.6)

**Date:** 2026-10-02

**Two settings for the kinds a name or a description implies.** `generation.impliedFormats` is the
switch §4 asks for, on by default. `generation.impliedFormatChance`, a half by default, is how often
an invented word takes the implied kind where one is implied; it is a number, not a way of
switching the row off, and the page of switches says so. The rule table itself is not a setting:
which names and descriptions imply what is a fact the corpus measures, kept in one class and pinned
by a test, not a decision somebody tunes per run
([ADR-0022](0022-the-characters-a-value-is-made-of.md), M9.6 amendment). Seventy-four settings in
eight groups.

## Amendment (M9.7)

**Date:** 2026-10-02

**`memory.rememberAcceptedRequests`, on by default, is 9.7's switch**: off, the memory of observed
values learns from replies alone, as it did before 9.7. Seventy-five settings in eight groups.

## Amendment (M10.4-M10.7)

**Date:** 2026-10-02

**`mutation.breakAFormat`, on by default, is 10.6's switch.** Rows 10.4, 10.5 and 10.7 land with no
switch of their own, by the maintainer's choice, and §4 says so here as it did at 9.5: each widens a
kind of change that already has one - `mutation.wrongType`, `mutation.notJson`,
`mutation.beyondItsWidth` - or the list of awkward values, which a plan does without by leaving out
the strategy that pushes. What cannot be done without a code change is to run the narrower kinds
alone; [the switches](../switches.md), *Some with no switch, by choice*, says so where an ablation is
planned. `mutation.oversizedLength` now also says how many digits the number far past a format's
width has. Seventy-six settings in eight groups.

## Amendment (M9.8-M9.9)

**Date:** 2026-10-03

**Three switches, all on by default.** `memory.pluralIdentifiers` and `memory.namesByResource` are
9.8's: off, a gap named for several identifiers (`{ids}`) or for a thing's name (`{productName}`)
takes only a value carrying its own name, and a reply that is a plain list of words is not kept as
things, as before 9.8. Both sit under `memory.*` beside `memory.identifiersByResource`, because they
widen what the memory of things by kind keeps and answers, and both change nothing while it is off;
their descriptions say so. `generation.omitHalProperties` is 9.9's: off, a body is sent with
`_links` and `_embedded` when it was built with them, as before 9.9. It is under `generation.*`
because it decides what a body the tool builds may carry, whatever source filled it. Which names
are written like several identifiers or like a thing's name, and which two names HAL keeps for the
server, are facts about spelling and about HAL rather than numbers anybody tunes, so they stay in the
code ([ADR-0021](0021-how-a-request-body-is-built.md), M9.8 and M9.9 amendments). Seventy-nine
settings in eight groups.

## Amendment (2.1, the kinds a name implies)

**Date:** 2026-10-04

**The five rules 2.1 adds to the table of kinds a name implies ride `generation.impliedFormats`,
with no switch of their own.** The table is still not a setting, as the M9.6 amendment says, though
one of the new rules, a bare `name`, is kept below the bar the corpus sets by the maintainer's choice
rather than as the fact the corpus measures; that choice is recorded where the rule is
([ADR-0022](0022-the-characters-a-value-is-made-of.md), 2.1 amendment) and on the page of switches.
A campaign that wants the new rules alone apart from the old compares two versions of RESTest. No
setting is added.

## Amendment (2.1, settings for a body that makes something)

**Date:** 2026-10-04

**Two settings for filling a body that makes something afresh.** `generation.freshWhereMade` is the
switch §4 asks for, on by default. `generation.freshWhereMadeChance`, three in ten by default, is how
often the body of a `POST` is filled afresh, decided once for each body; it is a number, not a way of
switching the row off, and the page of switches says so. Which names are written like identifiers,
and so keep what the API returned, is a fact about spelling and stays in the code, as it did at M9.8.
[ADR-0020](0020-what-a-dictionary-is.md)'s 2.1 amendment says what the switch does and why.

**`generation.optionalPropertyChance` is 0.8 by default, and `generation.optionalBodyChance` 0.9.**
Both were a half, the number nobody had to argue for, and a half was costly where a body makes
something. Documents under-declare `required`, so a property an API needs was left out of half the
bodies; and a Swagger 2 body is optional unless the document says otherwise, though almost no API
accepts a request without it, so half the requests to such an operation went without one. With
both set back to 0.5, a run includes optional properties and bodies as often as 2.0 did, so the two
can be compared without a code change.

One consequence reaches beyond bodies. With `generation.optionalParametersBySize` on, which is the
default, `optionalBodyChance` decides only whether an optional body is sent. Switched off, it is also
the coin every optional parameter is decided on, so the file that switches 2.9 off now sends each
optional parameter nine times in ten where it sent it half the time. The page of switches says so,
and a coin of its own for optional parameters, which the roadmap lists among the smaller things
2.0 left, would separate the two. Eighty-one settings in eight groups.

`GenerationSettings`, the record that carries these settings in Java, gains the two new components,
so code that builds one by hand has to add them. What RESTest keeps from one minor version to the
next is its command line and its files ([ADR-0015](0015-command-line-contract.md)), not the shape
of this record.

## Amendment (12 October 2026): a switch for each check on a reply

**Date:** 2026-10-12

**A ninth group, `checks.*`, holds a switch for each check a run makes on what the API sends back.**
§4 asks every behaviour a run can do without to ship with a switch, and the two checks that judge
replies were older than that rule and had none: a person whose API's document describes its error
replies badly saw the same faults on every run and could stop them only by not running RESTest.
`checks.internalServerError` turns off the check that reports a reply of 500 (`F100`), and
`checks.replyOffSchema` the check that reports a reply whose body is not the shape the document gives
(`F200`). Both are on by default.

Three things were decided with them.
- **The group is named for what a developer calls these, not for the catalogue's numbers or the
  word testers use.** A developer reads "checks" without explanation; "oracles" would have needed
  one, and a switch per catalogue number says nothing to somebody who does not know the
  catalogue. The number of each kind stands beside its switch on the page of
  switches and in the description `--print-settings` writes.
- **Each switch is named for what is wrong with a reply it reports**, which is also the rule for any
  check added after them. `internalServerError` rather than `serverError`, because the catalogue has
  a kind of its own for a 5xx other than 500, and a switch called `serverError` would read as
  covering it.
- **A check switched off is not made at all**, rather than made and its faults dropped, so that
  turning off the one that takes time saves the time it takes.

No check decides what a run sends: switching one off changes which replies are called faults. It
can still change what is sent, indirectly. The checks are made on the one thread that delivers what
a run announces, the same thread that hands each reply to the memory of what the API returned, and
a run waits when that thread falls behind. So with the check of a reply's shape off, a run may send
more requests in the same budget, and learn from a reply sooner. Only a run that has [its seed
back](../switches.md#getting-the-seed-back) builds the same requests whichever checks are made.
`serverErrors` in `report.json` counts what the API answered whichever checks are made, as
[ADR-0016](0016-how-a-fault-is-presented.md) already says. And since a check switched off reports no
fault, it can turn a run's `1` into a `0`: the exit code says what was found among the kinds of fault
whose check was on. Like every setting, a switch is recorded in `report.json`'s `settings`, with its
value and where it came from, which is how a report says which checks its run made; the maintainer
chose on 12 October 2026 not to add a line on the screen of its own. A switch is for the whole run,
never for one operation, and [the known limitations](../known-limitations.md#what-a-run-says) say so.
Eighty-three settings in nine groups.

## Amendment (12 October 2026): two older behaviours with no switch, not three

**Date:** 2026-10-12

The M11.2 amendment lists three older behaviours with no switch. The third, lists written for one
place asked before lists written for a whole kind of value, is gone: since
[ADR-0033](0033-a-dictionary-file-is-written-by-operation-and-place.md) every list handed over is
written for places of one API, and the lists are asked in the order they were handed over. Two are
left, and the page of switches says so.

## Amendment (13 October 2026): a run of a known size, and how long a stopped run may write

**Date:** 2026-10-13

Four settings join the `schedule` group. `stopAfterRequests` (off) and `requestsBeforeStopping`
(1000) let a run be given a size rather than only a length: on, it stops asking for more once it has
sent that many requests, waits for their answers as it does at a deadline, and ends. A run of a
known size sends the same requests on a slow machine as on a fast one, which is what RESTest's own
tests of the command line need: two in three of them ran for a second or two of the clock, and the
slowest machines continuous integration runs on spent that second starting up and answered `3`,
"nothing was tested". A pair rather than one number with zero for "off", by the rule given with the
switches: a number set to zero says nothing about switching.

`interruptWritingGrace` (5 s) and `interruptLookingGrace` (1 s) were two constants in the command:
how long a run stopped from outside may spend writing what it found once it has stopped waiting for
its answers, and how long it then gives its directory to say what was written. They are decisions,
not facts - the sum, with `interruptGrace`, has to fit inside the ten seconds a stopped container
is given - and §5 says such constants move when the code around them is next touched. It was, so
that a test of a run that cannot finish writing could set the first to a second and the second to
ten: the test took eight seconds and failed on a slow Windows machine whose directory did not
answer within the second it was given; it now takes one and does not depend on the machine.

Eighty-seven settings in nine groups.

## Amendment (14 October 2026): six numbers that were decisions, moved

**Date:** 2026-10-14

An audit of the code on 13 October 2026 found a dozen tuning numbers kept as constants in files
that had been touched since §5 said such numbers move to the settings when the code around them is
next touched. Six move here.

Two were second copies of a setting that already existed. The source that offers what the API
returned searched a value for something to change as deep as six, a constant, while
`memory.asDeepAsAReplyIsRead` says how deep a reply is read, six by default: raise the setting to
ten and what the memory kept at depth seven to ten was thrown away by the source that offers it.
That source now reads the setting. The builder of words to a pattern declined any pattern whose
shortest match passed ten thousand characters, a constant, while `generation.longestString` says how
long a word may be, ten thousand by default, and the inventor of words honours it; the builder is
now handed the setting.

Four become settings, each with the value the constant had, so that no run changes:

- `generation.changedValueAttempts` (4): how many times a fresh value is asked for, to change one
  value in a thing the API returned, beside the three other counts of attempts in that group;
- `generation.usualRepetitions` (8): how far a repetition with no stated end runs in a word built to
  a pattern, beside `usualLongestString` and `usualMostItems`;
- `report.detailsShownPerFault` (10) and `report.commonestStatusCodesListed` (10): how many of a
  fault's particulars the console prints under it, and how many status codes `report.json` lists
  as the commonest, beside `faultsShownOnTheConsole` and `skippedOperationsShownOnTheConsole`,
  which were made settings when they were written.

What stays, and why. The builder of words to a pattern keeps five numbers that bound the work it
hands a library - how many candidates it draws, how many characters it builds before giving up -
which are safeguards against a pattern that would otherwise run for ever, not choices about what is
sent. The rest are in files this change does not touch, and move with the next change to each: the
weight of one answer in the engine's running average, the page size and the batch of the stored run
(which takes no settings at all), the half-minute the event stream waits for the reports at the end
of a run, the depth to which the schema oracle follows references, the depth and budget of the
schema converter's combinations, and the lengths of the passwords, user names and names a name
implies.

Ninety-one settings in nine groups.

## Amendment (15 October 2026): two more checks

**Date:** 2026-10-15

`checks.otherServerError` turns off the check that reports a server error other than 500 and 501
(`F121`), and `checks.invalidDataAccepted` the check that reports a request broken on purpose that
the API accepted (`F206`). Both are on by default and named, as the 12 October amendment asks, for
what is wrong with a reply each reports; `otherServerError` reads beside `internalServerError`, which
was named so that it would not seem to cover the other 5xx. The group lists its checks in the order
of the catalogue's numbers. Ninety-three settings in nine groups.

## Amendment (17 October 2026): a check on a reply that never came

**Date:** 2026-10-17

`checks.noReplyInTime` turns off the check that reports a request sent on a connection open to the
API that got no reply before RESTest stopped waiting (`F312`). It is on by default, decided by the
maintainer on 15 October 2026.

The 12 October amendment's rule names each switch "for what is wrong with a reply it reports", and
this check has no reply to judge. The rule is read as naming a switch for what is wrong with what
came back, or did not: `noReplyInTime` says what the check reports, as `invalidDataAccepted` does,
and not the engine's limit it depends on, which is `engine.readTimeout`, `engine.writeTimeout` or
`engine.callTimeout`, whichever runs out first. How long the engine waits stays where it was, among the engine's
settings; this switch only decides whether a wait that ran out is called a fault. The group keeps the
catalogue's order. Ninety-four settings in nine groups.

## Amendment (17 October 2026): three checks on what the document declares

**Date:** 2026-10-17

`checks.notImplemented` turns off the check that reports a reply of 501 to an operation the document
declares (`F205`); `checks.undeclaredStatusCode` the check that reports a reply whose status code,
below 500, the document does not declare for its operation (`F920`); and
`checks.undeclaredContentType` the check that reports a reply with a body whose content type the
document does not declare for its status code (`F921`). All three are on by default, decided by the
maintainer on 17 October 2026, and named for what is wrong with what came back, as the amendment of
12 October asks. The group keeps the order of the numbers of what its checks report, the catalogue's
first and then RESTest's own ([ADR-0036](0036-kinds-of-fault-restest-numbers-itself.md)), so
`notImplemented` sits between `replyOffSchema` and `invalidDataAccepted`. Ninety-seven settings in
nine groups.
