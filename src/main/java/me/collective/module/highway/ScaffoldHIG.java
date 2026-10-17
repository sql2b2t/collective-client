package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.setting.StringSetting;
import me.collective.util.InventoryUtil;
import me.collective.util.FindItemResult;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

/**
 * Порт ScaffoldHIG: строит блоки под собой на ходу.
 * Список блоков — через запятую (whitelist) или запрет (blacklist).
 */
public class ScaffoldHIG extends Module {
    public enum ListMode {
        Whitelist,
        Blacklist
    }

    public final EnumSetting<ListMode> listMode = add(new EnumSetting<>("ListMode", ListMode.Whitelist));
    public final StringSetting blocks = add(new StringSetting("Blocks", "netherrack"));
    public final NumberSetting extend = add(new NumberSetting("Extend", 1, 0, 5, 1));
    public final BooleanSetting keepY = add(new BooleanSetting("KeepY", false));
    public final NumberSetting height = add(new NumberSetting("Height", 119, -64, 320, 1));

    private boolean worked = false;

    public ScaffoldHIG() {
        super("ScaffoldHIG", "Scaffolds blocks under you.", Category.HIGHWAY);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        float f = MathHelper.sin(mc.player.getYaw() * 0.017453292f);
        float g = MathHelper.cos(mc.player.getYaw() * 0.017453292f);
        Vec3d playerPos = mc.player.getEntityPos();
        boolean standing = mc.player.getVelocity().x == 0.0 && mc.player.getVelocity().z == 0.0;

        Set<Block> filter = parseBlocks();
        for (int i = 0; i <= (standing ? 0 : extend.getIntValue()); i++) {
            double px = playerPos.x - f * i;
            double py = keepY.getValue() ? height.getValue() - 1.0 : playerPos.y - 0.5;
            double pz = playerPos.z + g * i;
            BlockPos bPos = new BlockPos(
                MathHelper.floor(px), MathHelper.floor(py), MathHelper.floor(pz));

            if (!mc.world.getBlockState(bPos).isReplaceable()) {
                worked = false;
                continue;
            }
            worked = true;

            FindItemResult item;
            if (listMode.getValue() == ListMode.Whitelist) {
                item = InventoryUtil.findInHotbar(stack ->
                    stack.getItem() instanceof BlockItem && filter.contains(Block.getBlockFromItem(stack.getItem())));
            } else {
                item = InventoryUtil.findInHotbar(stack ->
                    stack.getItem() instanceof BlockItem && !filter.contains(Block.getBlockFromItem(stack.getItem())));
            }
            if (!item.found()) {
                return;
            }
            InventoryUtil.swap(item.slot(), true);

            Vec3d hitVec = new Vec3d(px, py, pz);
            Direction side = Direction.getFacing(hitVec.x, hitVec.y, hitVec.z).getOpposite();
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(hitVec, side, bPos, true));

            InventoryUtil.swapBack();
        }
    }

    private Set<Block> parseBlocks() {
        Set<Block> out = new HashSet<>();
        for (String raw : blocks.getValue().split(",")) {
            String id = raw.strip().toLowerCase();
            if (id.isEmpty()) continue;
            if (!id.contains(":")) id = "minecraft:" + id;
            Identifier ident = Identifier.tryParse(id);
            if (ident == null) continue;
            if (Registries.BLOCK.containsId(ident)) out.add(Registries.BLOCK.get(ident));
        }
        if (out.isEmpty()) {
            Block fallback = Registries.BLOCK.get(Identifier.of("minecraft", "netherrack"));
            if (fallback != null) out.add(fallback);
        }
        return out;
    }

    public boolean hasWorked() {
        return worked;
    }
}
