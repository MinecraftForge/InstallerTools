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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraftforge.installertools.util.Utils;

public class Copy extends Task {

    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> inputO = parser.accepts("input").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();

        try {
            OptionSet options = parser.parse(args);

            File input = options.valueOf(inputO).getAbsoluteFile();
            File output = options.valueOf(outputO).getAbsoluteFile();

            log("Input:  " + input);
            log("Output: " + output);

            if (!input.exists()) error("Missing required input: " + input);
            if (output.exists() && output.isDirectory()) error("Output is a directory: " + output);

            if (output.exists()) output.delete();
            if (!output.getParentFile().exists()) output.getParentFile().mkdirs();

            try (FileInputStream inputStream = new FileInputStream(input); FileOutputStream outputStream = new FileOutputStream(output)) {
                Utils.copy(inputStream, outputStream);
            }
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }

}