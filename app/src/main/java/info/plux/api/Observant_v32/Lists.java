package info.plux.api.Observant_v32;

import java.util.List;

public final class Lists {

    private Lists() {
    }

    // Get first element in list
    public static <T> T getFirst(List<T> list) {
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    // Get last element in list
    public static <T> T getLast(List<T> list) {
        return list != null && !list.isEmpty() ? list.get(list.size() - 1) : null;
    }
}