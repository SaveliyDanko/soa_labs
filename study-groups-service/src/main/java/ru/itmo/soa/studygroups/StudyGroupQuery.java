package ru.itmo.soa.studygroups;

import jakarta.enterprise.context.ApplicationScoped;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.model.ApiModels.Color;
import ru.itmo.soa.model.ApiModels.Country;
import ru.itmo.soa.model.ApiModels.FormOfEducation;
import ru.itmo.soa.model.ApiModels.Location;
import ru.itmo.soa.model.ApiModels.Person;
import ru.itmo.soa.model.ApiModels.Semester;
import ru.itmo.soa.model.ApiModels.StudyGroup;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

@ApplicationScoped
public class StudyGroupQuery {
    private record Field<T extends Comparable<? super T>>(
            Class<T> type, Function<StudyGroup, T> value, Function<String, T> parser) {

        T parse(String raw) {
            try {
                return parser.apply(raw);
            } catch (RuntimeException exception) {
                throw new ApiException(ErrorCode.INVALID_FILTER_VALUE,
                        Map.of("value", raw, "expectedType", type.getSimpleName()), exception);
            }
        }

        Predicate<StudyGroup> filter(String operator, String raw) {
            T expected = parse(raw);
            String needle = operator.equals("contains") ? raw.toLowerCase(Locale.ROOT) : null;
            return group -> {
                T actual = value.apply(group);
                if (actual == null) return false;
                if (needle != null) return actual.toString().toLowerCase(Locale.ROOT).contains(needle);
                int compared = actual.compareTo(expected);
                return switch (operator) {
                    case "eq" -> compared == 0;
                    case "ne" -> compared != 0;
                    case "gt" -> compared > 0;
                    case "ge" -> compared >= 0;
                    case "lt" -> compared < 0;
                    case "le" -> compared <= 0;
                    default -> false;
                };
            };
        }

        Comparator<StudyGroup> comparator(boolean descending) {
            Comparator<T> order = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
            return Comparator.comparing(value, Comparator.nullsLast(order));
        }
    }

    private static final Map<String, Field<?>> FIELDS = fields();
    private static final List<String> OPERATORS = List.of("eq", "ne", "gt", "ge", "lt", "le", "contains");
    private static final Comparator<StudyGroup> BY_ID = Comparator.comparingInt(StudyGroup::getId);

    public List<StudyGroup> apply(List<StudyGroup> source, List<String> filters, List<String> sorts) {
        Predicate<StudyGroup> predicate = filters.stream()
                .map(this::parseFilter)
                .reduce(group -> true, Predicate::and);
        Comparator<StudyGroup> comparator = sorts.stream()
                .map(this::parseSort)
                .reduce(Comparator::thenComparing)
                .orElse(BY_ID)
                .thenComparing(BY_ID);
        return source.stream().filter(predicate).sorted(comparator).toList();
    }

    private Predicate<StudyGroup> parseFilter(String raw) {
        String[] parts = raw.split(":", 3);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new ApiException(ErrorCode.INVALID_FILTER_FORMAT, Map.of("filter", raw));
        }
        Field<?> field = FIELDS.get(parts[0]);
        if (field == null) {
            throw new ApiException(ErrorCode.INVALID_FILTER_FIELD, Map.of("field", parts[0]));
        }
        String operator = parts[1].toLowerCase(Locale.ROOT);
        if (!OPERATORS.contains(operator)) {
            throw new ApiException(ErrorCode.INVALID_FILTER_OPERATOR, Map.of("operator", operator));
        }
        if (operator.equals("contains") && field.type() != String.class) {
            throw new ApiException(ErrorCode.INVALID_FILTER_OPERATOR,
                    Map.of("field", parts[0], "operator", operator));
        }
        return field.filter(operator, parts[2]);
    }

    private Comparator<StudyGroup> parseSort(String raw) {
        String[] parts = raw.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank() ||
                (parts.length == 2 && !parts[1].equalsIgnoreCase("asc") && !parts[1].equalsIgnoreCase("desc"))) {
            throw new ApiException(ErrorCode.INVALID_SORT_FORMAT, Map.of("sort", raw));
        }
        Field<?> field = FIELDS.get(parts[0]);
        if (field == null) {
            throw new ApiException(ErrorCode.INVALID_SORT_FIELD, Map.of("field", parts[0]));
        }
        return field.comparator(parts.length == 2 && parts[1].equalsIgnoreCase("desc"));
    }

    private static Float parseFloat(String raw) {
        float value = Float.parseFloat(raw);
        if (!Float.isFinite(value)) throw new NumberFormatException();
        return value;
    }

    private static Double parseDouble(String raw) {
        double value = Double.parseDouble(raw);
        if (!Double.isFinite(value)) throw new NumberFormatException();
        return value;
    }

    private static <P, T> Function<StudyGroup, T> nested(
            Function<StudyGroup, P> parent, Function<P, T> value) {
        return group -> {
            P object = parent.apply(group);
            return object == null ? null : value.apply(object);
        };
    }

    private static Map<String, Field<?>> fields() {
        Function<StudyGroup, Location> location = nested(StudyGroup::getGroupAdmin, Person::getLocation);
        Map<String, Field<?>> fields = new LinkedHashMap<>();
        fields.put("id", new Field<>(Integer.class, StudyGroup::getId, Integer::valueOf));
        fields.put("name", new Field<>(String.class, StudyGroup::getName, Function.identity()));
        fields.put("coordinates.x", new Field<>(Float.class, g -> g.getCoordinates().getX(), StudyGroupQuery::parseFloat));
        fields.put("coordinates.y", new Field<>(Float.class, g -> g.getCoordinates().getY(), StudyGroupQuery::parseFloat));
        fields.put("creationDate", new Field<>(Instant.class, StudyGroup::getCreationDate, Instant::parse));
        fields.put("studentsCount", new Field<>(Long.class, StudyGroup::getStudentsCount, Long::valueOf));
        fields.put("formOfEducation", new Field<>(FormOfEducation.class, StudyGroup::getFormOfEducation, FormOfEducation::valueOf));
        fields.put("semesterEnum", new Field<>(Semester.class, StudyGroup::getSemesterEnum, Semester::valueOf));
        fields.put("groupAdmin.name", new Field<>(String.class,
                nested(StudyGroup::getGroupAdmin, Person::getName), Function.identity()));
        fields.put("groupAdmin.birthday", new Field<>(ZonedDateTime.class,
                nested(StudyGroup::getGroupAdmin, Person::getBirthday), ZonedDateTime::parse));
        fields.put("groupAdmin.hairColor", new Field<>(Color.class,
                nested(StudyGroup::getGroupAdmin, Person::getHairColor), Color::valueOf));
        fields.put("groupAdmin.nationality", new Field<>(Country.class,
                nested(StudyGroup::getGroupAdmin, Person::getNationality), Country::valueOf));
        fields.put("groupAdmin.location.x", new Field<>(Long.class, nested(location, Location::getX), Long::valueOf));
        fields.put("groupAdmin.location.y", new Field<>(Double.class, nested(location, Location::getY), StudyGroupQuery::parseDouble));
        fields.put("groupAdmin.location.z", new Field<>(Double.class, nested(location, Location::getZ), StudyGroupQuery::parseDouble));
        return Map.copyOf(fields);
    }
}
