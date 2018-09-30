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
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class CreateDirectory extends Task {

    @Override
    public void process(String[] args) throws IOException {

        OptionParser parser = new OptionParser();
        OptionSpec<File> targetO = parser.accepts("target").withRequiredArg().ofType(File.class).required();

        try {
            OptionSet options = parser.parse(args);

            File target = options.valueOf(targetO);

            log("Target: " + target);

            if (!target.exists() && !target.mkdirs())
                error("Could not make folders: " + target);
            log("Directory created");
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }
}
