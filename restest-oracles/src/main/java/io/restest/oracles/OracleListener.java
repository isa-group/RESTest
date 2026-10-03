/*
 * Copyright 2026 ISA Research Group, Universidad de Sevilla.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.restest.oracles;

import io.restest.core.event.EventStream;
import io.restest.core.event.RunEvent;
import io.restest.core.event.RunListener;
import io.restest.core.model.ApiModel;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.Oracle;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Listens to a run and asks every rule about each attempt, announcing whatever they object to.
 *
 * <p>This is what joins the two halves of testing together. Requests go out and replies come back;
 * each finished attempt is announced; this hears the announcement, puts the attempt to every rule
 * in turn, and announces each fault they find so that the reports can print it.
 *
 * <p>Judging happens away from the sending of requests - it is a listener like any other - which is
 * what keeps a slow rule from becoming a slow run. A rule that throws is not allowed to stop the
 * others or the run: it is stopped here, at the one rule that failed, and counted, so that the
 * attempt is still put to every other rule and the run still ends by admitting that something on
 * our side went wrong.
 */
public final class OracleListener implements RunListener {

    private final ApiModel api;
    private final List<Oracle> oracles;
    private final EventStream events;
    private final Clock clock;
    private final AtomicLong failures = new AtomicLong();
    private final Set<String> broken = ConcurrentHashMap.newKeySet();

    /** Judges with the rules RESTest ships with. */
    public static OracleListener standard(ApiModel api, EventStream events) {
        return new OracleListener(api, Oracles.standard(), events, Clock.systemUTC());
    }

    public OracleListener(ApiModel api, List<Oracle> oracles, EventStream events, Clock clock) {
        this.api = Objects.requireNonNull(api, "api");
        this.oracles = List.copyOf(oracles);
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The rules this is judging with, in the order it asks them. */
    public List<Oracle> oracles() {
        return oracles;
    }

    /**
     * How many times a rule failed while judging an attempt.
     *
     * <p>Read at the end of the run, because it changes what the run is allowed to say. Some of what
     * should have been judged was not, so "nothing was found wrong" would be a stronger claim than
     * the evidence supports.
     *
     * <p>This counts judgements, not rules: one rule that fails on everything fails once per reply,
     * which against a fast API is thousands of times in a minute. Whoever reports it wants
     * {@link #rulesThatFailed()} for how bad it is and this for how often, because "four thousand
     * failures" and "one rule, four thousand times" describe the same run and only the second sends
     * anybody to the right place.
     */
    public long failures() {
        return failures.get();
    }

    /** How many distinct rules failed at least once. */
    public int rulesThatFailed() {
        return broken.size();
    }

    @Override
    public void on(RunEvent event) {
        if (!(event instanceof RunEvent.InteractionCompleted completed)) {
            return;
        }
        for (Oracle oracle : oracles) {
            try {
                for (Finding finding : oracle.judge(completed.interaction(), api)) {
                    events.publish(new RunEvent.FaultFound(clock.instant(), finding));
                }
            } catch (RuntimeException | LinkageError itFailed) {
                // Caught around one rule rather than around all of them. Letting it out of here
                // ended the whole loop, so every rule after the one that failed never saw this
                // attempt at all - and a reply that went unchecked by most of the rules was
                // indistinguishable, in the report, from one they had all passed.
                //
                // LinkageError is caught alongside for the reason the event stream catches it: a
                // rule whose own library is missing a class throws one, and a broken rule is a
                // broken rule however it says so. Errors that mean the machine itself is in
                // trouble are still left alone.
                failures.incrementAndGet();
                // By class rather than by the name the rule gives itself: a rule broken enough to
                // throw may be broken enough to throw again when asked what it is called.
                broken.add(oracle.getClass().getName());
            }
        }
    }
}
