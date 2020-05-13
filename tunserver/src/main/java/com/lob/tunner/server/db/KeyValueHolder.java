package com.lob.tunner.server.db;

import java.util.Map;
import java.util.Objects;

public class KeyValueHolder<K, V> implements Map.Entry<K, V> {
    final K key;

    final V value;

    KeyValueHolder(K k, V v) {
        this.key = Objects.requireNonNull(k);
        this.value = Objects.requireNonNull(v);
    }

    public K getKey() {
        return this.key;
    }

    public V getValue() {
        return this.value;
    }

    public V setValue(V value) {
        throw new UnsupportedOperationException("not supported");
    }

    public boolean equals(Object o) {
        if (!(o instanceof Map.Entry)) {
            return false;
        } else {
            Map.Entry<?, ?> e = (Map.Entry)o;
            return this.key.equals(e.getKey()) && this.value.equals(e.getValue());
        }
    }

    public int hashCode() {
        return this.key.hashCode() ^ this.value.hashCode();
    }

    public String toString() {
        return this.key + "=" + this.value;
    }
}
