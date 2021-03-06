package com.hartwig.hmftools.isofox;

import static com.hartwig.hmftools.common.ensemblcache.GeneTestUtils.createGeneDataCache;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.BND;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DEL;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DUP;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INV;
import static com.hartwig.hmftools.isofox.TestUtils.CHR_1;
import static com.hartwig.hmftools.isofox.TestUtils.CHR_2;
import static com.hartwig.hmftools.isofox.TestUtils.GENE_ID_1;
import static com.hartwig.hmftools.isofox.TestUtils.GENE_ID_2;
import static com.hartwig.hmftools.isofox.TestUtils.GENE_ID_3;
import static com.hartwig.hmftools.isofox.TestUtils.GENE_ID_5;
import static com.hartwig.hmftools.isofox.TestUtils.createGeneCollection;
import static com.hartwig.hmftools.isofox.TestUtils.addTestGenes;
import static com.hartwig.hmftools.isofox.TestUtils.addTestTranscripts;
import static com.hartwig.hmftools.isofox.TestUtils.createCigar;
import static com.hartwig.hmftools.isofox.TestUtils.createMappedRead;
import static com.hartwig.hmftools.isofox.TestUtils.createSupplementaryReadPair;
import static com.hartwig.hmftools.isofox.fusion.FusionFragmentType.MATCHED_JUNCTION;
import static com.hartwig.hmftools.isofox.fusion.FusionFragmentType.UNKNOWN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static htsjdk.samtools.SAMFlag.FIRST_OF_PAIR;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache;
import com.hartwig.hmftools.isofox.common.GeneCollection;
import com.hartwig.hmftools.isofox.common.ReadRecord;
import com.hartwig.hmftools.isofox.fusion.FusionFragment;

import org.junit.Test;

