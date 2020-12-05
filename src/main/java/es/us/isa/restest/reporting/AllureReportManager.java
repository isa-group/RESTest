package es.us.isa.restest.reporting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import es.us.isa.restest.util.AllureAuthManager;
import org.apache.commons.io.FileUtils;

import es.us.isa.restest.util.PropertyManager;

/**
 * Class for managing the generation of test reports with Allure
 * 
 * @author Sergio Segura
 */
public class AllureReportManager {

	private String resultsDirPath;
	private String reportDirPath;
	private List<String> authProperties;
	private String allureCommand;
	private Boolean historyTrend = false;					// If true, it show history information by copying the 'history' directory from previous report
	private Boolean loadCategories = true;					// If true, it uses the custom categories defined in the properties file located in src/main/resources
	
	
	public AllureReportManager() {
		this(PropertyManager.readProperty("allure.results.dir"), PropertyManager.readProperty("allure.report.dir"), new ArrayList<>());
	}
	
	public AllureReportManager(String resultsDir, String reportDir, List<String> authProperties) {
		this.resultsDirPath = resultsDir;
		this.reportDirPath = reportDir;
		this.authProperties = authProperties;

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
	
		// If category loading is enabled, we must copy the file "allure-categories.json" to the allure results directory.
		if (loadCategories)
			copyCategoryFile();
		
		// Generate report
		Runtime rt = Runtime.getRuntime();
		try {
			Process proc = rt.exec(allureCommand + " generate -c " + resultsDirPath  + " -o " + reportDirPath);
			proc.waitFor();
			AllureAuthManager.confidentialityFilter(authProperties, resultsDirPath);
			AllureAuthManager.confidentialityFilter(authProperties, reportDirPath + "/data/attachments");
		} catch (IOException e) {
			System.err.println("Error generating report: " + e.getMessage());
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.err.println("Error while generating test report: " + e.getMessage());
			e.printStackTrace();
			Thread.currentThread().interrupt();
		}
	}
	
	// Copy the files allure-categories.json file from the resource directory to the allure results directory
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
	
	public void setEnvironmentProperties(String propertiesFilePath) {
		File sourceFile = new File(propertiesFilePath);
		File targetFile = new File(resultsDirPath + "/environment.properties");
		
		try {
			FileUtils.copyFile(sourceFile, targetFile);
		} catch (IOException e) {
			System.err.println("Error copying Allure environment.properties file:" + e.getMessage());
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
