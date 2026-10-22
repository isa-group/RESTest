# ADR-0037: Every fault is an error or a warning, and a run fails on its errors

**Status:** Accepted
**Date:** 2026-10-22

## Context

Until now every fault RESTest reported was the same. Each was printed under the catalogue's name
for its kind — `F200  Schema Violation: Received A Response From API With A Structure/Data That Is
Not Matching Its Schema` — and any one of them ended a run with `1`
([ADR-0015](0015-command-line-contract.md)).

Two kinds of fault have since made that a poor reading of a run. A reply whose status code, or
content type, the document does not declare ([ADR-0036](0036-kinds-of-fault-restest-numbers-itself.md))
is very often the document's to fix: documents list what an operation answers when all goes well and
leave out the ways it refuses a request. On fifteen APIs of the Web Fuzzing Dataset run locally, a
minute each, the runs that brought in those two kinds counted 1,443,634 faults of the first on
thirteen of them, and one API's run went from `0` to `1` for that alone. A build went red for a line missing in a document as it did for an API falling
over, and a developer reading the run could not tell the two apart without reading the catalogue's
numbers.

**What the related tools do, read in their code on 22 October 2026.**

| Tool | Severity | What fails a run | How a user asks for more |
|---|---|---|---|
| CATS 15.0.3 | *error*, behaviour that has to be acted on, and *warn*, the contract and the service out of line. Warn: an undocumented status code, a body off its schema, a content type, a 501. Error: a success to a request fuzzed to be refused, a server error, a timeout | Errors only | `--failOn warn`, or `--qualityGate "errors<5,warns<20"`. A level can be lowered by ignoring a reply, never raised |
| Schemathesis 4.32.3 | critical, high, medium, low, fixed per kind of failure and used only to order what it prints. A body off its schema is high; an undocumented status code, an undocumented content type and invalid input accepted are medium | Any failure, of any severity | Its run *warnings* — validation it could not do, among others — fail a run when listed under `[warnings] fail-on` in its file |
| EvoMaster 6.2.0, RESTler, WuppieFuzz 1.8.0, RestTestGen | None | Nothing: each ends with `0` whatever it found | — |
| Prism 5.16.0 | `Error` and `Warning`: an undeclared status code is an error when it is a success and a warning otherwise | (a proxy, with no run to end) | `--errors` turns errors into replies of 500 |
| Spectral, vacuum, ESLint, Grype | error, warn and more | Errors only | `--fail-severity warn`, `--max-warnings 0`, `--fail-on` |

The catalogue itself has no severity. Its description of `F200` says that whether the API or the
document is at fault cannot be known without debugging, and that `F100` and `F312` need a person to
check them. And in the tools that let a run fail on what could not be checked, that is always a
switch of its own, never a step on the ladder of severities.

## Decision

### Two severities, by one rule

**An error is the API failing at its job: it falls over, never answers, or accepts what its own
document forbids. A warning is the API answering something its document does not describe, where the
document may be the one out of date.**

| Code | Title | Severity | What to look at |
|---|---|---|---|
| `F100` | Internal server error | error | look in the API's log for what failed while it handled the request; the document is not what to fix |
| `F121` | Other server error | error | look at what the API sits behind or calls - a gateway, a database, another service - as well as at its code; the document is not what to fix |
| `F206` | Invalid data accepted | error | make the API refuse what its document forbids; where the document forbids more than the API should, loosen the document instead |
| `F312` | No reply in time | error | look at what the operation waits on - a database, another service, a lock; an API known to be slow gets longer with engine.readTimeout |
| `F200` | Reply off schema | warning | the API and its document disagree about the reply's body: compare the places listed with the document, and fix whichever is wrong |
| `F205` | Not implemented | warning | implement the operation, or take it out of the document |
| `F920` | Undeclared status code | warning | declare the status code for the operation in the document, or stop the API answering it |
| `F921` | Undeclared content type | warning | declare the content type for that status code in the document, or have the API send the one declared |

