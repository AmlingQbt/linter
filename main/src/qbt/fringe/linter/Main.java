package qbt.fringe.linter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import misc1.commons.options.HelpOptionsFragment;
import misc1.commons.options.NamedStringListArgumentOptionsFragment;
import misc1.commons.options.OptionsFragment;
import misc1.commons.options.OptionsResults;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

class Main {
    public static interface Options {
        public static final OptionsFragment<Options, ?, ImmutableList<String>> files = new NamedStringListArgumentOptionsFragment<Options>(ImmutableList.of("-f", "--file"), "Check this file");
        public static final OptionsFragment<Options, ?, ImmutableList<String>> dirs = new NamedStringListArgumentOptionsFragment<Options>(ImmutableList.of("-d", "--dir"), "Check this directory");
        public static final OptionsFragment<Options, ?, ?> help = new HelpOptionsFragment<Options>(ImmutableList.of("-h", "--help"), "Show help");
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
        OptionsResults<Options> o = OptionsResults.simpleParse(Options.class, "linter", args);

        ImmutableList.Builder<String> b = ImmutableList.builder();
        b.addAll(o.get(Options.files));
        for(String dir : o.get(Options.dirs)) {
            search(b, new File(dir));
        }

        ImmutableMap.Builder<String, Linter> lintersBuilder = ImmutableMap.builder();
        for(Map.Entry<String, Class<? extends Linter>> e : linterClasses.entrySet()) {
            lintersBuilder.put(e.getKey(), e.getValue().newInstance());
        }
        ImmutableMap<String, Linter> linters = lintersBuilder.build();

        boolean failed = false;
        for(String file : b.build()) {
            List<Triple<Integer, String, String>> failures = Lists.newArrayList();
            for(Map.Entry<String, Linter> lintersEntry : linters.entrySet()) {
                for(Pair<Integer, String> failure : lintersEntry.getValue().check(slurp(file))) {
                    failures.add(Triple.of(failure.getLeft(), lintersEntry.getKey(), failure.getRight()));
                }
            }
            Collections.sort(failures, new Comparator<Triple<Integer, String, String>>() {
                @Override
                public int compare(Triple<Integer, String, String> l, Triple<Integer, String, String> r) {
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
                }
            });
            for(Triple<Integer, String, String> failure : failures) {
                System.out.println(file + ":" + (failure.getLeft() + 1) + ":" + failure.getMiddle() + ":" + failure.getRight());
                failed = true;
            }
        }
        if(failed) {
            System.exit(1);
        }
        else {
            System.out.println("Linter complete without errors.");
        }
    }

    public static List<String> slurp(String path) {
        try {
            FileInputStream fis = new FileInputStream(path);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImmutableList.Builder<String> b = ImmutableList.builder();
            while(true) {
                int bb = fis.read();
                if(bb < 0) {
                    byte[] line = baos.toByteArray();
                    if(line.length > 0) {
                        b.add(new String(line));
                    }
                    fis.close();
                    return b.build();
                }
                baos.write(bb);
                if(bb == '\n') {
                    b.add(new String(baos.toByteArray()));
                    baos.reset();
                }
            }
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
