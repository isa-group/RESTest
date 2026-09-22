# ADR-0023: A plan is an ordered list of named sources, and the order is the preference

**Status:** Accepted
**Date:** 2026-09-22

## Context

ADR-0013 §2 sketched a file: named strategies, each with a share of the budget and an ordered list
of groups over named sources, a group being either *exclusive* or *weighted*. ADR-0020 §5 settled
what a share and a weight each divide. Neither said what the file looks like, and nothing built it:
until now every source was hard-wired in `RandomTestCaseGenerator`'s constructor, in one order, the
same for everybody, with the shares as constants.

M2.5b is what forced the question. It gives the tool a memory of what the API has returned, and the
maintainer asked that the memory be a source somebody *chooses* rather than one switched on for
everybody — which is exactly what §2's own example shows, `observed` sitting in a weighted group
beside `enum` and `example`. There was nowhere to choose it. So the plan file comes first, and
2.5b adds one word to a table it can then join.

Writing the format out, twice, is what produced most of what follows. Three of §2's ideas turned
out to be saying something the structure already said, and the maintainer caught two of them by
reading a draft.

## Decision

### 1. A strategy is an ordered list of sources, and the order is the exclusivity

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
  methods: [GET, HEAD, OPTIONS, TRACE]
  only: [getPetById, "GET /pets/{petId}"]
