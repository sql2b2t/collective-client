#version 330

#moj_import <minecraft:fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;

noperspective in float lineEdge;
noperspective in float halfWidthPx;
noperspective in float outerHalfPx;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    float distPx = abs(lineEdge) * outerHalfPx;

    float coverage = clamp(halfWidthPx + 0.5 - distPx, 0.0, 1.0);

    color.a *= coverage;
    if (color.a < 0.001) discard;

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
