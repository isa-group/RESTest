package es.us.isa.restest.util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
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

	public static void createFileWithHeader(String path, String header) {
		File csvFile = new File(path);
		csvFile.delete(); // delete file if it exists
		try {
			csvFile.createNewFile();
			FileOutputStream oCsvFile = new FileOutputStream(csvFile, true);
			header += "\n";
			oCsvFile.write(header.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void writeRow(String path, String row) {
		File csvFile = new File(path);
		try {
			FileOutputStream oCsvFile = new FileOutputStream(csvFile, true);
			row += "\n";
			oCsvFile.write(row.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
