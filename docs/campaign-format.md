# The campaign file

A campaign file — a *plan* — tells a run where its values should come from and which operations it
may touch. You never need one: RESTest carries a plan of its own, and that plan is a file you can
print, change and hand back.

```bash
restest run --print-campaign > plan.yaml
# edit it
restest run api.yaml --url http://localhost:9966 --campaign plan.yaml
```

The decisions behind the format are in [ADR-0023](adr/0023-the-campaign-file.md). What a list of
values is, and how one is written, is [the dictionary format](dictionary-format.md).

A plan is about the API. How the tool itself behaves — how hard it pushes, how much it keeps, how
deep it goes — is [the settings](settings.md), which travel with a machine rather than with an API.

## What a plan says

```yaml
version: 1

strategies:
  - name: nominal
    share: 75
    sources:
      - source: enum
      - weighted:
          - source: example
            weight: 20
          - source: random
            weight: 25
          - dictionaries: given
            weight: 50
          - source: default
            weight: 5

  - name: fuzzing
    share: 25
    sources:
      - dictionary: fuzzing
      - source: random

operations:
  methods: [GET, POST]
  only: [getPetById, "GET /pets/{petId}"]
```

A plan is a handful of **strategies**. Each is one way of building a request: a name, a share of the
run's time, and the **sources** its values come from. `operations:` at the end says what the run may
touch at all, and can be left out.

## Strategies

Every request is built one way throughout, and which way is drawn per request, by share.

`share` is out of a hundred, and the shares of a plan add up to a hundred. A file that does not is
refused, and told what it does add up to.

Most runs want two: one building requests meant to work, one pushing at the API with values nobody
sensible would send. The second is not looking for a refusal — a refusal is a fair answer, and a
great many APIs accept an empty word quite happily. It is looking for the API falling over.

There is no key saying which is which. A strategy that draws on the list of values to push with is
one that pushes, and that is how the run's summary knows how many of its requests were of that kind.

## Sources

The sources of a strategy are **asked in the order written, and the first answer is taken**. So the
order is your statement about which source knows best.

Three ways to name one:

| Written | Means |
|---|---|
| `source: <word>` | one of RESTest's own, from the table below |
| `dictionary: <name>` | one list of values, by the `name` inside the list's own file |
| `dictionaries: given` | every list handed over with `--dictionary` |

Saying which kind you mean is not ceremony: without it, a list of yours called `example` would
silently shadow RESTest's own samples, and nobody reading the file could tell which a bare name was.

### RESTest's own sources

| Word | What answers |
|---|---|
| `enum` | the closed list of values the document says it accepts |
| `example` | the sample values the document's author wrote down |
| `default` | the value the document says applies when the caller sends nothing |
| `observed` | what the API itself has already sent back, earlier in this run |
| `random` | a value invented to fit the shape |

`enum` is worth putting first and leaving there. A closed list is not advice: it is the whole set of
values the API will take, so anything else sent in its place is a value the document says is not
allowed.

`example` and `default` are different statements and are separate sources for that reason. Given
`limit: { type: integer, default: 20, example: 5 }`, the default says *omit this and you get 20* —
one value, so a parameter left to it never varies and is never exercised near its limits. The
example says *5 is a value that works*, and is usually a value that existed in the API its author
was looking at, which is why an identifier from a document reaches a real row and an invented one
reaches a 404.

### `observed`, the one source with a memory

Every other source reads the document, which says the same thing whoever asks and whenever. This one
listens to the API. When a request comes back with a reply the API was happy with, what that reply
contained is kept, and the next request that needs a value of the same name sends one the API itself
produced — an identifier that exists, a reference that resolves, a name spelled the way the API
spells it. Of the values inside the request bodies of our fifty-document corpus, 89% carry a name
some reply of the same API also carries.

