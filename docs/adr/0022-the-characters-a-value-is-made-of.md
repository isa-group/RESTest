# ADR-0022: The characters a value is made of are read where its length already is, and the regular expressions are somebody else's

**Status:** Accepted
**Date:** 2026-09-20

## Context

A specification says three kinds of thing about a piece of text. How long it may be, which
`RandomValueProvider` has read since M1.5. Which *kind* of value it is — `format: date-time`,
`format: email`, `format: uuid`. And how it must be spelled — `pattern`, a regular expression the
value has to satisfy. Until this increment the second and the third were read by the parser, stored
faithfully in `StringSchema`, and used by nothing that builds a value. A `date-time` parameter got
`aK3xQ9pR`, and an API that looks at what it is given answered 400 before anything worth testing
happened.

M2.4 closes that. Three questions come with it, and none has one obvious answer.

### What there is to gain, measured

Every `openapi.*` file in the corpus, parsed, walking every place a request can carry a value — a
parameter, or a step inside a request body. Places, not schemas: a shape a document declares once
and twenty operations refer to is twenty places.

| | |
|---|---|
| Documents parsed | 50 |
| Places naming a kind of value RESTest now builds | 225, in 14 documents |
| …of them in the five APIs the tool is measured on | 25 — flight-search 6, pet-clinic 7, gestao-hospital 6, kafka-rest-proxy 4, notebook-manager 2 |
| Places naming a kind RESTest does **not** build | 4 distinct names: `Integer`, `string`, `password`, `binary` |
| Places stating a spelling rule | 23 — pet-clinic 10, GitHub 7, AmadeusHotel 6 |
| Distinct spelling rules across the corpus | 26 |

The thousand-odd `format` declarations a grep finds in those documents are mostly on values an API
*returns*. Those are the schema oracle's business, not generation's, and nothing here sends one.

Two of the four kinds we do not build are somebody writing the *type* of a value where its kind was
asked for. `password` is a note to whoever draws the form. `binary` is not text at all. An ordinary
word answers all four, which is what makes the list of kinds worth building short and closed.

### The spelling rules are a compiler's problem

The 26 rules use character classes, ranges, negation, `\d`, `\p{L}`, non-capturing groups,
alternation, and every quantifier there is. One of them is
`^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{4})$`. Working backwards
from such a rule to a string that satisfies it is parsing a language and walking the result.

And the rules do not stand alone. Thirteen places state a spelling *and* a length, and one of them —
GitHub's `^[0-9a-fA-F]+$` with `minLength` and `maxLength` both 40 — is satisfied by nothing the
spelling alone suggests. The spelling says one or more; only the length says forty.

## Decision

### 1. Both are read where the length already is, not as new sources

`RandomValueProvider.text` reads `format` and `pattern` alongside `minLength` and `maxLength`. It is
not a new source in the chain of ADR-0013 §1, and nothing about the chain moves.

The reason is that reading a `format` *is* reading the shape. A source in the chain answers from
knowledge the document does not carry — a sample somebody wrote, a list somebody curated, a value the
API handed back. There is no such knowledge here: the document said the value is a date, and a date
is what gets built. Putting that behind a new name in the chain would mean arguing about where in the
order it sits, and the honest answer is "last, where invention already is".

Practically it is also the only place that can be right. A shape states its kind, its spelling and
its lengths at once, and they have to hold together. Inside one method all three are in hand.

The four branches, in order:

```
a kind and a spelling both stated -> the kind's value, kept only if the spelling accepts it
a kind we build                   -> a value of that kind
a spelling                        -> a string built to match it
neither                           -> an ordinary word, which is what every string used to be
```

Each falls through to the next when what it produces is not something the rest of the shape allows.
The kind comes first because it describes the whole value and the spelling only its characters; the
one place in the corpus that states both is an `email` beside a rule of `@`, and an e-mail address
satisfies that rule.

### 2. The kinds are built, not looked up in a shipped list

ADR-0013 §1 lists a *"format dictionary — the schema declares a `format` we have values for"* among
the eight sources, and ADR-0020 §5 names 2.4's shipped format dictionary as the competitor that makes
M2.10's weighted groups worth having. **No such file is shipped, and this reverses that much of
both.**

Three reasons, in order of weight:

- **A dictionary sees only its key.** A list filed under `date-time` knows nothing about the
  `maxLength` on the shape it is answering for, nor about a spelling rule beside it. Every conflict
  in §1 above would have needed a guard around the dictionary instead of resolving itself.
- **Variety.** The chain is exclusive until M2.10, so a shipped list would *replace* invention for
  every value of its kind. The corpus names `uri` 569 times; a run would send half a dozen fixed
  addresses for all of them, for its whole length.
- **A fresh identifier cannot come out of a file**, and a `uuid` is usually an identifier.

What does not change: the `format` **keying** stays exactly as ADR-0020 defined it, for a *user's*
file. Somebody holding real IBANs for `format: iban` still writes them that way, and that file still
beats invention. The difference is only that RESTest ships none of its own.

What it costs: ADR-0020 §5's argument for weighted groups loses the competitor it was counting on,
and falls back to the case it already documents — a `type`-keyed list somebody writes replacing
invention wholesale for a whole kind of value. M2.10 is unchanged in scope; its motivating example is
now the smaller one.

### 3. The regular expressions are read by a library, and every value it builds is checked

`com.github.curious-odd-man:rgxgen`, Apache-2.0, no dependencies of its own, confined to
`restest-gen` behind `MatchingStrings` with an architecture rule that says so. It parses all 26 of
the corpus's rules, `\p{L}` and non-capturing groups included, and — the thing that settled it —
`generate` takes a `java.util.random.RandomGenerator`, which is the run's own seeded source. Nothing
about repeating a run exactly had to change.

