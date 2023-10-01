package jumper.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class HttpClientConfiguration {

  @Value("${CUSTOM_CIPHERS:}")
  List<String> customCiphers;

  @Bean
  public HttpClientCustomizer httpClientCustomizer() {
    try {
      List<String> dtCiphers =
          List.of(
              "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
              "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
              "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",
              "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
              "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
              "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
              "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256"
              // ,"TLS_ECDHE_ECDSA_WITH_AES_256_CCM"
              // ,"TLS_DHE_RSA_WITH_AES_256_CCM"
              ,
              "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
              "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
              "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256",
              "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
              // ,"TLS_ECDHE_ECDSA_WITH_AES_128_CCM"
              // ,"TLS_DHE_RSA_WITH_AES_128_CCM"
              ,
              "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
              "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
              "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",
              "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
              "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
              "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
              "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
              "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
              "TLS_AES_256_GCM_SHA384",
              "TLS_CHACHA20_POLY1305_SHA256",
              "TLS_AES_128_GCM_SHA256"
              // ,"TLS_AES_128_CCM_SHA256"
              );

      SslContext s =
          SslContextBuilder.forClient()
              .trustManager(InsecureTrustManagerFactory.INSTANCE)
              .protocols("TLSv1.2", "TLSv1.3")
              .sslProvider(SslProvider.JDK)
              .ciphers(
                  Stream.concat(dtCiphers.stream(), customCiphers.stream())
                      .distinct()
                      .collect(Collectors.toList()))
              .build();

      return httpClient -> httpClient.secure(t -> t.sslContext(s));

    } catch (SSLException e) {
      e.printStackTrace();
    }

    return httpClient -> httpClient;
  }

  @Bean
  public WebClient createWebClient() throws SSLException {
    SslContext sslContext =
        SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
    HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));
    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
  }
}
