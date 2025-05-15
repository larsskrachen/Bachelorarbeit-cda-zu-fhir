package lars.ba.own.mapping;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/convert")
public class CdaToFhirResource {

    @Inject
    CdaToFhirConverter converter;

    @POST
    @Path("/cda-to-fhir")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response convertCdaToFhir(String cdaJson) {
        try {
            String fhirJson = converter.convertCdaToFhir(cdaJson);
            return Response.ok(fhirJson).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Json.createObjectBuilder()
                            .add("error", e.getMessage())
                            .build())
                    .build();
        }
    }
}
