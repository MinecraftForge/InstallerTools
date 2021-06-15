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
package net.minecraftforge.installertools.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VersionJson {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(VersionJson.Argument.class, new VersionJson.Argument.Deserializer())
            .setPrettyPrinting().create();

    public static VersionJson load(File path) {
        try (InputStream stream = new FileInputStream(path)) {
            return load(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static VersionJson load(InputStream stream) {
        return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), VersionJson.class);
    }

    public Arguments arguments;
    public AssetIndex assetIndex;
    public String assets;
    public Map<String, Download> downloads;
    public Library[] libraries;

    private List<LibraryDownload> _natives = null;

    public List<LibraryDownload> getNatives() {
        if (_natives == null) {
            _natives = new ArrayList<>();
            OS os = OS.getCurrent();
            for (Library lib : libraries) {
                if (lib.natives != null && lib.downloads.classifiers != null && lib.natives.containsKey(os.getName())) {
                    LibraryDownload l = lib.downloads.classifiers.get(lib.natives.get(os.getName()));
                    if (l != null) {
                        _natives.add(l);
                    }
                }
            }
        }
        return _natives;
    }

    public List<String> getPlatformJvmArgs() {
        return Stream.of(arguments.jvm).filter(arg -> arg.rules != null && arg.isAllowed()).
                flatMap(arg -> arg.value.stream()).
                map(s -> {
                    if (s.indexOf(' ') != -1)
                        return "\"" + s + "\"";
                    else
                        return s;
                }).collect(Collectors.toList());
    }

    public static class Arguments {
        public Argument[] game;
        public Argument[] jvm;
    }

    public static class Argument {
        public Rule[] rules;
        public List<String> value;

        public Argument(Rule[] rules, List<String> value) {
            this.rules = rules;
            this.value = value;
        }

        public boolean isAllowed() {
            if (rules != null) {
                for (Rule rule : rules) {
                    if (!rule.allowsAction()) {
                        return false;
                    }
                }
            }
            return true;
        }

        public static class Deserializer implements JsonDeserializer<VersionJson.Argument> {
            @Override
            public Argument deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                if (json.isJsonPrimitive()) {
                    return new Argument(null, Collections.singletonList(json.getAsString()));
                }

                JsonObject obj = json.getAsJsonObject();
                if (!obj.has("rules") || !obj.has("value"))
                    throw new JsonParseException("Error parsing arguments in version json. File is corrupt or its format has changed.");

                JsonElement val = obj.get("value");
                Rule[] rules = GSON.fromJson(obj.get("rules"), Rule[].class);
                @SuppressWarnings("unchecked")
                List<String> value = val.isJsonPrimitive() ? Collections.singletonList(val.getAsString()) : GSON.fromJson(val, List.class);

                return new Argument(rules, value);
            }
        }
    }

    public static class Rule {
        public String action;
        public OsCondition os;

        public boolean allowsAction() {
            return os != null && os.platformMatches() == action.equals("allow");
        }
    }

    public static class OsCondition {
        public String name;
        public String version;
        public String arch;

        public boolean nameMatches() {
            return name == null || OS.getCurrent().getName().equals(name);
        }

        public boolean versionMatches() {
            return version == null || Pattern.compile(version).matcher(System.getProperty("os.version")).find();
        }

        public boolean archMatches() {
            return arch == null || Pattern.compile(arch).matcher(System.getProperty("os.arch")).find();
        }

        public boolean platformMatches() {
            return nameMatches() && versionMatches() && archMatches();
        }
    }

    public static class AssetIndex extends Download {
        public String id;
        public int totalSize;
    }

    public static class Download {
        public String sha1;
        public int size;
        public URL url;
    }

    public static class LibraryDownload extends Download {
        public String path;
    }

    public static class Downloads {
        public Map<String, LibraryDownload> classifiers;
        public LibraryDownload artifact;
    }

    public static class Library {
        //Extract? rules?
        public String name;
        public Map<String, String> natives;
        public Downloads downloads;
        private Artifact _artifact;

        public Artifact getArtifact() {
            if (_artifact == null) {
                _artifact = Artifact.from(name);
            }
            return _artifact;
        }
    }

    public static enum OS {
        WINDOWS("windows", "win"),
        LINUX("linux", "linux", "unix"),
        OSX("osx", "mac"),
        UNKNOWN("unknown");

        private final String name;
        private final String[] keys;

        private OS(String name, String... keys) {
            this.name = name;
            this.keys = keys;
        }

        public String getName() {
            return this.name;
        }

        public static OS getCurrent() {
            String prop = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
            for (OS os : OS.values()) {
                for (String key : os.keys) {
                    if (prop.contains(key)) {
                        return os;
                    }
                }
            }
            return UNKNOWN;
        }
    }
}
