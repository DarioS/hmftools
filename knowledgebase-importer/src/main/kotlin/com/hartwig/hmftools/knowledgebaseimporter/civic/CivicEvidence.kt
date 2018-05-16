package com.hartwig.hmftools.knowledgebaseimporter.civic

import com.hartwig.hmftools.knowledgebaseimporter.output.Actionability
import com.hartwig.hmftools.knowledgebaseimporter.output.HmfLevel
import com.hartwig.hmftools.knowledgebaseimporter.output.HmfResponse
import org.apache.commons.csv.CSVRecord

data class CivicEvidence(private val csvRecord: CSVRecord) {
    val cancerType: String = csvRecord["disease"].orEmpty()
    val doid: String = csvRecord["doid"].orEmpty()
    val drugs: List<String> = csvRecord["drugs"].orEmpty().split(",").map { it.trim() }
    val type: String = csvRecord["evidence_type"].orEmpty()
    val direction: String = csvRecord["evidence_direction"].orEmpty()
    val level: String = csvRecord["evidence_level"].orEmpty()
    val significance: String = csvRecord["clinical_significance"].orEmpty()
    val actionabilityItems: List<Actionability> = Actionability("civic", listOf(cancerType), drugs, level, significance, type,
                                                                HmfLevel(level), HmfResponse(significance))
}
