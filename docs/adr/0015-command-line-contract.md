# ADR-0015: One command, a time budget spent in full, and an exit code that means something

**Status:** Accepted, amended at M1.7 and M1.7c
**Date:** 2026-09-14 (amended 2026-09-15)

## Context

Every part of a run exists and is tested on its own: the parser (M1.2), the HTTP engine (M1.3), the
interaction store (M1.4), the generator (M1.5), the oracles and the reports (M1.6). Nothing joins
them. The only place the whole pipeline is assembled is a *test*,
`restest-cli/src/test/java/io/restest/cli/WholeRunTest.java`, and its own comment says as much:
"This is the shape the `restest run` command will take."

M1.7 turns that shape into the product. Writing the command means answering questions the repository
has never answered, and three of them are expensive to get wrong because other things will be built
on top of them: the CI job added in this same increment, the evaluation entry point of M1.8, and any
pipeline a user wires RESTest into.

**What is already settled elsewhere.** ADR-0013's consequences place the loop in the command-line
module, "so that generation never gains a dependency on the engine or the store", and argue against
making that loop sequential. ADR-0009 defines idle time as "the fraction of the budget during which
no request was in flight". ADR-0005 says a test case cut off by the budget produces no `Interaction`,
because inventing one would mean fabricating the moment it was sent. ADR-0011 describes `--budget` as
"to stop after a given time" and expects each invocation to exit so a harness can loop.

**What is not settled anywhere.** What `--budget 30s` means when the document takes two seconds to
read. Whether the budget is spent in full or merely acts as a ceiling. What the command returns.
Where a run writes its files. What happens to requests still in flight when the deadline passes. A
search of the whole repository for an exit-code convention finds one thing only: the architecture
rule naming which module is allowed to end the process. Not what value it may end with.

## Decision

### The surface

```
restest run [--url=<base>] [--budget=<duration>] [--seed=<n>] [--out=<dir>] <spec>
```

`<spec>` is a file path, a URL or a classpath resource — whatever the parser accepts, which is the
same set in all three cases and never throws. `run` is the only sub-command. `recheck`, `explain` and
`replay` arrive in later increments beside it, sharing this option vocabulary and these exit codes.

`--budget` defaults to **60 seconds** and accepts `500ms`, `30s`, `5m`, `2h`, a bare number read as
seconds, and ISO-8601 (`PT1M30S`). It has a default because the first design principle is zero
configuration to start, and the tool's own zero-configuration example passes no budget.

`--url` defaults to the first server the document declares that is absolute and has a host. A
document with no `servers:` block is given `/` by the parser, which is not an address anything can be
sent to; that is refused with a message asking for `--url`, rather than assembled into a request the
engine will reject much later.

`--out` defaults to `restest-out/`, and a run writes ~~two files~~ its files into it: `report.json`
and — **amended at M1.7: only when `--store` asks for it** — `run.sqlite`, the latter accompanied,
while it is open, by the two working files SQLite keeps beside it. One directory rather than one flag per artefact, so later report formats need no new option. A
previous run in the same directory is replaced, working files included, because a fresh database next
to another run's leftovers is a database that may not open.

A directory nothing can be written to is not a malfunction and does not answer 4: it is one of the
ways a run cannot start, and it answers 3 with a sentence naming the directory.

### The budget is the whole invocation, reading the document included

The engine is built **before** the document is parsed, so the clock that measures idle time and the
clock that measures the budget start at the same moment and cover the same window.

This is the part somebody will want to change later, so the reasoning is written down. Idle time
exists to catch a tool that spends its budget computing instead of testing — the failure that put
RESTest 1.x mid-field in 2026. Time spent reading a four-thousand-line document is time not spent
testing. Starting the clock after the parse would move that time outside the measurement and report
such a run as perfectly efficient, which is precisely the answer the metric was invented to prevent.
It would also hide the constraint solving that arrives in M5.4, where the up-front cost is real.

The cost is that a small budget against a large document reads as a high idle percentage. That number
is true, and it is the number that creates pressure to make the parse faster.

### The budget is spent, not merely respected

The run cycles the testable operations until the deadline rather than stopping after one pass. Every
pass draws fresh random values, so a repeat is a different test. A run that stopped after 200
milliseconds would make "30% of it idle" meaningless, and would score near zero on the
area-under-the-curve measures this project is compared against.

This is a `for` loop and a queue. It is not a scheduler: no weights, no phases, no strategy
selection. Those need the vocabulary ADR-0013 reserves for M2, and inventing them here would pre-empt
that decision with an accident.

Requests are sent asynchronously, with the work in flight bounded at twice the engine's own
concurrency ceiling, so the engine's limiter is always the binding constraint and never runs short
while the next test case is being built. Generation stays on one thread, because one generator is one
sequence of decisions and is not safe to share.

