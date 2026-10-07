package org.webrtc;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/** Keeps screen encoding budgets stable across idle periods, retaining native software fallback. */
public final class ScreenVideoEncoderFactory extends DefaultVideoEncoderFactory {
    public interface Observer {
        boolean isSampling();
        void onFrame(long inputNs, long outputNs, int bytes, boolean keyFrame, Integer qp,
                     int bitrateBps, double framerateFps);
    }

    public ScreenVideoEncoderFactory(EglBase.Context context, IntSupplier frameRate, Observer observer) {
        super(new HardwareVideoEncoderFactory(context, true, true) {
            @Override public VideoEncoder createEncoder(VideoCodecInfo info) {
                VideoEncoder encoder = super.createEncoder(info);
                return encoder == null ? null : new ObservedEncoder(encoder, frameRate, observer);
            }
        });
    }

    private static final class ObservedEncoder implements VideoEncoder {
        private final VideoEncoder delegate;
        private final Observer observer;
        private final IntSupplier frameRate;
        private final ConcurrentHashMap<Long, Long> inputs = new ConcurrentHashMap<>();
        private volatile int bitrateBps;
        private volatile double framerateFps;

        ObservedEncoder(VideoEncoder delegate, IntSupplier frameRate, Observer observer) {
            this.delegate = delegate;
            this.frameRate = frameRate;
            this.observer = observer;
        }

        @Override public VideoCodecStatus initEncode(Settings settings, Callback callback) {
            bitrateBps = settings.startBitrate * 1000;
            framerateFps = settings.maxFramerate;
            return delegate.initEncode(settings, (image, info) -> {
                Long inputNs = inputs.remove(image.captureTimeNs);
                if (inputNs != null && observer.isSampling()) {
                    observer.onFrame(inputNs, System.nanoTime(), image.buffer.remaining(),
                            image.frameType == EncodedImage.FrameType.VideoFrameKey, image.qp,
                            bitrateBps, framerateFps);
                }
                callback.onEncodedFrame(image, info);
            });
        }

        @Override public VideoCodecStatus encode(VideoFrame frame, EncodeInfo info) {
            if (observer.isSampling()) {
                if (inputs.size() >= 240) inputs.clear();
                inputs.put(frame.getTimestampNs(), System.nanoTime());
            } else if (!inputs.isEmpty()) inputs.clear();
            VideoCodecStatus status = delegate.encode(frame, info);
            if (status != VideoCodecStatus.OK) inputs.remove(frame.getTimestampNs());
            return status;
        }

        @Override public VideoCodecStatus setRateAllocation(BitrateAllocation bitrate, int fps) {
            bitrateBps = bitrate.getSum();
            framerateFps = Math.max(5, Math.min(60, frameRate.getAsInt()));
            return delegate.setRateAllocation(bitrate, (int) framerateFps);
        }

        @Override public VideoCodecStatus setRates(RateControlParameters parameters) {
            bitrateBps = parameters.bitrate.getSum();
            // HardwareVideoEncoder advances its surface timestamp by 1 / fps. The
            // native estimate can fall to 1 fps on a static screen, giving each
            // resumed animation frame a whole second's CBR budget and flooding RTP.
            // This changes the encoder budget only; capture still follows damage
            // events and the selected maximum, and the network bitrate is preserved.
            framerateFps = Math.max(5, Math.min(60, frameRate.getAsInt()));
            return delegate.setRates(new RateControlParameters(parameters.bitrate, framerateFps));
        }

        @Override public VideoCodecStatus release() {
            inputs.clear();
            return delegate.release();
        }
        @Override public boolean isHardwareEncoder() { return delegate.isHardwareEncoder(); }
        @Override public ScalingSettings getScalingSettings() { return delegate.getScalingSettings(); }
        @Override public ResolutionBitrateLimits[] getResolutionBitrateLimits() { return delegate.getResolutionBitrateLimits(); }
        @Override public EncoderInfo getEncoderInfo() { return delegate.getEncoderInfo(); }
        @Override public String getImplementationName() { return delegate.getImplementationName(); }
    }
}
