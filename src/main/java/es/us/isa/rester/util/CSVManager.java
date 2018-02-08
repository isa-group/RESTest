package es.us.isa.rester.util;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class CSVManager {

	
	/**
	 * Returns a list with the values of the first column in the input CSV file
	 * @param path
	 * @return
	 */
	public static List<String> readValues(String path) {
		List<String> values = new ArrayList<String>();
		
		Reader in;
		try {
			in = new FileReader(path);
			
			Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(in);
			for (CSVRecord record : records)
			    values.add(record.get(0));

		}catch (IOException ex) {
			System.err.println("Error parsing CSV file: " + path + ". Message: " + ex.getMessage());
			ex.printStackTrace();
		}
		
		return values;
	}
}
