package com.hartwig.hmftools.common.lims;

import java.time.LocalDate;

import org.jetbrains.annotations.NotNull;

final class LimsTestUtil {

    private LimsTestUtil() {
    }

    @NotNull
    static LocalDate toDate(@NotNull final String date) {
        return LocalDate.parse(date, LimsConstants.DATE_FORMATTER);
    }

    @NotNull
    static ImmutableLimsJsonSampleData.Builder createLimsSampleDataBuilder() {
        return ImmutableLimsJsonSampleData.builder()
                .sampleId("")
                .dnaConcentration("")
                .arrivalDateString("")
                .samplingDateString("")
                .tumorPercentageString("")
                .primaryTumor("")
                .labelSample("")
                .projectName("")
                .submission("");
    }
}
