package qbt.fringe.linter;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.Pair;

public class UnusedImportsLinter implements Linter {
    enum State {
        ZERO,
        SLASH,
        LINE_COMMENT,
        BIG_COMMENT,
        BIG_COMMENT_STAR,
        SQUOTE,
        SQUOTE_BACKSLASH,
        DQUOTE,
        DQUOTE_BACKSLASH,
        ;
    }

    private static final Pattern IMPORT_PATTERN = Pattern.compile("import [^ ]*\\.([^ \\.]*);\n");

    @Override
    public List<Pair<Integer, String>> check(List<String> lines) {
        final Multimap<String, Integer> unusedImports = HashMultimap.create();
        for(int i = 0; i < lines.size(); ++i) {
            Matcher m = IMPORT_PATTERN.matcher(lines.get(i));
            if(m.matches()) {
                unusedImports.put(m.group(1), i);
            }
        }

        State s = State.ZERO;
        class Id {
            StringBuffer sb = new StringBuffer();

            public void append(char c) {
                if(c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                    sb.append(c);
                }
                else {
                    sep();
                }
            }

            public void sep() {
                int len = sb.length();
                if(len > 0) {
                    unusedImports.asMap().remove(sb.toString());
                    sb.delete(0, len);
                }
            }
        }
        Id id = new Id();
        for(int i = 0; i < lines.size(); ++i) {
            String line = lines.get(i);
            if(IMPORT_PATTERN.matcher(line).matches()) {
                continue;
            }
            for(int j = 0; j < line.length(); ++j) {
                char c = line.charAt(j);
                switch(s) {
                    case ZERO:
                        if(c == '/') {
                            s = State.SLASH;
                        }
                        else if(c == '\'') {
                            id.sep();
                            s = State.SQUOTE;
                        }
                        else if(c == '"') {
                            id.sep();
                            s = State.DQUOTE;
                        }
                        else {
                            id.append(c);
                        }
                        break;

                    case SLASH:
                        if(c == '/') {
                            id.sep();
                            s = State.LINE_COMMENT;
                        }
                        else if(c == '*') {
                            id.sep();
                            s = State.BIG_COMMENT;
                        }
                        else {
                            id.append('/');
                            id.append(c);
                            s = State.ZERO;
                        }
                        break;

                    case LINE_COMMENT:
                        if(c == '\n') {
                            s = State.ZERO;
                        }
                        break;

                    case BIG_COMMENT:
                        if(c == '*') {
                            s = State.BIG_COMMENT_STAR;
                        }
                        break;

                    case BIG_COMMENT_STAR:
                        if(c == '/') {
                            s = State.ZERO;
                        }
                        else if(c == '*') {
                            // nothing, remain in BIG_COMMENT_STAR
                        }
                        else {
                            s = State.BIG_COMMENT;
                        }
                        break;

                    case SQUOTE:
                        if(c == '\'') {
                            s = State.ZERO;
                        }
                        else if(c == '\\') {
                            s = State.SQUOTE_BACKSLASH;
                        }
                        break;

                    case SQUOTE_BACKSLASH:
                        s = State.SQUOTE;
                        break;

                    case DQUOTE:
                        if(c == '"') {
                            s = State.ZERO;
                        }
                        else if(c == '\\') {
                            s = State.DQUOTE_BACKSLASH;
                        }
                        break;

                    case DQUOTE_BACKSLASH:
                        s = State.DQUOTE;
                        break;

                    default:
                        throw new IllegalStateException(String.valueOf(s));
                }
            }
        }
        id.sep();

        ImmutableList.Builder<Pair<Integer, String>> b = ImmutableList.builder();
        for(Map.Entry<String, Integer> e : unusedImports.entries()) {
            b.add(Pair.of(e.getValue(), "Unused import of " + e.getKey()));
        }
        return b.build();
    }
}
