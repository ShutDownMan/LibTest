package com.example.libtest.mpegts.sources;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ByteChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;

import com.google.common.io.ByteSource;

import com.example.libtest.mpegts.sources.ByteChannelMTSSource;
import com.example.libtest.mpegts.sources.ByteSourceMTSSource;
import com.example.libtest.mpegts.sources.InputStreamMTSSource;
import com.example.libtest.mpegts.sources.LoopingMTSSource;
import com.example.libtest.mpegts.sources.MTSSource;
import com.example.libtest.mpegts.sources.MultiMTSSource;
import com.example.libtest.mpegts.sources.ResettableMTSSource;
import com.example.libtest.mpegts.sources.SeekableByteChannelMTSSource;

public class MTSSources {
	public static com.example.libtest.mpegts.sources.MTSSource fromSources(com.example.libtest.mpegts.sources.MTSSource... sources) {
		return fromSources(1, false, sources);
	}

	public static com.example.libtest.mpegts.sources.MTSSource fromSources(int loops, com.example.libtest.mpegts.sources.MTSSource... sources) {
		return fromSources(loops, false, sources);
	}

	public static com.example.libtest.mpegts.sources.MTSSource fromSources(int loops, boolean fixContinuity, com.example.libtest.mpegts.sources.MTSSource... sources) {
		return MultiMTSSource.builder()
				.setFixContinuity(fixContinuity)
				.setSources(sources)
				.loops(loops)
				.build();
	}

	public static com.example.libtest.mpegts.sources.MTSSource from(ByteChannel channel) throws IOException {
		return ByteChannelMTSSource.builder()
				.setByteChannel(channel)
				.build();
	}

	public static ResettableMTSSource from(SeekableByteChannel channel) throws IOException {
		return SeekableByteChannelMTSSource.builder()
				.setByteChannel(channel)
				.build();
	}

	public static ResettableMTSSource from(File file) throws IOException {
		return SeekableByteChannelMTSSource.builder()
				.setByteChannel(FileChannel.open(file.toPath()))
				.build();
	}

	public static ResettableMTSSource from(ByteSource byteSource) throws IOException {
		return ByteSourceMTSSource.builder()
				.setByteSource(byteSource)
				.build();
	}

	public static com.example.libtest.mpegts.sources.MTSSource from(InputStream inputStream) throws IOException {
		return InputStreamMTSSource.builder()
				.setInputStream(inputStream)
				.build();
	}

	public static com.example.libtest.mpegts.sources.MTSSource loop(ResettableMTSSource source) {
		return LoopingMTSSource.builder()
				.setSource(source)
				.build();
	}

	public static MTSSource loop(ResettableMTSSource source, int maxLoops) {
		return LoopingMTSSource.builder()
				.setSource(source)
				.setMaxLoops(maxLoops)
				.build();
	}

}