**Everything it builds is held against the rule again**, by `java.util.regex`, before it is offered.
Correctness is therefore a checked property of each value rather than a claim about the library, and
a disagreement between two readings of a dialect costs a value instead of producing a wrong one. A
rule this platform will not compile is declined for the same reason: nothing could check it.

`MatchingStrings` adds the two things the library cannot know about:

- **How far a repetition runs.** Its own answer is a hundred, which turns "any number of digits" into
  a hundred-digit number. The shape's upper length is used where it states one, and its lower length
  wins over both.
- **The length, by drawing again.** There is no "of this length" in the library, so candidates are
  drawn until one fits — up to two hundred. GitHub's forty hexadecimal digits came out at forty about
  once in fifty draws; a draw costs microseconds, and almost every shape is satisfied by the first
  candidate and never reaches the second.

The kinds of value are **not** a library. `UUID`, `DateTimeFormatter`, `Base64` and `URI` are in
`java.base`, and a regular expression for `date-time` would cheerfully produce `8336-00-95T06:87:98Z`
— a string of the right shape that is not a date.

### 4. Not being able to honour a rule means two different things

They end differently, and the difference is deliberate:

- **A rule nobody could read** is treated as though it had not been written: an ordinary word, as
  before. Refusing to test a parameter over a notation nobody here understands helps nobody, and the
  tool never promised every dialect. None of the corpus's 26 is in this case today, and a test says
  so.
- **A rule that was read, and nothing satisfying it also fits the length the shape demands**, means
  there is genuinely no value to send. Nothing is offered, so the parameter is left out or the
  operation is reported as untestable. That is what `RandomValueProvider` already does when it runs
  out of attempts, for the reason its own comment gives: better than being counted among the
  operations being tested while every one of its requests is thrown away. It triggers nowhere in the
  five measured APIs.

### 5. A value built this way is still recorded as invented

`GeneratedValue.generatedBy("random")`, unchanged. Reading a `format` is reading the shape exactly as
reading `maxLength` is, and RESTest did invent the value either way.

The alternative — a sub-kind, the way ADR-0019 §4 gave the three kinds of declared value one — is
recorded here rather than taken. It was right there because *default*, *enumerated* and *sample* are
three different statements by the document's author that a consumer could not otherwise tell apart.
Here there is one statement and one inventor. Nothing reads a finer answer until M6's feedback, and
whoever needs it then can add it without changing what is stored today.

## Consequences

- A value whose kind or spelling a document states is now one the document accepts. Across the fifty
  documents, every value invented for such a place satisfies the whole shape it came from, and a test
  asserts exactly that.
- **The shared satisfaction check now covers spelling rules**, which tightens every generation test in
  the module at once, the golden-corpus run against the five measured APIs included. It also removed
  the one place that had its own stricter copy of that check: `DeclaredSamplesAcrossTheCorpusTest`
  held samples to a pattern because invented values were excused from one, and nothing is excused
  now.
- **A new dependency, and the one here without a module descriptor.** rgxgen's jar carries neither
  `module-info.class` nor an `Automatic-Module-Name`, so `restest-gen` says `requires rgxgen` and
  names an automatic module derived from the jar's file name. It compiles, it runs, and the name
  would change if the artefact were ever renamed. Accepted because the packaging step that automatic
  modules actually block is `jlink`, and M7.3 is a GraalVM native image; and because the alternative
  with a descriptor does not exist — `com.github.mifmif:generex`, the other candidate, is
  unmaintained since 2017, pulls in `dk.brics.automaton`, and has no descriptor either.
- **Unicode letters are letters.** `\p{L}` is what pet-clinic's name parameters state, and a value
  built from it is as likely to be Cyrillic or CJK as Latin. That is what the document asked for, and
  an API that means "Latin letters" should have said so; it does mean those parameters now exercise
  the encoding path as well as the validation one.
- Two hundred draws for one value is a lot of draws. It happens only where a spelling and a tight
  length disagree, which is thirteen places in fifty documents, and the work is microseconds. If a
  campaign ever shows it on a profile, the answer is a repetition count aimed at the wanted length
  rather than drawn and filtered.
- One more list to keep: the kinds of value RESTest builds. It is closed, it is pinned by a test
  against the corpus, and adding to it is adding one line to a `switch`.

## Alternatives considered

- **A shipped dictionary keyed by `format`,** as ADR-0013 §1 and ADR-0020 §5 both expected. Rejected
  for the three reasons in §2. It was the cheaper change and would have given M2.10 a better example,
  and neither is worth sending six fixed web addresses for every URI in an API.
- **Writing the regular-expression reader here.** About 350 lines for the subset the corpus needs,
  no new dependency, no automatic module, and a dialect we would own. Rejected: it is a compiler, the
  cost of getting it subtly wrong is silent wrong values rather than a crash, and the project's own
  measurement of where bugs live — ten of eighteen review findings at M1.5 were in the arithmetic of
  building a value from a schema — is an argument against writing more of that arithmetic, not less.
- **A new source in the chain, named `format` or `pattern`.** Rejected in §1: it would need a place
  in the order, and the only defensible place is where invention already is.
- **Honouring a stated `format` on a number** — `int32`, `int64`, `double`. Rejected: those are
  widths, and the bounds a shape states already express them. A value of `2147483647` is a value to
  push at an API with, and it is in the list that does that.
- **Inferring a kind from a parameter's name** — dates for `bornOn`, addresses for `homepage`.
  Rejected again, as ADR-0013 rejected semantic dictionaries: the hard part is deciding what a
  parameter means, and reading what the document says is not that.
