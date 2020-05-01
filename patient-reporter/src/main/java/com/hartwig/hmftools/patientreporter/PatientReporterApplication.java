package com.hartwig.hmftools.patientreporter;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.hartwig.hmftools.common.ecrf.projections.PatientTumorLocation;
import com.hartwig.hmftools.common.hospital.HospitalModel;
import com.hartwig.hmftools.common.hospital.HospitalModelFactory;
import com.hartwig.hmftools.common.lims.Lims;
import com.hartwig.hmftools.common.lims.LimsFactory;
import com.hartwig.hmftools.common.lims.LimsStudy;
import com.hartwig.hmftools.common.lims.LimsWide;
import com.hartwig.hmftools.common.lims.LimsWideFile;
import com.hartwig.hmftools.patientreporter.cfreport.CFReportWriter;
import com.hartwig.hmftools.patientreporter.qcfail.ImmutableQCFailReportData;
import com.hartwig.hmftools.patientreporter.qcfail.QCFailReason;
import com.hartwig.hmftools.patientreporter.qcfail.QCFailReport;
import com.hartwig.hmftools.patientreporter.qcfail.QCFailReportData;
import com.hartwig.hmftools.patientreporter.qcfail.QCFailReporter;
import com.hartwig.hmftools.patientreporter.reportingdb.ReportingDb;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;

public class PatientReporterApplication {

    private static final Logger LOGGER = LogManager.getLogger(PatientReporterApplication.class);

    public static final String VERSION = PatientReporterApplication.class.getPackage().getImplementationVersion();

    // Uncomment this line when generating an example report using PDFWriterTest
    //            public static final String VERSION = "7.11";

    public static void main(final String... args) throws ParseException, IOException {
        final Options options = PatientReporterConfig.createOptions();

        CommandLine cmd = createCommandLine(options, args);
        PatientReporterConfig config = PatientReporterConfig.createConfig(VERSION, cmd);


        if (!PatientReporterConfig.validInputForBaseReport(cmd)) {
            printUsageAndExit(options);
        }

        if (config.logDebug()) {
            Configurator.setRootLevel(Level.DEBUG);
        }

        LOGGER.info("Running patient reporter v{}", VERSION);
        SampleMetadata sampleMetadata = buildSampleMetadata(config);

        ReportWriter reportWriter = CFReportWriter.createProductionReportWriter();
        if (config.qcFail() && PatientReporterConfig.validInputForQCFailReport(cmd)) {
            LOGGER.info("Generating qc-fail report");
            QCFailReason reason = QCFailReason.fromIdentifier(config.qcFailReason());
            QCFailReporter reporter = new QCFailReporter(buildQCFailReportData(config));
            QCFailReport report = reporter.run(sampleMetadata, reason, config.comments(), config.correctedReport());
            String outputFilePath = generateOutputFilePathForPatientReport(config.outputDir(), report);
            reportWriter.writeQCFailReport(report, outputFilePath);

            ReportingDb.addQCFailReportToReportingDb(config.reportingDbTsv(), report);
        } else if (PatientReporterConfig.validInputForAnalysedReport(cmd)) {
            LOGGER.info("Generating patient report");
            AnalysedPatientReporter reporter = new AnalysedPatientReporter(buildAnalysedReportData(config));

            AnalysedPatientReport report = reporter.run(sampleMetadata,
                    config.purplePurityTsv(),
                    config.purpleQcFile(),
                    config.PurpleGeneCnvTsv(),
                    config.somaticVariantVcf(),
                    config.bachelorTsv(),
                    config.linxFusionTsv(),
                    config.linxDisruptionTsv(),
                    config.linxViralInsertionTsv(),
                    config.linxDriversTsv(),
                    config.chordPredictionTxt(),
                    config.circosFile(),
                    config.comments(),
                    config.correctedReport(),
                    config.unofficialReport());
            String outputFilePath = generateOutputFilePathForPatientReport(config.outputDir(), report);
            reportWriter.writeAnalysedPatientReport(report, outputFilePath);

            ReportingDb.addSequenceReportToReportingDb(config.reportingDbTsv(), report);
        } else {
            printUsageAndExit(options);
        }
    }

