package com.hartwig.hmftools.common.purple.copynumber;

import java.util.EnumSet;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.purple.gender.Gender;
import com.hartwig.hmftools.common.purple.region.FittedRegion;
import com.hartwig.hmftools.common.purple.region.GermlineStatus;
import com.hartwig.hmftools.common.utils.Doubles;

import org.jetbrains.annotations.NotNull;

class ExtractGermlineDeletions {

    private final Gender gender;

    ExtractGermlineDeletions(final Gender gender) {
        this.gender = gender;
    }

    @NotNull
    List<CombinedRegion> extractGermlineDeletions(@NotNull final List<CombinedRegion> regions) {
        final EnumSet<GermlineStatus> eligibleStatus = EnumSet.of(GermlineStatus.HET_DELETION, GermlineStatus.HOM_DELETION);

        final List<CombinedRegion> result = Lists.newArrayList();
        for (final CombinedRegion parent : regions) {
            result.addAll(extractChildren(eligibleStatus, parent));
        }

        return result;
    }

    @NotNull
    private List<CombinedRegion> extractChildren(@NotNull final EnumSet<GermlineStatus> eligibleStatus,
            @NotNull final CombinedRegion parent) {
        final List<CombinedRegion> children = Lists.newArrayList();

        double baf = parent.tumorBAF();
        double copyNumber = parent.tumorCopyNumber();
        for (int i = 0; i < parent.regions().size(); i++) {
            final FittedRegion child = parent.regions().get(i);

            if (eligibleStatus.contains(child.status())) {
                if (child.status().equals(GermlineStatus.HET_DELETION)) {
                    final double upperBound = upperBound(child);
                    if (Doubles.lessThan(upperBound, Math.min(0.5, copyNumber))) {
                        children.add(createChild(child, upperBound, baf));
                    }
                }

                if (child.status().equals(GermlineStatus.HOM_DELETION)) {
                    children.add(createChild(child, child.refNormalisedCopyNumber(), baf));
                }
            }
        }

        return extendRight(children);
    }

    @NotNull
    private static CombinedRegion createChild(@NotNull final FittedRegion child, double newCopyNumber, double newBaf) {
        final CombinedRegion result = new CombinedRegionImpl(child);
        result.setTumorCopyNumber(method(child), newCopyNumber);
        result.setInferredTumorBAF(newBaf);
        return result;
    }

    @NotNull
    private static CopyNumberMethod method(@NotNull final FittedRegion child) {
        switch (child.status()) {
            case HOM_DELETION:
                return CopyNumberMethod.GERMLINE_HOM_DELETION;
            default:
                return CopyNumberMethod.GERMLINE_HET2HOM_DELETION;
        }
    }

    @NotNull
    private static List<CombinedRegion> extendRight(@NotNull final List<CombinedRegion> children) {
        int i = 0;
        while (i < children.size() - 1) {
            final CombinedRegion target = children.get(i);
            final CombinedRegion neighbour = children.get(i + 1);

            if (target.region().status().equals(neighbour.region().status()) && target.end() + 1 == neighbour.start()) {
                target.extendWithUnweightedAverage(neighbour.region());
                children.remove(i + 1);
            } else {
                i++;
            }
        }

        return children;
    }

    private double upperBound(@NotNull final FittedRegion region) {
        double expectedNormalRatio = HumanChromosome.fromString(region.chromosome()).isDiploid(gender) ? 1 : 0.5;
        return Math.max(region.tumorCopyNumber(), region.refNormalisedCopyNumber()) / (2 * Math.min(expectedNormalRatio,
                region.observedNormalRatio()));
    }
}
