package me.collective.util;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Утилита для трансляции и сопоставления клавиш между GLFW (1.21.11) и SDL/InputConstants (26.3).
 */
public final class KeyHelper {
    private KeyHelper() {}

    /**
     * Преобразует устаревший GLFW keycode в сканкод Minecraft 26.3 (SDL),
     * либо возвращает исходный код, если он уже является кодом 26.3.
     */
    public static int to263Key(int key) {
        if (key <= 0) return key;

        switch (key) {
            case 344: return InputConstants.KEY_RSHIFT;    // 229
            case 340: return InputConstants.KEY_LSHIFT;    // 225
            case 345: return InputConstants.KEY_RCONTROL;  // 228
            case 341: return InputConstants.KEY_LCONTROL;  // 224
            case 346: return InputConstants.KEY_RALT;      // 230
            case 342: return InputConstants.KEY_LALT;      // 226
            case 256: return InputConstants.KEY_ESCAPE;    // 41
            case 257: return InputConstants.KEY_RETURN;    // 40
            case 32:  return InputConstants.KEY_SPACE;     // 44
            case 259: return InputConstants.KEY_BACKSPACE; // 42
            case 258: return InputConstants.KEY_TAB;       // 43
            case 261: return InputConstants.KEY_DELETE;    // 76
            case 268: return InputConstants.KEY_HOME;      // 74
            case 269: return InputConstants.KEY_END;       // 77
            case 266: return InputConstants.KEY_PAGEUP;    // 75
            case 267: return InputConstants.KEY_PAGEDOWN;  // 78
            case 260: return InputConstants.KEY_INSERT;    // 73
            case 265: return InputConstants.KEY_UP;        // 82
            case 264: return InputConstants.KEY_DOWN;      // 81
            case 263: return InputConstants.KEY_LEFT;      // 80
            case 262: return InputConstants.KEY_RIGHT;     // 79
            case 280: return InputConstants.KEY_CAPSLOCK;  // 57
            case 282: return InputConstants.KEY_NUMLOCK;   // 83
            case 283: return InputConstants.KEY_PRINTSCREEN; // 70
            case 284: return InputConstants.KEY_PAUSE;     // 72
            case 281: return InputConstants.KEY_SCROLLLOCK; // 71
            default: break;
        }

        // Letters A-Z: GLFW 65..90 -> SDL KEY_A (4) .. KEY_Z (29)
        if (key >= 65 && key <= 90) {
            return (key - 65) + InputConstants.KEY_A;
        }

        // Digits 1-9: GLFW 49..57 -> SDL KEY_1 (30) .. KEY_9 (38)
        if (key >= 49 && key <= 57) {
            return (key - 49) + InputConstants.KEY_1;
        }
        // Digit 0: GLFW 48 -> SDL KEY_0 (39)
        if (key == 48) {
            return InputConstants.KEY_0;
        }

        // Function keys F1-F12: GLFW 290..301 -> SDL KEY_F1 (58) .. KEY_F12 (69)
        if (key >= 290 && key <= 301) {
            return (key - 290) + InputConstants.KEY_F1;
        }

        return key;
    }

    /**
     * Проверяет совпадение бинда и нажатой клавиши.
     */
    public static boolean isMatch(int boundKey, int pressedKey) {
        if (boundKey <= 0 || pressedKey <= 0) return false;
        if (boundKey == pressedKey) return true;
        return to263Key(boundKey) == to263Key(pressedKey);
    }

    /**
     * Проверяет, является ли клавиша Right Shift.
     */
    public static boolean isRightShift(int key) {
        return key == InputConstants.KEY_RSHIFT || key == 344 || to263Key(key) == InputConstants.KEY_RSHIFT;
    }

    /**
     * Проверяет физическое состояние клавиши в данный момент.
     */
    public static boolean isKeyDown(int key) {
        if (key <= 0) return false;
        int target = to263Key(key);
        return InputConstants.isKeyDown(target);
    }

    /**
     * Преобразует SDL кнопку мыши (1=Left, 3=Right, 2=Middle) в стандартную нумерацию GLFW (0=Left, 1=Right, 2=Middle).
     */
    public static int toStandardMouseButton(int button) {
        if (button == 1) return 0; // SDL Left Click -> standard 0
        if (button == 3) return 1; // SDL Right Click -> standard 1
        if (button == 2) return 2; // SDL Middle Click -> standard 2
        return button;
    }

    public static boolean isEnter(int key) {
        int k = to263Key(key);
        return k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER || k == 257 || key == InputConstants.KEYCODE_RETURN || key == InputConstants.KEYCODE_NUMPADENTER;
    }

    public static boolean isEscape(int key) {
        int k = to263Key(key);
        return k == InputConstants.KEY_ESCAPE || k == 256;
    }

    public static boolean isBackspace(int key) {
        int k = to263Key(key);
        return k == InputConstants.KEY_BACKSPACE || k == 259 || key == InputConstants.KEYCODE_BACKSPACE;
    }

    public static boolean isDelete(int key) {
        int k = to263Key(key);
        return k == InputConstants.KEY_DELETE || k == 261 || key == InputConstants.KEYCODE_DELETE;
    }

    public static boolean isLeft(int key) {
        int k = to263Key(key);
        return k == InputConstants.KEY_LEFT || k == 263 || key == InputConstants.KEYCODE_LEFT;
    }

    public static boolean isRight(int key) {
        int k = to263Key(key);
        return k == InputConstants.KEY_RIGHT || k == 262 || key == InputConstants.KEYCODE_RIGHT;
    }

    public static boolean isUp(int key) {
        int k = to263Key(key);
        return k == InputConstants.KEY_UP || k == 265 || key == InputConstants.KEYCODE_UP;
    }

    public static boolean isDown(int key) {
        int k = to263Key(key);
        return k == InputConstants.KEY_DOWN || k == 264 || key == InputConstants.KEYCODE_DOWN;
    }

    public static boolean isHome(int key) {
        int k = to263Key(key);
        return k == InputConstants.KEY_HOME || k == 268 || key == InputConstants.KEYCODE_HOME;
    }

    public static boolean isEnd(int key) {
        int k = to263Key(key);
        return k == InputConstants.KEY_END || k == 269 || key == InputConstants.KEYCODE_END;
    }

    public static boolean isControlDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
    }

    public static boolean isShiftDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    public static boolean isAltDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);
    }
}
