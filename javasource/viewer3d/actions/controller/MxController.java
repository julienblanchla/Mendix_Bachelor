package viewer3d.actions.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.core.objectmanagement.member.MendixBinary;
import com.mendix.logging.ILogNode;
import com.mendix.m2ee.api.IMxRuntimeRequest;
import com.mendix.m2ee.api.IMxRuntimeResponse;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import com.mendix.thirdparty.org.json.JSONObject;
import com.mendix.viewer3d.jtreader.JtReader;
import com.mendix.viewer3d.jtreader.JtUtils;
import com.mendix.viewer3d.jtreader.StreamType;

import viewer3d.actions.Controller;
import viewer3d.actions.ErrorCodes;
import viewer3d.actions.JTException;
import viewer3d.actions.JtResponseWriter;
import viewer3d.actions.MendixFileDocumentDataProvider;
import viewer3d.actions.ModelType;
import viewer3d.actions.httprouter.annotation.Context;
import viewer3d.actions.httprouter.annotation.FormPart;
import viewer3d.actions.httprouter.annotation.GetMapping;
import viewer3d.actions.httprouter.annotation.PathVariable;
import viewer3d.actions.httprouter.annotation.PostMapping;
import viewer3d.actions.httprouter.annotation.Request;
import viewer3d.actions.httprouter.annotation.Response;
import viewer3d.actions.websocket.IWebSocketController;
import viewer3d.actions.websocket.IWebSocketResponder;
import viewer3d.proxies.MxChildDocument;
import viewer3d.proxies.MxModelDocument;
import viewer3d.proxies.Session;
import viewer3d.shared.utils.FileHandling;
import viewer3d.shared.utils.Util;
import viewer3d.shared.utils.Util.ExceptionConsumer;

@Controller("mx")
public class MxController implements IWebSocketController {

    private static final ILogNode logger = Util.DEFAULTLOGGER;

    private static final int JT_HEADER_LENGTH = 80;

    static String VERSION_PATTERN = "(\\d+)[.](\\d+)";
    
    static String INVALID_VERSION = "Only JT models with version starting from 9.0 are supported";
    
    static String INVALID_ENTRY_PATH = "Invalid entry path";
    
