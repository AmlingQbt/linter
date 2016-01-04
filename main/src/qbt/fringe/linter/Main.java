package qbt.fringe.linter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import misc1.commons.options.NamedStringListArgumentOptionsFragment;
import misc1.commons.options.OptionsFragment;
import misc1.commons.options.OptionsResults;
import misc1.commons.options.SimpleMain;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

public class Main extends SimpleMain<Main.Options, Exception> {
    public static interface Options {
        public static final OptionsFragment<Options, ?, ImmutableList<String>> files = new NamedStringListArgumentOptionsFragment<Options>(ImmutableList.of("-f", "--file"), "Check this file");
        public static final OptionsFragment<Options, ?, ImmutableList<String>> dirs = new NamedStringListArgumentOptionsFragment<Options>(ImmutableList.of("-d", "--dir"), "Check this source directory");
        public static final OptionsFragment<Options, ?, ImmutableList<String>> jars = new NamedStringListArgumentOptionsFragment<Options>(ImmutableList.of("-j", "--jars"), "Check this jar file");
        public static final OptionsFragment<Options, ?, ImmutableList<String>> libs = new NamedStringListArgumentOptionsFragment<Options>(ImmutableList.of("-l", "--libs"), "Check this directory of jar files");
        public static final OptionsFragment<Options, ?, ?> help = simpleHelpOption();
    }

    @Override
    protected Class<Options> getOptionsClass() {
        return Options.class;
    }

    private static final Map<String, Class<? extends Linter>> linterClasses;
    static {
        ImmutableMap.Builder<String, Class<? extends Linter>> b = ImmutableMap.builder();

        b.put("controlCoddling", ControlCoddlingLinter.class);
        b.put("controlSpacing", ControlSpacingLinter.class);
        b.put("importsOrder", ImportsOrderLinter.class);
        b.put("simpleImportsOnly", SimpleImportsOnlyLinter.class);
        b.put("unusedImports", UnusedImportsLinter.class);
        b.put("lineEnds", LineEndsLinter.class);
        b.put("whitespace", WhitespaceLinter.class);

        linterClasses = b.build();
    }

    private static void search(ImmutableList.Builder<String> b, File f) {
        if(f.isDirectory()) {
            for(File f2 : f.listFiles()) {
                search(b, f2);
            }
        }
        if(f.isFile() && f.getName().endsWith(".java")) {
            b.add(f.getAbsolutePath());
        }
    }

    public static void main(String[] args) throws Exception {
        new Main().exec(args);
    }

    @Override
    public int run(OptionsResults<Options> o) throws Exception {
        ImmutableList.Builder<String> files = ImmutableList.builder();
        files.addAll(o.get(Options.files));
        for(String dir : o.get(Options.dirs)) {
            search(files, new File(dir));
        }

        ImmutableList.Builder<String> jars = ImmutableList.builder();
        jars.addAll(o.get(Options.jars));
        for(String libs : o.get(Options.libs)) {
            for(File jar : new File(libs).listFiles()) {
                if(jar.getName().endsWith(".jar")){
                    jars.add(jar.getAbsolutePath());
                }
            }
        }

        ImmutableMap.Builder<String, Linter> lintersBuilder = ImmutableMap.builder();
        for(Map.Entry<String, Class<? extends Linter>> e : linterClasses.entrySet()) {
            lintersBuilder.put(e.getKey(), e.getValue().newInstance());
        }
        ImmutableMap<String, Linter> linters = lintersBuilder.build();

        int violations = 0;
        for(String file : files.build()) {
            try(FileInputStream is = new FileInputStream(file)) {
                violations += check(linters, file, slurp(is));
            }
        }
        for(String jar : jars.build()) {
            try(FileInputStream is = new FileInputStream(jar)) {
                try(ZipInputStream zis = new ZipInputStream(is)) {
                    while(true) {
                        ZipEntry ze = zis.getNextEntry();
                        if(ze == null) {
                            break;
                        }
                        if(ze.isDirectory()) {
                            continue;
                        }
                        if(!ze.getName().endsWith(".java")) {
                            continue;
                        }
                        List<String> lines = slurp(zis);
                        violations += check(linters, jar + "!" + ze.getName(), lines);
                    }
                }
            }
        }
        if(violations > 0) {
            System.err.println("Linter complete with " + violations + " errors.");
            return 1;
        }
        else {
            System.err.println("Linter complete without errors.");
            return 0;
        }
    }

    private static List<String> slurp(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImmutableList.Builder<String> b = ImmutableList.builder();
        while(true) {
            int bb = is.read();
            if(bb < 0) {
                byte[] line = baos.toByteArray();
                if(line.length > 0) {
                    b.add(new String(line));
                }
                return b.build();
            }
            baos.write(bb);
            if(bb == '\n') {
                b.add(new String(baos.toByteArray()));
                baos.reset();
            }
        }
    }

    private static int check(ImmutableMap<String, Linter> linters, String label, List<String> lines) {
        List<Triple<Integer, String, String>> failures = Lists.newArrayList();
        for(Map.Entry<String, Linter> lintersEntry : linters.entrySet()) {
            for(Pair<Integer, String> failure : lintersEntry.getValue().check(lines)) {
                failures.add(Triple.of(failure.getLeft(), lintersEntry.getKey(), failure.getRight()));
            }
        }
        Collections.sort(failures, (l, r) -> {
            int lLine = l.getLeft();
            int rLine = r.getLeft();
            if(lLine < rLine) {
                return -1;
            }
            if(lLine > rLine) {
                return 1;
            }

            int ret = l.getMiddle().compareTo(r.getMiddle());
            if(ret != 0) {
                return ret;
            }

            return l.getRight().compareTo(r.getRight());
        });
        for(Triple<Integer, String, String> failure : failures) {
            System.out.println(label + ":" + (failure.getLeft() + 1) + ":" + failure.getMiddle() + ":" + failure.getRight());
        }
        return failures.size();
    }
}
