package com.inza.standaurafx.client.render;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AuraRuntimeSettings {
    private static final Map<String, Parameter> PARAMETERS = new LinkedHashMap<>();

    private static final AuraMode DEFAULT_AURA_MODE = AuraMode.AUTO;
    private static AuraMode auraMode = DEFAULT_AURA_MODE;

    private static final Parameter CHAOS = register("chaos", 0.2F, 0.0F, 1.0F);
    private static final Parameter GLOBAL_ALPHA = register("globalAlpha", 0.9F, 0.0F, 2.0F);
    private static final Parameter FRAMEBUFFER_SCALE = register("framebufferScale", 0.5F, 0.25F, 1.0F);

    private static final Parameter SHAPE_SCALE_X = register("shapeScaleX", 0.42F, 0.05F, 2.0F);
    private static final Parameter SHAPE_SCALE_Y = register("shapeScaleY", 0.68F, 0.05F, 2.0F);
    private static final Parameter SHAPE_OFFSET_X = register("shapeOffsetX", 0.0F, -1.0F, 1.0F);
    private static final Parameter SHAPE_OFFSET_Y = register("shapeOffsetY", 0.05F, -1.0F, 1.0F);

    private static final Parameter BASE_AURA_WIDTH = register("baseAuraWidth", 0.050F, 0.0F, 0.5F);
    private static final Parameter AURA_WIDTH_CHAOS = register("auraWidthChaos", 0.028F, 0.0F, 0.5F);
    private static final Parameter EDGE_WARP_STRENGTH = register("edgeWarpStrength", 0.010F, 0.0F, 0.2F);
    private static final Parameter AURA_THICKNESS = register("auraThickness", 0.45F, 0.0F, 2.0F);
    private static final Parameter NOISE_SCALE = register("noiseScale", 10.0F, 0.0F, 100.0F);

    private static final Parameter FILL_ALPHA_BASE = register("fillAlphaBase", 0.32F, 0.0F, 2.0F);
    private static final Parameter FILL_ALPHA_FLOW = register("fillAlphaFlow", 0.20F, 0.0F, 2.0F);
    private static final Parameter CORE_ALPHA = register("coreAlpha", 0.16F, 0.0F, 2.0F);
    private static final Parameter EDGE_ALPHA_BASE = register("edgeAlphaBase", 0.80F, 0.0F, 2.0F);
    private static final Parameter EDGE_ALPHA_FLOW = register("edgeAlphaFlow", 0.25F, 0.0F, 2.0F);
    private static final Parameter RIM_ALPHA = register("rimAlpha", 0.42F, 0.0F, 2.0F);

    private static final Parameter INNER_HIGHLIGHT_BASE = register("innerHighlightBase", 0.35F, 0.0F, 2.0F);
    private static final Parameter INNER_HIGHLIGHT_FLOW = register("innerHighlightFlow", 0.20F, 0.0F, 2.0F);
    private static final Parameter OUTER_HIGHLIGHT_BASE = register("outerHighlightBase", 0.70F, 0.0F, 2.0F);
    private static final Parameter OUTER_HIGHLIGHT_FLOW = register("outerHighlightFlow", 0.25F, 0.0F, 2.0F);
    private static final Parameter EDGE_HIGHLIGHT_STRENGTH = register("edgeHighlightStrength", 0.55F, 0.0F, 2.0F);

    private AuraRuntimeSettings() {
    }

    public static float chaos() {
        return CHAOS.get();
    }

    public static float globalAlpha() {
        return GLOBAL_ALPHA.get();
    }

    public static float framebufferScale() {
        return FRAMEBUFFER_SCALE.get();
    }

    public static float shapeScaleX() {
        return SHAPE_SCALE_X.get();
    }

    public static float shapeScaleY() {
        return SHAPE_SCALE_Y.get();
    }

    public static float shapeOffsetX() {
        return SHAPE_OFFSET_X.get();
    }

    public static float shapeOffsetY() {
        return SHAPE_OFFSET_Y.get();
    }

    public static float baseAuraWidth() {
        return BASE_AURA_WIDTH.get();
    }

    public static float auraWidthChaos() {
        return AURA_WIDTH_CHAOS.get();
    }

    public static float edgeWarpStrength() {
        return EDGE_WARP_STRENGTH.get();
    }

    public static float auraThickness() {
        return AURA_THICKNESS.get();
    }

    public static float noiseScale() {
        return NOISE_SCALE.get();
    }

    public static float fillAlphaBase() {
        return FILL_ALPHA_BASE.get();
    }

    public static float fillAlphaFlow() {
        return FILL_ALPHA_FLOW.get();
    }

    public static float coreAlpha() {
        return CORE_ALPHA.get();
    }

    public static float edgeAlphaBase() {
        return EDGE_ALPHA_BASE.get();
    }

    public static float edgeAlphaFlow() {
        return EDGE_ALPHA_FLOW.get();
    }

    public static float rimAlpha() {
        return RIM_ALPHA.get();
    }

    public static float innerHighlightBase() {
        return INNER_HIGHLIGHT_BASE.get();
    }

    public static float innerHighlightFlow() {
        return INNER_HIGHLIGHT_FLOW.get();
    }

    public static float outerHighlightBase() {
        return OUTER_HIGHLIGHT_BASE.get();
    }

    public static float outerHighlightFlow() {
        return OUTER_HIGHLIGHT_FLOW.get();
    }

    public static float edgeHighlightStrength() {
        return EDGE_HIGHLIGHT_STRENGTH.get();
    }

    public static AuraMode getAuraMode() {
        return auraMode;
    }

    public static void setAuraMode(AuraMode mode) {
        auraMode = mode == null ? DEFAULT_AURA_MODE : mode;
    }

    public static Parameter getParameter(String name) {
        return PARAMETERS.get(normalizeName(name));
    }

    public static Collection<Parameter> getParameters() {
        return Collections.unmodifiableCollection(PARAMETERS.values());
    }

    public static void resetAll() {
        for (Parameter parameter : PARAMETERS.values()) {
            parameter.reset();
        }
    }

    private static Parameter register(String name, float defaultValue, float minValue, float maxValue) {
        Parameter parameter = new Parameter(name, defaultValue, minValue, maxValue);
        PARAMETERS.put(normalizeName(name), parameter);
        return parameter;
    }

    private static String normalizeName(String name) {
        return name == null
            ? ""
            : name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(".", "");
    }

    public enum AuraMode {
        CLOSE("close"),
        AUTO("auto"),
        OPEN("open");

        private final String name;

        AuraMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static AuraMode fromName(String name) {
            String normalizedName = normalizeName(name);
            for (AuraMode mode : values()) {
                if (normalizeName(mode.name).equals(normalizedName)) {
                    return mode;
                }
            }
            return null;
        }
    }

    public static final class Parameter {
        private final String name;
        private final float defaultValue;
        private final float minValue;
        private final float maxValue;
        private float value;

        private Parameter(String name, float defaultValue, float minValue, float maxValue) {
            this.name = name;
            this.defaultValue = defaultValue;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.value = defaultValue;
        }

        public String getName() {
            return name;
        }

        public float getDefaultValue() {
            return defaultValue;
        }

        public float getMinValue() {
            return minValue;
        }

        public float getMaxValue() {
            return maxValue;
        }

        public float get() {
            return value;
        }

        public boolean canSet(float newValue) {
            return !Float.isNaN(newValue) && !Float.isInfinite(newValue) && newValue >= minValue && newValue <= maxValue;
        }

        public void set(float newValue) {
            value = newValue;
        }

        public void reset() {
            value = defaultValue;
        }
    }
}
