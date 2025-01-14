/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.codecs.compressing;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.codecs.compressing.CompressingStoredFieldsReader.SerializedDocument;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DocIDMerger;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.GrowableByteArrayDataOutput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BitUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.packed.PackedInts;

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

/**
 * {@link StoredFieldsWriter} impl for {@link CompressingStoredFieldsFormat}.
 *
 * @lucene.experimental
 */
public final class CompressingStoredFieldsWriter extends StoredFieldsWriter {

	/**
	 * Extension of stored fields file
	 */
	public static final String FIELDS_EXTENSION = "fdt";

	/**
	 * Extension of stored fields index file
	 */
	public static final String FIELDS_INDEX_EXTENSION = "fdx";

	static final int STRING = 0x00;
	static final int BYTE_ARR = 0x01;
	static final int NUMERIC_INT = 0x02;
	static final int NUMERIC_FLOAT = 0x03;
	static final int NUMERIC_LONG = 0x04;
	static final int NUMERIC_DOUBLE = 0x05;

	static final int TYPE_BITS = PackedInts.bitsRequired(NUMERIC_DOUBLE);
	static final int TYPE_MASK = (int) PackedInts.maxValue(TYPE_BITS);

	static final String CODEC_SFX_IDX = "Index";
	static final String CODEC_SFX_DAT = "Data";
	static final int VERSION_START = 1;
	static final int VERSION_CURRENT = VERSION_START;

	private final String segment;
	private CompressingStoredFieldsIndexWriter indexWriter;
	private IndexOutput fieldsStream;

	private Compressor compressor;
	private final CompressionMode compressionMode;
	private final int chunkSize;
	private final int maxDocsPerChunk;

	private final GrowableByteArrayDataOutput bufferedDocs;
	// 下标为docId，数组元素为域的属性为存储的个数
	private int[] numStoredFields; // number of stored fields
	// 下标为docId，数组元素为每篇文档的最后一个域的域值在bufferedDocs中下标值, bufferedDocs中存放了当前文档的所有域的域值
	private int[] endOffsets; // end offsets in bufferedDocs
	private int docBase; // doc ID at the beginning of the chunk
	private int numBufferedDocs; // docBase + numBufferedDocs == current doc ID

	private long numChunks; // number of compressed blocks written
	private long numDirtyChunks; // number of incomplete compressed blocks written

	/**
	 * Sole constructor.
	 */
	public CompressingStoredFieldsWriter(Directory directory, SegmentInfo si, String segmentSuffix, IOContext context,
																			 String formatName, CompressionMode compressionMode, int chunkSize, int maxDocsPerChunk, int blockSize) throws IOException {
		assert directory != null;
		this.segment = si.name;
		this.compressionMode = compressionMode;
		this.compressor = compressionMode.newCompressor();
		this.chunkSize = chunkSize;
		this.maxDocsPerChunk = maxDocsPerChunk;
		this.docBase = 0;
		this.bufferedDocs = new GrowableByteArrayDataOutput(chunkSize);
		this.numStoredFields = new int[16];
		this.endOffsets = new int[16];
		this.numBufferedDocs = 0;

		boolean success = false;
		IndexOutput indexStream = directory.createOutput(IndexFileNames.segmentFileName(segment, segmentSuffix, FIELDS_INDEX_EXTENSION),
			context);
		try {
			fieldsStream = directory.createOutput(IndexFileNames.segmentFileName(segment, segmentSuffix, FIELDS_EXTENSION),
				context);

			final String codecNameIdx = formatName + CODEC_SFX_IDX;
			final String codecNameDat = formatName + CODEC_SFX_DAT;
			CodecUtil.writeIndexHeader(indexStream, codecNameIdx, VERSION_CURRENT, si.getId(), segmentSuffix);
			CodecUtil.writeIndexHeader(fieldsStream, codecNameDat, VERSION_CURRENT, si.getId(), segmentSuffix);
			assert CodecUtil.indexHeaderLength(codecNameDat, segmentSuffix) == fieldsStream.getFilePointer();
			assert CodecUtil.indexHeaderLength(codecNameIdx, segmentSuffix) == indexStream.getFilePointer();

			indexWriter = new CompressingStoredFieldsIndexWriter(indexStream, blockSize);
			indexStream = null;

			fieldsStream.writeVInt(chunkSize);
			fieldsStream.writeVInt(PackedInts.VERSION_CURRENT);

			success = true;
		} finally {
			if (!success) {
				IOUtils.closeWhileHandlingException(fieldsStream, indexStream, indexWriter);
			}
		}
	}

