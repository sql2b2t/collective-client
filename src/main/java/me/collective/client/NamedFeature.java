package me.collective.client;

import me.collective.client.settings.NamedEntry;

import java.util.Objects;

public class NamedFeature implements NamedEntry {
   public final String name;
   private String description = "";

   @Override
   public String getName() {
      return this.name;
   }

   public NamedFeature(String name) {
      this.name = name;
   }

   @Override
   public boolean equals(Object other) {
      if (this == other) {
         return true;
      } else if (other != null && this.getClass() == other.getClass()) {
         NamedFeature feature = (NamedFeature)other;
         return Objects.equals(this.name, feature.name);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.name);
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public String getDescription() {
      return this.description;
   }

   public boolean isDescriptionPresent() {
      return !Objects.equals(this.description, "");
   }
}
