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
 * <p>The 100s are things an API does that HTTP itself forbids, the 200s are things it does that its
 * own specification forbids, and the 300s are security weaknesses. The 900s, which are not listed
 * here, are reserved by the catalogue for codes a tool defines for itself.
 *
 * <p>These numbers move between published versions of the catalogue, and have. A reader comparing
 * two fault counts has to read the version beside them before the numbers mean anything, which is
 * why every report carries it.
 */
public enum WfcFault implements FaultCategory {

    HTTP_STATUS_500(100, "HTTP Status 500", "causes500_internalServerError"),
    HTTP_STATUS_NO_NON_STANDARD_CODES(101, "HTTP Violation: no-non-standard-codes", "invalidStatusCode"),
    HTTP_STATUS_NO_201_IF_DELETE(102, "HTTP Violation: no-201-if-delete", "201OnDelete"),
    HTTP_STATUS_NO_201_IF_GET(103, "HTTP Violation: no-201-if-get", "201OnGet"),
    HTTP_STATUS_NO_201_IF_PATCH(104, "HTTP Violation: no-201-if-patch", "201OnPatch"),
    HTTP_STATUS_NO_204_IF_CONTENT(105, "HTTP Violation: no-204-if-content", "204WhenContent"),
    HTTP_STATUS_NO_413_IF_NO_PAYLOAD(106, "HTTP Violation: no-413-if-no-payload", "413WhenNoPayload"),
    HTTP_STATUS_NO_415_IF_NO_PAYLOAD(107, "HTTP Violation: no-415-if-no-payload", "415WhenNoPayload"),
    HTTP_STATUS_NO_304_IF_NO_GET_OR_HEAD(108, "HTTP Violation: no-304-if-no-get-or-head", "304OnWrongVerb"),
    HTTP_STATUS_NO_401_IF_NO_WWW_AUTHENTICATE(109,
            "HTTP Violation: no-401-if-no-authenticate",
            "401MissingWwwAuthenticate"),
    HTTP_STATUS_NO_405_IF_NO_ALLOW(110, "HTTP Violation: no-405-if-no-allow", "405MissingAllow"),
    HTTP_STATUS_NO_205_IF_CONTENT(111, "HTTP Violation: no-205-if-content", "205WhenContent"),
    HTTP_STATUS_NO_426_IF_NO_UPGRADE(112, "HTTP Violation: no-426-if-no-upgrade", "426MissingUpgrade"),
    HTTP_NONWORKING_DELETE(113,
            "HTTP Violation: Resource Still Accessible After Successful DELETE",
            "deleteDoesNotWork"),
    HTTP_SIDE_EFFECTS_FAILED_MODIFICATION(114,
            "HTTP Violation: A Failed PUT or PATCH Must Not Change The Resource",
            "sideEffectsFailedModification"),
    HTTP_REPEATED_CREATE_PUT(115,
            "HTTP Violation: Repeated PUT Creates Resource With 201 Instead of Updating",
            "repeatedCreatePut"),
    HTTP_MISLEADING_CREATE_PUT(116,
            "HTTP Violation: Misleading PUT 201 Creates When Resource Already Exists",
            "misleadingCreatePut"),
    HTTP_PARTIAL_UPDATE_PUT(117, "HTTP Violation: The Verb PUT Must Make a Full Replacement", "partialUpdatePut"),
    HTTP_NON_IDEMPOTENT_PUT(118, "HTTP Violation: PUT Implementation Must be Idempotent", "nonIdempotentPut"),
    HTTP_INVALID_MERGE_PATCH(119, "HTTP Violation: Invalid JSON Merge Patch", "invalidMergePatch"),
    HTTP_INVALID_LOCATION(120, "HTTP Violation: Invalid Location HTTP Header", "returnsInvalidLocationHeader"),
    SCHEMA_INVALID_RESPONSE(200,
            "Schema Violation: Received A Response From API With A Structure/Data That Is Not Matching Its Schema",
            "returnsMismatchResponseWithSchema"),
    SCHEMA_INVALID_ALLOW(201, "Schema Violation: Invalid Allow HTTP Header", "invalidAllow"),
    SCHEMA_STATUS_NO_401_IF_NO_AUTH(202, "Schema Violation: no-401-if-no-auth", "401WhenNoAuth"),
    SCHEMA_STATUS_NO_403_IF_NO_401(203, "Schema Violation: no-403-if-no-401", "403WhenNo401"),
    SCHEMA_STATUS_HAS_406_IF_ACCEPT(204, "Schema Violation: has-406-if-accept", "406WhenValid"),
    SCHEMA_STATUS_NO_501_IF_IMPLEMENTED(205, "Schema Violation: no-501-if-implemented", "501OnDeclaredEndpoint"),
    SCHEMA_VALIDATION_BYPASS(206, "Received Success Response When Sending Wrong Data", "successOnInvalidInputs"),
    SECURITY_SQL_INJECTION(300, "SQL Injection (SQLi)", "vulnerableToSQLInjection"),
    SECURITY_XSS(301, "Cross-Site Scripting (XSS)", "vulnerableToXSS"),
    SECURITY_SSRF(302, "Server-Side Request Forgery (SSRF)", "vulnerableToSSRF"),
    SECURITY_MASS_ASSIGNMENT(303, "Mass Assignment", "vulnerableToMassAssignment"),
    SECURITY_EXISTENCE_LEAKAGE(304,
            "Leakage Information Existence of Protected Resource",
            "allowsUnauthorizedAccessToProtectedResource"),
    SECURITY_NOT_RECOGNIZED_AUTHENTICATED(305,
            "Wrongly Not Recognized as Authenticated",
            "authenticatedButWronglyToldNot"),
    SECURITY_WRONG_AUTHORIZATION(306,
            "Allowed To Modify Resource That Likely Should Had Been Protected",
            "missedAuthorizationCheck"),
    SECURITY_IGNORE_ANONYMOUS(307,
            "A Protected Resource Is Accessible Without Providing Any Authentication",
            "ignoreAnonymous"),
    SECURITY_ANONYMOUS_MODIFICATIONS(308, "Anonymous Modifications", "anonymousModifications"),
    SECURITY_LEAKED_STACK_TRACES(309, "Leaked Stack Trace", "leakedStackTrace"),
    SECURITY_HIDDEN_ACCESSIBLE_ENDPOINT(310, "Hidden Accessible Endpoint", "hiddenAccessible");

    /**
     * Which published version of the catalogue this list was copied from. Reports say so, because a
     * fault code only means something next to the version of the list it was taken from.
     */
    public static final String CATALOGUE_VERSION = "0.9.0";

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
