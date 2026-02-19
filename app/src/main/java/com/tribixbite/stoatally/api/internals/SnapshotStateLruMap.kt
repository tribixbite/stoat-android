package com.tribixbite.stoatally.api.internals

import androidx.compose.runtime.mutableStateOf

/**
 * A size-bounded [MutableMap] backed by a [LinkedHashMap] in access-order mode (LRU).
 * Mutations trigger Compose snapshot state reads/writes so that Composables
 * observing this map recompose on structural changes.
 *
 * Not thread-safe — callers must ensure single-threaded or synchronized access
 * (same constraint as [androidx.compose.runtime.mutableStateMapOf]).
 */
class SnapshotStateLruMap<K, V>(
    private val maxSize: Int
) : MutableMap<K, V> {

    // Monotonic version counter — read in accessors, written on mutation.
    // Triggers Compose snapshot invalidation on any structural change.
    private val _version = mutableStateOf(0)

    private val backing = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    private fun readVersion() {
        @Suppress("UNUSED_VARIABLE")
        val v = _version.value
    }

    private fun writeVersion() { _version.value++ }

    override val size: Int get() { readVersion(); return backing.size }
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() { readVersion(); return backing.entries }
    override val keys: MutableSet<K> get() { readVersion(); return backing.keys }
    override val values: MutableCollection<V> get() { readVersion(); return backing.values }
    override fun containsKey(key: K): Boolean { readVersion(); return backing.containsKey(key) }
    override fun containsValue(value: V): Boolean { readVersion(); return backing.containsValue(value) }
    override fun get(key: K): V? { readVersion(); return backing[key] }
    override fun isEmpty(): Boolean { readVersion(); return backing.isEmpty() }

    override fun clear() { backing.clear(); writeVersion() }
    override fun put(key: K, value: V): V? { val old = backing.put(key, value); writeVersion(); return old }
    override fun putAll(from: Map<out K, V>) { backing.putAll(from); writeVersion() }
    override fun remove(key: K): V? { val old = backing.remove(key); writeVersion(); return old }
}
