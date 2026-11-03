package me.collective.gui.alt;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;

/**
 * Modern GPU-accelerated Signed Distance Field (SDF) renderer using OpenGL shaders.
 * Evaluates mathematical geometry directly in physical screen pixels with sub-pixel
 * anti-aliasing. Produces 100% vector-smooth, retina-quality rounded rectangles,
 * circles, and borders with zero GUI scale quantization or pixelation artifacts.
 */
public final class GpuSdf {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static int program = -1;
    private static int uScale = -1;
    private static int uRectPos = -1;
    private static int uRectSize = -1;
    private static int uRadius = -1;
    private static int uFillColor = -1;
    private static int uBorderColor = -1;
    private static int uBorderThickness = -1;
    private static int uShadowSpread = -1;

    private static int vao = -1;
    private static int vbo = -1;
    private static boolean initialized = false;
    private static boolean failed = false;

    private static final FloatBuffer QUAD_BUFFER = BufferUtils.createFloatBuffer(8);

    private static final String VERTEX_SHADER =
            "#version 150\n" +
            "in vec2 aPos;\n" +
            "out vec2 vScreenPos;\n" +
            "uniform vec2 uScale;\n" +
            "void main() {\n" +
            "    vScreenPos = aPos;\n" +
            "    gl_Position = vec4(aPos.x * uScale.x - 1.0, 1.0 - aPos.y * uScale.y, 0.0, 1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 150\n" +
            "in vec2 vScreenPos;\n" +
            "out vec4 fragColor;\n" +
            "uniform vec2 uRectPos;\n" +
            "uniform vec2 uRectSize;\n" +
            "uniform float uRadius;\n" +
            "uniform vec4 uFillColor;\n" +
            "uniform vec4 uBorderColor;\n" +
            "uniform float uBorderThickness;\n" +
            "uniform float uShadowSpread;\n" +
            "\n" +
            "float roundedBoxSDF(vec2 p, vec2 b, float r) {\n" +
            "    vec2 q = abs(p) - b + vec2(r);\n" +
            "    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 halfSize = uRectSize * 0.5;\n" +
            "    vec2 center = uRectPos + halfSize;\n" +
            "    float dist = roundedBoxSDF(vScreenPos - center, halfSize, uRadius);\n" +
            "\n" +
            "    if (uShadowSpread > 0.0) {\n" +
            "        // Soft Gaussian glow mode\n" +
            "        if (dist <= 0.0) {\n" +
            "            fragColor = uFillColor;\n" +
            "        } else {\n" +
            "            float factor = exp(-(dist * dist) / (2.0 * uShadowSpread * uShadowSpread));\n" +
            "            fragColor = vec4(uFillColor.rgb, uFillColor.a * factor);\n" +
            "        }\n" +
            "        return;\n" +
            "    }\n" +
            "\n" +
            "    // Sub-pixel anti-aliased edge over 1 physical pixel\n" +
            "    float fillAlpha = smoothstep(0.7, -0.7, dist);\n" +
            "    if (fillAlpha <= 0.0) discard;\n" +
            "\n" +
            "    if (uBorderThickness > 0.0) {\n" +
            "        float innerDist = dist + uBorderThickness;\n" +
            "        float borderAlpha = clamp(fillAlpha - smoothstep(0.7, -0.7, innerDist), 0.0, 1.0);\n" +
            "        float coreAlpha = smoothstep(0.7, -0.7, innerDist);\n" +
            "        vec4 col = uFillColor * coreAlpha + uBorderColor * borderAlpha;\n" +
            "        fragColor = col;\n" +
            "    } else {\n" +
            "        fragColor = vec4(uFillColor.rgb, uFillColor.a * fillAlpha);\n" +
            "    }\n" +
            "}\n";

    private GpuSdf() {}

