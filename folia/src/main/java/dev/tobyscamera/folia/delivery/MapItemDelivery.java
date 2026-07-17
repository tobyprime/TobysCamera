package dev.tobyscamera.folia.delivery;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Inserts printed maps and drops every inventory remainder at the recipient's location. */
public final class MapItemDelivery {
    private MapItemDelivery() { }

    public static <T> void deliver(Iterable<T> items, Function<T, ? extends Map<?, ? extends T>> insert, Consumer<T> drop) {
        for (T item : items) {
            Map<?, ? extends T> leftovers = insert.apply(item);
            for (T leftover : leftovers.values()) {
                drop.accept(leftover);
            }
        }
    }
}
