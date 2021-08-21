
package es.us.isa.restest.configuration.pojos;

import java.util.List;

public class Generator {

    private String type;
    private List<GenParameter> genParameters = null;
    private Boolean valid = true; // Whether or not the generator generates valid data.

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<GenParameter> getGenParameters() {
        return genParameters;
    }

    public void setGenParameters(List<GenParameter> genParameters) {
        this.genParameters = genParameters;
    }

    public Boolean isValid() {
        return this.valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }

    public void addGenParameter(GenParameter genParameter){
        this.genParameters.add(genParameter);
    }

    public void removeGenParameter(GenParameter genParameter){
        this.genParameters.remove(genParameter);
    }

}
