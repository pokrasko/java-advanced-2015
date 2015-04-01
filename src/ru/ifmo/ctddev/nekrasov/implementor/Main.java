package ru.ifmo.ctddev.nekrasov.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/*public class Main extends BaseTester {
    public static void main(String[] args) throws ImplerException {
        new Main()
                .add("interface", InterfaceImplementorTest.class)
                .add("class", ClassImplementorTest.class)
                .run(new String[]{"class", "ru.ifmo.ctddev.nekrasov.implementor.Implementor"});
    }
}*/

public class Main {
    public static void main(String[] args) throws ClassNotFoundException, ImplerException {
        Implementor implementor = new Implementor();
        if (args[0] == null || args[0].equals("-jar") && args[1] == null) {
            System.err.println("Invalid call format");
            return;
        }
        if (args[0].equals("-jar")) {
            File jarFile = new File(args[2]);
            try (final URLClassLoader loader = getClassLoader(jarFile)) {
                implementor.implementJar(loader.loadClass(args[1]), jarFile);
            } catch (final IOException e) {
                throw new ImplerException("couldn't read the jar file");
            }
        } else {
            implementor.implement(Class.forName(args[0]), new File("."));
        }
    }

    private static URLClassLoader getClassLoader(final File root) {
        try {
            return new URLClassLoader(new URL[]{root.toURI().toURL()});
        } catch (final MalformedURLException e) {
            throw new AssertionError(e);
        }
    }
}
