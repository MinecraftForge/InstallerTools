/*
 * InstallerTools
 * Copyright (c) 2019-2019.
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import de.siegmar.fastcsv.reader.CsvContainer;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class SrgMcpRenamer extends Task {

    @Override
    public void process(String[] args) throws IOException {

        OptionParser parser = new OptionParser();
        OptionSpec<File> mcpO = parser.accepts("mcp").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> inputO = parser.accepts("input").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();

        try {
            OptionSet options = parser.parse(args);

            File mcp = options.valueOf(mcpO).getAbsoluteFile();
            File input = options.valueOf(inputO).getAbsoluteFile();
            File output = options.valueOf(outputO).getAbsoluteFile();

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
                    CsvReader reader = new CsvReader();
                    reader.setContainsHeader(true);
                    CsvContainer csv = reader.read(new InputStreamReader(zip.getInputStream(entry)));
                    for (CsvRow row : csv.getRows()) {
                        String searge = row.getField("searge");
                        if (searge == null)
                            searge = row.getField("param");
                        map.put(searge, row.getField("name"));
                    }
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

            ByteArrayOutputStream memory = input.equals(output) ? new ByteArrayOutputStream() : null;
            try (ZipOutputStream zout = new ZipOutputStream(memory == null ? new FileOutputStream(output) : memory);
                ZipInputStream zin = new ZipInputStream(new FileInputStream(input))) {
                ZipEntry ein = null;
                while ((ein = zin.getNextEntry()) != null) {
                    if (ein.getName().endsWith(".class")) {
                        byte[] data = toByteArray(zin);

                        ClassReader reader = new ClassReader(data);
                        ClassWriter writer = new ClassWriter(0);
                        reader.accept(new ClassRemapper(writer, remapper), 0);
                        data = writer.toByteArray();

                        ZipEntry eout = new ZipEntry(ein.getName());
                        eout.setTime(0x386D4380); //01/01/2000 00:00:00 java 8 breaks when using 0.
                        zout.putNextEntry(eout);
                        zout.write(data);
                    } else {
                        zout.putNextEntry(ein);
                        copy(zin, zout);
                    }
                }
            }

            if (memory != null)
                Files.write(output.toPath(), memory.toByteArray());

            log("Process complete");
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }

    private byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    private void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[0x100];
        int cnt = 0;
        while ((cnt = input.read(buf, 0, buf.length)) != -1) {
            output.write(buf, 0, cnt);
        }
    }
}
