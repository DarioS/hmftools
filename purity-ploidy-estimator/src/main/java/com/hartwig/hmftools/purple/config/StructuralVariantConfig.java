package com.hartwig.hmftools.purple.config;

import java.io.File;
import java.util.Optional;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public interface StructuralVariantConfig {
    default boolean enabled() {
        return file().isPresent();
    }

    Optional<File> file();
}
