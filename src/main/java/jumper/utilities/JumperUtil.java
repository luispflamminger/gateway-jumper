package jumper.utilities;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;

@Slf4j
@Service
public class JumperUtil {

    @Value( "${spring.codec.max-in-memory-size}")
    private int limit;

    public String getBodyForContentType(MediaType mediaType, byte[] originalBody){
        String bodyToStore;
        if (mediaType != null &&
                (
                        mediaType.isCompatibleWith(MediaType.parseMediaType("text/*")) ||
                                mediaType.isCompatibleWith(MediaType.APPLICATION_JSON) ||
                                mediaType.isCompatibleWith(MediaType.APPLICATION_XML)
                )
        )
        {
            bodyToStore = new String(originalBody);
        }
        else {
            log.debug("MediaType identified as non text, store as base64");
            bodyToStore = Base64Utils.encodeToString(originalBody);
        }
        if (bodyToStore.length() > limit){
            log.debug("payload string exceeded limit, will not be stored");
            bodyToStore = "";
        }

        log.debug("storing: {}", bodyToStore);
        return bodyToStore;
    }
}
