package es.us.isa.restest.inputs.random;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

/** 
 * @author Sergio Segura
 *
 */
public class RandomDateGenerator extends RandomGenerator {

    private Date startDate;				// Optional: Specifies a min-max range to generate the values 
    private Date endDate;				
    private int startDays;				// Set the min date as a number of days from today
    private int endDays;				// Set the max date as a number of days from today
    private boolean fromToday=false; 	// Set true to generate dates from today on

    private String format;				// SimpleDateFormat

	private static final Logger logger = LogManager.getLogger(RandomDateGenerator.class.getName());
	
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

	public void setStartDate(String startDate) {
		try {
			this.startDate = new SimpleDateFormat("yyyy-MM-dd").parse(startDate);
		} catch (ParseException e) {
			logger.error("Error parsing date: {} ", startDate);
			logger.error("Exception: ", e);
		}
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(String endDate) {
		try {
			this.endDate = new SimpleDateFormat("yyyy-MM-dd").parse(endDate);
		} catch (ParseException e) {
			logger.error("Error parsing date: {} ", endDate);
			logger.error("Exception: ", e);;
		}
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public boolean fromToday() {
		return fromToday;
	}

	public void setFromToday(boolean fromToday) {
		Date today = new Date();
		if (fromToday)
			startDate = today;
		else {
	    	// Set default start date range 10 years ago
	    	this.startDate = new DateTime(today).minusYears(10).toDate();
		}
	}

	public int getStartDays() {
		return startDays;
	}

	public void setStartDays(int startDays) {
		this.startDays = startDays;
    	this.startDate = new DateTime(new Date()).plusDays(startDays).toDate();
	}

	public int getEndDays() {
		return endDays;
	}

	public void setEndDays(int endDays) {
		this.endDays = endDays;
    	this.endDate = new DateTime(new Date()).plusDays(endDays).toDate();
	}


}
