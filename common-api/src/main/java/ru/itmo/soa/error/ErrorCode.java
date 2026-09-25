package ru.itmo.soa.error;

/** Стабильные коды ошибок и сообщения, общие для обоих сервисов. */
public enum ErrorCode {
    BAD_REQUEST(400, "Некорректный запрос"),
    MALFORMED_JSON(400, "Тело запроса содержит некорректный JSON, неизвестное поле или недопустимое значение"),
    MISSING_PARAMETER(400, "Отсутствует обязательный параметр"),
    INVALID_PARAMETER_TYPE(400, "Параметр имеет неверный тип"),
    INVALID_PARAMETER_VALUE(400, "Значение параметра не соответствует допустимым ограничениям"),
    INVALID_FILTER_FORMAT(400, "Фильтр должен содержать поле, оператор и значение, разделённые двоеточием"),
    INVALID_FILTER_FIELD(400, "Указано неизвестное поле фильтрации"),
    INVALID_FILTER_OPERATOR(400, "Оператор фильтрации неизвестен или неприменим к указанному полю"),
    INVALID_FILTER_VALUE(400, "Значение фильтра не соответствует типу поля"),
    INVALID_SORT_FORMAT(400, "Сортировка должна содержать поле и, при необходимости, направление asc или desc через запятую"),
    INVALID_SORT_FIELD(400, "Указано неизвестное поле сортировки"),
    UNAUTHORIZED(401, "Для выполнения запроса необходимо пройти аутентификацию"),
    FORBIDDEN(403, "Недостаточно прав для выполнения запроса"),
    ENDPOINT_NOT_FOUND(404, "Запрошенный адрес не существует"),
    STUDY_GROUP_NOT_FOUND(404, "Учебная группа не найдена"),
    NO_GROUP_WITH_ADMIN(404, "Нет групп с назначенным администратором"),
    METHOD_NOT_ALLOWED(405, "HTTP-метод не поддерживается для этого адреса"),
    NOT_ACCEPTABLE(406, "Запрошенный формат ответа не поддерживается"),
    DATA_INTEGRITY_VIOLATION(409, "Операция конфликтует с текущим состоянием хранилища"),
    UNSUPPORTED_MEDIA_TYPE(415, "Ожидается тело запроса в формате application/json"),
    VALIDATION_FAILED(422, "Поля запроса не прошли проверку"),
    INTERNAL_SERVER_ERROR(500, "Внутренняя ошибка сервера"),
    UPSTREAM_BAD_RESPONSE(502, "Сервис учебных групп вернул некорректный ответ"),
    STUDY_GROUPS_SERVICE_UNAVAILABLE(503, "Сервис учебных групп временно недоступен"),
    HTTP_ERROR(500, "Не удалось обработать HTTP-запрос");

    private final int status;
    private final String message;

    ErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int status() { return status; }
    public String message() { return message; }

    public static ErrorCode forHttpStatus(int status) {
        return switch (status) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> ENDPOINT_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> DATA_INTEGRITY_VIOLATION;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 500 -> INTERNAL_SERVER_ERROR;
            default -> HTTP_ERROR;
        };
    }
}
