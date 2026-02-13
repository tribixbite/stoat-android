package chat.stoat.api.routes.server

import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.Member
import chat.stoat.core.model.schemas.PermissionDescription
import chat.stoat.core.model.schemas.Role
import chat.stoat.core.model.schemas.Server
import chat.stoat.core.model.schemas.ServerWithChannelObjects
import chat.stoat.core.model.schemas.User
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

@Serializable
data class FetchMembersResponse(
    val members: List<Member>,
    val users: List<User>
)

suspend fun ackServer(serverId: String) {
    StoatHttp.put("/servers/$serverId/ack".api())
}

suspend fun fetchMembers(
    serverId: String,
    includeOffline: Boolean = false,
    pure: Boolean = false
): FetchMembersResponse {
    val response = StoatHttp.get("/servers/$serverId/members".api()) {
        parameter("exclude_offline", !includeOffline)
    }

    val responseContent = response.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), responseContent)
        throw Error(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    val membersResponse =
        StoatJson.decodeFromString(FetchMembersResponse.serializer(), responseContent)

    if (pure) {
        return membersResponse
    }

    membersResponse.members.forEach { member ->
        if (!StoatAPI.members.hasMember(serverId, member.id!!.user)) {
            StoatAPI.members.setMember(serverId, member)
        }
    }

    membersResponse.users.forEach { user ->
        user.id?.let { StoatAPI.userCache.putIfAbsent(it, user) }
    }

    return membersResponse
}

suspend fun fetchMember(serverId: String, userId: String, pure: Boolean = false): Member {
    val response = StoatHttp.get("/servers/$serverId/members/$userId".api())

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response.bodyAsText())
        throw Exception(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    val member = StoatJson.decodeFromString(Member.serializer(), response.bodyAsText())

    if (!pure) {
        member.id?.let {
            if (!StoatAPI.members.hasMember(serverId, it.user)) {
                StoatAPI.members.setMember(serverId, member)
            }
        }
    }

    return member
}

suspend fun leaveOrDeleteServer(serverId: String, leaveSilently: Boolean = false) {
    StoatHttp.delete("/servers/$serverId".api()) {
        parameter("leave_silently", leaveSilently)
    }
}

@Serializable
data class ServerCreationBody(
    val name: String,
    val description: String? = null,
    val nsfw: Boolean = false
)

suspend fun createServer(
    name: String,
    description: String = "",
    nsfw: Boolean = false
): ServerWithChannelObjects {
    val body = ServerCreationBody(name, description, nsfw)

    val response = StoatHttp.post("/servers/create".api()) {
        setBody(StoatJson.encodeToString(ServerCreationBody.serializer(), body))
    }

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response.bodyAsText())
        throw Exception(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    return StoatJson.decodeFromString(ServerWithChannelObjects.serializer(), response.bodyAsText())
}

// --- Server editing ---

/**
 * Edit server properties (name, description, icon, banner, etc).
 * Requires ManageServer permission.
 */
suspend fun editServer(
    serverId: String,
    name: String? = null,
    description: String? = null,
    icon: String? = null,
    banner: String? = null,
    remove: List<String>? = null
) {
    val body = mutableMapOf<String, JsonElement>()
    if (name != null) body["name"] = StoatJson.encodeToJsonElement(String.serializer(), name)
    if (description != null) body["description"] = StoatJson.encodeToJsonElement(String.serializer(), description)
    if (icon != null) body["icon"] = StoatJson.encodeToJsonElement(String.serializer(), icon)
    if (banner != null) body["banner"] = StoatJson.encodeToJsonElement(String.serializer(), banner)
    if (remove != null) body["remove"] = StoatJson.encodeToJsonElement(ListSerializer(String.serializer()), remove)

    val response = StoatHttp.patch("/servers/$serverId".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(MapSerializer(String.serializer(), JsonElement.serializer()), body))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    // Update local cache
    val updated = StoatJson.decodeFromString(Server.serializer(), response)
    StoatAPI.serverCache[serverId]?.let { existing ->
        StoatAPI.serverCache[serverId] = existing.mergeWithPartial(updated)
    }
}

// --- Moderation: Kick, Ban, Timeout ---

/**
 * Kick a member from the server.
 * Requires KickMembers permission.
 */
suspend fun kickMember(serverId: String, userId: String) {
    StoatHttp.delete("/servers/$serverId/members/$userId".api())
    StoatAPI.members.removeMember(serverId, userId)
}

@Serializable
data class BanBody(
    val reason: String? = null
)

@Serializable
data class ServerBan(
    @SerialName("_id")
    val id: BanId? = null,
    val reason: String? = null
)

@Serializable
data class BanId(
    val server: String? = null,
    val user: String? = null
)

@Serializable
data class ServerBansResponse(
    val bans: List<ServerBan>,
    val users: List<User>
)

/**
 * Ban a member from the server.
 * Requires BanMembers permission.
 */
suspend fun banMember(serverId: String, userId: String, reason: String? = null) {
    StoatHttp.put("/servers/$serverId/bans/$userId".api()) {
        if (reason != null) {
            contentType(ContentType.Application.Json)
            setBody(StoatJson.encodeToString(BanBody.serializer(), BanBody(reason)))
        }
    }
    StoatAPI.members.removeMember(serverId, userId)
}

