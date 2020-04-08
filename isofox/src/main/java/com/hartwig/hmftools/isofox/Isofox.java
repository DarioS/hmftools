package com.hartwig.hmftools.isofox;

import static java.lang.Math.max;

import static com.hartwig.hmftools.common.sigs.DataUtils.convertToPercentages;
import static com.hartwig.hmftools.common.sigs.DataUtils.copyVector;
import static com.hartwig.hmftools.isofox.ChromosomeGeneTask.PERF_FIT;
import static com.hartwig.hmftools.isofox.ChromosomeGeneTask.PERF_FRAG_LENGTH;
import static com.hartwig.hmftools.isofox.IsofoxConfig.GENE_TRANSCRIPTS_DIR;
import static com.hartwig.hmftools.isofox.IsofoxConfig.LOG_DEBUG;
import static com.hartwig.hmftools.isofox.IsofoxConfig.ISF_LOGGER;
import static com.hartwig.hmftools.isofox.IsofoxConfig.LOG_LEVEL;
import static com.hartwig.hmftools.isofox.IsofoxConfig.createCmdLineOptions;
import static com.hartwig.hmftools.isofox.IsofoxConfig.validConfigPaths;
import static com.hartwig.hmftools.isofox.TaskType.APPLY_GC_ADJUSTMENT;
import static com.hartwig.hmftools.isofox.TaskType.FRAGMENT_LENGTHS;
import static com.hartwig.hmftools.isofox.TaskType.TRANSCRIPT_COUNTS;
import static com.hartwig.hmftools.isofox.TaskType.GENERATE_TRANSCRIPT_GC_COUNTS;
import static com.hartwig.hmftools.isofox.adjusts.FragmentSizeCalcs.setConfigFragmentLengthData;
import static com.hartwig.hmftools.isofox.exp_rates.ExpectedTransRates.calcTotalTranscriptExpression;
import static com.hartwig.hmftools.isofox.exp_rates.ExpectedTransRates.setTranscriptsPerMillion;
import static com.hartwig.hmftools.isofox.adjusts.GcRatioCounts.writeReadGcRatioCounts;
import static com.hartwig.hmftools.isofox.results.SummaryStats.createSummaryStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.utils.PerformanceCounter;
import com.hartwig.hmftools.common.utils.version.VersionInfo;
import com.hartwig.hmftools.common.ensemblcache.EnsemblGeneData;
import com.hartwig.hmftools.isofox.adjusts.FragmentSizeCalcs;
import com.hartwig.hmftools.isofox.exp_rates.ExpectedCountsCache;
import com.hartwig.hmftools.isofox.adjusts.GcRatioCounts;
import com.hartwig.hmftools.isofox.adjusts.GcTranscriptCalculator;
import com.hartwig.hmftools.isofox.results.ResultsWriter;
import com.hartwig.hmftools.isofox.results.SummaryStats;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;

public class Isofox
{
    private final IsofoxConfig mConfig;
    private final ResultsWriter mResultsWriter;
    private final EnsemblDataCache mGeneTransCache;
    private final ExpectedCountsCache mExpectedCountsCache;
    private final GcTranscriptCalculator mGcTranscriptCalcs;

    private final List<int[]> mFragmentLengthDistribution;

    private boolean mIsValid;

    public Isofox(final IsofoxConfig config, final CommandLine cmd)
    {
        mConfig = config;

        mResultsWriter = new ResultsWriter(mConfig);

        mGeneTransCache = new EnsemblDataCache(cmd.getOptionValue(GENE_TRANSCRIPTS_DIR), RefGenomeVersion.HG37);

        if(!mConfig.RestrictedGeneIds.isEmpty())
        {
            mGeneTransCache.setRestrictedGeneIdList(mConfig.RestrictedGeneIds);
        }

        mGeneTransCache.setRequiredData(true, false, false, mConfig.CanonicalTranscriptOnly);
        mGeneTransCache.load(false);

        mExpectedCountsCache = mConfig.ExpCountsFile != null || mConfig.ApplyGcBiasAdjust ? new ExpectedCountsCache(mConfig) : null;

        mGcTranscriptCalcs = mConfig.WriteExpectedGcRatios || mConfig.ApplyGcBiasAdjust ?
                new GcTranscriptCalculator(mConfig, mGeneTransCache) : null;

        mFragmentLengthDistribution = Lists.newArrayList();
        mIsValid = true;
    }

