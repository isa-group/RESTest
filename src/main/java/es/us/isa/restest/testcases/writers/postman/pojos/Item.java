
package es.us.isa.restest.testcases.writers.postman.pojos;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Item {

    private String name;
    private Request request;
    private List<Object> response = null;

    public Item() {
        this.response = new ArrayList<>();
    }

    public Item(String name, Request request, List<Object> response) {
        this.name = name;
        this.request = request;
        this.response = response;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Request getRequest() {
        return request;
    }

    public void setRequest(Request request) {
        this.request = request;
    }

    public List<Object> getResponse() {
        return response;
    }

    public void setResponse(List<Object> response) {
        this.response = response;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return Objects.equals(name, item.name) &&
                Objects.equals(request, item.request) &&
                Objects.equals(response, item.response);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, request, response);
    }
}
