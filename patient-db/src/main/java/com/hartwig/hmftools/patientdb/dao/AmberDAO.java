package com.hartwig.hmftools.patientdb.dao;

import static com.hartwig.hmftools.patientdb.Config.DB_BATCH_INSERT_SIZE;
import static com.hartwig.hmftools.patientdb.database.hmfpatients.Tables.AMBER;
import static com.hartwig.hmftools.patientdb.database.hmfpatients.Tables.AMBERPATIENT;
import static com.hartwig.hmftools.patientdb.database.hmfpatients.Tables.AMBERSAMPLE;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.amber.AmberBAF;
import com.hartwig.hmftools.common.amber.AmberPatient;
import com.hartwig.hmftools.common.amber.AmberSample;
import com.hartwig.hmftools.common.amber.ImmutableAmberPatient;
import com.hartwig.hmftools.common.amber.ImmutableAmberSample;
import com.hartwig.hmftools.common.utils.Doubles;

import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep5;
import org.jooq.InsertValuesStep7;
import org.jooq.InsertValuesStepN;
import org.jooq.Record;
import org.jooq.Result;

class AmberDAO {

    @NotNull
    private final DSLContext context;

    AmberDAO(@NotNull final DSLContext context) {
        this.context = context;
    }

    private static void addRecord(@NotNull Timestamp timestamp, @NotNull InsertValuesStep5 inserter, @NotNull String sample,
            @NotNull AmberBAF variant) {
        inserter.values(sample, variant.chromosome(), variant.position(), Doubles.greaterThan(variant.normalBAF(), 0), timestamp);
    }

    void truncatePatients() {
        context.delete(AMBERPATIENT).execute();
    }

    @NotNull
    List<AmberPatient> readPatients() {
        final List<AmberPatient> result = Lists.newArrayList();
        final Result<Record> queryResult = context.select().from(AMBERPATIENT).fetch();
        for (Record record : queryResult) {
            result.add(ImmutableAmberPatient.builder()
                    .patientId(record.get(AMBERPATIENT.PATIENTID))
                    .sample(record.get(AMBERPATIENT.FIRSTSAMPLEID))
                    .otherSample(record.get(AMBERPATIENT.SECONDSAMPLEID))
                    .matches(record.get(AMBERPATIENT.MATCHES))
                    .sites(record.get(AMBERPATIENT.SITES))
                    .build());
        }

        return result;
    }

    @NotNull
    List<AmberSample> readSamples() {
        final List<AmberSample> result = Lists.newArrayList();
        final Result<Record> queryResult = context.select().from(AMBERSAMPLE).fetch();

        for (Record record : queryResult) {
            int size = record.valuesRow().size();
            final byte[] entries = new byte[size - 2];
            for (int i = 0; i < size - 2; i++) {
                entries[i] = record.get(i + 2, Byte.class);
            }

            result.add(ImmutableAmberSample.builder().sampleId(record.get(AMBERSAMPLE.SAMPLEID)).entries(entries).build());
        }

        return result;
    }

    void writePatients(String sample, List<AmberPatient> mapping) {
        if (mapping.isEmpty()) {
            return;
        }

        final Set<Integer> patientIds = mapping.stream().map(AmberPatient::patientId).collect(Collectors.toSet());
        if (patientIds.size() != 1) {
            throw new IllegalArgumentException("Only one patient id permitted");
        }

        Set<String> samples = Sets.newHashSet(sample);
        mapping.forEach(x -> {
            samples.add(x.sample());
            samples.add(x.otherSample());
        });

        context.delete(AMBERPATIENT).where(AMBERPATIENT.FIRSTSAMPLEID.in(samples)).execute();
        context.delete(AMBERPATIENT).where(AMBERPATIENT.SECONDSAMPLEID.in(samples)).execute();

        Timestamp timestamp = new Timestamp(new Date().getTime());

        InsertValuesStep7 inserter = context.insertInto(AMBERPATIENT,
                AMBERPATIENT.MODIFIED,
                AMBERPATIENT.PATIENTID,
                AMBERPATIENT.FIRSTSAMPLEID,
                AMBERPATIENT.SECONDSAMPLEID,
                AMBERPATIENT.MATCHES,
                AMBERPATIENT.SITES,
                AMBERPATIENT.LIKELIHOOD);

        for (AmberPatient amberPatient : mapping) {
            inserter.values(timestamp,
                    amberPatient.patientId(),
                    amberPatient.sample(),
                    amberPatient.otherSample(),
                    amberPatient.matches(),
                    amberPatient.sites(),
                    amberPatient.likelihood());
        }

        inserter.execute();
    }

