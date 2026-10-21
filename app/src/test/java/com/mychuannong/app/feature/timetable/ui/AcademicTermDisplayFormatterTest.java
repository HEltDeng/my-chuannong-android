package com.mychuannong.app.feature.timetable.ui;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class AcademicTermDisplayFormatterTest {
    @Test
    public void formatsFourAcademicYearsRelativeToAdmissionYear() {
        Integer studentId = 2025;
        Map<String, String> expectedLabels = new LinkedHashMap<>();
        expectedLabels.put("2025-2026-1", "大一上");
        expectedLabels.put("2025-2026-2", "大一下");
        expectedLabels.put("2026-2027-1", "大二上");
        expectedLabels.put("2026-2027-2", "大二下");
        expectedLabels.put("2027-2028-1", "大三上");
        expectedLabels.put("2027-2028-2", "大三下");
        expectedLabels.put("2028-2029-1", "大四上");
        expectedLabels.put("2028-2029-2", "大四下");

        expectedLabels.forEach((rawTerm, expectedLabel) ->
                assertEquals(
                        expectedLabel,
                        AcademicTermDisplayFormatter.INSTANCE.format(rawTerm, studentId)
                ));
    }

    @Test
    public void supportsDifferentAdmissionYear() {
        assertEquals(
                "大三下",
                AcademicTermDisplayFormatter.INSTANCE.format("2024-2025-2", 2022)
        );
    }

    @Test
    public void unknownYearNeverGuessesAndAccountChangeRecomputes() {
        String rawTerm = "2026-2027-1";
        assertEquals("2026秋", AcademicTermDisplayFormatter.INSTANCE.format(rawTerm, null));
        assertEquals("2027春", AcademicTermDisplayFormatter.INSTANCE.format("2026-2027-2", null));
        assertEquals("大二上", AcademicTermDisplayFormatter.INSTANCE.format(rawTerm, 2025));
        assertEquals("大一上", AcademicTermDisplayFormatter.INSTANCE.format(rawTerm, 2026));
        assertEquals("2026-2027-1", rawTerm);
    }

    @Test
    public void returnsRawTermForMalformedOrUnsupportedTerms() {
        Integer studentId = 2025;
        List<String> unsupportedTerms = List.of(
                "2025-2027-1",
                "2025-2026-3",
                "2025/2026/1",
                " 2025-2026-1 "
        );

        unsupportedTerms.forEach(rawTerm ->
                assertEquals(
                        rawTerm,
                        AcademicTermDisplayFormatter.INSTANCE.format(rawTerm, studentId)
                ));
        assertEquals("2029秋", AcademicTermDisplayFormatter.INSTANCE.format("2029-2030-1", 2025));
    }
}