`F206` is an error although an API's library is often what accepted the request
([the faults](../faults.md#f206-invalid-data-accepted) list the common leniencies): the document says
the API must refuse it, and an API that does not has failed at a job its document gives it. `F205` is
a warning, as CATS has it: the document promises an operation the API does not do, and either can be
the one to change.

Each kind's severity is fixed: one table, in one place, `FaultReading` in `restest-core`, which both
reports and the command's exit code read. It lives in the core module because `restest-report`
requires nothing else and the command needs it too; it reads kinds the rules in `restest-oracles`
have already decided were faults, and those rules do not change. It is keyed by the kind's number,
which [ADR-0016](0016-how-a-fault-is-presented.md) makes a fault's identity within the catalogue
version RESTest ships. A kind the table does not know — one somebody else's rule reports — is an
error under its own name, with nothing to advise: every fault failed a run before this decision, and
that is kept for a rule nobody here wrote. The copy of the catalogue, `WfcFault`, carries none of
RESTest's opinions, and nothing in a stored run changes.

### How a run ends

**A run that found an error ends with `1`. One that found only warnings ends with `0`**, and its
summary counts them and says that they did not fail it. `checks.failOnWarnings`, `false` unless set,
makes a warning end a run with `1` as an error does, for a team that owns its document and wants a
build to go red the moment the API and the document disagree.

It is a setting, not an option. [ADR-0025](0025-settings.md) says that `--set` gives the command line
the reach without the surface, and the test it gives — would it mean the same thing for another API
on the same machine — puts it among the settings rather than in a plan. ADR-0015 reserved an option,
`--fail-on`, "because per-run configuration belongs to the increment that owns configuration": that
increment made settings, and this is one.

This changes what `0` and `1` mean, the first such change in 2.x; ADR-0015's amendment of this date
says why it is made in a minor version, and what it costs.

**A reply no check could judge never fails a run**, not even with `checks.failOnWarnings`, decided by
the maintainer on 22 October 2026. It is no fault: a reply whose body the document gives no shape for
says nothing against the API. [ADR-0014](0014-response-conformance.md) had kept it out of the exit
code because the exit code was frozen; that reason no longer holds, and this one replaces it.

### What a person reads

On the screen a fault starts with its severity, then RESTest's title for its kind, then the number in
brackets:

```
error    Internal server error  (F100)
         getVisit - GET http://localhost:9966/petclinic/api/visits/99999999999999999999  ->  500
```

- **The title is the check's switch, in words.** *Reply off schema* is `checks.replyOffSchema`, so
  the name a person reads leads to the line that turns the check off; the switches page calls each
  check by the same words, and a test holds the two together.
- **What to look at is said once for each kind**: under the kind in the summary, and in
  `faultsByCategory` in `report.json`. Not under every fault, which a run can repeat a hundred
  thousand times, and not in each of the thousand faults the report can write out whole.
- **The summary counts errors and then warnings**, each on how many operations, so that what failed
  the run is read first.
- **The screen keeps room for errors.** It prints the first fifty faults, as before
  (`report.faultsShownOnTheConsole`), and at most ten of them may be warnings
  (`report.warningsShownOnTheConsole`). A document that leaves out its refusals made every one of the
  fifty an `F920`, and in a minute's run of one API none of its 10,902 replies of 500 reached the
  screen. A setting is added rather than the old one's meaning changed.

`report.json` says each fault's severity and title beside its number in every row that counts
faults, and counts errors and warnings apart in `totals`. The catalogue's names stay there, for
comparing a run with another tool's.

## Consequences

- **A run's exit code means what a developer would want it to mean**: red for an API that fell over,
  hung or let through what it should refuse; green, with the warnings said, for an API whose document
  is behind it.
- **Against 2.1.0, one kind of run ends differently:** one whose only faults are replies off their
  schema (`F200`), which ended with `1` and ends with `0`. With `--set checks.failOnWarnings=true`
  such a run ends with `1` again. `F205`, `F920` and `F921` came after 2.1.0.
- **On fifteen APIs of the Web Fuzzing Dataset run locally**, a minute each, seed 42, read back from
  the runs made for [ADR-0014](0014-response-conformance.md)'s amendment of 19 October with no request
  sent: one run's exit code changes, bibliothek's, whose only faults are `F920`; every other API
  answered 500 at least once. `F206` was found on 71 operations of twelve APIs, and was the only
  error on 31 of them.
- **Run again with this decision**, the same fifteen APIs, a minute each, seed 42: bibliothek ended
  with `0`, its 622,908 faults all warnings, and the other fourteen with `1`, as predicted; on every
  one, errors and warnings added up to the faults counted. On spring-rest-example, the API whose
  10,902 replies of 500 had not reached the screen once, the screen printed ten warnings and then
  forty errors, 36 of them replies of 500.
- **Raising or lowering a kind on one operation is still to come**, with oracles configured per
  operation. It will need a severity decided for each fault rather than for each kind; both reports
  look it up today, so both change then.
- **A third level, between error and warning, is not ruled out.** It waits for the oracles that need
  one: the HTTP semantics the catalogue numbers 101 to 122, or valid input refused (`F207`).

## Alternatives considered

- **Three levels** — error, warning and note, or `F206` as a warning of a higher priority — weighed
  with the maintainer on 22 October 2026, for the oracles to come. Kept to two until those oracles
  need a third: a level is cheap to add, but each one is a question about every kind.
- **`F206` a warning**, as a first wording of the rule had it: a warning wherever the API and its
  document disagree and either may be the one at fault. It changed no exit code on the fifteen APIs, since each that accepted a broken
  request also answered 500. Not taken, for the reason above.
- **A severity for each kind given as a setting**, as linters let a rule's level be set. CATS and
  Schemathesis fix theirs, and raising or lowering a kind belongs with the configuration per
  operation, which can say where.
- **Severity for ordering only**, as Schemathesis has it. A build would still go red for a line
  missing in a document.
- **An option, `--fail-on` or `--fail-severity`**, as CATS, Grype and Spectral spell it. A second
  surface for what a setting already says.
- **Letting a run fail on replies it could not check when asked**, with a switch of its own as ESLint,
  Semgrep and Schemathesis have one. Declined by the maintainer: such a reply says nothing against
  the API.
- **Fifty faults of each severity on the screen.** Up to a hundred faults printed, and a setting
  whose meaning changes.
- **Keeping the catalogue's names on the screen**, after RESTest's. Fourteen words for `F200`; the
  number is enough to look the name up.
