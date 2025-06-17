package viewer3d.actions;

public enum ErrorCodes {
	Internal_Error(0),
	Fetch_Failure(1),
	Query_Failure(2),
	Fetch_Zip_Failure(3),
	File_Missing_In_Zip(4),
	Invalid_JT(0),
	Local_File_Query_Not_Support_Multiple_Files(5),
	Not_Supported_Version(6),
	Not_Current_Model(7);
	
	private final int _value;
    private ErrorCodes(int value) {
        this._value = value;
    }
    
    public String message() {
    	return String.format("{\"code\":%d}", _value);
    }
}
