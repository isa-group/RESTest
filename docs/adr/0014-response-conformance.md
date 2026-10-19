# ADR-0014: A reply is judged against the specification document itself, by an off-the-shelf validator

**Status:** Accepted, amended at M1.8 and on 10, 17 and 19 October 2026
**Date:** 2026-09-13

## Context

M1.6 adds the oracle that reports WFC fault 101: *"Received A Response From API With A Structure/Data
That Is Not Matching Its Schema"*. Deciding that needs two things — the shape a reply was promised to
have, and something that checks a reply against a shape.

The obvious place to get the shape is `ApiModel`, the canonical model ADR-0012 introduced. It is what
everything else downstream of the parser uses, and it is deliberately ours rather than the parser
library's. But `CanonicalSchema` is a *reading* of the document, not the document, and it is an
incomplete one by design: composition (`oneOf`, `anyOf`, `allOf`) and discriminators do not arrive
until M2.1, and anything the parser could not represent is recorded as `UnsupportedSchema`.

Judging an API's replies against our own partial reading has a specific failure mode, and it is the
worst one a testing tool can have: every gap in our reading becomes a complaint about an API that was
behaving perfectly. A tool that reports working software as broken gets switched off, and nothing it
says afterwards is believed.

`docs/DESIGN.md` already named `com.networknt:json-schema-validator` under "Stack", so the second
question — who does the checking — had an answer waiting. What was open was what to hand it.

## Decision

**A reply is judged against the specification document, not against RESTest's reading of it.**

- `ApiModel` carries the document it was read from, as one OpenAPI 3 JSON document
  (`ApiModel.document()`).
- `restest-spec` fills it by writing the parsed document back out with swagger-core's own writer,
  inside the one module allowed to see that library (ADR-0007). This happens for every input, not
  only for OAS 2.0, so that one shape is what everything downstream meets.
- `restest-oracles` registers that document with `com.networknt:json-schema-validator` under an
  in-memory name, and validates a reply's body against a JSON pointer into it —
  `#/paths/~1pets~1%7BpetId%7D/get/responses/200/content/application~1json/schema`.
- Which reply and which media type apply is still decided by the canonical model, whose matching
  rules (exact status, then `2XX`, then `default`; exact media type, then `type/*`, then `*/*`) live
  in one place and are not written a second time. The document is consulted only for how it spells
  the two keys it was asked about.
- Remote references are switched off outright. A specification is somebody else's file, and a file
  that can make the tool fetch an address of its choosing is not something to leave enabled.

**A reply written as a reference is followed.** Documents routinely declare a reply once under
`components/responses` and point at it from everywhere it is sent — one of the five APIs this tool is
measured on does it three hundred times over. Local references are followed, up to a short chain; one
naming another file is not, because following it would mean reading a file we deliberately did not
resolve.

**Nothing uncertain is reported.** The check ends quietly, with no finding, when the reply was never
answered, no reply is declared for that status, no shape is declared for the media type that came
back, the media type is not JSON, **there is no body at all**, the body was kept only in part, the
reply insists on a character set other than UTF-8, the shape the document names is one it never
defines or one in another file, or the document was not kept. A reply whose declared shape cannot be
used is not evidence about the API.

The empty-body case deserves its own sentence, because it looks like a check being given away. It is
not: an answer to a `HEAD` request carries the headers a `GET` would, the declared media type among
them, and no body — and so does a `304`. Complaining there would be complaining about an API for
obeying HTTP. Whether a reply was allowed to be empty at all is a question about status codes, and
belongs with the oracles that judge those (M3.1).

**`format` is not asserted, and this has to be switched off explicitly.** In JSON Schema, saying a
string is a `date-time` or an `email` is an annotation rather than a rule; tools disagree about which
to enforce, and enforcing them would report working APIs as broken over a matter of opinion. The
concrete case is not hypothetical: a Java service returning a `LocalDateTime` writes
`2026-09-13T10:30:00`, the document generated from that service calls it a `date-time`, and RFC 3339
wants a time zone. Neither side is at fault and there is nothing for anybody to fix.

The validator's OpenAPI 3.0 dialect turns format assertion **on** by default — its 3.1 dialect does
not — so `formatAssertionsEnabled(false)` is set, and a test fails if it is ever unset. All five of
the benchmark APIs are OAS 3.0 and all five declare formats for their dates, so this single setting
is the difference between a usable tool and one that reports two dozen faults that are not faults.

## Consequences

- Composition, discriminators, references between shapes, and everything else a specification can
  say, all count from M1.6 — whether or not RESTest's own model understands them yet. M2.1 improves
  input generation; it does not have to arrive before replies can be judged properly.
- Measured against the corpus: a declared shape is reached for **80 of the 81** readable operations
  across the five benchmark APIs (flight-search 23/23, gestao-hospital 10/10, kafka-rest-proxy 33/33,
  notebook-manager 1/2, pet-clinic 13/13). The one that is not declares that it replies with JSON and
  then says nothing about the shape of it. Those numbers are pinned by a test, because a rule that
  silently reaches nothing is indistinguishable from an API with no faults.
