package com.blade;

import com.blade.event.EventListener;
import com.blade.event.EventManager;
import com.blade.event.EventType;
import com.blade.ioc.Ioc;
import com.blade.ioc.SimpleIoc;
import com.blade.kit.Assert;
import com.blade.kit.BladeKit;
import com.blade.mvc.hook.WebHook;
import com.blade.mvc.http.HttpMethod;
import com.blade.mvc.http.SessionManager;
import com.blade.mvc.route.RouteHandler;
import com.blade.mvc.route.RouteMatcher;
import com.blade.mvc.ui.template.DefaultEngine;
import com.blade.mvc.ui.template.TemplateEngine;
import com.blade.server.netty.NettyServer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static com.blade.mvc.Const.*;

/**
 * Blade Core
 *
 * @author biezhi
 *         2017/5/31
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Blade {

    private boolean started = false;

    private RouteMatcher routeMatcher = new RouteMatcher();
    private NettyServer nettyServer = new NettyServer();
    private Class<?> bootClass;

    private List<WebHook> middlewares = new ArrayList<>();

    private Set<String> pkgs = new LinkedHashSet<>(Arrays.asList(PLUGIN_PACKAGE_NAME));
    private Set<String> statics = new HashSet<>(Arrays.asList("/favicon.ico", "/static/", "/upload/", "/webjars/"));

    private Ioc ioc = new SimpleIoc();
    private TemplateEngine templateEngine = new DefaultEngine();

    private Environment environment = Environment.empty();

    private EventManager eventManager = new EventManager();
    private SessionManager sessionManager = new SessionManager();

    private Consumer<Exception> startupExceptionHandler = (e) -> log.error("Failed to start Blade", e);

    private CountDownLatch latch = new CountDownLatch(1);

    public static Blade of() {
        return new Blade();
    }

    public static Blade me() {
        return new Blade();
    }

    public Ioc ioc() {
        return ioc;
    }

    public Blade get(@NonNull String path, @NonNull RouteHandler handler) {
        routeMatcher.addRoute(path, handler, HttpMethod.GET);
        return this;
    }

    public Blade post(@NonNull String path, @NonNull RouteHandler handler) {
        routeMatcher.addRoute(path, handler, HttpMethod.POST);
        return this;
    }

    public Blade put(@NonNull String path, @NonNull RouteHandler handler) {
        routeMatcher.addRoute(path, handler, HttpMethod.PUT);
        return this;
    }

    public Blade delete(@NonNull String path, @NonNull RouteHandler handler) {
        routeMatcher.addRoute(path, handler, HttpMethod.DELETE);
        return this;
    }

    public Blade before(@NonNull String path, @NonNull RouteHandler handler) {
        routeMatcher.addRoute(path, handler, HttpMethod.BEFORE);
        return this;
    }

    public Blade after(@NonNull String path, @NonNull RouteHandler handler) {
        routeMatcher.addRoute(path, handler, HttpMethod.AFTER);
        return this;
    }

    public Blade templateEngine(@NonNull TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        return this;
    }

    public TemplateEngine templateEngine() {
        return templateEngine;
    }

    public RouteMatcher routeMatcher() {
        return routeMatcher;
    }

    public Blade register(@NonNull Object bean) {
        ioc.addBean(bean);
        return this;
    }

    public Blade register(@NonNull Class<?> cls) {
        ioc.addBean(cls);
        return this;
    }

    public Blade addStatics(@NonNull String... folders) {
        statics.addAll(Arrays.asList(folders));
        return this;
    }

    public Blade showFileList(boolean fileList) {
        this.environment(ENV_KEY_STATIC_LIST, fileList);
        return this;
    }

    public Blade gzip(boolean gzipEnable) {
        this.environment(ENV_KEY_GZIP_ENABLE, gzipEnable);
        return this;
    }

    public Object getBean(@NonNull Class<?> cls) {
        return ioc.getBean(cls);
    }

    public boolean devMode() {
        return environment.getBoolean(ENV_KEY_DEV_MODE, true);
    }

    public Blade devMode(boolean devMode) {
        this.environment(ENV_KEY_DEV_MODE, devMode);
        if (!devMode) {
            this.enableMonitor(false);
        }
        return this;
    }

    public Class<?> bootClass() {
        return this.bootClass;
    }

    public Blade enableMonitor(@NonNull boolean enableMonitor) {
        this.environment(ENV_KEY_MONITOR_ENABLE, enableMonitor);
        return this;
    }

    public Blade enableCors(boolean enableCors) {
        this.environment(ENV_KEY_CORS_ENABLE, enableCors);
        return this;
    }

    public Set<String> getStatics() {
        return statics;
    }

    public Blade scanPackages(@NonNull String... pkgs) {
        this.pkgs.addAll(Arrays.asList(pkgs));
        return this;
    }

    public Set<String> scanPackages() {
        return pkgs;
    }

    public Blade bootConf(@NonNull String bootConf) {
        this.environment(ENV_KEY_BOOT_CONF, bootConf);
        return this;
    }

    public Blade environment(@NonNull String key, @NonNull Object value) {
        environment.set(key, value);
        return this;
    }

    public Environment environment() {
        return environment;
    }

    public Blade listen(int port) {
        Assert.greaterThan(port, 0, "server port not is negative number.");
        this.environment(ENV_KEY_SERVER_PORT, port);
        return this;
    }

    public Blade listen(@NonNull String address, int port) {
        Assert.greaterThan(port, 0, "server port not is negative number.");
        this.environment(ENV_KEY_SERVER_ADDRESS, address);
        this.environment(ENV_KEY_SERVER_PORT, port);
        return this;
    }

    public Blade use(@NonNull WebHook... middlewares) {
        if (!BladeKit.isEmpty(middlewares)) {
            this.middlewares.addAll(Arrays.asList(middlewares));
        }
        return this;
    }

    public List<WebHook> middlewares() {
        return this.middlewares;
    }

    public Blade appName(@NonNull String appName) {
        this.environment(ENV_KEY_APP_NAME, appName);
        return this;
    }

    public Blade event(@NonNull EventType eventType, @NonNull EventListener eventListener) {
        eventManager.addEventListener(eventType, eventListener);
        return this;
    }

    public EventManager eventManager() {
        return eventManager;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public Blade disableSession() {
        this.sessionManager = null;
        return this;
    }

    public Blade start() {
        return this.start(null, DEFAULT_SERVER_ADDRESS, DEFAULT_SERVER_PORT, null);
    }

    public Blade start(Class<?> mainCls, String... args) {
        return this.start(mainCls, DEFAULT_SERVER_ADDRESS, DEFAULT_SERVER_PORT, args);
    }

    public Blade start(Class<?> bootClass, @NonNull String address, int port, String... args) {
        try {
            Assert.greaterThan(port, 0, "server port not is negative number.");
            this.bootClass = bootClass;
            eventManager.fireEvent(EventType.SERVER_STARTING, this);
            Thread thread = new Thread(() -> {
                try {
                    nettyServer.start(Blade.this, args);
                    latch.countDown();
                    nettyServer.join();
                } catch (Exception e) {
                    startupExceptionHandler.accept(e);
                }
            });
            thread.setName("_(:3」∠)_");
            thread.start();
            started = true;
        } catch (Exception e) {
            startupExceptionHandler.accept(e);
        }
        return this;
    }

    public Blade await() {
        if (!started) {
            throw new IllegalStateException("Server hasn't been started. Call start() before calling this method.");
        }
        try {
            latch.await();
        } catch (Exception e) {
            log.error("awit error", e);
            Thread.currentThread().interrupt();
        }
        return this;
    }

    public void stop() {
        eventManager.fireEvent(EventType.SERVER_STOPPING, this);
        nettyServer.stop();
        eventManager.fireEvent(EventType.SERVER_STOPPED, this);
    }

}