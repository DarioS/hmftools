package com.hartwig.hmftools.store

import com.hartwig.hmftools.bedpe.Breakend
import com.hartwig.hmftools.bedpe.Breakpoint
import com.hartwig.hmftools.gripss.ContigComparator
import com.hartwig.hmftools.gripss.store.LocationStore
import junit.framework.Assert.assertTrue
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test

class LocationStoreTest {

    private val contigComparator = ContigComparator(null)

    @Test
    fun testContainSingle() {
        val entry1 = Breakend.fromBed("1\t665\t666\t.\t440\t+")
        val entry2 = Breakend.fromBed("1\t766\t767\t.\t440\t+")
        val store = LocationStore(contigComparator, listOf(entry1, entry2), listOf())
        Assert.assertTrue(store.contains(Breakend("1", 666, 666, 1)))
        Assert.assertFalse(store.contains(Breakend("1", 667, 667, 1)))
        Assert.assertTrue(store.contains(Breakend("1", 666, 666, 1)))
    }

    @Test
    fun testOutOfOrder() {
        val entry1 = Breakend.fromBed("1\t665\t666\t.\t440\t+")
        val entry2 = Breakend.fromBed("1\t766\t767\t.\t440\t+")
        val store = LocationStore(contigComparator, listOf(entry2, entry1), listOf())
        Assert.assertTrue(store.contains(Breakend("1", 666, 666, 1)))
        Assert.assertFalse(store.contains(Breakend("1", 667, 667, 1)))
        Assert.assertTrue(store.contains(Breakend("1", 666, 666, 1)))
    }

    @Test
    fun testGoBackFarEnough() {
        val entry1 = Breakpoint(Breakend("1", 224, 233, 1), Breakend("MT", 335, 335, 1))
        val entry2 = Breakpoint(Breakend("1", 229, 230, 1), Breakend("7", 335, 335, 1))
        val entry3 = Breakpoint(Breakend("1", 300, 300, 1), Breakend("MT", 335, 335, 1))
        val store = LocationStore(contigComparator, listOf(), listOf(entry2, entry1, entry3))

        Assert.assertTrue(store.contains(Breakpoint(Breakend("1", 230, 230, 1), entry1.endBreakend)))
        Assert.assertTrue(store.contains(Breakpoint(Breakend("1", 231, 231, 1), entry1.endBreakend)))
        Assert.assertTrue(store.contains(Breakpoint(Breakend("1", 230, 230, 1), entry1.endBreakend)))
    }

    @Test
    fun testBreakendsReversed() {
        val entry1 = Breakpoint(Breakend("1", 224, 233, 1), Breakend("MT", 335, 335, 1))
        val entry2 = Breakpoint(Breakend("1", 229, 230, 1), Breakend("7", 335, 335, 1))
        val entry3 = Breakpoint(Breakend("1", 300, 300, 1), Breakend("MT", 335, 335, 1))
        val store = LocationStore(contigComparator, listOf(), listOf(entry2, entry1, entry3))

        Assert.assertTrue(store.contains(Breakpoint(entry1.endBreakend, entry1.startBreakend)))
        Assert.assertTrue(store.contains(Breakpoint(entry2.startBreakend, entry1.endBreakend)))
    }

    @Ignore
    fun testStuff() {
        val breakEnds = Breakend.fromBedFile("/Users/jon/hmf/resources/gridss_pon_single_breakend.bed")
        val breakpoints = Breakpoint.fromBedpeFile("/Users/jon/hmf/resources/gridss_pon_breakpoint.bedpe", contigComparator)

        val ponStore = LocationStore(contigComparator, breakEnds, breakpoints)
        for (breakEnd in breakEnds) {
            assertTrue(ponStore.contains(breakEnd))
        }

        for (breakpoint in breakpoints) {
            assertTrue(ponStore.contains(breakpoint))
        }
    }
}