package viewer3d.actions.websocket;

public class Utils {
	
	public static Boolean hasBit(int bitPattern, int posIndex)
    {
        return (bitPattern & 1 << posIndex) != 0;
    }
	
	public static boolean hasFlag(byte value, byte flag) {
		return (value & flag) == flag;
	}

}