    public static synchronized void ensureInit() {
        if (initialized || failed) return;
        try {
            int vert = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vert, VERTEX_SHADER);
            GL20.glCompileShader(vert);
            if (GL20.glGetShaderi(vert, GL20.GL_COMPILE_STATUS) == 0) {
                System.err.println("GpuSdf vert compile failed: " + GL20.glGetShaderInfoLog(vert));
                failed = true;
                return;
            }

            int frag = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(frag, FRAGMENT_SHADER);
            GL20.glCompileShader(frag);
            if (GL20.glGetShaderi(frag, GL20.GL_COMPILE_STATUS) == 0) {
                System.err.println("GpuSdf frag compile failed: " + GL20.glGetShaderInfoLog(frag));
                failed = true;
                return;
            }

            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vert);
            GL20.glAttachShader(program, frag);
            GL20.glBindAttribLocation(program, 0, "aPos");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                System.err.println("GpuSdf link failed: " + GL20.glGetProgramInfoLog(program));
                failed = true;
                return;
            }

            uScale = GL20.glGetUniformLocation(program, "uScale");
            uRectPos = GL20.glGetUniformLocation(program, "uRectPos");
            uRectSize = GL20.glGetUniformLocation(program, "uRectSize");
            uRadius = GL20.glGetUniformLocation(program, "uRadius");
            uFillColor = GL20.glGetUniformLocation(program, "uFillColor");
            uBorderColor = GL20.glGetUniformLocation(program, "uBorderColor");
            uBorderThickness = GL20.glGetUniformLocation(program, "uBorderThickness");
            uShadowSpread = GL20.glGetUniformLocation(program, "uShadowSpread");

            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();

            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);

            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            initialized = true;
        } catch (Throwable t) {
            t.printStackTrace();
            failed = true;
        }
    }

    /**
     * Draw a filled rounded rectangle with physical-pixel anti-aliasing.
     */
    public static void fillRounded(float gx, float gy, float gw, float gh, float gr, int color) {
        drawBox(gx, gy, gw, gh, gr, color, 0, 0f, 0f);
    }

    /**
     * Draw a rounded rectangle with both fill and stroke border in a single smooth pass.
     */
    public static void fillAndStroke(float gx, float gy, float gw, float gh, float gr, int fillColor, int borderColor, float strokeThickness) {
        drawBox(gx, gy, gw, gh, gr, fillColor, borderColor, strokeThickness, 0f);
    }

    /**
     * Draw a stroked rounded outline with physical-pixel anti-aliasing.
     */
    public static void strokeRounded(float gx, float gy, float gw, float gh, float gr, float strokeThickness, int borderColor) {
        drawBox(gx, gy, gw, gh, gr, 0, borderColor, strokeThickness, 0f);
    }

    /**
     * Draw a smooth circle with physical-pixel anti-aliasing.
     */
    public static void circle(float gcx, float gcy, float gr, int color) {
        fillRounded(gcx - gr, gcy - gr, gr * 2f, gr * 2f, gr, color);
    }

    /**
     * Draw a smooth circle ring with physical-pixel anti-aliasing.
     */
    public static void circleRing(float gcx, float gcy, float gr, float thickness, int color) {
        strokeRounded(gcx - gr, gcy - gr, gr * 2f, gr * 2f, gr, thickness, color);
    }

    /**
     * Draw a multi-layer soft glow / shadow.
     */
    public static void softGlow(float gx, float gy, float gw, float gh, float gr, int color, float spread, float yOff) {
        drawBox(gx, gy + yOff, gw, gh, gr, color, 0, 0f, Math.max(1f, spread * (float) mc.getWindow().getScaleFactor()));
    }

    private static void drawBox(float gx, float gy, float gw, float gh, float gr,
                                int fillColor, int borderColor, float strokeThickness, float shadowSpread) {
        if (gw <= 0f || gh <= 0f) return;
        ensureInit();
        if (failed || program == -1) {
            return;
        }

        double scale = mc.getWindow().getScaleFactor();
        int fbW = mc.getWindow().getFramebufferWidth();
        int fbH = mc.getWindow().getFramebufferHeight();
        if (fbW <= 0 || fbH <= 0) return;

        // Convert GUI coordinates to physical screen pixels
        float px = (float) (gx * scale);
        float py = (float) (gy * scale);
        float pw = (float) (gw * scale);
        float ph = (float) (gh * scale);
        float pr = Math.min((float) (gr * scale), Math.min(pw, ph) * 0.5f);
        float pt = (float) (strokeThickness * scale);

        // Save OpenGL state
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL20.glUseProgram(program);

        // Set uniforms
        GL20.glUniform2f(uScale, 2.0f / (float) fbW, 2.0f / (float) fbH);
        GL20.glUniform2f(uRectPos, px, py);
        GL20.glUniform2f(uRectSize, pw, ph);
        GL20.glUniform1f(uRadius, pr);
        GL20.glUniform1f(uBorderThickness, pt);
        GL20.glUniform1f(uShadowSpread, shadowSpread);

        // Colors
        setUniformColor(uFillColor, fillColor);
        setUniformColor(uBorderColor, borderColor);

        // Quad bounding box (expand slightly for anti-aliasing / shadow bleed)
        float margin = shadowSpread > 0.0f ? shadowSpread * 3.0f : 2.0f;
        float x0 = px - margin;
        float y0 = py - margin;
        float x1 = px + pw + margin;
        float y1 = py + ph + margin;

        QUAD_BUFFER.clear();
        QUAD_BUFFER.put(x0).put(y0);
        QUAD_BUFFER.put(x1).put(y0);
        QUAD_BUFFER.put(x1).put(y1);
        QUAD_BUFFER.put(x0).put(y1);
        QUAD_BUFFER.flip();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, QUAD_BUFFER, GL15.GL_STREAM_DRAW);

        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);

        // Restore OpenGL state
        GL30.glBindVertexArray(prevVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVbo);
        GL20.glUseProgram(prevProgram);

        if (!prevBlend) GL11.glDisable(GL11.GL_BLEND);
        if (prevDepth) GL11.glEnable(GL11.GL_DEPTH_TEST);
        if (prevCull) GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private static void setUniformColor(int loc, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255.0f;
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        GL20.glUniform4f(loc, r, g, b, a);
    }
}
