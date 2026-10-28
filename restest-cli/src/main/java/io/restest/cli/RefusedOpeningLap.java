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
package io.restest.cli;

import io.restest.core.event.Unauthorized;
import java.time.InstantSource;
import java.util.Objects;
import java.util.Optional;

/**
 * Says whether the API refused the whole of a run's opening lap, so that the run can stop sending
 * rather than collect the same refusal until its time runs out.
 *
 * <p>The opening lap is the first round of a run: every operation sent once, each with the request
 * the API is likeliest to accept. When every one of those requests is answered, and every answer is
 * 401 - "who are you?" - the API is refusing all of itself to this run, for want of a key or because
 * the key is wrong. Left alone, such a run sends thousands of requests a second that all come back
 * the same, and ends as if it had found nothing wrong. {@link RunLoop} asks this, and stops.
 *
 * <p>Anything else rules the stop out for the rest of the run, because it means the run can still
 * learn something: a reply that is not a 401 - an operation anybody may call, a 400 for a value the
 * API checks before it asks who is calling, a 500 - and a request of the lap that got no reply at
 * all: one that never came in time is a fault of its own, and a connection refused or cut may be an
 * API still starting up. So is a 401 the run never
 * hears because its time ran out first: the question is asked once, when the lap is over and every
 * one of its requests has been settled, and never again. A run without an opening lap never asks it.
 *
 * <p>Answers arrive on threads of their own and the question is asked on the thread that decides
 * what to send, so everything is kept behind one lock: each request settles with one call that
 * says what came back and that it settled, and the question reads all of it at once. It is told by
 * the loop directly, the moment an answer arrives, and never by listening to the run's
 * announcements, which reach their listeners later: told that way, it could see a lap settled
 * before it had heard what the last request of it brought back.
 */
final class RefusedOpeningLap {

    private final InstantSource clock;
    private final Object lock = new Object();

    // Everything below but the answer is read and written holding the lock.
    private long lapRequests;
    private boolean lapOver;
    private long lapSettled;
    private boolean somethingElseCameBack;

    /**
     * The answer once it has been given, and nothing until then. Read without the lock, so that what
     * arrives once it has been given, and the question asked before every request, cost nothing.
     */
    private volatile Optional<Unauthorized> answer;

    private RefusedOpeningLap(boolean asked, InstantSource clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.answer = asked ? null : Optional.empty();
    }

    /**
     * Watches a run's opening lap.
     *
     * @param asked whether the run stops when the API refuses its whole opening lap; off, the
     *     answer is no from the start
     */
    static RefusedOpeningLap watching(boolean asked) {
        return watching(asked, InstantSource.system());
    }

    /** The same, telling the time by the given clock. */
    static RefusedOpeningLap watching(boolean asked, InstantSource clock) {
        return new RefusedOpeningLap(asked, clock);
    }

    /** A request of the opening lap is about to be sent. */
    void sentInTheLap() {
        synchronized (lock) {
            lapRequests++;
        }
    }

    /**
     * The opening lap has sent everything it will send. Told again at every ordinary request, which
     * comes only once the lap is over, so that a run whose lap sent nothing has its answer too.
     */
    void lapIsOver() {
        if (answer != null) {
            return;
        }
        synchronized (lock) {
            lapOver = true;
        }
    }

    /**
     * A request settled, one way or another: answered, or given up on with nothing to show.
     *
     * @param statusCode what the API answered, if it answered at all
     * @param inTheLap whether the request belonged to the opening lap
     */
    void settled(Optional<Integer> statusCode, boolean inTheLap) {
        if (answer != null) {
            return;
        }
        synchronized (lock) {
            if (inTheLap) {
                lapSettled++;
                if (statusCode.isEmpty()) {
                    somethingElseCameBack = true;
                }
            }
            if (statusCode.isPresent() && statusCode.get() != 401) {
                somethingElseCameBack = true;
            }
            // From here on the answer can only be no, so it is given now rather than when the lap is
            // over: what arrives after it then costs nothing.
            if (somethingElseCameBack && answer == null) {
                answer = Optional.empty();
            }
        }
    }

    /**
     * Whether the API refused the whole opening lap: answered the first time the lap is over and
     * every one of its requests has settled, or as soon as something other than a 401 came back.
     * Until then, and ever after when the answer was no, it says no.
     *
     * @return that the API refused the lap, when it did
     */
    Optional<Unauthorized> refused() {
        Optional<Unauthorized> given = answer;
        if (given != null) {
            return given;
        }
        synchronized (lock) {
            if (answer == null && lapOver && lapSettled >= lapRequests) {
                answer = lapRequests > 0 && !somethingElseCameBack
                        ? Optional.of(new Unauthorized(clock.instant(), lapRequests))
                        : Optional.empty();
            }
            return answer == null ? Optional.empty() : answer;
        }
    }
}
