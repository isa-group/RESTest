package com.atlassian.oai.validator.whitelist;

public enum StatusType {
    /**
     * All success (2xx) status codes
     */
    SUCCESS("2\\d\\d"),
    /**
     * All redirect (3xx) status codes
     */
    REDIRECTION("3\\d\\d"),
    /**
     * All client error (4xx) status codes
     */
    CLIENT_ERROR("4\\d\\d"),
    /**
     * All server error (5xx) status codes
     */
    SERVER_ERROR("5\\d\\d");

    private final String pattern;

    StatusType(final String pattern) {
        this.pattern = pattern;
    }

    public boolean matches(final int status) {
        return String.valueOf(status).matches(pattern);
    }

    @Override
    public String toString() {
        return pattern.replace("\\d", "x");
    }
}
