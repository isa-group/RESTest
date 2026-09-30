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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a run and the thread Java gives a stopped program share: the order to stop, and the wait
 * for the run to have left behind what it found.
 */
class StopFromOutsideTest {

    @Test
    @DisplayName("a run nobody stopped has not been asked to stop")
    void nobody_asked() {
        StopFromOutside stop = new StopFromOutside();

        assertThat(stop.asked()).isFalse();
        assertThat(stop.askedAt()).isEmpty();
    }

    @Test
    @DisplayName("a run is stopped when it is first asked, however often it is asked after")
    void the_first_order_is_the_one_that_counts() throws InterruptedException {
        StopFromOutside stop = new StopFromOutside();

        stop.ask();
        Instant first = stop.askedAt().orElseThrow();
        Thread.sleep(20);
        stop.ask();

        assertThat(stop.asked()).isTrue();
        assertThat(stop.askedAt()).contains(first);
    }

    @Test
    @DisplayName("the wait ends as soon as the run says it is done")
    void the_wait_ends_when_the_run_is_done() {
        StopFromOutside stop = new StopFromOutside();
        Thread.ofPlatform().daemon().start(() -> {
            while (!stop.asked()) {
                Thread.onSpinWait();
            }
            stop.wrappedUp();
        });

        long began = System.nanoTime();
        boolean done = stop.askAndWait(Duration.ofSeconds(30));

        assertThat(done).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("the wait ends when its time is up, and says the run was not done")
    void the_wait_has_an_end() {
        StopFromOutside stop = new StopFromOutside();

        long began = System.nanoTime();
        boolean done = stop.askAndWait(Duration.ofMillis(200));

        assertThat(done).isFalse();
        assertThat(stop.asked()).describedAs("the run was asked all the same").isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - began))
                .isGreaterThanOrEqualTo(Duration.ofMillis(190));
    }

    @Test
    @DisplayName("a run that was done before it was stopped lets the program end at once")
    void done_before_it_was_stopped() {
        StopFromOutside stop = new StopFromOutside();
        stop.wrappedUp();

        assertThat(stop.askAndWait(Duration.ofSeconds(30))).isTrue();
    }

    @Test
    @DisplayName("Java's own arrangement is undone once the run is over, and undoing it twice is harmless")
    void the_shutdown_hook_is_undone() {
        AtomicBoolean ran = new AtomicBoolean();
        StopsFromOutside.Arrangement heard = StopsFromOutside.shutdownHook()
                .whenStopped(() -> ran.set(true));

        assertThatCode(() -> {
            heard.close();
            heard.close();
        }).doesNotThrowAnyException();
        assertThat(ran).describedAs("nothing told the program to stop").isFalse();
    }
}
