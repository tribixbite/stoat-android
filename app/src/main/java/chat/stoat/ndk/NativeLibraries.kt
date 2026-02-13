package chat.stoat.ndk

import android.util.Log
import chat.stoat.BuildConfig

annotation class NativeLibrary(val name: String) {
    companion object {
        const val LIB_NAME_NATIVE_MARKDOWN = "stendal"
        const val LIB_NAME_NATIVE_MARKDOWN_V2 = "finalmarkdown"
    }
}

object NativeLibraries {
    private const val TAG = "NativeLibraries"

    /** Whether the stendal (cmark) native library loaded successfully */
    var stendalAvailable: Boolean = false
        private set

    /** Whether the finalmarkdown native library loaded successfully */
    var finalMarkdownAvailable: Boolean = false
        private set

    fun init() {
        // Load stendal (cmark markdown parser)
        try {
            System.loadLibrary(NativeLibrary.LIB_NAME_NATIVE_MARKDOWN)
            Stendal.init()
            stendalAvailable = true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "stendal native library not available: ${e.message}")
        }

        // Load finalmarkdown (experimental, source not yet available)
        try {
            System.loadLibrary(NativeLibrary.LIB_NAME_NATIVE_MARKDOWN_V2)
            FinalMarkdown.init(BuildConfig.DEBUG)
            finalMarkdownAvailable = true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "finalmarkdown native library not available: ${e.message}")
        }
    }
}
