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
