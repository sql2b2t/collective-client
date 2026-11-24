package me.collective.client.render;

import me.collective.client.events.EventStage;
import me.collective.client.utils.entity.SidePlayer;

import me.collective.client.ClientAccess;
import me.collective.client.events.WorldRenderEvent;
import me.collective.client.events.EventHandler;
import me.collective.client.events.ModelFeatureRendererRenderModelEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.witch.WitchModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class EntityChamsRenderer implements ClientAccess {
   public static final MeshBuffer fillMesh = MeshBuffer.create()
      .setFormat(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
   public static final MeshBuffer outlineMesh = MeshBuffer.create()
      .setFormat(Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
   private static final SubmitNodeStorage submitNodes = new SubmitNodeStorage();
   private static final FeatureRenderDispatcher featureDispatcher = new FeatureRenderDispatcher(
      submitNodes,
      ClientAccess.collectiveMinecraft.getBlockRenderer(),
      ChamsBufferSource.INSTANCE,
      ClientAccess.collectiveMinecraft.getAtlasManager(),
      NoOpOutlineBufferSource.INSTANCE,
      DiscardingBufferSource.INSTANCE,
      ClientAccess.collectiveMinecraft.font
   );
   static Color fillColor;
   static Color outlineColor;
   private static float lineWidth;
   static EntityRenderRelation relation;
   private static boolean renderingEntity;

   public static boolean isRenderingEntity() {
      return renderingEntity;
   }

   private EntityChamsRenderer() {
      throw new AssertionError();
   }

   static {
      ClientAccess.eventBus.subscribeStatic(
         EntityChamsRenderer.class
      );
   }

   public static void setLineWidth(double width) {
      lineWidth = (float)width;
   }

   static float getLineWidth() {
      if (lineWidth <= 0.0F) {
         lineWidth = 1.0F;
      }
      return lineWidth;
   }

   private static void setModelPartSkipDraw(
      ModelPart part, String partName, boolean skipDraw
   ) {
      if (!part.children.isEmpty()) {
         part.children.forEach((childName, child) -> {
            if (childName.equals(partName)) {
               child.skipDraw = skipDraw;
            }

            setModelPartSkipDraw(child, partName, skipDraw);
         });
      }
   }

   @EventHandler
   private static void onModelFeatureRendererRenderModelEvent(
      ModelFeatureRendererRenderModelEvent event
   ) {
      if (isRenderingEntity()) {
         boolean skipDraw = event.getStage() == EventStage.Pre;
         EntityModel model = event.getModel();
         if (model instanceof VillagerModel) {
            setModelPartSkipDraw(model, "hat_rim", skipDraw);
         }

         if (model instanceof WitchModel) {
            setModelPartSkipDraw(model, "hat", skipDraw);
            setModelPartSkipDraw(model, "hat2", skipDraw);
            setModelPartSkipDraw(model, "hat3", skipDraw);
            setModelPartSkipDraw(model, "hat4", skipDraw);
            setModelPartSkipDraw(model, "hat_rim", skipDraw);
         }

         if (model instanceof FrogModel) {
            setModelPartSkipDraw(model, "left_foot", skipDraw);
            setModelPartSkipDraw(model, "right_foot", skipDraw);
            setModelPartSkipDraw(model, "left_hand", skipDraw);
            setModelPartSkipDraw(model, "right_hand", skipDraw);
         }
      }
   }

   private static void setModelPartSkipDraw(
      EntityModel model, String partName, boolean skipDraw
   ) {
      setModelPartSkipDraw(model.root(), partName, skipDraw);
   }

   public static void renderEntity(PoseStack poses, Entity entity) {
      renderingEntity = true;
      try {
         CameraRenderState cameraState = ClientAccess.collectiveMinecraft.gameRenderer.getLevelRenderState().cameraRenderState;
         Vec3 cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer
            .getMainCamera()
            .position();
         float partialTick = RenderUtils.getPartialTick();
         if (entity instanceof SidePlayer) {
            partialTick = 1.0F;
         }

         double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - cameraPosition.x;
         double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - cameraPosition.y;
         double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - cameraPosition.z;
         EntityRenderer renderer = ClientAccess.collectiveMinecraft.getEntityRenderDispatcher().getRenderer(entity);
         EntityRenderState renderState = renderer.createRenderState(entity, partialTick);
         prepareRenderState(renderState);
         Vec3 renderOffset = renderer.getRenderOffset(renderState);
         double renderX = x + renderOffset.x();
         double renderY = y + renderOffset.y();
         double renderZ = z + renderOffset.z();
         poses.pushPose();
         try {
            poses.translate(renderX, renderY, renderZ);
            renderer.submit(renderState, poses, submitNodes, cameraState);
         } finally {
            poses.popPose();
         }
         featureDispatcher.renderAllFeatures();
         submitNodes.endFrame();
      } finally {
         renderingEntity = false;
      }
   }

   public static void drawMeshes(RenderType fillType, RenderType outlineType) {
      fillMesh.draw(
         fillType
      );
      outlineMesh.draw(
         outlineType
      );
   }

   public static void setColors(Color outlineColor, Color fillColor) {
      EntityChamsRenderer.fillColor = fillColor;
      EntityChamsRenderer.outlineColor = outlineColor;
   }

   static boolean hasVisibleOutline() {
      return outlineColor != null && outlineColor.getAlpha() > 0;
   }

   static boolean hasVisibleFill() {
      return fillColor != null && fillColor.getAlpha() > 0;
   }

   @EventHandler(priority = -9999)
   public static void onWorldRender(WorldRenderEvent.Main event) {
      flush();
   }

   private static void prepareRenderState(EntityRenderState renderState) {
      renderState.displayFireAnimation = false;
      renderState.nameTag = null;
      renderState.leashStates = null;
      if (renderState instanceof HumanoidRenderState humanoidState) {
         humanoidState.headEquipment = ItemStack.EMPTY;
         humanoidState.chestEquipment = ItemStack.EMPTY;
         humanoidState.legsEquipment = ItemStack.EMPTY;
         humanoidState.feetEquipment = ItemStack.EMPTY;
      }

      if (renderState instanceof AvatarRenderState avatarState) {
         boolean showOuterLayers = !(
            EntityRenderStateAccess.getEntity(avatarState) instanceof ForcedEntityLayers forcedLayers
               && forcedLayers.collective$areLayersForced()
         );
         avatarState.showCape = false;
         avatarState.showHat = showOuterLayers;
         avatarState.showJacket = showOuterLayers;
         avatarState.showLeftPants = showOuterLayers;
         avatarState.showRightPants = showOuterLayers;
         avatarState.showLeftSleeve = showOuterLayers;
         avatarState.showRightSleeve = showOuterLayers;
         avatarState.arrowCount = 0;
      }
   }

   public static void setRelation(EntityRenderRelation relation) {
      EntityChamsRenderer.relation = relation;
   }

   public static void flush() {
      drawMeshes(
         ClientRenderTypes.worldFill(),
         ClientRenderTypes.worldLines()
      );
   }

}
