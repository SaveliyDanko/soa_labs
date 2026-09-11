package ru.itmo.soa.isu

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.ResourceAccessException
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
    } catch (exception: UpstreamServiceException) {
        throw exception
    } catch (exception: RestClientResponseException) {
        val status = HttpStatus.resolve(exception.statusCode.value())
        if (status == HttpStatus.NOT_FOUND) {
            throw UpstreamServiceException(
                HttpStatus.NOT_FOUND,
                "STUDY_GROUP_NOT_FOUND",
                "Сервис Study Groups не нашёл указанную группу",
            )
        }
        if (status != null && status.is4xxClientError) {
            throw UpstreamServiceException(
                status,
                "UPSTREAM_REQUEST_REJECTED",
                "Сервис Study Groups отклонил запрос",
                mapOf("upstreamStatus" to status.value().toString()),
            )
        }
        throw UpstreamServiceException(
            HttpStatus.BAD_GATEWAY,
            "UPSTREAM_BAD_RESPONSE",
            "Сервис Study Groups вернул ошибочный ответ",
            mapOf("upstreamStatus" to exception.statusCode.value().toString()),
        )
    } catch (exception: ResourceAccessException) {
        throw UpstreamServiceException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "STUDY_GROUPS_SERVICE_UNAVAILABLE",
            "Сервис Study Groups временно недоступен",
        )
    } catch (exception: RuntimeException) {
        throw UpstreamServiceException(
            HttpStatus.BAD_GATEWAY,
            "UPSTREAM_BAD_RESPONSE",
            "Не удалось обработать ответ сервиса Study Groups",
        )
    }
}
