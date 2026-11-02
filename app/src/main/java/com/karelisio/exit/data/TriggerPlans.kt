package com.karelisio.exit.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Plans d'urgence : pour chaque déclencheur, l'alternative choisie par l'utilisateur. */
object TriggerPlans {
    fun parse(raw: String): Map<String, String> = runCatching {
        Json.parseToJsonElement(raw).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }.getOrDefault(emptyMap())

    fun encode(plans: Map<String, String>): String = JsonObject(plans.mapValues { JsonPrimitive(it.value) }).toString()
}
