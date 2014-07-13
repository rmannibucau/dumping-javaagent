package com.github.rmannibucau.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class DumpingAgent {
    private static Instrumentation instrumentation;

    public static void premain(final String agentArgs, final Instrumentation instrumentation) {
        start(agentArgs, instrumentation);
    }

    public static void agentmain(final String agentArgs, final Instrumentation instrumentation) {
        start(agentArgs, instrumentation);
    }

    private static synchronized void start(final String agentArgs, final Instrumentation instrumentation) {
        if (DumpingAgent.instrumentation != null) {
            return;
        }

        DumpingAgent.instrumentation = instrumentation;
        if (agentArgs == null) {
            System.err.println("DumpingAgent needs a folder to dump classes, use -javaagent:dumping-agent.jar=/tmp/foo for instance");
            return;
        }

        final int filterIdx = agentArgs.indexOf('?');
        final File outputFolder;
        final String filter;
        if (filterIdx > 0) {
            outputFolder = new File(agentArgs.substring(0, filterIdx));
            filter = agentArgs.substring(filterIdx + 1, agentArgs.length());
        } else {
            outputFolder = new File(agentArgs);
            filter = null;
        }

        if (!mkdirs(outputFolder) || !outputFolder.canWrite()) {
            System.err.println("DumpingAgent needs an existing and writable folder to dump classes, " + agentArgs + " doesn't exist");
            return;
        }

        instrumentation.addTransformer(new Dumper(outputFolder, filter));
    }

    private static class Dumper implements ClassFileTransformer {
        private final File base;
        private final String filter;

        public Dumper(final File outputFolder, final String filter) {
            this.base = outputFolder;
            this.filter = filter;
        }

        @Override
        public byte[] transform(final ClassLoader loader, final String className,
                                final Class<?> classBeingRedefined,
                                final ProtectionDomain protectionDomain,
                                final byte[] classfileBuffer) throws IllegalClassFormatException {
            dump(className, classfileBuffer);
            return classfileBuffer;
        }

        private void dump(final String className, final byte[] classfileBuffer) {
            if (filter != null && !className.replace('/', '.').startsWith(filter)) {
                return;
            }

            final File file = new File(base, className + ".class");
            final File parentFile = file.getParentFile();
            if (!mkdirs(parentFile)) {
                return;
            }

            FileOutputStream writer = null;
            try {
                writer = new FileOutputStream(file);
                writer.write(classfileBuffer);
            } catch (final IOException e) {
                e.printStackTrace();
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (final IOException e) {
                        // no-op
                    }
                }
            }
        }
    }

    private static boolean mkdirs(final File dir) {
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            System.err.println("Can't create: " + dir.getAbsolutePath());
            return false;
        }
        return true;
    }
}