    void writeIdentity(AmberSample identity) {
        byte[] entries = identity.entries();
        if (entries.length != AMBERSAMPLE.fields().length - 2) {
            throw new IllegalArgumentException(
                    "Identity has " + entries.length + " sites but " + (AMBERSAMPLE.fields().length - 2) + " are required");

        }

        context.delete(AMBERSAMPLE).where(AMBERSAMPLE.SAMPLEID.eq(identity.sampleId())).execute();
        InsertValuesStepN inserter = context.insertInto(AMBERSAMPLE,
                AMBERSAMPLE.MODIFIED,
                AMBERSAMPLE.SAMPLEID,
                AMBERSAMPLE.SITE1,
                AMBERSAMPLE.SITE2,
                AMBERSAMPLE.SITE3,
                AMBERSAMPLE.SITE4,
                AMBERSAMPLE.SITE5,
                AMBERSAMPLE.SITE6,
                AMBERSAMPLE.SITE7,
                AMBERSAMPLE.SITE8,
                AMBERSAMPLE.SITE9,
                AMBERSAMPLE.SITE10,
                AMBERSAMPLE.SITE11,
                AMBERSAMPLE.SITE12,
                AMBERSAMPLE.SITE13,
                AMBERSAMPLE.SITE14,
                AMBERSAMPLE.SITE15,
                AMBERSAMPLE.SITE16,
                AMBERSAMPLE.SITE17,
                AMBERSAMPLE.SITE18,
                AMBERSAMPLE.SITE19,
                AMBERSAMPLE.SITE20,
                AMBERSAMPLE.SITE21,
                AMBERSAMPLE.SITE22,
                AMBERSAMPLE.SITE23,
                AMBERSAMPLE.SITE24,
                AMBERSAMPLE.SITE25,
                AMBERSAMPLE.SITE26,
                AMBERSAMPLE.SITE27,
                AMBERSAMPLE.SITE28,
                AMBERSAMPLE.SITE29,
                AMBERSAMPLE.SITE30,
                AMBERSAMPLE.SITE31,
                AMBERSAMPLE.SITE32,
                AMBERSAMPLE.SITE33,
                AMBERSAMPLE.SITE34,
                AMBERSAMPLE.SITE35,
                AMBERSAMPLE.SITE36,
                AMBERSAMPLE.SITE37,
                AMBERSAMPLE.SITE38,
                AMBERSAMPLE.SITE39,
                AMBERSAMPLE.SITE40,
                AMBERSAMPLE.SITE41,
                AMBERSAMPLE.SITE42,
                AMBERSAMPLE.SITE43,
                AMBERSAMPLE.SITE44,
                AMBERSAMPLE.SITE45,
                AMBERSAMPLE.SITE46,
                AMBERSAMPLE.SITE47,
                AMBERSAMPLE.SITE48,
                AMBERSAMPLE.SITE49,
                AMBERSAMPLE.SITE50,
                AMBERSAMPLE.SITE51,
                AMBERSAMPLE.SITE52,
                AMBERSAMPLE.SITE53,
                AMBERSAMPLE.SITE54,
                AMBERSAMPLE.SITE55,
                AMBERSAMPLE.SITE56,
                AMBERSAMPLE.SITE57,
                AMBERSAMPLE.SITE58,
                AMBERSAMPLE.SITE59,
                AMBERSAMPLE.SITE60,
                AMBERSAMPLE.SITE61,
                AMBERSAMPLE.SITE62,
                AMBERSAMPLE.SITE63,
                AMBERSAMPLE.SITE64,
                AMBERSAMPLE.SITE65,
                AMBERSAMPLE.SITE66,
                AMBERSAMPLE.SITE67,
                AMBERSAMPLE.SITE68,
                AMBERSAMPLE.SITE69,
                AMBERSAMPLE.SITE70,
                AMBERSAMPLE.SITE71,
                AMBERSAMPLE.SITE72,
                AMBERSAMPLE.SITE73,
                AMBERSAMPLE.SITE74,
                AMBERSAMPLE.SITE75,
                AMBERSAMPLE.SITE76,
                AMBERSAMPLE.SITE77,
                AMBERSAMPLE.SITE78,
                AMBERSAMPLE.SITE79,
                AMBERSAMPLE.SITE80,
                AMBERSAMPLE.SITE81,
                AMBERSAMPLE.SITE82,
                AMBERSAMPLE.SITE83,
                AMBERSAMPLE.SITE84,
                AMBERSAMPLE.SITE85,
                AMBERSAMPLE.SITE86,
                AMBERSAMPLE.SITE87,
                AMBERSAMPLE.SITE88,
                AMBERSAMPLE.SITE89,
                AMBERSAMPLE.SITE90,
                AMBERSAMPLE.SITE91,
                AMBERSAMPLE.SITE92,
                AMBERSAMPLE.SITE93,
                AMBERSAMPLE.SITE94,
                AMBERSAMPLE.SITE95,
                AMBERSAMPLE.SITE96,
                AMBERSAMPLE.SITE97,
                AMBERSAMPLE.SITE98,
                AMBERSAMPLE.SITE99,
                AMBERSAMPLE.SITE100);

        List<Object> collection = Lists.newArrayList();
        collection.add(new Timestamp(new Date().getTime()));
        collection.add(identity.sampleId());
        for (final byte entry : entries) {
            collection.add(entry);
        }

        inserter.values(collection);
        inserter.execute();
    }

    void write(@NotNull String sample, @NotNull List<AmberBAF> variants) {
        Timestamp timestamp = new Timestamp(new Date().getTime());
        deleteAmberRecordsForSample(sample);

        for (List<AmberBAF> splitRegions : Iterables.partition(variants, DB_BATCH_INSERT_SIZE)) {
            InsertValuesStep5 inserter =
                    context.insertInto(AMBER, AMBER.SAMPLEID, AMBER.CHROMOSOME, AMBER.POSITION, AMBER.HETEROZYGOUS, AMBER.MODIFIED);
            splitRegions.forEach(variant -> addRecord(timestamp, inserter, sample, variant));
            inserter.execute();
        }
    }

    void deleteAmberRecordsForSample(@NotNull String sample) {
        context.delete(AMBER).where(AMBER.SAMPLEID.eq(sample)).execute();
    }
}