package dev.wsc.launcher;

import android.content.Context;
import android.os.Build;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Java method-hooking facade that dispatches to either canyie/Pine or LSPosed/LSPlant
 * depending on the Android version.
 *
 * <p><b>Why hybrid?</b><br>
 * Pine (the original hook library used by this project) works on Android 14+ but fails
 * to hook ART internals on some Android 10/11 ROMs (its ClassLinker-aware stubs depend
 * on internal ART layout that changed across versions).<br>
 * LSPlant 6.4 works on Android 10-13 but fails on Android 14+ because the required
 * internal ART symbol {@code art::GetMethodShorty} was removed from libart.so in
 * Android 14 (replaced by inlined code or a different ABI).</p>
 *
 * <p><b>Strategy:</b><br>
 * On Android 14 (API 34) and above, use Pine (proven to work on tested devices).<br>
 * On Android 13 (API 33) and below, use LSPlant (proven to work in LSPlant's own test
 * matrix for API 21-33).</p>
 *
 * <p>This class keeps the same Pine-style {@code MethodHook}/{@code CallFrame} API that
 * the existing call sites in {@link GuestRuntime} and {@link SdkContextHooks} already use,
 * so they don't need to change.</p>
 *
 * <p><b>Visibility note:</b> This class and its inner types are {@code public} because
 * LSPlant generates a stub class {@code LSPHooker_} in an anonymous DexFile (loaded
 * via a separate ClassLoader). For the cross-loader invocation to succeed, the hooker
 * class and its callback method must be accessible from outside the defining loader.</p>
 */
public final class Hooker {

    /** Callback contract — drop-in replacement for {@code top.canyie.pine.callback.MethodHook}. */
    public abstract static class MethodHook {
        public void beforeCall(CallFrame frame) throws Throwable {}
        public void afterCall(CallFrame frame) throws Throwable {}
    }

    /** Per-call frame — drop-in replacement for {@code top.canyie.pine.Pine.CallFrame}. */
    public static final class CallFrame {
        public Object thisObject;
        public Object[] args;
        public Object result;
        private boolean resultSet = false;

        Object backup;          // LSPlant only: set by Hooker once doHook returns
        Member target;          // for isStatic detection and reflection
        private boolean isStatic;

        CallFrame(Object thisObject, Object[] args, Object backup, Member target, boolean isStatic) {
            this.thisObject = thisObject;
            this.args = args;
            // backup is a java.lang.reflect.Method when using LSPlant; null when using Pine
            // (Pine's CallFrame is its own type and we proxy through it).
            this.backup = backup;
            this.target = target;
            this.isStatic = isStatic;
        }

        public void setResult(Object value) {
            this.result = value;
            this.resultSet = true;
        }

        public Object getResult() { return result; }

        public boolean hasThrowable() { return throwable != null; }
        public Throwable getThrowable() { return throwable; }
        public void setThrowable(Throwable t) { this.throwable = t; this.resultSet = true; }
        Throwable throwable;

        /**
         * Re-invoke the original method with the given receiver and arguments.
         * Mirrors {@code Pine.CallFrame.invokeOriginalMethod(Object, Object[])}.
         *
         * <p><b>Args convention (matches Pine):</b> {@code args[0..n-1]} are the method's
         * actual parameters (no receiver slot). For instance methods, the receiver is
         * passed separately as {@code thiz}. The Hooker.callback() normalizes the LSPlant
         * args (which include the receiver at args[0]) into this Pine-style convention
         * before constructing the CallFrame, so call sites see the same shape regardless
         * of backend.</p>
         */
        public Object invokeOriginalMethod(Object thiz, Object[] args) throws Throwable {
            if (backend == BACKEND_PINE && pineFrame != null) {
                // Delegate to Pine's CallFrame for the actual invocation.
                return pineFrame.invokeOriginalMethod(thiz, args);
            }
            // LSPlant path: backup is a java.lang.reflect.Method.
            Method backup = (Method) this.backup;
            if (backup == null) throw new IllegalStateException("backup method not set");
            try {
                if (isStatic) {
                    if (args == null || args.length == 0) return backup.invoke(null);
                    Object[] passed = new Object[args.length];
                    System.arraycopy(args, 0, passed, 0, args.length);
                    return backup.invoke(null, passed);
                } else {
                    // Instance method: args[] contains only the parameters (no receiver).
                    Object receiver = thiz != null ? thiz : thisObject;
                    if (args == null || args.length == 0) return backup.invoke(receiver);
                    Object[] passed = new Object[args.length];
                    System.arraycopy(args, 0, passed, 0, args.length);
                    return backup.invoke(receiver, passed);
                }
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }

        // Pine backend proxying. When using Pine, we wrap Pine's own CallFrame so
        // user callbacks can call invokeOriginalMethod() transparently.
        top.canyie.pine.Pine.CallFrame pineFrame;

        // Which backend this frame was created with.
        static int backend;
        static final int BACKEND_NONE = 0;
        static final int BACKEND_PINE = 1;
        static final int BACKEND_LSPLANT = 2;
    }

