# ADR-0036: Kinds of fault RESTest numbers itself

**Status:** Accepted
**Date:** 2026-10-17

## Context

Two disagreements between an API and its document are worth telling a developer, and the Web
Fuzzing Commons catalogue RESTest numbers its faults by has no number for either:

- **A status code the document does not declare.** pet-clinic's document says `POST /api/pettypes`
  answers `200`, `304`, `400`, `404` or `500`; the API answers `201 Created`.
- **A content type the document does not declare.** The same document says a `400` comes as
  `application/json`; the API sends `application/problem+json`, the standard form of an error that
  Spring Boot writes.

The catalogue leaves the numbers from 900 to 999 to each tool for kinds like these
([ADR-0016](0016-how-a-fault-is-presented.md)), and `FaultCategory` was made an interface so that a
kind of RESTest's own could take its place beside the catalogue's. Nothing used that room yet.

**What the tools that use the catalogue do, read in their code on 17 October 2026.**

- **EvoMaster** (master at `ab983ba3`) checks every reply with Atlassian's validator, which reports
  both disagreements — `validation.response.status.unknown`, `validation.response.contentType.notAllowed`
  — and EvoMaster files every message of that validator under `F200`, the catalogue's *reply off its
  schema*. An undeclared `204` is `F200` in its own tests. It keeps one fault per operation and
  message key, so all of an operation's undeclared codes are one fault.
- **Schemathesis** (4.31.1) has a check for each, `status_code_conformance` and
  `content_type_conformance`, on by default with the severity `medium`, and its export to the
  catalogue's report format writes both as `F200`. Its own numbers, 904 to 906, mean other things:
  invalid input accepted with a status other than 2xx, an answer to an undeclared method, and access
  to another user's objects.
- **EvoMaster's** experimental numbers, 950 to 990, are about other protocols and a timeout; none is
  about an undeclared status code or content type. **CATS** writes no catalogue codes, and uses 952
  to 957 and 999 as status codes of its own for failures of the connection.

So no tool numbers these two in the 900s, and the two that use the catalogue most count them under
`F200`.

## Decision

**RESTest numbers them itself: `F920`, a status code the document does not declare, and `F921`, a
content type the document does not declare.** They are `RestestFault`, an enum beside `WfcFault`
that implements `FaultCategory` the same way, so every report counts and writes them as it does the
catalogue's kinds.

- **Their names are RESTest's.** The catalogue has none to copy: *A Status Code The Document Does
  Not Declare* and *A Content Type The Document Does Not Declare*, written in the catalogue's style
  so that they read beside its names, and the labels for a test that reproduces them,
  `returnsUndeclaredStatusCode` and `returnsUndeclaredContentType`.
- **Their keys are in words**: `RESTEST_UNDECLARED_STATUS_CODE` and `RESTEST_UNDECLARED_CONTENT_TYPE`,
  rather than the default `FaultCategory` gives, `RESTEST_920`. A key in words says what it counts
  wherever it is read, as the catalogue's keys do and as Schemathesis's own do
  (`SCHEMATHESIS_INVALID_INPUT_ACCEPTED`). It looks like the name of a setting in the environment,
  `RESTEST_<GROUP>_<KEY>`, and is not one: the switch for `F920` is `RESTEST_CHECKS_UNDECLARED_STATUS_CODE`,
  and no group of settings is called `undeclared`.
- **The numbers avoid the ones other tools use**, 904 to 906 and 950 to 999, so that two reports read
  side by side, in the catalogue's viewer say, do not show one number meaning two things. A later
  kind of RESTest's own takes the next number from 922 on.
- **Not `F200`.** In RESTest `F200` is a reply whose *body* is not the shape the document gives, and
  is what `checks.replyOffSchema` turns off. Filing an undeclared status code under it would make one
  number mean three things a developer fixes in three different places, would take away the number a
  developer reads to tell them apart, and would leave one kind reported by three rules with three
  switches, against [ADR-0016](0016-how-a-fault-is-presented.md)'s one switch for one kind. The cost
  is comparability: to set RESTest's count of `F200` beside EvoMaster's or Schemathesis's, add
  `F920` and `F921` to it. [The faults](../faults.md#the-catalogue) say so.

**A rule about what a document declares is not a lever, so the convention against counting a 404
does not apply.**
RESTest's maintainer asked that no lever which reacts to what an operation answers count a reply that
depends on the values RESTest made up, such as a `404` to an identifier it guessed. That convention is
about what a run *sends*: taking budget from an operation for the tool's own mistake. A rule sends
nothing; it says whether the document declares what came back. A `404` the document leaves out is
one it leaves out, whatever made the API answer it, so `F920` reports it.

## Consequences

- `report.json` carries codes that are not the catalogue's, under a `faultCatalogue` that names the
  catalogue. Its documentation, the faults page and the glossary say that a code from 900 to 999 is
  RESTest's and that its key starts with `RESTEST_`; a test holds the two kinds to the range and keeps
  their numbers and keys apart from every one of the catalogue's.
- A count of `F920` or `F921` compares only with another count from RESTest.
- If a later version of the catalogue numbers either kind, RESTest moves to its number, as it moved
  its two kinds at Faults 0.8.0, and says so in the release that does.

## Alternatives considered

- **`F200`, as EvoMaster and Schemathesis.** Comparable with them at once, and wrong for a reader of
  RESTest's own reports, for the reasons above.
- **Asking the catalogue for two numbers first.** Its maintainers have added numbers other tools
  asked for, Schemathesis's among them, and it may be worth doing; it is a step outside this
  repository, and RESTest would wait for a release of the catalogue before reporting either kind.
- **The keys `RESTEST_920` and `RESTEST_921`.** What `FaultCategory` gives a kind that names none,
  and unreadable without the table beside it.
