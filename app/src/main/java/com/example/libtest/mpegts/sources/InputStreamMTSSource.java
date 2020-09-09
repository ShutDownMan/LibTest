package com.example.libtest.mpegts.sources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import com.google.common.base.Preconditions;
import com.example.libtest.mpegts.Constants;
import com.example.libtest.mpegts.MTSPacket;
import com.example.libtest.mpegts.sources.AbstractMTSSource;

public class InputStreamMTSSource extends AbstractMTSSource {

	private InputStream inputStream;

	private InputStreamMTSSource(InputStream inputStream) throws IOException {
		this.inputStream = inputStream;
	}

	@Override
	protected MTSPacket nextPacketInternal() throws IOException {
		byte[] barray = new byte[Constants.MPEGTS_PACKET_SIZE];
		if (inputStream.read(barray) != Constants.MPEGTS_PACKET_SIZE) {
			inputStream.close();
			return null;
		}

		// Parse the packet
		return new MTSPacket(ByteBuffer.wrap(barray));
	}

	@Override
	protected void closeInternal() throws Exception {
		try (InputStream toClose = inputStream) {
		}
		inputStream = null;
	}

	public static InputStreamMTSSourceBuilder builder() {
		return new InputStreamMTSSourceBuilder();
	}

	public static class InputStreamMTSSourceBuilder {
		private InputStream inputStream;

		private InputStreamMTSSourceBuilder() {
		}

		public InputStreamMTSSourceBuilder setInputStream(InputStream inputStream) {
			this.inputStream = inputStream;
			return this;
		}

		public InputStreamMTSSource build() throws IOException {
			Preconditions.checkNotNull(inputStream, "InputStream cannot be null");
			return new InputStreamMTSSource(inputStream);
		}
	}
}
