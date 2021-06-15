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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.INamedMappingFile;
import net.minecraftforge.srgutils.IRenamer;
import net.minecraftforge.srgutils.IMappingFile.IClass;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;
import net.minecraftforge.srgutils.IMappingFile.IPackage;
import net.minecraftforge.srgutils.IMappingFile.IParameter;

public class ChainMappings extends Task {
    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> leftO = parser.accepts("left").withRequiredArg().ofType(File.class).required();
        OptionSpec<Void> reverseLeftO = parser.accepts("reverse-left");
        OptionSpec<String> leftNamesO = parser.accepts("left-names").withRequiredArg().ofType(String.class);
        OptionSpec<File> rightO = parser.accepts("right").withRequiredArg().ofType(File.class).required();
        OptionSpec<Void> reverseRightO = parser.accepts("reverse-right");
        OptionSpec<String> rightNamesO = parser.accepts("right-names").withRequiredArg().ofType(String.class);
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();

        OptionSpec<Void> classesO = parser.accepts("classes");
        OptionSpec<Void> fieldsO  = parser.accepts("fields");
        OptionSpec<Void> methodsO = parser.accepts("methods");
        OptionSpec<Void> paramsO  = parser.accepts("params");

        try {
            OptionSet options = parser.parse(args);

            File left = options.valueOf(leftO);
            File right = options.valueOf(rightO);
            String[] leftNames = parseNames(options, leftNamesO);
            String[] rightNames = parseNames(options, rightNamesO);
            File output = options.valueOf(outputO);

            final boolean selective = options.has(classesO) || options.has(methodsO) || options.has(fieldsO) || options.has(paramsO);
            final boolean classes = !selective || options.has(classesO);
            final boolean fields  = !selective || options.has(fieldsO);
            final boolean methods = !selective || options.has(methodsO);
            final boolean params  = !selective || options.has(paramsO);

            log("Left:    " + left);
            log("         Reversed=" + options.has(reverseLeftO));
            log("         " + (leftNames == null ? "null" : options.valueOf(leftNamesO)));
            log("Right:   " + right);
            log("         Reversed=" + options.has(reverseRightO));
            log("         " + (rightNames == null ? "null" : options.valueOf(rightNamesO)));
            log("Classes: " + classes);
            log("Fields:  " + fields);
            log("Methods: " + methods);
            log("Params:  " + params);
            log("Output:  " + output);

            if (output.exists() && !delete(output))
                error("Could not delete output file: " + output);

            if (!output.getParentFile().exists() && !output.getParentFile().mkdirs())
                error("Could not make output folders: " + output.getParentFile());

            if (!left.exists())
                error("Left does not exist: " + left);
            if (!right.exists())
                error("Right does not exist: " + right);


            IMappingFile leftM = leftNames == null ? IMappingFile.load(left) : INamedMappingFile.load(left).getMap(leftNames[0], leftNames[1]);
            if (options.has(reverseLeftO))
                leftM = leftM.reverse();
            IMappingFile rightM = rightNames == null ? IMappingFile.load(right) : INamedMappingFile.load(right).getMap(rightNames[0], rightNames[1]);
            if (options.has(reverseRightO))
                rightM = rightM.reverse();
            IMappingFile outputM = leftM.rename(makeRenamer(rightM, classes, fields, methods, params));

            outputM.write(output.toPath(), IMappingFile.Format.TSRG2, false);
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

    private String[] parseNames(OptionSet options, OptionSpec<String> spec) {
        if (!options.has(spec))
            return null;
        String[] ret = options.valueOf(spec).split(",");
        if (ret.length != 2)
            throw new IllegalArgumentException("Name argument must be exactly two values, EXA: left,right");
        return ret;
    }

    protected IRenamer makeRenamer(IMappingFile link, boolean classes, boolean fields, boolean methods, boolean params) {
        return new IRenamer() {
            public String rename(IPackage value) {
                return link.remapPackage(value.getMapped());
            }

            public String rename(IClass value) {
                return classes ? link.remapClass(value.getMapped()) : value.getMapped();
            }

            public String rename(IField value) {
                IClass cls = link.getClass(value.getParent().getMapped());
                return cls == null || !fields ? value.getMapped() : cls.remapField(value.getMapped());
            }

            public String rename(IMethod value) {
                IClass cls = link.getClass(value.getParent().getMapped());
                return cls == null || !methods ? value.getMapped() : cls.remapMethod(value.getMapped(), value.getMappedDescriptor());
            }

            public String rename(IParameter value) {
                IMethod mtd = value.getParent();
                IClass cls = link.getClass(mtd.getParent().getMapped());
                mtd = cls == null ? null : cls.getMethod(mtd.getMapped(), mtd.getMappedDescriptor());
                return mtd == null || !params ? value.getMapped() : mtd.remapParameter(value.getIndex(), value.getMapped());
            }
        };
    }
}
