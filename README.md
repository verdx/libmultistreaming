# Introduction

## What's new

**libmultistreaming** is a forked repository from [libstreaming](https://github.com/fyhertz/libstreaming), offering some new functionalities and an easier use.

The main changes are the following:
  - The RTSP client and server have been modified so they can work together. This way, in the same app, you can send and receive different streams. 
  - A new class `StreamingRecord´ has been created where all streams, received and local, are stored. Classes can implement ´StreamingRecordObserver´ to be notified when a stream is received externally or a local stream is initiated.
  - Two new intermediary interfaces have been created,  `BasicViewModel´ and ´DefaultViewModel´, to manage the network on which the streams are going to be sent and received. Two default classes have been added, ´DefaultNetwork´ and ´DefaultViewModel´, which use the default network in the device and IP, but new implementations could be created, using other networks, such as Bluetooth or Wifi Direct.
  - An option to download the streams has also been created, using the class `SaveStream´. This class can be used to download both local or received streams.
  - Following the example of repository [libstreaming-examples](https://github.com/fyhertz/libstreaming-examples), some usage examples of the new library have been added to a repository [libmultistreaming-examples](https://github.com/verdx/libmultistreaming-examples).
  - The library is now also capable of "multi-hopping", automatically sending the received streams to other devices in the network.
  - This repository has been simplified and modified so it can be used as an external module in Android Studio easily.

## What it does

**libmultistreaming** is an API that allows you, with only a few lines of code, to stream the camera and/or microphone of an Android-powered device using RTP over UDP. It also allows us to receive streams from other devices at the same time, and even send them automatically to the rest of the devices in the network. It can be configured to download the local and/or received streams.

* Android 4.0 or more recent is required.
* Supported encoders include H.264, H.263, AAC and AMR.



## How does it work? You should read this, it's important!

# Using libmultistreaming in your app

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
git clone https://github.com/verdx/libmultistreaming
```

, then open the menu `File-\>Project Structure-\>Modules-\>Add(+)-\>Import´ and select the folder where it was downloaded.

It is recommended to add it as a submodule inside your own repository. This way if anyone clones your repository it will be easily downloaded. To do so run the following:

```sh
$ git submodule add https://github.com/verdx/libmultistreaming
```

## How to stream video and audio to other devices

This example is extracted from [this simple app](https://github.com/verdx/libmultistreaming-examples/blob/master/README.md#example-2---sender). This Activity could also be a Fragment.

Both the ´StreamingRecordObserver´ and ´TextureView.SurfaceTextureListener´ are technically optional in this case, but very recommended. The ´StreamingRecordObserver´ is used to be notified when a local stream has been started or ended. The ´TextureView.SurfaceTextureListener´ is used to be notified when the custom ´AutoFitTextureView´ is prepared to host the camera output.

```java
public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener, StreamingRecordObserver
    ...
```

The XML for this activity must include an `AutoFitTextureView´ if we want a preview of the camera output

```xml
    ...
    <net.verdx.libstreaming.gui.AutoFitTextureView
        android:id="@+id/textureView" android:layout_width="match_parent"
        android:layout_height="match_parent" />
    ...
```

The `onCreate´ method in this Activity or Fragment should include all of these initializations.

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

In the Activity or Fragment's `onDestroy´ method, we have to make sure to stop the camera.

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

## How to receive audio and video streams from other devices

This example is extracted from [this simple app](https://github.com/verdx/libmultistreaming-examples/blob/master/README.md#example-2---receiver). This Activity could also be a Fragment.

The `StreamingRecordObserver´ is needed in this case to listen on when an external stream has been received or has ended. 

```java
public class MainActivity extends AppCompatActivity implements StreamingRecordObserver
    ...
```

The XML for this activity may include a `RecyclerView` to display the received streams. All the needed classes to go with it are implemented in the library.

```xml
    ...
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/streamsList"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>
    ...
```

The `onCreate´ method in this Activity or Fragment should include all of these initializations.

```java
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        ...

        /*
        Initialize the RecyclerView with an adapter and an array list
         */
        streamList = new ArrayList<>();
        RecyclerView streamsListView = this.findViewById(R.id.streamsList);
        streamsListView.setLayoutManager(new LinearLayoutManager(this));
        addDefaultItemList();
        arrayAdapter = new StreamListAdapter(this, streamList, this);
        streamsListView.setAdapter(arrayAdapter);

        /*
        Initialize the StreamingRecord singleton and add this activity as an observer
         */
        StreamingRecord.getInstance().addObserver(this);

        /*
        Initialize the ViewModel and observe the network status
         */
        mViewModel = new DefaultViewModel(this.getApplication());
        mViewModel.isNetworkAvailable().observe(this, (Observer<Boolean>) aBoolean -> {
            isNetworkAvailable = aBoolean;
            mStatusTextView.setText(getDeviceStatus());
            if(isNetworkAvailable){
                mViewModel.initNetwork();
            }
        });
    }
```

From the implemented methods from the `StreamingRecordObserver´, we only need to implement two, the rest can be empty in this case. We will add the received streams to the list when they are received and remove them when they are finished. We can also set the downloading state if we are using the default `StreamDetail`. If you want to remove some element you'll have to override a new `StreamDetail`

```java
        @Override
    public void onStreamingAvailable(Streaming streaming, boolean bAllowDispatch) {
        final String path = streaming.getUUID().toString();
        this.runOnUiThread(() -> updateList(true,
                path,
                streaming.getName(),
                streaming.getReceiveSession().getDestinationAddress().toString(),
                streaming.getReceiveSession().getDestinationPort(),
                streaming.isDownloading()));
    }

    @Override
    public void onStreamingUnavailable(Streaming streaming) {
        final String path = streaming.getUUID().toString();
        this.runOnUiThread(() -> updateList(false,
                path,
                streaming.getName(),
                streaming.getReceiveSession().getDestinationAddress().toString(),
                streaming.getReceiveSession().getDestinationPort(),
                streaming.isDownloading()));
    }

    @Override
    public void onStreamingDownloadStateChanged(Streaming streaming, boolean bIsDownloading) {
        final String path = streaming.getUUID().toString();
        this.runOnUiThread(() -> setStreamDownload(path, bIsDownloading));
    }
```

To update the list you will have to create a `StreamDetail` and add it to the list. The same for removing it. Finally, you will have to set the list to the adapter.
```java
StreamDetail detail = new StreamDetail(uuid, name, ip, port, download);
streamList.add(detail);
streamList.remove(detail);
arrayAdapter.setStreamsData(streamList);
```

