# SOAP 1.1

WSDL: `http://localhost:3000/soap/isu?wsdl`.
Namespace: `urn:itmo:soa:isu:v1`. `Content-Type: text/xml; charset=UTF-8`.

Изменение формы обучения существующей группы 7:

```bash
curl -i http://localhost:3000/soap/isu \
  -H 'Content-Type: text/xml; charset=UTF-8' \
  -H 'SOAPAction: "urn:itmo:soa:isu:v1:changeEducationForm"' \
  --data-binary @- <<'XML'
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:i="urn:itmo:soa:isu:v1">
  <s:Body><i:changeEducationForm><i:groupId>7</i:groupId><i:newForm>EVENING_CLASSES</i:newForm></i:changeEducationForm></s:Body>
</s:Envelope>
XML
```

Отчисление всех студентов (группа удаляется):

```bash
curl -i http://localhost:3000/soap/isu \
  -H 'Content-Type: text/xml; charset=UTF-8' \
  -H 'SOAPAction: "urn:itmo:soa:isu:v1:expelAll"' \
  --data-binary @- <<'XML'
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:i="urn:itmo:soa:isu:v1">
  <s:Body><i:expelAll><i:groupId>7</i:groupId></i:expelAll></s:Body>
</s:Envelope>
XML
```

Отсутствующая группа даёт SOAP Fault с HTTP 500 и типизированными полями
`detail/IsuFault/status = 404`, `code = STUDY_GROUP_NOT_FOUND`.
Прежний `POST /isu/group/7/expel-all` преобразует тот же Fault в HTTP 404 и прежний JSON.
Префиксы XML могут отличаться; значимы namespace URI и имена элементов.
