package com.hartwig.hmftools.knowledgebasegenerator;

import java.io.IOException;
import java.util.List;

import com.hartwig.hmftools.knowledgebasegenerator.actionability.gene.ActionableGene;
import com.hartwig.hmftools.knowledgebasegenerator.cnv.CnvExtractor;
import com.hartwig.hmftools.knowledgebasegenerator.eventtype.EventType;
import com.hartwig.hmftools.knowledgebasegenerator.eventtype.EventTypeAnalyzer;
import com.hartwig.hmftools.vicc.datamodel.ViccEntry;
import com.hartwig.hmftools.vicc.reader.ViccJsonReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ViccAmpsDelExtractorTestApplication {
    private static final Logger LOGGER = LogManager.getLogger(ViccAmpsDelExtractorTestApplication.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        String viccJsonPath = System.getProperty("user.home") + "/hmf/projects/vicc/all.json";

        String source = "civic";
        LOGGER.info("Reading VICC json from {} with source '{}'", viccJsonPath, source);
        List<ViccEntry> viccEntries = ViccJsonReader.readSingleKnowledgebase(viccJsonPath, source);
        LOGGER.info("Read {} entries", viccEntries.size());

        for (ViccEntry viccEntry : viccEntries) {


            List<EventType> eventType = EventTypeAnalyzer.determineEventType(viccEntry);
        //    LOGGER.info("eventType: " + eventType);

           // for (EventType type: eventType) {
                // Generating actionable event and known events

               // ActionableGene actionableGene = CnvExtractor.extractingCNVs(viccEntry, type);
            //}
        }
    }
}
