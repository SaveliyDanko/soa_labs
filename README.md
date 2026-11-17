# Лабораторная работа №4 — SOAP, Mule ESB, Kubernetes

Основа — реактивная лабораторная №3, коммит `d3d6a77`.
[Условия и уточнённый объём работы](docs/lab4-requirements.md).

```text
Браузер → Ingress TLS / port-forward → Nginx → Spring Cloud Gateway
                                               ├─ /api/study-groups → Eureka + LoadBalancer
                                               │                       └─ Study Groups ×2 → PostgreSQL + PVC
                                               ├─ /isu → REST-адаптер ×2
                                               │          └─ SOAP → Mule ESB → SOAP ISU ×2 (Tomcat 9)
                                               │                                  └─ REST → Mule ESB
                                               │                                              └─ Gateway → Study Groups
                                               └─ /soap/isu → Mule ESB → SOAP ISU

ConfigMap → Config Server → настройки приложений
Secret → пароль PostgreSQL и необязательный Sentry DSN
```

SOAP ISU — WAR с Apache CXF 3.6.13 / JAX-WS на **Apache Tomcat 9**.
SOAP 1.1, document/literal wrapped; классы генерируются из [WSDL](isu-soap-contract/src/main/resources/wsdl/isu.wsdl).
В SOAP-сервисе остались проверка параметров и бизнес-операции: удаление опустевшей группы
и изменение только формы обучения. Study Groups, его API, OpenAPI, Reactor Netty,
зависимости, настройки и код браузерного клиента не изменены.
Java-приложения сохраняют Java 11 / Spring Boot 2.7.18 / Spring Cloud 2021.0.9.
Mule Kernel 4.9.0 работает отдельно на Java 17.

## Контракты

REST-адаптер содержит только HTTP-контроллер, SOAP-клиент и преобразование форматов.
В нём нет бизнес-валидации, доступа к БД или вызовов Study Groups. Каждый запрос вызывает одну SOAP-операцию:

| Прежний REST-адрес | SOAP-операция |
|---|---|
| `POST /isu/group/{id}/expel-all` | `expelAll(groupId)` |
| `POST /isu/group/{id}/change-edu-form/{newForm}` | `changeEducationForm(groupId, newForm)` |

Типизированный SOAP Fault преобразуется в прежний HTTP-код и русский JSON:
некорректный ID — 400, отсутствие группы — 404, сбой ESB — 503.
Даты `xs:dateTime` сохраняют дробную часть секунд. Повторы изменяющих запросов отключены.
SOAP доступен на `/soap/isu`, WSDL — `/soap/isu?wsdl`.
[Примеры SOAP-запросов](docs/soap-examples.md).

[Mule-приложение](mule-esb/src/main/mule/integration.xml) содержит потоки `isu-soap-proxy`
и `study-groups-rest-proxy`. Первый передаёт SOAP/WSDL/Fault, второй — GET/PUT/DELETE
от SOAP ISU через Gateway в Study Groups. Бизнес-логики в ESB нет.
Используется настоящий Mule Kernel и HTTP Connector из публичного репозитория MuleSoft.
Его ответы содержат `X-ESB: mule`, `X-ESB-Flow`; SOAP также содержит `X-SOAP-Instance`.

## Локальный Kubernetes

Нужны Docker с BuildKit, Bash, [kind](https://kind.sigs.k8s.io/docs/user/quick-start/),
[kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/) и Python 3.
Maven и JDK на хосте не нужны. Желательно около 6 ГБ свободной RAM.

```bash
export DB_PASSWORD='replace-with-your-local-password'
./scripts/start-kind.sh
export KUBECONFIG="$PWD/.local/kubeconfig"
kubectl -n soa-lab4 port-forward service/client 3000:8080
```

В другом терминале: `./scripts/smoke-test.sh`.
Клиент: http://localhost:3000/; Swagger: http://localhost:3000/swagger-ui.html;
WSDL через Mule: http://localhost:3000/soap/isu?wsdl.
Port-forward использует HTTP; для внешнего HTTPS предусмотрен [Ingress](k8s/ingress.yaml).
`CLIENT_URL` меняет адрес проверки. `ALLOW_SELF_SIGNED_TLS=1` разрешает учебный сертификат.

Первый запуск скачивает Kubernetes, JDK, Maven-зависимости и Mule; повторный использует cache.
`start-kind.sh` создаёт отдельный kubeconfig `.local/kubeconfig`, собирает и загружает
образы в kind, применяет манифесты и ожидает готовности. Другие контексты не переключает.

## Передача для раскатки

[Инструкция оператору](docs/deployment.md): registry, секреты, Kustomize, HTTPS,
диагностика, установка Mule на отдельном сервере / Helios.

```bash
./scripts/build-images.sh
kubectl kustomize .
./scripts/deploy-k8s.sh
```

`deploy-k8s.sh` работает в **текущем kubectl-контексте**, namespace `soa-lab4`.
`DB_PASSWORD` обязателен; для существующего PVC нужен прежний пароль. `SENTRY_DSN` необязателен.
Секреты не хранятся в Git. ConfigMap содержит конфигурационный репозиторий Config Server.

В Kubernetes настроены Deployment, ClusterIP Service, startup/readiness/liveness probes,
requests/limits, RollingUpdate и PodDisruptionBudget для пар реплик.
PostgreSQL использует StatefulSet с PVC. Учебные PostgreSQL, Config Server, Eureka,
Gateway и Mule имеют по одной реплике; это не полная конфигурация высокой доступности.
Docker Compose и HAProxy заменены Kubernetes; вариант №3 сохранён в ветке `codex/lab-3-microservices`.

## Проверки

```bash
mvn clean verify                              # Java 11 и Maven 3.9
python3 scripts/verify-lab4.py                 # работающий стенд
node --test web-client/test/errors.test.cjs   # при установленном Node.js
```

Java-тесты покрывают неизменённый Study Groups, реальный SOAP endpoint на Tomcat,
WSDL/Fault, вложенные поля, точность дат, REST ↔ SOAP и недоступный ESB.
Сквозной скрипт проверяет цепочку с настоящим Mule и PostgreSQL, прежние REST-адреса,
оба SOAP-метода, ошибки и документацию. Он создаёт и удаляет только собственные тестовые группы.
