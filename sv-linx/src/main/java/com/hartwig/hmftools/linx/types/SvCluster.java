package com.hartwig.hmftools.linx.types;

import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import static com.hartwig.hmftools.common.utils.Strings.appendStr;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.BND;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INF;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.SGL;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.typeAsInt;
import static com.hartwig.hmftools.linx.LinxConfig.LNX_LOGGER;
import static com.hartwig.hmftools.linx.LinxOutput.SUBSET_DELIM;
import static com.hartwig.hmftools.linx.LinxOutput.SUBSET_SPLIT;
import static com.hartwig.hmftools.linx.analysis.SimpleClustering.hasLowJcn;
import static com.hartwig.hmftools.linx.analysis.ClusterClassification.isSimpleSingleSV;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.addSvToChrBreakendMap;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.calcConsistency;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.getSvTypesStr;
import static com.hartwig.hmftools.linx.chaining.ChainMetrics.extractChainMetrics;
import static com.hartwig.hmftools.linx.types.ResolvedType.LINE;
import static com.hartwig.hmftools.linx.types.ResolvedType.NONE;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.isStart;
import static com.hartwig.hmftools.linx.types.LinxConstants.SHORT_TI_LENGTH;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantType;
import com.hartwig.hmftools.linx.analysis.ClusterMetrics;
import com.hartwig.hmftools.linx.analysis.ClusteringReason;
import com.hartwig.hmftools.linx.analysis.ClusterClassification;
import com.hartwig.hmftools.linx.chaining.ChainMetrics;
import com.hartwig.hmftools.linx.chaining.SvChain;
import com.hartwig.hmftools.linx.cn.LohEvent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SvCluster
{
    private int mId;

    private int mConsistencyCount;
    private boolean mIsConsistent; // follows from telomere to centromere to telomore
    private String mDesc;
    private final int[] mTypeCounts;

    private boolean mIsResolved; // if true, then protect from subsequent merging
    private ResolvedType mResolvedType;

    private final List<String> mAnnotationList;

    private final List<SvVarData> mSVs;
    private final List<SvChain> mChains; // pairs of SVs linked into chains
    private final List<LinkedPair> mLinkedPairs; // final set after chaining and linking
    private final List<LinkedPair> mAssemblyLinkedPairs; // TIs found during assembly
    private final List<ArmGroup> mArmGroups; // organise SVs into a group per chromosomal arm
    private final List<ArmCluster> mArmClusters; // clusters of proximate SVs on an arm, currently only used for annotations
    private final Map<String, List<SvBreakend>> mChrBreakendMap; // note: does not contain replicated SVs
    private final List<SvVarData> mUnchainedSVs; // includes replicated SVs
    private final List<LohEvent> mLohEvents;
    private String mClusteringReasons;

    private boolean mRequiresReplication;

    // cached lists of identified special cases
    private final List<SvVarData> mLongDelDups;
    private final List<SvVarData> mFoldbacks;
    private final List<SvVarData> mInversions;
    private final List<SvVarData> mDoubleMinuteSVs;
    private final List<SvChain> mDoubleMinuteChains;
    private boolean mHasLinkingLineElements;
    private boolean mIsSubclonal;
    private boolean mRequiresRecalc;

    // state for SVs which link different arms or chromosomes
    private boolean mRecalcRemoteSVStatus;
    private final List<SvVarData> mShortTIRemoteSVs;
    private final List<SvVarData> mUnlinkedRemoteSVs;

    private double mMinJcn;
    private double mMaxJcn;

    private ClusterMetrics mMetrics;

    public static String CLUSTER_ANNOT_DM = "DM";
    public static String CLUSTER_ANNOT_BFB = "BFB";
    public static String CLUSTER_ANNOT_REP_REPAIR = "REP_REPAIR";
    public static String CLUSTER_ANNOT_SHATTERING = "CHROMO_THRIP";

    public SvCluster(final int clusterId)
    {
        mId = clusterId;
        mSVs = Lists.newArrayList();
        mArmGroups = Lists.newArrayList();
        mArmClusters = Lists.newArrayList();
        mTypeCounts = new int[StructuralVariantType.values().length];

        // annotation info
        mDesc = "";
        mConsistencyCount = 0;
        mIsConsistent = false;
        mIsResolved = false;
        mResolvedType = NONE;
        mRequiresRecalc = true;
        mAnnotationList = Lists.newArrayList();
        mChrBreakendMap = Maps.newHashMap();

        // chain data
        mLinkedPairs = Lists.newArrayList();
        mAssemblyLinkedPairs= Lists.newArrayList();
        mChains = Lists.newArrayList();
        mUnchainedSVs = Lists.newArrayList();
        mLohEvents = Lists.newArrayList();

        mLongDelDups = Lists.newArrayList();
        mFoldbacks = Lists.newArrayList();
        mDoubleMinuteSVs = Lists.newArrayList();
        mDoubleMinuteChains = Lists.newArrayList();
        mHasLinkingLineElements = false;
        mInversions = Lists.newArrayList();
        mShortTIRemoteSVs = Lists.newArrayList();
        mUnlinkedRemoteSVs = Lists.newArrayList();
        mRecalcRemoteSVStatus = false;
        mIsSubclonal = false;

        mRequiresReplication = false;

        mMinJcn = 0;
        mMaxJcn = 0;
        mClusteringReasons = "";

        mMetrics = new ClusterMetrics();
    }

    public int id() { return mId; }

    public int getSvCount() { return mSVs.size(); }

    public final String getDesc() { return mDesc; }

    public final List<SvVarData> getSVs() { return mSVs; }
    public final SvVarData getSV(int index) { return index < mSVs.size() ? mSVs.get(index) : null; }

    public void addVariant(final SvVarData var)
    {
        if(mSVs.contains(var))
        {
            LNX_LOGGER.error("cluster({}) attempting to add SV({}) again", mId, var.id());
            return;
        }

        mSVs.add(var);
        mRequiresRecalc = true;

        mAnnotationList.clear();
        mDoubleMinuteSVs.clear();
        mDoubleMinuteChains.clear();

        var.setCluster(this);

        mUnchainedSVs.add(var);

        if(!mHasLinkingLineElements)
        {
            mIsResolved = false;
            mResolvedType = NONE;
        }

        ++mTypeCounts[typeAsInt(var.type())];

        if (var.type() == BND || var.isCrossArm())
            mRecalcRemoteSVStatus = true;

        addSvToChrBreakendMap(var, mChrBreakendMap);

        // keep track of all SVs in their respective chromosomal arms
        for (int be = SE_START; be <= SE_END; ++be)
        {
            if (be == SE_END && var.isSglBreakend())
                continue;

            if (be == SE_END && var.isLocal())
                continue;

            boolean useStart = isStart(be);

            boolean groupFound = false;
            for (ArmGroup armGroup : mArmGroups)
            {
                if (armGroup.chromosome().equals(var.chromosome(useStart)) && armGroup.arm().equals(var.arm(useStart)))
                {
                    armGroup.addVariant(var);
                    groupFound = true;
                    break;
                }
            }

            if (!groupFound)
            {
                ArmGroup armGroup = new ArmGroup(var.chromosome(useStart), var.arm(useStart));
                armGroup.addVariant(var);
                mArmGroups.add(armGroup);
            }
        }
    }

    public void removeVariant(final SvVarData var)
    {
        mSVs.remove(var);
        mRequiresRecalc = true;

        mDoubleMinuteSVs.remove(var);
        mLongDelDups.remove(var);
        mFoldbacks.remove(var);
        mLongDelDups.remove(var);
        mInversions.remove(var);

        --mTypeCounts[typeAsInt(var.type())];

        for (int be = SE_START; be <= SE_END; ++be)
        {
            SvBreakend breakend = var.getBreakend(be);

            if(breakend == null)
                continue;

            for (ArmGroup armGroup : mArmGroups)
            {
                if (armGroup.chromosome().equals(breakend.chromosome()) && armGroup.arm().equals(breakend.arm()))
                {
                    armGroup.getBreakends().remove(breakend);
                    armGroup.getSVs().remove(var);
                    break;
                }
            }

            final List<SvBreakend> breakendList = mChrBreakendMap.get(breakend.chromosome());
            breakendList.remove(breakend);
        }
    }

    public List<ArmGroup> getArmGroups() { return mArmGroups; }
    public Map<String, List<SvBreakend>> getChrBreakendMap() { return mChrBreakendMap; }

    public boolean requiresReplication() { return mRequiresReplication; }

    public void setRequiresReplication()
    {
        mRequiresReplication = true;
    }

    public void addLohEvent(final LohEvent lohEvent)
    {
        if(!mLohEvents.contains(lohEvent))
            mLohEvents.add(lohEvent);
    }

    public final List<LohEvent> getLohEvents() { return mLohEvents; }

    public List<SvChain> getChains() { return mChains; }

    public void addChain(SvChain chain, boolean resetId)
    {
        if(resetId)
            chain.setId(mChains.size());

        mChains.add(chain);

        for(SvVarData var : chain.getSvList())
        {
            mUnchainedSVs.remove(var);
        }
    }

    public boolean isFullyChained(boolean requireConsistency)
    {
        if(!mUnchainedSVs.isEmpty() || mChains.isEmpty())
            return false;

        if(requireConsistency)
        {
            for (final SvChain chain : mChains)
            {
                if (!chain.isConsistent(requireConsistency))
                    return false;
            }
        }

        return true;
    }

    public void dissolveLinksAndChains()
    {
        mUnchainedSVs.clear();
        mUnchainedSVs.addAll(mSVs);
        mChains.clear();
    }

    public List<SvVarData> getUnlinkedSVs() { return mUnchainedSVs; }

    public final List<LinkedPair> getLinkedPairs() { return mLinkedPairs; }
    public final List<LinkedPair> getAssemblyLinkedPairs() { return mAssemblyLinkedPairs; }
    public void setAssemblyLinkedPairs(final List<LinkedPair> pairs)
    {
        mAssemblyLinkedPairs.clear();
        mAssemblyLinkedPairs.addAll(pairs);
    }

    public void mergeOtherCluster(final SvCluster other)
    {
        mergeOtherCluster(other, true);
    }

    public void mergeOtherCluster(final SvCluster other, boolean logDetails)
    {
        if(other == this || other.id() == id())
        {
            LNX_LOGGER.error("attempting to merge same cluster({})", id());
            return;
        }

        // just add the other cluster's variants - no preservation of links or chains
        if(other.getSvCount() > getSvCount())
        {
            if(logDetails)
            {
                LNX_LOGGER.debug("cluster({} svs={}) merges in other cluster({} svs={}) and adopts new ID",
                        id(), getSvCount(), other.id(), other.getSvCount());
            }

            // maintain the id of the larger group
            mId = other.id();
        }
        else if(logDetails)
        {
            LNX_LOGGER.debug("cluster({} svs={}) merges in other cluster({} svs={})",
                    id(), getSvCount(), other.id(), other.getSvCount());
        }

        addVariantLists(other);

        other.getLohEvents().stream().forEach(this::addLohEvent);

        String[] clusterReasons = other.getClusteringReasons().split(SUBSET_SPLIT);
        for(int i = 0; i < clusterReasons.length; ++i)
        {
            if(clusterReasons[i].isEmpty())
                break;

            addClusterReason(ClusteringReason.valueOf(clusterReasons[i]));
        }

        if(other.hasLinkingLineElements())
        {
            // retain status as a LINE cluster
            markAsLine();
        }

        // merge in fully assembled chains only
        other.getChains().stream()
                .filter(x -> x.getAssemblyLinkCount() == x.getLinkCount())
                .forEach(x -> mChains.add(x));
    }

    private void addVariantLists(final SvCluster other)
    {
        for(final SvVarData var : other.getSVs())
        {
            addVariant(var);
        }

        mAssemblyLinkedPairs.addAll(other.getAssemblyLinkedPairs());

        if(other.requiresReplication())
        {
            mRequiresReplication = true;
        }

        mInversions.addAll(other.getInversions());
        mFoldbacks.addAll(other.getFoldbacks());
        mLongDelDups.addAll(other.getLongDelDups());
    }

    public void addClusterReason(final ClusteringReason reason)
    {
        if(!mClusteringReasons.contains(reason.toString()))
        {
            mClusteringReasons = appendStr(mClusteringReasons, reason.toString(), SUBSET_DELIM);
        }
    }

    public final String getClusteringReasons() { return mClusteringReasons; }
    public boolean hasClusterReason(ClusteringReason reason) { return mClusteringReasons.contains(reason.toString()); }

    public boolean isConsistent()
    {
        updateClusterDetails();
        return mIsConsistent;
    }

    public int getConsistencyCount()
    {
        updateClusterDetails();
        return mConsistencyCount;
    }

    public void setResolved(boolean toggle, final ResolvedType type)
    {
        mIsResolved = toggle;
        mResolvedType = type;

        if(mDesc.isEmpty())
            mDesc = getClusterTypesAsString();
    }

    public boolean isResolved() { return mIsResolved; }
    public final ResolvedType getResolvedType() { return mResolvedType; }

    public boolean isSyntheticType()
    {
        if(mSVs.size() == 1)
            return false;

        return ClusterClassification.isSyntheticType(this);
    }

    public void updateClusterDetails()
    {
        if(!mRequiresRecalc)
            return;

        mConsistencyCount = calcConsistency(mSVs);

        mIsConsistent = (mConsistencyCount == 0);

        mDesc = getClusterTypesAsString();

        setMinMaxCNChange();

        resetBreakendMapIndices();

        mRequiresRecalc = false;
    }

    public void logDetails()
    {
        updateClusterDetails();

        if(isSimpleSingleSV(this))
        {
            LNX_LOGGER.debug("cluster({}) simple svCount({}) desc({}) armCount({}) consistency({}) ",
                    id(), getSvCount(), getDesc(), getArmCount(), getConsistencyCount());
        }
        else
        {
            double chainedPerc = 1 - (getUnlinkedSVs().size()/mSVs.size());

            String otherInfo = "";

            if(!mFoldbacks.isEmpty())
            {
                otherInfo += String.format("foldbacks=%d", mFoldbacks.size());
            }

            if(!mLongDelDups.isEmpty())
            {
                otherInfo = appendStr(otherInfo, String.format("longDelDup=%d", mLongDelDups.size()), ' ');
            }

            if(!mInversions.isEmpty())
            {
                otherInfo = appendStr(otherInfo, String.format("inv=%d", mInversions.size()), ' ');
            }

            if(!mDoubleMinuteSVs.isEmpty())
            {
                otherInfo = appendStr(otherInfo, "DM", ' ');
            }

            if(!mShortTIRemoteSVs.isEmpty())
            {
                otherInfo = appendStr(otherInfo, String.format("sti-bnd=%d", mShortTIRemoteSVs.size()), ' ');
            }

            if(!mUnlinkedRemoteSVs.isEmpty())
            {
                otherInfo = appendStr(otherInfo, String.format("unlnk-bnd=%d", mUnlinkedRemoteSVs.size()), ' ');
            }

            LNX_LOGGER.debug(String.format("cluster(%d) complex SVs(%d) desc(%s res=%s) arms(%d) consis(%d) chains(%d perc=%.2f) replic(%s) %s",
                    id(), getSvCount(), getDesc(), mResolvedType, getArmCount(), getConsistencyCount(),
                    mChains.size(), chainedPerc, mRequiresReplication, otherInfo));
        }
    }

    public int getArmCount() { return mArmGroups.size(); }

    public final String getClusterTypesAsString()
    {
        if(mSVs.size() == 1)
        {
            return mSVs.get(0).typeStr();
        }

        return getSvTypesStr(mTypeCounts);
    }

    public int getSglBreakendCount()
    {
        return mTypeCounts[typeAsInt(SGL)] + mTypeCounts[typeAsInt(INF)];
    }

    public int getTypeCount(StructuralVariantType type)
    {
        return mTypeCounts[typeAsInt(type)];
    }

    public final List<SvVarData> getLongDelDups() { return mLongDelDups; }
    public final List<SvVarData> getFoldbacks() { return mFoldbacks; }
    public final List<SvVarData> getInversions() { return mInversions; }

    public void registerFoldback(final SvVarData var)
    {
        if(!mFoldbacks.contains(var))
            mFoldbacks.add(var);
    }

    public void deregisterFoldback(final SvVarData var)
    {
        if(mFoldbacks.contains(var))
            mFoldbacks.remove(var);
    }

    public void registerInversion(final SvVarData var)
    {
        if(!mInversions.contains(var))
            mInversions.add(var);
    }

    public void registerLongDelDup(final SvVarData var)
    {
        if(!mLongDelDups.contains(var))
            mLongDelDups.add(var);
    }

    public final List<SvVarData> getDoubleMinuteSVs() { return mDoubleMinuteSVs; }
    public final List<SvChain> getDoubleMinuteChains() { return mDoubleMinuteChains; }

    public void setDoubleMinuteData(final List<SvVarData> svList, final List<SvChain> chains)
    {
        mDoubleMinuteSVs.clear();
        mDoubleMinuteSVs.addAll(svList);
        mDoubleMinuteChains.clear();
        mDoubleMinuteChains.addAll(chains);
    }

    public void clearDoubleMinuteData()
    {
        mDoubleMinuteSVs.clear();
        mDoubleMinuteChains.clear();
        mAnnotationList.remove(CLUSTER_ANNOT_DM);
    }

    public void markAsLine()
    {
        mHasLinkingLineElements = true;
        setResolved(true, LINE);
    }

    public boolean hasLinkingLineElements() { return mHasLinkingLineElements; }

    public void markSubclonal()
    {
        int lowCNChangeSupportCount = (int)mSVs.stream().filter(x -> hasLowJcn(x)).count();
        mIsSubclonal = lowCNChangeSupportCount / (double)mSVs.size() > 0.5;
    }

    public boolean isSubclonal() { return mIsSubclonal; }

    public final List<SvVarData> getUnlinkedRemoteSVs() { return mUnlinkedRemoteSVs; }

    public void setArmLinks()
    {
        if(!mRecalcRemoteSVStatus)
            return;

        // keep track of BND which are or aren't candidates for links between arms
        mShortTIRemoteSVs.clear();
        mUnlinkedRemoteSVs.clear();

        for (final SvChain chain : mChains)
        {
            // any pair of remote SVs which don't form a short TI are fair game
            for (final LinkedPair pair : chain.getLinkedPairs())
            {
                if (pair.first().isCrossArm() && pair.second().isCrossArm() && pair.length() <= SHORT_TI_LENGTH)
                {
                    if (!mShortTIRemoteSVs.contains(pair.first()))
                        mShortTIRemoteSVs.add(pair.first());

                    if (!mShortTIRemoteSVs.contains(pair.second()))
                        mShortTIRemoteSVs.add(pair.second());
                }
            }
        }

        mUnlinkedRemoteSVs.clear();
        mUnlinkedRemoteSVs.addAll(mSVs.stream()
                .filter(x -> x.isCrossArm())
                .filter(x -> !x.inLineElement())
                .filter(x -> !mShortTIRemoteSVs.contains(x))
                .collect(Collectors.toList()));

        mRecalcRemoteSVStatus = false;
    }

    private void setMinMaxCNChange()
    {
        if(mSVs.size() == 1)
        {
            mMinJcn = mMaxJcn = mSVs.get(0).jcn();
            return;
        }

        // establish the high and low ploidy from all SVs
        mMinJcn = -1;
        mMaxJcn = 0;

        int svCalcJcnCount = 0;
        Map<Integer,Integer> jcnFrequency = Maps.newHashMap();

        double tightestMinJcn = 0;
        double tightestMaxJcn = -1;
        int countHalfToOneJcn = 0;
        double minSvJcn = -1;
        int maxAssembledMultiple = 1; // the highest multiple of a breakend linked to other assembled breakends

        for (final SvVarData var : mSVs)
        {
            int svJcn = var.getImpliedJcn();
            maxAssembledMultiple = max(maxAssembledMultiple, var.getMaxAssembledBreakend());

            if (mMinJcn < 0 || svJcn < mMinJcn)
            {
                mMinJcn = svJcn;
                minSvJcn = svJcn;
            }

            mMaxJcn = max(mMaxJcn, svJcn);

            if(var.hasCalculatedJcn())
            {
                ++svCalcJcnCount;

                int minJcnInt = (int)ceil(var.jcnMin());
                int maxJcnInt = (int)floor(var.jcnMax());
                maxJcnInt = max(minJcnInt, maxJcnInt);

                if(tightestMaxJcn == -1 || var.jcnMax() < tightestMaxJcn)
                    tightestMaxJcn = var.jcnMax();

                tightestMinJcn = max(var.jcnMin(), tightestMinJcn);

                if(var.jcnMin() < 1 && var.jcnMax() > 0.5)
                    ++countHalfToOneJcn;

                for(int i = minJcnInt; i <= maxJcnInt; ++i)
                {
                    Integer svCount = jcnFrequency.get(i);
                    if(svCount == null)
                        jcnFrequency.put(i, 1);
                    else
                        jcnFrequency.put(i, svCount+1);
                }
            }
        }

        if(svCalcJcnCount > 0)
        {
            mMinJcn = -1;
            mMaxJcn = 0;

            for (Map.Entry<Integer, Integer> entry : jcnFrequency.entrySet())
            {
                int ploidy = entry.getKey();
                int svCount = entry.getValue();

                if (svCount == svCalcJcnCount)
                {
                    // all SVs can settle on the same ploidy value, so take this
                    mMaxJcn = ploidy;
                    mMinJcn = ploidy;
                    break;
                }

                if (ploidy > 0 && (mMinJcn < 0 || ploidy < mMinJcn))
                     mMinJcn = ploidy;

                mMinJcn = max(mMinJcn, minSvJcn);
                mMaxJcn = max(mMaxJcn, ploidy);
            }

            if(mMinJcn < mMaxJcn && maxAssembledMultiple == 1)
            {
                if (tightestMaxJcn > tightestMinJcn && tightestMaxJcn - tightestMinJcn < 1)
                {
                    // if all SVs cover the same value but it's not an integer, still consider them uniform
                    mMinJcn = 1;
                    mMaxJcn = 1;
                }
                else if (countHalfToOneJcn == svCalcJcnCount)
                {
                    mMinJcn = 1;
                    mMaxJcn = 1;
                }
            }
        }

        // correct for the ploidy ratios implied from the assembled links
        if(maxAssembledMultiple > 1)
        {
            mMaxJcn = max(mMaxJcn, maxAssembledMultiple * mMinJcn);
        }
    }

    public double getMaxJcn() { return mMaxJcn; }
    public double getMinJcn() { return mMinJcn; }

    public boolean hasVariedJcn()
    {
        if(mRequiresRecalc)
            updateClusterDetails();

        if(mSVs.size() == 1)
            return false;

        return (mMaxJcn > mMinJcn && mMinJcn >= 0);
    }

    private void resetBreakendMapIndices()
    {
        for (Map.Entry<String, List<SvBreakend>> entry : mChrBreakendMap.entrySet())
        {
            List<SvBreakend> breakendList = entry.getValue();

            for (int i = 0; i < breakendList.size(); ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                breakend.setClusterChrPosIndex(i);
            }
        }
    }

    public void cacheLinkedPairs()
    {
        // moves assembly and unique inferred linked pairs which are used in chains to a set of 'final' linked pairs
        mLinkedPairs.clear();

        for (final SvChain chain : mChains)
        {
            for (final LinkedPair pair : chain.getLinkedPairs())
            {
                if(mLinkedPairs.stream().anyMatch(x -> x.matches(pair)))
                    continue;

                mLinkedPairs.add(pair);
                pair.first().addLinkedPair(pair, pair.firstLinkOnStart());
                pair.second().addLinkedPair(pair, pair.secondLinkOnStart());
            }
        }
    }

    public final SvChain findChain(final SvVarData var)
    {
        for(final SvChain chain : mChains)
        {
            if(chain.getSvIndex(var) >= 0)
                return chain;
        }

        return null;
    }

    public final List<SvChain> findChains(final SvVarData var)
    {
        return mChains.stream().filter(x -> x.hasSV(var)).collect(Collectors.toList());
    }

    public final SvChain findSameChainForSVs(SvVarData var1, SvVarData var2)
    {
        List<SvChain> chains1 = findChains(var1);
        List<SvChain> chains2 = findChains(var2);

        for(SvChain chain1 : chains1)
        {
            for(SvChain chain2 : chains2)
            {
                if(chain1 == chain2)
                    return chain1;
            }
        }

        return null;
    }

    public int getChainId(final SvVarData var)
    {
        final SvChain chain = findChain(var);

        if(chain != null)
            return chain.id();

        // otherwise set an id based on index in the unchained variants list
        for(int i = 0; i < mUnchainedSVs.size(); ++i)
        {
            final SvVarData unchainedSv = mUnchainedSVs.get(i);

            if(unchainedSv == var)
                return mChains.size() + i + 1;
        }

        return var.id();
    }

    public final List<ArmCluster> getArmClusters() { return mArmClusters; }
    public ArmCluster findArmCluster(final SvBreakend breakend) { return ArmCluster.findArmCluster(this, breakend); }

    public ClusterMetrics getMetrics() { return mMetrics; }

    public ChainMetrics getLinkMetrics()
    {
        ChainMetrics chainMetrics = new ChainMetrics();
        mChains.stream().forEach(x -> chainMetrics.add(extractChainMetrics(x)));
        return chainMetrics;
    }

    public final List<String> getAnnotationList() { return mAnnotationList; }

    public final void addAnnotation(final String annotation)
    {
        if(mAnnotationList.contains(annotation))
            return;

        mAnnotationList.add(annotation);
    }

    public boolean hasAnnotation(final String annotation) { return mAnnotationList.contains(annotation); }

    public String getAnnotations() { return mAnnotationList.stream().collect (Collectors.joining (SUBSET_SPLIT)); }

    public void determineRequiresReplication()
    {
        if(!hasVariedJcn() && !requiresReplication())
            return;

        // look for varying JCN amongst the SVs in this cluster as indication of replicated breakends to guide chaining
        double clusterMinJcn = getMinJcn();

        if(clusterMinJcn <= 0)
        {
            LNX_LOGGER.debug("cluster({}) warning: invalid JCN variation(min={} max={})", mId, clusterMinJcn, mMaxJcn);
            return;
        }

        for(SvVarData var : mSVs)
        {
            int svJcn = var.getImpliedJcn();
            int maxAssemblyBreakends = var.getMaxAssembledBreakend();

            int svMultiple = (int)round(svJcn / clusterMinJcn);

            if(maxAssemblyBreakends > 1)
                svMultiple = max(svMultiple, maxAssemblyBreakends);

            if(svMultiple <= 1)
                continue;

            LNX_LOGGER.debug("cluster({}) SV({}) JCN multiple({}, ploidy({} vs min={})",
                    mId, var.posId(), svMultiple, svJcn, clusterMinJcn);

            if(!requiresReplication())
                setRequiresReplication();
        }
    }

    public String toString()
    {
        return String.format("%d: SVs(%d) res(%s) desc(%s) chains(%d)", mId, mSVs.size(), mResolvedType, mDesc, mChains.size());
    }

}
