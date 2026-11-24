#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;
in float LineWidth;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;

noperspective out float lineEdge;
noperspective out float halfWidthPx;
noperspective out float outerHalfPx;

const float VIEW_SHRINK = 1.0 - (1.0 / 256.0);
const mat4 VIEW_SCALE = mat4(
    VIEW_SHRINK, 0.0, 0.0, 0.0,
    0.0, VIEW_SHRINK, 0.0, 0.0,
    0.0, 0.0, VIEW_SHRINK, 0.0,
    0.0, 0.0, 0.0, 1.0
);


void main() {
    mat4 mv = VIEW_SCALE * ModelViewMat;

    vec4 clipPos = ProjMat * mv * vec4(Position, 1.0);

    vec3 viewPos = (mv * vec4(Position, 1.0)).xyz;
    vec3 viewDir = (mv * vec4(Normal, 0.0)).xyz;

    float dd = dot(viewDir, viewDir);
    float t = dd > 0.0 ? -dot(viewPos, viewDir) / dd : 0.0;
    vec3 refPos = viewPos + viewDir * t;

    vec4 refClip = ProjMat * vec4(refPos, 1.0);
    vec4 dirClip = ProjMat * vec4(viewDir, 0.0);

    vec2 dir = dirClip.xy * refClip.w - refClip.xy * dirClip.w;
    dir *= ScreenSize;
    if (dot(dir, dir) == 0.0) {
        dir = vec2(1.0, 0.0);
    }
    vec2 lineScreenDirection = normalize(dir);

    halfWidthPx = max(LineWidth, 0.0) * 0.5;
    outerHalfPx = halfWidthPx + 0.5;

    vec2 lineOffset = vec2(-lineScreenDirection.y, lineScreenDirection.x)
                    * (outerHalfPx * 2.0 / ScreenSize);
    if (lineOffset.x < 0.0) {
        lineOffset = -lineOffset;
    }

    float side = (gl_VertexID % 2 == 0) ? 1.0 : -1.0;
    gl_Position = vec4(clipPos.xy + side * lineOffset * clipPos.w, clipPos.zw);
    lineEdge = side;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color;
}