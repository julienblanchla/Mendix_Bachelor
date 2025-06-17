package viewer3d.actions.websocket;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;

import com.google.flatbuffers.FlatBufferBuilder;
import com.mendix.logging.ILogNode;
import com.mendix.systemwideinterfaces.core.IContext;

import viewer3d.actions.websocketprotocol.ContentType;
import viewer3d.actions.websocketprotocol.HeaderEntry;
import viewer3d.actions.websocketprotocol.Message;
import viewer3d.actions.websocketprotocol.MessageContent;
import viewer3d.actions.websocketprotocol.MessageFlags;
import viewer3d.actions.websocketprotocol.MessageType;

public class WebSocketChannel implements MessageHandler.Whole<ByteBuffer> {
	
	private RemoteEndpoint.Basic _endPoint;
	private IWebSocketController _handler;
	private ILogNode _logger;
	private IContext _context;
	private static AtomicInteger msgIdCounter = new AtomicInteger(0);
	
	public WebSocketChannel(ILogNode logger, IContext context, RemoteEndpoint.Basic endPoint, IWebSocketController handler) {
		this._logger = logger;
		this._endPoint = endPoint;
		this._handler = handler;
		this._context = context;
	}

	@Override
	public void onMessage(ByteBuffer buffer) {
		var offset = 0;
        var rawBodyFlag = buffer.getInt();
        offset += 4;

        if (Utils.hasBit(rawBodyFlag, 0))
        {
            offset += 4;
        }

        buffer.position(offset);
        var message = Message.getRootAsMessage(buffer);
        var messageType = message.messageType();
        var responseType = message.responseType();
        var messageFlags = message.messageFlags();
        var content = message.content();
        var headerLength = content.headerLength();

        var header = new HashMap<String, String>();
        var entry = new HeaderEntry();
        for (var i = 0; i < headerLength; i += 1)
        {
            content.header(entry, i);
            header.put(entry.name(), entry.value());
        }

        if (messageType != MessageType.Request)
        {
            throw new UnsupportedOperationException();
        }

        var contentType = message.contentType();
        var request = _decodeBody(buffer, content, contentType);
        var method = message.action();
        var id = message.id();
 
        if(Utils.hasFlag(messageFlags, MessageFlags.Authentication)) {
			_sendResponse(null, responseType, header, method, id, MessageFlags.Welcome, -1, -1);
		} else if(Utils.hasFlag(messageFlags, MessageFlags.None)) {
			this._handler.handleRequest(this._context, request, header, method, id, (Object response, int totalLength, int contentLength, Map<String, String> headers, boolean hasMore) -> {
				var flags = hasMore == true ? MessageFlags.None : MessageFlags.Complete;
				this._sendResponse(response, responseType, header, method, id, flags, totalLength, contentLength);
			});
		}
	}

	private Object _decodeBody(ByteBuffer buffer, MessageContent content, byte contentType) {
		Object request = null;
        var bodyLength = content.bodyLength();
        if(bodyLength > 0) {
        	 switch (contentType)
             {
                 case ContentType.Text:
                	var charBuffer = StandardCharsets.UTF_8.decode(buffer);
                  	request = new String(charBuffer.array());
                 case ContentType.Json:
                    throw new UnsupportedOperationException();
                 default:
                	 request = content.bodyAsByteBuffer();
             }
        }
		return request;
	}
	
	@SuppressWarnings("unchecked")
	private void _sendResponse(Object data, byte contentType, Map<String, String> headers, String action, int requestId, byte flags, int totalLength, int contentLength) {
		if(data != null) {
			if(data instanceof Exception) {
				flags = MessageFlags.Error;
				data = ((Exception)data).getMessage();
			}
			
			var result = _encodeBody(data, contentType);
			data = result.data;
			contentType = result.type;
		}
		
		// size 942808
		var msg = _createResponse(requestId, contentType, action, headers, flags, totalLength, contentLength);
		try {
			if(data == null) {
				this._endPoint.sendBinary(msg);
			} else {
				this._endPoint.sendBinary(msg, false);
				if(data instanceof ArrayList<?>) {
					var buffers = (ArrayList<ByteBuffer>)data;
					for(int i = 0, end = buffers.size() - 1; i <= end; i++) {
						var buffer = buffers.get(i);
						this._endPoint.sendBinary(buffer, i == end);
						
					}
				} else {
					this._endPoint.sendBinary((ByteBuffer)data, true);
				}
			}
			
		} catch (Exception e) {
			this._logger.error(e);
		}
	}
	
	private BodyResult _encodeBody(Object body, byte contentType) {
		var result = new BodyResult();
		if(body instanceof ByteBuffer) {
			result.data = (ByteBuffer)body;
			result.type = ContentType.ArrayBuffer;
		} else if(body instanceof ArrayList<?>) { 
			result.data = body;
			result.type = ContentType.ArrayBuffer;
		} else if(body instanceof byte[]) {
			result.data = ByteBuffer.wrap((byte[])body);
			result.type = ContentType.ArrayBuffer;
		} else if(body instanceof String) {
			result.data = StandardCharsets.UTF_8.encode((String)body);
			result.type = ContentType.Text;
		}
		return result;
	}
	
	private ByteBuffer _createResponse(int correlationId, byte contentType, String action, Map<String, String> headers, byte flags, int totalLength, int contentLength) {
		var msgId = msgIdCounter.incrementAndGet();
		var builder = new FlatBufferBuilder(256 + 5);
		
		if(totalLength > 0 && contentLength > 0) {
			if(headers == null) {
				headers = new HashMap<String, String>();
			}
			
			headers.put("content-length", Integer.toString(contentLength));
			headers.put("total-length", Integer.toString(totalLength));
		}
		

        // build header
		var headerOffset = -1;
		if(headers != null) {
			var headerSize = headers.size();
			if(headerSize > 0) {
				var headerEntries = new int[headerSize];
				var idx = 0;
		        for(Map.Entry<String, String> entry:  headers.entrySet()) {
		        	var name = builder.createString(entry.getKey());
		            var value = builder.createString(entry.getValue());
		            HeaderEntry.startHeaderEntry(builder);
		            HeaderEntry.addName(builder, name);
		            HeaderEntry.addValue(builder, value);
		            var offset  = HeaderEntry.endHeaderEntry(builder);
		            headerEntries[idx++] = offset;
		        }
		        
		        headerOffset = MessageContent.createHeaderVector(builder, headerEntries);
			}
		}

        MessageContent.startMessageContent(builder);
        if (headerOffset >= 0)
        {
            MessageContent.addHeader(builder, headerOffset);
        }

        var contentOffset = MessageContent.endMessageContent(builder);

        var actionOffset = -1;
        if (action != null && action.length() > 0)
        {
            actionOffset = builder.createString(action);
        }

        Message.startMessage(builder);
        if (actionOffset >= 0)
        {
            Message.addAction(builder, actionOffset);
        }

        Message.addContent(builder, contentOffset);
        Message.addId(builder, msgId);
        Message.addCorrelationId(builder, correlationId);
        Message.addMessageFlags(builder, flags);
        Message.addMessageType(builder, MessageType.Response);
        Message.addContentType(builder, contentType);

        var messageOffset = Message.endMessage(builder);
        builder.finish(messageOffset);
        var msgArray = builder.sizedByteArray();
        var msgSize = msgArray.length;
        var buffer = ByteBuffer.allocate(msgSize + 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(1);
        buffer.putInt(msgSize);
        buffer.put(msgArray);
        buffer.rewind();
        return buffer;
	}
		
}
