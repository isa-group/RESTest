# The settings

A setting is a number somebody decided about how the tool behaves — how hard it pushes, how much it
keeps, how deep it goes, how long it waits. You never need to change one: RESTest starts with
nothing beside it and works against an unknown API. When you do need to change one, it is one line,
and you never recompile.

```bash
restest run --print-settings > settings.yaml
# edit a line
restest run api.yaml --url http://localhost:9966 --settings settings.yaml
```

The decisions behind all of this are in [ADR-0025](adr/0025-settings.md).

## Settings are not a plan

Two files, and it is worth being clear which is which.

A **plan** says where a run's values come from and which operations it may touch. It travels with an
API: somebody writes one per API, and it means the same thing on every machine. See
[the campaign file](campaign-format.md).

**Settings** say how the tool behaves. They travel with a machine or with an experiment: a
concurrency that suits your laptop is wrong on the benchmark's machine, and an experiment that turns
a behaviour off should not have to rewrite a file about the API.

The test for which one something belongs in: *would it mean the same thing for a different API on
the same machine?* If yes, it is a setting.

## The four places a setting can be given

Each one wins over the one before it.

| | Where | Looks like |
|---|---|---|
| 1 | What RESTest does when nobody has said otherwise | — |
| 2 | A file named with `--settings` | `maxConcurrency: 8` under `engine:` |
| 3 | An environment variable | `RESTEST_ENGINE_MAX_CONCURRENCY=8` |
| 4 | `--set`, repeated as often as you like | `--set engine.maxConcurrency=8` |

The same words name a setting everywhere. `engine.maxConcurrency` in a file and after `--set`;
`RESTEST_ENGINE_MAX_CONCURRENCY` in the environment, which is the same name in the spelling
environments use — capitals, and an underscore wherever the name has a capital of its own.

**No file is looked for anywhere unless you name it.** Starting the tool with nothing beside it has
to behave the same way in every directory, and a file found by accident is a run nobody can explain
afterwards.

## The template, in full

This is what `restest run --print-settings` writes. It is generated from the settings themselves
rather than kept as a file in this repository, so it cannot drift from what the tool actually does,
and so that it can say where each value came from — which a file sitting in a repository could never
know. A test keeps the copy below in step with it.

Save it, change the lines you care about, delete the rest or leave them, and hand it back with
`--settings`. Every line is optional: what you leave out keeps the value RESTest uses.

```yaml
# The settings this run uses, and where each of its values came from.
#
# Save this, change a line, and hand it back with --settings <file>. The same
# values can be given as --set engine.maxConcurrency=8 on the command line, or as
# RESTEST_ENGINE_MAX_CONCURRENCY=8 in the environment. What is typed on the command
# line wins over the environment, which wins over a file, which wins over what
# RESTest does when nobody has said otherwise.
#
# These are settings of the tool. Where a run's values come from, and which
# operations it may touch, is a plan instead: --print-campaign writes one out.

engine:
  # how long to wait for the API to accept a connection at all
  connectTimeout: "10s"       # default
  # how long to wait for the API to answer once connected
  readTimeout: "30s"          # default
  # how long to wait while sending a request body
  writeTimeout: "10s"         # default
  # the fewest requests kept in flight, however badly the API behaves
  minConcurrency: 1           # default
  # how many requests are in flight before anything is known about the API
  initialConcurrency: 4       # default
  # the most requests ever in flight at once. 1 for a fragile API
  maxConcurrency: 16          # default
  # how much slower than its best answer so far counts as the API struggling
  slowdownFactor: 2           # default
  # how much of a reply body is kept in memory
  maxRetainedResponseBytes: 1048576 # default
  # whether a redirection is followed instead of being reported
  followRedirects: false      # default
  # what the tool calls itself in the User-Agent header
  userAgent: "RESTest/2.0"    # default

schedule:
  # how many requests may await an answer, as a multiple of maxConcurrency
  workAheadFactor: 2          # default
  # how many announcements may await the reports before the run pauses
  announcementsAllowedToPileUp: 1000 # default
  # how long past the deadline to wait for answers already asked for
  stragglerGrace: "10s"       # default
  # whether a run starts by sending every operation once, the request likeliest to work
  openingLap: true            # default
  # how long each step of that opening lap waits for the answers to the one before
  openingLapPatience: "2s"    # default

generation:
  # below this depth, only what the description insists on is built
  optionalNestingDepth: 4     # default
  # where building stops, however insistent the description is
  hardNestingDepth: 8         # default
  # the longest word invented when the description does not demand more
  usualLongestString: 64      # default
  # beyond this, a demanded length is declined rather than built
  longestString: 10000        # default
  # where an invented number starts, when the description states no bottom
  lowestNumber: 0             # default
  # how far above that it may go, when the description states no top
  roomAboveIt: 1000           # default
  # decimal places for a number allowed to have them
  decimalPlaces: 2            # default
  # the most items put in a list when the description does not demand more
  usualMostItems: 4           # default
  # beyond this, a demanded number of items is declined rather than built
  mostItems: 100              # default
  # how often an optional property is included anyway, between 0 and 1
  optionalPropertyChance: 0.5 # default
  # how often a request that merely accepts a body sends it anyway, between 0 and 1; a GET or a HEAD never does
  optionalBodyChance: 0.5     # default
  # how often one more optional parameter is added on top of the ones already chosen, between 0 and 1
  optionalParameterContinueChance: 0.5 # default
  # one time in this many, a value allowed to be absent is sent as nothing
  nullInOneIn: 8              # default
  # how many times a fresh element is attempted for a list of distinct items
  uniqueAttempts: 8           # default
  # how many times a value is invented again after an unsendable one
  sendableAttempts: 8         # default
  # how many bodies are drawn while looking for one its media type can carry
  writableBodyAttempts: 8     # default

memory:
  # how many values the run remembers under any one name. 0 remembers none
  mostValuesUnderOneName: 20  # default
  # how many different names are remembered at all
  mostNames: 2000             # default
  # how long one remembered word or number may be, written out
  longestValueKept: 10000     # default
  # the largest reply the run reads looking for values to remember
  longestReplyRead: 524288    # default
  # how far into a reply that search goes
  asDeepAsAReplyIsRead: 6     # default

document:
  # how long to wait for a description fetched over the network
  fetchTimeout: "10s"         # default
  # the largest description that is read at all
  mostBytesRead: 67108864     # default

report:
  # how many faults of one kind, on one operation, are written out whole
  writeUpsPerOperationAndKind: 5 # default
  # and how many in the whole file
  writeUpsInTotal: 1000       # default
  # how much of any one body the report quotes
  mostBodyBytesKept: 24576    # default
  # how many faults are printed in full before the screen stops being the place
  faultsShownOnTheConsole: 50 # default
```

