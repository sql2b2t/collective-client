#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4  vertexColor;

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

    fragColor = apply_fog(color,
        sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd, FogColor
    );
}