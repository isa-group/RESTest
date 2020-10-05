package es.us.isa.restest.inputs.semantic;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

public class GenerateSPARQLFilters {

    public static String generateSPARQLFilters(Parameter parameter){
        String res = "";
        switch (parameter.getSchema().getType()){
            case "string":
                //TODO
                res = generateSPARQLFiltersString(parameter);
                break;
            case "number":      // Combined with integer
            case "integer":
                res = generateSPARQLFiltersNumber(parameter);
                break;
            case "boolean":
                // TODO
                break;
            case "array":
                // TODO
                break;
            case "object":
                // TODO
                break;
        }




        return res;
    }

    private static String generateSPARQLFiltersString(Parameter parameter){
        String res = "";
        Schema schema = parameter.getSchema();

        if(schema.getPattern() != null){
            res = res + "\tFILTER regex(str(?" + parameter.getName() + "), " + " \"" + schema.getPattern() + "\")\n";
        }
        return res;
    }

    private static String generateSPARQLFiltersNumber(Parameter parameter){
        String res = "";
        Schema schema = parameter.getSchema();

        // TODO: < or <= ?
        if(schema.getMinimum() != null){
            res = res + "\tFILTER (?" + parameter.getName() + " >= " + schema.getMinimum() + " )\n" ;
        }

        if(schema.getMaximum() != null){
            res = res + "\tFILTER (?" + parameter.getName() + " <= " + schema.getMaximum() + " )\n" ;
        }

        return res;
    }

}
