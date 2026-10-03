# What RESTest is for

A REST API promises things in its OpenAPI document: which operations it has, what each one accepts,
and what each one answers. RESTest checks those promises from the outside. It reads the document,
invents requests from it, sends them to the running API for as long as you allow, and tells you
where the API broke or answered something its own document says it never would.

## Testing from the outside

RESTest is a **black-box** tester. It never sees the API's code, its database or its logs: the
document and the replies are all it has. That has two consequences worth knowing from the start.

- **It works with any API**, whatever language it is written in and wherever it runs, as long as
  RESTest can reach it over HTTP and has a document describing it. The document can be OpenAPI 2.0,
  3.0 or 3.1, as a file or at an address.
- **What it can judge is limited to what it can see.** It can tell that a reply was a crash, or was
  the wrong shape. It cannot tell that a reply was the wrong answer — that an order total was
  miscalculated, say — because nothing in a document says what the right answer is.

## What it finds

A run reports two kinds of fault:

| Code | What happened |
|---|---|
| `F100` | The API answered `500 Internal Server Error`: something went wrong inside it that it did not expect |
| `F200` | The API answered with a body that is not the shape its document gives for that reply |

Each fault comes with the request that caused it, what the API answered, and a `curl` command that
sends the same request again, so that whoever fixes the API can see the fault for themselves.

A fault is **a reason to look, not a verdict**. A 500 can come from a database that was down rather
than from a bug, and a reply of the wrong shape can be a mistake in the document rather than in the
API. Either way, somebody who knows the API should look at it.

## How it goes about it

You give RESTest a time budget, a minute by default, and it uses all of it. Before anything is left
to chance it sends every operation once, with the request it thinks most likely to be accepted. Then
it keeps sending, and mixes several kinds of request:

- requests built to be **accepted**, from the values the document states, from values invented to
  fit what the document says, from values you hand it, and from what the API has already returned —
  an identifier the API handed back is a better guess than an invented number;
- requests that **push** at the API with values nobody sensible would send: an empty word, a number
  too large to hold, text where a number belongs;
- requests that take one the API **accepted** and send it again with exactly **one thing changed**,
  which is where the failures an ordinary request never reaches tend to be;
- short **series** about something a run created: delete it and read it again, create it twice.

Chapter 5 says how to change that mix, and which operations a run may touch.

## A word of warning

**RESTest writes to whatever API it is pointed at.** It tests an operation that creates something by
creating something, and one that deletes by deleting. Point it at a copy of your API — on your own
machine, or in a test environment — and never at one whose data matters. If a run must stay away
from writes, chapter 5 shows how to keep it to the requests that change nothing.

## When it is useful

- **Before a release**, to find the requests that make an API fall over.
- **In continuous integration**, as a check that goes red when a change breaks the API or the
  document stops matching it. Chapter 9 shows how.
- **On an API you do not know**, to find out quickly what its document promises and what it keeps.

## Before you start

**What you need.** A computer with Java 21 or later and git, to build and run RESTest, and Docker,
for the practice API every example runs against. [Chapter 2](02-installing.md) says how to check
each. Chapters 3 and 4 also use three small command-line tools many systems already have: `curl`, to
ask the API something directly, [`jq`](https://jqlang.org/), to read JSON, and
[`sqlite3`](https://sqlite.org/cli.html), to read a stored run. RESTest itself needs no account, no
server of its own and no configuration.

**How the examples are written.** Commands are for a shell such as bash or zsh, run from the root of
the RESTest checkout:

```bash
./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s
```

What they print is shown the way it appears, in a block of its own, copied from a real run. The
numbers in it are that run's; yours will be different, because a run sends values chosen by chance
and learns from what the API answers.

The [next chapter](02-installing.md) installs RESTest.
