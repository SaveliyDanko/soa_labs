package ru.itmo.soa.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.context.annotation.Import;
import ru.itmo.soa.web.ApiWebConfiguration;

@EnableZuulProxy
@SpringBootApplication
@Import(ApiWebConfiguration.class)
public class GatewayApplication {
    public static void main(String[] args) { SpringApplication.run(GatewayApplication.class, args); }
}
