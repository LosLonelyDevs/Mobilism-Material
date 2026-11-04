package org.mobilism.material.settings

class MessageReadHistory(
    load: () -> Set<Long> = { emptySet() },
    private val save: (Set<Long>) -> Unit = {},
) {
    private val ids = load().toMutableSet()

    @Synchronized operator fun contains(id: Long): Boolean = id in ids

    @Synchronized fun remember(readIds: Collection<Long>) {
        if (ids.addAll(readIds)) save(ids.toSet())
    }
}