    public boolean runAnalysis()
    {
        ISF_LOGGER.info("sample({}) running RNA expression analysis", mConfig.SampleId);

        // allocate work at the chromosome level
        List<ChromosomeGeneTask> chrTasks = Lists.newArrayList();

        for(Map.Entry<String,List<EnsemblGeneData>> entry : mGeneTransCache.getChrGeneDataMap().entrySet())
        {
            final List<EnsemblGeneData> geneDataList = entry.getValue();

            final String chromosome = entry.getKey();

            if (mConfig.skipChromosome(chromosome) || geneDataList.isEmpty())
                continue;

            ChromosomeGeneTask chrGeneTask = new ChromosomeGeneTask(
                    mConfig, chromosome, geneDataList, mGeneTransCache, mResultsWriter, mExpectedCountsCache, mGcTranscriptCalcs);

            chrTasks.add(chrGeneTask);
        }

        if(mConfig.requireFragmentLengthCalcs())
        {
            calcFragmentLengths(chrTasks);

            if(mConfig.WriteFragmentLengthsOnly)
            {
                mResultsWriter.close();
                return true;
            }
        }

        if(mConfig.WriteExpectedGcRatios)
        {
            generateGcRatios(chrTasks);
            mGcTranscriptCalcs.close();
            return true;
        }

        boolean validExecution = executeChromosomeTask(chrTasks, TRANSCRIPT_COUNTS);

        if(!validExecution)
            return false;

        if(!mConfig.generateExpectedDataOnly())
        {
            int totalReadsProcessed = chrTasks.stream().mapToInt(x -> x.getFragmentAllocator().totalReadCount()).sum();
            int totalDuplicateReads = chrTasks.stream().mapToInt(x -> x.getFragmentAllocator().duplicateReadCount()).sum();
            ISF_LOGGER.info("read {} total BAM records", totalReadsProcessed);

            int totalFragCount = chrTasks.stream().mapToInt(x -> x.getTotalFragmentCount()).sum();
            int enrichedGeneFragCount = chrTasks.stream().mapToInt(x -> x.getEnrichedGenesFragmentCount()).sum();

            GcRatioCounts nonEnrichedGcRatioCounts = new GcRatioCounts();
            chrTasks.forEach(x -> nonEnrichedGcRatioCounts.mergeRatioCounts(x.getNonEnrichedGcRatioCounts().getCounts()));
            double medianGCRatio = nonEnrichedGcRatioCounts.getPercentileRatio(0.5);

            if(mConfig.ApplyGcBiasAdjust)
            {
                applyGcAdjustments(chrTasks, nonEnrichedGcRatioCounts);
            }

            final SummaryStats summaryStats = createSummaryStats(
                    totalFragCount, enrichedGeneFragCount, totalDuplicateReads,
                    medianGCRatio, mFragmentLengthDistribution, mConfig.ReadLength);

            mResultsWriter.writeSummaryStats(summaryStats);

            if(mConfig.WriteGcData)
            {
                GcRatioCounts combinedGcRatioCounts = new GcRatioCounts();
                chrTasks.forEach(x -> combinedGcRatioCounts.mergeRatioCounts(x.getFragmentAllocator().getGcRatioCounts().getCounts()));

                writeReadGcRatioCounts(mResultsWriter.getReadGcRatioWriter(), "ALL", combinedGcRatioCounts.getCounts(), false);
                double[] percentData = new double[combinedGcRatioCounts.size()];

                copyVector(combinedGcRatioCounts.getCounts(), percentData);
                convertToPercentages(percentData);
                writeReadGcRatioCounts(mResultsWriter.getReadGcRatioWriter(), "ALL_PERC", percentData, true);

                if(!mConfig.EnrichedGeneIds.isEmpty())
                    writeReadGcRatioCounts(mResultsWriter.getReadGcRatioWriter(), "NON_ENRICHED", nonEnrichedGcRatioCounts.getCounts(), false);

                if(mConfig.ApplyGcBiasAdjust)
                {
                    writeReadGcRatioCounts(mResultsWriter.getReadGcRatioWriter(), "TRANS_FIT_EXPECTED",
                            mGcTranscriptCalcs.getTranscriptFitGcCounts().getCounts(), false);

                    copyVector(mGcTranscriptCalcs.getTranscriptFitGcCounts().getCounts(), percentData);
                    convertToPercentages(percentData);
                    writeReadGcRatioCounts(mResultsWriter.getReadGcRatioWriter(), "TRANS_FIT_EXPECTED_PERC", percentData, true);

                    writeReadGcRatioCounts(mResultsWriter.getReadGcRatioWriter(), "ADJUSTMENTS",
                            mGcTranscriptCalcs.getGcRatioAdjustments(), true);
                }
            }

            // calculate a TPM for all transcripts before results are written
            double totalFragsPerKb = chrTasks.stream()
                    .mapToDouble(x -> calcTotalTranscriptExpression(x.getGeneCollectionSummaryData()))
                    .sum();

            double tpmFactor = totalFragsPerKb / 1e6;
            chrTasks.forEach(x -> setTranscriptsPerMillion(x.getGeneCollectionSummaryData(), tpmFactor));
            chrTasks.forEach(x -> x.writeResults());
        }

        mResultsWriter.close();

        logPerformanceStats(chrTasks);
        return true;
    }

