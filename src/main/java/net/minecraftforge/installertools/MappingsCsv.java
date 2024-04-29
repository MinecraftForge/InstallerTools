/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installertools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraftforge.srgutils.IMappingFile;

public class MappingsCsv extends Task {
    public static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    public static final long ZIPTIME = 628041600000L;

    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> mapO = parser.accepts("srg").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> clientO = parser.accepts("client").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> serverO = parser.accepts("server").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();

        try {
            OptionSet options = parser.parse(args);

            File map = options.valueOf(mapO);
            File client = options.valueOf(clientO);
            File server = options.valueOf(serverO);
            File output = options.valueOf(outputO);

            log("SRG:    " + map);
            log("Client: " + client);
            log("Server: " + server);
            log("Output: " + output);

            if (output.exists() && !delete(output))
                error("Could not delete output file: " + output);

            File parent = output.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                error("Could not make output folders: " + parent);

            if (!map.exists())
                error("SRG does not exist: " + map);
            if (!client.exists())
                error("Client does not exist: " + client);
            if (!server.exists())
                error("Server does not exist: " + server);


            IMappingFile pg_client = IMappingFile.load(client);
            IMappingFile pg_server = IMappingFile.load(server);
            IMappingFile srg = IMappingFile.load(map);

            Map<String, String> cfields = new TreeMap<>();
            Map<String, String> sfields = new TreeMap<>();
            Map<String, String> cmethods = new TreeMap<>();
            Map<String, String> smethods = new TreeMap<>();
            gatherNames(srg, pg_client, cfields, cmethods);
            gatherNames(srg, pg_server, sfields, smethods);

            String[] header = new String[] {"searge", "name", "side", "desc"};
            List<String[]> fields = new ArrayList<>();
            List<String[]> methods = new ArrayList<>();
            fields.add(header);
            methods.add(header);

            for (String name : cfields.keySet()) {
                String cname = cfields.get(name);
                String sname = sfields.get(name);
                if (cname.equals(sname)) {
                    fields.add(new String[]{name, cname, "2", ""});
                    sfields.remove(name);
                } else
                    fields.add(new String[]{name, cname, "0", ""});
            }

            for (String name : cmethods.keySet()) {
                String cname = cmethods.get(name);
                String sname = smethods.get(name);
                if (cname.equals(sname)) {
                    methods.add(new String[]{name, cname, "2", ""});
                    smethods.remove(name);
                } else
                    methods.add(new String[]{name, cname, "0", ""});
            }

            sfields.forEach((k,v) -> fields.add(new String[] {k, v, "1", ""}));
            smethods.forEach((k,v) -> methods.add(new String[] {k, v, "1", ""}));

            try (FileOutputStream fos = new FileOutputStream(output);
                    ZipOutputStream out = new ZipOutputStream(fos)) {
                writeCsv("fields.csv", fields, out);
                writeCsv("methods.csv", methods, out);
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

    private void gatherNames(IMappingFile srg, IMappingFile official, Map<String, String> fields, Map<String, String> methods) {
        for (IMappingFile.IClass cls : official.getClasses()) {
            IMappingFile.IClass obf = srg.getClass(cls.getMapped());
            if (obf == null) // Class exists in official source, but doesn't make it past obfusication so it's not in our mappings.
                continue;

            for (IMappingFile.IField fld : cls.getFields()) {
                String name = obf.remapField(fld.getMapped());
                if (name.startsWith("field_") || name.startsWith("f_"))
                    fields.put(name, fld.getOriginal());
            }

            for (IMappingFile.IMethod mtd : cls.getMethods()) {
                String name = obf.remapMethod(mtd.getMapped(), mtd.getMappedDescriptor());
                if (name.startsWith("func_") || name.startsWith("m_"))
                    methods.put(name, mtd.getOriginal());
            }
        }
    }


    public static ZipEntry getStableEntry(String name) {
        return getStableEntry(name, ZIPTIME);
    }

    public static ZipEntry getStableEntry(String name, long time) {
        TimeZone _default = TimeZone.getDefault();
        TimeZone.setDefault(GMT);
        ZipEntry ret = new ZipEntry(name);
        ret.setTime(time);
        TimeZone.setDefault(_default);
        return ret;
    }


    protected static void writeCsv(String name, List<String[]> mappings, ZipOutputStream out) throws IOException {
        if (mappings.size() <= 1)
            return;

        out.putNextEntry(getStableEntry(name));

        byte[] comma = ",".getBytes(StandardCharsets.UTF_8);
        byte[] lf = "\n".getBytes(StandardCharsets.UTF_8);

        for (String[] row : mappings) {
            for (int x = 0; x < row.length; x++) {
                out.write(row[x].getBytes(StandardCharsets.UTF_8));
                if (x != row.length - 1)
                    out.write(comma);
            }
            out.write(lf);
        }
        out.closeEntry();
    }
}
