package act.i18n;

import act.app.ActionContext;
import act.conf.AppConfig;
import act.controller.Controller;
import org.joda.time.DateTime;
import org.osgl.$;
import org.osgl.http.H;
import org.osgl.mvc.annotation.PostAction;
import org.osgl.util.S;

import javax.inject.Inject;
import java.util.Locale;

/**
 * Responsible for setting up client Locale for the context
 * <p>
 * The client locale info is resolved in the following sequence:
 * 1. check the request parameter by configured name
 * 2. check the session variable
 * 3. check the cookie value
 * 4. check the `Accept-Language` header
 * 5. use the server locale
 */
public class LocaleResolver {

    private static final String KEY = "__locale__";
    private static final int COOKIE_TTL = 60 * 60 * 24 * 7;

    private ActionContext context;
    private AppConfig config;
    private boolean enabled;
    private Locale locale;
    private boolean reset;
    private boolean resolvedFromParam;

    @PostAction("i18n/locale")
    public static void updateLocale(H.Request request) {
        // there is no logic needed as locale has been processed built-in logic already
        String s = request.header(H.Header.Names.REFERER);
        if (S.notBlank(s)) {
            throw Controller.Util.redirect(s);
        }
    }

    @Inject
    public LocaleResolver(ActionContext context) {
        AppConfig config = context.config();
        this.enabled = config.i18nEnabled();
        if (!this.enabled) {
            return;
        }
        this.context = context;
        this.config = config;
    }

    public void resolve() {
        if (!enabled) {
            return;
        }
        Locale locale = resolveFromParam();
        if (!reset && null == locale) {
            locale = resolveFromSessionOrCookie();
        }
        if (null == locale) {
            locale = resolveFromHeader();
        }
        if (null == locale) {
            locale = resolveFromServer();
        }
        context.locale(locale);
        this.locale = locale;
    }

    public void dissolve() {
        if (!shouldWriteLocaleCookie()) {
            return;
        }
        String cookieName = config.localeCookieName();
        Locale locale = context.locale();
        if (null == locale) {
            locale = this.locale;
        }
        String localeStr = locale.toString();
        H.Session session = context.session();
        if (null != session) {
            if (reset) {
                session.remove(KEY);
            } else {
                session.put(KEY, localeStr);
            }
        }
        H.Cookie cookie = new H.Cookie(cookieName, localeStr);
        cookie.domain(config.cookieDomain());
        cookie.path("/");
        // in case we have resolved locale from cookie and we shouldn't write cookie anymore, we need to clear it
        cookie.maxAge(reset ? -1 : COOKIE_TTL);
        if (reset) {
            cookie.expires(DateTime.now().minusDays(1).toDate());
        }
        context.resp().addCookie(cookie);
    }

    private boolean shouldWriteLocaleCookie() {
        /*
         * 1. i18n must be enabled
         * 2. anyone of the following condition is true
         * 2.1 resolved from param
         * 2.2 the current locale does not match the locale resolved originally (meaning programmatically updated locale)
         * 2.3 reset is true (meaning session and cookie should be cleared)
         */
        return enabled && (reset || resolvedFromParam || locale != context.locale());
    }

    private Locale resolveFromSessionOrCookie() {
        // if session exists then resolve from session, otherwise resolve from cookie
        H.Session session = context.session();
        if (null != session) {
            return parseStr(session.get(KEY));
        } else {
            H.Cookie cookie = context.cookie(config.localeCookieName());
            return null == cookie ? null : parseStr(cookie.value());
        }
    }

    private Locale resolveFromParam() {
        String s = context.paramVal(config.localeParamName());
        Locale locale = parseStr(s);
        if (null != locale) {
            resolvedFromParam = true;
        }
        return locale;
    }

    private Locale resolveFromHeader() {
        return context.req().locale();
    }

    private Locale resolveFromServer() {
        return config.locale();
    }

    private Locale parseStr(String val) {
        if (null == val) {
            return null;
        }
        if ("default".equals(val)) {
            reset = true;
            return null;
        }
        String[] sa = val.trim().split("[-_]");
        int len = sa.length;
        switch (len) {
            case 3:
                return new Locale(sa[0], sa[1], sa[2]);
            case 2:
                return new Locale(sa[0], sa[1]);
            default:
                return new Locale(sa[0]);
        }
    }

    public static void main(String[] args) {
        System.out.println(Locale.US.toLanguageTag());
    }

}
