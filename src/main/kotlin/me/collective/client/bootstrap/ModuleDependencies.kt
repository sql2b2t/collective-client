package me.collective.client.bootstrap

import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.managers.ServerStateManager
import me.collective.client.modules.PlacementModule
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.client.BaritoneModule
import me.collective.client.modules.client.ColorsModule
import me.collective.client.modules.client.HUDModule
import me.collective.client.modules.combat.AnchorAuraModule
import me.collective.client.modules.combat.AntiFeetPlaceModule
import me.collective.client.modules.combat.AntiPhaseModule
import me.collective.client.modules.combat.AuraModule
import me.collective.client.modules.combat.AutoArmorModule
import me.collective.client.modules.combat.AutoClickerModule
import me.collective.client.modules.combat.AutoCrystalModule
import me.collective.client.modules.combat.AutoDropModule
import me.collective.client.modules.combat.AutoExpModule
import me.collective.client.modules.combat.AutoLogModule
import me.collective.client.modules.combat.AutoMaceModule
import me.collective.client.modules.combat.AutoMineModule
import me.collective.client.modules.combat.AutoTrapModule
import me.collective.client.modules.combat.BlockerModule
import me.collective.client.modules.combat.CriticalsModule
import me.collective.client.modules.combat.FeetPlaceModule
import me.collective.client.modules.combat.KeyPearlModule
import me.collective.client.modules.combat.MainhandModule
import me.collective.client.modules.combat.OffhandModule
import me.collective.client.modules.combat.PistonCrystalModule
import me.collective.client.modules.combat.PlainHoleFillPolicy
import me.collective.client.modules.combat.PusherModule
import me.collective.client.modules.combat.WebAuraModule
import me.collective.client.modules.exploit.ChorusControlModule
import me.collective.client.modules.exploit.FastLatencyModule
import me.collective.client.modules.exploit.PhaseModule
import me.collective.client.modules.exploit.ReachModule
import me.collective.client.modules.exploit.RocketExtenderModule
import me.collective.client.modules.exploit.TimerModule
import me.collective.client.modules.misc.AutoSignModule
import me.collective.client.modules.misc.AutoWitherModule
import me.collective.client.modules.misc.BetterChatModule
import me.collective.client.modules.misc.MiddleClickModule
import me.collective.client.modules.movement.AutoWalkModule
import me.collective.client.modules.movement.ElytraFlyModule
import me.collective.client.modules.movement.FastClimbModule
import me.collective.client.modules.movement.FastFallModule
import me.collective.client.modules.movement.FastSwimModule
import me.collective.client.modules.movement.FastWebModule
import me.collective.client.modules.movement.FireworksModule
import me.collective.client.modules.movement.HighJumpModule
import me.collective.client.modules.movement.NoSlowModule
import me.collective.client.modules.movement.ObstaclePasserModule
import me.collective.client.modules.movement.SpeedModule
import me.collective.client.modules.movement.SprintModule
import me.collective.client.modules.movement.StepModule
import me.collective.client.modules.movement.VelocityModule
import me.collective.client.modules.movement.WarpModule
import me.collective.client.modules.player.AutoToolModule
import me.collective.client.modules.player.ChestStealerModule
import me.collective.client.modules.player.FastPlaceModule
import me.collective.client.modules.player.FreecamInput
import me.collective.client.modules.player.HighwayBuilderModule
import me.collective.client.modules.player.InventorySorterModule
import me.collective.client.modules.player.NukerModule
import me.collective.client.modules.player.ReplenishModule
import me.collective.client.modules.player.ScaffoldModule
import me.collective.client.modules.player.SourceFillModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.modules.render.ChamsModule
import me.collective.client.modules.render.ESPModule
import me.collective.client.modules.render.FreeLookModule
import me.collective.client.modules.render.HighlightModule
import me.collective.client.modules.render.HitmarkerModule
import me.collective.client.modules.render.LogoutSpotsModule
import me.collective.client.modules.render.NameTagsModule
import me.collective.client.modules.render.ParticlesModule
import me.collective.client.modules.render.ProjectileTrajectorySimulator
import me.collective.client.modules.render.SearchModule
import me.collective.client.modules.render.ShaderModule
import me.collective.client.modules.render.SkeletonModule
import me.collective.client.modules.render.TracersModule
import me.collective.client.settings.ColorSetting
import me.collective.client.utils.inventory.ChestSearchController
import me.collective.client.utils.world.BlockBreakTracker
import me.collective.mixin.BlockModuleBindings
import me.collective.mixin.BundleContentsModuleBindings
import me.collective.mixin.EntityModuleBindings
import me.collective.mixin.HoneyBlockModuleBindings
import me.collective.mixin.LevelModuleBindings
import me.collective.mixin.LiquidBlockModuleBindings
import me.collective.mixin.LivingEntityModuleBindings
import me.collective.mixin.MaterialModuleBindings
import me.collective.mixin.MinecraftModuleBindings
import me.collective.mixin.MobModuleBindings
import me.collective.mixin.PlayerModuleBindings
import me.collective.mixin.SlimeBlockModuleBindings
import me.collective.mixin.SweetBerryBushBlockModuleBindings
import me.collective.mixin.TextureTransformModuleBindings
import me.collective.mixin.TridentItemModuleBindings
import me.collective.mixin.WebBlockModuleBindings
import java.util.Objects

