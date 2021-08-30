
package es.us.isa.restest.configuration.pojos;

import java.util.Map;

public class Auth {

    private Boolean required;
    private Map<String, String> queryParams = null;
    private Map<String, String> headerParams = null;
    private String apiKeysPath; // JSON file containing array of API keys (path relative to src/test/resources/auth/)
    private String headersPath; // JSON file containing array of auth headers (path relative to src/test/resources/auth/)
    private String oauthPath; // JSON file containing OAuth details (path relative to src/test/resources/auth/)

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(Map<String, String> queryParams) {
        this.queryParams = queryParams;
    }

    public Map<String, String> getHeaderParams() {
        return headerParams;
    }

    public void setHeaderParams(Map<String, String> headerParams) {
        this.headerParams = headerParams;
    }

    public String getApiKeysPath() {
        return apiKeysPath;
    }

    public void setApiKeysPath(String apiKeysPath) {
        this.apiKeysPath = apiKeysPath;
    }

    public String getHeadersPath() {
        return headersPath;
    }

    public void setHeadersPath(String headersPath) {
        this.headersPath = headersPath;
    }

    public String getOauthPath() {
        return oauthPath;
    }

    public void setOauthPath(String oauthPath) {
        this.oauthPath = oauthPath;
    }
}
