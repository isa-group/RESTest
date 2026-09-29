# The switches

A switch is a setting that is `true` or `false` and turns off one thing RESTest does. Every
behaviour a run can do without has one, for two reasons. Somebody whose API dislikes one of them can
turn it off in a line. And what each one is worth can be measured by running the same tool twice,
once with it and once without, rather than by building two versions of the tool. A comparison of
that kind is called an *ablation*, and this page is written for whoever is planning one.

```bash
restest run api.yaml --url http://localhost:9966 --set schedule.openingLap=false
```

Switches are settings, so they are given the ways any setting is — in a file named with
`--settings`, in the environment, or after `--set` — and [the settings](settings.md) explains how.

This page lists every switch, says what turning it off does to a run, what leaving it on costs and
what it was found to be worth. It gives, ready to save and hand over, the files that turn off
everything one increment added, or a whole milestone. The numbers beside each switch — 9.1, 10.2 —
are the increments of [the roadmap](../ROADMAP.md) that added it. The page ends with what a run can
do without that is not a switch at all, because it belongs to the plan.

A test keeps the page and the tool in step. Every switch there is appears here, every switch named
here exists, every file below is one the tool accepts, and the plan below is the one RESTest ships
with one thing taken out. The decision behind all of it is [ADR-0025](adr/0025-settings.md).

## Every switch

### Reaching every operation, and early

| Switch | By default | Came with | Off, a run… |
|---|---|---|---|
| `schedule.openingLap` | `true` | 9.1 | starts drawing requests at once, instead of first sending every operation once with the request it is likeliest to accept |
| `memory.identifiersByResource` | `true` | 9.2 | fills a gap such as `{petTypeId}` in `/pettypes/{petTypeId}` only with a value some reply carried under that very name, and no longer with the `id` of one of the pet types `GET /pettypes` listed |
| `memory.identifiersByResourceFirst` | `true` | 9.2 | asks for a value carrying the gap's own name first, and for the things of its kind only when there is none. It changes nothing while `memory.identifiersByResource` is off |
| `generation.optionalParametersBySize` | `true` | 2.9 | decides each optional parameter on a coin of its own, so the request carrying only what the API requires is drawn once in 2ⁿ attempts for an operation with n optional parameters, rather than about half the time |

### Breaking things in more ways

What a strategy whose plan says `mutates: accepted` may change in a request the API accepted. A kind
of change is made only when its own switch and `mutation.violations` are both on.

| Switch | By default | Came with | Off, a run… |
|---|---|---|---|
| `mutation.violations` | `true` | 10.1 | changes nothing in any request the API accepted: that strategy builds its whole share the way an ordinary strategy with the same sources would, drawing the same numbers. Every other `mutation.*` switch then changes nothing |
| `mutation.dropRequired` | `true` | 10.1 | never leaves out a parameter or body property the description says is required |
| `mutation.wrongLocation` | `true` | 10.1 | never sends a required parameter somewhere other than its own place: the query, a header or a cookie |
| `mutation.wrongType` | `true` | 10.1 | never sends a value of another kind, such as a word where a number is declared |
| `mutation.outsideABound` | `true` | 10.1 | never sends a value one step past a limit the description states |
| `mutation.breakAnEnumeration` | `true` | 10.1 | never sends a value that is not on a closed list the description states |
| `mutation.breakAPattern` | `true` | 10.1 | never sends a word its stated pattern refuses |
| `mutation.sendNull` | `true` | 10.1 | never sends `null` for a body property that may not be null |
| `mutation.sendEmpty` | `true` | 10.1 | never sends an empty word, list or object where the description forbids one |
| `mutation.oversize` | `true` | 10.1 | never sends a word or list far longer than the longest the description allows |
| `mutation.wrongRoot` | `true` | 10.2 | never sends the whole body as another kind of thing, such as a list where an object is declared |
| `mutation.emptyBody` | `true` | 10.2 | never sends a body with no bytes in it where the description requires one |
| `mutation.notJson` | `true` | 10.2 | never sends a body that is not JSON |
| `mutation.wrongContentType` | `true` | 10.2 | never sends the accepted body under a media type the operation does not take |
| `mutation.beyondItsWidth` | `true` | 10.2 | never sends a number past what its declared format holds, such as 2147483648 for `int32` |

With every kind of change switched off one by one, a run is exactly the run `mutation.violations`
off gives.

What a strategy whose plan says `sends: sequences` may send around a thing it created itself. With
all six off, that strategy builds its whole share the way an ordinary one with the same sources
would.

