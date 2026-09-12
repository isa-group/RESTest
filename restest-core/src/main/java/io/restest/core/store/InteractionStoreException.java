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
package io.restest.core.store;

/**
 * Thrown when the evidence of a run cannot be written down or read back: the disk is full, the file
 * is not where it was, the database is corrupt.
 *
 * <p>Almost nothing else in RESTest throws. A specification that cannot be read and an API that
 * refuses to answer are the tool's subject matter, and both are reported rather than raised. This is
 * the exception to that, and deliberately so: a run whose evidence is being silently dropped is
 * producing nothing of value, and carrying on quietly would waste the whole budget before anyone
 * noticed.
 */
public class InteractionStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InteractionStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public InteractionStoreException(String message) {
        super(message);
    }
}
