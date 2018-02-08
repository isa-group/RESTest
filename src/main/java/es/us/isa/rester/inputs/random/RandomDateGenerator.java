package es.us.isa.rester.inputs.random;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.joda.time.DateTime;

/** 
 * @author Sergio
 *
 */
public class RandomDateGenerator extends RandomGenerator {

    private Date startDate;		// Optional: Specifies a min-max range to generate the values 
    private Date endDate;
    private String format;		// SimpleDateFormat
	
    public RandomDateGenerator() {
    	super();
    	
    	// Set default format
    	format = "yyyy-MM-dd HH:mm:ss";
    	
    	Date date = new Date();
    	
    	// Set default start date range 10 years ago
    	this.startDate = new DateTime(date).minusYears(10).toDate();
    	
    	// Set default end date range 2 years from now
    	this.endDate = new DateTime(date).plusYears(2).toDate();
    }
   
	@Override
	public Date nextValue() {
		Date value = new Date(rand.nextLong(startDate.getTime(), endDate.getTime()));
		return value;
	}
	
	@Override
	public String nextValueAsString() {
		SimpleDateFormat sdfDate = new SimpleDateFormat(format);
		String date = sdfDate.format(nextValue());
		return date;
	}

	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}


}
