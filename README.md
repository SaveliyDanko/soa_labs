# Лабораторная работа №3 — Study Groups & ISU

Основа: lab-2, ветка `feature/payara-wildfly`, коммит `55d081b`.
Оба сервиса перенесены с Jakarta EE на Spring Boot; контракт предыдущих
лабораторных, браузерный клиент и OpenAPI сохранены.

## Архитектура

```text
Браузер — HTTPS → Nginx (клиент и TLS) → Zuul API Gateway
                                        ├─ Ribbon + Eureka
                                        │   ├─ study-groups-1:8181 ─┐
                                        │   └─ study-groups-2:8182 ─┴─ PostgreSQL
                                        └─ HAProxy:9000
                                            ├─ isu-1:8281
                                            └─ isu-2:8282
                                                   └─ Zuul → study-groups

Config Server:8888 → общие настройки, study-groups, ISU, Gateway
Eureka:8761       ← регистрация обоих экземпляров study-groups
```

| Требование | Реализация |
|---|---|
| Config Service | Spring Cloud Config Server, файловый native-репозиторий `config-repo/` |
| Service Discovery | Eureka; регистрация, heartbeat и динамический список экземпляров |
| Load Balancer вызываемого сервиса | Ribbon в Zuul, правило RoundRobinRule |
| API Gateway | Zuul; API, Swagger и межсервисные вызовы проходят через него |
| Вызывающий сервис на Spring Boot | Два процесса ISU, порты 8281 и 8282 |
| Балансировка вызывающего сервиса | HAProxy, roundrobin и HTTP health checks |
| Согласованность экземпляров | Общая PostgreSQL вместо отдельных коллекций в памяти |

Используются Java 11, Spring Boot 2.3.12.RELEASE и Spring Cloud Hoxton.SR12.
Это намеренно совместимая legacy-линейка: требуемые заданием Ribbon и Zuul 1
не входят в современные версии Spring Cloud. Для учебного стенда, не для
публикации в интернете. Совместимость линейки описана в
[релизе Hoxton.SR12](https://spring.io/blog/2021/07/07/spring-cloud-hoxton-sr12-has-been-released/).

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

Порты 3000, 8181 и 8443 ведут в один HTTPS-вход, далее — в Zuul.
Порты экземпляров, PostgreSQL и самого Zuul на хост **не публикуются**.
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
`X-Gateway: zuul` — прохождение через gateway.
По серии запросов видны обе реплики каждой пары. При выключении реплики
Eureka/Ribbon обновляют реестр не мгновенно; скрипт учитывает время сходимости.
HAProxy и Nginx повторно разрешают имена через Docker DNS, поэтому смена IP
после перезапуска контейнеров не требует перезапуска балансировщиков.
Автоматические повторные попытки Ribbon отключены, чтобы POST/PUT/DELETE
не выполнялись повторно при неопределённом результате сетевого запроса.

Центральные настройки находятся в `config-repo/*.yml`; при запуске клиенты
обращаются к Config Server и завершаются с ошибкой при его недоступности.
Config Server разрешает переопределять значения переменными окружения: это
позволяет задать отдельный порт и идентификатор каждой реплики.
После изменения файлов перезапустите потребителей:
`docker compose restart study-groups-1 study-groups-2 isu-1 isu-2 api-gateway`.
Автоматическое горячее обновление через Bus не реализовано и заданием не требуется.

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
обработчики Spring MVC. Ошибки имеют поля `timestamp`, `status`, `code`,
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

Тесты покрывают сортировку/фильтрацию, бизнес-операции, Spring MVC, JSON,
русские ошибки, CORS, Location за HTTPS-прокси, хранение через два экземпляра
репозитория и HTTP-клиент ISU. Для тестов БД используется H2;
интеграционная проверка стенда использует настоящую PostgreSQL.

Старые WAR-дескрипторы, сборка Payara/WildFly и переносимый Java-прокси заменены
новым способом запуска. Вариант lab-2 и его инструкция Helios остаются в ветке
`feature/payara-wildfly`; старые скрипты не подходят к JAR-приложениям lab-3.
