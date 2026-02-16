package com.jay.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Encryption encryption = new Encryption();
    private Ai ai = new Ai();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenExpiration;
        private long refreshTokenExpiration;
        private String issuer;
    }

    @Getter
    @Setter
    public static class Encryption {
        private String secretKey;
    }

    @Getter
    @Setter
    public static class Ai {
        private String provider = "log";
        private Claude claude = new Claude();

        @Getter
        @Setter
        public static class Claude {
            private String apiKey;
            private String model = "claude-sonnet-4-5-20250929";
            private int maxTokens = 1024;
        }
    }
}
