package com.batteryguard;

public final class BatterySnapshot {
    public enum PowerSource {
        AC,
        BATTERY,
        NO_BATTERY,
        UNKNOWN
    }

    private final boolean batteryInfoValid;
    private final int percent;
    private final PowerSource powerSource;
    private final int batteryFlag;
    private final String problem;

    private BatterySnapshot(
        boolean batteryInfoValid,
        int percent,
        PowerSource powerSource,
        int batteryFlag,
        String problem
    ) {
        this.batteryInfoValid = batteryInfoValid;
        this.percent = percent;
        this.powerSource = powerSource;
        this.batteryFlag = batteryFlag;
        this.problem = problem;
    }

    public static BatterySnapshot valid(int percent, boolean acConnected, int batteryFlag) {
        return new BatterySnapshot(
            true,
            percent,
            acConnected ? PowerSource.AC : PowerSource.BATTERY,
            batteryFlag,
            null
        );
    }

    public static BatterySnapshot noBattery(int batteryFlag) {
        return new BatterySnapshot(
            false,
            -1,
            PowerSource.NO_BATTERY,
            batteryFlag,
            null
        );
    }

    public static BatterySnapshot unavailable(String problem) {
        return new BatterySnapshot(
            false,
            -1,
            PowerSource.UNKNOWN,
            -1,
            problem
        );
    }

    public boolean isBatteryInfoValid() {
        return batteryInfoValid;
    }

    public int getPercent() {
        return percent;
    }

    public PowerSource getPowerSource() {
        return powerSource;
    }

    public int getBatteryFlag() {
        return batteryFlag;
    }

    public String getProblem() {
        return problem;
    }

    public boolean isOnBattery() {
        return batteryInfoValid && powerSource == PowerSource.BATTERY;
    }

    public boolean isAcConnected() {
        return powerSource == PowerSource.AC;
    }
}
