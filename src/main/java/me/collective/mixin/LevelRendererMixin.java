package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
import me.collective.client.render.EntityRenderStateAccess;
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
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import me.collective.client.render.MultiBufferSource;
import me.collective.client.render.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
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
   @org.spongepowered.asm.mixin.Mutable
   @Nullable
   private RenderTarget entityOutlineTarget;
   @Shadow
   @Final
   private LevelTargetBundle targets;
   @Unique
   private Stack<RenderTarget> entityOutlineTargetStack;
   @Unique
   private Stack<ResourceHandle<RenderTarget>> entityOutlineHandleStack;

   // Mojang leaves the weather render pass lambda unnamed.
   @WrapWithCondition(
      method = "method_62216(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;IF)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V"
      )
   , require = 0)
   private boolean shouldRenderWeather(
      WeatherEffectRenderer weatherRenderer, MultiBufferSource buffers, Vec3 cameraPosition, WeatherRenderState weatherState
   ) {
      return !ambienceModule().isToggled()
         || !ambienceModule().worldWeatherSetting.getValue()
         || ambienceModule().weatherSetting.getValue()
            != AmbienceModule.WeatherChoice.CLEAR;
   }

   @Inject(method = "render", at = @At("RETURN"))
   private void renderWorldEvents(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
      org.joml.Matrix4fc inputMatrix1,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      CallbackInfo callback
   ) {
      PoseStack poses = new PoseStack();
      float partialTick = RenderUtils.getPartialTick();
      GpuBufferSlice previousFog = RenderSystem.getShaderFog();
      RenderSystem.setShaderFog(
         ClientAccess.collectiveMinecraft.gameRenderer
            .fogRenderer
            .getBuffer(FogMode.NONE)
      );
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
            ClientAccess.collectiveMinecraft.gameRenderer.mainRenderTarget()
         );
         RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
         WorldRenderEvent.Main mainEvent = WorldRenderEvent.Main.create(
            poses, partialTick
         );
         ClientAccess.eventBus.post(
            mainEvent
         );
         RenderUtils.clearDepth(
            ClientAccess.collectiveMinecraft.gameRenderer.mainRenderTarget()
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
      method = "method_62214(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;ZLcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBuffers;bufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;", shift = Shift.BEFORE)
   , require = 0)
   private void postBeforeBufferSourceEvent(
      GpuBufferSlice inputBuffer,
      LevelRenderState renderState,
      ProfilerFiller profiler,
      Matrix4f inputMatrix,
      ResourceHandle firstTarget,
      ResourceHandle secondTarget,
      boolean renderFlag,
      ResourceHandle thirdTarget,
      ResourceHandle fourthTarget,
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
      method = "render",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/culling/Frustum;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;ZLnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
         shift = Shift.BEFORE
      )
   , require = 0)
   private void addSkyShaderPass(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
      org.joml.Matrix4fc inputMatrix1,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
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
      method = "method_62214(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;ZLcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;renderBlockOutline(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;ZLnet/minecraft/client/renderer/state/level/LevelRenderState;)V"
      )
   , require = 0)
   private boolean shouldRenderBlockOutline(
      LevelRenderer renderer, BufferSource bufferSource, PoseStack poseStack, boolean translucent, LevelRenderState renderState
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

   @Inject(method = "doesMobEffectBlockSky", at = @At("HEAD"), cancellable = true, require = 0)
   private void suppressSkyBlindness(
      Camera camera, CallbackInfoReturnable<Boolean> callback
   ) {
      if (noRenderModule().isToggled()
         && noRenderModule().blindnessSetting.getValue()) {
         callback.setReturnValue(false);
      }
   }

   @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"), require = 0)
   private boolean applyCameraSpectatorOverride(boolean originalSpectator) {
      return freecam().isToggled()
         || (viewClip().isToggled() && ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().isDetached())
         || originalSpectator;
   }

   @Inject(method = "render", at = @At("HEAD"))
   private void captureWorldRenderState(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
      org.joml.Matrix4fc inputMatrix1,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      CallbackInfo callback
   ) {
      RenderUtils.setPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
      this.worldPoseStack = new PoseStack();
      this.blockOutlineEventPosted = false;
      this.blockOutlineCancelled = false;
   }

   // Mojang leaves the background color render pass lambda unnamed.
   @ModifyExpressionValue(method = "method_62218(Lorg/joml/Vector4f;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;colorFromFloat(FFFF)I"), require = 0)
   private int applySkyboxBackgroundColor(int originalColor) {
      return skyboxModule().isToggled()
            && skyboxModule().isCurrentDimensionEnabled()
         ? ARGB.color(
            0,
            (skyboxModule().backgroundSetting.getValue())
               .hashCode()
         )
         : originalColor;
   }

   // Mojang leaves the sky render pass lambda unnamed.
   @ModifyArgs(
      method = "method_62215(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/SkyRenderState;Lnet/minecraft/client/renderer/SkyRenderer;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFog(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V")
   , require = 0)
   private static void applySkyFogSlice(Args arguments) {
      if (skyboxModule().isToggled()
         && skyboxModule().splitFogSetting.getValue()
         && skyboxModule().isCurrentDimensionEnabled()) {
         arguments.set(
            0,
            skyboxModule().getSkySlice()
         );
      }
   }

   @WrapWithCondition(
      method = "submitEntities",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"
      )
   )
   private boolean shouldSubmitVisibleEntity(
      EntityRenderDispatcher dispatcher, EntityRenderState renderState, CameraRenderState cameraState, double x, double y, double z, PoseStack poses, SubmitNodeCollector collector
   ) {
      Entity entity = EntityRenderStateAccess.getEntity(renderState);
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

   @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"), require = 0)
   private boolean applyFreecamDetachedCamera(boolean originalDetached) {
      return freecam().isToggled() || originalDetached;
   }

   @Inject(method = "addWeatherPass", at = @At("HEAD"))
   private void beginWeatherPassColor(
      FrameGraphBuilder frameGraph, GpuBufferSlice fogBuffer, CallbackInfo callback
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 0.25F);
   }

   @Inject(method = "addWeatherPass", at = @At("TAIL"))
   private void resetWeatherPassColor(
      FrameGraphBuilder frameGraph, GpuBufferSlice fogBuffer, CallbackInfo callback
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   @ModifyExpressionValue(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/EnvironmentAttributeProbe;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;", ordinal = 1)
   , require = 0)
   private Object applyCloudHeight(Object originalHeight) {
      return skyboxModule().getCloudHeight((Float)originalHeight);
   }

   @ModifyExpressionValue(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/EnvironmentAttributeProbe;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;", ordinal = 0)
   )
   private Object applyCloudColor(Object originalColor) {
      return skyboxModule().isToggled()
            && skyboxModule().cloudsSetting.getValue()
         ? (skyboxModule().cloudsColorSetting.getValue())
            .hashCode()
         : originalColor;
   }

   @Inject(method = "render", at = @At("HEAD"))
   private void hideGuiForFreecamHands(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
      org.joml.Matrix4fc inputMatrix1,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      CallbackInfo callback
   ) {
      if (this.hasSavedHideGui) {
         // Leftover from a frame whose restore never ran (exception/cancel).
         ClientAccess.collectiveMinecraft.gui.hud.isHidden = this.savedHideGui;
         this.hasSavedHideGui = false;
      }
      if (freecam().shouldHideHands()
         && IrisShaderBridge.isHandRendererAvailable()) {
         this.savedHideGui = ClientAccess.collectiveMinecraft.gui.hud.isHidden;
         this.hasSavedHideGui = true;
         ClientAccess.collectiveMinecraft.gui.hud.isHidden = true;
      }
   }

   @Inject(method = "render", at = @At("RETURN"))
   private void restoreGuiAfterFreecamHands(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
      org.joml.Matrix4fc inputMatrix1,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      CallbackInfo callback
   ) {
      if (this.hasSavedHideGui) {
         ClientAccess.collectiveMinecraft.gui.hud.isHidden = this.savedHideGui;
         this.hasSavedHideGui = false;
      }
   }

   @Inject(
      method = "render",
      at = @At(
         value = "INVOKE_ASSIGN",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/client/renderer/culling/Frustum;"
      )
   , require = 0)
   private void captureCullFrustum(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
      org.joml.Matrix4fc inputMatrix1,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      CallbackInfo callback,
      @Local Frustum frustum
   ) {
      RotationManager.setFrustum(frustum);
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
