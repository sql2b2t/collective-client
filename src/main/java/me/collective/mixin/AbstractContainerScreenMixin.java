package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.events.AbstractContainerScreenEvent;
import me.collective.client.events.AbstractContainerScreenRenderTooltipEvent;
import me.collective.client.modules.misc.ChestSearchBarModule;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.utils.inventory.ContainerTooltipComponent;
import me.collective.client.access.TooltipComponentCapture;
import me.collective.client.utils.KeyUtils;
import me.collective.client.utils.inventory.ChestSearchController;
import me.collective.client.modules.player.InventoryTweaksModule;
import me.collective.client.modules.render.TooltipsModule;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin implements ClientAccess {
   private static ChestSearchBarModule collective$chestSearchBar;
   private static TooltipsModule collective$tooltips;
   private static InventoryTweaksModule collective$inventoryTweaks;
   private static ChestSearchBarModule collective$chestSearchBar() {
      if (collective$chestSearchBar == null) {
         collective$chestSearchBar = ClientContext.getModule(ChestSearchBarModule.class);
      }
      return collective$chestSearchBar;
   }
   private static TooltipsModule collective$tooltips() {
      if (collective$tooltips == null) {
         collective$tooltips = ClientContext.getModule(TooltipsModule.class);
      }
      return collective$tooltips;
   }
   private static InventoryTweaksModule collective$inventoryTweaks() {
      if (collective$inventoryTweaks == null) {
         collective$inventoryTweaks = ClientContext.getModule(InventoryTweaksModule.class);
      }
      return collective$inventoryTweaks;
   }
   @Shadow
   @Nullable
   protected Slot hoveredSlot;
   @Unique
   private int collective$mouseX;
   @Unique
   private int collective$mouseY;
   @Unique
   private int collective$tooltipX;
   @Unique
   private int collective$tooltipY;
   @Unique
   private Slot collective$cachedHoveredSlot;
   @Unique
   private MapItemSavedData collective$slotMapData;
   @Unique
   private int collective$slotMapId;
   @Unique
   private final AbstractContainerScreenEvent collective$containerScreenEvent = new AbstractContainerScreenEvent(
      (AbstractContainerScreen)(Object)this
   );

   @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
   public void collective$preventInventoryCloseDuringSearch(
      KeyEvent keyEvent, CallbackInfoReturnable<Boolean> callback
   ) {
      if (collective$chestSearchBar() != null
         && collective$chestSearchBar().isToggled()
         && ChestSearchController.searchBox != null
         && ChestSearchController.searchBox.isFocused()
         && ClientAccess.collectiveMinecraft.options.keyInventory.matches(keyEvent)) {
         callback.setReturnValue(true);
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void collective$runContainerTickCallback(CallbackInfo callback) {
      this.collective$containerScreenEvent
         .runTickCallback();
   }

   @Inject(method = "renderTooltip", at = @At("HEAD"))
   private void collective$postContainerScreenEvent(
      GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo callback
   ) {
      this.collective$containerScreenEvent
         .setGraphics(graphics);
      this.collective$containerScreenEvent
         .setMouseX(mouseX);
      this.collective$containerScreenEvent
         .setMouseY(mouseY);
      ClientAccess.eventBus.post(
         this.collective$containerScreenEvent
      );
   }

   @Inject(method = "renderContents", at = @At("HEAD"))
   private void collective$captureHoveredSlotAndMouse(
      GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback
   ) {
      if (!KeyUtils.isPhysicalKeyDown(342)) {
         this.collective$cachedHoveredSlot = this.hoveredSlot;
      }

      this.collective$mouseX = mouseX;
      this.collective$mouseY = mouseY;
   }

   @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
   private void collective$useInspectedHoveredSlot(
      double mouseX, double mouseY, CallbackInfoReturnable<Slot> callback
   ) {
      if (this.collective$cachedHoveredSlot != null
         && collective$tooltips().shouldInspectContents(
            this.collective$cachedHoveredSlot.getItem()
         )) {
         callback.setReturnValue(this.collective$cachedHoveredSlot);
      }
   }

   @ModifyExpressionValue(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;hasClickedOutside(DDII)Z"))
   public boolean collective$keepInspectedClickInside(boolean originalInside) {
      return (this.collective$cachedHoveredSlot == null
         || !collective$tooltips().shouldInspectContents(this.collective$cachedHoveredSlot.getItem()))
         && originalInside;
   }

   @ModifyArgs(
      method = "mouseClicked",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V", ordinal = 1)
   )
   private void collective$useBundleInspectButton(Args arguments) {
      int mouseButton = arguments.<Integer>get(2);
      if (this.collective$cachedHoveredSlot != null
         && collective$tooltips().shouldInspectContents(
            this.collective$cachedHoveredSlot.getItem()
         )
         && mouseButton == 0
         && this.collective$cachedHoveredSlot
            .getItem()
            .is(ItemTags.BUNDLES)) {
         arguments.set(2, 1);
      }
   }

   @ModifyArgs(
      method = "mouseReleased",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V", ordinal = 9)
   )
   private void collective$swapBundleReleaseButton(Args arguments) {
      Slot slot = arguments.get(0);
      int mouseButton = arguments.<Integer>get(2);
      if (collective$inventoryTweaks().isToggled()
         && collective$inventoryTweaks().shouldSwapBundleClickButton(
            slot, mouseButton
         )) {
         arguments.set(
            2,
            mouseButton == 0 ? 1 : 0
         );
      }
   }

   @WrapWithCondition(
      method = "renderTooltip",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphics;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V"
      )
   )
   private boolean collective$renderTooltipWithEvent(
      GuiGraphics graphics, Font font, List text, Optional image, int tooltipX, int tooltipY, Identifier background
   ) {
      if (this.collective$cachedHoveredSlot != null
         && collective$tooltips().shouldInspectContents(
            this.collective$cachedHoveredSlot.getItem()
         )) {
         tooltipX = this.collective$tooltipX;
         tooltipY = this.collective$tooltipY;
      } else {
         this.collective$tooltipX = tooltipX;
         this.collective$tooltipY = tooltipY;
      }

      List components = TooltipComponentCapture.captureComponents(graphics, text, image);
      AbstractContainerScreenRenderTooltipEvent event = new AbstractContainerScreenRenderTooltipEvent(
         (Screen)(Object)this,
         components,
         tooltipX,
         tooltipY,
         this.collective$mouseX,
         this.collective$mouseY
      );
      ClientAccess.eventBus
         .post(event);
      if (!event.isCancelled()) {
         ((GuiGraphicsAccessor)graphics)
            .drawTooltipsHook(
               font,
               event.getTooltipComponents(),
               tooltipX,
               tooltipY,
               DefaultTooltipPositioner.INSTANCE,
               null
            );
      }

      return false;
   }

   @Inject(method = "renderSlot", at = @At("HEAD"))
   private void collective$captureSlotMapData(
      GuiGraphics graphics, Slot slot, int mouseX, int mouseY, CallbackInfo callback
   ) {
      this.collective$slotMapData = null;
      ItemStack stack = slot.getItem();
      if (stack.is(Items.FILLED_MAP)) {
         int mapId = stack.getOrDefault(DataComponents.MAP_ID, new MapId(-1)).id();
         if (mapId != -1) {
            this.collective$slotMapId = mapId;
            this.collective$slotMapData = ClientContext.mapDataCache
               .getMapData(stack, mapId);
         }
      }
   }

   @WrapWithCondition(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"))
   private boolean collective$shouldRenderSlotItem(
      GuiGraphics graphics, ItemStack stack, int itemX, int itemY, int seed
   ) {
      return !collective$tooltips().isToggled()
         || !collective$tooltips().mapOverlaySetting.getValue()
         || this.collective$slotMapData == null;
   }

   @WrapWithCondition(
      method = "renderSlot",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V")
   )
   private boolean collective$renderMapOverlay(
      GuiGraphics graphics, Font font, ItemStack stack, int itemX, int itemY, String countText, @Local(argsOnly = true) Slot slot
   ) {
      if (!collective$tooltips().isToggled() || !collective$tooltips().mapOverlaySetting.getValue() || this.collective$slotMapData == null) {
         return true;
      }
      ContainerTooltipComponent.renderMapOverlay(
         graphics,
         this.collective$slotMapData,
         this.collective$slotMapId,
         itemX,
         itemY
      );
      if (!KeyUtils.isPhysicalKeyDown(342)) {
         graphics.renderItemDecorations(font, stack, itemX, itemY);
      }

      return false;
   }
}
