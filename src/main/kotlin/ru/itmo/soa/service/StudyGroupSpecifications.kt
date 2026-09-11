package ru.itmo.soa.service

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import ru.itmo.soa.domain.Color
import ru.itmo.soa.domain.Country
import ru.itmo.soa.domain.FormOfEducation
import ru.itmo.soa.domain.Semester
import ru.itmo.soa.domain.StudyGroup
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

object StudyGroupSpecifications {
    val fields: Map<String, Class<*>> = linkedMapOf(
        "id" to Int::class.java,
        "name" to String::class.java,
        "coordinates.x" to Float::class.java,
        "coordinates.y" to Float::class.java,
        "creationDate" to Date::class.java,
        "studentsCount" to Long::class.java,
        "formOfEducation" to FormOfEducation::class.java,
        "semesterEnum" to Semester::class.java,
        "groupAdmin.name" to String::class.java,
        "groupAdmin.birthday" to ZonedDateTime::class.java,
        "groupAdmin.hairColor" to Color::class.java,
        "groupAdmin.nationality" to Country::class.java,
        "groupAdmin.location.x" to Long::class.java,
        "groupAdmin.location.y" to Double::class.java,
        "groupAdmin.location.z" to Double::class.java,
    )

    private val operators = setOf("eq", "ne", "gt", "ge", "lt", "le", "contains")

    fun fromFilters(filters: List<String>?): Specification<StudyGroup> {
        var result = Specification.unrestricted<StudyGroup>()
        filters.orEmpty().forEach { result = result.and(parse(it)) }
        return result
    }

    private fun parse(raw: String): Specification<StudyGroup> {
        val parts = raw.split(":", limit = 3)
        if (parts.size != 3 || parts[2].isBlank()) {
            throw InvalidQueryException(
                "INVALID_FILTER_FORMAT",
                "Фильтр должен иметь формат field:operator:value",
                mapOf("filter" to raw),
            )
        }
        val field = parts[0]
        val operator = parts[1].lowercase(Locale.ROOT)
        val type = fields[field] ?: throw InvalidQueryException(
            "INVALID_FILTER_FIELD",
            "Неизвестное поле фильтрации: $field",
            mapOf("field" to field),
        )
        if (operator !in operators) throw InvalidQueryException(
            "INVALID_FILTER_OPERATOR",
            "Неизвестный оператор фильтрации: $operator",
            mapOf("operator" to operator),
        )
        if (operator == "contains" && type != String::class.java) {
            throw InvalidQueryException(
                "INVALID_FILTER_OPERATOR",
                "Оператор contains применим только к строкам",
                mapOf("field" to field, "operator" to operator),
            )
        }
        val value = convert(parts[2], type)
        return Specification { root, _, cb -> predicate(resolvePath(root, field), operator, value, cb) }
    }

    private fun resolvePath(root: Path<*>, field: String): Path<*> =
        field.split('.').fold(root) { current, part -> current.get<Any>(part) }

    @Suppress("UNCHECKED_CAST")
    private fun predicate(path: Path<*>, operator: String, value: Any, cb: CriteriaBuilder): Predicate {
        val expression = path as Expression<Comparable<Any>>
        val comparableValue = value as Comparable<Any>
        return when (operator) {
            "eq" -> cb.equal(path, value)
            "ne" -> cb.notEqual(path, value)
            "gt" -> cb.greaterThan(expression, comparableValue)
            "ge" -> cb.greaterThanOrEqualTo(expression, comparableValue)
            "lt" -> cb.lessThan(expression, comparableValue)
            "le" -> cb.lessThanOrEqualTo(expression, comparableValue)
            "contains" -> cb.like(
                cb.lower(path.`as`(String::class.java)),
                "%${value.toString().lowercase(Locale.ROOT)}%",
            )
            else -> throw InvalidQueryException("INVALID_FILTER_OPERATOR", "Неизвестный оператор: $operator")
        }
    }

    private fun convert(value: String, type: Class<*>): Any = try {
        when (type) {
            String::class.java -> value
            Int::class.java -> value.toInt()
            Long::class.java -> value.toLong()
            Float::class.java -> value.toFloat()
            Double::class.java -> value.toDouble()
            Date::class.java -> Date.from(Instant.parse(value))
            ZonedDateTime::class.java -> ZonedDateTime.parse(value)
            FormOfEducation::class.java -> FormOfEducation.valueOf(value)
            Semester::class.java -> Semester.valueOf(value)
            Color::class.java -> Color.valueOf(value)
            Country::class.java -> Country.valueOf(value)
            else -> throw InvalidQueryException(
                "UNSUPPORTED_FILTER_TYPE",
                "Неподдерживаемый тип фильтра: ${type.simpleName}",
            )
        }
    } catch (exception: InvalidQueryException) {
        throw exception
    } catch (exception: RuntimeException) {
        throw InvalidQueryException(
            "INVALID_FILTER_VALUE",
            "Некорректное значение '$value' для поля типа ${type.simpleName}",
            mapOf("value" to value, "expectedType" to type.simpleName),
            exception,
        )
    }
}
