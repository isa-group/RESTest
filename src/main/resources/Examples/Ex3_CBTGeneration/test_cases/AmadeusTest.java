package Example3;

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
	private static final String testId = "null";
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
	public void test_1h7u98k5b6stg_getMultiHotelOffers() {
		String testResultId = "test_1h7u98k5b6stg_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(true, true, "individual_parameter_constraint:Violated 'max' constraint of integer parameter radius");
		statusCode5XXFilter.updateFaultyData(true, true, "individual_parameter_constraint:Violated 'max' constraint of integer parameter radius");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("boardType", "BREAKFAST")
				.queryParam("hotelIds", "HHALB462")
				.queryParam("chains", "BW,WW,CW,WV,EC")
				.queryParam("adults", "1")
				.queryParam("sort", "DISTANCE")
				.queryParam("page[offset]", "74")
				.queryParam("checkOutDate", "2023-05-30")
				.queryParam("ratings", "2")
				.queryParam("currency", "SRD")
				.queryParam("childAges", "0")
				.queryParam("lang", "AR")
				.queryParam("radius", "207")
				.queryParam("rateCodes", "PKG,CON")
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
	public void test_qhhs8m2rj23m_getMultiHotelOffers() {
		String testResultId = "test_qhhs8m2rj23m_getMultiHotelOffers";

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
				.queryParam("chains", "EC,BW,CW")
				.queryParam("latitude", "51.514081")
				.queryParam("sort", "DISTANCE")
				.queryParam("page[offset]", "18")
				.queryParam("checkOutDate", "2023-05-27")
				.queryParam("ratings", "2")
				.queryParam("childAges", "6")
				.queryParam("radius", "20")
				.queryParam("radiusUnit", "KM")
				.queryParam("lang", "TY")
				.queryParam("paymentPolicy", "GUARANTEE")
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
	public void test_u7ojzme7yotx_getMultiHotelOffers() {
		String testResultId = "test_u7ojzme7yotx_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("amenities", "TELEVISION,SOLARIUM")
				.queryParam("page[offset]", "38")
				.queryParam("hotelIds", "HXXIF400")
				.queryParam("adults", "2")
				.queryParam("currency", "LRD")
				.queryParam("sort", "DISTANCE")
				.queryParam("radius", "35")
				.queryParam("radiusUnit", "KM")
				.queryParam("lang", "CY")
				.queryParam("hotelName", "Hotel California")
				.queryParam("priceRange", "41-130")
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
	public void test_socc0vlvboth_getMultiHotelOffers() {
		String testResultId = "test_socc0vlvboth_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("checkOutDate", "2023-05-21")
				.queryParam("roomQuantity", "7")
				.queryParam("latitude", "51.514081")
				.queryParam("adults", "5")
				.queryParam("radiusUnit", "MILE")
				.queryParam("lang", "RM")
				.queryParam("checkInDate", "2023-05-12")
				.queryParam("hotelName", "Hotel California")
				.queryParam("paymentPolicy", "DEPOSIT")
				.queryParam("longitude", "-0.073438")
				.queryParam("bestRateOnly", "true")
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
	public void test_qe6lvexxp44x_getMultiHotelOffers() {
		String testResultId = "test_qe6lvexxp44x_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("hotelIds", "RTVLCBON")
				.queryParam("chains", "6C,HS,HI")
				.queryParam("hotelName", "Hotel")
				.queryParam("view", "FULL")
				.queryParam("page[offset]", "83")
				.queryParam("checkOutDate", "2023-05-23")
				.queryParam("ratings", "2")
				.queryParam("includeClosed", "false")
				.queryParam("currency", "COP")
				.queryParam("childAges", "12")
				.queryParam("radius", "117")
				.queryParam("radiusUnit", "MILE")
				.queryParam("lang", "NO")
				.queryParam("rateCodes", "PKG,TVL,TUR,WKD,COR")
				.queryParam("priceRange", "2-717")
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
	public void test_qxwmgt9pnzom_getMultiHotelOffers() {
		String testResultId = "test_qxwmgt9pnzom_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("amenities", "TELEVISION,NO_PORN_FILMS,BEACH")
				.queryParam("page[offset]", "50")
				.queryParam("boardType", "BREAKFAST")
				.queryParam("checkOutDate", "2023-05-21")
				.queryParam("hotelIds", "ICPPTICA")
				.queryParam("chains", "WV,EC,HS")
				.queryParam("includeClosed", "false")
				.queryParam("radiusUnit", "KM")
				.queryParam("lang", "IA")
				.queryParam("checkInDate", "2023-05-16")
				.queryParam("rateCodes", "PKG,RAC,PRO")
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
	public void test_vb3lcdypcqch_getMultiHotelOffers() {
		String testResultId = "test_vb3lcdypcqch_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("roomQuantity", "3")
				.queryParam("chains", "HS,EC,6C,WV")
				.queryParam("cityCode", "TYO")
				.queryParam("adults", "6")
				.queryParam("sort", "NONE")
				.queryParam("checkInDate", "2023-05-07")
				.queryParam("page[limit]", "45")
				.queryParam("view", "LIGHT")
				.queryParam("page[offset]", "44")
				.queryParam("checkOutDate", "2023-05-25")
				.queryParam("includeClosed", "false")
				.queryParam("currency", "CLF")
				.queryParam("radius", "130")
				.queryParam("radiusUnit", "KM")
				.queryParam("priceRange", "3-475")
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
	public void test_1iyc5fvy11k6e_getMultiHotelOffers() {
		String testResultId = "test_1iyc5fvy11k6e_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("amenities", "MEETING_ROOMS,RESTAURANT,SWIMMING_POOL")
				.queryParam("boardType", "BREAKFAST")
				.queryParam("hotelIds", "HSFELAAY")
				.queryParam("chains", "HS,BW,EC,CW")
				.queryParam("adults", "7")
				.queryParam("sort", "PRICE")
				.queryParam("checkInDate", "2023-05-08")
				.queryParam("hotelName", "Barcelo")
				.queryParam("view", "NONE")
				.queryParam("page[offset]", "37")
				.queryParam("ratings", "1")
				.queryParam("currency", "HKD")
				.queryParam("radiusUnit", "KM")
				.queryParam("rateCodes", "TVL,COR,RAC,GOV,WKD")
				.queryParam("priceRange", "-797")
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
	public void test_tocun2eccdo0_getMultiHotelOffers() {
		String testResultId = "test_tocun2eccdo0_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("page[limit]", "90")
				.queryParam("view", "FULL")
				.queryParam("checkOutDate", "2023-06-03")
				.queryParam("hotelIds", "HSMUCBBN")
				.queryParam("ratings", "2")
				.queryParam("adults", "7")
				.queryParam("childAges", "19")
				.queryParam("radiusUnit", "KM")
				.queryParam("checkInDate", "2023-05-10")
				.queryParam("hotelName", "Ibis")
				.queryParam("paymentPolicy", "GUARANTEE")
				.queryParam("bestRateOnly", "true")
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
	public void test_skmqn5i6tiya_getMultiHotelOffers() {
		String testResultId = "test_skmqn5i6tiya_getMultiHotelOffers";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("amenities", "CASINO,TELEVISION,BABY-SITTING")
				.queryParam("page[offset]", "25")
				.queryParam("boardType", "ROOM_ONLY")
				.queryParam("hotelIds", "RTVLCBON")
				.queryParam("roomQuantity", "5")
				.queryParam("ratings", "1")
				.queryParam("currency", "XPF")
				.queryParam("sort", "NONE")
				.queryParam("lang", "AZ")
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

}
