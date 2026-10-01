# ADR-0015: One command, a time budget spent in full, and an exit code that means something

**Status:** Accepted, amended at M1.7, M1.8, M2.7a, M2.10a, M11.1, M9.1, M11.3, M12.1a, M12.1c and M12.1b, in #314 and #344, and on 1 October 2026
**Date:** 2026-09-14 (amended 2026-09-15, 2026-09-18, 2026-09-22, 2026-09-23, 2026-09-30)

## Context

Every part of a run exists and is tested on its own: the parser (M1.2), the HTTP engine (M1.3), the
interaction store (M1.4), the generator (M1.5), the oracles and the reports (M1.6). Nothing joins
them. The only place the whole pipeline is assembled is a *test*,
`restest-cli/src/test/java/io/restest/cli/WholeRunTest.java`, and its own comment says as much:
"This is the shape the `restest run` command will take."

M1.7 turns that shape into the product. Writing the command means answering questions the repository
has never answered, and three of them are expensive to get wrong because other things will be built
on top of them: the CI job added in this same increment, the evaluation entry point of M1.9, and any
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
engine will reject much later. **Amended later: `--url` names the machine, and an address given
without a path of its own keeps the directory the document declares — see the last amendment, which
also says what that cost before it was fixed.**

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
| `3` | Nothing could be tested: the document yielded no testable operation, no usable base address, or nowhere to write the results. |
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
- The evaluation harness of M1.9 has the interface it was promised: point at a document, point at a
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
- The evaluation entry point of M1.9 gets a simpler job than it looked: a loop that does not pass
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

## Amendment (M1.8)

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

The check now happens before anything is tested, and the table row says what the prose already said.
This is recorded rather than left to be inferred because the exit code is a compatibility surface:
other people's scripts branch on it, and widening the meaning of a number without writing it down is
how a contract stops being one.

The check asks the file system whether the directory is writable, which is an answer that can be
wrong in the permissive direction: a Windows directory carrying the read-only attribute, or mode 555
under a user who may write anywhere regardless, both report themselves as writable. Those runs still
fail later and still answer 4. What this buys is the ordinary case - somebody pointing the tool at a
directory they cannot write to - answered properly, not a guarantee that answer 4 is now
unreachable.

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
added in passing, and it is **M3.7** in the roadmap - after the reports increment, because what a
run cut short should leave behind is a question about what a run writes.

**Answered at M12.1b: the first answer's evidence, said to be a run cut short, with the second
answer's number, Java's `130` or `143` — see that amendment.**



## Amendment (M2.7a)

**Date:** 2026-09-18

**Two options: `--dictionary`, repeatable, and `--fuzzing`.**

`--dictionary <file-or-directory>` names a list of values to send, and may be repeated; a directory
contributes every `.yaml`, `.yml` and `.json` in it, in name order. Values good enough to be worth
keeping belong beside the specification they were worked out for, and until now there was no way to
hand them to a run. The format is in `docs/dictionary-format.md`.

`--fuzzing <percentage>` says how much of the time goes on requests built from values chosen to be
awkward. It defaults to 25 and `--fuzzing 0` sends none.

The second one is here because writing the test that compares a run with and without such values
showed it had to be: there was no way to express "do not send those", and a capability a user cannot
turn off is one they cannot manage. Design principle 1 is "zero configuration to start; full
configuration available", and a tool that deliberately sends bad data to somebody's API should be
able to be told not to.

Neither changes an exit code, and neither is required. A run with no options at all behaves as it did
except that a quarter of its requests now carry awkward values, which is the increment.

## Amendment (fixing what `--url` throws away)

**Date:** 2026-09-18

**`--url` says which machine, not which directory. An address given without a path of its own keeps
the path the document declares; an address given with one replaces it entirely.**

### Why

"The surface" above says `--url` "defaults to the first server the document declares that is absolute
and has a host", and says nothing about what happens to that server when somebody does pass the
option. The code answered the question by accident: the address given replaced the declared one
whole, path included.

For most documents that is invisible, because most declare a server with no path. For the ones that
do declare a path it loses the API completely. Measured, rather than imagined: a five-minute run
against the `pet-clinic` API of the 2027 REST League, which declares
`http://localhost:9966/petclinic/api` and is served on a port the harness chooses. Every request went
to `/pets` rather than `/petclinic/api/pets`. 43,549 requests, 43,549 answers of 404, zero operations
covered, 3.5% of the code reached - and a summary reading "no faults found", which was true and
worthless. One of the five APIs the tool is evaluated on scored nothing at all, and nothing in the
output said why.

The rule this amendment adopts is the one a person would expect. Somebody who says the API has moved
to another machine is telling us the machine. They are not saying the directory it is served from has
gone away — and if they were, they could say so, because an address with a path in it still replaces
the declared one.

### How

`BaseAddress` decides it, before a single request is assembled, which is where the rest of this
decision's address checking already lives.

An address is treated as naming no directory when its path is empty or is nothing but slashes. A
trailing slash is somebody finishing an address, not a claim that the API sits at the very root of the
server, so `http://host:8080/` keeps the declared directory exactly as `http://host:8080` does — and
neither does a second slash change the answer, because a rule whose result depends on a typing habit
is not a rule.

Which directory is three readings of the document, in this order.

**If the document describes the very machine it was pointed at, it has already answered** — same
protocol, same host, same port, with a port left unwritten counting as the one its protocol implies.
A document that lists `https://api.example.com/v2` and then `http://localhost:8080` is saying
something precise about each, and somebody testing the local one should not have production's
directory bolted on. This reading includes the answer "no directory".

It sees only addresses that can be read at all, as every reading below does, so an address this
decision would refuse cannot settle anything by being listed first — and it can only recognise a
machine in an address written out whole. One carrying a blank the document never filled in,
`https://{customer}.example.com/v2`, or anything else no parser will take, names no machine that
could be compared; it is matched by nothing, and its directory is used only if no other reading
answers.

