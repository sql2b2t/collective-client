#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;
uniform bool u_Image;
uniform sampler2D u_Overlay;
uniform float u_OverlayAlpha;
uniform int u_Width;
uniform bool u_FastLines;
uniform int u_ShapeMode;
uniform bool u_GlowInner;
uniform int u_GlowQuality;
uniform float u_Time;
uniform bool u_Smooth;

uniform vec4 u_FillColor;
uniform vec4 u_FillColor2;
uniform vec4 u_FillColor3;

uniform vec4 u_OutlineColor;
uniform vec4 u_OutlineColor2;
uniform vec4 u_OutlineColor3;

uniform int u_Dots;
uniform int u_DotsRadius;
uniform float u_DotsAlpha;
uniform float u_Step;
uniform float u_Glow;

uniform bool u_Fade;

out vec4 color;

float wave(float x) {
    return abs(fract(x / 1.0) * 2.0 - 1.0); // triangle pattern between 0 and 1
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 gradient(vec4 rgb1, vec4 rgb2, vec4 rgb3, float step, float time) {
    vec2 frag = vec2(gl_FragCoord.xy);
    float distance = frag.x + frag.y;

    distance = distance / step;
    distance = wave((frag.x + frag.y) / step + time);
    if (!u_Smooth) {
        return mix(rgb1, rgb3, distance);
    }

    if (distance < 0.5) {
        float t = distance / 0.5;
        return mix(rgb1, rgb2, t);
    } else {
        float t = (distance - 0.5) / 0.5;
        return mix(rgb2, rgb3, t);
    }
}

void main() {
    vec4 center = texture(u_Texture, v_TexCoord);

    vec4 outline = gradient(u_OutlineColor, u_OutlineColor2, u_OutlineColor3, u_Step * 300., u_Time);

    if (center.a != 0.0) {
        float a = center.a;
        vec4 fill = gradient(u_FillColor, u_FillColor2, u_FillColor3, u_Step * 300., u_Time);
        if (decorator(u_Dots, u_DotsRadius)) {
            center = vec4(fill.rgb, u_DotsAlpha);
        } else if (u_Image) {
            vec4 overlay = texture(u_Overlay, v_TexCoord * vec2(1.0, -1.0));
            if (overlay.a == 0.0) discard;
            center.rgb = mix(overlay.rgb, fill.rgb, fill.a);
            center.a = u_OverlayAlpha;
        } else {
            center = fill;
        }
        if (u_Width != 0 && u_GlowInner) {
            center = blending(center, bloom(outline, u_Glow), blurFactor(false, u_Width, u_GlowQuality, u_FastLines, u_Fade));
        }
        center.a *= a;

    } else if (u_Width > 0.0) {
        float blurFactor = blurFactor(true, u_Width, u_GlowQuality, u_FastLines, u_Fade) * u_Glow;
        if (blurFactor == 0.0) discard;

        if (u_GlowQuality * u_Width > 1) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    if (x == 0 && y == 0)
                        continue;

                    vec4 tex = texture(u_Texture, v_TexCoord + v_OneTexel * vec2(x, y));
                    if (tex.a > 0.0) {
                        center.rgb = outline.rgb;
                        center.a = tex.a;
                        break;
                    }
                }
            }
        }

        if (center.a == 0.0) {
            center = outline;
            center.a *= blurFactor;
        }
    }

    color = center;
}
