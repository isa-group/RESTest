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

import picocli.CommandLine.IVersionProvider;

/**
 * Answers {@code restest --version}.
 *
 * <p>The version is read off the packaged file the program was started from, which is the same place
 * a run's report reads it when it records which RESTest produced it. One source, so the two can
 * never disagree - and comparing one milestone's results against another's is only possible if a
 * report can name the version that wrote it.
 *
 * <p>Started from loose compiled classes rather than a packaged file, there is nothing to read and
 * the answer is {@code unknown}. That is the honest answer, and it is also a hint: a report from
 * such a run says {@code unknown} too.
 */
final class ToolVersion implements IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[] {"RESTest " + describing()};
    }

    /** The version this copy of RESTest was built as, or {@code unknown}. */
    static String describing() {
        String version = ToolVersion.class.getPackage().getImplementationVersion();
        return version == null ? "unknown" : version;
    }
}
