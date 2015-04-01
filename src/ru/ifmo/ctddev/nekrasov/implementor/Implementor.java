package ru.ifmo.ctddev.nekrasov.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * Creates a class which extends some class or implement some interface,
 * writes the resultant class to <tt>.java</tt> file, and if requested compiles it
 * and writes resultant <tt>.class</tt> files to <tt>.jar</tt> file.
 * <p>
 * Implements {@link info.kgeorgiy.java.advanced.implementor.JarImpler}.
 *
 * @author Dmitry Nekrasov
 */

public class Implementor implements JarImpler {
    private File absoluteFile;

    /**
     * Creates a class which extends some class or implement some interface,
     * implementing default non-private constructors and non-private abstract methods.
     *
     * @param parent a class or interface which the implemented class extended or implemented from
     * @param root root directory of the class's package system
     * @throws ImplerException if <code>parent</code> is primitive, final or has no default non-private constructors
     */
    public void implement(Class<?> parent, File root) throws ImplerException {
        if (parent.isPrimitive()) {
            throw new ImplerException("Class is primitive");
        } else if (Modifier.isFinal(parent.getModifiers())) {
            throw new ImplerException("Class is final");
        }

        String childName = parent.getSimpleName() + "Impl";
        String path = parent.getCanonicalName().replace(".", File.separator) + "Impl.java";
        absoluteFile = new File(root, path);
        absoluteFile.getParentFile().mkdirs();

        try (BufferedWriter writer = Files.newBufferedWriter(absoluteFile.toPath(), Charset.forName("UTF-8"))) {
            writer.write("package " + parent.getPackage().getName() + ";\n\n");

            writer.write("public class " + childName);
            if (parent.isInterface()) {
                writer.write(" implements ");
            } else {
                writer.write(" extends ");
            }
            writer.write(parent.getCanonicalName() + " {");

            Constructor[] constructors = parent.getDeclaredConstructors();
            boolean hasConstructors = constructors.length == 0;
            for (Constructor constructor : constructors) {
                if (!Modifier.isPrivate(constructor.getModifiers())) {
                    writeConstructor(writer, constructor, childName);
                    hasConstructors = true;
                }
            }
            if (!hasConstructors) {
                throw new ImplerException("there is no default non-private constructor");
            }

            Set<HashableMethod> methods = getUniqueMethods(parent);
            for (HashableMethod hashableMethod : methods) {
                Method method = hashableMethod.getMethod();
                if (!Modifier.isFinal(method.getModifiers()) && !Modifier.isPrivate(method.getModifiers())) {
                    writeMethod(writer, method);
                }
            }

            writer.write("\n}");
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Creates a class calling implement(parent, jarFile)
     * and then compiles it and packs it into a jar file.
     *
     * @param parent a class or interface which the implemented class extended or implemented from
     * @param jarFile target <tt>.jar</tt> file
     * @throws ImplerException if corresponding implement method throws it,
     * the class hasn't been compiled or something hasn't been written to a jar file
     */
    public void implementJar(Class<?> parent, File jarFile) throws ImplerException {
        implement(parent, jarFile);
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final List<String> args = new ArrayList<>();
        args.add(absoluteFile.getAbsolutePath());
        final int exitCode = compiler.run(null, null, null, args.toArray(new String[args.size()]));
        if (exitCode != 0) {
            throw new ImplerException("Couldn't compile a class");
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        File jar2File = new File(jarFile, parent.getSimpleName() + "Impl.jar");
        try (JarOutputStream writer = new JarOutputStream(Files.newOutputStream(jar2File.toPath()), manifest)) {
            File upperFolderFile = new File(parent.getPackage().getName().split("\\.")[0]);
            addEntry(upperFolderFile, writer);
        } catch (IOException e) {
            throw new ImplerException("Couldn't write an JAR file");
        }
    }

    /**
     * Writes source code for a constructor, which calls superclass constructor with the same arguments.
     *
     * @param writer opened file to write to
     * @param constructor the constructor to write
     * @param className name of the constructor's class
     * @throws IOException if <code>writer</code> throws it
     */
    private void writeConstructor(BufferedWriter writer, Constructor constructor, String className) throws IOException {
        writeHeader(writer, constructor, className);

        writer.write("\t\tsuper(");
        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            if (i != 0) {
                writer.write(", ");
            }
            writer.write("arg" + (i + 1));
        }
        writer.write(");\n");

        writer.write("\t}");
    }

    /**
     * Writes source code for a method, which returns standard value
     * (false for boolean return type, 0 for integral type or null for an object)
     * if it isn't void.
     *
     * @param writer opened file to write to
     * @param method the method to write
     * @throws IOException if <code>writer</code> throws it
     */
    private void writeMethod(BufferedWriter writer, Method method) throws IOException {
        Class returnType = method.getReturnType();
        String name = method.getName();
        writeHeader(writer, method, returnType.getCanonicalName() + " " + name);

        if (!returnType.equals(void.class)) {
            writer.write("\t\treturn ");
            if (returnType.equals(boolean.class)) {
                writer.write("false");
            } else if (returnType.isPrimitive()) {
                writer.write("0");
            } else {
                writer.write("null");
            }
            writer.write(";\n");
        }

        writer.write("\t}");
    }

    /**
     * Writes an executable's (a constructor's or a method's) header.
     *
     * @param writer opened file to write to
     * @param executable the executable whose header is to write
     * @param name name of the executable
     * @throws IOException if <code>writer</code> throws it
     */
    private void writeHeader(BufferedWriter writer, Executable executable, String name) throws IOException {
        writer.write("\n\n\t");

        int modifiers = executable.getModifiers();
        Class[] parameterTypes = executable.getParameterTypes();
        Class[] exceptionTypes = executable.getExceptionTypes();

        writeModifiers(writer, modifiers);
        writer.write(name + "(");
        writeParameters(writer, parameterTypes);
        writer.write(")");
        writeExceptions(writer, exceptionTypes);
        writer.write(" {\n");
    }

    /**
     * Writes executable's modifiers.
     *
     * @param writer opened file to write to
     * @param modifiers the modifiers to write
     * @throws IOException if <code>writer</code> throws it
     */
    private void writeModifiers(BufferedWriter writer, int modifiers) throws IOException {
        if (Modifier.isPublic(modifiers)) {
            writer.write("public ");
        } else if (Modifier.isProtected(modifiers)) {
            writer.write("protected ");
        }
        if (Modifier.isStatic(modifiers)) {
            writer.write("static ");
        }
    }

    /**
     * Writes executable's parameters.
     *
     * @param writer opened file to write to
     * @param parameterTypes the parameters' types to write
     * @throws IOException if <code>writer</code> throws it
     */
    private void writeParameters(BufferedWriter writer, Class[] parameterTypes) throws IOException {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i != 0) {
                writer.write(", ");
            }
            writer.write(parameterTypes[i].getCanonicalName() + " arg" + (i + 1));
        }
    }

