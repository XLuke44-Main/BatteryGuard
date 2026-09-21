package com.batteryguard;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class WindowsBatteryProvider {
    private static final long COMMAND_TIMEOUT_SECONDS = 4L;

    private static final String POWERSHELL_SCRIPT =
        "$ErrorActionPreference='Stop'; " +
        "$bat=Get-WmiObject -Class 'Win32_Battery' -ErrorAction Stop | " +
        "Where-Object { $_.EstimatedChargeRemaining -ge 0 } | Select-Object -First 1; " +
        "if($null -eq $bat){ Write-Output 'NO_BATTERY'; exit 0 }; " +
        "$status=$null; " +
        "try { " +
        "  $status=Get-WmiObject -Namespace 'root\\wmi' -Class 'BatteryStatus' -ErrorAction Stop | " +
        "    Where-Object { $_.Active -and $_.Voltage -gt 0 } | Select-Object -First 1 " +
        "} catch { " +
        "  try { " +
        "    $status=Get-CimInstance -Namespace 'root\\wmi' -ClassName 'BatteryStatus' -ErrorAction Stop | " +
        "      Where-Object { $_.Active -and $_.Voltage -gt 0 } | Select-Object -First 1 " +
        "  } catch { $status=$null } " +
        "}; " +
        "$percent=[int]$bat.EstimatedChargeRemaining; " +
        "$flag=[int]$bat.BatteryStatus; " +
        "$ac=-1; " +
        "if($null -ne $status) { " +
        "  if([bool]$status.PowerOnline) { $ac=1 } else { $ac=0 } " +
        "} else { " +
        "  switch($flag) { " +
        "    1 { $ac=0 }; 2 { $ac=1 }; 3 { $ac=1 }; 4 { $ac=0 }; 5 { $ac=0 }; " +
        "    6 { $ac=1 }; 7 { $ac=1 }; 8 { $ac=1 }; 9 { $ac=1 }; " +
        "    default { $ac=-1 } " +
        "  } " +
        "}; " +
        "if($percent -lt 0 -or $percent -gt 100 -or $ac -lt 0) { throw 'Windows battery provider returned invalid power status.' }; " +
        "Write-Output ($percent.ToString()+','+$ac.ToString()+','+$flag.ToString())";

    public BatterySnapshot query() {
        if (!isWindows()) {
            return BatterySnapshot.unavailable(
                "Battery Guard requires Windows; the current OS is not Windows."
            );
        }

        Process process = null;
        try {
            process = new ProcessBuilder(
                "powershell.exe",
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-Command", POWERSHELL_SCRIPT
            ).redirectErrorStream(true).start();

            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), Charset.forName("UTF-8"))
            );
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) output.append('\n');
                output.append(line);
                if (output.length() > 4096) break;
            }

            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy();
                process.destroyForcibly();
                return unavailable("Windows battery query timed out.");
            }

            if (process.exitValue() != 0) {
                return unavailable("Windows battery query failed: " + output.toString().trim());
            }

            String result = output.toString().trim();
            if ("NO_BATTERY".equals(result)) {
                return BatterySnapshot.noBattery(-1);
            }

            String[] parts = result.split(",");
            if (parts.length != 3) {
                return unavailable("Windows battery query returned unexpected data.");
            }

            int percent = Integer.parseInt(parts[0].trim());
            int ac = Integer.parseInt(parts[1].trim());
            int flag = Integer.parseInt(parts[2].trim());

            if (flag == 255 || ac == 255 || percent == 255) {
                return unavailable("Windows reports unknown battery power status.");
            }
            if ((ac != 0 && ac != 1) || percent < 0 || percent > 100) {
                return unavailable("Windows returned invalid battery power status.");
            }

            return BatterySnapshot.valid(percent, ac == 1, flag);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return unavailable("Windows battery query was interrupted.");
        } catch (Exception ex) {
            return unavailable(
                "Windows battery query failed: " + ex.getClass().getSimpleName() + ": " +
                String.valueOf(ex.getMessage())
            );
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private BatterySnapshot unavailable(String message) {
        return BatterySnapshot.unavailable(message);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
            .toLowerCase(Locale.ENGLISH).contains("win");
    }
}
