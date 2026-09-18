# The dictionary format

A dictionary is a list of values worth sending, in a JSON file. RESTest reads them, adds them to the
sources it asks when it needs a value, and uses them alongside everything the specification itself
says.

This is a published format. It is plain JSON with no schema to install and no tooling to run,
because the point of a dictionary is that a person can write one in an afternoon — or a script can
produce one — and commit it next to the specification it belongs to.

```bash
restest run api.yaml --url https://api.example --dictionary ids.json --dictionary ./dictionaries/
```

`--dictionary` takes a file or a directory, and may be repeated. A directory contributes every
`.json` file in it, read in name order. RESTest always uses its own list of deliberately awkward
values on top of whatever you give it; `--fuzzing 0` turns that off.

A file that cannot be read costs the values in it and is reported. It never ends the run.

## The file

```json
{
  "version": 1,
  "name": "petshop-ids",
  "description": "Identifiers that exist in the staging database.",
  "keyedBy": "operationAndParameter",
  "expects": "acceptance",
  "values": {
    "getOwner": { "ownerId": [1, 2, 3] },
    "GET /pets/{petId}": { "petId": [7] }
  }
}
```

| Field | | |
|---|---|---|
| `version` | required | The version of **this format**, not of your file. Today it is `1`. A file written in a version RESTest does not know is refused by name rather than half-read |
| `name` | required | What this dictionary is called. It is what a report prints beside a value that came from it, so a name like `staging-ids` is worth more than `dict1` |
| `description` | optional | For whoever reads the file next |
| `keyedBy` | required | What decides which of the values apply. One of the five below |
| `expects` | optional | What you believe about these values: `acceptance`, `refusal` or `unknown`. **Left out means `unknown`**, which is the ordinary case |
| `values` | required | The values themselves, under their keys |

Anything else in the file is refused by name. A misspelled `expects` or `keyedBy` would otherwise
leave a dictionary that loads without complaint and then never contributes a value.

**One dictionary per file.** A file states once what decides which of its values apply and once what
you believe about them, and letting several share a file would make both statements meaningless. Keep
several by keeping several files.

## The five keyings

| `keyedBy` | The key is | Use it for |
|---|---|---|
| `type` | the kind of value wanted: `string`, `integer`, `number`, `boolean`, `array`, `object`, `null` | values that suit anything of a kind — the awkward values RESTest ships are keyed this way |
| `format` | the `format` the document declares — `date`, `uuid`, `email` | values that satisfy a stated format |
| `schema` | the name the document gave the shape — `Pet`, `Owner` | whole objects, which is what a request body is |
| `name` | the parameter's name, wherever it appears | every `petId` in the API at once |
| `operationAndParameter` | the operation, then the parameter | one parameter of one operation, which is as specific as it gets |

Values under the key **`any`** apply whatever the key is. It is where `null` belongs, and anything
else that makes sense everywhere.

```json
{
  "version": 1, "name": "formats", "keyedBy": "format",
  "values": {
    "any":       [null],
    "date":      ["2026-09-18", "1970-01-01"],
    "email":     ["someone@example.com"],
    "uuid":      ["123e4567-e89b-12d3-a456-426614174000"]
  }
}
```

### Naming an operation

An operation is named by its `operationId` when the document declares one, and otherwise by its
method and path: `GET /pets/{petId}`. That is the same name RESTest prints in its own output, so the
way to find it is to run the tool and copy it.

This has one sharp edge worth knowing. If a document gives an operation no identifier and somebody
later adds one, the name changes, and entries written against the old name stop matching. RESTest
says so rather than silently doing nothing:

```
restest: ids.json has values for 1 operation this API does not have (getOwnerRenamedSince), so those values will never be used
```

A parameter is named by its name alone. A name declared in two places — a `petId` in the path and a
`petId` in the query string — gets the entry in both.

## The values

A value is **any JSON**: a word, a number, `true`, `null`, a list, or a whole object. That last one
is what makes a dictionary useful for request bodies, where the thing worth keeping is the object
rather than any field of it.

```json
{
  "version": 1, "name": "bodies", "keyedBy": "schema", "expects": "acceptance",
  "values": {
    "Owner": [
      { "firstName": "George", "lastName": "Franklin", "city": "Madison" }
    ]
  }
}
```

RESTest picks among the values that apply at random rather than always the first, so a run works
through the whole list.

A value that could not actually be put in the request — one that would leave a gap in a path empty, or
carry a line break into a header — is skipped for that place and used elsewhere.

One thing to watch for, because nothing can warn you about it: **two entries under the same key**, as
in `{"string": ["a"], "string": ["b"]}`, are not an error in JSON, and the second silently replaces
the first. Most editors will point it out.

## What `expects` is for

`refusal` says every value in the file is meant to be turned away. RESTest keeps such values out of
its ordinary requests and spends a separate share of its time on requests built entirely from them,
because an API stops reading at the first thing it does not like and a request with one bad value
among good ones would teach nothing that a request of all bad values does not. The summary then says
how many requests were of that kind, so the refusals they earn do not read as the API turning away
ordinary traffic.

`acceptance` says you have checked, or the values came from the API itself. Such a list is asked
alongside the document's own answers, in an order set by how much it knows about the value:

```
the closed list of values the document says it accepts     nothing overrides this
a list keyed by operationAndParameter or by name           knows about one value
the document's own examples and defaults
a list keyed by schema, format or type                     knows about a kind of value
whatever RESTest can invent
```

So a file of real identifiers for `getOwner`'s `ownerId` beats the document's sample of that
parameter, and a file of plausible surnames for every piece of text does not. A list keyed by
`schema` is on the second side rather than the first because a document declares a shape once and
every parameter referring to it gets the same one.

Where a parameter declares an `enum`, nothing in your file is used for it. That list is not advice:
it is the whole set of values the API says it takes, and sending anything else would be sending a
value the document has already refused.

> **A list keyed by `type` or `format` replaces what RESTest would otherwise invent for that kind of
> value — it is not added to it.** A file offering two surnames under `string` makes every string
> parameter in the API send one of those two, for the whole run. If you want your values used
> *alongside* invented ones, key them to the parameters you mean (`name`, or
> `operationAndParameter`), where only those parameters are affected.

`unknown` — the default — says nobody has checked. It is not an admission of failure: a list of
plausible surnames is a genuinely useful thing to have without anybody having confirmed that this
particular API takes any of them.
