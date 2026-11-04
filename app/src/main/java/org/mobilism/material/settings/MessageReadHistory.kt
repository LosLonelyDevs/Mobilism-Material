package org.mobilism.material.settings

class MessageReadHistory(
    load: () -> Set<Long> = { emptySet() },
    private val save: (Set<Long>) -> Unit = {},
) {
    private val ids = load().toMutableSet()

    operator fun contains(id: Long): Boolean = id in ids

    fun remember(readIds: Collection<Long>) {
        if (ids.addAll(readIds)) save(ids.toSet())
    }
}
