package com.example.data.remote

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.ToJson
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

class FlexibleStringListAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): List<String> {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> listOf(reader.nextString())
            JsonReader.Token.BEGIN_ARRAY -> {
                val list = mutableListOf<String>()
                reader.beginArray()
                while (reader.hasNext()) {
                    when (reader.peek()) {
                        JsonReader.Token.STRING -> list.add(reader.nextString())
                        JsonReader.Token.NUMBER -> list.add(reader.nextString())
                        else -> reader.skipValue()
                    }
                }
                reader.endArray()
                list
            }
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                emptyList()
            }
            JsonReader.Token.NUMBER -> listOf(reader.nextString())
            else -> {
                reader.skipValue()
                emptyList()
            }
        }
    }

    @ToJson
    fun toJson(value: List<String>): List<String> = value
}

@JsonClass(generateAdapter = true)
data class ArchiveSearchResponse(
    val response: ArchiveSearchBody? = null
)

@JsonClass(generateAdapter = true)
data class ArchiveSearchBody(
    val numFound: Int = 0,
    val docs: List<ArchiveDoc> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ArchiveDoc(
    val identifier: String = "",
    val title: List<String> = emptyList(),
    val description: List<String> = emptyList(),
    val creator: List<String> = emptyList(),
    val language: List<String> = emptyList(),
    val subject: List<String> = emptyList(),
    val downloads: Long = 0L,
    @Json(name = "publicdate") val publicDate: List<String> = emptyList(),
    @Json(name = "licenseurl") val licenseUrl: List<String> = emptyList(),
    val collection: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ArchiveMetadataResponse(
    val server: String? = null,
    val dir: String? = null,
    val files: List<ArchiveFileEntry> = emptyList(),
    val metadata: ArchiveItemMetadata? = null
)

@JsonClass(generateAdapter = true)
data class ArchiveItemMetadata(
    val identifier: String? = null,
    val title: List<String> = emptyList(),
    val description: List<String> = emptyList(),
    val creator: List<String> = emptyList(),
    val language: List<String> = emptyList(),
    val subject: List<String> = emptyList(),
    @Json(name = "licenseurl") val licenseUrl: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ArchiveFileEntry(
    val name: String = "",
    val format: String? = null,
    val source: String? = null,
    val size: String? = null,
    val length: String? = null,
    val width: String? = null,
    val height: String? = null
)

interface ArchiveOrgApi {
    @GET("advancedsearch.php")
    suspend fun searchVideos(
        @Query("q") query: String,
        @Query("fl[]") fields: List<String> = listOf(
            "identifier", "title", "description", "creator", "language",
            "subject", "downloads", "publicdate", "licenseurl", "collection"
        ),
        @Query("sort[]") sort: String = "downloads desc",
        @Query("rows") rows: Int = 20,
        @Query("page") page: Int = 1,
        @Query("output") output: String = "json"
    ): ArchiveSearchResponse

    @GET("metadata/{identifier}")
    suspend fun getMetadata(
        @Path("identifier") identifier: String
    ): ArchiveMetadataResponse
}
