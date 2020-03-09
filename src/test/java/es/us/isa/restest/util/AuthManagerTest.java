package es.us.isa.restest.util;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AuthManagerTest {

    final String AUTH_KEYS = "Sample/apikeys.json";

    @Test
    @Ignore
    public void testGetAuthKeys() {
        Map<String, String> authProperties = new HashMap<>();
        AuthManager authManager = new AuthManager(AUTH_KEYS);
        for(String authProperty : authManager.getAuthPropertyNames())
            authProperties.put(authProperty, authManager.getAuthProperty(authProperty));

        assertTrue("hello", authProperties.get("apikey_1").equals("abc"));
        assertTrue("hello2", authProperties.get("apikey_2").equals("ghi"));

        for(String authProperty : authManager.getAuthPropertyNames())
            authProperties.put(authProperty, authManager.getAuthProperty(authProperty));

        assertTrue("hello3", authProperties.get("apikey_1").equals("def"));
        assertTrue("hello4", authProperties.get("apikey_2").equals("jkl"));
    }
}
