package com.hartwig.hmftools.linx.fusion;

import static com.hartwig.hmftools.common.ensemblcache.GeneTestUtils.addGeneData;
import static com.hartwig.hmftools.common.ensemblcache.GeneTestUtils.addTransExonData;
import static com.hartwig.hmftools.common.ensemblcache.GeneTestUtils.createEnsemblGeneData;
import static com.hartwig.hmftools.common.ensemblcache.GeneTestUtils.createGeneDataCache;
import static com.hartwig.hmftools.common.ensemblcache.GeneTestUtils.createTransExons;
import static com.hartwig.hmftools.common.ensemblcache.GeneTestUtils.generateExonStarts;
import static com.hartwig.hmftools.common.ensemblcache.GeneTestUtils.generateTransName;
import static com.hartwig.hmftools.common.fusion.FusionCommon.NEG_STRAND;
import static com.hartwig.hmftools.common.fusion.FusionCommon.POS_STRAND;
import static com.hartwig.hmftools.common.fusion.KnownFusionType.EXON_DEL_DUP;
import static com.hartwig.hmftools.common.fusion.KnownFusionType.IG_KNOWN_PAIR;
import static com.hartwig.hmftools.common.fusion.KnownFusionType.IG_PROMISCUOUS;
import static com.hartwig.hmftools.common.fusion.KnownFusionType.KNOWN_PAIR;
import static com.hartwig.hmftools.common.fusion.KnownFusionType.PROMISCUOUS_3;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.NEG_ORIENT;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.POS_ORIENT;
import static com.hartwig.hmftools.linx.fusion.FusionConstants.PRE_GENE_PROMOTOR_DISTANCE;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.CHR_1;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.CHR_2;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.GENE_NAME_1;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.GENE_NAME_2;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.TRANS_1;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.addTestGenes;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.addTestTranscripts;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createBnd;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createDel;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createSgl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache;
import com.hartwig.hmftools.common.ensemblcache.EnsemblGeneData;
import com.hartwig.hmftools.common.ensemblcache.TranscriptData;
import com.hartwig.hmftools.common.fusion.GeneAnnotation;
import com.hartwig.hmftools.common.fusion.KnownFusionData;
import com.hartwig.hmftools.linx.analysis.SampleAnalyser;
import com.hartwig.hmftools.linx.types.SglMapping;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.types.SvVarData;
import com.hartwig.hmftools.linx.utils.LinxTester;

import org.junit.Test;

public class SpecialFusionsTest
{
    @Test
    public void testSameGeneFusions()
    {
        LinxTester tester = new LinxTester();

        EnsemblDataCache geneTransCache = createGeneDataCache();

        tester.initialiseFusions(geneTransCache);

        addTestGenes(geneTransCache);
        addTestTranscripts(geneTransCache);

        // mark as 3' promiscuous
        tester.FusionAnalyser.getFusionFinder().getKnownFusionCache()
                .addData(new KnownFusionData(PROMISCUOUS_3, "", GENE_NAME_1, "", "", ""));

        List<GeneAnnotation> upGenes = Lists.newArrayList();
        int upPos = 1950;
        upGenes.addAll(geneTransCache.findGeneAnnotationsBySv(0, false, CHR_1, upPos, POS_ORIENT, PRE_GENE_PROMOTOR_DISTANCE));
        upGenes.get(0).setPositionalData(CHR_1, upPos, POS_ORIENT);

        // add downstream breakends
        List<GeneAnnotation> downGenes = Lists.newArrayList();
        int downPos = 1350;
        downGenes.addAll(geneTransCache.findGeneAnnotationsBySv(0, true, CHR_1, downPos, NEG_ORIENT, PRE_GENE_PROMOTOR_DISTANCE));
        downGenes.get(0).setPositionalData(CHR_1, downPos, NEG_ORIENT);

        FusionParameters params = new FusionParameters();
        params.RequirePhaseMatch = true;
        params.AllowExonSkipping = true;

        List<GeneFusion> fusions = tester.FusionAnalyser.getFusionFinder().findFusions(upGenes, downGenes, params, true);

        assertEquals(1, fusions.size());
        final GeneFusion fusion = fusions.get(0);

        assertEquals(upPos, fusion.upstreamTrans().gene().position());
        assertEquals(downPos, fusion.downstreamTrans().gene().position());
        assertEquals(0, fusion.getExonsSkipped(false));
        assertEquals(0, fusion.getExonsSkipped(false));
        assertTrue(!fusion.reportable());
    }

