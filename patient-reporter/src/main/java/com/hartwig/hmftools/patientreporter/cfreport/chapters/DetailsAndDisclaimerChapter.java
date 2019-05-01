package com.hartwig.hmftools.patientreporter.cfreport.chapters;

import com.hartwig.hmftools.patientreporter.AnalysedPatientReport;
import com.hartwig.hmftools.patientreporter.SampleReport;
import com.hartwig.hmftools.patientreporter.cfreport.ReportResources;
import com.hartwig.hmftools.patientreporter.cfreport.components.ReportSignature;
import com.hartwig.hmftools.patientreporter.cfreport.components.TableUtil;
import com.hartwig.hmftools.patientreporter.cfreport.data.DataUtil;
import com.hartwig.hmftools.patientreporter.report.pages.SampleDetailsPage;
import com.itextpdf.io.IOException;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.property.UnitValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class DetailsAndDisclaimerChapter implements ReportChapter {

    private static final Logger LOGGER = LogManager.getLogger(SampleDetailsPage.class);

    private final AnalysedPatientReport patientReport;

    public DetailsAndDisclaimerChapter(@NotNull final AnalysedPatientReport patientReport) {
        this.patientReport = patientReport;
    }

    @Override
    @NotNull
    public String getName() {
        return "Sample details & disclaimers";
    }

    public boolean isFullWidth() {
        return false;
    }

    @Override
    public final void render(@NotNull final Document reportDocument) throws IOException {

        // Main content
        Table table = new Table(UnitValue.createPercentArray(new float[] {1, 0.1f, 1}));
        table.setWidth(getContentWidth());
        table.addCell(TableUtil.getLayoutCell()
                .add(createSampleDetailsDiv(patientReport)));
        table.addCell(TableUtil.getLayoutCell()); // Spacer
        table.addCell(TableUtil.getLayoutCell()
                .add(createDisclaimerDiv()));
        reportDocument.add(table);

        reportDocument.add(ReportSignature.createEndOfReportIndication());
        reportDocument.add(ReportSignature.createSignatureDiv(patientReport.logoRVAPath(), patientReport.signaturePath()));

    }

    @NotNull
    private static Div createSampleDetailsDiv(@NotNull final AnalysedPatientReport patientReport) {

        final SampleReport sampleReport = patientReport.sampleReport();

        String recipient = sampleReport.recipient();
        if (recipient == null) {
            LOGGER.warn("No recipient address present for sample " + sampleReport.sampleId());
            recipient = DataUtil.NAString;
        }

        Div div = new Div();

        // Heading
        div.add(new Paragraph("Sample details")
                .addStyle(ReportResources.smallBodyHeadingStyle()));

        // Content
        div.add(createContentParagraph("The samples have been sequenced at ", ReportResources.HARTWIG_ADDRESS));
        div.add(createContentParagraph("The samples have been analyzed by Next Generation Sequencing "));
        div.add(createContentParagraph("This experiment is performed on the tumor sample which arrived on ", DataUtil.formatDate(sampleReport.tumorArrivalDate())));
        div.add(createContentParagraph("The pathology tumor percentage for this sample is " + sampleReport.pathologyTumorPercentage()));
        div.add(createContentParagraph("This experiment is performed on the blood sample which arrived on ", DataUtil.formatDate(sampleReport.bloodArrivalDate())));
        div.add(createContentParagraph("This experiment is performed according to lab procedures: " + sampleReport.labProcedures()));
        div.add(createContentParagraph("This report is generated and verified by: " + patientReport.user()));
        div.add(createContentParagraph("This report is addressed at: " + recipient));
        patientReport.comments().ifPresent(comments -> div.add(createContentParagraph("Comments: " + comments)));

        return div;

    }

    @NotNull
    private static Div createDisclaimerDiv() {

        Div div = new Div();

        // Heading
        div.add(new Paragraph("Disclaimer")
                .addStyle(ReportResources.smallBodyHeadingStyle()));

        div.add(createContentParagraph("The data on which this report is based is generated from tests that are performed under ISO/ICE-17025:2005 accreditation."));
        div.add(createContentParagraph("The analysis done for this report has passed all internal quality controls."));
        div.add(createContentParagraph("For feedback or complaints please contact ", ReportResources.CONTACT_EMAIL_QA));
        div.add(createContentParagraph("For general questions, please contact us at ", ReportResources.CONTACT_EMAIL_GENERAL));

        return div;

    }

    @NotNull
    private static Paragraph createContentParagraph(@NotNull String text) {
        return new Paragraph(text)
                .addStyle(ReportResources.smallBodyTextStyle())
                .setFixedLeading(ReportResources.BODY_TEXT_LEADING);
    }

    @NotNull
    private static Paragraph createContentParagraph(@NotNull String regularPart, @NotNull String boldPart) {
        return createContentParagraph(regularPart)
                .add(new Text(boldPart)
                        .addStyle(ReportResources.smallBodyBoldTextStyle()))
                .setFixedLeading(ReportResources.BODY_TEXT_LEADING);
    }

}