    static String INVALID_ZIP_FILE = "The zipped Model file is invalid";

    
    /**
     * ref: viewer3d.actions.httprouter: resolveParamInputObjects
     * 
     * 
     * */
    @PostMapping("models")
    public void postModel(@Context IContext context, 
    		@Request IMxRuntimeRequest request,
            @Response IMxRuntimeResponse response, 
            @FormPart("files") ArrayList<?> filesInput,
            @FormPart("modelType") String modelTypeStr, 
            @FormPart("name") String name,
            // upload single file jt in batch mode
            @FormPart("batch") String batch,
            @FormPart("entryPath") String entryPath) throws Exception {
    	    	

        var modelType = ModelType.values()[Integer.parseInt(modelTypeStr)];
        if(filesInput.size()<=0) {
        	logger.warn("missing upload files");
        	return;
        }        
		@SuppressWarnings("unchecked")
		// Convert file to InputStream already takes lots of time
		// check viewer3d.actions.httprouter::createFormPartMap		
		var files = (ArrayList<InputStream>)filesInput;		
		InputStream file = files.get(0);

        ExceptionConsumer<IContext> modelConsumer = (IContext ctx) -> {
        	// Case multiple model: each file is a independent model
        	if("true".equalsIgnoreCase(batch) && files.size()>1) {
        		this.batchSaveFiles(ctx, response, files, name, modelType);
        		return;
        	}        	
        	
        	// Others: single Model
            MxModelDocument obj = null;
            if(files.size()==1) {
            	if (name.toLowerCase().endsWith(".zip")) {
                    obj = this.saveZippedFile(ctx, response, file, entryPath, modelType);
                } else {
                    obj = this.saveFile(ctx, response, file, name, modelType);
                }
            } else {
            	// multiple files or folder upload (shattered jt)             	
            	obj = this.saveFiles(ctx, response, files, name, modelType);
            }            

            if (obj == null) {
                return;
            }
            var modelId = obj.getModelId();
            var oStream = response.getOutputStream();
            oStream.write(modelId.getBytes());
            response.setContentType("text/plain;charset=utf-8");
            response.setStatus(IMxRuntimeResponse.OK);
            oStream.close();
        };
        Util.handleTransaction(context, modelConsumer);
    }
    
    
    /**
     * Used for retrieve other file formats exclude JT
     * 
     * modelId format:
     * 
     * - ${uuid}                           means MxModelDocument
     * - ${uuid}@@path1>>path2>>coll.jt    MxChildDocument
     * - ${uuid}@@path1>>coll.bin          MxChildDocument
     * - ${uuid}@@coll.bmp                 MxChildDocument
     * 
     * */
    @GetMapping("{modelId}")
    public void getModel(@Context IContext context, 
    		@Request IMxRuntimeRequest request, 
    		@Response IMxRuntimeResponse response,
            @PathVariable("modelId") String modelId) throws Exception {
        if(!Util.isValidRequest(context,request,response)){
            response.setStatus(IMxRuntimeResponse.BAD_REQUEST);
            return;
        }
        ExceptionConsumer<IContext> requestConsumer = (IContext ctx) -> {
            String childName = null; 
            String id=modelId;
            int idx = modelId.indexOf("@@");
            if(idx>-1) {
                id        = modelId.substring(0, idx);
                childName = modelId.substring(idx+2);
                childName = childName.replaceAll(">>", "/");
            }
            
            // build the query
            String xpath = "//Viewer3D.MxModelDocument[ModelId='" + id + "']";
            if(childName!=null) {
                xpath = xpath + "/Viewer3D.ModelEntries/Viewer3D.MxChildDocument[Name='" + childName + "']";
            }
            logger.info(xpath);
            
            // TODO: ref https://apidocs.rnd.mendix.com/9/runtime/com/mendix/core/Core.html#createXPathQuery(java.lang.String)           
            var documents = Core.retrieveXPathQuery(ctx, xpath);
            if (documents.isEmpty()) {
                logger.info("modelId not found: " + modelId);
                httpError(response, ErrorCodes.Fetch_Failure);
                return;
            }else {
                var document = documents.get(0);
                FileHandling.setRespondWithFile(context, response, document);
                response.setContentType("application/octet-stream");
            }             
        };
        Util.handleTransaction(context, requestConsumer);
    }

	

    @GetMapping("{modelId}/viewer/session")
    public void getViewerSessionData(@Context IContext context, @Request IMxRuntimeRequest request, 
        @Response IMxRuntimeResponse response,
        @PathVariable("modelId") String modelId) throws Exception {
        JSONObject sessionData = new JSONObject();

        ExceptionConsumer<IContext> sessionConsumer = (IContext ctx) -> {
            var viewerSession = getViewerSession(ctx, modelId);
            if(viewerSession == null) {
                logger.error("No associated UserSession founded for " + modelId);
                response.setStatus(IMxRuntimeResponse.NOT_FOUND);
                return;
            }

            var data = viewerSession.getData();
            if(data == null || data.isEmpty()) {
                sessionData.put("session", "{}");
            } else {
                sessionData.put("session", data);
            }
        };

        Util.handleTransaction(context, sessionConsumer);
        
        response.setContentType("applications/json");
        logger.info("JSON: " + sessionData.toString());
        response.getWriter().write(sessionData.toString());
        response.setStatus(IMxRuntimeResponse.OK);
    }

    @PostMapping("{modelId}/viewer/session")
    public void updateViewerSession(@Context IContext context, @Request IMxRuntimeRequest request, 
        @Response IMxRuntimeResponse response,
        @PathVariable("modelId") String modelId,
        @FormPart("data") String data) throws Exception {
        
        ExceptionConsumer<IContext> sessionConsumer = (IContext ctx) -> {
            var viewerSession = getViewerSession(ctx, modelId);
            if(viewerSession == null) {
                logger.error("No associated UserSession founded for " + modelId);
                response.setStatus(IMxRuntimeResponse.NOT_FOUND);
                return;
            }
    
            logger.info("Update Session for " + modelId + " Data: " + data);
            viewerSession.setData(ctx, data);
            Core.commit(ctx, viewerSession.getMendixObject());
        };

        Util.handleTransaction(context, sessionConsumer);
       
        response.setContentType("text/plain;charset=utf-8");
        response.setStatus(IMxRuntimeResponse.OK);
    }

