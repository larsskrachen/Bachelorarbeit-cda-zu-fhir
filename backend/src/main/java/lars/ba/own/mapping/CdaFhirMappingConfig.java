package lars.ba.own.mapping;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.List;

@ConfigMapping(prefix = "cda-mapping")
public interface CdaFhirMappingConfig {
    ResourcesMapping resources();

    interface ResourcesMapping {
        MedicationAdministrationMapping medicationAdministration();
        ObservationMapping observation();
    }

    interface MedicationAdministrationMapping {
        String profile();
        String status();
        MedicationAdminPathsMapping paths();
    }

    interface MedicationAdminPathsMapping {
        MedicationMapping medication();
        DosageMapping dosage();
        RouteMapping route();
    }

    interface MedicationMapping {
        PathsConfig code();
        PathsConfig display();
        SystemPathsConfig system();
    }

    interface DosageMapping {
        PathsConfig value();
        PathsConfig unit();
        DefaultConfig unitSystem();
    }

    interface RouteMapping {
        PathsConfig code();
        PathsConfig display();
        PathsConfig system();
    }

    interface PathsConfig {
        List<String> paths();
    }

    interface ObservationMapping{
        @WithName("social-history")
        SocialHistoryMapping socialHistory();
        @WithName("vital-signs")
        VitalSignsMapping vitalSigns();
    }

    interface VitalSignsMapping{
        String profile();
        String status();
        VitalSignPathsMapping paths();
    }

    interface SocialHistoryMapping{
        String profile();
        String status();
        SocialHistoryPathsMapping paths();
    }

    interface VitalSignPathsMapping{
        VitalSignCode code();
        @WithName("effectiveTime")
        VitalSignEffectivTime effectiveTime();
        @WithName("value")
        VitalSignValue value();
        @WithName("valueRatio")
        VitalSignValueRatio valueRatio();
    }

    interface VitalSignValue{
        PathsConfig type();
        PathsConfig value();
        PathsConfig display();
        PathsConfig system();
        PathsConfig code();
        PathsConfig unit();
    }
    interface VitalSignValueRatio{
        NumeratorMapping numerator();
        DenominatorMapping denominator();
    }

    interface NumeratorMapping{
        PathsConfig value();
        PathsConfig unit();
    }

    interface DenominatorMapping{
        PathsConfig value();
        PathsConfig unit();
    }

    interface SocialHistoryPathsMapping{
        SocialHistoryCode code();
        @WithName("effectiveDateTime")
        SocialHistoryEffectivDateTime effectiveDateTime();
    }

    interface VitalSignEffectivTime{
        @WithName("effectiveTime")
        PathsConfig effectiveTime();
    }

    interface SocialHistoryEffectivDateTime{
        @WithName("effectiveDateTimeLow")
        PathsConfig effectiveDateTimeLow();
        @WithName("effectiveDateTimeHigh")
        PathsConfig effectiveDateTimeHigh();
    }

    interface VitalSignCode {
        PathsConfig code();
        PathsConfig display();
        SystemPathsConfig system();
    }

    interface SocialHistoryCode {
        PathsConfig code();
        PathsConfig display();
        SystemPathsConfig system();
    }

    interface SystemPathsConfig {
        List<String> paths();
        @WithName("default")
        String default_();
    }

    interface DefaultConfig {
        @WithName("default")
        String default_();
    }
}


