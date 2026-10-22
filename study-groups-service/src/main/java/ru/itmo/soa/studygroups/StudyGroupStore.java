package ru.itmo.soa.studygroups;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

@Repository
public class StudyGroupStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public StudyGroupStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public StudyGroup create(StudyGroupRequest request) {
        String payload = encode(request);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO study_groups (creation_date, payload) VALUES (?, ?)", new String[]{"id"});
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setString(2, payload);
            return statement;
        }, key);
        return find(java.util.Objects.requireNonNull(key.getKey()).intValue());
    }

    public StudyGroup find(int id) {
        List<StudyGroup> groups = jdbc.query("SELECT id, creation_date, payload FROM study_groups WHERE id = ?", this::mapGroup, id);
        if (groups.isEmpty()) throw notFound(id);
        return groups.get(0);
    }

    @Transactional
    public StudyGroup update(int id, StudyGroupRequest request) {
        if (jdbc.update("UPDATE study_groups SET payload = ? WHERE id = ?", encode(request), id) == 0) throw notFound(id);
        return find(id);
    }

    public void delete(int id) {
        if (jdbc.update("DELETE FROM study_groups WHERE id = ?", id) == 0) throw notFound(id);
    }

    public List<StudyGroup> snapshot() {
        return jdbc.query("SELECT id, creation_date, payload FROM study_groups ORDER BY id", this::mapGroup);
    }

    private StudyGroup mapGroup(ResultSet row, int rowNumber) throws SQLException {
        try {
            StudyGroupRequest request = json.readValue(row.getString("payload"), StudyGroupRequest.class);
            return new StudyGroup(row.getInt("id"), row.getTimestamp("creation_date").toInstant(), request);
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
