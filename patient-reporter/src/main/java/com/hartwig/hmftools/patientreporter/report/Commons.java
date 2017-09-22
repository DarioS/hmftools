package com.hartwig.hmftools.patientreporter.report;

import static net.sf.dynamicreports.report.builder.DynamicReports.report;
import static net.sf.dynamicreports.report.builder.DynamicReports.stl;

import java.awt.Color;

import org.jetbrains.annotations.NotNull;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.constant.HorizontalTextAlignment;
import net.sf.dynamicreports.report.constant.VerticalTextAlignment;

public class Commons {
    // MIVO: change font to monospace to remove text truncation issue (see gene panel type column for example)
    private static final String FONT = "Times New Roman";
    private static final Color BORKIE_COLOR = new Color(221, 235, 247);
    public static final String DATE_TIME_FORMAT = "dd-MMM-yyyy";
    private static final int TABLE_PADDING = 1;
    public static final int SECTION_VERTICAL_GAP = 25;

    @NotNull
    public static StyleBuilder tableHeaderStyle() {
        return fontStyle().bold()
                .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)
                .setVerticalTextAlignment(VerticalTextAlignment.MIDDLE)
                .setFontSize(10)
                .setBorder(stl.pen1Point())
                .setBackgroundColor(BORKIE_COLOR)
                .setPadding(TABLE_PADDING);
    }

    @NotNull
    static StyleBuilder dataStyle() {
        return fontStyle().setFontSize(8)
                .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)
                .setVerticalTextAlignment(VerticalTextAlignment.MIDDLE)
                .setPadding(TABLE_PADDING);
    }

    @NotNull
    public static StyleBuilder dataTableStyle() {
        return dataStyle().setBorder(stl.pen1Point());
    }

    @NotNull
    public static StyleBuilder fontStyle() {
        return stl.style().setFontName(FONT);
    }

    @NotNull
    static JasperReportBuilder baseTable() {
        return report().setColumnStyle(dataStyle()).setColumnTitleStyle(tableHeaderStyle()).highlightDetailEvenRows();
    }

    @NotNull
    public static JasperReportBuilder smallTable() {
        return report().setColumnStyle(dataStyle().setFontSize(6)).setColumnTitleStyle(tableHeaderStyle()).highlightDetailEvenRows();
    }

    @NotNull
    static StyleBuilder sectionHeaderStyle() {
        return fontStyle().bold().setFontSize(12).setHorizontalTextAlignment(HorizontalTextAlignment.CENTER);
    }

    @NotNull
    static StyleBuilder linkStyle() {
        return dataStyle().setForegroundColor(Color.BLUE);
    }
}
