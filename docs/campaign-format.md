# The campaign file

A campaign file — a *plan* — tells a run where its values should come from and which operations it
may touch. You never need one: RESTest carries a plan of its own, and that plan is a file you can
print, change and hand back.

```bash
restest run --print-campaign > plan.yaml
# edit it
restest run api.yaml --url http://localhost:9966 --campaign plan.yaml
```

The decisions behind the format are in [ADR-0023](adr/0023-the-campaign-file.md), and its version 2
in [ADR-0034](adr/0034-version-2-of-the-plans-format.md). What a list of values is, and how one is
written, is [the dictionary format](dictionary-format.md).

A plan is about the API. How the tool itself behaves — how hard it pushes, how much it keeps, how
deep it goes — is [the settings](settings.md), which travel with a machine rather than with an API.

## What a plan says

```yaml
version: 2

strategies:
  - name: nominal
    share: 60
    kind: nominal
    sources:
      - source: enum
      - weighted: {example: 20, random: 25, dictionaries: 50, default: 5}

  - name: mutation
    share: 15
    kind: mutation
    sources:
      - source: enum
      - source: random

  - name: fuzzing
    share: 25
    kind: fuzzing
    sources:
      - source: random

operations:
  methods: [GET, POST]
  only: [getPetById, "GET /pets/{petId}"]
```

A plan is a handful of **strategies**. Each is one way of building a request: a name, a share of the
run's time, a **kind**, and the **sources** its values come from. `operations:` at the end says what
the run may touch at all, and can be left out.

`version` is the version of this format, `2`. A plan written in another version is refused, and told
that `restest run --print-campaign` prints one this version reads.

## Strategies

