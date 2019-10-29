package com.hartwig.hmftools.patientreporter.variants;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.actionability.ActionabilityAnalyzer;
import com.hartwig.hmftools.common.actionability.EvidenceItem;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.drivercatalog.DriverCategory;
import com.hartwig.hmftools.common.ecrf.projections.PatientTumorLocation;
import com.hartwig.hmftools.common.lims.LimsGermlineReportingChoice;
import com.hartwig.hmftools.common.position.GenomePosition;
import com.hartwig.hmftools.common.variant.Hotspot;
import com.hartwig.hmftools.common.variant.ImmutableReportableVariant;
import com.hartwig.hmftools.common.variant.ReportableVariant;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.patientreporter.actionability.ReportableEvidenceItemFactory;
import com.hartwig.hmftools.patientreporter.variants.driver.DriverGeneView;
import com.hartwig.hmftools.patientreporter.variants.germline.GermlineReportingModel;
import com.hartwig.hmftools.patientreporter.variants.germline.GermlineVariant;
import com.hartwig.hmftools.patientreporter.variants.germline.ReportableGermlineVariant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReportableVariantAnalyzer {

    private static final Logger LOGGER = LogManager.getLogger(ReportableVariantAnalyzer.class);

    private ReportableVariantAnalyzer() {
    }

    @NotNull
    public static ReportVariantAnalysis mergeSomaticAndGermlineVariants(@NotNull List<SomaticVariant> somaticVariantsReport,
            @NotNull List<DriverCatalog> driverCatalog, @NotNull DriverGeneView driverGeneView,
            @NotNull List<ReportableGermlineVariant> germlineVariantsToReport, @NotNull GermlineReportingModel germlineReportingModel,
            @NotNull LimsGermlineReportingChoice germlineReportingChoice, @NotNull ActionabilityAnalyzer actionabilityAnalyzer,
            @Nullable PatientTumorLocation patientTumorLocation) {
        List<ReportableVariant> allReportableVariants = Lists.newArrayList();
        for (SomaticVariant variant : somaticVariantsReport) {
            DriverCatalog catalog = catalogEntryForVariant(driverCatalog, variant.gene());
            if (catalog == null) {
                throw new IllegalStateException("No driver entry found for gene: " + variant.gene());
            }
            double driverLikelihood = catalog.driverLikelihood();
            for (ReportableGermlineVariant germlineVariant : germlineVariantsToReport) {
                if (germlineVariant.variant().gene().equals(variant.gene())) {
                    driverLikelihood = Math.max(driverLikelihood, germlineVariant.driverLikelihood());
                }
            }
            allReportableVariants.add(fromSomaticVariant(variant).driverCategory(catalog.category())
                    .driverLikelihood(driverLikelihood)
                    .notifyClinicalGeneticist(false)
                    .build());
        }

        boolean wantsToBeNotified = germlineReportingChoice == LimsGermlineReportingChoice.ALL
                || germlineReportingChoice == LimsGermlineReportingChoice.ACTIONABLE_ONLY;
        for (ReportableGermlineVariant germlineVariant : germlineVariantsToReport) {
            DriverCategory category = driverGeneView.category(germlineVariant.variant().gene());
            if (category == null) {
                // This is not correct in theory but we assume every non-driver germline gene is a TSG.
                category = DriverCategory.TSG;
            }
            DriverCatalog catalog = catalogEntryForVariant(driverCatalog, germlineVariant.variant().gene());
            double driverLikelihood = germlineVariant.driverLikelihood();
            if (catalog != null) {
                driverLikelihood = Math.max(driverLikelihood, catalog.driverLikelihood());
            }
            allReportableVariants.add(fromGermlineVariant(germlineVariant.variant()).driverCategory(category)
                    .driverLikelihood(driverLikelihood)
                    .notifyClinicalGeneticist(wantsToBeNotified && germlineReportingModel.notifyAboutGene(germlineVariant.variant().gene()))
                    .build());

        }

        String primaryTumorLocation = patientTumorLocation != null ? patientTumorLocation.primaryTumorLocation() : null;
        Map<ReportableVariant, List<EvidenceItem>> evidencePerVariant =
                actionabilityAnalyzer.evidenceForAllVariants(allReportableVariants, primaryTumorLocation);

        // Extract somatic evidence for high drivers variants into flat list (See DEV-824)
        List<EvidenceItem> filteredEvidence =
                ReportableEvidenceItemFactory.reportableFlatListDriversAllVariant(evidencePerVariant, driverCatalog);

        // Check that all variants with high level evidence are reported (since they are in the driver catalog).
        for (Map.Entry<ReportableVariant, List<EvidenceItem>> entry : evidencePerVariant.entrySet()) {
            ReportableVariant variant = entry.getKey();
            if (!allReportableVariants.contains(variant) && !Collections.disjoint(entry.getValue(), filteredEvidence)) {
                LOGGER.warn("Evidence found on somatic variant on gene {} which is not included in driver catalog!", variant.gene());
            }
        }
        return ImmutableReportVariantAnalysis.of(allReportableVariants, filteredEvidence);
    }

    @Nullable
    private static DriverCatalog catalogEntryForVariant(@NotNull List<DriverCatalog> driverCatalogList, @NotNull String gene) {
        for (DriverCatalog entry : driverCatalogList) {
            if (entry.gene().equals(gene)) {
                return entry;
            }
        }
        return null;
    }

    @NotNull
    private static ImmutableReportableVariant.Builder fromGermlineVariant(@NotNull GermlineVariant variant) {
        return ImmutableReportableVariant.builder()
                .gene(variant.gene())
                .position(variant.position())
                .chromosome(variant.chromosome())
                .ref(variant.ref())
                .alt(variant.alt())
                .canonicalCodingEffect(variant.codingEffect())
                .gDNA(toGDNA(variant))
                .hgvsCodingImpact(variant.hgvsCodingImpact())
                .hgvsProteinImpact(variant.hgvsProteinImpact())
                .totalReadCount(variant.totalReadCount())
                .alleleReadCount(variant.alleleReadCount())
                .totalPloidy(variant.adjustedCopyNumber())
                .allelePloidy(calcAllelePloidy(variant.adjustedCopyNumber(), variant.adjustedVAF()))
                .hotspot(Hotspot.NON_HOTSPOT)
                .clonalLikelihood(1D)
                .biallelic(variant.biallelic());
    }

    @NotNull
    private static ImmutableReportableVariant.Builder fromSomaticVariant(@NotNull SomaticVariant variant) {
        return ImmutableReportableVariant.builder()
                .gene(variant.gene())
                .position(variant.position())
                .chromosome(variant.chromosome())
                .ref(variant.ref())
                .alt(variant.alt())
                .canonicalCodingEffect(variant.canonicalCodingEffect())
                .gDNA(toGDNA(variant))
                .hgvsCodingImpact(variant.canonicalHgvsCodingImpact())
                .hgvsProteinImpact(variant.canonicalHgvsProteinImpact())
                .totalReadCount(variant.totalReadCount())
                .alleleReadCount(variant.alleleReadCount())
                .totalPloidy(variant.adjustedCopyNumber())
                .allelePloidy(calcAllelePloidy(variant.adjustedCopyNumber(), variant.adjustedVAF()))
                .hotspot(variant.hotspot())
                .clonalLikelihood(variant.clonalLikelihood())
                .biallelic(variant.biallelic());
    }

    @NotNull
    private static String toGDNA(@NotNull GenomePosition genomePosition) {
        return genomePosition.chromosome() + ":" + genomePosition.position();
    }

    private static double calcAllelePloidy(double adjustedCopyNumber, double adjustedVAF) {
        return adjustedCopyNumber * Math.max(0, Math.min(1, adjustedVAF));
    }
}

