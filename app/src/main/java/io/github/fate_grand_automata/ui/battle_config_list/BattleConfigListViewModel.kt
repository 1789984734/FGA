package io.github.fate_grand_automata.ui.battle_config_list

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.serialization.json.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.fate_grand_automata.R
import io.github.fate_grand_automata.prefs.core.BattleConfigCore
import io.github.fate_grand_automata.prefs.core.PrefsCore
import io.github.fate_grand_automata.scripts.enums.GameServer
import io.github.fate_grand_automata.scripts.prefs.IBattleConfig
import io.github.fate_grand_automata.scripts.prefs.IPreferences
import io.github.fate_grand_automata.util.toggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BattleConfigListViewModel @Inject constructor(
    prefsCore: PrefsCore,
    val prefs: IPreferences
) : ViewModel() {
    val battleConfigItems = prefsCore
        .battleConfigList
        .asFlow()
        .map { list ->
            list
                .map { key -> prefsCore.forBattleConfig(key) }
                .sortedWith(
                    compareBy<BattleConfigCore, Int?>((nullsFirst())) {
                        // sort by null, NA, JP, CN, TW, KR
                        it.server.get().asGameServer()?.let { server ->
                            GameServer.values.indexOf(server)
                        }
                    }.thenBy(String.CASE_INSENSITIVE_ORDER) {
                        it.name.get()
                    }
                )
        }

    private val _selectedConfigs = MutableStateFlow(emptySet<String>())
    val selectedConfigs: StateFlow<Set<String>> = _selectedConfigs

    fun toggleSelected(id: String) {
        _selectedConfigs.value = selectedConfigs.value.toggle(id)

        if (selectedConfigs.value.isEmpty()) {
            endSelection()
        }
    }

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode

    fun startSelection(id: String) {
        _selectedConfigs.value = setOf(id)
        _selectionMode.value = true
    }

    fun endSelection() {
        _selectionMode.value = false
    }

    private fun configsToExport() =
        if (selectionMode.value) {
            selectedConfigs.value.map { prefs.forBattleConfig(it) }
        } else prefs.battleConfigs

    fun newConfig(): IBattleConfig {
        val guid = UUID.randomUUID().toString()

        return prefs.addBattleConfig(guid)
    }

    data class ImportExportResult(val failureCount: Int)

    private suspend fun exportAsync(dirUri: Uri, context: Context): ImportExportResult {
        var failed = 0

        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val dir = DocumentFile.fromTreeUri(context, dirUri)

            val configs = configsToExport()

            configs.forEach { battleConfig ->
                val values = battleConfig.export()
                val jsonObject = buildJsonObject {
                    for ((key, value) in values) {
                        when (value) {
                            is String -> put(key, value)
                            is Number -> put(key, JsonPrimitive(value))
                            is Boolean -> put(key, value)
                            is Set<*> -> putJsonArray(key) { value.forEach { add(it.toString()) } }
                            null -> put(key, JsonNull)
                            else -> put(key, value.toString())
                        }
                    }
                }

                try {
                    dir?.createFile("*/*", "${battleConfig.name}.fga")
                        ?.uri
                        ?.let { uri ->
                            resolver.openOutputStream(uri)?.use { outStream ->
                                outStream.writer().use { it.write(jsonObject.toString()) }
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to export")
                    ++failed
                }
            }
        }

        return ImportExportResult(failed)
    }

    fun exportBattleConfigs(context: Context, dirUri: Uri?) {
        if (dirUri != null) {
            viewModelScope.launch {
                val result = exportAsync(dirUri, context)

                if (result.failureCount > 0) {
                    val msg = context.getString(R.string.battle_config_list_export_failed, result.failureCount)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun importAsync(uris: List<Uri>, context: Context): ImportExportResult {
        var failed = 0

        withContext(Dispatchers.IO) {
            uris.forEach { uri ->
                try {
                    val jsonText = context.contentResolver.openInputStream(uri)?.use { inStream ->
                        inStream.use {
                            it.reader().readText()
                        }
                    }

                    if (jsonText != null) {
                        val jsonObject = Json.parseToJsonElement(jsonText).jsonObject
                        val map = jsonObject.entries.associate { (key, element) ->
                            key to when (element) {
                                is JsonArray -> element.map { it.jsonPrimitive.content }.toSet()
                                is JsonPrimitive -> when {
                                    element.isString -> element.content
                                    element.booleanOrNull != null -> element.boolean
                                    element.intOrNull != null -> element.int
                                    element.longOrNull != null -> element.long
                                    element.floatOrNull != null -> element.float
                                    else -> element.content
                                }
                                else -> null
                            }
                        }

                        newConfig().import(map)
                    }
                } catch (e: Exception) {
                    ++failed
                    Timber.e(e, "Import Failed")
                }
            }
        }

        return ImportExportResult(failed)
    }

    fun importBattleConfigs(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val result = importAsync(uris, context)

            if (result.failureCount > 0) {
                val msg = context.getString(R.string.battle_config_list_import_failed, result.failureCount)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun deleteSelected() {
        selectedConfigs.value.forEach {
            prefs.removeBattleConfig(it)
        }

        endSelection()
    }
}