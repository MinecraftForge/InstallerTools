package net.minecraftforge.installertools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraftforge.installertools.util.ManifestJson;
import net.minecraftforge.installertools.util.VersionJson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;

public class DownloadMojmaps extends Task {
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<String> versionO = parser.accepts("version").withRequiredArg().ofType(String.class).required();
        OptionSpec<String> sideO = parser.accepts("side").withRequiredArg().ofType(String.class).required();
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();

        try {
            OptionSet options = parser.parse(args);

            String mcversion = options.valueOf(versionO);
            String side = options.valueOf(sideO);
            File output = options.valueOf(outputO);

            log("MC Version: " + mcversion);
            log("Side:       " + side);
            log("Output:     " + output);

            if (output.exists() && !output.delete())
                error("Could not delete output file: " + output);

            if (!output.getParentFile().exists() && !output.getParentFile().mkdirs())
                error("Could not make output folders: " + output.getParentFile());

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

                    Files.copy(download.url.openStream(), output.toPath());
                    log("Downloaded Mojang mappings for " + mcversion);
                }
            }
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }
}
