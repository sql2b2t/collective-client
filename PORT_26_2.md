# Порт Collective Client на Minecraft 26.2

Сгенерировано `scripts/port_26_2.py`.

## Сводка
- Скопировано файлов: **1394**
- `build.gradle`: `net.fabricmc.fabric-loom`, Java 25, удалён блок `remapJar`, `mod*` -> обычные конфигурации
- `gradle.properties`: `minecraft_version=26.2`, `loader_version=0.20.1`, `loom_version=1.19.4`, `fabric_api_version=0.161.0+26.2`
- `fabric.mod.json`: `minecraft >= 26.2`, `java >= 25`
- Переименований применено: **287** в **71** файлах
- `vfp-maven`: сгенерирован `vfp-maven/com/viaversion/viafabricplus-api/5.0.3/viafabricplus-api-5.0.3.jar`
- Авто-резолв intermediary по Tiny: заменено **15** вхождений

## Зависимости Modrinth

| Mod | Было | Стало | Версия |
|---|---|---|---|
| `viafabricplus` | `YzQYJEjP` | `wvLPUTIv` | `5.0.3` |
| `lithium` | `Ow7wA0kG` | `f7vZ0VWU` | `mc26.2-0.25.3-fabric` |
| `immediatelyfast` | `QwkfUKSj` | `pMWERcSu` | `1.16.5+26.2-fabric` |
| `sodium` | `RB7CDjTS` | `Ql1mFeyg` | `mc26.2-0.9.4-beta.1-fabric` |
| `xaeros-minimap` | `62EynHPJ` | `kYWdcLLn` | `fabric-26.2-26.6.2` |
| `entityculling` | `CTabrwB1` | `M8qoBSI5` | `1.12.2` |
| `zakoopt` | `Nxk0BrEK` | `i1bvkkmG` | `2.1.3+26.2` |

## Таблица ренеймов

| kind | old | new | замен |
|---|---|---|---|
| CLASS | `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper` | `net.fabricmc.fabric.api.client.keybinding.v1.KeyMappingHelper` | 0 |
| CLASS | `KeyBindingHelper` | `KeyMappingHelper` | 0 |
| CLASS | `net.fabricmc.fabric.api.networking.v1.PacketByteBufs` | `net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs` | 0 |
| CLASS | `PacketByteBufs` | `FriendlyByteBufs` | 0 |
| CLASS | `net.minecraft.world.entity.MobSpawnType` | `net.minecraft.world.entity.EntitySpawnReason` | 0 |
| CLASS | `MobSpawnType` | `EntitySpawnReason` | 0 |
| CLASS | `net.minecraft.client.gui.GuiGraphics` | `net.minecraft.client.gui.GuiGraphicsExtractor` | 61 |
| CLASS | `GuiGraphics` | `GuiGraphicsExtractor` | 86 |
| CLASS | `net.minecraft.world.inventory.ClickType` | `net.minecraft.world.inventory.ContainerInput` | 33 |
| CLASS | `ClickType` | `ContainerInput` | 107 |

## Оставшиеся intermediary-имена (method_/field_/class_)

Не найдено.

## Автоисправление ошибок компиляции (BUILD FAILED, итераций: 3)

