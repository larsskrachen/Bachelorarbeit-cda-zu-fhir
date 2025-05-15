package lars.ba.own.mapping;

import ca.uhn.fhir.context.FhirContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.hl7.fhir.r4.model.*;

import java.math.BigDecimal;
import java.util.*;

@ApplicationScoped
public class CdaToFhirConverter {

    private FhirContext fhirContext = FhirContext.forR4();

    @Inject
    CdaFhirMappingConfig mappingConfig;

    private String findValueFromPaths(Map<String, String> valueMap, List<String> paths, String defaultValue) {
        if (paths == null || paths.isEmpty()) {
            return defaultValue;
        }

        for (String path : paths) {
            if (valueMap.containsKey(path)) {
                return valueMap.get(path);
            }
        }

        return defaultValue;
    }

    // Mapping von CDA-Sektionen zu FHIR-Ressourcentypen
    private static final Map<String, String> SECTION_TO_RESOURCE_TYPE = Map.ofEntries(
            Map.entry("10160-0", "MedicationAdministration"),
            Map.entry("29762-2", "Observation"),
            Map.entry("Allergies", "AllergyIntolerance"),
            Map.entry("8716-3", "Observation"),
            Map.entry("Problems", "Condition"),
            Map.entry("Procedures", "Procedure"),
            Map.entry("Immunizations", "Immunization"),
            Map.entry("Results", "DiagnosticReport"),
            Map.entry("Encounters", "Encounter"),
            Map.entry("Plan of Care", "CarePlan")
            // Weitere Mappings können hier hinzugefügt werden
    );

    /**
     * Konvertiert CDA-JSON zu FHIR-Ressourcen
     */
    public String convertCdaToFhir(String cdaJson) {
        // Parse JSON
        jakarta.json.JsonReader jsonReader = Json.createReader(new java.io.StringReader(cdaJson));
        JsonArray cdaEntries = jsonReader.readArray();
        jsonReader.close();

        // Ergebnis-Bundle erstellen
        Bundle resultBundle = new Bundle();
        resultBundle.setType(Bundle.BundleType.COLLECTION);

        // CDA-Einträge verarbeiten
        for (JsonValue entryValue : cdaEntries) {
            JsonObject entry = entryValue.asJsonObject();
            String sectionCode = entry.getJsonObject("inhalt").getString("section_code");

            // Generisches Processing basierend auf Sektionstyp
            processEntry(entry, sectionCode, resultBundle);
        }

        // FHIR-Bundle zu JSON konvertieren
        return fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(resultBundle);
    }

