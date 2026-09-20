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

Two reasons:

- **Variety.** The chain is exclusive until M2.10, so a shipped list would *replace* invention for
  every value of its kind. The corpus names `uri` 569 times; a run would send half a dozen fixed
  addresses for all of them, for its whole length. An invented one is different every time.
- **A fresh identifier cannot come out of a file**, and a `uuid` is usually an identifier. Nor can a
  date drawn from a window somebody can widen, which is what makes a run explore rather than repeat.

A third reason was written here first and does not survive its own implementation, so it is recorded
as withdrawn rather than quietly dropped. It read: *a dictionary sees only its key, where invention
sees the whole shape, so every conflict in §1 resolves itself instead of needing a guard.*
`FormattedStrings` **also** sees only the key — a name and a source of numbers, nothing else — and
§1's fallthrough is exactly the guard the argument claimed to avoid: a shape saying
`format: email, maxLength: 12` does not get a shortened address, it gets an ordinary word, which is
what the dictionary was accused of. The two surviving reasons are enough on their own, and the
withdrawn one is left here because an argument that sounds good and is not is worth being able to
find again.

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

`MatchingStrings` adds the things the library cannot know about. Every one of them is there
because a review of this increment demonstrated the failure it prevents, on real input, three passes
running.

- **How far a repetition runs.** Its own answer is a hundred, and repetitions *multiply*: four nested
  "one or more" groups is a hundred to the fourth power, and a rule of that shape produced 868,356
  characters. Eight is the default here, with the shape's own lower length winning over it — a
  specification insisting on two hundred characters is insisting — and its upper length capping both.
- **The lengths worth having, as a preference rather than a rule.** Not longer than the sixty-four
  characters every other invented string keeps to, and not the empty string where anything else would
  do. A candidate outside that is set aside while better ones are looked for, and taken in the end if
  none turns up. Neither end can be a hard limit, and both were, once: a rule asking for a hundred and
  twenty-eight hexadecimal digits is the *document* asking, and treating sixty-four as a limit made the
  tool quietly send a word the document refuses; while `pattern: "$"` refuses nothing at all, builds
  only the empty string, and — with "at least one character" read as a limit — left the parameter with
  no value. The hard limits are the shape's own, and ten thousand characters where it states no
  maximum.
- **How long the rule could possibly run**, worked out from the rule before a character is built.
  This is the defence that holds, and it took four attempts, each of the first three demonstrated
  inadequate by review on real input. A cap on repetitions does not bound the value, because a rule
  may state its own counts: `[a-z]{1000000}` is a megabyte. Reading counts off the rule one at a
  time does not bound it either, because counts *multiply* — nine "repeat this nine times" written
  inside one another is 387 million characters in a rule thirty characters long, and `{900000000,}`
  states no upper count to read at all. And reading the rule *as the notation defines it* does not
  bound it, because the builder does not: a question about what comes next matches no characters by
  the notation's rules and the builder builds every one of them, so `(?=[a-z]{900000000})x` — 21
  characters — was counted as one.

  So `MatchLength` counts what the **builder** will make at its very worst: pieces in a row add up, a
  choice takes the widest span, a repetition multiplies, counts written one after another multiply
  too, arithmetic saturates rather than wrapping, a group that asks about what surrounds it costs
  what is inside it, and anything it cannot read through — or whose length depends on what was
  matched elsewhere, which is what a back-reference is — it refuses to answer for. A rule whose worst
  is longer than the value may be is declined unbuilt. Deliberately generous: a rule it refuses may
  merely have looked dangerous, which costs a spelling rule rather than the run.

  It also counts the **shortest** the rule could build, which answers a different question: whether a
  value short enough to be worth reading exists at all. Where one does not — a rule asking for a
  hundred and twenty-eight characters — the first acceptable value is taken and the search stops,
  instead of spending half a millisecond of the run's own thread hunting for a shorter one that
  cannot exist.
- **Which reader to trust first.** This platform compiles the rule *before* the library parses it.
  It refuses a rule nested twenty thousand deep in microseconds, where the library goes looking for
  memory it cannot have. A cheap first filter and nothing more: a short rule demanding a hundred
  million characters compiles instantly, which is why the count above exists.
