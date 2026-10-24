package me.collective.emotes;

import java.util.List;
import net.minecraft.resources.Identifier;

public record Emote(String id, String title, String file, java.util.List<String> animations, boolean loop) {
    public static final List<Emote> ALL = List.of(
            emote("wave", "Махание рукой", "wave", false),
            variant("wiggling_torso", "Суююю", "basic_dances", true, "wiggling_torso"),
            emote("twerk", "Тверк", "twerk", true),
            emote("take_the_l", "Танец проигравшего", "take_the_l", true),
            emote("gangnam_style", "Каннам-стайл", "gangnam_style", true),
            emote("sit", "Сесть", "sit", true),
            emote("backflip", "Сальто назад", "backflip", false),
            emote("cheer", "Радость", "cheer", true),
            emote("shrug", "Пожать плечами", "shrug", false),
            emote("hug", "Объятие", "hug", false),
            emote("laugh", "Смех", "laugh", true),
            emote("yawn", "Зевок", "yawn", false),
            emote("hands_up", "Руки вверх", "hands_up", true),
            emote("flex_muscle", "Показать мышцы", "flex_muscle", false),
            emote("arms_crossed", "Скрестить руки", "arms_crossed", true),
            emote("hands_on_hips", "Руки на бёдрах", "hands_on_hips", true),
            variant("head_nod", "Кивок", "head_affirm", false, "haed_nod"),
            variant("head_shake", "Покачать головой", "head_affirm", false, "haed_shake"),
            emote("arm_stretch", "Разминка рук", "arm_stretch", true),
            variant("side_step_shuffle", "Шаффл в сторону", "basic_dances", true, "side_step_shuffle"),
            variant("arm_wave_dance", "Волна руками", "basic_dances", true, "arm_wave_dance"),
            emote("air_drum", "Воздушные барабаны", "air_drum", true),
            emote("robot_dance", "Танец робота", "robot_dance", true),
            emote("hide_barrel", "Спрятаться", "hide", true),
            variants("clap", "Аплодисменты", "clap", true, "clap", "clap2", "clap3"),
            variants("point", "Указать", "point", false, "point", "point2"),
            variants("cry", "Плач", "cry", true, "cry", "cry2"),
            variants("salute", "Отдать честь", "salute", true, "salute", "salute2"),
            variants("facepalm", "Фейспалм", "facepalm", false, "facepalm", "facepalm2", "facepalm3"),
            variants("kneel", "Встать на колено", "kneel", true, "kneel", "kneel2", "kneel3"),
            variants("lay", "Лечь", "lay", true, "lay", "lay2", "lay3"),
            variants("bow", "Поклон", "bow", true, "bow", "bow2", "bow3"),
            variants("lean_to_left", "Наклон", "lean", true, "lean_to_left", "lean_to_right", "lean_to_back")
    );

    public net.minecraft.resources.Identifier icon() {
        String mapped = switch (this.id) {
            case "twerk" -> "buttsit";
            case "take_the_l" -> "robot_dance";
            default -> this.id;
        };
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("collective", "textures/gui/emotes/" + mapped + ".png");
    }

    private static Emote variants(String id, String title, String file, boolean loop, String... animations) {
        return new Emote(id, title, file, List.of(animations), loop);
    }

    private static Emote variant(String id, String title, String file, boolean loop, String animation) {
        return new Emote(id, title, file, List.of(animation), loop);
    }

    private static Emote emote(String id, String title, String file, boolean loop) {
        return variant(id, title, file, loop, id);
    }
}