A gap in a web address gets a second chance. The API usually calls a thing's identifier `id`, and the
address that reads one calls it something else — `/pettypes/{petTypeId}` — so no reply ever carries
a value named `petTypeId`. What the API returns at an address is therefore also kept under the kind
of thing the address is about: everything `GET /pettypes` lists is a pet type, and `{petTypeId}` is
filled with the `id` of one of them. The kind is asked before the name, because a generic name is a
poor guide — under `/flights/{id}`, an airport's `id` answers to `id` as readily as a flight's. A gap
whose own name is not written like an identifier, such as `{username}`, only takes a property with
exactly its name. Two settings govern it: `memory.identifiersByResource` switches it off, and
`memory.identifiersByResourceFirst` asks the name first instead ([the settings](settings.md)).

The memory also lets go of what the run itself deleted: a `DELETE /pets/{petId}` the API answers
with a 2XX removes that pet, so later requests stop naming it. And when a request needs the
identifier of a thing the memory holds none of - nothing of the kind was ever returned, or all of it
has been deleted - and some `POST` creates such things, the run sends that creation first and the
request straight after, with the identifier the creation came back with rather than one from the
memory. Only a plan that names `observed` gets this, since without a memory nobody knows what is
missing. `memory.forgetWhatWasDeleted` and `sequences.pairs` switch the two off.

Asked for a whole thing the document gives a name to — "send me an `Owner`" — it offers an owner the
API returned, with the parts the API only ever *sends* taken out of it (that is what `readOnly`
means) and one value inside it replaced by a different one. Sending an unchanged copy would usually
ask the API to create a duplicate; changing one thing asks it to accept something new that is
otherwise exactly as real as what it sent. Where nothing different can be had — every value in it is
one no other source can fill — it goes as it came back, and the run records that it was unchanged
rather than naming a value that was not.

It can only offer a value that fits: the kind has to match, and where the document states the closed
list of values it accepts, a value seen elsewhere in the API is not made acceptable by having been
seen.

**It costs one promise, and the cost is real.** A run whose plan names `observed` cannot be repeated
by giving it the same `--seed` again: what it sends depends on what the API answered, and an API
answers differently on a different day. The same number gets you a similar run, not the same one.
What you have instead is the record: `--store` keeps every request and reply of the run you actually
had, so a surprising result can be examined rather than chased. Sending those stored requests again
is a separate command and is not built yet. Take `observed` out of your plan and the seed means
exactly what it always did.

`observed` is a word, not a list: it is one of RESTest's own sources, so a file of your own called
`observed` is a different thing entirely and is named with `dictionary: observed` as usual.

### Every list you handed over

`dictionaries: given` is one source, not one per file, so it carries one weight however many lists
there are. Within it, lists written for one particular place — a named parameter, or one operation's
one parameter — are asked before lists written for a whole kind of value. That ranking is RESTest's
and you do not have to state it.

To place a list somewhere else in the order, name it: `dictionary: my-good-values`.

## Choosing instead of ordering

A `weighted:` group asks **every** source in it and picks one of the answers, in proportion to the
weights. The weights in a group add up to a hundred.

```yaml
      - weighted:
          - source: random
            weight: 90
          - source: default
            weight: 10
```

Use it where more than one source is worth hearing from. Asked in turn, a parameter whose document
offers one sample would receive that same value on every request for the whole run; in a group it
gets a turn among the others.

**A source with nothing to say does not get its share.** It goes to the ones that answered. That is
what lets a plan name a list you have not handed over, or the API's own replies before the API has
answered anything, and still behave sensibly — and it means the proportions a run achieves differ
from the ones you wrote, which is worth knowing before you measure them.

One thing to know about a group inside a request body: what fills the properties *inside* a body
is asked of the same strategy, but without invention in it — invention is what is doing the asking.
So a group of `random` and `default` is a choice at the top level and leaves `default` answering on
its own underneath. It is worth knowing before measuring a plan's proportions.

**Put invention inside a group, not after one.** `random` fits a value to whatever it is asked
about, so it answers wherever anything could, and a source written below it is asked for almost
nothing. A run says so when it sees one rather than refusing the plan, because a shape nothing
satisfies leaves invention with no answer either.

## The first round of a run

Before anything is chosen, a run sends every operation it can test once, each with the request it
is most likely to accept, and only then starts drawing. That first round uses your plan too: the
first strategy that does not push at the API, with two differences. A plan whose every strategy
pushes has no request anybody believes in, and so no first round.

