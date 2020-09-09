package com.example.libtest.mpegts.sources;

import com.example.libtest.mpegts.MTSPacket;

public interface MTSSource {
	public MTSPacket nextPacket() throws Exception;
	public void close() throws Exception;
}
