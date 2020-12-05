
package es.us.isa.restest.testcases.writers.postman.pojos;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostmanCollectionObject {

    private Info info;
    private List<Item> item = null;

    public PostmanCollectionObject() {
        this.item = new ArrayList<>();
    }

    public PostmanCollectionObject(Info info, List<Item> item) {
        this.info = info;
        this.item = item;
    }

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

    public List<Item> getItem() {
        return item;
    }

    public void setItem(List<Item> item) {
        this.item = item;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostmanCollectionObject that = (PostmanCollectionObject) o;
        return Objects.equals(info, that.info) &&
                Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(info, item);
    }
}
