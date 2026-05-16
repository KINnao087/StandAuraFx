#version 120

uniform sampler2D uMaskTex;
uniform vec2 uTexelSize;
uniform vec2 uMaskUvMin;
uniform vec2 uMaskUvMax;

uniform float uTime;
uniform float uChaos;
uniform float uGlobalAlpha;

// Shape controls.
uniform vec2 uShapeScale;
uniform vec2 uShapeOffset;
uniform float uAspect;

// Aura controls.
uniform float uAntiAlias;
uniform float uBaseAuraWidth;
uniform float uAuraWidthChaos;
uniform float uEdgeWarpStrength;
uniform float uNoiseScale;

// Alpha controls.
uniform float uFillAlphaBase;
uniform float uFillAlphaFlow;
uniform float uCoreAlpha;
uniform float uEdgeAlphaBase;
uniform float uEdgeAlphaFlow;
uniform float uRimAlpha;

// Color controls.
uniform vec3 uInnerColorA;
uniform vec3 uInnerColorB;
uniform vec3 uOuterColorA;
uniform vec3 uOuterColorB;
uniform vec3 uEdgeColor;
uniform vec3 uRimColor;

// Color blend controls.
uniform float uInnerHighlightBase;
uniform float uInnerHighlightFlow;
uniform float uOuterHighlightBase;
uniform float uOuterHighlightFlow;
uniform float uEdgeHighlightStrength;

varying vec2 vUv;

const float PI = 3.14159265358979323846;
const int DIRECTION_COUNT = 16;
const int STEP_COUNT = 32;
const float SDF_SEARCH_RADIUS = 0.16;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(a, b, u.x)
         + (c - a) * u.y * (1.0 - u.x)
         + (d - b) * u.x * u.y;
}

float fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;

    for (int i = 0; i < 5; i++) {
        value += amp * noise(p);
        p *= 2.0;
        amp *= 0.5;
    }

    return value;
}

float sampleMask(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return 0.0;
    }

    vec4 maskColor = texture2D(uMaskTex, uv);
    return max(maskColor.a, max(maskColor.r, max(maskColor.g, maskColor.b)));
}

float sampleMaskAtShape(vec2 p) {
    vec2 localUv = vec2((p.x / max(uAspect, 0.0001)) * 0.5 + 0.5, p.y * 0.5 + 0.5);
    return sampleMask(mix(uMaskUvMin, uMaskUvMax, localUv));
}

float standAuraSDF(vec2 p) {
    float centerMask = sampleMaskAtShape(p);
    float inside = step(0.05, centerMask);

    float best = 1000.0;

    for (int directionIndex = 0; directionIndex < DIRECTION_COUNT; directionIndex++) {
        float angle = 2.0 * PI * (float(directionIndex) / float(DIRECTION_COUNT));
        vec2 dir = vec2(cos(angle), sin(angle));

        for (int stepIndex = 1; stepIndex <= STEP_COUNT; stepIndex++) {
            float shapeDist = SDF_SEARCH_RADIUS * (float(stepIndex) / float(STEP_COUNT));
            float sampleInside = step(0.05, sampleMaskAtShape(p + dir * shapeDist));

            if (abs(sampleInside - inside) > 0.5) {
                best = min(best, shapeDist);
                break;
            }
        }
    }

    if (best > 999.0) {
        best = 2.0;
    }

    return inside > 0.5 ? -best : best;
}

