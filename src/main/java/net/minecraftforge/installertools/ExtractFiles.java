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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ExtractFiles extends Task {

    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> archiveO = parser.accepts("archive", "The archive").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> targetO = parser.accepts("target", "The target directory").withRequiredArg().ofType(File.class).required();
        OptionSpec<String> filesO = parser.accepts("files", "A comma-separated list of files to extract").withRequiredArg().ofType(String.class).required();
        OptionSpec<String> filesReplaceO = parser.accepts("filesReplace", "A comma-separated list of files to extract and replace tokens").withRequiredArg().ofType(String.class);
        OptionSpec<String> executablesO = parser.accepts("executables", "A comma-separated list of files to extract and set the executable bit").withRequiredArg().ofType(String.class);
        OptionSpec<String> tokensO = parser.accepts("token", "Tokens to replace").withRequiredArg().ofType(String.class);

        try {
            OptionSet options = parser.parse(args);

            File archive = options.valueOf(archiveO);
            File target = options.valueOf(targetO);
            Path targetPath = target.toPath();
            Set<String> files = Arrays.stream(options.valueOf(filesO).split(",")).collect(Collectors.toSet());
            List<String> filesReplace = options.has(filesReplaceO) ? Arrays.asList(options.valueOf(filesReplaceO).split(",")) : null;
            List<String> executables = options.has(filesReplaceO) ? Arrays.asList(options.valueOf(executablesO).split(",")) : null;
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

            log("Archive:       " + archive);
            log("Target:        " + target);
            log("Files:         " + files);
            log("Files Replace: " + filesReplace);
            log("Executables:   " + executables);
            log("Tokens:        " + tokens);
            if (filesReplace != null)
                files.addAll(filesReplace);
            if (executables != null)
                files.addAll(executables);

            if (!archive.exists())
                error("Could not find archive: " + archive);
            if (!target.exists() && !target.mkdirs())
                error("Could not make target folder: " + target);

            try (FileSystem fs = FileSystems.newFileSystem(archive.toPath(), null)) {
                for (String file : files) {
                    Path path = fs.getPath(file);
                    if (!Files.exists(path))
                        log("Could not find file in archive: " + file);
                    Path copyTo = targetPath.resolve(path.toString());
                    if (!Files.exists(copyTo.getParent()) && !copyTo.getParent().toFile().mkdirs())
                        error("Couldn't make parent directory: " + copyTo.getParent().toAbsolutePath());

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
                    if (executables != null && executables.contains(file) && !copyTo.toFile().setExecutable(true)) {
                        log("Couldn't set executable bit for file: " + copyTo.toAbsolutePath());
                    }
                }
            }
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }
}
