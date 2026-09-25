package ru.itmo.soa.studygroups;

import jakarta.ws.rs.ApplicationPath;
import ru.itmo.soa.error.ApiApplication;

@ApplicationPath("/")
public class RestApplication extends ApiApplication {
    public RestApplication() {
        super(StudyGroupResource.class, DocumentationResource.class, StrictStudyGroupReader.class, CorsFilter.class);
    }
}