/**
 * Unban a user from the server.
 * Requires BanMembers permission.
 */
suspend fun unbanMember(serverId: String, userId: String) {
    StoatHttp.delete("/servers/$serverId/bans/$userId".api())
}

/**
 * List all bans for a server.
 * Requires BanMembers permission.
 */
suspend fun fetchBans(serverId: String): ServerBansResponse {
    val response = StoatHttp.get("/servers/$serverId/bans".api()).bodyAsText()
    return StoatJson.decodeFromString(ServerBansResponse.serializer(), response)
}

// --- Member editing (nickname, avatar, roles, timeout) ---

@Serializable
data class EditMemberBody(
    val nickname: String? = null,
    val avatar: String? = null,
    val roles: List<String>? = null,
    val timeout: String? = null, // ISO 8601 timestamp or null to clear
    val remove: List<String>? = null // ["Nickname", "Avatar", "Timeout"]
)

/**
 * Edit a server member's properties (nickname, avatar, roles, timeout).
 * Requires ManageNicknames/AssignRoles/TimeoutMembers depending on field.
 */
suspend fun editMember(
    serverId: String,
    userId: String,
    nickname: String? = null,
    avatar: String? = null,
    roles: List<String>? = null,
    timeout: String? = null,
    remove: List<String>? = null
): Member {
    val body = EditMemberBody(nickname, avatar, roles, timeout, remove)
    val response = StoatHttp.patch("/servers/$serverId/members/$userId".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(EditMemberBody.serializer(), body))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    val member = StoatJson.decodeFromString(Member.serializer(), response)
    StoatAPI.members.setMember(serverId, member)
    return member
}

// --- Channel creation in server ---

@Serializable
data class CreateChannelBody(
    val name: String,
    val type: String = "Text", // "Text" or "Voice"
    val description: String? = null,
    val nsfw: Boolean = false
)

/**
 * Create a new channel in a server.
 * Requires ManageChannel permission.
 */
suspend fun createChannel(
    serverId: String,
    name: String,
    type: String = "Text",
    description: String? = null,
    nsfw: Boolean = false
): Channel {
    val body = CreateChannelBody(name, type, description, nsfw)
    val response = StoatHttp.post("/servers/$serverId/channels".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(CreateChannelBody.serializer(), body))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    val channel = StoatJson.decodeFromString(Channel.serializer(), response)
    channel.id?.let { StoatAPI.channelCache[it] = channel }
    return channel
}

// --- Role management ---

@Serializable
data class CreateRoleBody(
    val name: String,
    val rank: Double? = null
)

@Serializable
data class CreateRoleResponse(
    val id: String,
    val role: Role
)

/**
 * Create a new role on a server.
 * Requires ManageRole permission.
 */
suspend fun createRole(serverId: String, name: String, rank: Double? = null): CreateRoleResponse {
    val body = CreateRoleBody(name, rank)
    val response = StoatHttp.post("/servers/$serverId/roles".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(CreateRoleBody.serializer(), body))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(CreateRoleResponse.serializer(), response)
}

@Serializable
data class EditRoleBody(
    val name: String? = null,
    val colour: String? = null,
    val hoist: Boolean? = null,
    val rank: Double? = null,
    val remove: List<String>? = null // ["Colour"]
)

/**
 * Edit a role's properties.
 * Requires ManageRole permission.
 */
suspend fun editRole(
    serverId: String,
    roleId: String,
    name: String? = null,
    colour: String? = null,
    hoist: Boolean? = null,
    rank: Double? = null,
    remove: List<String>? = null
): Role {
    val body = EditRoleBody(name, colour, hoist, rank, remove)
    val response = StoatHttp.patch("/servers/$serverId/roles/$roleId".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(EditRoleBody.serializer(), body))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(Role.serializer(), response)
}

/**
 * Delete a role from the server.
 * Requires ManageRole permission.
 */
suspend fun deleteRole(serverId: String, roleId: String) {
    StoatHttp.delete("/servers/$serverId/roles/$roleId".api())
}

/**
 * Set default role permissions for a server.
 * PUT /servers/{serverId}/permissions/default
 * Body: { "permissions": Long }
 */
suspend fun setDefaultPermissions(
    serverId: String,
    permissions: Long
) {
    @Serializable
    data class Body(val permissions: Long)

    StoatHttp.put("/servers/$serverId/permissions/default".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(permissions)))
    }
}

/**
 * Set permission overrides for a role on a server.
 * Requires ManagePermissions permission.
 */
suspend fun setServerPermissions(
    serverId: String,
    roleId: String,
    allow: Long,
    deny: Long
) {
    val body = PermissionDescription(a = allow, d = deny)
    StoatHttp.put("/servers/$serverId/permissions/$roleId".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(PermissionDescription.serializer(), body))
    }
}

/**
 * Set permission overrides for a role on a channel.
 * Requires ManagePermissions permission.
 */
suspend fun setChannelPermissions(
    channelId: String,
    roleId: String,
    allow: Long,
    deny: Long
) {
    val body = PermissionDescription(a = allow, d = deny)
    StoatHttp.put("/channels/$channelId/permissions/$roleId".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(PermissionDescription.serializer(), body))
    }
}