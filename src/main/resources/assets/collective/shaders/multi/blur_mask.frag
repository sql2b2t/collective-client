#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;
uniform sampler2D u_Mask;

uniform vec2 u_Direction;
uniform float u_Width;
uniform float u_Sigma;

out vec4 color;

float pdf(float x, float sigma) {
    return exp(-((x*x) / (sigma*sigma)));
}

void main() {
    float radiusMul = texture(u_Mask, v_TexCoord).a;
    if (radiusMul == 0) {
        color = texture(u_Texture, v_TexCoord);
        return;
    }
    float fullRadius = (u_Width * radiusMul);
    float sum = 0;
    for(float j = -fullRadius; j <= fullRadius; j++) {
        sum += pdf(j, u_Sigma);
    }
    vec4 blurred = vec4(0);
    for(float j = -fullRadius; j <= fullRadius; j+=1.0) {
        float normalizedWeight = pdf(j, u_Sigma) / sum;
        vec2 offset = v_OneTexel * j * u_Direction;
        vec4 current = texture(u_Texture, v_TexCoord + offset);
        blurred += current * normalizedWeight;
    }
    color = blurred;
}