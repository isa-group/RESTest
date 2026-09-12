# Architecture decision records

An ADR is a short note recording one decision: what we chose, why, and what it costs us.
One file, numbered, never deleted. If a decision is reversed later, the old record stays and is
marked *Superseded by ADR-XXXX*, so the reasoning is still readable.

The point is not ceremony. It is that in two years somebody — possibly one of us — will ask
"why on earth is it done this way?", and the answer should take one minute to find rather than an
afternoon of archaeology. It is also the main defence against the bus factor that left RESTest 1.x
hard to maintain.

## Format

    # ADR-NNNN: <decision, as a short statement>

    **Status:** Proposed | Accepted | Superseded by ADR-NNNN
    **Date:** YYYY-MM-DD

    ## Context
    What forced a decision. Facts, constraints, measurements.

    ## Decision
    What we are doing. Present tense, unambiguous.

    ## Consequences
    What this buys us, what it costs us, and what it rules out.

    ## Alternatives considered
    What else was on the table and why it lost.

## When to write one

Write an ADR when a choice has more than one defensible answer and would be expensive to reverse:
a dependency that will spread through the codebase, a boundary between modules, a file format, a
licence. Do not write one for ordinary implementation choices — those belong in the pull request.

## Index

| # | Decision | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-rewrite-not-refactor.md) | Rewrite rather than refactor; Apache-2.0; no 1.x source | Accepted |
| [0003](0003-java-baseline.md) | Every module targets Java 21; toolchain on 25 | Accepted, amended at M0.2 |
| [0004](0004-module-structure.md) | Multi-module structure and inward dependencies | Accepted, amended at M0.2 |
| [0005](0005-interpreted-test-model.md) | Test cases are data, not generated source code | Accepted |
| [0006](0006-event-stream-and-store.md) | One event stream; every interaction persisted | Accepted |
| [0007](0007-specification-parser-boundary.md) | The parser sits behind our own interface; OAS 2.0 and every 3.x | Accepted, amended at M0.2 |
| [0008](0008-extension-points-no-ai-abstractions.md) | Extension points, and no AI-specific abstractions | Accepted |
| [0009](0009-non-blocking-engine.md) | The engine never blocks; idle time replaces a throughput floor | Accepted |
| [0010](0010-idl-strategy.md) | IDL: relicensed assets, ANTLR4 parser, solver behind an interface | Accepted |
| [0011](0011-evaluation-harness.md) | Evaluation harness in `evaluation/`, outside the build | Accepted |
| [0012](0012-canonical-model.md) | The canonical model: immutable records, sealed types, recorded gaps | Accepted |
