
package es.us.isa.restest.testcases.writers.postman.pojos;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Request {

    private String method;
    private List<Header> header = null;
    private Url url;
    private Body body;

    public Request() {
        this.header = new ArrayList<>();
    }

    public Request(String method, List<Header> header, Url url, Body body) {
        this.method = method;
        this.header = header;
        this.url = url;
        this.body = body;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<Header> getHeader() {
        return header;
    }

    public void setHeader(List<Header> header) {
        this.header = header;
    }

    public Url getUrl() {
        return url;
    }

    public void setUrl(Url url) {
        this.url = url;
    }

    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Request request = (Request) o;
        return Objects.equals(method, request.method) &&
                Objects.equals(header, request.header) &&
                Objects.equals(url, request.url) &&
                Objects.equals(body, request.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, header, url, body);
    }
}
