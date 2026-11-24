package me.collective.client.events

enum class EventStage {
   Pre,
   Post;

   fun isPre(): Boolean = this == Pre

   fun isPost(): Boolean = this == Post
}
