package io.github.zopulus.coogle;

import java.nio.file.*;
import java.lang.reflect.Method;
import java.util.Comparator;

/** Executes the production status command; old 1.3.2 must be recognized as needing a payload update. */
public final class CompanionUpgradeRegressionTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("coogle-pr6-upgrade");
        Path active = root.resolve("active"), staged = root.resolve("staged");
        int failures = 0;
        try {
            failures += expect(active, staged, "MISSING", "fresh install before staging");
            fixture(active, "controlVersion=1\nversion=1.3.2\nversionCode=132\n");
            failures += expect(active, staged, "UPDATE_REQUIRED", "installed released 1.3.2 (no payload revision)");
            fixture(staged, "controlVersion=1\nversion=1.3.2\nversionCode=132\n");
            failures += expect(active, staged, "UPDATE_REQUIRED", "staged old 1.3.2 must not hide missing update");
            fixture(staged, "controlVersion=1\npayloadRevision=3\n");
            failures += expect(active, staged, "PENDING", "new payload staged over old active module");
            try(var files=Files.walk(staged)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
            fixture(active, "controlVersion=1\npayloadRevision=3\n");
            failures += expect(active, staged, "READY", "new payload after reboot");
            for(String revision:new String[]{"1","2","bad","-1","2\npayloadRevision=3"}) {
                fixture(active,"controlVersion=1\npayloadRevision="+revision+"\n");
                failures += expect(active,staged,"UPDATE_REQUIRED","old/invalid revision "+revision.replace('\n',' '));
            }
            fixture(active,"controlVersion=1\npayloadRevision=4\n");
            failures += expect(active,staged,"READY","newer compatible payload must not be downgraded");
            Files.delete(active.resolve("mount-google.sh"));
            failures += expect(active,staged,"UPDATE_REQUIRED","required mount helper missing");
            fixture(active,"controlVersion=2\npayloadRevision=3\n");
            failures += expect(active,staged,"UPDATE_REQUIRED","incompatible control protocol");
            fixture(active,"controlVersion=1\npayloadRevision=3\n");
            Files.createFile(active.resolve("disable"));
            failures += expect(active, staged, "DISABLED", "disabled current payload");
        } finally {
            try(var files=Files.walk(root)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
        }
        if(failures!=0) throw new AssertionError(failures+" upgrade regression cases failed");
    }
    private static void fixture(Path dir,String properties) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("module.prop"),"id=coogle_companion\n"+properties);
        Files.writeString(dir.resolve("features.sh"),"fixture\n");
        Files.writeString(dir.resolve("mount-google.sh"),"fixture\n");
        Files.writeString(dir.resolve("features.conf"),"hey=0\n");
        Files.writeString(dir.resolve("applied-features.conf"),"hey=0\n");
        Files.writeString(dir.resolve("applied-boot-id"),"current-boot\n");
    }
    private static int expect(Path active,Path staged,String expected,String name) throws Exception {
        Method method=CompanionInstaller.class.getDeclaredMethod("statusCommand"); method.setAccessible(true);
        String command=((String)method.invoke(null)).replace("/data/adb/modules/coogle_companion",unix(active))
                .replace("/proc/sys/kernel/random/boot_id",unix(active.getParent().resolve("boot-id")))
                .replace("/data/adb/modules_update/coogle_companion",unix(staged));
        Files.writeString(active.getParent().resolve("boot-id"),"current-boot\n");
        Process p=new ProcessBuilder("C:/Program Files/Git/bin/bash.exe","-c",command).redirectErrorStream(true).start();
        String actual=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        if(p.waitFor()!=0) throw new AssertionError(actual);
        boolean pass=expected.equals(actual);
        System.out.println((pass?"PASS":"FAIL")+": "+name+" expected="+expected+" actual="+actual);
        return pass?0:1;
    }
    private static String unix(Path path) { String s=path.toString().replace('\\','/');return "/"+Character.toLowerCase(s.charAt(0))+s.substring(2); }
}