    /** The single replacement entry-point required by LSPlant (signature: Object callback(Object[])). */
    private final MethodHook userHook;
    private final Method callbackMethod;
    private final boolean isStatic;
    private final Member target;

    Hooker(MethodHook userHook, boolean isStatic, Member target) throws NoSuchMethodException {
        this.userHook = userHook;
        this.isStatic = isStatic;
        this.target = target;
        this.callbackMethod = Hooker.class.getDeclaredMethod("callback", Object[].class);
    }

    /**
     * Public callback entry-point required by LSPlant.
     *
     * <p>LSPlant generates a stub class named {@code LSPHooker_} in an anonymous DexFile
     * (loaded via a separate ClassLoader). That stub invokes
     * {@code hooker_object.callback(args)} on our Hooker instance. For the cross-loader
     * invocation to succeed, BOTH the class and the method must be {@code public}.</p>
     *
     * <p>Signature is fixed: {@code Object callback(Object[])} — any other signature
     * is undefined behavior per the LSPlant API contract.</p>
     */
    public Object callback(Object[] args) throws Throwable {
        // LSPlant semantics: args[0] is the receiver for instance methods, args[1..n] are params.
        // Pine semantics (which the existing call sites expect): args[0..n-1] are params only;
        // `this` lives in frame.thisObject. We normalize to Pine semantics here so that
        // GuestRuntime / SdkContextHooks code can treat frame.args[0] as the first parameter
        // regardless of which backend is active.
        Object thisObject;
        Object[] userArgs;
        if (isStatic) {
            thisObject = null;
            userArgs = args == null ? new Object[0] : args;
        } else {
            thisObject = (args != null && args.length > 0) ? args[0] : null;
            if (args == null || args.length <= 1) {
                userArgs = new Object[0];
            } else {
                // Strip args[0] (receiver) so userArgs matches Pine's convention.
                userArgs = new Object[args.length - 1];
                System.arraycopy(args, 1, userArgs, 0, args.length - 1);
            }
        }

        CallFrame frame = new CallFrame(thisObject, userArgs, backup, target, isStatic);
        try {
            userHook.beforeCall(frame);
            if (frame.resultSet) {
                if (frame.throwable != null) {
                    userHook.afterCall(frame);
                    throw frame.throwable;
                }
                userHook.afterCall(frame);
                return frame.result;
            }
            // Invoke the original. We pass `thisObject` as the receiver for instance methods.
            // invokeOriginalMethod() will skip args[0] (which would be the receiver in LSPlant
            // format) — but userArgs no longer has the receiver, so the args we pass back
            // through invokeOriginalMethod are already in the right shape for backup.invoke().
            try {
                Object original = frame.invokeOriginalMethod(thisObject, userArgs);
                frame.result = original;
            } catch (Throwable t) {
                frame.throwable = t;
            }
            if (frame.throwable != null) {
                userHook.afterCall(frame);
                throw frame.throwable;
            }
            userHook.afterCall(frame);
            return frame.result;
        } catch (Throwable t) {
            // LSPlant logs/ignores exceptions thrown by callbacks; return the failure explicitly.
            GuestRuntime.log("PINE_COMPAT: callback exception", t);
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            if (t instanceof Error) throw (Error) t;
            throw new RuntimeException(t);
        }
    }

    /** The backup method returned by LSPlant. Populated by {@link #hook(Member, MethodHook)}. */
    Method backup;

    private native Method doHook(Member target, Method callback);
    private native boolean doUnhook(Member target);

    /** Equivalent to {@code Pine.hook(Method, MethodHook)}. */
    static Hooker hook(Method target, MethodHook hook) {
        return hookImpl(target, hook);
    }

    /** Equivalent to {@code Pine.hook(Constructor, MethodHook)}. */
    static Hooker hook(Constructor<?> target, MethodHook hook) {
        return hookImpl(target, hook);
    }

