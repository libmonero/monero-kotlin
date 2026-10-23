package monero

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.math.BigInteger
import monero.common.MoneroJson
import monero.wallet.model.MoneroBlockWallet
import monero.wallet.model.MoneroOutputWallet
import monero.wallet.model.MoneroTxWallet
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * What the tests of the models share: the comparison of JSON, the fields of a model that are in its JSON, and the
 * values that fill a model.
 */
object ModelTestSupport {

    private val mapper = MoneroJson.mapper

    fun tree(json: String): JsonNode = mapper.readTree(json)

    fun listOf(type: Class<*>): JavaType = mapper.typeFactory.constructCollectionType(List::class.java, type)

    fun read(json: String, type: JavaType): Any = mapper.readValue(json, type) ?: fail("$type was read as null")

    fun write(model: Any): String = MoneroJson.serialize(model)

    /**
     * Compare two JSON documents by value: the order of the properties does not matter, the order of an array does.
     *
     * @return where the two differ, or null if they are the same
     */
    fun diff(expected: JsonNode?, actual: JsonNode?, path: String = "$"): String? {
        if (expected != null && expected.isObject) {
            if (actual == null || !actual.isObject) return "$path: expected an object, but it is ${show(actual)}"
            for ((key, value) in expected.properties()) {
                if (!actual.has(key)) return "$path.$key: missing, expected ${show(value)}"
                diff(value, actual.get(key), "$path.$key")?.let { return it }
            }
            for ((key, value) in actual.properties()) {
                if (!expected.has(key)) return "$path.$key: not expected, but it is ${show(value)}"
            }
            return null
        }
        if (expected != null && expected.isArray) {
            if (actual == null || !actual.isArray) return "$path: expected an array, but it is ${show(actual)}"
            if (expected.size() != actual.size()) return "$path: expected ${expected.size()} items, but there are ${actual.size()}"
            for (i in 0 until expected.size()) {
                diff(expected[i], actual[i], "$path[$i]")?.let { return it }
            }
            return null
        }
        return if (expected == actual) null else "$path: expected ${show(expected)}, but it is ${show(actual)}"
    }

    private fun show(node: JsonNode?): String {
        val text = node?.toString() ?: "null"
        return if (text.length <= 80) text else text.substring(0, 77) + "..."
    }

    fun assertSameJson(expected: String, actual: String) {
        assertNull(diff(tree(expected), tree(actual)))
    }

    // fixtures

    fun fixtureFile(folder: String, name: String): File {
        val url = ModelTestSupport::class.java.getResource("/fixtures/$folder/$name.json") ?: fail("No fixture $folder/$name")
        return File(url.toURI())
    }

    fun fixture(folder: String, name: String): String = fixtureFile(folder, name).readText(Charsets.UTF_8)

    fun fixtureNames(folder: String): List<String> {
        val url = ModelTestSupport::class.java.getResource("/fixtures/$folder") ?: fail("No fixtures in $folder")
        return File(url.toURI()).list()!!.filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }.sorted()
    }

    // the classes of the model

    /**
     * The classes of the model that are read from and written to JSON: the public ones that can be built without
     * arguments, in the given package.
     */
    fun modelTypes(vararg packages: String): List<Class<*>> {
        val root = File(MoneroJson::class.java.protectionDomain.codeSource.location.toURI())
        val types = ArrayList<Class<*>>()
        for (pkg in packages) {
            val dir = File(root, pkg.replace('.', '/'))
            for (file in dir.listFiles { f -> f.name.endsWith(".class") && '$' !in f.name }!!.sortedBy { it.name }) {
                val type = Class.forName(pkg + "." + file.name.removeSuffix(".class"))
                if (Modifier.isPublic(type.modifiers) && !Modifier.isAbstract(type.modifiers) && !type.isEnum && !type.name.endsWith("Kt")
                    && !type.name.endsWith("Listener") && type.constructors.any { it.parameterCount == 0 }) {
                    types.add(type)
                }
            }
        }
        return types
    }

    /**
     * The fields of a model that are in its JSON.
     */
    fun jsonFields(type: Class<*>): List<Field> {
        val fields = ArrayList<Field>()
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic || field.isAnnotationPresent(JsonIgnore::class.java)) continue
                field.isAccessible = true
                fields.add(field)
            }
            current = current.superclass
        }
        return fields
    }

    fun jsonName(field: Field): String = field.getAnnotation(JsonProperty::class.java)?.value ?: field.name

    fun newInstance(type: Class<*>): Any = type.getDeclaredConstructor().newInstance()

    /**
     * Fills a model with a value that is different for every field, so a field that is copied or mapped from another one
     * shows.
     */
    class Sampler {

        // the queries have queries of their own, of the same classes, so the depth of the models is bounded
        private val maxDepth = 6
        private var n = 0
        private var depth = 0

        fun populate(type: Class<*>): Any = populate(newInstance(type))

        fun populate(model: Any): Any {
            depth++
            try {
                for (field in jsonFields(model.javaClass)) {
                    val elementType = elementTypeOf(model.javaClass, field)
                    field.set(model, if (elementType != null) makeList(elementType) else make(field.genericType, field.name))
                }
            } finally {
                depth--
            }
            return model
        }

        // the wallet models hold subclasses of the classes that their fields declare
        private fun elementTypeOf(owner: Class<*>, field: Field): Class<*>? = when {
            MoneroTxWallet::class.java.isAssignableFrom(owner) && (field.name == "inputs" || field.name == "outputs") -> MoneroOutputWallet::class.java
            MoneroBlockWallet::class.java.isAssignableFrom(owner) && field.name == "txs" -> MoneroTxWallet::class.java
            else -> null
        }

        private fun makeList(elementType: Class<*>): Any {
            return arrayListOf(populate(newInstance(elementType)), populate(newInstance(elementType)))
        }

        private fun make(type: Type, name: String): Any? {
            if (type is WildcardType) return make(type.upperBounds[0], name)
            if (type is ParameterizedType) {
                val raw = type.rawType as Class<*>
                val arguments = type.actualTypeArguments
                if (List::class.java.isAssignableFrom(raw)) {
                    val element = arguments[0]
                    if (element is Class<*> && element != String::class.java && !element.isEnum && depth >= maxDepth && !isNumber(element)) return null
                    return arrayListOf(make(element, name + "Item"), make(element, name + "Item"))
                }
                if (Map::class.java.isAssignableFrom(raw)) return linkedMapOf(make(arguments[0], name + "Key") to make(arguments[1], name + "Value"))
                fail("No sample for $type of $name")
            }
            val c = type as Class<*>
            return when {
                c == String::class.java -> "$name-${++n}"
                c == java.lang.Boolean::class.java -> (++n and 1) == 0
                c == java.lang.Integer::class.java -> Int.MAX_VALUE - ++n
                c == java.lang.Long::class.java -> Long.MAX_VALUE - ++n
                c == BigInteger::class.java -> BigInteger.ONE.shiftLeft(64).subtract(BigInteger.valueOf(++n + 1L))
                c.isEnum -> c.enumConstants[++n % c.enumConstants.size]
                else -> if (depth >= maxDepth) null else populate(newInstance(c))
            }
        }

        private fun isNumber(type: Class<*>) = Number::class.java.isAssignableFrom(type)
    }
}
