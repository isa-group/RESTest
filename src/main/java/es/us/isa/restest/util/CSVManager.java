package es.us.isa.restest.util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import static es.us.isa.restest.util.FileManager.createFileIfNotExists;
import static es.us.isa.restest.util.FileManager.deleteFile;

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

	/**
	 * Returns a list with the values of all rows (including header, if any)
	 * of the input CSV file. Each row is a list of strings (one element per field)
	 * @param path
	 * @return
	 */
	public static List<List<String>> readCSV(String path) {
		List<List<String>> rows = new ArrayList<>();

		Reader in;
		try {
			in = new FileReader(path);
			Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(in);
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
		List<List<String>> rows = readCSV(path);
		if (!includeFirstRow)
			rows.remove(0);
		return rows;
	}

	/**
	 * Create a new CSV file in the given path and with the given header.
	 * @param path Path where to place the file. Parent folders must be already created
	 * @param header Header to add to the first line. If null, no header will be added
	 */
	public static void createFileWithHeader(String path, String header) {
		deleteFile(path); // delete file if it exists
		createFileIfNotExists(path);
		createFileWithHeader(new File(path), header);
	}

	private static void createFileWithHeader(File csvFile, String header) {
		try(FileOutputStream oCsvFile = new FileOutputStream(csvFile, true)) {
			if (header != null) {
				header += "\n";
				oCsvFile.write(header.getBytes());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void writeRow(String path, String row) {
		File csvFile = new File(path);
		try(FileOutputStream oCsvFile = new FileOutputStream(csvFile, true)) {
			row += "\n";
			oCsvFile.write(row.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
