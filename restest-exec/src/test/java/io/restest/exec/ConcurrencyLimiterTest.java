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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.restest.core.exec.EngineSettings;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    @DisplayName("a hold gives no slot to anybody until it is over, however many are free")
    void a_hold_gives_nothing_until_it_is_over() throws InterruptedException {
        ConcurrencyLimiter limiter = limiter(1, 4, 16);
        limiter.holdFor(Duration.ofMillis(300));
        long before = System.nanoTime();

        Took took = takeASlotElsewhere(limiter);

        assertThat(took.done().await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(took.got()).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - before))
                .isGreaterThanOrEqualTo(Duration.ofMillis(250));
    }

    @Test
    @DisplayName("a hold sends the limit to the floor, and quick answers during it earn nothing")
    void a_hold_starts_again_from_the_floor() throws InterruptedException {
        ConcurrencyLimiter limiter = limiter(1, 8, 16);
        useEverySlot(limiter);
        assertThat(limiter.limit()).isEqualTo(9);

        limiter.holdFor(Duration.ofMillis(300));
        Took took = takeASlotElsewhere(limiter);
        took.waitingOutTheHold();
        limiter.observe(FAST, false);
        assertThat(limiter.limit()).describedAs("waiting out a hold is not running out of room")
                .isEqualTo(1);

        assertThat(took.done().await(5, TimeUnit.SECONDS)).isTrue();
        limiter.release();
        useEverySlot(limiter);
        assertThat(limiter.limit()).describedAs("and it climbs again as it always does")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a shorter hold leaves a longer one in force")
    void a_shorter_hold_changes_nothing() throws InterruptedException {
        ConcurrencyLimiter limiter = limiter(1, 4, 16);
        limiter.holdFor(Duration.ofMillis(400));
        limiter.holdFor(Duration.ofMillis(10));

        Took took = takeASlotElsewhere(limiter);

        assertThat(took.done().await(200, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(took.done().await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("a longer hold moves the end of one in force later")
    void a_longer_hold_extends_one_in_force() throws InterruptedException {
        ConcurrencyLimiter limiter = limiter(1, 4, 16);
        limiter.holdFor(Duration.ofMillis(50));
        limiter.holdFor(Duration.ofMillis(500));
        long before = System.nanoTime();

        Took took = takeASlotElsewhere(limiter);

        assertThat(took.done().await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - before))
                .isGreaterThanOrEqualTo(Duration.ofMillis(450));
    }

    @Test
    @DisplayName("what is held is dropped: a request waiting out a hold goes away without a slot, "
            + "and one with no hold in force is untouched")
    void what_is_held_is_dropped() throws InterruptedException {
        ConcurrencyLimiter limiter = limiter(1, 1, 1);
        limiter.holdFor(Duration.ofHours(1));
        Took held = takeASlotElsewhere(limiter);
        held.waitingOutTheHold();

        limiter.dropWhatIsHeld();

        assertThat(held.done().await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(held.got()).isFalse();

        ConcurrencyLimiter unheld = limiter(1, 1, 1);
        unheld.acquire();
        Took waiting = takeASlotElsewhere(unheld);
        unheld.dropWhatIsHeld();
        assertThat(waiting.done().await(200, TimeUnit.MILLISECONDS))
                .describedAs("waiting for a slot, not for a hold").isFalse();
        unheld.release();
        assertThat(waiting.done().await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(waiting.got()).isTrue();
    }

    @Test
    @DisplayName("a request waiting out a hold when the engine closes goes away without a slot")
    void closing_lets_a_held_request_go_without_a_slot() throws InterruptedException {
        ConcurrencyLimiter limiter = limiter(1, 4, 16);
        limiter.holdFor(Duration.ofHours(1));
        Took took = takeASlotElsewhere(limiter);
        assertThat(took.done().await(100, TimeUnit.MILLISECONDS)).isFalse();

        limiter.close();

        assertThat(took.done().await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(took.got()).isFalse();
    }

    @Test
    @DisplayName("a hold longer than anything a clock counts is still a hold, never a failure")
    void a_hold_of_centuries_is_a_hold() throws InterruptedException {
        ConcurrencyLimiter limiter = limiter(1, 4, 16);
        limiter.holdFor(Duration.ofSeconds(Long.MAX_VALUE));
        Took took = takeASlotElsewhere(limiter);

        assertThat(took.done().await(100, TimeUnit.MILLISECONDS)).isFalse();
        limiter.close();
        assertThat(took.done().await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("with a rate, the first request goes at once and the next waits for its turn, "
            + "however many slots are free")
    void the_next_request_waits_for_its_turn() throws InterruptedException {
        ConcurrencyLimiter limiter = paced(300);
        long before = System.nanoTime();
        assertThat(limiter.acquire()).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - before))
                .isLessThan(Duration.ofMillis(150));

        Took next = takeASlotElsewhere(limiter);

        assertThat(next.done().await(100, TimeUnit.MILLISECONDS))
                .describedAs("a fifth of a second between two requests").isFalse();
        assertThat(next.done().await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(next.got()).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - before))
                .isGreaterThanOrEqualTo(Duration.ofMillis(190));
    }

    @Test
    @DisplayName("a quiet spell is never made up for: after it, the requests are as far apart as "
            + "ever")
    void no_burst_after_a_quiet_spell() throws InterruptedException {
        AtomicLong clock = new AtomicLong();
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(
                EngineSettings.defaults().withMostRequestsPerMinute(300), clock::get);
        goes(limiter);
        limiter.release();
        clock.addAndGet(Duration.ofMillis(600).toNanos());

        assertThat(limiter.untilItsTurn()).describedAs("its turn came long ago").isZero();
        goes(limiter);

        assertThat(limiter.untilItsTurn())
                .describedAs("two turns went unused, and neither is given back")
                .isEqualTo(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("waiting for a turn is not running out of room, so it earns nothing")
    void waiting_for_a_turn_earns_nothing() throws InterruptedException {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(EngineSettings.defaults()
                .withConcurrency(1, 2, 16).withMostRequestsPerMinute(1));
        limiter.acquire();
        Took next = takeASlotElsewhere(limiter);
        next.waitingOutTheHold();

        limiter.observe(FAST, false);

        assertThat(limiter.limit()).describedAs("one slot of two was ever in use").isEqualTo(2);
        limiter.close();
    }

    @Test
    @DisplayName("a hold the API asks for while a request waits for its turn still holds it once "
            + "the turn has come")
    void a_hold_during_a_turn_still_holds() throws InterruptedException {
        ConcurrencyLimiter limiter = paced(300);
        long before = System.nanoTime();
        limiter.acquire();
        limiter.release();
        Took next = takeASlotElsewhere(limiter);
        next.waitingOutTheHold();

        limiter.holdFor(Duration.ofMillis(600));

        assertThat(next.done().await(400, TimeUnit.MILLISECONDS))
                .describedAs("its turn came at a fifth of a second, inside the hold").isFalse();
        assertThat(next.done().await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(next.got()).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - before))
                .isGreaterThanOrEqualTo(Duration.ofMillis(550));
    }

    @Test
    @DisplayName("a request waiting for its turn when the engine closes goes away without a slot")
    void closing_lets_a_request_waiting_for_its_turn_go() throws InterruptedException {
        ConcurrencyLimiter limiter = paced(1);
        limiter.acquire();
        Took next = takeASlotElsewhere(limiter);
        next.waitingOutTheHold();

        limiter.close();

        assertThat(next.done().await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(next.got()).isFalse();
    }

    @Test
    @DisplayName("once what is held is dropped, a request that would wait for its turn goes away "
            + "without a slot, and one whose turn has come still goes")
    void dropping_lets_a_request_waiting_for_its_turn_go() throws InterruptedException {
        ConcurrencyLimiter limiter = paced(1);
        limiter.acquire();
        Took next = takeASlotElsewhere(limiter);
        next.waitingOutTheHold();

        limiter.dropWhatIsHeld();

        assertThat(next.done().await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(next.got()).isFalse();

        ConcurrencyLimiter full = new ConcurrencyLimiter(EngineSettings.defaults()
                .withoutConcurrency().withMostRequestsPerMinute(6000));
        full.acquire();
        Thread.sleep(50);
        Took waiting = takeASlotElsewhere(full);
        full.dropWhatIsHeld();
        assertThat(waiting.done().await(200, TimeUnit.MILLISECONDS))
                .describedAs("waiting for a slot, its turn already come").isFalse();
        full.release();
        assertThat(waiting.done().await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(waiting.got()).isTrue();
    }

    @Test
    @DisplayName("how long one more request would wait counts the turns of those handed over "
            + "before it")
    void the_wait_for_a_turn_counts_those_before_it() throws InterruptedException {
        AtomicLong clock = new AtomicLong(1_000);
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(
                EngineSettings.defaults().withMostRequestsPerMinute(600), clock::get);
        assertThat(limiter.untilItsTurn()).isZero();

        limiter.handedOver();
        assertThat(limiter.untilItsTurn())
                .describedAs("its turn is now; one more would come a tenth of a second after it")
                .isEqualTo(Duration.ofMillis(100));
        assertThat(limiter.acquire()).isTrue();
        limiter.handedOver();
        limiter.handedOver();
        assertThat(limiter.untilItsTurn()).isEqualTo(Duration.ofMillis(300));

        clock.addAndGet(Duration.ofMillis(100).toNanos());
        limiter.notGoing();
        assertThat(limiter.untilItsTurn()).isEqualTo(Duration.ofMillis(100));

        ConcurrencyLimiter unpaced = limiter(1, 4, 16);
        unpaced.handedOver();
        unpaced.acquire();
        unpaced.handedOver();
        assertThat(unpaced.untilItsTurn()).describedAs("no rate, no turns").isZero();
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE + 5, -1, Long.MAX_VALUE - 5})
    @DisplayName("wherever the clock begins, and when it goes round, the first request goes at "
            + "once and the next waits a turn")
    void the_first_turn_is_now_whatever_the_clock(long start) throws InterruptedException {
        AtomicLong clock = new AtomicLong(start);
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(
                EngineSettings.defaults().withMostRequestsPerMinute(600), clock::get);

        goes(limiter);
        assertThat(limiter.untilItsTurn()).isEqualTo(Duration.ofMillis(100));

        clock.addAndGet(Duration.ofMillis(100).toNanos());
        assertThat(limiter.untilItsTurn()).isZero();
        goes(limiter);
    }

    /** A slot being taken on a thread of its own: whether it was, once it is done trying. */
    private record Took(CountDownLatch done, AtomicBoolean took, Thread thread) {

        boolean got() {
            return took.get();
        }

        /** Returns once the thread is waiting, with a time limit, as it does out a hold. */
        void waitingOutTheHold() {
            while (thread.getState() != Thread.State.TIMED_WAITING) {
                Thread.onSpinWait();
            }
        }
    }

    private static Took takeASlotElsewhere(ConcurrencyLimiter limiter) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean took = new AtomicBoolean();
        Thread thread = Thread.ofVirtual().unstarted(() -> {
            try {
                took.set(limiter.acquire());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        Took taking = new Took(done, took, thread);
        thread.start();
        return taking;
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

    @Test
    @DisplayName("how much slower counts as struggling is a setting: told to be forgiving, the "
            + "engine keeps the same number of requests in flight where it would have backed off")
    void how_forgiving_to_be_is_a_setting() throws InterruptedException {
        ConcurrencyLimiter forgiving = new ConcurrencyLimiter(EngineSettings.defaults()
                .withConcurrency(1, 4, 16)
                .withSlowdownFactor(1_000));
        for (int i = 0; i < 5; i++) {
            forgiving.observe(FAST, false);
        }

        forgiving.observe(SLOW, false);

        assertThat(forgiving.limit())
                .describedAs("the same answer lowers the limit at the usual setting, and an API "
                        + "that is simply uneven rather than struggling is what this is for")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("and told to be strict, it backs off where it would have carried on")
    void being_strict_is_the_same_setting() {
        ConcurrencyLimiter strict = new ConcurrencyLimiter(EngineSettings.defaults()
                .withConcurrency(1, 4, 16)
                .withSlowdownFactor(1.01));
        for (int i = 0; i < 5; i++) {
            strict.observe(USUAL, false);
        }

        strict.observe(USUAL * 2, false);

        assertThat(strict.limit()).isEqualTo(3);
    }

    /**
     * Hands a request over and gives it a slot, as the engine does, failing rather than hanging if
     * it would have to wait: with a clock the test sets, a wait would never end.
     */
    private static void goes(ConcurrencyLimiter limiter) {
        limiter.handedOver();
        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThat(limiter.acquire()).isTrue());
    }

    /** A limiter with room for sixteen in flight, kept to so many requests a minute. */
    private static ConcurrencyLimiter paced(int perMinute) {
        return new ConcurrencyLimiter(
                EngineSettings.defaults().withMostRequestsPerMinute(perMinute));
    }

    private static ConcurrencyLimiter limiter(int minimum, int initial, int maximum) {
        return new ConcurrencyLimiter(
                EngineSettings.defaults().withConcurrency(minimum, initial, maximum));
    }
}
