# The switches

A switch is a setting that is `true` or `false` and turns off one thing RESTest does. Nearly every
idea a run puts into practice has one; [a few have none by choice](#some-with-no-switch-by-choice),
and [a few older ones have none yet](#what-has-no-switch-yet). There are two reasons for them.
Somebody whose API dislikes one of them can turn it off in a line. And what each one is worth can be
measured by running the same tool twice, once with it and once without, rather than by building two
versions of the tool. A comparison of that kind is called an *ablation*, and this page is written
for whoever is planning one as much as for whoever wants one thing off. Sending a key somebody hands over has no switch, because it happens only when asked
for; [a section further down](#a-key-the-api-asks-for) says why.

```bash
restest run api.yaml --url http://localhost:9966 --set schedule.openingLap=false
```

Switches are settings, so they are given the ways any setting is — in a file named with
`--settings`, in the environment, or after `--set` — and [the settings](settings.md) explains how.

This page lists every switch, the idea it is part of, what turning it off does to a run and what
leaving it on costs. It gives, ready to save and hand over, the files that turn off one idea, or a
whole family of them. After the files, the page says what a run can do without that is not a switch
at all, because it belongs to the plan.

A test keeps the page and the tool in step. Every switch there is appears here, every switch named
here exists, every file below is one the tool accepts and does what its label says, and the plan
below is the one RESTest ships with one thing taken out. The decision behind all of it is
[ADR-0025](adr/0025-settings.md).

## Every switch

### Reaching every operation, and early

| Switch | By default | Part of | Off, a run… |
|---|---|---|---|
| `schedule.openingLap` | `true` | The opening lap | starts drawing requests at once, instead of first sending every operation once with the request it is likeliest to accept |
| `memory.identifiersByResource` | `true` | Identifiers by resource | fills a gap such as `{petTypeId}` in `/pettypes/{petTypeId}` only with a value some reply carried under that very name, and no longer with the `id` of one of the pet types `GET /pettypes` listed |
| `memory.identifiersByResourceFirst` | `true` | The order identifiers are looked for in | asks for a value carrying the gap's own name first, and for the things of its kind only when there is none. It changes nothing while `memory.identifiersByResource` is off |
| `generation.impliedFormats` | `true` | Implied kinds | invents an ordinary word where a name or a description implies a kind the document does not declare — an e-mail address for `billing_email`, a test card number for `ccNumber`, a country code where the description says "ISO 3166", a date in the form the description writes out, a password strong enough for the usual rule for `password`, a short fresh user name for `username`, a person's name of letters for `firstName`, a fresh name of letters for a bare `name`, a gender for `gender`. How often it sends the implied kind when on is `generation.impliedFormatChance`, a number rather than a switch |
| `memory.rememberAcceptedRequests` | `true` | Accepted values | remembers only what the API's replies carried, and no longer the values of the requests it accepted — the e-mail address and password a registration went with are then not there for the login after it |
| `memory.pluralIdentifiers` | `true` | Plural and name gaps | fills a gap named for several identifiers, such as `{ids}` in `/persons/{ids}` or `{petIds}`, only with a value some reply carried under that very name, and no longer with the `id` of one of the persons the API returned. It changes nothing while `memory.identifiersByResource` is off |
| `memory.namesByResource` | `true` | Plural and name gaps | fills a gap named for a thing's name, such as `{productName}`, only with a value some reply carried under that very name, and no longer with the `name` of one of the products the API returned; and no longer keeps a reply that is a plain list of words, such as `["car", "bike"]` from `GET /products`, as the names of that many products. A gap called `{name}` takes the `name` of the things its address is about either way. It changes nothing while `memory.identifiersByResource` is off |
| `generation.omitHalProperties` | `true` | HAL's own properties | sends `_links` and `_embedded` in a body when it was built with them, at any depth — invented to the description, sent back from a reply or taken from a sample. HAL keeps those two names for what a server writes, and an API built on HAL reads them in a request its own way, whatever its description says they look like |
| `generation.freshWhereMade` | `true` | Made afresh | fills the body of a `POST` the way it fills any other body: a property the API has returned a value for — a user name, an e-mail address — may be given that value, and a registration carrying one is refused as already made. How often, when on, such a property is invented afresh instead is `generation.freshWhereMadeChance`, a number rather than a switch. A property named like an identifier is given what the API returned either way |
| `generation.optionalParametersBySize` | `true` | Optional parameters by number | decides each optional parameter on a coin of its own, weighted by `generation.optionalBodyChance`, so the request carrying only what the API requires is drawn once in 10ⁿ attempts for an operation with n optional parameters, rather than about half the time. That is at the coin's default of 0.9; with `generation.optionalBodyChance` at 0.5, it is once in 2ⁿ. [The settings](settings.md#five-things-worth-knowing) say why |

### Breaking things in more ways

What a strategy of kind `mutation` may change in a request the API accepted. A kind
of change is made only when its own switch and `mutation.violations` are both on.

| Switch | By default | Part of | Off, a run… |
|---|---|---|---|
| `mutation.violations` | `true` | One value changed | changes nothing in any request the API accepted: that strategy builds its whole share the way an ordinary strategy with the same sources would, drawing the same numbers. Every other `mutation.*` switch then changes nothing |
| `mutation.dropRequired` | `true` | One value changed | never leaves out a parameter or body property the description says is required |
| `mutation.wrongLocation` | `true` | One value changed | never sends a required parameter somewhere other than its own place: the query, a header or a cookie |
| `mutation.wrongType` | `true` | One value changed | never sends a value of another kind, such as a word where a number is declared |
| `mutation.outsideABound` | `true` | One value changed | never sends a value one step past a limit the description states |
| `mutation.breakAnEnumeration` | `true` | One value changed | never sends a value that is not on a closed list the description states |
| `mutation.breakAPattern` | `true` | One value changed | never sends a word its stated pattern refuses |
| `mutation.breakAFormat` | `true` | Words that only look like their format | never sends a word that only looks like its declared format, such as `2021-02-30` for a `date` or `a@b.` for an `email` |
| `mutation.sendNull` | `true` | One value changed | never sends `null` for a body property that may not be null |
| `mutation.sendEmpty` | `true` | One value changed | never sends an empty word, list or object where the description forbids one |
| `mutation.oversize` | `true` | One value changed | never sends a word or list far longer than the longest the description allows |
| `mutation.wrongRoot` | `true` | Bodies of the wrong shape | never sends the whole body as another kind of thing, such as a list where an object is declared |
| `mutation.emptyBody` | `true` | Bodies of the wrong shape | never sends a body with no bytes in it where the description requires one |
| `mutation.notJson` | `true` | Bodies of the wrong shape | never sends a body that is not JSON |
| `mutation.wrongContentType` | `true` | Bodies of the wrong shape | never sends the accepted body under a media type the operation does not take |
| `mutation.beyondItsWidth` | `true` | Bodies of the wrong shape | never sends a number past what its declared format holds, such as 2147483648 for `int32` |

With every kind of change switched off one by one, a run is exactly the run `mutation.violations`
off gives.

What a strategy of kind `sequences` may send around a thing it created itself. With
all six off, that strategy builds its whole share the way an ordinary one with the same sources
would.

| Switch | By default | Part of | Off, a run… |
|---|---|---|---|
| `sequences.readAfterDelete` | `true` | Series | never reads a thing it created and deleted, to ask whether it is gone |
| `sequences.deleteTwice` | `true` | Series | never deletes the same thing twice |
| `sequences.writeUnderDeleted` | `true` | Series | never adds or changes something under a thing it has deleted |
| `sequences.putTwice` | `true` | Series | never sends the same replacement twice, reading the thing after each |
| `sequences.safeGet` | `true` | Series | never reads a thing again after other reads, to ask whether reading changed it |
| `sequences.createTwice` | `true` | Series | never sends the same creation twice |

### Judging what came back

Each check a run makes on what came back, or did not, reports one kind of fault, named by its number
in the catalogue [the faults](faults.md) describes, or, for the two kinds the catalogue has no number
for, by RESTest's own. What each switch is part of is that kind's title, the words a run prints for
it, and the switch is the title written as one word: *Reply off schema* is `checks.replyOffSchema`.

| Switch | By default | Part of | Off, a run… |
|---|---|---|---|
| `checks.internalServerError` | `true` | Internal server error | never reports a reply of 500 as a fault (`F100`) |
| `checks.otherServerError` | `true` | Other server error | never reports a reply of a server error other than 500 and 501, such as 502, 503 or 504, as a fault (`F121`) |
| `checks.replyOffSchema` | `true` | Reply off schema | never checks the body of a reply against the shape the document gives it, so never reports one that is off it (`F200`) |
| `checks.notImplemented` | `true` | Not implemented | never reports a reply of 501, not implemented, to an operation the document declares, as a fault (`F205`); such replies are still counted under `serverErrors` |
| `checks.invalidDataAccepted` | `true` | Invalid data accepted | never reports a request broken on purpose, with one thing in it the document forbids, that the API accepted (`F206`) |
| `checks.noReplyInTime` | `true` | No reply in time | never reports a request that got no reply, on a connection open to the API, before RESTest stopped waiting, as a fault (`F312`); such requests are still counted under `noReply` |
| `checks.undeclaredStatusCode` | `true` | Undeclared status code | never reports a reply whose status code, below 500, the document does not declare for its operation (`F920`) |
| `checks.undeclaredContentType` | `true` | Undeclared content type | never reports a reply with a body whose content type the document does not declare for its status code, below 500 (`F921`) |

No check decides what a run sends: switching one off changes which replies are called faults, and,
for a check that reports errors — or warnings, with `checks.failOnWarnings` on — the exit code. [The
faults](faults.md#switching-a-check-off) says what else it changes, and what it does not.

### And seven that are not levers

| Switch | By default | Part of | Off, a run… |
|---|---|---|---|
| `checks.failOnWarnings` | `false` | How a run ends | ends with `0` when the only faults it found are warnings, as every run does by default. On, it ends with `1` for a warning as it does for an error. It decides what a run's findings do to how it ends, not what it sends or checks, so it plays no part in an ablation |
| `engine.followRedirects` | `false` | Redirections | reports a redirection rather than following it. It decides what a 3XX answer means rather than turning off something a run could do without, which is why it is off by default and plays no part in an ablation |
| `schedule.stopAfterRequests` | `false` | A run of a known size | ends when its budget is spent. On, it ends once it has sent `schedule.requestsBeforeStopping` requests and their answers are in, or when its budget is spent, whichever comes first, which is how a short run is made the same on a slow machine as on a fast one. It changes when a run ends, not what it sends, so it plays no part in an ablation either |
| `report.html` | `true` | The page | writes no `report.html`, only `report.json`, for a run whose results only a program will read. It changes which files a run leaves, not what it sends or finds, so it plays no part in an ablation |
| `schedule.stopWhenTheOpeningLapIsRefused` | `true` | A run the API refuses | goes on until its budget is spent when the API answers 401 to every request of the opening lap, rather than stopping there and ending with `5`. It changes when a run ends, not what it sends, so it plays no part in an ablation. It does nothing to a run that has no opening lap — `schedule.openingLap` off, or a plan that cannot build the request each operation is likeliest to accept — nor to one whose lap never ends, because its budget or `schedule.requestsBeforeStopping` runs out first: such a run is never stopped this way |
| `engine.limitTheRate` | `false` | A rate promised to the API's owner | sends as fast as its requests in flight and the API's answers allow. On, it never sends two requests closer together than a minute divided by `engine.mostRequestsPerMinute`, evenly and never in a burst after a quiet spell; naming `engine.mostRequestsPerMinute` turns it on by itself. It is a courtesy to the API rather than a way of finding more, so it plays no part in an ablation; it changes when requests go, and so how many fit in a run's time, but makes nothing a run sends depend on the API's replies, so the file for [getting the seed back](#getting-the-seed-back) leaves it as it is |
| `schedule.waitWhenToldTo` | `true` | An API that asks the run to wait | goes on sending to an operation the API answers 429, or 503 with `Retry-After`, as if it had answered anything else, and never holds back the whole API. On, such an operation is left alone for as long as the API says, or for a wait of `schedule.firstGuessedWait` doubling up to `schedule.longestGuessedWait` when it does not say, while the others go on; and when a second operation is told to wait while another waits, nothing at all is sent until the wait is over. It is a courtesy to the API rather than a way of finding more, so it plays no part in an ablation; it changes what is sent only against an API that asks to wait, and then which turns are skipped depends on when the API asked, which is why the file for [getting the seed back](#getting-the-seed-back) turns it off |

The three of these that are on by default are turned off with these files, the first for a campaign
that keeps only what a program reads, the second for one that keeps sending to an API that refuses
it, as RESTest did before it stopped such runs, and the third for one that sends to an API that asks
to wait as RESTest did before it waited:

**The page**

```yaml
report:
  html: false
```

**A run the API refuses**

```yaml
schedule:
  stopWhenTheOpeningLapIsRefused: false
```

**An API that asks the run to wait**

```yaml
schedule:
  waitWhenToldTo: false
```

## What leaving each one on costs

| Idea | Leaving it on costs |
|---|---|
| The opening lap | The first seconds of the run: one request for each operation, in five steps, each waiting up to `schedule.openingLapPatience` for the answers to the one before. Paid out of the budget like everything else |
| Identifiers by resource | A second memory beside the first — the things each kind of address returned — held to the same `memory.*` limits |
| The order identifiers are looked for in | Nothing |
| Implied kinds | Half the invented words at places whose name or description implies a kind, which would otherwise be ordinary words — the share `generation.impliedFormatChance` sets |
| Accepted values | A second source for the memory beside the replies, held to the same `memory.*` limits; the values of every accepted request, filed as a reply's are. A value an API refuses to take twice, such as a user name in a registration, can be refused as already used |
| Plural and name gaps | Nothing beyond the memory of things by kind, where a reply that is a plain list of words is kept as things with one property each, held to the same `memory.*` limits |
| HAL's own properties | One walk over every body before it is sent |
| Made afresh | Three in ten of the `POST` bodies invention builds — the share `generation.freshWhereMadeChance` sets — carry invented values where they would have carried ones the API returned. Now and then a value the API returned is the one it would take, such as the name of a category a new product has to be filed under |
| Optional parameters by number | Nothing |
| One value changed | Its part of the share given to the strategy that changes accepted requests, nearly a third of the run, which builds an ordinary request whenever nothing accepted can be changed; each operation's newest `mutation.acceptedKept` accepted requests, kept in memory; and [the seed](#getting-the-seed-back) |
| Words that only look like their format, and bodies of the wrong shape | Their part of the same share, and the seed |
| Series | The share given to the strategy that sends series, fifteen in a hundred of the run, which builds an ordinary request whenever what it drew is not a creation; a series' later steps go out ahead of ordinary requests, so it takes a little more than its share; and the seed |
| Internal server error | One look at the status code of each reply |
| Other server error | One look at the status code of each reply |
| Reply off schema | Checking the body of every JSON reply against the shape the document gives it, which can leave fewer requests sent within the budget ([the faults](faults.md#switching-a-check-off) says why) |
| Not implemented | One look at the status code of each reply |
| Invalid data accepted | One look at what each request expected, and, for one broken on purpose, at the status code of its reply |
| No reply in time | One look at how each request that got no reply ended |
| Undeclared status code | Finding, for each reply below 500, what the document declares for its status code; one minute's runs could not tell it from the noise, even where most replies were reported |
| Undeclared content type | The same, and a look at the content type of each reply with a body |

What each was measured to be worth while the rewrite was first built is kept with the rest of that
record, on the
tag [`history/2.0-development`](https://github.com/isa-group/RESTest/blob/history/2.0-development/docs/switches.md).

## Switching off one idea

Each file names only what it switches off, so it describes the tool as shipped with that one thing
missing: whatever it leaves out keeps its default. Save one and hand it over:

```bash
restest run api.yaml --url http://localhost:9966 --settings without-series.yaml
```

Only one file can be handed over with `--settings`. To combine two, put their lines in one file, the
way the files for a whole family below do. `--set` wins over the file.

**The opening lap**

```yaml
schedule:
  openingLap: false
```

**Identifiers by resource**

```yaml
memory:
  identifiersByResource: false
```

**The order identifiers are looked for in** — the gap's own name first, then the things of its kind

```yaml
memory:
  identifiersByResourceFirst: false
```

**Implied kinds** — the kinds a name or a description implies

```yaml
generation:
  impliedFormats: false
```

**Accepted values** — the values of the requests the API accepted

```yaml
memory:
  rememberAcceptedRequests: false
```

**Plural and name gaps** — gaps named for several identifiers or for a thing's name

```yaml
memory:
  pluralIdentifiers: false
  namesByResource: false
```

**HAL's own properties** — `_links` and `_embedded` left out of bodies

```yaml
generation:
  omitHalProperties: false
```

**Made afresh** — the body of a `POST` filled with values of its own rather than what the API
returned

```yaml
generation:
  freshWhereMade: false
```

**Optional parameters by number** — how many optional parameters drawn first

```yaml
generation:
  optionalParametersBySize: false
```

**One value changed** — the bodies of the wrong shape are still sent

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

**Words that only look like their format**

```yaml
mutation:
  breakAFormat: false
```

**Bodies of the wrong shape** — and numbers too wide for their format; one value changed is still
sent

```yaml
mutation:
  wrongRoot: false
  emptyBody: false
  notJson: false
  wrongContentType: false
  beyondItsWidth: false
```

**Series**

```yaml
sequences:
  readAfterDelete: false
  deleteTwice: false
  writeUnderDeleted: false
  putTwice: false
  safeGet: false
  createTwice: false
```

**Internal server error** — `F100`

```yaml
checks:
  internalServerError: false
```

**Other server error** — `F121`

```yaml
checks:
  otherServerError: false
```

**Reply off schema** — `F200`

```yaml
checks:
  replyOffSchema: false
```

**Not implemented** — `F205`

```yaml
checks:
  notImplemented: false
```

**Invalid data accepted** — `F206`

```yaml
checks:
  invalidDataAccepted: false
```

**No reply in time** — `F312`

```yaml
checks:
  noReplyInTime: false
```

**Undeclared status code** — `F920`

```yaml
checks:
  undeclaredStatusCode: false
```

**Undeclared content type** — `F921`

```yaml
checks:
  undeclaredContentType: false
```

## Switching off a whole family

The ideas fall into two families: reaching every operation the API will answer, and early, and
breaking things in more ways. The checks on what came back are in neither, since no check decides
what a run sends. These three files, beside the tool as shipped, are the four ways of
running it that an ablation by family compares.

**Reach** — every idea of the first table but the order identifiers are looked for in

```yaml
schedule:
  openingLap: false

generation:
  optionalParametersBySize: false
  impliedFormats: false
  omitHalProperties: false
  freshWhereMade: false

memory:
  identifiersByResource: false
  rememberAcceptedRequests: false
  pluralIdentifiers: false
  namesByResource: false
```

**Break** — every change to an accepted request, and every series. The two strategies that change
accepted requests and send series then build their share the way `nominal` does, so the run is nine
tenths ordinary requests and a tenth pushing at the API.

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
  impliedFormats: false
  omitHalProperties: false
  freshWhereMade: false

memory:
  identifiersByResource: false
  rememberAcceptedRequests: false
  pluralIdentifiers: false
  namesByResource: false

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

`observed` is what lets a run send an identifier the API itself handed back, rather than one
it invented. It is in three of the shipped plan's four strategies, each time inside a group whose
weights add up to a hundred. Taking it out means dividing its weight among the others in the
proportions they already had, as nearly as whole numbers allow: in the plan RESTest ships today, 15,
20, 40 and 5 become 19, 25, 50 and 6. This is that plan with the memory taken out that way. The test
that checks this page holds it to the shipped plan, so that it changes whenever that plan does:

```yaml
version: 2

strategies:

  - name: nominal
    share: 45
    kind: nominal
    sources:
      - source: enum
      - weighted: {example: 19, random: 25, dictionaries: 50, default: 6}

  - name: sequences
    share: 15
    kind: sequences
    sources:
      - source: enum
      - weighted: {example: 19, random: 25, dictionaries: 50, default: 6}

  - name: mutation
    share: 30
    kind: mutation
    sources:
      - source: enum
      - weighted: {example: 19, random: 25, dictionaries: 50, default: 6}

  - name: fuzzing
    share: 10
    kind: fuzzing
    sources:
      - source: random
```

Hand it over with `--campaign`. It takes identifiers by resource away too, since a gap filled from
the things of its kind is filled from the same memory. What the memory is worth was measured when it
was added; the record of the rewrite's first version has the numbers.

A plan written for an earlier version of RESTest may be refused, if its format has changed since,
and otherwise leaves out whatever strategies came after it: one written before accepted requests
were changed, for example, has no strategy that changes them and none that sends series, so it
switches those off too. Start from `restest run --print-campaign` on the version being measured,
or from the plan above.

### A list of values of your own

`dictionaries` has nothing to say until a list is handed over, and its weight passes to the
others in its group until one is. So a run with a list of values and a run without one differ only
in `--dictionary <file>`, and the plan does not change.

### Values nobody sensible would send

The strategy that pushes at the API has a tenth of the run. To give it more or less, change its
share in the plan, and the shares of the others so that they still add up to a hundred; to push at
the API not at all, leave the pushing strategy out and share its tenth among the others. The
changes to accepted requests also send values the document forbids, and are switched off with
`mutation.violations` rather than in the plan. They draw on the list of awkward values too, with or
without the pushing strategy: a value of the wrong kind in a request the API accepted is taken from
it, one value at a time, until `mutation.wrongType` is off.

### What the document writes down

`source: example` and `source: default` are the samples and defaults the document's author wrote.
To go without one, take its line out of every group it is in and divide its weight among the
others, as above. `source: enum` is best left where it is, because a closed list is the whole set of
values the API will take.

### How much of the run each strategy gets

That is the plan's `share:`, out of a hundred. A strategy switched off by its switches keeps its
share and builds it the ordinary way. Taking the strategy out of the plan instead means giving its
share to the others by hand, since the shares have to add up to a hundred, and the run that gives
is not always the one its switches give. For the series it is: given to `nominal`, whose sources it
has, their fifteen in a hundred build the same requests, one for one.

### A key the API asks for

Handing a key over with `--auth` is not a switch either: it is an instruction about one API, and a
run without it is simply a run without `--auth`. Nothing about keys happens unless one is given, and
a run given none sends exactly what it sent before keys could be handed over.

## Getting the seed back

A run is repeated by its `--seed` alone only when nothing it sends depends on what the API answered.
Four things do, and all four are on in the tool as shipped: the memory of what the API returned,
the changes to accepted requests, the series, and leaving alone what the API asks to be left alone,
which skips turns when and where the API says. To get the seed back, hand over the plan above with
`--campaign`, and this file with `--settings`:

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

schedule:
  waitWhenToldTo: false
```

The run says which it is, under the line that gives its seed. While any of the four is on, it
prints `what it sends depends on the API's own replies, so the seed alone does not repeat this run`.
Once all four are off, that line is gone.

## Some with no switch, by choice

The memory of what the API returned keeps a value only under a name some request of the document
asks for — a parameter's, or a property's anywhere in a body — and when it holds as many names as
`memory.mostNames` allows, a new one makes room by letting go of the name heard of longest ago.
Otherwise the first names an API happened to send would be the only ones a run could ever remember:
an API whose diagnostic pages list hundreds of names would fill it in seconds, and a token a login
handed back a minute later would never be kept. A word it remembered is only offered where it is as
long as the document allows, so a password a diagnostic page shows as `******` is not sent where the
document asks for eight characters at least. Turning new names away once the memory was full was not
a behaviour anybody chose, so there is no switch to bring it back.

Three kinds of change draw on more than their first versions did, and none of the three has a switch
of its own, since each widens a kind that already has one:

- a value of the wrong kind is drawn from every awkward value in RESTest's own list to push with
  and a few more it keeps for it; `mutation.wrongType` turns the kind off whole;
- a body that is not JSON is broken in six ways, and a number past its format's width can be
  thousands of digits long; `mutation.notJson` and `mutation.beyondItsWidth` turn those kinds off
  whole;
- the list of awkward values RESTest carries holds 88 values. A plan without the pushing strategy
  sends none of them in requests made entirely of awkward values; a value of the wrong kind still
  draws on them.

The kinds a name implies - among them a password, a user name, a person's name, a name of letters for
a bare `name`, a gender - have no switch of their own either: `generation.impliedFormats` turns every
implied kind off whole.

## What has no switch yet

Two behaviours are older than the settings and have no switch. They are given one when what they do
is next changed — a change to one of their numbers, which may become a setting, is not that — and
until then the tool always does them:

- where an operation wants a whole thing of a shape the API has returned, one it returned is sent
  back with one value in it changed. This is part of the memory, so the plan without the memory
  goes without it too;
- the `Accept` header asks for JSON first, and for anything else after it.

## Where a run says which switches it had

`report.json` carries every setting, its value and where it came from, the switches among them, so a
directory of results says which way of running the tool produced it. A run started with any setting
that is not RESTest's own also says so once on the screen, and `--print-settings` lists which ones
they are.
