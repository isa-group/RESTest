# Reading what a run says

A run says what it found three times over: on the screen as it goes, in `report.json` when it ends,
and, with `--store`, in `run.sqlite`. This chapter reads each with a question in mind. Every line and
every key is listed in [What a run leaves behind](../report.md), and every kind of fault in [The
faults RESTest reports](../faults.md); this chapter is about using them.

## A fault, line by line

A fault from the clinic, as the screen prints it:

```
TO BE RUN: an F100 from the clinic
```

- `F100  HTTP Status 500` is the kind of fault: a number from a catalogue several testing tools
  share, and the catalogue's name for it.
- The second line is the request: the operation, by the name the document gives it, then the method
  and the address, and after the arrow what the API answered.
- The third says what is wrong, in a sentence.
- The last is a `curl` command that sends exactly the same request again. Paste it into a terminal
  and the clinic falls over the same way, which is what whoever fixes it will want to see.

A fault of the other kind says more, because a reply of the wrong shape can be wrong in several
places at once:

```
TO BE RUN: an F200 from the clinic
```

The indented lines under the sentence are the places in the reply that disagree with the document,
and how. A reply that leaves out something the document calls required, or sends a number as text,
shows here. Remember that either side can be wrong: the API, or the document describing it.

## The summary

```
TO BE RUN: the clinic's summary
```

Read it from the top:

- **How much was sent**: requests, operations, time, and how much of that time RESTest had nothing in
  flight. That last number, *idle*, is kept as close to nothing as possible: the budget is for
  testing.
- **The opening lap**: the first round, which sent every operation once with the request most likely
  to be accepted, and how many operations answered it with a success.
- **How the API answered**, by class of status code. Mostly `4xx` means mostly refused, and then the
  requests are what need work: [chapter 6](06-dictionaries.md) shows how to hand over values the API
  will accept.
- **What kind of requests were sent**: how many pushed at the API on purpose, how many changed one
  thing in a request the API had accepted, how many were steps of a series. Their refusals are
  expected, and they are counted here so that they do not read as the API turning away ordinary
  requests.
- **How many operations answered 500**, and how many some 5xx. This counts operations, not replies:
  one broken operation asked six hundred times is six hundred replies, but one operation.
- **The faults**, by kind.
- **What could not be tested**, if anything, each operation with the reason. `no faults found` says
  nothing about an operation that was never tried, so read this line before celebrating.

## Asking the report

`report.json` holds all of it for a program to read. [`jq`](https://jqlang.org/), a small
command-line tool for JSON, answers most questions in one line. These assume the report of the last
run is in `restest-out/`.

**Which operations broke, and how often?**

```bash
jq -r '.faultsByOperation[] | "\(.count)\t\(.label)\t\(.operation)"' restest-out/report.json
```

```
TO BE RUN: faults by operation
```

**How do I reproduce the first fault?**

```bash
jq -r '.findings[0].curl' restest-out/report.json
```

Every fault written out whole has one: the first five of each kind on each operation, up to a
thousand in all. The rest are counted, never lost: `faultsByOperation` has every one.

**Did the run test everything?**

```bash
jq '.skippedOperations' restest-out/report.json
```

An empty list means every operation the document describes was tried. Otherwise each entry names an
operation and says why it could not be.

**What did the API answer most?**

```bash
jq '.replies' restest-out/report.json
```

**Is the second run better than the first?** Keep each in a directory of its own with `--out`, and
compare their totals:

```bash
jq -c '.totals' runs/first/report.json runs/second/report.json
```

Two runs are only worth comparing if the API was in the same state at the start of each: start the
clinic again between them.

## Asking the stored run

A run made with `--store` keeps every request and reply in `run.sqlite`, an ordinary SQLite database
with one table, `interaction`. [`sqlite3`](https://sqlite.org/cli.html), SQLite's own command-line
tool, asks it anything:

```bash
sqlite3 restest-out/run.sqlite "SELECT operation, status_code, COUNT(*) FROM interaction GROUP BY operation, status_code ORDER BY operation, status_code"
```

```
TO BE RUN: the stored run by operation and status
```

Each row's `document` column holds the whole exchange as JSON — what the request was meant to be,
where every value in it came from, what was sent and what came back — in the same shape as a fault
in `report.json`:

```bash
sqlite3 restest-out/run.sqlite "SELECT document FROM interaction WHERE status_code = 500 ORDER BY sent_at_nanos LIMIT 1" | jq .
```

[What a run leaves behind](../report.md#runsqlite) lists every column, and every key of that
document.

The [next chapter](05-plans.md) changes what a run sends.
