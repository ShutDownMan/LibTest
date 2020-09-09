package com.example.libtest.mpegts.sinks;

import java.nio.channels.ByteChannel;

import com.example.libtest.mpegts.MTSPacket;
import com.example.libtest.mpegts.sinks.MTSSink;

public class ByteChannelSink implements MTSSink {

	private ByteChannel byteChannel;

	private ByteChannelSink(ByteChannel byteChannel) {
		this.byteChannel = byteChannel;
	}

	@Override
	public void send(MTSPacket packet) throws Exception {
		byteChannel.write(packet.getBuffer());
	}

	public static ByteChannelSinkBuilder builder() {
		return new ByteChannelSinkBuilder();
	}

	public static class ByteChannelSinkBuilder {
		private ByteChannel byteChannel;

		private ByteChannelSinkBuilder(){}

		public ByteChannelSink build() {
			return new ByteChannelSink(byteChannel);
		}

		public ByteChannelSinkBuilder setByteChannel(ByteChannel byteChannel) {
			this.byteChannel = byteChannel;
			return this;
		}
	}
}
