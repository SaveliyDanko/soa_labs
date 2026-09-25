package ru.itmo.soa.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ru.itmo.soa.error.ValidationMessages.NOT_BLANK;
import static ru.itmo.soa.error.ValidationMessages.POSITIVE;
import static ru.itmo.soa.error.ValidationMessages.REQUIRED;

/** Shared JSON-B compatible models used by both Jakarta REST services. */
public final class ApiModels {
    private ApiModels() {}

    public enum FormOfEducation { DISTANCE_EDUCATION, FULL_TIME_EDUCATION, EVENING_CLASSES }
    public enum Semester { SECOND, THIRD, SEVENTH, EIGHTH }
    public enum Color { GREEN, YELLOW, ORANGE }
    public enum Country { USA, CHINA, VATICAN, SOUTH_KOREA, NORTH_KOREA }

    public static class Coordinates {
        @NotNull(message = REQUIRED) private Float x;
        @NotNull(message = REQUIRED) private Float y;
        public Coordinates() {}
        public Coordinates(Float x, Float y) { this.x = x; this.y = y; }
        public Float getX() { return x; }
        public void setX(Float x) { this.x = x; }
        public Float getY() { return y; }
        public void setY(Float y) { this.y = y; }
    }

    public static class Location {
        @NotNull(message = REQUIRED) private Long x;
        @NotNull(message = REQUIRED) private Double y;
        @NotNull(message = REQUIRED) private Double z;
        public Location() {}
        public Location(Long x, Double y, Double z) { this.x = x; this.y = y; this.z = z; }
        public Long getX() { return x; }
        public void setX(Long x) { this.x = x; }
        public Double getY() { return y; }
        public void setY(Double y) { this.y = y; }
        public Double getZ() { return z; }
        public void setZ(Double z) { this.z = z; }
    }

    public static class Person {
        @NotBlank(message = NOT_BLANK) private String name;
        private ZonedDateTime birthday;
        private Color hairColor;
        @NotNull(message = REQUIRED) private Country nationality;
        @Valid private Location location;
        public Person() {}
        public Person(String name, ZonedDateTime birthday, Color hairColor, Country nationality, Location location) {
            this.name = name; this.birthday = birthday; this.hairColor = hairColor;
            this.nationality = nationality; this.location = location;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public ZonedDateTime getBirthday() { return birthday; }
        public void setBirthday(ZonedDateTime birthday) { this.birthday = birthday; }
        public Color getHairColor() { return hairColor; }
        public void setHairColor(Color hairColor) { this.hairColor = hairColor; }
        public Country getNationality() { return nationality; }
        public void setNationality(Country nationality) { this.nationality = nationality; }
        public Location getLocation() { return location; }
        public void setLocation(Location location) { this.location = location; }
    }

    public static class StudyGroupRequest {
        @NotBlank(message = NOT_BLANK) private String name;
        @NotNull(message = REQUIRED) @Valid private Coordinates coordinates;
        @NotNull(message = REQUIRED) @Positive(message = POSITIVE) private Long studentsCount;
        @NotNull(message = REQUIRED) private FormOfEducation formOfEducation;
        private Semester semesterEnum;
        @Valid private Person groupAdmin;
        public StudyGroupRequest() {}
        public StudyGroupRequest(String name, Coordinates coordinates, Long studentsCount,
                                 FormOfEducation formOfEducation, Semester semesterEnum, Person groupAdmin) {
            this.name = name; this.coordinates = coordinates; this.studentsCount = studentsCount;
            this.formOfEducation = formOfEducation; this.semesterEnum = semesterEnum; this.groupAdmin = groupAdmin;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Coordinates getCoordinates() { return coordinates; }
        public void setCoordinates(Coordinates coordinates) { this.coordinates = coordinates; }
        public Long getStudentsCount() { return studentsCount; }
        public void setStudentsCount(Long studentsCount) { this.studentsCount = studentsCount; }
        public FormOfEducation getFormOfEducation() { return formOfEducation; }
        public void setFormOfEducation(FormOfEducation formOfEducation) { this.formOfEducation = formOfEducation; }
        public Semester getSemesterEnum() { return semesterEnum; }
        public void setSemesterEnum(Semester semesterEnum) { this.semesterEnum = semesterEnum; }
        public Person getGroupAdmin() { return groupAdmin; }
        public void setGroupAdmin(Person groupAdmin) { this.groupAdmin = groupAdmin; }
    }