| Switch | By default | Came with | Off, a run… |
|---|---|---|---|
| `sequences.readAfterDelete` | `true` | 10.3 | never reads a thing it created and deleted, to ask whether it is gone |
| `sequences.deleteTwice` | `true` | 10.3 | never deletes the same thing twice |
| `sequences.writeUnderDeleted` | `true` | 10.3 | never adds or changes something under a thing it has deleted |
| `sequences.putTwice` | `true` | 10.3 | never sends the same replacement twice, reading the thing after each |
| `sequences.safeGet` | `true` | 10.3 | never reads a thing again after other reads, to ask whether reading changed it |
| `sequences.createTwice` | `true` | 10.3 | never sends the same creation twice |

### And one that is not a lever

| Switch | By default | Came with | Off, a run… |
|---|---|---|---|
| `engine.followRedirects` | `false` | 1.3 | reports a redirection rather than following it. It decides what a 3XX answer means rather than turning off something a run could do without, which is why it is off by default and plays no part in an ablation |

## What each one costs, and what it was found to be worth

What the screenings measured, each of them twenty minutes an API and one run per variant. **8.4**
ran on 27–28 September 2026 at `76534ebd`, on sixteen APIs. **8.5** ran on 28–29 September at
`6afcaec3`, on the eleven APIs of the 2026 edition of the competition, counting unique server
failures with erc20 left out. The tables themselves are in [the roadmap](../ROADMAP.md#m8--evaluation).

| Increment | Leaving it on costs | What it was found to be worth |
|---|---|---|
| 9.1, the opening lap | The first seconds of the run: one request for each operation, in five steps, each waiting up to `schedule.openingLapPatience` for the answers to the one before. Paid out of the budget like everything else | 8.4: without it, ten fewer operations answered 2XX across the sixteen APIs, and less area under both curves on thirteen of them |
| 9.2, identifiers by resource | A second memory beside the first — the things each kind of address returned — held to the same `memory.*` limits | 8.4: the first minute of gestao-hospital, five operations covered at ten seconds without it and fifteen to seventeen in every other variant; little elsewhere |
| 9.2's order | Nothing | 8.4: nothing measurable |
| 2.9, optional parameters by number | Nothing | 8.4: nothing measurable, as the increment predicted, since few of the APIs measured have more than one optional parameter anywhere. Left on by the maintainer's decision, for the 232 operations of the wider corpus that have four or more |
| 10.1, one value changed | Its part of the fifth of the run given to the strategy that changes accepted requests; each operation's newest `mutation.acceptedKept` accepted requests, kept in memory; and [the seed](#getting-the-seed-back) | 8.5: 108 unique server failures without it and 147 with it. It also gives branch coverage its early lead, 17.9% at ten seconds against 15.2% |
| 10.2, the body as a whole | Its part of the same fifth, and the seed | 8.5: 113 unique server failures without it and 147 with it |
| 10.3, series | The tenth of the run given to the strategy that sends series, whose later steps go out ahead of ordinary requests, so it takes a little more than its share; and the seed | 8.5: 144 unique server failures without them and 147 with them, which is nothing measurable. Left on by the maintainer's decision |
| All of 10.1, 10.2 and 10.3 | All of the above | 8.5: 71 unique server failures without them and 147 with them |

## Switching off everything one increment added

Each file names only what it switches off, so it describes the tool as shipped with that one thing
missing: whatever it leaves out keeps its default. Save one and hand it over:

```bash
restest run api.yaml --url http://localhost:9966 --settings without-series.yaml
```

Files combine the way settings always do. Two of them can be written as one, and `--set` wins over
a file.

**9.1, the opening lap**

```yaml
schedule:
  openingLap: false
```

**9.2, identifiers by resource**

```yaml
memory:
  identifiersByResource: false
```

**9.2's order, turned back to the name first**

```yaml
memory:
  identifiersByResourceFirst: false
```

**2.9, how many optional parameters drawn first**

```yaml
generation:
  optionalParametersBySize: false
```

**10.1, one value changed.** The body as a whole, 10.2, is still changed.

```yaml
mutation:
  dropRequired: false
  wrongLocation: false
  wrongType: false
  outsideABound: false
  breakAnEnumeration: false
  breakAPattern: false
  sendNull: false
  sendEmpty: false
  oversize: false
```

**10.2, the body changed as a whole.** One value, 10.1, is still changed.

```yaml
mutation:
  wrongRoot: false
  emptyBody: false
  notJson: false
  wrongContentType: false
  beyondItsWidth: false
```

**10.3, series**

```yaml
sequences:
  readAfterDelete: false
  deleteTwice: false
  writeUnderDeleted: false
  putTwice: false
  safeGet: false
  createTwice: false
```

## Switching off a whole milestone

These three, beside the tool as shipped, are the four ways of running it that an ablation by
milestone compares.

**Reach: 2.9, 9.1 and 9.2**

```yaml
schedule:
  openingLap: false

generation:
  optionalParametersBySize: false

memory:
  identifiersByResource: false
```

**Break: 10.1, 10.2 and 10.3.** The two strategies that change accepted requests and send series
then build their share the way `nominal` does. So the run is three quarters ordinary requests and a
quarter pushing at the API, as it was before 10.1.

```yaml
mutation:
  violations: false

sequences:
  readAfterDelete: false
  deleteTwice: false
  writeUnderDeleted: false
  putTwice: false
  safeGet: false
  createTwice: false
```

**Both**

```yaml
schedule:
  openingLap: false

generation:
  optionalParametersBySize: false

memory:
  identifiersByResource: false

mutation:
  violations: false

sequences:
  readAfterDelete: false
  deleteTwice: false
  writeUnderDeleted: false
  putTwice: false
  safeGet: false
  createTwice: false
```

## What is not a switch

A switch turns off something the tool does. Where a run's values come from is not the tool's
behaviour but the plan's — see [the campaign file](campaign-format.md) — so a source is taken out
of the plan and never switched off by a setting. A plan that names a source some setting had
silenced would say something about the run that is not true.

### The memory of what the API returned

`source: observed` is what lets a run send an identifier the API itself handed back, rather than one
it invented. It is in three of the shipped plan's four strategies, each time inside a group whose
weights add up to a hundred. Taking it out means dividing its weight among the others in the
proportions they already had, so 15, 20, 40 and 5 out of 80 become 19, 25, 50 and 6 out of 100.
This is the plan RESTest ships with the memory taken out that way. The test that checks this page
holds it to the shipped plan, so that it changes whenever that plan does:

```yaml
version: 1

strategies:

  - name: nominal
    share: 45
    sources:
      - source: enum
      - weighted:
          - source: example
            weight: 19
          - source: random
            weight: 25
          - dictionaries: given
            weight: 50
          - source: default
            weight: 6

  - name: sequences
    share: 10
    sends: sequences
    sources:
      - source: enum
      - weighted:
          - source: example
            weight: 19
          - source: random
            weight: 25
          - dictionaries: given
            weight: 50
          - source: default
            weight: 6

  - name: mutation
    share: 20
    mutates: accepted
    sources:
      - source: enum
      - weighted:
          - source: example
            weight: 19
          - source: random
            weight: 25
          - dictionaries: given
            weight: 50
          - source: default
            weight: 6

  - name: fuzzing
    share: 25
    sources:
      - dictionary: fuzzing
      - source: random
```

Hand it over with `--campaign`. It takes 9.2 away too, since a gap filled from the things of its
kind is filled from the same memory. 8.3 measured the difference on the five APIs of the 2027
edition, twenty minutes each, at `da17d2ba`: 65 operations answered 2XX without the memory, against
100 with it. Nearly all of the difference is on kafka-rest-proxy, whose operations need an
identifier that only the API itself can supply.

A plan written for an earlier version of RESTest leaves out whatever strategies came after it. A
plan without the memory written before 10.1, for example, has no strategy that changes accepted
requests and none that sends series, so it switches those off too. Start from `restest run
--print-campaign` on the version being measured, or from the plan above.

### A list of values of your own

`dictionaries: given` has nothing to say until a list is handed over, and its weight passes to the
others in its group until one is. So a run with a list of values and a run without one differ only
in `--dictionary <file>`, and the plan does not change.

### Values nobody sensible would send

`--fuzzing 0` leaves out the strategy that pushes at the API and gives its quarter of the run to the
others, in proportion to their shares. `--fuzzing 40` gives it more.

### What the document writes down

`source: example` and `source: default` are the samples and defaults the document's author wrote.
To go without one, take its line out of every group it is in and divide its weight among the
others, as above. `source: enum` is best left where it is, because a closed list is the whole set of
values the API will take.

### How much of the run each strategy gets

That is the plan's `share:`, out of a hundred. A strategy switched off by its switches keeps its
share and builds it the ordinary way. Taking the strategy out of the plan gives its share to the
others instead, which is a different run from the one its switches give.

## Getting the seed back

A run is repeated by its `--seed` alone only when nothing it sends depends on what the API answered.
Three things do, and all three are on in the tool as shipped: the memory of what the API returned,
the changes to accepted requests, and the series. To get the seed back, hand over the plan above
with `--campaign`, and this file with `--settings`:

```yaml
mutation:
  violations: false

sequences:
  readAfterDelete: false
  deleteTwice: false
  writeUnderDeleted: false
  putTwice: false
  safeGet: false
  createTwice: false
```

The run says which it is, under its first line. While any of the three is on, it prints
`what it sends depends on the API's own replies, so the seed alone does not repeat this run`. Once
all three are off, that line is gone.

## Where a run says which switches it had

`report.json` carries every setting, its value and where it came from, the switches among them, so a
directory of results says which way of running the tool produced it. A run started with any setting
that is not RESTest's own also says so once on the screen, and `--print-settings` lists which ones
they are.
