package it.unicas.chronogram.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the Vue/Ionic single-page app bundled into the WAR (the CI pipeline
 * copies {@code frontend/dist} into {@code classpath:/static} before packaging).
 *
 * <p>Any GET that does not match a real static file — and is not an API or
 * actuator path, which must keep their JSON 404 semantics — falls back to
 * {@code index.html} so client-side routes (e.g. {@code /tabs/home}) survive a
 * full page reload or a direct link.</p>
 *
 * <p>When the bundle is absent (plain backend build, tests) the resolver simply
 * returns 404s: no behaviour change for API clients.</p>
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
