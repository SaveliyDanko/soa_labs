package ru.itmo.soa.studygroups;

import jakarta.enterprise.context.ApplicationScoped;
import ru.itmo.soa.model.ApiModels.Color;
import ru.itmo.soa.model.ApiModels.Country;
import ru.itmo.soa.model.ApiModels.FormOfEducation;
import ru.itmo.soa.model.ApiModels.Semester;
import ru.itmo.soa.model.ApiModels.StudyGroup;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

@ApplicationScoped
public class StudyGroupQuery {
    private record Field(Class<?> type, Function<StudyGroup, Object> value) {}
    private record SortRule(Field field, boolean descending) {}

    private static final Map<String, Field> FIELDS = fields();
    private static final List<String> OPERATORS = List.of("eq", "ne", "gt", "ge", "lt", "le", "contains");

    public List<StudyGroup> apply(List<StudyGroup> source, List<String> filters, List<String> sorts) {
        Predicate<StudyGroup> predicate = filters.stream()
                .map(this::parseFilter)
                .reduce(group -> true, Predicate::and);
        Comparator<StudyGroup> comparator = parseSorts(sorts);
        return source.stream().filter(predicate).sorted(comparator).toList();
    }

    private Predicate<StudyGroup> parseFilter(String raw) {
        String[] parts = raw.split(":", 3);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw invalid("INVALID_FILTER_FORMAT", "Фильтр должен иметь формат field:operator:value",
                    "filter", raw);
        }
        Field field = FIELDS.get(parts[0]);
        if (field == null) {
            throw invalid("INVALID_FILTER_FIELD", "Неизвестное поле фильтрации: " + parts[0],
                    "field", parts[0]);
        }
        String operator = parts[1].toLowerCase(Locale.ROOT);
        if (!OPERATORS.contains(operator)) {
            throw invalid("INVALID_FILTER_OPERATOR", "Неизвестный оператор фильтрации: " + operator,
                    "operator", operator);
        }
        if (operator.equals("contains") && field.type() != String.class) {
            throw new ApiException(400, "INVALID_FILTER_OPERATOR", "Оператор contains применим только к строкам",
                    Map.of("field", parts[0], "operator", operator));
        }
        Object expected = convert(parts[2], field.type());
        return group -> matches(field.value().apply(group), operator, expected);
    }

    private Comparator<StudyGroup> parseSorts(List<String> sorts) {
        List<SortRule> rules = new ArrayList<>();
        for (String raw : sorts) {
            String[] parts = raw.split(",", -1);
            if (parts.length > 2 || parts[0].isBlank() ||
                    (parts.length == 2 && !parts[1].equalsIgnoreCase("asc") && !parts[1].equalsIgnoreCase("desc"))) {
                throw invalid("INVALID_SORT_FORMAT", "Сортировка должна иметь формат field,asc или field,desc",
                        "sort", raw);
            }
            Field field = FIELDS.get(parts[0]);
            if (field == null) {
                throw invalid("INVALID_SORT_FIELD", "Неизвестное поле сортировки: " + parts[0],
                        "field", parts[0]);
            }
            rules.add(new SortRule(field, parts.length == 2 && parts[1].equalsIgnoreCase("desc")));
        }
        if (rules.isEmpty()) {
            rules.add(new SortRule(FIELDS.get("id"), false));
        }
        return (left, right) -> {
            for (SortRule rule : rules) {
                Object leftValue = rule.field().value().apply(left);
                Object rightValue = rule.field().value().apply(right);
                int compared = compareNullable(leftValue, rightValue);
                if (compared != 0) {
                    // Nulls remain last in either direction; only non-null values are reversed.
                    return leftValue == null || rightValue == null || !rule.descending() ? compared : -compared;
                }
            }
            return Integer.compare(left.getId(), right.getId());
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean matches(Object actual, String operator, Object expected) {
        if (actual == null) return false;
        if (operator.equals("contains")) {
            return actual.toString().toLowerCase(Locale.ROOT)
                    .contains(expected.toString().toLowerCase(Locale.ROOT));
        }
        int compared = ((Comparable) actual).compareTo(expected);
        return switch (operator) {
            case "eq" -> compared == 0;
            case "ne" -> compared != 0;
            case "gt" -> compared > 0;
            case "ge" -> compared >= 0;
            case "lt" -> compared < 0;
            case "le" -> compared <= 0;
            default -> false;
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compareNullable(Object left, Object right) {
        if (left == right) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return ((Comparable) left).compareTo(right);
    }

    private Object convert(String raw, Class<?> type) {
        try {
            if (type == String.class) return raw;
            if (type == Integer.class) return Integer.valueOf(raw);
            if (type == Long.class) return Long.valueOf(raw);
            if (type == Float.class) {
                Float value = Float.valueOf(raw);
                if (!Float.isFinite(value)) throw new NumberFormatException();
                return value;
            }
            if (type == Double.class) {
                Double value = Double.valueOf(raw);
                if (!Double.isFinite(value)) throw new NumberFormatException();
                return value;
            }
            if (type == Instant.class) return Instant.parse(raw);
            if (type == ZonedDateTime.class) return ZonedDateTime.parse(raw);
            if (type == FormOfEducation.class) return FormOfEducation.valueOf(raw);
            if (type == Semester.class) return Semester.valueOf(raw);
            if (type == Color.class) return Color.valueOf(raw);
            if (type == Country.class) return Country.valueOf(raw);
            throw new IllegalArgumentException("Unsupported filter type");
        } catch (RuntimeException exception) {
            throw new ApiException(400, "INVALID_FILTER_VALUE",
                    "Некорректное значение '" + raw + "' для поля типа " + type.getSimpleName(),
                    Map.of("value", raw, "expectedType", type.getSimpleName()), Map.of(), exception);
        }
    }

    private ApiException invalid(String code, String message, String key, String value) {
        return new ApiException(400, code, message, Map.of(key, value));
    }

    private static Map<String, Field> fields() {
        Map<String, Field> fields = new LinkedHashMap<>();
        fields.put("id", new Field(Integer.class, StudyGroup::getId));
        fields.put("name", new Field(String.class, StudyGroup::getName));
        fields.put("coordinates.x", new Field(Float.class, g -> g.getCoordinates().getX()));
        fields.put("coordinates.y", new Field(Float.class, g -> g.getCoordinates().getY()));
        fields.put("creationDate", new Field(Instant.class, StudyGroup::getCreationDate));
        fields.put("studentsCount", new Field(Long.class, StudyGroup::getStudentsCount));
        fields.put("formOfEducation", new Field(FormOfEducation.class, StudyGroup::getFormOfEducation));
        fields.put("semesterEnum", new Field(Semester.class, StudyGroup::getSemesterEnum));
        fields.put("groupAdmin.name", new Field(String.class,
                g -> g.getGroupAdmin() == null ? null : g.getGroupAdmin().getName()));
        fields.put("groupAdmin.birthday", new Field(ZonedDateTime.class,
                g -> g.getGroupAdmin() == null ? null : g.getGroupAdmin().getBirthday()));
        fields.put("groupAdmin.hairColor", new Field(Color.class,
                g -> g.getGroupAdmin() == null ? null : g.getGroupAdmin().getHairColor()));
        fields.put("groupAdmin.nationality", new Field(Country.class,
                g -> g.getGroupAdmin() == null ? null : g.getGroupAdmin().getNationality()));
        fields.put("groupAdmin.location.x", new Field(Long.class, g ->
                g.getGroupAdmin() == null || g.getGroupAdmin().getLocation() == null ? null : g.getGroupAdmin().getLocation().getX()));
        fields.put("groupAdmin.location.y", new Field(Double.class, g ->
                g.getGroupAdmin() == null || g.getGroupAdmin().getLocation() == null ? null : g.getGroupAdmin().getLocation().getY()));
        fields.put("groupAdmin.location.z", new Field(Double.class, g ->
                g.getGroupAdmin() == null || g.getGroupAdmin().getLocation() == null ? null : g.getGroupAdmin().getLocation().getZ()));
        return Map.copyOf(fields);
    }
}
