package dev.syncforge.backendstarterspring.web

import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushRequest
import dev.syncforge.network.api.PushResponse
import dev.syncforge.server.syncServerJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.HttpMessageNotWritableException
import kotlin.reflect.KClass

class SyncForgeJsonHttpMessageConverter : AbstractHttpMessageConverter<Any>(MediaType.APPLICATION_JSON) {

    private val supportedTypes: Map<KClass<*>, KSerializer<*>> = mapOf(
        PushRequest::class to PushRequest.serializer(),
        PushResponse::class to PushResponse.serializer(),
        PullResponse::class to PullResponse.serializer(),
        TokenResponse::class to TokenResponse.serializer(),
        Map::class to serializer<Map<String, String>>(),
    )

    override fun supports(clazz: Class<*>): Boolean =
        supportedTypes.keys.any { it.java.isAssignableFrom(clazz) } ||
            Map::class.java.isAssignableFrom(clazz)

    override fun readInternal(clazz: Class<out Any>, inputMessage: HttpInputMessage): Any {
        val body = inputMessage.body.reader().readText()
        val serializer = serializerFor(clazz)
            ?: throw HttpMessageNotReadableException("Unsupported request type: ${clazz.name}", inputMessage)
        return syncServerJson.decodeFromString(serializer, body) as Any
    }

    override fun writeInternal(value: Any, outputMessage: HttpOutputMessage) {
        val serializer = serializerFor(value::class.java)
            ?: throw HttpMessageNotWritableException("Unsupported response type: ${value::class.java.name}")
        @Suppress("UNCHECKED_CAST")
        val json = syncServerJson.encodeToString(serializer as KSerializer<Any>, value)
        outputMessage.body.write(json.toByteArray())
    }

    private fun serializerFor(clazz: Class<*>): KSerializer<*>? {
        if (Map::class.java.isAssignableFrom(clazz)) {
            return serializer<Map<String, String>>()
        }
        return supportedTypes.entries.firstOrNull { (type, _) -> type.java.isAssignableFrom(clazz) }?.value
    }
}