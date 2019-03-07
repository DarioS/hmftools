package com.hartwig.hmftools.svanalysis.analysis;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.BND;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DUP;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.SGL;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.typeAsInt;
import static com.hartwig.hmftools.svanalysis.analysis.CNAnalyser.CENTROMERE_CN;
import static com.hartwig.hmftools.svanalysis.analysis.CNAnalyser.P_ARM_TELOMERE_CN;
import static com.hartwig.hmftools.svanalysis.analysis.CNAnalyser.Q_ARM_TELOMERE_CN;
import static com.hartwig.hmftools.svanalysis.analysis.ClusterAnalyser.SHORT_TI_LENGTH;
import static com.hartwig.hmftools.svanalysis.analysis.LinkFinder.NO_DB_MARKER;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.CHROMOSOME_ARM_P;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.appendStr;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.calcConsistency;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.copyNumbersEqual;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.getSvTypesStr;
import static com.hartwig.hmftools.svanalysis.types.SvChain.getRepeatedSvSequence;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.CLUSTER_ANNONTATION_CT;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.CLUSTER_ANNONTATION_DM;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_DEL_EXT_TI;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_DUP_EXT_TI;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.isSpecificCluster;
import static com.hartwig.hmftools.svanalysis.types.SvLinkedPair.LINK_TYPE_TI;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.SVI_END;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.SVI_START;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.isSpecificSV;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.isStart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.purple.purity.PurityContext;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantType;
import com.hartwig.hmftools.common.variant.structural.annotation.EnsemblGeneData;
import com.hartwig.hmftools.svanalysis.types.SvArmCluster;
import com.hartwig.hmftools.svanalysis.types.SvArmGroup;
import com.hartwig.hmftools.svanalysis.types.SvBreakend;
import com.hartwig.hmftools.svanalysis.types.SvCNData;
import com.hartwig.hmftools.svanalysis.types.SvChain;
import com.hartwig.hmftools.svanalysis.types.SvCluster;
import com.hartwig.hmftools.svanalysis.types.SvLinkedPair;
import com.hartwig.hmftools.svanalysis.types.SvVarData;
import com.hartwig.hmftools.svannotation.SvGeneTranscriptCollection;
import com.sun.jmx.snmp.SnmpVarBind;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// post-clustering and chaining routines for annotating clusters, chains and links

public class ClusterAnnotations
{
    private static final Logger LOGGER = LogManager.getLogger(ClusterAnnotations.class);

    public static final String ALL_ANNOTATIONS = "ALL";
    public static final String DOUBLE_MINUTES = "DM";
    public static final String FOLDBACK_MATCHES = "FBM";
    public static final String CHROMOTHRIPSIS = "CT";
    public static final String REPLICATION_REPAIR = "REPR";

    public static boolean runAnnotation(final String annotationsList, final String annotation)
    {
        if(annotationsList.isEmpty())
            return false;

        if(annotationsList.contains(ALL_ANNOTATIONS))
            return true;

        return annotationsList.contains(annotation);
    }

    public static void annotateTemplatedInsertions(final List<SvCluster> clusters, final Map<String, List<SvBreakend>> chrBreakendMap)
    {
        /* work out:
            - whether a TI has a DB on either or both sides in the same cluster
            - number of chained assembled TIs in a row
            - if local, the distance to the next SV in the cluster
         */

        for(final SvCluster cluster : clusters)
        {
            if(cluster.getChains().isEmpty())
                continue;

            isSpecificCluster(cluster);

            // gather up start and end arms from each chain
            List<String> startEndArms = Lists.newArrayList();

            for(final SvChain chain : cluster.getChains())
            {
                for (int be1 = SVI_START; be1 <= SVI_END; ++be1)
                {
                    boolean isFirst = isStart(be1);

                    final SvBreakend chainEnd = chain.getChainEndSV(isFirst).getBreakend(chain.chainEndOpenOnStart(isFirst));
                    if (chainEnd == null)
                        continue;

                    if (!startEndArms.contains(chainEnd.getChrArm()))
                        startEndArms.add(chainEnd.getChrArm());
                }
            }

            for(final SvChain chain : cluster.getChains())
            {
                // use chain start and end as reference point for CN gain and whether a TI is effectively inside a synthetic DEL or not

                for(final SvLinkedPair pair : chain.getLinkedPairs())
                {
                    if(pair.linkType() != LINK_TYPE_TI)
                        continue;

                    if(pair.first().type() == SGL || pair.second().type() == SGL)
                        continue;

                    // find closest SV in this cluster
                    final SvVarData first = pair.first();
                    final SvVarData second = pair.second();
                    SvBreakend firstBreakend = first.getBreakend(pair.firstLinkOnStart());
                    final List<SvBreakend> breakendList = chrBreakendMap.get(firstBreakend.chromosome());

                    int[] nextSVData = getNextClusterSVData(cluster, breakendList, pair);
                    pair.setNextSVData(nextSVData[NEXT_SV_DISTANCE], nextSVData[NEXT_SV_TRAVERSED_COUNT]);

                    pair.setTraversedSVCount(getTraversedSvCount(cluster, breakendList,
                            pair.getBreakend(true).getChrPosIndex(), pair.getBreakend(false).getChrPosIndex()));

                    SvLinkedPair dbFirst = first.getDBLink(pair.firstLinkOnStart());
                    SvLinkedPair dbSecond = pair.second().getDBLink(pair.secondLinkOnStart());

                    pair.setDBLenFirst(dbFirst != null ? dbFirst.length() : NO_DB_MARKER);
                    pair.setDBLenSecond(dbSecond != null ? dbSecond.length() : NO_DB_MARKER);

                    if(startEndArms.contains(firstBreakend.getChrArm()))
                    {
                        pair.setOnArmOfOrigin(true);
                    }
                }
            }
        }
    }

    private static int getTraversedSvCount(final SvCluster cluster, final List<SvBreakend> breakendList, int lowerIndex, int upperIndex)
    {
        String traversedInfo = getTraversedSvData(cluster, breakendList, lowerIndex, upperIndex);

        if(traversedInfo.isEmpty())
            return 0;

        String[] items = traversedInfo.split(";");
        return items.length;
    }

    private static String getTraversedSvData(final SvCluster cluster, final List<SvBreakend> breakendList, int lowerIndex, int upperIndex)
    {
        if(lowerIndex >= upperIndex - 1)
            return "";

        String traversedInfo = "";

        for (int i = lowerIndex + 1; i <= upperIndex - 1; ++i)
        {
            final SvBreakend breakend = breakendList.get(i);
            final SvCluster otherCluster = breakend.getSV().getCluster();

            if (otherCluster == cluster || otherCluster.isResolved())
                continue;

            traversedInfo = appendStr(traversedInfo,
                    String.format("%d %.2f", breakend.orientation(), breakend.getSV().copyNumberChange(breakend.usesStart())),
                    ';');
        }

        return traversedInfo;
    }

    private static int NEXT_SV_DISTANCE = 0;
    private static int NEXT_SV_TRAVERSED_COUNT = 1;