    @NotNull
    private static String generateOutputFilePathForPatientReport(@NotNull String reportDirectory, @NotNull PatientReport patientReport) {
        SampleReport sampleReport = patientReport.sampleReport();
        LimsStudy study = LimsStudy.fromSampleId(sampleReport.tumorSampleId());

        String filePrefix = study == LimsStudy.CORE
                ? sampleReport.tumorSampleId() + "_" + sampleReport.hospitalPatientId().replace(" ", "_")
                : sampleReport.tumorSampleId();

        String fileSuffix = patientReport.isCorrectedReport() ? "_corrected.pdf" : ".pdf";

        return reportDirectory + File.separator + filePrefix + "_hmf_report" + fileSuffix;
    }

    @NotNull
    private static SampleMetadata buildSampleMetadata(@NotNull PatientReporterConfig config) {
        SampleMetadata sampleMetadata = ImmutableSampleMetadata.builder()
                .refSampleId(config.refSampleID())
                .refSampleBarcode(config.refSampleBarcode())
                .tumorSampleId(config.tumorSampleId())
                .tumorSampleBarcode(config.tumorSampleBarcode())
                .build();

        LOGGER.info("Printing sample meta data for {}", sampleMetadata.tumorSampleId());
        LOGGER.info(" Tumor sample barcode: {}", sampleMetadata.tumorSampleBarcode());
        LOGGER.info(" Ref sample: {}", sampleMetadata.refSampleId());
        LOGGER.info(" Ref sample barcode: {}", sampleMetadata.refSampleBarcode());

        return sampleMetadata;
    }

    @NotNull
    private static QCFailReportData buildQCFailReportData(@NotNull PatientReporterConfig config) throws IOException {
        String tumorLocationCsv = config.tumorLocationCsv();
        List<PatientTumorLocation> patientTumorLocations = PatientTumorLocation.readRecords(tumorLocationCsv);
        LOGGER.info("Loaded tumor locations for {} patients from {}", patientTumorLocations.size(), tumorLocationCsv);

        String limsDirectory = config.limsDir();
        Lims lims = LimsFactory.fromLimsDirectory(limsDirectory);
        LOGGER.info("Loaded LIMS data for {} samples from {}", lims.sampleBarcodeCount(), limsDirectory);

        String hospitalsDirectory = config.hospitalDir();
        HospitalModel hospitalModel = HospitalModelFactory.fromHospitalDirectory(hospitalsDirectory);
        LOGGER.info("Loaded data for {} hospitals from {}", hospitalModel.hospitalCount(), hospitalsDirectory);

        LOGGER.info("Reading lims wide file {}", config.contactWideTsv());
        LimsWide limsWide = LimsWideFile.read(config.contactWideTsv());

        return ImmutableQCFailReportData.builder()
                .patientTumorLocations(patientTumorLocations)
                .limsModel(lims)
                .limsWideModel(limsWide)
                .hospitalModel(hospitalModel)
                .signaturePath(config.signature())
                .logoRVAPath(config.rvaLogo())
                .logoCompanyPath(config.companyLogo())
                .build();
    }

    @NotNull
    private static AnalysedReportData buildAnalysedReportData(@NotNull PatientReporterConfig config) throws IOException {
        return AnalysedReportDataLoader.buildFromFiles(buildQCFailReportData(config),
                config.knowledgebaseDir(),
                config.germlineGenesCsv(),
                config.sampleSummaryTsv());
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull Options options, @NotNull String... args) throws ParseException {
        return new DefaultParser().parse(options, args);
    }

    private static void printUsageAndExit(@NotNull Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Patient-Reporter", options);
        System.exit(1);
    }
}
