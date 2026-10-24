package me.collective.module.render;

import me.collective.category.Category;
import me.collective.module.Module;

public class NoHurtCam extends Module {
    public static NoHurtCam INSTANCE;

    public NoHurtCam() {
        super("NoHurtCam", "Отключает тряску камеры при получении урона.", Category.RENDER);
        INSTANCE = this;
    }
}
