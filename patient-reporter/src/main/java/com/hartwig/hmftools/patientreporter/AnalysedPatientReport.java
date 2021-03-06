package com.hartwig.hmftools.patientreporter;

import java.util.List;
import java.util.Optional;

import com.hartwig.hmftools.common.actionability.ClinicalTrial;
import com.hartwig.hmftools.common.actionability.EvidenceItem;
import com.hartwig.hmftools.common.chord.ChordStatus;
import com.hartwig.hmftools.common.fusion.ReportableGeneFusion;
import com.hartwig.hmftools.common.variant.msi.MicrosatelliteStatus;
import com.hartwig.hmftools.common.variant.tml.TumorMutationalStatus;
import com.hartwig.hmftools.patientreporter.homozygousdisruption.ReportableHomozygousDisruption;
import com.hartwig.hmftools.patientreporter.purple.ReportableGainLoss;
import com.hartwig.hmftools.patientreporter.structural.ReportableGeneDisruption;
import com.hartwig.hmftools.patientreporter.variants.ReportableVariant;
import com.hartwig.hmftools.patientreporter.viralInsertion.ViralInsertion;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class AnalysedPatientReport implements PatientReport {

    @Override
    @NotNull
    public abstract SampleReport sampleReport();

    @NotNull
    @Override
    public abstract String qsFormNumber();

    public abstract double impliedPurity();

    public abstract boolean hasReliablePurity();

    public abstract boolean hasReliableQuality();

    public abstract double averageTumorPloidy();

    @NotNull
    public abstract String clinicalSummary();

    @NotNull
    public abstract List<EvidenceItem> tumorSpecificEvidence();

    @NotNull
    public abstract List<ClinicalTrial> clinicalTrials();

    @NotNull
    public abstract List<EvidenceItem> offLabelEvidence();

    @NotNull
    public abstract List<ReportableVariant> reportableVariants();

    public abstract double microsatelliteIndelsPerMb();

    @NotNull
    public abstract MicrosatelliteStatus microsatelliteStatus();

    public abstract int tumorMutationalLoad();

    @NotNull
    public abstract TumorMutationalStatus tumorMutationalLoadStatus();

    public abstract double tumorMutationalBurden();

    public abstract double chordHrdValue();

    @NotNull
    public abstract ChordStatus chordHrdStatus();

    @NotNull
    public abstract List<ReportableGainLoss> gainsAndLosses();

    @NotNull
    public abstract List<ReportableGeneFusion> geneFusions();

    @NotNull
    public abstract List<ReportableGeneDisruption> geneDisruptions();

    @NotNull
    public abstract List<ReportableHomozygousDisruption> homozygousDisruptions();

    @Nullable
    public abstract List<ViralInsertion> viralInsertions();

    @NotNull
    public abstract String circosPath();

    @Override
    @NotNull
    public abstract Optional<String> comments();

    public abstract boolean isUnofficialReport();

    @Override
    public abstract boolean isCorrectedReport();

    @Override
    @NotNull
    public abstract String signaturePath();

    @Override
    @NotNull
    public abstract String logoRVAPath();

    @Override
    @NotNull
    public abstract String logoCompanyPath();
}
