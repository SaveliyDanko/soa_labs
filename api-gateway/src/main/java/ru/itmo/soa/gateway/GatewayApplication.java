package ru.itmo.soa.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import ru.itmo.soa.web.ApiWebConfiguration;

@SpringBootApplication
@Import(ApiWebConfiguration.class)
public class GatewayApplication {
    @Bean
    public HttpClientCustomizer disableTransportRetry() {
        return client -> client.disableRetry(true);
    }

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