object ModuleDependencies {
    @JvmStatic
    fun bind(registry: ModuleRegistry) {
        Objects.requireNonNull(registry, "Module manager is not initialized")
        bindMinecraftHooks(registry)
        ServerStateManager.bindModules(registry)
        BlockBreakTracker.bindModules(registry)
        RotationManager.bindReferencedModules(registry)
        SprintModule.bindReferencedModules(registry)
        ColorSetting.bindModuleRegistry(registry)
        CriticalsModule.bindModules(registry)
        HighlightModule.bindModuleDependencies(registry)
        AuraModule.bindModuleRegistry(registry)
        ScaffoldModule.bindReferencedModules(registry)
        HighwayBuilderModule.bindReferencedModules(registry)
        NoSlowModule.bindReferencedModules(registry)
        VelocityModule.bindModules(registry)
        OffhandModule.bindModuleConnections(registry)
        SpeedModule.bindReferencedModules(registry)
        StepModule.bindReferencedModules(registry)
        ESPModule.bindModules(registry)
        MiddleClickModule.bindReferencedModules(registry)
        TimerModule.bindReferencedModules(registry)
        AutoWitherModule.bindReferencedModules(registry)
        AutoLogModule.bindModules(registry)
        FastPlaceModule.bindModules(registry)
        ElytraFlyModule.bindModuleReferences(registry)
        AutoWalkModule.bindReferencedModules(registry)
        AnchorAuraModule.bindReferencedModules(registry)
        PlacementModule.bindModules(registry)
        FeetPlaceModule.bindReferencedModules(registry)
        FastFallModule.bindReferencedModules(registry)
        ReplenishModule.bindModules(registry)
        PlainHoleFillPolicy.connectSpeedMineModule(registry)
        AutoTrapModule.bindReferencedModules(registry)
        AutoCrystalModule.bindReferencedModules(registry)
        SpeedMineModule.bindModules(registry)
        AutoExpModule.bindReferencedModules(registry)
        BetterChatModule.bindReferencedModules(registry)
        HUDModule.bindReferencedModules(registry)
        ChamsModule.bindReferencedModules(registry)
        NameTagsModule.bindReferencedModules(registry)
        KeyPearlModule.bindReferencedModules(registry)
        ChestSearchController.bindReferencedModules(registry)
        ShaderModule.bindModules(registry)
        HitmarkerModule.bindCrosshair(registry)
        SearchModule.bindModules(registry)
        AutoArmorModule.bindModuleRegistry(registry)
        NukerModule.bindReferencedModules(registry)
        LogoutSpotsModule.bindFontsModule(registry)
        ChorusControlModule.bindModules(registry)
        FastSwimModule.bindReferencedModules(registry)
        SkeletonModule.bindModuleDependencies(registry)
        BlockerModule.bindReferencedModules(registry)
        WarpModule.bindReferencedModules(registry)
        AutoToolModule.bindModules(registry)
        ParticlesModule.bindColorsModule(registry)
        FreecamInput.bindModules(registry)
        TracersModule.bindReferencedModules(registry)
        FreeLookModule.bindReferencedModules(registry)
        ProjectileTrajectorySimulator.bindModules(registry)
        ReachModule.bindModuleDependencies(registry)
        WebAuraModule.bindModules(registry)
        ChestStealerModule.bindReferencedModules(registry)
        FastLatencyModule.bindReferencedModules(registry)
        AntiCheatModule.bindReferencedModules(registry)
        FastWebModule.bindModules(registry)
        RocketExtenderModule.bindReferencedModules(registry)
        ColorsModule.bindSearchModule(registry)
        AutoDropModule.bindModules(registry)
        PhaseModule.bindReferencedModules(registry)
        AutoMineModule.bindReferencedModules(registry)
        AutoSignModule.bindModules(registry)
        PusherModule.bindModules(registry)
        PistonCrystalModule.bindModules(registry)
        AutoClickerModule.bindModules(registry)
        FireworksModule.bindModules(registry)
        HighJumpModule.bindReferencedModules(registry)
        MainhandModule.bindModuleConnections(registry)
        AntiPhaseModule.bindMultiTaskModule(registry)
        FastClimbModule.bindModules(registry)
        SourceFillModule.bindModuleReferences(registry)
        InventorySorterModule.bindModules(registry)
        AutoMaceModule.bindReferencedModules(registry)
        AntiFeetPlaceModule.bindModules(registry)
        BaritoneModule.bindModuleDependencies(registry)
        ObstaclePasserModule.bindModules(registry)
    }

    private fun bindMinecraftHooks(registry: ModuleRegistry) {
        BlockModuleBindings.bind(registry)
        LiquidBlockModuleBindings.bind(registry)
        WebBlockModuleBindings.bind(registry)
        SlimeBlockModuleBindings.bind(registry)
        SweetBerryBushBlockModuleBindings.bind(registry)
        HoneyBlockModuleBindings.bind(registry)
        MinecraftModuleBindings.bind(registry)
        BundleContentsModuleBindings.bind(registry)
        LevelModuleBindings.bind(registry)
        EntityModuleBindings.bind(registry)
        LivingEntityModuleBindings.bind(registry)
        TridentItemModuleBindings.bind(registry)
        TextureTransformModuleBindings.bind(registry)
        MaterialModuleBindings.bind(registry)
        MobModuleBindings.bind(registry)
        PlayerModuleBindings.bind(registry)
    }
}
