package lars.ba.own.extraction;

import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class CDAProcessor {
    DataFrames dataFrames;
    private String currentAuftragId;

    public CDAProcessor() {
        this.dataFrames = new DataFrames();
    }

    public String createAuftrag(String auftragsname) {
        String auftragId = UUID.randomUUID().toString();
        Map<String, Object> newRow = new HashMap<>();
        newRow.put("auftrags_id", auftragId);
        newRow.put("auftragsname", auftragsname);
        newRow.put("erstellungsdatum", LocalDateTime.now());

        dataFrames.addRow("auftraege", newRow);
        this.currentAuftragId = auftragId;
        return auftragId;
    }

    public void processCDAFile(List<FileUpload> files) throws Exception {
        if (currentAuftragId == null) {
            throw new IllegalStateException("Kein aktiver Auftrag - erstellen Sie zuerst einen Auftrag");
        }

        for (FileUpload file : files) {
            String dokumentId = UUID.randomUUID().toString();
            Map<String, Object> cdaData;

            try (InputStream inputStream = Files.newInputStream(file.uploadedFile())) {
                cdaData = extractCDAData(inputStream);
            }

            processDocumentMetadata(dokumentId, file.fileName(), cdaData);
            processPatientData(dokumentId, cdaData);
            processAuthorData(dokumentId, cdaData);
            processContentData(dokumentId, cdaData);
        }
    }

    private Map<String, Object> extractCDAData(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(inputStream);

        if (!validateCDAStructure(doc)) {
            throw new IllegalArgumentException("Ungültige CDA-Dokumentstruktur");
        }

        return findAllEntries(doc);
    }

    private void processDocumentMetadata(String dokumentId, String fileName, Map<String, Object> cdaData) {
        Map<String, Object> newDoc = new HashMap<>();
        newDoc.put("dokument_id", dokumentId);
        newDoc.put("auftrags_id", currentAuftragId);
        newDoc.put("dateiname", fileName);
        newDoc.put("erstellungsdatum", LocalDateTime.now());
        newDoc.put("validiert", true);

        dataFrames.addRow("dokumente", newDoc);
    }

    private void processPatientData(String dokumentId, Map<String, Object> cdaData) {
        @SuppressWarnings("unchecked")
        Map<String, Object> patient = (Map<String, Object>) ((Map<String, Object>) cdaData.get("metadata")).get("patient");

        Map<String, Object> newPatient = new HashMap<>();
        newPatient.put("patient_id", UUID.randomUUID().toString());
        newPatient.put("dokument_id", dokumentId);
        newPatient.put("vorname", patient.get("vorname"));
        newPatient.put("nachname", patient.get("nachname"));
        newPatient.put("geschlecht", patient.get("geschlecht_code"));
        newPatient.put("geburtsdatum", patient.get("geburtsdatum"));
        newPatient.put("id", patient.get("id"));
        newPatient.put("id_root", patient.get("id_root"));

        dataFrames.addRow("patienten", newPatient);
    }

    private void processAuthorData(String dokumentId, Map<String, Object> cdaData) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> authors = (List<Map<String, Object>>)
                ((Map<String, Object>) cdaData.get("metadata")).get("autoren");

        for (Map<String, Object> author : authors) {
            Map<String, Object> newAuthor = new HashMap<>();
            newAuthor.put("autor_id", UUID.randomUUID().toString());
            newAuthor.put("dokument_id", dokumentId);
            newAuthor.put("vorname", author.get("vorname"));
            newAuthor.put("nachname", author.get("nachname"));
            newAuthor.put("organisation", author.get("organisation"));

            dataFrames.addRow("autoren", newAuthor);
        }
    }

    private void processContentData(String dokumentId, Map<String, Object> cdaData) {
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> sections = (Map<String, Map<String, Object>>) cdaData.get("sections");

        for (Map.Entry<String, Map<String, Object>> sectionEntry : sections.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sectionData = (Map<String, Object>) sectionEntry.getValue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) sectionData.get("entries");
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) sectionData.get("meta");
            @SuppressWarnings("unchecked")
            Map<String, Object> code = (Map<String, Object>) meta.get("code");

            for (Map<String, Object> entry : entries) {
                String inhaltId = UUID.randomUUID().toString();

                Map<String, Object> inhalt = new HashMap<>();
                inhalt.put("inhalt_id", inhaltId);
                inhalt.put("dokument_id", dokumentId);
                inhalt.put("section_code", code.get("code"));
                inhalt.put("section_title", meta.get("title"));
                inhalt.put("codeSystem", code.get("codeSystem"));
                inhalt.put("codeSystemName", code.get("codeSystemName"));

                Map<String, Object> children = (Map<String, Object>) entry.get("children");
                if (children != null && !children.isEmpty()) {
                    String firstKey = children.keySet().iterator().next(); // Erster Schlüssel in children
                    Map<String, Object> firstEntry = (Map<String, Object>) children.get(firstKey);

                    if (firstEntry != null && firstEntry.containsKey("attributes")) {
                        Map<String, Object> attributes = (Map<String, Object>) firstEntry.get("attributes");
                        if (attributes != null && attributes.containsKey("classCode")) {
                            inhalt.put("section_type", attributes.get("classCode")); // Nur classCode speichern
                        }
                    }
                }


                dataFrames.addRow("inhalte", inhalt);

                Map<String, Object> elevatedEntry = elevateChildren(entry);
                Map<String, Object> flattenedEntry = flattenDict(elevatedEntry);

                for (Map.Entry<String, Object> valueEntry : flattenedEntry.entrySet()) {
                    String cleanedValue = cleanValue(valueEntry.getValue());
                    if (cleanedValue != null) {
                        Map<String, Object> wert = new HashMap<>();
                        wert.put("wert_id", UUID.randomUUID().toString());
                        wert.put("dokument_id", dokumentId);
                        wert.put("inhalt_id", inhaltId);
                        wert.put("key_path", valueEntry.getKey());
                        wert.put("value", cleanedValue);

                        dataFrames.addRow("werte", wert);
                    }
                }
            }
        }
    }

    private String cleanValue(Object value) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            if (map.size() == 1 && map.containsKey("text")) {
                return map.get("text").toString();
            }
            return null;
        }
        return value != null ? value.toString() : null;
    }

    private Map<String, Object> elevateChildren(Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>(data);

        if (result.containsKey("children")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> children = (Map<String, Object>) result.get("children");

            if (children.size() == 1) {
                Map.Entry<String, Object> child = children.entrySet().iterator().next();
                result.put(child.getKey(), elevateChildren((Map<String, Object>) child.getValue()));
                result.remove("children");
            }
        }

        for (Map.Entry<String, Object> entry : new HashMap<>(result).entrySet()) {
            if (entry.getValue() instanceof Map) {
                result.put(entry.getKey(), elevateChildren((Map<String, Object>) entry.getValue()));
            } else if (entry.getValue() instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) entry.getValue();
                result.put(entry.getKey(), list.stream()
                        .map(item -> item instanceof Map ? elevateChildren((Map<String, Object>) item) : item)
                        .collect(Collectors.toList()));
            }
        }

        return result;
    }

    private Map<String, Object> flattenDict(Map<String, Object> data) {
        return flattenDictHelper(data, "", ".");
    }

    private Map<String, Object> flattenDictHelper(Map<String, Object> data, String parentKey, String sep) {
        Map<String, Object> items = new HashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("children".equals(key)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> childMap = (Map<String, Object>) value;
                items.putAll(flattenDictHelper(childMap, parentKey, sep));
                continue;
            }

            if ("attributes".equals(key)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> attrMap = (Map<String, Object>) value;
                for (Map.Entry<String, Object> attr : attrMap.entrySet()) {
                    String attrPath = parentKey.isEmpty() ? "@" + attr.getKey() :
                            parentKey + "@" + attr.getKey();
                    items.put(attrPath, attr.getValue());
                }
                continue;
            }

            if ("text".equals(key)) {
                if (!parentKey.isEmpty()) {
                    items.put(parentKey + "/text()", value);
                } else {
                    items.put(parentKey, value);
                }
                continue;
            }

            String newKey = parentKey.isEmpty() ? key : parentKey + sep + key;

            if (value instanceof Map) {
                items.putAll(flattenDictHelper((Map<String, Object>) value, newKey, sep));
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    String arrayKey = key + "[" + i + "]";
                    if (item instanceof Map) {
                        items.putAll(flattenDictHelper((Map<String, Object>) item,
                                parentKey.isEmpty() ? arrayKey : parentKey + sep + arrayKey, sep));
                    } else {
                        items.put(parentKey.isEmpty() ? arrayKey : parentKey + sep + arrayKey, item);
                    }
                }
            } else {
                items.put(newKey, value);
            }
        }

        return items;
    }

    private boolean validateCDAStructure(Document doc) {
        NodeList patientNodes = doc.getElementsByTagNameNS(CDAConfig.NS.get("cda"), "recordTarget");
        NodeList authorNodes = doc.getElementsByTagNameNS(CDAConfig.NS.get("cda"), "author");
        NodeList bodyNodes = doc.getElementsByTagNameNS(CDAConfig.NS.get("cda"), "structuredBody");

        return patientNodes.getLength() > 0 && authorNodes.getLength() > 0 && bodyNodes.getLength() > 0;
    }

    private Map<String, Object> findAllEntries(Document doc) {
        Map<String, Object> result = new HashMap<>();

        // Extract metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("patient", extractPatientInfo(doc));
        metadata.put("autoren", extractAuthorInfo(doc));
        result.put("metadata", metadata);

        // Process sections
        result.put("sections", processSections(doc));

        return result;
    }

    private Map<String, Object> extractPatientInfo(Document doc) {
        Map<String, Object> patientInfo = new HashMap<>();
        Element patientRole = (Element) doc.getElementsByTagNameNS(CDAConfig.NS.get("cda"), "patientRole").item(0);

        if (patientRole != null) {
            Map<String, String> mapping = CDAClassManager.PATIENT_MAPPING.get("elements");

            patientInfo.put("vorname", safeExtract(patientRole, mapping.get("name") + "/cda:given", null));
            patientInfo.put("nachname", safeExtract(patientRole, mapping.get("name") + "/cda:family", null));
            patientInfo.put("geschlecht_code", safeExtract(patientRole, mapping.get("gender"), "code"));
            patientInfo.put("geburtsdatum", safeExtract(patientRole, mapping.get("birthTime"), "value"));
            patientInfo.put("id", safeExtract(patientRole, CDAClassManager.PATIENT_MAPPING.get("attributes").get("id"), "extension"));
            patientInfo.put("id_root", safeExtract(patientRole, CDAClassManager.PATIENT_MAPPING.get("attributes").get("id_root"), "root"));
        }

        return patientInfo;
    }

    private List<Map<String, Object>> extractAuthorInfo(Document doc) {
        List<Map<String, Object>> authors = new ArrayList<>();
        NodeList authorNodes = doc.getElementsByTagNameNS(CDAConfig.NS.get("cda"), "author");

        for (int i = 0; i < authorNodes.getLength(); i++) {
            Element author = (Element) authorNodes.item(i);
            Map<String, String> mapping = CDAClassManager.AUTHOR_MAPPING.get("elements");

            Map<String, Object> authorData = new HashMap<>();
            authorData.put("vorname", safeExtract(author, mapping.get("person") + "/cda:given", null));
            authorData.put("nachname", safeExtract(author, mapping.get("person") + "/cda:family", null));
            authorData.put("organisation", safeExtract(author, mapping.get("organization"), null));

            authors.add(authorData);
        }

        return authors;
    }

    private Map<String, Object> processSections(Document doc) {
        Map<String, Object> sectionsDict = new HashMap<>();
        NodeList sections = doc.getElementsByTagNameNS(CDAConfig.NS.get("cda"), "section");

        for (int i = 0; i < sections.getLength(); i++) {
            Element section = (Element) sections.item(i);
            // Only process direct children of the section
            if (section.getParentNode().getNodeName().contains("section")) {
                continue; // Skip nested sections
            }

            Element sectionCode = (Element) section.getElementsByTagNameNS(CDAConfig.NS.get("cda"), "code").item(0);
            Element sectionTitle = (Element) section.getElementsByTagNameNS(CDAConfig.NS.get("cda"), "title").item(0);

            String sectionKey = (sectionCode != null ? sectionCode.getAttribute("code") : "no-code")
                    + "_" + (sectionTitle != null ? sectionTitle.getTextContent() : "no-title");

            Map<String, Object> sectionData = new HashMap<>();

            // Process meta information
            Map<String, Object> meta = new HashMap<>();
            if (sectionCode != null) {
                Map<String, String> codeAttrs = new HashMap<>();
                NamedNodeMap attributes = sectionCode.getAttributes();
                for (int j = 0; j < attributes.getLength(); j++) {
                    Node attr = attributes.item(j);
                    codeAttrs.put(attr.getNodeName(), attr.getNodeValue());
                }
                meta.put("code", codeAttrs);
            } else {
                meta.put("code", new HashMap<>());
            }
            meta.put("title", sectionTitle != null ? sectionTitle.getTextContent() : null);
            sectionData.put("meta", meta);

            // Process only direct entry children
            List<Map<String, Object>> entries = new ArrayList<>();
            NodeList childNodes = section.getChildNodes();
            for (int j = 0; j < childNodes.getLength(); j++) {
                Node node = childNodes.item(j);
                if (node.getNodeType() == Node.ELEMENT_NODE
                        && node.getLocalName().equals("entry")
                        && node.getNamespaceURI().equals(CDAConfig.NS.get("cda"))) {
                    entries.add(processEntry((Element) node));
                }
            }
            sectionData.put("entries", entries);
            sectionsDict.put(sectionKey, sectionData);
        }
        return sectionsDict;
    }

    private Map<String, Object> processEntry(Element entryElement) {
        return processElement(entryElement);
    }

    private Map<String, Object> processElement(Element element) {
        Map<String, Object> result = new HashMap<>();

        // Process attributes
        if (element.getAttributes().getLength() > 0) {
            Map<String, String> attributes = new HashMap<>();
            NamedNodeMap attrs = element.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                String name = attr.getNodeName();
                if (name.contains(":")) {
                    name = name.split(":", 2)[1];
                }
                attributes.put(name, attr.getNodeValue());
            }
            result.put("attributes", attributes);
        }

        // Process text content
        String text = element.getTextContent().trim();
        if (!text.isEmpty() && element.getChildNodes().getLength() == 1) {
            result.put("text", text);
        }

        // Process children
        Map<String, Object> children = new HashMap<>();
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();
                if (tagName.contains(":")) {
                    tagName = tagName.split(":", 2)[1];
                }

                Map<String, Object> childContent = processElement(childElement);
                if (!childContent.isEmpty()) {
                    if (children.containsKey(tagName)) {
                        Object existing = children.get(tagName);
                        if (existing instanceof List) {
                            ((List<Map<String, Object>>) existing).add(childContent);
                        } else {
                            List<Map<String, Object>> list = new ArrayList<>();
                            list.add((Map<String, Object>) existing);
                            list.add(childContent);
                            children.put(tagName, list);
                        }
                    } else {
                        children.put(tagName, childContent);
                    }
                }
            }
        }

        if (!children.isEmpty()) {
            result.put("children", children);
        }

        return result;
    }

    private String safeExtract(Element element, String path, String attr) {
        try {
            // Simple XPath-like navigation (basic implementation)
            String[] parts = path.split("/");
            Element current = element;

            for (String part : parts) {
                if (current == null) return "";

                if (part.contains(":")) {
                    String[] nsAndName = part.split(":");
                    NodeList nodes = current.getElementsByTagNameNS(CDAConfig.NS.get(nsAndName[0]), nsAndName[1]);
                    if (nodes.getLength() == 0) return "";
                    current = (Element) nodes.item(0);
                } else {
                    NodeList nodes = current.getElementsByTagName(part);
                    if (nodes.getLength() == 0) return "";
                    current = (Element) nodes.item(0);
                }
            }

            if (attr != null) {
                return current.getAttribute(attr).trim();
            } else {
                return current.getTextContent().trim();
            }
        } catch (Exception e) {
            return "";
        }
    }

    public Map<String, List<Map<String, Object>>> getData() {
        return dataFrames.data;
    }
}