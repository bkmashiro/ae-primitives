package dev.yuzhe.aeprimitives.kinetics.catalyst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CatalystDefinitionTest {
    @Test
    void parsesDataDrivenFanTypeAndSpecializedFluidVisual() {
        var json = JsonParser.parseString("""
                {
                  "activation": ["minecraft:water_bucket", "#c:water_buckets"],
                  "fan_processing_type": "create:splashing",
                  "install_remainder": "minecraft:bucket",
                  "removal_result": "minecraft:water_bucket",
                  "display": {"kind": "fluid", "id": "minecraft:water", "color": "#55aaff"}
                  }
                """).getAsJsonObject();

        var definition = CatalystDefinition.parse(ResourceLocation.parse("test:water"), json);

        assertThat(definition.activation()).containsExactly("minecraft:water_bucket", "#c:water_buckets");
        assertThat(definition.fanProcessingType()).isEqualTo("create:splashing");
        assertThat(definition.installRemainder()).contains(ResourceLocation.parse("minecraft:bucket"));
        assertThat(definition.removalResult()).contains(ResourceLocation.parse("minecraft:water_bucket"));
        assertThat(definition.display().kind()).isEqualTo(CatalystVisual.Kind.FLUID);
        assertThat(definition.display().resource()).contains(ResourceLocation.parse("minecraft:water"));
        assertThat(definition.display().tint()).contains(0xff55aaff);
    }

    @Test
    void defaultsToRenderingTheInstalledItem() {
        var json = JsonParser.parseString("""
                {
                  "activation": ["#example:fan_catalysts"],
                  "fan_processing_type": "example:custom"
                }
                """).getAsJsonObject();

        var definition = CatalystDefinition.parse(ResourceLocation.parse("test:custom"), json);

        assertThat(definition.display().kind()).isEqualTo(CatalystVisual.Kind.ITEM);
        assertThat(definition.display().resource()).isEmpty();
    }

    @Test
    void rejectsDefinitionsWithoutActivationItems() {
        var json = JsonParser.parseString("{\"fan_processing_type\":\"create:splashing\"}").getAsJsonObject();
        assertThatThrownBy(() -> CatalystDefinition.parse(ResourceLocation.parse("test:broken"), json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activation");
    }
}