- **Whether the two readings agree at all.** The library does not understand lookahead, so
  `^(?=.*[A-Z])[A-Za-z0-9]{8}$` — an ordinary password rule — parses without complaint and produces
  ten characters where the rule demands eight. Every candidate would then be refused one by one and
  the parameter reported as one no value could be found for, which §4 makes an expensive answer. So
  trial values are drawn when the rule is first read, and if none of the eight satisfies this
  platform's reading, the rule is treated as one nobody can read. The verdict is kept for the whole
  run, which is the cost: a rule the two agree about only rarely is settled by eight draws taken
  once. Eight rather than one because the answer is kept; eight rather than eighty because the only
  rules that ever get there are ones the two readers really do disagree about.
- **The length, by drawing again.** There is no "of this length" in the library, so candidates are
  drawn until one fits. GitHub's forty hexadecimal digits come out at forty about once in thirty-nine
  draws, so the allowance is twenty per character demanded, between two hundred and four thousand,
  and stops on a budget of a million characters built. It is still a lottery, and the odds are worth
  writing down: at an exactly demanded length *n* one draw in about *n* hits, which makes 40
  characters a certainty to nine figures and 200 the same, while 2,000 — where the character budget
  bites before the attempts do — is about seven in ten. Beyond that the tool reports that no value
  could be found, which is honest about the tool and not quite honest about the shape. Nothing in
  the corpus demands more than 40.

The three things that are **not** here are worth naming too, because each was considered and left.
Nothing tells the run when a rule was refused for being dangerous rather than for being unreadable,
so the API sees an ordinary word and nobody learns why; that belongs with the rest of what a run says
about a document it could not fully use. Whether the two readers agree is decided once and kept, so a
rule they agree about only rarely is settled by eight draws taken at one arbitrary moment. And a rule
naming more than a hundred groups is refused whether they are nested or side by side, because the
count that keeps the reading off the end of its own stack does not distinguish the two.

The kinds of value are **not** a library. `UUID`, `DateTimeFormatter`, `Base64` and `URI` are in
`java.base`, and a regular expression for `date-time` would cheerfully produce `8336-00-95T06:87:98Z`
— a string of the right shape that is not a date.

### 4. Not being able to honour a rule means two different things

They end differently, and the difference is deliberate:

- **A rule nobody could read** is treated as though it had not been written: an ordinary word, as
  before. Refusing to test a parameter over a notation nobody here understands helps nobody, and the
  tool never promised every dialect. This covers three cases that look different and are not — a
  rule neither reader accepts, a rule only one of them accepts, and a rule they both accept and
  understand differently. The last is the one worth naming, because it is silent: only the trial
  values described in §3 tell it apart from a rule that is simply hard to satisfy, and without them a
  password rule would take its whole operation out of the run. None of the corpus's 26 rules is in
  any of the three cases today, and a test says so.
- **A rule that was read, understood the same way by both, and nothing satisfying it also fits the
  length the shape demands**, means there is genuinely no value to send. Nothing is offered, so the
  parameter is left out or the operation is reported as untestable. That is what
  `RandomValueProvider` already does when it runs out of attempts, for the reason its own comment
  gives: better than being counted among the operations being tested while every one of its requests
  is thrown away. It triggers nowhere in the fifty documents of the corpus, and a test pins that too
  — a described place that yields nothing is a failure there, not a tolerated outcome.

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
- Hundreds of draws for one value is a lot of draws. It happens only where a spelling and a tight
  length disagree, which is thirteen places in fifty documents, and the work is microseconds. If a
  campaign ever shows it on a profile, the answer is a repetition count aimed at the wanted length
  rather than drawn and filtered.
- **A rule can now cost its parameter for a reason that is not the document's fault.** A rule the
  count above refuses — nested repetitions that multiply past what may be sent, a count too large to
  build — is answered with an ordinary word, which the API will very likely refuse. That is the
  right trade against ending the run, and it is the one place where the tool knowingly sends
  something the document does not accept. Nothing in the fifty-document corpus is in this case, and
  a test says so; if one ever is, the run has no way of mentioning it, which is a gap a later
  increment should close alongside the rest of what a run says about a document it could not fully
  use.
- **A rule is read once per run, not once per value.** Reading one is parsing a small language, and
  the API in the corpus with the most of them states a rule for fifteen of the values in every
  request it takes. Measured on that one: eight microseconds to build a whole request before this
  increment, eleven with a rule read afresh each time, eight again once each rule is read once and
  kept. The store of read rules belongs to the generator rather than to the class, because
  everything in a generator belongs to one run and two runs in the same program have to be two runs;
  it holds one entry per rule the document states, so it cannot grow.
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