    private static int[] getNextClusterSVData(final SvCluster cluster, final List<SvBreakend> breakendList, final SvLinkedPair pair)
    {
        // walk forward and backwards from this pair to the closest SV in the same cluster
        // counting the number of non-trivial SVs traversed in the process
        int[] nextSvData = {-1, 0};

        SvBreakend firstBreakend = pair.first().getBreakend(pair.firstLinkOnStart());
        SvBreakend secondBreakend = pair.second().getBreakend(pair.secondLinkOnStart());
        int lowerIndex = min(firstBreakend.getChrPosIndex(), secondBreakend.getChrPosIndex());
        int upperIndex = max(firstBreakend.getChrPosIndex(), secondBreakend.getChrPosIndex());
        int svsTraversed = 0;

        if(lowerIndex > 0)
        {
            for(int i = lowerIndex - 1; i >= 0; --i)
            {
                final SvBreakend breakend = breakendList.get(i);

                if (breakend.getSV().getCluster() == cluster)
                {
                    final SvBreakend refBreakend = breakendList.get(lowerIndex);
                    nextSvData[NEXT_SV_DISTANCE] = (int)(refBreakend.position() - breakend.position());
                    nextSvData[NEXT_SV_TRAVERSED_COUNT] = svsTraversed;
                    break;
                }
                else if(!breakend.getSV().getCluster().isResolved())
                {
                    ++svsTraversed;
                }
            }
        }

        if(upperIndex < breakendList.size() - 1)
        {
            svsTraversed = 0;

            for(int i = upperIndex + 1; i < breakendList.size(); ++i)
            {
                final SvBreakend breakend = breakendList.get(i);

                if (breakend.getSV().getCluster() == cluster)
                {
                    final SvBreakend refBreakend = breakendList.get(upperIndex);
                    long distance = breakend.position() - refBreakend.position();

                    if (nextSvData[NEXT_SV_DISTANCE] == -1 || distance < nextSvData[NEXT_SV_DISTANCE])
                    {
                        nextSvData[NEXT_SV_TRAVERSED_COUNT] = svsTraversed;
                        nextSvData[NEXT_SV_DISTANCE] = (int)distance;
                    }

                    break;
                }
                else if(!breakend.getSV().getCluster().isResolved())
                {
                    ++svsTraversed;
                }
            }
        }

        return nextSvData;
    }

    private static int CHAIN_TI_COUNT = 0;
    private static int CHAIN_TI_DB_COUNT = 1;
    private static int CHAIN_TI_SHORT_COUNT = 2;
    private static int CHAIN_TI_ASMB_COUNT = 3;

    public static void annotateChainedClusters(final SvCluster cluster, long proximityCutoff)
    {
        if(cluster.isResolved())
            return;

        boolean isComplex = cluster.hasReplicatedSVs() || !cluster.getFoldbacks().isEmpty();
        boolean isIncomplete = !cluster.isFullyChained() || cluster.getTypeCount(SGL) > 0;

        // skip simple chained clusters
        if(cluster.getArmCount() == 1 && cluster.getSvCount() == 2)
            return;

        isSpecificCluster(cluster);

        /* data to gather for each arm in the chain
            - number of links
            - number of short TIs without proximate deletion bridges
            - links with copy number gain
            - start and end locations
         */

        List<String> originArms = Lists.newArrayList();
        List<String> fragmentArms = Lists.newArrayList();

        int chainCount = cluster.getChains().size();
        int unlinkedSvCount = cluster.getUnlinkedSVs().size();
        int inconsistentChains = 0;
        int repeatedChainEndArms = 0;
        int chainsWithCNLoss = 0;
        int chainsWithCNGain = 0;

        // isSpecificCluster(cluster);

        List<String> chainEndArms = Lists.newArrayList();

        for (final SvChain chain : cluster.getChains())
        {
            if (!chain.isConsistent())
            {
                ++inconsistentChains;
                continue;
            }

            final SvBreakend firstBreakend = chain.getOpenBreakend(true);
            final SvBreakend lastBreakend = chain.getOpenBreakend(false);

            double firstCN = firstBreakend.copyNumber();
            double lastCN = lastBreakend.copyNumber();
            double linkCNTotal = 0; // to track CN along TIs

            final String startChrArm = firstBreakend != null ? firstBreakend.getChrArm() : "";
            final String endChrArm = lastBreakend != null ? lastBreakend.getChrArm() : "";

            if(!startChrArm.isEmpty() && !chainEndArms.contains(startChrArm))
                chainEndArms.add(startChrArm);
            else
                ++repeatedChainEndArms;

            if(!startChrArm.equals(endChrArm))
            {
                if (!endChrArm.isEmpty() && !chainEndArms.contains(endChrArm))
                    chainEndArms.add(startChrArm);
                else
                    ++repeatedChainEndArms;
            }

            Map<String, int[]> armDataMap = new HashMap();

            if(!startChrArm.isEmpty())
                armDataMap.put(startChrArm, new int[CHAIN_TI_ASMB_COUNT+1]);

            if(!endChrArm.isEmpty())
                armDataMap.put(endChrArm, new int[CHAIN_TI_ASMB_COUNT+1]);

            int shortTICount = 0;
            long chainLinkLength = 0;

            for(final SvLinkedPair pair : chain.getLinkedPairs())
            {
                final SvVarData first = pair.first();

                if(pair.first().type() == SGL || pair.second().type() == SGL)
                    continue;

                chainLinkLength += pair.length();
                linkCNTotal += (pair.getBreakend(true).copyNumber() + pair.getBreakend(false).copyNumber()) * 0.5;

                final String chrArm = first.getBreakend(pair.firstLinkOnStart()).getChrArm();

                int[] armData = armDataMap.get(chrArm);

                if(armData == null)
                {
                    armData = new int[CHAIN_TI_ASMB_COUNT+1];
                    armDataMap.put(chrArm, armData);
                }

                ++armData[CHAIN_TI_COUNT];

                if((pair.getDBLenFirst() > NO_DB_MARKER && pair.getDBLenFirst() <= proximityCutoff)
                || (pair.getDBLenSecond() > NO_DB_MARKER && pair.getDBLenSecond() <= proximityCutoff))
                {
                    ++armData[CHAIN_TI_DB_COUNT];
                }

                if(pair.length() <= SHORT_TI_LENGTH)
                {
                    ++armData[CHAIN_TI_SHORT_COUNT];
                    ++shortTICount;

                    if(pair.isAssembled())
                        ++armData[CHAIN_TI_ASMB_COUNT];
                }
            }

            // check for synthetic DELs and DUPs from longer chains
            if(inconsistentChains == 0 && !isIncomplete && !isComplex
            && chainCount == 1 && chain.getLinkCount() == shortTICount
            && firstBreakend.getChrArm().equals(lastBreakend.getChrArm())
            && firstBreakend.orientation() != lastBreakend.orientation())
            {
                long syntheticLength = abs(lastBreakend.position() - firstBreakend.position());
                long avgLinkLength = round(chainLinkLength/chain.getLinkCount());

                cluster.setSynDelDupData(syntheticLength, avgLinkLength);

                if((firstBreakend.position() < lastBreakend.position() && firstBreakend.orientation() == 1)
                        || (lastBreakend.position() < firstBreakend.position() && lastBreakend.orientation() == 1))
                {
                    cluster.setResolved(false, RESOLVED_TYPE_DEL_EXT_TI);
                }
                else
                {
                    cluster.setResolved(false, RESOLVED_TYPE_DUP_EXT_TI);
                }

                LOGGER.debug("cluster({}) chainLinks({}) synLen({}) avgTILen({}) marked as {}",
                        cluster.id(), chain.getLinkCount(), syntheticLength, avgLinkLength, cluster.getResolvedType());
            }

            String chainInfo = startChrArm + "-" + endChrArm;

            for (Map.Entry<String,int[]> entry : armDataMap.entrySet())
            {
                final String chrArm = entry.getKey();
                final int[] armData = entry.getValue();

                int linkCount = armData[CHAIN_TI_COUNT];
                int dbCount = armData[CHAIN_TI_DB_COUNT];
                int shortCount = armData[CHAIN_TI_SHORT_COUNT];
                int assembledCount = armData[CHAIN_TI_ASMB_COUNT];

                boolean isOrigin = false;
                if(chrArm.equals(startChrArm) || chrArm.equals(endChrArm))
                {
                    isOrigin = true;
                }
                else if(shortCount > dbCount)
                {
                    isOrigin = false;
                }

                if(isOrigin)
                {
                    // if this arm exists twice already, then more than 2 chains end up on the same arm which is invalid
                    originArms.add(chrArm);

                    if(fragmentArms.contains(chrArm))
                        fragmentArms.remove(chrArm);
                }
                else if(!isOrigin && !fragmentArms.contains(chrArm))
                {
                    fragmentArms.add(chrArm);
                }

                chainInfo += String.format(" %s %s: LK=%d DB=%d SH=%d AS=%d",
                        isOrigin ? "O" : "F", chrArm, linkCount, dbCount, shortCount, assembledCount);
            }

            chain.setDetails(chainInfo);

            double lossCNTotal = 0; // to track CN loss across SVs

            for (final SvVarData var : chain.getSvList())
            {
                if(!var.isNullBreakend())
                {
                    lossCNTotal += (var.copyNumber(true) - var.copyNumberChange(true)
                            + var.copyNumber(false) - var.copyNumberChange(false)) * 0.5;
                }
                else
                {
                    lossCNTotal += var.copyNumber(false) - var.copyNumberChange(false);
                }
           }

            // LOGGER.debug("cluster({}) chain({}) {}", cluster.id(), chain.id(), chainInfo);

            double avgDelCN = lossCNTotal / chain.getSvCount();
            double avgLinkCN = linkCNTotal / chain.getLinkCount();

            // summarise chain CN profile
            if(copyNumbersEqual(firstCN, lastCN))
            {
                double avgChainEndCN = (firstCN + lastCN) * 0.5;
                if(avgDelCN < avgChainEndCN && copyNumbersEqual(avgLinkCN, avgChainEndCN))
                {
                    ++chainsWithCNLoss;
                }
                else if(avgLinkCN > avgChainEndCN && copyNumbersEqual(avgDelCN, avgChainEndCN))
                {
                    ++chainsWithCNGain;
                }
            }
        }

        cluster.setArmData(originArms.size(), fragmentArms.size());

        int armGroupCount = cluster.getArmGroups().size();

        final List<SvVarData> unlinkedRemoteSVs = cluster.getUnlinkedRemoteSVs();

        int inconsistentArmCount = 0;

        for(final SvArmGroup armGroup : cluster.getArmGroups())
        {
            if(!armGroup.isConsistent())
            {
                ++inconsistentArmCount;
                continue;
            }

            for (final SvVarData var : unlinkedRemoteSVs)
            {
                if (armGroup.getSVs().contains(var))
                {
                    ++inconsistentArmCount;
                    continue;
                }
            }
        }

        boolean isComplete = (inconsistentChains == 0) && (repeatedChainEndArms == 0) && (unlinkedSvCount == 0);

        LOGGER.debug("cluster({}) {} chains({} incons={}) chainEnds(arms={} repeats={}) unlinkedSVs({} armCount({} incons={}))",
                cluster.id(), isComplete ? "COMPLETE" : "incomplete",
                chainCount, inconsistentChains, chainEndArms.size(), repeatedChainEndArms,
                unlinkedSvCount, armGroupCount, inconsistentArmCount);

        if(isComplete)
        {
            cluster.addAnnotation("COMPLETE");

            // TEMP: chromothripsis is currently defined as fully chained simple cluster
            // but needs to take into account the copy number gain / loss compared with the surrounding chromatid
            if(!isComplex)
            {
                cluster.addAnnotation(CLUSTER_ANNONTATION_CT);
            }

            // for each chain, check the CN relative to the start and end
            if(chainsWithCNGain == chainCount)
            {
                cluster.addAnnotation("CN_GAIN");
            }
            else if(chainsWithCNLoss == chainCount)
            {
                cluster.addAnnotation("CN_LOSS");
            }
        }
    }