	@Override
	public void close() throws IOException {
		try {
			IOUtils.close(fieldsStream, indexWriter, compressor);
		} finally {
			fieldsStream = null;
			indexWriter = null;
			compressor = null;
		}
	}

	// 一篇文档中域的属性为存储的个数
	private int numStoredFieldsInDoc;

	@Override
	public void startDocument() throws IOException {
	}

	@Override
	// 这个方法在调用IndexWriter.add(Document)过程中调用
	public void finishDocument() throws IOException {
		if (numBufferedDocs == this.numStoredFields.length) {
			final int newLength = ArrayUtil.oversize(numBufferedDocs + 1, 4);
			this.numStoredFields = ArrayUtil.growExact(this.numStoredFields, newLength);
			endOffsets = ArrayUtil.growExact(endOffsets, newLength);
		}
		// 记录当前文档包含的域的个数，域必须是Store.YES
		this.numStoredFields[numBufferedDocs] = numStoredFieldsInDoc;
		numStoredFieldsInDoc = 0;
		// 一个文档的所有域的域值都存放在bufferedDocs中
		// 记录当前文档的最后一个域的最后一个域值在 bufferedDocs[]数组中的位置
		endOffsets[numBufferedDocs] = bufferedDocs.getPosition();
		// 文档号+1
		++numBufferedDocs;
		if (triggerFlush()) {
			flush();
		}
	}

	private static void saveInts(int[] values, int length, DataOutput out) throws IOException {
		assert length > 0;
		if (length == 1) {
			out.writeVInt(values[0]);
		} else {
			boolean allEqual = true;
			// 遍历判断每一个文档中包含的 Store.YES的域的个数是不是相同
			for (int i = 1; i < length; ++i) {
				if (values[i] != values[0]) {
					allEqual = false;
					break;
				}
			}
			// 如果相同
			if (allEqual) {
				// 标示位，说明所有文档的域的个数相同
				out.writeVInt(0);
				// 只要写入一次文档的域的个数即可
				out.writeVInt(values[0]);
			} else {
				// 否则使用PackedInts压缩存储每篇文档的域的个数
				long max = 0;
				for (int i = 0; i < length; ++i) {
					// 使用"|"的方法不会破坏最大values的最高位
					max |= values[i];
				}
				// 存储最大的values需要的bit位的个数
				final int bitsRequired = PackedInts.bitsRequired(max);
				// bitsRequire同时也是个标志位
				out.writeVInt(bitsRequired);
				// PackedInt来存储
				final PackedInts.Writer w = PackedInts.getWriterNoHeader(out, PackedInts.Format.PACKED, length, bitsRequired, 1);
				for (int i = 0; i < length; ++i) {
					w.add(values[i]);
				}
				w.finish();
			}
		}
	}

	private void writeHeader(int docBase, int numBufferedDocs, int[] numStoredFields, int[] lengths, boolean sliced) throws IOException {
		// 描述 sliceBit >= 2^15次方则为true
		final int slicedBit = sliced ? 1 : 0;

		// save docBase and numBufferedDocs
		// 存储docBase的值
		fieldsStream.writeVInt(docBase);
		// 存储文档号跟slicedBit
		fieldsStream.writeVInt((numBufferedDocs) << 1 | slicedBit);

		// save numStoredFields,
		// numStoredFields: 文档号中 Store.YES的域的个数
		// numBufferedDocs: 文档个数
		saveInts(numStoredFields, numBufferedDocs, fieldsStream);

		// save lengths
		// lengths: 每一个文档的域值在一个buffer[][]中的偏移位置，差值存储
		saveInts(lengths, numBufferedDocs, fieldsStream);
	}

