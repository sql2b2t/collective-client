package me.collective.mixin;

import me.collective.module.ModuleManager;
import me.collective.module.render.Tooltips;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Optional;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinHandledScreenTooltips {

    private static final int BORDER = 1;
    private static final int PADDING = 3;

    @Shadow
    protected Slot focusedSlot;

    @Redirect(
        method = "extractTooltip",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;Z)V")
    )
    private void collective$tooltips(GuiGraphicsExtractor context, Font font, List<Component> lines,
                                     Optional<TooltipComponent> data, int mouseX, int mouseY,
                                     Identifier background, boolean hasItemInHand) {
        Tooltips module = ModuleManager.INSTANCE.getModule(Tooltips.class);
        if (module == null || !module.isActive()) {
            context.setTooltipForNextFrame(font, lines, data, mouseX, mouseY, background, hasItemInHand);
            return;
        }

        ItemStack stack = focusedSlot != null ? focusedSlot.getItem() : ItemStack.EMPTY;

        List<ClientTooltipComponent> components =
            module.buildComponents(lines, data.orElse(null), stack, mouseX, mouseY);

        Integer border = module.borderColor();

        if (border == null) {
            context.tooltip(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, background, false);
            return;
        }

        int width = contentWidth(font, components);
        int height = contentHeight(font, components);

        var position = DefaultTooltipPositioner.INSTANCE.positionTooltip(
            context.guiWidth(), context.guiHeight(),
            mouseX, mouseY, width, height);

        int x = position.x() - PADDING;
        int y = position.y() - PADDING;
        int frameWidth = width + PADDING * 2;
        int frameHeight = height + PADDING * 2;

        context.fill(x, y, x + frameWidth, y + frameHeight, border);

        context.tooltip(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, background, false);
    }

    private static int contentWidth(Font font, List<ClientTooltipComponent> components) {
        int width = 0;
        for (ClientTooltipComponent component : components) {
            width = Math.max(width, component.getWidth(font));
        }
        return width + BORDER * 2;
    }

    private static int contentHeight(Font font, List<ClientTooltipComponent> components) {
        int height = 0;
        for (ClientTooltipComponent component : components) {
            height += component.getHeight(font);
        }
        return height + BORDER * 2;
    }
}