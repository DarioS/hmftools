package com.hartwig.hmftools.sage.read;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.logging.log4j.util.Strings;
import org.junit.Assert;
import org.junit.Test;

public class IndexedBasesTest {

    private final IndexedBases victim = new IndexedBases(1000, 5, 4, 6, 3, "GATCTCCTCA".getBytes());

    @Test
    public void testRightFlankMatchingBases() {
        assertEquals(-1, victim.rightFlankMatchingBases(3, "TCTCCTCG".getBytes()));

        assertEquals(3, victim.rightFlankMatchingBases(3, "TCTCCTCAG".getBytes()));
        assertEquals(3, victim.rightFlankMatchingBases(3, "TCTCCTCA".getBytes()));
        assertEquals(2, victim.rightFlankMatchingBases(3, "TCTCCTC".getBytes()));
        assertEquals(1, victim.rightFlankMatchingBases(3, "TCTCCT".getBytes()));
        assertEquals(0, victim.rightFlankMatchingBases(3, "TCTCC".getBytes()));
    }

    @Test
    public void testLeftFlankMatchingBases() {
        assertEquals(-1, victim.leftFlankMatchingBases(5, "TTCTCCTCA".getBytes()));

        assertEquals(3, victim.leftFlankMatchingBases(5, "GATCTCCTCA".getBytes()));
        assertEquals(3, victim.leftFlankMatchingBases(4, "ATCTCCTCA".getBytes()));
        assertEquals(2, victim.leftFlankMatchingBases(3, "TCTCCTCA".getBytes()));
        assertEquals(1, victim.leftFlankMatchingBases(2, "CTCCTCA".getBytes()));
        assertEquals(0, victim.leftFlankMatchingBases(1, "TCCTCA".getBytes()));
    }

    @Test
    public void testCoreMatch() {
        assertTrue(victim.coreMatch(5, "GATCTCCTCA".getBytes()));
        assertTrue(victim.coreMatch(1, "TCC".getBytes()));

        assertFalse(victim.coreMatch(1, "CCC".getBytes()));
        assertFalse(victim.coreMatch(1, "TTC".getBytes()));
        assertFalse(victim.coreMatch(1, "TCT".getBytes()));
        assertFalse(victim.coreMatch(1, "TC".getBytes()));
        assertFalse(victim.coreMatch(0, "CC".getBytes()));
    }
    
    @Test
    public void testPartialMatchMustHaveAtLeastOneFullSide() {
        ReadContext victim = new ReadContext("", 1000, 2, 2, 2, 2, "GGTAA".getBytes(), Strings.EMPTY);
        Assert.assertEquals(ReadContextMatch.FULL, victim.matchAtPosition(2, "GGTAA".getBytes()));

        assertEquals(ReadContextMatch.PARTIAL, victim.matchAtPosition(2, "GGTA".getBytes()));
        assertEquals(ReadContextMatch.PARTIAL, victim.matchAtPosition(2, "GGT".getBytes()));
        assertEquals(ReadContextMatch.CORE, victim.matchAtPosition(1, "GT".getBytes()));

        assertEquals(ReadContextMatch.PARTIAL, victim.matchAtPosition(1, "GTAA".getBytes()));
        assertEquals(ReadContextMatch.PARTIAL, victim.matchAtPosition(0, "TAA".getBytes()));
        assertEquals(ReadContextMatch.CORE, victim.matchAtPosition(0, "TA".getBytes()));
        assertEquals(ReadContextMatch.CORE, victim.matchAtPosition(0, "T".getBytes()));
    }

    @Test
    public void testNegativeReadIndex() {
        ReadContext victim = new ReadContext("", 1000, 2, 2, 2, 2, "GGTAA".getBytes(), Strings.EMPTY);
        assertEquals(ReadContextMatch.FULL, victim.matchAtPosition(2, "GGTAA".getBytes()));
        assertEquals(ReadContextMatch.NONE, victim.matchAtPosition(-1, "GGTAA".getBytes()));
    }

    @Test
    public void testPhasedMNV() {
        ReadContext victim1 = new ReadContext(Strings.EMPTY, 1000, 4, 4, 4, 4, "GATCTTGAT".getBytes(), Strings.EMPTY);
        ReadContext victim2 = new ReadContext(Strings.EMPTY, 1001, 5, 5, 5, 4, "GATCTTGATC".getBytes(), Strings.EMPTY);

        assertTrue(victim1.phased(-1, victim2));
        assertTrue(victim2.phased(1, victim1));
    }

    @Test
    public void testPhasedReadLongEnoughOnAtLeastOneSide() {
        ReadContext victim1 = new ReadContext(Strings.EMPTY, 1000, 4, 4, 4, 4, "GATCTTGA".getBytes(), Strings.EMPTY);
        ReadContext victim2 = new ReadContext(Strings.EMPTY, 1001, 5, 5, 5, 4, "GATCTTGATCT".getBytes(), Strings.EMPTY);

        assertTrue(victim1.phased(-1, victim2));
        assertTrue(victim2.phased(1, victim1));
    }

    @Test
    public void testLongReadShortFlanks() {
        final String read = "TACCACAAATACATATACGTGTATCTGTCTGTGTGTTATGAACTTATATAAACCATCAC";
        ReadContext victim1 = new ReadContext(Strings.EMPTY, 1010, 10, 9, 11, 3, read.getBytes(), Strings.EMPTY);
        ReadContext victim2 = new ReadContext(Strings.EMPTY, 1030, 40, 39, 41, 3, read.getBytes(), Strings.EMPTY);

        assertTrue(victim1.phased(-30, victim2));
        assertTrue(victim2.phased(30, victim1));
    }

    @Test
    public void testBothCentreMatches() {
        ReadContext victim1 = new ReadContext(Strings.EMPTY, 1000, 4, 4, 4, 4, "AAAATGGGG".getBytes(), Strings.EMPTY);
        ReadContext victim2 = new ReadContext(Strings.EMPTY, 1005, 5, 5, 5, 4,     "TGGGGACCCC".getBytes(), Strings.EMPTY);
        assertFalse(victim1.phased(-5, victim2));
        assertFalse(victim2.phased(5, victim1));
    }
}
