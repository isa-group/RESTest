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
package io.restest.core.exec;

import java.time.Duration;
import java.util.Objects;

/**
 * How the HTTP engine should behave while it sends requests: how long to wait, how many requests to
 * have in flight at once, how much of a large reply to keep, and what to call ourselves.
 *
 * <p>{@link #defaults()} is meant to be usable against an unknown API with nobody having configured
 * anything, which is the tool's first design principle. Every value below says why it is what it is,
 * because a default nobody can defend is a value that gets changed at random later.
 *
 * <p>Settings are a value, not a switchboard: an engine reads them once when it is built, and
 * changing them means building another engine. Two engines with different settings can run side by
 * side in the same program without either one noticing the other.
 *
 * @param connectTimeout how long to wait for the API to accept a connection at all
 * @param readTimeout how long to wait for the API to send something once connected, each time it is
 *     waited for. A reply that keeps sending a little at a time never runs out of it
 * @param writeTimeout how long to wait while sending a request body
 * @param callTimeout the longest one request may take, from the moment it is sent to the last byte
 *     of its reply. This is the one that decides how long a hung endpoint costs us: an endpoint
 *     that streams, or sends a byte every few seconds, would otherwise hold its place among the
 *     requests in flight for as long as the run lasts. None of the three waits above may be longer,
 *     since the whole request is given up on when this runs out
 * @param minConcurrency the fewest requests the engine will keep in flight, however badly the API
 *     behaves. Never zero: at zero the engine would have stopped testing
 * @param initialConcurrency how many requests are in flight before anything is known about the
 *     API's speed
 * @param maxConcurrency the most requests the engine will ever have in flight. This is the promise
 *     that the tool stays a test tool and does not turn into a load generator against somebody's
 *     staging environment
 * @param slowdownFactor how much slower than the best answer so far counts as "the API is
 *     struggling", at which point the engine keeps fewer requests in flight. Two is deliberately
 *     forgiving: normal APIs vary by more than a few percent, and reacting to that would leave the
 *     number of requests in flight oscillating rather than settling
 * @param maxRetainedResponseBytes how much of a reply body is kept. Beyond this the reply is kept as
 *     a truncated payload that still remembers how long the whole thing was, so a report can say "12
 *     MB of JSON" without the run holding 12 MB per response in memory
 * @param followRedirects whether a redirection is followed automatically. Off by default: an API
 *     that answers 302 where its own documentation promises 200 has a bug, and a client that quietly
 *     follows the redirect hides exactly the answer the tool exists to look at
 * @param userAgent what the engine calls itself in the {@code User-Agent} header, so that a person
 *     reading their API's access log can tell which traffic was ours
 */