    /**
     * Verarbeitet einen CDA-Eintrag basierend auf seinem Sektionstyp
     */
    private void processEntry(JsonObject entry, String sectionCode, Bundle bundle) {
        try {
            // Bestimme den FHIR-Ressourcentyp basierend auf dem Sektionstyp
            String resourceType = SECTION_TO_RESOURCE_TYPE.getOrDefault(sectionCode, "Observation");

            // Map erstellen für einfacheren Zugriff auf Werte
            Map<String, String> valueMap = createValueMap(entry);

            // Resource basierend auf Typ erstellen und konfigurieren
            Resource resource = createResource(resourceType, entry.getString("inhalt_id"), valueMap, sectionCode);

            // Zum Bundle hinzufügen, wenn Resource erstellt wurde
            if (resource != null) {
                bundle.addEntry()
                        .setResource(resource)
                        .setFullUrl(resourceType + "/" + resource.getId());
            } else {
                System.out.println("Konnte keine Resource für Sektion erstellen: " + sectionCode);
            }
        } catch (Exception e) {
            System.err.println("Fehler bei der Verarbeitung von Sektion " + sectionCode + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Erstellt eine FHIR-Resource basierend auf dem angegebenen Typ und den Werten
     */
    private Resource createResource(String resourceType, String inhaltId, Map<String, String> valueMap, String sectionCode) {
        switch (resourceType) {
            case "MedicationAdministration":
                return createMedicationAdministration(inhaltId, valueMap);
            case "Observation":
                return createObservation(inhaltId, valueMap, sectionCode);
            case "AllergyIntolerance":
                return createAllergyIntolerance(inhaltId, valueMap);
            case "Condition":
                return createCondition(inhaltId, valueMap);
//            case "Procedure":
//                return createProcedure(inhaltId, valueMap);
//            case "Immunization":
//                return createImmunization(inhaltId, valueMap);
//            case "DiagnosticReport":
//                return createDiagnosticReport(inhaltId, valueMap);
//            case "Encounter":
//                return createEncounter(inhaltId, valueMap);
//            case "CarePlan":
//                return createCarePlan(inhaltId, valueMap);
            default:
                // Standardmäßig eine generische Observation erstellen
                return createGenericObservation(inhaltId, valueMap, sectionCode);
        }
    }

    private Timing.UnitsOfTime mapCdaTimeUnitToFhir(String cdaTimeUnit) {
        if (cdaTimeUnit == null) return Timing.UnitsOfTime.D; // Default: Tag

        switch (cdaTimeUnit.toLowerCase()) {
            case "s":
            case "sec":
                return Timing.UnitsOfTime.S;
            case "min":
                return Timing.UnitsOfTime.MIN;
            case "h":
            case "hr":
                return Timing.UnitsOfTime.H;
            case "d":
            case "day":
                return Timing.UnitsOfTime.D;
            case "wk":
            case "week":
                return Timing.UnitsOfTime.WK;
            case "mo":
            case "month":
                return Timing.UnitsOfTime.MO;
            case "a":
            case "yr":
            case "year":
                return Timing.UnitsOfTime.A;
            default:
                return Timing.UnitsOfTime.D;
        }
    }

    /**
     * Erstellt eine Map mit Schlüssel-Wert-Paaren aus dem CDA-JSON-Objekt für einfacheren Zugriff
     */
    private Map<String, String> createValueMap(JsonObject entry) {
        Map<String, String> valueMap = new HashMap<>();

        // Prüfen, ob es ein "werte"-Array im JSON gibt
        if (entry.containsKey("werte") && entry.get("werte").getValueType() == JsonValue.ValueType.ARRAY) {
            JsonArray werteArray = entry.getJsonArray("werte");

            // Durch alle Einträge im werte-Array iterieren
            for (int i = 0; i < werteArray.size(); i++) {
                if (werteArray.get(i).getValueType() == JsonValue.ValueType.OBJECT) {
                    JsonObject werteObj = werteArray.get(i).asJsonObject();

                    // Prüfen, ob key_path und value existieren
                    if (werteObj.containsKey("key_path") && werteObj.containsKey("value")) {
                        String keyPath = werteObj.getString("key_path");
                        String value = werteObj.get("value").toString().replaceAll("\"", "");

                        // key_path als Schlüssel und value als Wert speichern
                        valueMap.put(keyPath, value);
                    }
                }
            }
        }

        return valueMap;
    }

    private double castSaveToDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }


    /**
     * Erstellt eine MedicationAdministration-Resource basierend auf der Konfiguration
     */
    private MedicationAdministration createMedicationAdministration(String inhaltId, Map<String, String> valueMap) {
        MedicationAdministration medicationAdministration = new MedicationAdministration();
        medicationAdministration.setId("med-"+ inhaltId);

        // Profile aus der Konfiguration
        medicationAdministration.setMeta(new Meta().addProfile(
                mappingConfig.resources().medicationAdministration().profile()
        ));

        medicationAdministration.setSubject(new Reference("Patient/3"));

        // Status aus der Konfiguration
        medicationAdministration.setStatus(MedicationAdministration.MedicationAdministrationStatus.fromCode(
                mappingConfig.resources().medicationAdministration().status()
        ));

        // Medikation mit Konfiguration aus externen Pfaden
        String medicationCode = findValueFromPaths(valueMap,
                mappingConfig.resources().medicationAdministration().paths().medication().code().paths(), null);

        if (medicationCode != null) {
            CodeableConcept medicationCodeConcept = new CodeableConcept();
            Coding coding = medicationCodeConcept.addCoding();

            coding.setSystem(findValueFromPaths(valueMap,
                    mappingConfig.resources().medicationAdministration().paths().medication().system().paths(),
                    mappingConfig.resources().medicationAdministration().paths().medication().system().default_()));

            coding.setCode(medicationCode);

            coding.setDisplay(findValueFromPaths(valueMap,
                    mappingConfig.resources().medicationAdministration().paths().medication().display().paths(), ""));

            medicationAdministration.setMedication(medicationCodeConcept);
        }

        // Dosierung mit Konfiguration aus externen Pfaden
        String doseValue = findValueFromPaths(valueMap,
                mappingConfig.resources().medicationAdministration().paths().dosage().value().paths(), null);

        if (doseValue != null) {
            medicationAdministration.setDosage(new MedicationAdministration.MedicationAdministrationDosageComponent()
                    .setDose(new Quantity()
                            .setValue(Double.parseDouble(doseValue))
                            .setSystem(mappingConfig.resources().medicationAdministration().paths().dosage().unitSystem().default_())
                            .setUnit(findValueFromPaths(valueMap,
                                    mappingConfig.resources().medicationAdministration().paths().dosage().unit().paths(), null))));
        }

        // Route mit Konfiguration aus externen Pfaden
        String routeCode = findValueFromPaths(valueMap,
                mappingConfig.resources().medicationAdministration().paths().route().code().paths(), null);

        if (routeCode != null) {
            medicationAdministration.getDosage().setRoute(new CodeableConcept().addCoding(new Coding()
                    .setCode(routeCode)
                    .setDisplay(findValueFromPaths(valueMap,
                            mappingConfig.resources().medicationAdministration().paths().route().display().paths(), null))
                    .setSystem(findValueFromPaths(valueMap,
                            mappingConfig.resources().medicationAdministration().paths().route().system().paths(), null))));
        }

        return medicationAdministration;
    }


    /**
     * Erstellt eine Observation-Resource basierend auf dem Sektionstyp
     */
    private Observation createObservation(String inhaltId, Map<String, String> valueMap, String sectionCode) {
        if ("29762-2".equals(sectionCode)) {
            return createSocialHistoryObservation(inhaltId, valueMap);
        } else if ("8716-3".equals(sectionCode)) {
            return createVitalSignObservation(inhaltId, valueMap);
        } else {
            return createGenericObservation(inhaltId, valueMap, sectionCode);
        }
    }

    /**
     * Erstellt eine Observation für Sozialanamnese
     */
    private Observation createSocialHistoryObservation(String inhaltId, Map<String, String> valueMap) {
        Observation observation = new Observation();

        // ID setzen
        observation.setId("obs-" + inhaltId);

        // Status setzen
        observation.setStatus(Observation.ObservationStatus.FINAL);

        observation.setSubject(new Reference("Patient/3"));

        observation.setCode(new CodeableConcept().addCoding(new Coding()
                .setSystem(findValueFromPaths(valueMap, mappingConfig.resources().observation().socialHistory().paths().code().system().paths(), null))
                .setDisplay(findValueFromPaths(valueMap, mappingConfig.resources().observation().socialHistory().paths().code().display().paths(), null))
                .setCode(findValueFromPaths(valueMap, mappingConfig.resources().observation().socialHistory().paths().code().code().paths(), null))));


        Period period = new Period();
        period.setStartElement(new DateTimeType(findValueFromPaths(valueMap, mappingConfig.resources().observation().socialHistory().paths().effectiveDateTime().effectiveDateTimeLow().paths(), null)));
        period.setEndElement(new DateTimeType(findValueFromPaths(valueMap, mappingConfig.resources().observation().socialHistory().paths().effectiveDateTime().effectiveDateTimeHigh().paths(), null)));
        observation.setEffective(period);

//        // Code
//        if (valueMap.containsKey("observation.code@code")) {
//            CodeableConcept code = new CodeableConcept();
//            Coding coding = code.addCoding();
//            coding.setSystem(valueMap.getOrDefault("observation.code@codeSystem",
//                    "http://loinc.org"));
//            coding.setCode(valueMap.get("observation.code@code"));
//            coding.setDisplay(valueMap.getOrDefault("observation.code@displayName",
//                    "Social History Observation"));
//            observation.setCode(code);
//        }
//
//        // Zeitraum
//        if (valueMap.containsKey("observation.effectiveTime.low@value") ||
//                valueMap.containsKey("observation.effectiveTime.high@value")) {
//
//            Period period = new Period();
//
//            if (valueMap.containsKey("observation.effectiveTime.low@value")) {
//                period.setStartElement(new DateTimeType(valueMap.get("observation.effectiveTime.low@value")));
//            }
//
//            if (valueMap.containsKey("observation.effectiveTime.high@value")) {
//                period.setEndElement(new DateTimeType(valueMap.get("observation.effectiveTime.high@value")));
//            }
//
//            observation.setEffective(period);
//        }
//
//        // Wert, falls vorhanden
//        if (valueMap.containsKey("observation.value@code")) {
//            CodeableConcept valueCC = new CodeableConcept();
//            Coding valueCoding = valueCC.addCoding();
//            valueCoding.setSystem(valueMap.getOrDefault("observation.value@codeSystem",
//                    "http://terminology.hl7.org/CodeSystem/v3-NullFlavor"));
//            valueCoding.setCode(valueMap.get("observation.value@code"));
//            valueCoding.setDisplay(valueMap.getOrDefault("observation.value@displayName", ""));
//            observation.setValue(valueCC);
//        } else if (valueMap.containsKey("observation.value@value")) {
//            // Versuchen, den Wert als Zahl zu interpretieren
//            try {
//                Double numericValue = Double.parseDouble(valueMap.get("observation.value@value"));
//                Quantity quantity = new Quantity();
//                quantity.setValue(numericValue);
//                if (valueMap.containsKey("observation.value@unit")) {
//                    quantity.setUnit(valueMap.get("observation.value@unit"));
//                }
//                observation.setValue(quantity);
//            } catch (NumberFormatException e) {
//                // Falls keine Zahl, dann als Text behandeln
//                observation.setValue(new StringType(valueMap.get("observation.value@value")));
//            }
//        }

        return observation;
    }

    /**
     * Erstellt eine Observation für Vitalparameter
     */
    private Observation createVitalSignObservation(String inhaltId, Map<String, String> valueMap) {
        Observation observation = new Observation();

        // ID setzen
        observation.setId("vital-" + inhaltId);

        // Status setzen
        observation.setStatus(Observation.ObservationStatus.FINAL);

        observation.setSubject(new Reference("Patient/3"));

        observation.setCode(new CodeableConcept().addCoding(new Coding()
                .setCode(findValueFromPaths(valueMap, mappingConfig.resources().observation().vitalSigns().paths().code().code().paths(), null))
                .setDisplay(findValueFromPaths(valueMap, mappingConfig.resources().observation().vitalSigns().paths().code().display().paths(), null))
                .setSystem(findValueFromPaths(valueMap, mappingConfig.resources().observation().vitalSigns().paths().code().system().paths(), null))));


        if (Objects.equals(findValueFromPaths(valueMap, mappingConfig.resources().observation().vitalSigns().paths().value().type().paths(), null), "RTO_PQ_PQ")) {
            observation.setValue(new Ratio()
                    .setNumerator(new Quantity(castSaveToDouble(findValueFromPaths(valueMap, mappingConfig.resources().observation().vitalSigns().paths().valueRatio().numerator().value().paths(), null))))
                    .setDenominator(new Quantity(castSaveToDouble(findValueFromPaths(valueMap, mappingConfig.resources().observation().vitalSigns().paths().valueRatio().denominator().value().paths(), null)))
                            .setUnit(findValueFromPaths(valueMap, mappingConfig.resources().observation().vitalSigns().paths().valueRatio().denominator().unit().paths(), null))));
        } else {
            observation.setValue(new Quantity()
                    .setUnit(findValueFromPaths(valueMap, mappingConfig.resources().observation().vitalSigns().paths().value().unit().paths(), null))
                    .setValue(new BigDecimal(findValueFromPaths(valueMap, mappingConfig.resources().observation().vitalSigns().paths().value().value().paths(), null)))
                    .setSystem(findValueFromPaths(valueMap, mappingConfig.resources().observation().vitalSigns().paths().value().system().paths(), null))
                    .setCode(findValueFromPaths(valueMap, mappingConfig.resources().observation().vitalSigns().paths().value().code().paths(), null)));
        }

        return observation;
    }

    /**
     * Erstellt eine generische Observation
     */
    private Observation createGenericObservation(String inhaltId, Map<String, String> valueMap, String sectionTitle) {
        Observation observation = new Observation();

        // ID setzen
        observation.setId("obs-" + inhaltId);

        // Status setzen
        observation.setStatus(Observation.ObservationStatus.FINAL);

        // Code für die Beobachtung (basierend auf der Sektion, falls kein spezifischer Code vorhanden)
        if (valueMap.containsKey("observation.code@code")) {
            CodeableConcept code = new CodeableConcept();
            Coding coding = code.addCoding();
            coding.setSystem(valueMap.getOrDefault("observation.code@codeSystem",
                    "http://terminology.hl7.org/CodeSystem/observation-category"));
            coding.setCode(valueMap.get("observation.code@code"));
            coding.setDisplay(valueMap.getOrDefault("observation.code@displayName", sectionTitle));
            observation.setCode(code);
        } else {
            // Fallback-Code basierend auf Sektionsname
            CodeableConcept code = new CodeableConcept();
            code.setText(sectionTitle);
            observation.setCode(code);
        }

        // Wert (abhängig vom Typ in den Quelldaten)
        if (valueMap.containsKey("observation.value@code")) {
            // Konzept-Wert
            CodeableConcept valueCC = new CodeableConcept();
            Coding valueCoding = valueCC.addCoding();
            valueCoding.setSystem(valueMap.getOrDefault("observation.value@codeSystem",
                    "http://terminology.hl7.org/CodeSystem/v3-NullFlavor"));
            valueCoding.setCode(valueMap.get("observation.value@code"));
            valueCoding.setDisplay(valueMap.getOrDefault("observation.value@displayName", ""));
            observation.setValue(valueCC);
        } else if (valueMap.containsKey("observation.value@value")) {
            // Versuchen als Zahl zu interpretieren, sonst Text
            try {
                Double numericValue = Double.parseDouble(valueMap.get("observation.value@value"));
                Quantity quantity = new Quantity();
                quantity.setValue(numericValue);
                if (valueMap.containsKey("observation.value@unit")) {
                    quantity.setUnit(valueMap.get("observation.value@unit"));
                }
                observation.setValue(quantity);
            } catch (NumberFormatException e) {
                observation.setValue(new StringType(valueMap.get("observation.value@value")));
            }
        }

        // Zeit/Zeitraum
        if (valueMap.containsKey("observation.effectiveTime@value")) {
            observation.setEffective(new DateTimeType(valueMap.get("observation.effectiveTime@value")));
        } else if (valueMap.containsKey("observation.effectiveTime.low@value") ||
                valueMap.containsKey("observation.effectiveTime.high@value")) {

            Period period = new Period();
            if (valueMap.containsKey("observation.effectiveTime.low@value")) {
                period.setStartElement(new DateTimeType(valueMap.get("observation.effectiveTime.low@value")));
            }
            if (valueMap.containsKey("observation.effectiveTime.high@value")) {
                period.setEndElement(new DateTimeType(valueMap.get("observation.effectiveTime.high@value")));
            }
            observation.setEffective(period);
        }

        return observation;
    }

    /**
     * Erstellt eine AllergyIntolerance-Resource
     */
    private AllergyIntolerance createAllergyIntolerance(String inhaltId, Map<String, String> valueMap) {
        AllergyIntolerance allergyIntolerance = new AllergyIntolerance();

        // ID setzen
        allergyIntolerance.setId("allergy-" + inhaltId);

        // Status (standardmäßig aktiv)
        allergyIntolerance.setClinicalStatus(new CodeableConcept().addCoding(
                new Coding().setSystem("http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical")
                        .setCode("active").setDisplay("Active")));

        // Verifizierungsstatus
        allergyIntolerance.setVerificationStatus(new CodeableConcept().addCoding(
                new Coding().setSystem("http://terminology.hl7.org/CodeSystem/allergyintolerance-verification")
                        .setCode("confirmed").setDisplay("Confirmed")));

        // Typ (Allergie vs. Intoleranz)
        if (valueMap.containsKey("observation.code@code")) {
            String code = valueMap.get("observation.code@code");
            if (code.contains("allergy")) {
                allergyIntolerance.setType(AllergyIntolerance.AllergyIntoleranceType.ALLERGY);
            } else if (code.contains("intolerance")) {
                allergyIntolerance.setType(AllergyIntolerance.AllergyIntoleranceType.INTOLERANCE);
            }
        }

        // Kategorie (Medikament, Nahrung, Umwelt)
        if (valueMap.containsKey("observation.code@code") || valueMap.containsKey("observation.value@code")) {
            String code = valueMap.getOrDefault("observation.code@code",
                    valueMap.getOrDefault("observation.value@code", ""));

            if (code.contains("drug") || code.contains("medication")) {
                allergyIntolerance.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION);
            } else if (code.contains("food")) {
                allergyIntolerance.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.FOOD);
            } else if (code.contains("environment")) {
                allergyIntolerance.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.ENVIRONMENT);
            }
        }

