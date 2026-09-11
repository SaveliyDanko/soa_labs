# Study Groups & ISU API

Реализация на Kotlin и Spring Boot: REST API коллекции `StudyGroup`, сервис
составных операций `/isu`, OpenAPI 3.0 и Swagger UI.

## Запуск

Требуются JDK 17+ и Maven 3.9+.

```bash
mvn spring-boot:run
```

После запуска:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI YAML: <http://localhost:8080/openapi.yaml>
- API коллекции: <http://localhost:8080/api/study-groups>
- API ИСУ: <http://localhost:8080/isu>

По умолчанию данные сохраняются в файловой H2 базе `./data`.

## Ошибки API

Ответы об ошибках содержат стабильное поле `code`, понятное сообщение и контекст в
`details` или `violations`. Основные статусы:

- `400 Bad Request` — неверный path/query-параметр, фильтр, сортировка или сломанный JSON;
- `404 Not Found` — группа или endpoint не найдены;
- `409 Conflict` — операция конфликтует с состоянием данных;
- `415 Unsupported Media Type` — тело передано не как `application/json`;
- `422 Unprocessable Content` — JSON разобран, но значения полей не прошли валидацию;
- `502 Bad Gateway` / `503 Service Unavailable` — ошибочный ответ или недоступность сервиса Study Groups при вызове через `/isu`.

Статус `400` допустим и для `GET`: он относится к ошибке клиентского запроса, а не к
наличию тела или конкретному HTTP-методу. Например, его получат запросы с `id=abc`,
отрицательным номером страницы или неизвестным полем фильтрации.
