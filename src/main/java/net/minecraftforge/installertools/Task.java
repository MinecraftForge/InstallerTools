/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installertools;

import java.io.IOException;

public abstract class Task {
    public abstract void process(String[] args) throws IOException;

    protected void error(String message) {
        log(message);
        throw new RuntimeException(message);
    }

    protected void log(String message) {
        System.out.println(message);
    }
}
