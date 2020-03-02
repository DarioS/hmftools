package com.hartwig.hmftools.knowledgebasegenerator.hotspot;

import static org.junit.Assert.assertFalse;

import static junit.framework.TestCase.assertTrue;

import org.apache.logging.log4j.util.Strings;
import org.junit.Test;

public class HotspotExtractorTest {

    @Test
    public void canAssessWhetherFeatureIsProteinAnnotation() {
        assertTrue(HotspotExtractor.isProteinAnnotation("L2230V"));
        assertTrue(HotspotExtractor.isProteinAnnotation("V5del"));

        assertFalse(HotspotExtractor.isProteinAnnotation(Strings.EMPTY));
        assertFalse(HotspotExtractor.isProteinAnnotation("truncating"));
        assertFalse(HotspotExtractor.isProteinAnnotation("20LtoV"));
        assertFalse(HotspotExtractor.isProteinAnnotation("L20"));
        assertFalse(HotspotExtractor.isProteinAnnotation("LP"));
        assertFalse(HotspotExtractor.isProteinAnnotation("L"));
        assertFalse(HotspotExtractor.isProteinAnnotation("L2"));
        assertFalse(HotspotExtractor.isProteinAnnotation("L20Pdel5"));
    }
}