package org.mobilism.material.settings

import android.content.Context
import android.net.Uri
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
) {
    val themes: List<ThemeDefinition> get() = ThemeCatalog.builtIn + customThemes
    val theme: ThemeDefinition get() = ThemeCatalog.resolve(themeId, customThemes)
}

class AppearanceStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("material-appearance", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "themes")
    private val mutableState = MutableStateFlow(AppearanceState())
    val state = mutableState.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val themes = directory.listFiles().orEmpty().filter { it.extension == "json" && it.length() <= 64 * 1024 }
            .mapNotNull { file -> runCatching { ThemeCatalog.parse(file.readText()) }.getOrNull() }
        mutableState.value = AppearanceState(
            themeId = preferences.getString("theme", ThemeCatalog.default.id) ?: ThemeCatalog.default.id,
            font = AppFont.entries.firstOrNull { it.name == preferences.getString("font", null) } ?: AppFont.Geist,
            reduceMotion = preferences.getBoolean("reduce-motion", false),
            customThemes = themes,
        )
    }

    fun selectTheme(theme: ThemeDefinition) {
        preferences.edit().putString("theme", theme.id).apply()
        mutableState.update { it.copy(themeId = theme.id) }
    }

    fun selectFont(font: AppFont) {
        preferences.edit().putString("font", font.name).apply()
        mutableState.update { it.copy(font = font) }
    }

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
