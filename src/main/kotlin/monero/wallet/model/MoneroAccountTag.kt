package monero.wallet.model

/**
 * Models the tag of accounts: a name that groups accounts, and the label that describes the group.
 */
class MoneroAccountTag {
    var tag: String? = null
    var label: String? = null
    var accountIndices: List<Int>? = null

    constructor()

    constructor(other: MoneroAccountTag) {
        this.tag = other.tag
        this.label = other.label
        this.accountIndices = other.accountIndices?.let { ArrayList(it) }
    }

    fun copy(): MoneroAccountTag {
        return MoneroAccountTag(this)
    }
}