    public static void annotateFoldbacks(final List<SvCluster> clusters)
    {
        // now foldbacks are known, add other annotations about them
        for(final SvCluster cluster : clusters)
        {
            final Map<String, List<SvBreakend>> chrBreakendMap = cluster.getChrBreakendMap();

            for (final Map.Entry<String, List<SvBreakend>> entry : chrBreakendMap.entrySet())
            {
                int chromosomeFoldbackCount = 0;
                List<SvVarData> chainedFoldbacks = Lists.newArrayList(); // to avoid double counting

                for(final SvBreakend breakend : entry.getValue())
                {
                    if (!breakend.getSV().isFoldback())
                        continue;

                    // only process each SV's foldback once where both breakends are part of it
                    if(!breakend.getSV().isChainedFoldback())
                    {
                        if(!breakend.usesStart())
                            continue;
                    }
                    else
                    {
                        final SvVarData otherSv = breakend.getSV().getChainedFoldbackSv();
                        if(chainedFoldbacks.contains(otherSv))
                            continue;

                        chainedFoldbacks.add(breakend.getSV());
                    }

                    ++chromosomeFoldbackCount;
                }

                if(chromosomeFoldbackCount == 0)
                    continue;

                chainedFoldbacks.clear();
                int foldbackIndex = 0;

                for(final SvBreakend breakend : entry.getValue())
                {
                    // isSpecificSV(breakend.getSV());

                    if(!breakend.getSV().isFoldback())
                        continue;

                    if(!breakend.getSV().isChainedFoldback())
                    {
                        if(!breakend.usesStart())
                            continue;
                    }
                    else
                    {
                        final SvVarData otherSv = breakend.getSV().getChainedFoldbackSv();
                        if(chainedFoldbacks.contains(otherSv))
                            continue;

                        chainedFoldbacks.add(breakend.getSV());
                    }

                    String existingInfo = breakend.getSV().getFoldbackInfo(breakend.usesStart());

                    if(existingInfo.isEmpty())
                        existingInfo = breakend.getSV().getFoldbackInfo(!breakend.usesStart());;

                    long armLength = SvUtilities.getChromosomalArmLength(breakend.chromosome(), breakend.arm());
                    double positionPercent;
                    int foldbackRank;

                    if(breakend.arm() == CHROMOSOME_ARM_P)
                    {
                        foldbackRank = foldbackIndex;
                        positionPercent = breakend.position() / (double)armLength;
                    }
                    else
                    {
                        foldbackRank = chromosomeFoldbackCount - foldbackIndex - 1;
                        long chromosomeLength = SvUtilities.CHROMOSOME_LENGTHS.get(breakend.chromosome());
                        long centromere = chromosomeLength - armLength;
                        positionPercent = 1 - (breakend.position() - centromere) / (double)armLength;
                    }

                    ++foldbackIndex;

                    String facesTorC = (breakend.orientation() == 1) == (breakend.arm() == CHROMOSOME_ARM_P) ? "T" : "C";

                    String foldbackInfo = String.format("%s;%s;%d;%.4f", existingInfo, facesTorC, foldbackRank, positionPercent);

                    for(int be = SVI_START; be <= SVI_END; ++be)
                    {
                        boolean isStart = isStart(be);

                        if(breakend.getSV().getFoldbackBreakend(isStart) == null)
                            continue;

                        breakend.getSV().setFoldbackInfo(isStart, foldbackInfo);
                    }
                }
            }
        }
    }

