package com.ashraf.notesapi.support;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Builds a JWT-shaped (but unsigned) string purely so tests can exercise exp-claim parsing. */
public class FakeJwt {

    public static String withExpiryInSeconds(long secondsFromNow) {
        long exp = System.currentTimeMillis() / 1000 + secondsFromNow;
        String header = encode("{\"alg\":\"none\"}");
        String payload = encode("{\"user_id\":1,\"exp\":" + exp + "}");
        return header + "." + payload + ".signature";
    }

    private static String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
