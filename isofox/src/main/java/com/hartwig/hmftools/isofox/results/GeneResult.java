package com.hartwig.hmftools.isofox.results;

import static com.hartwig.hmftools.isofox.common.FragmentType.ALT;
import static com.hartwig.hmftools.isofox.common.FragmentType.CHIMERIC;
import static com.hartwig.hmftools.isofox.common.FragmentType.DUPLICATE;
import static com.hartwig.hmftools.isofox.common.FragmentType.READ_THROUGH;
import static com.hartwig.hmftools.isofox.common.FragmentType.TOTAL;
import static com.hartwig.hmftools.isofox.common.FragmentType.TRANS_SUPPORTING;
import static com.hartwig.hmftools.isofox.common.FragmentType.UNSPLICED;
import static com.hartwig.hmftools.isofox.common.FragmentType.typeAsInt;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.DELIMITER;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.FLD_CHROMOSOME;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.FLD_GENE_ID;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.FLD_GENE_NAME;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.FLD_GENE_SET_ID;

import java.util.StringJoiner;

import com.hartwig.hmftools.common.ensemblcache.EnsemblGeneData;
import com.hartwig.hmftools.isofox.common.GeneCollection;
import com.hartwig.hmftools.isofox.common.GeneReadData;

import org.immutables.value.Value;

public class GeneResult
{
    public final EnsemblGeneData GeneData;
    public final String CollectionId;
    public final int IntronicLength;
    public final int TransCount;

    private double mUnsplicedAlloc;
    private double mFitResiduals;

    public GeneResult(final GeneCollection geneCollection, final GeneReadData geneReadData)
    {
        GeneData = geneReadData.GeneData;
        CollectionId = geneCollection.chrId();

        long exonicLength = geneReadData.calcExonicRegionLength();
        IntronicLength = (int)(GeneData.length() - exonicLength);
        TransCount = geneReadData.getTranscripts().size();

        mFitResiduals = 0;
        mUnsplicedAlloc = 0;
    }

    public double getUnsplicedAlloc() { return mUnsplicedAlloc; }
    public void setUnsplicedAllocation(double unsplicedAlloc) { mUnsplicedAlloc = unsplicedAlloc; }

    public void setFitResiduals(double residuals) { mFitResiduals = residuals; }
    public double getFitResiduals() { return mFitResiduals; }

    public static final String FLD_SUPPORTING_TRANS = "SupportingTrans";
    public static final String FLD_UNSPLICED = "Unspliced";

    public static String csvHeader()
    {
        return new StringJoiner(DELIMITER)
                .add(FLD_GENE_ID)
                .add(FLD_GENE_NAME)
                .add(FLD_CHROMOSOME)
                .add("GeneLength").add("IntronicLength").add("TransCount")
                .add("UnsplicedAlloc").add("FitResiduals")
                .add(FLD_GENE_SET_ID)
                .toString();
    }

    public String toCsv()
    {
        return new StringJoiner(DELIMITER)
                .add(GeneData.GeneId)
                .add(GeneData.GeneName)
                .add(GeneData.Chromosome)
                .add(String.valueOf(GeneData.length()))
                .add(String.valueOf(IntronicLength))
                .add(String.valueOf(TransCount))
                .add(String.format("%.1f", getUnsplicedAlloc()))
                .add(String.format("%.1f", getFitResiduals()))
                .add(CollectionId)
                .toString();
    }
}