	private boolean triggerFlush() {
		// trigger的条件
		return bufferedDocs.getPosition() >= chunkSize || // chunks of at least chunkSize bytes
			numBufferedDocs >= maxDocsPerChunk;
	}

	// 当commit 或者 文档个数等于128(LZ4.FAST)或者文档个数等于521(LZ4.HIGH)会调用此方法
	private void flush() throws IOException {
		indexWriter.writeIndex(numBufferedDocs, fieldsStream.getFilePointer());

		// transform end offsets into lengths

		final int[] lengths = endOffsets;
		// 计算前后数组两个元素的差值
		for (int i = numBufferedDocs - 1; i > 0; --i) {
			lengths[i] = endOffsets[i] - endOffsets[i - 1];
			assert lengths[i] >= 0;
		}
		final boolean sliced = bufferedDocs.getPosition() >= 2 * chunkSize;
		// 每篇文档包含的域的种类跟域值写入到fdx中
		writeHeader(docBase, numBufferedDocs, numStoredFields, lengths, sliced);

		// compress stored fields to fieldsStream
		if (sliced) {
			// big chunk, slice it
			for (int compressed = 0; compressed < bufferedDocs.getPosition(); compressed += chunkSize) {
				compressor.compress(bufferedDocs.getBytes(), compressed, Math.min(chunkSize, bufferedDocs.getPosition() - compressed), fieldsStream);
			}
		} else {
			compressor.compress(bufferedDocs.getBytes(), 0, bufferedDocs.getPosition(), fieldsStream);
		}

		// reset
		// 更新docBase
		docBase += numBufferedDocs;
		numBufferedDocs = 0;
		// 复用存放所有域的所有域值的数组
		bufferedDocs.reset();
		numChunks++;
	}

	@Override
	public void writeField(FieldInfo info, IndexableField field)
		throws IOException {

		// 当前文档中存储域的个数
		++numStoredFieldsInDoc;

		int bits = 0;
		final BytesRef bytes;
		final String string;
		// 域值只有 binary，Sting，numeric三种情况
		// 处理域值为数字类型的情况
		Number number = field.numericValue();
		if (number != null) {
			if (number instanceof Byte || number instanceof Short || number instanceof Integer) {
				bits = NUMERIC_INT;
			} else if (number instanceof Long) {
				bits = NUMERIC_LONG;
			} else if (number instanceof Float) {
				bits = NUMERIC_FLOAT;
			} else if (number instanceof Double) {
				bits = NUMERIC_DOUBLE;
			} else {
				throw new IllegalArgumentException("cannot store numeric type " + number.getClass());
			}
			string = null;
			bytes = null;
		} else {
			// 处理域值为binary的情况
			bytes = field.binaryValue();
			if (bytes != null) {
				bits = BYTE_ARR;
				string = null;
			} else {
				// 处理域值为String的情况
				bits = STRING;
				string = field.stringValue();
				if (string == null) {
					throw new IllegalArgumentException("field " + field.name() + " is stored but does not have binaryValue, stringValue nor numericValue");
				}
			}
		}

		// 用infoAndBits来描述域值的类型跟域名的编号, 低3位表示域值类型，其他位表示域名的编号
		final long infoAndBits = (((long) info.number) << TYPE_BITS) | bits;
		// 写入域的编号，域值类型的信息数据
		bufferedDocs.writeVLong(infoAndBits);

		if (bytes != null) {
			bufferedDocs.writeVInt(bytes.length);
			bufferedDocs.writeBytes(bytes.bytes, bytes.offset, bytes.length);
		} else if (string != null) {
			// 写入域值, 先将String类型转化为ByteRef再写入
			bufferedDocs.writeString(string);
		} else {
			if (number instanceof Byte || number instanceof Short || number instanceof Integer) {
				bufferedDocs.writeZInt(number.intValue());
			} else if (number instanceof Long) {
				writeTLong(bufferedDocs, number.longValue());
			} else if (number instanceof Float) {
				writeZFloat(bufferedDocs, number.floatValue());
			} else if (number instanceof Double) {
				writeZDouble(bufferedDocs, number.doubleValue());
			} else {
				throw new AssertionError("Cannot get here");
			}
		}
	}

