package es.us.isa.restest.util;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;

import org.junit.Ignore;
import org.junit.Test;

import es.us.isa.restest.reporting.AllureReportManager;

public class AllureReportManagerTest {

	String resultsDir = "src/test/resources/allure-results";
	String reportDir = "target/allure-report";

	
	@Test
	@Ignore		// To avoid the test failing in Travis
	public void testGenerateReport() {

		// Generate report
		AllureReportManager arm = new AllureReportManager(resultsDir, reportDir, new ArrayList<>());
		arm.generateReport();
		
		File dir = new File(reportDir);
		assertTrue("Test report not created", dir.exists());
		
	}
}
