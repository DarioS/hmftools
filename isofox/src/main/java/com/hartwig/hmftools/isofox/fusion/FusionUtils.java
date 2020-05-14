package com.hartwig.hmftools.isofox.fusion;

import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.isofox.fusion.FusionConstants.REALIGN_MIN_SOFT_CLIP_BASE_LENGTH;
import static com.hartwig.hmftools.isofox.fusion.FusionFragmentType.MATCHED_JUNCTION;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.isofox.common.ReadRecord;

import htsjdk.samtools.CigarOperator;

public class FusionUtils
{
    public static final int FS_UPSTREAM = 0;
    public static final int FS_DOWNSTREAM = 1;
    public static final int FS_PAIR = 2;

    public static int switchStream(int iter) { return iter == FS_UPSTREAM ? FS_DOWNSTREAM : FS_UPSTREAM; }

    public static boolean lowerChromosome(final String chr, final String otherChr)
    {
        return chromosomeRank(chr) < chromosomeRank(otherChr);
    }

    public static int chromosomeRank(final String chromosome)
    {
        if(!HumanChromosome.contains(chromosome))
            return -1;

        if(chromosome.equals("X"))
            return 23;
        else if(chromosome.equals("Y"))
            return 24;
        else if(chromosome.equals("MT"))
            return 25;
        else
            return Integer.parseInt(chromosome);
    }

    public static String formLocationPair(final String[] chromosomes, final int[] geneCollectionIds, final boolean[] isGenic)
    {
        return String.format("%s_%s",
                formLocation(chromosomes[SE_START], geneCollectionIds[SE_START], isGenic[SE_START]),
                formLocation(chromosomes[SE_END], geneCollectionIds[SE_END], isGenic[SE_END]));
    }

    public static String formLocation(final String chromosome, final int geneCollectionId, boolean isGenic)
    {
        if(isGenic)
            return String.format("%s:%d", chromosome, geneCollectionId);
        else
            return String.format("%s:pre_%d", chromosome, geneCollectionId);
    }

    public static String formChromosomePair(final String chr1, final String chr2) { return chr1 + "_" + chr2; }
    public static String[] getChromosomePair(final String chrPair) { return chrPair.split("_"); }

    public static Set<Integer> collectCandidateJunctions(final Map<String, List<ReadRecord>> readsMap, final String chromosome)
    {
        Set<Integer> candidateJunctions = Sets.newHashSet();

        for(Map.Entry<String,List<ReadRecord>> entry : readsMap.entrySet())
        {
            for(ReadRecord read : entry.getValue())
            {
                if(!read.Chromosome.equals(chromosome))
                    continue;

                if(read.spansGeneCollections() && read.containsSplit())
                {
                    // find the largest N-split to mark the junction
                    final int[] splitJunction = findSplitReadJunction(read);

                    if(splitJunction != null)
                    {
                        candidateJunctions.add(splitJunction[SE_START]);
                        candidateJunctions.add(splitJunction[SE_END]);
                    }

                    break;
                }

                if(read.isSoftClipped(SE_START) && read.Cigar.getFirstCigarElement().getLength() >= REALIGN_MIN_SOFT_CLIP_BASE_LENGTH)
                    candidateJunctions.add(read.getCoordsBoundary(SE_START));

                if(read.isSoftClipped(SE_END) && read.Cigar.getLastCigarElement().getLength() >= REALIGN_MIN_SOFT_CLIP_BASE_LENGTH)
                    candidateJunctions.add(read.getCoordsBoundary(SE_END));
            }
        }

        return candidateJunctions;
    }

    public static int[] findSplitReadJunction(final ReadRecord read)
    {
        if(!read.spansGeneCollections() || !read.containsSplit())
            return null;

        final int maxSplitLength = read.Cigar.getCigarElements().stream()
                .filter(x -> x.getOperator() == CigarOperator.N)
                .mapToInt(x -> x.getLength()).max().orElse(0);

        final List<int[]> mappedCoords = read.getMappedRegionCoords();
        for(int i = 0; i < mappedCoords.size() - 1; ++i)
        {
            final int[] lowerCoords = mappedCoords.get(i);
            final int[] upperCoords = mappedCoords.get(i + 1);

            if(upperCoords[SE_START] - lowerCoords[SE_END] - 1 == maxSplitLength)
            {
                return new int[] { lowerCoords[SE_END], upperCoords[SE_START] };
            }
        }

        return null;
    }
}