    @GetMapping("{modelId}/ps")
    public void getPs(@Context IContext ctx, @Request IMxRuntimeRequest request, @Response IMxRuntimeResponse response,
            @PathVariable("modelId") String modelId) throws Exception {
        this.processJtRequest(ctx, request, response, modelId, StreamType.ProductStructure);
    }

    @PostMapping("{modelId}/shapes")
    public void getShapes(@Context IContext ctx, @Request IMxRuntimeRequest request,
            @Response IMxRuntimeResponse response, @PathVariable("modelId") String modelId) throws Exception {
        this.processJtRequest(ctx, request, response, modelId, StreamType.Shapes);
    }

    @PostMapping("{modelId}/pmi")
    public void getPmi(@Context IContext ctx, @Request IMxRuntimeRequest request, @Response IMxRuntimeResponse response,
            @PathVariable("modelId") String modelId) throws Exception {
        this.processJtRequest(ctx, request, response, modelId, StreamType.Pmi);
    }

    @PostMapping("{modelId}/metadata")
    public void getMetadata(@Context IContext ctx, @Request IMxRuntimeRequest request,
            @Response IMxRuntimeResponse response, @PathVariable("modelId") String modelId) throws Exception {
        this.processJtRequest(ctx, request, response, modelId, StreamType.Metadata);
    }
    
    @GetMapping("{modelId}/2DImage")
    public void get2dImage(@Context IContext ctx, @Request IMxRuntimeRequest request, @Response IMxRuntimeResponse response,
            @PathVariable("modelId") String modelId) throws Exception {
        this.process2dRequest(ctx, request, response, modelId);
    }
    
    @Override
	public void handleRequest(IContext context, Object request, Map<String, String> headers, String method, int messageId, IWebSocketResponder responder) {
    	try {
    		var action = headers.get("action");
        	var modelId = headers.get("modelName");
        	switch(action) {
        		case "ps":
        			this.processJtRequest(context, null, modelId, StreamType.ProductStructure, headers, responder);
        			break;
        		case "pmi":
        			this.processJtRequest(context, request, modelId, StreamType.Pmi, headers, responder);
        			break;
        		case "shapes":
        			this.processJtRequest(context, request, modelId, StreamType.Shapes, headers, responder);
        			break;
        		case "metadata":
        			this.processJtRequest(context, request, modelId, StreamType.Metadata, headers, responder);
        			break;
        	}
        	
        	logger.info("Controller " + this.getClass().getSimpleName() + " handling request: (action = " + action + ", modelId = " + modelId + ")");
        	
    	} catch(CoreException exception) {
    		responder.respond(exception, -1, -1, headers, false);
    		logger.error(exception);
    	}
	}
    
    private void processJtRequest(IContext ctx, Object request, String modelId, StreamType st, Map<String, String> headers, IWebSocketResponder responder) throws CoreException  {
        if (!Util.isValidModelId(modelId)) {
            responder.respond(JTException.create(ErrorCodes.Fetch_Failure), -1, -1, headers, false);
            return;
        }

        var mendixObject = this.getModelMxObject(ctx, modelId);
        if (mendixObject == null) {
            responder.respond(JTException.create(ErrorCodes.Fetch_Failure), -1, -1, headers, false);
            return;
        }

        Set<UUID> segmentIds = null;
        if(request instanceof ByteBuffer) {
        	segmentIds = JtUtils.INSTANCE.parseSegmentIds((ByteBuffer) request);
        }
        
        var size = (long) mendixObject.getMember(ctx, "Size").getValue(ctx);
        var dp = new MendixFileDocumentDataProvider(ctx, mendixObject, size);
        var jtReader = new JtReader(dp);
        var chunks = jtReader.readChunked(st, segmentIds);
        var totalLength = 0;
        if(chunks != null && chunks.size() > 0) {
        	for(var chunk : chunks) {
            	for(var entry: chunk) {
            		totalLength += entry.getLength();
            	}
            }
            
            for(int i = 0, end = chunks.size() - 1; i <= end; i++) {
            	var chunk = chunks.get(i);
            	var contentLength = 0;
            	var buffers = new ArrayList<ByteBuffer>();
            	for(var entry: chunk) {
            		contentLength += entry.getLength();
					var b = entry.getBuffer();
					var buff = ByteBuffer.wrap(b.array(), entry.getOffset(), entry.getLength());
					buffers.add(buff);
            	}
            	
            	var segmentCount = chunk.size();
            	if(st == StreamType.ProductStructure) {
            		segmentCount = chunk.size() - 2;
            	} else {
            		segmentCount = chunk.size() - 1;
            	}
            	headers.put("segment-count", Integer.toString(segmentCount));
            	responder.respond(buffers, totalLength, contentLength, headers, i < end);
            }
        } else {
        	responder.respond(null, -1, -1, headers, false);
        }

    }

