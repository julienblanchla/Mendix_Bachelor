package viewer3d.actions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mendix.core.CoreException;
import com.mendix.core.objectmanagement.member.MendixBinary;
import com.mendix.logging.ILogNode;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import com.mendix.viewer3d.jtreader.BufferEntry;
import com.mendix.viewer3d.jtreader.DataProvider;
import com.mendix.viewer3d.jtreader.Span;

import viewer3d.proxies.MxChildDocument;
import viewer3d.proxies.MxModelDocument;
import viewer3d.shared.utils.Util;

import org.apache.commons.lang3.tuple.Pair;

class SourceMapEntry {

	public byte[] sourceName;

	public byte[] modelId;

	SourceMapEntry(byte[] fileName, byte[] modelId) {
		this.sourceName = fileName;
		this.modelId = modelId;
	}
}

public class MendixFileDocumentDataProvider implements DataProvider {

	private IContext ctx;

	private IMendixObject mxObj;

	private ILogNode logger = Util.DEFAULTLOGGER;

	private CompletableFuture<ByteBuffer> cachedDataFuture;

	private ExecutorService threadPool;

	private ArrayList<SourceMapEntry> childEntries;

	private ByteBuffer sourceMapBuffer;

	private int sourceMapByteSize;

	private long fileSize;

	public MendixFileDocumentDataProvider(IContext ctx, IMendixObject mxObj, long fileSize) throws CoreException {
		this.ctx = ctx;
		this.mxObj = mxObj;
		this.fileSize = fileSize;
		this.threadPool = Executors.newCachedThreadPool();
		this.childEntries = null;
		this.sourceMapBuffer = null;
		this.sourceMapByteSize = 0;
		var objectType = mxObj.getType();
		if (objectType.equals("Viewer3D.MxModelDocument")) {
			var docObj = MxModelDocument.initialize(ctx, mxObj);
			var childDocuments = docObj.getModelEntries();
			if (childDocuments != null && childDocuments.size() > 0) {
				this.sourceMapByteSize += 4;
				childEntries = new ArrayList<SourceMapEntry>(childDocuments.size());
				for (MxChildDocument child : childDocuments) {
					var sourceName = child.getName();
					var modelId = child.getModelId();
					logger.info(sourceName + ":" + modelId);					
					var sourceNameBytes = sourceName.getBytes();
					var modelIdBytes = modelId.getBytes();
					this.sourceMapByteSize += 8 + sourceNameBytes.length + modelIdBytes.length;
					childEntries.add(new SourceMapEntry(sourceNameBytes, modelIdBytes));
				}
			}
		}

		// download all data at once if < 5MB
		if (fileSize < 5 * 1024 * 1024) {
			this.cachedDataFuture = this.getChunkAsync(new Span(0, (int) fileSize));
		}
	}

	@Override
	public long getSize() throws Exception {
		return this.fileSize;
	}

	@Override
	public ByteBuffer getSourceMap(ByteOrder byteOrder) throws Exception {
		if (this.sourceMapBuffer == null && this.sourceMapByteSize > 0) {
			this.sourceMapBuffer = ByteBuffer.allocate(this.sourceMapByteSize);
			this.sourceMapBuffer.order(byteOrder);
			this.sourceMapBuffer.putInt(this.childEntries.size());
			for (var entry : this.childEntries) {
				this.sourceMapBuffer.putInt(entry.sourceName.length);
				this.sourceMapBuffer.put(entry.sourceName, 0, entry.sourceName.length);

				this.sourceMapBuffer.putInt(entry.modelId.length);
				this.sourceMapBuffer.put(entry.modelId, 0, entry.modelId.length);
			}

			this.sourceMapBuffer.flip();
		}

		return this.sourceMapBuffer;
	}

