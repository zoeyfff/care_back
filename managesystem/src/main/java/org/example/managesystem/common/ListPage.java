package org.example.managesystem.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ListPage {

    private ListPage() {
    }

    public static <T> Map<String, Object> of(List<T> list, long total) {
        Map<String, Object> map = new HashMap<>(4);
        map.put("list", list);
        map.put("total", total);
        return map;
    }
}
