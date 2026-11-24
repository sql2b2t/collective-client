package me.collective.client.render.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ReverseStringFormatter {
   private static final char ARGUMENT_PLACEHOLDER = '\u0001';
   private final List<String> arguments = new ArrayList<>();

   private void prependString(String value) {
      this.arguments.add(0, value);
   }

   public ReverseStringFormatter prepend(boolean value) {
      this.prependString(String.valueOf(value));
      return this;
   }

   public ReverseStringFormatter prepend(float value) {
      this.prependString(String.valueOf(value));
      return this;
   }

   public ReverseStringFormatter prepend(char value) {
      this.prependString(String.valueOf(value));
      return this;
   }

   public ReverseStringFormatter prepend(short value) {
      this.prependString(String.valueOf(value));
      return this;
   }

   public ReverseStringFormatter prepend(int value) {
      this.prependString(String.valueOf(value));
      return this;
   }

   public ReverseStringFormatter prepend(long value) {
      this.prependString(String.valueOf(value));
      return this;
   }

   public ReverseStringFormatter prepend(Object value) {
      this.prependString(String.valueOf(value));
      return this;
   }

   public String format(String template) {
      int argumentIndex = 0;
      while (true) {
         int placeholder = template.indexOf(ARGUMENT_PLACEHOLDER);
         if (placeholder < 0 || argumentIndex == this.arguments.size()) {
            return template;
         }

         String argument = Objects.requireNonNull(this.arguments.get(argumentIndex));

         template = new StringBuilder(template)
            .replace(placeholder, placeholder + 1, argument)
            .toString();
         argumentIndex++;
      }
   }
}