No document in the corpus this tool is measured against declares more than one server, so this
reading changes nothing there. It is for documents in the wild, which routinely list production
beside a local address, and it is written down because the alternative was to leave the ordering of
somebody else's `servers:` block deciding where our requests go.

**Otherwise it comes from the address the run would have used had nobody said anything** — the first
one the document declares that requests could actually be sent to. If that address names no
directory then there is none, whatever a later one says. A document that declares
`http://localhost:8080` and then `https://api.example.com/v1`, which is what a generated document
next to a published address looks like, is tested at the root on both readings; borrowing the second
address's directory would send every request somewhere the first says does not exist.

**Only when the document declares no address anything could be sent to does the search go further**,
and then the first directory that can be read from any of them is used. That is a different rule and
it is the right one there: such a document cannot start a run at all without `--url`, so there is no
second reading to disagree with, and what the document says about the directory is all there is.
Two ordinary shapes land here — `- url: /api/v3`, and the `//api.example.com/v2` that reading an
older document with no `schemes` produces — and both now contribute a directory where before they
were ignored entirely.

What cannot be read is refused rather than guessed at, and each refusal is a way of being wrong that
was found rather than imagined:

- **A path still carrying a blank nobody filled in.** `http://{host}/{context}/api` has a directory
  reading `/{context}/api`, and sending that to a server gets exactly the same nothing as sending
  the wrong path — the very failure this amendment exists to remove. The document's own defaults are
  applied first, so `https://{region}.example.com/v2` with a default for `region` contributes `/v2`.
  A blank in the machine's name does not hide the directory beside it: `https://{customer}.example
  .com/v2` still says `/v2`, and that is the case where `--url` was compulsory anyway. What is
  refused is anything that would not be a legal path on its own - a blank, a space, a half-written
  escape - whatever made it one.
- **An address served over something other than the web**, since nothing would be sent there — and
  one that names a protocol without naming a machine after it, `http:/example.com/v2` with a slash
  missing, whose every character after the colon would otherwise read as a directory made out of a
  machine's name.
- **A path that does not begin at the root.** `- url: api.example/v2` might mean a machine or a
  directory, and reading it as one would splice a machine's name into every request.
- **An address carrying a query string or a fragment**, which this same decision refuses when a
  person types one; reading half of one the document wrote would be no more consistent.

The path is taken exactly as the document wrote it, escapes included. Spelling `%2F` out as a slash
would name a different resource than the document does, and spelling `%3F` out would bolt a query
string onto an address that had none.

### Consequences

- A document that declares a path, tested against an address that does not, now reaches the API. That
  is the whole point, and it changes the result of every such run - from nothing to something.
- **There is no way to say "the root of this server, never mind the directory".** That is a real
  cost and it is worth stating plainly: 25 of the corpus's 46 documents declare a directory, and
  anyone running one of those against a deployment that does not have it — a container serving the
  API at its root, a proxy that strips the prefix — can no longer say so. The obvious spelling,
  `--url http://host:8080/`, deliberately does not mean it: a trailing slash is what people type when
  they finish an address, and reading it as a command would silently lose the directory in the common
  case to serve the rare one. Nothing is invented here to fill the gap, because an option nobody has
  asked for yet would be a guess at what they would want it to do — but the gap is now a known one
  rather than an accident, and a `--url` that could say "exactly this and nothing more" is where it
  would be filled. One spelling does work today, by accident rather than by design and tested by
  nothing: `--url http://host:8080/.` carries a path, so it replaces the declared directory, and the
  dot segment is removed before the request goes out. It is recorded here so that whoever fills the
  gap knows it is there, not as an answer.
- Two addresses that disagree about the directory still resolve to the first. A document declaring
  both `https://api.example/v2` and `https://api.example/v3` gets `/v2`, as it already did when no
  `--url` was given.
- The run's own header prints the address it settled on, so what this decides is visible in the first
  line of every run rather than inferrable from the traffic.

### What this does not fix

A run where every single request is refused still reads much like a run against an API that refuses
everything. Telling "the API said no to all of it" from "we never found the API" is a question about
what a run says when nothing could be judged, which is M3.6's, not this one's.


## Amendment (M2.10a)

**Date:** 2026-09-22

**Two options, one refusal, and a positional parameter that is no longer always required.**

```
restest run [--campaign=<file>] [--print-campaign] ... <spec>
```

`--campaign <file>` names a plan saying where this run's values come from and which operations it
may touch; [ADR-0023](0023-the-campaign-file.md) is the format.

`--print-campaign` writes out the plan RESTest follows when it is given none, and stops. It is the
one thing `run` does that needs no document, because it is a question about the tool rather than
about an API - so `<specification>` became optional and is asked for in this command's own words
instead. Everything else about a missing document is unchanged, including the exit code.

**Naming both `--campaign` and `--fuzzing` answers 2.** A plan sets the share of every strategy it
names, including the one that pushes, and the option sets that one share. Honouring both would mean
deciding which the person meant by a rule nobody wrote down, so neither is guessed at.

**A plan that cannot be read answers 2 and the run does not start**, which is a departure from how
this command treats everything else it is handed. A specification it cannot fully read is reported
and worked with; a list of values it cannot read costs the run those values. A plan is different
because of what a plan can say: one of the things it is for is keeping a run to the operations HTTP
calls *safe*, and carrying on with a different plan would answer "only read from this API" by
writing to it. The rule this follows is the one the smoke gate follows - a gate that reports
success because it could not run is worse than no gate.

## Amendment (M11.1)

**Date:** 2026-09-22

**Three options for the numbers, and one refusal.**

```
restest run [--settings=<file>] [--set=<group.key=value>]... [--print-settings] ... <spec>
```

[ADR-0025](0025-settings.md) is the format and the reasoning; this records what the command line
gains by it, because the command line is a compatibility surface and 12.1 freezes it.

