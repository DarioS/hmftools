package com.hartwig.hmftools.sig_analyser.buckets;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.GenericDataCollection;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class BaConfig
{
    // constraint constants - consider moving any allocation-related ones to config to make them visible
    public static double SAMPLE_ALLOCATED_PERCENT = 0.995;
    public static double SIG_SIMILAR_CSS = 0.90; // for sig and bucket group similarity
    public static double DOMINANT_CATEGORY_PERCENT = 0.7; // mark a group with a category if X% of samples in it have this attribute (eg cancer type, UV)
    public static double MAX_ELEVATED_PROB = 1e-12;
    public static double MIN_DISCOVERY_SAMPLE_COUNT = 0.0001; // % of cohort to consider a pair of samples similar (5K at 0.01% of 55M)
    public static double PERMITTED_PROB_NOISE = 1e-4;
    public static double MIN_GROUP_ALLOC_PERCENT = 0.10; // only allocate a sample to a group if it takes at this much of the elevated count
    public static double MIN_GROUP_ALLOC_PERCENT_LOWER = 0.1; // hard lower limit
    public static double SKIP_ALLOC_FACTOR = 2.0; // skip adding a sample to a group if another candidate not chosen to allocate X times as much
    public static int MAX_CANDIDATE_GROUPS = 1500; // in place for speed and memory considerations
    public static double MAX_NOISE_TO_SAMPLE_RATIO = 5; // per sample, the total potential noise across all buckets cannot exceeds this multiple of variant total
    public static double MAX_NOISE_ALLOC_PERCENT = 0.5; // per sample, the max vs total variants which can be allocated to noise
    public static double DEFAULT_SIG_RATIO_RANGE_PERCENT = 0.1; // if ratio ranges are used, this percent width can be applied
    public static double UNIQUE_SIG_CSS_THRESHOLD = 0.8;
    public static double UNIQUE_SIG_MIN_ALLOC_PERCENT = 0.3;
    public static double MAJOR_GROUP_ALLOC_PERC = 0.02;
    public static double MAJOR_GROUP_SAMPLE_PERC = 0.05;

    // configuration state
    public double HighCssThreshold; // CSS level for samples or groups to be consider similar
    public int MinBucketCountOverlap; // used for pairings of samples with reduced bucket overlap
    public int MaxProposedSigs;
    public int MutationalLoadCap;
    public int ApplyPredefinedSigCount; // how many loaded sigs to apply prior to discovery (optimisation)
    public int RunCount; //  number of iterations searching for potential bucket groups / sigs
    public int MinSampleAllocCount; // hard lower limit to allocate a sample to a group
    public int ExcessDiscoveryRunId; // run iteration from which to begin discovery using excess-unalloc method
    public boolean UseRatioRanges;
    public int UniqueDiscoveryRunId;
    public boolean UseBackgroundCounts; // whether to make a distinction between background and elevated counts
    public boolean ApplyNoise; // whether to factor Poisson noise into the sample counts and fits
    public boolean ThrottleDiscovery; // whether to allow discovery after each run
    public double GroupMergeScoreThreshold; // after discovery is complete, attempt to merge similar sigs if score exceeds this
    public String SpecificCancer;
    public String MsiFilter;
    public List<Integer> SampleWatchList;
    public int SpecificSampleId;

    // config strings
    public static String BA_PREDEFINED_SIGS = "ba_predefined_sigs_file";
    public static String BA_PREDEFINED_SIG_APPLY_COUNT = "ba_predefined_sig_apply_count";
    public static String BA_EXT_SAMPLE_DATA_FILE = "ba_ext_data_file";
    public static String BA_SAMPLE_CALC_DATA_FILE = "ba_sam_calc_data_file";

    private static String BA_USE_BACKGROUND_SIGS = "ba_use_background_sigs"; // true by default
    private static String BA_CSS_HIGH_THRESHOLD = "ba_css_high";
    private static String BA_MAX_PROPOSED_SIGS = "ba_max_proposed_sigs";
    private static String BA_CSS_SIG_THRESHOLD = "ba_css_proposed_sigs";
    private static String BA_SPECIFIC_CANCER = "ba_specific_cancer";
    private static String BA_MSI_FILTER = "ba_msi_filter";
    private static String BA_RUN_COUNT = "ba_run_count";
    private static String BA_MIN_SAM_ALLOC_COUNT = "ba_min_sample_alloc_count";
    private static String BA_MUT_LOAD_CAP = "ba_mut_load_cap";
    private static String BA_EXCESS_GRP_RUN_INDEX = "ba_excess_grp_run_index";
    private static String BA_MIN_BUCKET_COUNT_OVERLAP = "ba_min_bc_overlap";
    private static String BA_USE_RATIO_RANGES = "ba_use_ratio_ranges";
    private static String BA_MERGE_SIG_SCORE = "ba_merge_sig_score";


    public BaConfig()
    {
        HighCssThreshold = 0.995;
        MaxProposedSigs = 0;
        MutationalLoadCap = 0;
        ApplyPredefinedSigCount = 0;
        ExcessDiscoveryRunId = -1;
        MinBucketCountOverlap = 3;
        UseBackgroundCounts = true;
        ApplyNoise = true;
        SampleWatchList = Lists.newArrayList();
        SpecificSampleId = -1;
    }

    public void load(final CommandLine cmd)
    {
        HighCssThreshold = Double.parseDouble(cmd.getOptionValue(BA_CSS_HIGH_THRESHOLD, "0.995"));
        MaxProposedSigs = Integer.parseInt(cmd.getOptionValue(BA_MAX_PROPOSED_SIGS, "0"));
        MutationalLoadCap = Integer.parseInt(cmd.getOptionValue(BA_MUT_LOAD_CAP, "10000"));
        ApplyPredefinedSigCount = Integer.parseInt(cmd.getOptionValue(BA_PREDEFINED_SIG_APPLY_COUNT, "0"));
        MinSampleAllocCount = Integer.parseInt(cmd.getOptionValue(BA_MIN_SAM_ALLOC_COUNT, "1"));
        ExcessDiscoveryRunId = Integer.parseInt(cmd.getOptionValue(BA_EXCESS_GRP_RUN_INDEX, "-1"));
        MinBucketCountOverlap = Integer.parseInt(cmd.getOptionValue(BA_MIN_BUCKET_COUNT_OVERLAP, "3"));
        UseRatioRanges = cmd.hasOption(BA_USE_RATIO_RANGES);
        GroupMergeScoreThreshold = Double.parseDouble(cmd.getOptionValue(BA_MERGE_SIG_SCORE, "0"));

        UseBackgroundCounts = Boolean.parseBoolean(cmd.getOptionValue(BA_USE_BACKGROUND_SIGS, "true"));

        ExcessDiscoveryRunId = Integer.parseInt(cmd.getOptionValue(BA_EXCESS_GRP_RUN_INDEX, "-1"));
        UniqueDiscoveryRunId = -1;
        ThrottleDiscovery = false;

        RunCount = Integer.parseInt(cmd.getOptionValue(BA_RUN_COUNT, "25"));

        SpecificCancer = cmd.getOptionValue(BA_SPECIFIC_CANCER, "");
        MsiFilter = cmd.getOptionValue(BA_MSI_FILTER, "");
    }

    public boolean logSample(int sampleId)
    {
        return SampleWatchList.contains(sampleId);
    }

    public static void addCmdLineArgs(Options options)
    {
        options.addOption(BA_USE_BACKGROUND_SIGS, true, "Default true. Whether to calculate and use background sigs");
        options.addOption(BA_EXT_SAMPLE_DATA_FILE, true, "Sample external data");
        options.addOption(BA_EXT_SAMPLE_DATA_FILE, true, "Sample external data");
        options.addOption(BA_CSS_HIGH_THRESHOLD, true, "Cosine sim for high-match test");
        options.addOption(BA_CSS_SIG_THRESHOLD, true, "Cosine sim for comparing proposed sigs");
        options.addOption(BA_MAX_PROPOSED_SIGS, true, "Maximum number of bucket groups to turn into proposed sigs");
        options.addOption(BA_SAMPLE_CALC_DATA_FILE, true, "Optional: file containing computed data per sample");
        options.addOption(BA_SPECIFIC_CANCER, true, "Optional: Only process this cancer type");
        options.addOption(BA_RUN_COUNT, true, "Number of search iterations");
        options.addOption(BA_PREDEFINED_SIGS, true, "Predefined sigs to use during discovery");
        options.addOption(BA_PREDEFINED_SIG_APPLY_COUNT, true, "How many predefined sigs to apply prior to discovery");
        options.addOption(BA_MIN_SAM_ALLOC_COUNT, true, "Min count to allocate a sample to a group");
        options.addOption(BA_MUT_LOAD_CAP, true, "Mutational load cap used in background counts calc");
        options.addOption(BA_EXCESS_GRP_RUN_INDEX, true, "Run id for excess-unalloc group logic to kick in");
        options.addOption(BA_MIN_BUCKET_COUNT_OVERLAP, true, "Min buckets for candidate group discovery");
        options.addOption(BA_USE_RATIO_RANGES, false, "Allow a computed range around sig ratios");
        options.addOption(BA_MSI_FILTER, true, "Use 'Include' to only look at MSI samples, or 'Exclude' to exclude them");
        options.addOption(BA_MERGE_SIG_SCORE, true, "After discovery, merge similar sigs before final fit");
    }

}