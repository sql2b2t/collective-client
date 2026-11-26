#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;
uniform int u_Width;
uniform float u_GlowMultiplier;
uniform float u_FillOpacity;

out vec4 color;

void main() {
    vec4 center = texture(u_Texture, v_TexCoord);

    if (center.a != 0.0) {
        center.a = u_FillOpacity;
    }
    else {
        if (u_Width == 0.0) discard;

        float dist = u_Width * u_Width + 1.0;

        for (int x = -u_Width; x <= u_Width; x++) {
            for (int y = -u_Width; y <= u_Width; y++) {
                vec4 offset = texture(u_Texture, v_TexCoord + v_OneTexel * vec2(x, y));

                if (offset.a != 0) {
                    dist = min(x * x + y * y - 1.0, dist);
                    center = offset;
                }
            }
        }

        float minDist = u_Width * u_Width;

        if (dist > minDist) center.a = 0.0;
        else {
            center.a = min((1.0 - (dist / minDist)) * u_GlowMultiplier, 1.0);
        }
    }

    color = center;
}