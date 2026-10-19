import java.nio.file.*;
import java.util.*;

/** Configure at JVM creation: Windows AF_UNIX does not prefer java.io.tmpdir. */
public final class StartupPaths {
    static Path temporary(Path root) throws Exception {
        Path base=root.toRealPath(), tmp=base.resolve("tmp");
        Files.createDirectories(tmp);
        if(!tmp.toRealPath().equals(tmp))throw new SecurityException("Sandbox tmp must remain inside owned state");
        if(System.getProperty("os.name").startsWith("Windows")
                && tmp.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+18>107)
            throw new IllegalArgumentException("Owned tmp path exceeds Windows AF_UNIX limit; preserve state and request a scoped path review");
        return tmp;
    }
    static List<String> vmArgs(Path root) throws Exception {
        String tmp=temporary(root).toString();
        return List.of("-Djava.io.tmpdir="+tmp,"-Djdk.net.unixdomain.tmpdir="+tmp);
    }
    static void environment(Map<String,String> env,Path root) throws Exception {
        String tmp=temporary(root).toString();
        env.put("TEMP",tmp);env.put("TMP",tmp);
    }
    static void verify(Path root) throws Exception {
        Path tmp=temporary(root);
        for(String key:List.of("java.io.tmpdir","jdk.net.unixdomain.tmpdir")) {
            String value=System.getProperty(key);
            if(value==null||!Path.of(value).toAbsolutePath().normalize().equals(tmp))
                throw new SecurityException("Launch with owned selector socket directory: "+key);
        }
    }
}