- A verdict is defensible in one sentence: *your document says this, your API sent that*. Nobody has
  to be persuaded that our reading of their document was right, because our reading was not used.
- The canonical model and the judging path can disagree, and that is the point rather than a flaw:
  the model decides what to send, the document decides what was acceptable to receive.
- What is validated against is the specification *as swagger-parser understood it*. For a 3.x input
  that is a near-identity round trip. For a 2.0 input it is the conversion, which is the only thing
  it could be, since 2.0 spells responses differently from everything downstream.
- A document that cannot be written back out is recorded as a `SpecificationIssue` and the run
  continues with one fewer thing it can check. Fewer checks is worse than all of them; it is far
  better than refusing to test the API.
- Memory: a run holds the specification as text for its whole length. A large specification is a few
  megabytes, against a store that already holds every request and reply.
- Six more jars, confined to `restest-oracles` by an architecture test: the validator, the JSON
  library it uses (Jackson 3, `tools.jackson`, a different coordinate from the Jackson 2 already in
  the build), a YAML reader, `com.ethlo.time:itu` and the SLF4J facade. The optional `joni` and
  GraalVM JavaScript regular-expression engines are **not** taken — they add tens of megabytes to
  match patterns exactly as ECMAScript would, and `java.util.regex` is close enough for a check whose
  purpose is to avoid false positives.
- The SLF4J facade needs a binding or it prints warnings to standard error on first use. Choosing one
  is the application's business, so `restest-cli` takes the do-nothing binding at runtime scope.
- `jackson-annotations` is pinned in the root POM. Two paths through the dependency tree ask for
  different versions of it, Maven's mediation picked the nearer, and the result was a class missing
  at runtime in the one module that assembles everything. Found by the end-to-end test, which is
  where a problem of that shape can be found at all.
- M7.3's native binary will need reflection configuration for the validator. Recorded here so that
  increment meets it as a known cost rather than a surprise.

## Alternatives considered

- **Write our own validator over `CanonicalSchema`.** Comparable in size to the translation it would
  have replaced (~280 lines against ~240), and the wrong half to own: the translation is a
  transformation a test can check by comparing the JSON it emits, while the assertions are semantics
  — `multipleOf` on decimals, code-point lengths, unanchored patterns, deep equality for
  `uniqueItems` — that a library has already got right. It would also have had to learn composition
  at M2.1, which is exactly where hand-written validators go wrong.
- **Render `CanonicalSchema` into a JSON Schema document and validate against that.** Keeps the
  change inside `restest-oracles` and needs nothing from `ApiModel`. Rejected because it judges the
  API against our reading of its document after all — every gap in the model still becomes a false
  complaint — and because the rendering is a second place for the specification's meaning to be lost.
- **Keep the user's original bytes rather than writing the parsed document back out.** Faithful for
  3.x, and no use for 2.0, which has no `content` maps and keeps its schemas somewhere else entirely.
  One shape downstream was worth more than a byte-exact copy of an input we have already converted.
- **Assert `format`.** More faults found, and false ones. Reconsidered when M2.4 makes RESTest able
  to *produce* values in declared formats, at which point the tool has an opinion worth having.

---

## Amendment (M1.8)

**Date:** 2026-09-15

**Whether a reply is JSON is decided by our own reader, not by the checker throwing. How often the
check ends quietly is still not reported anywhere, and that is a known gap rather than a decision.**

### Why

The decision above says nothing uncertain is reported, and lists the cases where the check ends
quietly. The code implemented one of them by accident: whether a body was JSON at all was decided by
the checker throwing, and *every* exception out of the checker was read as "the body is not JSON".
A shape the checker could not use therefore became a fault reported against an API that had answered
perfectly, which is the failure this decision exists to prevent.

The body is now read with RESTest's own reader first. If it is not one JSON value, that is a fact
about the reply and is reported. If it is, and the checker still cannot finish, that is a fact about
the declared shape and nothing is claimed.

### The gap this leaves, stated rather than hidden

A run reports how many replies it judged and how many faults it found. It does not report how many
replies it could not judge at all. An API whose declared shape the checker cannot use - a pattern its
regular-expression engine rejects, a shape that names something absent - has every one of its replies
skipped, and the run ends "no faults found" with nothing anywhere saying that the check never ran.

This is not new: the same silence already applied whenever the shape could not be loaded, which is
the commoner of the two paths. What is new is that this increment made the *other* kind of failure -
a rule that throws - visible, counted, and worth a different exit code, which makes the contrast
sharp enough to write down.

Closing it needs somewhere to put the number: a count carried out of the rules and into the run's
summary and its report, in the way a rule's own failures now are. That is an increment, not a line,
and it is **M3.6** in the roadmap - placed after per-operation oracle configuration, which already
has to answer the neighbouring question of which checks were deliberately switched off.

## Amendment (10 October 2026)

**The fault this record's oracle reports is `F200`.** Faults 0.8.0 of Web Fuzzing Commons renumbered
the catalogue: a reply whose structure does not match its schema became `F200`, and `101` now means a
non-standard status code. RESTest reports it as `F200`; the table in ADR-0016's M1.10 amendment shows
the renumbering.