`--settings <file>` names a file of settings, in YAML. `--set group.key=value` sets one of them and
may be repeated. Both are read alongside the environment, which names the same settings as
`RESTEST_<GROUP>_<KEY>`; what is typed on the command line wins over the environment, which wins
over the file, which wins over what the tool does when nobody has said otherwise.

`--print-settings` writes out the settings this very command would have used — every one of them,
with a line saying what it does and a note saying which of those four places decided its value — and
stops. Like `--print-campaign`, it is a question about the tool rather than about an API, so it
needs no document.

**`--print-settings` alongside `--settings` is not a conflict**, which is a deliberate difference
from `--print-campaign` alongside `--campaign`. A plan is printed *instead of* being read, so being
handed one while asked to print the carried one is two questions at once. Settings are printed
*after* being gathered, so being handed a file makes the answer more useful rather than ambiguous:
what is printed is what this command would use, that file included, with every value naming where it
came from. `--print-settings` together with `--print-campaign` is refused, because those two are
questions of the same kind and answering one would read as an answer to both.

**A settings file or a `--set` this version cannot accept answers `2`, and the run does not start.**
The same rule a plan file already follows, for the same reason: the numbers decide what the run
does — how hard it pushes at somebody's API, how much of its replies it keeps — so running with
different numbers from the ones that were asked for would produce a result answering a question
nobody put. This is what the exit-code table means from 12.1 by *a settings or plan file the command
line named and the tool could not accept*.

**Nothing else changes.** A run with no settings behaves exactly as it did, since the defaults are
the constants that were in the code before, and every existing option keeps its meaning.

## Amendment (M9.1)

**Date:** 2026-09-23

**The loop is still not a scheduler, and now there is one.** The M1.7 decision describes the run as
"a `for` loop and a queue. It is not a scheduler: no weights, no phases, no strategy selection."
The loop kept its half - the slots, answers dealt with as they arrive, the pause for the reports,
the drain at the deadline - and asks a `Scheduler` what to send next and when the time is up.
Answers are still dealt with the moment they arrive. What is new is one deliberate, bounded wait
before sending: at the start of a run, each step of the first round is sent only once the answers to
the step before are in and have been heard by the listeners, for at most
`schedule.openingLapPatience` and never past the deadline. The part of that wait with nothing in
flight is counted in the idle time like every other pause. The summary gains one line saying what that round took and what it bought,
and `report.json` a `phases` block. [ADR-0026](0026-what-a-run-sends-first.md).

## Amendment (M11.3)

**Date:** 2026-09-30

**One more option, and a variable beside it.**

```
restest run [--auth=<key>]... ... <spec>
```

[ADR-0029](0029-the-key-an-api-asks-for.md) is the reasoning. What is recorded here is what the
command line gains, since 12.1 freezes it.

`--auth` hands over a key the API asks for, and may be repeated. It is written in one of three ways:
- the key alone, for a document that declares one API key;
- `<scheme>=<key>`, naming one of the document's schemes;
- `header:<name>=<key>`, `query:<name>=<key>` or `cookie:<name>=<key>`, for a key the document does
  not declare, which then goes with every request.

`RESTEST_AUTH` holds what one `--auth` holds, so that a key need not be typed. It is not a setting:
it is never printed or recorded with them. A key typed for the same place wins over it.

**A key typed that cannot be placed answers `2`.** That is decided after the document is read and
before anything is sent, as for a plan file the command line named, and the message never repeats
the key. A key in `RESTEST_AUTH` that cannot be placed is only a warning, and the run goes on.

**A key the document asks for and nobody handed over is not a refusal.** A line under the count of
operations says which key, and names the option that would give it.

`--print-settings` and `--print-campaign` read no document, so they read no key either.

**Four complaints no longer repeat what was typed.**
- `--url` with a query string says so without repeating the query, which may be a key, and names
  `--auth query:<name>=<key>`. It answers `3`, as before.
- An argument the command does not understand is not repeated. An option that does not exist is
  named, up to any `=`; anything else is only counted. It answers `2`, as before.
- Any other complaint about the command line is said in the framework's words, with whatever was
  typed after `--auth` taken out. An option missing its value, followed by `--auth=<key>`, is told
  it found `--auth=<key>` - those very characters, not the key. It answers `2`, as before.
- An argument that runs straight on from `--auth`, with no space or `=` between them, is refused
  before the framework reads the command line, without repeating it. So is one in a file of
  arguments named with `@`; nothing after `--` is looked at. It answers `2`.

At the end, a run handed a key says how many replies repeated one back, on a line of its own before
the files are named.

**Nothing else changes.** A run handed no key behaves exactly as it did.

## Amendment (an error that escapes a run answers 4)

**Date:** 2026-09-30

**Whatever escapes the command answers `4`, the failures Java calls errors included, so RESTest no
longer ends with the `1` Java gives a program that crashes. What is still open is listed at the
end.**

### Why

The table gives `4` to "an unexpected failure", and the code kept that promise for one kind of
failure only. Java has two. An *exception* is the kind a program is expected to deal with; an
*error* - running out of memory, running out of room for the stack, a piece of Java missing where
the program runs - is the kind it is not. The command-line framework hands the exceptions a command
throws to the handler that answers `4`, and lets every error through. An error that got that far
went on up and out of the program, and Java ended it the way it ends any program that crashes: with
`1`, the number that says a fault was found. A harness reading the number, which is what a harness
does, counted a crash as a finding.

The review of 11.3 found it, with a document whose shape made a walk of it run out of memory. That
walk is fixed, but an error anywhere on the thread running the command went the same way: before
this amendment, a shape that contains itself, walked with both nesting settings at a million, ran
out of room for the stack and answered `1`. So did a failure inside the framework itself - an
exception while it writes the help, say - because the framework's own last resort answers `1`
unless it is told otherwise.

### How

