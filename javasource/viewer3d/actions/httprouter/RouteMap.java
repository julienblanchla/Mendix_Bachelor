package viewer3d.actions.httprouter;

import java.lang.reflect.Method;
import java.util.HashMap;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import viewer3d.actions.Controller;
import viewer3d.actions.httprouter.annotation.*;

public class RouteMap {
    private HashMap<String, HashMap<String, Pair<Class<?>, Method>>> map;

    private PathMatcher pathMatcher;

    public RouteMap() {
        this.map = new HashMap<String, HashMap<String, Pair<Class<?>, Method>>>();
        this.pathMatcher = new AntPathMatcher();
    }

    public void registerClass(Class<?> cls) {
    	var ann = cls.getAnnotation(Controller.class);
    	if(ann != null) {
    		var path = ann.value();
    		if(path.lastIndexOf("/") != path.length() - 1) {
    			path += "/";
    		}
    		
    		for (var mtd : cls.getDeclaredMethods()) {
                if (mtd.isAnnotationPresent(GetMapping.class)) {
                    var pattern = mtd.getDeclaredAnnotation(GetMapping.class).value();
                    this.addRoute("GET", path + pattern, cls, mtd);
                }
                if (mtd.isAnnotationPresent(DeleteMapping.class)) {
                    var pattern = mtd.getDeclaredAnnotation(DeleteMapping.class).value();
                    this.addRoute("DELETE",  path + pattern, cls, mtd);
                }
                if (mtd.isAnnotationPresent(PostMapping.class)) {
                    var pattern = mtd.getDeclaredAnnotation(PostMapping.class).value();
                    this.addRoute("POST",  path + pattern, cls, mtd);
                }
                if (mtd.isAnnotationPresent(PutMapping.class)) {
                    var pattern = mtd.getDeclaredAnnotation(PutMapping.class).value();
                    this.addRoute("PUT",  path + pattern, cls, mtd);
                }
            }
    	}
        
    }

    public RouteInfo resolveRouteInfo(String httpMethod, String path) {
        var innerMap = this.map.get(httpMethod);
        if (innerMap == null) {
            return null;
        }

        for (var pattern : innerMap.keySet()) {
            if (this.pathMatcher.match(pattern, path)) {
                var tuple = innerMap.get(pattern);
                var routeInfo = new RouteInfo();
                routeInfo.setCls(tuple.getLeft());
                routeInfo.setMtd(tuple.getRight());
                routeInfo.setPattern(pattern);
                routeInfo.setVariableMap(this.pathMatcher.extractUriTemplateVariables(pattern, path));
                return routeInfo;
            }
        }

        return null;
    }

    private void addRoute(String httpMethod, String pattern, Class<?> cls, Method mtd) {
        if (!this.map.containsKey(httpMethod)) {
            this.map.put(httpMethod, new HashMap<String, Pair<Class<?>, Method>>());
        }

        var innerMap = this.map.get(httpMethod);
        innerMap.put(pattern, Pair.of(cls, mtd));
    }
}