    private static Hooker hookImpl(Member target, MethodHook hook) {
        try {
            ensureLoaded();
            if (CallFrame.backend == CallFrame.BACKEND_PINE) {
                return hookViaPine(target, hook);
            }
            if (CallFrame.backend == CallFrame.BACKEND_LSPLANT) {
                return hookViaLsplant(target, hook);
            }
            GuestRuntime.log("HOOKER: no backend available; cannot hook " + target, null);
            return null;
        } catch (Throwable t) {
            GuestRuntime.log("HOOKER: hook threw for " + target, t);
            return null;
        }
    }

    /** Convenience wrapper for any {@link Member} (Method or Constructor). */
    static Hooker hook(Member target, MethodHook hook) {
        if (target instanceof Method) return hook((Method) target, hook);
        if (target instanceof Constructor) return hook((Constructor<?>) target, hook);
        throw new IllegalArgumentException("unsupported Member: " + target);
    }

    boolean unhook() {
        if (CallFrame.backend == CallFrame.BACKEND_PINE) {
            // Pine hook records are auto-unhooked via GC; we leave them installed
            // for the lifetime of the guest process (matches original Pine behavior).
            return true;
        }
        try {
            return doUnhook(target);
        } catch (Throwable t) {
            GuestRuntime.log("LSPLANT: unhook threw for " + target, t);
            return false;
        }
    }

    // -------- Pine backend --------

    @SuppressWarnings("unused")
    private static Hooker hookViaPine(Member target, MethodHook hook) throws NoSuchMethodException {
        boolean isStatic;
        if (target instanceof Method) isStatic = Modifier.isStatic(((Method) target).getModifiers());
        else if (target instanceof Constructor) isStatic = false;
        else throw new IllegalArgumentException("unsupported Member: " + target);

        Hooker h = new Hooker(hook, isStatic, target);
        // Bridge: Pine's MethodHook has the same beforeCall/afterCall shape but a
        // different CallFrame type. We adapt by wrapping each invocation.
        top.canyie.pine.callback.MethodHook pineAdapter = new top.canyie.pine.callback.MethodHook() {
            @Override public void beforeCall(top.canyie.pine.Pine.CallFrame frame) {
                CallFrame ours = new CallFrame(frame.thisObject, frame.args, null, target, isStatic);
                ours.pineFrame = frame;
                ours.backend = CallFrame.BACKEND_PINE;
                try {
                    hook.beforeCall(ours);
                } catch (Throwable t) {
                    GuestRuntime.log("PINE: beforeCall threw", t);
                    frame.setThrowable(t);
                    return;
                }
                if (ours.resultSet) {
                    if (ours.throwable != null) frame.setThrowable(ours.throwable);
                    else frame.setResult(ours.result);
                }
            }
            @Override public void afterCall(top.canyie.pine.Pine.CallFrame frame) {
                CallFrame ours = new CallFrame(frame.thisObject, frame.args, null, target, isStatic);
                ours.pineFrame = frame;
                ours.backend = CallFrame.BACKEND_PINE;
                ours.result = frame.getResult();
                ours.throwable = frame.getThrowable();
                if (ours.throwable != null || frame.hasThrowable()) {
                    ours.throwable = frame.getThrowable();
                }
                try {
                    hook.afterCall(ours);
                } catch (Throwable t) {
                    GuestRuntime.log("PINE: afterCall threw", t);
                    frame.setThrowable(t);
                    return;
                }
                if (ours.resultSet) {
                    if (ours.throwable != null) frame.setThrowable(ours.throwable);
                    else frame.setResult(ours.result);
                }
            }
        };
        if (target instanceof Method) {
            top.canyie.pine.Pine.hook((Method) target, pineAdapter);
        } else if (target instanceof Constructor) {
            top.canyie.pine.Pine.hook((Constructor<?>) target, pineAdapter);
        }
        // Pine doesn't return a Hooker-like object; we return ours as a no-op holder.
        return h;
    }

    // -------- LSPlant backend --------

    @SuppressWarnings("unused")
    private static Hooker hookViaLsplant(Member target, MethodHook hook) {
        try {
            boolean isStatic;
            if (target instanceof Method) isStatic = Modifier.isStatic(((Method) target).getModifiers());
            else if (target instanceof Constructor) isStatic = false;
            else throw new IllegalArgumentException("unsupported Member: " + target);
            Hooker h = new Hooker(hook, isStatic, target);
            Method backup = h.doHook(target, h.callbackMethod);
            if (backup == null) {
                GuestRuntime.log("LSPLANT: hook failed for " + target, null);
                return null;
            }
            h.backup = backup;
            backup.setAccessible(true);
            return h;
        } catch (Throwable t) {
            GuestRuntime.log("LSPLANT: hook threw for " + target, t);
            return null;
        }
    }

