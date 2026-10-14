import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;

public class BytecodeAuditor {

    static class MemberRef {
        String owner;
        String name;
        String desc;
        boolean isMethod;
        String sourceClass;

        public MemberRef(String owner, String name, String desc, boolean isMethod, String sourceClass) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.isMethod = isMethod;
            this.sourceClass = sourceClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MemberRef)) return false;
            MemberRef that = (MemberRef) o;
            return isMethod == that.isMethod && owner.equals(that.owner) && name.equals(that.name) && desc.equals(that.desc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, name, desc, isMethod);
        }
    }

    public static void main(String[] args) throws Exception {
        File serverJar = new File("versions/1.21.4/purpur-1.21.4.jar");
        File apiJar = new File("libraries/org/purpurmc/purpur/purpur-api/1.21.4-R0.1-SNAPSHOT/purpur-api-1.21.4-R0.1-SNAPSHOT.jar");

        System.out.println("Scanning API classes in " + apiJar + " and " + serverJar + "...");
        Set<String> allClasses = new HashSet<>();
        loadClassNames(apiJar, allClasses);
        loadClassNames(serverJar, allClasses);

        System.out.println("Total classes found: " + allClasses.size());

        System.out.println("Parsing constant pools of " + serverJar + "...");
        Set<MemberRef> checkedRefs = new LinkedHashSet<>();
        Set<String> referencedClasses = new TreeSet<>();

        try (JarFile jf = new JarFile(serverJar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String srcClass = entry.getName().substring(0, entry.getName().length() - 6);
                    try (InputStream is = jf.getInputStream(entry)) {
                        parseClassConstantPool(is, srcClass, checkedRefs, referencedClasses);
                    }
                }
            }
        }

        System.out.println("Total unique Bukkit/Paper referenced classes: " + referencedClasses.size());
        System.out.println("Total unique Bukkit/Paper member refs: " + checkedRefs.size());

        List<String> missingClasses = new ArrayList<>();
        for (String c : referencedClasses) {
            if (!allClasses.contains(c) && !c.startsWith("java/")) {
                missingClasses.add(c);
            }
        }

        System.out.println("\n=== MISSING CLASSES (" + missingClasses.size() + ") ===");
        for (String mc : missingClasses) {
            System.out.println("MISSING CLASS: " + mc);
        }

        // Now verify with ClassLoader for methods & fields
        System.out.println("\nVerifying methods and fields via URLClassLoader...");
        List<java.net.URL> urls = new ArrayList<>();
        urls.add(apiJar.toURI().toURL());
        urls.add(serverJar.toURI().toURL());

        File libsDir = new File("libraries");
        if (libsDir.exists()) {
            addJarsRecursively(libsDir, urls);
        }

        URLClassLoader cl = new URLClassLoader(urls.toArray(new java.net.URL[0]));

        List<String> missingMembers = new ArrayList<>();
        int checkedCount = 0;
        for (MemberRef ref : checkedRefs) {
            checkedCount++;
            String className = ref.owner.replace('/', '.');
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className, false, cl);
            } catch (Throwable t) {
                missingMembers.add("CLASS_NOT_FOUND (" + t + ") for member: " + ref.owner + "." + ref.name + " " + ref.desc + " (called by " + ref.sourceClass + ")");
                continue;
            }

            try {
                if (ref.isMethod) {
                    if ("<init>".equals(ref.name)) {
                        if (!hasConstructor(clazz, ref.desc, cl)) {
                            missingMembers.add("MISSING_CONSTRUCTOR: " + ref.owner + "." + ref.name + ref.desc + " (called by " + ref.sourceClass + ")");
                        }
                    } else if (!"<clinit>".equals(ref.name)) {
                        if (!hasMethod(clazz, ref.name, ref.desc, cl)) {
                            missingMembers.add("MISSING_METHOD: " + ref.owner + "." + ref.name + ref.desc + " (called by " + ref.sourceClass + ")");
                        }
                    }
                } else {
                    if (!hasField(clazz, ref.name, cl)) {
                        missingMembers.add("MISSING_FIELD: " + ref.owner + "." + ref.name + " " + ref.desc + " (called by " + ref.sourceClass + ")");
                    }
                }
            } catch (Throwable t) {
                missingMembers.add("LINKAGE_ERROR: " + ref.owner + "." + ref.name + ref.desc + " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ") (called by " + ref.sourceClass + ")");
            }
        }

        System.out.println("\n=== MISSING MEMBERS (" + missingMembers.size() + ") ===");
        for (String mm : missingMembers) {
            System.out.println(mm);
        }
        System.out.println("\nAudit complete! Checked " + checkedCount + " member references.");
    }

    static void addJarsRecursively(File dir, List<java.net.URL> urls) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                addJarsRecursively(f, urls);
            } else if (f.getName().endsWith(".jar")) {
                try {
                    urls.add(f.toURI().toURL());
                } catch (Exception ignored) {}
            }
        }
    }

    static void loadClassNames(File jar, Set<String> set) throws Exception {
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.getName().endsWith(".class")) {
                    set.add(e.getName().substring(0, e.getName().length() - 6));
                }
            }
        }
    }

    static void parseClassConstantPool(InputStream is, String srcClass, Set<MemberRef> refs, Set<String> refClasses) throws Exception {
        DataInputStream dis = new DataInputStream(new BufferedInputStream(is));
        int magic = dis.readInt();
        if (magic != 0xCAFEBABE) return;
        int minor = dis.readUnsignedShort();
        int major = dis.readUnsignedShort();
        int count = dis.readUnsignedShort();

        Object[] cp = new Object[count];
        int[] cpType = new int[count];

        for (int i = 1; i < count; i++) {
            int tag = dis.readUnsignedByte();
            cpType[i] = tag;
            switch (tag) {
                case 1: // Utf8
                    cp[i] = dis.readUTF();
                    break;
                case 3: // Integer
                case 4: // Float
                    dis.readInt();
                    break;
                case 5: // Long
                case 6: // Double
                    dis.readLong();
                    i++; // takes two entries
                    break;
                case 7: // Class
                    cp[i] = dis.readUnsignedShort();
                    break;
                case 8: // String
                case 16: // MethodType
                case 19: // Module
                case 20: // Package
                    dis.readUnsignedShort();
                    break;
                case 9: // Fieldref
                case 10: // Methodref
                case 11: // InterfaceMethodref
                case 12: // NameAndType
                case 17: // Dynamic
                case 18: // InvokeDynamic
                    int part1 = dis.readUnsignedShort();
                    int part2 = dis.readUnsignedShort();
                    cp[i] = new int[]{part1, part2};
                    break;
                case 15: // MethodHandle
                    dis.readUnsignedByte();
                    dis.readUnsignedShort();
                    break;
                default:
                    return; // unknown tag or stop
            }
        }

        // Now resolve references
        for (int i = 1; i < count; i++) {
            int tag = cpType[i];
            if (tag == 7) { // Class
                Integer nameIdx = (Integer) cp[i];
                if (nameIdx != null && nameIdx < count && cp[nameIdx] instanceof String) {
                    String className = (String) cp[nameIdx];
                    if (isTargetPackage(className)) {
                        refClasses.add(className);
                    }
                }
            } else if (tag == 9 || tag == 10 || tag == 11) { // Fieldref, Methodref, InterfaceMethodref
                int[] parts = (int[]) cp[i];
                int classIdx = parts[0];
                int natIdx = parts[1];

                if (classIdx < count && cp[classIdx] instanceof Integer) {
                    int classNameIdx = (Integer) cp[classIdx];
                    if (classNameIdx < count && cp[classNameIdx] instanceof String) {
                        String className = (String) cp[classNameIdx];
                        if (isTargetPackage(className)) {
                            refClasses.add(className);
                            if (natIdx < count && cp[natIdx] instanceof int[]) {
                                int[] natParts = (int[]) cp[natIdx];
                                int nameIdx = natParts[0];
                                int descIdx = natParts[1];
                                if (nameIdx < count && descIdx < count && cp[nameIdx] instanceof String && cp[descIdx] instanceof String) {
                                    String name = (String) cp[nameIdx];
                                    String desc = (String) cp[descIdx];
                                    boolean isMethod = (tag == 10 || tag == 11);
                                    refs.add(new MemberRef(className, name, desc, isMethod, srcClass));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static boolean isTargetPackage(String name) {
        return name.startsWith("org/bukkit/") || name.startsWith("io/papermc/paper/") || name.startsWith("net/kyori/adventure/") || name.startsWith("co/aikar/timings/");
    }

    static boolean hasConstructor(Class<?> clazz, String desc, ClassLoader cl) {
        for (java.lang.reflect.Constructor<?> c : clazz.getDeclaredConstructors()) {
            if (getMethodDescriptor(c).equals(desc)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasMethod(Class<?> clazz, String name, String desc, ClassLoader cl) {
        if (clazz.isInterface()) {
            if (hasMethod(Object.class, name, desc, cl)) return true;
        }
        Class<?> curr = clazz;
        while (curr != null) {
            for (java.lang.reflect.Method m : curr.getDeclaredMethods()) {
                if (m.getName().equals(name) && getMethodDescriptor(m).equals(desc)) {
                    return true;
                }
            }
            for (Class<?> iface : curr.getInterfaces()) {
                if (hasMethodInInterface(iface, name, desc)) {
                    return true;
                }
            }
            curr = curr.getSuperclass();
        }
        return false;
    }

    static boolean hasMethodInInterface(Class<?> iface, String name, String desc) {
        for (java.lang.reflect.Method m : iface.getDeclaredMethods()) {
            if (m.getName().equals(name) && getMethodDescriptor(m).equals(desc)) {
                return true;
            }
        }
        for (Class<?> superIface : iface.getInterfaces()) {
            if (hasMethodInInterface(superIface, name, desc)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasField(Class<?> clazz, String name, ClassLoader cl) {
        Class<?> curr = clazz;
        while (curr != null) {
            try {
                curr.getDeclaredField(name);
                return true;
            } catch (NoSuchFieldException ignored) {}
            for (Class<?> iface : curr.getInterfaces()) {
                if (hasFieldInInterface(iface, name)) {
                    return true;
                }
            }
            curr = curr.getSuperclass();
        }
        return false;
    }

    static boolean hasFieldInInterface(Class<?> iface, String name) {
        try {
            iface.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException ignored) {}
        for (Class<?> superIface : iface.getInterfaces()) {
            if (hasFieldInInterface(superIface, name)) {
                return true;
            }
        }
        return false;
    }

    static String getMethodDescriptor(java.lang.reflect.Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) {
            sb.append(getTypeDescriptor(p));
        }
        sb.append(")").append(getTypeDescriptor(m.getReturnType()));
        return sb.toString();
    }

    static String getMethodDescriptor(java.lang.reflect.Constructor<?> c) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : c.getParameterTypes()) {
            sb.append(getTypeDescriptor(p));
        }
        sb.append(")V");
        return sb.toString();
    }

    static String getTypeDescriptor(Class<?> c) {
        if (c.isPrimitive()) {
            if (c == void.class) return "V";
            if (c == boolean.class) return "Z";
            if (c == byte.class) return "B";
            if (c == char.class) return "C";
            if (c == short.class) return "S";
            if (c == int.class) return "I";
            if (c == long.class) return "J";
            if (c == float.class) return "F";
            if (c == double.class) return "D";
        }
        if (c.isArray()) {
            return "[" + getTypeDescriptor(c.getComponentType());
        }
        return "L" + c.getName().replace('.', '/') + ";";
    }
}
