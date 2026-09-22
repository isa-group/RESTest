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
| `format` | the `format` the document declares — `date`, `uuid`, `email` | values that satisfy a stated format. RESTest builds its own for the kinds it knows, so reach for this where it does not: an account number, a book's identifier, a code your API invented |
| `schema` | the name the document gave the shape — `Pet`, `Owner` | whole objects, which is what a request body is |
| `name` | the name alone, wherever it appears — a parameter or a property inside a body | every `petId` in the API at once |
| `operationAndParameter` | the operation, then the place in its request | one place in one operation, which is as specific as it gets |

Values under the key **`any`** apply whatever the key is. It is where `null` belongs, and anything
else that makes sense everywhere.

It is blunter than it looks. In a file keyed by operation and place, `any` applies to *every* place
of *every* operation — every parameter, every body, and every piece of every body. Its values are
drawn against whatever is written for the place itself, so one value under `any` beside a list of
three is sent about a quarter of the time there; where nothing else is written for a place, which is
most of them, it is the only answer and is sent every time. `any: [null]` sends null everywhere.
Reach for it when you mean exactly that.

```yaml
version: 1
name: formats
keyedBy: format

values:
  any:  [null]
  iban: ["GB33BUKB20201555555555", "DE75512108001245126199"]
  isbn: ["978-3-16-148410-0"]
```

Kinds RESTest builds for itself — `date`, `date-time`, `email`, `uuid`, `uri`, `ipv4` and the rest of
the ones the specification format defines — are worth writing down only when you want *your* values
sent instead of built ones. Everything else is where a file like this earns its keep.

Nothing here needs quoting to be safe. YAML's own older rules turn a startling number of ordinary
words into something else — `no`, `off` and `n` into false, `yes`, `on` and `y` into true, an
unquoted date into a date — so a list of country codes holding `NO` would send `false` and never say
so. RESTest recognises only what JSON itself has: `true`, `false`, `null`, a whole number and a
number. Everything else is a piece of text, which is what somebody writing a list of values meant.

Numbers keep every digit the file wrote, rather than the nearest value sixty-four bits can hold, so
a deliberately enormous one arrives as it was written.

### Naming an operation

An operation answers to **two names, and either will do**:

- its `operationId`, when the document declares one — `getPetById`;
- its method and its path, always — `GET /pets/{petId}`: the method in capitals, one space, and the
  path exactly as the document writes it, braces and all.

The second is there so that a file can be written from the specification with no reasoning and no
exceptions. If you would rather not check whether a document declares identifiers, use the method and
the path everywhere and you will always be right. RESTest turns them into whichever name it prints in
its own output, so the two never come apart.

Unquoted is fine. YAML is happy with `GET /pets/{petId}:` as a key.

### Naming a place in the request

Inside an operation, a key names **one place a value goes**:

| Key | Means |
|---|---|
| `ownerId` | the parameter of that name, wherever the document puts it — path, query string, header, cookie |
| `tags[]` | every element of the parameter `tags`, where the document declares it a list |
| `filter.city` | the property `city` of the parameter `filter`, where the document declares it an object |
| `body` | the whole request body, as one value |
| `body.city` | the property `city` at the top of the body |
| `body.owner.email` | the property `email` of the object under `owner` |
| `body.tags[].label` | the `label` of **every** element of the list `tags` |
| `body[]` | every element, when the body is itself a list |

A parameter has pieces just as a body does, and they are written the same way. Most of the pieces in
most APIs are in the body, which is why the examples lean that way.

`[]` stands for every element because there is no one element a value could be meant for: a list of
values is drawn from each time an element is built.

A name declared in two places — a `petId` in the path and a `petId` in the query string — gets the
entry in both, and there is no way to say which you meant: the key is the name, and both parameters
have it. Where they mean different things, write values that suit either, or leave the entry out and
let the document answer.

Under `keyedBy: name` the key is the **last step alone**: `email` matches a parameter called `email`
and an `email` three levels inside a body, anywhere in the API. That is the difference between the
two keyings — one means this value and no other, the other means this name anywhere.

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

### A body piece by piece, or a body whole

Both work, and they are for different things.

```yaml
values:
  addOwner:
    body.city:      [Madison, Seville]      # a list per piece
    body.telephone: ["6085551023"]
  addVisit:
    body:                                   # or the whole thing at once
      - {date: "2026-09-18", description: rabies shot}
```

**A list per piece is the one to reach for.** RESTest still assembles the body, so what the document
says still applies around your values — the formats it declares, the lists of allowed values, which
optional properties to include this time — and the body varies from request to request.

One thing to know before writing a list per piece: **where the document offers a whole sample body
of its own, that sample is what gets sent**, and nothing inside it is ever asked for, so entries for
its pieces do nothing. The run says so when it reads your file. It is commoner than it sounds — all
twelve of kafka-rest-proxy's bodies are written out in full in its document.

