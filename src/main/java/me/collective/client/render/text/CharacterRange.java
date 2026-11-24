package me.collective.client.render.text;

import me.collective.client.utils.inventory.CharacterConsumer;

import java.util.Collection;

public interface CharacterRange {
   default void addTo(Collection characters) {
      this.forEachCharacter(characters::add);
   }

   void forEachCharacter(CharacterConsumer consumer);
}
