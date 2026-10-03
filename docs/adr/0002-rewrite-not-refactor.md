# ADR-0002: Rewrite rather than refactor; Apache-2.0; no 1.x source carried over

**Status:** Accepted
**Date:** 2026-09-11

## Context

Three properties of RESTest 1.x are structural rather than local, and each one blocks a stated
requirement for v2.0:

- Configuration cost. Two files per API in two formats, around 105 lines of test configuration per
  operation. The FSE'22 study required 26k lines of it, ~95% copy-pasted. The requirement "the OAS
  should be enough" is incompatible with this design, not with its implementation.
- Code generation in the execution path. Java source assembled by string concatenation, compiled
  in-process, with the compiler's success flag ignored. Every oracle is frozen at generation time.
- Static global state and 19 hardcoded relative paths. The jar is not relocatable and two
  configurations cannot coexist in one JVM, which blocks "usable as a library dependency".

Removing any one of these is a rewrite of the module that contains it. Removing all three is a
rewrite of the tool.

Separately: 1.x is LGPL-3.0, `IDLReasoner-choco` ships no licence file at all, and the older
MiniZinc IDLReasoner is GPL-3.0. That is a redistribution hazard today and a barrier to industrial
adoption tomorrow.

## Decision

Write v2.0 from scratch on a long-lived `v2` branch of the same repository. Release under
**Apache-2.0**, and relicense `isa-group/IDL` and `IDLReasoner-choco` to Apache-2.0 as well, adding
the missing licence file.

**No source file from 1.x is copied.** What carries over is ideas, the IDL grammar, the catalogue of
test data generators as a specification of behaviour, and the test corpus. Anything that looks like
a 1.x class is reimplemented against the new interfaces.

Keep the name RESTest; change the Maven coordinates to `es.us.isa.restest:restest-*:2.0.0` and state
in the README that this is a rewrite, not an upgrade path.

## Consequences

- The licence conversation stays narrow: no inherited copyright to negotiate around.
- No compatibility with 1.x configuration files. Deliberate — those files are the problem.
- We lose 1.x's accumulated small fixes. Mitigated by the golden specification corpus, which
  preserves the *situations* 1.x learned to handle even though it does not preserve the code.
- Relicensing needs agreement from the 1.x copyright holders. Administrative lead time; it blocks
  milestone M5, so it starts before any code is written.

## Alternatives considered

- **Incremental refactoring of 1.x.** Each of the three problems is load-bearing; the intermediate
  states would not be releasable, and the dependency stack (REST-Assured 4.2.0 from 2019, Jackson
  2.11.4, JUnit 4 at compile scope) would have to be modernised first, which is itself a rewrite.
- **A new repository.** Loses citations, stars, issue history and inbound links.
- **Staying on LGPL-3.0.** Keeps the adoption barrier and leaves the IDLReasoner licence gap open.
