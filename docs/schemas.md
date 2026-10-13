# The JSON Schemas of a dictionary and a plan

RESTest reads two files that a person, or a program, writes for it: a
[dictionary](dictionary-format.md), handed over with `--dictionary`, and a
[plan](campaign-format.md), handed over with `--campaign`. Each has a **JSON Schema**: a file, itself
written in JSON, that describes the shape the other file has to have — which members it holds, what
kind of value each one is, which words are allowed where. A **validator** is a program that reads a
file and a schema and says whether the file has that shape, and where it does not. Both schemas are
written in **draft-07**, the version of the JSON Schema standard that editors, validators and the
tools that guide a language model all read.

A schema is help, never a requirement. RESTest reads a file that names no schema, and checks every
file it reads itself.

## Where they are

| Where | The dictionary's | The plan's |
|---|---|---|
| On the web, for the version you run | `https://raw.githubusercontent.com/isa-group/RESTest/v<version>/schemas/dictionary.schema.json` | `https://raw.githubusercontent.com/isa-group/RESTest/v<version>/schemas/campaign.schema.json` |
| In a checkout of the repository | `schemas/dictionary.schema.json` | `schemas/campaign.schema.json` |
| In a release's archive | `restest-<version>/schemas/dictionary.schema.json` | `restest-<version>/schemas/campaign.schema.json` |
| In the container image | `/opt/restest/schemas/dictionary.schema.json` | `/opt/restest/schemas/campaign.schema.json` |

Every release carries the schemas of the formats it reads, and each schema's address on the web is
its file at that release's tag. Use the ones of the version you run: a later version may read a
format an earlier one does not.

To copy one out of the image:

```bash
docker run --rm --entrypoint cat ghcr.io/isa-group/restest:<version> /opt/restest/schemas/dictionary.schema.json > dictionary.schema.json
```

## In an editor

An editor that understands YAML through the YAML language server — Visual Studio Code with its YAML
extension, among others — checks a file against the schema a comment on its first line names, and
offers the members and the words allowed as you type:

```yaml
# yaml-language-server: $schema=https://raw.githubusercontent.com/isa-group/RESTest/v<version>/schemas/dictionary.schema.json
version: 1
name: clinic
keyedBy: operationAndParameter
values:
  listOwners:
    lastName: [Franklin, Davis]
```

RESTest reads that line as the comment it is. A file written in JSON has no comments, and a
`$schema` member inside it is refused like any other member RESTest does not know, so a JSON file is
matched to its schema in the editor's own settings instead.

## With a validator

Any validator that reads draft-07 will do. With
[check-jsonschema](https://github.com/python-jsonschema/check-jsonschema), from a checkout, the
dictionary of [the manual](manual/06-dictionaries.md#a-dictionary-for-the-clinic) is checked with

```bash
check-jsonschema --schemafile schemas/dictionary.schema.json clinic.yaml
```

which answers

```
ok -- validation done
```

With `lastName: [Franklin, Davis, Rodriquez, McTavish, Coleman]` changed to `lastName: Franklin`,
one value where a list of values belongs, it answers

```
Schema validation errors were encountered.
  clinic.yaml::$.values.listOwners.lastName: 'Franklin' is not of type 'array'
```

A plan has one place where a validator says more than one thing. A line of `sources:` is written
one of three ways — `source:`, `dictionary:` or `weighted:` — and a validator, given a line that is
none of them, tries all three and says why each does not fit. With a strategy's `- source: random`
changed to `- source: exmaple`:

```
Schema validation errors were encountered.
  plan.yaml::$.strategies[1].sources[0]: {'source': 'exmaple'} is not valid under any of the given schemas
  Underlying errors caused this.

  Best Match:
    $.strategies[1].sources[0]: Additional properties are not allowed ('source' was unexpected)
  Best Deep Match:
    $.strategies[1].sources[0].source: 'exmaple' is not one of ['example', 'random', 'observed', 'dictionaries', 'default', 'enum']

  3 other errors were produced. Use '--verbose' to see all errors.
```

The line that says what is wrong is the one that goes deepest into the file, here the last: the
word is not one of RESTest's. The others say that the line is not a `dictionary:` or a `weighted:`
either. The output above is check-jsonschema 0.36.2's.

## For a language model

The engines that hold a language model to a schema while it writes — so that whatever it writes has
the shape — read both schemas. Each schema was given to llama.cpp's converter to its grammars (its
last version written in Python, of 3 September 2026), to llguidance 1.3.0 and to outlines-core
0.2.14, and each of them accepted every whole example on these pages and refused files with a member,
a word or a kind of value wrong. XGrammar was not tried; its code handles every part of JSON Schema
these two use.

- **Outlines does not hold a number to the smallest and largest a schema allows**, so a share of `0`
  or a weight of `101` gets through it. RESTest refuses either when it reads the file.
- **A model held to a schema writes the members in the order the schema lists them**, and llama.cpp
  has it write the required ones first, so a dictionary's `description` comes last. The order means
  nothing to RESTest.
- **The strict modes of hosted model services refuse both schemas**, because both hold members whose
  names cannot be listed in advance: the operations of a dictionary, and the dictionaries of a group.
  A model there can still be given the schema to read with its instructions.

## What a schema cannot say

A file valid by its schema is not always a file RESTest reads. A schema describes the shape of one
file, and some of what RESTest checks is not shape.

**RESTest refuses, although the schema lets it through:**

- shares of a plan that do not add up to 100, or weights in one group that do not;
- a group with fewer than two sources in it — `weighted: {}`, or `dictionary: {}` inside a group,
  among them;
- two strategies with one name, and a name made of nothing but spaces, for a strategy or for a
  dictionary a plan names;
- `fuzzing` as a dictionary's name, or as the name of a dictionary a plan draws on: it is the name of
  RESTest's own list of values to push with;
- a key written twice in one place, which YAML allows and RESTest refuses. Some validators refuse it
  too; others read only the second.

**RESTest reads, and says as the run starts:**

- an operation, in a dictionary or under a plan's `only:`, that the API does not have;
- a dictionary a plan draws on that nobody handed over.

**The schema is stricter in one place**: an HTTP method under a plan's `methods:` is written in
capitals or in small letters, and RESTest reads `Get` as well.

## When a validator reads YAML differently

RESTest reads in a YAML file only what JSON itself has — `true`, `false`, `null` and numbers — and
everything else as text ([the dictionary format](dictionary-format.md#where-a-value-goes) says why).
A validator that reads YAML by YAML 1.2's own core rules reads three more things: `~` as null, `True`
and `TRUE` as true, and `0x1` as the number 1. So the two can disagree about one file: `version: 0x1`
is valid to such a validator and refused by RESTest, which finds text where a version belongs, and
`name: True` is the other way round. Quote such a value — `"~"`, `"True"` — and both read it as
text.
