package com.localegrid.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocaleGridPathTest {
    @Test
    void resolvesPlainCategoryFileName() {
        LocaleGridPath.LocaleFileName fileName = LocaleGridPath.resolveFileName("common.json", "ko");

        assertEquals("common", fileName.getCategory());
        assertEquals(LocaleGridPath.FileNamePattern.CATEGORY_ONLY, fileName.getPattern());
        assertEquals("common.json", fileName.fileNameForLocale("en"));
    }

    @Test
    void resolvesLocaleSuffixFileNameWhenSuffixMatchesFolderLocale() {
        LocaleGridPath.LocaleFileName fileName = LocaleGridPath.resolveFileName("common_ko.json", "ko");

        assertEquals("common", fileName.getCategory());
        assertEquals(LocaleGridPath.FileNamePattern.CATEGORY_WITH_LOCALE_SUFFIX, fileName.getPattern());
        assertEquals("common_en.json", fileName.fileNameForLocale("en"));
        assertEquals("common_ja.json", fileName.fileNameForLocale("ja"));
    }

    @Test
    void supportsLocaleSuffixesThatContainUnderscores() {
        LocaleGridPath.LocaleFileName fileName = LocaleGridPath.resolveFileName("common_pt_BR.json", "pt_BR");

        assertEquals("common", fileName.getCategory());
        assertEquals(LocaleGridPath.FileNamePattern.CATEGORY_WITH_LOCALE_SUFFIX, fileName.getPattern());
        assertEquals("common_en_US.json", fileName.fileNameForLocale("en_US"));
    }

    @Test
    void treatsNonMatchingSuffixAsPlainCategoryName() {
        LocaleGridPath.LocaleFileName fileName = LocaleGridPath.resolveFileName("common_en.json", "ko");

        assertEquals("common_en", fileName.getCategory());
        assertEquals(LocaleGridPath.FileNamePattern.CATEGORY_ONLY, fileName.getPattern());
        assertEquals("common_en.json", fileName.fileNameForLocale("ja"));
    }

    @Test
    void ignoresNonJsonFiles() {
        assertNull(LocaleGridPath.resolveFileName("common_ko.js", "ko"));
    }
}
