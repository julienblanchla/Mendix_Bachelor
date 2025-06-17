package viewer3d.actions;

public interface JtOutputWriter {
    public void setLength(int length) throws java.lang.Exception;

    public void write(byte[] bytes, int offset, int length) throws java.lang.Exception;
}
