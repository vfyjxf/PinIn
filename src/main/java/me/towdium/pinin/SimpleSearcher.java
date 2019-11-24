package me.towdium.pinin;

import it.unimi.dsi.fastutil.ints.IntList;
import me.towdium.pinin.utils.Accelerator;

import java.util.ArrayList;
import java.util.List;

public class SimpleSearcher<T> implements Searcher<T> {
    List<T> objs = new ArrayList<>();
    final Accelerator acc;
    final PinIn context;
    final boolean suffix;

    public SimpleSearcher(boolean suffix, PinIn context) {
        this.context = context;
        this.suffix = suffix;
        acc = new Accelerator(context);
    }

    @Override
    public void put(String name, T identifier) {
        acc.put(name);
        objs.add(identifier);
    }

    @Override
    public List<T> search(String name) {
        List<T> ret = new ArrayList<>();
        acc.search(name);
        IntList strs = acc.strs();
        for (int i = 0; i < strs.size(); i++) {
            int s = strs.getInt(i);
            if (acc.contains(s, suffix)) ret.add(objs.get(i));
        }
        return ret;
    }

    @Override
    public PinIn context() {
        return context;
    }
}
