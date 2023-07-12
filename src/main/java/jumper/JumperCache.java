package jumper;

import jumper.model.TokenInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class JumperCache
{

    private static Logger log = LoggerFactory.getLogger( JumperCache.class);

    @Value( "${jumpercache.ttlOffset}")
    private int ttlOffset;

    @SuppressWarnings( "unused")
    private long cleanCacheInSeconds;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool( 1);

    Map<String, TokenInfo> cachingList = new HashMap<>();

    public JumperCache( @Value( "${jumpercache.cleanCacheInSeconds:0}") long cleanCacheInSeconds)
    {
        if( cleanCacheInSeconds > 0)
        {
            scheduler.scheduleAtFixedRate( this.cleanCacheJob(), cleanCacheInSeconds, cleanCacheInSeconds, TimeUnit.SECONDS);
            log.debug( "JumperCache cleanup job is enabled. the cache is cleaned every {} seconds.", cleanCacheInSeconds);
        }
        else
        {
            log.debug( "JumperCache cleanup job is not enabled. To activate the cache cleaning job, you must specify a value > 0 in your properties with the key 'jumpercache.cleanCacheInSeconds'.");
        }
    }

    /**
     *
     * @param itemKey
     * @return returns the ChacheItem or null if not exist
     */
    public TokenInfo getToken( String tokenCacheKey) {

        log.debug( "try to grab token from cache with key: {}", tokenCacheKey);

        if (log.isDebugEnabled()) {
            printCache();
        }

        TokenInfo token = this.cachingList.get( tokenCacheKey);
        if( token != null)
        {
            if( isValid( token))
            {
                return token;
            }
            else
            {
                // delete token and return null
                log.debug( "TTL: {} seconds | The token has expired or will be exipred in less than {} seconds and will be deleted", token.getExpiresIn(), this.ttlOffset);
                this.deleteTokenByKey( tokenCacheKey);
                return null;
            }
        }
        else
        {
            return null;
        }
    }

    public void saveToken( String tokenKey, TokenInfo gwAccessToken) {
        // save token
        log.debug( "Token saved with tokenKey: '{}'", tokenKey);
        this.cachingList.put( tokenKey, gwAccessToken);
    }

    public void printCache() {
        log.debug( "---Jumper-Cache-List--------------------------------------------");
        log.debug( "Number of Tokens in JumperCache: {}", this.cachingList.size());

        this.cachingList.entrySet().forEach( entry ->
        {
            log.debug( "TTL: {} seconds, CacheKey: {}", entry.getValue().getExpiresIn(), entry.getKey());
        });
        log.debug( "----------------------------------------------------------------");
    }

    private boolean isValid( TokenInfo token) {
        return token.getExpiresIn() > this.ttlOffset ? true : false;
    }

    private void deleteTokenByKey( String itemKey) {
        this.cachingList.remove( itemKey);
    }

    private Runnable cleanCacheJob() {
        return new Runnable()
        {
            @Override
            public void run() {
                log.debug( "----Clean-Jumper-Cache---Size:{}--------------------------------", cachingList.size());
                cachingList.entrySet().removeIf( entry ->
                {
                    if( isValid( entry.getValue()))
                    {
                        log.debug( "Valid | TTL={} tokenKey: {}", entry.getValue().getExpiresIn(), entry.getKey());
                        return false;
                    }
                    else
                    {
                        log.debug( "Expired -> Delete now | TTL={} tokenKey: {}", entry.getValue().getExpiresIn(), entry.getKey());
                        return true;
                    }
                });
                log.debug( "----Clean-Jumper-Cache---Size:{}-after-cleaning-------------------------------", cachingList.size());
            }
        };
    }

}
