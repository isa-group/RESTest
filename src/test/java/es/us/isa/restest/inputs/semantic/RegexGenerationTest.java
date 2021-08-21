package es.us.isa.restest.inputs.semantic;

import it.units.inginf.male.outputs.FinalSolution;
import org.junit.Test;

import java.util.*;

import static es.us.isa.restest.inputs.semantic.regexGenerator.RegexGeneratorUtils.learnRegex;
import static org.junit.Assert.assertTrue;


public class RegexGenerationTest {

	@Test
	public void testRegexGeneration(){

		String name = "operationName_parameterName";

		Set<String> matches = new HashSet<>();
		matches.add("ES");
		matches.add("JA");
		matches.add("US");
		matches.add("DE");
		matches.add("AM");

		Set<String> unmatches = new HashSet<>();
		matches.add("AFG");
		matches.add("CYP");
		matches.add("HUN");
		matches.add("POL");
		matches.add("ZWE");

		FinalSolution solution = learnRegex(name, matches, unmatches, false);

		assertTrue("Error generating a regular expression", solution.getValidationPerformances().get("match recall")  >= 0.0);

	}


}