    public static void findIncompleteFoldbackCandidates(final String sampleId, final SvCluster cluster,
            final Map<String, List<SvBreakend>> chrBreakendMap, final CNAnalyser cnAnalyser)
    {
        // for each cluster with incomplete foldbacks, search for candidate clusters which could resolve it

        // for now just focus on single foldback clusters
        if(cluster.getFoldbacks().isEmpty())
            return;

        if(cluster.isResolved()) // eg LowQual
            return;

        // find every other non-simple cluster which has a breakend facing one of the foldbacks in this cluster

        // first establish the bounds of the the foldbacks, independently for each arm
        int foldbackCount = 0;

        boolean isMultiArm = false;
        String chromosome = "";
        String arm = "";
        double maxFoldbackPloidy = 0;
        String foldbackIds = "";
        String foldbackOrientations = "";

        List<SvVarData> chainedFoldbacks = Lists.newArrayList(); // to avoid double counting

        // find the outermost foldbacks to check against all other opposing cluster breakends
        SvBreakend breakendLower = null; // lowest facing up
        SvBreakend breakendUpper = null; // highest facing down

        for(SvVarData var : cluster.getFoldbacks())
        {
            SvBreakend breakend = null;

            if (!var.isChainedFoldback())
            {
                breakend = var.getBreakend(true);
            }
            else
            {
                final SvVarData otherSv = var.getChainedFoldbackSv();
                if (chainedFoldbacks.contains(otherSv))
                    continue;

                chainedFoldbacks.add(var);
                breakend = var.getFoldbackBreakend(true);

                if (breakend == null)
                    breakend = var.getFoldbackBreakend(false);
            }

            if (chromosome == "")
            {
                chromosome = breakend.chromosome();
                arm = breakend.arm();
            }
            else if (!breakend.chromosome().equals(chromosome) || !breakend.arm().equals(arm))
            {
                isMultiArm = true;
            }

            if(!isMultiArm)
            {
                if (breakend.orientation() == 1 && (breakendUpper == null || breakend.position() > breakendUpper.position()))
                {
                    breakendUpper = breakend;
                }
                else if (breakend.orientation() == -1 && (breakendLower == null || breakend.position() < breakendLower.position()))
                {
                    breakendLower = breakend;
                }
            }

            maxFoldbackPloidy = max(maxFoldbackPloidy, breakend.getSV().ploidyMin());
            ++foldbackCount;

            foldbackIds = appendStr(foldbackIds, breakend.getSV().id(), ';');
            String direction = (breakend.orientation() == 1) == (breakend.arm() == CHROMOSOME_ARM_P) ? "T" : "C";
            foldbackOrientations = appendStr(foldbackOrientations, direction, ';');
        }

        if(breakendLower == null && breakendUpper == null)
            return;

        // get CN change across the centromere
        double centromereCNChange = cnAnalyser.getCentromereCopyNumberChange(chromosome, arm.equals(CHROMOSOME_ARM_P));

        final double[] cnData = cnAnalyser.getChrCopyNumberMap().get(chromosome);

        double telomereCN = 0;
        if (cnData != null)
        {
            telomereCN = arm == CHROMOSOME_ARM_P ? cnData[P_ARM_TELOMERE_CN] : cnData[Q_ARM_TELOMERE_CN];
        }

        // now find all clusters with opposing breakends

        final List<SvBreakend> breakendList = chrBreakendMap.get(chromosome);

        // cache max opposing and net ploidy for each opposing cluster
        List<SvCluster> processedClusters = Lists.newArrayList();
        int opposingClusterCount = 0;
        String allClusterInfo = "";

        for(int i = 0; i <= 1; ++i)
        {
            SvBreakend fbBreakend = (i == 0) ? breakendLower : breakendUpper;

            if(fbBreakend == null)
                continue;

            int index = fbBreakend.getChrPosIndex();

            while (true)
            {
                if (fbBreakend.orientation() == 1) // walk in the direction the foldback faces
                    --index;
                else
                    ++index;

                if (index < 0 || index >= breakendList.size())
                    break;

                final SvBreakend nextBreakend = breakendList.get(index);
                if (nextBreakend.arm() != fbBreakend.arm())
                    break;

                if (nextBreakend.orientation() == fbBreakend.orientation())
                    continue;

                final SvCluster nextCluster = nextBreakend.getSV().getCluster();

                if(processedClusters.contains(nextCluster))
                    continue;

                processedClusters.add(nextCluster);

                if (nextCluster.isResolved())
                    continue;

                // found an opposing non-simple cluster, gather up details about it
                // max opposing CN min poidy and net ploidy
                final List<SvBreakend> nextClusterBreakends = nextCluster.getChrBreakendMap().get(chromosome);

                double maxOpposingPloidy = 0;
                double netPloidy = 0;

                for(final SvBreakend otherBreakend : nextClusterBreakends)
                {
                    if (otherBreakend.arm() != fbBreakend.arm())
                        continue;

                    netPloidy += otherBreakend.getSV().ploidyMin() * otherBreakend.orientation();

                    // take the max opposing breakend for lower and upper FB breakends
                    if(breakendLower != null && otherBreakend.orientation() == 1 && otherBreakend.position() > breakendLower.position())
                    {
                        maxOpposingPloidy = max(maxOpposingPloidy, otherBreakend.getSV().ploidyMin());
                    }
                    else if(breakendUpper != null && otherBreakend.orientation() == -1 && otherBreakend.position() < breakendUpper.position())
                    {
                        maxOpposingPloidy = max(maxOpposingPloidy, otherBreakend.getSV().ploidyMin());
                    }
                }

                ++opposingClusterCount;

                String clusterInfo = String.format("%d/%d/%.2f/%.2f",
                        nextCluster.id(), nextCluster.getSvCount(), netPloidy, maxOpposingPloidy);

                allClusterInfo = appendStr(allClusterInfo, clusterInfo, ';');
            }
        }

        // SampleId,ClusterId,ClusterCount,FoldbackCount,IsMultiArm,ClusterDesc,Chromosome,Arm,SvIds,Orientations,PosLower,PosUpper,
        // MaxFoldbackPloidy,TelomereCN,CentromereCNChg,OpposingClusterCount,OpposingClusterInfo

        String infoStr = String.format("%s,%d,%d,%d,%s,%s,%s,%s",
                sampleId, cluster.id(), cluster.getSvCount(), foldbackCount, isMultiArm, cluster.getDesc(), chromosome, arm);

        infoStr += String.format(",%s,%s,%d,%d,%.2f,%.2f,%.2f,%d,%s",
                foldbackIds, foldbackOrientations,
                breakendLower != null ? breakendLower.position() : -1, breakendUpper != null ? breakendUpper.position() : -1,
                maxFoldbackPloidy, telomereCN, centromereCNChange, opposingClusterCount, allClusterInfo);

        LOGGER.info("INCONSIST_FBS: {}", infoStr);
    }

    private static double DM_PLOIDY_MIN_RATIO = 2.3;
    private static double DM_MIN_PLOIDY = 3;

    private static double DM_PLOIDY_INCOMPLETE_MIN_RATIO = 4;
    private static double DM_INCOMPLETE_MIN_PLOIDY = 10;

    private static int DM_MAX_SV_COUNT = 16;

