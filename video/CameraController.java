package d2d.testing.streaming.video;

import android.content.Context;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import d2d.testing.streaming.gui.AutoFitTextureView;

public class CameraController {

    public interface Callback{
        void cameraStarted();
        void cameraError(int error);
        void cameraError(Exception ex);
        void cameraClosed();
    }


    private static CameraController INSTANCE = null;

    public static void initiateInstance(Context ctx){
        if(INSTANCE == null){
            INSTANCE = new CameraController(ctx);

        }
    }

    public static CameraController getInstance(){
        return INSTANCE;
    }

    private final WindowManager mWinManager;
    private final CameraManager mCamManager;
    private String mCameraId;
    private List<Surface> mSurfaceList;
    private final List<Callback> mListeners;
    private final HandlerThread mCallbackHandlerTh;
    private final Handler mCallbackHandler;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mCaptureBuilder;
    private CameraCaptureSession mCaptureSession;
    private AutoFitTextureView mTextureView;
    private Context mContext;
    private boolean mConfigured = false;


    private final CameraDevice.StateCallback mCamStCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            try {
                mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                for(Surface surf : mSurfaceList){
                    mCaptureBuilder.addTarget(surf);
                }
                mCameraDevice.createCaptureSession(mSurfaceList, mCamSessionStCallback, mCallbackHandler);
            } catch (CameraAccessException e) {
                for(Callback cb : mListeners){
                    cb.cameraError(e);
                }
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            for(Callback cb : mListeners){
                cb.cameraClosed();
            }
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            for(Callback cb : mListeners){
                cb.cameraError(error);
            }
        }
    };

    public CameraDevice getCameraDevice() { return mCameraDevice;}

    private final CameraCaptureSession.StateCallback mCamSessionStCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mCaptureSession = session;
            mCaptureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

            try {
                //request endlessly repeating capture of images by this capture session
                mCaptureSession.setRepeatingRequest(mCaptureBuilder.build(), null, mCallbackHandler);
                for(Callback cb : mListeners){
                    cb.cameraStarted();
                }
            } catch (CameraAccessException e) {
                for(Callback cb : mListeners){
                    cb.cameraError(e);
                }
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            for(Callback cb : mListeners){
                cb.cameraError(new Exception());
            }
        }
    };

    private CameraController(Context context){
        mCamManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mWinManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mListeners = new ArrayList<>();
        mCallbackHandlerTh = new HandlerThread("Camera Callback handler");
        mCallbackHandlerTh.start();
        mCallbackHandler = new Handler(mCallbackHandlerTh.getLooper());
        mCameraId = "0";
        if (cameraFacingBack(mCameraId)) {
            switchCamera();
        }
        mTextureView = null;
        mContext = context;
        mConfigured = false;
    }

    private String[] getCameraIdList(){
        try {
            return mCamManager.getCameraIdList();
        } catch (CameraAccessException e) {return new String[0];}
    }


    public <T> Size[] getCameraOutputSizes(String cameraId, Class<T> klass){
        try{
            CameraCharacteristics characteristics = mCamManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            return map.getOutputSizes(klass);
        }catch (CameraAccessException e){
            return new Size[0];
        }
    }

    public void configureCamera(AutoFitTextureView surface, Context context) {
        mTextureView = surface;
        mContext = context;
        mConfigured = true;
    }

    public void startCamera() {

        if (mConfigured == true) {
            mSurfaceList = new ArrayList<>();
            Size[] resolutions = getPrivType_2Target_MaxResolutions(mCameraId, SurfaceTexture.class, MediaCodec.class);

            mTextureView.setAspectRatio(resolutions[0].getWidth(), resolutions[0].getHeight());
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(resolutions[0].getWidth(), resolutions[0].getHeight());
            Surface surface1 = new Surface(surfaceTexture);
            mSurfaceList.add(surface1);

            try {
                VideoPacketizerDispatcher.start(PreferenceManager.getDefaultSharedPreferences(mContext), VideoQuality.DEFAULT_VIDEO_QUALITY);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mSurfaceList.add(VideoPacketizerDispatcher.getEncoderInputSurface());

            try {
                mCamManager.openCamera(mCameraId, mCamStCallback, mCallbackHandler);
            } catch (SecurityException | CameraAccessException ex) {
                for (Callback cb : mListeners) {
                    cb.cameraError(ex);
                }
            }
        } else {
            throw new IllegalStateException("Camera not configured");
        }
    }

    public void stopCamera(){
        try {
            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCameraDevice.close();
        } catch (CameraAccessException ignored) {}
        mSurfaceList = null;
        mCameraDevice = null;
        mCaptureBuilder = null;
        mCaptureSession = null;
    }

    public void switchCamera() {
        String[] cameraIdList = getCameraIdList();
        if(cameraFacingFront(mCameraId)) {
            for (String id : cameraIdList) {
                if (cameraFacingBack(id)) {
                    mCameraId = id;
                    break;
                }
            }
        }
        else if(cameraFacingBack(mCameraId)){
            for (String id : cameraIdList) {
                if (cameraFacingFront(id)) {
                    mCameraId = id;
                    break;
                }
            }
        }

        if (mConfigured) {
            stopCamera();
            startCamera();
        }
    }

    public void addListener(Callback cb){
        mListeners.add(cb);
    }

    public void removeListener(Callback cb){
        mListeners.remove(cb);
    }


    private boolean cameraFacingFront(String cameraId){
        try {
            CameraCharacteristics characteristics = mCamManager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return true;
            }
        } catch (CameraAccessException ignored) {}
        return false;
    }

    private boolean cameraFacingBack(String cameraId){
        try {
            CameraCharacteristics characteristics = mCamManager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return true;
            }
        } catch (CameraAccessException ignored) {}
        return false;
    }

    private boolean cameraExternal(String cameraId){
        try {
            CameraCharacteristics characteristics = mCamManager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                return true;
            }
        } catch (CameraAccessException ignored) {}
        return false;
    }


    public static boolean isHardwareLevelSupported(CameraCharacteristics c, int requiredLevel) {
        final int[] sortedHwLevels = {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
        };
        int deviceLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (requiredLevel == deviceLevel) {
            return true;
        }

        for (int sortedlevel : sortedHwLevels) {
            if (sortedlevel == requiredLevel) {
                return true;
            } else if (sortedlevel == deviceLevel) {
                return false;
            }
        }
        return false; // Should never reach here
    }

    public <T, X> Size[] getPrivType_2Target_MaxResolutions(String cameraId, Class<T> previewClass, Class<X> codecClass){
        Size[] res = new Size[2];
        try {
            CameraCharacteristics characteristics = mCamManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3) || isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)){
                res[0] = getPreviewOutputSize(mWinManager.getDefaultDisplay(), characteristics, previewClass, null); //Mejor match de resolucion para preview
                res[1] = map.getOutputSizes(codecClass)[0]; //Maxima resolucion para procesamiento
            }
            else if(isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED)){
                CamcorderProfile profile = CamcorderProfile.get(Integer.parseInt(cameraId), CamcorderProfile.QUALITY_HIGH);
                res[0] = getPreviewOutputSize(mWinManager.getDefaultDisplay(), characteristics, previewClass, null); //Mejor match de resolucion para preview
                res[1] = new Size(profile.videoFrameWidth, profile.videoFrameHeight); //Maxima resolucion permitida por la camara
            }
            else { //Legacy
                res[0] = getPreviewOutputSize(mWinManager.getDefaultDisplay(), characteristics, previewClass, null); //Mejor match de resolucion para preview
                res[1] = getPreviewOutputSize(mWinManager.getDefaultDisplay(), characteristics, codecClass, null); //Mejor match de resolucion para procesamiento
            }
        } catch (CameraAccessException ignored) {}
        return res;
    }


    private static <T> Size getPreviewOutputSize(Display display, CameraCharacteristics characteristics, Class<T> targetClass, Integer format){

        // Find which is smaller: screen or 1080p
        SmartSize screenSize = getDisplaySmartSize(display);
        boolean hdScreen = screenSize.longd >= SmartSize.SIZE_1080p.longd || screenSize.shortd >= SmartSize.SIZE_1080p.shortd;
        SmartSize maxSize = null;
        if (hdScreen) maxSize = SmartSize.SIZE_1080p;
        else maxSize = screenSize;

        // If image format is provided, use it to determine supported sizes; else use target class
        StreamConfigurationMap config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (format == null)
            assert(StreamConfigurationMap.isOutputSupportedFor(targetClass));
        else
            assert(config.isOutputSupportedFor(format));
        Size[] allSizes = null;
        List<SmartSize> allSSizesList = new ArrayList<>();
        if (format == null)
            allSizes = config.getOutputSizes(targetClass) ;
        else allSizes = config.getOutputSizes(format);
        List<Size> allSizesList = new ArrayList<>(Arrays.asList(allSizes));

        // Get available sizes and sort them by area from largest to smallest
        Collections.sort(allSizesList, new SmartSize.CompareSizesByArea());
        Collections.reverse(allSizesList);

        for(Size sz : allSizesList){
            allSSizesList.add(new SmartSize(sz.getWidth(), sz.getHeight()));
        }

        // Then, get the largest output size that is smaller or equal than our max size
        for(SmartSize sz : allSSizesList){
            if(sz.longd <= maxSize.longd && sz.shortd <= maxSize.shortd){
                return sz.size;
            }
        }
        return null;
    }

    private static class SmartSize {
        public Size size;
        public int longd;
        public int shortd;

        public static final SmartSize SIZE_1080p = new SmartSize(1920, 1080);

        public SmartSize(int width, int height){
            size = new Size(width, height);
            longd = Math.max(width, height);
            shortd = Math.min(width, height);
        }

        static class CompareSizesByArea implements Comparator<Size> {
            @Override
            public int compare(Size lhs, Size rhs) {
                // We cast here to ensure the multiplications won't overflow
                return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                        (long) rhs.getWidth() * rhs.getHeight());
            }
        }
    }

    private static SmartSize getDisplaySmartSize(Display display){
        Point outPoint = new Point();
        display.getRealSize(outPoint);
        return new SmartSize(outPoint.x, outPoint.y);
    }

}
