package dev.ciryva.app

/**
 * An intentionally small, useful offline draft interaction for P02.
 * No model request or conversation archive is implied by a saved draft.
 */
interface DraftStore {
    fun read(): String
    /** Returns true only after the store reports a successful write. */
    fun write(text: String): Boolean
    fun clear(): Boolean
}

class DraftEditor(private val store: DraftStore) {
    companion object {
        const val MAX_LENGTH = 20_000
    }

    fun load(): String = store.read().take(MAX_LENGTH)

    fun save(text: String): Boolean {
        require(text.length <= MAX_LENGTH) { "Draft too large" }
        return store.write(text)
    }

    fun clear(): Boolean = store.clear()
}
