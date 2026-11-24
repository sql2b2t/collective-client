#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;

uniform vec2 u_Direction;
uniform float u_Width;
uniform float u_Sigma;
uniform float u_Bloom;
uniform float u_Modulate;
uniform float u_Opacity;

out vec4 color;

float pdf(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma));
}

void main() {
    int radius = int(u_Width);
    float totalWeight = pdf(0.0, u_Sigma);
    vec4 blurred = texture(u_Texture, v_TexCoord) * totalWeight;
    for (int j = 1; j <= radius; ++j) {
        float weight = pdf(float(j), u_Sigma);
        vec2 offset = v_OneTexel * float(j) * u_Direction;
        blurred += texture(u_Texture, v_TexCoord + offset) * weight;
        blurred += texture(u_Texture, v_TexCoord - offset) * weight;
        totalWeight += 2.0 * weight;
    }
    blurred /= totalWeight;
    float fac = max(blurred.a * u_Modulate, 1.0);
    color = vec4(min(blurred.rgb * fac, 1.0), min(blurred.a * u_Bloom, u_Opacity));
}
