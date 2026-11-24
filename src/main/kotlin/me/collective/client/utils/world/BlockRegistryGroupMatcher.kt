package me.collective.client.utils.world

import me.collective.client.utils.collections.NamedRegistryEntry
import me.collective.client.utils.collections.RegistryGroupMatcher

import java.util.HashMap
import java.util.function.Predicate
import java.util.stream.Stream
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.BannerBlock
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CarpetBlock
import net.minecraft.world.level.block.ConcretePowderBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.world.level.block.SignBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.StainedGlassBlock
import net.minecraft.world.level.block.StainedGlassPaneBlock
import net.minecraft.world.level.block.TrapDoorBlock

class BlockRegistryGroupMatcher : RegistryGroupMatcher {
   override fun getRegistryId(): Identifier {
      return Registries.BLOCK.identifier()
   }

   override fun getGroupNames(): Collection<String> {
      val availableGroups: Map<String, List<NamedRegistryEntry>> = groups
      return availableGroups.keys
   }

   override fun getGroupEntries(groupName: String): Collection<NamedRegistryEntry> {
      val availableGroups: Map<String, List<NamedRegistryEntry>> = groups
      val lowerCaseName = groupName.lowercase()
      val emptyGroup: List<NamedRegistryEntry> = emptyList()
      return availableGroups.getOrDefault(lowerCaseName, emptyGroup)
   }

   companion object {
      private val groups: MutableMap<String, List<NamedRegistryEntry>> = HashMap()

      init {
         val bedsBlocks = BuiltInRegistries.BLOCK.stream()
         val bedsFilter = Predicate<Block> { block -> block is BedBlock }
         val bedsMatches = bedsBlocks.filter(bedsFilter)
         registerGroup("beds", bedsMatches)

         val bannersBlocks = BuiltInRegistries.BLOCK.stream()
         val bannersFilter = Predicate<Block> { block -> block is BannerBlock }
         val bannersMatches = bannersBlocks.filter(bannersFilter)
         registerGroup("banners", bannersMatches)

         val doorsBlocks = BuiltInRegistries.BLOCK.stream()
         val doorsFilter = Predicate<Block> { block -> block is DoorBlock }
         val doorsMatches = doorsBlocks.filter(doorsFilter)
         registerGroup("doors", doorsMatches)

         val trapdoorsBlocks = BuiltInRegistries.BLOCK.stream()
         val trapdoorsFilter = Predicate<Block> { block -> block is TrapDoorBlock }
         val trapdoorsMatches = trapdoorsBlocks.filter(trapdoorsFilter)
         registerGroup("trapdoors", trapdoorsMatches)

         val slabsBlocks = BuiltInRegistries.BLOCK.stream()
         val slabsFilter = Predicate<Block> { block -> block is SlabBlock }
         val slabsMatches = slabsBlocks.filter(slabsFilter)
         registerGroup("slabs", slabsMatches)

         val shulkersBlocks = BuiltInRegistries.BLOCK.stream()
         val shulkersFilter = Predicate<Block> { block -> block is ShulkerBoxBlock }
         val shulkersMatches = shulkersBlocks.filter(shulkersFilter)
         registerGroup("shulkers", shulkersMatches)

         val carpetsBlocks = BuiltInRegistries.BLOCK.stream()
         val carpetsFilter = Predicate<Block> { block -> block is CarpetBlock }
         val carpetsMatches = carpetsBlocks.filter(carpetsFilter)
         registerGroup("carpets", carpetsMatches)

         val glassesBlocks = BuiltInRegistries.BLOCK.stream()
         val glassesFilter = Predicate<Block> { block -> block is StainedGlassBlock || block === Blocks.GLASS }
         val glassesMatches = glassesBlocks.filter(glassesFilter)
         registerGroup("glasses", glassesMatches)

         val glassPanesBlocks = BuiltInRegistries.BLOCK.stream()
         val glassPanesFilter = Predicate<Block> { block -> block is StainedGlassPaneBlock || block === Blocks.GLASS_PANE }
         val glassPanesMatches = glassPanesBlocks.filter(glassPanesFilter)
         registerGroup("glass_panes", glassPanesMatches)

         val signsBlocks = BuiltInRegistries.BLOCK.stream()
         val signsFilter = Predicate<Block> { block -> block is SignBlock }
         val signsMatches = signsBlocks.filter(signsFilter)
         registerGroup("signs", signsMatches)

         val railsBlocks = BuiltInRegistries.BLOCK.stream()
         val railsFilter = Predicate<Block> { block -> block is BaseRailBlock }
         val railsMatches = railsBlocks.filter(railsFilter)
         registerGroup("rails", railsMatches)

         val concretePowderBlocks = BuiltInRegistries.BLOCK.stream()
         val concretePowderFilter = Predicate<Block> { block -> block is ConcretePowderBlock }
         val concretePowderMatches = concretePowderBlocks.filter(concretePowderFilter)
         registerGroup("concrete_powder", concretePowderMatches)

         val oresFilter = Predicate<String> { name -> name.contains("_ore") }
         registerGroup("ores", oresFilter)

         val planksFilter = Predicate<String> { name -> name.contains("_plank") }
         registerGroup("planks", planksFilter)

         val logsFilter = Predicate<String> { name -> name.contains("_log") }
         registerGroup("logs", logsFilter)

         val bricksFilter = Predicate<String> { name -> name.contains("brick") }
         registerGroup("bricks", bricksFilter)

         val headsFilter = Predicate<String> { name ->
            if (!name.contains("skull")) {
               if (!name.contains("head")) {
                  false
               } else {
                  true
               }
            } else {
               true
            }
         }
         registerGroup("heads", headsFilter)

         val candlesFilter = Predicate<String> { name -> name.contains("candle") }
         registerGroup("candles", candlesFilter)

         val concreteFilter = Predicate<String> { name -> name.contains("_concrete") }
         registerGroup("concrete", concreteFilter)

      }

      private fun registerGroup(groupName: String, blocks: Stream<Block>) {
         val targetGroups: MutableMap<String, List<NamedRegistryEntry>> = groups
         val entries: Stream<NamedRegistryEntry> = blocks.map { block ->
            NamedRegistryEntry(getBlockName(block), block)
         }
         val groupEntries: List<NamedRegistryEntry> = entries.toList()
         targetGroups[groupName] = groupEntries
      }

      private fun registerGroup(groupName: String, nameFilter: Predicate<String>) {
         val holders: Stream<Holder.Reference<Block>> = BuiltInRegistries.BLOCK.listElements()
         val holderFilter = Predicate<Holder.Reference<Block>> { holder ->
            val keyFilter = Predicate<ResourceKey<Block>> { registryKey ->
               val id: Identifier = registryKey.identifier()
               val name = id.toShortLanguageKey()
               nameFilter.test(name)
            }
            holder.`is`(keyFilter)
         }
         val matchingHolders: Stream<Holder.Reference<Block>> = holders.filter(holderFilter)
         val matchingBlocks: Stream<Block> = matchingHolders.map { holder -> holder.value() }
         registerGroup(groupName, matchingBlocks)
      }

      private fun getBlockName(block: Block): String {
         val id: Identifier = BuiltInRegistries.BLOCK.getKey(block)
         return id.toShortLanguageKey()
      }
   }
}
