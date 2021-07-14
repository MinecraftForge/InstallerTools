/*
 * InstallerTools
 * Copyright (c) 2019-2021.
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

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class ExtractFiles extends Task {

    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> archiveO = parser.accepts("archive", "The archive").withRequiredArg().ofType(File.class).required();
        OptionSpec<String> fromO = parser.accepts("from", "File in the archive to extract").withRequiredArg().ofType(String.class).required();
        OptionSpec<File>   toO   = parser.accepts("to"  , "Path to extract file to"       ).withRequiredArg().ofType(File.class  ).required();
        OptionSpec<File> execsO = parser.accepts("execs", "A file to set the executable flag on").withRequiredArg().ofType(File.class);

        try {
            OptionSet options = parser.parse(args);

            File archive = options.valueOf(archiveO);

            List<String> from = options.valuesOf(fromO);
            List<File>   to   = options.valuesOf(toO);
            if (from.size() != to.size())
                throw new IllegalArgumentException("Invalid arguments, must have matching from/to set");

            List<File> execs = options.valuesOf(execsO);

            /*
            Map<String, String> tokens = new HashMap<>();
            String prev = null;
            for (String s : options.valuesOf(tokensO)) {
                if (prev == null) {
                    prev = s;
                } else {
                    tokens.put(prev, s);
                    prev = null;
                }
            }
            */

            log("Archive: " + archive);
            for (int x = 0; x < from.size(); x++) {
            log("Extract: " + from.get(x));
            log("         " + to.get(x));
            }
            for (int x = 0; x < execs.size(); x++) {
            log("Exec:    " + execs.get(x));
            }

            if (!archive.exists())
                error("Could not find archive: " + archive);

            try (FileSystem fs = FileSystems.newFileSystem(archive.toPath(), null)) {
                for (int x = 0; x < from.size(); x++) {
                    Path path = fs.getPath(from.get(x));
                    if (!Files.exists(path))
                        log("Could not find file in archive: " + from.get(x));

                    File toF = to.get(x);
                    if (!toF.getParentFile().exists() && !toF.getParentFile().mkdirs())
                        error("Couldn't make parent directory: " + toF.getParentFile().getAbsolutePath());

                    Files.copy(path, toF.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    /*
                    if (filesReplace != null && filesReplace.contains(file)) {
                        List<String> lines = Files.readAllLines(path).stream()
                                .map(s -> {
                                    for (Map.Entry<String, String> entry : tokens.entrySet()) {
                                        s = s.replace(entry.getKey(), entry.getValue());
                                    }
                                    return s;
                                }).collect(Collectors.toList());
                        Files.write(copyTo, lines);
                    } else {
                        Files.copy(path, copyTo, StandardCopyOption.REPLACE_EXISTING);
                    }
                    */
                }
            }

            for (File exec : execs) {
                if (!exec.setExecutable(true))
                    log("Couldn't set executable bit for file: " + exec.getAbsolutePath());
            }
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }
}
