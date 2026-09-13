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

import java.util.Optional;

/**
 * The published list of fault kinds RESTest can report, copied from the Web Fuzzing Commons
 * catalogue.
 *
 * <p>Every entry here is somebody else's wording, not ours, and that is the point. The code, the
 * name, and the name a generated test would carry, are what let a result from RESTest be compared
 * with a result from another tool that uses the same catalogue. A test checks this list against the
 * catalogue's own published file, so the day the two disagree the build says so rather than the
 * comparison quietly stopping being true.
 *
 * <p>The whole catalogue is listed, not only the parts RESTest can currently find. Which faults the
 * tool looks for is decided by which oracles are switched on, and that grows over time; the numbers
 * themselves do not belong to RESTest and are not ours to trim.
 *
 * <p>The 100s are faults any API can have and the 200s are security weaknesses. The 900s, which are
 * not listed here, are reserved by the catalogue for codes a tool defines for itself.
 */
public enum WfcFault implements FaultCategory {

    HTTP_STATUS_500(100, "HTTP Status 500",
            "causes500_internalServerError"),
    SCHEMA_INVALID_RESPONSE(101,
            "Received A Response From API With A Structure/Data That Is Not Matching Its Schema",
            "returnsMismatchResponseWithSchema"),
    SCHEMA_VALIDATION_BYPASS(102, "Received Success Response When Sending Wrong Data",
            "successOnInvalidInputs"),
    DELETE_NOT_WORKING(103, "Resource Still Accessible After Being Deleted",
            "deleteNotWorking"),
    FAILED_CREATION_SIDE_EFFECTS(104, "Failed Creation of Resource Has Side Effects on Backend",
            "sideEffectsOnFailedCreation"),
    SQL_INJECTION(200, "SQL Injection (SQLi)",
            "vulnerableToSQLInjection"),
    XSS(201, "Cross-Site Scripting (XSS)",
            "vulnerableToXSS"),
    SSRF(202, "Server-Side Request Forgery (SSRF)",
            "vulnerableToSSRF"),
    MASS_ASSIGNMENT(203, "Mass Assignment",
            "vulnerableToMassAssignment"),
    SECURITY_EXISTENCE_LEAKAGE(204, "Leakage Information Existence of Protected Resource",
            "allowsUnauthorizedAccessToProtectedResource"),
    SECURITY_NOT_RECOGNIZED_AUTHENTICATED(205, "Wrongly Not Recognized as Authenticated",
            "authenticatedButWronglyToldNot"),
    SECURITY_WRONG_AUTHORIZATION(206,
            "Allowed To Modify Resource That Likely Should Had Been Protected",
            "missedAuthorizationCheck"),
    SECURITY_IGNORE_ANONYMOUS(207,
            "A Protected Resource Is Accessible Without Providing Any Authentication",
            "ignoreAnonymous"),
    SECURITY_ANONYMOUS_MODIFICATIONS(208, "Anonymous Modifications",
            "anonymousModifications"),
    SECURITY_LEAKED_STACK_TRACES(209, "Leaked Stack Trace",
            "leakedStackTrace"),
    SECURITY_HIDDEN_ACCESSIBLE_ENDPOINT(210, "Hidden Accessible Endpoint",
            "hiddenAccessible");

    /**
     * Which published version of the catalogue this list was copied from. Reports say so, because a
     * fault code only means something next to the version of the list it was taken from.
     */
    public static final String CATALOGUE_VERSION = "0.7.0";

    /** What the catalogue is called, as its own authors write it. */
    public static final String CATALOGUE_NAME = "Web Fuzzing Commons";

    private final int code;
    private final String descriptiveName;
    private final String testCaseLabel;

    WfcFault(int code, String descriptiveName, String testCaseLabel) {
        this.code = code;
        this.descriptiveName = descriptiveName;
        this.testCaseLabel = testCaseLabel;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String descriptiveName() {
        return descriptiveName;
    }

    @Override
    public String testCaseLabel() {
        return testCaseLabel;
    }

    /** The category a number names, or nothing if the catalogue has no such number. */
    public static Optional<WfcFault> byCode(int code) {
        for (WfcFault fault : values()) {
            if (fault.code == code) {
                return Optional.of(fault);
            }
        }
        return Optional.empty();
    }
}
