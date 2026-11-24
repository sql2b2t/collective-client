// Original binary: sg/ec/Fz791iFWQBPEWRiC
package me.collective.client.bots.network

import net.minecraft.network.protocol.common.ClientboundClearDialogPacket
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.ClientboundCustomReportDetailsPacket
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.network.protocol.common.ClientboundServerLinksPacket
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket
import net.minecraft.network.protocol.common.ClientboundTransferPacket
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundAnimatePacket
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundCooldownPacket
import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundDebugBlockValuePacket
import net.minecraft.network.protocol.game.ClientboundDebugChunkValuePacket
import net.minecraft.network.protocol.game.ClientboundDebugEntityValuePacket
import net.minecraft.network.protocol.game.ClientboundDebugEventPacket
import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
import net.minecraft.network.protocol.game.ClientboundGameEventPacket
import net.minecraft.network.protocol.game.ClientboundGameTestHighlightPosPacket
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket
import net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket
import net.minecraft.network.protocol.game.ClientboundResetScorePacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket
import net.minecraft.network.protocol.game.ClientboundServerDataPacket
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.network.protocol.game.ClientboundTabListPacket
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.network.protocol.game.ClientboundTestInstanceBlockStatus
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket

abstract class AbstractBotPlayPacketListener : ClientGamePacketListener {
    override fun handleSetEquipment(packet: ClientboundSetEquipmentPacket) {
    }

    override fun handleSelectAdvancementsTab(packet: ClientboundSelectAdvancementsTabPacket) {
    }

    override fun handleLogin(packet: ClientboundLoginPacket) {
    }

    override fun handleCustomReportDetails(packet: ClientboundCustomReportDetailsPacket) {
    }

    override fun handleSetPlayerTeamPacket(packet: ClientboundSetPlayerTeamPacket) {
    }

    override fun handleDeleteChat(packet: ClientboundDeleteChatPacket) {
    }

    override fun handleEntityEvent(packet: ClientboundEntityEventPacket) {
    }

    override fun handleDebugEvent(packet: ClientboundDebugEventPacket) {
    }

    override fun handlePlayerInfoUpdate(packet: ClientboundPlayerInfoUpdatePacket) {
    }

    override fun handleInitializeBorder(packet: ClientboundInitializeBorderPacket) {
    }

    override fun handleAwardStats(packet: ClientboundAwardStatsPacket) {
    }

    override fun handleSetSimulationDistance(packet: ClientboundSetSimulationDistancePacket) {
    }

    override fun handleClearDialog(packet: ClientboundClearDialogPacket) {
    }

    override fun handleWaypoint(packet: ClientboundTrackedWaypointPacket) {
    }

    override fun handlePongResponse(packet: ClientboundPongResponsePacket) {
    }

    override fun handleContainerClose(packet: ClientboundContainerClosePacket) {
    }

    override fun handleCommandSuggestions(packet: ClientboundCommandSuggestionsPacket) {
    }

    override fun handleForgetLevelChunk(packet: ClientboundForgetLevelChunkPacket) {
    }

    override fun handleHurtAnimation(packet: ClientboundHurtAnimationPacket) {
    }

    override fun handleTagQueryPacket(packet: ClientboundTagQueryPacket) {
    }

    override fun handleBundlePacket(packet: ClientboundBundlePacket) {
    }

    override fun handleChunkBatchStart(packet: ClientboundChunkBatchStartPacket) {
    }

    override fun handleDebugSample(packet: ClientboundDebugSamplePacket) {
    }

    override fun handleRecipeBookAdd(packet: ClientboundRecipeBookAddPacket) {
    }

    override fun handleRotateMob(packet: ClientboundRotateHeadPacket) {
    }

    override fun handlePlayerInfoRemove(packet: ClientboundPlayerInfoRemovePacket) {
    }

    override fun handleChunkBlocksUpdate(packet: ClientboundSectionBlocksUpdatePacket) {
    }

    override fun handleBlockEvent(packet: ClientboundBlockEventPacket) {
    }

    override fun handleSoundEntityEvent(packet: ClientboundSoundEntityPacket) {
    }

    override fun handleMoveEntity(packet: ClientboundMoveEntityPacket) {
    }

    override fun handleSetDisplayObjective(packet: ClientboundSetDisplayObjectivePacket) {
    }

    override fun handleOpenBook(packet: ClientboundOpenBookPacket) {
    }

    override fun handleGameEvent(packet: ClientboundGameEventPacket) {
    }

    override fun handleSetExperience(packet: ClientboundSetExperiencePacket) {
    }

    override fun handleSetCamera(packet: ClientboundSetCameraPacket) {
    }

    override fun handleMountScreenOpen(packet: ClientboundMountScreenOpenPacket) {
    }

    override fun handleUpdateMobEffect(packet: ClientboundUpdateMobEffectPacket) {
    }

