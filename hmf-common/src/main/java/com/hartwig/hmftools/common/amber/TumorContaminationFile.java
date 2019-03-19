package com.hartwig.hmftools.common.amber;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;

import org.jetbrains.annotations.NotNull;

public class TumorContaminationFile {

    private static final String DELIMITER = "\t";
    private static final String HEADER_PREFIX = "Chr";

    private static final String AMBER_EXTENSION = ".amber.contamination";

    public static String generateContaminationFilename(@NotNull final String basePath, @NotNull final String sample) {
        return basePath + File.separator + sample + AMBER_EXTENSION;
    }

    public static void write(@NotNull final String filename, @NotNull final List<TumorContamination> contamination) throws IOException {
        Files.write(new File(filename).toPath(), toLines(contamination));
    }

    @NotNull
    public static List<TumorContamination> read(@NotNull final String fileName) throws IOException {
        return fromLines(Files.readAllLines(new File(fileName).toPath()));
    }

    @NotNull
    private static List<TumorContamination> fromLines(@NotNull List<String> lines) {
        final List<TumorContamination> result = Lists.newArrayList();
        for (String line : lines) {
            if (!line.startsWith(HEADER_PREFIX)) {
                result.add(fromString(line));
            }
        }

        return result;
    }

    @NotNull
    private static TumorContamination fromString(@NotNull final String line) {
        String[] values = line.split(DELIMITER);

        final BaseDepth template = ModifiableBaseDepth.create()
                .setChromosome(values[0])
                .setPosition(Long.valueOf(values[1]))
                .setRef(BaseDepth.Base.valueOf(values[2]))
                .setIndelCount(0);

        final BaseDepth normalDepth = ModifiableBaseDepth.create().from(template).setReadDepth(Integer.valueOf(values[4]));
        normalDepth.baseMap().put(BaseDepth.Base.valueOf(values[2]), Integer.valueOf(values[5]));
        normalDepth.baseMap().put(BaseDepth.Base.valueOf(values[3]), Integer.valueOf(values[6]));

        final BaseDepth tumorDepth = ModifiableBaseDepth.create().from(template).setReadDepth(Integer.valueOf(values[7]));
        normalDepth.baseMap().put(BaseDepth.Base.valueOf(values[2]), Integer.valueOf(values[8]));
        normalDepth.baseMap().put(BaseDepth.Base.valueOf(values[3]), Integer.valueOf(values[9]));

        return ImmutableTumorContamination.builder().from(template).normal(normalDepth).tumor(tumorDepth).build();
    }

    @NotNull
    private static List<String> toLines(@NotNull final List<TumorContamination> contamination) {
        final List<String> lines = Lists.newArrayList();
        lines.add(header());
        contamination.stream().map(TumorContaminationFile::toString).forEach(lines::add);
        return lines;
    }

    @NotNull
    private static String header() {
        return new StringJoiner(DELIMITER, "", "").add("Chromosome")
                .add("Position")
                .add("Ref")
                .add("Alt")
                .add("NormalDepth")
                .add("NormalRefSupport")
                .add("NormalAltSupport")
                .add("TumorDepth")
                .add("TumorRefSupport")
                .add("TumorAltSupport")
                .toString();
    }

    @NotNull
    private static String toString(@NotNull final TumorContamination ratio) {
        return new StringJoiner(DELIMITER).add(String.valueOf(ratio.chromosome()))
                .add(String.valueOf(ratio.position()))
                .add(String.valueOf(ratio.tumor().ref()))
                .add(String.valueOf(ratio.tumor().alt()))
                .add(String.valueOf(ratio.normal().readDepth()))
                .add(String.valueOf(ratio.normal().refSupport()))
                .add(String.valueOf(ratio.normal().altSupport()))
                .add(String.valueOf(ratio.tumor().readDepth()))
                .add(String.valueOf(ratio.tumor().refSupport()))
                .add(String.valueOf(ratio.tumor().altSupport()))
                .toString();
    }

}