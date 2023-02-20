package jumper.exception;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.REACTIVE;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type=REACTIVE)
@ConditionalOnClass(WebFluxConfigurer.class)
@EnableConfigurationProperties({ServerProperties.class, WebProperties.class})
public class CustomErrorWebFluxAutoConfiguration {

    private final ServerProperties serverProperties;
/*
    private final ApplicationContext applicationContext;

    private final ResourceProperties resourceProperties;

    private final List<ViewResolver> viewResolvers;

    private final ServerCodecConfigurer serverCodecConfigurer;

    public CustomErrorWebFluxAutoConfiguration(ServerProperties serverProperties,
                                               ResourceProperties resourceProperties,
                                               ObjectProvider<ViewResolver> viewResolversProvider,
                                               ServerCodecConfigurer serverCodecConfigurer,
                                               ApplicationContext applicationContext) {
        this.serverProperties = serverProperties;
        this.applicationContext = applicationContext;
        this.resourceProperties = resourceProperties;
        this.viewResolvers = viewResolversProvider.orderedStream()
                .collect(Collectors.toList());
        this.serverCodecConfigurer = serverCodecConfigurer;
    }
*/

    public CustomErrorWebFluxAutoConfiguration(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }

    @Bean
    @ConditionalOnMissingBean(value=ErrorWebExceptionHandler.class, search=SearchStrategy.CURRENT)
    @Order(-1)
    public ErrorWebExceptionHandler errorWebExceptionHandler(ErrorAttributes errorAttributes, WebProperties webProperties, ObjectProvider<ViewResolver> viewResolvers, ServerCodecConfigurer serverCodecConfigurer, ApplicationContext applicationContext) {
        JsonErrorWebExceptionHandler exceptionHandler = new JsonErrorWebExceptionHandler(
                errorAttributes,
                webProperties.getResources(),
                this.serverProperties.getError(),
                applicationContext);
        exceptionHandler.setViewResolvers(viewResolvers.orderedStream().toList());
        exceptionHandler.setMessageWriters(serverCodecConfigurer.getWriters());
        exceptionHandler.setMessageReaders(serverCodecConfigurer.getReaders());
        return exceptionHandler;
    }


    @Bean
    @ConditionalOnMissingBean(value = ErrorAttributes.class, search = SearchStrategy.CURRENT)
    public DefaultErrorAttributes errorAttributes() {
        return new DefaultErrorAttributes();
    }
}

