package com.hilight.studio;

import com.hilight.core.Engine;
import com.hilight.core.IHiLightService;
import com.hilight.core.Log;
import com.hilight.core.RendererContract;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shizuku host: Shizuku starts this class in an app_process running as the shell UID (or root), so it
 * inherits CONTROL_DEVICE_LIGHTS and can drive HiLight directly.
 *
 * Unlike the adb host there is no file bridge — the app holds a binder to this object and pushes
 * state straight in.
 */
public final class HiLightUserService extends IHiLightService.Stub {

    private static final int MAX_STATE_JSON_CHARS = 262_144;
    private static final int ROOT_UID = 0;
    private static final int FIRST_APPLICATION_UID = 10_000;

    private final Engine engine = new Engine();
    private final boolean engineReady;
    private volatile int boundClientUid = -1;

    /** Shizuku instantiates the service with a no-arg constructor. */
    public HiLightUserService() {
        boolean ready = false;
        try {
            engine.start();
            ready = true;
            Log.i("Shizuku user service up, build " + BuildConfig.VERSION_NAME + " ("
                    + BuildConfig.VERSION_CODE + "), renderer r"
                    + RendererContract.IMPLEMENTATION_REVISION + ", " + engine.ledCount()
                    + " LEDs, uid " + android.os.Process.myUid());
        } catch (Throwable t) {
            Log.w("engine start failed: " + t);
        }
        engineReady = ready;
    }

    private boolean isCallerAuthorized() {
        int callingUid;
        try {
            callingUid = android.os.Binder.getCallingUid();
        } catch (RuntimeException ignored) {
            // An unidentifiable caller is not authorized to drive or stop a privileged renderer.
            return false;
        }
        if (callingUid == ROOT_UID || callingUid == SHELL_UID) return true;
        try {
            if (callingUid == android.os.Process.myUid()) return true;
        } catch (RuntimeException ignored) {
        }
        if (callingUid < FIRST_APPLICATION_UID) return false;
        int current = boundClientUid;
        if (current == -1) {
            synchronized (this) {
                if (boundClientUid == -1) {
                    boundClientUid = callingUid;
                    return true;
                }
                current = boundClientUid;
            }
        }
        return callingUid == current;
    }

    @Override
    public void setState(String json) {
        if (!isCallerAuthorized()) {
            Log.w("rejected setState from unauthorized caller uid");
            return;
        }
        if (json == null || json.length() > MAX_STATE_JSON_CHARS) {
            Log.w("rejected invalid or oversized state json");
            return;
        }
        engine.setState(json);
    }

    @Override
    public String status() {
        if (!isCallerAuthorized()) return "{}";
        JSONObject status = engine.statusJson();
        try {
            return status
                    .put("rendererVersionCode", BuildConfig.VERSION_CODE)
                    .put("rendererVersionName", BuildConfig.VERSION_NAME)
                    .put("rendererContractVersion", RendererContract.CONTRACT_VERSION)
                    .put("rendererImplementationRevision",
                            RendererContract.IMPLEMENTATION_REVISION)
                    .put("rendererStatusSchemaVersion",
                            RendererContract.STATUS_SCHEMA_VERSION)
                    .put("rendererClearAlgorithmVersion",
                            RendererContract.CLEAR_ALGORITHM_VERSION)
                    .put("rendererServiceVersion",
                            RendererContract.shizukuServiceVersion(BuildConfig.VERSION_CODE))
                    .put("rendererReady", engineReady)
                    .toString();
        } catch (Exception ignored) {
            return status.toString();
        }
    }

    @Override
    public int ledCount() {
        if (!isCallerAuthorized()) return 0;
        return engine.ledCount();
    }

