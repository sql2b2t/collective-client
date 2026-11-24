package me.collective.client.audio

import me.collective.client.utils.collections.NamedIdentifier

enum class BuiltinSound(soundName: String) {
    NONE("none"),
    KABAN("kaban"),
    NEVERLOSE("neverlose"),
    CSS("css"),
    COD("cod"),
    QUAKE("quake"),
    TOOLBOX("toolbox"),
    WARNING("warning"),
    STEAM("steam"),
    WHATSAPP("whatsapp"),
    VK("vk"),
    ICQ("icq"),
    STALKER("stalker"),
    HOVER("hover"),
    CLICK("click"),
    RCLICK("rclick"),
    BODYSPLAT("bodysplat"),
    BLIP("blip"),
    BLIP2("blip2"),
    HL2("hl2"),
    PICKUP("pickup"),
    PICKUP2("pickup2"),
    HORROR("horror");

    val identifier: NamedIdentifier = if (soundName == "none") {
        NamedIdentifier.NONE
    } else {
        NamedIdentifier(soundName)
    }
}