### Применённые фиксы:
- gradle-wrapper.properties: upgraded Gradle wrapper to 9.8.0
- ChatMessageEvent.kt: updated moved import for `GuiMessageTag` -> `net.minecraft.client.multiplayer.chat.GuiMessageTag`
- ColorSubmitNodeStorage.kt: updated moved import for `BlockStateModel` -> `net.minecraft.client.renderer.block.dispatch.BlockStateModel`
- ColorSubmitNodeStorage.kt: updated moved import for `CameraRenderState` -> `net.minecraft.client.renderer.state.level.CameraRenderState`
- BaritoneAdapter.kt: updated moved import for `GuiMessageTag` -> `net.minecraft.client.multiplayer.chat.GuiMessageTag`
- ShaderRegistry.kt: updated moved import for `AtlasManager` -> `net.minecraft.client.resources.model.sprite.AtlasManager`
- PlayerModelModule.kt: updated moved import for `CameraRenderState` -> `net.minecraft.client.renderer.state.level.CameraRenderState`
- BetterChatModule.kt: updated moved import for `GuiMessageTag` -> `net.minecraft.client.multiplayer.chat.GuiMessageTag`
- ChatFilterModule.kt: updated moved import for `GuiMessageTag` -> `net.minecraft.client.multiplayer.chat.GuiMessageTag`
- ESPModule.kt: updated moved import for `CameraRenderState` -> `net.minecraft.client.renderer.state.level.CameraRenderState`
- ShaderModule.kt: updated moved import for `CameraRenderState` -> `net.minecraft.client.renderer.state.level.CameraRenderState`
- ChatUtils.kt: updated moved import for `GuiMessageTag` -> `net.minecraft.client.multiplayer.chat.GuiMessageTag`

