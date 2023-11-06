/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installertools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import de.siegmar.fastcsv.reader.NamedCsvReader;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraftforge.installertools.util.Utils;

public class SrgMcpRenamer extends Task {
    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> mcpO = parser.accepts("mcp").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> inputO = parser.accepts("input").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();
        parser.accepts("strip-signatures");

        try {
            OptionSet options = parser.parse(args);

            File mcp = options.valueOf(mcpO).getAbsoluteFile();
            File input = options.valueOf(inputO).getAbsoluteFile();
            File output = options.valueOf(outputO).getAbsoluteFile();
            boolean stripSignatures = options.has("strip-signatures");

            log("Input:  " + input);
            log("Output: " + output);
            log("MCP:    " + mcp);

            if (!mcp.exists())
                error("Missing required MCP data: " + mcp);
            if (!input.exists())
                error("Missing required input jar: " + input);
            if (output.exists()) output.delete();
            if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
            output.createNewFile();

            log("Loading MCP Data");
            Map<String, String> map = new HashMap<>();
            try (ZipFile zip = new ZipFile(mcp)) {
                List<ZipEntry> entries = zip.stream().filter(e -> e.getName().endsWith(".csv")).collect(Collectors.toList());
                for (ZipEntry entry : entries) {
                    NamedCsvReader reader = NamedCsvReader.builder().build(new InputStreamReader(zip.getInputStream(entry)));
                    reader.stream().forEach(row -> {
                        String searge;
                        try {
                            searge = row.getField("searge");
                        } catch (NoSuchElementException e) {
                            searge = row.getField("param");
                        }
                        map.put(searge, row.getField("name"));
                    });
                }
            }

            Remapper remapper = new Remapper() {
                @Override
                public String mapFieldName(final String owner, final String name, final String descriptor) {
                    return map.getOrDefault(name, name);
                }
                @Override
                public String mapInvokeDynamicMethodName(final String name, final String descriptor) {
                    return map.getOrDefault(name, name);
                }
                @Override
                public String mapMethodName(final String owner, final String name, final String descriptor) {
                  return map.getOrDefault(name, name);
                }
            };

            log("Processing ZIP file");
            List<ZipEntryProcessor> processors = new ArrayList<>();
            processors.add(new ZipEntryProcessor(ein -> ein.getName().endsWith(".class"), (ein, zin, zout) -> this.processClass(ein, zin, zout, remapper)));

            if (stripSignatures) {
                processors.add(new ZipEntryProcessor(this::holdsSignatures, (ein, zin, zout) -> log("Stripped signature entry data " + ein.getName())));
                processors.add(new ZipEntryProcessor(ein -> ein.getName().endsWith("META-INF/MANIFEST.MF"), this::processManifest));
            }

            ZipWritingConsumer defaultProcessor = (ein, zin, zout) -> {
                zout.putNextEntry(makeNewEntry(ein));
                Utils.copy(zin, zout);
            };

            processors.add(new ZipEntryProcessor(ein -> ein.getName().startsWith("META-INF/jarjar/") && ein.getName().endsWith(".jar"),
                    (ein, zin, zout) -> this.processNestedJar(processors, defaultProcessor, ein, zin, zout)));

            ByteArrayOutputStream memory = input.equals(output) ? new ByteArrayOutputStream() : null;
            try (ZipOutputStream zout = new ZipOutputStream(memory == null ? new FileOutputStream(output) : memory);
                ZipInputStream in = new ZipInputStream(new FileInputStream(input))) {

                process(processors, defaultProcessor, in, zout);
            }

            if (memory != null)
                Files.write(output.toPath(), memory.toByteArray());

            log("Process complete");
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }

    private void process(List<ZipEntryProcessor> processors, ZipWritingConsumer defaultProcessor, ZipInputStream in, ZipOutputStream zout) throws IOException {
        forEachZipEntry(in, (ein, zin) -> {
            for (ZipEntryProcessor processor : processors) {
                if (processor.validate(ein)) {
                    processor.getProcessor().process(ein, zin, zout);
                    return;
                }
            }

            defaultProcessor.process(ein, zin, zout);
        });
    }

