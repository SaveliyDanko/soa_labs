package ru.itmo.soa.isu

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import ru.itmo.soa.api.dto.StudyGroupRequest
import ru.itmo.soa.api.dto.StudyGroupResponse
import ru.itmo.soa.service.UpstreamServiceException

@Component
class HttpStudyGroupsClient(
    builder: RestClient.Builder,
    @Value("\${study-groups.base-url:http://localhost:8080}") baseUrl: String,
) : StudyGroupsClient {
    private val client = builder.baseUrl(baseUrl).build()

    override fun get(id: Int): StudyGroupResponse = execute {
        requireNotNull(
            client.get().uri("/api/study-groups/{id}", id)
                .retrieve().body(StudyGroupResponse::class.java),
        )
    }

    override fun update(id: Int, request: StudyGroupRequest): StudyGroupResponse = execute {
        requireNotNull(
            client.put().uri("/api/study-groups/{id}", id)
                .body(request).retrieve().body(StudyGroupResponse::class.java),
        )
    }

    override fun delete(id: Int) {
        execute {
            client.delete().uri("/api/study-groups/{id}", id).retrieve().toBodilessEntity()
        }
    }

    private fun <T> execute(action: () -> T): T = try {
        action()
    } catch (exception: RestClientResponseException) {
        throw UpstreamServiceException(
            HttpStatus.resolve(exception.statusCode.value()) ?: HttpStatus.BAD_GATEWAY,
            "Первый сервис отклонил операцию: HTTP ${exception.statusCode.value()}",
        )
    } catch (exception: RuntimeException) {
        throw UpstreamServiceException(HttpStatus.BAD_GATEWAY, "Первый сервис недоступен")
    }
}
