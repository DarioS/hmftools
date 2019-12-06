package com.hartwig.hmftools.sage.context;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.region.GenomeRegion;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.sage.config.SageConfig;
import com.hartwig.hmftools.sage.sam.SamSlicer;
import com.hartwig.hmftools.sage.sam.SamSlicerFactory;
import com.hartwig.hmftools.sage.select.PositionSelector;
import com.hartwig.hmftools.sage.select.TierSelector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;

public class AltContextSupplier implements Supplier<List<AltContext>> {

    private static final Logger LOGGER = LogManager.getLogger(AltContextSupplier.class);

    private final String sample;
    private final String bamFile;
    private final SageConfig config;
    private final GenomeRegion bounds;
    private final SamSlicerFactory samSlicerFactory;
    private final TumorRefContextCandidates candidates;
    private final RefContextConsumer refContextConsumer;
    private final PositionSelector<AltContext> consumerSelector;
    private final List<AltContext> altContexts = Lists.newArrayList();
    private final TierSelector tierSelector;

    public AltContextSupplier(@NotNull final SageConfig config, @NotNull final String sample, @NotNull final GenomeRegion bounds,
            @NotNull final String bamFile, @NotNull final RefSequence refGenome, @NotNull final SamSlicerFactory samSlicerFactory,
            final List<VariantHotspot> hotspots, final List<GenomeRegion> panelRegions) {
        this.config = config;
        this.sample = sample;
        this.bamFile = bamFile;
        this.samSlicerFactory = samSlicerFactory;
        this.consumerSelector = new PositionSelector<>(altContexts);
        this.candidates = new TumorRefContextCandidates(sample);
        this.bounds = bounds;
        this.refContextConsumer = new RefContextConsumer(true, config, bounds, refGenome, this.candidates);
        this.tierSelector = new TierSelector(panelRegions, hotspots);

    }

    private void processFirstPass(final SAMRecord samRecord) {
        refContextConsumer.accept(samRecord);
    }

    private void processSecondPass(final SAMRecord samRecord) {
        consumerSelector.select(samRecord.getAlignmentStart(),
                samRecord.getAlignmentEnd(),
                x -> x.primaryReadContext().accept(x.readDepth() < config.maxReadDepth(), samRecord, config));
    }

    @Override
    public List<AltContext> get() {

        final SamSlicer slicer = samSlicerFactory.create(bounds);

        if (bounds.start() == 1) {
            LOGGER.info("Beginning processing of {} chromosome {} ", sample, bounds.chromosome());
        }

        LOGGER.info("Variant candidates {} position {}:{}", sample, bounds.chromosome(), bounds.start());

        try (final SamReader tumorReader = SamReaderFactory.makeDefault().open(new File(bamFile))) {

            slicer.slice(tumorReader, this::processFirstPass);

            // Add all valid alt contexts
            candidates.refContexts().stream().flatMap(x -> x.alts().stream()).filter(this::altSupportPredicate).forEach(x -> {
                x.setPrimaryReadCounterFromInterim();
                altContexts.add(x);
            });

            slicer.slice(tumorReader, this::processSecondPass);

        } catch (IOException e) {
            throw new CompletionException(e);
        }

        return altContexts.stream().filter(this::qualPredicate).collect(Collectors.toList());
    }


    private boolean altSupportPredicate(@NotNull final AltContext altContext) {
        return altContext.altSupport() >= config.filter().hardMinTumorAltSupport() || tierSelector.isHotspot(altContext);
    }

    private boolean qualPredicate(@NotNull final AltContext altContext) {
        return altContext.primaryReadContext().quality() >= config.filter().hardMinTumorQual() || tierSelector.isHotspot(altContext);
    }

}