package com.geoiq.lk_vision_demo.data

/**
 * Wire protocol constants for the GeoIQ vision/chat agent.
 *
 * Mirrors the subset of the Lenskart consumer app's `GeoCallbackType` that this sample
 * exercises, so both clients speak the same protocol. The backend matches these strings
 * exactly — keep them in sync.
 */
object GeoProtocol {

    // ── Topics ────────────────────────────────────────────────────────────────
    /** Agent → client. Transcriptions, widget payloads, session events. */
    const val TOPIC_PUBLISH = "lk_va_publish"

    /** Client → agent data channel. */
    const val TOPIC_LISTEN = "lk_va_listen"

    const val TOPIC_FILE = "send-file"

    // ── Outbound RPC methods (client → agent) ─────────────────────────────────
    const val TE_SEND_MESSAGE = "te_send_message"
    const val TE_EXIT_CHAT = "te_exit_chat"
    const val TE_HANDOVER_VOICE = "te_handover_voice"
    const val TE_HANDOVER_TEXT = "te_handover_text"
    const val TE_HANDOVER_VOICE_CONFIRM = "te_handover_voice_confirm"
    const val TE_HANDOVER_TEXT_CONFIRM = "te_handover_text_confirm"

    // ── Outbound data-channel events, published on [TOPIC_LISTEN] ────────────
    /** Chat → voice: confirmation sheet opened. */
    const val BA_HANDOVER_SHEET = "ba_handover_sheet"

    /** Chat → voice: switch confirmed. */
    const val BA_HANDOVER_VOICE = "ba_handover_voice"

    /** Voice → chat: confirmation sheet opened. */
    const val VAP_HANDOVER_SHEET = "vap_handover_sheet"

    /** Voice → chat: switch confirmed. */
    const val VAP_HANDOVER_ASSIST = "vap_handover_assist"

    /** Published on [TOPIC_PUBLISH] once per room to request the session snapshot. */
    const val VAL_GET_SESSION_INFO = "val_get_session_info"

    // ── Client-registered RPC methods (agent → client) ────────────────────────
    const val FE_RPC_ROOM_DISCONNECTION = "fe_rpc_room_disconnection"

    // ── Inbound event names ──────────────────────────────────────────────────
    /** Backend ended the agent session (e.g. inactivity). Triggers a token refresh. */
    const val BA_SESSION_END = "ba_session_end"
    const val BA_CHAT_HISTORY = "ba_chat_history"

    // ── Session source events (the token request's `event` field) ─────────────
    const val VAEP_PDP_CHAT = "vaep_pdp_chat"
    const val VAEP_LENS_PACKAGE = "vaep_addon_lens_package_page"
    const val HANDOVER_FROM_VOICE = "handover_from_voice"
    const val HANDOVER_FROM_ASSIST = "handover_from_assist"

    // ── Participant attributes ───────────────────────────────────────────────
    const val ATTR_AGENT_STATE = "lk.agent.state"
    const val AGENT_STATE_LISTENING = "listening"
    const val AGENT_STATE_THINKING = "thinking"

    // ── JSON keys ────────────────────────────────────────────────────────────
    const val KEY_EVENT = "event"
    const val KEY_VA_DATA = "va_data"
    const val KEY_JUNO = "juno"
    const val KEY_RESULT = "result"
    const val KEY_METADATA = "metadata"
    const val KEY_AGENT_TYPE = "agent_type"
    const val KEY_TEXT = "text"
    const val KEY_MESSAGE = "message"
    const val KEY_LABEL = "label"
    const val KEY_TITLE = "title"
    const val KEY_REASON = "reason"
    const val KEY_LOCALE_CONFIG = "locale_config"
    const val KEY_CODE = "code"
    const val KEY_IS_DEFAULT = "is_default"
    const val KEY_SELECTED_LANGUAGE = "selected_language"
    const val KEY_DEVICE_LANGUAGE = "deviceLanguage"
    const val KEY_AI_CONTEXT = "aiContext"
    const val KEY_ACCESS_TOKEN = "accessToken"
    const val KEY_ROOM_NAME = "room_name"
    const val KEY_IDENTITY = "identity"
}

/**
 * Which backend agent a session should be routed to.
 *
 * Sent as `agent_type` on the token request. Without it the backend falls back to the
 * voice agent, so a chat session would silently get voice behaviour.
 */
enum class AgentType(val wireValue: String) {
    VOICE_ASSIST("b_voice_assist"),
    CHAT_ASSIST("b_chat_assist"),
}
