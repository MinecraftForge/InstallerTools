/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installertools;

import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IRenamer;
import net.minecraftforge.srgutils.IMappingFile.IClass;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;
import net.minecraftforge.srgutils.IMappingFile.IPackage;
import net.minecraftforge.srgutils.IMappingFile.IParameter;

public class MergeMappings extends ChainMappings {
    @Override
    protected IRenamer makeRenamer(IMappingFile link, boolean classes, boolean fields, boolean methods, boolean params) {
        return new IRenamer() {
            public String rename(IPackage value) {
                return link.remapPackage(value.getOriginal());
            }

            public String rename(IClass value) {
                return classes ? link.remapClass(value.getOriginal()) : value.getMapped();
            }

            public String rename(IField value) {
                IClass cls = link.getClass(value.getParent().getOriginal());
                return cls == null || !fields ? value.getMapped() : cls.remapField(value.getOriginal());
            }

            public String rename(IMethod value) {
                IClass cls = link.getClass(value.getParent().getOriginal());
                return cls == null || !methods ? value.getMapped() : cls.remapMethod(value.getOriginal(), value.getDescriptor());
            }

            public String rename(IParameter value) {
                IMethod mtd = value.getParent();
                IClass cls = link.getClass(mtd.getParent().getOriginal());
                mtd = cls == null ? null : cls.getMethod(mtd.getOriginal(), mtd.getDescriptor());
                return mtd == null || !params ? value.getMapped() : mtd.remapParameter(value.getIndex(), value.getMapped());
            }
        };
    }
}