    private void applyGcAdjustments(final List<ChromosomeGeneTask> chrTasks, final GcRatioCounts actualGcCounts)
    {
        ISF_LOGGER.info("applying GC adjustments and transcript re-fit");

        if(mConfig.GcAdjustmentsFile == null)
        {
            // not thread safe at the moment
            chrTasks.forEach(x -> x.applyGcAdjustment());

            ISF_LOGGER.debug("total({}) transcript expected GC counts from fit", String.format("%.0f",
                    mGcTranscriptCalcs.getTranscriptFitGcCounts().getCountsTotal()));

            // gather up global expected counts
            mGcTranscriptCalcs.calcGcRatioAdjustments(actualGcCounts);
        }

        // now re-fit all transcripts
        boolean validExecution = executeChromosomeTask(chrTasks, APPLY_GC_ADJUSTMENT);

        if(!validExecution)
            return;
    }

    private void calcFragmentLengths(final List<ChromosomeGeneTask> chrTasks)
    {
        // for now a way of only calculating fragment lengths and nothing more
        boolean validExecution = executeChromosomeTask(chrTasks, FRAGMENT_LENGTHS);

        if(!validExecution)
        {
            mIsValid = false;
            return;
        }

        // merge results from all chromosomes
        int maxReadLength = 0;
        for(final ChromosomeGeneTask chrGeneTask : chrTasks)
        {
            final FragmentSizeCalcs fragSizeCalcs = chrGeneTask.getFragSizeCalcs();
            maxReadLength = max(maxReadLength, fragSizeCalcs.getMaxReadLength());
            FragmentSizeCalcs.mergeData(mFragmentLengthDistribution, fragSizeCalcs);
        }

        if(mConfig.ApplyFragmentLengthAdjust)
            setConfigFragmentLengthData(mConfig, maxReadLength, mFragmentLengthDistribution);

        if (mConfig.WriteFragmentLengths && !mConfig.FragmentLengthsByGene)
        {
            FragmentSizeCalcs.writeFragmentLengths(mResultsWriter.getFragmentLengthWriter(), mFragmentLengthDistribution, null);
        }

        if(mConfig.WriteFragmentLengthsOnly)
        {
            final PerformanceCounter perfCounter = chrTasks.get(0).getPerfCounters()[PERF_FRAG_LENGTH];

            for(int i = 1; i < chrTasks.size(); ++i)
            {
                perfCounter.merge(chrTasks.get(i).getPerfCounters()[PERF_FRAG_LENGTH]);
            }

            perfCounter.logStats();
        }
    }