## Amendment (17 October 2026): the status code and the content type have rules of their own

**The check of a reply's shape still ends quietly when no reply is declared for its status code, or
no shape for its media type; two other rules now say so.** A reply whose status code the document
does not declare for the operation, below 500, is `F920`, and a reply with a body whose content type
the document does not declare for its status code, below 500, is `F921`
([ADR-0036](0036-kinds-of-fault-restest-numbers-itself.md)). They decide what is declared with the
same matching rules this record keeps in one place, in the canonical model: exact status, then
`2XX`, then `default`; exact media type, then `type/*`, then `*/*`. The rule of shape is unchanged:
it reports only a body it can judge, and stays silent about the rest.

The empty-body case above said whether a reply was allowed to be empty "belongs with the oracles
that judge" status codes. `F921` leaves a reply with no body alone, whatever type it names, for the
reason given there: an answer to `HEAD` and a `304` carry no body by the rules of HTTP.

Neither new rule judges an operation RESTest could not read in full. A reply the document declares
through a reference that does not resolve is dropped from the canonical model with a warning, and
would otherwise look undeclared. A reply the parser drops on its own — written as a word rather than
an object — leaves only a warning about the document, naming no operation, and does look
undeclared; [the known limitations](../known-limitations.md#what-a-run-says) say so.

## Amendment (19 October 2026): what could not be checked is counted and said

**The gap the M1.8 amendment left is closed: a run counts, check by check, the replies it could not
judge and why, and says so in its summary and in `report.json`. The exit code does not change.**

### What counts

Every reply is judged by at least one check — whether it is a 500 is never in doubt — so "replies no
check could judge" would always be none and say nothing. What a reader needs is, for each check,
how many of the replies it applies to it could not judge, and why. Three checks can be unable to;
the others decide from the status code or from what was sent, and always can.

- **The check of a reply's shape (`F200`)** counts a reply it would have compared with a shape and
  could not, for one reason each, the first that applies, in this order: the document describes the
  reply only in words, or names its media type and gives no shape (`noShapeDeclared`); the
  document could not be kept, or the shape cannot be reached in it — another file, nothing, a
  circle (`shapeUnusable`); the reply is not JSON though the document gives a shape for it
  (`notJson`); the body was cut short (`cutShort`); the reply names a character set other than UTF-8
  (`notUtf8`); the checker cannot be built for the shape, or cannot finish with it
  (`shapeUnusable`); a hidden key made the body something that is not JSON (`keyHidden`). A reply
  that got no answer, has no body, or answers with a status code or content type the document does
  not declare is not counted: there is nothing to judge, or another check reports it.
- **The check of a status code (`F920`)** counts a reply to an operation that declares no reply at
  all (`noReplyDeclared`), and, on an operation read only in part, a status code not among those
  it read (`readInPart`). A code it did read is held to as on any other operation: only what looks
  undeclared may be declared in the part it could not read.
- **The check of a content type (`F921`)** counts a status code declared with no content type
  (`noContentTypeDeclared`) and, on an operation read only in part, a type not among those it read
  (`readInPart`).

Nothing a check reports changes: only what it says about a reply it reports nothing about.

**The shape of a reply whose status code or content type the document does not declare is not
counted.** It was weighed and set aside by RESTest's maintainer: `F920` and `F921` report such a
reply, and counting it again would say the same thing twice. The cost is stated rather than hidden:
with either of those checks switched off, nothing says those replies went unchecked, and the known
limitations say so.

### Where it is said

- **The summary**, after the verdict and before the operations that could not be tested, one line
  for each check and reason, most replies first: `the shape of 287676 replies, on 22 operations: the
  document gives no shape`. Nothing is printed when every reply was checked.
- **`report.json`**, under `notChecked`: one row for each check and reason, with its code, the
  reason in one word, the phrase the screen prints, the count, and every operation the replies came
  from. An empty list when every reply was checked. The words for the reasons are a closed list in
  RESTest's code, and a test holds the documentation to it.

How it gets there: a check that can be unable to judge gives a verdict — what it found wrong, or why
it could not judge — inside the module of checks, and the listener that runs the checks announces one
event for a reply that some checks could not judge, naming each. The interface every check
implements is unchanged, so a check written elsewhere stays one method to write. A check that throws
is still RESTest's own failure, counted as before and ending the run with `4`, never a reply it could
not judge.

### Why the exit code does not change

A run's `0` still means that no fault was found in what the run could check. Making it mean "and
everything was checked" would change what `0` means, which this project's command line has promised
not to do within a major version ([ADR-0015](0015-command-line-contract.md)); and it would end
almost every run with something else, since most documents leave some reply without a shape: of
fifteen APIs run for a minute each on 19 October 2026, eleven had replies some check could not
judge, and on two of them, restcountries and rest-scs, not one reply's shape could be checked.
Whether a run may be told to fail when something could not be checked is not decided here.
Schemathesis, the tool closest to this, prints what it could not validate as a warning that fails a
run only when asked.
