package lars.ba.own.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/extract")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
public class Extraction {

    @POST
    @Path("/xml")
    public Response uploadXmlFiles(
            @RestForm("files") List<FileUpload> files) {
        List<String> uploadedFiles = new ArrayList<>();

        CDAProcessor processor = new CDAProcessor();

        try {
            // Validierung der Dateien
            for (FileUpload file : files) {
                if (!file.fileName().toLowerCase().endsWith(".xml")) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Es werden nur XML-Dateien akzeptiert")
                            .build();
                }
            }

            String auftragId = processor.createAuftrag("Mein CDA Import");
            System.out.println("Auftrag erstellt mit ID: " + auftragId);

            processor.processCDAFile(files);

            Map<String, List<Map<String, Object>>> results = processor.getData();

            // ObjectMapper mit JavaTimeModule registrieren
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());

            // JSON konvertieren
            String json = objectMapper.writeValueAsString(results);

//            String formattetJson = JsonFormatter.formatJson(json);


            return Response.ok(json).build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity("Fehler beim Upload: " + e.getMessage())
                    .build();
        }
    }
}
