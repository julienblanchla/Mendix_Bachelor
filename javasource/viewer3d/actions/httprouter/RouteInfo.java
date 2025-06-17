package viewer3d.actions.httprouter;

import java.lang.reflect.*;
import java.util.Map;

public class RouteInfo {
    private Class<?> cls;

    public Class<?> getCls() {
        return this.cls;
    }

    public void setCls(Class<?> value) {
        this.cls = value;
    }

    private Method mtd;

    public Method getMtd() {
        return this.mtd;
    }

    public void setMtd(Method value) {
        this.mtd = value;
    }

    private String pattern;

    public String getPattern() {
        return this.pattern;
    }

    public void setPattern(String value) {
        this.pattern = value;
    }

    private Map<String, String> variableMap;

    public Map<String, String> getVariableMap() {
        return this.variableMap;
    }

    public void setVariableMap(Map<String, String> value) {
        this.variableMap = value;
    }
}
