package es.us.isa.restest.util;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static es.us.isa.restest.inputs.semantic.ARTEInputGenerator.LIMIT;
import static es.us.isa.restest.util.FileManager.createFileIfNotExists;
import static es.us.isa.restest.util.FileManager.deleteFile;

public class CSVManager {

	private static final Logger logger = LogManager.getLogger(CSVManager.class.getName());
	
	/**
	 * Returns a list with the values of the first column in the input CSV file
	 * @param path The path of the CSV file
	 * @return the values of the first column of the CSV file
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
			logger.error("Error parsing CSV file: {}", path);
			logger.error("Exception: ", ex);
		}
		
		return values;
	}

	/**
	 * Returns a list with the values of all rows (including header, if any)
	 * of the input CSV file. Each row is a list of strings (one element per field)
	 * @param path The path of the CSV file
	 * @param delimiter The character that separates the values in each row
	 * @return the values of all rows of the CSV file
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
			logger.error("Error parsing CSV file: {}", path);
			logger.error("Exception: ", ex);
		}

		return rows;
	}

	/**
	 * Returns a list with the values of all rows of the input CSV file. Each row
	 * is a list of strings (one element per field)
	 * @param path The path of the CSV file
	 * @param includeFirstRow Whether to include first row of the CSV in the result
	 *                        or not. Useful for excluding header.
	 * @return the values of all rows of the CSV file
	 */
	public static List<List<String>> readCSV(String path, Boolean includeFirstRow) {
		List<List<String>> rows = readCSV(path, ',');
		if (!includeFirstRow)
			rows.remove(0);
		return rows;
	}

	/**
	 * Call {@link #readCSV(String path, char delimiter)} with delimiter=','
	 * @param path The path of the CSV file
	 * @return the values of all rows of the CSV file
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
			logger.error("Exception: ", e);
		}

	}

	public static void collectionToCSV(String path, Collection<String> collection) {
		try (FileWriter writer = new FileWriter(path)) {
			String collect = collection.stream().collect(Collectors.joining("\n"));
			writer.write(collect);
		} catch (IOException e) {
			logger.error(e.getMessage());
		}

	}

	public static void setToCSVWithLimit(String path, Set<String> collection) {

		try (FileWriter writer = new FileWriter(path)) {
			List<String> collectionAsList = new ArrayList<>(collection);
			Collections.shuffle(collectionAsList);

			Set<String> subSet = collectionAsList.stream().limit(LIMIT).collect(Collectors.toSet());
			String collect = subSet.stream().collect(Collectors.joining("\n"));
			writer.write(collect);

		} catch(IOException e) {
			logger.error(e.getMessage());
		}

	}
}