    private void generateGcRatios(final List<ChromosomeGeneTask> chrTasks)
    {
        // for now a way of only calculating fragment lengths and nothing more
        boolean validExecution = executeChromosomeTask(chrTasks, GENERATE_TRANSCRIPT_GC_COUNTS);

        if(!validExecution)
        {
            mIsValid = false;
            return;
        }
    }

    private boolean executeChromosomeTask(final List<ChromosomeGeneTask> chrTasks, TaskType taskType)
    {
        chrTasks.forEach(x -> x.setTaskType(taskType));

        if(mConfig.Threads <= 1)
        {
            chrTasks.forEach(x -> x.call());
            return true;
        }

        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("Isofox-%d").build();

        ExecutorService executorService = Executors.newFixedThreadPool(mConfig.Threads, namedThreadFactory);
        List<FutureTask> threadTaskList = new ArrayList<FutureTask>();

        for(ChromosomeGeneTask chrGeneTask : chrTasks)
        {
            FutureTask futureTask = new FutureTask(chrGeneTask);

            threadTaskList.add(futureTask);
            executorService.execute(futureTask);
        }

        if(!checkThreadCompletion(threadTaskList))
        {
            mIsValid = false;
            return false;
        }

        executorService.shutdown();
        return true;
    }

    private boolean checkThreadCompletion(final List<FutureTask> taskList)
    {
        try
        {
            for (FutureTask futureTask : taskList)
            {
                futureTask.get();
            }
        }
        catch (Exception e)
        {
            ISF_LOGGER.error("task execution error: {}", e.toString());
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static void main(@NotNull final String[] args) throws ParseException
    {
        final VersionInfo version = new VersionInfo("isofox.version");
        ISF_LOGGER.info("Isofox version: {}", version.version());

        final Options options = createCmdLineOptions();
        final CommandLine cmd = createCommandLine(args, options);

        if (cmd.hasOption(LOG_DEBUG))
        {
            Configurator.setRootLevel(Level.DEBUG);
        }
        else if(cmd.hasOption(LOG_LEVEL))
        {
            Configurator.setRootLevel(Level.valueOf(cmd.getOptionValue(LOG_LEVEL)));
        }

        if(!validConfigPaths(cmd))
        {
            ISF_LOGGER.error("invalid input files or paths, exiting");
            return;
        }

        IsofoxConfig config = new IsofoxConfig(cmd);

        if(!config.isValid())
        {
            ISF_LOGGER.error("missing config options, exiting");
            return;
        }

        Isofox isofox = new Isofox(config, cmd);
        if(!isofox.runAnalysis())
        {
            ISF_LOGGER.info("Isofox RNA analysis failed");
            return;
        }

        ISF_LOGGER.info("Isofox RNA analysis complete");
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final String[] args, @NotNull final Options options) throws ParseException
    {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

    private void logPerformanceStats(final List<ChromosomeGeneTask> chrTasks)
    {
        final PerformanceCounter[] perfCounters = chrTasks.get(0).getPerfCounters();

        for(int i = 1; i < chrTasks.size(); ++i)
        {
            final PerformanceCounter[] chrPCs = chrTasks.get(i).getPerfCounters();

            for(int j = 0; j < perfCounters.length; ++j)
            {
                perfCounters[j].merge(chrPCs[j]);
            }
        }

        Arrays.stream(perfCounters).forEach(x -> x.logStats());

        if(mConfig.RunPerfChecks)
        {
            // log 10 slowest times and their interval names
            final List<Double> fitTimes = perfCounters[PERF_FIT].getTimes();
            final List<String> fitGenes = perfCounters[PERF_FIT].getTimeNames();

            if(fitTimes.size() >= 10 && fitGenes.size() == fitTimes.size())

                for (int i = fitTimes.size() - 1; i >= fitTimes.size() - 10; --i)
                {
                    ISF_LOGGER.info(String.format("fit times: geneSet(%s) time(%.3f)", fitGenes.get(i), fitTimes.get(i)));
                }
        }
    }

}