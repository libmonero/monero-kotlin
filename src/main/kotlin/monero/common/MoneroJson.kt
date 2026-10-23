package monero.common

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper

/**
 * JSON of the Monero models, which is the one that monero-cpp and monero-c write: the names of the properties, a field
 * that is not set is left out, and the enumerations are numbers.
 *
 * The models are read and written through their fields.
 */
object MoneroJson {

    val mapper: ObjectMapper = JsonMapper.builder()
        .visibility(PropertyAccessor.ALL, Visibility.NONE)
        .visibility(PropertyAccessor.FIELD, Visibility.ANY)
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.WRITE_ENUMS_USING_INDEX, true)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .build()

    /**
     * Serialize a model to JSON.
     *
     * @param model is the model to serialize
     * @return the JSON
     */
    fun serialize(model: Any?): String {
        try {
            return mapper.writeValueAsString(model)
        } catch (e: JsonProcessingException) {
            throw MoneroError("Cannot serialize ${model?.javaClass?.simpleName}: ${e.originalMessage}", e)
        }
    }

    /**
     * Deserialize a model from JSON.
     *
     * @param json is the JSON to deserialize
     * @param type is the type of the model
     * @return the model
     * @throws MoneroError if the JSON is not valid, or it is not a model of that type
     */
    fun <T> deserialize(json: String, type: Class<T>): T {
        return read(json, type.simpleName) { mapper.readValue(json, type) }
    }

    /**
     * Deserialize a model from JSON.
     *
     * @param json is the JSON to deserialize
     * @param type is the type of the model, e.g. a list
     * @return the model
     * @throws MoneroError if the JSON is not valid, or it is not a model of that type
     */
    fun <T> deserialize(json: String, type: TypeReference<T>): T {
        return read(json, type.type.typeName) { mapper.readValue(json, type) }
    }

    inline fun <reified T> deserialize(json: String): T = deserialize(json, object : TypeReference<T>() {})

    private fun <T> read(json: String, name: String, parse: () -> T?): T {
        try {
            return parse() ?: throw MoneroError("The JSON is null, and a $name is needed")
        } catch (e: JsonProcessingException) {
            throw MoneroError("Cannot deserialize a $name: ${e.originalMessage}", e)
        }
    }
}
