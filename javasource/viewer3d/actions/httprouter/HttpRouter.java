package viewer3d.actions.httprouter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import com.mendix.core.Core;
import com.mendix.logging.ILogNode;
import com.mendix.m2ee.api.*;
import com.mendix.systemwideinterfaces.core.IContext;

import viewer3d.actions.httprouter.annotation.*;
import viewer3d.shared.utils.Util;

public class HttpRouter {
	private RouteMap routeMap;

	private ILogNode logger;

	public HttpRouter() {
		this.logger = Util.getLogger(HttpRouter.class.getSimpleName());
		this.routeMap = new RouteMap();
	}

	public void addController(Class<?> controllerCls) {
		this.routeMap.registerClass(controllerCls);
	}

	public void processRequest(IContext ctx, IMxRuntimeRequest request, IMxRuntimeResponse response, String path) {
		this.logger.info("Processing request: path = " + path);
		if (!Util.isValidRequest(ctx, request, response)) {
			return;
		}
		// find the matched method according to HTTP method and path
		var httpMethod = request.getHttpServletRequest().getMethod().toUpperCase();
		var routeInfo = this.routeMap.resolveRouteInfo(httpMethod, path);
		if (routeInfo == null) {
			response.setStatus(IMxRuntimeResponse.NOT_FOUND);
			return;
		}
		try {
			var controller = routeInfo.getCls().getDeclaredConstructor().newInstance();
			this.logger.info("Routing to controller " + routeInfo.getCls().getSimpleName() + " and method "
					+ routeInfo.getMtd().getName());
			// resolve parameters
			var paramInputObjects = resolveParamInputObjects(ctx, request, response, path, routeInfo);
			// invoke handler method
			routeInfo.getMtd().invoke(controller, paramInputObjects);
		} catch (InvocationTargetException ex) {
			this.logger.error(ex.getCause());
			response.setStatus(IMxRuntimeResponse.INTERNAL_SERVER_ERROR);
		} catch (Exception ex) {
			this.logger.error(ex);
			response.setStatus(IMxRuntimeResponse.INTERNAL_SERVER_ERROR);
		}
	}

	private Object[] resolveParamInputObjects(IContext ctx, IMxRuntimeRequest request, IMxRuntimeResponse response,
			String path, RouteInfo routeInfo) throws Exception {
		var parameters = routeInfo.getMtd().getParameters();
		var paramInputObjects = new ArrayList<Object>(parameters.length);
		Map<String, Object> queryParamMap = null;
		for (var p : parameters) {
			// check the annotation
			var annotations = p.getAnnotations();
			var aList = getParamAnnotations(annotations);
			if (aList.size() == 0) {
				// there's an un-annotated param, throw
				throw new Exception("Parameter " + p.getName() + " does not have any annotation.");
			}
			if (aList.size() > 1) {
				// there are multiple annotations, throw
				throw new Exception("Parameter " + p.getName() + " have multiple annotations.");
			}
			var aClass = aList.get(0);

			// resolve parameter
			if (aClass == PathVariable.class) {
				// only support string for now
				if (p.getType() != String.class) {
					throw new Exception("Only String is supported as type of param with annotation PathVariable.");
				}
				var paramName = p.getAnnotation(PathVariable.class).value();
				paramInputObjects.add(routeInfo.getVariableMap().get(paramName));
			} else if (aClass == RequestParam.class) {
				// only support string for now
				if (p.getType() != String.class) {
					throw new Exception("Only String is supported as type of param with annotation RequestParam.");
				}
				var paramName = p.getAnnotation(RequestParam.class).value();
				if (queryParamMap == null) {
					queryParamMap = this.createQueryParamMap(request.getHttpServletRequest());
				}
				var paramValue = queryParamMap.get(paramName);
				paramInputObjects.add(paramValue);
			} else if (aClass == FormPart.class) {
				var paramName = p.getAnnotation(FormPart.class).value();
				if (queryParamMap == null) {
					queryParamMap = this.createFormPartMap(request.getHttpServletRequest());
				}
				var paramValue = queryParamMap.get(paramName);
				paramInputObjects.add(paramValue);
			} else if (aClass == Request.class) {
				paramInputObjects.add(request);
			} else if (aClass == Response.class) {
				paramInputObjects.add(response);
			} else if (aClass == Context.class) {
				paramInputObjects.add(ctx);
			}
		}
		return paramInputObjects.toArray();
	}

	private List<Class<?>> getParamAnnotations(Annotation[] annotations) {
		// supported annotations: @Request, @Response, @RequestParam, @PathVariable,
		// @Context
		var ret = new ArrayList<Class<?>>();
		for (var a : annotations) {
			var type = a.annotationType();
			if (type == Request.class) {
				ret.add(Request.class);
			} else if (type == Response.class) {
				ret.add(Response.class);
			} else if (type == RequestParam.class) {
				ret.add(RequestParam.class);
			} else if (type == FormPart.class) {
				ret.add(FormPart.class);
			} else if (type == PathVariable.class) {
				ret.add(PathVariable.class);
			} else if (type == Context.class) {
				ret.add(Context.class);
			}
		}
		return ret;
	}

	private Map<String, Object> createQueryParamMap(HttpServletRequest req) throws IOException, ServletException {
		var parts = req.getParts();
		var contentType = req.getContentType();
		var ret = new HashMap<String, Object>();
		if (contentType != null && contentType.startsWith("multipart/form-data")) {
			for (var part : parts) {
				var name = part.getName();
				var type = part.getContentType();
				var stream = part.getInputStream();
				if (type != null) {
					ret.put(name, stream);
				} else {
					var str = convert(stream, StandardCharsets.UTF_8);
					ret.put(name, str);
				}
			}
		} else {
			var qs = req.getQueryString();
			var paramList = qs.split("&");
			for (var param : paramList) {
				var arr = param.split("=");
				ret.put(arr[0], URLDecoder.decode(arr[1], StandardCharsets.UTF_8));
			}
		}

		return ret;
	}

	private Map<String, Object> createFormPartMap(HttpServletRequest req) throws IOException, ServletException {
		var parts = req.getParts();
		var ret = new HashMap<String, Object>();
		if (parts != null) {						
			for (var part : parts) {
				var name = part.getName();
				var type = part.getContentType();
				var stream = part.getInputStream();
				if (type != null) {
					// handle array case
					this.updateMap(ret, name, stream);
				} else {
					var str = convert(stream, StandardCharsets.UTF_8);
					ret.put(name, str);
				}
			}
		}
		return ret;
	}
	
	private void updateMap(HashMap<String, Object> targetMap, String name, InputStream value) {
		var prev = targetMap.get(name);
		if(prev == null) {
			var newValue = new ArrayList<InputStream>();
			targetMap.put(name, newValue);
			newValue.add(value);	
		} else {			
			((ArrayList<InputStream>)prev).add(value);
		}
	}

	private String convert(InputStream inputStream, Charset charset) throws IOException {

		StringBuilder stringBuilder = new StringBuilder();
		String line = null;

		try (var bufferedReader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
			while ((line = bufferedReader.readLine()) != null) {
				stringBuilder.append(line);
			}
		}

		return stringBuilder.toString();
	}
}
