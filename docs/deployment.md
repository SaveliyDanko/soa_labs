# Передача для раскатки

Исходники подготовлены под Kubernetes. Раскатку на целевом стенде и установку Mule
на Helios выполняет другой человек; целевые системы из среды разработки не изменяются.

## Сборка и registry

На машине с Docker BuildKit:

```bash
export IMAGE_PREFIX=registry.example.org/your-project/soa-lab4
export IMAGE_TAG=lab4-v1
./scripts/build-images.sh
for module in config-service discovery-service api-gateway study-groups-service isu-service isu-rest-adapter mule-esb client; do
  docker push "$IMAGE_PREFIX/$module:$IMAGE_TAG"
done
```

Замените пример registry своим адресом. Для приватного registry настройте `imagePullSecrets`
в namespace `soa-lab4` до запуска Deployment. Используйте новый тег при изменении кода.
Для kind `start-kind.sh` загружает образы напрямую в node; registry не нужен.
Java-приложения собираются на Java 11, Mule — на Java 17.
SOAP ISU развёртывается как `ROOT.war` на Tomcat 9.

## Применение

Нужен кластер с default StorageClass и разрешениями на namespace, Secret, ConfigMap,
Service, Deployment, StatefulSet, PVC и PodDisruptionBudget. PVC PostgreSQL — 1 GiB,
`ReadWriteOnce`; размер меняется в `k8s/postgres.yaml`.

```bash
export KUBECONFIG=/path/to/target-kubeconfig
kubectl config current-context
export DB_PASSWORD='set-the-real-password'
export PUBLIC_BASE_URL=https://soa.example.org
# Необязательно: export SENTRY_DSN=...
./scripts/deploy-k8s.sh
```

`IMAGE_PREFIX` и `IMAGE_TAG` должны совпадать со сборкой.
`PUBLIC_BASE_URL` без завершающего `/` задаёт SOAP-адрес в WSDL.
Пароль хранится в Secret; для существующего PVC используйте прежний пароль:
изменение Secret само по себе не меняет пароль созданной БД.
ConfigMap генерируется из `config-repo/`. Скрипт применяет ресурсы, перезапускает
Deployment и ожидает готовности. PVC он не удаляет.
При ручном изменении конфигурации перезапустите Config Server и его потребителей.

## HTTPS

Нужен Ingress controller с default IngressClass. Можно явно задать `ingressClassName`
в `k8s/ingress.yaml`. Замените `soa.local` своим доменом и создайте TLS Secret:

```bash
kubectl -n soa-lab4 create secret tls soa-lab4-tls --cert=/path/to/fullchain.pem --key=/path/to/private-key.pem
kubectl apply -f k8s/ingress.yaml
```

Ingress ведёт только в Service `client`. Nginx передаёт API и SOAP в Gateway,
сохраняя внешний Host и HTTPS-схему для `Location`. Инфраструктурные Service — ClusterIP.
Для локального HTTP используйте `kubectl -n soa-lab4 port-forward service/client 3000:8080`.

## Проверка и диагностика

```bash
kubectl -n soa-lab4 get pods,services,pvc
CLIENT_URL=https://soa.example.org python3 scripts/verify-lab4.py
kubectl -n soa-lab4 get events --sort-by=.lastTimestamp
kubectl -n soa-lab4 logs deployment/isu-rest-adapter
kubectl -n soa-lab4 logs deployment/isu-service
kubectl -n soa-lab4 logs deployment/mule-esb
kubectl -n soa-lab4 logs deployment/api-gateway
```

Скрипт создаёт только группы со случайным маркером и удаляет их после проверки.
`X-Gateway` подтверждает Gateway; `X-ESB: mule`, `X-ESB-Flow: isu-soap` — шину;
`X-SOAP-Instance` — экземпляр SOAP-сервиса. Health ESB — `/health` на порту 8081.

На учебном стенде можно проверить восстановление: создать группу, удалить один pod
Study Groups, дождаться замены и прочитать группу по ID. Пересоздание pod PostgreSQL
также использует прежний PVC. Удаление PVC или kind-кластера удаляет соответствующие данные.
PostgreSQL и Mule в этой учебной конфигурации имеют по одной реплике.

## Mule на Helios или другом отдельном сервере

Kubernetes запускает Mule в pod. Для установки именно на Helios используется тот же артефакт:

```bash
# Java 17, Maven 3.9
mvn -f mule-esb/pom.xml package
```

Установите подходящие для ОС и архитектуры сервера JDK 17 и Mule Kernel 4.9.0
из [официального дистрибутива](https://developer.mulesoft.com/download-mule-esb-runtime/).
Скопируйте артефакт в каталог `apps` распакованного runtime:

```bash
export MULE_HOME=/path/to/mule-standalone-4.9.0
cp lab4-integration-4.0.0-mule-application.jar "$MULE_HOME/apps/lab4-integration.jar"
export HTTP_PORT=8081
export SOAP_HOST=reachable-soap-backend
export SOAP_PORT=8080
export GATEWAY_HOST=reachable-api-gateway
export GATEWAY_PORT=8080
"$MULE_HOME/bin/mule" console
```

Адреса backend и Gateway должны быть доступны с Helios. ClusterIP обычно доступен
только внутри Kubernetes: оператор обеспечивает маршрутизацию или SSH-туннели в обе стороны.
Задайте доступный URL Helios в `MULE_ESB_URL` для Gateway и SOAP ISU,
`ISU_SOAP_URL` для REST-адаптера и перезапустите приложения.
Отключите Kubernetes Deployment Mule, чтобы оба потока использовали шину на Helios.
Повторите `verify-lab4.py`.

Фактическая установка и связность сетей проверяются оператором при раскатке.
