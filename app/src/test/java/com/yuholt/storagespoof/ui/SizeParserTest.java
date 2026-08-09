package com.yuholt.storagespoof.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class SizeParserTest {
    @Test
    public void parsesRawBytes() {
        assertEquals(1_048_576L, SizeParser.parse("1048576"));
    }

    @Test
    public void parsesDecimalUnits() {
        assertEquals(1_610_612_736L, SizeParser.parse("1.5 GB"));
        assertEquals(134_217_728L, SizeParser.parse("128 mb"));
    }

    @Test
    public void formatsRoundTrippableValues() {
        assertEquals("128 MB", SizeParser.format(134_217_728L));
        assertEquals(134_217_728L, SizeParser.parse(SizeParser.format(134_217_728L)));
    }

    @Test
    public void rejectsInvalidAndNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> SizeParser.parse("-1 MB"));
        assertThrows(IllegalArgumentException.class, () -> SizeParser.parse("hello"));
        assertThrows(IllegalArgumentException.class, () -> SizeParser.parse(""));
    }
}
