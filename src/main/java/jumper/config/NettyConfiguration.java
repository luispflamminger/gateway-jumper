package jumper.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NettyConfiguration {

    @Value("${spring.cloud.gateway.httpclient.max-initial-line-length-tardis}")
    int maxInitialLineLength;

    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> customizer() {

        return factory -> factory.addServerCustomizers(
                server -> server.httpRequestDecoder(
                        dec -> dec.maxInitialLineLength(maxInitialLineLength)
                )
        );
    }
}
