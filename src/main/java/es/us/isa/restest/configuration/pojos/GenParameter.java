
package es.us.isa.restest.configuration.pojos;

import java.util.List;

public class GenParameter {

    private String name;
    private List<String> values = null;
    private List<Object> objectValues = null;

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

    public List<Object> getObjectValues() {
        return objectValues;
    }

    public void setObjectValues(List<Object> objectValues) {
        this.objectValues = objectValues;
    }

}