```

**There is no `exclusive` keyword.** §2 writes one, but an exclusive group means *ask these in turn
and stop at the first answer*, which is what an ordered list already means: `exclusive: [a, b]` and
two consecutive entries are the same thing. Written out, the plan RESTest ships was four
single-entry groups of pure noise. `weighted:` is therefore the only group keyword, and §2's
semantics are untouched — only its syntax shrinks.

### 2. A source says which kind of thing it is

Three forms, and a plan says which it means rather than leaving it to be guessed:

| Written | Means |
|---|---|
| `source: <word>` | one of the tool's own: `enum`, `example`, `default`, `random` |
| `dictionary: <name>` | one list of values, by the name the list carries |
| `dictionaries: given` | every list this run was handed |

§2 wrote `custom` and `format` among the source words. Both are dictionary *keyings* rather than
sources, so they are named by naming the dictionary. And flat names would let a list called
`example` shadow the built-in silently, with no way for a reader to tell which kind a bare name
was — the built-in set grows, while file names are the user's to choose.

`dictionaries: given` exists so that the plan RESTest ships can speak about lists whose names it
cannot know. It expands to **one** answerer, not one per list: a step carries one weight, and three
lists arriving as three answerers would quietly give that step three times its say. Within it the
lists written for one particular place are asked before those written for a whole kind of value,
which is the one piece of ranking that does not come from the plan, because no plan should have to
state that a list written for `petId` knows more about a `petId` than a list of every date in the
world.

### 3. Shares add up to a hundred, and so do the weights in any one group

Both are validated when the plan is read. There is nothing behind these numbers to derive: 75 means
75.

An earlier draft let a strategy serve only some operations — reads, say, or writes — so that the
budget could be split per kind. It was dropped. Its one real use is a different fuzzing share for
writes than for reads, and it was the sole cause of every confusing thing in the format: shares that
summed to 175, redistribution, per-operation validation, and operations that no strategy served. A
strategy without a scope already means *every operation*, so adding it later changes no file anybody
has written — and 2.5b will supply the evidence for how the budget should be split.

### 4. The operation filter is the run's, and there is no `safeOnly`

`operations:` says what the run may touch at all. Absent, it touches everything. It can only ever
subtract: the run has already worked out which operations it could test and named the ones it
cannot, and this narrows that set rather than reopening it.

ADR-0013 §6 asks for a `safeOnly` key. **It is not built.** HTTP's safe methods are exactly `GET`,
`HEAD`, `OPTIONS` and `TRACE`, so `methods:` already says it, and a second way to write one thing
would force a rule for a file that sets both. §6's real point survives and is why the key is not
called `readOnly`: an API that searches with `POST` is ordinary, and one of the five in the priority
corpus does exactly that. The format documentation carries `methods: [GET, HEAD, OPTIONS, TRACE]` as
a named recipe, which is where the word *safe* belongs.

### 5. Whether a strategy pushes at the API is derived, not declared

The `Strategy` record carries a flag saying its requests are built from values nobody sensible would
send; the console reads it to say how many of a run's requests were pushing rather than trying to
work. **The file has no key for it.** It is worked out from what the strategy draws on: one that
draws on the list of values to push with is pushing, because that is what those values are.

Two reasons. A word whose only job is to be true is a word that can be false, and deriving it means
a plan somebody wrote by hand gets the same treatment as the one RESTest ships. And the honest name
for the concept is ADR-0013 §3's **intent**, which oracles read and which the roadmap assigns to
M3.1b — so publishing a key now would publish a word we already know we rename, in a file people
commit beside their specifications.

The derivation is over the plan; whether the strategy *actually* pushes also needs the list to be
there. A plan naming a list nobody handed over describes a strategy with nothing to send, and a run
that called those requests "pushing at the API" would put a meaningless number in its summary.

### 6. A strategy left with nothing but invention is left out

The plan RESTest ships pushes with a list of awkward values, and a run given no such list has
nothing awkward to send. That strategy would spend a quarter of the budget on ordinary invented
values under another name and, having no `enum` step, would send values a document says are not
allowed. Left out instead, and the remaining shares keep the proportions the plan wrote them in.

**The test is what remains, not what is missing.** An earlier version dropped any strategy all of
whose named lists were absent, which discarded a strategy that had merely lost one source of
several: a plan reading `[dictionary: mine, source: enum, source: example, source: random]` lost
its whole nominal strategy when `mine` was not handed over, leaving the entire run pushing at the
API. A strategy that still asks the document what it says has most of its job.

### 7. The plan RESTest carries is a file, and `--fuzzing` adjusts it

`default-campaign.yaml`, shipped in `restest-gen`'s resources beside the list of awkward values that
already ships from there, read at startup when no `--campaign` is given, and printed by
`--print-campaign` so that somebody can save it, change a line and hand it back. A plan written into
the tool would be one nobody could read or copy.

`--fuzzing` is not a second way of arranging sources: it adjusts the shares of that plan and nothing
else, so the option and the file cannot come to disagree about anything except the one number the
option is about. Naming both `--campaign` and `--fuzzing` is refused, because a plan sets the share
of every strategy it names and the option sets one of them, and honouring both would mean deciding
which the person meant by a rule nobody wrote down.

### 8. The shipped plan chooses among its sources rather than ranking them, and that was settled
by measurement after being decided the other way twice

The objection to ranking is real: asked in turn, a source that answers stops the ones behind it
*for that value*, so a parameter whose document offers one sample receives that same value on every
request for a whole run and invention never explores it. Across the priority corpus there are 125
samples against 13 enumerations and no declared defaults at all, so that is the common case rather
than a corner.

The argument for ranking it anyway looked strong, and was measured first at the wrong thing. On
pet-clinic, whose document declares `ownerId` example `1`, asking the sample outright sends the
documented identifier in 100% of requests where weighting it against invention sends it in about a
third. An identifier a document writes down is usually one that existed in the API its author was
looking at, so the conclusion drawn was that ranking must cover more of the API.

**It does not, and the reason is that a run deletes things.** Since M2.5a a run exercises the
operations that create, change and delete, so the row a document names stops existing partway
through. A plan that cannot vary that value then sends the same 404 for the rest of the run, and
the three operations taking an owner never recover. Measured end to end against a containerised
pet-clinic, **restarting the API before every run** - without which the comparison measures the
damage left by the previous one:

| Seed | sample ranked first | sample chosen among |
|---|---:|---:|
| 3 | 18 | 19 |
| 7 | 17 | 18 |
| 23 | 17 | 25 |
| 41 | 16 | 21 |
| 99 | 16 | 15 |
| **mean operations answering 2XX** | **16.8** | **19.6** |

So the shipped plan weights, and the lesson is about the metric rather than the number: *fidelity to
the document* and *whether the request worked* come apart the moment a run mutates the API, and only
the second is worth optimising. `DeclaredSamplesAcrossTheCorpusTest` asked for the first on every
draw; it now asks that the author's identifier is among what goes out and is the value most often
sent, which is what actually helps, with these figures in its own comment.

The pinning does not disappear - it is bounded by the weight instead - and M2.5b removes the reason
for it, by supplying identifiers that are real *and* varied.

## Consequences

- **A plan somebody names and RESTest cannot read ends the run**, which is the first place this
  tool refuses rather than carrying on with less. §4's filter is why: a plan can say "only the
  operations that read", and carrying on with a plan that says nothing of the kind would answer
  that by writing to the API. A list of values that will not load still only costs its values.
- **A group means something slightly different below the top level.** Invention fills what is
  inside a body by asking the rest of the strategy, and the rest of the strategy is built without
  invention in it - so a group of `random` and `default` is a choice at the top level and leaves
  `default` answering alone underneath. Making the two identical means letting invention consult a
  chain that contains invention, and the depth guard that would have to stop that recursion lives
  inside one provider rather than in the interface between them. Recorded rather than fixed.
- **A run's arrangement of sources is a file anybody can read**, print and change, instead of a
  constructor. ADR-0013's claim that adding a source is "one class and one line in a plan" is now
  literally true, and 2.5b is the increment that tests it.
- **The default changes for every run**, in two ways beyond the sources being named. A stated
  default is now weighted against invention rather than asked before it, which matters for nothing
  in the priority corpus, since none of the five declares one. And lists somebody handed over are
  asked at one point in the order rather than split around the document by how particular they are;
  the ranking among *them* survives, and a plan that wants one placed after the document's own
  samples names it there.
- **The strategy that pushes fills nested values from its own sources** rather than from the
  document. It used to be handed the document's sources for anything inside a body, by a line whose
  comment said the plan would answer this question instead. The plan now does.
- **Two components read YAML the same way**, because the reader the dictionary format needed — JSON's
  six kinds of value and nothing more, numbers kept exactly as written, a key written twice refused
  — is now one class used by both, rather than the same hundred lines twice.
- **The scheduler still does not know what time it is.** A share is drawn per request, as it was, so
  it is honoured on average rather than as a stretch of the clock. Moving time-keeping out of
  `RunLoop` is M2.10b.

## Alternatives considered

- **Keeping `exclusive` for symmetry with `weighted`.** Rejected: it says what the list order says,
  and the plan RESTest ships needed four of them in a row to say nothing.
- **Flat source names, with a rule that a dictionary may not take a built-in's name.** Rejected: it
  fixes the collision and not the reading. A person scanning a plan could still not tell which kind
  a name was, and the set of reserved words grows with every source added.
- **Shares that sum to 100 across the file, with scoped strategies renormalised per operation.**
  Considered at length and dropped with the scoping itself. It cannot express "a quarter of every
  operation goes on pushing" once scopes are disjoint, and the alternative that can — shares summing
  to 100 per operation — produces a file whose column of numbers reads 175.
- **Ranking the document's sample above invention in the shipped plan.** Adopted twice on the
  strength of a unit-level measurement and rejected on the end-to-end one in §8. Kept here because
  the mistake is instructive: the metric that favoured it counted how often the tool quoted the
  document, which is not the same as how much of the API it reached.