- Anything that escapes the command is caught in `Restest.run`, the method every caller goes
  through, and said the way an exception already was: `restest: the run could not be completed:`,
  what broke, and its stack trace. The answer is `4`. When not even that can be written - a program
  that has run out of memory may not have enough left to write it - the answer is `4` all the same.
- The framework is told that its last resort answers `4`.
- `main` ends the program with whatever `run` answered, and with `4` if even the few lines of `run`
  around the command fail, which takes memory gone before a word could be written.

**In `run` rather than only in `main`, deliberately.** `run` is documented as answering `4` when
RESTest itself went wrong, so a test, or a program running RESTest inside itself, now gets that
answer - the one a script gets - instead of an error it would have had to know to catch. The cost is
that such a program no longer sees the error itself: only the `4`, and the stack trace on the output
it handed over for problems. On its way out, a run closes its engine, its store and the thread that
delivers its events, however it ends; requests it had already sent may still be finishing, for as
long as the engine waits for a reply, and after running out of memory nothing about a program is
certain. What Java itself does about running out of memory, such as writing a heap dump, happens
where the error is thrown, before anything could catch it.

**No thread kept a crashed run alive.** Java waits, before ending, for every thread not marked as a
*daemon*, and it ended with that `1` at once: during a run, the thread running the command is the
only such thread. The one delivering events is a daemon, requests go out on virtual threads, which
always are, and so is every thread the HTTP client and Java start for themselves - checked with a
thread dump of a run in flight. The HTTP client's own pool of ordinary threads is never started,
because RESTest sends each request on a thread of its own. Since `main` now ends the program itself,
without waiting for any thread, this matters from here on only to a program running RESTest inside
itself.

### What the number cannot say

It is RESTest's number only while RESTest is running. Java that cannot start - a class path with no
RESTest on it, an option it does not know - answers `1` by itself. Java started with
`-XX:+ExitOnOutOfMemoryError` answers `3` when memory runs out, before any of this is reached, and
Java stopped from outside answers whatever the operating system gives it. The launcher starts Java
with none of those options, but Java also reads options from `JAVA_TOOL_OPTIONS` and
`JDK_JAVA_OPTIONS`, which is where containers often put the second; an image that does gives
running out of memory the answer that means nothing could be tested.

One mistake in what is typed reaches the framework's last resort too: a file of arguments named with
`@` that is there but cannot be opened, a directory for instance. It answered `1` and now answers
`4`, with the framework's stack trace. The table's number for it is `2`, and saying so in plain
words is for the command line's own increment, 12.1. **Done at M12.1a: it answers `2`, in a
sentence — see that amendment.**

### Still open

Two ways RESTest can go wrong without answering `4`, found in the review of this amendment and left
for a change of their own:

- **An error on a request's own thread.** Each request is sent on a thread of its own, and an error
  there - running out of memory while a reply is read, a class the HTTP client needs missing - ends
  the request with nothing, which the loop counts, silently, as a request nothing came back for. A
  run that found faults elsewhere answers `1`, one that did not answers `0`, and one where every
  request ended that way answers `3` and blames the address.
- **A key that could not be picked out of an exchange.** The exchange is kept without its details
  and the run says so, as a fault of RESTest's own; the number does not.

And one thing a harness may read instead of the number: a run that broke while it was sending still
writes its report, which is right, and the report does not say that the run broke.

Running out of memory in a listener already answers `4`, through the announcements that never
reached the reports. What went wrong is written to Java's own error output rather than to the one
handed to `run`, and the run first waits until its deadline for the reports to catch up.

### Alternatives considered

- **Catch only in `main`.** A program running RESTest inside itself would go on receiving the error,
  and could act on it. Rejected, because `run` promises an answer, and because nothing but a second
  program could then check what a crash answers.
- **Answer some errors and throw the rest** - answer running out of room for the stack or a missing
  class, and throw running out of memory on to a program running RESTest inside itself, after saying
  it, so that such a program's own way of dealing with it still works. Rejected for now: `run` would
  keep two promises, and a program that wants to react to memory running out can have Java do it at
  the moment it happens, with the options above.
- **A number of its own for a crash.** A crash is RESTest going wrong, which is what `4` already
  says; a new number is a change to the contract that every script branching on it would have to
  learn, for a difference nobody acts on.
- **A handler for everything no thread catches, set for the whole program.** It would reach threads
  that are not RESTest's in a program that runs RESTest inside itself, and it is the kind of state
  shared by the whole program that two runs in one program cannot both own.

## Amendment (M12.1a)

**Date:** 2026-09-30

**The command line is frozen. Two commands join `run`, the help says what every number means, a
file of arguments that cannot be read answers `2`, and there is no `--header`.**

### The surface

```
restest run <specification> [--url=<base>] [--auth=<key>]... [--budget=<duration>]
            [--seed=<number>] [--out=<directory>] [--dictionary=<file-or-directory>]...
            [--fuzzing=<percentage>] [--campaign=<file>] [--print-campaign]
            [--settings=<file>] [--set=<group.key=value>]... [--print-settings] [--store]
restest version
restest help [<command>]
```

Every one of them takes `-h` and `--help`; `restest`, `run` and `version` take `-V` and
`--version`. [`docs/command-line.md`](../command-line.md) is the whole of it in one place — the
commands, every option with its value and its default, the two kinds of variable, files of
arguments, and the exit codes. A test reads the tool's own help on every build and fails when the
page disagrees with it about the commands, the name, value or default of an option, the variables,
or an exit code and what it means; what the page says about files of arguments, and its longer
descriptions of the options, are checked by the tests of those behaviours rather than against the
page. The page is the contract, and this amendment says what the contract promises.

**What frozen means.** For every 2.x version: no command, option or environment variable is removed
or renamed, and none changes what it means; no exit code changes what it means; and the files a run
writes keep their names. A minor version may add - a command, an option, a setting, a number for
something that has none yet - because a script written for 2.0 does not break when something is
added beside what it uses. Anything that would break one waits for 3.0. That is semantic
versioning, the tenth design principle, applied to the command line; the wording of what the tool
prints for people is not part of it.

