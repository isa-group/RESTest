# ADR-0028: A creation may start a short series about the thing it made, and each series asks one question

**Status:** Accepted
**Date:** 2026-09-28

## Context

Some faults only show across several requests:
- an API may say it deleted a thing and still hand it back;
- it may fail on a second deletion of the same thing;
- it may let something be added under a thing that no longer exists;
- it may break when the same creation arrives twice;
- it may give a different result when the same replacement is sent twice;
- it may change a thing merely because the thing was read.

No single request reaches any of those, however well it is built. Roadmap row 10.3 asked for
sequences over real resources: each series creates its own victim, and what it learns stays with it.

The unit a series needs was built once already. 9.3 sent a creation and then the request that needed
its identifier, and was measured and not merged, because its trigger waited for the memory of
observed values to be empty. Pairs fired 7 times in 338,229 requests
([ADR-0013](0013-input-generation.md), Amendment M9.3). That amendment ends on the point this record
starts from: *the trigger, not the unit, is the open part*. A series that creates its own victim
waits for nothing.

The row as written named four shapes. At the maintainer's request, the shapes were chosen afresh
from what the related tools do and from the two properties HTTP gives methods (RFC 9110 §9.2):
- **safe:** GET, HEAD and OPTIONS change nothing;
- **idempotent:** PUT and DELETE leave, however often they are repeated, the state one request would.

Each tool was checked against its documentation or its source:
- **RESTler:** `use_after_free_checker.py`.
- **Schemathesis:** `specs/openapi/checks.py`.
- **EvoMaster:** `HttpSemanticsService.kt`, and Sahin and Arcuri, *Validating HTTP Semantics in REST
  APIs With Constructed Call Sequence Scenarios*, 2026.
- **CATS:** `CheckDeletedResourcesNotAvailableFuzzer`.
- **WuppieFuzz:** its 2025 paper.
- **RestTestGen:** its ICST 2020 paper, and its mass-assignment extension.

The legend is ✓ built in, ◐ in part, and — not done or not documented.

| Series | RESTler | Schemathesis | EvoMaster | CATS | RestTestGen | WuppieFuzz |
|---|---|---|---|---|---|---|
| A thing created is there to read | ◐ sent, not checked | ✓ | ◐ through `Location` (fault 120) | — | ✓ | ◐ |
| A thing deleted is gone for a reader | ✓ same resource only | ✓ reads only, its children included | ✓ GET, DELETE, GET (fault 113) | ✓ | — | — |
| Deleting twice is answered calmly | — (skips the DELETE itself) | — (excluded: DELETE is idempotent) | — | — | — | ◐ duplicates any request |
| Something written under a deleted thing is refused | — | ◐ reads only | — | — | — | — |
| The same PUT twice leaves one state | — | — | ✓ PUT, GET, PUT, GET (fault 118) | — | — | ◐ |
| A PUT replaces the whole thing | — | — | ✓ PUT, GET (fault 117) | — | — | — |
| Reading changes nothing | — | — | — | — | — | — |
| The same creation twice breaks nothing | — | — | — | — | — | ◐ |
| A child reached through another parent is refused | ✓ | — | — | — | — | — |
| A failed creation leaves no trace | ✓ | — | — | — | — | — |
| A failed change leaves no trace (fault 114) | — | — | ✓ | — | — | — |

Two things in that table decided more than the rest.
- **Where a tool combines checks, it guards against their interfering.** Schemathesis's
  use-after-free check leaves out a PUT and a POST after a deletion, because either may create the
  thing again, and a repeated DELETE, because 204 is a correct answer to it. RESTler's retries only
  operations consuming exactly the deleted hierarchy.
- **EvoMaster's nine rules found 166 faults on 36 APIs, and almost none of them were server errors**
  (133 were `Allow` headers). The competition counts server errors, and how a verdict is reached
  scores nothing.

How the five priority APIs answer a creation was read from the recorded runs of 8.4, in the
benchmark proxy's own database:

| API | Creations answered 2XX | Identifier in the body | `Location` |
|---|---|---|---|
| pet-clinic | owners, pets, pet types, visits, specialties, vets (201) | `id` | always, e.g. `/api/owners/895`, without the document's `/petclinic` base; a pet's points at `/api/pets/854` |
| notebook-manager | notebooks (201) | `id` | no |
| gestao-hospital | stock and check-in (200); hospitals 400 since 28 September | `id`, a MongoDB identifier | no |
| kafka-rest-proxy | no topic created in twenty minutes: 400, 404, 422, 500 | it would be `topic_name` | no |
| flight-search | one registration in twenty minutes | — | — |

None of the five documents declares OpenAPI `links`, and one of the corpus's 46 does.

## Decision

### 1. A strategy in the plan sends series

A strategy may say `sends: sequences`. The plan RESTest ships has four strategies: nominal 45,
sequences 10, mutation 20, fuzzing 25. The ten points come out of nominal, and the strategy sits
right after it, so the draw ranges of mutation and fuzzing do not move. The strategy's `sources:`
are those of nominal, and they build every step of a series, the creation included.

A strategy that sends series cannot also change accepted requests: each says what its requests are
about, and the two would say different things. It cannot push at the API either, since a series is
only worth sending about a thing created with values meant to work. The plan file refuses both.

### 2. A series starts only on a creation's turn

When the strategy is drawn for a `POST`, that request is the first step of a series. Which series is
drawn among those the document says can be asked of what the `POST` makes, and that are switched on.
The draw uses numbers of its own (§8). On any other turn, the strategy builds exactly the request
nominal would. So a creation the API refuses costs no request that would not have been sent anyway.

### 3. One series, one question

A series is an experiment: its first steps prepare a state, and its last ones ask one question. Only
steps that read, and that are needed to read the answer, are added to it. Any other write belongs to
a series of its own.

The rule was set on a counterexample the maintainer asked about: POST, GET, DELETE, GET, PUT,
DELETE.
- The PUT may create the thing again, since RFC 9110 lets it.
- If it does, the last DELETE deletes something new, and the question it was meant to ask - is a
  second deletion answered calmly? - has become a different one, depending on how the PUT was
  answered. A server error there cannot be put down to anything.
- That is the reason ADR-0027 changes one thing at a time, and the reason Schemathesis leaves the
  PUT out.
- Reads do not interfere, since a safe method changes nothing. So several reads after one deletion,
  or two reads around the step a question is about, remain one question.

| Series (its switch) | The question | Steps after the creation | What they expect |
|---|---|---|---|
| `readAfterDelete` | Is a deleted thing gone for whoever reads it? | read it; delete it; read it again; read each address under it | every read after the deletion: a refusal |
| `deleteTwice` | Is DELETE idempotent? | delete it; delete it again | nothing in particular: 404 and 2XX are both right, a server error is not |
| `writeUnderDeleted` | Can something still be written under a thing that no longer exists? | delete it; one addition, change or deletion under its address, drawn | a refusal; for a deletion under it, nothing in particular |
| `putTwice` | Is PUT idempotent, and did it replace the whole thing? | replace it; read it; the same replacement again; read it again | nothing in particular - the reads are there to be compared |
| `safeGet` | Does reading change anything? | read it; read it with some optional parameters; ask for its headers; read the list it joins; read it again | nothing in particular - the first and last read are there to be compared |
| `createTwice` | Does creating the same thing twice break anything? | the same creation again | nothing in particular: a duplicate may be refused or made, but not fail |

A step whose answer the question needs ends the series when that answer is not a success:
- a creation, or a deletion;
- `putTwice`'s first replacement;
- the first read of `readAfterDelete` and `safeGet`, without which there is nothing to compare.

So a creation refused makes nothing to ask about, and a deletion refused deletes nothing. A step
that only adds an observation - one of the reads under the address - is left out when it cannot be
built.

Against the row as written:
- `deleteThenUse` became three questions (`readAfterDelete`, `deleteTwice`, `writeUnderDeleted`),
  and lost the replacement of the deleted thing, whose answer says nothing clear either way.
- `danglingReference` became the reads under the address in the first and the writes in the third.
- `createTwice` is as it was.
- `crossUpdate` - the identifier of one thing written into an update of another - is not taken. No
  tool does it, and it applies to few operations.