        // Code für das Allergen
        if (valueMap.containsKey("observation.value@code")) {
            CodeableConcept allergen = new CodeableConcept();
            Coding allergenCoding = allergen.addCoding();
            allergenCoding.setSystem(valueMap.getOrDefault("observation.value@codeSystem",
                    "http://terminology.hl7.org/CodeSystem/v3-NullFlavor"));
            allergenCoding.setCode(valueMap.get("observation.value@code"));
            allergenCoding.setDisplay(valueMap.getOrDefault("observation.value@displayName", ""));
            allergyIntolerance.setCode(allergen);
        }

        // Reaktionsdetails, falls vorhanden
        if (valueMap.containsKey("observation.text()")) {
            AllergyIntolerance.AllergyIntoleranceReactionComponent reaction = new AllergyIntolerance.AllergyIntoleranceReactionComponent();
            reaction.setDescription(valueMap.get("observation.text()"));
            allergyIntolerance.addReaction(reaction);
        }

        return allergyIntolerance;
    }

    /**
     * Erstellt eine Condition-Resource
     */
    private Condition createCondition(String inhaltId, Map<String, String> valueMap) {
        Condition condition = new Condition();

        // ID setzen
        condition.setId("cond-" + inhaltId);

        // Klinischer Status
        condition.setClinicalStatus(new CodeableConcept().addCoding(
                new Coding().setSystem("http://terminology.hl7.org/CodeSystem/condition-clinical")
                        .setCode("active").setDisplay("Active")));

        // Verifizierungsstatus
        condition.setVerificationStatus(new CodeableConcept().addCoding(
                new Coding().setSystem("http://terminology.hl7.org/CodeSystem/condition-ver-status")
                        .setCode("confirmed").setDisplay("Confirmed")));

        // Kategorie (Problem)
        condition.addCategory(new CodeableConcept().addCoding(
                new Coding().setSystem("http://terminology.hl7.org/CodeSystem/condition-category")
                        .setCode("problem-list-item").setDisplay("Problem List Item")));

        // Code für die Kondition
        if (valueMap.containsKey("act.code@code") || valueMap.containsKey("observation.value@code")) {
            CodeableConcept code = new CodeableConcept();
            Coding coding = code.addCoding();

            if (valueMap.containsKey("act.code@code")) {
                coding.setSystem(valueMap.getOrDefault("act.code@codeSystem",
                        "http://terminology.hl7.org/CodeSystem/v3-NullFlavor"));
                coding.setCode(valueMap.get("act.code@code"));
                coding.setDisplay(valueMap.getOrDefault("act.code@displayName", ""));
            } else {
                coding.setSystem(valueMap.getOrDefault("observation.value@codeSystem",
                        "http://terminology.hl7.org/CodeSystem/v3-NullFlavor"));
                coding.setCode(valueMap.get("observation.value@code"));
                coding.setDisplay(valueMap.getOrDefault("observation.value@displayName", ""));
            }

            condition.setCode(code);
        }

        // Datum der Aufzeichnung
        if (valueMap.containsKey("act.effectiveTime@value")) {
            condition.setRecordedDate(new Date(valueMap.get("act.effectiveTime@value")));
        } else if (valueMap.containsKey("observation.effectiveTime@value")) {
            condition.setRecordedDate(new Date(valueMap.get("observation.effectiveTime@value")));
        }

        // Zeitraum
        if (valueMap.containsKey("act.effectiveTime.low@value") || valueMap.containsKey("act.effectiveTime.high@value") ||
                valueMap.containsKey("observation.effectiveTime.low@value") || valueMap.containsKey("observation.effectiveTime.high@value")) {

            Period period = new Period();

            if (valueMap.containsKey("act.effectiveTime.low@value")) {
                period.setStartElement(new DateTimeType(valueMap.get("act.effectiveTime.low@value")));
            } else if (valueMap.containsKey("observation.effectiveTime.low@value")) {
                period.setStartElement(new DateTimeType(valueMap.get("observation.effectiveTime.low@value")));
            }

            if (valueMap.containsKey("act.effectiveTime.high@value")) {
                period.setEndElement(new DateTimeType(valueMap.get("act.effectiveTime.high@value")));
            } else if (valueMap.containsKey("observation.effectiveTime.high@value")) {
                period.setEndElement(new DateTimeType(valueMap.get("observation.effectiveTime.high@value")));
            }

            condition.setOnset(period);
        }

        return condition;
    }
}