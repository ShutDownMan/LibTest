package com.example.libtest.mpegts.sources;

import java.nio.ByteBuffer;
import java.util.Map;

import com.google.common.collect.Maps;
import com.example.libtest.mpegts.MTSPacket;


/**
 * This class will attempt to fix timestamp discontinuities
 * when switching from one source to another.
 * This should allow for smoother transitions between videos.<br>
 * This class does 3 things:
 * <ol>
 * <li> Rewrite the PCR to be continuous with the previous source</li>
 * <li> Rewrite the PTS of the PES to be continuous with the previous source</li>
 * <li> Rewrite the continuity counter to be continuous with the previous source</li>
 * </ol>
 *
 * Code using this class should call {@link #fixContinuity(MTSPacket)} for each source packet,
 * then {@link #nextSource()} after the last packet of the current source and before the first packet of the next source.
 */
public class ContinuityFixer {
	private Map<Integer, MTSPacket> pcrPackets;
	private Map<Integer, MTSPacket> allPackets;
	private Map<Integer, Long> ptss;
	private Map<Integer, Long> lastPTSsOfPreviousSource;
	private Map<Integer, Long> lastPCRsOfPreviousSource;
	private Map<Integer, Long> firstPCRsOfCurrentSource;
	private Map<Integer, Long> firstPTSsOfCurrentSource;

	private Map<Integer, MTSPacket> lastPacketsOfPreviousSource = Maps.newHashMap();
	private Map<Integer, MTSPacket> firstPacketsOfCurrentSource = Maps.newHashMap();
	private Map<Integer, Integer> continuityFixes = Maps.newHashMap();

	private boolean firstSource;


	public ContinuityFixer() {
		pcrPackets = Maps.newHashMap();
		allPackets = Maps.newHashMap();
		ptss = Maps.newHashMap();
		lastPTSsOfPreviousSource = Maps.newHashMap();
		lastPCRsOfPreviousSource = Maps.newHashMap();
		firstPCRsOfCurrentSource = Maps.newHashMap();
		firstPTSsOfCurrentSource = Maps.newHashMap();

		lastPacketsOfPreviousSource = Maps.newHashMap();
		firstPacketsOfCurrentSource = Maps.newHashMap();
		continuityFixes = Maps.newHashMap();
		firstSource = true;
	}

	/**
	 * Signals the {@link com.example.libtest.mpegts.sources.ContinuityFixer} that the following
	 * packet will be from another source.
	 *
	 * Call this method after the last packet of the current source and before the first packet of the next source.
	 */
	public void nextSource() {
		firstPCRsOfCurrentSource.clear();
		lastPCRsOfPreviousSource.clear();
		firstPTSsOfCurrentSource.clear();
		lastPTSsOfPreviousSource.clear();
		firstPacketsOfCurrentSource.clear();
		lastPacketsOfPreviousSource.clear();
		for (MTSPacket mtsPacket : pcrPackets.values()) {
			lastPCRsOfPreviousSource.put(mtsPacket.getPid(), mtsPacket.getAdaptationField().getPcr().getValue());
		}
		lastPTSsOfPreviousSource.putAll(ptss);
		lastPacketsOfPreviousSource.putAll(allPackets);
		pcrPackets.clear();
		ptss.clear();
		allPackets.clear();
		firstSource = false;
	}

	/**
	 * Fix the continuity of the packet.
	 *
	 * Call this method for each source packet, in order.
	 *
	 * @param tsPacket The packet to fix.
	 */
	public void fixContinuity(MTSPacket tsPacket) {
		int pid = tsPacket.getPid();
		allPackets.put(pid, tsPacket);
		if (!firstPacketsOfCurrentSource.containsKey(pid)) {
			firstPacketsOfCurrentSource.put(pid, tsPacket);
			if (!firstSource) {
				MTSPacket lastPacketOfPreviousSource = lastPacketsOfPreviousSource.get(pid);
				int continuityFix = lastPacketOfPreviousSource == null ? 0 : lastPacketOfPreviousSource.getContinuityCounter() - tsPacket.getContinuityCounter();
				if (tsPacket.isContainsPayload()) {
					continuityFix++;
				}
				continuityFixes.put(pid, continuityFix);
			}
		}
		if (!firstSource) {
			tsPacket.setContinuityCounter((tsPacket.getContinuityCounter() + continuityFixes.get(pid)) % 16);
		}
		fixPTS(tsPacket, pid);
		fixPCR(tsPacket, pid);
	}

