package es.us.isa.restest.util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import es.us.isa.restest.runners.RESTestRunner;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static es.us.isa.restest.util.FileManager.createFileIfNotExists;
import static es.us.isa.restest.util.FileManager.deleteFile;

public class CSVManager {

	private static final Logger logger = LogManager.getLogger(CSVManager.class.getName());
	
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

	/**
	 * Returns a list with the values of all rows (including header, if any)
	 * of the input CSV file. Each row is a list of strings (one element per field)
	 * @param path
	 * @param delimiter
	 * @return
	 */
	public static List<List<String>> readCSV(String path, char delimiter) {
		List<List<String>> rows = new ArrayList<>();

		Reader in;
		try {
			in = new FileReader(path);
			Iterable<CSVRecord> records = CSVFormat.EXCEL.withDelimiter(delimiter).parse(in);
			for (CSVRecord record : records) {
				List<String> currentRow = new ArrayList<>();
				for (String field: record)
					currentRow.add(field);
				rows.add(currentRow);
			}
		} catch (IOException ex) {
			System.err.println("Error parsing CSV file: " + path + ". Message: " + ex.getMessage());
			ex.printStackTrace();
		}

		return rows;
	}

	/**
	 * Returns a list with the values of all rows of the input CSV file. Each row
	 * is a list of strings (one element per field)
	 * @param path
	 * @param includeFirstRow Whether to include first row of the CSV in the result
	 *                        or not. Useful for excluding header.
	 * @return
	 */
	public static List<List<String>> readCSV(String path, Boolean includeFirstRow) {
		List<List<String>> rows = readCSV(path, ',');
		if (!includeFirstRow)
			rows.remove(0);
		return rows;
	}

	/**
	 * Call {@link #readCSV(String path, char delimiter)} with delimiter=','
	 */
	public static List<List<String>> readCSV(String path) {
		return readCSV(path, ',');
	}

	/**
	 * Create a new CSV file in the given path and with the given header.
	 * @param path Path where to place the file. Parent folders must be already created
	 * @param header Header to add to the first line. If null, no header will be added
	 */
	public static void createCSVwithHeader(String path, String header) {
		deleteFile(path); // delete file if it exists
		createFileIfNotExists(path);
		writeCSVRow(path, header);
	}

	public static void writeCSVRow(String path, String row) {
		File csvFile = new File(path);
		try(FileOutputStream oCsvFile = new FileOutputStream(csvFile, true)) {
			row += "\n";
			oCsvFile.write(row.getBytes());
		} catch (IOException e) {
			logger.error("The line could not be written to the CSV: {}", path);
			e.printStackTrace();
		}

	}
}
