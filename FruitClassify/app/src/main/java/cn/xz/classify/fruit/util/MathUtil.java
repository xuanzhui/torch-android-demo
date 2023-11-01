package cn.xz.classify.fruit.util;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import cn.xz.classify.fruit.IndexValPair;

public class MathUtil {
    public static float[] softmax(float[] values) {
        double total = 0;
        for (float v : values) {
            total += Math.exp(v);
        }

        float[] res = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            res[i] = (float) (Math.exp(values[i]) / total);
        }

        return res;
    }

    public static List<IndexValPair> rankDesc(float[] values) {
        List<IndexValPair> res = new LinkedList<>();
        for (int i=0; i < values.length; i++) {
            res.add(new IndexValPair(i, values[i]));
        }

        res.sort(new Comparator<IndexValPair>() {
            @Override
            public int compare(IndexValPair t0, IndexValPair t1) {
                return Float.compare(t1.value, t0.value);
            }
        });

        return res;
    }
}
