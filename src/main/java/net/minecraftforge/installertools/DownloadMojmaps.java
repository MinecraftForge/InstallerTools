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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraftforge.installertools.util.ManifestJson;
import net.minecraftforge.installertools.util.VersionJson;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IMappingFile.Format;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class DownloadMojmaps extends Task {
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<String> versionO = parser.accepts("version").withRequiredArg().ofType(String.class).required();
        OptionSpec<String> sideO = parser.accepts("side").withRequiredArg().ofType(String.class).required();
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();
        OptionSpec<Void> sanitizeO = parser.accepts("sanitize");
        OptionSpec<Format> formatO = parser.accepts("format").withRequiredArg().ofType(Format.class);
        OptionSpec<Void> skipIfExistsO = parser.accepts("skipIfExists");

        try {
            OptionSet options = parser.parse(args);

            String mcversion = options.valueOf(versionO);
            String side = options.valueOf(sideO);
            File output = options.valueOf(outputO).getAbsoluteFile();
            boolean sanitize = options.has(sanitizeO);
            Format format = !options.has(formatO) ? Format.TSRG : options.valueOf(formatO);
            boolean skip = options.has(skipIfExistsO);

            log("MC Version: " + mcversion);
            log("Side:       " + side);
            log("Output:     " + output);
            log("Sanitize:   " + sanitize);
            log("Format:     " + format);
            log("Skip:       " + skip);

            // Just trust it, The preferred method is to use the sanitized format and use the installer's output caching but this is added just in case.
            if (output.exists() && skip) {
                log("Skipping as output file exists");
                return;
            }

            File parent = output.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                error("Could not make output folders: " + parent);

            try (InputStream manIn = new URL(MANIFEST_URL).openStream()) {
                URL url = GSON.fromJson(new InputStreamReader(manIn), ManifestJson.class).getUrl(mcversion);
                if (url == null)
                    error("Missing version from manifest: " + mcversion);

                try (InputStream verIn = url.openStream()) {
                    VersionJson json = VersionJson.load(verIn);
                    if (json == null)
                        error("Missing Minecraft version JSON from URL " + url);

                    VersionJson.Download download = json.downloads.get(side + "_mappings");
                    if (download == null || download.url == null)
                        error("Missing download info for " + side + " mappings");

                    if (sanitize) {
                        try (InputStream is = download.url.openStream()) {
                            // Sending it through the load/write process nukes all the comments and other things that may be in the file.
                            // As well as sorts things. So it *should* result in the same output file as long as Mojang doesn't change
                            // any of the actual functional content of the file
                            IMappingFile map = IMappingFile.load(is);
                            map.write(output.toPath(), format, false);
                        }
                    } else {
                        Files.copy(download.url.openStream(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    log("Downloaded Mojang mappings for " + mcversion);
                }
            }
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }
}
