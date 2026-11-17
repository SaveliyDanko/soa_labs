package ru.itmo.soa.studygroups;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

@Repository
public class StudyGroupStore {
    private final DatabaseClient database;
    private final ObjectMapper json;

    public StudyGroupStore(DatabaseClient database, ObjectMapper json) {
        this.database = database;
        this.json = json;
    }

    @Transactional
    public Mono<StudyGroup> create(StudyGroupRequest request) {
        return Mono.defer(() -> database.sql("INSERT INTO study_groups (creation_date, payload) VALUES (:date, :payload)")
                .bind("date", OffsetDateTime.now(ZoneOffset.UTC)).bind("payload", encode(request))
                .filter(statement -> statement.returnGeneratedValues("id"))
                .map((row, metadata) -> row.get("id", Integer.class)).one()
                .flatMap(this::find));
    }

    public Mono<StudyGroup> find(int id) {
        return database.sql("SELECT id, creation_date, payload FROM study_groups WHERE id = :id")
                .bind("id", id).map((row, metadata) -> mapGroup(row)).one()
                .switchIfEmpty(Mono.error(notFound(id)));
    }

    @Transactional
    public Mono<StudyGroup> update(int id, StudyGroupRequest request) {
        return Mono.defer(() -> database.sql("UPDATE study_groups SET payload = :payload WHERE id = :id")
                .bind("payload", encode(request)).bind("id", id).fetch().rowsUpdated()
                .flatMap(count -> count == 0 ? Mono.error(notFound(id)) : find(id)));
    }

    public Mono<Void> delete(int id) {
        return database.sql("DELETE FROM study_groups WHERE id = :id").bind("id", id).fetch().rowsUpdated()
                .flatMap(count -> count == 0 ? Mono.error(notFound(id)) : Mono.empty());
    }

    public Flux<StudyGroup> snapshot() {
        return database.sql("SELECT id, creation_date, payload FROM study_groups ORDER BY id")
                .map((row, metadata) -> mapGroup(row)).all();
    }

    private StudyGroup mapGroup(Row row) {
        try {
            StudyGroupRequest request = json.readValue(row.get("payload", String.class), StudyGroupRequest.class);
            return new StudyGroup(row.get("id", Integer.class),
                    row.get("creation_date", OffsetDateTime.class).toInstant(), request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось прочитать учебную группу из хранилища", exception);
        }
    }

    private String encode(StudyGroupRequest request) {
        try {
            return json.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сохранить учебную группу", exception);
        }
    }

    private ApiException notFound(int id) {
        return new ApiException(ErrorCode.STUDY_GROUP_NOT_FOUND, Map.of("id", Integer.toString(id)));
    }
}