- **Every group is asked in turn instead of chosen among**, best first: a closed list of accepted
  values, then what the API has already returned, then your own lists, then the document's sample,
  then its default, and last a value invented to fit. One request with one chance should carry the
  best value there is; choosing among sources is what a long run needs, not what one request needs.
  A source you write on its own keeps its place, and a source your plan does not name is not used.
- **Only what the API requires goes in** - no optional parameters - plus a body wherever the
  document describes one, required or not, because most documents never say a body is required even
  when the operation cannot work without it. A body a `GET` or `HEAD` merely accepts is the
  exception, because RESTest cannot send a body with either: it is left out. A `GET` or `HEAD` that
  insists on one is not in the round at all, and the run counts it among the operations it cannot
  test.

The round goes lists first (`GET /owners`), then what creates (`POST`), then what reads one thing
(`GET /owners/{ownerId}`), then what changes (`PUT`, `PATCH`), and deletes last, each step waiting
for the answers to the one before, so that an identifier the API has just handed back can be sent by
the next step. Switch it off with `--set schedule.openingLap=false`; how long a step waits is
`schedule.openingLapPatience`. Both are settings rather than lines here, because they are about how
the tool behaves rather than about where values come from - see [the settings](settings.md).

## Which operations a run may touch

```yaml
operations:
  methods: [GET, POST]
  only: [getPetById, "GET /pets/{petId}"]
```

Both are optional and both narrow; together, an operation has to survive each. Leave the block out
and the run touches every operation it could test.

`methods` takes HTTP methods in any case. To keep a run to the methods HTTP itself calls **safe** —
the ones that ask for something and change nothing:

```yaml
operations:
  methods: [GET, HEAD, OPTIONS, TRACE]
```

That is the recipe, and there is deliberately no `safeOnly` shorthand for it. Note what it is not:
an API that searches with `POST` is perfectly ordinary, and one of the five APIs RESTest is measured
on does exactly that — so this loses that operation, and calling the filter "read-only" would be a
lie about it.

`only` takes an operation either way a document lets you name it: the `operationId` it declares, or
the method and path you can read straight off it. An operation named here that the API does not have
is reported before any request is sent, because a plan written against an older document tests less
than its author believes.

A filter can only subtract. RESTest has already worked out which operations it can build a request
for at all, and said which it cannot; this narrows that set.

## What a run tells you about a plan

None of these stop a run. All of them are the same kind of thing: something the plan asked for that
will not happen, which nobody would otherwise find out about.

- a list the plan names that nobody handed over
- an operation under `only` that this API does not have
- a source written below invention, which will hardly ever be reached
A plan that cannot be read at all is the exception: the run **does not start**, and says why. That
is deliberately not how the tool treats a specification or a list of values, which it works with as
best it can. A plan is different because of what a plan can say — one of the things it is for is
keeping a run away from everything that writes, and carrying on with a plan that says nothing of
the kind would answer "only read from this API" by writing to it.

## `--fuzzing` and `--campaign`

`--fuzzing <percentage>` changes how much of the run pushes at the API, and nothing else about the
plan RESTest carries. `--fuzzing 0` leaves the pushing strategy out altogether.

Naming both `--fuzzing` and `--campaign` is refused: your plan sets the share of every strategy it
names, including that one, so there is nothing left for the option to mean.

## What this version refuses

A plan this version cannot read is refused by name rather than half-understood — a plan that quietly
lost a strategy would send a quarter of its requests somewhere nobody asked for, and nothing in the
output would say so.

- a `version` this build does not read, said as a version rather than as a misspelling
- a member nobody recognises, which is usually a typo and would otherwise load and do nothing
- a `source:` word RESTest does not have, answered with the words it does
- a source named two ways at once, or none
- `dictionaries:` saying anything but `given`
- a `weight` on a source that is not in a group, where there is no choice for it to divide
- a source in a group with no `weight`
- shares, or the weights in one group, that do not add up to a hundred
- a group with one source in it, where there is nothing to choose between
- a key written twice, which YAML allows and which would leave the first quietly replaced
