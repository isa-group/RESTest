# Dictionaries

RESTest invents values to fit what the document says: a number within the limits it gives, a date
for a date, a word as long as the document allows. It also reuses what the API has already returned.
But some values cannot be invented. An API that searches owners by surname finds nothing for a
surname made of random letters, and one that wants a product code of its own answers every invented
one with a refusal. If you know values the API will accept, write them in a **dictionary** and hand
it over. The format, with everything it can say, is [The dictionary format](../dictionary-format.md).

## When it is worth it

Look at the summary of a run. If an operation keeps being refused — `4xx` for nearly every request
to it — and the reason is a value only somebody who knows the API could supply, a dictionary is the
answer. Identifiers that exist, names the API holds, codes it recognises: those are what dictionaries
are for.

## A dictionary for the clinic

The clinic searches its owners by surname, and when RESTest creates an owner it has to invent a
telephone number made only of digits. A dictionary with surnames the clinic holds and a few
telephone numbers:

```yaml
version: 1
name: clinic
description: Owners the clinic knows, and telephone numbers it accepts.
keyedBy: name
values:
  lastName: [Franklin, Davis, Rodriquez, McTavish, Coleman]
  telephone: ["6085551023", "6085551749", "6085553198"]
```

Save it as `clinic.yaml` and hand it over with `--dictionary`:

```bash
./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s --dictionary clinic.yaml
```

`--dictionary` takes a file or a directory of them, and can be given more than once.

## The parts of the file

- `version` is the version of the format, `1`.
- `name` is what the list is called. A plan can name a list to say which requests draw on it; the
  plan RESTest carries asks every list you hand over when it builds requests meant to be accepted.
- `keyedBy` says what the keys under `values` are. `name`, used here, means a name wherever it
  appears: `lastName` is the query parameter of the search and the property inside the body that
  creates an owner, and the list fills both. The other keyings are narrower — one place in one
  operation — or wider — every value of a kind, such as every date — and [the
  format](../dictionary-format.md#the-five-keyings) has them all.
- `values` holds the lists.

**A list replaces what RESTest would have sent for the places it covers; it is not added to it.**
With the file above, every `lastName` the run sends is one of those five. That is usually what you
want for values that must exist. For values you only want sent now and then, key the list narrowly,
to the operations where they matter.

## Checking that it was read

A file RESTest cannot read is said before the first request, again at the end of the summary, and in
`report.json`, and the run carries on without it. A file it can read but parts of which can never be
used — a parameter the document does not have, a misspelt operation — is said in one line as the run
starts. Either way, look at the first lines of the run.

`report.json` lists every list the run held, its own included:

```bash
jq '.dictionaries' restest-out/report.json
```

```
TO BE RUN: jq '.dictionaries' with clinic.yaml
```

A file under `read` was read. Being read is not the same as being used — the plan decides which
requests draw on which lists — but a file that was not read was certainly not used.

## Writing one from the document

A dictionary is often written from the OpenAPI document, by a person or a program going through it
operation by operation. [Writing one of these from a
specification](../dictionary-format.md#writing-one-of-these-from-a-specification) has the four rules
that keep such a file correct.

The [next chapter](07-settings.md) changes how the tool itself behaves.
