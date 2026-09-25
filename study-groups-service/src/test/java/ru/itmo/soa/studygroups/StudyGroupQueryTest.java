package ru.itmo.soa.studygroups;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.model.ApiModels.Color;
import ru.itmo.soa.model.ApiModels.Coordinates;
import ru.itmo.soa.model.ApiModels.Country;
import ru.itmo.soa.model.ApiModels.FormOfEducation;
import ru.itmo.soa.model.ApiModels.Location;
import ru.itmo.soa.model.ApiModels.Person;
import ru.itmo.soa.model.ApiModels.Semester;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StudyGroupQueryTest {
    private final StudyGroupQuery query = new StudyGroupQuery();

    @Test
    void sortsByIdByDefault() {
        assertEquals(List.of(1, 2, 3), ids(query.apply(
                List.of(group(3, "C", 30), group(1, "A", 10), group(2, "B", 20)),
                List.of(), List.of())));
    }

    @Test
    void combinesFiltersAndSortsWithIdAsFinalTieBreaker() {
        List<StudyGroup> groups = List.of(group(5, "Math B", 30), group(4, "Math A", 20),
                group(3, "Math A", 30), group(2, "Math A", 30),
                group(1, "Physics", 40), group(6, "Math A", 10));

        assertEquals(List.of(2, 3, 4, 5), ids(query.apply(groups,
                List.of("name:CONTAINS:mAtH", "studentsCount:ge:20"),
                List.of("name", "studentsCount,DESC"))));
    }

    @ParameterizedTest
    @CsvSource({"asc, 1, 2", "desc, 2, 1"})
    void keepsNullsLastInEitherDirection(String direction, int first, int second) {
        StudyGroup a = group(1, "A", 10);
        StudyGroup b = group(2, "B", 20);
        b.getGroupAdmin().setName("Zoe");
        b.getGroupAdmin().getLocation().setX(4L);
        StudyGroup withoutAdmin = group(3, "C", 30);
        withoutAdmin.setGroupAdmin(null);
        StudyGroup withoutLocation = group(4, "D", 40);
        withoutLocation.getGroupAdmin().setName(null);
        withoutLocation.getGroupAdmin().setLocation(null);
        List<StudyGroup> groups = List.of(withoutLocation, b, withoutAdmin, a);

        for (String field : List.of("groupAdmin.name", "groupAdmin.location.x")) {
            assertEquals(List.of(first, second, 3, 4), ids(query.apply(groups,
                    List.of(), List.of(field + "," + direction))), field);
        }
    }

    @ParameterizedTest
    @CsvSource({"eq, 20, 2", "ne, 20, 1|3", "gt, 20, 3", "ge, 20, 2|3",
            "lt, 20, 1", "le, 20, 1|2"})
    void supportsComparisonOperators(String operator, int value, String expectedIds) {
        List<StudyGroup> groups = List.of(group(3, "C", 30), group(1, "A", 10), group(2, "B", 20));

        assertEquals(expectedIds, String.join("|", query.apply(groups,
                        List.of("studentsCount:" + operator + ":" + value), List.of()).stream()
                .map(group -> group.getId().toString()).toList()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "id:eq:1", "name:eq:Math", "coordinates.x:eq:1.5", "coordinates.y:eq:-2.0",
            "creationDate:eq:2026-01-01T00:00:00Z", "studentsCount:eq:20",
            "formOfEducation:eq:FULL_TIME_EDUCATION", "semesterEnum:eq:SECOND",
            "groupAdmin.name:contains:ANN", "groupAdmin.birthday:eq:2000-01-01T10:00:00+03:00",
            "groupAdmin.hairColor:eq:GREEN", "groupAdmin.nationality:eq:USA",
            "groupAdmin.location.x:eq:3", "groupAdmin.location.y:eq:4.5", "groupAdmin.location.z:eq:6.5"
    })
    void filtersEverySupportedField(String filter) {
        StudyGroup group = group(1, "Math", 20);

        assertEquals(List.of(group), query.apply(List.of(group), List.of(filter), List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "groupAdmin.name:eq:Anna", "groupAdmin.name:ne:Anna", "groupAdmin.name:contains:anna",
            "groupAdmin.birthday:gt:1999-01-01T00:00:00Z", "groupAdmin.hairColor:ne:GREEN",
            "groupAdmin.nationality:eq:USA", "groupAdmin.location.x:ne:3",
            "groupAdmin.location.y:ge:0", "groupAdmin.location.z:lt:10"
    })
    void nullValuesNeverMatchFilters(String filter) {
        StudyGroup withoutAdmin = group(1, "A", 10);
        withoutAdmin.setGroupAdmin(null);
        StudyGroup emptyAdmin = group(2, "B", 20);
        emptyAdmin.setGroupAdmin(new Person());
        StudyGroup emptyLocation = group(3, "C", 30);
        emptyLocation.setGroupAdmin(new Person(null, null, null, null, new Location()));

        assertEquals(List.of(), query.apply(List.of(withoutAdmin, emptyAdmin, emptyLocation),
                List.of(filter), List.of()));
    }

    @ParameterizedTest
    @CsvSource({
            "id, text, Integer", "id, 2147483648, Integer",
            "studentsCount, 9223372036854775808, Long",
            "coordinates.x, NaN, Float", "coordinates.y, Infinity, Float",
            "coordinates.x, 1e100, Float", "groupAdmin.location.y, -Infinity, Double",
            "groupAdmin.location.z, NaN, Double", "groupAdmin.location.y, 1e400, Double",
            "creationDate, yesterday, Instant", "groupAdmin.birthday, yesterday, ZonedDateTime",
            "formOfEducation, full_time_education, FormOfEducation", "semesterEnum, FIRST, Semester",
            "groupAdmin.hairColor, BLUE, Color", "groupAdmin.nationality, UNKNOWN, Country"
    })
    void rejectsInvalidValuesEvenWithAnEmptySource(String field, String value, String expectedType) {
        ApiException error = assertThrows(ApiException.class,
                () -> query.apply(List.of(), List.of(field + ":eq:" + value), List.of()));

        assertEquals(400, error.getStatus());
        assertEquals("INVALID_FILTER_VALUE", error.getCode());
        assertEquals(Map.of("value", value, "expectedType", expectedType), error.getDetails());
        assertNotNull(error.getCause());
    }

    @ParameterizedTest
    @CsvSource({
            "name:eq, INVALID_FILTER_FORMAT, filter, name:eq",
            "name:eq:, INVALID_FILTER_FORMAT, filter, name:eq:",
            "unknown:eq:value, INVALID_FILTER_FIELD, field, unknown",
            "name:LIKE:value, INVALID_FILTER_OPERATOR, operator, like"
    })
    void preservesFilterValidationErrors(String filter, String code, String key, String value) {
        ApiException error = assertThrows(ApiException.class,
                () -> query.apply(List.of(), List.of(filter), List.of()));

        assertEquals(400, error.getStatus());
        assertEquals(code, error.getCode());
        assertEquals(Map.of(key, value), error.getDetails());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "coordinates.x", "creationDate", "groupAdmin.nationality"})
    void rejectsContainsForNonStringsBeforeParsingTheValue(String field) {
        ApiException error = assertThrows(ApiException.class,
                () -> query.apply(List.of(), List.of(field + ":contains:invalid"), List.of()));

        assertEquals(400, error.getStatus());
        assertEquals("INVALID_FILTER_OPERATOR", error.getCode());
        assertEquals(Map.of("field", field, "operator", "contains"), error.getDetails());
    }

    @ParameterizedTest
    @CsvSource({
            "'name,sideways', INVALID_SORT_FORMAT, sort", "'name,', INVALID_SORT_FORMAT, sort",
            "'name,asc,extra', INVALID_SORT_FORMAT, sort", "',asc', INVALID_SORT_FORMAT, sort",
            "unknown, INVALID_SORT_FIELD, field"
    })
    void preservesSortValidationErrors(String sort, String code, String key) {
        ApiException error = assertThrows(ApiException.class,
                () -> query.apply(List.of(), List.of(), List.of(sort)));

        assertEquals(400, error.getStatus());
        assertEquals(code, error.getCode());
        assertEquals(Map.of(key, sort), error.getDetails());
    }

    private StudyGroup group(int id, String name, long count) {
        return new StudyGroup(id, Instant.parse("2026-01-01T00:00:00Z"),
                new StudyGroupRequest(name, new Coordinates(1.5f, -2f), count,
                        FormOfEducation.FULL_TIME_EDUCATION, Semester.SECOND,
                        new Person("Anna", ZonedDateTime.parse("2000-01-01T10:00:00+03:00"),
                                Color.GREEN, Country.USA, new Location(3L, 4.5, 6.5))));
    }

    private List<Integer> ids(List<StudyGroup> groups) {
        return groups.stream().map(StudyGroup::getId).toList();
    }
}
