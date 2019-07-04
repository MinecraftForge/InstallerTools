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
import java.io.IOException;
import java.lang.reflect.Field;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.ProgressMeter;
import net.md_5.specialsource.SpecialSource;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.minecraftforge.installertools.util.VersionJson;
import net.minecraftforge.installertools.util.VersionJson.Library;

public class DeobfRealms extends Task {

    @Override
    public void process(String[] args) throws IOException {

        OptionParser parser = new OptionParser();
        OptionSpec<File> mapO = parser.accepts("map").withRequiredArg().ofType(File.class).required();
        OptionSpec<String> mcpO = parser.accepts("mcp").withRequiredArg().ofType(String.class).required();
        OptionSpec<File> mcJarO = parser.accepts("mc").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> jsonO = parser.accepts("json").withRequiredArg().ofType(File.class);
        OptionSpec<File> libsO = parser.accepts("libs").withRequiredArg().ofType(File.class);

        try {
            OptionSet options = parser.parse(args);

            File map = options.valueOf(mapO);
            String mcp = options.valueOf(mcpO);
            File mcJar = options.valueOf(mcJarO);
            //TODO: Think about sided processors next time a breaking change is needed anyway
            if(mcJar.getName().contains("server")){
                log("Detected server jar, skipping client-only DEOBF_REALMS processor");
                return;
            }
            File json = options.has(jsonO) ? options.valueOf(jsonO) : new File(mcJar.getAbsolutePath().substring(0, mcJar.getAbsolutePath().length() - 4) + ".json"); // .jar -> .json As long as the launcher doesn't change this structure we're fine.
            File libs = options.has(libsO) ? options.valueOf(libsO) : new File(mcJar.getParentFile().getParentFile().getParentFile(), "libraries"); // './versions/version/version.jar' -> './libraries/'

            log("Map:  " + map);
            log("MCP:  " + mcp);
            log("Jar:  " + mcJar);
            log("Json: " + json);
            log("Libs: " + libs);

            if (!map.exists())
                error("Missing required Map: " + map);
            if (!mcJar.exists())
                error("Missing required MC jar: " + mcJar);
            if (!json.exists())
                error("Missing required Json: " + json);
            if (!libs.exists())
                error("Missing required Library Directory: " + libs);

            log("Loading Json: " + json);
            VersionJson jsonData = VersionJson.load(json);

            if (jsonData.libraries == null) {
                log("No libraries, assuming no realms in this version");
                return;
            }

            log("Scanning Libraries: ");
            Library realms = null;
            for (Library tmp : jsonData.libraries) {
                log("  " + tmp.name);
                if ("com.mojang".equals(tmp.getArtifact().getGroup()) &&
                    "realms".equals(tmp.getArtifact().getName())) {
                    realms = tmp;
                    break;
                }
            }

            if (realms == null) {
                log("No \"com.mojang:realms\" library found, assuming realms disabled for this version.");
                return;
            }

            String path = realms.downloads == null || realms.downloads.artifact == null ? realms.getArtifact().getPath() : realms.downloads.artifact.path;

            File vanilla = new File(libs, path);
            File target = new File(libs, path.substring(0, path.length() - 4) + '-' + mcp + ".jar");

            if (target.exists()) {
                log("Target \"" + target.getAbsolutePath() + "\" exists, Assuming job done");
                return;
            }

            if (!vanilla.exists())
                error("Missing realms jar \"" + vanilla.getAbsolutePath() + "\" Can not process.");

            JarMapping mapping = new JarMapping();
            mapping.loadMappings(map);

            setVerbose();

            try (Jar vanillaJar = Jar.init(vanilla);
                 Jar mcSSJar = Jar.init(mcJar)) {
                JointProvider inheritanceProviders = new JointProvider();
                inheritanceProviders.add(new JarProvider(vanillaJar));
                inheritanceProviders.add(new JarProvider(mcSSJar));
                mapping.setFallbackInheritanceProvider(inheritanceProviders);

                JarRemapper remapper = new JarRemapper(mapping);
                try {
                    remapper.remapJar(vanillaJar, target);
                } catch (IOException e) {
                    if (target.exists())
                        target.delete();
                    throw new RuntimeException(e);
                }
            }

            log("Process complete");
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }

    private void setVerbose() {
        try {
            ProgressMeter.printInterval = 10;
            Field verbose = SpecialSource.class.getDeclaredField("verbose");
            verbose.setAccessible(true);
            verbose.set(null, true);
        } catch (Throwable e) {
            log("Could not set verbose. Log may appear to freeze, it's not.");
        }
    }
}
