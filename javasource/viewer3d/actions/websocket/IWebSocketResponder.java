package viewer3d.actions.websocket;

import java.util.Map;

public interface IWebSocketResponder {

	void respond(Object response, int totalLength, int contentLength, Map<String, String> headers, boolean hasMore);
}
