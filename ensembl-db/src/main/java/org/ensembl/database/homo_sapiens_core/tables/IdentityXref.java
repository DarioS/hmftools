/*
 * This file is generated by jOOQ.
*/
package org.ensembl.database.homo_sapiens_core.tables;


import java.util.Arrays;
import java.util.List;

import javax.annotation.Generated;

import org.ensembl.database.homo_sapiens_core.HomoSapiensCore_89_37;
import org.ensembl.database.homo_sapiens_core.Keys;
import org.ensembl.database.homo_sapiens_core.tables.records.IdentityXrefRecord;
import org.jooq.Field;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.TableImpl;
import org.jooq.types.UInteger;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.9.5"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class IdentityXref extends TableImpl<IdentityXrefRecord> {

    private static final long serialVersionUID = 1778777008;

    /**
     * The reference instance of <code>homo_sapiens_core_89_37.identity_xref</code>
     */
    public static final IdentityXref IDENTITY_XREF = new IdentityXref();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<IdentityXrefRecord> getRecordType() {
        return IdentityXrefRecord.class;
    }

    /**
     * The column <code>homo_sapiens_core_89_37.identity_xref.object_xref_id</code>.
     */
    public final TableField<IdentityXrefRecord, UInteger> OBJECT_XREF_ID = createField("object_xref_id", org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false), this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.identity_xref.xref_identity</code>.
     */
    public final TableField<IdentityXrefRecord, Integer> XREF_IDENTITY = createField("xref_identity", org.jooq.impl.SQLDataType.INTEGER, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.identity_xref.ensembl_identity</code>.
     */
    public final TableField<IdentityXrefRecord, Integer> ENSEMBL_IDENTITY = createField("ensembl_identity", org.jooq.impl.SQLDataType.INTEGER, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.identity_xref.xref_start</code>.
     */
    public final TableField<IdentityXrefRecord, Integer> XREF_START = createField("xref_start", org.jooq.impl.SQLDataType.INTEGER, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.identity_xref.xref_end</code>.
     */
    public final TableField<IdentityXrefRecord, Integer> XREF_END = createField("xref_end", org.jooq.impl.SQLDataType.INTEGER, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.identity_xref.ensembl_start</code>.
     */
    public final TableField<IdentityXrefRecord, Integer> ENSEMBL_START = createField("ensembl_start", org.jooq.impl.SQLDataType.INTEGER, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.identity_xref.ensembl_end</code>.
     */
    public final TableField<IdentityXrefRecord, Integer> ENSEMBL_END = createField("ensembl_end", org.jooq.impl.SQLDataType.INTEGER, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.identity_xref.cigar_line</code>.
     */
    public final TableField<IdentityXrefRecord, String> CIGAR_LINE = createField("cigar_line", org.jooq.impl.SQLDataType.CLOB, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.identity_xref.score</code>.
     */
    public final TableField<IdentityXrefRecord, Double> SCORE = createField("score", org.jooq.impl.SQLDataType.DOUBLE, this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.identity_xref.evalue</code>.
     */
    public final TableField<IdentityXrefRecord, Double> EVALUE = createField("evalue", org.jooq.impl.SQLDataType.DOUBLE, this, "");

    /**
     * Create a <code>homo_sapiens_core_89_37.identity_xref</code> table reference
     */
    public IdentityXref() {
        this("identity_xref", null);
    }

    /**
     * Create an aliased <code>homo_sapiens_core_89_37.identity_xref</code> table reference
     */
    public IdentityXref(String alias) {
        this(alias, IDENTITY_XREF);
    }

    private IdentityXref(String alias, Table<IdentityXrefRecord> aliased) {
        this(alias, aliased, null);
    }

    private IdentityXref(String alias, Table<IdentityXrefRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() {
        return HomoSapiensCore_89_37.HOMO_SAPIENS_CORE_89_37;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UniqueKey<IdentityXrefRecord> getPrimaryKey() {
        return Keys.KEY_IDENTITY_XREF_PRIMARY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UniqueKey<IdentityXrefRecord>> getKeys() {
        return Arrays.<UniqueKey<IdentityXrefRecord>>asList(Keys.KEY_IDENTITY_XREF_PRIMARY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IdentityXref as(String alias) {
        return new IdentityXref(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public IdentityXref rename(String name) {
        return new IdentityXref(name, null);
    }
}
