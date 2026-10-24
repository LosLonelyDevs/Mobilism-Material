package org.mobilism.material.settings

import android.content.Context
import android.net.Uri
import android.graphics.Typeface
import android.provider.OpenableColumns
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.mobilism.material.designsystem.AppFont
import org.mobilism.material.designsystem.ThemeCatalog
import org.mobilism.material.designsystem.ThemeDefinition

data class AppearanceState(
    val themeId: String = ThemeCatalog.default.id,
    val font: AppFont = AppFont.Geist,
    val reduceMotion: Boolean = false,
    val customThemes: List<ThemeDefinition> = emptyList(),
    val customFontFile: File? = null,
    val customFontName: String? = null,
) {
    val themes: List<ThemeDefinition> get() = ThemeCatalog.builtIn + customThemes
    val theme: ThemeDefinition get() = ThemeCatalog.resolve(themeId, customThemes)
}

class AppearanceStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("material-appearance", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "themes")
    private val fontDirectory = File(context.filesDir, "fonts")
    private val mutableState = MutableStateFlow(AppearanceState())
    val state = mutableState.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val themes = directory.listFiles().orEmpty().filter { it.extension == "json" && it.length() <= 64 * 1024 }
            .mapNotNull { file -> runCatching { ThemeCatalog.parse(file.readText()) }.getOrNull() }
        val customFont = preferences.getString("font-file", null)
            ?.takeIf { it.matches(Regex("font-[a-zA-Z0-9-]+\\.font")) }
            ?.let { File(fontDirectory, it) }
            ?.takeIf { it.isFile && it.length() in 1..MAX_FONT_BYTES && validFont(it) }
        val savedFont = AppFont.entries.firstOrNull { it.name == preferences.getString("font", null) } ?: AppFont.Geist
        mutableState.value = AppearanceState(
            themeId = preferences.getString("theme", ThemeCatalog.default.id) ?: ThemeCatalog.default.id,
            font = if (savedFont == AppFont.Custom && customFont == null) AppFont.Geist else savedFont,
            reduceMotion = preferences.getBoolean("reduce-motion", false),
            customThemes = themes,
            customFontFile = customFont,
            customFontName = preferences.getString("font-name", null).takeIf { customFont != null },
        )
    }

    fun selectTheme(theme: ThemeDefinition) {
        preferences.edit().putString("theme", theme.id).apply()
        mutableState.update { it.copy(themeId = theme.id) }
    }

    fun selectFont(font: AppFont) {
        if (font == AppFont.Custom && mutableState.value.customFontFile == null) return
        preferences.edit().putString("font", font.name).apply()
        mutableState.update { it.copy(font = font) }
    }

    suspend fun importFont(uri: Uri) = withContext(Dispatchers.IO) {
        check(fontDirectory.isDirectory || fontDirectory.mkdirs()) { "Could not save the font." }
        val file = File.createTempFile("font-", ".font", fontDirectory)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Could not open the font.")
            input.use { stream ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        total += read
                        require(total <= MAX_FONT_BYTES) { "Choose a font smaller than 10 MB." }
                        output.write(buffer, 0, read)
                    }
                }
            }
            require(validFont(file)) { "Choose a valid TTF, OTF or TTC font." }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.substringBeforeLast('.')?.filterNot { it.isISOControl() }?.trim()?.take(80)
                ?.takeIf { it.isNotEmpty() } ?: "Custom font"
            check(preferences.edit().putString("font-file", file.name).putString("font-name", name)
                .putString("font", AppFont.Custom.name).commit()) { "Could not save the font." }
            mutableState.update { it.copy(font = AppFont.Custom, customFontFile = file, customFontName = name) }
            // A new filename keeps Compose's font cache from reusing the previous upload.
            fontDirectory.listFiles().orEmpty().filter { it != file }.forEach { it.delete() }
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }

    private fun validFont(file: File): Boolean = runCatching {
        val bytes = ByteArray(4)
        if (file.inputStream().use { it.read(bytes) } != 4) return@runCatching false
        val header = bytes.toList()
        val signatures = listOf(byteArrayOf(0, 1, 0, 0), "OTTO".toByteArray(), "ttcf".toByteArray(), "true".toByteArray())
        signatures.any { it.toList() == header } && Typeface.Builder(file).build() != null
    }.getOrDefault(false)

    private companion object { const val MAX_FONT_BYTES = 10L * 1024 * 1024 }

    fun reduceMotion(reduced: Boolean) {
        preferences.edit().putBoolean("reduce-motion", reduced).apply()
        mutableState.update { it.copy(reduceMotion = reduced) }
    }

    suspend fun importTheme(uri: Uri) = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(4096)
            val bytes = ByteArrayOutputStream()
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                require(bytes.size() + read <= 64 * 1024) { "Theme exceeds 64 KiB." }
                bytes.write(buffer, 0, read)
            }
            bytes.toByteArray().toString(Charsets.UTF_8)
        } ?: error("The theme file could not be opened.")
        val theme = ThemeCatalog.parse(json)
        check(directory.exists() || directory.mkdirs()) { "Could not create the theme folder." }
        val file = AtomicFile(File(directory, "${theme.id}.json"))
        val output = file.startWrite()
        try {
            output.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
        mutableState.update { it.copy(customThemes = it.customThemes.filterNot { old -> old.id == theme.id } + theme) }
        selectTheme(theme)
    }
}
