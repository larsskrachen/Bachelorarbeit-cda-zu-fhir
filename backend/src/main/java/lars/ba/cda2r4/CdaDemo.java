package lars.ba.cda2r4;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.emf.ecore.EPackage;
import org.hl7.fhir.r4.model.*;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.openhealthtools.mdht.uml.cda.CDAPackage;
import org.openhealthtools.mdht.uml.cda.ClinicalDocument;
import org.openhealthtools.mdht.uml.cda.consol.ConsolPackage;
import org.openhealthtools.mdht.uml.cda.consol.ContinuityOfCareDocument;
import org.openhealthtools.mdht.uml.cda.util.CDAUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import tr.com.srdc.cda2fhir.transform.CCDTransformerImpl;
import tr.com.srdc.cda2fhir.util.FHIRUtil;
import tr.com.srdc.cda2fhir.util.IdGeneratorEnum;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CdaDemo {

    public JsonNode getClinicalDocument(FileUpload file) throws Exception {
        CDAUtil.loadPackages();
        ConsolPackage.eINSTANCE.getContinuityOfCareDocument();
        ConsolPackage.eINSTANCE.eClass();
        EPackage.Registry.INSTANCE.put(CDAPackage.eNS_URI, CDAPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(ConsolPackage.eNS_URI, ConsolPackage.eINSTANCE);


        Path filePath = file.uploadedFile();
        InputStream inputStream = Files.newInputStream(filePath);

//        Path resourcePath = Paths.get(getClass().getResource("/cda.xml").toURI());
//        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("cda.xml");
//        InputStream inputStream = Files.newInputStream(resourcePath);
        ConsolPackage.eINSTANCE.eClass();
        ClinicalDocument cda = CDAUtil.load(inputStream);
        System.out.println("Tatsächlicher Dokument-Typ: " + cda.getClass().getName());
        CCDTransformerImpl ccdTransformer = new CCDTransformerImpl(IdGeneratorEnum.COUNTER);

        Identifier identifier = new Identifier();
        Bundle bundle = ccdTransformer.transformDocument((ContinuityOfCareDocument) cda, null, identifier);
        addHttpMethodToBundle(bundle);

        String base_path = "src/test/resources/output/FHIRDemo-";

        FHIRUtil.printJSON(bundle, base_path + System.currentTimeMillis() + ".json");

        JsonNode result = convertBundleToJsonNode(bundle);

        return result;
    }

    public static JsonNode convertBundleToJsonNode(Bundle bundle) throws Exception, JsonMappingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = convertBundleToJsonString(bundle);
        return objectMapper.readTree(jsonString);
    }

    public static String convertBundleToJsonString(Resource bundle) {
        FhirContext ctx = FhirContext.forR4();
        return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
    }

    public static void printBundleResources(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.hasResource()) {
                String resourceType = entry.getResource().getResourceType().name();
                String resourceJson = convertBundleToJsonString(entry.getResource());

                System.out.println("---- " + resourceType + " ----");
                System.out.println(resourceJson);
                System.out.println();
            }
        }
    }

    public static void addHttpMethodToBundle(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (!entry.hasRequest()) {
                entry.setRequest(new Bundle.BundleEntryRequestComponent());
            }

            Resource resource = entry.getResource();
            entry.getRequest().setMethod(Bundle.HTTPVerb.POST);
            entry.getRequest().setUrl(resource.getResourceType().name());

            // Generisch auf Identifier prüfen mit Reflection
            try {
                Method getIdentifierMethod = resource.getClass().getMethod("getIdentifier");
                if (getIdentifierMethod != null) {
                    Object result = getIdentifierMethod.invoke(resource);

                    Identifier identifier = null;

                    if (result instanceof Identifier) {
                        // Direkt ein Identifier-Objekt
                        identifier = (Identifier) result;
                    } else if (result instanceof List<?>) {
                        // Falls es eine Liste ist, erstes Element nehmen (falls vorhanden)
                        List<?> identifierList = (List<?>) result;
                        if (!identifierList.isEmpty() && identifierList.get(0) instanceof Identifier) {
                            identifier = (Identifier) identifierList.get(0);
                        }
                    }

                    // Falls wir einen gültigen Identifier haben, setze If-None-Exist
                    if (identifier != null && identifier.hasValue()) {
                        entry.getRequest().setIfNoneExist(
                                resource.getResourceType().name() + "?identifier=" + identifier.getValue()
                        );
                    }
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                // Resource hat keine getIdentifier-Methode, überspringe
            }
        }
    }

    public static InputStream removeXmlSections(String xmlFilePath, String sectionTagName) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new File(xmlFilePath));

            NodeList sections = doc.getElementsByTagName(sectionTagName);
            for (int i = sections.getLength(); i >= 0; i--) {
                Node section = sections.item(i);
                Element sectionElement = (Element) section;
                NodeList titleList = sectionElement.getElementsByTagName("title");
                System.out.println("Title: " + titleList.item(0).getTextContent());
//                section.getParentNode().removeChild(section);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(outputStream);
            transformer.transform(source, result);

            return new ByteArrayInputStream(outputStream.toByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


}
