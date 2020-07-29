package es.us.isa.restest.searchbased.terminationcriterion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import es.us.isa.restest.searchbased.terminationcriteria.MaxExecutionTime;
import es.us.isa.restest.searchbased.terminationcriteria.MaxExecutionTime.TimeUnit;
import io.qameta.allure.junit4.DisplayName;

public class MaxExecutionTimeTest {

	@Test
	@DisplayName("With a duration of 0 the criterion is always met")
	public void test() {
		MaxExecutionTime sut=new MaxExecutionTime(0,TimeUnit.MILLISECONDS);
		assertTrue(sut.test(null));
	}
	
	@Test
	@DisplayName("With a duration of 1 second the criterion is not met immediately but it is met after 2 seconds")
	public void testAfterTwoSeconds() {
		MaxExecutionTime sut=new MaxExecutionTime(1,TimeUnit.SECONDS);		
		assertFalse(sut.test(null));
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		assertTrue(sut.test(null));
	}
}
