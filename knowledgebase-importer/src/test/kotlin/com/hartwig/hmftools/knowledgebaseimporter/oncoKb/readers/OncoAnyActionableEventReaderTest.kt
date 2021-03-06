package com.hartwig.hmftools.knowledgebaseimporter.oncoKb.readers

import com.hartwig.hmftools.knowledgebaseimporter.knowledgebases.ProteinAnnotation
import com.hartwig.hmftools.knowledgebaseimporter.knowledgebases.SomaticEvent
import com.hartwig.hmftools.knowledgebaseimporter.knowledgebases.events.HgvsVariantType
import com.hartwig.hmftools.knowledgebaseimporter.knowledgebases.readers.KnowledgebaseEventReader
import com.hartwig.hmftools.knowledgebaseimporter.knowledgebases.readers.ProteinAnnotationReader
import com.hartwig.hmftools.knowledgebaseimporter.oncoKb.input.OncoActionableInput
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

class OncoAnyActionableEventReaderTest : StringSpec() {
    private val actionable = OncoActionableInput("ENST00000000", "BRAF", "", "", "", "")
    private val individualReaders = KnowledgebaseEventReader("", ProteinAnnotationReader)
    private val reader = OncoAnyActionableEventReader(individualReaders)

    init {
        "can read input with 2 protein alterations" {
            reader.read(actionable.copy(Alteration = "V600E/V600K")) shouldBe
                    listOf(ProteinAnnotation(actionable.transcript, "V600E", HgvsVariantType.SUBSTITUTION),
                           ProteinAnnotation(actionable.transcript, "V600K", HgvsVariantType.SUBSTITUTION))
            reader.read(actionable.copy(Alteration = "V600E / V600K")) shouldBe
                    listOf(ProteinAnnotation(actionable.transcript, "V600E", HgvsVariantType.SUBSTITUTION),
                           ProteinAnnotation(actionable.transcript, "V600K", HgvsVariantType.SUBSTITUTION))
        }

        "does not read 2 protein alterations with illegal separator" {
            reader.read(actionable.copy(Alteration = "V600E;V600K")) shouldBe emptyList<SomaticEvent>()
            reader.read(actionable.copy(Alteration = "V600E ; V600K")) shouldBe emptyList<SomaticEvent>()
        }
    }
}
