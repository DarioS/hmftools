package com.hartwig.hmftools.svannotation.analysis;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.fusions.KnownFusionsModel;
import com.hartwig.hmftools.common.variant.structural.EnrichedStructuralVariant;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantType;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneAnnotation;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneDisruption;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneFusion;
import com.hartwig.hmftools.common.variant.structural.annotation.ImmutableGeneDisruption;
import com.hartwig.hmftools.common.variant.structural.annotation.ImmutableGeneFusion;
import com.hartwig.hmftools.common.variant.structural.annotation.StructuralVariantAnnotation;
import com.hartwig.hmftools.common.variant.structural.annotation.Transcript;
import com.hartwig.hmftools.svannotation.VariantAnnotator;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StructuralVariantAnalyzer
{
    private static final int EXON_THRESHOLD = 1;

    @NotNull
    private final VariantAnnotator mGeneDataAnnotator;
    @NotNull
    private final Set<String> mDisruptionGeneIDPanel;
    @NotNull
    private final KnownFusionsModel mKnownFusionsModel;

    private static final Logger LOGGER = LogManager.getLogger(StructuralVariantAnalyzer.class);

    public StructuralVariantAnalyzer(@NotNull final VariantAnnotator mGeneDataAnnotator, @NotNull Set<String> mDisruptionGeneIDPanel,
            @NotNull final KnownFusionsModel mKnownFusionsModel)
    {
        // TODO (KODU) See if we can replace with a filter on gene name rather than gene ensembl ID.
        this.mGeneDataAnnotator = mGeneDataAnnotator;
        this.mDisruptionGeneIDPanel = mDisruptionGeneIDPanel;
        this.mKnownFusionsModel = mKnownFusionsModel;
    }

    @NotNull
    @Deprecated
    public StructuralVariantAnalysis run(@NotNull final List<EnrichedStructuralVariant> variants)
    {
        LOGGER.debug("annotating variants with gene and transcript info");
        final List<StructuralVariantAnnotation> annotations = mGeneDataAnnotator.annotateVariants(variants);

        final List<StructuralVariantAnnotation> copy = Lists.newArrayList(annotations);

        LOGGER.debug("processing fusions");
        final List<GeneFusion> fusions = findFusions(copy);

        LOGGER.debug("processing disruptions");
        final List<GeneDisruption> disruptions = findDisruptions(copy);

        return ImmutableStructuralVariantAnalysis.of(annotations, fusions, disruptions);
    }

    public final List<StructuralVariantAnnotation> findAnnotations(final List<EnrichedStructuralVariant> variants)
    {
        LOGGER.debug("annotating {} variants with gene and transcript info", variants.size());
        return mGeneDataAnnotator.annotateVariants(variants);
    }

    public final List<GeneFusion> findFusions(final List<StructuralVariantAnnotation> annotations)
    {
        LOGGER.debug("finding fusions");

        // left is upstream, right is downstream
        final List<List<Pair<Transcript, Transcript>>> fusionsPerVariant = Lists.newArrayList();

        for (final StructuralVariantAnnotation annotation : annotations)
        {
            final List<Pair<Transcript, Transcript>> fusions = Lists.newArrayList();

            for (final GeneAnnotation startGene : annotation.start())
            {
                boolean startUpstream = isUpstream(startGene);

                for (final GeneAnnotation endGene : annotation.end())
                {
                    boolean endUpstream = isUpstream(endGene);

                    if (startUpstream == endUpstream)
                        continue;

                    for (final Transcript t1 : getIntronicTranscripts(startGene.transcripts()))
                    {
                        for (final Transcript t2 : getIntronicTranscripts(endGene.transcripts()))
                        {
                            if (!isPotentiallyRelevantFusion(t1, t2))
                                continue;

                            if (startUpstream && t1.exonUpstreamPhase() == t2.exonDownstreamPhase())
                            {
                                fusions.add(Pair.of(t1, t2));
                            }
                            else if (!startUpstream && t2.exonUpstreamPhase() == t1.exonDownstreamPhase())
                            {
                                fusions.add(Pair.of(t2, t1));
                            }
                        }
                    }
                }
            }

            fusionsPerVariant.add(fusions);
        }

        return toReportableGeneFusions(fusionsPerVariant);
    }

    private static boolean isPotentiallyRelevantFusion(final Transcript t1, final Transcript t2)
    {
        final boolean sameGene = t1.geneName().equals(t2.geneName());

        if (sameGene)
        {
            // skip fusions between different transcripts in the same gene,
            if (!t1.transcriptId().equals(t2.transcriptId()))
                return false;

            // skip fusions within the same intron
            if (intronicDisruptionOnSameTranscript(t1, t2))
                return false;
        }

        return true;
    }

    @NotNull
    private List<GeneFusion> toReportableGeneFusions(@NotNull List<List<Pair<Transcript, Transcript>>> fusionsPerVariant)
    {
        final List<GeneFusion> result = Lists.newArrayList();

        for (final List<Pair<Transcript, Transcript>> fusions : fusionsPerVariant)
        {
            Optional<Pair<Transcript, Transcript>> reportableFusion = determineReportableFusion(fusions);

            for (final Pair<Transcript, Transcript> fusion : fusions)
            {
                final Transcript upstream = fusion.getLeft();
                final Transcript downstream = fusion.getRight();
                final boolean matchesKnownFusion = transcriptsMatchKnownFusion(mKnownFusionsModel, upstream, downstream);

                Boolean isPostCodingUpstream = postCoding(upstream);
                Boolean isPostCodingDownstream = postCoding(downstream);

                final boolean reportable = reportableFusion.isPresent() && reportableFusion.get() == fusion && matchesKnownFusion && (
                        (isPostCodingDownstream != null && !isPostCodingDownstream) && (isPostCodingUpstream == null
                                || !isPostCodingUpstream) && !(intragenic(upstream, downstream) && upstream.exonUpstreamPhase() == -1));

                final GeneFusion geneFusion = ImmutableGeneFusion.builder()
                        .reportable(reportable)
                        .upstreamLinkedAnnotation(upstream)
                        .downstreamLinkedAnnotation(downstream)
                        .primarySource(mKnownFusionsModel.primarySource(upstream.parent().synonyms(), downstream.parent().synonyms()))
                        .build();

                result.add(geneFusion);
            }
        }
        return result;
    }

    @NotNull
    private static Optional<Pair<Transcript, Transcript>> determineReportableFusion(@NotNull List<Pair<Transcript, Transcript>> fusions)
    {
        // Select either the canonical -> canonical transcript fusion
        //  then the one with the most exons where one end is canonical
        //  then the one with the most exons combined transcript

        Optional<Pair<Transcript, Transcript>> reportableFusion =
                fusions.stream().filter(pair -> pair.getLeft().isCanonical() && pair.getRight().isCanonical()).findFirst();

        if (!reportableFusion.isPresent())
        {
            reportableFusion = fusions.stream()
                    .filter(pair -> pair.getLeft().isCanonical() || pair.getRight().isCanonical())
                    .sorted(Comparator.comparingInt(a -> a.getLeft().exonMax() + a.getRight().exonMax()))
                    .reduce((a, b) -> b);
        }

        if (!reportableFusion.isPresent())
        {
            reportableFusion = fusions.stream()
                    .sorted(Comparator.comparingInt(a -> a.getLeft().exonMax() + a.getRight().exonMax()))
                    .reduce((a, b) -> b);
        }

        return reportableFusion;
    }

    public final List<GeneDisruption> findDisruptions(final List<StructuralVariantAnnotation> annotations)
    {
        LOGGER.debug("finding disruptions");

        final List<GeneAnnotation> geneAnnotations = Lists.newArrayList();

        for (final StructuralVariantAnnotation annotation : annotations)
        {
            @SuppressWarnings("ConstantConditions")
            final boolean pureIntronicDisruptionCanonical = annotation.start()
                    .stream()
                    .filter(gene -> gene.canonical() != null)
                    .anyMatch(gene -> annotation.end()
                            .stream()
                            .filter(other -> other.canonical() != null)
                            .anyMatch(other -> intronicDisruptionOnSameTranscript(gene.canonical(), other.canonical())));

            if (pureIntronicDisruptionCanonical && annotation.variant().type() != StructuralVariantType.INV)
                continue;

            geneAnnotations.addAll(annotation.annotations());
        }

        final Multimap<String, GeneAnnotation> geneMap = ArrayListMultimap.create();
        geneAnnotations.forEach(g -> geneMap.put(g.geneName(), g));

        final List<GeneDisruption> disruptions = Lists.newArrayList();

        for (final String geneName : geneMap.keySet())
        {
            for (final GeneAnnotation gene : geneMap.get(geneName))
            {
                for (final Transcript transcript : gene.transcripts())
                {
                    final GeneDisruption disruption = ImmutableGeneDisruption.builder()
                            .reportable(mDisruptionGeneIDPanel.stream().anyMatch(geneID -> gene.synonyms().contains(geneID))
                                    && transcript.isCanonical())
                            .linkedAnnotation(transcript)
                            .build();

                    disruptions.add(disruption);
                }
            }
        }

        return disruptions;
    }

    private static boolean transcriptsMatchKnownFusion(final KnownFusionsModel fusionsModel, final Transcript five, final Transcript three)
    {
        if(fusionsModel.exactMatch(five.parent().synonyms(), three.parent().synonyms()))
            return true;

        if(fusionsModel.intergenicPromiscuousMatch(five.parent().synonyms(), three.parent().synonyms()))
            return true;

        if(fusionsModel.intragenicPromiscuousMatch(five.parent().synonyms(), three.parent().synonyms())
        && three.exonDownstream() - five.exonUpstream() > EXON_THRESHOLD)
        {
            return true;
        }

        return false;
    }

    private static boolean intronicDisruptionOnSameTranscript(final Transcript t1, final Transcript t2)
    {
        final boolean sameTranscript = t1.transcriptId().equals(t2.transcriptId());
        final boolean bothIntronic = t1.isIntronic() && t2.isIntronic();
        final boolean sameExonUpstream = t1.exonUpstream() == t2.exonUpstream();

        return sameTranscript && bothIntronic && sameExonUpstream;
    }

    private static boolean isUpstream(GeneAnnotation gene)
    {
        int orientation = gene.variant().orientation(gene.isStart());
        return gene.strand() * orientation > 0;
    }

    private static List<Transcript> getIntronicTranscripts(List<Transcript> transcripts)
    {
        return transcripts.stream().filter(Transcript::isIntronic).collect(Collectors.toList());
    }

    @Nullable
    private static Boolean postCoding(@NotNull final Transcript transcript)
    {
        Long codingStart = transcript.codingStart();
        Long codingEnd = transcript.codingEnd();

        if (codingStart == null || codingEnd == null)
            return null;

        final int strand = transcript.parent().strand();
        final long position = transcript.parent().variant().position(transcript.parent().isStart());

        return (strand == 1 && position > codingEnd) || (strand == -1 && position < codingStart);
    }

    private static boolean intragenic(final Transcript upstream, final Transcript downstream)
    {
        return upstream.parent().synonyms().stream().anyMatch(downstream.parent().synonyms()::contains);
    }
}
