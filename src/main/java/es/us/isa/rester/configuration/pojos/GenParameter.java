
package es.us.isa.rester.configuration.pojos;

import java.util.List;

public class GenParameter {

    private String name;
    private List<String> values = null;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

}