    override fun handleCustomChatCompletions(packet: ClientboundCustomChatCompletionsPacket) {
    }

    override fun handleContainerSetSlot(packet: ClientboundContainerSetSlotPacket) {
    }

    override fun handleConfigurationStart(packet: ClientboundStartConfigurationPacket) {
    }

    override fun handleSetEntityPassengersPacket(packet: ClientboundSetPassengersPacket) {
    }

    override fun handleOpenScreen(packet: ClientboundOpenScreenPacket) {
    }

    override fun handleEntityLinkPacket(packet: ClientboundSetEntityLinkPacket) {
    }

    override fun handleTabListCustomisation(packet: ClientboundTabListPacket) {
    }

    override fun handleTitlesClear(packet: ClientboundClearTitlesPacket) {
    }

    override fun handleSetScore(packet: ClientboundSetScorePacket) {
    }

    override fun handleSetBorderWarningDelay(packet: ClientboundSetBorderWarningDelayPacket) {
    }

    override fun handleUpdateTags(packet: ClientboundUpdateTagsPacket) {
    }

    override fun handleSetBorderLerpSize(packet: ClientboundSetBorderLerpSizePacket) {
    }

    override fun handleGameTestHighlightPos(packet: ClientboundGameTestHighlightPosPacket) {
    }

    override fun setActionBarText(packet: ClientboundSetActionBarTextPacket) {
    }

    override fun handlePing(packet: ClientboundPingPacket) {
    }

    override fun handleBossUpdate(packet: ClientboundBossEventPacket) {
    }

    override fun handleSetEntityData(packet: ClientboundSetEntityDataPacket) {
    }

    override fun handleSetBorderCenter(packet: ClientboundSetBorderCenterPacket) {
    }

    override fun handleBlockUpdate(packet: ClientboundBlockUpdatePacket) {
    }

    override fun handleSetHeldSlot(packet: ClientboundSetHeldSlotPacket) {
    }

    override fun setSubtitleText(packet: ClientboundSetSubtitleTextPacket) {
    }

    override fun handleContainerSetData(packet: ClientboundContainerSetDataPacket) {
    }

    override fun setTitleText(packet: ClientboundSetTitleTextPacket) {
    }

    override fun handleExplosion(packet: ClientboundExplodePacket) {
    }

    override fun handleRespawn(packet: ClientboundRespawnPacket) {
    }

    override fun handlePlaceRecipe(packet: ClientboundPlaceGhostRecipePacket) {
    }

    override fun handlePlayerCombatEnd(packet: ClientboundPlayerCombatEndPacket) {
    }

    override fun handleTeleportEntity(packet: ClientboundTeleportEntityPacket) {
    }

    override fun handleAnimate(packet: ClientboundAnimatePacket) {
    }

    override fun handleLevelChunkWithLight(packet: ClientboundLevelChunkWithLightPacket) {
    }

    override fun handleDisconnect(packet: ClientboundDisconnectPacket) {
    }

    override fun handleDebugBlockValue(packet: ClientboundDebugBlockValuePacket) {
    }

    override fun handleTransfer(packet: ClientboundTransferPacket) {
    }

    override fun handleSetPlayerInventory(packet: ClientboundSetPlayerInventoryPacket) {
    }

    override fun handleMerchantOffers(packet: ClientboundMerchantOffersPacket) {
    }

    override fun handleStoreCookie(packet: ClientboundStoreCookiePacket) {
    }

    override fun handleCustomPayload(packet: ClientboundCustomPayloadPacket) {
    }

    override fun handleRemoveEntities(packet: ClientboundRemoveEntitiesPacket) {
    }

    override fun handleBlockDestruction(packet: ClientboundBlockDestructionPacket) {
    }

    override fun handleAddEntity(packet: ClientboundAddEntityPacket) {
    }

    override fun handleUpdateRecipes(packet: ClientboundUpdateRecipesPacket) {
    }

    override fun handleRotatePlayer(packet: ClientboundPlayerRotationPacket) {
    }

    override fun handleServerLinks(packet: ClientboundServerLinksPacket) {
    }

    override fun setTitlesAnimation(packet: ClientboundSetTitlesAnimationPacket) {
    }

    override fun handleUpdateAttributes(packet: ClientboundUpdateAttributesPacket) {
    }

    override fun handleStopSoundEvent(packet: ClientboundStopSoundPacket) {
    }

    override fun handleRecipeBookSettings(packet: ClientboundRecipeBookSettingsPacket) {
    }

    override fun handleChangeDifficulty(packet: ClientboundChangeDifficultyPacket) {
    }

    override fun handleEntityPositionSync(packet: ClientboundEntityPositionSyncPacket) {
    }

    override fun handleDebugEntityValue(packet: ClientboundDebugEntityValuePacket) {
    }

