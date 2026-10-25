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
package io.restest.report;

/**
 * What happened to one of the API's operations in a run, as a report says it beside the operation.
 *
 * <p>Four answers, kept apart because each tells a reader something different. An operation that
 * was tested and answered every request well is good news; one the run never got to is no news at
 * all, however clean it looks; one that could not be tested is something the tool could not do;
 * and one the plan left alone was left alone on purpose, which is neither of the last two.
 */
public enum OperationState {

    /** At least one request went to it. */
    TESTED("tested"),

    /**
     * It could have been tested, and no request went to it: the run ended first, or no request
     * could be built for it from what the document says.
     */
    NOT_REACHED("notReached"),

    /**
     * It was not tried: the document could not be read for it, or it asks for something RESTest
     * cannot send yet, such as a file upload. A report gives the reason beside it.
     */
    COULD_NOT_BE_TESTED("couldNotBeTested"),

    /** The plan asked the run to leave it alone. */
    LEFT_ALONE_BY_THE_PLAN("leftAloneByThePlan");

    private final String word;

    OperationState(String word) {
        this.word = word;
    }

    /** The word a report writes for it. */
    public String word() {
        return word;
    }
}
