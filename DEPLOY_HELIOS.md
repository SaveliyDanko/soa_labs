# Деплой на Helios

## 1. Сборка и загрузка

Выполнить локально из каталога проекта:

```bash
mvn clean package
ssh -p 2222 sxxxxxx@helios.cs.ifmo.ru 'mkdir -p ~/soa-lab1/data'
scp -P 2222 target/study-groups-service-1.0.0.jar \
  sxxxxxx@helios.cs.ifmo.ru:~/soa-lab1/application.jar
```

## 2. Запуск

```bash
ssh -p 2222 sxxxxxx@helios.cs.ifmo.ru
cd ~/soa-lab1

nohup env \
  SERVER_ADDRESS=127.0.0.1 \
  SERVER_PORT=40822 \
  DB_URL='jdbc:h2:file:./data/study-groups;AUTO_SERVER=TRUE' \
  STUDY_GROUPS_BASE_URL='http://127.0.0.1:40822' \
  java -jar application.jar > application.log 2>&1 &

echo $! > application.pid
tail -n 50 application.log
```

На сервере должна быть установлена Java 17 или новее (`java -version`).

## 3. Проверка

На Helios:

```bash
curl -L -o /dev/null -s -w '%{http_code}\n' \
  http://127.0.0.1:40822/swagger-ui.html
```

Ожидаемый HTTP-код — `200`.

На локальном компьютере открыть SSH-туннель:

```bash
ssh -p 2222 -N -L 18080:127.0.0.1:40822 \
  sxxxxxx@helios.cs.ifmo.ru
```

Swagger UI: <http://localhost:18080/swagger-ui.html>

OpenAPI YAML: <http://localhost:18080/openapi.yaml>

## Остановка

```bash
ssh -p 2222 sxxxxxx@helios.cs.ifmo.ru
cd ~/soa-lab1
kill "$(cat application.pid)"
```
