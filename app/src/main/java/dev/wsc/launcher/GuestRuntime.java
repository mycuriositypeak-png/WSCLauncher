package dev.wsc.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.util.Log;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.ZipFile;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/** Experimental in-process hosting. No integrity checks are disabled or patched. */
final class GuestRuntime {
    static final String TARGET = "com.monkeyibrow.worldsoccerchamps";
    static volatile String failure = "A Activity substituta não foi carregada.";
    static Application gameApplication;
    private static Context host, game;
    private static ClassLoader loader;
    private static Class<?> activityClass;
    private static ActivityInfo activityInfo;
    private static File logFile;
    private static NativeModRuntime nativeMods;

    static void log(String stage, Throwable error) {
        String line = new Date() + " | " + stage + (error == null ? "" : "\n" + Log.getStackTraceString(error));
        Log.i("WSCGuest", line);
        try (FileWriter writer = new FileWriter(logFile, true)) { writer.write(line + "\n"); }
        catch (Exception ignored) { }
    }

    static void prepare(Context context) {
        host = context;
        logFile = new File(context.getFilesDir(), "guest-diagnostic.log");
        try (FileWriter writer = new FileWriter(logFile, false)) { writer.write("WSCLauncher 1.0 | diagnóstico\n"); }
        catch (IOException ignored) { }
        RuntimeLogcat.start(context);
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            log("FALHA JAVA: " + thread.getName(), error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
        try {
            log("PRECHECK: processo " + android.os.Process.myPid() + "; 64 bits=" + android.os.Process.is64Bit()
                    + "; depurável=" + ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                    + "; bootstrap=context-before-create-v2", null);
            game = context.createPackageContext(TARGET, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(TARGET, PackageManager.GET_META_DATA);
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(TARGET);
            if (launch == null || launch.getComponent() == null) throw new IllegalStateException("Jogo instalado sem Activity inicial acessível.");
            activityInfo = context.getPackageManager().getActivityInfo(launch.getComponent(), PackageManager.GET_META_DATA);
            log("PACOTE: " + info.sourceDir + "; splits=" + Arrays.toString(info.splitSourceDirs), null);
            Map<String, String> libraries = collectLibraries(info);
            if (!libraries.containsKey("WorldSoccerChamps")) throw new IllegalStateException(
                    "Não encontrei libWorldSoccerChamps.so compatível com o processo " + processAbi()
                    + ". Consulte ARQUITETURAS e DIRETÓRIO NATIVO no diagnóstico. Instale o launcher com a mesma ABI do jogo.");
            loader = game.getClassLoader();
            ModStore store = new ModStore(host.getFilesDir());
            long version = host.getPackageManager().getPackageInfo(ModSpec.PACKAGE, 0).versionCode;
            List<ModSpec> activeMods = store.active(version);
            // Record the exact loaded snapshot for diagnostics and guest restart detection.
            StringBuilder report = new StringBuilder("Fingerprint: " + ModStore.fingerprintOf(activeMods)
                    + "\nJogo: " + version + "\nNative Mod ABI v1\n");
            for (ModSpec mod : activeMods) report.append("NATIVE MOD: ").append(mod.id)
                    .append(" v").append(mod.version).append("; aguardando biblioteca original.\n");
            ModStore.atomic(new File(host.getFilesDir(), "mods-runtime.txt"),
                    report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            log("MODS: " + activeMods.size() + " mod(s) nativo(s) ativo(s).", null);
            nativeMods = new NativeModRuntime(host, activeMods, libraries.get("WorldSoccerChamps"));
            log("ASSETS: " + Arrays.toString(game.getAssets().list("")), null);
            try (InputStream ignored = game.getAssets().open("Icon-57.png")) {
                log("ASSET PROBE: Icon-57.png acessível.", null);
            } catch (IOException missingIcon) {
                log("ASSET PROBE: Icon-57.png ausente; continuando, pois o nome pode variar entre versões.", null);
            }
            installClassFallback();
            installLibraries(libraries);
            SdkContextHooks.install(host, game, loader);
            activityClass = Class.forName(activityInfo.name, false, loader);
            if (!Activity.class.isAssignableFrom(activityClass)) throw new IllegalStateException("Classe inicial não é uma Activity.");
            log("ACTIVITY: " + activityInfo.name + "; application=" + info.className, null);
            installActivityHooks();
            String applicationName = info.className == null ? Application.class.getName() : info.className;
            // Runs the game's normal Application initialization, including its protection code.
            gameApplication = new Instrumentation().newApplication(loader, applicationName, new GuestContext(host, game));
            log("APPLICATION: attach concluído; iniciando onCreate.", null);
            gameApplication.onCreate();
            log("APPLICATION: onCreate concluído; aguardando Activity.", null);
        } catch (Throwable error) {
            failure = error.toString();
            log("BOOTSTRAP FALHOU", error);
            // Hook callbacks check this flag so the host can display the error.
            activityClass = null;
        }
    }

    private static String processAbi() {
        return android.os.Process.is64Bit() ? "arm64-v8a" : "armeabi-v7a";
    }

    static String requireCocosLibrary(Context activity) throws PackageManager.NameNotFoundException {
        String packageName = activity.getPackageName();
        if (!TARGET.equals(packageName))
            throw new IllegalStateException("O contexto do jogo não foi anexado à Activity. Pacote: " + packageName);
        ApplicationInfo original = activity.getPackageManager().getApplicationInfo(TARGET, PackageManager.GET_META_DATA);
        String library = original.metaData == null ? null : original.metaData.getString("android.app.lib_name");
        if (library == null || library.trim().isEmpty())
            throw new IllegalStateException("O jogo instalado não declarou android.app.lib_name para o Cocos.");
        return library;
    }

    private static Map<String,String> collectLibraries(ApplicationInfo info) throws IOException {
        String abi = processAbi();
        int elfClass = android.os.Process.is64Bit() ? 2 : 1;
        Set<String> architectures = new TreeSet<>();
        Map<String,String> libs = new HashMap<>();
        log("DIRETÓRIO NATIVO: " + info.nativeLibraryDir + "; ABI DO PROCESSO: " + abi
                + "; ABIs DO APARELHO: " + Arrays.toString(Build.SUPPORTED_ABIS), null);
        File[] files = new File(info.nativeLibraryDir).listFiles();
        if (files != null) for (File f : files) if (f.getName().startsWith("lib") && f.getName().endsWith(".so")) {
            try (InputStream input = new FileInputStream(f)) {
                byte[] header = new byte[5];
                if (input.read(header) == 5 && header[0] == 0x7f && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') {
                    architectures.add("ELF " + (header[4] == 2 ? "64" : "32") + " bits extraído");
                    if (header[4] == elfClass)
                        libs.put(f.getName().substring(3, f.getName().length()-3), f.getAbsolutePath());
                }
            }
        }
        List<String> apks = new ArrayList<>();
        apks.add(info.sourceDir);
        if (info.splitSourceDirs != null) Collections.addAll(apks, info.splitSourceDirs);
        for (String apk : apks) try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String path = entries.nextElement().getName();
                if (path.startsWith("lib/") && path.endsWith(".so")) {
                    String[] parts = path.split("/");
                    if (parts.length == 3) architectures.add(parts[1]);
                }
                if (path.startsWith("lib/" + abi + "/lib") && path.endsWith(".so")) {
                    String name = path.substring(path.lastIndexOf('/')+4, path.length()-3);
                    libs.putIfAbsent(name, apk + "!/" + path);
                }
            }
        }
        log("ARQUITETURAS DO JOGO: " + architectures, null);
        log("BIBLIOTECAS " + abi + ": " + libs.keySet(), null);
        return libs;
    }

    private static void installLibraries(Map<String,String> libs) throws Exception {
        Method find = dalvik.system.BaseDexClassLoader.class.getDeclaredMethod("findLibrary", String.class);
        Pine.hook(find, new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame frame) {
                if (frame.thisObject != loader) return;
                String name = (String) frame.args[0];
                if (libs.containsKey(name)) {
                    log("NATIVE LOAD: " + name + " -> " + libs.get(name), null);
                    frame.setResult(libs.get(name));
                }
            }
        });
    }

    private static void installClassFallback() throws Exception {
        Method load = ClassLoader.class.getDeclaredMethod("loadClass", String.class, boolean.class);
        ClassLoader hostLoader = host.getClassLoader();
        Pine.hook(load, new MethodHook() {
            @Override public void afterCall(Pine.CallFrame frame) throws Throwable {
                if (frame.thisObject != hostLoader || !(frame.getThrowable() instanceof ClassNotFoundException)) return;
                try { frame.setResult(frame.invokeOriginalMethod(loader, frame.args)); }
                catch (InvocationTargetException error) {
                    if (!(error.getCause() instanceof ClassNotFoundException)) throw error.getCause();
                }
            }
        });
    }

    private static void installActivityHooks() throws Exception {
        Method newActivity = Instrumentation.class.getDeclaredMethod("newActivity", ClassLoader.class, String.class, Intent.class);
        Pine.hook(newActivity, new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                if (activityClass == null || !GuestStubActivity.class.getName().equals(frame.args[1])) return;
                log("ACTIVITY: começando instanciação.", null);
                Activity guest = (Activity) activityClass.getDeclaredConstructor().newInstance();
                log("ACTIVITY: instanciada no processo do launcher.", null);
                frame.setResult(guest);
            }
        });
        // Context adaptation happens directly before onCreate below. Framework
        // attachment entry-point hooks are skipped on the observed release path.
        Pine.hook(Activity.class.getDeclaredMethod("getApplication"), new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame frame) {
                if (activityClass != null && activityClass.isInstance(frame.thisObject) && gameApplication != null)
                    frame.setResult(gameApplication);
            }
        });
        for (Method method : Instrumentation.class.getDeclaredMethods()) {
            if (!Arrays.asList("callActivityOnCreate", "callActivityOnStart", "callActivityOnResume",
                    "callActivityOnPause", "callActivityOnStop", "callActivityOnDestroy").contains(method.getName())) continue;
            Pine.hook(method, new MethodHook() {
                private boolean isGuest(Pine.CallFrame frame) {
                    return activityClass != null && frame.args.length > 0 && activityClass.isInstance(frame.args[0]);
                }
                @Override public void beforeCall(Pine.CallFrame frame) {
                    if (!isGuest(frame)) return;
                    Activity current = (Activity) frame.args[0];
                    log("LIFECYCLE: " + method.getName() + " iniciado; taskId=" + current.getTaskId()
                            + "; isTaskRoot=" + current.isTaskRoot() + "; isFinishing=" + current.isFinishing(), null);
                    if (method.getName().equals("callActivityOnCreate")) {
                        Activity guest = (Activity) frame.args[0];
                        try {
                            boolean changed = GuestContext.bindBeforeCreate(guest, activityClass, game);
                            log("ACTIVITY: contexto antes de onCreate; aplicado=" + changed
                                    + "; base=" + guest.getBaseContext().getClass().getName(), null);
                            String library = requireCocosLibrary(guest);
                            log("ACTIVITY: contexto confirmado; pacote=" + guest.getPackageName() + "; biblioteca Cocos=" + library, null);
                        } catch (Exception error) {
                            frame.setThrowable(error); // Pine logs/ignores thrown callback exceptions; return the failure explicitly.
                            return;
                        }
                        if (nativeMods.hasModules()) NativeModBridge.attach(guest);
                        // The Activity is still registered as the host's stub in Android's bookkeeping.
                        Intent intent = new Intent(guest.getIntent());
                        intent.setComponent(new ComponentName(TARGET, activityInfo.name));
                        intent.setAction(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_LAUNCHER);
                        intent.setExtrasClassLoader(loader);
                        guest.setIntent(intent);
                        if (activityInfo.screenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                            guest.setRequestedOrientation(activityInfo.screenOrientation);
                    }
                }
                @Override public void afterCall(Pine.CallFrame frame) {
                    if (isGuest(frame)) {
                        Activity current = (Activity) frame.args[0];
                        log("LIFECYCLE: " + method.getName()
                                + (frame.hasThrowable() ? " FALHOU" : " concluído.")
                                + "; isFinishing=" + current.isFinishing(), frame.getThrowable());
                    }
                }
            });
        }
        Pine.hook(Activity.class.getDeclaredMethod("finish"), new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame frame) {
                if (activityClass != null && activityClass.isInstance(frame.thisObject))
                    log("ACTIVITY: finish() solicitado.", new Throwable("Origem do finish()"));
            }
        });
        installEngineObservation();
        Pine.hook(Activity.class.getDeclaredMethod("onResume"), new MethodHook() {
            @Override public void afterCall(Pine.CallFrame frame) {
                if (activityClass != null && activityClass.isInstance(frame.thisObject))
                    log("ACTIVITY: onResume alcançado (ainda não comprova renderização do jogo).", null);
            }
        });
    }

    private static void installEngineObservation() throws Exception {
        Class<?> cocos = Class.forName("org.cocos2dx.lib.Cocos2dxActivity", false, loader);
        for (Method method : cocos.getDeclaredMethods()) {
            if (!Arrays.asList("onCreate", "onLoadNativeLibraries", "init").contains(method.getName())) continue;
            Pine.hook(method, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame frame) {
                    if (activityClass != null && activityClass.isInstance(frame.thisObject))
                        log("COCOS: " + method.getName() + " iniciado.", null);
                }
                @Override public void afterCall(Pine.CallFrame frame) {
                    if (method.getName().equals("onLoadNativeLibraries") && activityClass != null
                            && activityClass.isInstance(frame.thisObject) && !frame.hasThrowable()) {
                        try { nativeMods.load(); }
                        catch (Throwable error) { log("NATIVE MOD: inicialização falhou.", error); frame.setThrowable(error); }
                    }
                    if (activityClass != null && activityClass.isInstance(frame.thisObject))
                        log("COCOS: " + method.getName() + (frame.hasThrowable() ? " FALHOU" : " concluído."), frame.getThrowable());
                }
            });
        }
    }
}
