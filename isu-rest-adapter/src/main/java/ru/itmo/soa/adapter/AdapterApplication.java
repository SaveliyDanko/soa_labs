package ru.itmo.soa.adapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import ru.itmo.soa.web.ApiWebConfiguration;

@SpringBootApplication
@Import(ApiWebConfiguration.class)
public class AdapterApplication {
    public static void main(String[] args) { SpringApplication.run(AdapterApplication.class, args); }
}