### `restest version` and `restest help`

`restest version` prints what `restest --version` prints, now two lines: the version alone, as
before, so a script that reads one line reads the same line; and the Java and the machine, because
those are the first questions about anything that behaves differently on somebody else's computer.
A command of its own because the list of commands is where people look first, and it is the line
pasted at the top of a report of something that went wrong.

`restest help` is the command-line framework's own: `restest help run` is `restest run --help`. It
costs nothing, and it is the spelling that `git`, `docker` and `go` have taught people to try.

### The help, complete

`restest run --help` gains three sections. **Exit codes**, the table above in plain words, read from
the same constants the command answers with, and a test fails if the two lists differ. Writing them
out showed the table short by one case: since M1.7 the command has also answered `3` when not one
request was answered - none could be built, the budget ran out before the first, or nothing at the
address replied - which the table's "nothing could be tested" covered only by a stretch. The row
now says so, in the help and on the page. The meaning is the one the code always had; a script
that starts an API and RESTest together meets it when RESTest is the quicker of the two. **Environment**,
naming `RESTEST_AUTH` and `RESTEST_<GROUP>_<KEY>` with an example of each and what wins over what.
**Examples**, each printed whole on one line - the framework breaks a long line after a colon or a
full stop, which put half an address on one line and half on the next - and each one a command the
tool parses, which a test checks. `restest --help` gains two lines to start from. Every option
already said what it does; a test now holds every option of every command to that.

### A file of arguments is read once, and one that cannot be read answers `2`

The framework reads a file named with `@` as if its words had been typed, and one that is there
and cannot be read - a directory named by mistake - made it throw an error of its own, which
reached the last resort: `4` and a stack trace, for a typing mistake. It now answers `2`, with a
sentence naming the file.

To say that, the command has to read the files before the framework does, and 11.3 already read
them once to look for a key stuck to `--auth` inside one. Two readings of one file were two chances
to disagree, and the review of this amendment found both: its own reading split words differently
from the framework's - comments, quotation marks in the middle of a word, a name through a link -
and a file that can be read only once, a pipe or `@/dev/stdin`, gave its words to the first reading
and left the framework nothing, so a run went ahead without the arguments it was handed. **So the
command now reads every file of arguments itself, once, by the framework's own rules - the same
tokenizer, the same quotation marks, `#` for a comment, every line break made one character before
any of it, a file read once for each argument that names it - hands the framework the words, and
tells the framework not to read files itself.** A test reads every awkward case both ways and
requires the same words. A file means exactly what it meant; a pipe now works; and what is looked
through for a key is what is acted on. One thing the framework read no longer does anything: the
system property `picocli.useSimplifiedAtFiles`, which switched its reading to one argument a line.
RESTest never set it, and a person would have had to set it for Java as a whole.

The one place the command still looks at more than the framework would is a `--` inside a file:
the framework takes it to end the options for everything after it, typed or not, and the search
for a stuck key stops at it only for the rest of that file, as it did in 11.3. So `@a.args
--auth<key>`, with `a.args` ending in `--`, is refused rather than taken for the document's name.

### Why there is no `--header`

The roadmap asked for `--header name:value`, for a bearer token or a session cookie somebody
already holds. It was written before 11.3, and 11.3 already carries both, under the one option this
command has for anything that signs a request:

```bash
restest run api.yaml --auth 'header:Authorization=Bearer eyJhbGciOi...'
restest run api.yaml --auth cookie:JSESSIONID=8F3A2C...
```

A value given with its place goes with every request and is hidden in everything the run writes,
which is everything `--header` was to do. A second option for it would be the second vocabulary
[ADR-0029](0029-the-key-an-api-asks-for.md)'s eleventh section rules out, and two ways to say one
thing is one more thing to explain, test and keep. The maintainer took it out of the row on 30
September. The help and the page show the two lines above, and a run refusing a key for an HTTP
bearer or basic scheme now ends by naming the line that sends one already held.

A header that is not a secret - a tenant's name, a language - has no option of its own either.
Given the same way, it is hidden like any value handed over, and one shorter than four characters is
refused, since hiding it would hide it inside ordinary words everywhere the run writes. Nobody has
asked for more, and the fixed headers of a Web Fuzzing Commons file, with the rest of 2.6, are where
more would come.

### Consequences

- The harness repository's adapter, and any script around the tool, has a surface that stays as it
  is for every 2.x version.
- Adding to the command line needs this ADR amended and the page changed with it; the test that
  reads the page is what reminds whoever forgets.
- A key, a token and a cookie come in by one door. When the rest of 2.6 brings a sign-in, it comes
  through the same option or beside it as `--auth-file`, as ADR-0029 says.

### Still open

What a run stopped from outside leaves behind. Today Ctrl-C, `kill` and `docker stop` end it where it
stands, with Java's `130` or `143`, no summary and no report. Increment 12.1b takes from the three
answers the M1.8 amendment listed the first one's evidence - write what was found, say that the run
was cut short, close the store - or says plainly that it could not; and it ends with the number the
second one names, the conventional one for an interrupted program, which is Java's `130` or `143`.
The maintainer chose both on 30 September, so that a run cut short is never read as one that
passed. *Done at M12.1b, below.*

The two ways RESTest can go wrong without answering `4`, listed at the end of the amendment before
this one, go to 12.1b too, with the answers the maintainer chose on 30 September. *They were split
from it the same evening, into 12.1c, taken first; its amendment, below, closes them.* A request RESTest
loses on its own thread counts as RESTest's failure: the run carries on to the end of its budget,
then answers `4` with the first failure's stack trace, and stops early, saying RESTest lost them,
once as many requests as may be in flight have ended unanswered with nothing answered at all. And
an exchange kept without its details answers `4`.

