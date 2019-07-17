package com.hartwig.hmftools.patientreporter.copynumber;

import com.hartwig.hmftools.common.purple.gene.GeneCopyNumber;

import org.jetbrains.annotations.NotNull;

public enum CopyNumberInterpretation {
    GAIN("gain"),
    FULL_LOSS("full loss"),
    PARTIAL_LOSS("partial loss");

    @NotNull
    private final String text;

    CopyNumberInterpretation(@NotNull final String text) {
        this.text = text;
    }

    @NotNull
    public String text() {
        return text;
    }

    @NotNull
    public static CopyNumberInterpretation fromCopyNumber(@NotNull GeneCopyNumber copyNumber) {
        // Assume the copy number is significant.
        if (copyNumber.minCopyNumber() > 2) {
            return GAIN;
        } else if (copyNumber.maxCopyNumber() > 0.5) {
            return PARTIAL_LOSS;
        } else {
            return FULL_LOSS;
        }
    }
}