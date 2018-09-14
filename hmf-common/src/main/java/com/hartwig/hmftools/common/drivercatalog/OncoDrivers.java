package com.hartwig.hmftools.common.drivercatalog;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.dnds.DndsDriverGeneLikelihood;
import com.hartwig.hmftools.common.dnds.DndsDriverImpactLikelihood;
import com.hartwig.hmftools.common.numeric.Doubles;
import com.hartwig.hmftools.common.variant.CodingEffect;
import com.hartwig.hmftools.common.variant.EnrichedSomaticVariant;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.VariantType;

import org.jetbrains.annotations.NotNull;

public class OncoDrivers {

    static final int MAX_REPEAT_COUNT = 7;

    @NotNull
    public static List<DriverCatalog> oncoDrivers(@NotNull final Map<String, DndsDriverGeneLikelihood> likelihoodsByGene,
            @NotNull final List<EnrichedSomaticVariant> variants) {

        final List<DriverCatalog> driverCatalog = Lists.newArrayList();

        final Map<VariantType, Long> variantTypeCounts =
                variants.stream().filter(x -> !x.isFiltered()).collect(Collectors.groupingBy(SomaticVariant::type, Collectors.counting()));
        long sampleSNVCount = variantTypeCounts.getOrDefault(VariantType.SNP, 0L);

        final Map<String, List<EnrichedSomaticVariant>> codingVariants =
                DriverCatalogFactory.codingVariantsByGene(likelihoodsByGene.keySet(), variants);

        for (String gene : codingVariants.keySet()) {

            final DndsDriverImpactLikelihood geneMissenseLikelihood = likelihoodsByGene.get(gene).missense();
            final List<EnrichedSomaticVariant> geneVariants = codingVariants.get(gene);

            driverCatalog.add(geneDriver(sampleSNVCount, gene, geneMissenseLikelihood, geneVariants));
        }

        return driverCatalog;
    }

    @NotNull
    static DriverCatalog geneDriver(long sampleSNVCount, @NotNull final String gene,
            @NotNull final DndsDriverImpactLikelihood missenseLikelihood, @NotNull final List<EnrichedSomaticVariant> codingVariants) {

        final Map<DriverImpact, Long> variantCounts = DriverCatalogFactory.driverImpactCount(codingVariants);
        long missenseVariants = variantCounts.getOrDefault(DriverImpact.MISSENSE, 0L);
        long nonsenseVariants = variantCounts.getOrDefault(DriverImpact.NONSENSE, 0L);
        long spliceVariants = variantCounts.getOrDefault(DriverImpact.SPLICE, 0L);
        long inframeVariants = variantCounts.getOrDefault(DriverImpact.INFRAME, 0L);
        long frameshiftVariants = variantCounts.getOrDefault(DriverImpact.FRAMESHIFT, 0L);

        final ImmutableDriverCatalog.Builder builder = ImmutableDriverCatalog.builder()
                .gene(gene)
                .category(DriverCategory.ONCO)
                .driverLikelihood(1)
                .dndsLikelihood(missenseVariants > 0 ? missenseLikelihood.dndsLikelihood() : 0)
                .missense(missenseVariants)
                .nonsense(nonsenseVariants)
                .splice(spliceVariants)
                .inframe(inframeVariants)
                .frameshift(frameshiftVariants)
                .driver(missenseVariants > 0 ? DriverType.DNDS : DriverType.NONE);

        if (codingVariants.stream().anyMatch(SomaticVariant::hotspot)) {
            return builder.driver(DriverType.HOTSPOT).build();
        }

        // TODO: Add NEAR_HOTSPOT HERE

        if (codingVariants.stream().anyMatch(OncoDrivers::isInframeIndel)) {
            return builder.driver(DriverType.INFRAME).build();
        }

        final double driverLikelihood =
                Doubles.positive(missenseLikelihood.dndsLikelihood()) && missenseVariants > 0 ? missenseProbabilityDriverVariant(
                        sampleSNVCount,
                        missenseLikelihood) : 0;

        return builder.driverLikelihood(driverLikelihood).build();
    }

    private static boolean isInframeIndel(@NotNull final EnrichedSomaticVariant variant) {
        return variant.type() == VariantType.INDEL && variant.canonicalCodingEffect() == CodingEffect.MISSENSE
                && variant.repeatCount() <= MAX_REPEAT_COUNT;
    }

    public static double missenseProbabilityDriverVariant(long sampleSNVCount, @NotNull final DndsDriverImpactLikelihood likelihood) {
        return DriverCatalogFactory.probabilityDriverVariant(sampleSNVCount, likelihood);
    }

}
