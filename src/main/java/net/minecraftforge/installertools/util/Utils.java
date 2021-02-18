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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Utils {
    public static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[0x100];
        int cnt = 0;
        while ((cnt = input.read(buf, 0, buf.length)) != -1) {
            output.write(buf, 0, cnt);
        }
    }

    public static void forZip(ZipFile zip, IOConsumer<ZipEntry> consumer) throws IOException {
        for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
            consumer.accept(entries.nextElement());
        }
    }

    @FunctionalInterface
    public static interface IOConsumer<T> {
        public void accept(T value) throws IOException;
    }
}
