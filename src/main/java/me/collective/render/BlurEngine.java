package me.collective.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.List;

public class BlurEngine {
    public static final BlurEngine INSTANCE = new BlurEngine();
    private static final Minecraft mc = Minecraft.getInstance();

    private int shaderProgram = -1;
    private int uDirection = -1;
    private int uRadius = -1;
    private int uAlpha = -1;
    private int uTexture = -1;

    private int vao = -1;
    private int vbo = -1;

    private int sceneTexture = -1;
    private int pingFbo = -1;
    private int pingTexture = -1;

    private int lastWidth = 0;
    private int lastHeight = 0;
    private boolean initialized = false;
    private boolean shaderFailed = false;

    private static final String VERTEX_SHADER =
            "#version 150\n" +
            "in vec2 aPos;\n" +
            "out vec2 texCoord;\n" +
            "void main() {\n" +
            "    texCoord = (aPos + 1.0) * 0.5;\n" +
            "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 150\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform vec2 uDirection;\n" +
            "uniform float uRadius;\n" +
            "uniform float uAlpha;\n" +
            "in vec2 texCoord;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    vec4 sum = vec4(0.0);\n" +
            "    float totalWeight = 0.0;\n" +
            "    float rLimit = clamp(uRadius, 1.0, 16.0);\n" +
            "    float sigma = max(rLimit * 0.5, 0.5);\n" +
            "    float twoSigmaSquare = 2.0 * sigma * sigma;\n" +
            "    for (float r = -rLimit; r <= rLimit; r += 1.0) {\n" +
            "        float weight = exp(-(r * r) / twoSigmaSquare);\n" +
            "        sum += texture(uTexture, texCoord + r * uDirection) * weight;\n" +
            "        totalWeight += weight;\n" +
            "    }\n" +
            "    vec4 col = sum / totalWeight;\n" +
            "    fragColor = vec4(col.rgb, col.a * uAlpha);\n" +
            "}\n";

