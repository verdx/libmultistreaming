# Introduction

## What's new

**libstreaming2.0** is a forked repository from [libstreaming](https://github.com/fyhertz/libstreaming), offering some new functionalities and an easier use.

The main changes are the following:
  - The RTSP client and server have been modified so they can work together. This way, in the same app, you can send and receive different streams. 
  - A new class `StreamingRecord´ has been created were all streams, received and local, are stored. Classes can implement ´StreamingRecordObserver´ to be notified when a stream is received externally or a local stream is initiated.
  - Two new intermediary interfaces have been created,  `BasicViewModel´ and ´DefaultViewModel´, to manage the network on which the streams are going to be sent and received. Two default classes have been added, ´DefaultNetwork´ and ´DefaultViewModel´, which use the default network in the device and IP, but new implementations could be created, using other networks, such as Bluetooth or Wifi Direct.
  - An option to download the streams has also been created, using class `SaveStream´. This class can be used to download both local or received streams.
  - Following the example of repository [libstreaming-examples](https://github.com/fyhertz/libstreaming-examples), some usage examples of the new library have been added to repository [libstreaming2.0-examples](https://github.com/verdx/libstreaming2.0-examples).
  - The library is now also capable of "multi-hopping", automatically sending the received streams to other devices in the network.
  - This repository has been simplified and modified so it can be used as an external module in Android Studio in an easy way.

## What it does

**libstreaming2.0** is an API that allows you, with only a few lines of code, to stream the camera and/or microphone of an android powered device using RTP over UDP. It also allows to receive streams from other devices at the same time, and even sending them automatically to the rest of devices in the network. It can be configured to download the local and/or received streams.

* Android 4.0 or more recent is required.
* Supported encoders include H.264, H.263, AAC and AMR.



## How does it work? You should really read this, it's important!

# Using libstreaming in your app

## Required permissions and services

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <application>
        ...
        <activity android:name="net.verdx.libstreaming.gui.ViewStreamActivity" /> // ONLY if you want to receive and see streams
        <service android:name="net.verdx.libstreaming.rtsp.RtspServer" />
    </application>

```

## Adding it as a module

To include it in your application using Android Studio, clone this repository using:
```sh
git clone https://github.com/verdx/libstreaming2.0
```

, then open menu `File-\>Project Structure-\>Modules-\>Add(+)-\>Import´ and select the folder where it was downloaded.

It is recommended to add it as a submodule inside your own repository. This way if anyone clones your repository it will be easily downloaded. To do so run the following:

```sh
$ git submodule add https://github.com/verdx/libstreaming2.0
```

## How to stream video and audio to other devices

This example is extracted from [this simple app](htpps://github.com/verdx/libstreaming2.0-examples#Example2-sender). This Activity could also be a Fragment.

The `StreamingRecordObserver´ is needed in this case to listen on when a local stream has been started or ended. The ´TextureView.SurfaceTextureListener´ is used to be notified when the custom ´AutoFitTextureView´ is prepared to host the camera output.

```java
public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener, StreamingRecordObserver
    ...
```

The xml for this activity must include an `AutoFitTextureView´ if we want a preview of the camera output

```xml
    ...
    <net.verdx.libstreaming.gui.AutoFitTextureView
        android:id="@+id/textureView" android:layout_width="match_parent"
        android:layout_height="match_parent" />
    ...
```

The `onCreate´ method in this Activity or Fragment should include all of this initializations.

```java
    @Override
    protected void onCreate(Bundle savedInstanceState) {
       
        ...

       /*
        Manually check for the needed permissions
         */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.CAMERA}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.RECORD_AUDIO}, 1);
        }

        /*
        Add this class as an observer to the static StreamingRecord instance and the TextureView's Callbacks
         */
        StreamingRecord.getInstance().addObserver(this);
        mTextureView.setSurfaceTextureListener(this);

        /*
        Initialize the SessionBuilder with the desired parameters
         */
        mSessionBuilder = SessionBuilder.getInstance()
                .setPreviewOrientation(90)
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_AAC)
                .setAudioQuality(new AudioQuality(16000, 32000))
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setVideoQuality(new VideoQuality(320, 240, 20, 500000));

        /*
        Initialize the CameraController and ViewModel. This one will create the Network.
         */
        CameraController.initiateInstance(this);
        mViewModel = new DefaultViewModel(this.getApplication());

        /*
        We must observe the network availability to know when to start the server and client and in 
        case of network loss, restart them.
         */
        mViewModel.isNetworkAvailable().observe(this, aBoolean -> {
            isNetworkAvailable = aBoolean;
            mStatusTextView.setText(getDeviceStatus());
            if(isNetworkAvailable){
                mViewModel.initNetwork();
            }
        });
    }
```

In the Activity or Fragment's `onDestroy´ method we have to make sure to stop the camera.

```java
    @Override
    public void onDestroy() {
        ...
        CameraController.getInstance().stopCamera();
    }
```

To start and stop streaming the following methods must be used:

```java

        StreamingRecord.getInstance().addLocalStreaming(localStreamUUID, mStreamName, mSessionBuilder);
        StreamingRecord.getInstance().removeLocalStreaming();
```

If the `DefaultNetwork´ is being used, one of the following methods must be used to set the IPs with which it will be connecting. It should be called any time they are updated.

```java
((DefaultViewModel)mViewModel).setDestinationIpsArray(ArrayList<String> ipList);
((DefaultViewModel)mViewModel).setDestinationIpsSettings(Application app);
((DefaultViewModel)mViewModel).setDestinationIpsStream(InputStream stream);
```

From the implemented methods in the `TextureView.SurfaceTextureListener´, we only need to implement one, the rest can be empty in this case. We will configure the camera and start it when the ´AutoFitTextureView´ is available so the camera preview is shown there.

```java
    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        CameraController.getInstance().configureCamera(mTextureView, this);
        CameraController.getInstance().startCamera();
    }
```

To switch the camera to face the front or back, the following method can be used:

```java
CameraController.getInstance().switchCamera();
```

Finally, the useful methods to implement in this case from the `StreamingRecordObserver´ could be ´onLocalStreamingAvailable´ and ´onLocalStreamingUnavailable´, in case we need to display a message or change anything when the stream is started and finished. 

## How to receive streams 

//TODO

