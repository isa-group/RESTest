# The dictionary format

A dictionary is a list of values worth sending, in a YAML file. RESTest reads them, adds them to the
sources it asks when it needs a value, and uses them alongside everything the specification itself
says.

This is a published format. YAML, with no schema to install and no tooling to run, because the point
of a dictionary is that a person can write one in an afternoon — or a script can produce one — and
commit it next to the specification it belongs to, which is written in YAML too. JSON is a subset of
YAML, so a file written that way is read the same way without anybody being told.

```bash
restest run api.yaml --url https://api.example --dictionary ids.yaml --dictionary ./dictionaries/
```

`--dictionary` takes a file or a directory, and may be repeated. A directory contributes every
`.yaml`, `.yml` and `.json` file in it, read in name order. RESTest always uses its own list of
values to push at an API with on top of whatever you give it; `--fuzzing 0` turns that off.

A file that cannot be read costs the values in it and is reported. It never ends the run.

## The file

```yaml
version: 1
name: petshop-ids
description: Identifiers that exist in the staging database.
keyedBy: operationAndParameter

values:
  getOwner:
    ownerId: [1, 2, 3]
  "GET /pets/{petId}":
    petId: [7]
```

| Field | | |
|---|---|---|
| `version` | required | The version of **this format**, not of your file. Today it is `1`. A file written in a version RESTest does not know is refused by name rather than half-read |
| `name` | required | What this dictionary is called. A plan names the dictionaries each kind of request draws on, so the name is how a list gets used, and it is what a report prints beside a value that came from it |
| `description` | optional | For whoever reads the file next |
| `keyedBy` | required | What decides which of the values apply. One of the five below |
| `values` | required | The values themselves, under their keys |

Anything else in the file is refused by name. A misspelled `keyedBy` would otherwise leave a
dictionary that loads without complaint and then never contributes a value. The same goes for a key
written twice, which YAML allows and which would otherwise leave the first list quietly replaced.

**One dictionary per file.** A file states once what decides which of its values apply, and letting
several share a file would make that statement meaningless. Keep several by keeping several files.

**A dictionary does not say what the API will make of its values, and could not.** A value can be
perfectly good and the request still refused for a rule about some other parameter; and a value that
looks outrageous — an empty word, a zero, a list with nothing in it — is one a great many APIs accept
quite happily. What a list is *for* is decided by the plan that names it, not by the file.

## The five keyings

| `keyedBy` | The key is | Use it for |
|---|---|---|
| `type` | the kind of value wanted: `string`, `integer`, `number`, `boolean`, `array`, `object`, `null` | values that suit anything of a kind — the list RESTest ships is keyed this way |
| `format` | the `format` the document declares — `date`, `uuid`, `email` | values that satisfy a stated format |
| `schema` | the name the document gave the shape — `Pet`, `Owner` | whole objects, which is what a request body is |
| `name` | the parameter's name, wherever it appears | every `petId` in the API at once |
| `operationAndParameter` | the operation, then the parameter | one parameter of one operation, which is as specific as it gets |

Values under the key **`any`** apply whatever the key is. It is where `null` belongs, and anything
else that makes sense everywhere.

```yaml
version: 1
name: formats
keyedBy: format

values:
  any:   [null]
  date:  ["2026-09-18", "1970-01-01"]
  email: [someone@example.com]
  uuid:  [123e4567-e89b-12d3-a456-426614174000]
```

Quote a value where YAML would otherwise read it as something else. Unquoted, `2026-09-18` is a
*date* to YAML rather than a piece of text, and RESTest refuses it rather than guessing at what to
send — the message says so.

### Naming an operation

An operation is named by its `operationId` when the document declares one, and otherwise by its
method and path: `GET /pets/{petId}`. That is the same name RESTest prints in its own output, so the
way to find it is to run the tool and copy it.

This has one sharp edge worth knowing. If a document gives an operation no identifier and somebody
later adds one, the name changes, and entries written against the old name stop matching. RESTest
says so rather than silently doing nothing:

```
restest: ids.yaml has values for 1 operation this API does not have (getOwnerRenamedSince), so those values will never be used
```

A parameter is named by its name alone. A name declared in two places — a `petId` in the path and a
`petId` in the query string — gets the entry in both.

## The values

A value is anything JSON can carry: a word, a number, `true`, `null`, a list, or a whole object. That
last one is what makes a dictionary useful for request bodies, where the thing worth keeping is the
object rather than any field of it.

```yaml
version: 1
name: bodies
keyedBy: schema

values:
  Owner:
    - firstName: George
      lastName: Franklin
      city: Madison
```

RESTest picks among the values that apply at random rather than always the first, so a run works
through the whole list. A value that could not actually be put in the request — one that would leave
a gap in a path empty, or carry a line break into a header — is skipped for that place and used
elsewhere.

## Which lists get used for what

A run does not build every request the same way. Most of its time goes on requests meant to work;
some of it goes on pushing at the API with values nobody sensible would send, which is how an API
gets asked what it does with the unexpected. Those are kept apart: an API stops reading at the first
thing it does not like, so one odd value among good ones teaches nothing that a request of all odd
values does not.

**Which lists feed which kind of request is decided by the plan, by name.** Today the plan is built
in and names one list, `fuzzing` — the one RESTest carries, and any of yours called the same. Every
other list feeds ordinary requests. When the campaign file arrives you write the plan yourself, and
one list can feed two kinds of request, or two lists one kind.

Among the lists feeding ordinary requests, the order is how much each one knows about the value:

```
the closed list of values the document says it accepts     nothing overrides this
a list keyed by operationAndParameter or by name           knows about one value
the document's own examples and defaults
a list keyed by schema, format or type                     knows about a kind of value
whatever RESTest can invent
```

So a file of real identifiers for `getOwner`'s `ownerId` beats the document's sample of that
parameter, and a file of plausible surnames for every piece of text does not. A list keyed by
`schema` is on the second side because a document declares a shape once and every parameter
referring to it gets the same one.

Where a parameter declares an `enum`, nothing in your file is used for it. That list is not advice:
it is the whole set of values the API says it takes, and sending anything else would be sending a
value the document has already refused. The one exception is an enumeration none of whose values
could be put where that parameter goes — every one of them empty, in a path — which is read as no
enumeration at all, because the alternative is an operation that can never be tested.

> **A list replaces what RESTest would otherwise have sent for the values it covers — it is not added
> to it.** A file offering two surnames under `string` makes *every* string parameter in the API send
> one of those two, for the whole run. Keying narrowly does not change that; it changes how much it
> covers. A list keyed by `name` or `operationAndParameter` replaces the values of the parameters you
> name and leaves the rest of the API alone, which is usually what somebody adding "a few good names"
> wants. Having your values sent *as well as* invented ones is a different thing, and RESTest cannot
> do it yet.
