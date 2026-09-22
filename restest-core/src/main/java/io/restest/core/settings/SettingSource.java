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
package io.restest.core.settings;

/**
 * Where one setting's value came from.
 *
 * <p>A run is configured from four places at once, and the later ones win: what the tool does when
 * nobody has said otherwise, then a file somebody named, then the environment the tool was started
 * in, then what was typed on the command line. When a run behaves unexpectedly the first question
 * is which of the four decided a particular number, so every run says so - on the screen when asked,
 * and in the report it writes, whether asked or not.
 *
 * <p>There is a fifth answer, and it is the honest one for a value nobody gave: some settings are
 * places inside a range, and moving the range moves them. Those say so rather than claiming to be
 * what the tool does by default, because they are not.
 */
public enum SettingSource {

    /** Nobody said otherwise, so this is what RESTest does. */
    DEFAULT("default"),

    /**
     * Nobody named this one either, but it is not what RESTest does by default: it was worked out
     * from a setting that <em>was</em> named.
     *
     * <p>Where the engine starts is the one that does this today. It is a place inside the range of
     * how many requests may be in flight, so asking for one request at a time moves it to one
     * without anybody mentioning it. Calling that a default would put two different values under
     * the same word in two results directories, with nothing in either to explain the difference.
     */
    WORKED_OUT("worked out"),

    /** A file named with {@code --settings}. */
    FILE("file"),

    /** An environment variable, {@code RESTEST_GROUP_KEY}. */
    ENVIRONMENT("environment"),

    /** Typed after {@code --set}. */
    COMMAND_LINE("command line");

    private final String written;

    SettingSource(String written) {
        this.written = written;
    }

    /** What this is called in what a person reads and in what a program reads. */
    public String written() {
        return written;
    }
}
