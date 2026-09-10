# Деплой на Helios

## 1. Сборка и загрузка

Выполнить локально из каталога проекта:

```bash
mvn clean package
```

ssh -p 2222 s408145@helios.cs.ifmo.ru 'mkdir -p ~/soa-lab1/data'

```bash
scp -P 2222 target/study-groups-service-1.0.0.jar s408145@helios.cs.ifmo.ru:~/soa-lab1/application.jar
```

## 2. Запуск

```bash
ssh -p 2222 s408145@helios.cs.ifmo.ru
```

```bash
cd ~/soa-lab1
```

```bash
java -jar application.jar --spring.profiles.active=server 
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
ssh -p 2222 -N -L 18080:127.0.0.1:40822 s408145@helios.cs.ifmo.ru
```

Swagger UI: <http://localhost:18080/swagger-ui.html>

OpenAPI YAML: <http://localhost:18080/openapi.yaml>

## Остановка

```bash
ssh -p 2222 sxxxxxx@helios.cs.ifmo.ru
cd ~/soa-lab1
kill "$(cat application.pid)"
```
