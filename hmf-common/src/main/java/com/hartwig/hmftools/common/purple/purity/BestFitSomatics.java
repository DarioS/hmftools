package com.hartwig.hmftools.common.purple.purity;

import java.util.List;

import com.hartwig.hmftools.common.numeric.Doubles;
import com.hartwig.hmftools.common.variant.SomaticVariant;

import org.jetbrains.annotations.NotNull;

class BestFitSomatics {

    @NotNull
    static FittedPurity bestSomaticFit(@NotNull List<SomaticVariant> somatics, @NotNull List<FittedPurity> candidates) {
        assert (!candidates.isEmpty());

        FittedPurity result = candidates.get(0);
        int resultCount = 0;

        for (FittedPurity candidate : candidates) {
            int candidateCount = count(somatics, candidate);
            if (candidateCount > resultCount) {
                result = candidate;
                resultCount = candidateCount;
            }
        }

        return result;
    }

    private static int count(@NotNull List<SomaticVariant> somatics, @NotNull FittedPurity purity) {

        double minVaf = purity.purity() / 2.0 - 0.1;
        double maxVaf = purity.purity() / 2.0 + 0.1;

        int count = 0;
        for (SomaticVariant somatic : somatics) {
            double vaf = somatic.alleleFrequency();
            if (Doubles.greaterOrEqual(vaf, minVaf) && Doubles.lessOrEqual(vaf, maxVaf)) {
                count++;
            }
        }
        return count;
    }
}
