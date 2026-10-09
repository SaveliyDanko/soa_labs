#!/usr/bin/env python3
"""Проверка работающего стенда lab-3, без сторонних Python-зависимостей."""
import argparse
import json
import os
from pathlib import Path
import ssl
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent.parent
BASE = os.environ.get("CLIENT_URL", "https://localhost:" + os.environ.get("CLIENT_HTTPS_PORT", "3000")).rstrip("/")
TLS = ssl._create_unverified_context()  # Только локальный учебный сертификат.
GROUPS = "/api/study-groups"


def request(path, method="GET", body=None, status=200, headers=None):
    url = path if path.startswith("http") else BASE + path
    fields = {"Accept": "application/json"}
    fields.update(headers or {})
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
        fields["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=fields)
    try:
        response = urllib.request.urlopen(req, context=TLS, timeout=25)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        payload = response.read()
        assert response.status == status, (method, url, response.status, payload.decode(errors="replace"))
        value = json.loads(payload) if payload and "application/json" in response.headers.get("Content-Type", "") else payload
        return value, response.headers


def eventually(check, timeout=90):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            return check()
        except (AssertionError, OSError, urllib.error.URLError) as error:
            last = error
            time.sleep(2)
    raise AssertionError("Стенд не пришёл в ожидаемое состояние") from last


def traced(path, method="GET"):
    value, headers = request(path, method)
    assert headers.get("X-Gateway") == "zuul", dict(headers)
    assert headers.get("X-Config-Source") == "spring-cloud-config", dict(headers)
    return value, headers["X-Service-Instance"]


def balance(path, expected, method="GET"):
    seen = set()
    for _ in range(12):
        _, instance = traced(path, method)
        seen.add(instance)
    assert seen == set(expected), ("Балансировка", seen, expected)
    print("Балансировка подтверждена:", ", ".join(sorted(seen)), flush=True)


def compose(*args):
    subprocess.run(["docker", "compose", *args], cwd=ROOT, check=True)


def verify(failover):
    tracked = set()
    marker = "lab3-" + uuid.uuid4().hex[:12]
    body = {"name": marker, "coordinates": {"x": 1.5, "y": -2}, "studentsCount": 25,
            "formOfEducation": "FULL_TIME_EDUCATION", "semesterEnum": "THIRD",
            "groupAdmin": {"name": "Анна", "nationality": "USA",
                           "birthday": "1985-06-20T10:15:30+03:00"}}
    try:
        eventually(lambda: traced(GROUPS))
        config_port = os.environ.get("CONFIG_PORT", "8888")
        for name in ("study-groups-service", "isu-service", "api-gateway"):
            config, _ = request(f"http://localhost:{config_port}/{name}/default")
            assert any(source["source"].get("lab.config-source") == "spring-cloud-config"
                       for source in config["propertySources"]), config
        def discovery():
            port = os.environ.get("EUREKA_PORT", "8761")
            registry, _ = request(f"http://localhost:{port}/eureka/apps/STUDY-GROUPS-SERVICE")
            instances = registry["application"]["instance"]
            if isinstance(instances, dict):
                instances = [instances]
            assert {item["instanceId"] for item in instances if item["status"] == "UP"} == {
                "study-groups-1", "study-groups-2"}, instances
        eventually(discovery)
        print("Config Server и регистрация двух реплик в Eureka подтверждены.", flush=True)

        created, headers = request(GROUPS, "POST", body, 201)
        group_id = created["id"]
        tracked.add(group_id)
        assert headers["Location"] == BASE + GROUPS + "/" + str(group_id), dict(headers)
        path = GROUPS + "/" + str(group_id)
        updated, _ = request(path, "PUT", dict(body, studentsCount=31))
        assert updated["studentsCount"] == 31 and updated["creationDate"] == created["creationDate"]
        legacy_groups = os.environ.get("PAYARA_URL", "https://localhost:" + os.environ.get("PAYARA_HTTPS_PORT", "8181"))
        legacy, headers = request(legacy_groups + path)
        assert legacy["id"] == group_id and headers["X-Gateway"] == "zuul"
        balance(path, ("study-groups-1", "study-groups-2"))
        for _ in range(6):
            group, _ = traced(path)
            assert group["name"] == marker and group["creationDate"] == created["creationDate"]
        isu_path = f"/isu/group/{group_id}/change-edu-form/DISTANCE_EDUCATION"
        balance(isu_path, ("isu-1", "isu-2"), "POST")
        for _ in range(6):
            group, _ = traced(path)
            assert group["formOfEducation"] == "DISTANCE_EDUCATION"
        page, _ = request(GROUPS + "?sort=name,asc&sort=studentsCount,desc&filter=name:eq:" + marker + "&size=1")
        assert page["totalElements"] == 1 and page["content"][0]["id"] == group_id

        invalid = dict(body, studentsCount=0)
        error, headers = request(GROUPS, "POST", invalid, 422)
        assert error["code"] == "VALIDATION_FAILED" and "studentsCount" in error["violations"]
        assert headers["Content-Language"] == "ru"
        error, _ = request(GROUPS, "POST", dict(body, unknown=True), 400)
        assert error["code"] == "MALFORMED_JSON"
        _, headers = request(GROUPS, "OPTIONS", headers={
            "Origin": "https://example.org", "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "Content-Type"})
        assert headers["Access-Control-Allow-Origin"] == "https://example.org"
        for document in ("/", "/swagger-ui.html", "/openapi.yaml", "/webjars/swagger-ui/5.18.2/swagger-ui.css"):
            request(document)
        print("CRUD, общая БД, межсервисные вызовы, ошибки, CORS и документация проверены.", flush=True)

        if failover:
            # Останавливаем только реплики этого Compose-проекта; в finally восстанавливаем.
            try:
                compose("stop", "study-groups-2", "isu-2")
                def remaining():
                    for _ in range(8):
                        _, instance = traced(path)
                        assert instance == "study-groups-1", instance
                        _, instance = traced(isu_path, "POST")
                        assert instance == "isu-1", instance
                eventually(remaining)
                print("После отключения реплик запросы обслуживают study-groups-1 и isu-1.", flush=True)
            finally:
                compose("start", "study-groups-2", "isu-2")
            eventually(discovery)
            eventually(lambda: balance(path, ("study-groups-1", "study-groups-2")))
            eventually(lambda: balance(isu_path, ("isu-1", "isu-2"), "POST"))

            for services, endpoint, method in (
                    (("study-groups-1", "study-groups-2"), path, "GET"),
                    (("isu-1", "isu-2"), isu_path, "POST")):
                try:
                    compose("stop", *services)
                    def unavailable():
                        error, headers = request(endpoint, method, status=503)
                        assert error["code"] == "UPSTREAM_UNAVAILABLE", error
                        assert error["message"] == "Сервис временно недоступен", error
                        assert error["path"] == endpoint, error
                        assert headers["Content-Language"] == "ru"
                    eventually(unavailable)
                finally:
                    compose("start", *services)
                eventually(lambda: traced(endpoint, method))
            eventually(discovery)
            eventually(lambda: balance(path, ("study-groups-1", "study-groups-2")))
            eventually(lambda: balance(isu_path, ("isu-1", "isu-2"), "POST"))
            print("Полная недоступность upstream даёт русский JSON 503; после восстановления данные сохранены.", flush=True)

        result, _ = traced(f"/isu/group/{group_id}/expel-all", "POST")
        assert result["resourceId"] == group_id
        tracked.discard(group_id)
        error, _ = request(path, status=404)
        assert error["code"] == "STUDY_GROUP_NOT_FOUND"
        error, _ = request(f"/isu/group/{group_id}/expel-all", "POST", status=404)
        assert error["code"] == "STUDY_GROUP_NOT_FOUND"
        legacy_isu = os.environ.get("ISU_URL", "https://localhost:" + os.environ.get("WILDFLY_HTTPS_PORT", "8443"))
        error, headers = request(legacy_isu + f"/isu/group/{group_id}/expel-all", "POST", status=404)
        assert error["code"] == "STUDY_GROUP_NOT_FOUND" and headers["X-Gateway"] == "zuul"
        print("Проверка lab-3 завершена успешно.", flush=True)
    finally:
        for group_id in tracked:
            try:
                request(GROUPS + "/" + str(group_id), "DELETE", status=204)
            except Exception as error:
                print(f"Не удалось удалить тестовую группу {group_id}: {error}", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--failover", action="store_true", help="Проверить отключение и восстановление реплик")
    verify(parser.parse_args().failover)
