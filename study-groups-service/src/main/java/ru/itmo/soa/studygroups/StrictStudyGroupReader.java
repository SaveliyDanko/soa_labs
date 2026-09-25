package ru.itmo.soa.studygroups;

import jakarta.annotation.Priority;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

/** Makes OpenAPI additionalProperties=false enforceable instead of silently ignoring misspelled fields. */
@Provider
@Priority(1000)
@Consumes(MediaType.APPLICATION_JSON)
public class StrictStudyGroupReader implements MessageBodyReader<StudyGroupRequest> {
    private static final Set<String> GROUP = Set.of("name", "coordinates", "studentsCount",
            "formOfEducation", "semesterEnum", "groupAdmin");
    private static final Set<String> COORDINATES = Set.of("x", "y");
    private static final Set<String> PERSON = Set.of("name", "birthday", "hairColor", "nationality", "location");
    private static final Set<String> LOCATION = Set.of("x", "y", "z");
    private final Jsonb jsonb = JsonbBuilder.create();

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type == StudyGroupRequest.class;
    }

    @Override
    public StudyGroupRequest readFrom(Class<StudyGroupRequest> type, Type genericType, Annotation[] annotations,
                                      MediaType mediaType, MultivaluedMap<String, String> headers, InputStream stream)
            throws IOException, WebApplicationException {
        try (JsonReader reader = Json.createReader(stream)) {
            JsonObject root = reader.readObject();
            rejectUnknown(root, GROUP, "");
            object(root, "coordinates", COORDINATES, "coordinates");
            JsonObject admin = object(root, "groupAdmin", PERSON, "groupAdmin");
            if (admin != null) object(admin, "location", LOCATION, "groupAdmin.location");
            return jsonb.fromJson(root.toString(), StudyGroupRequest.class);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw malformed(exception);
        }
    }

    private JsonObject object(JsonObject parent, String name, Set<String> allowed, String path) {
        if (!parent.containsKey(name) || parent.isNull(name)) return null;
        try {
            JsonObject value = parent.getJsonObject(name);
            rejectUnknown(value, allowed, path + ".");
            return value;
        } catch (ClassCastException exception) {
            throw malformed(exception);
        }
    }

    private void rejectUnknown(JsonObject object, Set<String> allowed, String prefix) {
        object.keySet().stream().filter(key -> !allowed.contains(key)).findFirst().ifPresent(key -> {
            throw new ApiException(ErrorCode.MALFORMED_JSON,
                    Map.of("field", prefix + key));
        });
    }

    private ApiException malformed(RuntimeException cause) {
        return new ApiException(ErrorCode.MALFORMED_JSON, Map.of(), cause);
    }
}
