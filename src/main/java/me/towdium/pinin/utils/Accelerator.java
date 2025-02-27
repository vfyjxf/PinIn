package me.towdium.pinin.utils;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.towdium.pinin.PinIn;
import me.towdium.pinin.elements.Char;
import me.towdium.pinin.elements.Pinyin;

import java.util.List;

public class Accelerator {
    final PinIn context;
    List<IndexSet.Storage> cache;
    char[] searchChars;
    String searchStr;
    Provider provider;
    Str str = new Str();
    boolean partial;

    public Accelerator(PinIn context) {
        this.context = context;
    }

    public void search(String s) {
        if (!s.equals(searchStr)) {
            // here we store both search token as string and char array
            // it seems stupid, but saves over 10% of accelerator overhead
            searchStr = s;
            searchChars = s.toCharArray();
            reset();
        }
    }

    public IndexSet get(char ch, int offset) {
        Char c = context.getChar(ch);
        IndexSet ret = (searchChars[offset] == c.get() ? IndexSet.ONE : IndexSet.NONE).copy();
        for (Pinyin p : c.pinyins()) ret.merge(get(p, offset));
        return ret;
    }

    public IndexSet get(Pinyin p, int offset) {
        for (int i = cache.size(); i <= offset; i++)
            cache.add(new IndexSet.Storage());
        IndexSet.Storage data = cache.get(offset);
        IndexSet ret = data.get(p.id);
        if (ret == null) {
            ret = p.match(searchStr, offset, partial);
            data.set(ret, p.id);
        }
        return ret;
    }

    public void setProvider(Provider p) {
        provider = p;
    }

    public void setProvider(String s) {
        str.s = s;
        provider = str;
    }

    public void reset() {
        cache = new ObjectArrayList<>();
    }

    // offset - offset in search string
    // start - start point in raw text
    public boolean check(int offset, int start) {
        if (offset == searchStr.length()) return partial || provider.end(start);
        if (provider.end(start)) return false;

        IndexSet s = get(provider.get(start), offset);

        if (provider.end(start + 1)) {
            int i = searchStr.length() - offset;
            return s.get(i);
        } else return s.traverse(i -> check(offset + i, start + 1));
    }

    public boolean matches(int offset, int start) {
        if (partial) {
            partial = false;
            reset();
        }
        return check(offset, start);
    }

    public boolean begins(int offset, int start) {
        if (!partial) {
            partial = true;
            reset();
        }
        return check(offset, start);
    }

    public boolean contains(int offset, int start) {
        if (!partial) {
            partial = true;
            reset();
        }
        for (int i = start; !provider.end(i); i++) {
            if (check(offset, i)) return true;
        }
        return false;
    }

    public String search() {
        return searchStr;
    }

    public int common(int s1, int s2, int max) {
        for (int i = 0; ; i++) {
            if (i >= max) return max;
            char a = provider.get(s1 + i);
            char b = provider.get(s2 + i);
            if (a != b || a == '\0') return i;
        }
    }

    public interface Provider {
        boolean end(int i);

        char get(int i);
    }

    static class Str implements Provider {
        String s;

        @Override
        public boolean end(int i) {
            return i >= s.length();
        }

        @Override
        public char get(int i) {
            return s.charAt(i);
        }
    }
}