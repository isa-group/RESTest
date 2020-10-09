
package es.us.isa.restest.testcases.writers.postman.pojos;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Url {

    private String raw;
    private String protocol;
    private List<String> host = null;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> path = null;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Query> query = null;

    public Url() {
        this.host = new ArrayList<>();
        this.path = new ArrayList<>();
        this.query = new ArrayList<>();
    }

    public Url(String raw, String protocol, List<String> host, List<String> path, List<Query> query) {
        this.raw = raw;
        this.protocol = protocol;
        this.host = host;
        this.path = path;
        this.query = query;
    }

    public String getRaw() {
        return raw;
    }

    public void setRaw(String raw) {
        this.raw = raw;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public List<String> getHost() {
        return host;
    }

    public void setHost(List<String> host) {
        this.host = host;
    }

    public List<String> getPath() {
        return path;
    }

    public void setPath(List<String> path) {
        this.path = path;
    }

    public List<Query> getQuery() {
        return query;
    }

    public void setQuery(List<Query> query) {
        this.query = query;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Url url = (Url) o;
        return Objects.equals(raw, url.raw) &&
                Objects.equals(protocol, url.protocol) &&
                Objects.equals(host, url.host) &&
                Objects.equals(path, url.path) &&
                Objects.equals(query, url.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw, protocol, host, path, query);
    }
}