- `putTwice` and `safeGet` are new: the idempotency and the safety the maintainer asked to be tested.

### 4. Where the thing lives, read off the addresses

For every `POST`, `Creations` reads where the thing it makes lives, and what hangs from it:
- **Its own address, by prefix:** the creation's address with one more gap. `/owners/{ownerId}` for
  `POST /owners`, with gaps matched by position whatever they are called.
- **Its own address, by kind:** any address about the same kind of thing, behind a gap named the way
  identifiers are. `/pets/{petId}` for `POST /owners/{ownerId}/pets`, with the kind read by the rules
  of ADR-0021's M9.2 amendment.
- **The addresses under it:** those that carry on past one of its own.
- **The list it joins:** the read at the creation's own address.

A series asks each method at the first address that has it, preferring one address that has
everything the question needs. pet-clinic deletes a pet only at `/pets/{petId}`, and that is where
the pet is read before and after. Only operations the run may touch are considered, so a plan
limited to reads and creations never has a deletion sent for it.

### 5. The identifier: the body first, then `Location`, never a guess

The thing's identifier is read from the creation's reply. The reply is unwrapped as the memory of
observed values unwraps one: an object, a list's elements, or what a wrapper with no identifier
holds. Then three rules are tried in turn:
1. a property named like one of the gaps the thing's own addresses have;
2. one called `id` or `_id`;
3. the kind of thing followed by id.

A property only written like an identifier - `ownerId` inside a pet - is some other thing's, and is
not taken. When the body carries none that fits, the `Location` header is read. The end of its
address is matched against the thing's own, because pet-clinic answers a creation under
`/petclinic/api` with `/api/owners/895`. Every candidate must fit the gap it goes into: the kind of
value, the declared form, the closed list, and whether an address can carry it.

Declared `links` are not read. They are row 4.3, and nearly no document writes them.

The gaps before the thing's own are sent what the creation was sent, so a pet is read under the
owner it was created under. So is a body's own identifier property, where a step sends a body that
declares one and does not mark it read-only. Every identifier a series uses is one it made, or one
it was given along with what it made.

### 6. The unit of work, and interference

These are the two decisions ADR-0013 left to M4, and they are now taken.

- **The unit is the series.** It is a fixed list of steps planned when it begins. Each step is built
  only once the answer to the one before has been heard, so one step of a series is in flight at a
  time. Any number of series and ordinary requests go out alongside.
- **The handover.** The loop hands every step's answer to the scheduler from the thread it arrived
  on, before the slot the request held is released. The scheduler only queues it, and the thread
  that decides builds the next step when it next asks what to do.
- **Ordering.** The next step of a series goes before the ordinary turn, and never during the
  opening lap, which starts none.
- **Interference is accepted, not prevented.** An ordinary request may come across a series' thing
  by chance. Nothing the competition scores depends on attributing an answer, and 4.5's stateful
  oracles are the ones that will have to decide what they need.

### 7. What a series learns stays with it

Neither the memory of observed values nor the memory of accepted requests hears a step of a series.
The thing is usually deleted moments after it is made, so an ordinary request handed its identifier
would be asking about a thing whose fate only the series knows. A step kept as an accepted request
would be changed by the mutation strategy into a request about that same thing. This goes further
than the row, which asked for what a unit knows about what it *deleted* to stay in that unit: it
keeps everything it made there too.

### 8. Numbers of their own

Three copies of the seed are split:
- the first remains the likeliest request's, as ADR-0026 §3 has it;
- the second chooses which series a creation starts, and which write goes under a deleted thing;
- the third feeds the sources the later steps are built from.

The first step is the operation's own turn. It is built from the strategy's sources and the
generator's own numbers, with everything the operation requires and a body wherever one is
described. Later steps are built whenever answers arrive, and draw nothing from the numbers the
ordinary requests come from. Which ordinary requests follow therefore never depends on how fast the
API answers a series.

With every series switched off, the shipped plan sends exactly what the plan without the strategy
sent, request for request, from the same seed, and a test holds that. Holding it needed a change
beyond this row: **strategies that name the same sources now share one built set of them**. Each
invention source reads a spelling rule once, and reading one draws numbers. So two strategies
building from the same sources separately parted company the first time the second met a pattern.
10.1's mutation strategy had the same flaw since it shipped; `RunOrderTest`'s pinned requests did not
move when it was fixed.

