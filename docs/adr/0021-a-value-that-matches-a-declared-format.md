# ADR-0021: A value matching a declared format is worked out, not looked up

**Status:** Accepted
**Date:** 2026-09-18

## Context

M2.4's job is one line in the roadmap: *format-aware and pattern-based generators (date, e-mail,
UUID, regular expressions)*. It is split, and this record covers the first half — the kinds of text
a document names. Patterns are M2.4b, which needs a way to build a string from a regular expression
and a rule for what wins when a string declares both; neither is settled here.

`RandomValueProvider` has admitted the gap in its own class comment since M1.5: a document can say
what *kind* of text it wants, and nothing paid any attention. A parameter described as a timestamp
was sent an ordinary word, the API answered 400, and the operation was never really exercised.

### Where a declared format actually is, measured

Every `openapi.*` file in the corpus, parsed, and every **parameter** asked what kind of text it
wants:

| | |
|---|---|
| Documents parsed | 50 |
| Parameters declaring a kind of text this can build | 23 — `date-time` 14, `date` 8, `password` 1 |
| Parameters declaring a name nobody could build a value for | 2 — `Integer`, `string` |
| Parameters declaring a kind of text **in the five APIs the tool is measured on** | **0** |

The last row is the one that shaped this increment, and it is not an oversight in those five
documents. All of them describe dates and identifiers — `departureTime` and `arrivalTime` in
flight-search, `entryDate` and `exitDate` in gestao-hospital, `birthDate` in pet-clinic,
`lastUpdate` in notebook-manager. Every one of those descriptions sits in a shape under
`components/schemas`, reached from a request body or from a reply, and never from a parameter.

Nothing sends a request body until M2.5. So the measurable gain here is 23 parameters across fifty
documents and nothing at all on the five that matter most — and the whole of the gain on those five
arrives with M2.5, which reaches those shapes. This increment is groundwork, and saying so is better
than pretending otherwise.

### Two names for the same thing, and why they disagree

[ADR-0013](0013-input-generation.md) §1 lists this source as a *format dictionary*, and
[ADR-0020](0020-what-a-dictionary-is.md) §5 calls it "the format dictionary that competes for every
string with a declared `format`". Both names imply a file of ready-made values, shipped with the
tool. The corpus says a shipped file would be the wrong shape.

## Decision

### 1. The values RESTest itself offers for a kind of text are computed

Not read from a file it ships. Two of the kinds make the case on their own.

**A date goes stale.** A file of literal timestamps written today describes a past that recedes
further every year the tool is used. One of the five APIs the tool is measured on is a flight
booking service, whose departure times are meant to be in the future; a second records hospital
entry and exit dates. A date worked out from the clock is still a plausible date in five years, and
a spread either side of now covers both a birth date and a departure.

**An identifier must not repeat.** A shipped identifier is the same sixteen bytes on every request,
so an API asked to create two things is asked to create both under one name. A fresh one each time
costs nothing and removes the collision.

The inference that would make these better still — that a field called `birthDate` wants a past date
and one called `departureTime` a future one — is reading meaning out of a name, which is deferred
work and is not done here.

### 2. A file a user writes is untouched, and still wins

Everything ADR-0020 built stays. `--dictionary` still reads a file keyed by `format`, and such a
file is asked **before** this source, in the tier ADR-0020 §4 assigns to a list that knows about a
kind of value. Somebody who maintains a list of dates beside their own specification knows more
about their API than RESTest does, and neither of the two problems above applies to a file its owner
keeps up to date.

So the ask order ADR-0020 §4 sets out is unchanged; this slots into it:

```
the closed list of values the document says it accepts    nothing overrides this
a list keyed by operationAndParameter or by parameter name knows about one value
the document's own samples and stated default
a list keyed by schema, format or type                    knows about a kind of value
what RESTest knows about the kind of text named           <- this record
whatever can be invented from the shape
```

This clarifies rather than contradicts ADR-0013 §1 and ADR-0020 §5. The source is where both said it
would be and answers the same question; only its insides are a computation rather than a file.

### 3. It answers for the kinds of text it can build, and says nothing otherwise

Twenty names: the timestamps and dates, `time` and `duration`, addresses on the web and in the post,
host names and both kinds of internet address, identifiers, encoded data, passwords, and the four
whole-number widths where they are declared on text rather than on a number. Names are matched
without regard to capitals, because documents are inconsistent about it.

Most are declared somewhere in the corpus — `uri` 569 times, `date-time` 336, `url` 40, `date` 36,
`uint64` 28, `email` 9, `password` 4, `byte` 3, `int64` 3, `uuid` once. The rest are there on one of
two grounds, and both are worth stating rather than implying a corpus count that does not exist.
`time`, `duration`, `hostname`, `ipv4`, `ipv6`, `idn-email`, `uri-reference` and `iri` are standard
spellings with one obvious reading each, and building them costs a line. `int32` and `uint32` are
neither declared on text in the corpus nor standard for text, but they are the same idiom as `int64`
and `uint64`, which are — splitting a family of four down the middle would be the arbitrary choice,
not including it.

A name it does not know — `id1`, `swagger`, `ISBN-13`, and the two in the corpus above — is left to
invention rather than guessed at. Numbers are left alone entirely: a number declared `int32` is
already invented well inside that width, and answering for it would gain nothing.

### 4. Where the shape contradicts itself, it says nothing

A document may name a kind of text and then forbid every value of it, by demanding a length no
address or timestamp could have. This checks the length the shape declares and declines when its
value would not fit, rather than sending something the document refuses while claiming to believe in
it.

