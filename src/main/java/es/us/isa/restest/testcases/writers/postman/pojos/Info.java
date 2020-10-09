
package es.us.isa.restest.testcases.writers.postman.pojos;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Info {

    private String _postman_id;
    private String name;
    private String schema;

    public Info() {
    }

    public Info(String _postman_id, String name, String schema) {
        this._postman_id = _postman_id;
        this.name = name;
        this.schema = schema;
    }

    public String get_postman_id() {
        return _postman_id;
    }

    public void set_postman_id(String _postman_id) {
        this._postman_id = _postman_id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Info info = (Info) o;
        return Objects.equals(_postman_id, info._postman_id) &&
                Objects.equals(name, info.name) &&
                Objects.equals(schema, info.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_postman_id, name, schema);
    }
}
