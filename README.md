# Study Groups & ISU

Лабораторная реализация состоит из двух независимых JAX-RS-сервисов и браузерного
клиента. Spring в проекте не используется.

| Компонент | Среда | Публичный HTTPS URL по умолчанию |
|---|---|---|
| `study-groups-service` | Payara 6 / Jakarta EE 10 | `https://localhost:8181/api/study-groups` |
| `isu-service` | WildFly 39 / Jakarta EE 10 | `https://localhost:8443/isu` |
| `web-client` | автономный Java 17 HTTPS-сервер + HTML/CSS/JavaScript | `https://localhost:3000` |

Модуль `common-api` содержит общий JSON-контракт. Первый сервис хранит коллекцию в
потокобезопасном application-scoped хранилище; данные живут до перезапуска Payara.
ID и `creationDate` генерируются исключительно сервером.

## Что реализовано

- CRUD для `StudyGroup` с кодами `201`, `200`, `204`, `400`, `404`, `415` и `422`;
- фильтрация по любому полю из спецификации, несколько `filter` объединяются через AND;
- многоуровневая сортировка повторяемым параметром `sort`;
- пагинация `page`/`size` и детерминированный порядок;
- максимум `groupAdmin`, подсчёт администраторов и поиск по подстроке имени;
- операции ISU `expel-all` и `change-edu-form`, которые действительно вызывают первый сервис по HTTPS;
- JSON-ошибки со стабильным `code`, `details` или `violations`;
- строгий JSON: неизвестные поля не игнорируются;
- CORS/preflight для прямых вызовов API;
- Swagger UI с документацией обоих сервисов;
- единый клиент для всех операций API, включая произвольные sort/filter и пагинацию.

## Запуск без Docker

На целевой машине нужен только полный JDK 17+ и стандартный `bash`. Payara и
WildFly передаются туда уже распакованными; Maven, Docker и доступ в интернет на
сервере не нужны.

На машине сборки укажите каталоги заранее распакованных Payara и WildFly:

```bash
PAYARA_SOURCE=/opt/payara6 \
WILDFLY_SOURCE=/opt/wildfly-39 \
./scripts/prepare-portable-deployment.sh
```

Скрипт собирает два WAR и Java-клиент, генерирует самоподписанный сертификат и
создаёт переносимый каталог `dist/soa-lab-runtime`. В нём уже находятся оба
application server, артефакты, TLS-конфигурация и скрипты управления. Скопируйте
этот каталог целиком, отредактируйте `config/runtime.env` и запустите:

```bash
./bin/start.sh
```

При первом запуске автоматически создаётся отдельный домен Payara и офлайн
настраиваются HTTPS-listener'ы обоих серверов. Повторный запуск идемпотентен.
Доступны команды `stop.sh`, `restart.sh`, `status.sh` и `logs.sh` в том же
каталоге `bin`.

В `config/runtime.env` включён профиль для серверов с жёсткими квотами:
небольшие heap и стеки потоков, `ActiveProcessorCount=2`, ограниченные пулы
Payara/WildFly и отключённый Hazelcast. Это предотвращает
`OutOfMemoryError: unable to create native thread`, который относится к
нативным потокам и лимитам ОС, а не к переполнению Java heap.

После запуска доступны:

- клиент: <https://localhost:3000/>;
- Swagger UI: <https://localhost:3000/swagger-ui.html>;
- Payara API: <https://localhost:8181/api/study-groups>;
- WildFly API: <https://localhost:8443/isu>.

HTTP-listener'ы Payara и WildFly отключены, а административные listener'ы доступны
только через loopback. Дополнительно `web.xml` обоих WAR требует `CONFIDENTIAL`
transport guarantee. Браузеру нужно один раз доверить
`config/tls/server.crt` из переносимого каталога.

Проверка запущенного стенда:

```bash
./scripts/smoke-test.sh
```

Остановка:

```bash
./bin/stop.sh
```

Подробная инструкция для Helios находится в [`DEPLOY_HELIOS.md`](DEPLOY_HELIOS.md).

## Локальный запуск через Docker (необязательно)

Контейнерный способ оставлен для разработки и интеграционных проверок:

```bash
./scripts/start-local.sh
./scripts/smoke-test.sh
docker compose down
```

Он требует Maven 3.9+, Docker и Docker Compose.

## API

Исходная спецификация: [`src/main/resources/static/openapi.yaml`](src/main/resources/static/openapi.yaml).

Сортировка задаётся как `field,asc` или `field,desc`; параметр можно повторять:

```text
GET /api/study-groups?sort=formOfEducation,asc&sort=studentsCount,desc
```

Фильтр имеет вид `field:operator:value`. Допустимы `eq`, `ne`, `gt`, `ge`, `lt`,
`le`, `contains`; несколько фильтров объединяются логическим AND:

```text
GET /api/study-groups?page=0&size=10&filter=name:contains:math&filter=studentsCount:ge:10
```

Поддерживаются поля `id`, `name`, `coordinates.x`, `coordinates.y`,
`creationDate`, `studentsCount`, `formOfEducation`, `semesterEnum`,
`groupAdmin.name`, `groupAdmin.birthday`, `groupAdmin.hairColor`,
`groupAdmin.nationality`, `groupAdmin.location.x`, `groupAdmin.location.y` и
`groupAdmin.location.z`.

## Тесты

```bash
mvn clean test package
```

Модульные тесты проверяют CRUD, составные фильтры/сортировку/пагинацию, специальные
операции и делегирование второго сервиса. `smoke-test.sh` дополнительно проверяет
оба развёрнутых application server, TLS-вызов между ними, клиент и Swagger UI.