    public static void findPotentialDoubleMinuteClusters(final String sampleId, final Map<String,
            List<SvBreakend>> chrBreakendMap, final CNAnalyser cnAnalyser, final SvGeneTranscriptCollection geneCollection)
    {
        /* Identify potential DM clusters if:
            - each of their breakends is X times the major allele ploidy of the near CN segment
            - they have offsetting facing breakends on each arm
            - any SVs they skip over have ploidy X times less than the DM
            - skip any groups containing SGLs/NONEs

           In addition, check any loose SVs which meet the above critieria but weren't complete:
           - eg if they contain SGLs or NONES
           - they aren't all offset on an arm
        */

        List<SvBreakend> incompleteDMBreakends = Lists.newArrayList();

        final Map<String,List<SvCNData>> chrCopyNumberDataMap = cnAnalyser.getChrCnDataMap();
        final Map<String,SvCNData[]> svCopyNumberDataMap = cnAnalyser.getSvIdCnDataMap();

        if(chrCopyNumberDataMap.isEmpty() || svCopyNumberDataMap.isEmpty())
            return;

        for (final Map.Entry<String, List<SvBreakend>> entry : chrBreakendMap.entrySet())
        {
            final String chromosome = entry.getKey();
            List<SvBreakend> breakendList = entry.getValue();

            double prevPloidy = 0;
            double minDMPloidy = 0;
            double minDMCopyNumber = 0;
            double maxDMCopyNumber = 0;
            double maxOutsideMap = 0;
            boolean inPotentialDM = false;
            boolean isDmGroupResolved = false;

            final List<SvCNData> cnDataList = chrCopyNumberDataMap.get(chromosome);

            if(cnDataList == null || cnDataList.isEmpty())
            {
                LOGGER.warn("sample({}) missing CN data for chromosome({})", sampleId, chromosome);
                continue;
            }

            double telomereMAP = cnDataList.get(0).majorAllelePloidy();

            List<SvVarData> dmSVList = Lists.newArrayList();
            List<SvBreakend> dmBreakendList = Lists.newArrayList();
            int overlappedCount = 0;

            for(final SvBreakend breakend : breakendList)
            {
                double minPloidy = breakend.getSV().ploidyMin();
                double maxPloidy = breakend.getSV().ploidyMax();

                isSpecificSV(breakend.getSV());

                // first check the min ploidy vs the adjacent CN segment
                double adjacentMap = getAdjacentMajorAllelePloidy(breakend, svCopyNumberDataMap, cnDataList);

                if(Double.isNaN(adjacentMap))
                    continue;

                if(minPloidy >= DM_INCOMPLETE_MIN_PLOIDY && minPloidy >= DM_PLOIDY_INCOMPLETE_MIN_RATIO * adjacentMap)
                {
                    // keep track of all high-ploidy breakends for later analysis of incomplete DM groups
                    incompleteDMBreakends.add(breakend);
                }

                if(!inPotentialDM)
                {
                    if(breakend.orientation() != -1)
                    {
                        prevPloidy = maxPloidy;
                        continue;
                    }

                    if(minPloidy < DM_MIN_PLOIDY || minPloidy < telomereMAP * DM_PLOIDY_MIN_RATIO
                    || (prevPloidy > 0 && minPloidy < prevPloidy * DM_PLOIDY_MIN_RATIO))
                    {
                        prevPloidy = maxPloidy;
                        continue;
                    }

                    if(!isValidDMBreakend(breakend, svCopyNumberDataMap, chrCopyNumberDataMap))
                        continue;

                    final SvVarData var = breakend.getSV();

                    // check the major allele ploidy outside this breakend
                    final SvCNData[] cnDataPair = svCopyNumberDataMap.get(var.id());
                    if(cnDataPair == null)
                        continue;

                    final SvCNData cnData = breakend.usesStart() ? cnDataPair[SVI_START] : cnDataPair[SVI_END];
                    final SvCNData prevCnData = cnDataList.get(cnData.getIndex() - 1);
                    double prevMap = prevCnData.majorAllelePloidy();

                    if(minPloidy < prevMap * DM_PLOIDY_MIN_RATIO)
                        continue;

                    // satisfies the conditions to start a potential DM
                    minDMPloidy = minPloidy;
                    minDMCopyNumber = breakend.copyNumber();
                    maxDMCopyNumber = minDMCopyNumber;
                    maxOutsideMap = prevMap;

                    inPotentialDM = true;

                    dmBreakendList.clear();
                    dmSVList.clear();
                    overlappedCount = 0;

                    dmBreakendList.add(breakend);
                    dmSVList.add(var);

                    isDmGroupResolved = false;
                }
                else
                {
                    // skip any low ploidy SVs
                    if(maxPloidy * DM_PLOIDY_MIN_RATIO < minDMPloidy)
                    {
                        ++overlappedCount;
                        continue;
                    }

                    final SvVarData var = breakend.getSV();

                    // cancel a potential group if it encounters a high-ploidy SGL
                    if(!isValidDMBreakend(breakend, svCopyNumberDataMap, chrCopyNumberDataMap))
                    {
                        inPotentialDM = false;
                        LOGGER.debug("cancelling potential DM group(count={} first={}) at invalid SV", dmSVList.size(), dmSVList.get(0).posId());
                        continue;
                    }

                    minDMPloidy = min(minPloidy, minDMPloidy);
                    minDMCopyNumber = min(breakend.copyNumber(), minDMCopyNumber);
                    maxDMCopyNumber = max(breakend.copyNumber(), maxDMCopyNumber);

                    dmBreakendList.add(breakend);

                    if(!dmSVList.contains(var))
                        dmSVList.add(var);

                    if(breakend.orientation() == 1)
                    {
                        isDmGroupResolved = isResolvedDMGroup(dmBreakendList, dmSVList);

                        if (isDmGroupResolved)
                        {
                            // check that the next breakend drops back below the required threshold
                            if(minDMPloidy < adjacentMap * DM_PLOIDY_MIN_RATIO)
                            {
                                LOGGER.debug(String.format("cancelling potential DM group(count=%d first=%s): minDmPloidy(%.2f) vs nextMap(%.2f)",
                                        dmSVList.size(), dmSVList.get(0).posId(), minDMPloidy, adjacentMap));

                                inPotentialDM = false;
                                continue;
                            }

                            maxOutsideMap = max(maxOutsideMap, adjacentMap);

                            LOGGER.debug("DM group identified");

                            // remove from consideration of incomplete DM groups
                            dmBreakendList.stream().forEach(x -> incompleteDMBreakends.remove(x));

                            reportPotentialDoubleMinuteGroup(sampleId, dmSVList, true, overlappedCount, cnAnalyser, geneCollection);
                            inPotentialDM = false;
                            continue;
                        }
                    }

                    if(dmSVList.size() >= DM_MAX_SV_COUNT && !isDmGroupResolved)
                    {
                        LOGGER.debug("cancelling potential DM group(count={} first={}) at max SV count",
                                dmSVList.size(), dmSVList.get(0).posId());
                        inPotentialDM = false;
                        continue;
                    }
                }
            }
        }

        if(incompleteDMBreakends.isEmpty())
            return;

        // now follow-up on any incomplete potential DM groups

        // first check that both breakends are present for all non-SGL SVs
        List<SvVarData> incompleteDMSvList = Lists.newArrayList();

        for(int i = 0; i < incompleteDMBreakends.size(); ++i)
        {
            SvBreakend breakend = incompleteDMBreakends.get(i);

            if(breakend.getSV().isNullBreakend())
            {
                incompleteDMSvList.add(breakend.getSV());
            }
            else
            {
                for (int j = i+1; j < incompleteDMBreakends.size(); ++j)
                {
                    SvBreakend otherBreakend = incompleteDMBreakends.get(j);
                    if (breakend.getSV() == otherBreakend.getSV())
                    {
                        incompleteDMSvList.add(breakend.getSV());
                        break;
                    }
                }
            }
        }

        if(incompleteDMSvList.isEmpty())
            return;

        // follow a chain through any linked chromosomes to create a set of related SVs
        while(!incompleteDMSvList.isEmpty())
        {
            List<String> chromosomeList = Lists.newArrayList();

            SvVarData var = incompleteDMSvList.get(0);

            if(!chromosomeList.contains(var.chromosome(true)))
                chromosomeList.add(var.chromosome(true));

            if(!var.isNullBreakend() && !chromosomeList.contains(var.chromosome(false)))
                chromosomeList.add(var.chromosome(false));

            List<SvVarData> dmSvList = Lists.newArrayList();
            dmSvList.add(var);

            boolean addedMoreChromosomes = true;

            while(addedMoreChromosomes)
            {
                addedMoreChromosomes = false;

                for (int j = 1; j < incompleteDMSvList.size(); ++j)
                {
                    SvVarData otherVar = incompleteDMSvList.get(j);

                    if (!chromosomeList.contains(otherVar.chromosome(true)) && !chromosomeList.contains(otherVar.chromosome(false)))
                        continue;

                    if(!dmSvList.contains(otherVar))
                        dmSvList.add(otherVar);

                    if(!chromosomeList.contains(otherVar.chromosome(true)))
                    {
                        addedMoreChromosomes = true;
                        chromosomeList.add(otherVar.chromosome(true));
                        break;
                    }

                    if(!var.isNullBreakend() && !chromosomeList.contains(otherVar.chromosome(false)))
                    {
                        addedMoreChromosomes = true;
                        chromosomeList.add(otherVar.chromosome(false));
                        break;
                    }
                }
            }

            if(dmSvList.isEmpty())
                break;

            dmSvList.stream().forEach(x -> incompleteDMSvList.remove(x));

            reportPotentialDoubleMinuteGroup(sampleId, dmSvList, false, 0, cnAnalyser, geneCollection);
        }
    }