public record EngineSettings(
        Duration connectTimeout,
        Duration readTimeout,
        Duration writeTimeout,
        Duration callTimeout,
        int minConcurrency,
        int initialConcurrency,
        int maxConcurrency,
        double slowdownFactor,
        long maxRetainedResponseBytes,
        boolean followRedirects,
        String userAgent) {

    /** One mebibyte: generous for an API reply, small enough to hold thousands of them. */
    public static final long DEFAULT_MAX_RETAINED_RESPONSE_BYTES = 1024L * 1024L;

    private static final EngineSettings DEFAULTS = new EngineSettings(
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofSeconds(10),
            // Twice the read timeout: a reply that has started arriving gets as long again to
            // finish, and only one still arriving after a minute - which is what a stream looks
            // like - is cut off.
            Duration.ofSeconds(60),
            1,
            4,
            16,
            2.0,
            DEFAULT_MAX_RETAINED_RESPONSE_BYTES,
            false,
            "RESTest/2.0");

    public EngineSettings {
        positive(connectTimeout, "connectTimeout");
        positive(readTimeout, "readTimeout");
        positive(writeTimeout, "writeTimeout");
        positive(callTimeout, "callTimeout");
        notLongerThanTheWholeRequest(connectTimeout, "connectTimeout", callTimeout);
        notLongerThanTheWholeRequest(readTimeout, "readTimeout", callTimeout);
        notLongerThanTheWholeRequest(writeTimeout, "writeTimeout", callTimeout);
        Objects.requireNonNull(userAgent, "userAgent");
        if (minConcurrency < 1) {
            throw new IllegalArgumentException("minConcurrency must be at least 1, so that the "
                    + "engine always has a request in flight: " + minConcurrency);
        }
        if (maxConcurrency < minConcurrency) {
            throw new IllegalArgumentException("maxConcurrency (" + maxConcurrency + ") is below "
                    + "minConcurrency (" + minConcurrency + "), which leaves no room to send "
                    + "anything");
        }
        if (initialConcurrency < minConcurrency || initialConcurrency > maxConcurrency) {
            throw new IllegalArgumentException("initialConcurrency (" + initialConcurrency
                    + ") is outside the range the engine is allowed to use, " + minConcurrency
                    + " to " + maxConcurrency);
        }
        if (!(slowdownFactor > 1) || !Double.isFinite(slowdownFactor)) {
            // The second half is not belt and braces. A number typed as 1e400 is larger than a
            // double can hold and arrives here as infinity, which satisfies "greater than 1" and
            // then cannot be written down again - so a run would be configured with a value no
            // report could state.
            throw new IllegalArgumentException("slowdownFactor must be greater than 1 and a number "
                    + "that can be written down: at 1 every answer slower than the fastest one so "
                    + "far would count as the API struggling, and the engine would never settle: "
                    + slowdownFactor);
        }
        if (maxRetainedResponseBytes < 1) {
            throw new IllegalArgumentException("maxRetainedResponseBytes must be at least 1; an "
                    + "engine that keeps nothing of a reply has nothing to judge: "
                    + maxRetainedResponseBytes);
        }
        if (userAgent.isBlank()) {
            throw new IllegalArgumentException("the engine names itself in the User-Agent header, "
                    + "so that a person reading their access log can tell which traffic was ours");
        }
    }

    /** Settings that work against an unknown API with nothing configured. */
    public static EngineSettings defaults() {
        return DEFAULTS;
    }

    public EngineSettings withConnectTimeout(Duration value) {
        return new EngineSettings(value, readTimeout, writeTimeout, callTimeout, minConcurrency,
                initialConcurrency, maxConcurrency, slowdownFactor, maxRetainedResponseBytes,
                followRedirects, userAgent);
    }

    public EngineSettings withReadTimeout(Duration value) {
        return new EngineSettings(connectTimeout, value, writeTimeout, callTimeout, minConcurrency,
                initialConcurrency, maxConcurrency, slowdownFactor, maxRetainedResponseBytes,
                followRedirects, userAgent);
    }

    public EngineSettings withWriteTimeout(Duration value) {
        return new EngineSettings(connectTimeout, readTimeout, value, callTimeout, minConcurrency,
                initialConcurrency, maxConcurrency, slowdownFactor, maxRetainedResponseBytes,
                followRedirects, userAgent);
    }

    /** The longest one request may take; each of the three other waits has to fit inside it. */
    public EngineSettings withCallTimeout(Duration value) {
        return new EngineSettings(connectTimeout, readTimeout, writeTimeout, value, minConcurrency,
                initialConcurrency, maxConcurrency, slowdownFactor, maxRetainedResponseBytes,
                followRedirects, userAgent);
    }

    /**
     * The whole concurrency range at once, because the three numbers only make sense together.
     *
     * @param minimum the fewest requests in flight
     * @param initial how many to start with, before anything is known about the API
     * @param maximum the most requests in flight, ever
     * @return settings using that range
     */
    public EngineSettings withConcurrency(int minimum, int initial, int maximum) {
        return new EngineSettings(connectTimeout, readTimeout, writeTimeout, callTimeout, minimum,
                initial, maximum, slowdownFactor, maxRetainedResponseBytes, followRedirects,
                userAgent);
    }

    /** One request at a time, for an API too fragile to be asked two questions at once. */
    public EngineSettings withoutConcurrency() {
        return withConcurrency(1, 1, 1);
    }

    /** How much slower than its best answer counts as the API struggling. Greater than one. */
    public EngineSettings withSlowdownFactor(double value) {
        return new EngineSettings(connectTimeout, readTimeout, writeTimeout, callTimeout,
                minConcurrency, initialConcurrency, maxConcurrency, value, maxRetainedResponseBytes,
                followRedirects, userAgent);
    }

    public EngineSettings withMaxRetainedResponseBytes(long value) {
        return new EngineSettings(connectTimeout, readTimeout, writeTimeout, callTimeout,
                minConcurrency, initialConcurrency, maxConcurrency, slowdownFactor, value,
                followRedirects, userAgent);
    }

    public EngineSettings withFollowRedirects(boolean value) {
        return new EngineSettings(connectTimeout, readTimeout, writeTimeout, callTimeout,
                minConcurrency, initialConcurrency, maxConcurrency, slowdownFactor,
                maxRetainedResponseBytes, value, userAgent);
    }

    public EngineSettings withUserAgent(String value) {
        return new EngineSettings(connectTimeout, readTimeout, writeTimeout, callTimeout,
                minConcurrency, initialConcurrency, maxConcurrency, slowdownFactor,
                maxRetainedResponseBytes, followRedirects, value);
    }

    /**
     * Refuses a wait longer than the whole request may take. It could never be waited for, so a run
     * that accepted it would be configured in a way nobody would get.
     */
    private static void notLongerThanTheWholeRequest(Duration value, String what,
            Duration callTimeout) {
        if (value.compareTo(callTimeout) > 0) {
            throw new IllegalArgumentException(what + " (" + said(value) + ") is longer than "
                    + "callTimeout (" + said(callTimeout) + "), the longest a whole request may "
                    + "take, so it could never be waited for; raise callTimeout as well");
        }
    }

    /** A length of time the way a person would write it in a settings file: in its largest unit. */
    private static String said(Duration value) {
        long millis = value.toMillis();
        if (millis % 3_600_000 == 0) {
            return millis / 3_600_000 + "h";
        }
        if (millis % 60_000 == 0) {
            return millis / 60_000 + "m";
        }
        return millis % 1000 == 0 ? millis / 1000 + "s" : millis + "ms";
    }

    private static void positive(Duration value, String what) {
        Objects.requireNonNull(value, what);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(what + " must be greater than zero, or the engine "
                    + "would give up before it started: " + value);
        }
    }
}
