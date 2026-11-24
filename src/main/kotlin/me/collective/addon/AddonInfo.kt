package me.collective.addon

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class AddonInfo(
    val name: String,
    val version: String = "1.0.0",
    val author: String = "Collective Developer",
    val description: String = "",
    val website: String = ""
)