    private static void reportPotentialDoubleMinuteGroup(final String sampleId, List<SvVarData> dmSVList, boolean isComplete,
            int overlappedCount, final CNAnalyser cnAnalyser, final SvGeneTranscriptCollection geneCollection)
    {
        String clusterInfo = "";
        String svInfo = "";
        List<SvCluster> clusters = Lists.newArrayList();

        int[] typeCounts = new int[StructuralVariantType.values().length];
        List<String> chromosomes = Lists.newArrayList();

        double minDMPloidy = 0;
        double maxDMCopyNumber = 0;

        for(final SvVarData var : dmSVList)
        {
            ++typeCounts[typeAsInt(var.type())];

            if(minDMPloidy == 0 || var.ploidyMin() < minDMPloidy)
                minDMPloidy = var.ploidyMin();

            maxDMCopyNumber = max(maxDMCopyNumber, max(var.copyNumber(true), var.copyNumber(false)));

            if(!chromosomes.contains(var.chromosome(true)))
                chromosomes.add(var.chromosome(true));

            if(!var.isNullBreakend() && !chromosomes.contains(var.chromosome(false)))
                chromosomes.add(var.chromosome(false));

            svInfo = appendStr(svInfo, var.id(), ';');

            final SvCluster cluster = var.getCluster();
            if (!clusters.contains(cluster))
            {
                clusters.add(cluster);

                cluster.addAnnotation(CLUSTER_ANNONTATION_DM);
                clusterInfo = appendStr(clusterInfo, String.format("%d-%s-%s", cluster.id(), cluster.getDesc(), cluster.getResolvedType()), ';');
            }
        }

        final String dmTypesStr = getSvTypesStr(typeCounts);

        final PurityContext purityContext = cnAnalyser.getPurityContext();
        double samplePurity = purityContext != null ? purityContext.bestFit().purity() : 0;
        double samplePloidy = purityContext != null ? purityContext.bestFit().ploidy() : 0;

        final SvChain chain = isComplete ? createDMChain(dmSVList) : null;

        long dmChainLength = chain != null ? chain.getLength(true) : 0;

        String amplifiedGenesStr = chain != null ? getAmplifiedGenesList(chain, geneCollection) : "";

        // get amplified genes list by looking at all section tranversed by this chain
        // or breakends with genes in them?
        long posStart = 0;
        long posEnd = 0;

        if(isComplete && dmSVList.size() == 1)
        {
            SvVarData dup = dmSVList.get(0);
            posStart = dup.position(true);
            posEnd = dup.position(false);
        }

        String chromosomeStr = "";
        for(String chr : chromosomes)
        {
            chromosomeStr = appendStr(chromosomeStr, chr, ';');
        }

        // SampleId,SamplePurity,SamplePloidy,IsComplete,GroupCount,ClusterInfo,SvTypes,SvInfo,Chromosomes,DMPosStart,DMPosEnd,
        // MaxDMCopyNumber,MinDMPloidy,SVOverlapCount,DMLength,AmplifiedGenes
        String infoStr = String.format("%s,%.2f,%.2f,%s,%d,%s,%s,%s,%s,%d,%d",
                sampleId, samplePurity, samplePloidy, isComplete, dmSVList.size(), clusterInfo, dmTypesStr, svInfo,
                chromosomeStr, posStart, posEnd);

        infoStr += String.format(",%.2f,%.2f,%d,%d,%s",
                 maxDMCopyNumber, minDMPloidy, overlappedCount, dmChainLength, amplifiedGenesStr);

        LOGGER.info("POTENTIAL_DM_DATA: {}", infoStr);
    }

    private static double getAdjacentMajorAllelePloidy(final SvBreakend breakend,
            final Map<String,SvCNData[]> svCopyNumberDataMap, final List<SvCNData> cnDataList)
    {
        // check the major allele ploidy outside this breakend
        final SvCNData[] cnDataPair = svCopyNumberDataMap.get(breakend.getSV().id());
        if(cnDataPair == null)
            return 0;

        final SvCNData cnData = breakend.usesStart() ? cnDataPair[SVI_START] : cnDataPair[SVI_END];
        if(cnData == null)
            return Double.NaN;

        // CN data is always the segment that starts with the SV's position, so the adjacent
        // segment will already be correct for orientation +1, but needs to take the preceding one for -1
        final SvCNData adjacentCNData = breakend.orientation() == -1 ? cnDataList.get(cnData.getIndex() - 1) : cnData;

        return adjacentCNData.majorAllelePloidy();
    }

    private static boolean isValidDMBreakend(final SvBreakend breakend,
            final Map<String,SvCNData[]> svCopyNumberDataMap, final Map<String,List<SvCNData>> chrCopyNumberDataMap)
    {
        final SvVarData var = breakend.getSV();

        if (var.isNullBreakend())
            return false;

        if (var.chromosome(true).equals(var.chromosome(false)))
            return true;

        // the other end must be a in a TI
        final SvLinkedPair remoteTI = var.getLinkedPair(!breakend.usesStart());

        if (remoteTI == null)
            return false;

        final String remoteChr = breakend.getSV().getBreakend(!breakend.usesStart()).chromosome();
        final List<SvCNData> cnDataList = chrCopyNumberDataMap.get(remoteChr);

        // check ploidy context of remote breakends as well
        for(int be = SVI_START; be <= SVI_END; ++be)
        {
            boolean isStart = isStart(be);
            final SvBreakend remoteBreakend = remoteTI.getBreakend(isStart);
            final SvVarData remoteSV = remoteBreakend.getSV();

            // the other end of this remote TI doesn't link back to the original chromosome
            final SvBreakend remoteOtherBreakend = remoteSV.getBreakend(!remoteBreakend.usesStart());

            if(remoteOtherBreakend == null || !remoteOtherBreakend.chromosome().equals(breakend.chromosome()))
                return false;

            final SvCNData[] cnDataPair = svCopyNumberDataMap.get(remoteSV.id());
            if (cnDataPair == null)
            {
                LOGGER.warn("missing CN data for DM SV({})", remoteSV.id());
                return false;
            }

            // check that the next breakend drops back below the required threshold
            final SvCNData cnData = remoteBreakend.usesStart() ? cnDataPair[SVI_START] : cnDataPair[SVI_END];

            if(cnDataList.size() <= cnData.getIndex())
            {
                LOGGER.error("chr({}) invalid cnDataIndex({}) vs list size({})", remoteChr, cnData.getIndex(), cnDataList.size());
                return false;
            }

            final SvCNData applicableCNData = isStart ? cnDataList.get(cnData.getIndex() - 1) : cnData;
            double outsideMap = applicableCNData.majorAllelePloidy();

            if (var.ploidyMin() < outsideMap * DM_PLOIDY_MIN_RATIO)
                return false;
        }

        return true;
    }

    private static boolean isResolvedDMGroup(List<SvBreakend> breakendList, List<SvVarData> svList)
    {
        // check that every local SVs has recorded both its breakends and every remote SV
        // forms a TI with another remote TI
        int orientationTotal = 0;

        for(final SvVarData var : svList)
        {
            final SvBreakend startBreakend = var.getBreakend(true);
            final SvBreakend endBreakend = var.getBreakend(false);

            orientationTotal += calcConsistency(var);

            if(var.chromosome(true).equals(var.chromosome(false)))
            {
                if(!breakendList.contains(startBreakend) || !breakendList.contains(var.getBreakend(false)))
                    return false;
            }
            else
            {
                // does this SV form a remote TI with another SV in this group?
                final SvBreakend remoteBreakend = breakendList.contains(startBreakend) ? endBreakend : startBreakend;
                final SvLinkedPair remoteTI = var.getLinkedPair(remoteBreakend.usesStart());

                if(remoteTI == null)
                    return false;

                final SvBreakend otherRemoteBreakend = remoteTI.getOtherBreakend(remoteBreakend);

                if(!svList.contains(otherRemoteBreakend.getSV()))
                    return false;
            }
        }

        if(orientationTotal != 0)
            return false;

        return true;
    }

