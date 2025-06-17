package viewer3d.actions;

import javax.servlet.MultipartConfigElement;

import com.mendix.core.Core;
import com.mendix.externalinterface.connector.RequestHandler;
import com.mendix.m2ee.api.*;

import viewer3d.actions.httprouter.HttpRouter;

public class HttpHandler extends RequestHandler {
	public static final HttpHandler instance = new HttpHandler();

	public static void register(String httpEndpoint) {
		Core.addRequestHandler(httpEndpoint + "/", HttpHandler.instance);
	}

	private HttpRouter router = new HttpRouter();

	public void addController(Class<?> controllerCls) {
		this.router.addController(controllerCls);
	}

	@Override
	public void processRequest(IMxRuntimeRequest request, IMxRuntimeResponse response, String path) throws Exception {
		var ctx = this.getSessionFromRequest(request).createContext();
		var config = Core.getConfiguration();
		var req = request.getHttpServletRequest();
		var contentType = req.getContentType();
		
		if (contentType != null && contentType.startsWith("multipart/form-data")) {
			req.setAttribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement(""));
		}
		
		if (!config.getEnableGuestLogin()) {
			var session = ctx.getSession();
			var user = session.getUser(ctx);
			if (user.isAnonymous()) {
				// invalid action
				response.setStatus(IMxRuntimeResponse.UNAUTHORIZED);
				this.logger.warn("Request is not authenticated.");
				return;
			}
		}

		this.router.processRequest(ctx, request, response, path);
	}
}
