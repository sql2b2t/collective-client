#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;

uniform int u_Pass;
uniform int u_Width;
uniform float u_Sigma;
uniform float u_Bloom;
uniform float u_Opacity;

out vec4 color;

void main() {
    float sigma = max(u_Sigma, 0.0001);
    float gaussExp = -1.0 / (2.0 * sigma * sigma);

    vec2 direction = u_Pass == 0 ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec2 step = v_OneTexel * direction;

    vec4 sum = texture(u_Texture, v_TexCoord);
    float weightSum = 1.0;

    for (int i = 1; i <= u_Width; i += 2) {
        float f1 = float(i);
        float f2 = f1 + 1.0;
        float w1 = exp(f1 * f1 * gaussExp);
        float w2 = i + 1 <= u_Width ? exp(f2 * f2 * gaussExp) : 0.0;
        float weight = w1 + w2;
        vec2 offset = step * ((f1 * w1 + f2 * w2) / weight);

        sum += (texture(u_Texture, v_TexCoord + offset) + texture(u_Texture, v_TexCoord - offset)) * weight;
        weightSum += weight * 2.0;
    }

    vec4 blurred = sum / weightSum;

    if (u_Pass == 0) {
        color = blurred;
        return;
    }

    if (blurred.a <= 0.0) {
        color = vec4(0.0);
        return;
    }

    color = vec4(min(blurred.rgb / blurred.a, 1.0), min(blurred.a * u_Bloom, u_Opacity));
}
