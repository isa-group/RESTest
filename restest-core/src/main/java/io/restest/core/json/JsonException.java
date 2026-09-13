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
package io.restest.core.json;

/**
 * Thrown when text will not turn into a JSON value, or a JSON value will not turn into text.
 *
 * <p> This is about the writing itself, not about anything an API said. Text that RESTest wrote and
 * cannot read back means a stored run has been damaged; a value that cannot be written means the
 * run being saved is incomplete. Both are worth stopping for, which is why they are raised rather
 * than reported.
 *
 * <p> Whoever catches this usually knows something the reader did not - which stored interaction
 * the damaged text belonged to, say - and is expected to say so while passing the problem on.
 */
public class JsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }

    public JsonException(String message) {
        super(message);
    }
}
