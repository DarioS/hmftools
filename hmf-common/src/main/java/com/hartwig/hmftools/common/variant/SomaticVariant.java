package com.hartwig.hmftools.common.variant;

import java.util.List;

import com.hartwig.hmftools.common.purple.region.GermlineStatus;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SomaticVariant extends Variant {

    double qual();

    @NotNull
    VariantType type();

    @NotNull
    String filter();

    @Nullable
    String dbsnpID();

    @NotNull
    List<String> cosmicIDs();

    @Nullable
    String canonicalCosmicID();

    int genesAffected();

    @NotNull
    String worstEffect();

    @NotNull
    String worstEffectTranscript();

    @NotNull
    CodingEffect worstCodingEffect();

    @NotNull
    String canonicalEffect();

    @NotNull
    Hotspot hotspot();

    boolean recovered();

    default boolean isHotspot() {
        return hotspot() == Hotspot.HOTSPOT;
    }

    double mappability();

    default boolean isDBSNP() {
        return dbsnpID() != null;
    }

    default boolean isCOSMIC() {
        return !cosmicIDs().isEmpty();
    }

    default boolean isFiltered() {
        return !filter().equals(SomaticVariantFactory.PASS_FILTER);
    }

    default boolean isSnp() {
        return type() == VariantType.SNP;
    }

    double adjustedCopyNumber();

    double adjustedVAF();

    double minorAlleleCopyNumber();

    double variantCopyNumber();

    boolean biallelic();

    @NotNull
    GermlineStatus germlineStatus();

    @NotNull
    String trinucleotideContext();

    @NotNull
    String microhomology();

    @NotNull
    String repeatSequence();

    int repeatCount();

    boolean highConfidenceRegion();

    @NotNull
    String kataegis();

    @NotNull
    VariantTier tier();

    double subclonalLikelihood();

    default double clonalLikelihood() {
        return 1 - subclonalLikelihood();
    }

    @Nullable
    AllelicDepth rnaDepth();

    @Nullable
    AllelicDepth referenceDepth();
}