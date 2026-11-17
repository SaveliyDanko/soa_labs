# Лабораторная работа №3 — Study Groups & ISU

Основа: lab-2, ветка `feature/payara-wildfly`, коммит `55d081b`.
Оба сервиса перенесены с Jakarta EE на Spring Boot; контракт предыдущих
лабораторных, браузерный клиент и OpenAPI сохранены.

## Архитектура

```text
Браузер — HTTPS → Nginx (клиент и TLS) → Spring Cloud Gateway (WebFlux)
                                        ├─ LoadBalancer + Eureka
                                        │   ├─ study-groups-1:8181 ─┐
                                        │   └─ study-groups-2:8182 ─┴─ PostgreSQL
                                        └─ HAProxy:9000
                                            ├─ isu-1:8281
                                            └─ isu-2:8282
                                                   └─ Gateway → study-groups

Config Server:8888 → общие настройки, study-groups, ISU, Gateway
Eureka:8761       ← регистрация обоих экземпляров study-groups
```

| Требование | Реализация |
|---|---|
| Config Service | Spring Cloud Config Server, файловый native-репозиторий `config-repo/` |
| Service Discovery | Eureka; регистрация, heartbeat и динамический список экземпляров |
| Load Balancer вызываемого сервиса | Spring Cloud LoadBalancer, RoundRobinLoadBalancer |
| API Gateway | Spring Cloud Gateway; API, Swagger и межсервисные вызовы проходят через него |
| Вызывающий сервис на Spring Boot | Два процесса ISU, порты 8281 и 8282 |
| Балансировка вызывающего сервиса | HAProxy, roundrobin и HTTP health checks |
| Согласованность экземпляров | Общая PostgreSQL вместо отдельных коллекций в памяти |