    // -------- Backend selection --------

    private static volatile boolean loaded = false;
    private static Context appContext = null;

    /** Set the application Context so the static Hooker can read SharedPreferences. */
    public static void setApplicationContext(Context context) {
        if (context != null) appContext = context.getApplicationContext();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (Hooker.class) {
            if (loaded) return;
            selectAndInitBackend();
            loaded = true;
        }
    }

    private static void selectAndInitBackend() {
        int sdk = Build.VERSION.SDK_INT;
        // User preference overrides the auto strategy.
        int pref = appContext != null ? UiPreferences.hookBackend(appContext) : UiPreferences.HOOK_AUTO;
        GuestRuntime.log("HOOKER: pref=" + prefName(pref) + " android=" + sdk, null);

        if (pref == UiPreferences.HOOK_LSPLANT) {
            // User explicitly forced LSPlant (e.g. on Android 14+ to test new behavior).
            if (initLsplant()) {
                CallFrame.backend = CallFrame.BACKEND_LSPLANT;
                GuestRuntime.log("HOOKER: using LSPlant (forced by user, Android " + sdk + ")", null);
                return;
            }
            GuestRuntime.log("HOOKER: LSPlant init failed (forced); falling back to Pine", null);
        } else if (pref == UiPreferences.HOOK_PINE) {
            // User explicitly forced Pine (e.g. on Android 11 where Pine might work on this ROM).
            if (initPine()) {
                CallFrame.backend = CallFrame.BACKEND_PINE;
                GuestRuntime.log("HOOKER: using Pine (forced by user, Android " + sdk + ")", null);
                return;
            }
            GuestRuntime.log("HOOKER: Pine init failed (forced); falling back to LSPlant", null);
        }

        // Auto strategy (default):
        //   Android 14 (API 34)+ : Pine (LSPlant 6.4 lacks GetMethodShorty here)
        //   Android 10-13 (API 29-33): LSPlant (Pine's ART introspection fails on some ROMs)
        //   Android 7-9 (API 24-28): LSPlant (LSPlant supports API 21+)
        if (sdk >= 34) {
            if (initPine()) {
                CallFrame.backend = CallFrame.BACKEND_PINE;
                GuestRuntime.log("HOOKER: using Pine (Android " + sdk + ")", null);
                return;
            }
            GuestRuntime.log("HOOKER: Pine init failed; falling back to LSPlant", null);
        }
        if (initLsplant()) {
            CallFrame.backend = CallFrame.BACKEND_LSPLANT;
            GuestRuntime.log("HOOKER: using LSPlant (Android " + sdk + ")", null);
            return;
        }
        if (initPine()) {
            CallFrame.backend = CallFrame.BACKEND_PINE;
            GuestRuntime.log("HOOKER: using Pine (fallback, Android " + sdk + ")", null);
            return;
        }
        GuestRuntime.log("HOOKER: no backend could be initialized; Java hooks unavailable", null);
        CallFrame.backend = CallFrame.BACKEND_NONE;
    }

    private static String prefName(int pref) {
        return pref == UiPreferences.HOOK_PINE ? "Pine" : pref == UiPreferences.HOOK_LSPLANT ? "LSPlant" : "Auto";
    }

    private static boolean initPine() {
        try {
            // Pine's native library (libpine.so) is loaded automatically on the first
            // call to Pine.hook(). We don't need to call PineConfig here — the defaults
            // (debuggable-only auto-init, etc.) are sufficient for our sideload-only use.
            return true;
        } catch (Throwable t) {
            GuestRuntime.log("HOOKER: Pine init failed", t);
            return false;
        }
    }

    private static boolean initLsplant() {
        try {
            // liblsplant and libshadowhook are runtime dependencies of libwscmodhost.
            // Load them in dependency order before libwscmodhost.
            try { System.loadLibrary("lsplant"); }
            catch (UnsatisfiedLinkError already) { /* already loaded */ }
            try { System.loadLibrary("shadowhook"); }
            catch (UnsatisfiedLinkError already) { /* already loaded by NativeModBridge */ }
            try {
                System.loadLibrary("wscmodhost");
            } catch (UnsatisfiedLinkError t) {
                GuestRuntime.log("LSPLANT: failed to load libwscmodhost.so", t);
                throw t;
            }
            // lsplant::Init is called from JNI_OnLoad of libwscmodhost.so. If it fails,
            // doHook() will return null on first hook and we'll log it.
            return true;
        } catch (Throwable t) {
            GuestRuntime.log("HOOKER: LSPlant init failed", t);
            return false;
        }
    }
}