    override fun handlePlayerAbilities(packet: ClientboundPlayerAbilitiesPacket) {
    }

    override fun handleChunkBatchFinished(packet: ClientboundChunkBatchFinishedPacket) {
    }

    override fun handleBlockChangedAck(packet: ClientboundBlockChangedAckPacket) {
    }

    override fun handleLightUpdatePacket(packet: ClientboundLightUpdatePacket) {
    }

    override fun handleSetBorderSize(packet: ClientboundSetBorderSizePacket) {
    }

    override fun handleResetScore(packet: ClientboundResetScorePacket) {
    }

    override fun handleTakeItemEntity(packet: ClientboundTakeItemEntityPacket) {
    }

    override fun handleBlockEntityData(packet: ClientboundBlockEntityDataPacket) {
    }

    override fun handleDamageEvent(packet: ClientboundDamageEventPacket) {
    }

    override fun handleUpdateAdvancementsPacket(packet: ClientboundUpdateAdvancementsPacket) {
    }

    override fun handleMinecartAlongTrack(packet: ClientboundMoveMinecartPacket) {
    }

    override fun handleOpenSignEditor(packet: ClientboundOpenSignEditorPacket) {
    }

    override fun handleTickingState(packet: ClientboundTickingStatePacket) {
    }

    override fun handleSetHealth(packet: ClientboundSetHealthPacket) {
    }

    override fun handleShowDialog(packet: ClientboundShowDialogPacket) {
    }

    override fun handleProjectilePowerPacket(packet: ClientboundProjectilePowerPacket) {
    }

    override fun handleSetChunkCacheCenter(packet: ClientboundSetChunkCacheCenterPacket) {
    }

    override fun handleKeepAlive(packet: ClientboundKeepAlivePacket) {
    }

    override fun handleSetSpawn(packet: ClientboundSetDefaultSpawnPositionPacket) {
    }

    override fun handleChunksBiomes(packet: ClientboundChunksBiomesPacket) {
    }

    override fun handleRequestCookie(packet: ClientboundCookieRequestPacket) {
    }

    override fun handlePlayerCombatEnter(packet: ClientboundPlayerCombatEnterPacket) {
    }

    override fun handleContainerContent(packet: ClientboundContainerSetContentPacket) {
    }

    override fun handleSystemChat(packet: ClientboundSystemChatPacket) {
    }

    override fun handleSetCursorItem(packet: ClientboundSetCursorItemPacket) {
    }

    override fun handleServerData(packet: ClientboundServerDataPacket) {
    }

    override fun handleResourcePackPop(packet: ClientboundResourcePackPopPacket) {
    }

    override fun handleLookAt(packet: ClientboundPlayerLookAtPacket) {
    }

    override fun handleCommands(packet: ClientboundCommandsPacket) {
    }

    override fun handleDebugChunkValue(packet: ClientboundDebugChunkValuePacket) {
    }

    override fun handlePlayerChat(packet: ClientboundPlayerChatPacket) {
    }

    override fun handleSoundEvent(packet: ClientboundSoundPacket) {
    }

    override fun handleItemCooldown(packet: ClientboundCooldownPacket) {
    }

    override fun handleSetChunkCacheRadius(packet: ClientboundSetChunkCacheRadiusPacket) {
    }

    override fun handleAddObjective(packet: ClientboundSetObjectivePacket) {
    }

    override fun handleSetTime(packet: ClientboundSetTimePacket) {
    }

    override fun handleMapItemData(packet: ClientboundMapItemDataPacket) {
    }

    override fun handleMoveVehicle(packet: ClientboundMoveVehiclePacket) {
    }

    override fun handleTestInstanceBlockStatus(packet: ClientboundTestInstanceBlockStatus) {
    }

    override fun handleResourcePackPush(packet: ClientboundResourcePackPushPacket) {
    }

    override fun handleParticleEvent(packet: ClientboundLevelParticlesPacket) {
    }

    override fun handleSetBorderWarningDistance(packet: ClientboundSetBorderWarningDistancePacket) {
    }

    override fun handleSetEntityMotion(packet: ClientboundSetEntityMotionPacket) {
    }

    override fun handleRemoveMobEffect(packet: ClientboundRemoveMobEffectPacket) {
    }

    override fun handlePlayerCombatKill(packet: ClientboundPlayerCombatKillPacket) {
    }

    override fun handleLevelEvent(packet: ClientboundLevelEventPacket) {
    }

    override fun handleMovePlayer(packet: ClientboundPlayerPositionPacket) {
    }

    override fun handleDisguisedChat(packet: ClientboundDisguisedChatPacket) {
    }

    override fun handleTickingStep(packet: ClientboundTickingStepPacket) {
    }

    override fun handleRecipeBookRemove(packet: ClientboundRecipeBookRemovePacket) {
    }

}