Используются Java 11, Spring Boot 2.7.18 и Spring Cloud 2021.0.9
([совместимость версий](https://spring.io/blog/2023/12/20/spring-cloud-2021-0-9-aka-jubilee-is-now-available/)).
Шлюз и оба прикладных сервиса работают на WebFlux / Reactor Netty:
контроллеры и сервисы возвращают Mono/Flux, межсервисный HTTP использует WebClient,
PostgreSQL — R2DBC с пулом соединений. JDBC, RestTemplate и Servlet-фильтры
удалены из цепочки прикладных запросов.

Zuul 1 и Ribbon из исходного задания заменены на Spring Cloud Gateway и
Spring Cloud LoadBalancer: старый шлюз не поддерживает реактивную обработку.
Config Server и Eureka остаются инфраструктурными Servlet-приложениями,
поскольку их серверные реализации не поддерживают WebFlux. Это исключение
не затрагивает обработку прикладного API.

Для сохранения Java 11 используется завершившая открытую поддержку линейка
Spring Boot 2.7; переход на актуальную линейку требует отдельного обновления JDK.
Фильтрация, сортировка и пагинация пока выполняются в памяти после реактивного
чтения коллекции: I/O не блокируется, но память и CPU зависят от размера коллекции.

## Запуск

Нужны Docker с Compose v2+ (BuildKit) и OpenSSL. Maven и JDK на хосте не нужны:
Docker собирает JAR и выполняет Java-тесты под Java 11.
Для скрипта интеграционной проверки нужен Python 3 без сторонних пакетов.

```bash
./scripts/start-local.sh
./scripts/smoke-test.sh
```

Первая сборка скачивает образы и Maven-зависимости. Запуск ждёт готовности
сервисов; первоначальная регистрация в Eureka может занять ещё несколько секунд.
Для самоподписанного сертификата разрешите открытие localhost в браузере.

| Назначение | URL по умолчанию |
|---|---|
| Клиент | https://localhost:3000/ |
| Swagger | https://localhost:3000/swagger-ui.html |
| API групп | https://localhost:3000/api/study-groups |
| ISU | https://localhost:3000/isu |
| Совместимый адрес lab-2, группы | https://localhost:8181/api/study-groups |
| Совместимый адрес lab-2, ISU | https://localhost:8443/isu |
| Eureka (только loopback) | http://localhost:8761/ |
| Config Server (только loopback) | http://localhost:8888/study-groups-service/default |
| Статистика HAProxy (только loopback) | http://localhost:9001/stats |

Порты 3000, 8181 и 8443 ведут в один HTTPS-вход, далее — в Spring Cloud Gateway.
Порты экземпляров, PostgreSQL и самого Gateway на хост **не публикуются**.
Внутри Docker-сети используется HTTP; TLS завершается на Nginx.
Административные endpoints Config/Eureka/HAProxy не являются публичным API.
Имена переменных `PAYARA_HTTPS_PORT` и `WILDFLY_HTTPS_PORT` сохранены только
для совместимости настроек запуска; серверов Payara/WildFly в lab-3 нет.

Настройка портов: `CLIENT_HTTPS_PORT`, `PAYARA_HTTPS_PORT`,
`WILDFLY_HTTPS_PORT`, `CONFIG_PORT`, `EUREKA_PORT`, `HAPROXY_STATS_PORT`.
Пароль БД: `DB_PASSWORD` (учебное значение по умолчанию `soa-lab`).
Пароль при повторном запуске должен соответствовать уже созданной БД.

```bash
docker compose ps
docker compose logs -f api-gateway study-groups-1 isu-1
docker compose down
```

Обычный `down` сохраняет данные в именованном томе PostgreSQL.
Не используйте `down -v`, если данные нужно сохранить.

## Проверка компонентов и отказоустойчивости

```bash
# Контракт API, Config, Eureka, обе балансировки, общая БД, CORS и Swagger
python3 scripts/verify-lab3.py

# Дополнительно: отключение отдельных реплик и полный отказ upstream
python3 scripts/verify-lab3.py --failover
```

Скрипт создаёт только собственные тестовые группы и удаляет их при завершении.
Режим `--failover` временно останавливает в том числе обе реплики сервиса:
запускайте его только на тестовом стенде, где допустимо прерывание API.
Остановленные экземпляры восстанавливаются в блоках `finally`.
Ответы прикладных сервисов содержат `X-Service-Instance`;
`X-Config-Source: spring-cloud-config` подтверждает получение общей настройки,
`X-Gateway: spring-cloud-gateway` — прохождение через gateway.
По серии запросов видны обе реплики каждой пары. При выключении реплики
Eureka/LoadBalancer обновляют реестр не мгновенно; скрипт учитывает время сходимости.
HAProxy и Nginx повторно разрешают имена через Docker DNS, поэтому смена IP
после перезапуска контейнеров не требует перезапуска балансировщиков.
Автоматические повторные попытки LoadBalancer и HTTP-транспорта отключены, чтобы POST/PUT/DELETE
не выполнялись повторно при неопределённом результате сетевого запроса.

Центральные настройки находятся в `config-repo/*.yml`; при запуске клиенты
обращаются к Config Server и завершаются с ошибкой при его недоступности.
Config Server разрешает переопределять значения переменными окружения: это
позволяет задать отдельный порт и идентификатор каждой реплики.
После изменения файлов перезапустите потребителей:
`docker compose restart study-groups-1 study-groups-2 isu-1 isu-2 api-gateway`.
Автоматическое горячее обновление через Bus не реализовано и заданием не требуется.

## Sentry

Во всех пяти типах Java-приложений подключён `sentry-spring-boot-starter`
(для Spring Boot 2, без Jakarta). Шлюз, study-groups и ISU используют
[интеграцию Sentry с WebFlux](https://github.com/getsentry/sentry-java/blob/8.61.1/sentry-spring-boot/src/main/java/io/sentry/spring/boot/SentryWebfluxAutoConfiguration.java).
Общий обработчик API отправляет серверные ошибки 5xx в Sentry ровно один раз,
используя scope текущего запроса после асинхронного I/O. Ожидаемые ошибки
валидации и 4xx в Sentry не отправляются. Сбои соединения и HTML-ошибки HAProxy
также регистрируются. Ошибки, уже обработанные upstream и возвращённые в JSON,
шлюз пропускает без повторной регистрации.

1. Создайте проект Java / Spring Boot в Sentry и скопируйте его DSN.
2. Скопируйте `.env.example` в `.env` и задайте `SENTRY_DSN`.
3. Запустите или пересоздайте приложения: `docker compose up -d --build`.

Переменные:

| Переменная | Назначение / значение по умолчанию |
|---|---|
| `SENTRY_DSN` | Адрес проекта; пустое значение отключает отправку событий |
| `SENTRY_ENVIRONMENT` | Окружение, `local` |
| `SENTRY_RELEASE` | Версия релиза, `soa-lab3@3.0.0` |
| `SENTRY_TRACES_SAMPLE_RATE` | Доля транзакций от 0 до 1, `0.0` (трейсинг отключён) |

События прикладного API содержат теги `service`, `instance`, `error.code`,
`http.status_code`, `http.method`. `server-name` различает реплики.
По умолчанию отправка PII выключена. Для локального запуска JAR экспортируйте
те же переменные окружения; значения в `.env` автоматически читает только Compose.
Без реального DSN можно проверить интеграцию Java-тестами, которые подменяют
Sentry scopes и проверяют 5xx, отсутствие дублей и игнорирование 4xx.
Не храните локальную `.env` в Git.


## Совместимость API

Спецификация: [openapi.yaml](src/main/resources/static/openapi.yaml).
Сохранены CRUD, пагинация, повторяемые sort/filter, специальные операции и ISU.
ID и creationDate назначает сервер; обновление не меняет дату создания.
Данные доступны через любую реплику и сохраняются после перезапуска.

```text
GET /api/study-groups?sort=formOfEducation,asc&sort=studentsCount,desc
GET /api/study-groups?page=0&size=10&filter=name:contains:math&filter=studentsCount:ge:10
POST /isu/group/{id}/expel-all
POST /isu/group/{id}/change-edu-form/{newForm}
```

Общий `common-api` задаёт строгую JSON-сериализацию, валидацию, CORS и
обработчики WebFlux. Ошибки имеют поля `timestamp`, `status`, `code`,
`message`, `path` и при необходимости `details`/`violations`.
Человекочитаемые сообщения — на русском, включая сетевые сбои;
HTTP-заголовок `Content-Language: ru`. Некорректный JSON: 400,
нарушения модели: 422, отсутствие группы: 404, недоступный upstream: 503.
Стектрейсы не возвращаются клиенту.

Браузерный клиент использует same-origin адреса: preflight в таком случае
не нужен. При обращении с другого origin OPTIONS обрабатывается на gateway.

## Тесты без запуска стенда

При установленном **JDK 11** и Maven 3.9:

```bash
mvn clean verify
node --test web-client/test/errors.test.cjs
```

Тесты покрывают сортировку/фильтрацию, бизнес-операции, WebFlux, JSON,
русские ошибки, CORS, Location за HTTPS-прокси, хранение через два экземпляра
репозитория и HTTP-клиент ISU. Для тестов БД используется R2DBC H2;
интеграционная проверка стенда использует настоящую PostgreSQL.

Старые WAR-дескрипторы, сборка Payara/WildFly и переносимый Java-прокси заменены
новым способом запуска. Вариант lab-2 и его инструкция Helios остаются в ветке
`feature/payara-wildfly`; старые скрипты не подходят к JAR-приложениям lab-3.