**A whole body is for when the object only makes sense as a whole**: when one field constrains
another, when the body is not an object at all (a bare list, a single word), or when you have a
payload you know the API accepts and want it sent exactly as written. What you give up is variation:
that object is sent as it stands, every time it is drawn.

Where a file gives both, **the whole body wins** and the pieces of it are never used — the point of
writing a body whole is that its fields agree with each other, and quietly replacing one of them
would undo that. RESTest says so when it reads the file.

## Which lists get used for what

A run does not build every request the same way. Most of its time goes on requests meant to work;
some of it goes on pushing at the API with values nobody sensible would send, which is how an API
gets asked what it does with the unexpected. Those are kept apart: an API stops reading at the first
thing it does not like, so one odd value among good ones teaches nothing that a request of all odd
values does not.

**Which lists feed which kind of request is decided by the plan, by name**, and the plan is
[a file you can write](campaign-format.md). The one RESTest carries names one list, `fuzzing` — the
one RESTest ships, and any of yours called the same — for the requests that push at the API, and
asks for every other list you handed over when building requests meant to work. In a plan of your
own, one list can feed two kinds of request, or two lists one kind.

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
> to it.** A file offering two surnames under `string` makes *every* piece of text in the API send one
> of those two, for the whole run: every text parameter, and every text property of every body, since
> a list is asked for each piece of a value as well as for the value itself. Keying narrowly does not
> change that; it changes how much it covers. A list keyed by `name` covers every place of that name,
> a parameter and a property alike; one keyed by `operationAndParameter` covers the places you name
> and leaves the rest of the API alone, which is usually what somebody adding "a few good names"
> wants. Having your values sent *as well as* invented ones is a different thing, and RESTest cannot
> do it yet.

### `observed` is not one of these

A run can also draw on **what the API itself has sent back**, which is filed under the same two
keyings as a list of yours: a value under its own name, and a whole thing under the name of its
shape. It is not a file, though, and there is nothing here to write for it. A plan asks for it with
`source: observed`, the way it asks for the document's own samples, and
[the campaign format](campaign-format.md) says what it does and what it costs. A file of yours
called `observed` is an ordinary list with an ordinary name, and a plan names it the ordinary way.

## What a run says about your file

Everything a document can settle is settled when the file is read, before a single request is sent,
and said in one line per file:

```
restest: ids.yaml: 5 of its 8 entries will never be used: 1 for no such operation in this API
         (ownerId in getOwnerRenamedSince), 1 for a piece of a body that is supplied whole, which
         is sent instead (body.city in addOwner), 3 for no such parameter or piece of a body in
         that operation (postcode in addOwner, firstName in addOwner, body.nonsense in addVisit)
```

Five things earn a mention:

| What | Usually means |
|---|---|
| no such operation in this API | the file has fallen behind the document — or the operation gained an `operationId` since |
| no such parameter or piece of a body in that operation | a misspelling, or a property written as `city` where it should be `body.city` |
| a place whose whole list of values the document declares | an `enum`, which nothing overrides, wherever it is |
| a property the document says the API only ever sends back | `readOnly`, which is never ours to send |
| a piece of a body that is supplied whole | some list gives that operation a whole body, and that is what gets sent |
| a piece of a body the document itself writes out in full | the document offers a whole sample body, which is sent as the author wrote it |

A line of its own appears when one file writes one operation under both of the names it answers to.
The entries under the `operationId` are the ones kept.

None of it ends the run. A dictionary is advice, and a run with some of it unusable is still a run.

Two things are never called wrong. Where the document runs out — a shape written in a way the parser
could not read, or the point at which one starts repeating itself — nothing below that is judged,
though everything above it still is. And a name the
document declares twice in one operation is not judged either, since one entry feeds both and what
settles one of them need not settle the other.

An object that merely allows properties it does not name is not one of those: a value is only ever
asked for under a name the document writes down, so an entry for `body.anything` is reported however
willing the API would be to receive it.

## Writing one of these from a specification

A file is often produced from the OpenAPI document by a person or a program working through it
operation by operation. Four rules make that reliable:

1. **Name every operation by its method and path** unless you are certain of its `operationId`. Both
   are accepted, and this one needs no checking.
2. **Write `body.` in front of anything inside the request body.** A bare `city` means a *parameter*
   called `city`, and if the operation has no such parameter the entry does nothing.
3. **Skip parameters the document gives an `enum` for.** The document has already said what those
   values are, and nothing in your file will be sent instead.
4. **Prefer a list per place over a whole body**, and keep the whole body for objects whose fields
   depend on each other.

Then look at what the run says about the file. The check happens as the file is read, before the
first request, so the first few lines of any run tell you what is wrong with it. There is no command
that reads a file and stops; until there is, point a short run at a server of your own rather than at
somebody else's API to see them.
