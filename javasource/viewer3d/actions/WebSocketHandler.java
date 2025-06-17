package viewer3d.actions;

import javax.websocket.*;
import com.mendix.core.Core;
import com.mendix.logging.ILogNode;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import viewer3d.actions.websocket.IWebSocketController;
import viewer3d.shared.utils.Util;
import viewer3d.actions.websocket.WebSocketChannel;

public class WebSocketHandler {
	
	public class WebSocketEndpoint extends Endpoint {
		
		private ILogNode _logger;
		private Class<?> controllerCls; 
		
		public WebSocketEndpoint(Class<?> controllerCls) {
	        this._logger = Util.getLogger(WebSocketEndpoint.class.getSimpleName());
	        this.controllerCls = controllerCls;
	    }
		
		@Override
	    public void onOpen(Session session, EndpointConfig config) {
	        var endpoint = session.getBasicRemote();
	        var props = config.getUserProperties();
	        var sessionId = (String) props.get("mxSessionId");
        	if (sessionId != null) {
        		try {
        			var mxSession = Core.getSessionById(UUID.fromString(sessionId));
        			if(mxSession != null) {
        				var ctx = mxSession.createContext();
        				var mxConfig = Core.getConfiguration();
        				if (!mxConfig.getEnableGuestLogin()) {
        					var user = mxSession.getUser(ctx);
        					if (user.isAnonymous()) {
        						// invalid action
        						this._logger.warn("Request is not authenticated.");
        						return;
        					}
        				}
        				var controller = (IWebSocketController)controllerCls.getDeclaredConstructor().newInstance();
    					
    	                session.addMessageHandler(new WebSocketChannel(_logger, ctx, endpoint, controller));
    	                _logger.info("WebSocketController " + controllerCls.getName() + " is opened");
        			}
				} catch (InstantiationException e) {
					_logger.error(e);
				} catch (IllegalAccessException e) {
					_logger.error(e);
				} catch (IllegalArgumentException e) {
					_logger.error(e);
				} catch (InvocationTargetException e) {
					_logger.error(e);
				} catch (NoSuchMethodException e) {
					_logger.error(e);
				} catch (SecurityException e) {
					_logger.error(e);
				}
             }
	    }

	    @Override
	    public void onClose(Session session, CloseReason closeReason) {
	    	_logger.info("WebSocketController " + controllerCls.getName() + " is closed" + closeReason);
	    }
	}
	
	public final static WebSocketHandler instance = new WebSocketHandler();
    
    private String webSocketEndpoint;
   
    private Map<String, Class<?>> controllers = new HashMap<String, Class<?>>();

    public static void register(String webSocketEndpoint) {
    	WebSocketHandler.instance.webSocketEndpoint = "/" + webSocketEndpoint + "/";
    }
    
    public void addController(Class<?> controllerCls) {
    	try {
    		var ann = controllerCls.getAnnotation(Controller.class);
        	if(ann != null) {
        		var interfaces = controllerCls.getInterfaces();
        		var hasCorrectContract = false;
        		for(var contract : interfaces) {
        			if(contract == IWebSocketController.class) {
        				hasCorrectContract = true;
        				break;
        			}
        		}
        		
        		if(hasCorrectContract) {
        			var path = WebSocketHandler.instance.webSocketEndpoint +  ann.value();
            		WebSocketHandler.instance.controllers.put(path, controllerCls);
            		Core.addWebSocketEndpoint(path, new WebSocketEndpoint(controllerCls));
        		}
        	}
    	} catch(DeploymentException ex) {
    		var logger = Util.getLogger(WebSocketHandler.class.getSimpleName());
    		logger.error(ex);
    	}
    }
}
