package com.example.libtest.mpegts.sinks;

import com.example.libtest.mpegts.MTSPacket;

public interface MTSSink {
	public void send(MTSPacket packet) throws Exception;
}
