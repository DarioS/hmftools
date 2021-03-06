package com.hartwig.hmftools.patientdb.readers.cpct;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.ecrf.datamodel.EcrfPatient;
import com.hartwig.hmftools.common.ecrf.datamodel.ValidationFinding;
import com.hartwig.hmftools.patientdb.curators.BiopsySiteCurator;
import com.hartwig.hmftools.patientdb.curators.TreatmentCurator;
import com.hartwig.hmftools.patientdb.curators.TumorLocationCurator;
import com.hartwig.hmftools.patientdb.data.BaselineData;
import com.hartwig.hmftools.patientdb.data.BiopsyData;
import com.hartwig.hmftools.patientdb.data.BiopsyTreatmentData;
import com.hartwig.hmftools.patientdb.data.BiopsyTreatmentResponseData;
import com.hartwig.hmftools.patientdb.data.Patient;
import com.hartwig.hmftools.patientdb.data.PreTreatmentData;
import com.hartwig.hmftools.patientdb.data.RanoMeasurementData;
import com.hartwig.hmftools.patientdb.data.SampleData;
import com.hartwig.hmftools.patientdb.data.TumorMarkerData;
import com.hartwig.hmftools.patientdb.matchers.BiopsyMatcher;
import com.hartwig.hmftools.patientdb.matchers.MatchResult;
import com.hartwig.hmftools.patientdb.matchers.TreatmentMatcher;
import com.hartwig.hmftools.patientdb.matchers.TreatmentResponseMatcher;
import com.hartwig.hmftools.patientdb.readers.EcrfPatientReader;

import org.jetbrains.annotations.NotNull;

public class CpctPatientReader implements EcrfPatientReader {

    @NotNull
    private final BaselineReader baselineReader;
    @NotNull
    private final PreTreatmentReader preTreatmentReader;
    @NotNull
    private final BiopsyReader biopsyReader;
    @NotNull
    private final BiopsyTreatmentReader biopsyTreatmentReader;

    public CpctPatientReader(@NotNull TumorLocationCurator tumorLocationCurator, @NotNull Map<Integer, String> hospitals,
            @NotNull BiopsySiteCurator biopsySiteCurator, @NotNull TreatmentCurator treatmentCurator) {
        this.baselineReader = new BaselineReader(tumorLocationCurator, hospitals);
        this.preTreatmentReader = new PreTreatmentReader(treatmentCurator);
        this.biopsyReader = new BiopsyReader(biopsySiteCurator);
        this.biopsyTreatmentReader = new BiopsyTreatmentReader(treatmentCurator);
    }

    @NotNull
    @Override
    public Patient read(@NotNull EcrfPatient ecrfPatient, @NotNull List<SampleData> sequencedSamples) {
        BaselineData baselineData = baselineReader.read(ecrfPatient);
        PreTreatmentData preTreatmentData = preTreatmentReader.read(ecrfPatient);
        List<BiopsyData> clinicalBiopsies = biopsyReader.read(ecrfPatient, baselineData.curatedTumorLocation());
        List<BiopsyTreatmentData> treatments = biopsyTreatmentReader.read(ecrfPatient);
        List<BiopsyTreatmentResponseData> treatmentResponses = BiopsyTreatmentResponseReader.read(ecrfPatient);
        List<TumorMarkerData> tumorMarkers = TumorMarkerReader.read(ecrfPatient);
        List<RanoMeasurementData> ranoMeasurements = RanoMeasurementReader.read(ecrfPatient);

        MatchResult<BiopsyData> matchedBiopsies =
                BiopsyMatcher.matchBiopsiesToTumorSamples(ecrfPatient.patientId(), sequencedSamples, clinicalBiopsies);
        MatchResult<BiopsyTreatmentData> matchedTreatments =
                TreatmentMatcher.matchTreatmentsToBiopsies(ecrfPatient.patientId(), withSampleMatchOnly(matchedBiopsies), treatments);

        // We also match responses to unmatched treatments. Not sure that is optimal. See also DEV-477.
        MatchResult<BiopsyTreatmentResponseData> matchedResponses =
                TreatmentResponseMatcher.matchTreatmentResponsesToTreatments(ecrfPatient.patientId(),
                        matchedTreatments.values(),
                        treatmentResponses);

        List<ValidationFinding> findings = Lists.newArrayList();
        findings.addAll(matchedBiopsies.findings());
        findings.addAll(matchedTreatments.findings());
        findings.addAll(matchedResponses.findings());

        return new Patient(ecrfPatient.patientId(),
                baselineData,
                preTreatmentData,
                sequencedSamples,
                matchedBiopsies.values(),
                matchedTreatments.values(),
                matchedResponses.values(),
                tumorMarkers,
                ranoMeasurements,
                findings);
    }

    @NotNull
    private static List<BiopsyData> withSampleMatchOnly(@NotNull MatchResult<BiopsyData> biopsies) {
        List<BiopsyData> biopsiesWithMatchedSample = Lists.newArrayList();
        for (BiopsyData biopsy : biopsies.values()) {
            if (biopsy.sampleId() != null) {
                biopsiesWithMatchedSample.add(biopsy);
            }
        }
        return biopsiesWithMatchedSample;
    }
}
