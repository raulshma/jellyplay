package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Regression guard for the device failure where a Jellyfin 12 + Calibre
 * library rendered every "C# …" book as download-only: the captured detail
 * JSON (BODY-level OkHttp log) carries a `.epub` `Path`, but the old
 * `stripUrlSuffixes` chopped the raw filesystem path at the literal `#` in
 * "C#", erasing the extension. The full wire → DTO → format chain must
 * resolve to a readable EPUB.
 */
class BookDetailPathParsingRegressionTest {

    @Test
    fun capturedDeviceJsonMapsToEpubBook() {
        val json = """
            {"Name":"Building CLI Applications with C# and .NET","ServerId":"84d626a1b0654459b5bc84c15b4d2d0d","Id":"f2dca93322bf9e5edcd2508cf13abcf8","PremiereDate":"2025-02-18T18:30:00.0000000Z","ExternalUrls":[],"Path":"/media/D1DSSD/Media/eBook/JellyfinBooks/Building CLI Applications with C# and .NET (23)/Building CLI Applications with C# and .NET - Tidjani Belmansour.epub","ChannelId":null,"Overview":"A step-by-step guide","Genres":[],"RunTimeTicks":10000000,"ProductionYear":2025,"ProviderIds":{},"Type":"Book","People":[{"Name":"Tidjani Belmansour","Id":"0d49598c1a2e93202853ee7ca85ce8cb","Role":"","Type":"Author"}],"Studios":[{"Name":"Packt Publishing Pvt Ltd","Id":"3c47aade9e1e2a2953cb2d0eec6d6bd0"}],"GenreItems":[],"UserData":{"PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":false,"Played":false,"Key":"f2dca933-22bf-9e5e-dcd2-508cf13abcf8","ItemId":"f2dca93322bf9e5edcd2508cf13abcf8"},"SeriesName":"","PrimaryImageAspectRatio":0.81,"ImageTags":{"Primary":"350de46c3da1d98ecbe6e77e6cfdf602"},"BackdropImageTags":[],"ImageBlurHashes":{"Primary":{"350de46c3da1d98ecbe6e77e6cfdf602":"dKC?7SNFMx^j~9t6jGw]ITxatSRiI;w{I=NuS%aLNHXS"}},"Chapters":[],"LocationType":"FileSystem","MediaType":"Book"}
        """.trimIndent()

        val dto = Json { ignoreUnknownKeys = true }.decodeFromString(BaseItemDto.serializer(), json)
        assertEquals("/media/D1DSSD/Media/eBook/JellyfinBooks/Building CLI Applications with C# and .NET (23)/Building CLI Applications with C# and .NET - Tidjani Belmansour.epub", dto.path)
        val mediaType = dto.type.toMediaType()
        assertEquals(com.raulshma.jellyplay.core.model.MediaType.BOOK, mediaType)
        val format = BookFormat.fromPath(dto.path)
        assertNotNull(format)
        assertEquals(BookFormat.EPUB, format)
    }
}