    private void processJtRequest(IContext context, IMxRuntimeRequest request, IMxRuntimeResponse response, String modelId,
            StreamType st) throws Exception {
        if(!Util.isValidRequest(context,request,response)){
        	response.setStatus(IMxRuntimeResponse.BAD_REQUEST);
            return;
        }
        
        if (!Util.isValidModelId(modelId)) {
            logger.info("Invalid Model Id");
            httpError(response, ErrorCodes.Fetch_Failure);
            return;
        }

        ExceptionConsumer<IContext> jtRequestConsumer = (IContext ctx) -> {
            var mendixObject = this.getModelMxObject(ctx, modelId);
            if (mendixObject == null) {
            	logger.info("Not Found Model by Id");
            	httpError(response, ErrorCodes.Fetch_Failure);
                return;
            }
    
            var ids = Util.getSegmentIds(request, st);
            var size = (long) mendixObject.getMember(ctx, "Size").getValue(ctx);
            var dp = new MendixFileDocumentDataProvider(ctx, mendixObject, size);
            var jtReader = new JtReader(dp);
            var writer = new JtResponseWriter(response, modelId);
            jtReader.pipe(st, writer, ids);
            response.setContentType("application/octet-stream");
        };
        Util.handleTransaction(context, jtRequestConsumer);
    }
    
    private void process2dRequest(IContext ctx, IMxRuntimeRequest request, IMxRuntimeResponse response, String modelId) throws Exception {
    	boolean userModel = modelId.startsWith("usermodel&");
    	IMendixObject mendixObject;
        if (!userModel) {
	        var mendixObjects = Core.retrieveXPathQuery(ctx, "//Viewer3D.MxModelDocument[ModelId='" + modelId + "']");
	        if (mendixObjects.size() == 0) {
	            mendixObjects = Core.retrieveXPathQuery(ctx, "//Viewer3D.MxChildDocument[ModelId='" + modelId + "']");
	            if (mendixObjects.size() == 0) {
	                response.setStatus(IMxRuntimeResponse.NOT_FOUND);
	                return;
	            }
	        }
	        mendixObject = mendixObjects.get(0);
	    } else {
	        String userModelId = modelId.substring(10);
		    var fds = Core.retrieveXPathQuery(ctx, "//System.FileDocument[FileID='" + userModelId + "']");
		    if (fds.size() > 0) {
		    	mendixObject = fds.get(0);
		    } else {
                response.setStatus(IMxRuntimeResponse.NOT_FOUND);
                return;
		    }
	    }
        
        var mxBinary = (MendixBinary) mendixObject.getMember(ctx, "Contents");
        var modelTypeStr = (String) mendixObject.getMember(ctx, "ModelType").getValue(ctx);
        var modelType = ModelType.valueOf(modelTypeStr);
        var size = (long) mendixObject.getMember(ctx, "Size").getValue(ctx);
		var iStream = mxBinary.getValue(ctx, 0, size);
		var bytes = iStream.readAllBytes();
		iStream.close();
		var oStream = response.getOutputStream();
        oStream.write(bytes);
        if(modelType == ModelType.emf) {
        	response.setContentType("application/octet-stream");        	
        } else {
        	response.setContentType("text/plain;charset=utf-8");
        }
        response.setStatus(IMxRuntimeResponse.OK);
        oStream.close();
    }

