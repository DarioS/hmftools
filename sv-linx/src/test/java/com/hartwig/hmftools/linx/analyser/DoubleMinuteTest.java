package com.hartwig.hmftools.linx.analyser;

import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.BND;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DEL;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DUP;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INF;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INV;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createTestSv;
import static com.hartwig.hmftools.linx.types.SvCluster.CLUSTER_ANNOT_BFB;
import static com.hartwig.hmftools.linx.types.SvCluster.CLUSTER_ANNOT_DM;
import static com.hartwig.hmftools.linx.types.SvVarData.NONE_SEGMENT_INFERRED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.linx.chaining.SvChain;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.types.SvVarData;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Ignore;
import org.junit.Test;

import com.hartwig.hmftools.linx.utils.LinxTester;

public class DoubleMinuteTest
{
    @Test
    public void testSimpleDupDM()
    {
        LinxTester tester = new LinxTester();

        // need to put another SV before it to set the background copy number for this chromatid
        final SvVarData var1 = createTestSv(1,"1","1",500,600,1,-1, DEL,1);

        // first a simple DUP
        final SvVarData dup = createTestSv(2,"1","1",50000,55000,-1,1, DUP,10);
        dup.setJcnRecalcData(8, 12);

        tester.AllVariants.add(var1);
        tester.AllVariants.add(dup);

        tester.preClusteringInit();

        tester.Analyser.clusterAndAnalyse();

        assertEquals(2, tester.Analyser.getClusters().size());

        SvCluster cluster = tester.findClusterWithSVs(Lists.newArrayList(dup));
        assertTrue(cluster != null);
        assertEquals(1, cluster.getSvCount());
        assertTrue(cluster.getSVs().contains(dup));
        assertTrue(cluster.hasAnnotation(CLUSTER_ANNOT_DM));

        assertTrue(cluster.getChains().size() == 1);
        assertEquals(1, cluster.getChains().get(0).getLinkCount());
        assertTrue(cluster.getChains().get(0).isClosedLoop());
    }

    @Test
    public void testChainedDM()
    {
        // form a DM from 2 INVs
        LinxTester tester = new LinxTester();

        // tester.logVerbose(true);

        final SvVarData var1 = createTestSv(1,"1","1",1000,6000,-1,-1, INV,8);
        final SvVarData var2 = createTestSv(2,"1","1",3000,8000,1,1, INV,8);

        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);
        tester.preClusteringInit();

        tester.preClusteringInit();

        tester.Analyser.clusterAndAnalyse();

        SvCluster cluster = tester.findClusterWithSVs(Lists.newArrayList(var1, var2));
        assertTrue(cluster != null);
        assertTrue(cluster.hasAnnotation(CLUSTER_ANNOT_DM));
        assertTrue(cluster.getDoubleMinuteSVs().contains(var1));
        assertTrue(cluster.getDoubleMinuteSVs().contains(var2));

        assertEquals(1, cluster.getChains().size());
        SvChain chain = cluster.getChains().get(0);
        assertEquals(2, chain.getLinkCount());
        assertEquals(2, chain.getSvCount());
        assertTrue(chain.isClosedLoop());
    }

    @Ignore
    @Test
    public void testChainedDMWithMinors()
    {
        // form a DM from 3 chained SVs, with some other SVs in the cluster having a lower ploidy
        LinxTester tester = new LinxTester();

        // 1 s10100 -> 6 e10600-10500s -> 4 s11500-10100e -> 3 s10200-12000e -> 5 s12100-12200e -> 2 e12500-11200s -> 1 e11000

        final SvVarData var1 = createTestSv(1,"1","1",10100,11000,-1,-1, INV,8);
        final SvVarData var2 = createTestSv(2,"1","1",11200,12500,1,1, INV,8);
        final SvVarData var3 = createTestSv(3,"1","2",12000,10200,-1,1, BND,8);
        final SvVarData var4 = createTestSv(4,"1","2",11500,10100,1,-1, BND,8);
        final SvVarData var5 = createTestSv(5,"1","1",12100,12200,1,-1, DEL,2);
        final SvVarData var6 = createTestSv(6,"1","1",10500,10600,-1,1, DUP,2);

        // unrelated SVs at either end of the cluster
        final SvVarData other1 = createTestSv(7,"1","1",100,200,1,-1, DEL,1);
        final SvVarData other2 = createTestSv(8,"1","1",20000,20100,1,-1, DEL,1);
        final SvVarData other3 = createTestSv(9,"2","2",20100,20100,1,-1, DEL,1);

        tester.AllVariants.add(other1);

        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);
        tester.AllVariants.add(var3);
        tester.AllVariants.add(var4);
        tester.AllVariants.add(var5);
        tester.AllVariants.add(var6);

        tester.AllVariants.add(other2);
        tester.AllVariants.add(other3);

        tester.preClusteringInit();

        tester.Analyser.clusterAndAnalyse();

        SvCluster cluster = tester.findClusterWithSVs(Lists.newArrayList(var1, var2, var3, var4, var5, var6));
        assertTrue(cluster != null);
        assertTrue(cluster.hasAnnotation(CLUSTER_ANNOT_DM));
        assertTrue(cluster.getDoubleMinuteSVs().contains(var1));
        assertTrue(cluster.getDoubleMinuteSVs().contains(var2));
        assertTrue(cluster.getDoubleMinuteSVs().contains(var3));
        assertTrue(cluster.getDoubleMinuteSVs().contains(var4));

        assertTrue(cluster.getChains().size() == 1);
        SvChain chain = cluster.getChains().get(0);
        assertEquals(18, chain.getLinkCount());
        assertEquals(6, chain.getSvCount());
    }

    @Test
    public void testMultipleDMsInCluster()
    {
        LinxTester tester = new LinxTester();

        // Configurator.setRootLevel(Level.DEBUG);

        final SvVarData var1 = createTestSv(1,"1","1",1000,10000,-1,1, DUP,8);
        final SvVarData var2 = createTestSv(2,"1","1",2000,3000,1,-1, DEL,1);
        var1.setAssemblyData(true, "asmb12");
        var2.setAssemblyData(true, "asmb12");

        final SvVarData var3 = createTestSv(3,"1","1",12000,12500,-1,-1, INV,8);
        final SvVarData var4 = createTestSv(4,"1","1",20000,20500,1,1, INV,8);
        final SvVarData var5 = createTestSv(5,"1","1",13000,14000,1,-1, DEL,1);
        final SvVarData var6 = createTestSv(6,"1","2",15000,100,1,-1, BND,1);
        final SvVarData var7 = createTestSv(7,"1","2",16000,200,-1,1, BND,1);

        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);
        tester.AllVariants.add(var3);
        tester.AllVariants.add(var4);
        tester.AllVariants.add(var5);
        tester.AllVariants.add(var6);
        tester.AllVariants.add(var7);

        tester.preClusteringInit();

        tester.Analyser.clusterAndAnalyse();

        assertEquals(1, tester.Analyser.getClusters().size());
        final SvCluster cluster = tester.Analyser.getClusters().get(0);
        assertTrue(cluster != null);
        assertTrue(cluster.hasAnnotation(CLUSTER_ANNOT_DM));
        assertTrue(cluster.getDoubleMinuteSVs().contains(var1));
        assertTrue(cluster.getDoubleMinuteSVs().contains(var3));
        assertTrue(cluster.getDoubleMinuteSVs().contains(var4));

        assertEquals(2, cluster.getChains().stream().filter(x -> x.isDoubleMinute()).count());

