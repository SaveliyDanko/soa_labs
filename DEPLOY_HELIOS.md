# Развёртывание лабораторной работы на Helios без Docker

Этот вариант рассчитан на закрытый сервер Helios: сборка выполняется локально,
а на сервер передаётся готовый каталог с распакованными Payara и WildFly. На
Helios не нужны Maven, Docker и доступ в интернет — достаточно полного JDK 17+
и стандартных утилит FreeBSD.

## 1. Схема стенда

| Компонент | Где запускается | Порт на Helios | Локальный порт туннеля |
|---|---|---:|---:|
| Java web client | отдельная JVM | 40820 | 13000 |
| Study Groups API | Payara 6 | 40821 | 18181 |
| ISU API | WildFly 39 | 40822 | 18443 |

Все публичные listener'ы работают только по HTTPS. Незашифрованные HTTP-listener'ы
Payara и WildFly отключаются при конфигурации, а административные порты доступны
только через `127.0.0.1`. WildFly вызывает Payara по HTTPS и проверяет его
сертификат через отдельный truststore.

Примеры ниже используют логин `s408522`. Если лабораторная разворачивается из
другой учётной записи, замените его своим логином.

## 2. Что требуется на локальной машине

- JDK 17 или новее, включая `keytool`;
- Maven 3.9+;
- OpenSSL;
- `bash`, `ssh` и `scp`;
- распакованный Payara 6 (проверено с Payara 6.2025.11);
- распакованный WildFly 39 (проверено с WildFly 39.0.1.Final).

Исходный код можно получить из GitHub:

```bash
git clone git@github.com:SaveliyDanko/soa_labs.git
cd soa_labs
```

Каталоги application server не хранятся в Git: они велики и зависят от
конкретного дистрибутива. Их нужно скачать и распаковать на машине сборки один
раз.

## 3. Подготовка переносимого runtime

Из корня проекта выполните:

```bash
PAYARA_SOURCE=/absolute/path/to/payara6 \
WILDFLY_SOURCE=/absolute/path/to/wildfly-39.0.1.Final \
./scripts/prepare-portable-deployment.sh
```

Скрипт автоматически:

1. выполняет `mvn clean package`;
2. собирает оба WAR и автономный Java-клиент;
3. при необходимости генерирует самоподписанный сертификат;
4. копирует распакованные Payara и WildFly;
5. удаляет из копий старые логи, временные данные и deployment state;
6. создаёт готовый каталог `dist/soa-lab-runtime`.

Его основное содержимое:

```text
soa-lab-runtime/
├── bin/                 # start, stop, restart, status, logs, configure
├── config/
│   ├── runtime.env      # порты и ресурсные лимиты
│   └── tls/             # сертификат, keystore и truststore
├── deployments/         # study-groups.war, isu.war, web-client.jar
├── runtime/
│   ├── payara/
│   └── wildfly/
└── state/               # создаётся/наполняется при первом запуске
```

`dist/` и локально сгенерированные TLS-ключи намеренно исключены из Git. Не
публикуйте приватный файл `server.key`.

Если `dist/soa-lab-runtime` уже существует, подготовительный скрипт сначала
переименует прежний каталог в `soa-lab-runtime.backup.<дата>`. После успешной
проверки новой сборки старую копию можно удалить, чтобы освободить место.

## 4. Настройка портов и ограничений

До отправки откройте `dist/soa-lab-runtime/config/runtime.env` и задайте:

```bash
CLIENT_BIND_ADDRESS=127.0.0.1
CLIENT_HTTPS_PORT=40820

PAYARA_BIND_ADDRESS=127.0.0.1
PAYARA_PORT_BASE=40740
PAYARA_HTTPS_PORT=40821

WILDFLY_BIND_ADDRESS=127.0.0.1
WILDFLY_HTTP_PORT=40823
WILDFLY_HTTPS_PORT=40822
WILDFLY_MANAGEMENT_PORT=40824

TLS_STORE_PASSWORD=changeit
STARTUP_TIMEOUT_SECONDS=90
SHUTDOWN_TIMEOUT_SECONDS=30

ACTIVE_PROCESSOR_COUNT=2
JAVA_THREAD_STACK_SIZE=256k
PAYARA_MAX_HEAP=320m
WILDFLY_MAX_HEAP=320m
CLIENT_MAX_HEAP=96m
CLIENT_WORKER_THREADS=4
```

