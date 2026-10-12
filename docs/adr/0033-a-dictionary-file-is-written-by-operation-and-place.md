# ADR-0033: A dictionary file is written by operation and place

**Status:** Accepted
**Date:** 2026-10-12

## Context

[ADR-0020](0020-what-a-dictionary-is.md) settled the dictionary format with five keyings, one per
file:

- `type`, the kind of value;
- `format`, the format the document declares;
- `schema`, the name of a shape;
- `name`, the name of a parameter or property anywhere in the API;
- `operationAndParameter`, one place in one operation's request.

Next to the keyings, a bucket `any` applied everywhere. The run ranked lists written for one value
before lists written for a kind. A list of the user's called `fuzzing` joined RESTest's own list to
push with.

Who writes dictionaries has changed since. The way RESTest is meant to be used, as its rules say, is
that a coding agent writes the inputs before a run, with the API's OpenAPI document in front of it.
An agent going through the document can write a value under every operation and place it applies to.
A keying that reaches several places at once saves it nothing.

What the files say, as of 12 October 2026:

- **Every known file uses one keying.** All 165 dictionary files known are keyed by
  `operationAndParameter`. They include the 123 handed to the measured campaigns: written by hand to
  a fixed rule, and written by a local model.
- **Nothing else is used.** None of the 165 uses `any`, and none is called `fuzzing`.
- **Nobody else has written one.** RESTest 2 has only been used in the competition, with those
  files.

What the other four keyings cost:

- **The stale-entry check never ran on them.** The check that says which entries of a file name
  places the API does not have only knew how to judge a file keyed by operation. A file keyed by
  `name` with a misspelt name was read without a word.
- **A user's `fuzzing` list was almost never used.** RESTest's own list was asked first, and it
  answers almost everywhere.
- **A JSON Schema of the format needs conditionals.** Whatever comes under `values` depends on
  `keyedBy`, so the schema has to say "if this, then that". Validators give poor messages for that,
  and the grammar engines of language models cannot express it.

## Decision

### 1. A dictionary file has one keying

`keyedBy` stays required, and it takes one value: `operationAndParameter`.

- **Every list sits under an operation, then under a place** in that operation's request.
- **A file keyed any other way is refused.** The refusal says how to write it: "ids.yaml says its
  values are keyed by 'name'; a dictionary puts each list under its operation and the place in its
  request (keyedBy: operationAndParameter)".
- **There is no transition warning**, and the message does not mention what used to be accepted.

### 2. `any` is not special

A key `any` under `values` is an operation's name like any other.

- `any: [null]` is refused, because what is under an operation must be places.
- `any: {ownerId: [1]}` is reported as an operation the API does not have.
- Values meant for every place of an API have no place in a file written for its places.

### 3. `version` stays 1

Every file keyed by operation keeps working unchanged. Raising the version would refuse all 165
known files in order to tell readers of the removed keyings something, and no such reader exists.

A file that was a valid `version: 1` under 2.1 and is refused now is the cost of this decision. It
is paid by keyings nobody used.

### 4. RESTest's own list stays keyed by type, and its name is its own

The list RESTest carries to push at an API with cannot know any API's places, so it keeps its shape:
- a list per kind of value;
- a bucket for every kind at once.

It is read through a path of its own. A file of the user's can no longer take that shape.

Its name, `fuzzing`, is RESTest's.
- **A file called `fuzzing` is refused**, with a message saying to give it another name and name it
  in the plan's pushing strategy.
- **The plan already allows that:** `- dictionary: mine` above `- dictionary: fuzzing`. Sources are
  asked in order, so the user's values go wherever their list names a place, and RESTest's go
  everywhere else.

### 5. `wrongType` draws on RESTest's list only

The change that sends a value of the wrong kind draws on:
- the lists called `fuzzing`, which can now only be RESTest's own;
- the values it keeps for itself.

It never draws on a list of the user's, whose values belong to the places it names.

### 6. What the keyings held up goes with them

- **The ranking inside `dictionaries: given` goes.** It put lists for one value before lists for a
  kind, and every file is now for one value. The lists are asked in the order they were handed over,
  which is the order the ranking already gave for files keyed by operation.
- **The warning about a user's `fuzzing` list going unused goes.** Such a list can no longer exist.
- **A dictionary no longer says whether it is about one value.** Every file is; RESTest's own list
  is not, and is recognised by its name. This withdraws ADR-0020's consequence that each dictionary
  says so for itself.

The API's memory of what it returned keeps its two ways of keeping values: under the name they came
back under, and whole things under the name of their shape. They are now its own, not borrowed from
the file format.

## What it overrides, and what it does not

**Semantic versioning, and design principle 10.** RESTest's rules make a fix a patch and an addition
a minor version. This is a removal, and it breaks files 2.1 read.
- It ships in 2.2.0, a minor version, by the maintainer's choice, on the evidence above that no such
  file exists.
- 2.2.0's release notes say so.

**ADR-0020's "a published format cannot be quietly replaced once anybody's file is written against
it".** It is replaced loudly instead:
- a refused file is said as the run starts, again at the end of the summary, and under `refused` in
  `report.json`;
- the refusal says what to write.

**What it does not override: [ADR-0015](0015-command-line-contract.md)'s contract.** The commands,
the exit codes and the files a run writes are untouched. This decision is no precedent for changing
any of them in a minor version.

## Consequences

- **One shape, which a schema can describe exactly** and an agent cannot get wrong for want of
  knowing which keying to pick.
- **Every file is now checked for stale entries** as it is read, since every file is keyed by
  operation.
- **Files get longer.** A value good for `ownerId` everywhere is written under every operation that
  takes `ownerId`. An agent does not mind; a person writing by hand repeats lines, or uses YAML's
  own anchors.
- **What goes:**
  - extending RESTest's list to push with by giving a list its name; naming a list in the plan
    replaces it;
  - every keying but one; what they said can be written place by place.
- **The format page, the manual's chapter 6 and the README** show files keyed by operation only. The
  manual's example was run against pet-clinic again: of 98 surnames the search sent in a 30-second
  run, 45 came from the list, which is the "about half" it said before.

## Alternatives considered

- **Deprecate in 2.2.0, remove in 3.0.0.** Read the four keyings with a warning for a version, then
  refuse them. Rejected by the maintainer: the warning would protect files nobody has written.
- **Leave them out of the documentation and the schema, and keep reading them.** The cheapest. It
  leaves code nobody sees and a schema stricter than the reader, which is two answers to "what does
  a dictionary look like".
- **Keep `type` for a user's list called `fuzzing`.** It keeps the one useful thing a user's type
  list did, adding to the values pushed at every place. That keeps two shapes in the public format,
  and the plan already lets a list of the user's be pushed with, at the places it names.
