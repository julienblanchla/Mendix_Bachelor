package viewer3d.actions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.mendix.viewer3d.jtreader.JtOutputWriter;

public class JtBufferResponseWriter implements JtOutputWriter {
    private ByteArrayOutputStream bufferStream;

    public JtBufferResponseWriter(String modelId) {

    }

    @Override
    public void setLength(int length) {
        this.bufferStream = new ByteArrayOutputStream(length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        this.bufferStream.write(b, off, len);
    }

    public byte[] buffer() {
        return this.bufferStream != null ? this.bufferStream.toByteArray() : null;
    }

    public int size() {
        return this.bufferStream != null ? this.bufferStream.size() : 0;
    }
}
