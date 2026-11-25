package me.collective.mixin;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoolArgumentType.class)
public class BoolArgumentTypeMixin {

   @Inject(method = "parse(Lcom/mojang/brigadier/StringReader;)Ljava/lang/Boolean;", at = @At("HEAD"), remap = false, cancellable = true)
   private void parseExtendedBoolean(
      StringReader reader, CallbackInfoReturnable<Boolean> callback
   ) throws CommandSyntaxException {
      String input = reader.readString();
      if (input.equals("0") || input.equalsIgnoreCase("false")) {
         callback.setReturnValue(false);
      } else if (input.equals("1") || input.equalsIgnoreCase("true")) {
         callback.setReturnValue(true);
      } else {
         throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidBool().createWithContext(reader, input);
      }
   }
}