    public static class StudyGroup extends StudyGroupRequest {
        @Positive(message = POSITIVE) private Integer id;
        @NotNull(message = REQUIRED) private Instant creationDate;
        public StudyGroup() {}
        public StudyGroup(Integer id, Instant creationDate, StudyGroupRequest request) {
            super(request.getName(), ApiModels.copy(request.getCoordinates()), request.getStudentsCount(),
                    request.getFormOfEducation(), request.getSemesterEnum(), ApiModels.copy(request.getGroupAdmin()));
            this.id = id; this.creationDate = creationDate;
        }
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public Instant getCreationDate() { return creationDate; }
        public void setCreationDate(Instant creationDate) { this.creationDate = creationDate; }
        public StudyGroupRequest toRequest() {
            return new StudyGroupRequest(getName(), ApiModels.copy(getCoordinates()), getStudentsCount(),
                    getFormOfEducation(), getSemesterEnum(), ApiModels.copy(getGroupAdmin()));
        }
        public StudyGroup copy() { return new StudyGroup(id, creationDate, toRequest()); }
        public void updateFrom(StudyGroupRequest request) {
            setName(request.getName()); setCoordinates(ApiModels.copy(request.getCoordinates()));
            setStudentsCount(request.getStudentsCount()); setFormOfEducation(request.getFormOfEducation());
            setSemesterEnum(request.getSemesterEnum()); setGroupAdmin(ApiModels.copy(request.getGroupAdmin()));
        }
    }

    public static class StudyGroupPage {
        private List<StudyGroup> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        public StudyGroupPage() {}
        public StudyGroupPage(List<StudyGroup> content, int page, int size, long totalElements, int totalPages) {
            this.content = content; this.page = page; this.size = size;
            this.totalElements = totalElements; this.totalPages = totalPages;
        }
        public List<StudyGroup> getContent() { return content; }
        public void setContent(List<StudyGroup> content) { this.content = content; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public long getTotalElements() { return totalElements; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    }

    public static class CountResult {
        private long count;
        private String comparedBy;
        private String greaterThan;
        public CountResult() {}
        public CountResult(long count, String comparedBy, String greaterThan) {
            this.count = count; this.comparedBy = comparedBy; this.greaterThan = greaterThan;
        }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
        public String getComparedBy() { return comparedBy; }
        public void setComparedBy(String comparedBy) { this.comparedBy = comparedBy; }
        public String getGreaterThan() { return greaterThan; }
        public void setGreaterThan(String greaterThan) { this.greaterThan = greaterThan; }
    }

    public static class ActionResult {
        private String code;
        private String message;
        private Integer resourceId;
        public ActionResult() {}
        public ActionResult(String code, String message, Integer resourceId) {
            this.code = code; this.message = message; this.resourceId = resourceId;
        }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Integer getResourceId() { return resourceId; }
        public void setResourceId(Integer resourceId) { this.resourceId = resourceId; }
    }

    public static class ApiErrorResponse {
        private Instant timestamp;
        private int status;
        private String code;
        private String message;
        private String path;
        private Map<String, String> details;
        public ApiErrorResponse() {}
        public ApiErrorResponse(int status, String code, String message, String path, Map<String, String> details) {
            this.timestamp = Instant.now(); this.status = status; this.code = code; this.message = message;
            this.path = path; this.details = details == null || details.isEmpty() ? null : new LinkedHashMap<>(details);
        }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public Map<String, String> getDetails() { return details; }
        public void setDetails(Map<String, String> details) { this.details = details; }
    }

    public static class ValidationErrorResponse extends ApiErrorResponse {
        private Map<String, String> violations;
        public ValidationErrorResponse() {}
        public ValidationErrorResponse(int status, String code, String message, String path,
                                       Map<String, String> violations) {
            super(status, code, message, path, null); this.violations = new LinkedHashMap<>(violations);
        }
        public Map<String, String> getViolations() { return violations; }
        public void setViolations(Map<String, String> violations) { this.violations = violations; }
    }

    private static Coordinates copy(Coordinates source) {
        return source == null ? null : new Coordinates(source.getX(), source.getY());
    }
    private static Location copy(Location source) {
        return source == null ? null : new Location(source.getX(), source.getY(), source.getZ());
    }
    private static Person copy(Person source) {
        return source == null ? null : new Person(source.getName(), source.getBirthday(), source.getHairColor(),
                source.getNationality(), copy(source.getLocation()));
    }
}
