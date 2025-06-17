/**
 * CacheManager to avoid duplicated JT download from Teamcenter
 * 
 * - thread safe
 * - with TTL
 */
package viewer3d.shared.utils;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CacheManager {	
	public static int defaultTTLMinutes=30;
	
	// expected multiple thread safe
	private static Map<String, CacheEntry> cache= new ConcurrentHashMap<>();
    private static ReadWriteLock lock= new ReentrantReadWriteLock();  
    private static ScheduledExecutorService executorService= Executors.newScheduledThreadPool(1);

    private CacheManager() {
    }
    
    public static void put(String key, Object value) {
        put(key, value, defaultTTLMinutes);
    }

    public static void put(String key, Object value, int ttlMinutes) {
        try {
            lock.writeLock().lock();
            cache.put(key, new CacheEntry(value, ttlMinutes));
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public static boolean has(String key) {
    	return cache.containsKey(key);
	}

    public static Object get(String key) {  
    	try {
    		CacheEntry entry = cache.get(key);
            if (entry != null ) {
            	if(!entry.isExpired()) {
            		return entry.getValue();
            	}else {
            		cache.remove(key);
            	}                
            }
		} catch (Exception e) {
			Util.getLogger("CacheManager").warn(e.getMessage());
		}
                
        return null;
    }
    
    
    public static void clear() {
    	cache.clear();
	}
    
    public static boolean remove(String key) {
    	if(has(key)) {
    		cache.remove(key);
    		return true;
    	}
    	return false;
	}
    
    public static void start() {
        executorService.scheduleAtFixedRate(() -> {
        	for (var entry: cache.entrySet()) {
        		if (entry.getValue().isExpired()) {
                    cache.remove(entry.getKey());
                }
        	}
        }, 10, 10, TimeUnit.MINUTES);
    }

    public static void shutdown() {
        executorService.shutdown();
    }
    
    
    private static class CacheEntry {
	    private Object value;
	    private int ttlMinutes;
	    private long birth;
	
	    public CacheEntry(Object value, int ttlMinutes) {
	        this.value = value;
	        this.ttlMinutes = ttlMinutes;
	        this.birth = System.currentTimeMillis();
	    }
	
	    public Object getValue() {
	        return value;
	    }
	
	    @SuppressWarnings("unused")
		public int getTtlMinutes() {
	        return ttlMinutes;
	    }
	
	    public boolean isExpired() {
	        return System.currentTimeMillis()- birth >= ttlMinutes * 60 * 1000;
	    }
	}
}