### Оставшиеся ошибки компиляции:
- `BotInteractionController.kt:111`: Unresolved reference 'createInteractionPacket'.
- `BotInteractionController.kt:119`: Unresolved reference 'createInteractionPacket'.
- `BotInteractionController.kt:126`: Unresolved reference 'createAttackPacket'.
- `BotManagerScreen.kt:74`: Unresolved reference 'setScreen' on receiver of type 'Minecraft'.
- `BotPlayPacketListener.kt:13`: Class 'BotPlayPacketListener' is not abstract and does not implement abstract members:
- `BotPlayPacketListener.kt:79`: Unresolved reference 'PLAYER'.
- `BotSessionTransfer.kt:37`: No value passed for parameter 'sessionId'.
- `BotClientLevel.kt:47`: Argument type mismatch: actual type is 'LevelRenderer', but 'LevelExtractor' was expected.
- `BotClientLevel.kt:164`: 'syncBlockState' overrides nothing. Potential signatures for overriding:
- `BotEntityTracker.kt:68`: Unresolved reference 'PLAYER'.
- `BotLocalPlayer.kt:18`: No value passed for parameter 'chatAbilities'.
- `BotWorldState.kt:183`: No value passed for parameter 'sessionId'.
- `BotWorldSynchronizer.kt:45`: Unresolved reference 'tabList' on receiver of type 'Gui'.
- `ActionCommand.kt:94`: Unresolved reference 'screen' on receiver of type 'Minecraft'.
- `ActionCommand.kt:110`: Unresolved reference 'screen' on receiver of type 'Minecraft'.
- `ActionCommand.kt:166`: Argument type mismatch: actual type is 'EntityType<CapturedType(*)>', but 'Holder<EntityType<*>>' was expected.
- `BaritoneCommand.kt:110`: Cannot access 'field mainCamera: Camera': it is private in 'net.minecraft.client.renderer.GameRenderer'.
- `BotCommand.kt:29`: Unresolved reference 'setScreen' on receiver of type 'Minecraft'.
- `BotCommand.kt:225`: Unresolved reference 'displayClientMessage' on receiver of type 'LocalPlayer'.
- `CheststealerCommand.kt:68`: Unresolved reference 'center' on receiver of type 'BlockPos'.
- `CheststealerCommand.kt:85`: Unresolved reference 'center' on receiver of type 'BlockPos'.
- `CheststealerCommand.kt:105`: Unresolved reference 'center' on receiver of type 'BlockPos'.
- `DefaultsCommand.kt:47`: Unresolved reference 'setScreen' on receiver of type 'Minecraft'.
- `DefaultsCommand.kt:54`: Unresolved reference 'setScreen' on receiver of type 'Minecraft'.
- `HighlightCommand.kt:64`: Unresolved reference 'center' on receiver of type 'BlockPos'.
- `HighlightCommand.kt:87`: Cannot access 'field mainCamera: Camera': it is private in 'net.minecraft.client.renderer.GameRenderer'.
- `InventoryCommand.kt:81`: Unresolved reference 'setScreen' on receiver of type 'Minecraft'.
- `MacroCommand.kt:363`: Unresolved reference 'color' on receiver of type 'ChatFormatting'.
- `MacroCommand.kt:364`: Unresolved reference 'withStyle'.
- `MacroCommand.kt:476`: Unresolved reference 'color' on receiver of type 'ChatFormatting'.
- `MacroCommand.kt:477`: Unresolved reference 'withStyle'.
- `ModuleCommand.kt:50`: Cannot infer type for type parameter 'T'. Specify it explicitly.
- `ModuleCommand.kt:52`: Unresolved reference 'color' on receiver of type 'ChatFormatting'.
- `ModuleCommand.kt:54`: Unresolved reference 'color' on receiver of type 'ChatFormatting'.
- `OffhandSwapStrategy.kt:17`: Unresolved reference 'handleInventoryMouseClick' on receiver of type 'MultiPlayerGameMode'.
- `OffhandSwapStrategy.kt:24`: Unresolved reference 'handleInventoryMouseClick' on receiver of type 'MultiPlayerGameMode'.
- `PanicCommand.kt:11`: Unresolved reference 'GuiMessage'.
- `PanicCommand.kt:33`: Unresolved reference 'chat' on receiver of type 'Gui'.
- `PanicCommand.kt:34`: Cannot infer type for value parameter 'message'. Specify it explicitly.
- `PanicCommand.kt:37`: Unresolved reference 'chat' on receiver of type 'Gui'.
- `PanicCommand.kt:50`: Unresolved reference 'chat' on receiver of type 'Gui'.
- `PanicCommand.kt:51`: Cannot infer type for value parameter 'message'. Specify it explicitly.
- `PanicCommand.kt:66`: Unresolved reference 'setScreen' on receiver of type 'Minecraft'.
- `PanicCommand.kt:71`: Unresolved reference 'setScreen' on receiver of type 'Minecraft'.
- `PresetCommand.kt:220`: Unresolved reference 'setScreen' on receiver of type 'Minecraft'.
- `PrintCommand.kt:17`: Unresolved reference 'chat' on receiver of type 'Gui'.
- `ChatMessageEvent.kt:3`: Unresolved reference 'GuiMessage'.
- `ChatMessageEvent.kt:11`: Unresolved reference 'Line'.
- `ChatMessageEvent.kt:15`: Unresolved reference 'Line'.
- `ChatMessageEvent.kt:35`: Unresolved reference 'Line'.
- `AbstractItemListComponent.kt:67`: Unresolved reference 'screen' on receiver of type 'Minecraft'.
- `ColorSubmitNodeStorage.kt:41`: Too many arguments for 'constructor(): SubmitNodeCollection'.
- `ColorSubmitNodeStorage.kt:45`: 'submitNameTag' overrides nothing. Potential signatures for overriding:
- `ColorSubmitNodeStorage.kt:103`: 'submitModelPart' overrides nothing. Potential signatures for overriding:
- `ColorSubmitNodeStorage.kt:116`: None of the following candidates is applicable:
- `ColorSubmitNodeStorage.kt:131`: 'submitBlock' overrides nothing.
- `ColorSubmitNodeStorage.kt:134`: 'submitMovingBlock' overrides nothing. Potential signatures for overriding:
- `ColorSubmitNodeStorage.kt:137`: 'submitBlockModel' overrides nothing. Potential signatures for overriding:
- `ColorSubmitNodeStorage.kt:151`: Argument type mismatch: actual type is 'RenderType', but 'Function<ChunkSectionLayer, RenderType>' was expected.
- `ColorSubmitNodeStorage.kt:151`: Argument type mismatch: actual type is 'BlockStateModel', but 'Boolean' was expected.

## Чеклист после автопорта
1. `./gradlew compileKotlin compileJava --no-daemon`
2. Адаптировать `render/` и шейдеры под изменения рендера в 26.2.
3. Исправить оставшиеся intermediary-имена и сломанные таргеты миксинов.
4. Обновить `baritone` sha256 / artifact в `build.gradle`, когда будет сборка под 26.2.
5. Запустить `./gradlew runClient -Dmixin.debug.export=true` и проверить логи.
