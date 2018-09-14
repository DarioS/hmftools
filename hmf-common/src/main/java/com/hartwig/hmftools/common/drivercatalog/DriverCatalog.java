package com.hartwig.hmftools.common.drivercatalog;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public interface DriverCatalog {

    @NotNull
    String gene();

    @NotNull
    DriverCategory category();

    @NotNull
    DriverType driver();

    double driverLikelihood();

    double dndsLikelihood();

    long missense();

    long nonsense();

    long splice();

    long inframe();

    long frameshift();

}
