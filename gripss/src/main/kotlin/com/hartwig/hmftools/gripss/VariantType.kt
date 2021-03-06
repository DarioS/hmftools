package com.hartwig.hmftools.gripss

import kotlin.math.abs

data class Single(
        override val alt: String,
        override val insertSequence: String,
        override val startOrientation: Byte)
    : VariantType() {

    override fun toString(): String {
        return if (startOrientation == 1.toByte()) "${alt}${insertSequence}." else ".${insertSequence}${alt}"
    }

    fun altString(alt: String): String {
        return if (startOrientation == 1.toByte()) "${alt}${insertSequence}." else ".${insertSequence}${alt}"
    }
}

data class Translocation(
        override val alt: String,
        override val insertSequence: String,
        override val otherChromosome: String,
        override val otherPosition: Int,
        override val startOrientation: Byte,
        override val endOrientation: Byte)
    : Paired() {
    override fun toString(): String = altString()

    override val length: Int = -1
}

data class Inversion(
        override val alt: String,
        override val insertSequence: String,
        override val otherChromosome: String,
        override val otherPosition: Int,
        override val startOrientation: Byte,
        override val endOrientation: Byte,
        override val length: Int)
    : Paired() {
    override fun toString(): String = altString()
}

data class Deletion(
        override val alt: String,
        override val insertSequence: String,
        override val otherChromosome: String,
        override val otherPosition: Int,
        override val startOrientation: Byte,
        override val endOrientation: Byte,
        override val length: Int)
    : Paired() {
    override fun toString(): String = altString()
}

data class Duplication(
        override val alt: String,
        override val insertSequence: String,
        override val otherChromosome: String,
        override val otherPosition: Int,
        override val startOrientation: Byte,
        override val endOrientation: Byte,
        override val length: Int) : Paired() {
    override fun toString(): String = altString()
}

data class Insertion(
        override val alt: String,
        override val insertSequence: String,
        override val otherChromosome: String,
        override val otherPosition: Int,
        override val startOrientation: Byte,
        override val endOrientation: Byte,
        override val length: Int) : Paired() {
    override fun toString(): String = altString()
}

sealed class Paired : VariantType() {
    abstract val otherChromosome: String
    abstract val otherPosition: Int
    abstract val endOrientation: Byte
    abstract val length: Int

    fun altString(): String {
        return when(Pair(startOrientation, endOrientation)) {
            Pair(1.toByte(),(-1).toByte()) -> "$alt$insertSequence[$otherChromosome:$otherPosition["
            Pair(1.toByte(),1.toByte()) -> "$alt$insertSequence]$otherChromosome:$otherPosition]"
            Pair((-1).toByte(),(-1).toByte()) -> "[$otherChromosome:$otherPosition[$insertSequence$alt"
            else -> "]$otherChromosome:$otherPosition]$insertSequence$alt"
        }
    }

    fun altString(position: Int, alt: String): String {
        return Insertion(alt, insertSequence, otherChromosome, position, startOrientation, endOrientation, 0).altString()
    }
}

sealed class VariantType {
    abstract val alt: String
    abstract val insertSequence: String
    abstract val startOrientation: Byte

    companion object Factory {
        private const val BREAKPOINT_REGEX = "^(.*)([\\[\\]])(.+)[\\[\\]](.*)\$"

        fun create(chromosome: String, position: Int, ref: String, alt: String): VariantType {
            if (alt.startsWith(".")) {
                return Single(alt.substring(alt.length - 1, alt.length), alt.substring(ref.length, alt.length - 1), -1)
            }

            if (alt.endsWith(".")) {
                return Single(alt.substring(0, 1), alt.substring(1, alt.length - ref.length), 1)
            }

            return paired(chromosome, position, ref, alt)
        }

        private fun paired(chromosome: String, position: Int, ref: String, alt: String): Paired {
            val match = Regex(BREAKPOINT_REGEX).find(alt)!!
            val (initialSequence, bracket, location, finalSequence) = match.destructured
            val locationList = location.split(":")
            val (otherChromosome, otherPosition) = Pair(locationList[0], locationList[1].toInt())

            val endOrientation: Byte = if (bracket == "]") 1 else -1

            val startOrientation: Byte
            val insertSequence: String
            val altBase: String
            if (initialSequence.isNotEmpty()) {
                startOrientation = 1
                insertSequence = initialSequence.substring(ref.length, initialSequence.length)
                altBase = alt.substring(0, 1)
            } else {
                startOrientation = -1
                insertSequence = finalSequence.substring(0, finalSequence.length - ref.length)
                altBase = alt.substring(alt.length - 1, alt.length)
            }

            if (chromosome != otherChromosome) {
                return Translocation(altBase, insertSequence, otherChromosome, otherPosition, startOrientation, endOrientation)
            }

            val length = abs(position - otherPosition)
            if (startOrientation == endOrientation) {
                return Inversion(altBase, insertSequence, otherChromosome, otherPosition, startOrientation, endOrientation, length)
            }

            if ((position <= otherPosition && startOrientation == (-1).toByte()) || (position >= otherPosition && startOrientation == (1).toByte())) {
                return Duplication(altBase, insertSequence, otherChromosome, otherPosition, startOrientation, endOrientation, length)
            }

            return if (length <= 1)
                Insertion(altBase, insertSequence, otherChromosome, otherPosition, startOrientation, endOrientation, length)
            else
                Deletion(altBase, insertSequence, otherChromosome, otherPosition, startOrientation, endOrientation, length)
        }
    }
}
