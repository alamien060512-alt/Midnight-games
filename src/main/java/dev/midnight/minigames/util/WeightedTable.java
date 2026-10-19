package dev.midnight.minigames.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Minimal weighted random picker. */
public final class WeightedTable<T> {

    private final List<T> values = new ArrayList<>();
    private final List<Double> cumulative = new ArrayList<>();
    private double total;

    public WeightedTable<T> add(T value, double weight) {
        if (weight <= 0) {
            return this;
        }
        total += weight;
        values.add(value);
        cumulative.add(total);
        return this;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public T pick(Random random) {
        if (values.isEmpty()) {
            return null;
        }
        double r = random.nextDouble() * total;
        for (int i = 0; i < values.size(); i++) {
            if (r < cumulative.get(i)) {
                return values.get(i);
            }
        }
        return values.get(values.size() - 1);
    }
}