## Amendment (M12.1c)

**Date:** 2026-09-30

**A run in which RESTest itself lost part of its work answers `4`: a request lost on the thread it
was sent on, and an exchange that had to be kept without its details. The two ways the amendment of
#344 left open are closed.**

### Why

The table gives `4` to RESTest going wrong, and both of these are RESTest going wrong while the API
answers perfectly well; what was missing was the number.

- **A request lost on its own thread.** Each request is sent on a thread of its own. The part of
  the tool sending it turns everything the API or the network can do - a refused connection, a reply
  cut off, a wait that ran out - into an answer, so what escapes it is the tool's own failure:
  running out of memory while a reply is read, say. The loop counted such a request, silently, as
  one nothing came back for. Measured on a stand-in that answers the first request for one
  operation with 512 MB, against a run limited to 128 MB of memory and told to keep replies whole up
  to a gigabyte (`--set engine.maxRetainedResponseBytes=1000000000`; by default the engine keeps the
  first megabyte of a reply and reads the rest away, and nothing is lost): the request was lost, the
  other 64,655 were answered, and the run said *no faults found* and answered `0`. A run in which every
  request went that way would have answered `3` and told its reader to check the address.
- **An exchange kept without its details.** When the key a run was handed cannot be picked out of
  an exchange, the exchange is kept with everything that could hold a key blanked
  ([ADR-0029](0029-the-key-an-api-asks-for.md), section 6). The run said so at the end, and answered
  as if nothing had happened. Every report and the stored run have that exchange with next to nothing
  in it, so whatever the run concludes, it concludes without it.

### What it does now

- **A lost request is counted apart**, with the first failure kept. The run carries on to the end
  of its budget: one request lost to an enormous reply says nothing about the next thousand. At the
  end it answers `4` and says, where problems go, how many requests RESTest lost, that they are
  missing from what is reported, and the first failure with its stack trace. So does a request the
  sending part hands back as nothing at all, a case its contract forbids, with nothing to show.
- **A run that loses every request stops early.** A lost request still counts as unanswered, so the
  rule that stops a run whose address answers nothing - as many requests as may be in flight, 32 by
  default, ended with not one answered, looked at the end of each round - stops this one too. It
  answers `4`, and says RESTest lost them. The address is named as well only when the API left other
  requests unanswered too - refused, reset, timed out - since then it may be wrong as well; the
  sentence that sends a reader to the address and nowhere else belongs to `3`. Stopping at the first
  lost request was considered and rejected by the maintainer.
- **An exchange kept without its details answers `4`**, with the sentence the run already printed.
- **In what order.** RESTest's own failures come before "was anything tested", as a report that
  broke already did: a run that lost every request tested nothing, and the reason is RESTest.

The help and [`docs/command-line.md`](../command-line.md) name the two in the row for `4`: *it lost
requests on their way, had to keep exchanges without their details, or broke outright*. The meaning
is the one the table always gave; the words now cover what the number does. An exchange kept without
its details is one in which the key was hidden the only way left, by blanking everything that could
hold it, and the page says so, so that a `4` from a run handed a key is not read as a key written
somewhere.

The first failure is printed through the hiding of the keys the run was handed, as every exchange
is, and so that failing to print it - memory running out a second time - leaves its kind said and
the rest of what the run has to say, where its report is included, still said. The failure that
escapes a run altogether, which the amendment of #344 answers with `4`, is printed as it was: with
the key hidden only where the exchanges were.

### Consequences

- A script can no longer read as a clean one a run whose sending part lost a request, or that had
  to keep an exchange without its details.
- A run that loses one request in a million answers `4` where it answered `0` or `1`. That is the
  point: a `4` says the run is not complete, which it is not, and what it did find is still printed
  and still in `report.json`.
- The tests reach both failures through a seam of the command's own: what builds the engine is
  handed to the run command, the engine that talks HTTP unless a test says otherwise. Nothing a
  person types reaches it.

### Still open

`report.json` still does not say that a run broke; a harness reading the file instead of the number
sees a finished run. The maintainer kept that a question of its own on 30 September.

The review of this amendment found three more ways a failure of RESTest's own reads as something
the API did. None was among the maintainer's two decisions; asked, the maintainer put them after
v2.0 on 30 September, as row 3.8 of the roadmap:

- **A key that cannot be added to a request.** The request is not sent, and is recorded as one that
  could not be assembled, which is a failure on the way to the API. Every request going that way
  would answer `3` and point at the address.
- **A failure of RESTest's own code inside the engine** that Java calls an exception rather than an
  error - in the code that records what went over the wire, say - is caught with the network's
  failures and recorded as one.
- **A failure while the loop deals with an answer** - announcing it, or handing it to a series - is
  lost with the future it happened in. The request is counted as answered and no report hears of
  it.

## Amendment (M12.1b)

**Date:** 2026-09-30

**A run stopped from outside - Ctrl-C, `kill`, `docker stop`, a terminal closed - makes no new
requests, waits a short while for the answers it is owed, and writes what it found, said to be a
run cut short: the summary, `report.json`, and a closed stored run. Or it says, before the program ends,
what it did not leave behind. It ends with Java's number for the way it was stopped, `130` or
`143`, which the help now lists.**

### Why

Until this amendment a run stopped from outside ended where it stood, with no summary, no
`report.json`, and with `--store` a database beside its two working files. Everything it had paid
for was lost, and the one number left, `130` or `143`, said nothing about what had been found. Of
the three answers the M1.8 amendment listed, the maintainer chose on 30 September the evidence of
the first - write what was found, say that the run was cut short, close the store - with the number
of the second, the conventional one for an interrupted program, so that a run stopped half-way is
never read as one that passed. And, the same day, how: with Java's shutdown hook, and a short wait,
`schedule.interruptGrace`, 2 seconds by default.

### How Java stops a program

