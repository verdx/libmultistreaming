package d2d.testing.streaming.video;

import android.content.Context;
import android.graphics.Point;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

    private CameraController(Context ctx){
        mCamManager = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        mWinManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        mCameraId = null;
        mListeners = new ArrayList<>();
        mCallbackHandlerTh = new HandlerThread("Camera Callback handler");
        mCallbackHandlerTh.start();
        mCallbackHandler = new Handler(mCallbackHandlerTh.getLooper());
    }

    public String[] getCameraIdList(){
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


    public void startCamera(String cameraId, List<Surface> surfaceList){
        mCameraId = cameraId;
        mSurfaceList = surfaceList;

        try{
            mCamManager.openCamera(cameraId, mCamStCallback, mCallbackHandler);
        }
        catch (SecurityException | CameraAccessException ex){
            for(Callback cb : mListeners){
                cb.cameraError(ex);
            }
        }
    }

    public void stopCamera(){
        if(mCameraId == null) return;
        try {
            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCameraDevice.close();
        } catch (CameraAccessException ignored) {}
        mCameraId = null;
        mSurfaceList = null;
        mCameraDevice = null;
        mCaptureBuilder = null;
        mCaptureSession = null;
    }

    public void addListener(Callback cb){
        mListeners.add(cb);
    }

    public void removeListener(Callback cb){
        mListeners.remove(cb);
    }


    public boolean itsCameraFacingFront(String cameraId){
        try {
            CameraCharacteristics characteristics = mCamManager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return true;
            }
        } catch (CameraAccessException ignored) {}
        return false;
    }

    public boolean itsCameraFacingBack(String cameraId){
        try {
            CameraCharacteristics characteristics = mCamManager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return true;
            }
        } catch (CameraAccessException ignored) {}
        return false;
    }

    public boolean itsCameraExternal(String cameraId){
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
