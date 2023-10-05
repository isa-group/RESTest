package restest;

import org.junit.*;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.FixMethodOrder;
import static org.junit.Assert.fail;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.Assert.assertTrue;
import org.junit.runners.MethodSorters;
import io.qameta.allure.restassured.AllureRestAssured;
import es.us.isa.restest.writers.restassured.filters.StatusCode5XXFilter;
import es.us.isa.restest.writers.restassured.filters.NominalOrFaultyTestCaseFilter;
import es.us.isa.restest.writers.restassured.filters.StatefulFilter;
import java.io.File;
import es.us.isa.restest.writers.restassured.filters.ResponseValidationFilter;
import es.us.isa.restest.writers.restassured.filters.CSVFilter;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AmadeusTest {

	private static final String OAI_JSON_URL = "src/main/resources/Examples/Ex3_CBTGeneration/spec_amadeus.yaml";
	private static final StatusCode5XXFilter statusCode5XXFilter = new StatusCode5XXFilter();
	private static final NominalOrFaultyTestCaseFilter nominalOrFaultyTestCaseFilter = new NominalOrFaultyTestCaseFilter();
	private static final ResponseValidationFilter validationFilter = new ResponseValidationFilter(OAI_JSON_URL);
	private static final AllureRestAssured allureFilter = new AllureRestAssured();
	private static final String APIName = "Example3";
	private static final String testId = "Example3";
	private static final CSVFilter csvFilter = new CSVFilter(APIName, testId);

	@BeforeClass
	public static void setUp() {
		RestAssured.baseURI = "https://test.api.amadeus.com/v2";

		statusCode5XXFilter.setAPIName(APIName);
		statusCode5XXFilter.setTestId(testId);
		nominalOrFaultyTestCaseFilter.setAPIName(APIName);
		nominalOrFaultyTestCaseFilter.setTestId(testId);
		validationFilter.setAPIName(APIName);
		validationFilter.setTestId(testId);
	}

	@Test
	public void test_uv5uwyrtyqlu_getMultiHotelOffers() {
		String testResultId = "test_uv5uwyrtyqlu_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(true, true, "individual_parameter_constraint:Violated 'format/pattern' constraint of string parameter checkInDate");
		statusCode5XXFilter.updateFaultyData(true, true, "individual_parameter_constraint:Violated 'format/pattern' constraint of string parameter checkInDate");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("amenities", "RESTAURANT,TENNIS,BUSINESS_CENTER")
				.queryParam("hotelIds", "HIALB1CE")
				.queryParam("roomQuantity", "1")
				.queryParam("chains", "WV,WW,HI")
				.queryParam("adults", "5")
				.queryParam("sort", "DISTANCE")
				.queryParam("checkInDate", "JWovSlmgKwGC")
				.queryParam("view", "FULL")
				.queryParam("page[offset]", "96")
				.queryParam("checkOutDate", "2023-12-03")
				.queryParam("includeClosed", "true")
				.queryParam("currency", "AED")
				.queryParam("childAges", "9")
				.queryParam("lang", "PI")
				.queryParam("paymentPolicy", "NONE")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/shopping/hotel-offers");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_1hb7q3iondcmq_getMultiHotelOffers() {
		String testResultId = "test_1hb7q3iondcmq_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("boardType", "BREAKFAST")
				.queryParam("roomQuantity", "1")
				.queryParam("latitude", "49.299005")
				.queryParam("adults", "7")
				.queryParam("hotelName", "Sol")
				.queryParam("bestRateOnly", "true")
				.queryParam("page[limit]", "24")
				.queryParam("view", "NONE")
				.queryParam("page[offset]", "71")
				.queryParam("checkOutDate", "2023-11-25")
				.queryParam("currency", "KWD")
				.queryParam("childAges", "12")
				.queryParam("radius", "16")
				.queryParam("radiusUnit", "MILE")
				.queryParam("rateCodes", "COR")
				.queryParam("paymentPolicy", "DEPOSIT")
				.queryParam("longitude", "7.463675")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/shopping/hotel-offers");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_1h7zaskbdi4vq_getMultiHotelOffers() {
		String testResultId = "test_1h7zaskbdi4vq_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("amenities", "MINIBAR,SWIMMING_POOL,JACUZZI")
				.queryParam("boardType", "ROOM_ONLY")
				.queryParam("hotelIds", "ICPPTICA")
				.queryParam("roomQuantity", "9")
				.queryParam("adults", "2")
				.queryParam("sort", "DISTANCE")
				.queryParam("checkInDate", "2023-11-09")
				.queryParam("hotelName", "Barcelo")
				.queryParam("view", "LIGHT")
				.queryParam("page[offset]", "89")
				.queryParam("ratings", "1")
				.queryParam("includeClosed", "false")
				.queryParam("currency", "KZT")
				.queryParam("childAges", "20")
				.queryParam("radiusUnit", "KM")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/shopping/hotel-offers");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_s4xx85xf244l_getMultiHotelOffers() {
		String testResultId = "test_s4xx85xf244l_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("boardType", "ROOM_ONLY")
				.queryParam("roomQuantity", "3")
				.queryParam("chains", "BW,HI")
				.queryParam("latitude", "51.514081")
				.queryParam("checkInDate", "2023-11-18")
				.queryParam("hotelName", "Barcelo")
				.queryParam("view", "FULL")
				.queryParam("checkOutDate", "2023-11-27")
				.queryParam("ratings", "2")
				.queryParam("currency", "BRL")
				.queryParam("childAges", "5")
				.queryParam("radius", "45")
				.queryParam("rateCodes", "TUR,WKD")
				.queryParam("priceRange", "-915")
				.queryParam("paymentPolicy", "GUARANTEE")
				.queryParam("longitude", "7.463675")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/shopping/hotel-offers");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_1h84uc1qy5s9x_getMultiHotelOffers() {
		String testResultId = "test_1h84uc1qy5s9x_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("roomQuantity", "7")
				.queryParam("chains", "HI,WW,EC")
				.queryParam("latitude", "51.514081")
				.queryParam("sort", "DISTANCE")
				.queryParam("bestRateOnly", "false")
				.queryParam("view", "NONE")
				.queryParam("checkOutDate", "2023-12-01")
				.queryParam("includeClosed", "false")
				.queryParam("currency", "XSU")
				.queryParam("radius", "196")
				.queryParam("radiusUnit", "MILE")
				.queryParam("lang", "YO")
				.queryParam("rateCodes", "WKD,TUR,STP,COR,GOV")
				.queryParam("longitude", "-0.073438")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/shopping/hotel-offers");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_t81tzmnec8rn_getMultiHotelOffers() {
		String testResultId = "test_t81tzmnec8rn_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("page[limit]", "13")
				.queryParam("amenities", "SERV_SPEC_MENU,RESTAURANT,NO_KID_ALLOWED")
				.queryParam("view", "LIGHT")
				.queryParam("page[offset]", "56")
				.queryParam("boardType", "ROOM_ONLY")
				.queryParam("cityCode", "MAD")
				.queryParam("includeClosed", "true")
				.queryParam("currency", "XAG")
				.queryParam("radiusUnit", "KM")
				.queryParam("checkInDate", "2023-11-17")
				.queryParam("paymentPolicy", "GUARANTEE")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/shopping/hotel-offers");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_1h821umszel9u_getMultiHotelOffers() {
		String testResultId = "test_1h821umszel9u_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("page[limit]", "19")
				.queryParam("view", "FULL")
				.queryParam("page[offset]", "40")
				.queryParam("boardType", "ROOM_ONLY")
				.queryParam("checkOutDate", "2023-11-21")
				.queryParam("hotelIds", "RTVLCBON")
				.queryParam("chains", "HS,CW,BW")
				.queryParam("adults", "7")
				.queryParam("childAges", "18")
				.queryParam("radius", "141")
				.queryParam("checkInDate", "2023-11-07")
				.queryParam("paymentPolicy", "DEPOSIT")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/shopping/hotel-offers");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_r1al096x02na_getMultiHotelOffers() {
		String testResultId = "test_r1al096x02na_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("amenities", "ANIMAL_WATCHING,KIDS_WELCOME")
				.queryParam("boardType", "BREAKFAST")
				.queryParam("hotelIds", "HXASH407")
				.queryParam("chains", "EC,HI,6C,WV")
				.queryParam("sort", "PRICE")
				.queryParam("checkInDate", "2023-11-09")
				.queryParam("page[offset]", "59")
				.queryParam("checkOutDate", "2023-11-27")
				.queryParam("ratings", "4")
				.queryParam("radiusUnit", "KM")
				.queryParam("lang", "MI")
				.queryParam("rateCodes", "TUR,MIL,GOV,STP,PKG")
				.queryParam("paymentPolicy", "GUARANTEE")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/shopping/hotel-offers");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_1jenqw9gprm1y_getMultiHotelOffers() {
		String testResultId = "test_1jenqw9gprm1y_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("amenities", "NO_PORN_FILMS,SERV_SPEC_MENU")
				.queryParam("view", "LIGHT")
				.queryParam("page[offset]", "15")
				.queryParam("boardType", "BREAKFAST")
				.queryParam("cityCode", "MEL")
				.queryParam("ratings", "4")
				.queryParam("childAges", "9")
				.queryParam("radiusUnit", "KM")
				.queryParam("checkInDate", "2023-11-11")
				.queryParam("hotelName", "Barcelo")
				.queryParam("rateCodes", "CON,TUR,RAC,SRS,GOV")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/shopping/hotel-offers");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_1iuvipsohy5wm_getMultiHotelOffers() {
		String testResultId = "test_1iuvipsohy5wm_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("amenities", "BAR,TENNIS")
				.queryParam("boardType", "ROOM_ONLY")
				.queryParam("cityCode", "OPO")
				.queryParam("sort", "DISTANCE")
				.queryParam("checkInDate", "2023-11-15")
				.queryParam("hotelName", "Barcelo")
				.queryParam("view", "NONE")
				.queryParam("page[offset]", "86")
				.queryParam("checkOutDate", "2023-12-04")
				.queryParam("ratings", "3")
				.queryParam("childAges", "9")
				.queryParam("radius", "144")
				.queryParam("radiusUnit", "MILE")
				.queryParam("lang", "SW")
				.queryParam("rateCodes", "GOV,TVL,SRS")
				.queryParam("paymentPolicy", "GUARANTEE")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/shopping/hotel-offers");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

}
