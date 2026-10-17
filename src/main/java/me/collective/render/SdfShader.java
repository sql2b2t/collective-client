package me.collective.render;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.awt.Color;
import java.nio.FloatBuffer;

/**
 * GPU-evaluated Signed Distance Field (SDF) renderer for smooth 2D UI primitives.
 * Renders pixel-perfect anti-aliased rounded rectangles, circles, and outlines.
 */
public class SdfShader {
    public static final SdfShader INSTANCE = new SdfShader();

    private int programId = -1;
    private int uWindowSize = -1;
    private int uSize = -1;
    private int uOrigin = -1;
    private int uRadius = -1;
    private int uColor = -1;
    private int uOutline = -1;
    private int uOutlineColor = -1;

    private int vao = -1;
    private int vbo = -1;
    private boolean initialized = false;
    private boolean failed = false;

    private static final String VERTEX_SHADER =
            "#version 150\n" +
            "in vec2 aPos;\n" +
            "uniform vec2 uWindowSize;\n" +
            "out vec2 vPos;\n" +
            "void main() {\n" +
            "    vPos = aPos;\n" +
            "    vec2 clip = (aPos / uWindowSize) * 2.0 - 1.0;\n" +
            "    gl_Position = vec4(clip.x, -clip.y, 0.0, 1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 150\n" +
            "uniform vec2 uSize;\n" +
            "uniform vec2 uOrigin;\n" +
            "uniform vec4 uRadius;\n" +
            "uniform vec4 uColor;\n" +
            "uniform float uOutline;\n" +
            "uniform vec4 uOutlineColor;\n" +
            "in vec2 vPos;\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "float selectRadius(vec2 p, vec4 r) {\n" +
            "    vec2 pair = (p.x > 0.0) ? r.yz : r.xw;\n" +
            "    return (p.y > 0.0) ? pair.y : pair.x;\n" +
            "}\n" +
            "\n" +
            "float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {\n" +
            "    float rad = selectRadius(p, r);\n" +
            "    vec2 q = abs(p) - b + rad;\n" +
            "    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rad;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 halfSize = uSize * 0.5;\n" +
            "    vec2 p = (vPos - uOrigin) - halfSize;\n" +
            "    float maxRad = min(halfSize.x, halfSize.y);\n" +
            "    vec4 clampedRad = clamp(uRadius, vec4(0.0), vec4(maxRad));\n" +
            "    float dist = roundedBoxSDF(p, halfSize, clampedRad);\n" +
            "    float aa = length(vec2(dFdx(dist), dFdy(dist)));\n" +
            "    if (aa <= 0.0) aa = fwidth(dist);\n" +
            "    if (aa <= 0.0) aa = 0.5;\n" +
            "    \n" +
            "    if (uOutline > 0.0) {\n" +
            "        float oDist = abs(dist + uOutline * 0.5) - uOutline * 0.5;\n" +
            "        float alpha = clamp(0.5 - oDist / aa, 0.0, 1.0);\n" +
            "        alpha = smoothstep(0.0, 1.0, alpha);\n" +
            "        if (alpha <= 0.001) discard;\n" +
            "        fragColor = vec4(uOutlineColor.rgb, uOutlineColor.a * alpha);\n" +
            "    } else {\n" +
            "        float alpha = clamp(0.5 - dist / aa, 0.0, 1.0);\n" +
            "        alpha = smoothstep(0.0, 1.0, alpha);\n" +
            "        if (alpha <= 0.001) discard;\n" +
            "        fragColor = vec4(uColor.rgb, uColor.a * alpha);\n" +
            "    }\n" +
            "}\n";

    public void init() {
        if (initialized || failed) return;
        try {
            int vert = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vert, VERTEX_SHADER);
            GL20.glCompileShader(vert);
            if (GL20.glGetShaderi(vert, GL20.GL_COMPILE_STATUS) == 0) {
                failed = true;
                return;
            }

            int frag = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(frag, FRAGMENT_SHADER);
            GL20.glCompileShader(frag);
            if (GL20.glGetShaderi(frag, GL20.GL_COMPILE_STATUS) == 0) {
                failed = true;
                return;
            }

            programId = GL20.glCreateProgram();
            GL20.glAttachShader(programId, vert);
            GL20.glAttachShader(programId, frag);
            GL20.glBindAttribLocation(programId, 0, "aPos");
            GL20.glLinkProgram(programId);

            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
                failed = true;
                return;
            }

            uWindowSize   = GL20.glGetUniformLocation(programId, "uWindowSize");
            uSize         = GL20.glGetUniformLocation(programId, "uSize");
            uOrigin       = GL20.glGetUniformLocation(programId, "uOrigin");
            uRadius       = GL20.glGetUniformLocation(programId, "uRadius");
            uColor        = GL20.glGetUniformLocation(programId, "uColor");
            uOutline      = GL20.glGetUniformLocation(programId, "uOutline");
            uOutlineColor = GL20.glGetUniformLocation(programId, "uOutlineColor");

            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();

            initialized = true;
        } catch (Throwable t) {
            failed = true;
        }
    }

    public void drawRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        drawRoundedRect(x, y, width, height, radius, radius, radius, radius, color);
    }

    public void drawRoundedRect(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, Color color) {
        if (!ensureReady()) return;
        setupDraw(x, y, width, height);

        GL20.glUniform4f(uRadius, rTL, rTR, rBR, rBL);
        GL20.glUniform4f(uColor, color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);
        GL20.glUniform1f(uOutline, 0.0f);

        renderQuad(x, y, width, height);
        endDraw();
    }

    public void drawRoundedOutline(float x, float y, float width, float height, float radius, float thickness, Color color) {
        if (!ensureReady()) return;
        setupDraw(x, y, width, height);

        GL20.glUniform4f(uRadius, radius, radius, radius, radius);
        GL20.glUniform1f(uOutline, thickness);
        GL20.glUniform4f(uOutlineColor, color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);

        renderQuad(x, y, width, height);
        endDraw();
    }

    public void drawCircle(float centerX, float centerY, float radius, Color color) {
        float size = radius * 2.0f;
        drawRoundedRect(centerX - radius, centerY - radius, size, size, radius, color);
    }

    public void drawCircleOutline(float centerX, float centerY, float radius, float thickness, Color color) {
        float size = radius * 2.0f;
        drawRoundedOutline(centerX - radius, centerY - radius, size, size, radius, thickness, color);
    }

    private boolean ensureReady() {
        if (!initialized && !failed) init();
        return initialized && !failed;
    }

    private void setupDraw(float x, float y, float width, float height) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        GL20.glUseProgram(programId);
        GL20.glUniform2f(uWindowSize, sw, sh);
        GL20.glUniform2f(uOrigin, x, y);
        GL20.glUniform2f(uSize, width, height);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    private void renderQuad(float x, float y, float width, float height) {
        float pad = 1.0f;
        float x1 = x - pad;
        float y1 = y - pad;
        float x2 = x + width + pad;
        float y2 = y + height + pad;

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        FloatBuffer buf = BufferUtils.createFloatBuffer(12);
        buf.put(new float[]{
                x1, y1,
                x2, y1,
                x1, y2,
                x1, y2,
                x2, y1,
                x2, y2
        }).flip();

        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_DYNAMIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        GL20.glDisableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private void endDraw() {
        GL20.glUseProgram(0);
    }
}
