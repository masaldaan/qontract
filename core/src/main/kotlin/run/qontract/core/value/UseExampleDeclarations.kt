package run.qontract.core.value

import run.qontract.core.ExampleDeclarations
import run.qontract.core.pattern.isPatternToken

data class UseExampleDeclarations(override val examples: Map<String, String> = emptyMap(), override val messages: List<String> = emptyList()) : ExampleDeclarations {
    override fun plus(more: ExampleDeclarations): ExampleDeclarations {
        val duplicateMessage = messageWhenDuplicateKeysExist(more, examples)
        for(message in duplicateMessage)
            println(duplicateMessage)

        return this.copy(examples = examples.plus(more.examples.filterNot { isPatternToken(it.value) }), messages = messages.plus(more.messages).plus(duplicateMessage))
    }

    override fun plus(more: Pair<String, String>): ExampleDeclarations = when {
        !isPatternToken(more.second) || more.second == "(null)"-> this.copy(examples = examples.plus(more))
        else -> this
    }

    override fun getNewName(typeName: String, keys: Collection<String>): String =
            generateSequence(typeName) { "${it}_" }.first { it !in keys }
}

internal fun messageWhenDuplicateKeysExist(newExampleDeclarations: ExampleDeclarations, examples: Map<String, String>): List<String> {
    val duplicateKeys = newExampleDeclarations.examples.keys.filter { it in examples }.filter { key ->
        val oldValue = examples.getValue(key)
        val newValue = newExampleDeclarations.examples.getValue(key)

        oldValue != newValue
    }

    return when {
        duplicateKeys.isNotEmpty() -> {
            val keysCsv = duplicateKeys.joinToString(", ")
            listOf("Duplicate keys with different values found: $keysCsv")
        }
        else -> emptyList()
    }
}
