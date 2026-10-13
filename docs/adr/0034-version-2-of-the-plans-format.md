# ADR-0034: Version 2 of the plan's format

**Status:** Accepted
**Date:** 2026-10-13

## Context

[ADR-0023](0023-the-campaign-file.md) settled the plan's format, and [ADR-0027](0027-changing-one-thing-in-an-accepted-request.md)
and [ADR-0028](0028-sequences-over-things-a-run-creates.md) added to it. While a JSON Schema of the
format was being written, so that an agent could check a plan before running it, the schema could not
say precisely what was wrong with a line of `sources:`. A source took one of four shapes, told apart by
which key was present, and an agent's validator, given a source that matched none, could not tell which
was meant. The maintainer judged that a sign of the format's design and decided, on 13 October 2026, to
redefine it before writing its schema.

What the format did badly:

- **The kind of a strategy was said three ways.** `mutates: accepted` and `sends: sequences` were keys
  with one possible value each. Pushing at the API was deduced from naming `dictionary: fuzzing`. Three
  rules existed only because of that: not `mutates` and `sends` together; no series drawing on
  `fuzzing`; and `fuzzing` as a name with a meaning of its own.
- **A source had three forms, one of them a key with one value:** `dictionaries: given`.
- **A weight was required or forbidden depending on where a source sat:** inside a `weighted:` group
  or outside one.

What the plans in use say: there are 39 known plans, 38 handed to the measured campaigns and one in
the competition's package. Every one translates into version 2 without loss. In each, the strategy that
pushes names RESTest's list first and invention after it, never inside a group and never in a strategy
that changes accepted requests or sends series. None names a list of the user's by its name.

## Decision

### 1. A strategy says its kind in one member

`kind:` is required, one of four words:

| `kind` | Each request it builds |
|---|---|
| `nominal` | is meant to work, built from its sources |
| `fuzzing` | pushes at the API: RESTest's own list of awkward values is asked first, and the strategy's sources for whatever that list has nothing sendable for |
| `mutation` | is a request the API accepted, sent again with one thing changed; its sources are what it falls back on |
| `sequences` | is meant to work, and a creation starts a series about the thing it creates |

RESTest's list of awkward values belongs to the kind `fuzzing` and is no longer named as a source. Two
of the three rules above cannot be broken any more, since a strategy has one kind and a series has no
way to name that list. The third, `fuzzing` as a special name, is now only a name no list may take
([ADR-0033](0033-a-dictionary-file-is-written-by-operation-and-place.md)).

The name stays a label, printed in reports. In the plan RESTest carries every name is its kind's word.

### 2. A source has two forms

- `source: <word>`, one of `enum`, `example`, `default`, `observed`, `random` and `dictionaries`. The
  last means every list handed over with `--dictionary`, in the order they were handed over.
- `dictionary: <name>`, one list of the user's by the name inside its file.

Naming a list stays: the maintainer requires it. The two forms keep a word of RESTest's apart from a
name somebody chose, so a misspelt word such as `source: exmaple` is refused rather than taken for a
list nobody handed over.

### 3. A group is a map from each source to its weight

```yaml
- weighted: {example: 15, random: 20, observed: 20, dictionaries: 40, default: 5}
```

RESTest's sources are keys by their words, and the user's lists are under `dictionary:` by name:

```yaml
- weighted:
    random: 30
    example: 10
    dictionary: {pet-clinic: 40, seed-data: 20}
```

A weight can no longer be written outside a group, nor left out inside one. Every weight is a whole
number from 1 to 100, and a group holds at least two.

**A group is asked in one fixed order:** `example`, `random`, `observed`, `dictionaries`, `default`,
`enum`, then the user's lists by name, whatever order the file writes them in. Maps have no order in
YAML or JSON, and tools that rewrite a file sort their keys. With a fixed order, a plan rewritten by
any of them draws the same requests from the same seed. The proportions never depended on the order;
which request a seed drew did. The plan RESTest carries already wrote its groups in this order, so its
runs do not change.

### 4. Every strategy has at least one source

That includes a strategy of kind `fuzzing`, whose sources are asked where RESTest's list has nothing
to send. Without one, those requests would be abandoned unbuilt. Version 1 allowed a pushing
strategy with RESTest's list alone; no known plan used that.

