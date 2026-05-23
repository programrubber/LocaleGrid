package com.localegrid.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.localegrid.settings.LocaleGridSettingsState;

import java.io.File;

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

        String name = current.getName();
        String category = name.substring(0, name.length() - ".json".length());
        return new LocaleFile(localesRoot, localeDir.getName(), category, current);
    }

    private static boolean sameFile(File left, File right) {
        return left.toPath().normalize().equals(right.toPath().normalize());
    }

    public static final class LocaleFile {
        private final File localesRoot;
        private final String locale;
        private final String category;
        private final File file;

        public LocaleFile(File localesRoot, String locale, String category, File file) {
            this.localesRoot = localesRoot;
            this.locale = locale;
            this.category = category;
            this.file = file;
        }

        public File getLocalesRoot() {
            return localesRoot;
        }

        public String getLocale() {
            return locale;
        }

        public String getCategory() {
            return category;
        }

        public File getFile() {
            return file;
        }
    }
}