	@Override
	public ByteBuffer getChunk(Span span) throws InterruptedException, ExecutionException {
		if (this.cachedDataFuture != null) {
			var data = this.cachedDataFuture.get();
			var bytes = new byte[span.getCount()];
			data.position((int) span.getOffset());
			data.get(bytes);
			return ByteBuffer.wrap(bytes);
		}

		return this.getChunkAsync(span).get();
	}

	@Override
	public List<BufferEntry> getChunks(List<Span> spans) throws InterruptedException, ExecutionException {
		if (this.cachedDataFuture != null) {
			var ret = new ArrayList<BufferEntry>(spans.size());
			for (var span : spans) {
				ret.add(new BufferEntry(this.cachedDataFuture.get(), (int) span.getOffset(), span.getCount()));
			}
			return ret;
		}

		// refer to DssDataProvider in Visualization/VisServices
		var merged = this.mergeSpans(spans);
		this.logger.info("Requested " + spans.size() + " chunk(s), merged into " + merged.size() + " chunk(s)");

		@SuppressWarnings("unchecked")
		CompletableFuture<ByteBuffer>[] bufferFutures = new CompletableFuture[merged.size()];
		for (var i = 0; i < merged.size(); i += 1) {
			var spanArr = merged.get(i).spans();
			var start = spanArr.get(0).getOffset();

			// calculate count
			var count = 0;
			for (var s : spanArr) {
				count += s.getCount();
			}

			bufferFutures[i] = this.getChunkAsync(new Span(start, count));
		}
		CompletableFuture.allOf(bufferFutures).get();

		// project back
		var ret = new BufferEntry[spans.size()];
		for (var i = 0; i < bufferFutures.length; i += 1) {
			var origSpans = merged.get(i).spans();
			var start = origSpans.get(0).getOffset();
			for (var j = 0; j < origSpans.size(); j += 1) {
				var s = origSpans.get(j);
				var idx = merged.get(i).indices().get(j);
				ret[idx] = new BufferEntry(bufferFutures[i].get(), (int) (s.getOffset() - start), s.getCount());
			}
		}

		return Arrays.asList(ret);
	}

	private CompletableFuture<ByteBuffer> getChunkAsync(Span span) {
		var completableFuture = new CompletableFuture<ByteBuffer>();

		this.threadPool.submit(() -> {
			try {
				var mxBinary = (MendixBinary) this.mxObj.getMember(this.ctx, "Contents");
				var iStream = mxBinary.getValue(ctx, span.getOffset(), span.getCount());
				var bytes = iStream.readAllBytes();
				iStream.close();

				completableFuture.complete(ByteBuffer.wrap(bytes));
			} catch (IOException ex) {
				completableFuture.completeExceptionally(ex);
			}
		});

		return completableFuture;
	}

	private ArrayList<MergedSpan> mergeSpans(List<Span> spanList) {
		// sort
		var spanRecords = new ArrayList<Pair<Span, Integer>>(spanList.size());
		for (var i = 0; i < spanList.size(); i += 1) {
			spanRecords.add(Pair.of(spanList.get(i), i));
		}

		spanRecords.sort((r1, r2) -> {
			return r1.getLeft().getOffset() < r2.getLeft().getOffset() ? -1 : 1;
		});

		// merge
		var merged = new ArrayList<MergedSpan>();
		var idx = 0;
		while (idx < spanRecords.size()) {
			var spans = new ArrayList<Span>();
			var indices = new ArrayList<Integer>();

			var curSpan = spanRecords.get(idx).getLeft();
			var end = curSpan.getOffset() + curSpan.getCount();

			spans.add(curSpan);
			indices.add(spanRecords.get(idx).getRight());

			while (idx + 1 < spanRecords.size() && end == spanRecords.get(idx + 1).getLeft().getOffset()) {
				idx += 1;
				curSpan = spanRecords.get(idx).getLeft();

				end += curSpan.getCount();

				spans.add(curSpan);
				indices.add(spanRecords.get(idx).getRight());
			}

			merged.add(new MergedSpan(spans, indices));
			idx += 1;
		}

		return merged;
	}
}
