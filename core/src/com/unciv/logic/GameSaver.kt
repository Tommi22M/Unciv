package com.unciv.logic

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.JsonReader
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.doMigrations
import com.unciv.models.metadata.isMigrationNecessary
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.saves.Gzip
import com.unciv.utils.Log
import kotlinx.coroutines.Job
import java.io.File

private const val SAVE_FILES_FOLDER = "SaveFiles"
private const val MULTIPLAYER_FILES_FOLDER = "MultiplayerGames"
private const val AUTOSAVE_FILE_NAME = "Autosave"
private const val SETTINGS_FILE_NAME = "GameSettings.json"

class GameSaver(
    /**
     * This is necessary because the Android turn check background worker does not hold any reference to the actual [com.badlogic.gdx.Application],
     * which is normally responsible for keeping the [Gdx] static variables from being garbage collected.
     */
    private val files: Files,
    private val customFileLocationHelper: CustomFileLocationHelper? = null,
    /** When set, we know we're on Android and can save to the app's personal external file directory
     * See https://developer.android.com/training/data-storage/app-specific#external-access-files */
    private val externalFilesDirForAndroid: String? = null
) {
    //region Data

    var autoSaveJob: Job? = null

    //endregion
    //region Helpers

    fun getSave(gameName: String): FileHandle {
        return getSave(SAVE_FILES_FOLDER, gameName)
    }
    fun getMultiplayerSave(gameName: String): FileHandle {
        return getSave(MULTIPLAYER_FILES_FOLDER, gameName)
    }

    private fun getSave(saveFolder: String, gameName: String): FileHandle {
        val localFile = files.local("${saveFolder}/$gameName")
        if (externalFilesDirForAndroid.isNullOrBlank() || !files.isExternalStorageAvailable) return localFile
        val externalFile = files.absolute(externalFilesDirForAndroid + "/${saveFolder}/$gameName")
        if (localFile.exists() && !externalFile.exists()) return localFile
        return externalFile
    }

    fun getMultiplayerSaves(): Sequence<FileHandle> {
        return getSaves(MULTIPLAYER_FILES_FOLDER)
    }

    fun getSaves(autoSaves: Boolean = true): Sequence<FileHandle> {
        val saves = getSaves(SAVE_FILES_FOLDER)
        val filteredSaves = if (autoSaves) { saves } else { saves.filter { !it.name().startsWith(AUTOSAVE_FILE_NAME) }}
        return filteredSaves
    }

    private fun getSaves(saveFolder: String): Sequence<FileHandle> {
        val localSaves = files.local(saveFolder).list().asSequence()
        if (externalFilesDirForAndroid.isNullOrBlank() || !files.isExternalStorageAvailable) return localSaves
        return localSaves + files.absolute(externalFilesDirForAndroid + "/${saveFolder}").list().asSequence()
    }

    fun canLoadFromCustomSaveLocation() = customFileLocationHelper != null

    /** Deletes a save.
     *  @return `true` if successful.
     *  @throws SecurityException when delete access was denied
      */
    fun deleteSave(gameName: String): Boolean {
        return getSave(gameName).delete()
    }

    fun deleteMultiplayerSave(gameName: String) {
        getMultiplayerSave(gameName).delete()
    }

    /**
     * Only use this with a [FileHandle] obtained by one of the methods of this class!
     */
    fun deleteSave(file: FileHandle) {
        file.delete()
    }

    interface ChooseLocationResult {
        val location: String?
        val exception: Exception?

        fun isCanceled(): Boolean = location == null && exception == null
        fun isError(): Boolean = exception != null
        fun isSuccessful(): Boolean = location != null
    }

    //endregion
    //region Saving

    fun saveGame(game: GameInfo, GameName: String, saveCompletionCallback: (Exception?) -> Unit = { if (it != null) throw it }): FileHandle {
        val file = getSave(GameName)
        saveGame(game, file, saveCompletionCallback)
        return file
    }

    /**
     * Only use this with a [FileHandle] obtained by one of the methods of this class!
     */
    fun saveGame(game: GameInfo, file: FileHandle, saveCompletionCallback: (Exception?) -> Unit = { if (it != null) throw it }) {
        try {
            file.writeString(gameInfoToString(game), false)
            saveCompletionCallback(null)
        } catch (ex: Exception) {
            saveCompletionCallback(ex)
        }
    }

    /**
     * Overload of function saveGame to save a GameInfoPreview in the MultiplayerGames folder
     */
    fun saveGame(game: GameInfoPreview, gameName: String, saveCompletionCallback: (Exception?) -> Unit = { if (it != null) throw it }): FileHandle {
        val file = getMultiplayerSave(gameName)
        saveGame(game, file, saveCompletionCallback)
        return file
    }

    /**
     * Only use this with a [FileHandle] obtained by one of the methods of this class!
     */
    fun saveGame(game: GameInfoPreview, file: FileHandle, saveCompletionCallback: (Exception?) -> Unit = { if (it != null) throw it }) {
        try {
            json().toJson(game, file)
            saveCompletionCallback(null)
        } catch (ex: Exception) {
            saveCompletionCallback(ex)
        }
    }

    class CustomSaveResult(
        override val location: String? = null,
        override val exception: Exception? = null
    ) : ChooseLocationResult

    /**
     * [gameName] is a suggested name for the file. If the file has already been saved to or loaded from a custom location,
     * this previous custom location will be used.
     *
     * Calls the [saveCompleteCallback] on the main thread with the save location on success, an [Exception] on error, or both null on cancel.
     */
    fun saveGameToCustomLocation(game: GameInfo, gameName: String, saveCompletionCallback: (CustomSaveResult) -> Unit) {
        val saveLocation = game.customSaveLocation ?: Gdx.files.local(gameName).path()
        val gameData = try {
            gameInfoToString(game)
        } catch (ex: Exception) {
            postCrashHandlingRunnable { saveCompletionCallback(CustomSaveResult(exception = ex)) }
            return
        }
        customFileLocationHelper!!.saveGame(gameData, saveLocation) {
            if (it.isSuccessful()) {
                game.customSaveLocation = it.location
            }
            saveCompletionCallback(it)
        }
    }

    //endregion
    //region Loading

    fun loadGameByName(gameName: String) =
            loadGameFromFile(getSave(gameName))

    fun loadGameFromFile(gameFile: FileHandle): GameInfo {
        return gameInfoFromString(gameFile.readString())
    }

    fun loadGamePreviewByName(gameName: String) =
            loadGamePreviewFromFile(getMultiplayerSave(gameName))

    fun loadGamePreviewFromFile(gameFile: FileHandle): GameInfoPreview {
        return json().fromJson(GameInfoPreview::class.java, gameFile)
    }

    class CustomLoadResult<T>(
        private val locationAndGameData: Pair<String, T>? = null,
        override val exception: Exception? = null
    ) : ChooseLocationResult {
        override val location: String? get() = locationAndGameData?.first
        val gameData: T? get() = locationAndGameData?.second
    }

    /**
     * Calls the [loadCompleteCallback] on the main thread with the [GameInfo] on success or the [Exception] on error or null in both on cancel.
     */
    fun loadGameFromCustomLocation(loadCompletionCallback: (CustomLoadResult<GameInfo>) -> Unit) {
        customFileLocationHelper!!.loadGame { result ->
            val location = result.location
            val gameData = result.gameData
            if (location == null || gameData == null) {
                loadCompletionCallback(CustomLoadResult(exception = result.exception))
                return@loadGame
            }

            try {
                val gameInfo = gameInfoFromString(gameData)
                gameInfo.customSaveLocation = location
                gameInfo.setTransients()
                loadCompletionCallback(CustomLoadResult(location to gameInfo))
            } catch (ex: Exception) {
                loadCompletionCallback(CustomLoadResult(exception = ex))
            }
        }
    }


    //endregion
    //region Settings

    private fun getGeneralSettingsFile(): FileHandle {
        return if (UncivGame.Current.consoleMode) FileHandle(SETTINGS_FILE_NAME)
        else files.local(SETTINGS_FILE_NAME)
    }

    fun getGeneralSettings(): GameSettings {
        val settingsFile = getGeneralSettingsFile()
        var settings: GameSettings? = null
        if (settingsFile.exists()) {
            try {
                settings = json().fromJson(GameSettings::class.java, settingsFile)
                if (settings.isMigrationNecessary()) {
                    settings.doMigrations(JsonReader().parse(settingsFile))
                }
            } catch (ex: Exception) {
                // I'm not sure of the circumstances,
                // but some people were getting null settings, even though the file existed??? Very odd.
                // ...Json broken or otherwise unreadable is the only possible reason.
                Log.error("Error reading settings file", ex)
            }
        }

        return settings ?: GameSettings().apply { isFreshlyCreated = true }
    }

    fun setGeneralSettings(gameSettings: GameSettings) {
        getGeneralSettingsFile().writeString(json().toJson(gameSettings), false)
    }

    companion object {

        var saveZipped = false

        /** Specialized function to access settings before Gdx is initialized.
         *
         * @param base Path to the directory where the file should be - if not set, the OS current directory is used (which is "/" on Android)
         */
        fun getSettingsForPlatformLaunchers(base: String = "."): GameSettings {
            // FileHandle is Gdx, but the class and JsonParser are not dependent on app initialization
            // In fact, at this point Gdx.app or Gdx.files are null but this still works.
            val file = FileHandle(base + File.separator + SETTINGS_FILE_NAME)
            return if (file.exists())
                json().fromJsonFile(
                    GameSettings::class.java,
                    file
                )
            else GameSettings().apply { isFreshlyCreated = true }
        }

        fun gameInfoFromString(gameData: String): GameInfo {
            return gameInfoFromStringWithoutTransients(gameData).apply {
                setTransients()
            }
        }

        /**
         * Parses [gameData] as gzipped serialization of a [GameInfoPreview]
         * @throws SerializationException
         */
        fun gameInfoPreviewFromString(gameData: String): GameInfoPreview {
            return json().fromJson(GameInfoPreview::class.java, Gzip.unzip(gameData))
        }

        /**
         * WARNING! transitive GameInfo data not initialized
         * The returned GameInfo can not be used for most circumstances because its not initialized!
         * It is therefore stateless and save to call for Multiplayer Turn Notifier, unlike gameInfoFromString().
         *
         * @throws SerializationException
         */
        private fun gameInfoFromStringWithoutTransients(gameData: String): GameInfo {
            val unzippedJson = try {
                Gzip.unzip(gameData)
            } catch (ex: Exception) {
                gameData
            }
            return json().fromJson(GameInfo::class.java, unzippedJson)
        }

        /** Returns gzipped serialization of [game], optionally gzipped ([forceZip] overrides [saveZipped]) */
        fun gameInfoToString(game: GameInfo, forceZip: Boolean? = null): String {
            val plainJson = json().toJson(game)
            return if (forceZip ?: saveZipped) Gzip.zip(plainJson) else plainJson
        }

        /** Returns gzipped serialization of preview [game] */
        fun gameInfoToString(game: GameInfoPreview): String {
            return Gzip.zip(json().toJson(game))
        }

    }

    //endregion
    //region Autosave

    /**
     * Runs autoSave
     */
    fun autoSave(gameInfo: GameInfo, postRunnable: () -> Unit = {}) {
        // The save takes a long time (up to a few seconds on large games!) and we can do it while the player continues his game.
        // On the other hand if we alter the game data while it's being serialized we could get a concurrent modification exception.
        // So what we do is we clone all the game data and serialize the clone.
        autoSaveUnCloned(gameInfo.clone(), postRunnable)
    }

    fun autoSaveUnCloned(gameInfo: GameInfo, postRunnable: () -> Unit = {}) {
        // This is used when returning from WorldScreen to MainMenuScreen - no clone since UI access to it should be gone
        autoSaveJob = launchCrashHandling(AUTOSAVE_FILE_NAME) {
            autoSaveSingleThreaded(gameInfo)
            // do this on main thread
            postCrashHandlingRunnable ( postRunnable )
        }
    }

    fun autoSaveSingleThreaded(gameInfo: GameInfo) {
        try {
            saveGame(gameInfo, AUTOSAVE_FILE_NAME)
        } catch (oom: OutOfMemoryError) {
            return  // not much we can do here
        }

        // keep auto-saves for the last 10 turns for debugging purposes
        val newAutosaveFilename =
            SAVE_FILES_FOLDER + File.separator + AUTOSAVE_FILE_NAME + "-${gameInfo.currentPlayer}-${gameInfo.turns}"
        getSave(AUTOSAVE_FILE_NAME).copyTo(files.local(newAutosaveFilename))

        fun getAutosaves(): Sequence<FileHandle> {
            return getSaves().filter { it.name().startsWith(AUTOSAVE_FILE_NAME) }
        }
        while (getAutosaves().count() > 10) {
            val saveToDelete = getAutosaves().minByOrNull { it.lastModified() }!!
            deleteSave(saveToDelete.name())
        }
    }

    fun loadLatestAutosave(): GameInfo {
        try {
            return loadGameByName(AUTOSAVE_FILE_NAME)
        } catch (ex: Exception) {
            // silent fail if we can't read the autosave for any reason - try to load the last autosave by turn number first
            val autosaves = getSaves().filter { it.name() != AUTOSAVE_FILE_NAME && it.name().startsWith(AUTOSAVE_FILE_NAME) }
            return loadGameFromFile(autosaves.maxByOrNull { it.lastModified() }!!)
        }
    }

    fun autosaveExists(): Boolean {
        return getSave(AUTOSAVE_FILE_NAME).exists()
    }

    // endregion
}
