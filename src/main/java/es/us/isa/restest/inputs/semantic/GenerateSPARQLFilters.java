package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.configuration.pojos.TestParameter;

import java.util.List;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.*;

public class GenerateSPARQLFilters {

    public static String generateSPARQLFilters(TestParameter parameter){
        // TODO: Important refactorization required (semantic parameter and Random_input_value)
        String res = "";
//        Generator generator = parameter.getGenerators().stream().filter(x-> x.getType().equals(SEMANTIC_PARAMETER)).findFirst().get(); OLD
        Generator generator = parameter.getGenerators().stream()
                .filter(x-> x.getType().equals(SEMANTIC_PARAMETER) || (  x.getType().equals(RANDOM_INPUT_VALUE) && x.isValid()==true  ))
                .findFirst().get();

        List<GenParameter> genParameters = generator.getGenParameters();

        for(GenParameter genParameter: genParameters){
            switch (genParameter.getName()){
                case GEN_PARAM_REG_EXP:
                    res = res + generateSPARQLFilterRegExp(parameter.getName(), genParameter.getValues().get(0));
                    break;
                case GEN_PARAM_MIN:
                    res = res + generateSPARQLFilterMinMax(parameter.getName(), genParameter.getValues().get(0), true);
                    break;
                case GEN_PARAM_MAX:
                    res = res + generateSPARQLFilterMinMax(parameter.getName(), genParameter.getValues().get(0), false);
                    break;
            }
        }

        return res;
    }

    private static String generateSPARQLFilterRegExp(String parameterName, String regexp){
        String modifiedRegex = regexp.replace("\\", "\\\\");
        String res = "\tFILTER regex(str(?" + parameterName + "), " + " \"" + modifiedRegex + "\")\n";

        return res;
    }



    private static String generateSPARQLFilterMinMax(String parameterName, String number, Boolean isMin){

        String res = "";

        if(isMin){
            res = res + "\tFILTER (?" + parameterName + " >= " + number + " )\n" ;
        }else{
            res = res + "\tFILTER (?" + parameterName + " <= " + number + " )\n" ;
        }

        return res;
    }

}
