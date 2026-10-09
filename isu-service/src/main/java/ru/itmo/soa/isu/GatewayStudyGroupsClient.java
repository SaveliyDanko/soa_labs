package ru.itmo.soa.isu;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;
import java.time.Duration;
import java.util.Map;

@Component
public class GatewayStudyGroupsClient implements StudyGroupsClient {
    private final RestTemplate client;
    private final String baseUrl;

    public GatewayStudyGroupsClient(RestTemplateBuilder builder,
                                    @Value("${study-groups.gateway-url}") String baseUrl) {
        this.client = builder.setConnectTimeout(Duration.ofSeconds(4)).setReadTimeout(Duration.ofSeconds(12)).build();
        this.baseUrl = baseUrl.replaceAll("/+$", "") + "/api/study-groups/";
    }

    @Override
    public StudyGroup get(int id) { return execute(id, HttpMethod.GET, null, StudyGroup.class); }

    @Override
    public StudyGroup update(int id, StudyGroupRequest request) {
        return execute(id, HttpMethod.PUT, request, StudyGroup.class);
    }

    @Override
    public void delete(int id) { execute(id, HttpMethod.DELETE, null, Void.class); }

    private <T> T execute(int id, HttpMethod method, Object body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<T> response = client.exchange(baseUrl + id, method, new HttpEntity<>(body, headers), responseType);
            int expectedStatus = method == HttpMethod.DELETE ? 204 : 200;
            if (response.getStatusCodeValue() != expectedStatus || (responseType != Void.class && response.getBody() == null)) {
                throw new ApiException(ErrorCode.UPSTREAM_BAD_RESPONSE, Map.of("upstreamStatus", Integer.toString(response.getStatusCodeValue())));
            }
            return response.getBody();
        } catch (RestClientResponseException exception) {
            int status = exception.getRawStatusCode();
            if (status == 404) throw new ApiException(ErrorCode.STUDY_GROUP_NOT_FOUND, Map.of("id", Integer.toString(id)));
            ErrorCode code = status == 503 || status == 504 ? ErrorCode.STUDY_GROUPS_SERVICE_UNAVAILABLE : ErrorCode.UPSTREAM_BAD_RESPONSE;
            throw new ApiException(code, Map.of("upstreamStatus", Integer.toString(status)), exception);
        } catch (ResourceAccessException exception) {
            throw new ApiException(ErrorCode.STUDY_GROUPS_SERVICE_UNAVAILABLE, Map.of(), exception);
        } catch (RestClientException exception) {
            throw new ApiException(ErrorCode.UPSTREAM_BAD_RESPONSE, Map.of(), exception);
        }
    }
}
