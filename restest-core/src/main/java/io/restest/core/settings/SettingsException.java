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

import java.io.Serial;

/**
 * What is thrown when somebody asked for a setting that does not exist, or for a value it cannot
 * take.
 *
 * <p>Every one of these carries a message meant to be printed straight to whoever typed the thing
 * it is complaining about: which key, what was wrong with it, and - when the key was misspelt - the
 * real key it most looks like. Nothing catches one of these and carries on: a run configured in a
 * way nobody could work out is worse than a run that did not start.
 */
public final class SettingsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * A refusal to print as it is.
     *
     * @param message what to say to whoever typed it
     */
    public SettingsException(String message) {
        super(message);
    }
}
