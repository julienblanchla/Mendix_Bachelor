package viewer3d.actions;

public class JTException extends Exception{
	private static final long serialVersionUID = 1L;

	private JTException(String errorMessage) {
		super(errorMessage);
	}
	
	public static JTException create(ErrorCodes errorCode) {
		return new JTException(errorCode.message());
	}
}
