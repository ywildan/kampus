package id.personal.campus.agent

/**
 * Penyusun badan `POST {base}/chat/completions`.
 *
 * Berkas ini **bebas `android.*`** supaya isi permintaan (termasuk `tools` + `tool_choice: auto`)
 * bisa diperiksa lewat unit test, bukan hanya lewat percobaan manual di perangkat.
 */
object ChatRequestBuilder {

    fun body(
        model: String,
        messages: List<AgentMessage>,
        tools: List<AgentToolSpec> = emptyList(),
        temperature: Double = AGENT_DEFAULT_TEMPERATURE,
        maxTokens: Int = AGENT_DEFAULT_MAX_TOKENS,
    ): String {
        val fields = linkedMapOf<String, AgentJsonValue>(
            "model" to strOf(model),
            "messages" to AgentJsonValue.Arr(messages.map(::messageNode)),
            "temperature" to numOf(temperature),
            "max_tokens" to numOf(maxTokens),
            "stream" to boolOf(true),
        )
        if (tools.isNotEmpty()) {
            fields["tools"] = AgentJsonValue.Arr(tools.map(::toolNode))
            fields["tool_choice"] = strOf("auto")
        }
        return AgentJson.write(AgentJsonValue.Obj(fields))
    }

    private fun messageNode(message: AgentMessage): AgentJsonValue {
        val fields = linkedMapOf<String, AgentJsonValue>(
            "role" to strOf(message.role.wire),
            "content" to strOf(message.content),
        )
        if (message.toolCalls.isNotEmpty()) {
            fields["tool_calls"] = AgentJsonValue.Arr(
                message.toolCalls.map { call ->
                    objOf(
                        "id" to strOf(call.id),
                        "type" to strOf("function"),
                        "function" to objOf(
                            "name" to strOf(call.name),
                            "arguments" to strOf(call.argumentsJson),
                        ),
                    )
                },
            )
        }
        message.toolCallId?.takeIf { it.isNotBlank() }?.let { fields["tool_call_id"] = strOf(it) }
        return AgentJsonValue.Obj(fields)
    }

    private fun toolNode(spec: AgentToolSpec): AgentJsonValue {
        val schema = AgentJson.parse(spec.parametersJson)
            ?: objOf("type" to strOf("object"), "properties" to objOf())
        return objOf(
            "type" to strOf("function"),
            "function" to objOf(
                "name" to strOf(spec.name),
                "description" to strOf(spec.description),
                "parameters" to schema,
            ),
        )
    }
}
