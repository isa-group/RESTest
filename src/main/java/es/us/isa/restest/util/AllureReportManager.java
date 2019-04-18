package es.us.isa.restest.util;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

/**
 * Class for managing the generation of test reports with Allure
 * 
 * @author Sergio Segura
 */
public class AllureReportManager {

	private String resultsDirPath;
	private String reportDirPath;
	private String allureCommand;
	private Boolean historyTrend = false;
	private Boolean loadCategories = true;
	
	
	public AllureReportManager() {
		this(PropertyManager.readProperty("allure.results.dir"), PropertyManager.readProperty("allure.report.dir"));
	}
	
	public AllureReportManager(String resultsDir, String reportDir) {
		this.resultsDirPath = resultsDir;
		this.reportDirPath = reportDir;

		// Allure command
		String os = System.getProperty("os.name");
		if (os.contains("Windows"))
			allureCommand = PropertyManager.readProperty("allure.command.windows");
		else
			allureCommand = PropertyManager.readProperty("allure.command.unix");
	}
	
	public void generateReport() {
		
		// If history trend is enabled, we must copy the "history" directory from the current report to the allure results directory
		if (historyTrend)
			copyHistoryDirectory();
	
		// If category loading is enabled, we must copy the file "categories.json" to the allure results directory.
		if (loadCategories)
			copyCategoryFile();
		
		// Generate report
		Runtime rt = Runtime.getRuntime();
		try {
			Process proc = rt.exec(allureCommand + " generate -c " + resultsDirPath  + " -o " + reportDirPath);
			proc.waitFor();
		} catch (IOException e) {
			System.err.println("Error generating report: " + e.getMessage());
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.err.println("Error while generating test report: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	// Copy the files categories.json file from the resource directory to the allure results directory
	private void copyCategoryFile() {
		
		File sourceFile = new File(PropertyManager.readProperty("allure.categories.path"));
		File targetFile = new File(resultsDirPath + "/categories.json");
		
		try {
			FileUtils.copyFile(sourceFile, targetFile);
		} catch (IOException e) {
			System.err.println("Error copying Allure categories file:" + e.getMessage());
			e.printStackTrace();
		}
		
	}

	// Copy the history subfolder of the allure report to the allure results directory to enable the trend view.
	private void copyHistoryDirectory() {
		
		// Check if there exist some report already, otherwise return
		File reportDir = new File(reportDirPath);
		if (!reportDir.exists())
			return;
		
		// Copy history directory to allure results directory
		File sourceDir = new File(reportDirPath + "/history");
		File targetDir = new File(resultsDirPath + "/history");
		
		try {
			FileUtils.copyDirectory(sourceDir, targetDir);
		} catch (IOException e) {
			System.err.println("Error copy history folder to allure results directory: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public Boolean historyTrend() {
		return historyTrend;
	}
	
	public void setHistoryTrend(Boolean historyTrend) {
		this.historyTrend = historyTrend;
	}
	
	public Boolean loadCategories() {
		return loadCategories;
	}
	
	public void setLoadCategories(Boolean loadCategories) {
		this.loadCategories = loadCategories;
	}
	
	public String getResultsDir() {
		return resultsDirPath;
	}
	
	public void setResultsDir(String resultsDir) {
		this.resultsDirPath = resultsDir;
	}
	
	public String getReportDir() {
		return reportDirPath;
	}
	
	public void setReportDir(String reportDir) {
		this.reportDirPath = reportDir;
	}

	public String getResultsDirPath() {
		return resultsDirPath;
	}

	public void setResultsDirPath(String resultsDirPath) {
		this.resultsDirPath = resultsDirPath;
	}

	public String getReportDirPath() {
		return reportDirPath;
	}

	public void setReportDirPath(String reportDirPath) {
		this.reportDirPath = reportDirPath;
	}

}
