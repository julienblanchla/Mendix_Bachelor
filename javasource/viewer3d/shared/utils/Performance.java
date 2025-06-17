package viewer3d.shared.utils;

import java.util.HashMap;
import java.util.Map;
import com.mendix.logging.ILogNode;

// naive version
public class Performance {
	private static Map<String, Long> tickMap = new HashMap<String, Long>();
	private static final ILogNode log = Util.getLogger("Performance");
	
	private static boolean debug = Debug.isDebugEnabled();
	

	public static void reset() {
		tickMap.clear();
	}
	
	public static void tick(String key) {
		if(!debug) return;
		
		long current = System.currentTimeMillis();
		if(tickMap.containsKey(key)) {
			// means endTick and restart again
			endTick(key);
			return;
		}
		
		tickMap.put(key, current);
		log.info(String.format("::%s event start at %s", key, current));
	}
	
	public static long endTick(String key) {
		if(!debug) return 0;
		
		long current = System.currentTimeMillis();
		if(!tickMap.containsKey(key)) {
			log.warn( String.format("You didn't track %s event before, skip", key)); 
			return 0;
		}
		
		long previous = tickMap.get(key);
		long duration = current-previous;
		tickMap.remove(key);
		log.info(String.format("##%s event start:end:duration %s:%s:%s", key, previous, current, duration));
		return duration;
	}
}
