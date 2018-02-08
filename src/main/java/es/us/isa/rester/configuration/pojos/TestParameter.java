
package es.us.isa.rester.configuration.pojos;


public class TestParameter {

    private String name;
    private Generator generator;
    private Boolean filter;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Generator getGenerator() {
        return generator;
    }

    public void setGenerator(Generator generator) {
        this.generator = generator;
    }

    public Boolean getFilter() {
        return filter;
    }

    public void setFilter(Boolean filter) {
        this.filter = filter;
    }

}
