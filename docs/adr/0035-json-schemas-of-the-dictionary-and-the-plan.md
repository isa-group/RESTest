# ADR-0035: The dictionary and the plan have published JSON Schemas, held to their readers

**Status:** Accepted
**Date:** 2026-10-13

## Context

A dictionary and a plan are the two files somebody writes for a run, and more and more often that
somebody is a coding agent or a language model working before the run starts. Two things showed
what a file of the wrong shape costs:

- **At M8.3, RESTest refused every dictionary of two campaigns** and ran without them. The refusals
  were in each run's output; nobody read them until the results were in.
- **On 4 October 2026, a small language model wrote dictionaries** for the APIs RESTest is measured
  on. Held to a JSON Schema of the format while it wrote, even a model of three billion parameters
  wrote files RESTest read. That schema was written outside this repository, where nothing kept it
  in step with RESTest's reader.

A JSON Schema is a file that describes the shape of another: which members it holds, what kind of
value each is, which words are allowed. Editors use one to check a file as it is typed, validators
to check it before it is used, and the engines that hold a language model to a shape turn one into a
grammar the model cannot leave.

Both formats were first made simple enough for a schema to describe exactly:
[ADR-0033](0033-a-dictionary-file-is-written-by-operation-and-place.md) left a dictionary one
keying, and [ADR-0034](0034-version-2-of-the-plans-format.md) gave a plan one way of writing each
thing it says.

## Decision

### 1. Two schemas, in `schemas/`

`schemas/dictionary.schema.json` describes a dictionary handed over with `--dictionary`, and
`schemas/campaign.schema.json` a plan handed over with `--campaign`. The list of values to push with
that RESTest carries is keyed by kind of value, not by operation, and is not a file anybody writes:
the dictionary's schema does not describe it.

### 2. Draft-07, and only what the engines accept

Both are written in draft-07, the version of JSON Schema that SchemaStore recommends and Ajv's
default class reads, and that every version of the YAML language server reads; 2020-12 it reads
only from version 1.20.0.

They use only what the open engines that hold a model to a schema — llama.cpp, XGrammar, Outlines
and llguidance — all accept, as read in their code and documentation on 12 October 2026: `type`,
`properties`, `required`, `additionalProperties`, `items`, `minItems`, `maxItems`, `enum`, `const`,
`anyOf`, a `$ref` inside the same file, `minLength`, `minimum` and `maximum` on whole numbers,
`title` and `description`. Never `oneOf`, `allOf`, `not`, `if`, `patternProperties` or `pattern`.
And:

- **every part that may be an object says whether it takes members it does not name**, since the
  engines assume different things when it is left out;
- **nothing is written beside a `$ref`**, which some engines ignore;
- **a part with a description has a type**, which Outlines requires, and a choice of shapes
  (`anyOf`) carries no description, which goes on the member that holds it instead;
- **"or nothing" is a list of types**, `["object", "null"]`, which every engine checked accepts;
- **"any value" is the six kinds of JSON value listed**, because llama.cpp reads an empty schema as
  "any object";
- **members are listed in the order the documentation writes them**, since a model held to a schema
  writes them in that order.

### 3. Each schema says the format in words

Every member carries a short description of what it is and what it takes, so that an agent given
nothing but the schema can write a file. Each schema's own description says what RESTest checks that
the schema cannot.

### 4. The address of a schema names its release

Each schema's `$id` is its file at the tag of a release:
`https://raw.githubusercontent.com/isa-group/RESTest/v<version>/schemas/<file>`. It names the release
being prepared: the version in the root `pom.xml` on a release, and a version no older than the
snapshot in between, the same in both files. The release steps in `docs/ci.md` move it, and a test
fails until they do.

### 5. They ship with every release

In a checkout they are in `schemas/`; a release's archive carries them in `schemas/`, and the image in
`/opt/restest/schemas/`. `check-restest.sh` checks that the image, and a launcher in a release's
folder, carry both.

### 6. A test holds them to the reader