void main() {
    float CHAOS = clamp(uChaos, 0.0, 1.0);

    vec2 p = vUv * 2.0 - 1.0;
    p.x *= uAspect;

    float d = standAuraSDF(p);
    float aa = max(uAntiAlias, 0.0001);

    vec2 q = p * mix(uNoiseScale, uNoiseScale * 1.25, CHAOS);

    float n1 = fbm(q + vec2(0.0, -uTime * mix(1.6, 1.8, CHAOS)));
    float n2 = fbm(q * mix(1.7, 1.9, CHAOS) + vec2(2.7, -uTime * mix(2.8, 3.2, CHAOS)));

    float n3 = fbm(vec2(
        q.x * mix(0.8, 0.9, CHAOS) - uTime * mix(0.7, 0.9, CHAOS),
        q.y * mix(2.4, 2.8, CHAOS) - uTime * mix(1.9, 2.3, CHAOS)
    ));

    float n4 = fbm(q * mix(2.2, 3.4, CHAOS) + vec2(
        -uTime * mix(2.8, 4.5, CHAOS),
         uTime * mix(1.2, 2.0, CHAOS)
    ));

    float flow = clamp(
        mix(0.45, 0.30, CHAOS) * n1 +
        mix(0.35, 0.25, CHAOS) * n2 +
        mix(0.20, 0.20, CHAOS) * n3 +
        mix(0.00, 0.25, CHAOS) * n4,
        0.0,
        1.0
    );

    float tonguePower = mix(2.5, 5.0, CHAOS);

    float tongue1 = pow(
        0.5 + 0.5 * sin(
            p.y * mix(34.0, 52.0, CHAOS) +
            uTime * mix(7.0, 11.0, CHAOS) +
            flow * mix(7.0, 12.0, CHAOS)
        ),
        tonguePower
    );

    float tongue2 = pow(
        0.5 + 0.5 * sin(
            p.x * mix(20.0, 34.0, CHAOS) -
            uTime * mix(5.0, 8.0, CHAOS) +
            flow * mix(8.0, 10.0, CHAOS)
        ),
        tonguePower
    );

    float tongue3 = pow(
        0.5 + 0.5 * sin(
            (p.x + p.y) * mix(26.0, 40.0, CHAOS) +
            uTime * mix(5.5, 9.0, CHAOS) +
            flow * mix(6.0, 9.0, CHAOS)
        ),
        mix(2.8, 6.0, CHAOS)
    );

    float tongue = max(max(tongue1, tongue2), tongue3);

    float jag = smoothstep(
        mix(0.25, 0.40, CHAOS),
        mix(0.85, 0.92, CHAOS),
        flow * mix(0.85, 0.75, CHAOS) + tongue * mix(0.40, 0.75, CHAOS)
    );

    jag = pow(jag, mix(1.0, 1.35, CHAOS));

    float spikePulse =
        mix(0.012, 0.020, CHAOS) * tongue1 +
        mix(0.010, 0.018, CHAOS) * tongue2 +
        mix(0.008, 0.016, CHAOS) * tongue3;

    float auraWidth =
        mix(uBaseAuraWidth, uBaseAuraWidth * 0.80, CHAOS) +
        mix(uAuraWidthChaos * 0.54, uAuraWidthChaos, CHAOS) * jag +
        spikePulse +
        mix(0.000, uAuraWidthChaos * 0.36, CHAOS) * smoothstep(0.55, 0.95, n4);

    float edgeWarp =
        mix(0.000, uEdgeWarpStrength, CHAOS) * (n4 - 0.5) +
        mix(0.000, uEdgeWarpStrength * 0.8, CHAOS) * (tongue - 0.5);

    float shellPos = auraWidth + edgeWarp;

    float outerMask = 1.0 - smoothstep(shellPos - aa, shellPos + aa, d);
    float innerCut = smoothstep(0.004, 0.018, d);
    float auraBand = outerMask * innerCut * step(0.0, d);

    float bandStart = 0.004;
    float bandEnd = max(shellPos, bandStart + 0.001);
    float bandT = clamp((d - bandStart) / (bandEnd - bandStart), 0.0, 1.0);

    float rim = auraBand * (1.0 - smoothstep(0.00, 0.28, bandT));
    float outerEdge = auraBand * smoothstep(0.45, 1.00, bandT);

    float wisps = smoothstep(0.20, 0.95, flow * 0.85 + tongue * 0.30);

    float fillAlpha = auraBand * (uFillAlphaBase + uFillAlphaFlow * wisps);
    float movingCoreAlpha = auraBand * smoothstep(0.55, 1.00, flow) * uCoreAlpha;
    float edgeAlpha = outerEdge * (uEdgeAlphaBase + uEdgeAlphaFlow * wisps);
    float innerRimAlpha = rim * uRimAlpha;

    float colorT = smoothstep(0.05, 1.00, bandT);

    vec3 auraInnerColor = mix(uInnerColorA, uInnerColorB, flow);
    auraInnerColor = mix(
        auraInnerColor,
        uRimColor,
        clamp(uInnerHighlightBase + uInnerHighlightFlow * wisps, 0.0, 1.0)
    );

    vec3 auraOuterColor = mix(
        uOuterColorA,
        uOuterColorB,
        clamp(uOuterHighlightBase + uOuterHighlightFlow * wisps, 0.0, 1.0)
    );

    vec3 auraFillColor = mix(auraInnerColor, auraOuterColor, colorT);

    vec3 finalEdgeColor = mix(
        auraFillColor,
        uEdgeColor,
        smoothstep(0.55, 1.00, bandT) * uEdgeHighlightStrength
    );

    vec3 color = auraFillColor;
    color += auraFillColor * movingCoreAlpha * 0.8;
    color = mix(color, finalEdgeColor, clamp(edgeAlpha, 0.0, 1.0));
    color = mix(color, uRimColor, clamp(innerRimAlpha, 0.0, 1.0));

    float alpha = clamp(
        fillAlpha + movingCoreAlpha + edgeAlpha + innerRimAlpha,
        0.0,
        1.0
    ) * uGlobalAlpha;

    if (alpha <= 0.001) {
        discard;
    }

    gl_FragColor = vec4(color, alpha);
}
