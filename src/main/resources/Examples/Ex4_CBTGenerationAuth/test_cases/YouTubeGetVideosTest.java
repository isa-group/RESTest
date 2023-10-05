package youtubeGetVideos;

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
public class YouTubeGetVideosTest {

	private static final String OAI_JSON_URL = "src/main/resources/Examples/Ex4_CBTGenerationAuth/openapi.yaml";
	private static final StatusCode5XXFilter statusCode5XXFilter = new StatusCode5XXFilter();
	private static final NominalOrFaultyTestCaseFilter nominalOrFaultyTestCaseFilter = new NominalOrFaultyTestCaseFilter();
	private static final ResponseValidationFilter validationFilter = new ResponseValidationFilter(OAI_JSON_URL);
	private static final AllureRestAssured allureFilter = new AllureRestAssured();
	private static final String APIName = "Example4";
	private static final String testId = "Example4";
	private static final CSVFilter csvFilter = new CSVFilter(APIName, testId);

	@BeforeClass
	public static void setUp() {
		RestAssured.baseURI = "https://youtube.googleapis.com/";

		statusCode5XXFilter.setAPIName(APIName);
		statusCode5XXFilter.setTestId(testId);
		nominalOrFaultyTestCaseFilter.setAPIName(APIName);
		nominalOrFaultyTestCaseFilter.setTestId(testId);
		validationFilter.setAPIName(APIName);
		validationFilter.setTestId(testId);
	}

