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
package io.restest.core.oracle;

/**
 * The kinds of fault RESTest reports that the shared catalogue has no number for, numbered by
 * RESTest itself.
 *
 * <p>Most of what RESTest finds wrong with an API has a number in the published catalogue,
 * {@link WfcFault}, so that a count from RESTest can be set beside a count from another tool. A few
 * things worth telling a developer have none: an API answering {@code 201} where its document
 * promises only {@code 200}, or sending its errors as {@code application/problem+json} where the
 * document says {@code application/json}. The catalogue leaves the numbers from 900 to 999 to each
 * tool for kinds like these, and this is where RESTest keeps its own.
 *
 * <p>The names and labels here are RESTest's own words, not the catalogue's, and the key of each
 * starts with {@code RESTEST_}, so that nobody mistakes one of these for a kind every tool agrees on.
 * Another tool may count the same disagreement under a number of the catalogue's, or under a number
 * of its own in the same range that means something else, so a count of these compares only with a
 * count from RESTest.
 */
public enum RestestFault implements FaultCategory {

    UNDECLARED_STATUS_CODE(920, "A Status Code The Document Does Not Declare",
            "returnsUndeclaredStatusCode"),
    UNDECLARED_CONTENT_TYPE(921, "A Content Type The Document Does Not Declare",
            "returnsUndeclaredContentType");

    private final int code;
    private final String descriptiveName;
    private final String testCaseLabel;

    RestestFault(int code, String descriptiveName, String testCaseLabel) {
        this.code = code;
        this.descriptiveName = descriptiveName;
        this.testCaseLabel = testCaseLabel;
    }

    @Override
    public int code() {
        return code;
    }

    /**
     * The key of this kind, in words rather than its number: {@code RESTEST_} followed by the name
     * of the entry, as the catalogue's own keys name what they mean.
     */
    @Override
    public String id() {
        return "RESTEST_" + name();
    }

    @Override
    public String descriptiveName() {
        return descriptiveName;
    }

    @Override
    public String testCaseLabel() {
        return testCaseLabel;
    }
}