    private static final String getAmplifiedGenesList(final SvChain chain, final SvGeneTranscriptCollection geneCollection)
    {
        if(geneCollection == null)
            return "";

        String genesStr = "";
        for(SvLinkedPair pair : chain.getLinkedPairs())
        {
            String chromosome = pair.chromosome();

            List<EnsemblGeneData> genesList = geneCollection.findGenesByRegion(chromosome, pair.getBreakend(true).position(), pair.getBreakend(false).position());

            if(genesList.isEmpty())
                continue;

            for(final EnsemblGeneData geneData : genesList)
            {
                genesStr = appendStr(genesStr, geneData.GeneName, ';');
            }
        }

        return genesStr;
    }

    private static final SvChain createDMChain(List<SvVarData> dmSVList)
    {
        if(dmSVList.size() == 1)
        {
            // special case creating a chain out of a DUP
            SvChain chain = new SvChain(0);
            final SvVarData var = dmSVList.get(0);
            if(var.type() != DUP)
                return null;

            SvLinkedPair pair = new SvLinkedPair(var, var, LINK_TYPE_TI, true, false);
            chain.addLink(pair, true);
            return chain;
        }

        // create a temporary cluster and try to chain it
        SvCluster dmCluster = new SvCluster(0);
        dmSVList.stream().forEach(x -> dmCluster.addVariant(x));

        LinkFinder linkFinder = new LinkFinder();
        dmCluster.setAssemblyLinkedPairs(linkFinder.createAssemblyLinkedPairs(dmCluster));

        ChainFinder chainFinder = new ChainFinder();
        chainFinder.initialise(dmCluster);
        chainFinder.formClusterChains(false);

        if(dmCluster.getChains().size() != 1)
            return null;

        return dmCluster.getChains().get(0);
    }

    private static double DOUBLE_MINUTE_PLOIDY_THRESHOLD = 8;
    private static double DOUBLE_MINUTE_PLOIDY_GAP_RATIO = 3;

    public static void reportDoubleMinutes(final SvCluster cluster, final Map<String, List<SvBreakend>> chrBreakendMap)
    {
        // order SVs in descending ploidy order
        List<Double> ploidyList = Lists.newArrayList();
        List<SvVarData> indexSvList = Lists.newArrayList();
        boolean hasHighPloidy = false;

        for(final SvVarData var : cluster.getSVs())
        {
            double ploidy = var.getSvData().ploidy();
            int i = 0;
            for(; i < ploidyList.size(); ++i)
            {
                Double otherPloidy = ploidyList.get(i);
                if(ploidy > otherPloidy)
                    break;
            }

            ploidyList.add(i, ploidy);
            indexSvList.add(i, var);

            if(ploidy >= DOUBLE_MINUTE_PLOIDY_THRESHOLD)
                hasHighPloidy = true;
        }

        if(!hasHighPloidy)
            return;

        boolean isPotentialDM = false;

        // keep track of any chromosome with high ploidy as an indication of false DMs and/or under-clustering
        List<String> highPloidyChromosomes = Lists.newArrayList();

        int svsAboveThreshold = 0;
        double minPloidyAboveThreshold = 0;
        for(int i = 0; i < ploidyList.size(); ++i)
        {
            Double ploidy = ploidyList.get(i);

            if(ploidy < DOUBLE_MINUTE_PLOIDY_THRESHOLD)
                break;

            ++svsAboveThreshold;
            minPloidyAboveThreshold = ploidy;

            final SvVarData var = indexSvList.get(i);

            for(int be = SVI_START; be <= SVI_END; ++be)
            {
                boolean useStart = isStart(be);

                if(var.isNullBreakend() && !useStart)
                    continue;

                if(!highPloidyChromosomes.contains(var.chromosome(useStart)))
                    highPloidyChromosomes.add(var.chromosome(useStart));
            }

            // check vs next
            if(i == ploidyList.size() - 1)
            {
                LOGGER.debug(String.format("cluster(%s count=%d) DM highPloidyCount(%d chr=%d) currentSV(%s) ploidy(%.2f) with no others",
                        cluster.id(), cluster.getSvCount(), svsAboveThreshold, highPloidyChromosomes.size(), var.posId(), ploidy));
                isPotentialDM = true;
                break;
            }
            else
            {
                double nextPloidy = ploidyList.get(i+1);

                if(nextPloidy * DOUBLE_MINUTE_PLOIDY_GAP_RATIO < ploidy)
                {
                    LOGGER.debug(String.format("cluster(%s count=%d) DM highPloidyCount(%d chr=%d) currentSV(%s) ploidy(%.2f) vs next(%.3f)",
                            cluster.id(), cluster.getSvCount(), svsAboveThreshold, highPloidyChromosomes.size(),
                            indexSvList.get(i).posId(), ploidy, nextPloidy));
                    isPotentialDM = true;
                    break;
                }
            }
        }

        if(isPotentialDM)
        {
            // check for high ploidy in other variants on the relevant chromosomes
            boolean otherClustersHaveHighPloidy = false;

            for(final String chromsome : highPloidyChromosomes)
            {
                final List<SvBreakend> breakendList = chrBreakendMap.get(chromsome);

                if(breakendList == null)
                    continue;

                for(final SvBreakend breakend : breakendList)
                {
                    if(breakend.getSV().getCluster() == cluster)
                        continue;

                    if(breakend.getSV().getSvData().ploidy() * DOUBLE_MINUTE_PLOIDY_GAP_RATIO >= minPloidyAboveThreshold)
                    {
                        otherClustersHaveHighPloidy = true;
                        break;
                    }
                }

                if(otherClustersHaveHighPloidy)
                    break;
            }

            final String dmAnnotation = otherClustersHaveHighPloidy ? CLUSTER_ANNONTATION_DM + "_Unclear" : CLUSTER_ANNONTATION_DM;
            cluster.addAnnotation(dmAnnotation);
        }

        /*
        if(cluster.isResolved() || !cluster.isFullyChained() || cluster.getChains().get(0).getLinkCount() <= 2)
            return;

        if(!cluster.getChains().get(0).couldFormLoop())
            return;

        final SvChain chain = cluster.getChains().get(0);

        // check for high copy number within this loop
        boolean hasHighCN = false;
        double cnTotal = 0;
        double maxCN = 0;
        double ploidyTotal = 0;

        for(final SvVarData var : chain.getSvList())
        {
            ploidyTotal += var.getSvData().ploidy();
            cnTotal += (var.copyNumber(true) + var.copyNumber(false)) * 0.5;
            maxCN = max(maxCN, var.copyNumber(true));
            maxCN = max(maxCN, var.copyNumber(false));

            if(var.copyNumber(true) >= DOUBLE_MINUTE_COPY_NUMBER_THRESHOLD || var.copyNumber(false) >= DOUBLE_MINUTE_COPY_NUMBER_THRESHOLD)
            {
                hasHighCN = true;
            }
        }

        if(hasHighCN)
        {
            LOGGER.debug(String.format("sample(%s) cluster(%d) chain(%d) links(%d) closed loop, copyNumber(avg=%.1f max=%.2f) ploidy(%.1f)",
                    sampleId, cluster.id(), chain.id(), chain.getLinkCount(),
                    cnTotal/chain.getSvList().size(), maxCN, ploidyTotal/chain.getSvList().size()));

            cluster.addAnnotation(CLUSTER_ANNONTATION_DM);
        }
        */
    }

