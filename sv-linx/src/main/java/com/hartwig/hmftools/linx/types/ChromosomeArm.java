package com.hartwig.hmftools.linx.types;

public enum ChromosomeArm
{
    P_ARM,
    Q_ARM,
    CENTROMERE,
    UNKNOWN;

    public final static String CHROMOSOME_ARM_P = "P";
    public final static String CHROMOSOME_ARM_Q = "Q";

    public static String asStr(final ChromosomeArm arm)
    {
        if(arm == P_ARM)
            return CHROMOSOME_ARM_P;
        else if(arm == Q_ARM)
            return CHROMOSOME_ARM_Q;
        else if(arm == CENTROMERE)
            return CENTROMERE.toString();
        else
            return UNKNOWN.toString();
    }

}
