#version 330 core

uniform sampler2D u_Texture;

uniform int u_Width;

out vec4 color;

void main() {
    ivec2 coord = ivec2(gl_FragCoord.xy);
    int last = textureSize(u_Texture, 0).x - 1;
    int nearest = 255;

    for (int x = 0; x <= u_Width; x++) {
        if (texelFetch(u_Texture, ivec2(clamp(coord.x - x, 0, last), coord.y), 0).a != 0.0
                || texelFetch(u_Texture, ivec2(clamp(coord.x + x, 0, last), coord.y), 0).a != 0.0) {
            nearest = x;
            break;
        }
    }

    color = vec4(float(nearest) / 255.0, 0.0, 0.0, 1.0);
}
