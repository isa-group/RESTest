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

import java.util.Objects;

/**
 * Where a run hears that the program it runs in has been told to stop.
 *
 * <p>The order comes from outside the program: Ctrl-C in a terminal, {@code kill} from a script,
 * {@code docker stop} for a container. Java passes it on, before it ends the program, to every part
 * of the program that asked to hear it, each on a thread of its own - which is what
 * {@link #shutdownHook()} asks for. The program ends once all of them are done, so what each one does
 * with its thread is the time a stopped program takes to end. A run uses it to stop sending and
 * write what it found; see {@link StopFromOutside}.
 *
 * <p>A test hands a run something else in its place, which the test sets off itself at a moment it
 * chooses: a real order to stop, sent to the program running the tests, would end the tests too.
 */
@FunctionalInterface
interface StopsFromOutside {

    /**
     * Arranges for this to be run, on a thread of its own, when the program is told to stop - until
     * the arrangement is undone. The program ends once it returns.
     *
     * @param stop what to do before the program ends
     * @return what undoes the arrangement
     */
    Arrangement whenStopped(Runnable stop);

    /** An arrangement to hear that the program has been told to stop. */
    interface Arrangement extends AutoCloseable {

        /** Undoes it. Doing so twice, or once the program has begun to end, does nothing. */
        @Override
        void close();
    }

    /**
     * Java's own way: a thread it runs when it has begun to end the program, whatever told it to.
     *
     * <p>Ctrl-C, {@code kill}, {@code docker stop} and a terminal being closed all end the program
     * this way, and Java ends it afterwards with the number that says which: 130 for Ctrl-C, 143 for
     * the other two, 129 for a closed terminal. Only {@code kill -9}, a container running out of
     * memory, and a Java started with {@code -Xrs} end it without running anything first.
     */
    static StopsFromOutside shutdownHook() {
        return stop -> {
            Objects.requireNonNull(stop, "stop");
            Runtime runtime = Runtime.getRuntime();
            Thread hook = Thread.ofPlatform().name("restest-stopped").unstarted(stop);
            try {
                runtime.addShutdownHook(hook);
            } catch (IllegalStateException alreadyEnding) {
                // Told to stop before there was anything to hear it with. The program is ending
                // this moment; there is nothing to undo.
                return () -> { };
            }
            return () -> {
                try {
                    runtime.removeShutdownHook(hook);
                } catch (IllegalStateException alreadyEnding) {
                    // The program has begun to end, and the thread is running or has run: it is
                    // waiting for the very run that is undoing this, which is how it hears that the
                    // run is done. Nothing is left to undo.
                }
            };
        };
    }
}