	// -0 isn't compressed.
	static final int NEGATIVE_ZERO_FLOAT = Float.floatToIntBits(-0f);
	static final long NEGATIVE_ZERO_DOUBLE = Double.doubleToLongBits(-0d);

	// for compression of timestamps
	static final long SECOND = 1000L;
	static final long HOUR = 60 * 60 * SECOND;
	static final long DAY = 24 * HOUR;
	static final int SECOND_ENCODING = 0x40;
	static final int HOUR_ENCODING = 0x80;
	static final int DAY_ENCODING = 0xC0;

	/**
	 * Writes a float in a variable-length format.  Writes between one and
	 * five bytes. Small integral values typically take fewer bytes.
	 * <p>
	 * ZFloat --&gt; Header, Bytes*?
	 * <ul>
	 *    <li>Header --&gt; {@link DataOutput#writeByte Uint8}. When it is
	 *       equal to 0xFF then the value is negative and stored in the next
	 *       4 bytes. Otherwise if the first bit is set then the other bits
	 *       in the header encode the value plus one and no other
	 *       bytes are read. Otherwise, the value is a positive float value
	 *       whose first byte is the header, and 3 bytes need to be read to
	 *       complete it.
	 *    <li>Bytes --&gt; Potential additional bytes to read depending on the
	 *       header.
	 * </ul>
	 */
	static void writeZFloat(DataOutput out, float f) throws IOException {
		int intVal = (int) f;
		final int floatBits = Float.floatToIntBits(f);

		if (f == intVal
			&& intVal >= -1
			&& intVal <= 0x7D
			&& floatBits != NEGATIVE_ZERO_FLOAT) {
			// small integer value [-1..125]: single byte
			out.writeByte((byte) (0x80 | (1 + intVal)));
		} else if ((floatBits >>> 31) == 0) {
			// other positive floats: 4 bytes
			out.writeInt(floatBits);
		} else {
			// other negative float: 5 bytes
			out.writeByte((byte) 0xFF);
			out.writeInt(floatBits);
		}
	}

	/**
	 * Writes a float in a variable-length format.  Writes between one and
	 * five bytes. Small integral values typically take fewer bytes.
	 * <p>
	 * ZFloat --&gt; Header, Bytes*?
	 * <ul>
	 *    <li>Header --&gt; {@link DataOutput#writeByte Uint8}. When it is
	 *       equal to 0xFF then the value is negative and stored in the next
	 *       8 bytes. When it is equal to 0xFE then the value is stored as a
	 *       float in the next 4 bytes. Otherwise if the first bit is set
	 *       then the other bits in the header encode the value plus one and
	 *       no other bytes are read. Otherwise, the value is a positive float
	 *       value whose first byte is the header, and 7 bytes need to be read
	 *       to complete it.
	 *    <li>Bytes --&gt; Potential additional bytes to read depending on the
	 *       header.
	 * </ul>
	 */
	static void writeZDouble(DataOutput out, double d) throws IOException {
		int intVal = (int) d;
		final long doubleBits = Double.doubleToLongBits(d);

		if (d == intVal &&
			intVal >= -1 &&
			intVal <= 0x7C &&
			doubleBits != NEGATIVE_ZERO_DOUBLE) {
			// small integer value [-1..124]: single byte
			out.writeByte((byte) (0x80 | (intVal + 1)));
			return;
		} else if (d == (float) d) {
			// d has an accurate float representation: 5 bytes
			out.writeByte((byte) 0xFE);
			out.writeInt(Float.floatToIntBits((float) d));
		} else if ((doubleBits >>> 63) == 0) {
			// other positive doubles: 8 bytes
			out.writeLong(doubleBits);
		} else {
			// other negative doubles: 9 bytes
			out.writeByte((byte) 0xFF);
			out.writeLong(doubleBits);
		}
	}

