package me.collective.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import me.collective.client.render.BufferSourceCleanup;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.RenderType;
import net.raphimc.immediatelyfast.feature.core.BatchableBufferSource;
import net.raphimc.immediatelyfast.feature.core.BufferAllocatorPool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(BatchableBufferSource.class)
public class BatchableBufferSourceMixin implements BufferSourceCleanup {
   @Shadow(remap = false)
   @Final
   protected Map pendingBuffers;
   @Shadow(remap = false)
   @Final
   protected Set activeLayers;

   @Override
   public void collective$cleanAll() {
      for (Object activeLayer : this.activeLayers) {
         RenderType renderType = (RenderType)activeLayer;
         ReferenceSet builders = (ReferenceSet)this.pendingBuffers.remove(renderType);
         if (builders != null) {
            for (Object activeBuilder : builders) {
               BufferBuilder builder = (BufferBuilder)activeBuilder;
               MeshData mesh = builder.build();
               if (mesh != null) {
                  mesh.close();
               }
               // Порядок важен: mesh.close() освобождает batch внутри ByteBufferBuilder,
               // иначе clear() внутри returnBufferAllocatorSafe шумит
               // "Clearing BufferBuilder with unused batches" на каждый билд.
               BufferAllocatorPool.returnBufferAllocatorSafe(builder.buffer);
            }

            builders.clear();
         }
      }

      this.activeLayers.clear();
   }
}
