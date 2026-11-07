package monero.wallet.model

/**
 * Models the tag of accounts: a name that groups accounts, and the label that describes the group.
 */
class MoneroAccountTag {
    var tag: String? = null
    var label: String? = null
    var accountIndices: List<Int>? = null

    constructor()

    /**
     * Construct a tag.
     *
     * @param tag is the tag
     * @param label is the label of the tag
     * @param accountIndices are the indices of the accounts that have the tag
     */
    constructor(tag: String?, label: String?, accountIndices: List<Int>? = null) {
        this.tag = tag
        this.label = label
        this.accountIndices = accountIndices
    }

    constructor(other: MoneroAccountTag) {
        this.tag = other.tag
        this.label = other.label
        this.accountIndices = other.accountIndices?.let { ArrayList(it) }
    }

    fun copy(): MoneroAccountTag {
        return MoneroAccountTag(this)
    }
}
