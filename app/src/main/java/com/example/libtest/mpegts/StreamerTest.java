package com.example.libtest.mpegts;

import java.io.File;

import com.example.libtest.mpegts.Streamer;
import com.example.libtest.mpegts.sinks.MTSSink;
import com.example.libtest.mpegts.sinks.UDPTransport;
import com.example.libtest.mpegts.sources.MTSSource;
import com.example.libtest.mpegts.sources.MTSSources;
import com.example.libtest.mpegts.sources.ResettableMTSSource;

public class StreamerTest {
	public static void main(String[] args) throws Exception {

		// Set up mts sink
		MTSSink transport = UDPTransport.builder()
				//.setAddress("239.222.1.1")
				.setAddress("127.0.0.1")
				.setPort(1234)
				.setSoTimeout(5000)
				.setTtl(1)
				.build();


		ResettableMTSSource ts1 = MTSSources.from(new File("/Users/abaudoux/Downloads/EBSrecording.mpg"));

		// media132, media133 --> ok
		// media133, media132 --> ok
		// media123, media132 --> ko


		// Build source
		MTSSource source = MTSSources.loop(ts1);

		// build streamer
		com.example.libtest.mpegts.Streamer streamer = Streamer.builder()
				.setSource(source)
				//.setSink(ByteChannelSink.builder().setByteChannel(fc).build())
				.setSink(transport)
				.build();

		// Start streaming
		streamer.stream();

	}
}