### 9. What a step records, and what is not judged

A test case that is a step records a `SequenceStep`:
- the series by its switch's name;
- its step number;
- the earlier exchanges of the same series it was built from or should be compared with (the
  creation, the deletion, the read to agree with);
- a sentence for a person.

Nothing records the series as a whole: its steps, and the exchanges each names, are the series. This
keeps ADR-0005's reasoning against an identifier that could drift from the edges it summarises.

Intents stay true. A refusal is expected only of a read, an addition or a change sent after the API
accepted the deletion of what it asks about. A replacement or a second deletion after it, which may
correctly succeed, expects nothing in particular, and so does everything else.

The maintainer decided that **verdicts are recorded and not judged** in v2.0. The oracles that would
read these steps - faults 113, 117 and 118 of the catalogue, and one for a read that changes the
thing - are rows 3.2 and 4.5. The competition judges the replies itself.

### 10. Six switches, and no clean-up

`sequences.*` has one switch per series, all on. It has no switch for the group, because the plan's
strategy is that switch: take the strategy out of the plan, or set all six off, and the plan builds
what it built before.

A series leaves what it created behind, as every ordinary creation does. ADR-0013 named clean-up as
a question a unit of work would have to answer, and this is the answer: none.

## Measurement

*To be filled in with the five APIs of the 2027 edition, restarted before every run, five seeds,
sixty seconds, the six switches off against on.*

## Consequences

- **ADR-0013's two decisions left to M4 are taken** (§6), and its §7 table gains a row: a plan that
  sends series is reproduced by replaying the stored run, since its later steps are built from its
  earlier answers. The command line says so under the seed, for a plan with series and no memory
  too.
- **ADR-0005 is amended:** a test case says when it is a step, a seventh component that travels in
  its JSON, so the store's layout does not change. A run stored before reads back with none.
- **ADR-0023 is amended** for `sends: sequences` and the shipped plan's four strategies; ADR-0025
  for the `sequences` group it named; ADR-0026 for the scheduler handing on a series' next step.
- **The console counts the steps of series by the kind of series**, and how many were begun. It
  interprets none of them, since what each step is for is the tool's business and the stored run
  says it.
- **3.2 and 4.5 have what they need** to judge a stored run offline: which series, which step, what
  came before, what was expected.
- **The mutation strategy's fallback is now exactly nominal's** (§8), which it was meant to be.

## Alternatives considered

- **A fixed number of series at a time**, a setting of their own, alongside the ordinary rounds. It
  is simpler, and the share would follow concurrency rather than a number in the plan. The
  maintainer chose the plan's share, so that series are one more way of building requests, divided
  like the others.
- **One series per creation per round.** Simple, but on pet-clinic it would add about twenty-four
  requests to every round of thirty-five, whatever anybody asked for.
- **Starting a series on any turn it is about**, a read of one owner starting "create, delete, read".
  The rate would not depend on how many creations an API has, but every series would test one
  operation for the price of two requests of preparation.
- **The likeliest request as the creation.** It is the best single request on pet-clinic, but it
  asks the memory before anything else. On kafka-rest-proxy, whose topics are named by whoever
  creates them, that means the name of a topic the API already has, which Kafka refuses to create
  again, so the series would end at its first step. The strategy's own sources, drawn, vary.
- **Letting the memories hear the series.** It would add identifiers the series delete moments
  later, and hand the mutation strategy requests about things only a series knows the state of.
- **One long series per deletion**: read, delete, read, replace, delete again. It was rejected on
  §3's counterexample.
- **`crossUpdate`.** No tool does it, it needs an update whose body declares the thing's identifier
  as writable, and three of the priority APIs have one. It is left for when the evidence asks for it.
- **Judging now.** The maintainer chose to record, above.
- **Reading declared `links` first.** It is exact where it exists, but the priority corpus has none
  and the wider corpus one; it is row 4.3.
- **Cleaning up after a series.** It is a request more per series, and it would change the state the
  ordinary requests meet, for a benefit nothing measures.
