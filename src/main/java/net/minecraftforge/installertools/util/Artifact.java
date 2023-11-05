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

import java.io.File;
import java.util.Locale;

public class Artifact {
    //Descriptor parts: group:name:version[:classifier][@extension]
    private String group;
    private String name;
    private String version;
    private String classifier = null;
    private String ext = "jar";

    //Caches so we don't rebuild every time we're asked.
    private String path;
    private String file;
    private String descriptor;
    private boolean isSnapshot = false;

    public static Artifact from(String descriptor) {
        Artifact ret = new Artifact();
        ret.descriptor = descriptor;

        String[] pts = descriptor.split(":");
        ret.group = pts[0];
        ret.name = pts[1];

        int last = pts.length - 1;
        int idx = pts[last].indexOf('@');
        if (idx != -1) {
            ret.ext = pts[last].substring(idx + 1);
            pts[last] = pts[last].substring(0, idx);
        }

        ret.version = pts[2];
        ret.isSnapshot = ret.version.toLowerCase(Locale.ENGLISH).endsWith("-snapshot");

        if (pts.length > 3)
            ret.classifier = pts[3];

        ret.file = ret.name + '-' + ret.version;
        if (ret.classifier != null) ret.file += '-' + ret.classifier;
        ret.file += '.' + ret.ext;

        ret.path = String.join("/", ret.group.replace('.', '/'), ret.name, ret.version, ret.file);

        return ret;
    }

    public static Artifact from(String group, String name, String version, String classifier, String ext) {
        StringBuilder buf = new StringBuilder();
        buf.append(group).append(':').append(name).append(':').append(version);
        if (classifier != null)
            buf.append(':').append(classifier);
        if (ext != null && !"jar".equals(ext))
            buf.append('@').append(ext);
        return from(buf.toString());
    }

    public File getLocalFile(File base) {
        return new File(base, getLocalPath());
    }

    public String getLocalPath() {
        return path.replace('/', File.separatorChar);
    }

    public String getDescriptor(){ return descriptor; }
    public String getPath()      { return path;       }
    public String getGroup()     { return group;      }
    public String getName()      { return name;       }
    public String getVersion()   { return version;    }
    public String getClassifier(){ return classifier; }
    public String getExtension() { return ext;        }
    public String getFilename()  { return file;       }

    public boolean isSnapshot()  { return isSnapshot; }

    public Artifact withVersion(String version) {
        return Artifact.from(group, name, version, classifier, ext);
    }

    @Override
    public String toString() {
        return getDescriptor();
    }
}
