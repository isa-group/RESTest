# ADR-0015: One command, a time budget spent in full, and an exit code that means something

**Status:** Accepted
**Date:** 2026-09-14

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

`--out` defaults to `restest-out/`, and a run writes exactly two files into it: `report.json` and
`run.sqlite`. One directory rather than one flag per artefact, so later report formats need no new
option. A previous run in the same directory is replaced.

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
the engine's read timeout. A run whose budget is smaller than a single slow answer will overshoot, and
that is the correct behaviour for a tool whose job is to report what the API did.

### Exit codes

| Code | Meaning |
|---|---|
| `0` | The run finished. No fault was found. |
| `1` | The run finished. At least one fault was found. |
| `2` | The command line was wrong. |
| `3` | Nothing could be tested: the document yielded no testable operation, or no usable base address. |
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
