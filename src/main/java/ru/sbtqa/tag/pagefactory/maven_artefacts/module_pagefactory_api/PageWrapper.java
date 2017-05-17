package ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api;

import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sbtqa.tag.pagefactory.PageFactory;
import ru.sbtqa.tag.pagefactory.WebElementsPage;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.annotations.PageEntry;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageInitializationException;
import ru.sbtqa.tag.qautils.errors.AutotestError;


/**
 * Кто это
 *
 * 1. Инфа о текущем контексте страницы
 *
 * 2. Механизм управления текущим контектсом страницы
 *
 * 3. Механизм рекурсивного поиска и загрузки страниц
 *
 */
public class PageWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(PageWrapper.class);

    private static String currentPageTitle;
    private static Page currentPage;

    private final String pagesPackage;

    /**
     *
     * @param pagesPackage a {@link java.lang.String} object.
     */
    public PageWrapper(String pagesPackage) {
        this.pagesPackage = pagesPackage;
    }

    /**
     * Initialize page with specified title and save its instance to
     * {@link PageWrapper#currentPage} for further use
     *
     * @param title page title
     * @return page instance
     * @throws PageInitializationException if failed to execute corresponding
     * page constructor
     */
    public WebElementsPage getPage(String title) throws PageInitializationException {
        if (null == currentPage || !currentPageTitle.equals(title)) {
            if (null != currentPage) {
                currentPage = getPage(currentPage.getClass().getPackage().getName(), title);
            }
            if (null == currentPage) {
                currentPage = getPage(pagesPackage, title);
            }
            if (null == currentPage) {
                throw new AutotestError("WebElementsPage object with title '" + title + "' is not registered");
            }
        }
        return currentPage;
    }

    /**
     * Initialize page by class
     *
     * @param page TODO
     * @return TODO
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageInitializationException
     * TODO
     */
    public WebElementsPage getPage(Class<? extends WebElementsPage> page) throws PageInitializationException {
        return bootstrapPage(page);
    }

    /**
     * <p>
     * Get WebElementsPage by PageEntry title </p>
     *
     * @param packageName a {@link java.lang.String} object.
     * @param title a {@link java.lang.String} object.
     * @return a WebElementsPage object.
     * @throws PageInitializationException {@inheritDoc}
     */
    public WebElementsPage getPage(String packageName, String title) throws PageInitializationException {
        return bootstrapPage(getPageClass(packageName, title));
    }

    /**
     * <p>
     * Getter for the field <code>currentPage</code>.</p>
     *
     * @return a WebElementsPage object.
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageInitializationException
     * TODO
     */
    public static WebElementsPage getCurrentPage() throws PageInitializationException {
        if (null == currentPage) {
            throw new PageInitializationException("Current page not initialized!");
        } else {
            return currentPage;
        }
    }

    /**
     * Redirect to WebElementsPage by WebElementsPage Entry url value
     *
     * @param title a {@link java.lang.String} object.
     * @return a WebElementsPage object.
     * @throws PageInitializationException TODO
     */
    public static WebElementsPage changeUrlByTitle(String title) throws PageInitializationException {
        if (null != currentPage) {
            currentPage = changeUrlByTitle(currentPage.getClass().getPackage().getName(), title);
        }
        if (null == currentPage) {
            currentPage = changeUrlByTitle(pagesPackage, title);
        }
        if (null == currentPage) {
            throw new AutotestError("WebElementsPage Object with title " + title + " is not registered");
        }
        return currentPage;
    }

    /**
     * Redirect to WebElementsPage by WebElementsPage Entry url value
     *
     * @param packageName a {@link java.lang.String} object.
     * @param title a {@link java.lang.String} object.
     * @return a WebElementsPage object.
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageInitializationException
     * TODO
     */
    public static WebElementsPage changeUrlByTitle(String packageName, String title) throws PageInitializationException {

        Class<?> pageClass = getPageClass(packageName, title);
        if (pageClass == null) {
            return null;
        }

        Annotation annotation = pageClass.getAnnotation(PageEntry.class);
        if (annotation != null && !((PageEntry) annotation).url().isEmpty()) {
            if (PageFactory.getWebDriver().getCurrentUrl() == null) {
                throw new AutotestError("Current URL is null");
            } else {
                try {
                    URL currentUrl = new URL(PageFactory.getWebDriver().getCurrentUrl());
                    String finalUrl = new URL(currentUrl.getProtocol(), currentUrl.getHost(), currentUrl.getPort(),
                            ((PageEntry) annotation).url()).toString();
                    PageFactory.getWebDriver().navigate().to(finalUrl);
                } catch (MalformedURLException ex) {
                    LOG.error("Failed to get current url", ex);
                }
            }

            return bootstrapPage(pageClass);
        }

        throw new AutotestError("WebElementsPage " + title + " doesn't have fast URL in PageEntry");
    }

    /**
     *
     * @param packageName TODO
     * @param title TODO
     * @return
     */
    private static Class<?> getPageClass(final String packageName, String title) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final Set<Class<?>> allClasses = new HashSet<>();
        try {
            for (ClassPath.ClassInfo info : ClassPath.from(loader).getAllClasses()) {
                if (info.getName().startsWith(packageName + ".")) {
                    allClasses.add(info.load());
                }
            }
        } catch (IOException ex) {
            LOG.warn("Failed to shape class info set", ex);
        }

        for (Class<?> page : allClasses) {
            String pageTitle = null;
            if (null != page.getAnnotation(PageEntry.class)) {
                pageTitle = page.getAnnotation(PageEntry.class).title();
            } else {
                try {
                    pageTitle = (String) FieldUtils.readStaticField(page, "title", true);
                } catch (IllegalArgumentException | IllegalAccessException ex) {
                    LOG.debug("Failed to read {} becase it is not page object", pageTitle, ex);
                }
            }
            if (pageTitle != null && pageTitle.equals(title)) {
                return page;
            }
        }

        return null;
    }

    /**
     * Run constructor of specified page class and put its instance into static
     * {@link #currentPage} variable
     *
     * @param page page class
     * @return initialized page
     * @throws PageInitializationException if failed to execute corresponding
     * page constructor
     */
    private static WebElementsPage bootstrapPage(Class<?> page) throws PageInitializationException {
        if (page != null) {
            try {
                @SuppressWarnings("unchecked")
                Constructor<WebElementsPage> constructor = ((Constructor<WebElementsPage>) page.getConstructor());
                constructor.setAccessible(true);
                currentPage = constructor.newInstance();
                currentPageTitle = currentPage.getPageTitle();
                return currentPage;
            } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new PageInitializationException("Failed to initialize page '" + page + "'", e);
            }
        }
        return null;
    }

    /**
     * @return the currentPageTitle
     */
    public static String getCurrentPageTitle() {
        return currentPageTitle;
    }
}