package lars.ba.own.extraction;

import java.util.Map;

class CDAClassManager {
    public static final Map<String, Map<String, String>> PATIENT_MAPPING = Map.of(
            "elements", Map.of(
                    "name", "./cda:patient/cda:name",
                    "gender", "./cda:patient/cda:administrativeGenderCode",
                    "birthTime", "./cda:patient/cda:birthTime",
                    "race", "./cda:patient/sdtc:raceCode",
                    "ethnicity", "./cda:patient/sdtc:ethnicGroupCode"
            ),
            "attributes", Map.of(
                    "id", "./cda:id",
                    "id_root", "./cda:id",
                    "address", "./cda:addr",
                    "telecom", "./cda:telecom"
            )
    );

    public static final Map<String, Map<String, String>> AUTHOR_MAPPING = Map.of(
            "elements", Map.of(
                    "person", "./cda:assignedAuthor/cda:assignedPerson/cda:name",
                    "organization", "./cda:assignedAuthor/cda:representedOrganization/cda:name"
            ),
            "attributes", Map.of(
                    "author_id", "./cda:assignedAuthor/cda:id",
                    "timestamp", "./cda:time"
            )
    );
}