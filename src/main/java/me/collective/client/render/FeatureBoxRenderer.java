package me.collective.client.render;

import me.collective.client.managers.AbstractRegistry;
import me.collective.client.settings.ValueProvider;

import me.collective.client.ClientAccess;
import me.collective.client.NamedFeature;
import me.collective.client.events.WorldRenderEvent;
import me.collective.client.events.EventHandler;
import me.collective.client.events.LocalPlayerTickEvent;
import me.collective.client.events.bus.EventBus;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public final class FeatureBoxRenderer extends AbstractRegistry<FeatureBoxRenderer.RenderConfiguration> implements ClientAccess {
   private final Map<RenderConfiguration, Set<FadingBoxRenderState>> boxesByConfiguration = new HashMap<>();

   public FeatureBoxRenderer() {
      super(new ArrayList<>());
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   @Override
   public boolean register(Object entry) {
      FeatureBoxRenderer.RenderConfiguration configuration = (FeatureBoxRenderer.RenderConfiguration)entry;
      return this.register(configuration);
   }
   @Override
   public boolean unregister(Object entry) {
      FeatureBoxRenderer.RenderConfiguration configuration = (FeatureBoxRenderer.RenderConfiguration)entry;
      return this.unregister(configuration);
   }

   public void addBox(NamedFeature feature, BlockPos position) {
      if (position != null) {
         AABB bounds = new AABB(position);
         this.addBox(feature, bounds);
      }
   }

   public boolean register(FeatureBoxRenderer.RenderConfiguration configuration) {
      synchronized (this.boxesByConfiguration) {
         this.boxesByConfiguration.putIfAbsent(configuration, new ObjectLinkedOpenHashSet<>());
      }
      return this.getRegistry().add(configuration);
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      synchronized (this.boxesByConfiguration) {
         for (Entry<RenderConfiguration, Set<FadingBoxRenderState>> entry : this.boxesByConfiguration.entrySet()) {
            Set<FadingBoxRenderState> boxes = entry.getValue();
            Predicate<FadingBoxRenderState> expiredBox = box -> {
               FeatureBoxRenderer.RenderConfiguration configuration = entry.getKey();
               float fadeDuration = configuration.getFadeDuration();
               return box.getFadeFactor(fadeDuration) == 0.0F;
            };
            boxes.removeIf(expiredBox);
         }
      }
   }

   public void addBox(NamedFeature feature, AABB bounds) {
      if (bounds != null) {
         Predicate<FeatureBoxRenderer.RenderConfiguration> matchesFeature = candidate -> candidate.feature().equals(feature);
         FeatureBoxRenderer.RenderConfiguration configuration = (FeatureBoxRenderer.RenderConfiguration)this.findFirstOrNull(matchesFeature);
         if (configuration == null) {
            throw new NoSuchElementException();
         }
         synchronized (this.boxesByConfiguration) {
            Set<FadingBoxRenderState> boxes = this.boxesByConfiguration.get(configuration);
            if (configuration.shouldRetainPreviousBoxes()) {
               for (FadingBoxRenderState box : boxes) {
                  if (bounds.equals(box.getBounds())) {
                     box.refreshFade();
                     return;
                  }
               }
            } else {
               boxes.clear();
            }
            FadingBoxRenderState newBox = new FadingBoxRenderState();
            newBox.setBounds(bounds);
            newBox.refreshFade();
            boxes.add(newBox);
         }
      }
   }

   public boolean unregister(FeatureBoxRenderer.RenderConfiguration configuration) {
      synchronized (this.boxesByConfiguration) {
         this.boxesByConfiguration.remove(configuration);
      }
      return this.getRegistry().remove(configuration);
   }

   @EventHandler
   private void onRenderWorld(WorldRenderEvent.Main event) {
      synchronized (this.boxesByConfiguration) {
         for (Entry<RenderConfiguration, Set<FadingBoxRenderState>> entry : this.boxesByConfiguration.entrySet()) {
            FeatureBoxRenderer.RenderConfiguration configuration = entry.getKey();
            Set<FadingBoxRenderState> boxes = entry.getValue();
            for (FadingBoxRenderState box : boxes) {
               float lineWidth = configuration.getLineWidth();
               box.setLineWidth(lineWidth);
               PoseStack poseStack = event.getPoseStack();
               Color fillColor = configuration.getFillColor();
               Color outlineColor = configuration.getOutlineColor();
               float fadeDuration = configuration.getFadeDuration();
               boolean fadeEnabled = configuration.isFadeEnabled();
               box.render(poseStack, fillColor, outlineColor, fadeDuration, fadeEnabled);
            }
         }
      }
   }

   public record RenderConfiguration(
      NamedFeature feature,
      ValueProvider<Color> fillColorSupplier,
      ValueProvider<Color> outlineColorSupplier,
      ValueProvider<Float> lineWidthSupplier,
      ValueProvider<Float> fadeDurationSupplier,
      ValueProvider<Boolean> retainPreviousBoxesSupplier,
      ValueProvider<Boolean> fadeEnabledSupplier,
      int fadeTimeScale
   ) {
      public float getLineWidth() {
         return this.lineWidthSupplier.getValue();
      }

      public boolean isFadeEnabled() {
         return this.fadeEnabledSupplier.getValue();
      }

      public Color getOutlineColor() {
         return this.outlineColorSupplier.getValue();
      }

      public float getFadeDuration() {
         if (this.fadeEnabledSupplier.getValue()) {
            return this.fadeDurationSupplier.getValue() * this.fadeTimeScale;
         }
         return 0.0F;
      }

      public Color getFillColor() {
         return this.fillColorSupplier.getValue();
      }

      public boolean shouldRetainPreviousBoxes() {
         return this.retainPreviousBoxesSupplier.getValue();
      }

      @Override
      public boolean equals(Object other) {
         if (this == other) {
            return true;
         } else if (other != null && this.getClass() == other.getClass()) {
            FeatureBoxRenderer.RenderConfiguration configuration = (FeatureBoxRenderer.RenderConfiguration)other;
            return Objects.equals(
               this.feature,
               configuration.feature
            );
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.feature);
      }

   }

   public static class Builder {
      private final NamedFeature feature;
      private final ValueProvider<Color> fillColorSupplier;
      private final ValueProvider<Color> outlineColorSupplier;
      private ValueProvider<Float> lineWidthSupplier = () -> 1.0F;
      private ValueProvider<Float> fadeDurationSupplier = () -> 1.0F;
      private ValueProvider<Boolean> retainPreviousBoxesSupplier = () -> true;
      private ValueProvider<Boolean> fadeEnabledSupplier = () -> true;
      private int fadeTimeScale;

      public Builder(NamedFeature feature, ValueProvider<Color> fillColorSupplier, ValueProvider<Color> outlineColorSupplier) {
         this.fadeTimeScale = 1000;
         this.feature = feature;
         this.fillColorSupplier = fillColorSupplier;
         this.outlineColorSupplier = outlineColorSupplier;
      }

      public FeatureBoxRenderer.Builder withFadeEnabled(
         ValueProvider<Boolean> supplier
      ) {
         this.fadeEnabledSupplier = supplier;
         return this;
      }

      public FeatureBoxRenderer.RenderConfiguration build() {
         return new FeatureBoxRenderer.RenderConfiguration(
            this.feature,
            this.fillColorSupplier,
            this.outlineColorSupplier,
            this.lineWidthSupplier,
            this.fadeDurationSupplier,
            this.retainPreviousBoxesSupplier,
            this.fadeEnabledSupplier,
            this.fadeTimeScale
         );
      }

      public FeatureBoxRenderer.Builder withLineWidth(
         ValueProvider<Float> supplier
      ) {
         this.lineWidthSupplier = supplier;
         return this;
      }

      public FeatureBoxRenderer.Builder withFadeDuration(
         ValueProvider<Float> supplier
      ) {
         this.fadeDurationSupplier = supplier;
         return this;
      }

      public FeatureBoxRenderer.Builder withRetainPreviousBoxes(
         ValueProvider<Boolean> supplier
      ) {
         this.retainPreviousBoxesSupplier = supplier;
         return this;
      }

      public FeatureBoxRenderer.Builder withFadeTimeScale(int fadeTimeScale) {
         this.fadeTimeScale = fadeTimeScale;
         return this;
      }
   }
}