    @Test
    public void testExonDelDupFusion()
    {
        LinxTester tester = new LinxTester();

        EnsemblDataCache geneTransCache = createGeneDataCache();

        tester.initialiseFusions(geneTransCache);

        addTestGenes(geneTransCache);
        addTestTranscripts(geneTransCache);

        final String transName = generateTransName(TRANS_1);

        final String knownDelRegion = String.format("%s;%d;%d;%d;%d", transName, 2, 3, 5, 6);
        tester.FusionAnalyser.getFusionFinder().getKnownFusionCache().addData(
                new KnownFusionData(EXON_DEL_DUP, GENE_NAME_1, GENE_NAME_1, "", "", knownDelRegion));

        FusionParameters params = new FusionParameters();
        params.RequirePhaseMatch = true;
        params.AllowExonSkipping = true;

        // first DEL doesn't delete a known region even though it's phased
        List<GeneAnnotation> upGenes = Lists.newArrayList();
        int upPos = 1550;
        upGenes.addAll(geneTransCache.findGeneAnnotationsBySv(0, true, CHR_1, upPos, POS_ORIENT, PRE_GENE_PROMOTOR_DISTANCE));
        upGenes.get(0).setPositionalData(CHR_1, upPos, POS_ORIENT);

        // add downstream breakends
        List<GeneAnnotation> downGenes = Lists.newArrayList();

        int downPos = 2150;
        downGenes.addAll(geneTransCache.findGeneAnnotationsBySv(0, false, CHR_1, downPos, NEG_ORIENT, PRE_GENE_PROMOTOR_DISTANCE));
        downGenes.get(0).setPositionalData(CHR_1, downPos, NEG_ORIENT);

        List<GeneFusion> fusions = tester.FusionAnalyser.getFusionFinder().findFusions(upGenes, downGenes, params, true);

        // second one does
        upGenes.clear();
        downGenes.clear();
        upPos = 1350;
        upGenes.addAll(geneTransCache.findGeneAnnotationsBySv(1, true, CHR_1, upPos, POS_ORIENT, PRE_GENE_PROMOTOR_DISTANCE));
        upGenes.get(0).setPositionalData(CHR_1, upPos, POS_ORIENT);

        downPos = 1950;
        downGenes.addAll(geneTransCache.findGeneAnnotationsBySv(1, false, CHR_1, downPos, NEG_ORIENT, PRE_GENE_PROMOTOR_DISTANCE));
        downGenes.get(0).setPositionalData(CHR_1, downPos, NEG_ORIENT);

        fusions.addAll(tester.FusionAnalyser.getFusionFinder().findFusions(upGenes, downGenes, params, true));

        assertEquals(1, fusions.size());
        final GeneFusion fusion = fusions.stream().filter(x -> x.knownType() == EXON_DEL_DUP).findFirst().orElse(null);
        assertTrue(fusion != null);

        // the selected fusion is the longest for coding bases and without any exon skipping
        assertEquals(upPos, fusion.upstreamTrans().gene().position());
        assertEquals(downPos, fusion.downstreamTrans().gene().position());
        assertEquals(0, fusion.getExonsSkipped(false));
        assertEquals(0, fusion.getExonsSkipped(false));
        assertTrue(fusion.reportable());
    }

