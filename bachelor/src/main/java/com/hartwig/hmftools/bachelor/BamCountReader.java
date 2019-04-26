package com.hartwig.hmftools.bachelor;

import static com.hartwig.hmftools.bachelor.BachelorPostProcess.REF_GENOME;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Sets;
import com.hartwig.hmftools.bachelor.types.BachelorGermlineVariant;
import com.hartwig.hmftools.common.hotspot.ImmutableVariantHotspotImpl;
import com.hartwig.hmftools.common.hotspot.VariantHotspot;
import com.hartwig.hmftools.common.hotspot.VariantHotspotEvidence;
import com.hartwig.hmftools.common.hotspot.VariantHotspotEvidenceFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;

public class BamCountReader
{
    private IndexedFastaSequenceFile mIndexedFastaSequenceFile;
    private String mSampleBamFile;
    private File mRefGenomeFile;
    SamReader mTumorReader;

    private static String TUMOR_BAM_FILE = "tumor_bam_file";
    private static int DEFAULT_MIN_BASE_QUALITY = 13;
    private static int DEFAULT_MIN_MAPPING_QUALITY = 1;

    private static final Logger LOGGER = LogManager.getLogger(BamCountReader.class);

    public BamCountReader()
    {
        mIndexedFastaSequenceFile = null;
        mTumorReader = null;
        mRefGenomeFile = null;
        mSampleBamFile = "";
    }

    public static void addCmdLineOptions(Options options)
    {
        options.addOption(TUMOR_BAM_FILE, true, "Location of a specific BAM file");
    }

    public boolean initialise(final CommandLine cmd, IndexedFastaSequenceFile ifSeqFile)
    {
        mIndexedFastaSequenceFile = ifSeqFile;

        mRefGenomeFile = new File(cmd.getOptionValue(REF_GENOME));

        if(cmd.hasOption(TUMOR_BAM_FILE))
        {
            mSampleBamFile = cmd.getOptionValue(TUMOR_BAM_FILE);
        }

        return true;
    }

    public void readBamCounts(List<BachelorGermlineVariant> bachRecords, final String bachDataDir)
    {
        String bamFile = "";

        if(!mSampleBamFile.isEmpty())
        {
            bamFile = mSampleBamFile;
        }
        else
        {
            // look for a single BAM file to parse
            final Path root = Paths.get(bachDataDir);

            try (final Stream<Path> stream = Files.walk(root, 1, FileVisitOption.FOLLOW_LINKS))
            {
                List<File> bamFiles = stream.map(Path::toFile)
                        .filter(p -> !p.isDirectory())
                        .filter(p_ -> p_.getName().endsWith(".bam"))
                        .collect(Collectors.toList());

                if(bamFiles.size() != 1)
                {
                    LOGGER.warn("invalid BAM file count({})", bamFiles.size());
                    return;
                }

                bamFile = bamFiles.get(0).getAbsolutePath();
                LOGGER.debug("found BAM file: {}", bamFile);
            }
            catch (IOException e)
            {
                LOGGER.error("failed to find BAM files from dir({})", bachDataDir);
                return;
            }
        }

        LOGGER.debug("reading BAM file: {}", bamFile);

        mTumorReader = SamReaderFactory.makeDefault().referenceSequence(mRefGenomeFile).open(new File(bamFile));

        final Set<VariantHotspot> allHotspots = Sets.newHashSet();

        for(BachelorGermlineVariant variant : bachRecords)
        {
            VariantHotspot variantHotspot = ImmutableVariantHotspotImpl.builder()
                    .chromosome(variant.Chromosome)
                    .position(variant.Position)
                    .ref(variant.Ref)
                    .alt(variant.Alts)
                    .build();

            allHotspots.add(variantHotspot);
        }

        final VariantHotspotEvidenceFactory hotspotEvidenceFactory = new VariantHotspotEvidenceFactory(DEFAULT_MIN_MAPPING_QUALITY, DEFAULT_MIN_BASE_QUALITY, allHotspots);
        final List<VariantHotspotEvidence> tumorEvidence = hotspotEvidenceFactory.evidence(mIndexedFastaSequenceFile, mTumorReader);

        if(tumorEvidence.size() != bachRecords.size())
        {
            LOGGER.error("incomplete BAM evidence read: evidenceCount({}) vs bachRecords({})", tumorEvidence.size(), bachRecords.size());
            return;
        }

        for(int i = 0; i < bachRecords.size(); ++i)
        {
            BachelorGermlineVariant variant = bachRecords.get(i);
            VariantHotspotEvidence evidence = tumorEvidence.get(i);

            variant.setTumorData(evidence.altSupport(), evidence.readDepth());

            LOGGER.debug("chr({}) position({}) matched, counts(ref={} alt={} depth={})",
                    variant.Chromosome, variant.Position,
                    variant.getTumorRefCount(), variant.getTumorAltCount(), variant.getTumorReadDepth());

        }
    }

}