The loop also pauses when the announcements it has made are further ahead of the listeners than a
fixed bound. Against an API that answers in three milliseconds the engine can produce interactions
faster than judging, storing and reporting them consumes, and an unbounded queue would grow until the
run ran out of memory. Pausing shows up as idle time, which is the honest place for it: the tool was
not testing, and it was our own work that stopped it.

### At the deadline, generation stops and what was sent is waited for

Nothing in flight is cancelled. Those requests were paid for and their answers are evidence; ADR-0005
forbids inventing an interaction for a test case that never went out, not discarding one that did.

The consequence is stated rather than hidden: a run may exceed its budget while draining, by at most
the engine's read timeout and a little. A run whose budget is smaller than a single slow answer will
overshoot, and that is the correct behaviour for a tool whose job is to report what the API did. Past
that, it stops waiting and says how many answers it never got, rather than hanging.

**Answers are dealt with as they arrive, not in the order the requests went out.** This is the part
that is easy to get subtly wrong and was got wrong first. Waiting for the oldest outstanding answer
before sending anything else means one slow operation stops the whole run — and, far worse, the run
still looks efficient, because there is always exactly one request in flight and "nothing in flight"
is how waste is measured. Measured on a four-operation stub where one operation slept: 46 requests in
what should have been a thirty-second run, reported as 1% idle.

**What the reports keep is bounded; what is counted is not.** A run that spends its whole budget
against a broken API finds faults by the hundred thousand, each carrying the attempt that produced
it. The screen stops printing them past fifty and says so; ~~the JSON report writes the first thousand
in full and records how many it wrote alongside the true total~~ **amended at M1.7: the first
thousand turned out to be a thousand copies of the same two faults, so the report's allowance is per
operation and kind instead — see ADR-0006's amendment**; the announcements waiting to reach
either are capped, and the loop pauses rather than letting them pile up. Every one of those caps is
stated in the output it applies to. The stored run keeps everything, which is its job — and how big
that gets is a question this decision deliberately leaves open. **Answered at M1.7: 661 MiB at the
default budget, which is why keeping it is now asked for rather than assumed. See the amendment
below and ADR-0006's.**

### Exit codes

| Code | Meaning |
|---|---|
| `0` | The run finished. No fault was found. |
| `1` | The run finished. At least one fault was found. |
| `2` | The command line was wrong. |
| `3` | Nothing could be tested: the document yielded no testable operation, no usable base address, or **amended at M1.7c: nowhere to write the results**. |
| `4` | RESTest itself malfunctioned — an unexpected failure, a listener that threw, or announcements that never reached one. |

`127` is never returned by the program. It is reserved by the launcher script for "this checkout has
not been built yet", so a problem with the wrapper can never be mistaken for an answer from the tool.

Faults found is `1` rather than `0` because the common case is a pipeline that should go red when the
API under test is broken, and because a tool that has to be asked twice — once to run, once to find
out whether it found anything — is one step more than anybody wants in a shell script. A reader who
disagrees should note the escape hatch reserved rather than built: `--fail-on`, deliberately not
implemented here, because per-run configuration belongs to the increment that owns configuration.

An operation that had to be skipped is **not** an error. The second design principle makes skipping
normal, and two of the five specifications this tool is evaluated against already read incompletely.
A run reports what it skipped and carries on; only a document that yields nothing at all is `3`.

### The launcher is a shortcut, not distribution

A `restest` script at the root of the checkout runs the built jars on the classpath. Packaging —
Maven Central, Homebrew, a container image, a native binary — belongs to M7 and replaces it. Two
details are load-bearing rather than incidental: the class path rather than the module path, because
two versions of the parser module cannot coexist on a module path; and the built jars rather than the
compiled-classes directories, because a report says which version of RESTest produced it by reading a
jar manifest, and a directory has none.

## Consequences

- One command does the whole thing, which is what the walking skeleton was for. Everything after this
  is an improvement to something a person can already run.
- The evaluation harness of M1.8 has the interface it was promised: point at a document, point at a
  deployment, stop after a time, exit.
- Idle time becomes a number about a real run rather than about a test. It will read higher than a
  measurement that excluded parsing would, and that is the intended trade.
- The exit code is now a compatibility surface. Changing what `1` means later breaks other people's
  pipelines, which is why it is written here rather than inferred from the code.
- A budget spent in full means a run produces far more interactions than a single pass would, and the
  stored file grows accordingly. That is the cost of a metric that means something.
- The overshoot while draining is bounded but real, and anybody timing runs to the second needs to
  know it.

## Alternatives considered

- **Start the budget after the document is read.** Kinder numbers, dishonest ones. It excludes from
  the measurement exactly the work the measurement exists to catch.