Payara создаёт несколько внутренних listener'ов относительно
`PAYARA_PORT_BASE`. При базе `40740` административный порт равен `40788`, а
стандартный HTTPS-порт — `40821`. На общем сервере выбирайте свободным весь блок
примерно из ста портов, а не только три публичных значения.

Значение `TLS_STORE_PASSWORD=changeit` для этой сборки менять нельзя без
одновременной пересборки keystore и конфигурации Payara.

Адрес `127.0.0.1` рекомендуется для работы через SSH-туннель: сервисы не будут
видны напрямую из сети. `0.0.0.0` нужен только если правила Helios явно требуют
прямого доступа.

Ресурсный профиль обязателен для Helios. Он ограничивает heap, стек и число
потоков всех трёх JVM, не позволяя application server строить пулы по общему
числу процессоров хоста.

## 5. Первичная передача на Helios

Готовый каталог занимает около 500–600 МБ. Передать его можно скриптом:

```bash
DEPLOY_TARGET=s408522@helios.cs.ifmo.ru \
SSH_PORT=2222 \
./scripts/upload-portable-deployment.sh
```

Или обычным `scp`:

```bash
scp -P 2222 -r dist/soa-lab-runtime \
  s408522@helios.cs.ifmo.ru:~/
```

Передаются именно файлы из готового runtime. Отдельные файлы контрольных сумм,
которые могли лежать рядом со скачанными ZIP-архивами (`*.sha1`, `*.sha256`,
`*.md5`), для запуска не нужны.

## 6. Первый запуск на Helios

Подключитесь к серверу:

```bash
ssh -p 2222 s408522@helios.cs.ifmo.ru
```

Проверьте Java и запустите стенд:

```bash
java -version
cd ~/soa-lab-runtime
chmod 700 bin/*.sh
./bin/start.sh
```

При первом запуске `start.sh` вызывает `configure.sh`, который:

- создаёт изолированный домен Payara в `state/payara`;
- устанавливает TLS-сертификат;
- отключает публичный HTTP;
- применяет низкоресурсные thread pool'ы;
- настраивает HTTPS и truststore WildFly.

Затем последовательно запускаются Payara, WildFly и web client. Скрипт не
переходит к следующему компоненту, пока предыдущий не прошёл health-check.

Успешное окончание выглядит так:

```text
[soa-lab] All services are running
[soa-lab] Client:       https://localhost:40820/
[soa-lab] Study Groups: https://localhost:40821/api/study-groups
[soa-lab] ISU:          https://localhost:40822/isu
```

Код `HTTP 405` при внутренней проверке `/isu/group/0/expel-all` ожидаем: URL
существует, но проверочный запрос использует GET, тогда как операция принимает
POST.

## 7. Управление стендом

Все команды выполняются из `~/soa-lab-runtime`:

```bash
./bin/status.sh       # состояние трёх процессов
./bin/restart.sh      # корректная остановка и повторный запуск
./bin/logs.sh         # объединённый просмотр журналов
./bin/stop.sh         # остановка всех процессов
```

Отдельные журналы находятся здесь:

```text
state/payara/domains/soa-lab/logs/server.log
logs/wildfly.log
runtime/wildfly/standalone/log/server.log
logs/web-client.log
```

После выхода из SSH-сессии сервисы продолжают работать.

## 8. SSH-туннель и открытие клиента

Команду нужно выполнять на локальной машине и оставить её работающей:

```bash
ssh -p 2222 -N \
  -L 13000:127.0.0.1:40820 \
  -L 18181:127.0.0.1:40821 \
  -L 18443:127.0.0.1:40822 \
  s408522@helios.cs.ifmo.ru
```

После этого доступны:

- web client: <https://localhost:13000/>;
- Swagger UI: <https://localhost:13000/swagger-ui.html>;
- Study Groups API: <https://localhost:18181/api/study-groups>;
- ISU API: <https://localhost:18443/isu>.

Сертификат самоподписанный и выпущен для `localhost`/`127.0.0.1`. При первом
открытии браузер покажет предупреждение: можно разово подтвердить переход либо
импортировать `dist/soa-lab-runtime/config/tls/server.crt` в локальное хранилище
доверенных сертификатов.

