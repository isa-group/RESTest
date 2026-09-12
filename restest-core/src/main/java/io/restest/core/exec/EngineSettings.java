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
 * @param readTimeout how long to wait for the API to send something once connected. This is the one
 *     that decides how long a hung endpoint costs us
 * @param writeTimeout how long to wait while sending a request body
 * @param minConcurrency the fewest requests the engine will keep in flight, however badly the API
 *     behaves. Never zero: at zero the engine would have stopped testing
 * @param initialConcurrency how many requests are in flight before anything is known about the
 *     API's speed
 * @param maxConcurrency the most requests the engine will ever have in flight. This is the promise
 *     that the tool stays a test tool and does not turn into a load generator against somebody's
 *     staging environment
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
        int minConcurrency,
        int initialConcurrency,
        int maxConcurrency,
        long maxRetainedResponseBytes,
        boolean followRedirects,
        String userAgent) {

    /** One mebibyte: generous for an API reply, small enough to hold thousands of them. */
    public static final long DEFAULT_MAX_RETAINED_RESPONSE_BYTES = 1024L * 1024L;

    private static final EngineSettings DEFAULTS = new EngineSettings(
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofSeconds(10),
            1,
            4,
            16,
            DEFAULT_MAX_RETAINED_RESPONSE_BYTES,
            false,
            "RESTest/2.0");

    public EngineSettings {
        positive(connectTimeout, "connectTimeout");
        positive(readTimeout, "readTimeout");
        positive(writeTimeout, "writeTimeout");
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
        return new EngineSettings(value, readTimeout, writeTimeout, minConcurrency,
                initialConcurrency, maxConcurrency, maxRetainedResponseBytes, followRedirects,
                userAgent);
    }

    public EngineSettings withReadTimeout(Duration value) {
        return new EngineSettings(connectTimeout, value, writeTimeout, minConcurrency,
                initialConcurrency, maxConcurrency, maxRetainedResponseBytes, followRedirects,
                userAgent);
    }

    public EngineSettings withWriteTimeout(Duration value) {
        return new EngineSettings(connectTimeout, readTimeout, value, minConcurrency,
                initialConcurrency, maxConcurrency, maxRetainedResponseBytes, followRedirects,
                userAgent);
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
        return new EngineSettings(connectTimeout, readTimeout, writeTimeout, minimum, initial,
                maximum, maxRetainedResponseBytes, followRedirects, userAgent);
    }

    /** One request at a time, for an API too fragile to be asked two questions at once. */
    public EngineSettings withoutConcurrency() {
        return withConcurrency(1, 1, 1);
    }

    public EngineSettings withMaxRetainedResponseBytes(long value) {
        return new EngineSettings(connectTimeout, readTimeout, writeTimeout, minConcurrency,
                initialConcurrency, maxConcurrency, value, followRedirects, userAgent);
    }

    public EngineSettings withFollowRedirects(boolean value) {
        return new EngineSettings(connectTimeout, readTimeout, writeTimeout, minConcurrency,
                initialConcurrency, maxConcurrency, maxRetainedResponseBytes, value, userAgent);
    }

    public EngineSettings withUserAgent(String value) {
        return new EngineSettings(connectTimeout, readTimeout, writeTimeout, minConcurrency,
                initialConcurrency, maxConcurrency, maxRetainedResponseBytes, followRedirects,
                value);
    }

    private static void positive(Duration value, String what) {
        Objects.requireNonNull(value, what);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(what + " must be greater than zero, or the engine "
                    + "would give up before it started: " + value);
        }
    }
}
