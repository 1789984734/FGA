package io.github.fate_grand_automata.util

import android.content.Context
import android.content.res.AssetManager
import io.github.fate_grand_automata.IStorageProvider
import io.github.fate_grand_automata.SupportImageKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

class SupportImageExtractor(
    val context: Context,
    val storageProvider: IStorageProvider
) {
    private val SupportImageKind.assetFolder
        get() = "Support/" + when (this) {
            SupportImageKind.Servant -> "servant"
            SupportImageKind.CE -> "ce"
            SupportImageKind.Friend -> "friend"
        }

    private fun extract(kind: SupportImageKind) {
        val assetFolder = kind.assetFolder
        val assets = context.assets

        val assetFileNames = assets.list(assetFolder) ?: run {
            Timber.w("Asset folder $assetFolder not found")
            return
        }

        for (assetFileName in assetFileNames) {
            val assetPath = "${assetFolder}/$assetFileName"
            val subFiles = assets.list(assetPath) ?: emptyArray()

            // This is a folder
            if (subFiles.isNotEmpty()) {
                for (subFileName in subFiles) {
                    val subAssetPath = "${assetPath}/$subFileName"
                    val subOutName = "$assetFileName/$subFileName"

                    copyAssetToFile(
                        assets,
                        subAssetPath,
                        kind,
                        subOutName
                    )
                }
            } else {
                copyAssetToFile(
                    assets,
                    assetPath,
                    kind,
                    assetFileName
                )
            }
        }
    }

    private fun copyAssetToFile(Assets: AssetManager, AssetPath: String, kind: SupportImageKind, fileName: String) {
        val originalDigest = MessageDigest.getInstance("SHA-256")
        val copiedFileDigest = MessageDigest.getInstance("SHA-256")

        repeat(MAX_COPY_ATTEMPTS) { attempt ->
            originalDigest.reset()
            copiedFileDigest.reset()

            val assetStream = DigestInputStream(Assets.open(AssetPath), originalDigest)
            assetStream.use {
                val outStream = DigestOutputStream(storageProvider.writeSupportImage(kind, fileName), copiedFileDigest)
                outStream.use {
                    assetStream.copyTo(outStream)
                }
            }

            if (MessageDigest.isEqual(originalDigest.digest(), copiedFileDigest.digest())) {
                return
            }

            Timber.w("Digests were not equal (attempt ${attempt + 1}/$MAX_COPY_ATTEMPTS)")
        }

        throw KnownException(KnownException.Reason.CouldNotOpenSupportFileForWriting(kind, fileName))
    }

    suspend fun extract() =
        withContext(Dispatchers.IO) {
            storageProvider.createNoMediaFile()
            extract(SupportImageKind.Servant)
            extract(SupportImageKind.CE)
        }

    companion object {
        private const val MAX_COPY_ATTEMPTS = 3
    }
}