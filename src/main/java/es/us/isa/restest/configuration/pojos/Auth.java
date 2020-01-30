
package es.us.isa.restest.configuration.pojos;

import java.util.List;

public class Auth {

    private Boolean required;
    private List<QueryParam> queryParams = null;
    private List<HeaderParam> headerParams = null;
    private String apiKeysPath; // JSON file containing array of API keys (path relative to src/main/resources/auth/)

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public List<QueryParam> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(List<QueryParam> queryParams) {
        this.queryParams = queryParams;
    }

    public List<HeaderParam> getHeaderParams() {
        return headerParams;
    }

    public void setHeaderParams(List<HeaderParam> headerParams) {
        this.headerParams = headerParams;
    }

    public String getApiKeysPath() {
        return apiKeysPath;
    }

    public void setApiKeysPath(String apiKeysPath) {
        this.apiKeysPath = apiKeysPath;
    }


}
