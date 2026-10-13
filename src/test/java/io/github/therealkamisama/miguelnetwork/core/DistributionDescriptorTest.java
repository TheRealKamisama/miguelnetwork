package io.github.therealkamisama.miguelnetwork.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DistributionDescriptorTest {
    @Test
    void parsesCurseForgeDescriptor() throws Exception {
        DistributionDescriptor descriptor = DistributionDescriptor.parse(new ByteArrayInputStream(
                "{\"channel\":\"curseforge\",\"bundledWstunnel\":false}".getBytes(StandardCharsets.UTF_8)));

        assertEquals("curseforge", descriptor.channel());
        assertFalse(descriptor.bundledWstunnel());
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThrows(IOException.class, () -> DistributionDescriptor.parse(new ByteArrayInputStream(
                "{\"channel\":\"curseforge\"}".getBytes(StandardCharsets.UTF_8))));
    }
}
