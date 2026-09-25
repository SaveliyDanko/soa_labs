package ru.itmo.soa.isu;

import jakarta.ws.rs.ApplicationPath;
import ru.itmo.soa.error.ApiApplication;

@ApplicationPath("/")
public class RestApplication extends ApiApplication {
    public RestApplication() {
        super(IsuResource.class, CorsFilter.class);
    }
}
