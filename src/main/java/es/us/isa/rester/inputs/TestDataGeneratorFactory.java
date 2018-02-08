package es.us.isa.rester.inputs;

import java.util.Iterator;
import java.util.List;
import es.us.isa.rester.configuration.pojos.GenParameter;
import es.us.isa.rester.configuration.pojos.Generator;
import es.us.isa.rester.inputs.random.RandomEnglishWordGenerator;
import es.us.isa.rester.inputs.random.RandomInputValueIterator;
import es.us.isa.rester.inputs.random.RandomNumberGenerator;
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
			// TODO
			break;	
		default:
			throw new IllegalArgumentException("Unexpected parameter for generator TestDataGenerator factory: " + generator.getType());
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
		
		RandomInputValueIterator<String> gen = new RandomInputValueIterator<String>();
		
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
	
	private static ITestDataGenerator createRandomNumber(Generator generator) {
		
		RandomNumberGenerator gen = null;
		
		GenParameter typeParam = TestConfigurationVisitor.searchGenParameter("type",generator.getGenParameters());
		if (typeParam==null || typeParam.getValues().get(0) == null) 
			throw new IllegalArgumentException("Missing number type");
		
		switch (typeParam.getValues().get(0)) {
		case "integer":
			gen = createRandomIntengerGenerator(DataType.INTEGER,generator);
			break;
		case "int32":
			gen = createRandomIntengerGenerator(DataType.INT32,generator);
			break;
		case "int64":
			gen = createRandomIntengerGenerator(DataType.INT64,generator);
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
	private static RandomNumberGenerator createRandomIntengerGenerator(DataType intType, Generator generator) {
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
}
