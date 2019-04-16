package es.us.isa.restest.util;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

public class AllureReportManagerTest {

	String resultsDir = "src/test/resources/allure-results";
	String reportDir = "target/allure-report";

	
	@Test
	public void testGenerateReport() {

		// Generate report
		AllureReportManager arm = new AllureReportManager(resultsDir, reportDir);
		arm.generateReport();
		
		File dir = new File(reportDir);
		assertTrue("Test report not created", dir.exists());
		
	}
}
