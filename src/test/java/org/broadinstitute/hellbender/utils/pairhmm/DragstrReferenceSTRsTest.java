package org.broadinstitute.hellbender.utils.pairhmm;

import org.apache.commons.lang3.ArrayUtils;
import org.broadinstitute.hellbender.utils.RandomDNA;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * Tool to figure out the period and repeat-length (in units) of STRs in a reference sequence.
 * <p>
 *    The period and repeat-length of a reference sequence position is determined as follow.
 *    The STR unit is solelly determined by the sequence from that base onwards.
 * </p>
 * <p>
 *     If the backward base sequence contains additional copies of that unit these are added to the repeat-length.
 * </p>
 * <p>
 *     However a larger repeat-length for a different STR unit upstream would effectively being ignored.
 * </p>
 * <p>
 *     All sites period and forward repeat-length are determined in a single pass thru the sequence (O(L * MaxPeriod)).
 *     The backward additional unit count is calculated on demand.
 * </p>
 */
public class DragstrReferenceSTRsTest {

    @Test(dataProvider = "testSequenceAndMaxPeriodData")
    public void testRepeatPeriodAndCountFullSequence(final String sequenceStr, final int maxPeriod) {
        final byte[] sequence = sequenceStr.getBytes();
        final DragstrReferenceSTRs subject = DragstrReferenceSTRs.of(sequence, 0, sequence.length, maxPeriod);
        assertCorrectness(subject, sequenceStr, maxPeriod, 0, sequence.length);
    }

    private void assertCorrectness(final DragstrReferenceSTRs subject, final String sequence, final int maxPeriod, final int start, final int end) {
        for (int position = start; position < end; position++) {
            final int[] expectedBestPeriodAndRepeat = calculateBestPeriodAndRepeat(sequence, position, maxPeriod);
            Assert.assertEquals(subject.period(position), expectedBestPeriodAndRepeat[0], "" + position);
            Assert.assertEquals(subject.repeatLength(position), expectedBestPeriodAndRepeat[1], "" + position);
            final String expectedUnit = sequence.substring(position, position + expectedBestPeriodAndRepeat[0]);
            Assert.assertEquals(subject.repeatUnitAsString(position), expectedUnit);
            Assert.assertEquals(new String(subject.repeatUnit(position)), expectedUnit);
        }
    }

    @Test(dataProvider = "testSequenceAndMaxPeriodData")
    public void testRepeatBestPeriodAndCountPartialSequence(final String seqStr, final int maxPeriod) {
        for (int start = 0; start <= 6; start++) {
            for (int end = 7; end < seqStr.length(); end++) {
                final DragstrReferenceSTRs partialSubject = DragstrReferenceSTRs.of(seqStr.getBytes(), start, end, maxPeriod);
                for (int pos = start; pos < end; pos++) {
                    final int[] result = calculateBestPeriodAndRepeat(seqStr, pos, maxPeriod);
                    Assert.assertEquals(result[0], partialSubject.period(pos));
                    Assert.assertEquals(result[1], partialSubject.repeatLength(pos));
                }
            }
        }
    }

    /**
     * Brute force approach to calcuate the "best" repeat unit length and repeat count. The best is the one
     * that has a maximum number of repeated units. In case of a tie, the smaller unit is considered a better
     * one.
     * @param sequence
     * @param position
     * @param maxPeriod
     * @return
     */
    public static int[] calculateBestPeriodAndRepeat(final String sequence, final int position, final int maxPeriod) {
        final int[] result = new int[2];
        result[0] = 1;
        result[1] = calculateRepeats(sequence, position, 1, true);
        for (int period = 2; period <= maxPeriod; period++) {
            final int candidate = calculateRepeats(sequence, position, period, true);
            if (candidate > result[1]) {
                result[0] = period;
                result[1] = candidate;
            }
        }
        result[1] = calculateRepeats(sequence, position, result[0], false);
        return result;
    }

    public static int calculateRepeats(String sequence, final int position, final int period, final boolean onlyForward) {
        if (period + position > sequence.length()) {
            return 0;
        }
        int result = 1; // the first match is a given.
        // forward counting.
        outter:
        for (int offset = period + position; offset <= sequence.length() - period; offset += period) {
            for (int i = 0; i < period; i++) {
                if (sequence.charAt(offset + i) != sequence.charAt(position + i)) {
                    break outter;
                }
            }
            result++;
        }

        if (onlyForward) {
            return result;
        }
        // backward counting.
        outter:
        for (int offset = position - period; offset >= 0; offset -= period) {
            for (int i = 0; i < period; i++) {
                if (sequence.charAt(offset + i) != sequence.charAt(position + i)) {
                    break outter;
                }
            }
            result++;
        }
        return result;
    }

    @DataProvider
    public static Object[][] testSequenceAndMaxPeriodData() {
        final List<Object[]> result = new ArrayList<>();
        final String[] fixSequences = {
                "TGATTTGCTCTGTCTGCTGCTGCTGCCTTCAGTAGGGTTGCACGCCTGGGCACGCCTGGAAT",
                "AGTATACTGAT",
                "GTCTATATATATTTTAATTAATTAATTAATTAAATATATTTTCTGCTGCCTTTTGGAT",
                "AAAAA",
                "A",
                "",
                "ACGTAGATCTGTAGCACTATCGAGC",
                "TACAACACAATACAATACAATACAATACAATACAAATACAAATACAATACAATACAATACAATACAATACAATACAATAT"};
        final Random rdn = new Random(131);
        final RandomDNA rdnDNA = new RandomDNA(rdn);

        final String[] randomSequences = new String[100];
        for (int i = 0; i < randomSequences.length; i++) {
            final int length = rdn.nextInt(195)  + 5;
            final byte[] bases = rdnDNA.nextBases(length);
            for (int position = 0; position < bases.length; position++) {
                if (rdn.nextDouble() < 1.0 / length) {
                    final int unitLength = rdn.nextInt(10) + 1;
                    final int intendedRepeats = rdn.nextInt(10) + 2;
                    final byte[] unit = rdnDNA.nextBases(unitLength);
                    for (int repeat = 0, offset = position; repeat < intendedRepeats && offset < length - unitLength; offset += unitLength, repeat++) {
                        System.arraycopy(unit, 0, bases, offset, Math.min(unitLength, bases.length - offset));
                    }
                }
            }
            randomSequences[i] = new String(bases);
        }
        for (final String fixSequence : fixSequences) {
            result.add(new Object[] { fixSequence, Math.max(5, fixSequence.length() / 4) });
        }
        for (final String randomSequence : randomSequences) {
            result.add(new Object[] { randomSequence, Math.max(5, randomSequence.length() / 4 ) });
        }
        return result.toArray(new Object[result.size()][]);
    }
}