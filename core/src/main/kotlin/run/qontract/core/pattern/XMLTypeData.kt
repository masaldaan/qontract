package run.qontract.core.pattern

data class XMLTypeData(val name: String = "", val attributes: Map<String, Pattern> = emptyMap(), val nodes: List<Pattern> = emptyList()) {
    fun isEmpty(): Boolean {
        return name.isEmpty() && attributes.isEmpty() && nodes.isEmpty()
    }
}