- **Stop after one test case per operation.** Simpler, and it makes `--budget` decorative. It also
  makes every run against a small API finish instantly, which is not testing, it is sampling.
- **Exit `0` whenever the run completed.** Defensible — finding a fault is the tool working, not
  failing — and it is how some linters behave. Rejected because the majority use is a gate, and
  because the alternative can be recovered with one flag later while this direction could not.
- **A shaded, executable jar.** One file to run, at the cost of a second copy of every class in a jar
  the architecture rules read, which is a failure mode the rules' own harness exists to catch.
- **Cancel what is in flight at the deadline.** Exact timing, thrown-away evidence, and interactions
  that end as "interrupted" for reasons that have nothing to do with the API.

## Amendment (M1.7)

**Date:** 2026-09-15

**`--store` asks for the run to be kept, and is off by default. The output directory belongs to one
run: every file RESTest itself writes there is deleted at the start of every run, whether or not that
run keeps anything.**

ADR-0006's own M1.7 amendment decides *whether* a run is kept and why, on the measurements. This one
decides only what that looks like from the command line, which is this ADR's job.

### Why

The surface above says a run writes `report.json` and `run.sqlite` into `--out`. Measured, the second
of those is 661 MiB at the default budget and 98.5% of everything a run writes, and no part of an
ordinary run reads it back: `recheck`, `replay` and corpus oracles are all commands somebody runs
afterwards, on purpose. Keeping it by default charges every run for a command nobody ran.

Two questions follow, and the second is the one that is easy to get wrong.

**What is it called and which way round is the default?** Off, asked for by name. This is a departure
from the first design principle as it is usually read — zero configuration to start — so it is worth
being exact about which reading. The principle is that the tool needs no configuration to *do its
job*, and its job is to find faults and report them, which a default run still does, faster. Keeping
the evidence is not the job; it is a second thing some people want afterwards, and that is what a
flag is for. The tool's zero-configuration example still passes nothing.

**What happens to what an earlier run left behind?** Every file RESTest writes is deleted at the
start of every run that gets as far as testing something — the stored run included, and including
when this run will not write one. A run that cannot start at all, because the document yields no
testable operation or no usable address, never reaches the output directory and leaves it alone;
destroying an earlier run's evidence on the way to answering "there was nothing to do" would be the
worst of both. The
alternative — delete only what *this* run is about to write — was considered and rejected. It sounds
safer and is worse: it leaves a directory holding one run's database beside another run's report, so
`restest recheck restest-out/run.sqlite` would re-examine a different run from the one the report
beside it describes. Two runs' artefacts in one directory, telling a consistent-looking story that is
not true, is a worse failure than a deleted file.

Note the exact scope, because the looser reading is dangerous: what is deleted is **the set of files
this tool writes**, by name, not the contents of the directory. `--out` accepts any path a person
cares to type, and a run pointed at a directory full of someone's work must remove its own leftovers
from it and nothing else.

That makes the rule one sentence a person can hold in their head — **a directory is one run** — and
puts the escape hatch where it already is: `--out` names the directory, so a run worth keeping is
given its own, or copied somewhere else afterwards. The cost is stated rather than hidden: running
`restest run` again in the same directory deletes the stored run you kept last time, and it does so
whether or not the new run keeps anything.

### How

```
restest run [--url=<base>] [--budget=<duration>] [--seed=<n>] [--out=<dir>] [--store] <spec>
```

- **`--store`** keeps the run's interactions in `run.sqlite`. Off by default. Without it no database
  file is opened and none is written; the run is otherwise identical, and its report says the same
  things.
- **`--out`** is unchanged: one directory, `restest-out/` by default, holding everything a run
  writes. **No per-artefact path options.** This is the point of the original decision — "so later
  report formats need no new option" — and M3.5 adds four formats. Of the tools we are measured
  against, CATS takes exactly this shape (`-o`, into `cats-report`), and Schemathesis's per-format
  path flags sit on top of a `--report-dir` default rather than replacing it, so that door stays open
  and is compatible to walk through later. EvoMaster is the counter-example worth not following: its
  statistics files escaped `outputFolder` and are now five independent path options.
- **Every file RESTest writes into that directory is deleted at the start of every run** —
  `run.sqlite` and its two working files included — whatever this run intends to write. Files the
  tool did not write are not touched.
- **A run says what it wrote**, and how large it is. When nothing was kept, it says that too, and
  names the flag — a person who wanted the evidence should find that out in the run that did not keep
  it, not the next day.

### Consequences

- A default run is materially faster for having been asked to do less: measured, 51% more requests in
  the same budget, with idle falling from 18.5% to 0.3%. The details and the method are in ADR-0006's
  M1.7 amendment.
