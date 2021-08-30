package es.us.isa.restest.inputs.stateful;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static es.us.isa.restest.inputs.stateful.DataMatching.getParameterValue;
import static org.junit.Assert.*;

public class DataMatchingTest {

    private static ObjectNode dict;

    @BeforeClass
    public static void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            dict = (ObjectNode) objectMapper.readTree(new File("src/test/resources/jsonData/data_matching.json"));
        } catch (IOException e) {
            fail("data_matching.json could not be loaded");
            e.printStackTrace();
        }
    }

    @Test
    public void nonExistingOperationSameFieldName() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/non/existing", "items.snippet.thumbnails.default.height");
        assertEquals("90", statefulValue.asText());
    }

    @Test
    public void sameOperationSameParameterName() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "regionCode");
        assertTrue(Arrays.asList("IT", "US", "JP", "DE", "FR", "FI", "NO", "ES").contains(statefulValue.asText()));
    }

    @Test
    public void sameOperationSameFieldName() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "items.snippet.thumbnails.default.height");
        assertEquals("90", statefulValue.asText());
    }

    @Test
    public void differentOperationSameParameterName() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "dvndf:-()=knkj13kmlas/b$%·g");
        assertEquals("7", statefulValue.asText());
    }

    @Test
    public void differentOperationSameFieldName() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "comments.snippet.tags.snippet.id");
        assertEquals("2", statefulValue.asText());
    }

    @Test
    public void sameOperationDifferentParameterName() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "idChannelId");
        assertEquals("UCgWHOqWzbZ0Brhy1xRx5W1g", statefulValue.asText());
    }

    @Test
    public void sameOperationDifferentParameterName2() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "thumbnailHighHeight");
        assertEquals("360", statefulValue.asText());
    }

    @Test
    public void sameOperationDifferentFieldName() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "high.height");
        assertEquals("360", statefulValue.asText());
    }

    @Test
    public void sameOperationDifferentFieldName2() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "thumbnail.high.height");
        assertEquals("360", statefulValue.asText());
    }

    @Test
    public void sameOperationDifferentFieldNameLonger() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "search.kind");
        assertEquals("youtube#searchListResponse", statefulValue.asText());
    }

    @Test
    public void sameOperationDifferentFieldNameLonger2() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "search.pages.nextPageToken");
        assertTrue(Arrays.asList("CAUQAA", "CAQQAA", "CBYQAA", "CDAQAA").contains(statefulValue.asText()));
    }

    @Test
    public void sameOperationDifferentFieldNameMultipleMatches() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "height");
        assertTrue(Arrays.asList("720", "480", "360", "180", "90").contains(statefulValue.asText()));
    }

    @Test
    public void differentOperationDifferentParameterName() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "advndf:-()=knkj13kmlas/b$%·g");
        assertEquals("7", statefulValue.asText());
    }

    @Test
    public void differentOperationDifferentParameterName2() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "b");
        assertEquals("6", statefulValue.asText());
    }

    @Test
    public void differentOperationDifferentFieldName() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "b.c");
        assertEquals("5", statefulValue.asText());
    }

    @Test
    public void differentOperationDifferentFieldNameLonger() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "y.z.a.b.c");
        assertEquals("5", statefulValue.asText());
    }

    @Test
    public void differentOperationDifferentFieldNameMultipleMatches() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "t.id");
        assertTrue(Arrays.asList("2", "3", "4", "8", "9", "10").contains(statefulValue.asText()));
    }

    @Test
    public void differentOperationDifferentFieldNameMultipleMatches2() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "comment.ID");
        assertTrue(Arrays.asList("8", "9", "10").contains(statefulValue.asText()));
    }

    @Test
    public void differentOperationDifferentFieldNameLongerMultipleMatches() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "authors.comments.id");
        assertTrue(Arrays.asList("8", "9", "10").contains(statefulValue.asText()));
    }

    @Test
    public void paramNameNotFound() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "ñldfjldkfjfg");
        assertNull(statefulValue);
    }

    @Test
    public void paramNameNotFound2() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "dvndf:-()=knkj13kmlas/b$%·ga");
        assertNull(statefulValue);
    }

    @Test
    public void paramNameNotFound3() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "items.snippet.thumbnails.medium.height.1");
        assertNull(statefulValue);
    }

    @Test
    public void paramNameNotFound4() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "a.b.c.s");
        assertNull(statefulValue);
    }

    @Test
    public void paramNameIdSearchIdNonExisting() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "ID");
        assertTrue(Arrays.asList("PLhHUqrvxXXCZs5fkLYW_zguo5BeVdz9XQ", "PLatn_iwPkj2pT4HucLlKJDOqoc1xQDgWK",
                "UCyEGR4ZUT5tR9L0ite00tDQ", "UCgWHOqWzbZ0Brhy1xRx5W1g", "udj780oIaeI", "cLPIlCCQjfU",
                "UCgWHOqWzbZ0Brhy1xRx5W1g")
                .contains(statefulValue.asText()));
    }

    @Test
    public void paramNameIdVideoIdExisting() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/videos", "id");
        assertTrue(Arrays.asList("udj780oIaeI", "cLPIlCCQjfU").contains(statefulValue.asText()));
    }

    @Test
    public void paramNameIdCommentIdExactExisting() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/comments", "id");
        assertEquals("9", statefulValue.asText());
    }

    @Test
    public void paramNameIdCommentIdExactExistingWithGet() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/getExamples", "id");
        assertEquals("0", statefulValue.asText());
    }

    @Test
    public void paramNameIdCommentIdExactExistingWithSetCamelCase() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/setExamples", "id");
        assertEquals("0", statefulValue.asText());
    }

    @Test
    public void paramNameIdCommentIdExactExistingWithSetNoCamelCase() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/setexamples", "id");
        assertTrue(Arrays.asList("UCgWHOqWzbZ0Brhy1xRx5W1g", "udj780oIaeI", "cLPIlCCQjfU", "UCyEGR4ZUT5tR9L0ite00tDQ",
                "UCgWHOqWzbZ0Brhy1xRx5W1g", "PLhHUqrvxXXCZs5fkLYW_zguo5BeVdz9XQ", "PLatn_iwPkj2pT4HucLlKJDOqoc1xQDgWK",
                "2", "3", "4", "8", "9", "10", "0").contains(statefulValue.asText()));
    }

    @Test
    public void paramNameFirstLevelSubPropertyOfExistingProperty() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/search", "nonExistingProperty.snippet.thumbnails.medium.height");
        assertEquals("180", statefulValue.asText());
    }

    @Test
    public void paramNameLastLevelSubPropertyOfExistingProperty() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/comments", "made.up.1.items.snippet.made.up.2.liveBroadcastContent");
        assertTrue(statefulValue.asText().equals("none") || statefulValue.asText().equals("live"));
    }

    @Test
    public void paramNameSubPropertyOfNonExistingProperty() {
        JsonNode statefulValue = getParameterValue(dict, "GET", "/youtube/v3/comments", "made.up.1.items.snippet.made.up.2.liveBroadcastContent.madeUpProperty");
        assertNull(statefulValue);
    }
}
