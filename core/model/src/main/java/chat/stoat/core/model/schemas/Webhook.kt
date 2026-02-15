package chat.stoat.core.model.schemas

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stoat/Revolt webhook object.
 * Token is only present when fetched with token auth or by webhook creator.
 */
@Serializable
@Parcelize
data class Webhook(
    @SerialName("id")
    val id: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    @SerialName("channel_id")
    val channelId: String? = null,
    val permissions: Long? = null,
    val token: String? = null
) : Parcelable
