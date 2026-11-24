#version 330 core

out vec4 color;

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D DepthSampler;
uniform sampler2D u_Texture;

uniform float FocusDistance;
uniform float BlurStrength;

uniform vec2 u_Direction;

float getDepth(vec2 uv) {
    return texture(DepthSampler, uv).r;
}

void main() {
    float depth = getDepth(v_TexCoord);

    float depthDiff = abs(depth - FocusDistance);
    float blurAmount = max(0.0, depthDiff);
    float finalStrength = depth > FocusDistance ? BlurStrength : 0;

    float blur = clamp(blurAmount * finalStrength, 0.0, 1.0);

    if (blur < 0.01) {
        color = texture(u_Texture, v_TexCoord);
        return;
    }

    vec4 dcolor = vec4(0.0);
    float totalWeight = 0.0;

    int samples = 6;
    float sigma = float(samples) / 2.0;

    for (int i = -samples; i <= samples; i++) {
        float fi = float(i);

        float weight = exp(-(fi * fi) / (2.0 * sigma * sigma));

        vec2 offset = fi * blur * v_OneTexel * u_Direction;

        dcolor += texture(u_Texture, v_TexCoord + offset) * weight;
        totalWeight += weight;
    }

    color = dcolor / totalWeight;
}