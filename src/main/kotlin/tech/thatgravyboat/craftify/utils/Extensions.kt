package tech.thatgravyboat.craftify.utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import gg.essential.vigilance.Vigilant.CategoryPropertyBuilder
import gg.essential.vigilance.data.PropertyInfo
import gg.essential.vigilance.data.PropertyType
import tech.thatgravyboat.craftify.config.ReadWritePropertyValue
import kotlin.reflect.KMutableProperty0

private val gson = GsonBuilder().setPrettyPrinting().create()

fun CategoryPropertyBuilder.string(field: KMutableProperty0<String>, name: String, description: String) {
    text(field, name, description)
}

fun CategoryPropertyBuilder.boolean(field: KMutableProperty0<Boolean>, name: String, description: String) {
    switch(field, name, description)
}

// Removed custom decimalSlider - use standard Vigilant decimalSlider instead
// Bounds are handled in the property setter

inline fun <reified T : PropertyInfo> CategoryPropertyBuilder.prop(field: KMutableProperty0<*>, name: String, description: String) {
    custom(field, T::class, name, description)
}

inline fun <reified T : Enum<T>> CategoryPropertyBuilder.enum(
    field: KMutableProperty0<T>,
    name: String,
    description: String,
    crossinline onLoad: ((T) -> Unit) = {  }
) {
    // Create an anonymous object with an Int property that syncs with the enum field
    // This allows OneConfig to recognize it as a standard property
    val enumField = field  // Store reference to avoid naming conflict
    val ordinalProperty = object {
        var ordinal: Int = enumField.get().ordinal
            get() {
                field = enumField.get().ordinal
                return field
            }
            set(value) {
                field = value.coerceIn(0, enumValues<T>().size - 1)
                val enum = enumValues<T>()[field]
                enumField.set(enum)
                onLoad(enum)
            }
    }
    
    // Use the property method with KPropertyBackedPropertyValue for OneConfig compatibility
    // We need to access the property through reflection to get a KMutableProperty0
    val options = enumValues<T>().map(Any::toString)
    
    // Use ReadWritePropertyValue but with a wrapper that OneConfig might recognize
    // Actually, let's try using the property method directly with the ordinal property
    val propertyValue = ReadWritePropertyValue(
        { ordinalProperty.ordinal },
        { value -> 
            ordinalProperty.ordinal = (value as? Number)?.toInt() ?: ordinalProperty.ordinal
        }
    )
    property<Int>(propertyValue, PropertyType.SELECTOR, name, description, options = options)
}

fun JsonObject.getString(key: String): String? = this.get(key)?.takeIf { it is JsonPrimitive && it.isString }?.asString
fun JsonObject.getInt(key: String): Int? = this.get(key)?.takeIf { it is JsonPrimitive && it.isNumber }?.asInt
fun JsonObject.getBoolean(key: String): Boolean? = this.get(key)?.takeIf { it is JsonPrimitive && it.isBoolean }?.asBoolean

fun String.readJson(): JsonObject = gson.fromJson<JsonObject>(this, JsonObject::class.java)