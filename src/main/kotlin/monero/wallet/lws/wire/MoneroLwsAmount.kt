package monero.wallet.lws.wire

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import java.math.BigInteger

// the lws wire format sends uint64 amounts as decimal strings, bc js
// cant count past 2^53. amounts are BigInteger like everywhere else in
// the library, which takes the whole uint64 range
private val UINT64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
private val DECIMAL_STRING = Regex("[0-9]+")

// reads a uint64 that the server wrote as a decimal string. a number,
// or anything else that is not digits, is an error
class MoneroLwsAmountDeserializer : JsonDeserializer<BigInteger>() {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BigInteger {
        val s = p.valueAsString
        if (p.currentToken != JsonToken.VALUE_STRING || !s.matches(DECIMAL_STRING)) {
            throw JsonMappingException.from(p, "\"$s\" is not a uint64 written as a decimal string")
        }
        val value = BigInteger(s)
        if (value > UINT64_MAX) throw JsonMappingException.from(p, "\"$s\" does not fit a uint64")
        return value
    }
}

// writes an amount the way the server does, a uint64 as a decimal string
class MoneroLwsAmountSerializer : JsonSerializer<BigInteger>() {

    override fun serialize(value: BigInteger, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value.signum() < 0) throw JsonMappingException.from(gen, "$value is negative, and a uint64 is not")
        if (value > UINT64_MAX) throw JsonMappingException.from(gen, "$value does not fit a uint64")
        gen.writeString(value.toString())
    }
}