	private void fixPCR(MTSPacket tsPacket, int pid) {
		if (tsPacket.isAdaptationFieldExist() && tsPacket.getAdaptationField() != null) {
			if (tsPacket.getAdaptationField().isPcrFlag()) {
				if (!firstPCRsOfCurrentSource.containsKey(pid)) {
					firstPCRsOfCurrentSource.put(pid, tsPacket.getAdaptationField().getPcr().getValue());
				}
				rewritePCR(tsPacket);
				pcrPackets.put(pid, tsPacket);
			}
		}
	}

	private void fixPTS(MTSPacket tsPacket, int pid) {
		if (tsPacket.isContainsPayload()) {
			ByteBuffer payload = tsPacket.getPayload();
			if (((payload.get(0) & 0xff) == 0) && ((payload.get(1) & 0xff) == 0) && ((payload.get(2) & 0xff) == 1)) {
				int extension = payload.getShort(6) & 0xffff;
				if ((extension & 0x80) != 0) {
					// PTS is present
					// TODO add payload size check to avoid indexoutofboundexception
					long pts = (((payload.get(9) & 0xE)) << 29) | (((payload.getShort(10) & 0xFFFE)) << 14) | ((payload.getShort(12) & 0xFFFE) >> 1);
					if (!firstPTSsOfCurrentSource.containsKey(pid)) {
						firstPTSsOfCurrentSource.put(pid, pts);
					}
					if (!firstSource) {
						long newPts = Math.round(pts + (getTimeGap(pid) / 300.0) + 100 * ((27_000_000 / 300.0) / 1_000));

						payload.put(9, (byte) (0x20 | ((newPts & 0x1C0000000l) >> 29) | 0x1));
						payload.putShort(10, (short) (0x1 | ((newPts & 0x3FFF8000) >> 14)));
						payload.putShort(12, (short) (0x1 | ((newPts & 0x7FFF) << 1)));
						payload.rewind();
						pts = newPts;
					}

					ptss.put(pid, pts);
				}
			}
		}
	}

	private long getTimeGap(int pid) {
		// Try with PCR of the same PID
		Long lastPCROfPreviousSource = lastPCRsOfPreviousSource.get(pid);
		if (lastPCROfPreviousSource == null) {
			lastPCROfPreviousSource = 0l;
		}
		Long firstPCROfCurrentSource = firstPCRsOfCurrentSource.get(pid);
		if (firstPCROfCurrentSource != null) {
			return lastPCROfPreviousSource - firstPCROfCurrentSource;
		}

		// Try with any PCR
		if (!lastPCRsOfPreviousSource.isEmpty()) {
			int pcrPid = lastPCRsOfPreviousSource.keySet().iterator().next();
			lastPCROfPreviousSource = lastPCRsOfPreviousSource.get(pcrPid);
			if (lastPCROfPreviousSource == null) {
				lastPCROfPreviousSource = 0l;
			}
			firstPCROfCurrentSource = firstPCRsOfCurrentSource.get(pcrPid);
			if (firstPCROfCurrentSource != null) {
				return lastPCROfPreviousSource - firstPCROfCurrentSource;
			}
		}

		// Try with PTS of the same PID
		Long lastPTSOfPreviousSource = lastPTSsOfPreviousSource.get(pid);
		if (lastPTSOfPreviousSource == null) {
			lastPTSOfPreviousSource = 0l;
		}

		Long firstPTSofCurrentSource = firstPTSsOfCurrentSource.get(pid);
		if (firstPTSofCurrentSource != null) {
			return (lastPTSOfPreviousSource - firstPTSofCurrentSource) * 300;
		}

		// Try with any PTS
		if (!lastPTSsOfPreviousSource.isEmpty()) {
			int randomPid = lastPTSsOfPreviousSource.keySet().iterator().next();
			lastPTSOfPreviousSource = lastPTSsOfPreviousSource.get(randomPid);
			if (lastPTSOfPreviousSource == null) {
				lastPTSOfPreviousSource = 0l;
			}

			firstPTSofCurrentSource = firstPTSsOfCurrentSource.get(randomPid);
			if (firstPTSofCurrentSource != null) {
				return (lastPTSOfPreviousSource - firstPTSofCurrentSource) * 300;
			}
		}

		return 0;
	}

	private void rewritePCR(MTSPacket tsPacket) {
		if (firstSource) {
			return;
		}
		long timeGap = getTimeGap(tsPacket.getPid());
		long pcr = tsPacket.getAdaptationField().getPcr().getValue();
		long newPcr = pcr + timeGap + 100 * ((27_000_000) / 1_000);
		tsPacket.getAdaptationField().getPcr().setValue(newPcr);
	}
}
