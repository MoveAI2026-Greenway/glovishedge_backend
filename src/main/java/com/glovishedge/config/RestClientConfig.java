package com.glovishedge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * spring-boot-starter-webmvc는 RestClient.Builder를 자동 제공하는 오토컨피그레이션을
 * 포함하지 않는다(Spring Boot 4.x에서 restclient 오토컨피그가 별도 모듈로 분리됨).
 * ExchangeRateClient/EuaMarketClient가 공통으로 주입받는 RestClient.Builder를 여기서 한 번만 정의한다.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
