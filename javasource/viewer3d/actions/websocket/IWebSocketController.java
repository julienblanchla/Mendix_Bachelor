package viewer3d.actions.websocket;

import java.util.Map;

import com.mendix.systemwideinterfaces.core.IContext;

public interface IWebSocketController {
	
	void handleRequest(IContext context, Object request, Map<String, String> headers, String method, int messageId, IWebSocketResponder responder);
}