### 5. Version 1 is refused

A plan of version 1 is refused with the message for a version this build does not read. The message
now also says how to go on: "'restest run --print-campaign' prints a plan it reads". Reading both
versions would keep two formats documented and a second reader forever. Translating version 1 with a
warning would keep a promise to read a format nobody writes any more.

### 6. The plan RESTest runs does not change

The reader translates version 2 into the same `Campaign` as before. The kind `fuzzing` becomes
RESTest's list asked first, and the others become the flags they always were. A test pins the whole
plan RESTest carries, with every strategy, source and weight. It was written and passing against
version 1's reader before the format changed, and was not touched after. The run order pinned against
pet-clinic did not change either. So nothing a run does changes, and there is nothing to measure.

## Translation from version 1

| Version 1 | Version 2 |
|---|---|
| `version: 1` | `version: 2` |
| no key (an ordinary strategy) | `kind: nominal` |
| `mutates: accepted` | `kind: mutation` |
| `sends: sequences` | `kind: sequences` |
| `- dictionary: fuzzing` as a strategy's first source | `kind: fuzzing`, and the line goes |
| `- dictionaries: given` | `- source: dictionaries` |
| `- weighted:` with `- source: example` / `weight: 15` … | `- weighted: {example: 15, …}` |
| a list of the user's inside a group, `- dictionary: mine` / `weight: 40` | `dictionary: {mine: 40}` inside the group's map |

**What has no translation:**
- RESTest's list named anywhere but first in a strategy;
- that list inside a group, or in a strategy that changes accepted requests;
- that list alone;
- the same source twice in one group.

None of the known plans does any of these.

## What it overrides

- **ADR-0023 §5, "whether a strategy pushes at the API is derived, not declared".** Its two reasons
  are answered here:
  - "A word whose only job is to be true is a word that can be false." The kind is now what makes the
    strategy push, since the reader turns it into drawing on RESTest's list first. The word and the
    behaviour cannot come apart.
  - "The honest name for the concept is the intent, which a later increment would rename." A
    request's intent has been recorded since M10.1, under its own name, so a strategy's kind no
    longer competes with it.
- **The format of ADR-0023, 0027 and 0028**, as the translation says.
- **ADR-0033 §4 on pushing with a list of one's own**, which named it above `dictionary: fuzzing`: a
  strategy of kind `fuzzing` now asks RESTest's list first, whatever it names.
- **Semantic versioning, and design principle 10.** Every plan of version 1 is refused from 2.2.0, a
  minor version, as [ADR-0033](0033-a-dictionary-file-is-written-by-operation-and-place.md) already
  does for dictionaries. The evidence is its own: every known plan translates. 2.2.0's release notes
  carry one line, with a link to the translation above.

**What it does not override:** [ADR-0015](0015-command-line-contract.md)'s exit codes. A plan that
cannot be read ends the run before anything is sent and answers `2`, as before.

## Consequences

- **Every thing a plan says has one way to be written**, which a JSON Schema can describe exactly.
- **The plan's schema comes next, with the dictionary's.** A format ought to come with its schema,
  so that a file can be checked before it is used. This one ships without, because the schemas, and
  where they live, are the next change, which follows before any release. No published version has
  a format without a schema.
- **Plans outside RESTest move to version 2 when they move to 2.2.0:** those of the evaluation
  campaigns and of the competition's package.
- **Messages say what to write.** For example, a weight outside a group, a group written as a list,
  `fuzzing` named as a source, and `dictionary` confused with `dictionaries`.

## Alternatives considered

Each was shown to the maintainer with examples on 13 October 2026.

- **One namespace for RESTest's words and the user's names**, as `- enum`, `- pet-clinic` and
  `- weighted: {example: 15, pet-clinic: 40}`. It is shorter. But a misspelt word becomes the name of
  a list nobody handed over, which a schema cannot catch.
- **Groups as lists of sources with a `weight`**, as in version 1. They keep "one of several shapes"
  inside a group, and a weight that is required or forbidden by position.
- **The kind of a strategy with three values**, pushing still deduced from naming RESTest's list. That
  keeps a special name among the sources and a rule only the reader can check.
- **Reading both versions, or translating version 1 with a warning.** See decision 5.
