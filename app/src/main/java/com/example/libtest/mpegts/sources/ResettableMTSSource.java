package com.example.libtest.mpegts.sources;

import com.example.libtest.mpegts.sources.MTSSource;

public interface ResettableMTSSource extends MTSSource {
	public void reset() throws Exception;
}
