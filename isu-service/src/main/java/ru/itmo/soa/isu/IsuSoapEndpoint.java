package ru.itmo.soa.isu;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import javax.jws.WebService;
import org.springframework.stereotype.Component;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.error.RequestValidation;
import ru.itmo.soa.model.ApiModels.FormOfEducation;
import ru.itmo.soa.soap.*;

@Component
@WebService(endpointInterface = "ru.itmo.soa.soap.IsuPortType", serviceName = "IsuService",
        portName = "IsuPort", targetNamespace = "urn:itmo:soa:isu:v1")
public class IsuSoapEndpoint implements IsuPortType {
    private final IsuService service;
    public IsuSoapEndpoint(IsuService service) { this.service = service; }

    @Override
    public ActionResult expelAll(String groupId) throws IsuFaultMessage {
        return invoke(() -> SoapModels.toSoap(service.expelAll(RequestValidation.positiveId(groupId, "groupId"))
                .block(Duration.ofSeconds(20))));
    }

    @Override
    public StudyGroup changeEducationForm(String groupId, String newForm) throws IsuFaultMessage {
        return invoke(() -> {
            final FormOfEducation form;
            try { form = FormOfEducation.valueOf(newForm); }
            catch (IllegalArgumentException | NullPointerException failure) {
                throw new ApiException(ErrorCode.INVALID_PARAMETER_VALUE,
                        Map.of("parameter", "newForm", "value", String.valueOf(newForm)));
            }
            return SoapModels.toSoap(service.changeEducationForm(RequestValidation.positiveId(groupId, "groupId"), form)
                    .block(Duration.ofSeconds(35)));
        });
    }

    // SOAP runs on Tomcat servlet workers; blocking here never occupies a Reactor event loop.
    private <T> T invoke(Supplier<T> operation) throws IsuFaultMessage {
        try { return operation.get(); }
        catch (Exception failure) {
            ApiException error = failure instanceof ApiException ? (ApiException) failure
                    : new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, Map.of(), failure);
            IsuFault fault = new IsuFault(); fault.setStatus(error.getStatus());
            fault.setCode(error.getCode()); fault.setMessage(error.getMessage());
            error.getDetails().forEach((name, value) -> {
                ErrorDetail detail = new ErrorDetail(); detail.setName(name); detail.setValue(value); fault.getDetail().add(detail);
            });
            throw new IsuFaultMessage(error.getMessage(), fault);
        }
    }
}