That is stricter than the file-backed dictionaries of ADR-0020, which offer a value without checking
it against anything but whether it can be written into the request. The difference is deliberate and
the asymmetry is defensible: a value in a file is one somebody chose for their own API, and a value
computed here is one RESTest chose. Bringing the dictionaries up to the same standard is not done
here — across the whole corpus exactly **one** parameter declares a kind of text and a length bound
together, so a mechanism for it would cost more than it returns. The number is recorded so the day
it changes, the decision can be taken again.

### 5. One seed and one day are one run. **This amends ADR-0013 §7**

Both the randomness and the moment are handed in rather than read from the surroundings, and the
identifier is built from the run's own generator rather than `UUID.randomUUID()`, which would take
its bytes from somewhere else entirely.

But a date worked out from the clock is not a function of the seed, and §7's table says a strategy
without a memory — "nominal from dictionaries, limits, fuzzing" — is reproduced by the seed. The
nominal strategy now contains a source that the seed alone does not pin. Saying nothing about that
would leave the table wrong, so: **the promise becomes one seed and one calendar day.**

Two runs started from the same number on the same day send the same requests. Two runs a week apart
send the same requests with the dates moved on by a week. That is the smallest weakening that gets
the anti-staleness argument in §1, and it is bought deliberately:

- The moment is **truncated to the day** before anything is worked out from it, rather than read
  afresh for every value. Without that, two runs five *seconds* apart would differ — a seed that
  reproduces nothing is worse than one that reproduces a day.
- A time of day, where one is wanted, comes from the seeded generator rather than from the clock,
  so it is pinned like everything else.

What this costs is on the record: a failure that turns on a particular generated date cannot be
reproduced from the seed the day after, and `--store` is off by default (M1.7b), so replaying the
run is not automatically available either. Anyone chasing such a failure needs `--store` on. The
alternative — deriving dates from the seed alone and ignoring the clock — was rejected because it
brings back exactly the staleness §1 exists to avoid.

A test that needs a fixed date hands in a fixed clock, which is the arrangement `JsonReport` and
`OracleListener` already use.

## Consequences

- **23 parameters across the corpus now get a value of the kind their document asked for**, and none
  of them is in the five APIs the tool is measured on. The corpus test pins that zero, so the day
  one of those five describes a parameter as a date, the build says so.
- **M2.5 inherits this at no cost.** The source is in the chain asked for values nested inside an
  invented one, as well as the chain asked for a parameter, so every formatted property inside a
  request body is covered the moment bodies are built. That is where the five priority APIs keep
  their dates, and it is where this increment's real return is.
- **The value a pushing request sends is untouched.** Such a request draws from the list of awkward
  values and falls back on invention, and neither asks this source for the value itself, so a
  timestamp that parses is not what a request built to be refused ends up sending. Pinned by a test
  that checks *every* draw rather than merely one of them.

  The precise version is worth writing down, because a looser one would be wrong. Invention asks
  better-informed sources about anything *nested* inside what it builds, and that nested chain
  carries this source — so a date inside an object built during a pushing request is a well-formed
  date. That is not new and not a leak: the same nested chain has always carried the document's own
  samples and defaults, which are equally well-formed. A pushing request pushes with the value it
  chose, not by corrupting everything around it.
- **Nothing reaches a host anybody could own.** Web addresses, e-mail addresses and host names are
  all built under `example.com`, which RFC 2606 reserves and nobody can register. A generated value
  that sent the API under test off to a real third party would be a poor way to find that out.
- **A user's `format`-keyed file now has a competitor**, which is exactly the weighted-group
  argument ADR-0020 §5 makes and hands to M2.10. Until then the chain is exclusive, so such a file
  replaces this source entirely for the kind of text it names — which is what ADR-0020 §5 already
  says, and what a file asking for that is asking for.
- **A pattern is still unhandled**, and `RandomValueProvider`'s class comment now says so precisely
  rather than saying the same of formats. M2.4b closes it. A shape declaring both a kind of text and
  a pattern therefore gets a value honouring the first and ignoring the second, which is the same
  answer it got before this increment for the pattern half.
- **A value from here reports its source as `format`**, which is also one of the five keyings a
  dictionary file may declare. A user who names their own dictionary `format` makes the two
  indistinguishable in a report. Left alone: every source name has this weakness, renaming this one
  would make the report less clear for the common case, and M2.10's plan file is where names become
  something a user chooses on purpose.

## Alternatives considered

- **A dictionary file shipped with the tool**, as ADR-0013 §1 and ADR-0020 §5 both read. Rejected on
  the two grounds in §1: dates go stale and identifiers repeat. Keeping the file mechanism for users
  while computing our own values takes the good half of it and leaves the bad half.
- **Format-aware invention inside `RandomValueProvider`** rather than a source of its own. Rejected
  because it would put the knowledge in the last-resort source, below the lists a user writes,
  inverting the order ADR-0020 §4 sets out. A separate source sits where that order says it belongs.
- **Answering for numbers declared `int32` and the rest.** Rejected as gaining nothing: invention
  already produces numbers well inside every one of those widths.
- **Reading a parameter's name** to tell a birth date from a departure. Rejected here: that is
  inference from names, which the deferred backlog owns, and M4.1 is where a mechanism for it would
  first be built and measured.
- **Delivering this together with M2.5**, so that the numbers behind it are the priority corpus's
  rather than the wider corpus's. Considered seriously once the measurement above was in hand, and
  decided against: M2.5 is a large increment on its own, and bodies built while every date in them
  is an ordinary word would be a worse starting point than bodies built on top of this.