    private void init() {
        if (initialized || shaderFailed) return;
        try {
            int vert = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vert, VERTEX_SHADER);
            GL20.glCompileShader(vert);
            if (GL20.glGetShaderi(vert, GL20.GL_COMPILE_STATUS) == 0) {
                shaderFailed = true;
                return;
            }

            int frag = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(frag, FRAGMENT_SHADER);
            GL20.glCompileShader(frag);
            if (GL20.glGetShaderi(frag, GL20.GL_COMPILE_STATUS) == 0) {
                shaderFailed = true;
                return;
            }

            shaderProgram = GL20.glCreateProgram();
            GL20.glAttachShader(shaderProgram, vert);
            GL20.glAttachShader(shaderProgram, frag);
            GL20.glBindAttribLocation(shaderProgram, 0, "aPos");
            GL20.glLinkProgram(shaderProgram);
            if (GL20.glGetProgrami(shaderProgram, GL20.GL_LINK_STATUS) == 0) {
                shaderFailed = true;
                return;
            }

            uDirection = GL20.glGetUniformLocation(shaderProgram, "uDirection");
            uRadius = GL20.glGetUniformLocation(shaderProgram, "uRadius");
            uAlpha = GL20.glGetUniformLocation(shaderProgram, "uAlpha");
            uTexture = GL20.glGetUniformLocation(shaderProgram, "uTexture");

            // Setup fullscreen triangle VBO/VAO
            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

            FloatBuffer buf = BufferUtils.createFloatBuffer(6);
            buf.put(new float[]{
                    -1.0f, -1.0f,
                     3.0f, -1.0f,
                    -1.0f,  3.0f
            }).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);

            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            initialized = true;
        } catch (Throwable t) {
            shaderFailed = true;
        }
    }

    private void setupTextures(int width, int height, int restoreFbo) {
        if (sceneTexture != -1 && width == lastWidth && height == lastHeight) return;

        lastWidth = width;
        lastHeight = height;

        if (sceneTexture == -1) sceneTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);

        if (pingFbo == -1) pingFbo = GL30.glGenFramebuffers();
        if (pingTexture == -1) pingTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, pingTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, pingFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, pingTexture, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, restoreFbo);
    }

    public void renderBlur(GuiGraphicsExtractor context, float radius, float alpha) {
        renderBlur(context, (List<float[]>) null, radius, alpha);
    }

    public void renderBlur(GuiGraphicsExtractor context, float x, float y, float w, float h, float radius, float alpha) {
        java.util.List<float[]> list = new java.util.ArrayList<>();
        list.add(new float[]{ x, y, x + w, y + h });
        renderBlur(context, list, radius, alpha);
    }

    public void renderBlur(GuiGraphicsExtractor context, List<float[]> regions, float radius) {
        float alpha = (me.collective.module.render.Blur.INSTANCE != null && me.collective.module.render.Blur.INSTANCE.opacity != null)
                ? me.collective.module.render.Blur.INSTANCE.opacity.getFloatValue() : 0.65f;
        renderBlur(context, regions, radius, alpha);
    }

    public void renderBlur(GuiGraphicsExtractor context, List<float[]> regions, float radius, float alpha) {
        if (context != null) {
            context.extractDeferredElements(0, 0, 0f);
        }

        int sw = mc.getWindow().getWidth();
        int sh = mc.getWindow().getHeight();
        if (sw <= 0 || sh <= 0) return;

        init();

        if (shaderFailed) {
            renderFallback(context, regions, alpha);
            return;
        }

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);

        try {
            setupTextures(sw, sh, prevFbo);

            // 1. Capture screen from the currently active FBO
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevFbo);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, sw, sh);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindVertexArray(vao);
            GL20.glUseProgram(shaderProgram);

            // 2. Pass 1: Horizontal blur into pingFbo (opaque intermediate)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, pingFbo);
            GL11.glViewport(0, 0, sw, sh);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
            GL20.glUniform1i(uTexture, 0);
            GL20.glUniform2f(uDirection, 1.0f / (float) sw, 0.0f);
            GL20.glUniform1f(uRadius, radius);
            if (uAlpha != -1) GL20.glUniform1f(uAlpha, 1.0f);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            // 3. Pass 2: Vertical blur into target FBO with scissor and alpha blending
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GL11.glViewport(0, 0, sw, sh);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, pingTexture);
            GL20.glUniform2f(uDirection, 0.0f, 1.0f / (float) sh);
            if (uAlpha != -1) GL20.glUniform1f(uAlpha, Math.max(0.005f, Math.min(1.0f, alpha)));

            // Enable alpha blending for transparent blur
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            if (regions != null && !regions.isEmpty()) {
                double scale = mc.getWindow().getGuiScale();
                GL11.glEnable(GL11.GL_SCISSOR_TEST);

                for (float[] r : regions) {
                    float rx1 = r[0];
                    float ry1 = r[1];
                    float rx2 = r[2];
                    float ry2 = r[3];

                    int sx = (int) Math.floor(rx1 * scale);
                    int sy = (int) Math.floor((mc.getWindow().getGuiScaledHeight() - ry2) * scale);
                    int boxW = (int) Math.ceil((rx2 - rx1) * scale);
                    int boxH = (int) Math.ceil((ry2 - ry1) * scale);

                    if (boxW <= 0 || boxH <= 0) continue;
                    if (sx < 0) { boxW += sx; sx = 0; }
                    if (sy < 0) { boxH += sy; sy = 0; }
                    if (sx + boxW > sw) boxW = sw - sx;
                    if (sy + boxH > sh) boxH = sh - sy;
                    if (boxW <= 0 || boxH <= 0) continue;

                    GL11.glScissor(sx, sy, boxW, boxH);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                }
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            }

            // Restore state
            if (!prevScissor) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            if (!prevBlend) {
                GL11.glDisable(GL11.GL_BLEND);
            } else {
                GL11.glEnable(GL11.GL_BLEND);
            }
            if (prevDepth) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            } else {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            GL30.glBindVertexArray(prevVao);
            GL20.glUseProgram(prevProgram);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GL13.glActiveTexture(prevActiveTex);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
        } catch (Throwable t) {
            renderFallback(context, regions, alpha);
        }
    }

    public void renderFallback(GuiGraphicsExtractor context, List<float[]> regions) {
        renderFallback(context, regions, 0.65f);
    }

    public void renderFallback(GuiGraphicsExtractor context, List<float[]> regions, float alpha) {
        if (regions == null || regions.isEmpty()) {
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            int a = (int) (Math.max(0.1f, Math.min(1.0f, alpha)) * 90);
            RenderUtil.drawRect(context, 0, 0, w, h, new Color(10, 10, 16, a));
            return;
        }
        for (float[] r : regions) {
            float x1 = r[0];
            float y1 = r[1];
            float x2 = r[2];
            float y2 = r[3];

            // Deep blurred glass tint backing - softer and transparent
            RenderUtil.drawRect(context, x1, y1, x2, y2, new Color(14, 14, 20, 65));
            RenderUtil.drawRect(context, x1, y1, x2, y2, new Color(30, 30, 38, 30));
            RenderUtil.drawOutline(context, x1, y1, x2 - x1, y2 - y1, 1, new Color(255, 255, 255, 15).getRGB());
        }
    }
}