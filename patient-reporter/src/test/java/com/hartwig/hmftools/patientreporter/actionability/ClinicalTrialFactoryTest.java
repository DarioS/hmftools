package com.hartwig.hmftools.patientreporter.actionability;


import static com.hartwig.hmftools.patientreporter.PatientReporterTestFactory.createTestEvidenceBuilder;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.actionability.ActionabilitySource;
import com.hartwig.hmftools.common.actionability.ClinicalTrial;
import com.hartwig.hmftools.common.actionability.EvidenceItem;

import org.junit.Test;

public class ClinicalTrialFactoryTest {

    @Test
    public void canExtractClinicalTrials() {
        EvidenceItem item = createTestEvidenceBuilder().event("event")
                .drug("acronym")
                .source(ActionabilitySource.ICLUSION)
                .reference("reference")
                .isOnLabel(true)
                .build();

        List<ClinicalTrial> trial = ClinicalTrialFactory.extractOnLabelTrials(Lists.newArrayList(item));

        assertEquals(1, trial.size());
        assertEquals("event", trial.get(0).event());
        assertEquals("acronym", trial.get(0).acronym());
        assertEquals("reference", trial.get(0).reference());
        assertEquals(ActionabilitySource.ICLUSION, trial.get(0).source());
    }
}