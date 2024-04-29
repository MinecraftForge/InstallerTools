/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installertools;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import net.minecraftforge.installertools.util.HashFunction;

public class BundlerExtract extends Task {
    private static final Attributes.Name FORMAT = new Attributes.Name("Bundler-Format");

    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> inputO = parser.accepts("input", "The input bundled jar").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputO = parser.accepts("output", "Output file if extracting single entry, or output directory").withRequiredArg().ofType(File.class).required();
        OptionSpec<Void> allO = parser.accepts("all", "Extract all files from the bundle.");
        OptionSpec<Void> jarOnlyO = parser.accepts("jar-only", "Only extract the main version jar file");
        OptionSpec<Void> librariesO = parser.accepts("libraries", "Only extract the libraries");

        try {
            OptionSet options = parser.parse(args);

            File input = options.valueOf(inputO).getAbsoluteFile();
            File output = options.valueOf(outputO).getAbsoluteFile();
            boolean all = options.has(allO);
            boolean jarOnly = options.has(jarOnlyO);
            boolean libs = options.has(librariesO);
            if (all && jarOnly)
                error("Can not specify --all and --jar-only at the same time");

            log("Input:   " + input);
            log("Output:  " + output);
            log("All:     " + all);
            log("JarOnly: " + jarOnly);
            log("Libs:    " + libs);

            if (!input.exists())
                error("Could not find input: " + input);

            try (FileSystem fs = FileSystems.newFileSystem(input.toPath(), null)) {
                Path mfp = fs.getPath("META-INF", "MANIFEST.MF");
                if (!Files.exists(mfp))
                    error("Input archive does not contain META-INF/MANIFEST.MF");

                Manifest mf = null;
                try (InputStream is = Files.newInputStream(mfp)) {
                    mf = new Manifest(is);
                }
                String format = mf.getMainAttributes().getValue(FORMAT);
                if (format == null)
                    error("Invalid bundler archive, missing format entry from manifest");

                if (!"1.0".equals(format))
                    error("Unsupported bundler format " + format + " only 1.0 is supported");

                FileList libraries = FileList.read(fs.getPath("META-INF", "libraries.list"));
                FileList versions = FileList.read(fs.getPath("META-INF", "versions.list"));

                if (jarOnly) {
                    FileList.Entry entry = null;
                    for (FileList.Entry e : versions.entries) {
                        if (e.path.endsWith(".jar")) {
                            entry = e;
                            break;
                        }
                    }

                    if (entry == null)
                        error("Could not find main jar in versions.list");

                    extractFile("versions", fs, entry, output);
                } else if (libs) {
                    if (output.exists() && output.isFile())
                        error("Can not extract to " + output + " as it is a file, not a directory");

                    for (FileList.Entry entry : libraries.entries)
                        extractFile("libraries", fs, entry, new File(output, entry.path));
                } else if (all) {
                    if (output.exists() && output.isFile())
                        error("Can not extract to " + output + " as it is a file, not a directory");

                    for (FileList.Entry entry : libraries.entries)
                        extractFile("libraries", fs, entry, new File(output, "libraries/" + entry.path));
                    for (FileList.Entry entry : versions.entries)
                        extractFile("versions", fs, entry, new File(output, "versions/" + entry.path));
                } else {
                    error("Must specify either --jar-only, --libraries, or --all");
                }
            }
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }

    private void extractFile(String group, FileSystem fs, FileList.Entry entry, File output) throws IOException {
        if (output.exists()) {
            if (output.isDirectory())
                error("Can not extract main version jar to a directory.");

            String existing = HashFunction.SHA256.hash(output);
            if (existing.equals(entry.hash)) {
                log("File already exists, and hash verified");
                return;
            }

            log("Existing file's hash does not match");
            log("Expected: " + entry.hash);
            log("Actual:   " + existing);
        }

        if (!output.getParentFile().exists())
            output.getParentFile().mkdirs();

        Files.copy(fs.getPath("META-INF", group, entry.path), output.toPath(), StandardCopyOption.REPLACE_EXISTING);

        String extracted = HashFunction.SHA256.hash(output);
        if (!extracted.equals(entry.hash)) {
            error("Failed to extract: " + group + '/' + entry.path + " Hash mismatch\n" +
                  "Expected: " + entry.hash + '\n' +
                  "Actual:   " + extracted);
        } else {
            log("Extracted: " + group + '/' + entry.path);
        }
    }

    private static class FileList {
        private static FileList read(Path path) throws IOException {
            List<Entry> ret = new ArrayList<>();
            for (String line : Files.readAllLines(path)) {
                String[] pts = line.split("\t");
                if (pts.length != 3)
                    throw new IllegalStateException("Invalid file list line: " + line);
                ret.add(new Entry(pts[0], pts[1], pts[2]));
            }
            return new FileList(ret);

        }
        private final List<Entry> entries;

        private FileList(List<Entry> entries) {
            this.entries = entries;
        }

        private static class Entry {
            private final String hash;
            @SuppressWarnings("unused")
            private final String id;
            private final String path;
            private Entry(String hash, String id, String path) {
                this.hash = hash;
                this.id = id;
                this.path = path;
            }
        }
    }
}
