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
package io.restest.core.event;

/**
 * Something that wants to be told what a run is doing.
 *
 * <p>A report printing to the screen, a file being written, a count being kept, an oracle judging
 * each attempt: all of them are one of these, and none of them knows about the others. Whatever a
 * listener does happens away from the sending of requests, so a slow listener delays the report,
 * not the run.
 *
 * <p>A listener is called one event at a time, in the order the events happened, and always on the
 * same thread, so it may keep a running total in an ordinary field without any locking of its own.
 *
 * <p>A listener that throws is not allowed to take the run down with it. The failure is counted and
 * the remaining listeners still hear the event.
 */
@FunctionalInterface
public interface RunListener {

    /** Called once for every event, in the order they happened. */
    void on(RunEvent event);
}
