package net.msrandom.unifiedbuilds.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;

public interface RemapTask extends ProjectJarArchive {
    @InputFile
    RegularFileProperty getInput();
}