	/**
	 * Writes a long in a variable-length format.  Writes between one and
	 * ten bytes. Small values or values representing timestamps with day,
	 * hour or second precision typically require fewer bytes.
	 * <p>
	 * ZLong --&gt; Header, Bytes*?
	 * <ul>
	 *    <li>Header --&gt; The first two bits indicate the compression scheme:
	 *       <ul>
	 *          <li>00 - uncompressed
	 *          <li>01 - multiple of 1000 (second)
	 *          <li>10 - multiple of 3600000 (hour)
	 *          <li>11 - multiple of 86400000 (day)
	 *       </ul>
	 *       Then the next bit is a continuation bit, indicating whether more
	 *       bytes need to be read, and the last 5 bits are the lower bits of
	 *       the encoded value. In order to reconstruct the value, you need to
	 *       combine the 5 lower bits of the header with a vLong in the next
	 *       bytes (if the continuation bit is set to 1). Then
	 *       {@link BitUtil#zigZagDecode(int) zigzag-decode} it and finally
	 *       multiply by the multiple corresponding to the compression scheme.
	 *    <li>Bytes --&gt; Potential additional bytes to read depending on the
	 *       header.
	 * </ul>
	 */
	// T for "timestamp"
	static void writeTLong(DataOutput out, long l) throws IOException {
		int header;
		if (l % SECOND != 0) {
			header = 0;
		} else if (l % DAY == 0) {
			// timestamp with day precision
			header = DAY_ENCODING;
			l /= DAY;
		} else if (l % HOUR == 0) {
			// timestamp with hour precision, or day precision with a timezone
			header = HOUR_ENCODING;
			l /= HOUR;
		} else {
			// timestamp with second precision
			header = SECOND_ENCODING;
			l /= SECOND;
		}

		final long zigZagL = BitUtil.zigZagEncode(l);
		header |= (zigZagL & 0x1F); // last 5 bits
		final long upperBits = zigZagL >>> 5;
		if (upperBits != 0) {
			header |= 0x20;
		}
		out.writeByte((byte) header);
		if (upperBits != 0) {
			out.writeVLong(upperBits);
		}
	}

	@Override
	public void finish(FieldInfos fis, int numDocs) throws IOException {
		if (numBufferedDocs > 0) {
			flush();
			numDirtyChunks++; // incomplete: we had to force this flush
		} else {
			assert bufferedDocs.getPosition() == 0;
		}
		if (docBase != numDocs) {
			throw new RuntimeException("Wrote " + docBase + " docs, finish called with numDocs=" + numDocs);
		}
		indexWriter.finish(numDocs, fieldsStream.getFilePointer());
		fieldsStream.writeVLong(numChunks);
		fieldsStream.writeVLong(numDirtyChunks);
		CodecUtil.writeFooter(fieldsStream);
		assert bufferedDocs.getPosition() == 0;
	}

	// bulk merge is scary: its caused corruption bugs in the past.
	// we try to be extra safe with this impl, but add an escape hatch to
	// have a workaround for undiscovered bugs.
	static final String BULK_MERGE_ENABLED_SYSPROP = CompressingStoredFieldsWriter.class.getName() + ".enableBulkMerge";
	static final boolean BULK_MERGE_ENABLED;

	static {
		boolean v = true;
		try {
			v = Boolean.parseBoolean(System.getProperty(BULK_MERGE_ENABLED_SYSPROP, "true"));
		} catch (SecurityException ignored) {
		}
		BULK_MERGE_ENABLED = v;
	}

