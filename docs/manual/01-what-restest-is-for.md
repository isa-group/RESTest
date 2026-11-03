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
- **What it can judge is limited to what it can see.** It can tell that a reply was a crash, was the
  wrong shape, or was a success where the document called for a refusal. It cannot tell that a reply
  was the wrong answer — that an order total was miscalculated, say — because nothing in a document
  says what the right answer is.

## What it finds

A run reports eight kinds of fault. Each is an **error** — the API failing at its job: it fell over,
never answered, or accepted what its own document forbids — or a **warning** — the API answering
something its document does not describe, where the document may be the one out of date. A run that
finds an error ends with `1`, so that a build goes red; warnings are counted and said, and end a run
with `1` only when you ask with `--set checks.failOnWarnings=true`.

| Title | Severity | Code | What happened |
|---|---|---|---|
| Internal server error | error | `F100` | The API answered `500 Internal Server Error`: something went wrong inside it that it did not expect |
| Other server error | error | `F121` | The API answered another server error, such as `503`: something in it, or in front of it, failed |
| Invalid data accepted | error | `F206` | The API accepted a request RESTest broke on purpose, which its document says it must refuse |
| No reply in time | error | `F312` | The API took a request and sent no reply before RESTest stopped waiting: something it waits on does not answer |
| Reply off schema | warning | `F200` | The API answered with a body that is not the shape its document gives for that reply |
| Not implemented | warning | `F205` | The API answered `501 Not Implemented` to an operation its document declares |
| Undeclared status code | warning | `F920` | The API answered with a status code its document does not declare for that operation, such as `201` where it promises `200` |
| Undeclared content type | warning | `F921` | The API sent a body under a content type its document does not declare for that reply, such as `application/problem+json` where it promises `application/json` |

Each fault comes with the request that caused it, what the API answered, and a `curl` command that
sends the same request again, so that whoever fixes the API can see the fault for themselves.

A fault is **a reason to look, not a verdict**. A 500 can come from a database that was down rather
than from a bug, a reply of the wrong shape can be a mistake in the document rather than in the API,
a broken request accepted can be the library the API reads its requests with, more lenient than its
document, a request never answered can be a service the API calls that is down, and a status code
or a content type the document does not declare is more often a line missing from the document
than a fault in the API. Either way, somebody who knows the API should look at it.

## How it goes about it

You give RESTest a time budget, a minute by default, and it uses all of it, reading the document
included.

1. **It reads the document**, and sets aside what it cannot test — a file upload the API insists on,
   say — naming each operation and the reason, so that `no faults found` is never read as covering
   an operation that was never tried.
2. **It sends every operation once**, with the request it thinks most likely to be accepted, before
   anything is left to chance: lists first, then creations, reads of one thing, changes and
   deletions, each step waiting for the answers to the one before so that an identifier just handed
   back can be used by the next ([the first round of a
   run](../campaign-format.md#the-first-round-of-a-run)).
3. **Then, until the budget runs out, it draws requests**, and mixes several kinds:
   - nearly half are built to be **accepted**, from the values the document states, from values
     invented to fit what the document says, from values you hand it, and from what the API has
     already returned — an identifier the API handed back is a better guess than an invented number;
   - nearly a third take a request the API **accepted** and send it again with exactly **one thing
     changed**, which gets past every check the API makes but one, and is where the failures an
     ordinary request never reaches tend to be; until the API has accepted something that can be
     changed, such a draw builds a request meant to be accepted instead, so fewer requests than that
     are sent this way ([changing one
     thing](../campaign-format.md#changing-one-thing-in-a-request-that-worked));
   - about one in seven may turn a creation into the first step of a short **series** about the
     thing created — delete it and read it again, create it twice — and builds an ordinary request
     when what it drew is not a creation
     ([series](../campaign-format.md#series-of-requests-around-a-thing-the-run-created));
   - a tenth **push** at the API with values nobody sensible would send — an empty word, a number one
     past the largest 32-bit integer, text where a number belongs — since an API that falls over on
     one of those has a fault whatever was sent ([how much of a run
     pushes](../campaign-format.md#how-much-of-a-run-pushes-at-the-api)).
4. **It judges every reply**, for the eight kinds of fault above.

The share of the budget in which RESTest had nothing in flight is reported as *idle*, and kept as
close to nothing as possible.

Because a run learns from the API's replies, running the same command twice makes two similar runs
rather than the same one, even with the same seed. Chapter 3 shows how to keep every request and
reply of the run you had, and [getting the seed back](../switches.md#getting-the-seed-back) has the
files that make a run repeatable from its seed.

Chapter 5 says how to change that mix, and which operations a run may touch.

## A word of warning

**RESTest writes to whatever API it is pointed at.** It tests an operation that creates something by
creating something, and one that deletes by deleting. Point it at a copy of your API — on your own
machine, or in a test environment — and never at one whose data matters. If a run must stay away
from writes, chapter 5 shows how to keep it to the requests that change nothing.

## When it is useful

- **With a coding agent**, which is where RESTest helps most. While the agent develops an API,
  RESTest checks that what it builds keeps the promises of its document, and serves as its sensor:
  each fault comes with the request that shows it, which points the agent, and you, at what is
  wrong, and sending that request again checks the fix. [RESTest and coding
  agents](../for-coding-agents.md) says how to ask an agent for it.
- **Before a release**, to find the requests that make an API fall over.
- **In continuous integration**, as a check that goes red when a change breaks the API, and, if you
  ask, when the document stops matching it. Chapter 9 shows how.
- **On an API you do not know**, to find out quickly what its document promises and what it keeps.

## Before you start

**What you need.** A computer with Docker, which runs RESTest with nothing else installed and runs the
practice API every example tests. Without Docker, RESTest installs with Homebrew on macOS and Linux,
or with jbang on any system, or runs on Java 21 or later, and the examples can use the public demonstration API the next chapters
name. [Chapter 2](02-installing.md) says how to install RESTest each way, or to build it from its
source. Several chapters also use three small
command-line tools many systems already have: `curl`, to ask the API something directly, [`jq`](https://jqlang.org/), to read JSON, and
[`sqlite3`](https://sqlite.org/cli.html), to read a stored run. RESTest itself needs no account, no
server of its own and no configuration.

**How the examples are written.** Commands are for a shell such as bash or zsh, and write `restest`
for RESTest, however it was installed — chapter 2 shows how to make that one word run it:

```bash
restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s
```

What they print is shown the way it appears, in a block of its own, copied from a real run. The
numbers in it are that run's; yours will be different, because a run sends values chosen by chance
and learns from what the API answers.

The [next chapter](02-installing.md) installs RESTest.
