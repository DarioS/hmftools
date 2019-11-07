package com.hartwig.hmftools.sage.read;

import com.hartwig.hmftools.common.hotspot.VariantHotspot;
import com.hartwig.hmftools.common.position.GenomePosition;
import com.hartwig.hmftools.sage.config.QualityConfig;
import com.hartwig.hmftools.sage.context.Realigned;
import com.hartwig.hmftools.sage.context.RealignedContext;

import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.SAMRecord;

public class ReadContextCounter implements GenomePosition {
    private final VariantHotspot variant;
    private final ReadContext readContext;

    private int full;
    private int partial;
    private int realigned;
    private int lengthened;
    private int shortened;
    private int coverage;

    private int quality;
    private double jitterPenalty;
    private int baseQuality;
    private int mapQuality;

    private int improperPair;

    public ReadContextCounter(@NotNull final VariantHotspot variant, @NotNull final ReadContext readContext) {
        assert (readContext.isComplete());
        this.variant = variant;
        this.readContext = readContext;
    }

    @NotNull
    @Override
    public String chromosome() {
        return variant.chromosome();
    }

    @Override
    public long position() {
        return variant.position();
    }

    public int support() {
        return full + partial + realigned;
    }

    public int coverage() {
        return coverage;
    }

    public double vaf() {
        return coverage == 0 ? 0d : (double) support() / coverage;
    }

    public int quality() {
        return Math.max(0, quality - (int) jitterPenalty);
    }

    public int baseQuality() {
        return baseQuality;
    }

    public int mapQuality() {
        return mapQuality;
    }

    public int[] rcc() {
        return new int[] { full, partial, realigned, shortened, lengthened };
    }

    public int[] rcq() {
        return new int[] { improperPair };
    }

    public int[] qual() {
        return new int[] { baseQuality, mapQuality, qualityJitterPenalty() };
    }

    @NotNull
    public ReadContext readContext() {
        return readContext;
    }

    @Override
    public String toString() {
        return readContext.centerBases();
    }

    public void accept(final SAMRecord record, final QualityConfig qualityConfig) {

        if (record.getAlignmentStart() <= variant.position() && record.getAlignmentEnd() >= variant.position()
                && readContext.isComplete()) {

            boolean covered = false;
            if (coverage < 1000) {
                int readIndex = record.getReadPositionAtReferencePosition(readContext.position()) - 1;
                if (readContext.isCentreCovered(readIndex, record.getReadBases())) {
                    covered = true;
                }

                ReadContextMatch match = readContext.matchAtPosition(readIndex, record.getReadBases());
                if (!match.equals(ReadContextMatch.NONE)) {
                    switch (match) {
                        case FULL:
                            full++;
                            incrementQualityFlags(record);
                            incrementQualityScores(readIndex, record, qualityConfig);
                            break;
                        case PARTIAL:
                            partial++;
                            incrementQualityFlags(record);
                            incrementQualityScores(readIndex, record, qualityConfig);
                            break;
                    }
                } else {
                    final RealignedContext context = new Realigned().realigned(readContext, record.getReadBases());
                    switch (context.type()) {
                        case EXACT:
                            realigned++;
                            covered = true;
                            break;
                        case LENGTHENED:
                            jitterPenalty += qualityConfig.jitterPenalty(context.repeatCount());
                            lengthened++;
                            covered = true;
                            break;
                        case SHORTENED:
                            jitterPenalty += qualityConfig.jitterPenalty(context.repeatCount());
                            shortened++;
                            covered = true;
                            break;
                    }
                }
            }

            if (covered) {
                coverage++;
            }
        }
    }

    private void incrementQualityScores(int readBaseIndex, final SAMRecord record, final QualityConfig qualityConfig) {
        final int baseQuality = baseQuality(readBaseIndex, record);
        final int distanceFromReadEdge = readContext.distanceFromReadEdge(readBaseIndex, record);

        final int mapQuality = record.getMappingQuality();

        int modifiedMapQuality = qualityConfig.modifiedMapQuality(mapQuality, readContext.distance(), record.getProperPairFlag());
        int modifiedBaseQuality = qualityConfig.modifiedBaseQuality(baseQuality, distanceFromReadEdge);

        this.mapQuality += mapQuality;
        this.baseQuality += baseQuality;
        this.quality += Math.max(0, Math.min(modifiedMapQuality, modifiedBaseQuality));
    }

    private int baseQuality(int readBaseIndex, SAMRecord record) {
        return variant.ref().length() == variant.alt().length()
                ? record.getBaseQualities()[readBaseIndex]
                : readContext.minCentreQuality(readBaseIndex, record);
    }

    public int qualityJitterPenalty() {
        return (int) jitterPenalty;
    }


    public boolean incrementCounters(@NotNull final ReadContext other) {
        if (readContext.isFullMatch(other)) {
            full++;
            return true;
        }

        return false;
    }

    private void incrementQualityFlags(@NotNull final SAMRecord record) {
        if (!record.getProperPairFlag()) {
            improperPair++;
        }
    }

}