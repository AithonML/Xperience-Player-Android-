package com.xperience.player.ui.liquidglass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import org.intellij.lang.annotations.Language

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object LiquidGlassShader {

    @Language("AGSL")
    const val SHADER_SRC = """
        uniform shader composited;
        uniform float2 uResolution;
        uniform float uCornerRadius;
        uniform float uRefraction;
        uniform float uSpecular;
        uniform float4 uTintColor;

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = uResolution * 0.5;
            float2 p = fragCoord - halfSize;
            float r = min(uCornerRadius, min(halfSize.x, halfSize.y));
            float dist = sdRoundedBox(p, halfSize, r);

            // Outside the rounded shape
            if (dist > 1.0) {
                return half4(0.0);
            }

            // Bezel meniscus region (edge refraction zone)
            float minDim = min(halfSize.x, halfSize.y);
            float bezelWidth = clamp(minDim * 0.22, 6.0, 20.0);
            float edgeDist = clamp(-dist / bezelWidth, 0.0, 1.0);

            // Compute normal XY from distance field gradient
            float eps = 1.0;
            float dX = sdRoundedBox(p + float2(eps, 0.0), halfSize, r) - sdRoundedBox(p - float2(eps, 0.0), halfSize, r);
            float dY = sdRoundedBox(p + float2(0.0, eps), halfSize, r) - sdRoundedBox(p - float2(0.0, eps), halfSize, r);
            float2 normalXY = normalize(float2(dX, dY));

            // Toroidal optical meniscus bend: 0 in center, peaks near curved rim
            float bend = sin((1.0 - edgeDist) * 1.5707963);
            float2 displacement = normalXY * bend * uRefraction;

            // Chromatic dispersion: offset R, G, and B slightly differently for real glass look
            float2 coordR = clamp(fragCoord + displacement * 1.04, float2(0.0), uResolution);
            float2 coordG = clamp(fragCoord + displacement * 1.00, float2(0.0), uResolution);
            float2 coordB = clamp(fragCoord + displacement * 0.96, float2(0.0), uResolution);

            half4 cR = composited.eval(coordR);
            half4 cG = composited.eval(coordG);
            half4 cB = composited.eval(coordB);
            half4 refracted = half4(cR.r, cG.g, cB.b, max(max(cR.a, cG.a), cB.a));

            // Crystal-clear dark tinting
            half4 tinted = mix(refracted, uTintColor, uTintColor.a);

            // 3D Fresnel Specular Rim Lighting
            // Light vector coming from elevated top-left
            float3 N = normalize(float3(normalXY * bend * 1.5, 1.0 - bend * 0.6));
            float3 L = normalize(float3(-0.5, -0.7, 0.8));
            float3 V = float3(0.0, 0.0, 1.0);
            float3 H = normalize(L + V);
            float NdotH = max(dot(N, H), 0.0);
            float specular = pow(NdotH, 32.0) * uSpecular * (1.0 - edgeDist);

            // Rim meniscus bright edge
            float rimGlow = smoothstep(2.0, 0.0, abs(dist)) * 0.35;

            half4 illuminated = tinted + half4(half3(specular + rimGlow), 0.0);

            // Anti-aliased outer boundary
            float edgeAlpha = smoothstep(1.0, -0.5, dist);
            return illuminated * edgeAlpha;
        }
    """

    fun createShader(): RuntimeShader {
        return RuntimeShader(SHADER_SRC)
    }
}