Сообщение SSH `open failed: connect failed: Connection refused` означает, что
туннель активен, но процесс на соответствующем удалённом порту не запущен. В
этом случае выполните на Helios `./bin/status.sh` и проверьте журналы.

## 9. Проверка

Быстрая проверка непосредственно на Helios:

```bash
curl -ksS https://127.0.0.1:40820/health
curl -ksS 'https://127.0.0.1:40821/api/study-groups?page=0&size=1'
curl -ksS -o /dev/null -w '%{http_code}\n' \
  https://127.0.0.1:40822/isu/group/0/expel-all
```

При активном туннеле на локальной машине можно выполнить полный smoke-тест:

```bash
PAYARA_URL=https://localhost:18181 \
ISU_URL=https://localhost:18443 \
CLIENT_URL=https://localhost:13000 \
PAYARA_HTTP_URL=http://localhost:18181 \
ISU_HTTP_URL=http://localhost:18443 \
./scripts/smoke-test.sh
```

Он создаёт тестовую группу, проверяет фильтрацию и сортировку, вызывает обе
операции ISU через WildFly, открывает ресурсы клиента и убеждается, что сервисы
не принимают незашифрованный HTTP.

## 10. Обновление без повторной передачи application server

После изменения исходного кода локально пересоберите проект:

```bash
mvn clean package
```

Передайте только три небольших артефакта:

```bash
scp -P 2222 \
  study-groups-service/target/study-groups.war \
  isu-service/target/isu.war \
  web-client-server/target/web-client.jar \
  s408522@helios.cs.ifmo.ru:~/soa-lab-runtime/deployments/
```

На Helios примените обновление:

```bash
cd ~/soa-lab-runtime
./bin/restart.sh
```

`start.sh` выполняет принудительный redeploy Payara WAR и заново помещает WAR в
WildFly, поэтому новая версия действительно заменяет прежнюю.

Если менялись управляющие скрипты или шаблон WildFly, дополнительно передайте:

```bash
scp -P 2222 portable/bin/*.sh \
  s408522@helios.cs.ifmo.ru:~/soa-lab-runtime/bin/
scp -P 2222 portable/config/configure-wildfly.cli.template \
  s408522@helios.cs.ifmo.ru:~/soa-lab-runtime/config/
```

Не перезаписывайте удалённый `config/runtime.env`, если в нём уже настроены ваши
порты. После изменения серверной конфигурации выполните:

```bash
cd ~/soa-lab-runtime
chmod 700 bin/*.sh
./bin/stop.sh || true
./bin/configure.sh --force
./bin/start.sh
```

## 11. Диагностика проблем с ресурсами

Ошибка

```text
OutOfMemoryError: unable to create native thread
```

не означает переполнение Java heap. Процесс не смог создать системный поток из-за
квоты пользователя или нехватки доступной памяти. Глобальный `_JAVA_OPTIONS` на
Helios может показывать `MaxHeapSize=1G`, но явные параметры portable-скриптов
имеют приоритет и задают меньшие значения для каждой JVM.

FreeBSD-совместимые команды для диагностики:

```bash
ulimit -u
ps -U "$USER" -H | wc -l
ps -U "$USER" -o pid,rss,command
pgrep -U "$USER" -fl java
```

Если остались старые JVM от предыдущих запусков, сначала попытайтесь остановить
их штатным `./bin/stop.sh`. Не используйте широкие команды `killall java` на
общем аккаунте, если там могут работать другие приложения.

Если `start.sh` завершился с ошибкой, соберите сведения:

```bash
./bin/status.sh || true
tail -n 150 state/payara/domains/soa-lab/logs/server.log
tail -n 150 logs/wildfly.log
tail -n 150 runtime/wildfly/standalone/log/server.log
tail -n 150 logs/web-client.log
```

Отсутствие `logs/wildfly.log` означает, что запуск остановился ещё на этапе
Payara. В таком случае основная причина находится в журнале Payara или в выводе
`asadmin`.

## 12. Полное удаление стенда

Сначала корректно остановите процессы:

```bash
cd ~/soa-lab-runtime
./bin/stop.sh
```

После этого каталог `~/soa-lab-runtime` можно удалить вручную. Вместе с ним будут
удалены application server, сертификаты, журналы и текущее in-memory состояние.
Сами исходники в GitHub и локальный каталог проекта от этого не изменятся.