    private IMendixObject getModelMxObject(IContext ctx, String modelId) throws CoreException {
        boolean userModel = modelId.startsWith("usermodel&");
        IMendixObject mendixObject;
        if (!userModel) {
            var mendixObjects = Core.retrieveXPathQuery(ctx, "//Viewer3D.MxModelDocument[ModelId='" + modelId + "']");
            if (mendixObjects.size() == 0) {
                mendixObjects = Core.retrieveXPathQuery(ctx, "//Viewer3D.MxChildDocument[ModelId='" + modelId + "']");
                if (mendixObjects.size() == 0) {
                    logger.info("Not Found MxModel for id: " + modelId);
                    return null;
                }
            }

            logger.info("Found MxModel for id: " + modelId);
            mendixObject = mendixObjects.get(0);
        } else {
            String userModelId = modelId.substring(10);
            var fds = Core.retrieveXPathQuery(ctx, "//System.FileDocument[FileID='" + userModelId + "']");
            if (fds.size() > 0) {
                mendixObject = fds.get(0);
            } else {
                return null;
            }
        }
        return mendixObject;
    }

    private MxModelDocument saveZippedFile(IContext ctx, IMxRuntimeResponse response, InputStream stream,
            String entryFileName, ModelType modelType) throws IOException, CoreException {
        if (!entryFileName.toLowerCase().endsWith(".jt")) {
            entryFileName += ".jt";
        } 
        
        ZipInputStream zipStream = new ZipInputStream(stream);
        ZipEntry entry;
        MxModelDocument entryObj = null;

        var entries = new java.util.ArrayList<MxChildDocument>();
        String error = null;
        while (error == null && (entry = zipStream.getNextEntry()) != null) {
            String fileName = entry.getName();
            if (!entry.isDirectory() && fileName.toLowerCase().endsWith(".jt")) {
                var size = entry.getSize();
                var buffer = new byte[(int) size];
                var pos = 0;
                var count = -1;

                while (true) {
                    count = zipStream.read(buffer, pos, buffer.length - pos);
                    if (count <= 0) {
                        break;
                    }
                    pos += count;
                }

                if (this.checkJTVersion(buffer)) {
                    var output = new ByteArrayInputStream(buffer);
                    // save the entry JT
                    if (fileName.equalsIgnoreCase(entryFileName)) {
                    	entryObj = this._saveFile(ctx, output, fileName, modelType);                                               
                    } else {
                    	// save the child JT
                        var modelEntry = this._saveChildFile(ctx, output, fileName);
                        entries.add(modelEntry);
                    }
                } else {
                    error = INVALID_VERSION;
                }
            }

            zipStream.closeEntry();
        }

        zipStream.close();

        if (error == null) {
            if (entryObj == null) {
                error = INVALID_ENTRY_PATH;
            } else if (entries.size() == 0) {
                error = INVALID_ZIP_FILE;
            }
        }

        if (error != null) {
            var writer = response.getWriter();
            writer.append(error);
            response.setStatus(IMxRuntimeResponse.DATAVALIDATION_ERROR_CODE);
            writer.flush();
        } else {
            entryObj.setModelEntries(entries);
            entryObj.commit();
        }
        return entryObj;
    }

    private MxModelDocument saveFile(IContext ctx, IMxRuntimeResponse response, InputStream stream, String name, ModelType modelType)
            throws CoreException, IOException {
        // validate stream
        var validated = modelType == ModelType.JT? this.validateJTStream(stream): stream;
        if (validated == null) {
            var writer = response.getWriter();
            writer.append(INVALID_VERSION);
            response.setStatus(IMxRuntimeResponse.DATAVALIDATION_ERROR_CODE);
            writer.flush();
            return null;
        }

        return this._saveFile(ctx, validated, name, modelType);
    }
    
    private MxModelDocument _saveFile(IContext ctx, InputStream stream, String name, ModelType modelType)
            throws CoreException, IOException {
        var doc = new MxModelDocument(ctx);
        doc.setModelId(UUID.randomUUID().toString());
        doc.setModelType(modelType.toString());
        Core.storeFileDocumentContent(ctx, doc.getMendixObject(), name, stream);
        doc.commit();
        return doc;
    }
    private MxChildDocument _saveChildFile(IContext ctx, InputStream stream, String name)
            throws CoreException, IOException {
        var doc = new MxChildDocument(ctx);
        doc.setModelId(UUID.randomUUID().toString());
        Core.storeFileDocumentContent(ctx, doc.getMendixObject(), name, stream);
        doc.commit();
        return doc;
    }
    
