package com.shortsb.embedding.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
// 단일 임베딩 HTTP 클라이언트 구성
public class EmbeddingConfig {

    @Bean
    RestClient embeddingRestClient(RestClient.Builder builder, EmbeddingProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        builder = builder.baseUrl(properties.baseUrl().toString()).requestFactory(factory);
        if (!properties.apiKey().isEmpty()) {
            builder.defaultHeader("X-API-Key", properties.apiKey());
        }
        return builder.build();
    }
}