- `--store` is a compatibility surface from now on, like the exit codes. Adding a flag later is
  cheap; changing which way round this one defaults is not.
- The evaluation entry point of M1.8 gets a simpler job than it looked: a loop that does not pass
  `--store` writes nothing, goes faster, and cannot fill a disk during a campaign. One that wants
  evidence passes the flag and gives each invocation its own `--out`.
- Anyone who kept a run and then runs again in the same directory loses it. This is the deliberate
  price of "a directory is one run", and the run says what it deleted.
- **A coupling M3.5 must not miss.** The original decision's reason for one directory was "so later
  report formats need no new option", and that still holds for the *option*. It does not hold for the
  deletion: a format that writes `report.html` and is not in the set of files a run clears will leave
  last run's HTML beside this run's JSON, which is the two-runs-in-one-directory failure arriving by
  the back door. What ships at M1.7b is a list of names in one place, with a comment saying so — a
  convention, not a guarantee, and nothing fails when the next format forgets. That is adequate for
  two files and will not be adequate for six, so **M3.5 owns making the set impossible to drift**:
  each format naming the file it writes, and the run clearing what the formats name. Recorded here
  rather than left to be discovered by a reader of a stale `report.html`.
- A surprising result in a run that kept nothing has to be reproduced by running again with `--store`
  and the same seed. That works today. ADR-0013's seventh decision means it stops working at M2.7 for
  strategies with a memory, which is recorded in ADR-0006's amendment as M2.7's problem to solve.

### Alternatives considered

- **`--store` on by default, `--no-store` to opt out.** Kinder to the person who wanted the evidence
  and forgot; charges 850 MiB to everyone else, including every CI run and every evaluation
  invocation. The failure mode of the chosen direction is a run you have to repeat; the failure mode
  of this one is a full disk in the middle of a campaign.
- **Delete only what this run writes.** Preserves a kept run across a later default run, at the price
  of a directory that holds two runs and looks like one.
- **A path per artefact (`--report-json-path`, `--store-path`).** What Dredd and EvoMaster do. Six
  more options by the end of M3.5, and Dredd's variant pairs two repeatable flags by position, which
  is easy to get wrong and hard to diagnose.
- **A timestamped subdirectory per run by default**, as CATS offers opt-in with
  `--timestampReports`, cleaning only when a directory is named. Weighed seriously, because it
  removes the deletion problem completely and makes "a directory is one run" true without deleting
  anything. It lost on arithmetic: with no retention policy it is not a policy but a leak. A default
  run writes 1.8 MiB, so a hundred of them cost 180 MiB and nobody would notice; a run with `--store`
  writes 850 MiB, so ten of them cost 8.5 GB and nothing prunes them. That turns the cost of
  `--store` from "850 MiB, replaced each run" into "850 MiB for every run ever made", which is worse
  than the problem the default was changed to solve. It becomes a good idea again the day it comes
  with a bound — keep the last N, prune the rest, say so — and that bound is the decision to make
  then, not the subdirectory.

---

## Amendment (M1.7c)

**Date:** 2026-09-15

**`3` also means there is nowhere to write the results. What Ctrl-C should do is left open,
deliberately.**

### Why

The table above gives `3` two meanings: no testable operation, or no usable base address. The prose
under "The surface" already gave it a third — "A directory nothing can be written to is not a
malfunction and does not answer 4" — and the code did not implement it. An unwritable `--out` was
discovered only when the report came to be written, which surfaced as a listener that threw, which
answered `4` and printed a stack trace. Whoever read that went looking for a bug in RESTest, and the
answer was that they had pointed it at a read-only directory.

The check now happens before anything is tested, and the table says what the prose already said.
This is recorded rather than left to be inferred because the exit code is a compatibility surface:
other people's scripts branch on it, and widening the meaning of a number without writing it down is
how a contract stops being one.

### What is deliberately still open

**Interrupting a run.** Ctrl-C today kills the process: no summary, no `report.json`, and with
`--store` a database left with its two working files beside it. That is not a decision anybody took;
it is the absence of one, and it is written here so that it stays visible rather than being
rediscovered.

It is not settled in this amendment because it has more than one defensible answer, and picking one
silently in the code is exactly what this repository's ADRs exist to prevent:

- Write what was found so far and answer as usual, which makes a partial report look like a finished
  one unless it says otherwise.
- Write nothing and answer with the conventional code for an interrupted program, which throws away
  evidence that was already paid for — the thing the drain at the deadline exists to avoid.
- Close the store cleanly and write nothing else, which keeps a run inspectable but leaves whoever
  interrupted it with no summary.

Whichever is chosen also has to work when the interrupt arrives during the drain, and has to not
promise files it did not finish writing. That is an increment with an ADR of its own, not a hook
added in passing.