`PublishedSchemasTest` asks RESTest's reader and the schema about the same files, read the way
RESTest reads them, and requires the same answer:

- every whole dictionary and plan the documentation shows, and the plan RESTest carries, is read by
  RESTest and valid by its schema;
- files RESTest reads although they are written loosely are valid;
- files that each break one thing in a valid one, and that RESTest refuses for their shape, are
  invalid, at the place and by the rule expected;
- what only RESTest can judge is refused by RESTest and valid by the schema, and what RESTest reads
  and only reports is valid;
- the closed lists of words in the plan's schema — kinds, sources, methods — are the words RESTest
  lists when somebody misspells one.

It also checks each schema against draft-07's own, against the keywords and rules of §2, and its
`$id` against the version. The validator RESTest judges replies with, confined to `restest-oracles`
in production code, is used by that test at test scope only.

### 7. A schema is help, never a requirement

RESTest reads a file that names no schema. A `$schema` member inside a file stays refused, like any
member the format does not have: allowing one later breaks no file, and refusing it later would. A
YAML file names its schema for an editor in a comment, and a JSON file in the editor's settings.

### 8. The reader agrees with the schema about `description`

A dictionary's `description` is a piece of text, or nothing — written with nothing after it, to be
filled in later. Until now the reader took anything there. It now refuses anything else, as the
schema does, and 2.2.0's release notes say so beside ADR-0033's line.

## What a schema cannot say

RESTest refuses, and the schema lets through: shares, or weights in one group, that do not add up to
100; a group with fewer than two sources; two strategies with one name; a name of nothing but
spaces; `fuzzing` as the name of a dictionary or of a dictionary a plan draws on; a key written twice.

RESTest reads, and only reports: an operation the API does not have, in a dictionary or under a
plan's `only:`; a dictionary a plan draws on that nobody handed over.

The schema is stricter in one place: it offers HTTP methods in capitals and in small letters, and
RESTest also reads `Get`.

## Checked by hand

On 13 October 2026, each schema was given to llama.cpp's converter to its grammars (its last
version written in Python, of 3 September 2026, matched by llguidance), to llguidance 1.3.0 and to
outlines-core 0.2.14. Each accepted every whole file the documentation shows, and refused files with
a member, a word or a kind of value wrong — except that Outlines does not hold whole numbers to their
smallest and largest, so it lets through a share of 0. XGrammar was not run; its converter's code
handles every keyword of §2. check-jsonschema 0.36.2 produced the output `docs/schemas.md` shows.

## Consequences

- **An editor or a model gets a file's shape right before a run**, and RESTest still checks what a
  shape cannot say.
- **A change to either format changes its schema in the same pull request**: the test fails
  otherwise, on the documentation's examples and on its own rows.
- **A release moves the schemas' `$id`**, and so does the pull request after it that moves `master` on.
- **The archive and the image grow by the two files**, 11 KB.
- **The settings file has no schema**; `docs/known-limitations.md` says so.

## Alternatives considered

- **Draft 2020-12**, the newest. Editors and validators that read only draft-07 are still common,
  and nothing in these formats needs what 2020-12 adds.
- **Schemas generated from the code.** The readers are written by hand, with no description of the
  format to generate one from; a generated schema would still need this test to show it agrees.
- **Schemas written in YAML**, as Web Fuzzing Commons writes its own. Editors and engines take JSON.
- **Schemas the strict modes of hosted model services accept.** They refuse members whose names are
  not listed in advance — the operations of a dictionary, the dictionaries of a group — and one of
  them requires every member to be present. Neither format can be written that way.
- **`oneOf` for a line of `sources:`**, which says "exactly one of these". The engines refuse it; an
  `anyOf` of three closed objects, each requiring its own member, cannot match twice anyway.
- **SchemaStore**, which gives editors a schema without a comment in the file. It matches schemas
  to files by their names, and a dictionary or a plan can be called anything.
- **A schema of one API's dictionary**, whose only keys are that API's operations and places. It
  describes one API, not the format, and is not part of this decision.
