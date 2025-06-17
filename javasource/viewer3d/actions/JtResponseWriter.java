package viewer3d.actions;

import java.io.IOException;

import com.mendix.m2ee.api.IMxRuntimeResponse;
import com.mendix.viewer3d.jtreader.JtOutputWriter;

public class JtResponseWriter implements JtOutputWriter {
    private IMxRuntimeResponse response;

    public JtResponseWriter(IMxRuntimeResponse response, String modelId) {
        this.response = response;
    }

    @Override
    public void setLength(int length) {
        response.addHeader("Content-Length", Integer.toString(length));
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        response.getOutputStream().write(b, off, len);
    }
}
