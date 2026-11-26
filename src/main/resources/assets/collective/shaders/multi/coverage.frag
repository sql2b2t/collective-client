#version 330 core

uniform sampler2D u_Texture;

uniform int u_Width;
uniform int u_Step;

out vec4 color;

void main() {
    ivec2 coord = ivec2(gl_FragCoord.xy);
    int last = textureSize(u_Texture, 0).x - 1;
    float sum = 0.0;

    for (int x = -u_Width; x <= u_Width; x++) {
        sum += texelFetch(u_Texture, ivec2(clamp(coord.x + x * u_Step, 0, last), coord.y), 0).a;
    }

    color = vec4(pack16(sum / float(2 * u_Width + 1)), 0.0, 1.0);
}
