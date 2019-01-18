package es.us.isa.rester.inputs;

import es.us.isa.rester.configuration.pojos.GenParameter;
import es.us.isa.rester.configuration.pojos.Generator;
import es.us.isa.rester.inputs.random.*;
import es.us.isa.rester.util.CSVManager;
import es.us.isa.rester.util.DataType;
import es.us.isa.rester.util.TestConfigurationVisitor;

public class TestDataGeneratorFactory {

	/**
	 * Create a test data generator based on the data from the test configuration file in JSON
	 * @param generator
	 * @return a test data generator
	 */
	public static ITestDataGenerator createTestDataGenerator(Generator generator) {
		
		ITestDataGenerator gen=null;
		
		switch(generator.getType()) {

			case "InputValue":
				gen = createInputValueIterator(generator);
				break;
			case "RandomInputValue":
				gen = createRandomInputValueGenerator(generator);
				break;
			case "RandomEnglishWord":
				gen = createRandomEnglishWordGenerator(generator);
				break;
			case "RandomNumber":
				gen = createRandomNumber(generator);
				break;
			case "RandomDate":
				gen = createRandomDate(generator);
				break;
			case "RandomRegExp":
				gen = createRandomRegExpGenerator(generator);
				break;
			case "RandomBoolean":
				gen = createRandomBooleanGenerator(generator);
				break;
			case "RandomObject":
				gen = createRandomObjectGenerator(generator);
				break;
			case "RandomString":
				gen = createRandomStringGenerator(generator);
				break;
			default:
				throw new IllegalArgumentException("Unexpected parameter for generator TestDataGenerator factory: " + generator.getType());
		}
		
		return gen;
	}


	// Create a random date generator
	private static ITestDataGenerator createRandomDate(Generator generator) {
		
		RandomDateGenerator gen = new RandomDateGenerator();
		
		for(GenParameter param: generator.getGenParameters()) {
			switch (param.getName()) {
			
			case "startDate":
				gen.setStartDate(param.getValues().get(0));
				break;
			case "endDate":
				gen.setEndDate(param.getValues().get(0));
				break;
			case "fromToday":
				gen.setFromToday(Boolean.parseBoolean(param.getValues().get(0)));
				break;
			case "startDays":
				gen.setStartDays(Integer.parseInt(param.getValues().get(0)));
				break;
			case "endDays":
				gen.setEndDays(Integer.parseInt(param.getValues().get(0)));
				break;
			case "format":
				gen.setFormat(param.getValues().get(0));
				break;
			default:
				throw new IllegalArgumentException("Unexpected parameter for random date generator: " + param.getName());
			}	
		}
		
		return gen;
	}


	// Create a random word generator
	private static ITestDataGenerator createRandomEnglishWordGenerator(Generator generator) {
		RandomEnglishWordGenerator gen = new RandomEnglishWordGenerator();
		
		for(GenParameter param: generator.getGenParameters()) {
			switch (param.getName()) {
			
			case "minWords":
				gen.setMinWords(Integer.parseInt(param.getValues().get(0)));
				break;
			case "maxWords":
				gen.setMaxWords(Integer.parseInt(param.getValues().get(0)));
				break;
			default:
				throw new IllegalArgumentException("Unexpected parameter for random English word generator: " + param.getName());
			}	
		}
		
		return gen;
	}


	private static ITestDataGenerator createRandomInputValueGenerator(Generator generator) {
		
		RandomInputValueIterator<String> gen = new RandomInputValueIterator<>();
		
		// Set parameters
		for(GenParameter param: generator.getGenParameters()) {
			switch (param.getName()) {
			
			case "values":
				gen.setValues(param.getValues());
				break;
			case "csv":
				gen.setValues(CSVManager.readValues(param.getValues().get(0)));
				break;
			default:
				throw new IllegalArgumentException("Unexpected parameter for generator RandomInputValue: " + param.getName());
			}	
		}
		
		return gen;
	}

	private static ITestDataGenerator createInputValueIterator(Generator generator) {

		InputValueIterator<String> gen = null;

		// Set parameters
		for(GenParameter param: generator.getGenParameters()) {
			switch (param.getName()) {

				case "values":
					gen = new InputValueIterator<>(param.getValues());
					break;
				default:
					throw new IllegalArgumentException("Unexpected parameter for InputValueIterator: " + param.getName());
			}
		}

		return gen;
	}
	
	private static ITestDataGenerator createRandomNumber(Generator generator) {
		
		RandomNumberGenerator gen = null;
		
		GenParameter typeParam = TestConfigurationVisitor.searchGenParameter("type",generator.getGenParameters());
		if (typeParam==null || typeParam.getValues().get(0) == null) 
			throw new IllegalArgumentException("Missing number type");
		
		switch (typeParam.getValues().get(0)) {
		case "integer":
			gen = createRandomIntegerGenerator(DataType.INTEGER,generator);
			break;
		case "int32":
			gen = createRandomIntegerGenerator(DataType.INT32,generator);
			break;
		case "int64":
			gen = createRandomIntegerGenerator(DataType.INT64,generator);
			break;
		case "double":
			gen = createRandomDoubleGenerator(DataType.DOUBLE,generator);
			break;
		case "number":
			gen = createRandomDoubleGenerator(DataType.NUMBER,generator);
			break;	
		case "long":
			gen = createRandomLongGenerator(DataType.LONG,generator);
			break;
		case "float":
			gen = createRandomFloatGenerator(DataType.FLOAT,generator);
			break;	
		default:
			throw new IllegalArgumentException("Wrong number type value" + typeParam.getValues().get(0));
		}	
		
		
		return gen;
	}

	
	// Create a random float generator
	private static RandomNumberGenerator createRandomFloatGenerator(DataType type, Generator generator) {
	RandomNumberGenerator gen = new RandomNumberGenerator(type);
		
		// Set parameters
		for(GenParameter param: generator.getGenParameters()) {
			switch (param.getName()) {
			
			case "min":
				gen.setMin(Float.parseFloat(param.getValues().get(0)));
				break;
			case "max":
				gen.setMax(Float.parseFloat(param.getValues().get(0)));
				break;
			case "type":
				// ignore
				break;
			default:
				throw new IllegalArgumentException("Unexpected parameter for random float generator: " + param.getName());
			}	
		}
		
		return gen;
	}