	@Override
	public int merge(MergeState mergeState) throws IOException {
		int docCount = 0;
		int numReaders = mergeState.maxDocs.length;

		MatchingReaders matching = new MatchingReaders(mergeState);
		if (mergeState.needsIndexSort) {
			/**
			 * If all readers are compressed and they have the same fieldinfos then we can merge the serialized document
			 * directly.
			 */
			List<CompressingStoredFieldsMergeSub> subs = new ArrayList<>();
			for (int i = 0; i < mergeState.storedFieldsReaders.length; i++) {
				if (matching.matchingReaders[i] &&
					mergeState.storedFieldsReaders[i] instanceof CompressingStoredFieldsReader) {
					CompressingStoredFieldsReader storedFieldsReader = (CompressingStoredFieldsReader) mergeState.storedFieldsReaders[i];
					storedFieldsReader.checkIntegrity();
					subs.add(new CompressingStoredFieldsMergeSub(storedFieldsReader, mergeState.docMaps[i], mergeState.maxDocs[i]));
				} else {
					return super.merge(mergeState);
				}
			}

			final DocIDMerger<CompressingStoredFieldsMergeSub> docIDMerger =
				DocIDMerger.of(subs, true);
			while (true) {
				CompressingStoredFieldsMergeSub sub = docIDMerger.next();
				if (sub == null) {
					break;
				}
				assert sub.mappedDocID == docCount;
				SerializedDocument doc = sub.reader.document(sub.docID);
				startDocument();
				bufferedDocs.copyBytes(doc.in, doc.length);
				numStoredFieldsInDoc = doc.numStoredFields;
				finishDocument();
				++docCount;
			}
			finish(mergeState.mergeFieldInfos, docCount);
			return docCount;
		}

		for (int readerIndex = 0; readerIndex < numReaders; readerIndex++) {
			MergeVisitor visitor = new MergeVisitor(mergeState, readerIndex);
			CompressingStoredFieldsReader matchingFieldsReader = null;
			if (matching.matchingReaders[readerIndex]) {
				final StoredFieldsReader fieldsReader = mergeState.storedFieldsReaders[readerIndex];
				// we can only bulk-copy if the matching reader is also a CompressingStoredFieldsReader
				if (fieldsReader != null && fieldsReader instanceof CompressingStoredFieldsReader) {
					matchingFieldsReader = (CompressingStoredFieldsReader) fieldsReader;
				}
			}

			final int maxDoc = mergeState.maxDocs[readerIndex];
			final Bits liveDocs = mergeState.liveDocs[readerIndex];

			// if its some other format, or an older version of this format, or safety switch:
			if (matchingFieldsReader == null || matchingFieldsReader.getVersion() != VERSION_CURRENT || BULK_MERGE_ENABLED == false) {
				// naive merge...
				StoredFieldsReader storedFieldsReader = mergeState.storedFieldsReaders[readerIndex];
				if (storedFieldsReader != null) {
					storedFieldsReader.checkIntegrity();
				}
				for (int docID = 0; docID < maxDoc; docID++) {
					if (liveDocs != null && liveDocs.get(docID) == false) {
						continue;
					}
					startDocument();
					storedFieldsReader.visitDocument(docID, visitor);
					finishDocument();
					++docCount;
				}
			} else if (matchingFieldsReader.getCompressionMode() == compressionMode &&
				matchingFieldsReader.getChunkSize() == chunkSize &&
				matchingFieldsReader.getPackedIntsVersion() == PackedInts.VERSION_CURRENT &&
				liveDocs == null &&
				!tooDirty(matchingFieldsReader)) {
				// optimized merge, raw byte copy
				// its not worth fine-graining this if there are deletions.

				// if the format is older, its always handled by the naive merge case above
				assert matchingFieldsReader.getVersion() == VERSION_CURRENT;
				matchingFieldsReader.checkIntegrity();

				// flush any pending chunks
				if (numBufferedDocs > 0) {
					flush();
					numDirtyChunks++; // incomplete: we had to force this flush
				}

				// iterate over each chunk. we use the stored fields index to find chunk boundaries,
				// read the docstart + doccount from the chunk header (we write a new header, since doc numbers will change),
				// and just copy the bytes directly.
				IndexInput rawDocs = matchingFieldsReader.getFieldsStream();
				CompressingStoredFieldsIndexReader index = matchingFieldsReader.getIndexReader();
				rawDocs.seek(index.getStartPointer(0));
				int docID = 0;
				while (docID < maxDoc) {
					// read header
					int base = rawDocs.readVInt();
					if (base != docID) {
						throw new CorruptIndexException("invalid state: base=" + base + ", docID=" + docID, rawDocs);
					}
					int code = rawDocs.readVInt();

					// write a new index entry and new header for this chunk.
					int bufferedDocs = code >>> 1;
					indexWriter.writeIndex(bufferedDocs, fieldsStream.getFilePointer());
					fieldsStream.writeVInt(docBase); // rebase
					fieldsStream.writeVInt(code);
					docID += bufferedDocs;
					docBase += bufferedDocs;
					docCount += bufferedDocs;

					if (docID > maxDoc) {
						throw new CorruptIndexException("invalid state: base=" + base + ", count=" + bufferedDocs + ", maxDoc=" + maxDoc, rawDocs);
					}

					// copy bytes until the next chunk boundary (or end of chunk data).
					// using the stored fields index for this isn't the most efficient, but fast enough
					// and is a source of redundancy for detecting bad things.
					final long end;
					if (docID == maxDoc) {
						end = matchingFieldsReader.getMaxPointer();
					} else {
						end = index.getStartPointer(docID);
					}
					fieldsStream.copyBytes(rawDocs, end - rawDocs.getFilePointer());
				}

				if (rawDocs.getFilePointer() != matchingFieldsReader.getMaxPointer()) {
					throw new CorruptIndexException("invalid state: pos=" + rawDocs.getFilePointer() + ", max=" + matchingFieldsReader.getMaxPointer(), rawDocs);
				}

				// since we bulk merged all chunks, we inherit any dirty ones from this segment.
				numChunks += matchingFieldsReader.getNumChunks();
				numDirtyChunks += matchingFieldsReader.getNumDirtyChunks();
			} else {
				// optimized merge, we copy serialized (but decompressed) bytes directly
				// even on simple docs (1 stored field), it seems to help by about 20%

				// if the format is older, its always handled by the naive merge case above
				assert matchingFieldsReader.getVersion() == VERSION_CURRENT;
				matchingFieldsReader.checkIntegrity();

				for (int docID = 0; docID < maxDoc; docID++) {
					if (liveDocs != null && liveDocs.get(docID) == false) {
						continue;
					}
					SerializedDocument doc = matchingFieldsReader.document(docID);
					startDocument();
					bufferedDocs.copyBytes(doc.in, doc.length);
					numStoredFieldsInDoc = doc.numStoredFields;
					finishDocument();
					++docCount;
				}
			}
		}
		finish(mergeState.mergeFieldInfos, docCount);
		return docCount;
	}

	/**
	 * Returns true if we should recompress this reader, even though we could bulk merge compressed data
	 * <p>
	 * The last chunk written for a segment is typically incomplete, so without recompressing,
	 * in some worst-case situations (e.g. frequent reopen with tiny flushes), over time the
	 * compression ratio can degrade. This is a safety switch.
	 */
	boolean tooDirty(CompressingStoredFieldsReader candidate) {
		// more than 1% dirty, or more than hard limit of 1024 dirty chunks
		return candidate.getNumDirtyChunks() > 1024 ||
			candidate.getNumDirtyChunks() * 100 > candidate.getNumChunks();
	}

	private static class CompressingStoredFieldsMergeSub extends DocIDMerger.Sub {
		private final CompressingStoredFieldsReader reader;
		private final int maxDoc;
		int docID = -1;

		public CompressingStoredFieldsMergeSub(CompressingStoredFieldsReader reader, MergeState.DocMap docMap, int maxDoc) {
			super(docMap);
			this.maxDoc = maxDoc;
			this.reader = reader;
		}

		@Override
		public int nextDoc() {
			docID++;
			if (docID == maxDoc) {
				return NO_MORE_DOCS;
			} else {
				return docID;
			}
		}
	}
}