## What a settings file looks like

```yaml
engine:
  maxConcurrency: 1
  readTimeout: 2m

report:
  faultsShownOnTheConsole: 10
```

That is all of it. Groups, and the settings inside them. Anything you leave out keeps the value
RESTest uses by default, so a file naming one line is a perfectly good file.

`engine.maxConcurrency: 1` on a line of its own works too, which is what somebody who has only ever
seen the command line will write.

What `--print-settings` writes is a file of exactly this shape, carrying every setting there is with
a line saying what each one does and a note saying where its value came from. The notes are
comments, so handing the file straight back changes nothing.

## What is refused

A run that could not be configured the way you asked does not happen. Running with different
numbers would answer a question nobody put, so RESTest stops before it sends anything and answers
`2`, *the command line was wrong*.

- **A setting that does not exist** is refused, naming the one it most looks like.
  `--set engine.maxConcurrancy=8` answers *did you mean `engine.maxConcurrency`?*
- **A value the setting cannot take** is refused, saying what it wanted: *engine.maxConcurrency
  takes a whole number, and 'lots' is not one.*
- **A value outside its range** is refused with the range, and with the other setting it disagrees
  with when there is one.
- **A file that cannot be read** is refused, naming the file.
- **A value the tool could not write back out** is refused: a length of time finer than a
  millisecond or longer than milliseconds can count, a number too large for the machine to hold, a
  number that would take more than a thousand characters to write out. Every setting has to survive
  being printed and recorded, or `--print-settings` and `report.json` would break on it later.

## Where a run says how it was configured

Two places, and you need neither to be watching.

`report.json` carries a `settings` block with every setting, its value, and which of the four places
decided it — so a directory of results carries the configuration that produced it, and two runs can
be compared line by line.

```json
{ "key": "engine.maxConcurrency", "value": "1", "source": "command line" }
```

And a run whose settings are not the ones RESTest ships says so on the screen, once, because an
environment variable is invisible in the command you typed and in the transcript you paste into a
bug report.

One source is worth knowing about: **worked out**. A few settings are places inside a range, and
moving the range moves them. `--set engine.maxConcurrency=1` also moves where the engine starts,
without you mentioning it, and that value is recorded as *worked out* rather than as a default —
because it is not what the tool does by default, and two results directories would otherwise carry
different values under the same word.

## The lengths of time

Anywhere a setting is a length of time, it is written the way people write one: `500ms`, `30s`,
`5m`, `2h`. A bare number means seconds. The formal spelling `PT1M30S` works too. It is the same
spelling `--budget` takes.

## Every setting

### `engine.*`

