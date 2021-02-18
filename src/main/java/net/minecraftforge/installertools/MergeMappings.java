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
