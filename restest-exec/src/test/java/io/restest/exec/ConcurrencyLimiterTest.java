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
package io.restest.exec;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.exec.EngineSettings;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the rule that decides how many requests may be in flight, by telling the limiter how
 * requests went rather than by sending any.
 */
class ConcurrencyLimiterTest {

    private static final long FAST = Duration.ofMillis(1).toNanos();
    private static final long USUAL = Duration.ofMillis(50).toNanos();
    private static final long SLOW = Duration.ofMillis(400).toNanos();

    @Test
    @DisplayName("the engine starts where the settings say, before anything is known about the API")
    void the_limit_starts_at_the_configured_value() {
        assertThat(limiter(1, 4, 16).limit()).isEqualTo(4);
    }

    @Test
    @DisplayName("quick answers alone are no reason to allow more: the slots were never all in use")
    void the_limit_does_not_grow_without_evidence() {
        ConcurrencyLimiter limiter = limiter(1, 4, 16);

        for (int i = 0; i < 20; i++) {
            limiter.observe(FAST, false);
        }

        assertThat(limiter.limit()).isEqualTo(4);
    }

    @Test
    @DisplayName("quick answers while every slot is in use are a reason to allow one more")
    void running_out_of_slots_and_answering_quickly_raises_the_limit()
            throws InterruptedException {
        ConcurrencyLimiter limiter = limiter(1, 2, 16);

        useEverySlot(limiter);

        assertThat(limiter.limit()).isEqualTo(3);
    }

    @Test
    @DisplayName("answers much slower than the quickest one mean requests are queueing: allow fewer")
    void a_slowdown_lowers_the_limit() throws InterruptedException {
        ConcurrencyLimiter limiter = limiter(1, 4, 16);
        for (int i = 0; i < 5; i++) {
            limiter.observe(FAST, false);
        }

        limiter.observe(SLOW, false);

        assertThat(limiter.limit()).isEqualTo(3);
    }

    @Test
    @DisplayName("one unusually quick answer does not convince the engine that everything else is slow")
    void a_single_fast_answer_does_not_collapse_the_limit() throws InterruptedException {
        ConcurrencyLimiter limiter = limiter(1, 4, 16);
        for (int i = 0; i < 10; i++) {
            limiter.observe(USUAL, false);
        }
        int before = limiter.limit();

        // A 404 for an identifier that does not exist, or a 400 refusing deliberately invalid
        // input: this tool produces those constantly, and they come back in no time at all.
        limiter.observe(FAST, false);
        for (int i = 0; i < 20; i++) {
            limiter.observe(USUAL, false);
        }

        assertThat(limiter.limit())
                .describedAs("ordinary answers after a quick one are still ordinary answers")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("a slowdown that passes is recovered from, rather than held against the API")
    void the_limit_climbs_back_after_a_slowdown_ends() throws InterruptedException {
        ConcurrencyLimiter limiter = limiter(1, 4, 16);
        for (int i = 0; i < 10; i++) {
            limiter.observe(USUAL, false);
        }
        for (int i = 0; i < 3; i++) {
            limiter.observe(SLOW, false);
        }
        assertThat(limiter.limit()).isLessThan(4);

        for (int i = 0; i < 6; i++) {
            useEverySlot(limiter);
        }

        assertThat(limiter.limit()).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("a request that failed outright halves the limit at once")
    void a_failure_halves_the_limit() {
        ConcurrencyLimiter limiter = limiter(1, 16, 16);

        limiter.observe(FAST, true);

        assertThat(limiter.limit()).isEqualTo(8);
    }

    @Test
    @DisplayName("however badly it goes, the engine keeps sending; however well, it stays bounded")
    void the_limit_stays_between_the_floor_and_the_ceiling() throws InterruptedException {
        ConcurrencyLimiter falling = limiter(2, 4, 8);
        for (int i = 0; i < 10; i++) {
            falling.observe(FAST, true);
        }
        assertThat(falling.limit()).isEqualTo(2);

        ConcurrencyLimiter rising = limiter(1, 2, 3);
        for (int i = 0; i < 10; i++) {
            useEverySlot(rising);
        }
        assertThat(rising.limit()).isEqualTo(3);
    }

    @Test
    @DisplayName("a request waits when every slot is taken, and goes as soon as one is freed")
    void acquire_waits_for_a_free_slot() throws InterruptedException {
        ConcurrencyLimiter limiter = limiter(1, 1, 1);
        limiter.acquire();
        AtomicBoolean went = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);
        Thread waiting = Thread.ofVirtual().start(() -> {
            try {
                limiter.acquire();
                went.set(true);
                done.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(done.await(200, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(went).isFalse();

        limiter.release();

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(went).isTrue();
        waiting.join();
    }

    /** Fills every slot, reports one quick answer, and hands the slots back. */
    private static void useEverySlot(ConcurrencyLimiter limiter) throws InterruptedException {
        int slots = limiter.limit();
        for (int i = 0; i < slots; i++) {
            limiter.acquire();
        }
        limiter.observe(FAST, false);
        for (int i = 0; i < slots; i++) {
            limiter.release();
        }
    }

    private static ConcurrencyLimiter limiter(int minimum, int initial, int maximum) {
        return new ConcurrencyLimiter(
                EngineSettings.defaults().withConcurrency(minimum, initial, maximum));
    }
}
