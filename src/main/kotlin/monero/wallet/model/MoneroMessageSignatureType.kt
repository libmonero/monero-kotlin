package monero.wallet.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Enumerates the keys that sign a message. The JSON of a result names the key as spend or view.
 */
enum class MoneroMessageSignatureType {
    SIGN_WITH_SPEND_KEY,
    SIGN_WITH_VIEW_KEY;

    @JsonValue
    fun toJson(): String = if (this == SIGN_WITH_SPEND_KEY) "spend" else "view"

    companion object {

        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromJson(value: String): MoneroMessageSignatureType {
            return when (value) {
                "spend" -> SIGN_WITH_SPEND_KEY
                "view" -> SIGN_WITH_VIEW_KEY
                else -> throw IllegalArgumentException("Unknown type of signature: $value")
            }
        }
    }
}