//        SvChain chain = cluster.getChains().get(0);
//        assertEquals(18, chain.getLinkCount());
//        assertEquals(6, chain.getSvCount());
    }

    @Test
    @Ignore
    public void testInvalidDM()
    {
        // first a cluster which grows in JCN evenly
        LinxTester tester = new LinxTester();

        // Configurator.setRootLevel(Level.DEBUG);

        // a cluster with a set of 3 foldbacks which control the ploidies
        SvVarData var1 = createTestSv(1,"1","1",400,500,-1,-1, INV,1);
        SvVarData var2 = createTestSv(2,"1","1",5000,5200,1,1, INV,2);
        SvVarData var3 = createTestSv(3,"1","1",1000,1200,-1,-1, INV,2);
        SvVarData var4 = createTestSv(4,"1","1",2000,2200,1,1, INV,3);
        SvVarData var5 = createTestSv(5,"1","1",2100,2200,1,-1, DEL,8);
        SvVarData var6 = createTestSv(6,"1","1",100,6000,-1,1, DUP,8);

        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);
        tester.AllVariants.add(var3);
        tester.AllVariants.add(var4);
        tester.AllVariants.add(var5);
        tester.AllVariants.add(var6);

        tester.preClusteringInit();

        tester.Analyser.clusterAndAnalyse();

        assertEquals(1, tester.Analyser.getClusters().size());
        SvCluster cluster = tester.Analyser.getClusters().get(0);
        assertFalse(cluster.getAnnotations().contains(CLUSTER_ANNOT_DM));
        assertTrue(cluster.getAnnotations().contains(CLUSTER_ANNOT_BFB));

        tester.clearClustersAndSVs();

        // cannot be 2 close INFs
        var1 = createTestSv(1,"1","0",1000,-1,1,-1, INF,
                10, 10, 8, 8, 8, "", NONE_SEGMENT_INFERRED);

        var2 = createTestSv(2,"1","0",2000,-1,1,-1, INF,
                10, 10, 8, 8, 8, "", NONE_SEGMENT_INFERRED);

        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);

        tester.preClusteringInit();

        tester.Analyser.clusterAndAnalyse();

        assertEquals(1, tester.Analyser.getClusters().size());
        cluster = tester.Analyser.getClusters().get(0);
        assertFalse(cluster.getAnnotations().contains(CLUSTER_ANNOT_DM));

        tester.clearClustersAndSVs();

        // cannot be a single DEL
        var1 = createTestSv(1,"1","1",1000,2000,-1,-1, INV,2);
        var2 = createTestSv(2,"1","1",4000,5000,1,-1, DEL,20);
        var3 = createTestSv(3,"1","1",5000,6000,1,1, INV,2);

        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);
        tester.AllVariants.add(var3);

        tester.preClusteringInit();

        tester.Analyser.clusterAndAnalyse();

        assertEquals(1, tester.Analyser.getClusters().size());
        cluster = tester.Analyser.getClusters().get(0);
        assertFalse(cluster.getAnnotations().contains(CLUSTER_ANNOT_DM));

    }

}
