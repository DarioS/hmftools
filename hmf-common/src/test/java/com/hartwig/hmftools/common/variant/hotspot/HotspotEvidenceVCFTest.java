package com.hartwig.hmftools.common.variant.hotspot;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import htsjdk.variant.variantcontext.VariantContext;

public class HotspotEvidenceVCFTest {

    private static final String CHROM = "1";
    private static final int POS = 100;

    private static final int NORMAL_READS = 10;
    private static final int NORMAL_REF_COUNT = 5;
    private static final int TUMOR_READS = 100;
    private static final int TUMOR_REF_COUNT = 40;

    @Test
    public void testCombine() {
        final HotspotEvidence first = create("G", "A", 0, 19, 1000);
        final HotspotEvidence second = create("G", "C", 0, 20, 998);
        final HotspotEvidence third = create("G", "T", 2, 30, 10000);

        final List<HotspotEvidence> unsorted = Lists.newArrayList(first, third, second);
        Collections.shuffle(unsorted);

        final VariantContext context = new HotspotEvidenceVCF("NORMAL", "TUMOR", 0.01, 2, 0, 0, 100, 150).create(unsorted);
        assertEquals(NORMAL_READS, context.getGenotype("NORMAL").getDP());
        assertEquals(NORMAL_REF_COUNT, context.getGenotype("NORMAL").getAD()[0]);
        assertEquals(0, context.getGenotype("NORMAL").getAD()[1]);

        assertEquals(TUMOR_READS, context.getGenotype("TUMOR").getDP());
        assertEquals(TUMOR_REF_COUNT, context.getGenotype("TUMOR").getAD()[0]);
        assertEquals(19, context.getGenotype("TUMOR").getAD()[1]);
    }

    @Test
    public void testHeterozygousLikelihood() {
        assertEquals(0.1875, HotspotEvidenceVCF.heterozygousLikelihood(5), 0.0001);
    }

    private static HotspotEvidence create(@NotNull final String ref, @NotNull final String alt, int normalAltCount, int tumorAltCount,
            int qualityScore) {
        return HotspotEvidenceTest.create(CHROM, POS, ref, alt)
                .qualityScore(qualityScore)
                .tumorAltCount(tumorAltCount)
                .normalAltCount(normalAltCount)
                .normalRefCount(NORMAL_REF_COUNT)
                .normalReads(NORMAL_READS)
                .tumorRefCount(TUMOR_REF_COUNT)
                .tumorReads(TUMOR_READS)
                .build();
    }

}
