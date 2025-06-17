package viewer3d.shared.utils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.mendix.core.Core;
import com.mendix.logging.ILogNode;
import com.mendix.m2ee.api.IMxRuntimeRequest;
import com.mendix.m2ee.api.IMxRuntimeResponse;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.viewer3d.jtreader.JtUtils;
import com.mendix.viewer3d.jtreader.StreamType;

public class Util {
	public static String loggerPrefix = Constants.MODULE_NAME;
	public static final ILogNode DEFAULTLOGGER = Core.getLogger(loggerPrefix);	
	public static ILogNode getLogger(String id) {
		id = String.format("%s_%s", loggerPrefix, id);
		return Core.getLogger(id);
	};
	
	private static volatile int uuid = -1;
	public static int getNextId() {
		uuid = uuid + 1;
		return uuid;
	}
	
	public static boolean isValidModelId(String modelId) {
		if (modelId.startsWith("usermodel&")) {
			String userModelId = modelId.substring(10);
			try {
				Integer.parseInt(userModelId);
				return true;
			} catch (NumberFormatException ex) {
				return false;
			}
		} else {
			try {
				UUID.fromString(modelId);
				return true;
			} catch (IllegalArgumentException ex) {
				return false;
			}
		}
	}

	public static Set<UUID> getSegmentIds(IMxRuntimeRequest request, StreamType st) throws IOException {
		if (st == StreamType.ProductStructure) {
			return null;
		}
		return JtUtils.INSTANCE.parseSegmentIds(request.getInputStream());
	}

	public static boolean isValidRequest(IContext ctx, IMxRuntimeRequest req, IMxRuntimeResponse response) {
		var reqCsrfToken = req.getHeader("x-csrf-token");
		var sessionCsrfToken = ctx.getSession().getCsrfToken();
		if (reqCsrfToken == null || !reqCsrfToken.equals(sessionCsrfToken)) {
			response.setContentType("text/plain;charset=utf-8");
			response.setStatus(IMxRuntimeResponse.BAD_REQUEST);
			return false;
		}
		return true;
	}

	@FunctionalInterface
	public interface ExceptionConsumer<T> {
		void accept(T ctx) throws Exception;
	}

	public static void handleTransaction(IContext context, ExceptionConsumer<IContext> action) throws Exception {
		try {
			context.startTransaction();
			action.accept(context);
		} catch (Exception ex) {
			context.rollbackTransaction();
			throw ex;
		} finally {
			context.endTransaction();
		}
	}
	
    public static boolean isBlank(final CharSequence cs) {
        final int strLen = cs == null ? 0 : cs.length();
        if (strLen == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    
    
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");	
    public static String getFormatDate(long time) {
    	// use current time if not specified
    	if(time<1) {
    		time = System.currentTimeMillis();
    	}
		String formattedDate = dateFormat.format(new Date(time));
		return formattedDate;		
	}
}
