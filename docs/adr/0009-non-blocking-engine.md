# ADR-0009: The engine never blocks on computation; idle time replaces a throughput floor

**Status:** Accepted
**Date:** 2026-09-11

## Context

In the SBFT 2026 competition RESTest's operations-covered AUC was 8,322 against a baseline of 56,590,
while its *final* operation coverage was mid-field. It reached a reasonable place far too slowly. Two
causes: a local multi-agent value generator that ran before testing started and consumed much of the
budget, and a generate-compile-execute pipeline (ADR-0005).

An earlier draft of the plan proposed enforcing a floor of 100 requests per minute. The maintainers
rejected it, and correctly: throughput depends on the response time of the API under test, which we
do not know in advance. A tool that sends 20 requests per minute against an API that takes three
seconds to answer is behaving perfectly. A fixed threshold would punish us for someone else's latency
and would tell us nothing about our own efficiency.

What we actually want to guarantee is narrower and entirely within our control: the tool must never
be the reason nothing is happening.

## Decision

**Invariant.** The request loop never waits on computation. Anything that can be slow — an external
data provider, a solver call, a large schema analysis — runs on its own thread and publishes its
results when ready. The generator always has something valid to send in the meantime, and improved
values are swapped in as they arrive. This is checked by an architecture test, not by configuration.

**Metric: idle time.** Every run reports the fraction of the budget during which no request was in
flight. Against a slow API, throughput is low and idle time is near zero — the tool is waiting for the
API, which is correct. With RESTest 1.x's problem, idle time is high regardless of the API. The metric
separates our inefficiency from theirs; a throughput threshold cannot.

**Adaptive concurrency.** Observed response times drive the number of in-flight requests within a
bounded range, so a slow API is absorbed by concurrency rather than by waiting. The user can cap it
for fragile targets.

**Overhead regression test in CI.** A fixed specification against a local stub with constant, known
latency, measuring milliseconds of *our* processing per request. It fails if we make it worse. It
says nothing about real APIs and does not pretend to.

## Consequences

- The 2026 failure mode cannot recur silently: it shows up as a number in every run summary.
- Expensive future components — including model-backed providers — are safe by construction, because
  the only way to add one is through an asynchronous interface.
- No arbitrary threshold to argue about, and no false failures on slow APIs.
- Adaptive concurrency must be bounded and overridable, or we become a load generator against
  someone's staging environment.
- More concurrency means more care with shared state, which is why ADR-0004 forbids static mutable
  state.

## Alternatives considered

- **A fixed requests-per-minute floor.** Rejected: assumes knowledge of the API's latency.
- **Measuring wall-clock throughput only.** Confounds our speed with the API's.
- **Doing all expensive work up front, before testing.** Exactly what failed in 2026.
