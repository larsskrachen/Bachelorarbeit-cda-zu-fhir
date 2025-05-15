package lars.ba.cda2r4;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.ArrayList;
import java.util.List;

@Path("/convert")
public class ExampleResource {

    @POST
    @Path("/cda2r4")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_PLAIN)
    public Response hello(@RestForm("files") List<FileUpload> files) throws Exception {


        for (FileUpload file : files) {
            if (!file.fileName().toLowerCase().endsWith(".xml")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Es werden nur XML-Dateien akzeptiert")
                        .build();
            }
        }

        ArrayList<JsonNode> returnValue = new ArrayList<>();

        for (FileUpload file : files) {
            returnValue.add(CdaIsolationUtil.executeInIsolation(() -> {
                CdaDemo trans = new CdaDemo();
                return trans.getClinicalDocument(file);
            }));
        }




        return Response.ok(returnValue).build();
    }
}
