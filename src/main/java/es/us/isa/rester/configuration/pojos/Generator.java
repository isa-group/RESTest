
package es.us.isa.rester.configuration.pojos;

import java.util.List;

public class Generator {

    private String type;
    private List<GenParameter> genParameters = null;

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

}
