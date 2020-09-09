package com.example.libtest.mpegts.sources;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.libtest.mpegts.MTSPacket;
import com.example.libtest.mpegts.sources.AbstractMTSSource;
import com.example.libtest.mpegts.sources.ContinuityFixer;
import com.example.libtest.mpegts.sources.MTSSource;
import com.example.libtest.mpegts.sources.ResettableMTSSource;

public class MultiMTSSource extends AbstractMTSSource {
	static final Logger log = LoggerFactory.getLogger("multisource");
	private List<com.example.libtest.mpegts.sources.MTSSource> sources;
	private com.example.libtest.mpegts.sources.MTSSource currentSource;
	private int idx;
	private boolean fixContinuity;

	private ContinuityFixer continuityFixer;
	private int maxLoops;
	private int currentLoop;
	private boolean closeCurrentSource;

	protected MultiMTSSource(boolean fixContinuity, int maxloops, Collection<com.example.libtest.mpegts.sources.MTSSource> sources) {
		Preconditions.checkArgument(sources.size() > 0, "Multisource must at least contain one source");
		Preconditions.checkArgument(maxloops != 0, "Cannot loop zero times");
		this.sources = Lists.newArrayList(sources);
		this.fixContinuity = fixContinuity;
		idx = 0;
		currentSource = this.sources.get(0);
		if (fixContinuity) {
			continuityFixer = new ContinuityFixer();
		}
		this.maxLoops = maxloops;
		if (maxloops != 1) {
			checkLoopingPossible(sources);
		}
		this.currentLoop = 1;
		this.closeCurrentSource = false;
	}

	private void checkLoopingPossible(Collection<com.example.libtest.mpegts.sources.MTSSource> sources) {
		for (com.example.libtest.mpegts.sources.MTSSource source : sources) {
			checkLoopingPossible(source);
		}
	}

	private void checkLoopingPossible(com.example.libtest.mpegts.sources.MTSSource source) {
		if (!(source instanceof ResettableMTSSource)) {
			throw new IllegalStateException("Sources must be resettable for looping");
		}
	}

	@Override
	protected MTSPacket nextPacketInternal() throws Exception {
		if (currentSource == null) {
			return null;
		}
		MTSPacket tsPacket = currentSource.nextPacket();
		if (tsPacket != null) {
			if (fixContinuity) {
				continuityFixer.fixContinuity(tsPacket);
			}
			return tsPacket;
		} else {
			nextSource();
			return nextPacket();
		}
	}

	public synchronized void updateSources(List<com.example.libtest.mpegts.sources.MTSSource> newSources) {
		checkLoopingPossible(newSources);
		List<com.example.libtest.mpegts.sources.MTSSource> oldSources = this.sources;
		this.sources = newSources;
		for (com.example.libtest.mpegts.sources.MTSSource oldSource : oldSources) {
			if (!newSources.contains(oldSource) && oldSource != currentSource) {
				try {
					oldSource.close();
				} catch (Exception e) {
					log.error("Error closing source", e);

				}
			}
		}
		closeCurrentSource = !newSources.contains(currentSource);
		// Force next call to nextSource() to pick source 0 (first source)
		idx = -1;
	}

	@Override
	protected synchronized void closeInternal() throws Exception {
		for (com.example.libtest.mpegts.sources.MTSSource source : sources) {
			source.close();
		}
		if (closeCurrentSource && currentSource != null && !sources.contains(currentSource)) {
			currentSource.close();
		}
	}


	private synchronized void nextSource() {
		if (closeCurrentSource) {
			try {
				currentSource.close();
			} catch (Exception e) {
				log.error("Error closing source", e);
			} finally {
				closeCurrentSource = false;
			}
		}
		if (fixContinuity) {
			continuityFixer.nextSource();
		}
		idx++;
		if (idx >= sources.size()) {
			currentLoop++;
			if (maxLoops > 0 && currentLoop > maxLoops) {
				currentSource = null;
			} else {
				idx = 0;
				for (com.example.libtest.mpegts.sources.MTSSource source : sources) {
					try {
						((ResettableMTSSource) source).reset();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				currentSource = sources.get(idx);
			}
		} else {
			currentSource = sources.get(idx);
		}
		if (idx < sources.size()) {
			log.info("Switched to source #{}", idx);
		}
	}

	public static MultiMTSSourceBuilder builder() {
		return new MultiMTSSourceBuilder();
	}

	public static class MultiMTSSourceBuilder {
		private List<com.example.libtest.mpegts.sources.MTSSource> sources = Lists.newArrayList();
		boolean fixContinuity = false;
		private int maxLoops = 1;

		private MultiMTSSourceBuilder() {
		}

		public MultiMTSSourceBuilder addSource(com.example.libtest.mpegts.sources.MTSSource source) {
			sources.add(source);
			return this;
		}

		public MultiMTSSourceBuilder addSources(Collection<com.example.libtest.mpegts.sources.MTSSource> sources) {
			sources.addAll(sources);
			return this;
		}

		public MultiMTSSourceBuilder setSources(com.example.libtest.mpegts.sources.MTSSource... sources) {
			this.sources = Lists.newArrayList(sources);
			return this;
		}

		public MultiMTSSourceBuilder setSources(Collection<MTSSource> sources) {
			this.sources = Lists.newArrayList(sources);
			return this;
		}

		public MultiMTSSourceBuilder setFixContinuity(boolean fixContinuity) {
			this.fixContinuity = fixContinuity;
			return this;
		}

		public MultiMTSSourceBuilder loop() {
			this.maxLoops = -1;
			return this;
		}

		public MultiMTSSourceBuilder loops(int count) {
			this.maxLoops = count;
			return this;
		}

		public MultiMTSSourceBuilder noLoop() {
			this.maxLoops = 1;
			return this;
		}

		public MultiMTSSource build() {
			return new MultiMTSSource(fixContinuity, maxLoops, sources);
		}
	}
}