    private void batchSaveFiles(IContext ctx, IMxRuntimeResponse response, ArrayList<InputStream> files, String name, ModelType modelType) 
    		throws IOException{
    	// 1, tidy up the list object
    	// 2, parallel upload
    	// 3, set the response
    	var tidyList = new ArrayList<Entry<String, InputStream>>();
    	var names = name.split(";;");
    	for (int i = 0; i < names.length; i++) {
    		tidyList.add(new SimpleEntry<String, InputStream>(names[i], files.get(i)));
    	}
    	
    	var modelIds = new ArrayList<String>();
    	tidyList.parallelStream().forEach(entry->{
    		MxModelDocument doc;
    		String fname = entry.getKey();
			try {
				doc = this._saveFile(ctx, entry.getValue(), fname, modelType);
				modelIds.add(doc.getModelId());
			} catch (CoreException e) {
				logger.error("Exception when save file: " + fname);
			} catch (IOException e) {
				logger.error("Exception when save file: " + fname);
			}
    	});
    	
    	var oStream = response.getOutputStream();
    	oStream.write(String.join(";", modelIds).getBytes());
    	response.setContentType("text/plain;charset=utf-8");
        response.setStatus(IMxRuntimeResponse.OK);
        oStream.close();  	
    }
    
    private MxModelDocument saveFiles(IContext ctx, IMxRuntimeResponse response, ArrayList<InputStream> files, String name, ModelType modelType)
            throws CoreException, IOException {
    	// 1, identify the entry file- the first one
    	// 2, save all of them- first save the entryFile, then the others    	
    	MxModelDocument doc = null;
    	var entries = new java.util.ArrayList<MxChildDocument>();
    	
        // such separator is intentional, because jt files may contain ";"    	
    	var names = name.split(";;");
    	for (int i = 0; i < files.size(); i++) {
    		if(i==0) {
    			// save the entryFile
    			doc = this._saveFile(ctx, files.get(i), names[i], modelType);
    		}else {
    			// save others
    			var childDoc = this._saveChildFile(ctx, files.get(i), names[i]);
                entries.add(childDoc);
    		}
    		doc.setModelEntries(entries);
    		doc.commit();
		}
    	return doc;
    }
    private boolean checkJTVersion(byte[] buffer) {
        if (buffer.length < JT_HEADER_LENGTH) {
            return false;
        }

        var str = new String(buffer, 0, JT_HEADER_LENGTH);
        var r = Pattern.compile(MxController.VERSION_PATTERN);
        var m = r.matcher(str);
        if (m.find()) {
            if (m.groupCount() > 0) {
                var version = m.group(0).split("[.]");
                try {
                    var major = Integer.parseInt(version[0], 10);
                    if (major < 9) {
                        return false;
                    }
                } catch (NumberFormatException ex) {
                    return false;
                }

                return true;
            }
        }

        return false;
    }

    private InputStream validateJTStream(InputStream stream) {
        // only reads the first 80 bytes
        try {
            var buffer = new byte[JT_HEADER_LENGTH];
            var bytesRead = stream.read(buffer, 0, JT_HEADER_LENGTH);
            if (bytesRead != JT_HEADER_LENGTH) {
                return null;
            }
            if (!this.checkJTVersion(buffer)) {
                return null;
            }
            var headerStream = new ByteArrayInputStream(buffer);
            return new SequenceInputStream(headerStream, stream);
        } catch (Exception ex) {
            logger.error("Exception when validating JT stream: ", ex);
            return null;
        }
    }