    @Test
    public void testIgRegionFusion()
    {
        LinxTester tester = new LinxTester();

        EnsemblDataCache geneTransCache = createGeneDataCache();

        tester.initialiseFusions(geneTransCache);

        String geneName = "IGH";
        String geneId1 = "ENSG0001";
        String chromosome = "1";

        String geneName2 = "GENE2";
        String geneId2 = "ENSG0002";

        String geneName3 = "GENE3";
        String geneId3 = "ENSG0003";

        byte strand = POS_STRAND;

        List<EnsemblGeneData> geneList = Lists.newArrayList();
        geneList.add(createEnsemblGeneData(geneId1, geneName, chromosome, strand, 100, 1500));
        geneList.add(createEnsemblGeneData(geneId2, geneName2, chromosome, strand, 10000, 11500));
        geneList.add(createEnsemblGeneData(geneId3, geneName3, chromosome, strand, 20000, 21500));

        addGeneData(geneTransCache, chromosome, geneList);

        List<TranscriptData> transDataList = Lists.newArrayList();

        int transId = 1;

        int[] exonStarts = generateExonStarts(100, 7, 100, 100);
        int[] exonPhases = new int[]{-1, -1, 1, 1, 1, 1, -1};

        TranscriptData transData = createTransExons(geneId1, transId++, strand, exonStarts, exonPhases, 100, true);
        transDataList.add(transData);

        addTransExonData(geneTransCache, geneId1, transDataList);

        exonStarts = generateExonStarts(10000, exonPhases.length, 100, 100);
        transData = createTransExons(geneId2, transId++, strand, exonStarts, exonPhases, 100, true);
        transDataList = Lists.newArrayList(transData);

        addTransExonData(geneTransCache, geneId2, transDataList);

        exonStarts = generateExonStarts(20000, exonPhases.length, 100, 100);
        transData = createTransExons(geneId3, transId++, strand, exonStarts, exonPhases, 100, true);
        transDataList = Lists.newArrayList(transData);

        addTransExonData(geneTransCache, geneId3, transDataList);

        final String igRegion = String.format("%d;%s;%d;%d;%d", NEG_STRAND, chromosome, 50, 2000, 0);

        tester.FusionAnalyser.getFusionFinder().getKnownFusionCache().addData(
                new KnownFusionData(IG_KNOWN_PAIR, geneName, geneName2, "", "", igRegion));

        tester.FusionAnalyser.getFusionFinder().getKnownFusionCache().addData(
                new KnownFusionData(IG_PROMISCUOUS, geneName, "", "", "", igRegion));

        FusionParameters params = new FusionParameters();
        params.RequirePhaseMatch = true;
        params.AllowExonSkipping = true;

        // a DEL linking the 2 regions
        List<GeneAnnotation> upGenes = Lists.newArrayList();
        upGenes.addAll(geneTransCache.findGeneAnnotationsBySv(0, true, chromosome, 200, NEG_ORIENT, 1000));
        upGenes.get(0).setPositionalData(chromosome, 200, NEG_ORIENT);

        // add downstream breakends
        List<GeneAnnotation> downGenes = Lists.newArrayList();

        downGenes.addAll(geneTransCache.findGeneAnnotationsBySv(0, false, chromosome, 9500, NEG_ORIENT, 1000));
        downGenes.get(0).setPositionalData(chromosome, 9500, NEG_ORIENT);

        List<GeneFusion> fusions = tester.FusionAnalyser.getFusionFinder().findFusions(upGenes, downGenes, params, true);

        upGenes.clear();
        upGenes.addAll(geneTransCache.findGeneAnnotationsBySv(1, true, chromosome, 200, NEG_ORIENT, 1000));
        upGenes.get(0).setPositionalData(chromosome, 500, NEG_ORIENT);

        downGenes.clear();
        downGenes.addAll(geneTransCache.findGeneAnnotationsBySv(1, false, chromosome, 19500, NEG_ORIENT, 1000));
        downGenes.get(0).setPositionalData(chromosome, 20100, NEG_ORIENT);

        fusions.addAll(tester.FusionAnalyser.getFusionFinder().findFusions(upGenes, downGenes, params, true));

        assertEquals(2, fusions.size());
        GeneFusion fusion = fusions.stream().filter(x -> x.knownType() == IG_KNOWN_PAIR).findFirst().orElse(null);
        assertTrue(fusion != null);

        // the selected fusion is the longest for coding bases and without any exon skipping
        assertEquals(200, fusion.upstreamTrans().gene().position());
        assertEquals(9500, fusion.downstreamTrans().gene().position());
        assertTrue(fusion.reportable());

        fusion = fusions.stream().filter(x -> x.knownType() == IG_PROMISCUOUS).findFirst().orElse(null);
        assertTrue(fusion != null);

        // the selected fusion is the longest for coding bases and without any exon skipping
        assertEquals(500, fusion.upstreamTrans().gene().position());
        assertEquals(20100, fusion.downstreamTrans().gene().position());
        assertTrue(!fusion.reportable());
    }

