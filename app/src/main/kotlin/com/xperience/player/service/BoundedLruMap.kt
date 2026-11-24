package com.xperience.player.service

/**
 * Thread-safe bounded LRU map using an access-ordered [LinkedHashMap].
 * Prevents unbounded heap growth in process-wide singleton services while keeping
 * `ConcurrentHashMap`-like ergonomics (`get`, `put`, `set`, `remove`, `clear`, `snapshot`).
 */
internal class BoundedLruMap<K, V>(
    private val maxEntries: Int
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive, got $maxEntries" }
    }

    private val lock = Any()
    private val map = object : LinkedHashMap<K, V>(minOf(maxEntries, 64), 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntries
        }
    }

    operator fun get(key: K): V? = synchronized(lock) { map[key] }

    operator fun set(key: K, value: V) {
        synchronized(lock) {
            map[key] = value
        }
    }

    fun put(key: K, value: V): V? = synchronized(lock) { map.put(key, value) }

    fun putIfAbsent(key: K, value: V): V? = synchronized(lock) {
        map[key] ?: map.put(key, value)
    }

    fun putAll(from: Map<out K, V>) {
        synchronized(lock) {
            map.putAll(from)
        }
    }

    fun remove(key: K): V? = synchronized(lock) { map.remove(key) }

    fun clear() {
        synchronized(lock) {
            map.clear()
        }
    }

    fun containsKey(key: K): Boolean = synchronized(lock) { map.containsKey(key) }

    val size: Int
        get() = synchronized(lock) { map.size }

    fun isEmpty(): Boolean = synchronized(lock) { map.isEmpty() }

    fun snapshot(): Map<K, V> = synchronized(lock) { LinkedHashMap(map) }

    fun entriesSnapshot(): List<Pair<K, V>> = synchronized(lock) {
        map.entries.map { it.key to it.value }
    }
}
