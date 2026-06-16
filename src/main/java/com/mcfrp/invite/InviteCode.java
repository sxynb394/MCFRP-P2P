package com.mcfrp.invite;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class InviteCode {
    private static final String PREFIX = "MCFRP://";

    public static String encode(String secretKey, String frpsHost, int frpsPort, String token) {
        String data = secretKey + "|" + frpsHost + ":" + frpsPort + "|" + token;
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
        return PREFIX + encoded;
    }

    public static InviteData decode(String code) {
        if (!code.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid invite code format");
        }

        String encoded = code.substring(PREFIX.length());
        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        String data = new String(decoded, StandardCharsets.UTF_8);

        String[] parts = data.split("\\|", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid invite code data");
        }

        String secretKey = parts[0];
        String[] hostPort = parts[1].split(":", 2);
        String frpsHost = hostPort[0];
        int frpsPort = Integer.parseInt(hostPort[1]);
        String token = parts[2];

        return new InviteData(secretKey, frpsHost, frpsPort, token);
    }

    public static String generateSecretKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static class InviteData {
        public final String secretKey;
        public final String frpsHost;
        public final int frpsPort;
        public final String token;

        public InviteData(String secretKey, String frpsHost, int frpsPort, String token) {
            this.secretKey = secretKey;
            this.frpsHost = frpsHost;
            this.frpsPort = frpsPort;
            this.token = token;
        }
    }
}
