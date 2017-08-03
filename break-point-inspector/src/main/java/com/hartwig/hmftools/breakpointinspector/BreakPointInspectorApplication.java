package com.hartwig.hmftools.breakpointinspector;

import static com.hartwig.hmftools.breakpointinspector.Util.prefixList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;

public class BreakPointInspectorApplication {

    private static final String REF_PATH = "ref";
    private static final String REF_SLICE = "ref_slice";
    private static final String TUMOR_PATH = "tumor";
    private static final String TUMOR_SLICE = "tumor_slice";
    private static final String PROXIMITY = "proximity";
    private static final String VCF = "vcf";
    private static final String VCF_OUT = "output_vcf";

    private static Options createOptions() {
        final Options options = new Options();
        options.addOption(REF_PATH, true, "the Reference BAM (indexed)");
        options.addOption(REF_SLICE, true, "the sliced Reference BAM to output");
        options.addOption(TUMOR_PATH, true, "the Tumor BAM (indexed)");
        options.addOption(TUMOR_SLICE, true, "the sliced Tumor BAM to output");
        options.addOption(PROXIMITY, true, "distance to scan around breakpoint");
        options.addOption(VCF, true, "Manta VCF file to batch inspect (can be compressed)");
        options.addOption(VCF_OUT, true, "VCF output file (annotated and filtered version of Manta VCF)");
        return options;
    }

    private static CommandLine createCommandLine(@NotNull final Options options, @NotNull final String... args) throws ParseException {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

    private static void printHelpAndExit(final Options options) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Break-Point-Inspector", "A second layer of filtering on top of Manta", options, "", true);
        System.exit(1);
    }

    private static List<String> parseMantaPRSR(final Genotype genotype) {
        String pr = (String) genotype.getExtendedAttribute("PR", "0,0");
        String sr = (String) genotype.getExtendedAttribute("SR", "0,0");
        return Stream.concat(Arrays.stream(pr.split(",")), Arrays.stream(sr.split(","))).collect(Collectors.toList());
    }