public class FusionFragmentsTest
{
    @Test
    public void testDelFragments()
    {
        final EnsemblDataCache geneTransCache = createGeneDataCache();

        addTestGenes(geneTransCache);
        addTestTranscripts(geneTransCache);

        int gcId = 0;

        final GeneCollection gc1 = createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_1)));
        final GeneCollection gc2 = createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_2)));

        // a simple DEL
        int readId = 0;
        ReadRecord read1 = createMappedRead(readId, gc1, 1050, 1089, createCigar(0, 40, 0));
        read1.setFlag(FIRST_OF_PAIR, true);

        ReadRecord[] readPair = createSupplementaryReadPair(readId, gc1, gc2, 1081, 1100, 10200, 10219,
                createCigar(0, 20, 20), createCigar(20, 20, 0), false);

        readPair[1].setStrand(true, false);

        List<ReadRecord> reads = Lists.newArrayList(read1, readPair[0], readPair[1]);
        FusionFragment fragment = FusionFragment.from(reads);

        assertEquals(MATCHED_JUNCTION, fragment.type());
        assertEquals(CHR_1, fragment.chromosomes()[SE_START]);
        assertEquals(CHR_1, fragment.chromosomes()[SE_END]);
        assertEquals(gc1.id(), fragment.geneCollections()[SE_START]);
        assertEquals(gc2.id(), fragment.geneCollections()[SE_END]);
        assertEquals(1100, fragment.junctionPositions()[SE_START]);
        assertEquals(10200, fragment.junctionPositions()[SE_END]);
        assertEquals(1, fragment.orientations()[SE_START]);
        assertEquals(-1, fragment.orientations()[SE_END]);
        assertEquals(1, fragment.junctionOrientations()[SE_START]);
        assertEquals(-1, fragment.junctionOrientations()[SE_END]);
        assertEquals(DEL, fragment.getImpliedSvType());

        final List<String>[] spliceGeneIds = new List[] { Lists.newArrayList(), Lists.newArrayList() };

        for(int se = SE_START; se <= SE_END; ++se)
        {
            spliceGeneIds[se].addAll(fragment.getGeneIds(se));
        }

        assertTrue(fragment.isSpliced());
        assertEquals(1, spliceGeneIds[SE_START].size());
        assertEquals(GENE_ID_1, spliceGeneIds[SE_START].get(0));
        assertEquals(1, spliceGeneIds[SE_END].size());
        assertEquals(GENE_ID_2, spliceGeneIds[SE_END].get(0));

        // unspliced DEL - from an SV at 1150 to 10150
        read1 = createMappedRead(++readId, gc1, 1110, 1149, createCigar(0, 40, 0));
        read1.setFlag(FIRST_OF_PAIR, true);

        readPair = createSupplementaryReadPair(readId, gc1, gc2, 1131, 1150, 10150, 10169,
                createCigar(0, 20, 20), createCigar(20, 20, 0), false);

        readPair[1].setStrand(true, false);

        reads = Lists.newArrayList(read1, readPair[0], readPair[1]);

        fragment = FusionFragment.from(reads);

        assertEquals(MATCHED_JUNCTION, fragment.type());
        assertEquals(CHR_1, fragment.chromosomes()[SE_START]);
        assertEquals(CHR_1, fragment.chromosomes()[SE_END]);
        assertEquals(gc1.id(), fragment.geneCollections()[SE_START]);
        assertEquals(gc2.id(), fragment.geneCollections()[SE_END]);
        assertEquals(1150, fragment.junctionPositions()[SE_START]);
        assertEquals(10150, fragment.junctionPositions()[SE_END]);
        assertEquals(1, fragment.junctionOrientations()[SE_START]);
        assertEquals(-1, fragment.junctionOrientations()[SE_END]);
        assertEquals(DEL, fragment.getImpliedSvType());

        for(int se = SE_START; se <= SE_END; ++se)
        {
            spliceGeneIds[se].clear();
            spliceGeneIds[se].addAll(fragment.getGeneIds(se));
        }

        assertTrue(fragment.isUnspliced());
        assertEquals(1, spliceGeneIds[SE_START].size());
        assertEquals(GENE_ID_1, spliceGeneIds[SE_START].get(0));
        assertEquals(1, spliceGeneIds[SE_END].size());
        assertEquals(GENE_ID_2, spliceGeneIds[SE_END].get(0));

        // short DEL without supplementary data
        read1 = createMappedRead(++readId, gc1, 1081, 1100, createCigar(0, 20, 20));
        ReadRecord read2 = createMappedRead(readId, gc2, 10200, 10219, createCigar(20, 20, 0));

        read2.setStrand(true, false);

        reads = Lists.newArrayList(read1, read2);
        fragment = FusionFragment.from(reads);

        assertEquals(MATCHED_JUNCTION, fragment.type());
        assertEquals(CHR_1, fragment.chromosomes()[SE_START]);
        assertEquals(CHR_1, fragment.chromosomes()[SE_END]);
        assertEquals(gc1.id(), fragment.geneCollections()[SE_START]);
        assertEquals(gc2.id(), fragment.geneCollections()[SE_END]);
        assertEquals(1, fragment.orientations()[SE_START]);
        assertEquals(-1, fragment.orientations()[SE_END]);
        assertEquals(1100, fragment.junctionPositions()[SE_START]);
        assertEquals(10200, fragment.junctionPositions()[SE_END]);
        assertEquals(1, fragment.junctionOrientations()[SE_START]);
        assertEquals(-1, fragment.junctionOrientations()[SE_END]);
        assertEquals(DEL, fragment.getImpliedSvType());

        for(int se = SE_START; se <= SE_END; ++se)
        {
            spliceGeneIds[se].clear();
            spliceGeneIds[se].addAll(fragment.getGeneIds(se));
        }

        assertTrue(fragment.isSpliced());
        assertEquals(1, spliceGeneIds[SE_START].size());
        assertEquals(GENE_ID_1, spliceGeneIds[SE_START].get(0));
        assertEquals(1, spliceGeneIds[SE_END].size());
        assertEquals(GENE_ID_2, spliceGeneIds[SE_END].get(0));

        // DEL spanning 2 gene collections but without a supplementary read or soft-clipping, but with the N-split in the reads representing the junction
        read1 = createMappedRead(readId, gc1, 1080, 10218, createCigar(0, 21, 9099, 19, 0));
        read2 = createMappedRead(++readId, gc1, 1081, 10219, createCigar(0, 20, 9099, 20, 0));
        read1.setFlag(FIRST_OF_PAIR, true);
        read2.setStrand(true, false);

        // override the gene collection Ids
        read1.setGeneCollection(SE_START, gc1.id(), true);
        read1.setGeneCollection(SE_END, gc2.id(), true);
        read2.setGeneCollection(SE_START, gc1.id(), true);
        read2.setGeneCollection(SE_END, gc2.id(), true);

        reads = Lists.newArrayList(read1, read2);

        fragment = FusionFragment.from(reads);

        assertEquals(MATCHED_JUNCTION, fragment.type());
        assertEquals(CHR_1, fragment.chromosomes()[SE_START]);
        assertEquals(CHR_1, fragment.chromosomes()[SE_END]);
        assertEquals(gc1.id(), fragment.geneCollections()[SE_START]);
        assertEquals(gc2.id(), fragment.geneCollections()[SE_END]);

        // assertEquals(1, fragment.orientations()[SE_START]); // not sure how these are set for overlapping reads
        // assertEquals(-1, fragment.orientations()[SE_END]);
        assertEquals(1100, fragment.junctionPositions()[SE_START]);
        assertEquals(10200, fragment.junctionPositions()[SE_END]);
        assertEquals(1, fragment.junctionOrientations()[SE_START]);
        assertEquals(-1, fragment.junctionOrientations()[SE_END]);
        assertEquals(DEL, fragment.getImpliedSvType());
    }

    @Test
    public void testDupFragments()
    {
        final EnsemblDataCache geneTransCache = createGeneDataCache();

        addTestGenes(geneTransCache);
        addTestTranscripts(geneTransCache);

        int gcId = 0;

        final GeneCollection gc1 =
                createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_1)));
        final GeneCollection gc2 =
                createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_2)));

        int readId = 0;

        // DUP between genes
        ReadRecord[] readPair = createSupplementaryReadPair(readId, gc2, gc1, 10281, 10300, 1200, 1219,
                createCigar(0, 20, 20), createCigar(20, 20, 0), true);

        ReadRecord read1 = createMappedRead(++readId, gc2, 10220, 10259, createCigar(0, 40, 0));

        readPair[0].setStrand(true, false);
        readPair[1].setStrand(true, false);

        List<ReadRecord> reads = Lists.newArrayList(read1, readPair[0], readPair[1]);
        FusionFragment fragment = FusionFragment.from(reads);

        assertEquals(MATCHED_JUNCTION, fragment.type());
        assertEquals(CHR_1, fragment.chromosomes()[SE_START]);
        assertEquals(CHR_1, fragment.chromosomes()[SE_END]);
        assertEquals(1200, fragment.junctionPositions()[SE_START]);
        assertEquals(10300, fragment.junctionPositions()[SE_END]);
        assertEquals(-1, fragment.junctionOrientations()[SE_START]);
        assertEquals(1, fragment.junctionOrientations()[SE_END]);
        assertEquals(DUP, fragment.getImpliedSvType());

        // and again but this time with the non-supp read on the first gene and first-of-pair switched around
        readPair = createSupplementaryReadPair(readId, gc1, gc2,  1200, 1219, 10281, 10300,
                createCigar(20, 20, 0), createCigar(00, 20, 20), false);

        read1 = createMappedRead(++readId, gc1, 1210, 1249, createCigar(0, 40, 0));
        read1.setStrand(true, false);
        read1.setFlag(FIRST_OF_PAIR, true);

        reads = Lists.newArrayList(read1, readPair[0], readPair[1]);
        fragment = FusionFragment.from(reads);

        assertEquals(MATCHED_JUNCTION, fragment.type());
        assertEquals(CHR_1, fragment.chromosomes()[SE_START]);
        assertEquals(CHR_1, fragment.chromosomes()[SE_END]);
        assertEquals(1200, fragment.junctionPositions()[SE_START]);
        assertEquals(10300, fragment.junctionPositions()[SE_END]);
        assertEquals(-1, fragment.junctionOrientations()[SE_START]);
        assertEquals(1, fragment.junctionOrientations()[SE_END]);
        assertEquals(DUP, fragment.getImpliedSvType());

        final List<String>[] spliceGeneIds = new List[] { Lists.newArrayList(), Lists.newArrayList() };

        for (int se = SE_START; se <= SE_END; ++se)
        {
            spliceGeneIds[se].clear();
            spliceGeneIds[se].addAll(fragment.getGeneIds(se));
        }

        assertEquals(1, spliceGeneIds[SE_START].size());
        assertEquals(GENE_ID_1, spliceGeneIds[SE_START].get(0));
        assertEquals(1, spliceGeneIds[SE_END].size());
        assertEquals(GENE_ID_2, spliceGeneIds[SE_END].get(0));
    }

    @Test
    public void testInvFragment()
    {
        final EnsemblDataCache geneTransCache = createGeneDataCache();

        addTestGenes(geneTransCache);
        addTestTranscripts(geneTransCache);

        int gcId = 0;

        final GeneCollection gc1 =
                createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_1)));

        // INV being +1/+1
        final GeneCollection gc3 =
                createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_3)));

        int readId = 0;

        String readBases = "AGAGAGAGAGAGAGAGAGAG";
        readBases += readBases;
        ReadRecord read1 = createMappedRead(++readId, gc1, 1081, 1100, createCigar(0, 20, 20), readBases);
        ReadRecord read2 = createMappedRead(readId, gc3, 20281, 20300, createCigar(0, 20, 20), readBases);
        ReadRecord read3 = createMappedRead(readId, gc3, 20220, 20259, createCigar(20, 20, 0));

        read1.setFlag(FIRST_OF_PAIR, true);
        read2.setFlag(FIRST_OF_PAIR, true);
        read1.setSuppAlignment("supp");
        read2.setSuppAlignment("supp");
        read1.setStrand(false, false);
        read1.setStrand(false, false);
        read3.setStrand(false, false);

        List<ReadRecord> reads = Lists.newArrayList(read1, read2, read3);
        FusionFragment fragment = FusionFragment.from(reads);

        assertEquals(MATCHED_JUNCTION, fragment.type());
        assertEquals(CHR_1, fragment.chromosomes()[SE_START]);
        assertEquals(CHR_1, fragment.chromosomes()[SE_END]);
        assertEquals(1100, fragment.junctionPositions()[SE_START]);
        assertEquals(20300, fragment.junctionPositions()[SE_END]);
        assertEquals(1, fragment.junctionOrientations()[SE_START]);
        assertEquals(1, fragment.junctionOrientations()[SE_END]);
        assertEquals(INV, fragment.getImpliedSvType());

        final List<String>[] spliceGeneIds = new List[] { Lists.newArrayList(), Lists.newArrayList() };

        for (int se = SE_START; se <= SE_END; ++se)
        {
            spliceGeneIds[se].clear();
            spliceGeneIds[se].addAll(fragment.getGeneIds(se));
        }

        assertEquals(1, spliceGeneIds[SE_START].size());
        assertEquals(GENE_ID_1, spliceGeneIds[SE_START].get(0));
        assertEquals(1, spliceGeneIds[SE_END].size());
        assertEquals(GENE_ID_3, spliceGeneIds[SE_END].get(0));


        // INV being -1/-1
        ReadRecord[] readPair = createSupplementaryReadPair(readId, gc1, gc3, 1100, 1119, 20100, 20119,
                createCigar(20, 20, 0), createCigar(20, 20, 0), false);

        read3 = createMappedRead(readId, gc3, 20110, 20149, createCigar(0, 40, 0));
        read3.setFlag(FIRST_OF_PAIR, true);

        readPair[0].setStrand(true, true);
        readPair[1].setStrand(true, true);
        read3.setStrand(true, true);

        reads = Lists.newArrayList(readPair[0], readPair[1], read3);
        fragment = FusionFragment.from(reads);

        assertEquals(MATCHED_JUNCTION, fragment.type());
        assertEquals(CHR_1, fragment.chromosomes()[SE_START]);
        assertEquals(CHR_1, fragment.chromosomes()[SE_END]);
        assertEquals(gc1.id(), fragment.geneCollections()[SE_START]);
        assertEquals(gc3.id(), fragment.geneCollections()[SE_END]);
        assertEquals(1100, fragment.junctionPositions()[SE_START]);
        assertEquals(20100, fragment.junctionPositions()[SE_END]);
        assertEquals(-1, fragment.junctionOrientations()[SE_START]);
        assertEquals(-1, fragment.junctionOrientations()[SE_END]);
        assertEquals(INV, fragment.getImpliedSvType());
    }

    @Test
    public void testBndFragment()
    {
        final EnsemblDataCache geneTransCache = createGeneDataCache();

        addTestGenes(geneTransCache);
        addTestTranscripts(geneTransCache);

        int gcId = 0;

        final GeneCollection gc3 = createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_3)));
        final GeneCollection gc5 = createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_5)));

        int readId = 0;

        // BND being -1/-1
        ReadRecord[] readPair = createSupplementaryReadPair(readId, gc5, gc3, 10200, 10219, 20281, 20300,
                createCigar(20, 20, 0), createCigar(0, 20, 20), true);

        ReadRecord read3 = createMappedRead(readId, gc3, 20220, 20259, createCigar(0, 40, 0));
        readPair[0].setStrand(true, false);
        readPair[1].setStrand(false, true);
        read3.setStrand(false, true);

        List<ReadRecord> reads = Lists.newArrayList(readPair[0], readPair[1], read3);
        FusionFragment fragment = FusionFragment.from(reads);

        assertEquals(MATCHED_JUNCTION, fragment.type());
        assertEquals(CHR_1, fragment.chromosomes()[SE_START]);
        assertEquals(CHR_2, fragment.chromosomes()[SE_END]);
        assertEquals(20300, fragment.junctionPositions()[SE_START]);
        assertEquals(10200, fragment.junctionPositions()[SE_END]);
        assertEquals(1, fragment.junctionOrientations()[SE_START]);
        assertEquals(-1, fragment.junctionOrientations()[SE_END]);
        assertEquals(BND, fragment.getImpliedSvType());

        final List<String>[] spliceGeneIds = new List[] { Lists.newArrayList(), Lists.newArrayList() };

        for(int se = SE_START; se <= SE_END; ++se)
        {
            spliceGeneIds[se].clear();
            spliceGeneIds[se].addAll(fragment.getGeneIds(se));
        }

        assertEquals(1, spliceGeneIds[SE_START].size());
        assertEquals(GENE_ID_3, spliceGeneIds[SE_START].get(0));
        assertEquals(1, spliceGeneIds[SE_END].size());
        assertEquals(GENE_ID_5, spliceGeneIds[SE_END].get(0));
    }

    @Test
    public void testLocalSplitReads()
    {
        // split reads at know junctions but too short to have supplementary data
        final EnsemblDataCache geneTransCache = createGeneDataCache();

        addTestGenes(geneTransCache);
        addTestTranscripts(geneTransCache);

        int gcId = 0;

        final GeneCollection gc1 = createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_1)));
        final GeneCollection gc2 = createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_2)));

        // a simple DEL
        int readId = 0;
        ReadRecord read1 = createMappedRead(readId, gc1, 1081, 1100, createCigar(0, 20, 20));
        ReadRecord read2 = createMappedRead(readId, gc2, 10200, 10219, createCigar(20, 20, 0));

        read2.setStrand(true, false);

        List<ReadRecord> reads = Lists.newArrayList(read1, read2);
        FusionFragment fragment = FusionFragment.from(reads);

        assertEquals(MATCHED_JUNCTION, fragment.type());
        assertEquals(CHR_1, fragment.chromosomes()[SE_START]);
        assertEquals(CHR_1, fragment.chromosomes()[SE_END]);
        assertEquals(1, fragment.orientations()[SE_START]);
        assertEquals(-1, fragment.orientations()[SE_END]);
        assertEquals(1100, fragment.junctionPositions()[SE_START]);
        assertEquals(10200, fragment.junctionPositions()[SE_END]);
        assertEquals(1, fragment.junctionOrientations()[SE_START]);
        assertEquals(-1, fragment.junctionOrientations()[SE_END]);
        assertEquals(DEL, fragment.getImpliedSvType());

        final List<String>[] spliceGeneIds = new List[] { Lists.newArrayList(), Lists.newArrayList() };

        for(int se = SE_START; se <= SE_END; ++se)
        {
            spliceGeneIds[se].addAll(fragment.getGeneIds(se));
        }

        assertTrue(fragment.isSpliced());
        assertEquals(1, spliceGeneIds[SE_START].size());
        assertEquals(GENE_ID_1, spliceGeneIds[SE_START].get(0));
        assertEquals(1, spliceGeneIds[SE_END].size());
        assertEquals(GENE_ID_2, spliceGeneIds[SE_END].get(0));

        // a fragment with the split in the second gene
        final GeneCollection gc3 = createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_3)));

        read1 = createMappedRead(++readId, gc1, 1081, 1100, createCigar(0, 20, 20));
        read2 = createMappedRead(readId, gc2, 10091, 20219, createCigar(10, 20, 10099, 10, 0));
        read2.setGeneCollection(SE_START, gc2.id(), true);
        read2.setGeneCollection(SE_END, gc3.id(), true);

        read2.setStrand(true, false);

        reads = Lists.newArrayList(read1, read2);
        fragment = FusionFragment.from(reads);

//        assertEquals(MATCHED_JUNCTION, fragment.type());
//        assertEquals(CHR_1, fragment.chromosomes()[SE_START]);
//        assertEquals(CHR_1, fragment.chromosomes()[SE_END]);
//        assertEquals(1, fragment.orientations()[SE_START]);
//        assertEquals(-1, fragment.orientations()[SE_END]);
//        assertEquals(1100, fragment.junctionPositions()[SE_START]);
//        assertEquals(10200, fragment.junctionPositions()[SE_END]);
//        assertEquals(1, fragment.junctionOrientations()[SE_START]);
//        assertEquals(-1, fragment.junctionOrientations()[SE_END]);
//        assertEquals(DEL, fragment.getImpliedSvType());

    }
}
