
package es.us.isa.restest.testcases.writers.postman.pojos;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Query {

    private String key;
    private String value;

    public Query() {
    }

    public Query(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Query query = (Query) o;
        return Objects.equals(key, query.key) &&
                Objects.equals(value, query.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }
}