    @Override
    public boolean stopAdbRenderers(int expectedPid, String expectedRendererInstanceId) {
        if (!isCallerAuthorized()) return false;
        boolean legacy = expectedRendererInstanceId != null
                && expectedRendererInstanceId.isEmpty();
        if (expectedPid <= 0 || (!legacy && !validInstanceId(expectedRendererInstanceId))) {
            return false;
        }

        File expectedProc = new File("/proc", Integer.toString(expectedPid));
        byte[] expectedCmdline = readCmdline(expectedProc);
        // A live proc directory with an unreadable/empty cmdline is unresolved, not proof that the
        // recorded helper exited. Refuse takeover until it disappears or has a readable new owner.
        if (expectedProc.isDirectory() && expectedCmdline == null) return false;
        if (expectedCmdline != null
                && !isExactAdbHelperCmdline(
                        expectedCmdline,
                        legacy ? "" : expectedRendererInstanceId)) {
            // PID reuse by an unrelated process proves the recorded source identity exited. This is
            // safe only when a read-only scan also proves that no HiLight adb helper remains.
            return !anyAdbHelperExists();
        }

        List<ProcessIdentity> helpers = new ArrayList<>();
        File[] processes = new File("/proc").listFiles();
        if (processes == null) return false;
        for (File process : processes) {
            int pid;
            try {
                pid = Integer.parseInt(process.getName());
            } catch (NumberFormatException ignored) {
                continue;
            }
            byte[] cmdline = readCmdline(process);
            if (cmdline != null && shouldSignalAdbHelper(
                    pid,
                    cmdline,
                    expectedPid,
                    legacy ? "" : expectedRendererInstanceId)) {
                helpers.add(new ProcessIdentity(pid, cmdline));
            }
        }

        // If the expected PID still exists it must be one of the exact identities above. This also
        // rejects a status forged by an unrelated process before any signal can be sent.
        if (expectedCmdline != null && helpers.stream().noneMatch(p -> p.pid == expectedPid)) {
            return false;
        }
        for (ProcessIdentity helper : helpers) {
            android.os.Process.sendSignal(helper.pid, 15); // cooperative SIGTERM
        }

        long deadline = android.os.SystemClock.elapsedRealtime() + PROCESS_EXIT_TIMEOUT_MS;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            boolean anyOriginalHelperAlive = false;
            for (ProcessIdentity helper : helpers) {
                File processDir = new File("/proc", Integer.toString(helper.pid));
                byte[] current = readCmdline(processDir);
                if (originalProcessUnresolved(
                        processDir.isDirectory(), helper.cmdline, current)) {
                    anyOriginalHelperAlive = true;
                    break;
                }
            }
            if (!anyOriginalHelperAlive) {
                // Stop only the acknowledged exact PID/instance. A different helper remains dark
                // but unowned, so refuse takeover rather than signaling it or trusting its status.
                return !anyAdbHelperExists();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Override
    public void destroy() {
        if (!isCallerAuthorized()) return;
        Log.i("Shizuku user service going away");
        stopThenExit(() -> {
            try {
                engine.stop();
            } catch (Throwable t) {
                Log.w("engine stop failed during service destroy: " + t);
            }
        }, () -> {
            Thread exitThread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }
                System.exit(0);
            }, "hilight-service-exit");
            exitThread.setDaemon(true);
            exitThread.start();
        });
    }

    /** Package-visible so the mandatory finally behavior can be covered without exiting the JVM. */
    static void stopThenExit(Runnable stopAction, Runnable exitAction) {
        try {
            stopAction.run();
        } finally {
            exitAction.run();
        }
    }

    /** A present but unreadable process remains owned until exact absence or PID reuse is proved. */
    static boolean originalProcessUnresolved(
            boolean processDirectoryPresent,
            byte[] originalCmdline,
            byte[] currentCmdline) {
        return processDirectoryPresent
                && (currentCmdline == null || Arrays.equals(originalCmdline, currentCmdline));
    }

    static boolean isExactAdbHelperCmdline(byte[] raw, String expectedInstanceId) {
        if (raw == null || raw.length == 0) return false;
        String[] args = new String(raw, StandardCharsets.UTF_8).split(String.valueOf((char) 0));
        if (args.length < 3) return false;
        String executable = new File(args[0]).getName();
        if (!("app_process".equals(executable)
                || "app_process32".equals(executable)
                || "app_process64".equals(executable))
                || !"/".equals(args[1])
                || !"com.hilight.core.AdbHelper".equals(args[2])) {
            return false;
        }
        String owner = "adb";
        String instance = "";
        boolean sawOwner = false;
        boolean sawInstance = false;
        for (int i = 3; i < args.length; i++) {
            if (args[i].isEmpty()) continue;
            if ("--owner".equals(args[i]) && !sawOwner && i + 1 < args.length) {
                owner = args[++i];
                sawOwner = true;
            } else if ("--instance".equals(args[i]) && !sawInstance && i + 1 < args.length) {
                instance = args[++i];
                sawInstance = true;
            } else if ("--dir".equals(args[i]) && i + 1 < args.length) {
                i++;
            } else if (!"--exclusive".equals(args[i])) {
                return false;
            }
        }
        if (!"adb".equals(owner)) return false;
        return expectedInstanceId == null || expectedInstanceId.equals(instance);
    }

    static boolean shouldSignalAdbHelper(
            int candidatePid,
            byte[] candidateCmdline,
            int expectedPid,
            String expectedRendererInstanceId) {
        return candidatePid == expectedPid
                && isExactAdbHelperCmdline(candidateCmdline, expectedRendererInstanceId);
    }

    private static boolean anyAdbHelperExists() {
        File[] processes = new File("/proc").listFiles();
        if (processes == null) return true;
        int selfPid = -1;
        try {
            selfPid = android.os.Process.myPid();
        } catch (RuntimeException ignored) {
        }
        for (File process : processes) {
            int pid;
            try {
                pid = Integer.parseInt(process.getName());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (pid == selfPid) continue;
            byte[] cmdline = readCmdline(process);
            if (cmdline != null && isExactAdbHelperCmdline(cmdline, null)) return true;
            // Same-UID cmdlines are normally readable. Treat a transiently unreadable shell-UID
            // process as unresolved rather than letting it hide a surviving adb helper.
            if (cmdline == null && process.isDirectory()
                    && Integer.valueOf(SHELL_UID).equals(readUid(process))) return true;
        }
        return false;
    }

    private static Integer readUid(File processDir) {
        File status = new File(processDir, "status");
        if (!status.isFile()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(status))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("Uid:")) continue;
                String[] values = line.substring(4).trim().split("\\s+");
                return values.length == 0 ? null : Integer.valueOf(values[0]);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static byte[] readCmdline(File processDir) {
        File cmdline = new File(processDir, "cmdline");
        if (!cmdline.isFile()) return null;
        try (FileInputStream input = new FileInputStream(cmdline)) {
            byte[] value = input.readNBytes(4096);
            return value.length == 0 ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean validInstanceId(String value) {
        return value != null && value.matches("[A-Za-z0-9._:-]{1,96}");
    }

    private static final class ProcessIdentity {
        final int pid;
        final byte[] cmdline;

        ProcessIdentity(int pid, byte[] cmdline) {
            this.pid = pid;
            this.cmdline = cmdline;
        }
    }

    private static final long PROCESS_EXIT_TIMEOUT_MS = 6_500L;
    private static final int SHELL_UID = 2_000;
}
