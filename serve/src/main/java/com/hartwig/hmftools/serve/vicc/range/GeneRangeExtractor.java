package com.hartwig.hmftools.serve.vicc.range;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.vicc.datamodel.Feature;
import com.hartwig.hmftools.vicc.datamodel.ViccEntry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

public class GeneRangeExtractor {
    private static final Logger LOGGER = LogManager.getLogger(GeneRangeExtractor.class);

    private static final Set<String> GENE_EXON = Sets.newHashSet("exon");
    private static final Set<String> GENE_MULTIPLE_CODONS = Sets.newHashSet("nonsense", "V600E/K");

    @NotNull
    public Map<Feature, String> extractGeneRanges(@NotNull ViccEntry viccEntry) {
        Map<Feature, String> geneRangesPerFeature = Maps.newHashMap();
        for (Feature feature : viccEntry.features()) {
            String event = Strings.EMPTY;
            if (feature.name().toLowerCase().contains("exon")) {
                event = "exon";
            }

            if (GENE_EXON.contains(feature.name().toLowerCase()) || GENE_EXON.contains(event)) {
                geneRangesPerFeature.put(feature, feature.name());
            } else if (GENE_MULTIPLE_CODONS.contains(feature.biomarkerType()) && feature.proteinAnnotation()
                    .substring(feature.proteinAnnotation().length() - 1)
                    .equals("X") || GENE_MULTIPLE_CODONS.contains(feature.proteinAnnotation())) {
                //TODO: possible using transvar for the codons
                geneRangesPerFeature.put(feature, feature.name());
            }
        }

        return geneRangesPerFeature;
    }
}
