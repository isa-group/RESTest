# ADR-0010: IDL — relicensed assets, an ANTLR4 parser, and the solver behind an interface

**Status:** Accepted
**Date:** 2026-09-11

## Context

Inter-parameter dependencies are RESTest's differentiator. IDL and IDLReasoner are the group's own
work, grounded in a study of more than 2,500 operations across 40 industrial APIs, and no competing
tool has an equivalent.

The existing implementation carries costs that a rewrite should not inherit. `IDLReasoner-choco`
pulls Choco 4.10.6 **and Eclipse Xtext 2.22.0**, which brings the Eclipse modelling framework, Guice
and Guava, pins old versions of Jackson and swagger-parser, and makes compilation to a native binary
impractical. `IDLReasoner-choco` ships no licence file at all, while the older MiniZinc IDLReasoner is
GPL-3.0 and RESTest 1.x is LGPL-3.0.

In 1.x, IDL is wired in shallowly and fails quietly: a broken specification only logs a warning, and
constraint-based testing silently degrades to unconstrained random generation. The constraint model
is also parsed once and frozen, which forecloses any future work that would discover dependencies
during a run.

The licence obstacle has since been removed: the author of the IDL parser can relicense it.

## Decision

1. **Relicense `isa-group/IDL` and `IDLReasoner-choco` to Apache-2.0**, adding the missing licence
   file. Administrative lead time; it blocks milestone M5, so it starts immediately.
2. **Reuse the IDL-to-CSP mapping and the analysis operations** from IDLReasoner. That is the real
   intellectual asset and it is well tested.
3. **Keep the `.xtext` file as the normative definition of the language, but port the runtime parser
   to ANTLR4**, validated by a **differential conformance test**: run the entire existing IDL corpus
   through both the old and the new parser and require identical results. The reason is weight, not
   licensing.
4. **The solver sits behind a `ConstraintSolver` interface**, with Choco as the default
   implementation. Choco's exact licence terms are confirmed before M5.
5. **The constraint set is mutable and per-operation, and every constraint carries provenance and
   confidence** (ADR-0008). Re-solving mid-run is a normal operation, not a reload hack.
6. **Failures are loud.** An inconsistent IDL specification is reported prominently; it never
   degrades silently to random generation.

## Consequences

- No Eclipse modelling framework, no Guice, no Guava in the runtime; the native binary stays viable.
- The differential test makes the port demonstrably behaviour-preserving, which is what allows us to
  reuse the published IDL corpus and to claim continuity with the 1.x results.
- Deferred work on dependency inference needs no surgery: it becomes another `ConstraintSource`.
- Two definitions of the grammar to keep aligned — the `.xtext` file and the ANTLR4 file. The
  differential test is what stops them diverging, and it runs in CI.
- Writing the port costs two or three increments.

## Alternatives considered

- **Reuse the Xtext parser unchanged.** Zero work, and inherits the whole Eclipse stack, the pinned
  versions and the native-image problem.
- **Xtext as a build-time generator with the runtime shaded out.** Keeps one grammar file, but is
  harder to verify and to maintain than the port.
- **Drop IDL and use a simpler internal constraint model.** Rejected: it is the differentiator, and
  the 2026 result was bad partly *because* IDL could not be deployed, not because it does not work.
