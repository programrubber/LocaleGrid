package com.localegrid.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.localegrid.settings.LocaleGridSettingsState;

import java.io.File;
import java.util.Locale;

public final class LocaleGridPath {
    private LocaleGridPath() {
    }

    public static boolean isLocaleJson(Project project, VirtualFile file) {
        return project != null && file != null && !file.isDirectory() && resolve(project, file) != null;
    }

    public static LocaleFile resolve(Project project, VirtualFile file) {
        if (project.getBasePath() == null || file == null || !"json".equalsIgnoreCase(file.getExtension())) {
            return null;
        }

        LocaleGridSettingsState settings = LocaleGridSettingsState.getInstance(project);
        File projectBase = new File(project.getBasePath());
        File localesRoot = new File(projectBase, settings.localesRoot).getAbsoluteFile();
        File current = new File(file.getPath()).getAbsoluteFile();
        File localeDir = current.getParentFile();
        File rootDir = localeDir == null ? null : localeDir.getParentFile();
        if (rootDir == null || !sameFile(localesRoot, rootDir)) {
            return null;
        }

        LocaleFileName fileName = resolveFileName(current.getName(), localeDir.getName());
        if (fileName == null) {
            return null;
        }
        return new LocaleFile(localesRoot, localeDir.getName(), fileName, current);
    }

    private static boolean sameFile(File left, File right) {
        return left.toPath().normalize().equals(right.toPath().normalize());
    }

    static LocaleFileName resolveFileName(String fileName, String locale) {
        if (fileName == null || locale == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return null;
        }

        String stem = fileName.substring(0, fileName.length() - ".json".length());
        String localeSuffix = "_" + locale;
        if (stem.endsWith(localeSuffix) && stem.length() > localeSuffix.length()) {
            return new LocaleFileName(
                stem.substring(0, stem.length() - localeSuffix.length()),
                FileNamePattern.CATEGORY_WITH_LOCALE_SUFFIX
            );
        }
        return new LocaleFileName(stem, FileNamePattern.CATEGORY_ONLY);
    }

    enum FileNamePattern {
        CATEGORY_ONLY,
        CATEGORY_WITH_LOCALE_SUFFIX
    }

    static final class LocaleFileName {
        private final String category;
        private final FileNamePattern pattern;

        private LocaleFileName(String category, FileNamePattern pattern) {
            this.category = category;
            this.pattern = pattern;
        }

        String getCategory() {
            return category;
        }

        FileNamePattern getPattern() {
            return pattern;
        }

        String fileNameForLocale(String locale) {
            if (pattern == FileNamePattern.CATEGORY_WITH_LOCALE_SUFFIX) {
                return category + "_" + locale + ".json";
            }
            return category + ".json";
        }
    }

    public static final class LocaleFile {
        private final File localesRoot;
        private final String locale;
        private final LocaleFileName fileName;
        private final File file;

        private LocaleFile(File localesRoot, String locale, LocaleFileName fileName, File file) {
            this.localesRoot = localesRoot;
            this.locale = locale;
            this.fileName = fileName;
            this.file = file;
        }

        public File getLocalesRoot() {
            return localesRoot;
        }

        public String getLocale() {
            return locale;
        }

        public String getCategory() {
            return fileName.getCategory();
        }

        public FileNamePattern getFileNamePattern() {
            return fileName.getPattern();
        }

        public File fileForLocale(String locale) {
            return new File(new File(localesRoot, locale), fileName.fileNameForLocale(locale));
        }

        public File getFile() {
            return file;
        }
    }
}
