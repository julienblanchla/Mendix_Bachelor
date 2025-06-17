package viewer3d.shared.utils;


import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class URLHelper {
	public static Map<String, String> parseUrl(String url) {
		try {
            URL website = new URL(url);
            
            String protocol = website.getProtocol();
            String host = website.getHost();
            int port = website.getPort();
            String path = website.getPath();
            String query = website.getQuery();
            
            Util.DEFAULTLOGGER.info(String.format("protocol: %s, host: %s, port: %s, path: %s, query: %s", 
            		protocol, host, port, path, query));
            
            Map<String, String> res = new HashMap<>();
            res.put("protocol", protocol);
            res.put("host", host);
            res.put("port", String.valueOf(port));
            res.put("path", path);
            res.put("query", query);
            return  res;
        } catch (Exception e) {
            e.printStackTrace();
        }
		
		return null;
	}
	
	public static Map<String, String> parseQueryParameters(String url) {
		Map<String, String> parameters = new HashMap<>();
		
		try {
			URL website = new URL(url);
			String query = website.getQuery();
			String[] pairs = query.split("&");
			
			for (String pair : pairs) {
				String[] keyValue = pair.split("=");
				if (keyValue.length == 2) {
					String key = keyValue[0];
					String value = keyValue[1];

					key = key.trim();
					value = value.trim();

					parameters.put(key, value);
				}
			}
			
			return parameters;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
		return null;
    }

}


