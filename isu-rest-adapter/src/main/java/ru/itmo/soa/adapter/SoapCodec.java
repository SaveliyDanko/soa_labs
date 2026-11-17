package ru.itmo.soa.adapter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.soap.IsuFault;
import ru.itmo.soa.soap.ObjectFactory;

@Component
public class SoapCodec {
    static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    static final String ISU = "urn:itmo:soa:isu:v1";
    private final JAXBContext jaxb;

    public SoapCodec() {
        try { jaxb = JAXBContext.newInstance(ObjectFactory.class); }
        catch (Exception failure) { throw new IllegalStateException("Не удалось загрузить SOAP-контракт", failure); }
    }

    private DocumentBuilderFactory documents() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    byte[] request(Object payload) {
        try {
            Document document = documents().newDocumentBuilder().newDocument();
            Element envelope = document.createElementNS(SOAP, "soap:Envelope"); document.appendChild(envelope);
            Element body = document.createElementNS(SOAP, "soap:Body"); envelope.appendChild(body);
            jaxb.createMarshaller().marshal(payload, body);
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            factory.newTransformer().transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (Exception failure) { throw new IllegalStateException("Не удалось сформировать SOAP-запрос", failure); }
    }

    <T> T response(byte[] bytes, Class<T> type) {
        try {
            Document document = documents().newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            Element envelope = document.getDocumentElement();
            if (!SOAP.equals(envelope.getNamespaceURI()) || !"Envelope".equals(envelope.getLocalName())) throw badResponse();
            Element body = child(envelope, SOAP, "Body");
            if (body == null) throw badResponse();
            Element fault = child(body, SOAP, "Fault");
            if (fault != null) {
                Element detail = child(fault, null, "detail");
                Element data = detail == null ? null : child(detail, ISU, "IsuFault");
                if (data == null) throw badResponse();
                IsuFault info = jaxb.createUnmarshaller().unmarshal(data, IsuFault.class).getValue();
                ErrorCode code = ErrorCode.valueOf(info.getCode());
                if (code.status() != info.getStatus()) throw badResponse();
                var details = new LinkedHashMap<String, String>();
                info.getDetail().forEach(entry -> details.put(entry.getName(), entry.getValue()));
                throw new ApiException(code, details);
            }
            Element payload = child(body, ISU, type.getAnnotation(XmlRootElement.class).name());
            if (payload == null) throw badResponse();
            return jaxb.createUnmarshaller().unmarshal(payload, type).getValue();
        } catch (ApiException failure) { throw failure; }
        catch (Exception failure) { throw new ApiException(ErrorCode.UPSTREAM_BAD_RESPONSE, java.util.Map.of(), failure); }
    }

    private Element child(Element parent, String namespace, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element && java.util.Objects.equals(namespace, node.getNamespaceURI()) && name.equals(node.getLocalName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private ApiException badResponse() { return new ApiException(ErrorCode.UPSTREAM_BAD_RESPONSE); }
}
