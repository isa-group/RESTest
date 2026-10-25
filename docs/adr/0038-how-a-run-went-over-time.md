# ADR-0038: report.json says how a run went over the time it was given

**Status:** Accepted
**Date:** 2026-10-25

## Context

`report.json` says how a run ended: so many replies of each class of status code, so many faults of
each kind, the opening lap's moments under `phases`, and how long the run took. It cannot say how
the run went. A run whose API fell over in its first seconds and recovered, one that worked for half
an hour and then stopped answering, and one that was slow to start and fast at the end all write the
same totals. The HTML report planned next is to draw the run as a timeline, with the opening lap
marked, and it has nothing to draw it from.

The file did not even say when the run began, nor how long it was given. The budget, `--budget`,
reached only the scheduler, as a deadline ([ADR-0013](0013-input-generation.md) §6).

None of the related tools writes a run over time into its report. The nearest is EvoMaster, whose
statistics can be written as snapshots taken every so many percent of the search budget.

## Decision

### 1. The time a run was given is cut into equal slices, fixed in number

`report.timelineIntervals`, 60 unless set otherwise, says how many. A slice is the budget divided by
that number, rounded up to a whole millisecond and never shorter than one, so a budget of a minute is
sixty slices of a second, one of an hour sixty of a minute, and one of ten seconds sixty of 167 ms,
the last of which ends after the budget does. The number of slices is the budget divided by the
length of one, rounded up, and so never more than the setting.

The slices are cut from the budget rather than from the time the run took because they have to exist
before the first reply: the report counts every reply as it arrives and remembers none of them, so
it must know which slice a reply goes in when it arrives. What the report keeps is one small count
per slice, however long the run lasts. The slices are worked out in milliseconds, which any budget
`--budget` takes fits in, so a run given centuries is cut like any other.

The number is a setting, as every number somebody decided is ([ADR-0025](0025-settings.md)), from
one to ten thousand. Every slice inside the run is written, an empty one too, so a number typed with
a few zeros too many would fill the file, and the memory, with empty slices: a hundred million over an
hour is 3.6 million slices of a millisecond, some 60 MB of file, and ran out of a gigabyte of memory
before the report was written (decided by the maintainer on 25 October 2026). It has no switch: it
changes nothing a run sends, and costs what §5 says.

### 2. A reply counts in the slice its request was sent in

By the moment the request went out, as a request counts in the stretch of the run it was sent in
under `phases`. An answer that arrives after the time is up still counts in the slice it was asked
in. A slice holds its beginning and not its end. A request sent before the first slice counts in the
first, and one sent at or after the end of the last slice counts in the last. So, for a run that
said it had begun before its first reply came back, as every run of RESTest's does, the slices add
up exactly to `replies.byClass`. Replies are counted by the class of their status code, `noReply`
among them, exactly as `replies.byClass` counts them, by one function every count of
`report.json` shares.

### 3. Only the slices that began before the run ended are written

A run of an hour stopped after ninety seconds writes two slices, not sixty, and is never drawn as a
run that went on sending nothing. The slices written never stop short of the last one something came
back in. A slice inside the run that nothing came back in is written, empty: reading the document at
the start, or a pause, is what it shows.

What this costs, accepted by the maintainer on 25 October 2026: a long budget stopped early gets few
slices. The hour stopped after ninety seconds is two bars of a minute.

### 4. The run says how long it was given

`RunEvent.RunStarted` carries the budget, which must be more than nothing, beside the moment the run
began. [ADR-0013](0013-input-generation.md) §6 is amended to match: the scheduler is still the only
part of the tool that decides anything by the budget, and the reports are told it so that they can
show it.

`report.json` gains one member at the top, `timeline`, after `phases`: when the run began, its
budget, the length of a slice, and the slices, each an object holding `byClass`, so that a later
count per slice is one member more rather than a second list. It writes no moment the run ended,
which `startedAt` and `totals.elapsed` already give.

A run that never says it began, which a run of RESTest's never does, has no time to cut: its
`timeline` has a budget and a slice of nothing and no slices, and a start worked out from when it
ended and how long it took. The report already writes an API with no title for such a run.

### 5. What it costs

Measured on 25 October 2026 by feeding one million made-up replies, spread over a minute, to the
report of `master` and to the report with the timeline, ten rounds each after five to warm up, three
times each in a process of its own: the report took about 31.5 ns per reply without the timeline and
49.5 ns with it, and the timeline alone 20 ns. At the fastest rate on record, restcountries' 287,676
replies in a minute, that is 0.1 ms in every second of a run, on the thread that delivers
announcements rather than the loop that sends. A minute's run of pet-clinic wrote 3.2 KB of
`timeline` into a report of 1.8 MB.

## Consequences

- **A report can draw how a run went**, and the opening lap on it, from `timeline` and `phases`.
- **`report.json` says the budget a run was given**, which it never said before.
- **One more setting**, `report.timelineIntervals`; the amendment to ADR-0025 of today counts them.
- **Every listener hears the budget.** None but the reports uses it.

## Alternatives considered

- **Slices of a fixed length, a second say.** A run of an hour writes 3,600 of them, of a day 86,400:
  the file would grow with the run, which nothing else in it does.
- **Slices over the time the run took.** That time is known only once the run has ended, and the
  slices are needed before the first reply.
- **Slices that adapt to the run**: start very fine, and merge them two by two whenever the run
  outgrows them, so that a run stopped early still has many. Turned down by the maintainer on
  25 October 2026 for the simpler grid; §3 says what that costs.
- **A reply counted when it arrived.** A reply that came after the time was up would fall outside the
  budget, and a slice would not say what was sent in it.
- **Round lengths**, 1, 2 and 5 times a power of ten. A reader of the file sees the length of a slice
  written beside the slices, and a chart labels its own axis.
- **One list of counts for each class**, `"2xx": [9, 7, 29, ...]`. Shorter, and the first count a
  slice needs beside its classes would need a list of its own.
- **A switch for the timeline**, asked about by the maintainer for runs whose report nobody draws.
  §5's cost is not one a run would notice.
- **A JSON Schema of `report.json`**, planned with this at first. Left out by the maintainer on
  25 October 2026.

## Amendment (25 October 2026): the page draws no opening lap

**The HTML report draws the timeline without marking the opening lap**, as the maintainer asked on
25 October 2026 while the page was designed. `phases` still says when the lap began and ended, for
any report that wants to mark it, and the page draws everything else this record describes
([ADR-0039](0039-the-html-report.md)).
