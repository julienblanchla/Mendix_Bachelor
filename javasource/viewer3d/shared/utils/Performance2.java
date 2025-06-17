package viewer3d.shared.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.MutableTriple;

import com.mendix.logging.ILogNode;

public class Performance2 {
	
	// data structure: 
	// event: [start, end, duration]
	private static Map<String, MutableTriple<Long, Long, Long>> tickMap = new HashMap<String, MutableTriple<Long, Long, Long>>();
	private static ArrayList<String> events = new ArrayList<String>();
	
	private static final ILogNode log = Util.getLogger("Performance2");
	
	private static boolean debug = true;

	public static void setActive(boolean active) {
		debug = active;	
	}
	
	public static void reset() {
		tickMap.clear();
		events.clear();
	}
	
	public static void tick(String key) {
		if(!debug) return;
		
		long current = System.currentTimeMillis();
		if(tickMap.containsKey(key)) {
			log.warn( String.format("You already have %s event ticking, it's value will be overriden.", key)); 
		}
		
		events.add(key);
		tickMap.put(key, MutableTriple.of(current, 0L, 0L));
		log.info(String.format("::%s event start at %s", key, current));
	}
	
	public static long endTick(String key) {
		if(!debug) return 0;
		
		long current = System.currentTimeMillis();
		if(tickMap.containsKey(key)) {
			log.warn( String.format("You didn't track %s event before, skip", key)); 
			return 0;
		}
		
		var value = (MutableTriple<Long, Long, Long>) tickMap.get(key);
		long previous = value.getLeft();
		long duration = current-previous;
		value.setMiddle(current);
		value.setRight(duration);
		log.info(String.format("##%s event duration %s", key, duration));
		return duration;
	}
	
	public static long getDuration(String key) {
		if(!debug) return 0;
		
		if(tickMap.containsKey(key)) {
			log.warn( String.format("You didn't track %s event, skip", key)); 
			return 0;
		}
		return tickMap.get(key).getRight();
	}
	
	public static void report() {
		if(!debug) return;
				
		log.info( "Event: start	end	duration");
		for (String key : events) {
			var value = tickMap.get(key);			
			log.info( String.format("Event %s: %s	%s	%s\n", key, value.getLeft(), value.getMiddle(), value.getRight())); 
		}
		log.info( "End");
	}
}
