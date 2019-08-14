/*
 * InstallerTools
 * Copyright (c) 2019-2019.
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraftforge.installertools.util.Utils;

import static org.objectweb.asm.Opcodes.*;

public class ExtractInheritance extends Task {
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithModifiers(Modifier.PRIVATE).setPrettyPrinting().create();
    private Map<String, ClassInfo> inClasses = new HashMap<>();
    private Map<String, ClassInfo> libClasses = new HashMap<>();
    private Set<String> failedClasses = new HashSet<>();

    @Override
    public void process(String[] args) throws IOException {

        OptionParser parser = new OptionParser();
        OptionSpec<File> inputO = parser.accepts("input").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> libraryO = parser.accepts("lib").withRequiredArg().ofType(File.class);

        try {
            OptionSet options = parser.parse(args);

            File input = options.valueOf(inputO).getAbsoluteFile();
            File output = options.valueOf(outputO).getAbsoluteFile();

            log("Input:  " + input);
            log("Output: " + output);

            if (!input.exists())
                error("Missing required input jar: " + input);

            if (output.exists()) output.delete();
            if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
            output.createNewFile();

            log("Reading Input: " + input);
            readJar(input, inClasses);

            for (File lib : options.valuesOf(libraryO)) {
                log("Reading Library: " + lib);
                readJar(lib, libClasses);
            }

            for (Entry<String, ClassInfo> entry : inClasses.entrySet())
                resolveClass(entry.getValue());

            Files.write(output.toPath(), GSON.toJson(inClasses).getBytes(StandardCharsets.UTF_8));

            log("Process complete");
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }

    private void readJar(File input, Map<String, ClassInfo> classes) throws IOException {
        try (ZipFile inJar = new ZipFile(input)) {
            Utils.forZip(inJar, entry -> {
                if (!entry.getName().endsWith(".class") || entry.getName().startsWith("."))
                    return;
                ClassReader reader = new ClassReader(Utils.toByteArray(inJar.getInputStream(entry)));
                ClassNode classNode = new ClassNode();
                reader.accept(classNode, 0);
                ClassInfo info = new ClassInfo(classNode);
                classes.put(info.name, info);
            });
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("Could not open input file: " + e.getMessage());
        }
    }

    private void resolveClass(ClassInfo cls) {
        if (cls == null || cls.resolved)
            return;

        if (!cls.name.equals("java/lang/Object") && cls.superName != null)
            resolveClass(getClassInfo(cls.superName));

        if (cls.interfaces != null)
            for (String intf : cls.interfaces)
                resolveClass(getClassInfo(intf));

        if (cls.methods != null) {
            for (MethodInfo mtd : cls.methods.values()) {
                if ("<init>".equals(mtd.getName()) || "<cinit>".equals(mtd.getName()))
                    continue;
                if ((mtd.access & (ACC_PRIVATE | ACC_STATIC)) != 0)
                    continue;

                MethodInfo override = null;
                Queue<ClassInfo> que = new ArrayDeque<>();
                Set<String> processed = new HashSet<>();

                if (cls.superName != null)
                    addQueue(cls.superName, processed, que);
                if (cls.interfaces != null)
                    cls.interfaces.forEach(intf -> addQueue(intf, processed, que));

                while (!que.isEmpty()) {
                    ClassInfo c = que.poll();
                    if (c.superName != null)
                        addQueue(c.superName, processed, que);
                    if (c.interfaces != null)
                        c.interfaces.forEach(intf -> addQueue(intf, processed, que));

                    MethodInfo m = c.getMethod(mtd.getName(), mtd.getDesc());

                    int bad_flags = ACC_PRIVATE | ACC_FINAL | ACC_STATIC;
                    if (m == null || (m.access & bad_flags) != 0)
                        continue;

                    override = m;
                }

                if (override != null)
                    mtd.override = override.getParent().name;
            }
        }

        cls.resolved = true;
    }

    private void addQueue(String cls, Set<String> visited, Queue<ClassInfo> que) {
        if (!visited.contains(cls)) {
            ClassInfo ci = getClassInfo(cls);
            if (ci != null)
                que.add(ci);
            visited.add(cls);
        }
    }

    private ClassInfo getClassInfo(String name) {
        ClassInfo ret = inClasses.get(name);
        if (ret != null)
            return ret;
        ret = libClasses.get(name);
        if (ret == null && !failedClasses.contains(name)) {
            try {
                Class<?> cls = Class.forName(name.replaceAll("/", "."), false, this.getClass().getClassLoader());
                ret = new ClassInfo(cls);
                libClasses.put(name, ret);
            } catch (ClassNotFoundException ex) {
                log("Cant Find Class: " + name);
                failedClasses.add(name);
            }
        }
        return ret;
    }

    private static class ClassInfo {
        public final String name;
        @SuppressWarnings("unused")
        public final int access;
        public final String superName;
        public final List<String> interfaces;
        public final Map<String, MethodInfo> methods;

        private boolean resolved = false;

        private Map<String, MethodInfo> makeMap(List<MethodInfo> lst) {
            if (lst.isEmpty())
                return null;
            Map<String, MethodInfo> ret = new HashMap<>();
            lst.forEach(info -> ret.put(info.getName() + " " + info.getDesc(), info));
            return ret;
        }

        ClassInfo(ClassNode node) {
            this.name = node.name;
            this.access = node.access;
            this.superName = node.superName;
            this.interfaces = node.interfaces.isEmpty() ? null : node.interfaces;

            List<MethodInfo> lst = new ArrayList<>();
            if (!node.methods.isEmpty())
                node.methods.forEach(mn -> lst.add(new MethodInfo(this, mn)));
            this.methods = makeMap(lst);
        }

        ClassInfo(Class<?> node) {
            this.name = node.getName().replace('.', '/');
            this.access = node.getModifiers();
            this.superName = node.getSuperclass() == null ? null : node.getSuperclass().getName().replace('.', '/');
            List<String> intfs = new ArrayList<>();
            for (Class<?> i : node.getInterfaces())
                intfs.add(i.getName().replace('.', '/'));
            this.interfaces = intfs.isEmpty() ? null : intfs;

            List<MethodInfo> mtds = new ArrayList<>();

            for (Constructor<?> ctr : node.getConstructors())
                mtds.add(new MethodInfo(this, ctr));

            for (Method mtd : node.getDeclaredMethods())
                mtds.add(new MethodInfo(this, mtd));

            this.methods = makeMap(mtds);
        }

        public MethodInfo getMethod(String name, String desc) {
            return methods == null ? null : methods.get(name + " " + desc);
        }
    }

    private static class MethodInfo {
        private final String name;
        private final String desc;
        public final int access;
        @SuppressWarnings("unused")
        public List<String> exceptions;
        private final ClassInfo parent;
        @SuppressWarnings("unused")
        public final Bouncer bouncer;

        @SuppressWarnings("unused")
        public String override = null;

        MethodInfo(ClassInfo parent, MethodNode node) {
            this.name = node.name;
            this.desc = node.desc;
            this.access = node.access;
            this.exceptions = node.exceptions.isEmpty() ? null : new ArrayList<>(node.exceptions);
            this.parent = parent;
            Bouncer bounce = null;

            if ((node.access & (ACC_SYNTHETIC | ACC_BRIDGE)) != 0 && (node.access & ACC_STATIC) == 0) {
                AbstractInsnNode start = node.instructions.getFirst();
                if (start instanceof LabelNode && start.getNext() instanceof LineNumberNode)
                    start = start.getNext().getNext();

                if (start instanceof VarInsnNode) {
                    VarInsnNode n = (VarInsnNode)start;
                    if (n.var == 0 && n.getOpcode() == ALOAD) {
                        AbstractInsnNode end = node.instructions.getLast();
                        if (end instanceof LabelNode)
                            end = end.getPrevious();

                        if (end.getOpcode() >= IRETURN && end.getOpcode() <= RETURN)
                            end = end.getPrevious();

                        if (end instanceof MethodInsnNode) {
                            while (start != end) {
                                if (!(start instanceof VarInsnNode) && start.getOpcode() != INSTANCEOF && start.getOpcode() != CHECKCAST) {
                                    end = null;
                                    break;
                                    /* We're in a lambda. so lets exit.
                                    System.out.println("Bounce? " + parent.name + "/" + name + desc);
                                    for (AbstractInsnNode asn : node.instructions.toArray())
                                        System.out.println("  " + asn);
                                    */
                                }
                                start = start.getNext();
                            }

                            MethodInsnNode mtd = (MethodInsnNode)end;
                            if (end != null && mtd.owner.equals(parent.name) &&
                                Type.getArgumentsAndReturnSizes(node.desc) == Type.getArgumentsAndReturnSizes(mtd.desc)) {
                                bounce = new Bouncer(mtd.name, mtd.desc);
                            }
                        }
                    }
                }
            }
            this.bouncer = bounce;
        }

        MethodInfo(ClassInfo parent, Method node) {
            this.name = node.getName();
            this.desc = Type.getMethodDescriptor(node);
            this.access = node.getModifiers();
            List<String> execs = new ArrayList<>();
            for (Class<?> e : node.getExceptionTypes())
                execs.add(e.getName().replace('.', '/'));
            this.exceptions = execs.isEmpty() ? null : execs;
            this.parent = parent;
            this.bouncer = null;
        }

        MethodInfo(ClassInfo parent, Constructor<?> node) {
            this.name = "<init>";
            this.desc = Type.getConstructorDescriptor(node);
            this.access = node.getModifiers();
            List<String> execs = new ArrayList<>();
            for (Class<?> e : node.getExceptionTypes())
                execs.add(e.getName().replace('.', '/'));
            this.exceptions = execs.isEmpty() ? null : execs;
            this.parent = parent;
            this.bouncer = null;
        }

        public ClassInfo getParent() {
            return parent;
        }

        public String toString() {
            return parent.name + "/" + name + desc;
        }

        public String getName() {
            return name;
        }

        public String getDesc() {
            return desc;
        }
    }

    public static class Bouncer {
        public final String name;
        public final String desc;
        public Bouncer(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }
    }
}