| Setting | Default | What it does |
|---|---|---|
| `connectTimeout` | `10s` | how long to wait for the API to accept a connection at all |
| `readTimeout` | `30s` | how long to wait for the API to answer once connected |
| `writeTimeout` | `10s` | how long to wait while sending a request body |
| `minConcurrency` | `1` | the fewest requests kept in flight, however badly the API behaves |
| `initialConcurrency` | `4` | how many requests are in flight before anything is known about the API |
| `maxConcurrency` | `16` | the most requests ever in flight at once. 1 for a fragile API |
| `slowdownFactor` | `2` | how much slower than its best answer so far counts as the API struggling |
| `maxRetainedResponseBytes` | `1048576` | how much of a reply body is kept in memory |
| `followRedirects` | `false` | whether a redirection is followed instead of being reported |
| `userAgent` | `RESTest/2.0` | what the tool calls itself in the User-Agent header |

### `schedule.*`

| Setting | Default | What it does |
|---|---|---|
| `workAheadFactor` | `2` | how many requests may await an answer, as a multiple of maxConcurrency |
| `announcementsAllowedToPileUp` | `1000` | how many announcements may await the reports before the run pauses |
| `stragglerGrace` | `10s` | how long past the deadline to wait for answers already asked for |
| `openingLap` | `true` | whether a run starts by sending every operation once, the request likeliest to work |
| `openingLapPatience` | `2s` | how long each step of that opening lap waits for the answers to the one before |

### `generation.*`

| Setting | Default | What it does |
|---|---|---|
| `optionalNestingDepth` | `4` | below this depth, only what the description insists on is built |
| `hardNestingDepth` | `8` | where building stops, however insistent the description is |
| `usualLongestString` | `64` | the longest word invented when the description does not demand more |
| `longestString` | `10000` | beyond this, a demanded length is declined rather than built |
| `lowestNumber` | `0` | where an invented number starts, when the description states no bottom |
| `roomAboveIt` | `1000` | how far above that it may go, when the description states no top |
| `decimalPlaces` | `2` | decimal places for a number allowed to have them |
| `usualMostItems` | `4` | the most items put in a list when the description does not demand more |
| `mostItems` | `100` | beyond this, a demanded number of items is declined rather than built |
| `optionalPropertyChance` | `0.5` | how often an optional property is included anyway, between 0 and 1 |
| `optionalBodyChance` | `0.5` | how often a request that merely accepts a body sends it anyway, between 0 and 1; a GET or a HEAD never does |
| `optionalParameterContinueChance` | `0.5` | how often one more optional parameter is added on top of the ones already chosen, between 0 and 1 |
| `nullInOneIn` | `8` | one time in this many, a value allowed to be absent is sent as nothing |
| `uniqueAttempts` | `8` | how many times a fresh element is attempted for a list of distinct items |
| `sendableAttempts` | `8` | how many times a value is invented again after an unsendable one |
| `writableBodyAttempts` | `8` | how many bodies are drawn while looking for one its media type can carry |

### `memory.*`

| Setting | Default | What it does |
|---|---|---|
| `mostValuesUnderOneName` | `20` | how many values the run remembers under any one name. 0 remembers none |
| `mostNames` | `2000` | how many different names are remembered at all |
| `longestValueKept` | `10000` | how long one remembered word or number may be, written out |
| `longestReplyRead` | `524288` | the largest reply the run reads looking for values to remember |
| `asDeepAsAReplyIsRead` | `6` | how far into a reply that search goes |

### `document.*`

| Setting | Default | What it does |
|---|---|---|
| `fetchTimeout` | `10s` | how long to wait for a description fetched over the network |
| `mostBytesRead` | `67108864` | the largest description that is read at all |

### `report.*`

| Setting | Default | What it does |
|---|---|---|
| `writeUpsPerOperationAndKind` | `5` | how many faults of one kind, on one operation, are written out whole |
| `writeUpsInTotal` | `1000` | and how many in the whole file |
| `mostBodyBytesKept` | `24576` | how much of any one body the report quotes |
| `faultsShownOnTheConsole` | `50` | how many faults are printed in full before the screen stops being the place |

## Two things worth knowing

**Moving the concurrency range moves where the engine starts.** `initialConcurrency` is where to
begin inside the range rather than a number of its own, so `--set engine.maxConcurrency=1` works as
typed and gives you one request at a time. Naming a starting number outright that lies outside the
range is still refused.

**`roomAboveIt` is a width, not a ceiling.** When a description states no bounds for a number,
RESTest invents one between `lowestNumber` and `lowestNumber + roomAboveIt`. When the description
states a bottom of its own, that bottom is used and the same room is allowed above it, so the room
to move in is the same wherever the numbers begin.

## What is not a setting

A number that is a fact rather than a decision stays in the code: `500` is a server error, a file
format has a version, a length of a day is a length of a day. The rule for moving one is whether a
reasonable user or a reasonable experiment might want a different value.

Some decisions have not moved yet, on purpose — the internals of the builder that satisfies a
spelling rule, the limits on how deeply a description may nest, the size of the batches the run's
own file is written in. They move when the code around them is next touched, rather than in a sweep.
