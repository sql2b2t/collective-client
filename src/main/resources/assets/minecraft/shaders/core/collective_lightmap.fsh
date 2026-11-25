#version 150

uniform float AmbientLightFactor;
uniform float SkyFactor;
uniform float BlockFactor;
uniform int UseBrightLightmap;
uniform vec3 SkyLightColor;
uniform float NightVisionFactor;
uniform float DarknessScale;
uniform float DarkenWorldFactor;
uniform float BrightnessFactor;
uniform vec3 AmbienceColor;

in vec2 texCoord;

out vec4 fragColor;

float get_brightness(float level) {
    float curved_level = level / (4.0 - 3.0 * level);
    return mix(curved_level, 1.0, AmbientLightFactor);
}

void main() {
    if (texCoord.x * 16 < 15 || texCoord.y * 16 < 15) {
        fragColor = vec4(AmbienceColor, 1.0);
    } else {
        fragColor = vec4(1.0);
    }
}
