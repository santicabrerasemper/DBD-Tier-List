package com.santi.dbdmeta

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun JsonElement.toKillers(): List<Killer> =
    jsonObject.mapNotNull { (apiKey, value) ->
        val obj = value.jsonObject
        val name = obj.string("name") ?: return@mapNotNull null
        Killer(
            id = name.toDbdId(),
            apiId = apiKey,
            name = name,
            difficulty = obj.string("difficulty"),
            imagePath = obj.string("image"),
            perkIds = obj.stringList("perks").map(String::toDbdId)
        )
    }.sortedBy { it.name }

fun JsonElement.toSurvivors(): List<Survivor> =
    jsonObject.mapNotNull { (apiKey, value) ->
        val obj = value.jsonObject
        val name = obj.string("name") ?: return@mapNotNull null
        Survivor(
            id = name.toDbdId(),
            apiId = apiKey,
            name = name,
            imagePath = obj.string("image"),
            perkIds = obj.stringList("perks").map(String::toDbdId)
        )
    }.sortedBy { it.name }

fun JsonElement.toPerks(role: Role): List<Perk> =
    jsonObject.mapNotNull { (apiKey, value) ->
        val obj = value.jsonObject
        val name = obj.string("name") ?: return@mapNotNull null
        Perk(
            id = name.toDbdId(),
            apiId = apiKey,
            name = name,
            role = role,
            characterId = obj.string("character")?.toDbdId(),
            description = obj.string("description").orEmpty().stripHtml(),
            categories = obj.stringList("categories"),
            imagePath = obj.string("image")
        )
    }.sortedBy { it.name }

fun JsonElement.toPatches(): List<Patch> =
    jsonArray.mapNotNull { value ->
        val obj = value.jsonObject
        val id = obj.string("id") ?: return@mapNotNull null
        Patch(
            id = id,
            type = (obj.int("type") ?: -1).toPatchType(),
            notesHtml = obj.string("notes").orEmpty()
        )
    }

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull

@Suppress("unused")
private fun JsonObject.double(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull

private fun JsonObject.stringList(key: String): List<String> {
    val element = this[key] ?: return emptyList()
    if (element !is JsonArray) return emptyList()
    return element.mapNotNull { it.jsonPrimitive.contentOrNull }
}