    @Test
    public void testSingleBreakendFusions()
    {
        LinxTester tester = new LinxTester();

        EnsemblDataCache geneTransCache = createGeneDataCache();

        tester.initialiseFusions(geneTransCache);

        addTestGenes(geneTransCache);
        addTestTranscripts(geneTransCache);

        PRE_GENE_PROMOTOR_DISTANCE = 200;

        // set known fusion gene for the SGL breakend
        tester.FusionAnalyser.getFusionFinder().getKnownFusionCache()
                .addData(new KnownFusionData(KNOWN_PAIR, GENE_NAME_1, GENE_NAME_2, "", "", ""));

        // test 1: a SGL by itself
        int varId = 1;

        SvVarData sgl1 = createSgl(varId++, CHR_1, 1150, POS_ORIENT);

        sgl1.getSglMappings().add(new SglMapping(CHR_1, 10150, NEG_ORIENT, "", 1));

        tester.AllVariants.add(sgl1);

        tester.preClusteringInit();
        tester.Analyser.clusterAndAnalyse();

        SampleAnalyser.setSvGeneData(tester.AllVariants, geneTransCache, false, false);
        tester.FusionAnalyser.annotateTranscripts(tester.AllVariants, false);

        tester.FusionAnalyser.run(tester.SampleId, tester.AllVariants, null,
                tester.getClusters(), tester.Analyser.getState().getChrBreakendMap());

        assertEquals(1, tester.FusionAnalyser.getFusions().size());

        GeneFusion fusion = tester.FusionAnalyser.getFusions().get(0);
        assertEquals(sgl1.id(), fusion.upstreamTrans().gene().id());
        assertEquals(sgl1.id(), fusion.downstreamTrans().gene().id());

        tester.clearClustersAndSVs();

        // test 2: test a chain with a SGL at the start
        sgl1 = createSgl(varId++, CHR_1, 10150, NEG_ORIENT);

        sgl1.getSglMappings().add(new SglMapping(CHR_1, 1150, POS_ORIENT, "", 1));


        SvVarData var1 = createDel(varId++, CHR_1, 14000,15000);

        tester.AllVariants.add(sgl1);
        tester.AllVariants.add(var1);

        tester.preClusteringInit();
        tester.Analyser.clusterAndAnalyse();

        assertEquals(1, tester.Analyser.getClusters().size());
        SvCluster cluster = tester.Analyser.getClusters().get(0);

        assertEquals(1, cluster.getChains().size());

        SampleAnalyser.setSvGeneData(tester.AllVariants, geneTransCache, true, false);
        tester.FusionAnalyser.annotateTranscripts(tester.AllVariants, true);

        tester.FusionAnalyser.run(tester.SampleId, tester.AllVariants, null,
                tester.getClusters(), tester.Analyser.getState().getChrBreakendMap());

        assertEquals(1, tester.FusionAnalyser.getFusions().size());

        fusion = tester.FusionAnalyser.getFusions().get(0);
        assertEquals(sgl1.id(), fusion.upstreamTrans().gene().id());
        assertEquals(sgl1.id(), fusion.downstreamTrans().gene().id());

        // test 3: a chain with the SGL breaking going outside the gene into a shard
        tester.clearClustersAndSVs();

        sgl1 = createSgl(varId++, CHR_2, 100, NEG_ORIENT);

        sgl1.getSglMappings().add(new SglMapping(CHR_1, 1150, POS_ORIENT, "", 1));

        var1 = createBnd(varId++, CHR_1, 10150, NEG_ORIENT, CHR_2, 200, POS_ORIENT);
        SvVarData var2 = createBnd(varId++, CHR_1, 500, POS_ORIENT, CHR_2, 500, NEG_ORIENT); // to assist clustering only

        tester.AllVariants.add(sgl1);
        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);

        tester.preClusteringInit();
        tester.Analyser.clusterAndAnalyse();

        assertEquals(1, tester.Analyser.getClusters().size());
        cluster = tester.Analyser.getClusters().get(0);

        assertEquals(1, cluster.getChains().size());

        SampleAnalyser.setSvGeneData(tester.AllVariants, geneTransCache, true, false);
        tester.FusionAnalyser.annotateTranscripts(tester.AllVariants, true);

        tester.FusionAnalyser.run(tester.SampleId, tester.AllVariants, null,
                tester.getClusters(), tester.Analyser.getState().getChrBreakendMap());

        assertEquals(1, tester.FusionAnalyser.getFusions().size());

        fusion = tester.FusionAnalyser.getFusions().get(0);
        assertEquals(sgl1.id(), fusion.upstreamTrans().gene().id());
        assertEquals(var1.id(), fusion.downstreamTrans().gene().id());

        // test 4: 2 SGLs at the start and end of a fusion
        tester.clearClustersAndSVs();

        sgl1 = createSgl(varId++, CHR_2, 100, NEG_ORIENT);
        sgl1.getSglMappings().add(new SglMapping(CHR_1, 1150, POS_ORIENT, "", 1));

        var1 = createBnd(varId++, CHR_2, 200, POS_ORIENT, "3", 200, POS_ORIENT);

        SvVarData sgl2 = createSgl(varId++, "3", 100, NEG_ORIENT);
        sgl2.getSglMappings().add(new SglMapping(CHR_1, 10150, NEG_ORIENT, "", 1));

        tester.AllVariants.add(sgl1);
        tester.AllVariants.add(sgl2);
        tester.AllVariants.add(var1);

        tester.preClusteringInit();
        tester.Analyser.clusterAndAnalyse();

        assertEquals(1, tester.Analyser.getClusters().size());
        cluster = tester.Analyser.getClusters().get(0);

        assertEquals(1, cluster.getChains().size());
        assertEquals(3, cluster.getChains().get(0).getSvCount());

        SampleAnalyser.setSvGeneData(tester.AllVariants, geneTransCache, true, false);
        tester.FusionAnalyser.annotateTranscripts(tester.AllVariants, true);

        tester.FusionAnalyser.run(tester.SampleId, tester.AllVariants, null,
                tester.getClusters(), tester.Analyser.getState().getChrBreakendMap());

        assertEquals(1, tester.FusionAnalyser.getFusions().size());

        fusion = tester.FusionAnalyser.getFusions().get(0);
        assertEquals(sgl1.id(), fusion.upstreamTrans().gene().id());
        assertEquals(sgl2.id(), fusion.downstreamTrans().gene().id());


    }


}
