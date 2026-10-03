# ADR-0001: Record architecture decisions

**Status:** Accepted
**Date:** 2026-09-11

## Context

RESTest 1.x accumulated decisions nobody wrote down. Two authors produced 59% of the commits and
the principal author left in 2022; what remained was code whose reasoning had to be reconstructed
by reading it. The audit for this rewrite found several choices — the in-process compiler, the
static `PropertyManager`, the file-mediated stateful mechanism — that look arbitrary today but were
almost certainly sensible answers to constraints that are no longer visible.

The same risk applies to v2.0, and more so: much of the code will be written quickly, and the
person reviewing it will not always be the person who will maintain it.

## Decision

Every decision with more than one defensible answer and a non-trivial cost to reverse is recorded
as a numbered file in `docs/adr/`, using the format in `docs/adr/README.md`. Records are never
deleted; a reversed decision is marked *Superseded by ADR-NNNN* and the new record explains what
changed.

Writing an ADR is part of the increment that makes the decision, not a separate task.

## Consequences

- A newcomer can read eleven short files and understand the shape of the system.
- Disagreements happen against a written position rather than against inferred intent.
- Small overhead per decision: perhaps fifteen minutes.
- A temptation to over-record. Implementation choices belong in the pull request, not here.

## Alternatives considered

- **A design chapter in the documentation.** Rots, because nobody updates a chapter when they change
  one thing. Numbered, immutable records do not have that failure mode.
- **Nothing.** This is what 1.x did.
