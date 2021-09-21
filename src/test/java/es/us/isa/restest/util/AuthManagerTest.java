package es.us.isa.restest.util;

import org.junit.Test;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;


public class AuthManagerTest {

    final String AUTH_KEYS = "Sample/apikeys.json";
    final String OAUTH_DATA = "Sample/oauth.json";
    final String OAUTH_DATA_WRONG = "Sample/oauth_wrong.json";
    final String OAUTH_DATA_SHORT_EXPIRATION = "Sample/oauth_short_expiration.json";

    @Test
    public void testGetAuthKeys() {
        Map<String, String> authProperties = new HashMap<>();
        AuthManager authManager = new AuthManager(AUTH_KEYS);
        for(String authProperty : authManager.getAuthPropertyNames())
            authProperties.put(authProperty, authManager.getAuthProperty(authProperty));

        assertEquals("hello", "abc", authProperties.get("apikey_1"));
        assertEquals("hello2", "ghi", authProperties.get("apikey_2"));

        for(String authProperty : authManager.getAuthPropertyNames())
            authProperties.put(authProperty, authManager.getAuthProperty(authProperty));

        assertEquals("hello3", "def", authProperties.get("apikey_1"));
        assertEquals("hello4", "jkl", authProperties.get("apikey_2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOauthBadJson() {
        AuthManager authManager = new AuthManager(AUTH_KEYS, true);
    }

    @Test
    public void testOauthBadCredentials() {
        AuthManager authManager = new AuthManager(OAUTH_DATA_WRONG, true);
        String oauthHeader = authManager.getUpdatedOauthHeader();
        assertNull(oauthHeader);
    }

    @Test
    public void testOauth() {
        AuthManager authManager = new AuthManager(OAUTH_DATA, true);
        String oauthHeader = authManager.getUpdatedOauthHeader();
        assertNotNull(oauthHeader);
        assertTrue(oauthHeader.matches("^Bearer .+$"));
        assertTrue(authManager.getExpiration() > new Date().getTime() / 1000);
        assertTrue(authManager.getExpiration() <= new Date().getTime() / 1000 + 3600);

        String renewedOauthHeader = authManager.getUpdatedOauthHeader();
        assertEquals(renewedOauthHeader, oauthHeader);
    }

    @Test
    public void testOauthShortExpiration() {
        AuthManager authManager = new AuthManager(OAUTH_DATA_SHORT_EXPIRATION, true);
        String oauthHeader = authManager.getUpdatedOauthHeader();
        assertNotNull(oauthHeader);
        assertTrue(oauthHeader.matches("^Bearer .+$"));
        assertTrue(authManager.getExpiration() > new Date().getTime() / 1000);
        assertTrue(authManager.getExpiration() <= new Date().getTime() / 1000 + 3600);

        String renewedOauthHeader = authManager.getUpdatedOauthHeader();
        assertNotEquals(renewedOauthHeader, oauthHeader);
    }
}
