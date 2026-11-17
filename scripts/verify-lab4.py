#!/usr/bin/env python3
"""Lab 4 end-to-end checks against a deployed stack; Python standard library only."""
import json
import os
import ssl
import time
import urllib.error
import urllib.request
import uuid
import xml.etree.ElementTree as ET

BASE = os.environ.get("CLIENT_URL", "http://localhost:3000").rstrip("/")
GROUPS = "/api/study-groups"
NS = {"s": "http://schemas.xmlsoap.org/soap/envelope/", "i": "urn:itmo:soa:isu:v1"}


def request(path, method="GET", body=None, expected=200, headers=None):
    data = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    fields = {"Accept": "application/json"}
    if data is not None:
        fields["Content-Type"] = "application/json"
    fields.update(headers or {})
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=fields)
    context = ssl.create_default_context()
    if os.environ.get("ALLOW_SELF_SIGNED_TLS") == "1":
        context = ssl._create_unverified_context()
    try:
        response = urllib.request.urlopen(req, context=context, timeout=55)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        payload = response.read()
        assert response.status == expected, (method, path, response.status, payload.decode(errors="replace"))
        result = json.loads(payload) if payload and "application/json" in response.headers.get("Content-Type", "") else payload
        return result, response.headers


def soap(operation, group_id, new_form=None, expected=200):
    root = ET.Element(ET.QName(NS["s"], "Envelope"))
    body = ET.SubElement(root, ET.QName(NS["s"], "Body"))
    action = ET.SubElement(body, ET.QName(NS["i"], operation))
    ET.SubElement(action, ET.QName(NS["i"], "groupId")).text = str(group_id)
    if new_form is not None:
        ET.SubElement(action, ET.QName(NS["i"], "newForm")).text = new_form
    payload, headers = request("/soap/isu", "POST", ET.tostring(root, encoding="utf-8"), expected,
                               {"Content-Type": "text/xml; charset=UTF-8", "Accept": "text/xml",
                                "SOAPAction": '"' + NS["i"] + ":" + operation + '"'})
    assert headers.get("X-ESB") == "mule" and headers.get("X-ESB-Flow") == "isu-soap", dict(headers)
    assert headers.get("X-SOAP-Instance"), dict(headers)
    return ET.fromstring(payload)


def verify():
    tracked = set()
    marker = "lab4-" + uuid.uuid4().hex[:12]
    body = {"name": marker, "coordinates": {"x": 1.5, "y": -2}, "studentsCount": 25,
            "formOfEducation": "FULL_TIME_EDUCATION", "semesterEnum": "THIRD",
            "groupAdmin": {"name": "Анна", "nationality": "USA", "hairColor": "GREEN",
                           "birthday": "1985-06-20T10:15:30+03:00", "location": {"x": 1, "y": 2, "z": 3}}}
    deadline = time.monotonic() + 120
    while True:
        try:
            request(GROUPS)
            break
        except (OSError, AssertionError):
            if time.monotonic() >= deadline:
                raise
            time.sleep(2)
    try:
        for document in ("/", "/swagger-ui.html", "/openapi.yaml"):
            request(document)
        wsdl, headers = request("/soap/isu?wsdl")
        assert b"urn:itmo:soa:isu:v1" in wsdl and b"soap:binding" in wsdl
        assert headers.get("X-ESB") == "mule", dict(headers)
        print("UI, OpenAPI и SOAP WSDL через Mule доступны.", flush=True)

        created, headers = request(GROUPS, "POST", body, 201)
        group_id = created["id"]
        tracked.add(group_id)
        assert headers["Location"] == BASE + GROUPS + "/" + str(group_id), dict(headers)
        assert headers["X-Gateway"] == "spring-cloud-gateway"
        path = GROUPS + "/" + str(group_id)
        changed, _ = request(f"/isu/group/{group_id}/change-edu-form/EVENING_CLASSES", "POST")
        assert changed["formOfEducation"] == "EVENING_CLASSES"
        assert changed["creationDate"] == created["creationDate"] and changed["name"] == marker
        assert changed["studentsCount"] == 25 and changed["groupAdmin"]["location"] == body["groupAdmin"]["location"]
        direct = soap("changeEducationForm", group_id, "DISTANCE_EDUCATION")
        assert direct.findtext("s:Body/i:changeEducationFormResponse/i:result/i:formOfEducation", namespaces=NS) == "DISTANCE_EDUCATION"
        persisted, _ = request(path)
        assert persisted["formOfEducation"] == "DISTANCE_EDUCATION"
        assert persisted["creationDate"] == created["creationDate"]
        page, _ = request(GROUPS + "?sort=name,asc&filter=name:eq:" + marker)
        assert page["totalElements"] == 1
        print("REST -> SOAP -> Mule -> неизменённый REST Study Groups: данные и контракт сохранены.", flush=True)

        for endpoint, code in (("/isu/group/abc/expel-all", "INVALID_PARAMETER_TYPE"),
                               (f"/isu/group/{group_id}/change-edu-form/WRONG", "INVALID_PARAMETER_VALUE")):
            error, headers = request(endpoint, "POST", expected=400)
            assert error["code"] == code and headers["Content-Language"] == "ru", error
        fault = soap("expelAll", "0", expected=500)
        assert fault.findtext("s:Body/s:Fault/detail/i:IsuFault/i:status", namespaces=NS) == "400"
        print("SOAP Fault и обратное преобразование в прежние REST-ошибки проверены.", flush=True)

        result, _ = request(f"/isu/group/{group_id}/expel-all", "POST")
        assert result["code"] == "ALL_STUDENTS_EXPELLED" and result["resourceId"] == group_id
        tracked.discard(group_id)
        error, _ = request(path, expected=404)
        assert error["code"] == "STUDY_GROUP_NOT_FOUND"
        fault = soap("expelAll", group_id, expected=500)
        assert fault.findtext("s:Body/s:Fault/detail/i:IsuFault/i:code", namespaces=NS) == "STUDY_GROUP_NOT_FOUND"
        second, _ = request(GROUPS, "POST", dict(body, name=marker + "-soap"), 201)
        tracked.add(second["id"])
        result = soap("expelAll", second["id"])
        assert result.findtext("s:Body/i:expelAllResponse/i:result/i:code", namespaces=NS) == "ALL_STUDENTS_EXPELLED"
        tracked.discard(second["id"])
        request(GROUPS + "/" + str(second["id"]), expected=404)
        print("Лабораторная №4: сквозная проверка завершена успешно.", flush=True)
    finally:
        for group_id in tracked:
            try:
                request(GROUPS + "/" + str(group_id), "DELETE", expected=204)
            except Exception as error:
                print("Не удалось удалить тестовую группу", group_id, error, flush=True)


if __name__ == "__main__":
    verify()
