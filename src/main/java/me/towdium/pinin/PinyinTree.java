package me.towdium.pinin;

import it.unimi.dsi.fastutil.chars.*;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.towdium.pinin.elements.Phoneme;
import me.towdium.pinin.elements.Pinyin;
import me.towdium.pinin.utils.IndexSet;
import me.towdium.pinin.utils.Matcher;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static me.towdium.pinin.utils.Matcher.check;
import static me.towdium.pinin.utils.Matcher.strCmp;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public class PinyinTree<T> {
    Node root = new NDense();
    PinIn context;
    CharList chars = new CharArrayList();
    IntList index = new IntArrayList();
    List<T> objects = new ObjectArrayList<>();
    final boolean suffix;

    private int charsEnd(int start) {
        for (int i = start; ; i++) {
            if (chars.getChar(i) == '\0') return i;
        }
    }

    private String charsStr(int start) {
        return charsStr(start, charsEnd(start));
    }

    private String charsStr(int start, int end) {
        return new String(chars.subList(start, end).toCharArray());
    }

    public PinyinTree(boolean suffix, PinIn context) {
        this.suffix = suffix;
        this.context = context;
    }

    public void put(String name, T identifier) {
        int pos = chars.size();
        index.add(pos);
        for (char c : name.toCharArray()) chars.add(c);
        chars.add('\0');
        for (int i = 0; i < (suffix ? name.length() : 1); i++)
            root = root.put(this, pos + i, objects.size());
        objects.add(identifier);
    }

    public Set<T> search(String s) {
        IntSet ret = new IntOpenHashSet();
        root.get(this, ret, s, 0);
        return ret.stream().map(i -> objects.get(i))
                .collect(Collectors.toSet());
    }

    public PinIn context() {
        return context;
    }

    interface Node {
        void get(PinyinTree p, IntSet ret, String name, int offset);

        void get(PinyinTree p, IntSet ret);

        Node put(PinyinTree p, int name, int identifier);
    }

    public static class NSlice implements Node {
        Node exit = new NMap();
        int start, end;

        public NSlice(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public void get(PinyinTree p, IntSet ret, String name, int offset) {
            get(p, ret, name, offset, 0);
        }

        @Override
        public void get(PinyinTree p, IntSet ret) {
            exit.get(p, ret);
        }

        @Override
        public Node put(PinyinTree p, int name, int identifier) {
            int length = end - start;
            int match = Matcher.strCmp(p.charsStr(start, end), p.charsStr(name), 0, 0, length);
            if (match >= length) exit = exit.put(p, name + length, identifier);
            else {
                cut(p, start + match);
                exit = exit.put(p, name + match, identifier);
            }
            return start == end ? exit : this;
        }

        private void cut(PinyinTree p, int offset) {
            NMap insert = new NMap();
            if (offset + 1 == end) insert.put(p, p.chars.getChar(offset), exit);
            else {
                NSlice half = new NSlice(offset + 1, end);
                half.exit = exit;
                insert.put(p, p.chars.getChar(offset), half);
            }
            exit = insert;
            end = offset;
        }

        private void get(PinyinTree p, IntSet ret, String name, int offset, int start) {
            if (this.start + start == end)
                exit.get(p, ret, name, offset);
            else if (offset == name.length()) exit.get(p, ret);
            else {
                char ch = p.chars.getChar(this.start + start);
                p.context.genChar(ch).match(name, offset).foreach(i ->
                        get(p, ret, name, offset + i, start + 1));
            }
        }
    }

    public static class NDense implements Node {
        // offset, object, offset, object
        IntList data = new IntArrayList();

        @Override
        public void get(PinyinTree p, IntSet ret, String name, int offset) {
            if (name.length() == offset) get(p, ret);
            else {
                for (int i = 0; i < data.size() / 2; i++) {
                    int ch = data.getInt(i * 2);
                    int obj = data.getInt(i * 2 + 1);
                    if (p.chars.getChar(ch) == '\0') return;
                    if (check(name, offset, p.charsStr(ch), 0, p.context)) ret.add(obj);
                }
            }
        }

        @Override
        public void get(PinyinTree p, IntSet ret) {
            for (int i = 0; i < data.size() / 2; i++)
                ret.add(data.getInt(i * 2 + 1));
        }

        @Override
        public Node put(PinyinTree p, int name, int identifier) {
            if (data.size() >= 512) {
                int pattern = data.getInt(0);
                int common = Integer.MAX_VALUE;
                for (int i = 0; i < data.size() / 2; i++) {
                    int offset = data.getInt(i * 2);
                    common = Math.min(common, strCmp(p.chars, pattern, offset));
                    if (common == 0) break;
                }
                Node ret = new NSlice(pattern, pattern + common);
                for (int j = 0; j < data.size() / 2; j++)
                    ret.put(p, data.getInt(j * 2), data.getInt(j * 2 + 1));
                ret.put(p, name, identifier);
                return ret;
            } else {
                data.add(name);
                data.add(identifier);
                return this;
            }
        }
    }

    public static class NMap implements Node {
        Char2ObjectMap<Node> children;
        Glue glue;
        IntSet leaves = new IntArraySet(1);

        @Override
        public void get(PinyinTree p, IntSet ret, String name, int offset) {
            if (name.length() == offset) get(p, ret);
            else if (children != null && glue != null) {
                Node n = children.get(name.charAt(offset));
                if (n != null) n.get(p, ret, name, offset + 1);
                glue.get(name, offset).forEach((c, is) -> is.foreach(i ->
                        children.get(c.charValue()).get(p, ret, name, offset + i)));
            }
        }

        @Override
        public void get(PinyinTree p, IntSet ret) {
            ret.addAll(leaves);
            if (children != null) children.forEach((c, n) -> n.get(p, ret));
        }

        @Override
        public NMap put(PinyinTree p, int name, int identifier) {
            if (p.chars.getChar(name) == '\0') {
                if (leaves.size() >= 32 && leaves instanceof IntArraySet)
                    leaves = new IntOpenHashSet(leaves);
                leaves.add(identifier);
            } else {
                init();
                char ch = p.chars.getChar(name);
                Node sub = children.get(ch);
                if (sub == null) put(p, ch, sub = new NDense());
                sub = sub.put(p, name + 1, identifier);
                children.put(ch, sub);
            }
            return this;
        }

        private void put(PinyinTree p, char ch, Node n) {
            init();
            if (children.size() >= 32 && children instanceof Char2ObjectArrayMap)
                children = new Char2ObjectOpenHashMap<>(children);
            children.put(ch, n);
            glue.put(ch, p);
        }

        private void init() {
            if (children == null || glue == null) {
                children = new Char2ObjectArrayMap<>();
                glue = new Glue();
            }
        }
    }

    static class Glue {
        Map<Pinyin, CharSet> map = new Object2ObjectArrayMap<>();
        Map<Phoneme, Set<Pinyin>> index;

        public Char2ObjectMap<IndexSet> get(String name, int offset) {
            Char2ObjectMap<IndexSet> ret = new Char2ObjectArrayMap<>();
            BiConsumer<Pinyin, CharSet> add = (p, cs) -> p.match(name, offset).foreach(i -> {
                for (char c : cs) ret.computeIfAbsent(c, k -> new IndexSet()).set(i);
            });

            if (index != null) {
                index.forEach((ph, ps) -> {
                    if (!ph.match(name, offset).isEmpty())
                        ps.forEach(p -> add.accept(p, map.get(p)));
                });
            } else map.forEach(add);
            return ret;
        }

        public void put(char ch, PinyinTree pi) {
            if (!Matcher.isChinese(ch)) return;
            for (Pinyin p : Pinyin.get(ch, pi.context)) {
                map.compute(p, (py, cs) -> {
                    if (cs == null) {
                        cs = new CharArraySet(1);
                        if (index != null) index.computeIfAbsent(py.phonemes()[0],
                                c -> new ObjectOpenHashSet<>()).add(py);
                    } else if (cs.size() >= 32 && cs instanceof CharArraySet)
                        cs = new CharOpenHashSet(cs);
                    cs.add(ch);
                    return cs;
                });
            }
            if (map.size() >= 32 && index == null) {
                map = new Object2ObjectOpenHashMap<>(map);
                index();
            }
        }

        public void index() {
            index = new Object2ObjectArrayMap<>();
            map.forEach((p, cs) -> index.computeIfAbsent(p.phonemes()[0],
                    c -> new ObjectOpenHashSet<>()).add(p));
        }
    }
}
