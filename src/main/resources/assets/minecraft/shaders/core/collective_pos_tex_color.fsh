#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a == 0.0) {
        discard;
    }
    float luminance = min(color.r + color.g + color.b, 1.0);
    fragColor = vec4(luminance, luminance, luminance, color.a) * vertexColor * ColorModulator;
}
