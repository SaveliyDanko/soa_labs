package ru.itmo.soa.isu;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import reactor.core.publisher.Mono;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.model.ApiModels;
import ru.itmo.soa.soap.IsuFaultMessage;
import ru.itmo.soa.soap.IsuPortType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false", "cxf.path=/soap", "study-groups.esb-url=http://localhost:1"})
class IsuSoapTest {
    @LocalServerPort int port;
    @MockBean StudyGroupsClient groups;
    IsuPortType soap;

    @BeforeEach void client() {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(IsuPortType.class); factory.setAddress("http://127.0.0.1:" + port + "/soap/isu");
        soap = (IsuPortType) factory.create();
    }

    @Test void exposesWsdlAndRemovesTheOldRestResource() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        var wsdl = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/soap/isu?wsdl")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, wsdl.statusCode()); assertTrue(wsdl.body().contains("urn:itmo:soa:isu:v1"));
        assertTrue(wsdl.body().contains("soap:binding"));
        var rest = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/isu/group/7/expel-all"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, rest.statusCode()); verifyNoInteractions(groups);
    }

    @Test void expelsThroughTheStudyGroupsClient() throws Exception {
        when(groups.delete(7)).thenReturn(Mono.empty());
        var result = soap.expelAll("7");
        assertEquals("ALL_STUDENTS_EXPELLED", result.getCode()); assertEquals(7, result.getResourceId());
        verify(groups).delete(7); verifyNoMoreInteractions(groups);
    }

    @Test void changesOnlyEducationFormAndPreservesNestedData() throws Exception {
        ApiModels.StudyGroup current = new ApiModels.StudyGroup(7, Instant.parse("2025-01-01T00:00:00.123456Z"),
                new ApiModels.StudyGroupRequest("Математика", new ApiModels.Coordinates(1f, -2f), 20L,
                        ApiModels.FormOfEducation.FULL_TIME_EDUCATION, ApiModels.Semester.THIRD,
                        new ApiModels.Person("Анна", ZonedDateTime.parse("1985-06-20T10:15:30+03:00"),
                                ApiModels.Color.GREEN, ApiModels.Country.USA, new ApiModels.Location(1L, 2d, 3d))));
        when(groups.get(7)).thenReturn(Mono.just(current));
        when(groups.update(eq(7), any())).thenAnswer(call -> Mono.just(new ApiModels.StudyGroup(7, current.getCreationDate(), call.getArgument(1))));
        var result = soap.changeEducationForm("7", "EVENING_CLASSES");
        assertEquals("EVENING_CLASSES", result.getFormOfEducation()); assertEquals("Математика", result.getName());
        assertEquals("2025-01-01T00:00:00.123456Z", result.getCreationDate().toXMLFormat());
        assertEquals(20, result.getStudentsCount()); assertEquals("Анна", result.getGroupAdmin().getName());
        assertEquals(3d, result.getGroupAdmin().getLocation().getZ());
        assertEquals("1985-06-20T10:15:30+03:00", result.getGroupAdmin().getBirthday().toXMLFormat());
    }

    @Test void validatesInsideSoapAndPublishesTypedFaults() {
        IsuFaultMessage invalid = assertThrows(IsuFaultMessage.class, () -> soap.expelAll("abc"));
        assertEquals(400, invalid.getFaultInfo().getStatus()); assertEquals("INVALID_PARAMETER_TYPE", invalid.getFaultInfo().getCode());
        IsuFaultMessage zero = assertThrows(IsuFaultMessage.class, () -> soap.expelAll("0"));
        assertEquals("INVALID_PARAMETER_VALUE", zero.getFaultInfo().getCode());
        IsuFaultMessage form = assertThrows(IsuFaultMessage.class, () -> soap.changeEducationForm("7", "WRONG"));
        assertEquals("INVALID_PARAMETER_VALUE", form.getFaultInfo().getCode()); verifyNoInteractions(groups);
    }

    @Test void translatesMissingGroupsToTypedSoapFaults() {
        when(groups.delete(9999)).thenReturn(Mono.error(new ApiException(ErrorCode.STUDY_GROUP_NOT_FOUND, Map.of("id", "9999"))));
        IsuFaultMessage fault = assertThrows(IsuFaultMessage.class, () -> soap.expelAll("9999"));
        assertEquals(404, fault.getFaultInfo().getStatus()); assertEquals("STUDY_GROUP_NOT_FOUND", fault.getFaultInfo().getCode());
        assertEquals("9999", fault.getFaultInfo().getDetail().get(0).getValue());
    }
}
