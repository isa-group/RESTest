package es.us.isa.restest.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class AuthManagerTest {

    final String AUTH_KEYS = "Sample/apikeys.json";

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
}
