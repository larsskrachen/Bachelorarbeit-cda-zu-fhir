package lars.ba.own.extraction;

import java.util.Map;

class CDAConfig {
    public static final Map<String, String> NS = Map.of(
            "cda", "urn:hl7-org:v3",
            "sdtc", "urn:hl7-org:sdtc"
    );

    public static final Map<String, String> CODE_SYSTEMS = Map.of(
            "LOINC", "2.16.840.1.113883.6.1",
            "SNOMED-CT", "2.16.840.1.113883.6.96",
            "HL7Gender", "2.16.840.1.113883.5.1"
    );

    public static final Map<String, String> XPATHS = Map.of(
            "patient", ".//cda:recordTarget/cda:patientRole",
            "author", ".//cda:author",
            "sections", ".//cda:structuredBody//cda:section"
    );
}