	// Create a random long generator
	private static RandomNumberGenerator createRandomLongGenerator(DataType type, Generator generator) {
		RandomNumberGenerator gen = new RandomNumberGenerator(type);
		
		// Set parameters
		for(GenParameter param: generator.getGenParameters()) {
			switch (param.getName()) {
			
			case "min":
				gen.setMin(Long.parseLong(param.getValues().get(0)));
				break;
			case "max":
				gen.setMax(Long.parseLong(param.getValues().get(0)));
				break;
			case "type":
				// ignore
				break;
			default:
				throw new IllegalArgumentException("Unexpected parameter for random long generator: " + param.getName());
			}	
		}
		
		return gen;
	}



	// Create a random double generator
	private static RandomNumberGenerator createRandomDoubleGenerator(DataType doubleType, Generator generator) {
		RandomNumberGenerator gen = new RandomNumberGenerator(doubleType);
		
		// Set parameters
		for(GenParameter param: generator.getGenParameters()) {
			switch (param.getName()) {
			
			case "min":
				gen.setMin(Double.parseDouble(param.getValues().get(0)));
				break;
			case "max":
				gen.setMax(Double.parseDouble(param.getValues().get(0)));
				break;
			case "type":
				// ignore
				break;
			default:
				throw new IllegalArgumentException("Unexpected parameter for random double generator: " + param.getName());
			}	
		}
		
		return gen;
	}


	// Create a random integer generator
	private static RandomNumberGenerator createRandomIntegerGenerator(DataType intType, Generator generator) {
		RandomNumberGenerator gen = new RandomNumberGenerator(intType);
		
		// Set parameters
		for(GenParameter param: generator.getGenParameters()) {
			switch (param.getName()) {
			
			case "min":
				gen.setMin(Integer.parseInt(param.getValues().get(0)));
				break;
			case "max":
				gen.setMax(Integer.parseInt(param.getValues().get(0)));
				break;
			case "type":
				// ignore
				break;
			default:
				throw new IllegalArgumentException("Unexpected parameter for random integer generator: " + param.getName());
			}	
		}
		
		return gen;
	}

	// Create a random regexp generator
	private static RandomRegExpGenerator createRandomRegExpGenerator(Generator generator) {
		RandomRegExpGenerator gen = null;

		GenParameter regExpParam = TestConfigurationVisitor.searchGenParameter("regExp",generator.getGenParameters());
		if (regExpParam==null || regExpParam.getValues().get(0) == null)
			throw new IllegalArgumentException("Missing regular expression parameter");
		else
			gen = new RandomRegExpGenerator(regExpParam.getValues().get(0));

		// Set parameters
		for(GenParameter param: generator.getGenParameters()) {
			switch (param.getName()) {
				case "minLength":
					gen.setMinLength(Integer.parseInt(param.getValues().get(0)));
					break;
				case "maxLength":
					gen.setMaxLength(Integer.parseInt(param.getValues().get(0)));
					break;
				default:
					throw new IllegalArgumentException("Unexpected parameter for random regExp generator: " + param.getName());
			}
		}

		return gen;
	}

	// Create a random boolean generator
	private static RandomBooleanGenerator createRandomBooleanGenerator(Generator generator) {
		RandomBooleanGenerator gen = new RandomBooleanGenerator();

		// Set parameters
		for(GenParameter param: generator.getGenParameters()) {
			switch (param.getName()) {

				case "trueProbability":
					gen.setTrueProbability(Double.parseDouble(param.getValues().get(0)));
					break;
				default:
					throw new IllegalArgumentException("Unexpected parameter for random boolean generator: " + param.getName());
			}
		}

		return gen;
	}

	// Create a random object generator
	private static RandomObjectGenerator createRandomObjectGenerator(Generator generator) {
		RandomObjectGenerator gen = new RandomObjectGenerator();

		// Set parameters
		for(GenParameter param: generator.getGenParameters()) {
			switch (param.getName()) {

				case "values":
					gen.setValues(param.getObjectValues());
					break;
				default:
					throw new IllegalArgumentException("Unexpected parameter for generator RandomInputValue: " + param.getName());
			}
		}
		return gen;
	}

	// Create a random string generator
	private static RandomStringGenerator createRandomStringGenerator(Generator generator) {
		RandomStringGenerator gen = new RandomStringGenerator();

		// Set parameters
		for(GenParameter param: generator.getGenParameters()) {
			switch (param.getName()) {

				case "minLength":
					gen.setMinLength(Integer.parseInt(param.getValues().get(0)));
					break;
				case "maxLength":
					gen.setMaxLength(Integer.parseInt(param.getValues().get(0)));
					break;
				case "includeSpecialCharacters":
					gen.setIncludeSpecialCharacters(Boolean.parseBoolean(param.getValues().get(0)));
					break;
				case "includeNumbers":
					gen.setIncludeNumbers(Boolean.parseBoolean(param.getValues().get(0)));
					break;
				case "includeAlphabetic":
					gen.setIncludeAlphabetic(Boolean.parseBoolean(param.getValues().get(0)));
					break;
				default:
					throw new IllegalArgumentException("Unexpected parameter for random string generator: " + param.getName());
			}
		}

		return gen;
	}
}