	@Test
	public void test_1h7wzgbkbccbo_youtubevideoslist() {
		String testResultId = "test_1h7wzgbkbccbo_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(true, false, "inter_parameter_dependency");
		statusCode5XXFilter.updateFaultyData(true, false, "inter_parameter_dependency");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("videoCategoryId", "19")
				.queryParam("part", "id,topicDetails,liveStreamingDetails")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "2316")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_1k1tykhpcnjud_youtubevideoslist() {
		String testResultId = "test_1k1tykhpcnjud_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(true, true, "individual_parameter_constraint:Changed value of integer parameter maxWidth from '7818' to boolean 'true'");
		statusCode5XXFilter.updateFaultyData(true, true, "individual_parameter_constraint:Changed value of integer parameter maxWidth from '7818' to boolean 'true'");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("regionCode", "FI")
				.queryParam("maxHeight", "3055")
				.queryParam("maxResults", "5")
				.queryParam("videoCategoryId", "19")
				.queryParam("part", "statistics,player,id")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "true")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_vbq7io11xlpz_youtubevideoslist() {
		String testResultId = "test_vbq7io11xlpz_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("maxHeight", "5310")
				.queryParam("maxResults", "3")
				.queryParam("videoCategoryId", "20")
				.queryParam("part", "liveStreamingDetails,localizations,snippet")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "7409")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_qxl2lxxnpo39_youtubevideoslist() {
		String testResultId = "test_qxl2lxxnpo39_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("regionCode", "FR")
				.queryParam("hl", "es")
				.queryParam("videoCategoryId", "19")
				.queryParam("part", "topicDetails,statistics")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "321")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_s1eg1wkcrsc8_youtubevideoslist() {
		String testResultId = "test_s1eg1wkcrsc8_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("regionCode", "NO")
				.queryParam("hl", "pt")
				.queryParam("maxHeight", "7740")
				.queryParam("videoCategoryId", "1")
				.queryParam("part", "topicDetails,statistics,status")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "4895")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_t4ndwus0wuox_youtubevideoslist() {
		String testResultId = "test_t4ndwus0wuox_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("regionCode", "NO")
				.queryParam("maxHeight", "3986")
				.queryParam("maxResults", "43")
				.queryParam("videoCategoryId", "19")
				.queryParam("part", "snippet,recordingDetails")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "7612")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_urumzhnmdt0z_youtubevideoslist() {
		String testResultId = "test_urumzhnmdt0z_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("hl", "ja")
				.queryParam("part", "recordingDetails,topicDetails,id,liveStreamingDetails,player")
				.queryParam("id", "7m-NPuXPBqM")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "6542")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_s1dvedrep06u_youtubevideoslist() {
		String testResultId = "test_s1dvedrep06u_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("part", "status,recordingDetails,snippet")
				.queryParam("id", "n8I-YVuaSR0")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_r1fn0qinevl0_youtubevideoslist() {
		String testResultId = "test_r1fn0qinevl0_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("regionCode", "ES")
				.queryParam("maxHeight", "1653")
				.queryParam("maxResults", "20")
				.queryParam("part", "localizations,topicDetails,player")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "4789")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_1iv9c0vubsapl_youtubevideoslist() {
		String testResultId = "test_1iv9c0vubsapl_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("hl", "pt")
				.queryParam("maxHeight", "6340")
				.queryParam("part", "status,id,contentDetails,snippet")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "7152")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_rhod43p0tr38_youtubevideoslist() {
		String testResultId = "test_rhod43p0tr38_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("regionCode", "US")
				.queryParam("part", "snippet,player")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "7184")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_t49k75l8ued2_youtubevideoslist() {
		String testResultId = "test_t49k75l8ued2_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("hl", "it")
				.queryParam("part", "statistics,id,player,recordingDetails")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_1h7o5qjboofnc_youtubevideoslist() {
		String testResultId = "test_1h7o5qjboofnc_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("maxHeight", "1989")
				.queryParam("part", "id,localizations,statistics,player,snippet,status")
				.queryParam("id", "7m-NPuXPBqM")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "2103")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_t8cdx4tex10j_youtubevideoslist() {
		String testResultId = "test_t8cdx4tex10j_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("part", "id,snippet,statistics,status")
				.queryParam("id", "n8I-YVuaSR0")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "5484")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_t84lrei9u15w_youtubevideoslist() {
		String testResultId = "test_t84lrei9u15w_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("maxHeight", "5658")
				.queryParam("part", "status,recordingDetails,snippet")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_trwdftepbf3s_youtubevideoslist() {
		String testResultId = "test_trwdftepbf3s_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("regionCode", "FR")
				.queryParam("hl", "no")
				.queryParam("videoCategoryId", "2")
				.queryParam("part", "liveStreamingDetails,snippet,id,contentDetails,player")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "2592")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_skpkoov8w10w_youtubevideoslist() {
		String testResultId = "test_skpkoov8w10w_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("hl", "ja")
				.queryParam("maxHeight", "5575")
				.queryParam("part", "statistics,liveStreamingDetails,snippet,localizations,topicDetails")
				.queryParam("id", "yuD34tEpRFw")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "6504")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_s1jh7oz03kms_youtubevideoslist() {
		String testResultId = "test_s1jh7oz03kms_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("regionCode", "US")
				.queryParam("maxHeight", "3718")
				.queryParam("part", "status,snippet")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "7974")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_vb92k8uzg3le_youtubevideoslist() {
		String testResultId = "test_vb92k8uzg3le_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("hl", "fr")
				.queryParam("maxHeight", "6458")
				.queryParam("part", "player,contentDetails,liveStreamingDetails,localizations,topicDetails")
				.queryParam("id", "5kUu97Vkwj4")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "7149")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

	@Test
	public void test_vf0x2oeaig51_youtubevideoslist() {
		String testResultId = "test_vf0x2oeaig51_youtubevideoslist";

		nominalOrFaultyTestCaseFilter.updateFaultyData(false, true, "none");
		statusCode5XXFilter.updateFaultyData(false, true, "none");
		csvFilter.setTestResultId(testResultId);
		statusCode5XXFilter.setTestResultId(testResultId);
		nominalOrFaultyTestCaseFilter.setTestResultId(testResultId);
		validationFilter.setTestResultId(testResultId);

		try {
			Response response = RestAssured
			.given()
				.queryParam("regionCode", "FI")
				.queryParam("maxHeight", "558")
				.queryParam("maxResults", "24")
				.queryParam("videoCategoryId", "17")
				.queryParam("part", "contentDetails,localizations,topicDetails,liveStreamingDetails,status")
				.queryParam("chart", "mostPopular")
				.queryParam("key", "AIzaSyDKJqjYRjSmICouuJ9GXrMJxfcBtIPDHmc")
				.queryParam("maxWidth", "2653")
				.filter(allureFilter)
				.filter(statusCode5XXFilter)
				.filter(nominalOrFaultyTestCaseFilter)
				.filter(validationFilter)
				.filter(csvFilter)
			.when()
				.get("/youtube/v3/videos");

			response.then();
			System.out.println("Test passed.");
		} catch (RuntimeException ex) {
			System.err.println(ex.getMessage());
			fail(ex.getMessage());
		}
	}

}