    private void forEachZipEntry(ZipInputStream zin, ZipConsumer entryConsumer) throws IOException {
        String prevName = null;
        ZipEntry ein;
        while ((ein = zin.getNextEntry()) != null) {
            try {
                entryConsumer.processEntry(ein, zin);
            } catch (ZipException e) {
                throw new RuntimeException("Unable to process entry '" + ein.getName() + "' due to an error when processing previous entry '" + prevName + "'", e);
            }
            prevName = ein.getName();
        }
    }

    private void processClass(final ZipEntry ein, final ZipInputStream zin, final ZipOutputStream zout, final Remapper remapper) throws IOException {
        byte[] data = Utils.toByteArray(zin);

        ClassReader reader = new ClassReader(data);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, remapper), 0);
        data = writer.toByteArray();

        zout.putNextEntry(makeNewEntry(ein));
        zout.write(data);
    }

    private void processManifest(final ZipEntry ein, final ZipInputStream zin, final ZipOutputStream zout) throws IOException {
        Manifest min = new Manifest(zin);
        Manifest mout = new Manifest();
        mout.getMainAttributes().putAll(min.getMainAttributes());
        min.getEntries().forEach((name, ain) -> {
            final Attributes aout = new Attributes();
            ain.forEach((k, v) -> {
                if (!"SHA-256-Digest".equalsIgnoreCase(k.toString())) {
                    aout.put(k, v);
                }
            });
            if (!aout.values().isEmpty()) {
                mout.getEntries().put(name, aout);
            }
        });

        zout.putNextEntry(makeNewEntry(ein));
        mout.write(zout);
        log("Stripped Manifest of sha digests");
    }

    private void processNestedJar(List<ZipEntryProcessor> processors, ZipWritingConsumer defaultProcessor, ZipEntry ein, ZipInputStream in, ZipOutputStream zout) throws IOException {
        zout.putNextEntry(makeNewEntry(ein));
        ZipInputStream nestedIn = new ZipInputStream(in);
        ZipOutputStream nestedOut = new ZipOutputStream(zout);
        process(processors, defaultProcessor, nestedIn, nestedOut);
        nestedOut.finish();
    }

    private boolean holdsSignatures(final ZipEntry ein) {
        return ein.getName().startsWith("META-INF/") && (ein.getName().endsWith(".SF") || ein.getName().endsWith(".RSA"));
    }

    private ZipEntry makeNewEntry(ZipEntry oldEntry) {
        ZipEntry newEntry = new ZipEntry(oldEntry.getName());

        // This is mandatory
        if (oldEntry.getLastModifiedTime() != null) {
            newEntry.setLastModifiedTime(oldEntry.getLastModifiedTime());
        } else {
            newEntry.setLastModifiedTime(FileTime.fromMillis(0x386D4380)); //01/01/2000 00:00:00 java 8 breaks when using 0.
        }

        // Optional arguments
        if (oldEntry.getCreationTime() != null) newEntry.setCreationTime(oldEntry.getCreationTime());
        if (oldEntry.getLastAccessTime() != null) newEntry.setLastAccessTime(oldEntry.getLastAccessTime());
        if (oldEntry.getComment() != null) newEntry.setComment(oldEntry.getComment());

        return newEntry;
    }

    private static class ZipEntryProcessor {
        private final Predicate<ZipEntry> validator;
        private final ZipWritingConsumer consumer;

        ZipEntryProcessor(Predicate<ZipEntry> validator, ZipWritingConsumer consumer) {
            this.validator = validator;
            this.consumer = consumer;
        }

        boolean validate(ZipEntry ein) {
            return this.validator.test(ein);
        }

        ZipWritingConsumer getProcessor() {
            return this.consumer;
        }
    }

    @FunctionalInterface
    private interface ZipWritingConsumer {
        void process(ZipEntry ein, ZipInputStream zin, ZipOutputStream zout) throws IOException;
    }

    @FunctionalInterface
    private interface ZipConsumer {
        void processEntry(ZipEntry entry, ZipInputStream stream) throws IOException;
    }
}
