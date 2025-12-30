package com.ingenio.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class G3Config {

    @Value("${g3.engine.url:http://g3-engine:8000}")
    private String g3EngineUrl;

    @Bean("g3WebClient")
    public WebClient g3WebClient() {
        return WebClient.builder()
                .baseUrl(g3EngineUrl)
                .build();
    }
}
