#version 330 core

out vec4 color;

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;
uniform bool u_Image;
uniform sampler2D u_Overlay;
uniform float u_OverlayAlpha;
uniform vec4 u_Fill;
uniform float u_Offset;
uniform float u_Strength;
uniform float u_StrengthLine;
uniform vec4 u_Outline;
uniform int u_Width;
uniform bool u_GlowInner;
uniform int u_GlowQuality;
uniform bool u_FastLines;
uniform int u_Dots;
uniform int u_DotsRadius;
uniform float u_DotsAlpha;
uniform float u_Glow;
uniform bool u_Fade;

vec3 calculate(vec3 c, float hue){
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    vec4 L = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 f = vec3(hue, d / (q.x + e), q.x);
    vec3 r = abs(fract(f.xxx + L.xyz) * 6.0 - L.www);
    return f.z * mix(L.xxx, clamp(r - L.xxx, 0.0, 1.0), f.y);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec4 cetner = texture(u_Texture, v_TexCoord);

    vec4 outline = u_Outline;
    if (u_Offset != 0.0 && u_StrengthLine != 0) {
        vec2 strength = (v_TexCoord * 3.0 * vec2(-u_StrengthLine, u_StrengthLine));
        float hue = float(mod (((strength.x + strength.y) + u_Offset), 1.0));
        outline = vec4(calculate(u_Outline.rgb, hue), u_Outline.w);
    }

    if (cetner.a != 0.0) {
        float a = cetner.a;
        vec4 fillColor = u_Fill;
        if (u_Offset != 0.0 && u_Strength != 0) {
            vec2 strength = (v_TexCoord * 3.0 * vec2(-u_Strength, u_Strength));
            float hue = float(mod (((strength.x + strength.y) + u_Offset), 1.0));
            fillColor = vec4(calculate(u_Fill.rgb, hue), u_Fill.w);
        }

        if (decorator(u_Dots, u_DotsRadius)) {
            cetner = vec4(fillColor.rgb, u_DotsAlpha);
        } else if (u_Image) {
            vec4 overlay = texture(u_Overlay, v_TexCoord * vec2(1.0, -1.0));
            if (overlay.a == 0.0) discard;
            cetner.rgb = mix(overlay.rgb, fillColor.rgb, fillColor.a);
            cetner.a = u_OverlayAlpha;
        } else {
            cetner = fillColor;
        }

        if (u_Width != 0 && u_GlowInner) {
            cetner = blending(cetner, bloom(outline, u_Glow), blurFactor(false, u_Width, u_GlowQuality, u_FastLines, u_Fade));
        }
        cetner.a *= a;
    } else if (u_Width > 0) {
        float blurFactor = blurFactor(true, u_Width, u_GlowQuality, u_FastLines, u_Fade) * u_Glow;
        if (blurFactor == 0.0) discard;

        if (u_GlowQuality * u_Width > 1) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    if (x == 0 && y == 0)
                        continue;

                    vec4 tex = texture(u_Texture, v_TexCoord + v_OneTexel * vec2(x, y));
                    if (tex.a > 0.0) {
                        cetner.rgb = outline.rgb;
                        cetner.a = tex.a;
                        break;
                    }
                }
            }
        }

        if (cetner.a == 0.0) {
            cetner = outline;
            cetner.a *= blurFactor;
        }
    }
    color = cetner;
}