    public static void main(final String... args) throws ParseException, IOException {

        final Options options = createOptions();
        try {
            final CommandLine cmd = createCommandLine(options, args);

            // grab arguments
            final String refPath = cmd.getOptionValue(REF_PATH);
            final String refSlicePath = cmd.getOptionValue(REF_SLICE);
            final String tumorPath = cmd.getOptionValue(TUMOR_PATH);
            final String tumorSlicePath = cmd.getOptionValue(TUMOR_SLICE);
            final String vcfPath = cmd.getOptionValue(VCF);
            final int range = Integer.parseInt(cmd.getOptionValue(PROXIMITY, "500"));

            if (refPath == null || tumorPath == null || vcfPath == null) {
                printHelpAndExit(options);
                return;
            }

            // load the files
            final File tumorBAM = new File(tumorPath);
            final SamReader tumorReader = SamReaderFactory.makeDefault().open(tumorBAM);
            final File refBAM = new File(refPath);
            final SamReader refReader = SamReaderFactory.makeDefault().open(refBAM);

            final File tumorSliceBAM;
            SAMFileWriter tumorWriter = null;
            if (tumorSlicePath != null) {
                tumorSliceBAM = new File(tumorSlicePath);
                tumorWriter = new SAMFileWriterFactory().makeBAMWriter(tumorReader.getFileHeader(), false, tumorSliceBAM);
            }

            final File refSliceBAM;
            SAMFileWriter refWriter = null;
            if (refSlicePath != null) {
                refSliceBAM = new File(refSlicePath);
                refWriter = new SAMFileWriterFactory().makeBAMWriter(refReader.getFileHeader(), false, refSliceBAM);
            }

            final File vcfFile = new File(vcfPath);
            final VCFFileReader vcfReader = new VCFFileReader(vcfFile, false);

            final String vcfOutputPath = cmd.getOptionValue(VCF_OUT);
            final VariantContextWriter vcfWriter;
            if (vcfOutputPath != null) {
                final VCFHeader header = vcfReader.getFileHeader();
                vcfWriter = new VariantContextWriterBuilder().setReferenceDictionary(header.getSequenceDictionary())
                        .setOutputFile(vcfOutputPath)
                        .build();
                // TODO: add HMF info meta-data
                Filter.updateHeader(header);
                vcfWriter.writeHeader(header);
            } else {
                vcfWriter = null;
            }

            // work out the reference sample
            final List<String> samples = vcfReader.getFileHeader().getGenotypeSamples();
            final Predicate<String> isRef = s -> s.endsWith("R") || s.endsWith("BL");
            final String refSampleName = samples.stream().filter(isRef).findFirst().orElse(null);
            final String tumorSampleName = samples.stream().filter(s -> s.endsWith("T") || !isRef.test(s)).findFirst().orElse(null);
            if (refSampleName == null || tumorSampleName == null) {
                System.err.println("could not determine tumor and sample from VCF");
                System.exit(1);
                return;
            }

            // output the header
            final ArrayList<String> header =
                    Lists.newArrayList("ID", "SVTYPE", "ORIENTATION", "MANTA_BP1", "MANTA_BP2", "MANTA_SVLEN", "MANTA_REF_PR_NORMAL",
                            "MANTA_REF_PR_SUPPORT", "MANTA_REF_SR_NORMAL", "MANTA_REF_SR_SUPPORT", "MANTA_TUMOR_PR_NORMAL",
                            "MANTA_TUMOR_PR_SUPPORT", "MANTA_TUMOR_SR_NORMAL", "MANTA_TUMOR_SR_SUPPORT", "MANTA_HOMSEQ", "MANTA_INSSEQ");
            header.addAll(prefixList(SampleStats.GetHeader(), "REF_"));
            header.addAll(prefixList(SampleStats.GetHeader(), "TUMOR_"));
            header.add("BPI_BP1");
            header.add("BPI_BP2");
            header.add("FILTER");
            System.out.println(String.join("\t", header));

            final Map<String, VariantContext> variantMap = new HashMap<>();
            for (VariantContext variant : vcfReader) {

                variantMap.put(variant.getID(), variant);

                final VariantContext mateVariant = variant;
                if (variant.hasAttribute("MATEID")) {
                    variant = variantMap.get(variant.getAttributeAsString("MATEID", ""));
                    if (variant == null) {
                        continue;
                    }
                }

                final String location = variant.getContig() + ":" + Integer.toString(variant.getStart());
                Location location1 = Location.parseLocationString(location, tumorReader.getFileHeader().getSequenceDictionary());

                // uncertainty
                final List<Integer> CIPOS = variant.getAttributeAsIntList("CIPOS", 0);
                final Range uncertainty1 = CIPOS.size() == 2 ? new Range(CIPOS.get(0), CIPOS.get(1)) : new Range(0, 0);
                final List<Integer> CIEND = variant.getAttributeAsIntList("CIEND", 0);
                Range uncertainty2 = CIEND.size() == 2 ? new Range(CIEND.get(0), CIEND.get(1)) : new Range(0, 0);

                HMFVariantType svType;
                Location location2;
                switch (variant.getStructuralVariantType()) {
                    case INV:
                        if (variant.hasAttribute("INV3")) {
                            svType = HMFVariantType.INV3;
                        } else if (variant.hasAttribute("INV5")) {
                            svType = HMFVariantType.INV5;
                        } else {
                            System.err.println(variant.getID() + " : expected either INV3 or INV5 flag");
                            continue;
                        }
                        location2 = location1.add(Math.abs(variant.getAttributeAsInt("SVLEN", 0)));
                        break;
                    case DEL:
                        svType = HMFVariantType.DEL;
                        location2 = location1.add(Math.abs(variant.getAttributeAsInt("SVLEN", 0)));
                        break;
                    case DUP:
                        svType = HMFVariantType.DUP;
                        location2 = location1.add(Math.abs(variant.getAttributeAsInt("SVLEN", 0)));
                        break;
                    case BND:

                        // get the CIPOS from the mate
                        final List<Integer> MATE_CIPOS = mateVariant.getAttributeAsIntList("CIPOS", 0);
                        uncertainty2 = MATE_CIPOS.size() == 2 ? new Range(MATE_CIPOS.get(0), MATE_CIPOS.get(1)) : new Range(0, 0);

                        // process the breakend string
                        final String call = variant.getAlternateAllele(0).getDisplayString();
                        final String[] leftSplit = call.split("\\]");
                        final String[] rightSplit = call.split("\\[");
                        if (leftSplit.length >= 2) {
                            location2 = Location.parseLocationString(leftSplit[1], tumorReader.getFileHeader().getSequenceDictionary());
                            if (leftSplit[0].length() > 0) {
                                svType = HMFVariantType.INV3;
                            } else {
                                svType = HMFVariantType.DUP;
                            }
                        } else if (rightSplit.length >= 2) {
                            location2 = Location.parseLocationString(rightSplit[1], tumorReader.getFileHeader().getSequenceDictionary());
                            if (rightSplit[0].length() > 0) {
                                svType = HMFVariantType.DEL;
                            } else {
                                svType = HMFVariantType.INV5;
                            }
                        } else {
                            System.err.println(variant.getID() + " : could not parse breakpoint");
                            continue;
                        }
                        break;
                    default:
                        System.err.println(variant.getID() + " : UNEXPECTED SVTYPE=" + variant.getStructuralVariantType());
                        continue;
                }

                final List<String> fields = Lists.newArrayList(variant.getID(), variant.getStructuralVariantType().toString(),
                        HMFVariantType.getOrientation(svType), location1.toString(), location2.toString(),
                        variant.getAttributeAsString("SVLEN", ""));

                fields.addAll(parseMantaPRSR(variant.getGenotype(refSampleName)));
                fields.addAll(parseMantaPRSR(variant.getGenotype(tumorSampleName)));

                fields.add(variant.getAttributeAsString("HOMSEQ", ""));
                fields.add(variant.getAttributeAsString("SVINSSEQ", ""));

                final HMFVariantContext ctx = new HMFVariantContext(location1, location2, svType);
                ctx.Filter.addAll(variant.getFilters());
                ctx.Uncertainty1 = uncertainty1;
                ctx.Uncertainty2 = uncertainty2;

                switch (ctx.Type) {
                    case DEL:
                        ctx.OrientationBP1 = 1;
                        ctx.OrientationBP2 = -1;
                        break;
                    case INV3:
                        ctx.OrientationBP1 = 1;
                        ctx.OrientationBP2 = 1;
                        break;
                    case INV5:
                        ctx.OrientationBP1 = -1;
                        ctx.OrientationBP2 = -1;
                        break;
                    case DUP:
                        ctx.OrientationBP1 = -1;
                        ctx.OrientationBP2 = 1;
                        break;
                }

                final StructuralVariantResult result =
                        Analysis.processStructuralVariant(refReader, refWriter, tumorReader, tumorWriter, ctx, range);

                fields.addAll(result.RefStats.GetData());
                fields.addAll(result.TumorStats.GetData());
                fields.add(ObjectUtils.firstNonNull(result.Breakpoints.getLeft(), "err").toString());
                fields.add(ObjectUtils.firstNonNull(result.Breakpoints.getRight(), "err").toString());
                fields.add(result.FilterString);

                System.out.println(String.join("\t", fields));

                final Set<String> filters = variant.getCommonInfo().getFiltersMaybeNull();
                if (filters != null) {
                    filters.clear();
                }
                variant.getCommonInfo().addFilters(result.Filters);

                if (vcfWriter != null) {
                    vcfWriter.add(variant);
                }
            }

            // close all the files
            refReader.close();
            tumorReader.close();
            if (refWriter != null) {
                refWriter.close();
            }
            if (tumorWriter != null) {
                tumorWriter.close();
            }
            if (vcfWriter != null) {
                vcfWriter.close();
            }

        } catch (ParseException e) {
            printHelpAndExit(options);
            System.exit(1);
        }
    }
}
