package org.infinispan.server.extensions;

import java.io.Serializable;

public class CustomEvent<K, V> implements Serializable {
    final K key;
    final V value;
    CustomEvent(K key, V value) {
        this.key = key;
        this.value = value;
    }
}
