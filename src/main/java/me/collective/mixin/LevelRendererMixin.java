package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.modules.render.AmbienceModule;
import me.collective.client.modules.render.ChamsModule;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.render.ShaderPassRenderState;
import me.collective.client.render.LevelRendererFramebufferAccess;
import me.collective.client.managers.RotationManager;
import me.collective.client.render.ShaderPass;
import me.collective.client.managers.RenderTargetRegistry;
import me.collective.client.render.RenderUtils;
import me.collective.client.render.IrisShaderBridge;
import me.collective.client.events.WorldRenderEvent;
import me.collective.client.managers.RenderStateManager;
import me.collective.client.modules.player.FreecamModule;
import me.collective.client.events.BlockOutlineRenderEvent;
import me.collective.client.modules.render.NoRenderModule;
import me.collective.client.modules.render.SkyboxModule;
import me.collective.client.modules.render.ViewClipModule;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.awt.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin implements ClientAccess, LevelRendererFramebufferAccess {
   private static ViewClipModule viewClip;
   private static AmbienceModule ambienceModule;
   private static NoRenderModule noRenderModule;
   private static FreecamModule freecam;
   private static SkyboxModule skyboxModule;
   private static ChamsModule chamsModule;
   private static ViewClipModule viewClip() {
      if (viewClip == null) {
         viewClip = ClientContext.getModule(ViewClipModule.class);
      }
      return viewClip;
   }
   private static AmbienceModule ambienceModule() {
      if (ambienceModule == null) {
         ambienceModule = ClientContext.getModule(AmbienceModule.class);
      }
      return ambienceModule;
   }
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   private static FreecamModule freecam() {
      if (freecam == null) {
         freecam = ClientContext.getModule(FreecamModule.class);
      }
      return freecam;
   }
   private static SkyboxModule skyboxModule() {
      if (skyboxModule == null) {
         skyboxModule = ClientContext.getModule(SkyboxModule.class);
      }
      return skyboxModule;
   }
   private static ChamsModule chamsModule() {
      if (chamsModule == null) {
         chamsModule = ClientContext.getModule(ChamsModule.class);
      }
      return chamsModule;
   }
   @Unique
   private PoseStack worldPoseStack;
   @Unique
   private boolean savedHideGui;
   @Unique
   private boolean hasSavedHideGui;
   @Unique
   private boolean blockOutlineEventPosted;
   @Unique
   private boolean blockOutlineCancelled;
   @Shadow
   @Nullable
   private ClientLevel level;
   @Shadow
   @Nullable
   private RenderTarget entityOutlineTarget;
   @Shadow
   @Final
   private LevelTargetBundle targets;
   @Shadow
   private Frustum cullingFrustum;
   @Unique
   private Stack<RenderTarget> entityOutlineTargetStack;
   @Unique
   private Stack<ResourceHandle<RenderTarget>> entityOutlineHandleStack;

   // Mojang leaves the weather render pass lambda unnamed.
   @WrapWithCondition(
      method = "method_62216(Lnet/minecraft/client/renderer/FogParameters;FLnet/minecraft/world/phys/Vec3;IF)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/world/level/Level;Lnet/minecraft/client/renderer/MultiBufferSource;IFLnet/minecraft/world/phys/Vec3;)V"
      )
   )
   private boolean shouldRenderWeather(
      WeatherEffectRenderer weatherRenderer, Level level, MultiBufferSource buffers, int ticks, float partialTick, Vec3 cameraPosition
   ) {
      return !ambienceModule().isToggled()
         || !ambienceModule().worldWeatherSetting.getValue()
         || ambienceModule().weatherSetting.getValue()
            != AmbienceModule.WeatherChoice.CLEAR;
   }

   @Inject(method = "renderLevel", at = @At("RETURN"))
   private void renderWorldEvents(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      Camera camera,
      GameRenderer gameRenderer,
      Matrix4f inputMatrix1,
      Matrix4f inputMatrix2,
      CallbackInfo callback
   ) {
      PoseStack poses = new PoseStack();
      float partialTick = RenderUtils.getPartialTick();
      FogParameters previousFog = RenderSystem.getShaderFog();
      RenderSystem.setShaderFog(FogParameters.NO_FOG);
      poses.pushPose();
      try {
         RenderUtils.applyCameraRotation(poses);
         WorldRenderEvent.BeforeDepthClear beforeDepthEvent = WorldRenderEvent.BeforeDepthClear.create(
            poses, partialTick
         );
         ClientAccess.eventBus.post(
            beforeDepthEvent
         );
         RenderUtils.clearDepth(
            ClientAccess.collectiveMinecraft.getMainRenderTarget()
         );
         RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
         WorldRenderEvent.Main mainEvent = WorldRenderEvent.Main.create(
            poses, partialTick
         );
         ClientAccess.eventBus.post(
            mainEvent
         );
         RenderUtils.clearDepth(
            ClientAccess.collectiveMinecraft.getMainRenderTarget()
         );
         WorldRenderEvent.Overlay overlayEvent = WorldRenderEvent.Overlay.create(
            poses, partialTick
         );
         ClientAccess.eventBus.post(
            overlayEvent
         );
      } finally {
         poses.popPose();
         RenderSystem.setShaderFog(previousFog);
      }
   }

   // Mojang leaves the world render pass lambda unnamed.
   @Inject(
      method = "method_62214(Lnet/minecraft/client/renderer/FogParameters;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;Lnet/minecraft/util/profiling/ProfilerFiller;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;ZLnet/minecraft/client/renderer/culling/Frustum;Lcom/mojang/blaze3d/resource/ResourceHandle;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBuffers;bufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;", shift = Shift.BEFORE)
   )
   private void postBeforeBufferSourceEvent(
      FogParameters fogParameters,
      DeltaTracker deltaTracker,
      Camera camera,
      ProfilerFiller profiler,
      Matrix4f frustumMatrix,
      Matrix4f projectionMatrix,
      ResourceHandle mainTarget,
      ResourceHandle translucentTarget,
      ResourceHandle itemEntityTarget,
      ResourceHandle particlesTarget,
      boolean renderOutline,
      Frustum frustum,
      ResourceHandle weatherTarget,
      CallbackInfo callback,
      @Local PoseStack poses
   ) {
      WorldRenderEvent.BeforeBufferSource event = WorldRenderEvent.BeforeBufferSource.create(
         poses, RenderUtils.getPartialTick()
      );
      ClientAccess.eventBus.post(
         event
      );
   }

   @Inject(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/FogParameters;ZZLnet/minecraft/client/DeltaTracker;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
         shift = Shift.BEFORE
      )
   )
   private void addSkyShaderPass(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      Camera camera,
      GameRenderer gameRenderer,
      Matrix4f inputMatrix1,
      Matrix4f inputMatrix2,
      CallbackInfo callback,
      @Local FrameGraphBuilder frameGraph
   ) {
      if (skyboxModule().isToggled()
         && skyboxModule().shaderSetting.getValue()) {
         FramePass skyFramePass = frameGraph.addPass("sky_collective");
         this.targets.main = skyFramePass.readsAndWrites(this.targets.main);
         skyFramePass.executes(
            () -> {
               ShaderPass skyShader = ClientContext.shaderRegistry
                  .getPass("sky");
               ShaderPassRenderState.INSTANCE
                  .configure(skyShader);
               RenderStateManager.addStates(
                  ShaderPassRenderState.INSTANCE
               );
               skyboxModule().drawShaderQuad();
               RenderStateManager.reset();
               RenderUtils.INSTANCE
                  .fillRect(
                     this.worldPoseStack, 0.0F, 0.0F, 1.0F, 1.0F, 0
                  );
            }
         );
      }
   }

   // Mojang leaves the world render pass lambda unnamed.
   @WrapWithCondition(
      method = "method_62214(Lnet/minecraft/client/renderer/FogParameters;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;Lnet/minecraft/util/profiling/ProfilerFiller;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;ZLnet/minecraft/client/renderer/culling/Frustum;Lcom/mojang/blaze3d/resource/ResourceHandle;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;renderBlockOutline(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;Z)V"
      )
   )
   private boolean shouldRenderBlockOutline(
      LevelRenderer renderer, Camera camera, BufferSource bufferSource, PoseStack poseStack, boolean translucent
   ) {
      if (!(ClientAccess.collectiveMinecraft.hitResult instanceof BlockHitResult)) {
         return true;
      }
      if (ClientAccess.collectiveMinecraft.level == null
         || ClientAccess.collectiveMinecraft.hitResult.getType() != HitResult.Type.BLOCK) {
         return true;
      }
      if (this.blockOutlineEventPosted) {
         // renderBlockOutline is called twice per frame (translucent halves);
         // post the event once and reuse its outcome for the second call.
         return !this.blockOutlineCancelled;
      }

      BlockPos blockPos = ((BlockHitResult)ClientAccess.collectiveMinecraft.hitResult)
         .getBlockPos();
      BlockOutlineRenderEvent event = new BlockOutlineRenderEvent(
         poseStack, null, blockPos, ClientAccess.collectiveMinecraft.level.getBlockState(blockPos)
      );
      ClientAccess.eventBus.post(
         event
      );
      this.blockOutlineEventPosted = true;
      this.blockOutlineCancelled = event.isCancelled();
      return !event.isCancelled();
   }

   @Inject(method = "doesMobEffectBlockSky", at = @At("HEAD"), cancellable = true)
   private void suppressSkyBlindness(
      Camera camera, CallbackInfoReturnable<Boolean> callback
   ) {
      if (noRenderModule().isToggled()
         && noRenderModule().blindnessSetting.getValue()) {
         callback.setReturnValue(false);
      }
   }

   @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"))
   private boolean applyCameraSpectatorOverride(boolean originalSpectator) {
      return freecam().isToggled()
         || (viewClip().isToggled() && ClientAccess.collectiveMinecraft.gameRenderer.getMainCamera().isDetached())
         || originalSpectator;
   }

   @Inject(method = "renderLevel", at = @At("HEAD"))
   private void captureWorldRenderState(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      Camera camera,
      GameRenderer gameRenderer,
      Matrix4f inputMatrix1,
      Matrix4f inputMatrix2,
      CallbackInfo callback
   ) {
      RenderUtils.setPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
      this.worldPoseStack = new PoseStack();
      this.blockOutlineEventPosted = false;
      this.blockOutlineCancelled = false;
   }

   // Mojang leaves the background color render pass lambda unnamed.
   @ModifyArgs(
      method = "method_62218(Lorg/joml/Vector4f;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V")
   )
   private static void applySkyboxBackgroundColor(Args arguments) {
      if (skyboxModule().isToggled()
         && skyboxModule().isCurrentDimensionEnabled()) {
         Color color = skyboxModule().backgroundSetting.getValue();
         arguments.set(0, color.getRed() / 255.0F);
         arguments.set(1, color.getGreen() / 255.0F);
         arguments.set(2, color.getBlue() / 255.0F);
      }
   }

   @WrapWithCondition(
      method = "renderEntities",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"
      )
   )
   private boolean shouldSubmitVisibleEntity(
      LevelRenderer renderer, Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poses, MultiBufferSource bufferSource
   ) {
      return (
            !noRenderModule().entitiesSetting.getValue()
               || entity == null
               || !noRenderModule().shouldHideEntity(entity)
         )
         && (entity == null || !chamsModule().shouldRenderThroughWalls(
            entity
         ));
   }

   @Inject(method = "resize", at = @At("HEAD"))
   private void resizeClientRenderTargets(int width, int height, CallbackInfo callback) {
      RenderTargetRegistry.resizeRenderTargets(width, height);
   }

   @ModifyExpressionValue(method = "collectVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
   private boolean applyFreecamDetachedCamera(boolean originalDetached) {
      return freecam().isToggled() || originalDetached;
   }

   @Inject(method = "addWeatherPass", at = @At("HEAD"))
   private void beginWeatherPassColor(
      FrameGraphBuilder frameGraph, Vec3 cameraPosition, float partialTick, FogParameters fogParameters, CallbackInfo callback
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 0.25F);
   }

   @Inject(method = "addWeatherPass", at = @At("TAIL"))
   private void resetWeatherPassColor(
      FrameGraphBuilder frameGraph, Vec3 cameraPosition, float partialTick, FogParameters fogParameters, CallbackInfo callback
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   @ModifyExpressionValue(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F")
   )
   private float applyCloudHeight(float originalHeight) {
      Float override = skyboxModule().getCloudHeight(originalHeight);
      return override != null ? override : originalHeight;
   }

   @ModifyExpressionValue(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getCloudColor(F)I")
   )
   private int applyCloudColor(int originalColor) {
      return skyboxModule().isToggled()
            && skyboxModule().cloudsSetting.getValue()
         ? (skyboxModule().cloudsColorSetting.getValue())
            .hashCode()
         : originalColor;
   }

   @Inject(method = "renderLevel", at = @At("HEAD"))
   private void hideGuiForFreecamHands(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      Camera camera,
      GameRenderer gameRenderer,
      Matrix4f inputMatrix1,
      Matrix4f inputMatrix2,
      CallbackInfo callback
   ) {
      if (this.hasSavedHideGui) {
         // Leftover from a frame whose restore never ran (exception/cancel).
         ClientAccess.collectiveMinecraft.options.hideGui = this.savedHideGui;
         this.hasSavedHideGui = false;
      }
      if (freecam().shouldHideHands()
         && IrisShaderBridge.isHandRendererAvailable()) {
         this.savedHideGui = ClientAccess.collectiveMinecraft.options.hideGui;
         this.hasSavedHideGui = true;
         ClientAccess.collectiveMinecraft.options.hideGui = true;
      }
   }

   @Inject(method = "renderLevel", at = @At("RETURN"))
   private void restoreGuiAfterFreecamHands(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      Camera camera,
      GameRenderer gameRenderer,
      Matrix4f inputMatrix1,
      Matrix4f inputMatrix2,
      CallbackInfo callback
   ) {
      if (this.hasSavedHideGui) {
         ClientAccess.collectiveMinecraft.options.hideGui = this.savedHideGui;
         this.hasSavedHideGui = false;
      }
   }

   @Inject(method = "prepareCullFrustum", at = @At("RETURN"))
   private void captureCullFrustum(
      Vec3 cameraPosition,
      Matrix4f frustumMatrix,
      Matrix4f projectionMatrix,
      CallbackInfo callback
   ) {
      RotationManager.setFrustum(this.cullingFrustum);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void initializeFramebufferStacks(CallbackInfo callback) {
      this.entityOutlineTargetStack = new ObjectArrayList<>();
      this.entityOutlineHandleStack = new ObjectArrayList<>();
   }

   @Override
   public void collective$pushFramebuffer(RenderTarget renderTarget) {
      this.entityOutlineTargetStack.push(this.entityOutlineTarget);
      this.entityOutlineTarget = renderTarget;
      this.entityOutlineHandleStack.push(this.targets.entityOutline);
      this.targets.entityOutline = () -> renderTarget;
   }

   @Override
   public void collective$popFramebuffer() {
      this.entityOutlineTarget = this.entityOutlineTargetStack.pop();
      this.targets.entityOutline = this.entityOutlineHandleStack.pop();
   }
}
