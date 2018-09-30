/*
 * InstallerTools
 * Copyright (c) 2016-2018.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.minecraftforge.installertools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class McpData extends Task {

    @SuppressWarnings("unchecked")
    @Override
    public void process(String[] args) throws IOException {

        OptionParser parser = new OptionParser();
        OptionSpec<File> inputO = parser.accepts("input").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();
        OptionSpec<String> keyO = parser.accepts("key").withRequiredArg().ofType(String.class).required();

        try {
            OptionSet options = parser.parse(args);

            File input = options.valueOf(inputO);
            File output = options.valueOf(outputO);
            String key = options.valueOf(keyO);

            log("Input:  " + input);
            log("Output: " + output);
            log("Key:    " + key);

            if (output.exists() && !delete(output))
                error("Could not delete output file: " + output);

            if (!output.getParentFile().exists() && !output.getParentFile().mkdirs())
                error("Could not make output folders: " + output.getParentFile());

            if (!input.exists())
                error("Input does not exist: " + input);

            try (ZipFile zip = new ZipFile(input)) {
                ZipEntry config = zip.getEntry("config.json");
                if (config == null)
                    error("Input zip file invalid, missing 'config.json' entry");

                try (InputStream cfgStream = zip.getInputStream(config)) {
                    McpConfig cfg = ConsoleTool.GSON.fromJson(new InputStreamReader(zip.getInputStream(config)), McpConfig.class);
                    if (cfg.data == null)
                        error("Invalid mcp config, missing data map");

                    String[] pts = key.split("\\.");
                    Map<String, Object> level = cfg.data;
                    for (int x = 0; x < pts.length - 1; x++) {
                        Object entry = level.get(pts[x]);
                        if (!(entry instanceof Map))
                            error("Config missing " + key + " data entry");
                        level = (Map<String, Object>)entry;
                    }

                    Object value = level.get(pts[pts.length - 1]);
                    if (!(value instanceof String))
                        error("Config missing " + key + " data entry");

                    String name = (String)value;
                    if (name.endsWith("/")) {
                        if (!output.mkdirs())
                            error("Failed to create output directory: " + output);

                        Enumeration<? extends ZipEntry> entries = zip.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry entry = entries.nextElement();
                            if (!entry.getName().startsWith(name) || entry.isDirectory())
                                continue;

                            File target = new File(output, entry.getName().substring(name.length()));
                            if (!target.getParentFile().exists() && !target.getParentFile().mkdirs())
                                error("Failed to create output directory: " + output);


                            log("Extracting: " + entry.getName());
                            try (FileOutputStream _output = new FileOutputStream(target);
                                InputStream _input = zip.getInputStream(entry)) {
                                copy(_input, _output);
                            }
                        }

                    } else {
                        ZipEntry entry = zip.getEntry((String)value);
                        if (entry == null)
                            error("Invalid zip, missing " + value + " entry");
                        log("Extracting: " + entry.getName());
                        try (FileOutputStream _output = new FileOutputStream(output);
                            InputStream _input = zip.getInputStream(entry)) {
                            copy(_input, _output);
                        }
                    }

                }
            }


        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }

    private boolean delete(File path) throws IOException {
        if (path.isDirectory()) {
            return Files.walk(path.toPath())
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .map(File::delete)
            .anyMatch(v -> !v);
        }
        return path.delete();
    }

    private void copy(InputStream source, OutputStream sink) throws IOException {
        byte[] buf = new byte[1024];
        int n;
        while ((n = source.read(buf)) > 0)
            sink.write(buf, 0, n);
    }

    public static class McpConfig {
        public int spec;
        public Map<String, Object> data;
    }
}
