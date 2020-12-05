
package es.us.isa.restest.testcases.writers.postman.pojos;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Body {

    private String mode;
    private String raw;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Urlencoded> urlencoded = null;

    public Body() {
        this.urlencoded = new ArrayList<>();
    }

    public Body(String mode, String raw, List<Urlencoded> urlencoded) {
        this.mode = mode;
        this.raw = raw;
        this.urlencoded = urlencoded;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getRaw() {
        return raw;
    }

    public void setRaw(String raw) {
        this.raw = raw;
    }

    public List<Urlencoded> getUrlencoded() {
        return urlencoded;
    }

    public void setUrlencoded(List<Urlencoded> urlencoded) {
        this.urlencoded = urlencoded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Body body = (Body) o;
        return Objects.equals(mode, body.mode) &&
                Objects.equals(raw, body.raw) &&
                Objects.equals(urlencoded, body.urlencoded);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, raw, urlencoded);
    }
}