    /**
     * Writes executable's exceptions.
     *
     * @param writer opened file to write to
     * @param exceptionTypes the exceptions to write
     * @throws IOException if <code>writer</code> throws it
     */
    private void writeExceptions(BufferedWriter writer, Class[] exceptionTypes) throws IOException {
        if (exceptionTypes.length == 0) {
            return;
        }
        writer.write(" throws ");
        writer.write(String.join(", ", Arrays.asList(exceptionTypes).stream().map(Class::getCanonicalName)
                .collect(Collectors.toList())));
    }

    /**
     * Gets a set of unique abstract methods of the class.
     *
     * @param clazz a class with methods to get
     * @return a {@link java.util.Set} of {@link ru.ifmo.ctddev.nekrasov.implementor.Implementor.HashableMethod}
     */
    private Set<HashableMethod> getUniqueMethods(Class<?> clazz) {
        Class class1 = clazz;
        Set<HashableMethod> hashSet = new HashSet<>();
        while (class1 != null && !class1.equals(Object.class)) {
            for (Method method : class1.getDeclaredMethods()) {
                checkMethod(hashSet, method, true);
            }
            for (Method method : class1.getMethods()) {
                checkMethod(hashSet, method, true);
            }
            class1 = class1.getSuperclass();
        }
        class1 = clazz;
        while (class1 != null && !class1.equals(Object.class)) {
            for (Method method : class1.getDeclaredMethods()) {
                checkMethod(hashSet, method, false);
            }
            for (Method method : class1.getMethods()) {
                checkMethod(hashSet, method, false);
            }
            class1 = class1.getSuperclass();
        }
        return hashSet;
    }

    /**
     * Adds an abstract method to a set or removes an implemented from the set.
     *
     * @param hashSet the set of methods to add to or to remove from
     * @param method the method to check
     * @param isAbstract if true it adds the method otherwise it removes
     */
    private void checkMethod(Set<HashableMethod> hashSet, Method method, boolean isAbstract) {
        HashableMethod hashableMethod = new HashableMethod(method);
        if (isAbstract && Modifier.isAbstract(method.getModifiers())) {
            hashSet.add(hashableMethod);
        } else if (!isAbstract && !Modifier.isAbstract(method.getModifiers())) {
            hashSet.remove(hashableMethod);
        }
    }

    /**
     * Adds an entry for a file to a <tt>.jar</tt> file. If the file is a directory it also creates
     * entries for child files and directories.
     *
     * @param file a file which the entry is attached to
     * @param writer the <tt>.jar</tt> file to write
     * @throws IOException if the entry hasn't been written to <tt>.jar</tt> file.
     */
    private void addEntry(File file, JarOutputStream writer) throws IOException {
        JarEntry entry = new JarEntry(file.getPath());
        writer.putNextEntry(entry);
        if (file.isDirectory()) {
            writer.closeEntry();
            for (File child : file.listFiles()) {
                addEntry(child, writer);
            }
        } else {
            try (BufferedInputStream reader = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, count);
                }
            } catch (IOException e) {
                e.getLocalizedMessage();
            }
            writer.closeEntry();
        }
    }


    /**
     * Implements a method which is hashed, so it can be added to hashset.
     */
    private class HashableMethod {
        /**
         * The hashed method.
         */
        private Method method;

        /**
         * Class constructor.
         *
         * @param method the method to be hashable
         */
        public HashableMethod(Method method) {
            this.method = method;
        }

        /**
         * Gets the method of this object.
         *
         * @return the method of this object
         */
        public Method getMethod() {
            return method;
        }

        /**
         * Gets hash code of the method.
         *
         * @return hash code of the method
         */
        public int hashCode() {
            int hash = method.getName().hashCode();
            for (Class clazz : method.getParameterTypes()) {
                hash ^= clazz.hashCode();
            }
            return hash;
        }

        /**
         * Compares this object to an object.
         *
         * @param o an object to compare to
         * @return true if <code>o</code> is an instance of {@link HashableMethod},
         * the method of <code>o</code> is equal to this method
         * and this method's type is assignable from return type of the method of <code>o</code>,
         * otherwise false
         */
        public boolean equals(Object o) {
            if (o instanceof HashableMethod) {
                Method method1 = ((HashableMethod) o).getMethod();
                return (method.getName().equals(method1.getName()))
                        & (Arrays.equals(method.getGenericParameterTypes(), method1.getGenericParameterTypes())
                        & method.getReturnType().isAssignableFrom(method1.getReturnType()));
            } else {
                return false;
            }
        }
    }
}
