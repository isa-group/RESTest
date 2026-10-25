# ADR-0039: The HTML report, a page drawn from report.json

**Status:** Accepted
**Date:** 2026-10-25

## Context

A run says what it found on the screen while it runs and in `report.json` when it ends. The screen
is gone once the terminal is, and stops printing faults after the first fifty. `report.json` holds
everything, for a program. Neither is something a person opens from what a build kept, from an
e-mail, or on a laptop with no network, and understands in a minute: the verdict, the errors and
warnings in all and on each operation, the request behind each kind of fault, and what could not be
tested.

The page was designed with the maintainer over several rounds, on a real run of pet-clinic. Three
directions were drawn; the maintainer chose the one closest to a console, on 25 October 2026, and
set one rule for it: **the page shows only what `report.json` holds.** Where the page needed
something the file lacked, the file gained it first: every operation with what happened to it, the
parts of the document that could not be used, the keys nobody handed over, how the run was started,
and the verdict ([ADR-0006](0006-event-stream-and-store.md) and
[ADR-0015](0015-command-line-contract.md), amendments of the same day). Response times per
operation, which the file does not hold, are not on the page.

Several related tools write a page. CATS writes a summary that can be filtered and searched, and a
page per test with the request, the reply and a `curl` command. RestTestGen writes a page listing
every request, with a button that copies it as `curl`. EvoMaster writes its report in the Web Fuzzing
Commons format with the viewer that format comes with, which fetches its files and so has to be
served over HTTP, and a page that needs nothing beside it for whoever cannot serve one. Schemathesis
writes JUnit XML, HAR, NDJSON and a Web Fuzzing Commons report, and no page.

## Decision

### 1. One file, `report.html`, beside `report.json`, on every run

The page is written into the same directory as `report.json`, after it, from the same run. A switch,
`report.html`, on unless set otherwise, leaves it out for a run whose results only a program will
read ([ADR-0025](0025-settings.md)); it changes which files a run leaves, not what it sends or finds,
so it is not a lever and plays no part in an ablation. A page left in the directory by an earlier run
is removed either way, with the file it is written into first, so that it is never read as this
run's.

The page is written whole or not at all, the way `report.json` is: in full under a name of its own
beside it, then given its name in one step. The two share the code that does it. A page that cannot
be written is a report that failed, and the run ends with `4`, as for any report.

### 2. It holds the whole of `report.json`, and draws itself from it

The page is the text `report.json` was written with, inside an element of the page, and a script
that draws the page from it in the browser. Nothing is worked out in Java for the page alone, so the
page cannot say something the file does not. A program reads `report.json`; how the page is laid out
is not part of what a version promises to keep.

**Embedded, not fetched.** A page opened from disk cannot fetch the file beside it in most browsers,
and a page that needs a second file stops working the moment one is sent without the other. The run
sits in `<script type="application/json" id="report">`, read with `JSON.parse`. Every `<` in it is
written as `\u003c`, which JSON reads back as the same character, so that nothing quoted from a
reply - `</script>`, `<!--` - can end that element early; the line and paragraph separators are
written as escapes as well. The escaping happens as the text is written out, never by a search and
replace that a `$` or a `\` in the data could break.

**Around the JSON report.** `HtmlReport.around(json, file)` hands the JSON report every event and,
when the run finishes, writes the page around the text the JSON report has just written: the run is
not written out twice, and the page does not depend on the order the two would have been subscribed
in.

### 3. No network, no framework, no build step

- The page states a `Content-Security-Policy` of its own: `default-src 'none'`, its own inline
  script and style, and pictures only from inside it. The browser then visits no address for it.
- What came from the API is put on the page only as text, with `textContent` and elements built one
  by one; the page holds no `innerHTML`, `eval` or the like, and a test reads it for them.
- The page is one resource of plain HTML, CSS and JavaScript in `restest-report`, with no library
  and nothing to build. The logo is two small pictures inside it, one for light and one for dark
  colours, at the size they are drawn. The fonts are the system's.
- Where JavaScript is off, the page says so and points at `report.json`; where the data is not what
  the page expects, it says that instead of showing nothing.

### 4. What it shows

The layout the maintainer approved:
- a header with the API, when, how long, and a choice of light or dark colours that follows the
  system until changed;
- four cards: replies by class of status code, errors by kind, warnings by kind, and operations by
  what was found on them;
- the timeline, columns of replies by class, one per slice of the budget;
- every operation in a table, with filters for those with errors, only warnings, nothing found and
  not tested, and those the plan left alone when there are any;
- for each kind of fault on an operation, the first of the faults `report.json` writes out whole,
  with buttons that copy its `curl` command or the whole of it as an issue in Markdown;
- each kind of fault with its catalogue number in parentheses, explained when pointed at or reached
  with the keyboard, and the classes of status code it came back in, which is the second
  classification [ADR-0016](0016-how-a-fault-is-presented.md) asks for;
- what could not be tested or read, and how the run was started;
- in that last part, the number the command ended with and what it means, read from the verdict
  `report.json` writes: `0`, `1`, `3`, or `130 or 143` for a run stopped from outside, since the file
  cannot know which way it was stopped.

The verdict was first drawn as a band across the top of the page, under the header. After seeing
the page for runs of four real APIs, the maintainer had the band taken out and the verdict given as
the exit code, in this last part, without saying there that a run ending with `4` broke
(25 October 2026).

Only the first written-out fault of a kind on an operation is shown, with a line saying how many
there were and how many `report.json` holds in full (maintainer, 25 October 2026).

## Consequences

- **A run can be read by somebody who will not read JSON**, from wherever its files were kept.
- **The page is about as large as `report.json`**, plus some 80 KB: it holds the whole of it. A run
  whose findings quote large bodies makes a page of megabytes.
- **The page's script has no automatic test.** Running a browser in the build would bring in what
  the build otherwise does without. What can be checked without one is checked: the run in the page
  reads back as `report.json`, hostile replies cannot break out of it, the policy is there, and the
  page holds no way of writing markup or fetching anything. The rest is tried by hand in browsers,
  opened from disk, before a change to the page is merged.
- **A key handed over is hidden in the page** because the page is the same text as `report.json`,
  hidden before either was written ([ADR-0029](0029-the-key-an-api-asks-for.md) §6).

## Alternatives considered

- **Fetching `report.json` from the page.** Refused by most browsers for a page opened from disk, and
  two files to keep together.
- **Writing the page in Java, with a template engine.** The filters, the search and the details
  opened on demand need a script anyway; the page's logic would be split between two languages, and
  a template engine would be one more dependency for one file.
- **A JavaScript framework or a charting library.** Either needs a build of its own, or a network to
  load from; the page is small enough without.
- **Working the verdict out in the page.** It would have contradicted the exit code for an API that
  takes connections and never answers, where every request is a fault and the run ends with `3`. The
  file says the verdict instead.
- **A listener of its own beside the JSON report.** It would have serialised the run a second time,
  or depended on being subscribed after it.
- **Every written-out fault on the page.** The first proves the fault and shows how to repeat it;
  the others stay in `report.json`.
- **The Web Fuzzing Commons viewer as the page.** Set aside by the maintainer on 10 October 2026: its
  format is built around generated test files, and carries no request, reply, `curl` command or
  severity; its viewer fetches its files, so it has to be served. Writing the run in that format as
  well is a decision of its own.
