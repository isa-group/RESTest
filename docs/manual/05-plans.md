# Plans

Every run follows a **plan**: a short file that says how the run builds its requests, where their
values come from, and which operations it may touch. You never need to write one, because RESTest
carries its own; but its plan is a file you can print, change and hand back, and this chapter shows
the changes most people want. The format itself, every word of it, is [The campaign
file](../campaign-format.md).

## The plan RESTest carries

```bash
./restest run --print-campaign
```

writes out the plan, with comments that explain each part. Its strategies and their shares, picked
out of it:

```bash
./restest run --print-campaign | grep -E 'name:|share:'
```

```
  - name: nominal
    share: 45
  - name: sequences
    share: 10
  - name: mutation
    share: 20
  - name: fuzzing
    share: 25
```

A plan is a handful of **strategies**, each a way of building a request, with a
**share** of the run out of a hundred:

- `nominal` builds requests meant to be accepted.
- `sequences` does the same, and turns each creation into the first step of a short series about
  the thing created.
- `mutation` takes a request the API accepted and changes one thing in it.
- `fuzzing` pushes at the API with values nobody sensible would send.

Each strategy lists its **sources**, the places a value can come from, which are asked in the order
written. `enum` is first: when the document gives a closed list of the values it accepts, a value
comes from that list. After it comes a **weighted** group, among which the value is drawn by weight
each time: `example` (a sample the document writes down), `random` (a value invented to fit what the
document says), `observed` (a value the API has already returned), `dictionaries: given` (the lists
you hand over), and `default` (the document's default).

## Changing a plan

Save it, change it, hand it back:

```bash
./restest run --print-campaign > plan.yaml
./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s --campaign plan.yaml
```

A plan RESTest cannot read stops the run before it sends anything, and says why: a misspelt word, a
share that does not add up. It never carries on with half a plan, because what a plan leaves out may
be exactly what you asked it to keep away from.

### Changing nothing on the API

To keep a run to the requests that only read, add a block at the end of the plan:

```yaml
operations:
  methods: [GET, HEAD, OPTIONS, TRACE]
```

Those are the methods HTTP itself calls *safe*. Most APIs keep to that, but not all: an API that
searches with `POST` loses that search, and one that changes something on a `GET` is not stopped by
this.

### Only some operations

`only` keeps the run to the operations it names, by the `operationId` the document gives them or by
method and path:

```yaml
operations:
  only: [listOwners, getOwner, "GET /api/pettypes"]
```

An operation named here that the document does not have is reported before any request is sent,
since a plan written against an older document tests less than its author believes.

### Pushing harder, or not at all

`fuzzing`'s share is how much of the run pushes. Shares add up to a hundred, so to change one, change
another to match. To push not at all, take the `fuzzing` strategy out and give its share to the
others.

### A run that the seed repeats

A run prints its **seed**, the number every choice it makes by chance comes from. `--seed` with the
same number makes the same choices. But three things in the plan RESTest carries make a run depend on
the API's answers as well — the `observed` source, the changes to accepted requests and the series —
so with them the seed makes a similar run rather than the same one. [Getting the seed
back](../switches.md#getting-the-seed-back) has the plan and the settings that leave all three out,
and with them `--seed` repeats a run exactly. `--store`, which keeps the run you actually had, is
usually the better record.

## What a plan is not

A plan is about **the API**: where values come from, what to touch. How **the tool** behaves — how
many requests it keeps in flight, how long it waits — is a setting, the subject of [chapter
7](07-settings.md), because it would be the same against any API on the same machine.

The [next chapter](06-dictionaries.md) hands over values of your own.