Ctrl-C, `kill`, `docker stop` and a closed terminal each send the program a signal - `INT`, `TERM`,
`TERM` and `HUP`. Java answers each by beginning to end the program: it starts every shutdown hook
the program registered, each on a thread of its own, while the rest of the program carries on; it
waits for all of them; and it ends the program with 128 and the signal's number - `130`, `143`, `129`.
Once it has begun, a second Ctrl-C, and the `System.exit` that `main` reaches at the end of a run,
wait on the first for ever. Read in the JDK's own source, `Shutdown.exit` and `Terminator`, rather
than assumed. Two consequences follow. What a hook does with its thread is how long a stopped
program takes to end. And nothing but the hook's own limit ends that wait, since pressing Ctrl-C
again does nothing.

### What it does now

- **A hook is registered for as long as a run lasts**, from the moment it begins reading the
  document until everything is written and said, and removed afterwards. Each run registers and
  removes its own, so two runs in one program are each stopped on their own (the sixth principle).
- **A run stopped before it begins testing does not begin.** Until then it has sent nothing and
  written nothing, and it has not yet cleared its directory of what an earlier run left there. So
  the hook says so - *the run was stopped before it began testing, so it sent nothing and wrote
  nothing, and* the directory *is as it was, with whatever an earlier run left there* - and lets
  the program end at once. Which of the two came first, the stop or the start of testing, is
  settled under one lock, so a run is never told it may begin once the hook has said it will not.
  Found by the review: with the hook registered only once testing began, as the plan approved on 30
  September had it, a stop while the document was read ended with `130` or `143` and no word, beside
  the complete report of an earlier run that a script could read as this one's. The maintainer
  approved the change on 1 October. Inside one program, a run stopped before it began testing
  answers `130` even when it goes on to find something else to answer, a key refused or nothing to
  test, since a program stopped from outside ends with Java's number whatever it answers.
- **The hook asks the run to stop and waits for it.** The loop looks between one decision and the
  next, and at least every fifty milliseconds while it waits for room to send, for the steps of the
  opening lap or for the reports to catch up. Once stopped, it hands nothing more to the engine; an
  opening lap still going is announced as cut short, as it already was when the time ran out. The
  requests it had already handed over still go out, those waiting their turn inside the engine
  included: the engine sends as many at once as its limit allows, and the loop hands it up to twice
  that, so up to half of them, and more once the limit has come down for a struggling API, are
  waiting when the stop comes. They are never more than may be waiting for an answer at once, 32
  by default.
- **It waits for the answers it is owed for `schedule.interruptGrace` from the moment it was
  stopped** - two seconds by default, where the end of a budget waits the engine's read timeout and
  the straggler grace, forty seconds. That includes a stop that comes while the run is already
  waiting after its deadline: that wait is shortened the same way.
- **An answer that comes after that wait is not announced, however the run ends.** It counts as never
  answered, and the run says how many there were. Before this amendment such an answer could still
  reach the stored run after the report had been written - rare at the end of a budget, where the
  wait is long, and common after a stop, where it is not - so the two could disagree about how many
  requests there were. The number still owed is now the requests sent less those counted when the
  loop stopped listening, exact where the count of free slots could be off by an answer being
  announced at that moment.
- **The run then writes what it found, as at the end of its budget.** `RunFinished` carries
  `cutShort`; the first line of the summary ends in `, cut short`; `report.json` has a member of its
  own, `"cutShort": true`, straight after `"api"`, the word the opening lap's row already used; the
  stored run is closed. Where problems go, the run says how long it ran of its budget and that what
  it reports is what it found until then, and how many answers were still owed, naming the setting.
- **When it is done, the hook returns and Java ends the program** with `130` or `143`.
- **When it is not done `schedule.interruptGrace` and five seconds after the stop, the hook says so**
  and returns all the same: *the run was stopped from outside and had not finished writing what it
  found 7s later, so it ends here*, followed by what is missing - `report.json` not written, or
  `run.sqlite` not closed. A stored run commits what it holds in groups, so one that was not closed
  has lost the last interactions it had not yet saved, and keeps some of what it did save in
  `run.sqlite-wal` beside it; the sentence says both, and that the three files go together. What
  is missing is read from the directory, not from the run, and it can be, because of the next
  point. The look is made on a thread of its own and given a second: writing may have run out of
  time because the directory stopped answering, and a look that waited on it would keep the program
  from ending when a second Ctrl-C does nothing. A directory that does not answer is said to.
- **`report.json` is written whole or not at all:** into `report.json.partial` first and then renamed
  in one step, so a program ended while writing it leaves no half of a report under the report's
  name. The next run in the directory clears the partial file with the rest.

### The numbers

`130` and `143` are Java's and are kept, not replaced: every shell and every build server already
reads them as "interrupted". The help now lists both, with what a run stopped that way leaves behind,
and the page's table gives them rows of their own; `129`, a closed terminal, stays among the numbers
RESTest does not choose. Inside one program - a test, or a program running RESTest inside itself -
there is no signal to tell Ctrl-C from `kill`, and a run stopped through the hook answers `130`.
That number is asked about before anything else in `ExitCode.of`, lost requests and broken reports
included, because a program stopped from outside ends with Java's number whatever the run would
have answered, and a test reading the run must read what a script would.

A run is cut short when the stop comes before it has stopped waiting for its answers. One stopped
later - while it writes - reports a complete run, with `"cutShort": false`, and the program still ends
with `130` or `143`: nothing was cut but the few lines after the report.

### The two waits, and why only one is a setting