    private Session getViewerSession(@Context IContext ctx, String modelId) throws Exception {
        // get the mxModelDocument by modelId
        var mendixObjects = Core.retrieveXPathQuery(ctx, "//Viewer3D.MxModelDocument[ModelId='" + modelId + "']");
        if (mendixObjects.isEmpty()) {
            logger.error("No associated MxModelDocument founded for " + modelId);
            return null;
        }

        // always use the first mendix object
        var mxDocument = MxModelDocument.initialize(ctx, mendixObjects.get(0));
        var modelAssociations = Core.retrieveByPath(ctx, mendixObjects.get(0), "Viewer3D.Session_MxModelDocument", false);

        var activeUserId = ctx.getCurrentUserObject().getId();
        logger.info("Active User Session Size: " + modelAssociations.size());
        for (IMendixObject iMendixObject : modelAssociations) {
            if(activeUserId.equals(iMendixObject.getOwner(ctx))) {
                logger.info("Get Active User Session");
                return Session.initialize(ctx, iMendixObject);
            }
        }

        logger.info("Start Create UserSession");
        var userSession = new Session(ctx);
        userSession.setSession_MxModelDocument(ctx, mxDocument);
        userSession.commit(ctx);
        logger.info("Create a New User Session Successfully");
        return userSession;
    }
    
    private void httpError(IMxRuntimeResponse response, ErrorCodes errorCode) throws IOException {
    	 var writer = response.getWriter();
         writer.append(errorCode.message());
         response.setStatus(IMxRuntimeResponse.INTERNAL_SERVER_ERROR);
         writer.flush();
    }
}









//@PostMapping("{modelId}/markups")
	// public void postMarkup(@Context IContext ctx, @Request IMxRuntimeRequest
	// request,
	// @Response IMxRuntimeResponse response, @PathVariable("modelId") String
	// modelId,
	// @FormPart("title") String title, @FormPart("width") String width,
	// @FormPart("height") String height,
	// @FormPart("snapshot") InputStream snapshot) throws Exception {
	// logger.info("Upload Markup start");
	//
	// if (!Util.isValidModelId(modelId)) {
	// logger.error("Invalid modelId");
	// response.setStatus(IMxRuntimeResponse.BAD_REQUEST);
	// return;
	// }
	//
	// ExceptionConsumer<IContext> markupConsumer = (IContext context) -> {
	// // get the mxModelDocument by modelId
	// List<IMendixObject> mendixObjects = Core.retrieveXPathQuery(ctx,
	// "//Viewer3D.MxModelDocument[ModelId='" + modelId + "']");
	// if (mendixObjects.isEmpty()) {
	// logger.error("No Associated MxModelDocument founded for" + modelId);
	// response.setStatus(IMxRuntimeResponse.NOT_FOUND);
	// return;
	// }
	//
	// // always use the first mendix object
	// var mxDocument = MxModelDocument.initialize(ctx, mendixObjects.get(0));
	//
	// var mxName = title;
	// if (mxName.isEmpty()) {
	// var mendixObject = mxDocument.getMendixObject();
	//
	// String userModelId;
	// if (modelId.startsWith("usermodel&")) {
	// userModelId = modelId.substring(10);
	// var fds = Core.retrieveXPathQuery(ctx, "//System.FileDocument[FileID='" +
	// userModelId + "']");
	// if (fds.size() > 0) {
	// mendixObject = fds.get(0);
	// }
	// }
	//
	// mxName = (String) mendixObject.getMember(ctx, "Name").getValue(ctx);
	// if (!mxName.isEmpty()) {
	// var commaIndex = mxName.lastIndexOf(".");
	// if (commaIndex != -1)
	// mxName = mxName.substring(0, commaIndex);
	// }
	// }
	// mxName += ".png";
	//
	// // create mx object
	// var markup = new Markup(context);
	// markup.setMarkup_MxModelDocument(context, mxDocument);
	// markup.setName(context, mxName);
	// logger.info("MxName: " + mxName);
	//
	// var thumbnailWidth = Float.parseFloat(width) * 0.25;
	// var thumbnailHeight = Float.parseFloat(height) * 0.25;
	// logger.info("Width: " + thumbnailWidth + "," + "height:" +
	// thumbnailHeight);
	// Core.storeImageDocumentContent(ctx, markup.getMendixObject(), snapshot, (int)
	// thumbnailWidth,
	// (int) thumbnailHeight);
	//
	// markup.commit(context);
	//
	// response.setContentType("text/plain;charset=utf-8");
	// response.setStatus(IMxRuntimeResponse.OK);
	// };
	//
	// Util.handleTransaction(ctx, markupConsumer);
	// }
