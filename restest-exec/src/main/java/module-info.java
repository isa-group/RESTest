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

/**
 * The HTTP engine: the part of RESTest that actually talks to the API under test.
 *
 * <p>Everything else in the tool works with records describing a request and a reply. This module is
 * where those become a real conversation over a socket, and where what came back becomes a record
 * again. It is the only module that opens a network connection.
 *
 * <p>The HTTP client is declared as {@code okhttp-jvm} rather than {@code okhttp}. In version 5 the
 * plainly named artifact holds no Java classes at all - it describes a library built for several
 * platforms - and the classes this module compiles against live in the {@code -jvm} one. That
 * artifact names itself {@code okhttp3}, which is why the clause below reads as it does.
 */
module io.restest.exec {
    requires io.restest.core;
    requires okhttp3;

    exports io.restest.exec;

    provides io.restest.core.exec.HttpEngine with io.restest.exec.OkHttpEngine;
}
