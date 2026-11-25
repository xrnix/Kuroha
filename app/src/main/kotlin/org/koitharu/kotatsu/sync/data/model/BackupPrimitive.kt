package org.koitharu.kotatsu.sync.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class BackupPrimitive {

	@Serializable
	@SerialName("string")
	data class StringValue(@SerialName("v") val value: String) : BackupPrimitive()

	@Serializable
	@SerialName("bool")
	data class BoolValue(@SerialName("v") val value: Boolean) : BackupPrimitive()

	@Serializable
	@SerialName("int")
	data class IntValue(@SerialName("v") val value: Int) : BackupPrimitive()

	@Serializable
	@SerialName("long")
	data class LongValue(@SerialName("v") val value: Long) : BackupPrimitive()

	@Serializable
	@SerialName("float")
	data class FloatValue(@SerialName("v") val value: Float) : BackupPrimitive()

	@Serializable
	@SerialName("string_set")
	data class StringSetValue(@SerialName("v") val value: Set<String>) : BackupPrimitive()

	companion object {

		fun of(value: Any?): BackupPrimitive? = when (value) {
			is String -> StringValue(value)
			is Boolean -> BoolValue(value)
			is Int -> IntValue(value)
			is Long -> LongValue(value)
			is Float -> FloatValue(value)
			is Set<*> -> StringSetValue(value.filterIsInstance<String>().toSet())
			else -> null
		}
	}

	fun rawValue(): Any = when (this) {
		is StringValue -> value
		is BoolValue -> value
		is IntValue -> value
		is LongValue -> value
		is FloatValue -> value
		is StringSetValue -> value
	}
}