The grace for answers is a setting, because a reasonable user changes it: Kubernetes gives a stopped
container thirty seconds, `docker stop` ten. The five seconds for writing are a constant beside the
code that uses them. They are not a pause anybody waits through - writing takes a fraction of a
second, the reports catching up, the report file, the last group of interactions - but how long to
wait for writing that has got stuck before saying so, and the reason for the number is `docker
stop`'s ten seconds: two for the answers, five for the writing and one for the look at the directory
leave two in hand. Anybody who raises the grace raises the whole wait with it. A grace longer than
anybody could wait - `--set schedule.interruptGrace=2600000h` - is waited like any other, until the
run is done; the review found that it made the hook fail at once, and every wait of the loop's and
the hook's now counts a length too long for the clock's own units as the longest wait there is. A
second setting for them was considered and not taken: one more number to freeze, for a wait that
in practice never runs out.

### What it cannot do

- **A stop before a run is testing** writes nothing, which is right: nothing was found yet. It says
  so, and leaves the directory as it was.
- **Requests already handed to the engine** still go out after a stop, as above. Dropping the ones
  still waiting their turn needs a way to tell the engine to send nothing more without cutting off
  what it has in flight, and the engine has none; it would be a change to it of its own.
- **`kill -9`, a container's memory limit, and `docker stop` once its ten seconds are up** end the
  program without running anything. Nothing can be written, and `137` is the answer.
- **Java started with `-Xrs`** does not install its handlers, so the signals end it outright, with
  nothing written.
- **A program started in the background by a shell that is not a terminal's** is started with
  Ctrl-C ignored, and Java leaves an ignored signal ignored. `kill -INT` then does nothing at all;
  `kill` does what it always does. Found while checking this amendment by hand, and the reason the
  test that sends Ctrl-C to a real program steps aside, rather than fails, when the signal is
  ignored where the tests run.
- **Windows** has no signals to send. Ctrl-C in a console and closing the console stop a run this
  way; `taskkill /F` is `kill -9`. The test with real signals does not run there.
- **A container image** has to start Java as the container's own process - `exec java`, or an
  `ENTRYPOINT` in its exec form - or `docker stop` reaches a shell that does not pass it on. The
  `restest` script already does; the image of 7.2a must too.

Measured on the pet clinic of the local setup, stopped twelve seconds into a sixty-second run with
`--store`: Ctrl-C ended the program 0.4 seconds later with `130`, `kill` in 0.7 seconds with `143`,
and `docker stop` on a Java 21 container in a second with `143`; each time the report said
`"cutShort": true`, the stored run was closed with nothing beside it, and the report and the stored
run held the same number of requests - 16,306 after Ctrl-C.

### Consequences

- A run stopped from outside costs what it had not yet done, and nothing it had already found.
- A script reading the number still reads a run stopped half-way as one that did not pass; a
  harness reading `report.json` instead can now tell it too.
- The run command is handed where it hears a stop, as it is handed its engine since 12.1c: Java's
  hook, except in a test that stops a run at a moment it chooses. Nothing a person types reaches it.

### Still open

`report.json` still does not say that a run broke, as distinct from being cut short; that stays the
question of its own the maintainer kept on 30 September. The three cases of row 3.8 are unchanged.

### Alternatives considered

- **Catching the signals directly**, through the part of Java that lets a program replace its
  handlers. RESTest could then choose the number and let a second Ctrl-C end the program at once.
  Not taken: that part of Java is outside its supported interface, and the maintainer chose the
  standard way on 30 September.
- **Interrupting the loop's thread** instead of having it look every fifty milliseconds. Java's
  interruption stays on the thread, and would cut short the waits that have to happen after the
  stop: for the answers owed, and for the reports to finish. Looking costs a wake-up every fifty
  milliseconds while the loop waits, and changes nothing in a run nobody stops.
- **Answering the ordinary number inside one program**, `0` to `4`, and leaving `130` to Java.
  Rejected: a test, or a program running RESTest inside itself, would read a run stopped half-way as
  one that passed.
- **Writing `report.json` in place.** A program ended while writing it would leave half a report
  under its name, and the sentence saying it was not written would have nothing true to say.
- **Registering the hook only once testing begins**, as first planned and built. Rejected after the
  review, by the maintainer on 1 October: a stop while the document was read then said nothing, and
  left an earlier run's files to be read as this one's.

## Amendment (`--fuzzing` removed before the freeze applies)

**Date:** 2026-10-01

**`--fuzzing` is gone, and `--campaign` is the one way to say how much of a run pushes at the API.**
The surface the M12.1a amendment froze loses one option:

```
restest run <specification> [--url=<base>] [--auth=<key>]... [--budget=<duration>]
            [--seed=<number>] [--out=<directory>] [--dictionary=<file-or-directory>]...
            [--campaign=<file>] [--print-campaign]
            [--settings=<file>] [--set=<group.key=value>]... [--print-settings] [--store]
```

The freeze holds from v2.0, which is not tagged yet, so this is the last point at which an option can
go without waiting for 3.0. Three reasons, any one of which would have been enough:

- **It says nothing a plan cannot.** It was added at M2.7a, before there was a plan file, because
  there was then no other way to stop sending awkward values. Since [ADR-0023](0023-the-campaign-file.md)
  it has been a second way of setting one number of the plan RESTest carries, refused beside
  `--campaign`, with code of its own to share out the rest of the run.
- **What it said had stopped being true.** Its help promised that `0` sends none of the values
  nobody sensible would send. Since 10.1, a fifth of the run changes accepted requests into ones the
  document forbids: a word where a number belongs, nothing where something is required, a number past
  its limit. A run against a stand-in API with `--fuzzing 0` sent `count=abc` 1,798 times in three
  seconds. The switch for those is `mutation.violations`, and no share of pushing touches it.
- **Nothing depends on it.** The evaluation harness passes plans, never this option.

Naming it now answers `2`, as an option that does not exist, followed by the usage; a test holds
that. The refusal to name it beside `--campaign`, from the M2.10a amendment, goes with it.

The help of `--seed` is rewritten, as wording rather than contract: it now says first that, with the
plan RESTest carries, the same number gives a similar run rather than the same one, and where to
read how to make it repeat a run exactly.
