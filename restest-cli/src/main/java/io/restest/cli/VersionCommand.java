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

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * The {@code restest version} command: says which RESTest this is, and which Java it is running on,
 * and tests nothing.
 *
 * <p>It prints exactly what {@code restest --version} prints, from the same {@link ToolVersion}. It
 * is there as a command of its own because that is where people look first - the list of commands
 * {@code restest --help} shows - and because it is the line somebody pastes at the top of a report
 * of something that went wrong.
 */
@Command(
        name = "version",
        description = "Says which RESTest this is, and which Java it is running on.",
        mixinStandardHelpOptions = true,
        versionProvider = ToolVersion.class)
final class VersionCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        for (String line : new ToolVersion().getVersion()) {
            spec.commandLine().getOut().println(line);
        }
        return ExitCode.NO_FAULTS;
    }
}
