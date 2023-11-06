/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
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