Every request is built one way throughout, and which way is drawn per request, by share. The one
exception is a strategy that [sends series](#series-of-requests-around-a-thing-the-run-created):
the requests after a series' first are sent in turns of their own, ahead of the ordinary ones, so
such a strategy takes more of the run's time than its share says.

`share` is out of a hundred, and the shares of a plan add up to a hundred. A file that does not is
refused, and told what it does add up to.

`kind` is what the strategy does, one of four words:

| `kind` | Each request it builds |
|---|---|
| `nominal` | is meant to work, built from its sources |
| `fuzzing` | pushes at the API with values nobody sensible would send: RESTest's own list of them is asked first, and the strategy's sources are asked for whatever that list has nothing sendable for |
| `mutation` | is [a request the API accepted, sent again with one thing changed](#changing-one-thing-in-a-request-that-worked); its sources are what it falls back on |
| `sequences` | is meant to work, and a creation starts [a series of requests about the thing it creates](#series-of-requests-around-a-thing-the-run-created) |

The `name` is a label, printed in reports; the `kind` is what the strategy does. In the plan RESTest
carries every name is its kind's word, but a name can be anything, and two strategies can be of the
same kind.

Most runs want two or three: one building requests meant to work, one pushing at the API with
values nobody sensible would send, and one changing a request that worked. The pushing one is not
looking for a refusal — a refusal is a fair answer, and a great many APIs accept an empty word quite
happily. It is looking for the API falling over. Each of its requests records that it was pushing,
which is how the run's summary knows how many were of that kind.

## Sources

The sources of a strategy are **asked in the order written, and the first answer is taken**. So the
order is your statement about which source knows best.

Two ways to name one, and a third to choose between several ([below](#choosing-instead-of-ordering)):

| Written | Means |
|---|---|
| `source: <word>` | one of RESTest's own, from the table below |
| `dictionary: <name>` | one list of values of yours, by the `name` inside the list's own file |
| `weighted: {...}` | a group to choose between |

Saying which sort of source you mean is not ceremony: without it, a list of yours called `example` would
silently shadow RESTest's own samples, and nobody reading the file could tell which a bare name was.
And a misspelt word of RESTest's, such as `source: exmaple`, is refused rather than taken for the
name of a list.

### RESTest's own sources

| Word | What answers |
|---|---|
| `enum` | the closed list of values the document says it accepts |
| `example` | the sample values the document's author wrote down |
| `default` | the value the document says applies when the caller sends nothing |
| `observed` | what the API itself has already sent back earlier in this run, and what it accepted in a request to another operation |
| `random` | a value invented to fit the shape |
| `dictionaries` | every list of values handed over with `--dictionary`, in the order they were handed over |

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

Only a value whose name some request of the document asks for is kept: a parameter's name, or a
property's anywhere inside a body. A reply's other names are never asked for, and some APIs send
thousands of them — flight-search lists every class in the program on one of its diagnostic pages —
so keeping them would leave no room for the token a login hands back. The memory holds at most
`memory.mostNames` names, and when it is full a new one makes room by letting go of the name heard
of longest ago.

It also keeps what the API *accepted*. When a request is answered with a success, the values it
carried — its parameters and every named piece of its body — are kept under their names beside what
replies carried, so the password a registration was accepted with is there for the login that asks
for a password next. Not from a deletion, whose thing is gone, nor from a request made to push at
the API, nor from one made by changing an accepted one; `memory.rememberAcceptedRequests` switches
it off ([the settings](settings.md)).

A gap in a web address gets a second chance. The API usually calls a thing's identifier `id`, and the
address that reads one calls it something else — `/pettypes/{petTypeId}` — so no reply ever carries
a value named `petTypeId`. What the API returns at an address is therefore also kept under the kind
of thing the address is about: everything `GET /pettypes` lists is a pet type, and `{petTypeId}` is
filled with the `id` of one of them. The kind is asked before the name, because a generic name is a
poor guide — under `/flights/{id}`, an airport's `id` answers to `id` as readily as a flight's. A gap
whose own name is not written like an identifier, such as `{username}`, only takes a property with
exactly its name. Two settings govern it: `memory.identifiersByResource` switches it off, and
`memory.identifiersByResourceFirst` asks the name first instead ([the settings](settings.md)).

Two more kinds of gap are filled the same way. One named for several identifiers, such as `{ids}` in
`/persons/{ids}`, takes one identifier, exactly as `{id}` would. One named for a thing's name, such
as `{productName}`, takes the `name` of one of the products the API returned, and never an
identifier; and an API that lists its products as nothing but their names — `["car", "bike"]` — has
each of those words kept as the name of one product. A name run into one word, such as `{username}`,
is not written like a thing's name and still takes only a property called exactly that.
`memory.pluralIdentifiers` and `memory.namesByResource` switch the two off.

Asked for a whole thing the document gives a name to — "send me an `Owner`" — it offers an owner the
API returned, with the parts the API only ever *sends* taken out of it (that is what `readOnly`
means) and one value inside it replaced by a different one. Sending an unchanged copy would usually
ask the API to create a duplicate; changing one thing asks it to accept something new that is
otherwise exactly as real as what it sent. Where nothing different can be had — every value in it is
one no other source can fill — it goes as it came back, and the run records that it was unchanged
rather than naming a value that was not.

It can only offer a value that fits: the kind has to match, a word has to be as long as the document
allows where it goes, and where the document states the closed list of values it accepts, a value
seen elsewhere in the API is not made acceptable by having been seen.

**It costs one promise, and the cost is real.** A run whose plan names `observed` cannot be repeated
by giving it the same `--seed` again: what it sends depends on what the API answered, and an API
answers differently on a different day. The same number gets you a similar run, not the same one.
What you have instead is the record: `--store` keeps every request and reply of the run you actually
had, so a surprising result can be examined rather than chased. Sending those stored requests again
is a separate command and is not built yet. Two other things a run does cost the same promise,
[changing a request that worked](#changing-one-thing-in-a-request-that-worked) and
[series](#series-of-requests-around-a-thing-the-run-created), so the seed means exactly what it
always did once `observed` is out of every strategy of your plan and both of those are switched
off. [The switches](switches.md#getting-the-seed-back) shows the plan RESTest carries without
`observed`, and the file of settings that switches the other two off.

`observed` is a word, not a list: it is one of RESTest's own sources, so a file of your own called
`observed` is a different thing entirely and is named with `dictionary: observed` as usual.

### Every list you handed over

`source: dictionaries` is one source, not one per file, so it carries one weight however many lists
there are. Within it, the lists are asked in the order they were handed over.

To place a list somewhere else in the order, name it: `dictionary: my-good-values`. `fuzzing` is
RESTest's own list to push with, which a strategy of kind `fuzzing` asks first; it is not named as a
source, and no list of yours can take its name.

## Choosing instead of ordering

A `weighted:` group asks **every** source in it and picks one of the answers, in proportion to the
weights. It is a map from each source to its weight, and the weights in a group add up to a hundred:

```yaml
      - weighted: {random: 90, default: 10}
```

RESTest's own sources go in by their words, and lists of yours by name under `dictionary`:

```yaml
      - weighted:
          random: 30
          example: 10
          dictionary: {pet-clinic: 40, seed-data: 20}
```

A weight is a whole number from 1 to 100; a group holds at least two sources. However a group is
written, it is asked in one order — `example`, `random`, `observed`, `dictionaries`, `default`,
`enum`, then your lists by name — so a plan rewritten by a tool that sorts the keys of a map draws
the same requests from the same seed. Proportions never depend on the order; which request a seed
draws would.

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

## Changing one thing in a request that worked

A strategy of kind `mutation` builds its requests another way: it takes a request the API has
already **accepted**, and sends it again with exactly one thing changed.

```yaml
  - name: mutation
    share: 20
    kind: mutation
    sources:
      - source: enum
      - source: random
```

An API checks what it is sent before it acts on it, and a request with several things wrong is
turned away by the first check it meets — so the code behind that check, where the failures a
correct request never reaches tend to live, never runs. Changing one thing gets past every check but
one, and whatever the API does next is down to that one thing: it refuses it, which is right; it
accepts it, which says a check is missing; or it fails, which is what a run is looking for.

Its `sources:` are what it falls back on: until the API has accepted something for an operation, or
when nothing in what it accepted can be changed, the request is built from them the ordinary way.
The plan RESTest carries gives its `mutation` strategy the same sources as `nominal`, so that its
fallback requests are exactly the ones `nominal` would build.

What is changed is one value — a parameter, or one property inside a JSON body, however deep — or
the JSON body as a whole, and how is one of fifteen kinds, each breaking something the
description states:

| The kind of change | What is sent |
|---|---|
| `dropRequired` | a required parameter or body property left out |
| `wrongLocation` | a required query parameter, header or cookie sent as one of the other two |
| `wrongType` | a value of another kind, drawn from a long list: a word where a number is declared, `1.5` where a whole number is, `"1"` or `[[]]` or `{}`, any value of another kind in the list of awkward values RESTest keeps. In a parameter declared a number or a yes-or-no, a word that does not read as one - `abc`, `1.5`, `0x1A`, `NaN`, `yes` - and arrives as written: none a header would lose a space of, none too long for an address |
| `outsideABound` | one step past a stated limit: one below the smallest number, one character more than the longest word, one item more than a list may hold |
| `breakAnEnumeration` | a value that is not on the closed list: `AVAILABLE` where `available` is |
| `breakAPattern` | a word close to the accepted one that its stated pattern refuses |
| `breakAFormat` | a word that looks like the stated format and is not: `2021-02-30` or `2021-13-01` for a `date`, an hour past 23 for a `date-time`, `a@b.` for an `email`, an identifier one group short for a `uuid`, `256.1.1.1` for an `ipv4` |
| `sendNull` | `null` for a body property that may not be null |
| `sendEmpty` | an empty word, list or object where the description forbids one |
| `oversize` | a word of ten thousand characters, or a list of a thousand items, where the description states a smaller most |
| `wrongRoot` | the whole body as another kind of thing: the accepted object inside a list, a word, a number, `true` |
| `emptyBody` | a body of no bytes at all, where the description says a body is required |
| `notJson` | a body that is not JSON: the accepted one cut off halfway, plain words, the accepted one followed by a word, with a line break written raw inside a piece of text, with a colon left out, or with a comma after its last item |
| `wrongContentType` | the accepted body, unchanged, under `text/plain`, `application/xml` or a form's media type, whichever the operation does not take |
| `beyondItsWidth` | a number past what its format holds: `2147483648` where the description says `int32`; and, inside a body, one as many digits long as an oversized word, past what a reader of numbers agrees to read at all |

Since each breaks something the description states, the request records that it expects to be
refused. It also records what was changed, and in which accepted request, so a stored run can put
the two side by side.

Some things are never changed, because the change would not be the one recorded: a value in the
path is never left out or emptied, since the address would then be a different one; a header the
client writes itself, such as `Content-Type`, is never left out or moved, since it would be put
back; and a body sent as the fields of a web form, which cannot say `null`, is left alone. A change
to one value never goes to the body as a whole, and a change to the body as a whole never goes
inside it.

Which kinds of change are made, and how large an oversized value is, are **settings**, not part of
the plan, because they are about how the tool behaves rather than about the API: every kind can be
switched off, and `violations` switches them all off at once — see
[`mutation.*`](settings.md#mutation). With it off, a strategy of kind `mutation` builds every request
from its sources, exactly as an ordinary one would.

A run that changes accepted requests is, like one that draws on `observed`, not repeated by its
seed alone: which requests were accepted is the API's answer.

## Series of requests around a thing the run created

A strategy of kind `sequences` builds its requests another way again. When it is drawn for an
operation that **creates** something - a `POST` - that request becomes the first of a short **series** about the thing it
creates. The rest of the series is sent one step at a time, each once the answer to the one before
has arrived, with the identifier the API gave the thing.

```yaml
  - name: sequences
    share: 10
    kind: sequences
    sources:
      - source: enum
      - source: random
```

Some faults only show across several requests, and every series asks one question about one of
them:

| Series | The question | After the creation |
|---|---|---|
| `readAfterDelete` | Is a deleted thing gone for whoever reads it? | read it, delete it, read it again, read what hangs from it |
| `deleteTwice` | Is a second deletion answered calmly? | delete it, delete it again |
| `writeUnderDeleted` | Can something still be written under a thing that no longer exists? | delete it, then add or change one thing under it |
| `putTwice` | Does the same replacement twice leave the same thing? | replace it, read it, the same replacement again, read it again |
| `safeGet` | Does reading change anything? | read it, read it in other ways, read it again |
| `createTwice` | Does creating the same thing twice break anything? | the same creation again |

Which series a creation starts is drawn among the ones that can be asked of what it makes, from
where the document says the thing lives: `/owners/{ownerId}` for `POST /owners`, `/pets/{petId}`
for a pet made under its owner. The identifier is read from the reply to the creation, and from its
`Location` header when the reply carries none - never from inside a list, which may list things
that were there before. A `POST` whose address ends in a gap, such as `POST /pet/{petId}`, names a
thing that already exists, so it is only ever sent twice. A series stops when a step its question
needs is not a success: nothing is asked about a creation the API refused.

Its `sources:` build every step of every series, the creation included, and every request the
strategy builds for an operation that creates nothing, which is built the ordinary way. The plan
RESTest carries gives its `sequences` strategy nominal's sources, so that a turn that starts no
series builds exactly the request nominal would.

Which series are sent is a **setting**, not part of the plan - see
[`sequences.*`](settings.md#sequences). With all six off, the strategy builds every request the
ordinary way. What a series learns stays with it: the memory of what the API returned does not hear
its replies, since the thing it made is usually deleted moments later.

Each step records which series it belongs to, its place in it, and the earlier exchanges it
follows, so a stored run says what every step was for. A run that sends series is, like one that
draws on `observed`, not repeated by its seed alone: its later steps are built from its earlier
answers.

## The first round of a run

Before anything is chosen, a run sends every operation it can test once, each with the request it
is most likely to accept, and only then starts drawing. That first round uses your plan too: the
first strategy of kind `nominal`, or if there is none the first that is not of kind `fuzzing`, with
two differences. A plan whose every strategy is of kind `fuzzing` has no request anybody believes in,
and so no first round.

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

## How much of a run pushes at the API

The plan RESTest carries gives a tenth of the run to its strategy of kind `fuzzing`. To change that,
change its `share` in a copy of the plan, and the shares of the others so that they still add up to
a hundred. To push at the API not at all, leave the strategy out and share its tenth among the
others. There is no option for it on the command line: the plan is the one place a share is said.

## What this version refuses

A plan this version cannot read is refused by name rather than half-understood — a plan that quietly
lost a strategy would send a quarter of its requests somewhere nobody asked for, and nothing in the
output would say so.

- a `version` this build does not read, said as a version rather than as a misspelling, with the
  command that prints a plan it does read
- a member nobody recognises, which is usually a typo and would otherwise load and do nothing
- a strategy with no `kind`, or a `kind` that is not one of the four
- a strategy with no sources, whatever its kind
- a `source:` word RESTest does not have, answered with the words it does — `fuzzing` among them,
  which is said to come with the kind `fuzzing`
- a source named two ways at once, or none
- a `weight` on a source that is not in a group, where there is no choice for it to divide
- a group written as a list rather than a map, a key in it that is neither one of RESTest's words nor
  `dictionary`, or a `dictionary:` in it that names no list
- a weight that is not a whole number from 1 to 100
- shares, or the weights in one group, that do not add up to a hundred
- a group with one source in it, where there is nothing to choose between
- two strategies with one name
- a key written twice, which YAML allows and which would leave the first quietly replaced