    public static void reportClusterArmSegments(final SvCluster cluster)
    {
        if (cluster.isResolved())
            return;

        if (cluster.getSvCount() < 6)
            return;

        // isSpecificCluster(cluster);

        final Map<String, List<SvBreakend>> chrBreakendMap = cluster.getChrBreakendMap();

        for (final Map.Entry<String, List<SvBreakend>> entry : chrBreakendMap.entrySet())
        {
            List<SvBreakend> breakendList = entry.getValue();

            if (breakendList.size() < 6)
                continue;

            // first establish the lowest copy number segments
            int bndCount = 0;
            boolean hasConsecutiveOrientations = false;
            double lowestCopyNumber = -1;

            List<SvArmCluster> localSegments = Lists.newArrayList();
            SvArmCluster armCuster = null;

            for (int i = 0; i < breakendList.size() - 1; ++i)
            {
                final SvBreakend breakend = breakendList.get(i);

                if(!hasConsecutiveOrientations && i < breakendList.size() - 1)
                {
                    hasConsecutiveOrientations = (breakend.orientation() != breakendList.get(i+1).orientation());
                }

                double lowCopyNumber = breakend.getCopyNumber(false);

                if (breakend.orientation() == 1 && (lowestCopyNumber == -1 || lowCopyNumber < lowestCopyNumber))
                {
                    // a new lowest segment, which will be the first original DSB
                    // all previous breakends will be put into the first arm cluster
                    lowestCopyNumber = lowCopyNumber;

                    localSegments.clear();

                    // add all previous breakends to the first arm cluster
                    armCuster = new SvArmCluster(localSegments.size(), cluster, breakend.chromosome(), breakend.arm());

                    for (int j = 0; j <= i; ++j)
                        armCuster.addBreakend(breakendList.get(j));

                    localSegments.add(armCuster);
                    armCuster = null;
                }
                else if (breakend.orientation() == 1 && copyNumbersEqual(lowestCopyNumber, lowCopyNumber))
                {
                    // end of a segment since next CN equals the low - add the last breakend
                    if(armCuster == null)
                    {
                        // probably an indication of failed clustering, so work around it
                        armCuster = new SvArmCluster(localSegments.size(), cluster, breakend.chromosome(), breakend.arm());
                        localSegments.add(armCuster);
                    }

                    armCuster.addBreakend(breakend);
                    armCuster = null;
                }
                else
                {
                    // either continuation of an existing segment or start of a new one
                    if (armCuster == null)
                    {
                        armCuster = new SvArmCluster(localSegments.size(), cluster, breakend.chromosome(), breakend.arm());
                        localSegments.add(armCuster);
                    }

                    armCuster.addBreakend(breakend);
                }

                if (breakend.getSV().type() == BND)
                    ++bndCount;
            }

            if (bndCount > 0 || !hasConsecutiveOrientations)
                continue;

            for (final SvArmCluster armCluster : localSegments)
            {
                List<SvBreakend> acBreakendList = armCluster.getBreakends();

                if(acBreakendList.size() < 4)
                    continue;

                int backwardCount = (int)acBreakendList.stream().filter(x -> x.orientation() == 1).count();
                int forwardCount = (int)acBreakendList.stream().filter(x -> x.orientation() == -1).count();

                // form into mutually exclusive pairs based on copy number change match
                List<SvBreakend> unlinkedBreakends = Lists.newArrayList(acBreakendList);
                List<SvLinkedPair> pairs = formPossibleLinkedPairsByShortest(unlinkedBreakends);

                LOGGER.debug("cluster({}) armCluster({} : {}_{}) count({} fwd={} bak={}) pairs({}) unlinked({})",
                        cluster.id(), armCluster.id(), armCluster.chromosome(), armCluster.arm(),
                        acBreakendList.size(), forwardCount, backwardCount, pairs.size(), unlinkedBreakends.size());
            }
        }
    }

    private static List<SvLinkedPair> formPossibleLinkedPairsByShortest(List<SvBreakend> unlinkedBreakends)
    {
        List<SvLinkedPair> pairs = Lists.newArrayList();

        while(!unlinkedBreakends.isEmpty())
        {
            boolean pairFound = false;

            for(int i = 0; i < unlinkedBreakends.size() - 2; ++i)
            {
                final SvBreakend be1 = unlinkedBreakends.get(i);
                final SvBreakend be2 = unlinkedBreakends.get(i+1);

                if(be1.orientation() != -1 || be2.orientation() != 1)
                    continue;

                // pair off these if the CN matches
                if (!copyNumbersEqual(be1.copyNumberChange(), be2.copyNumberChange()))
                    continue;

                SvLinkedPair pair = SvLinkedPair.from(be1, be2, LINK_TYPE_TI);
                pairs.add(pair);

                pairFound = true;
                unlinkedBreakends.remove(i+1); // higher index removed first
                unlinkedBreakends.remove(i);
                break;
            }

            if(!pairFound)
                break;
        }

        return pairs;
    }

    private static List<SvLinkedPair> formPossibleLinkedPairsConsecutively(List<SvBreakend> unlinkedBreakends)
    {
        List<SvLinkedPair> pairs = Lists.newArrayList();

        int j = 0;
        while(j < unlinkedBreakends.size())
        {
            final SvBreakend be1 = unlinkedBreakends.get(j);

            if(be1.orientation() == 1)
            {
                ++j;
                continue;
            }

            boolean linkFound = false;
            for(int k = j+1; k < unlinkedBreakends.size(); ++k)
            {
                final SvBreakend be2 = unlinkedBreakends.get(k);

                if (be2.orientation() == -1)
                    continue;

                // pair off these if the CN matches
                if (!copyNumbersEqual(be1.copyNumberChange(), be2.copyNumberChange()))
                    continue;

                SvLinkedPair pair = SvLinkedPair.from(be1, be2, LINK_TYPE_TI);
                pairs.add(pair);

                linkFound = true;
                unlinkedBreakends.remove(k); // higher index removed first
                unlinkedBreakends.remove(j);
                break;

            }

            if(linkFound)
                continue;
            else
                ++j;
        }

        return pairs;
    }

    public static void findChainRepeatedSegments(final String sampleId, final SvCluster cluster, final SvChain chain)
    {
        if(!chain.hasReplicatedSVs())
            return;

        List<SvVarData> replicatedSVs = Lists.newArrayList();

        final List<SvVarData> svList = chain.getSvList();

        for (int i = 0; i < svList.size(); ++i)
        {
            final SvVarData var1 = svList.get(i);

            if(replicatedSVs.contains(var1))
                continue;

            for (int j = i + 1; j < svList.size(); ++j)
            {
                final SvVarData var2 = svList.get(j);

                if (!var1.equals(var2, true))
                    continue;

                replicatedSVs.add(var1);

                // look for repeated sections forwards or backwards from this point
                List<SvVarData> forwardRepeats = getRepeatedSvSequence(svList, i, j, true);

                boolean forwardSequence = false;

                if(!forwardRepeats.isEmpty())
                {
                    forwardSequence = true;
                    replicatedSVs.addAll(forwardRepeats);
                }
                else
                {
                    forwardSequence = false;
                    forwardRepeats = getRepeatedSvSequence(svList, i, j, false);
                }

                if(!forwardRepeats.isEmpty())
                {
                    replicatedSVs.addAll(forwardRepeats);

                    forwardRepeats.set(0, var1);

                    String svIds = var1.id();
                    for(int k = 1; k < forwardRepeats.size(); ++k)
                        svIds += ";" + forwardRepeats.get(k).id();

                    if(forwardRepeats.size() >= 4)
                    {
                        LOGGER.debug("sample({}) cluster({}) chain({}) {} sequence of {} SVs starting at index({}:{}) SV({})",
                                sampleId, cluster.id(), chain.id(), forwardSequence ? "forward" : "reverse",
                                forwardRepeats.size(), i, j, var1.id());

                        // ClusterId,ChainId,SequenceCount,VarIds,MatchDirection
                        LOGGER.debug("CF_REPEAT_SEQ: {},{},{},{},{},{}",
                                sampleId, cluster.id(), chain.id(), forwardRepeats.size(), svIds, forwardSequence);
                    }

                    break;
                }

                // no sequence found
            }
        }
    }

}
