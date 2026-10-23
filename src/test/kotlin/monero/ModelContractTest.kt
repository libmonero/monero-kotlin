package monero

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.ModelTestSupport.Sampler
import monero.ModelTestSupport.jsonFields
import monero.ModelTestSupport.jsonName
import monero.ModelTestSupport.modelTypes
import monero.ModelTestSupport.newInstance
import monero.common.MoneroJson
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * What every class of the data model has to do, whatever its fields are: the classes are found in the compiled
 * packages, so a new class is tested without being listed.
 */
class ModelContractTest {

    private val types = modelTypes("monero.common", "monero.daemon.model", "monero.wallet.model")

    private fun forEachType(test: (Class<*>) -> Unit): List<DynamicTest> =
        types.map { type -> dynamicTest(type.simpleName) { test(type) } }

    @Test
    fun modelTypes_AreFound() {
        for (name in listOf("MoneroTx", "MoneroBlock", "MoneroDaemonInfo", "MoneroTxWallet", "MoneroTxQuery", "MoneroWalletConfig", "MoneroRpcConnection")) {
            assertTrue(types.any { it.simpleName == name }, "$name is not found")
        }
    }

    @TestFactory
    fun model_FieldsAreNamedInCamelCase() = forEachType { type ->
        for (field in jsonFields(type)) {
            val name = jsonName(field)
            assertTrue(name.matches(Regex("[a-z][A-Za-z0-9]*")), "${type.simpleName}.$name is not in camel case")
        }
    }

    @TestFactory
    fun model_WhenNothingIsSet_IsAnEmptyObject() = forEachType { type ->
        assertEquals("{}", MoneroJson.serialize(newInstance(type)))
    }

    @TestFactory
    fun model_ReadFromAnEmptyObject_HasNothingSet() = forEachType { type ->
        val model = MoneroJson.deserialize("{}", type)!!
        for (field in jsonFields(type)) assertNull(field.get(model), "${type.simpleName}.${field.name}")
    }

    @TestFactory
    fun model_ReadFromNullFields_HasNothingSet() = forEachType { type ->
        val json = JsonNodeFactory.instance.objectNode()
        for (field in jsonFields(type)) json.putNull(jsonName(field))
        val model = MoneroJson.deserialize(json.toString(), type)!!
        for (field in jsonFields(type)) assertNull(field.get(model), "${type.simpleName}.${field.name}")
    }

    @TestFactory
    fun model_ReadFromAnObjectWithUnknownFields_IgnoresThem() = forEachType { type ->
        val model = MoneroJson.deserialize("""{"unknown":1,"alsoUnknown":{"a":[1,2]}}""", type)!!
        assertEquals("{}", MoneroJson.serialize(model))
    }

    @TestFactory
    fun model_WithEveryFieldSet_IsReadAndWritten() = forEachType { type ->
        val model = Sampler().populate(type)
        val json = MoneroJson.serialize(model)

        val written = ModelTestSupport.tree(json) as ObjectNode
        for (field in jsonFields(type)) {
            if (field.get(model) != null) assertTrue(written.has(jsonName(field)), "${type.simpleName}.${jsonName(field)} is not written")
        }

        val read = MoneroJson.deserialize(json, type)!!
        assertEquals(type, read.javaClass)
        assertNull(ModelTestSupport.diff(written, ModelTestSupport.tree(MoneroJson.serialize(read))))
    }

    @TestFactory
    fun copy_HasEveryFieldOfTheOriginal() = types.filter { hasCopy(it) }.map { type ->
        dynamicTest(type.simpleName) {
            val model = Sampler().populate(type)
            val copy = type.getMethod("copy").invoke(model)
            assertNotNull(copy)
            assertEquals(type, copy.javaClass)
            assertNotSame(model, copy)
            assertNull(ModelTestSupport.diff(ModelTestSupport.tree(MoneroJson.serialize(model)), ModelTestSupport.tree(MoneroJson.serialize(copy))))
        }
    }

    @TestFactory
    fun copy_OfAModelWithNothingSet_HasNothingSet() = types.filter { hasCopy(it) }.map { type ->
        dynamicTest(type.simpleName) {
            assertEquals("{}", MoneroJson.serialize(type.getMethod("copy").invoke(newInstance(type))))
        }
    }

    private fun hasCopy(type: Class<*>) = type.methods.any { it.name == "copy" && it.parameterCount == 0 }
}
