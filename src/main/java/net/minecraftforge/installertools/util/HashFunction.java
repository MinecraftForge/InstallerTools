/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installertools.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

//These are all standard hashing functions the JRE is REQUIRED to have, so add a nice factory that doesn't require catching annoying exceptions;
public enum HashFunction {
    MD5("md5", 32),
    SHA1("SHA-1", 40),
    SHA256("SHA-256", 64);

    private String algo;
    private String pad;

    private HashFunction(String algo, int length) {
        this.algo = algo;
        this.pad = String.format(Locale.ENGLISH, "%0" + length + "d", 0);
    }

    public String getExtension() {
         return this.name().toLowerCase(Locale.ENGLISH);
    }

    public MessageDigest get() {
        try {
            return MessageDigest.getInstance(algo);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e); //Never happens
        }
    }

    public String hash(File file) throws IOException {
        try (FileInputStream fin = new FileInputStream(file)) {
            return hash(fin);
        }
    }

    public String hashNullable(File file) {
        try (FileInputStream fin = new FileInputStream(file)) {
            return hash(fin);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String hash(Iterable<File> files) throws IOException {
        MessageDigest hash = get();
        byte[] buf = new byte[1024];

        for (File file : files) {
            if (!file.exists())
                continue;

            try (FileInputStream fin = new FileInputStream(file)) {
                int count = -1;
                while ((count = fin.read(buf)) != -1)
                    hash.update(buf, 0, count);
            }
        }
        return pad(new BigInteger(1, hash.digest()).toString(16));
    }

    public String hash(String data) {
        return hash(data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8));
    }

    public String hash(InputStream stream) throws IOException {
        MessageDigest hash = get();
        byte[] buf = new byte[1024];
        int count = -1;
        while ((count = stream.read(buf)) != -1)
            hash.update(buf, 0, count);
        return pad(new BigInteger(1, hash.digest()).toString(16));
    }

    public String hash(byte[] data) {
        return pad(new BigInteger(1, get().digest(data)).toString(16));
    }

    public String pad(String hash) {
        return (pad + hash).substring(hash.length());
    }
}
