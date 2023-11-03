package d2d.testing.streaming.threads.workers;

import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import d2d.testing.streaming.threads.selectors.RTSPServerSelector;
import d2d.testing.streaming.Streaming;
import d2d.testing.streaming.StreamingRecord;
import d2d.testing.streaming.packets.DataReceived;
import d2d.testing.streaming.rtsp.RtspRequest;
import d2d.testing.streaming.rtsp.RtspResponse;
import d2d.testing.streaming.rtsp.UriParser;
import d2d.testing.streaming.sessions.RebroadcastSession;
import d2d.testing.streaming.sessions.ReceiveSession;
import d2d.testing.streaming.sessions.Session;
import d2d.testing.streaming.sessions.SessionBuilder;
import d2d.testing.streaming.sessions.TrackInfo;
import d2d.testing.streaming.utils.Logger;


/**
 * Esta clase es la encargada de tratar los datos de tipo RTSP del servidor.
 * La funcion principal es parsePackets(). La superclase recibe los bytes del selector, que ha enviado un cliente, y llama a esta funcion reimplementada para procesarlos.
 * La funcion parsea los bytes en un objeto RTSPRequest, llama a la funcion processRequest() que modifica el estado del servidor y crea una
 * RTSPResponse para devolver al selector y que la envie al cliente.
 *
 * RTSP define dos modalidades de comunicacion: Reproduccion y Publicacion de un streaming.
 * 1-Publicacion: El cliente activamente solicita al servidor que acepte su streaming y lo distribuya a otros clientes.
 *      -Primero el cliente envia un mensaje ANNOUNCE, describiendo los canales multimedia que ofrece. El servidor contesta con un ACK OK.
 *      -Luego el cliente envia un mensaje SETUP por cada canal multimedia (Audio, video), en el que principalmente se establecen los puertos que se van a utilizar.
 *      El servidor contesta con ACK OK.
 *      -Por ultimo el cliente envia un mensaje RECORD, indicando que empieza el streaming.
 * 2-Reproduccion: El cliente solicita al servidor un streaming. Este streaming puede ser producido por la camara del propio dispositivo servidor o
 * por otro cliente que publique el streaming con la modalidad anterior.
 *      -Primero el cliente envia un mensaje DESCRIBE, en el que en funcion de la uri que envie, el servidor contesta con informacion del streaming asociado
 *      -Luego el cliente envia un mensaje SETUP por cada canal en el que este interesado (Audio y video) y especifica como va a ser la transmision.
 *      -Por ultimo el cliente envia un mensaje PLAY, indicando al servidor que empiece a enviar.
 * El mensaje TEARDOWN se envia desde cualquiera de los extremos para finalizar el streaming.
 * El mensaje PAUSE deberia parar el envio de datos.
 *
 * En la clase RTSPServer de libstreaming solo esta implementada la modalidad 2, y solo siendo posible la transmision desde la camara del servidor.
 * En esta clase se implementan las dos modalidades, aunque necesitan unos retoques.
 *
 * Las clases Session representan una transmision. Los del tfg añadieron RebroadcastSession y RecieveSession identificando las transmisiones
 * del tipo, reproduccion de transmision proveniente de otro cliente y la de la modalidad de publicacion respectivamente.
 * TODO En principio se podrian juntar porque lo unico que cambia entre ellas es el origen y el destino. Hay que estudiarlo para el prototipo final.
 * TODO Para hacer la prueba de wifiaware en principio podemos dejarlo como esta
 *
 * Las sesiones se crean y configuran en DESCRIBE y se comienza el stream en SETUP. Yo creo que se deberian configurar en SETUP y comenzar en PLAY.
 * La funcionalidad de pausa no esta.
 * TODO Estudiar si mover las sesiones de metodo
 *
 * Falta estudiar y describir la modalidad de publicacion
 */
public class RTSPServerWorker extends AbstractWorker {

    private static final String TAG = "RTSPServerWorker";
    // RTSP Server Name
    public static String SERVER_NAME = "D2D RTSP Server";


    /** Expresion regular que encaja con la cabecera de todos los mensajes RSTP: "Tipo_de_operacion(ANNOUNCE,PLAY, etc) uri_del_stream(rtsp://...) RTSP"
     * (\\w+) encaja con una cadena de una o mas letras, numeros y _. Deberia ser ([A-Z]+)
     * (\\S+) encaja con una cadena de uno o mas caracteres que no sean espacios*/
    // Parse method & uri
    public static final Pattern regexMethod = Pattern.compile("(\\w+) (\\S+) RTSP",Pattern.CASE_INSENSITIVE);

    // Parse the uri
    public static final Pattern regexUrlMethod = Pattern.compile("rtsp://(\\S+)/(\\S+)",Pattern.CASE_INSENSITIVE);

    // Parse the uri
    public static final Pattern regexUrlMethodAux = Pattern.compile("rtsp://(\\S+)/(\\S+)/(\\S+)",Pattern.CASE_INSENSITIVE);

    /** Expresion regular para obtener los argumentos que vienen detras de la cabecera como:
     * Csec: 3
     * Session: 543545
     * */
    // Parse a request header
    public static final Pattern rexegHeader = Pattern.compile("(\\S+):(.+)",Pattern.CASE_INSENSITIVE);

    protected HashMap<SelectableChannel, Session> mSessions = new HashMap<>();
    protected HashMap<SelectableChannel, Map<UUID, Streaming>> mServerSessions = new HashMap<>();
    protected HashMap<SelectableChannel, RebroadcastSession> mRebroadcastSessions = new HashMap<>();
    protected HashMap<UUID, List<RebroadcastSession>> mRebroadcastSessionsUUIDIndex = new HashMap<>();


    /** Credentials for Basic Auth */
    private final String mUsername;
    private final String mPassword;

    private final RTSPServerSelector mServerSelector;

    public RTSPServerWorker(String username, String password, RTSPServerSelector serverSelector) {
        super(serverSelector);
        this.mUsername = username;
        this.mPassword = password;
        this.mServerSelector = serverSelector;
    }

    public RtspResponse processRequest(RtspRequest request, SelectableChannel channel) throws IllegalStateException, IOException {
        Session requestSession = mSessions.get(channel);
        ReceiveSession receiveSession = null;
        Map<UUID, Streaming> streamings = mServerSessions.get(channel);
        if(streamings == null){
            streamings = new HashMap<>();
            mServerSessions.put(channel, streamings);
        }
        else if(!request.path.isEmpty()){

            Streaming streaming = streamings.get(UUID.fromString(request.path));
            if(streaming != null){
                receiveSession = streaming.getReceiveSession();
            }
        }
        RebroadcastSession rebroadcastSession = mRebroadcastSessions.get(channel);
        RtspResponse response = new RtspResponse(request);


        //Ask for authorization unless this is an OPTIONS request
        if(!isAuthorized(request) && !request.method.equalsIgnoreCase("OPTIONS"))
        {
            response.attributes = "WWW-Authenticate: Basic realm=\""+SERVER_NAME+"\"\r\n";
            response.status = RtspResponse.STATUS_UNAUTHORIZED;
        }
        else
        {
            switch (request.method) {
                case "OPTIONS":
                    response.status = RtspResponse.STATUS_OK;
                    response.attributes = "Public: DESCRIBE,ANNOUNCE,SETUP,PLAY,RECORD,PAUSE,TEARDOWN\r\n";
                    break;
                case "DESCRIBE":
                    return DESCRIBE(request, channel);
                case "ANNOUNCE":
                    return ANNOUNCE(request, channel);
                case "SETUP":
                    if(requestSession != null) {
                        return SETUP(request, requestSession);
                    } else if(receiveSession != null) {
                        return SETUP(request, receiveSession);
                    } else if(rebroadcastSession != null) {
                        return SETUP(request, rebroadcastSession);
                    } else
                        response.status = RtspResponse.STATUS_BAD_REQUEST;
                    break;
                case "PLAY":
                    if(requestSession != null)
                        return PLAY(requestSession, channel);
                    else if(rebroadcastSession != null)
                        return PLAY(rebroadcastSession, channel);
                    else
                        response.status = RtspResponse.STATUS_BAD_REQUEST;
                    break;
                case "RECORD":
                    return RECORD(receiveSession, channel);
                case "PAUSE":
                    return PAUSE();
                case "TEARDOWN":
                    if(requestSession != null) {
                        return TEARDOWN(requestSession, channel);
                    } else if(receiveSession != null) {
                        return TEARDOWN(receiveSession, channel);
                    } else if(rebroadcastSession != null) {
                        return TEARDOWN(rebroadcastSession, channel);
                    } else
                        response.status = RtspResponse.STATUS_BAD_REQUEST;
                    break;
                default:
                    Logger.e("Command unknown: " + request);
                    response.status = RtspResponse.STATUS_BAD_REQUEST;
            }
        }
        return response;

    }
    // DESCRIBE Implementation for live Sessions and RebroadcastSessions...
    private RtspResponse DESCRIBE(RtspRequest request, SelectableChannel channel) throws IOException {
        RtspResponse response = new RtspResponse();
        Socket socket = ((SocketChannel) channel).socket();
        // Parse the requested URI and configure the session


        UUID localStreamingUUID = StreamingRecord.getInstance().getLocalStreamingUUID();
        boolean isLocalStream = localStreamingUUID != null && request.path.equals(localStreamingUUID.toString());

        // Si no es stream local es rebroadcast
        if(!isLocalStream) {
            //if es una session para hacer play a un stream de rebroadcast entonces
            try {
                RebroadcastSession session = handleRebroadcastRequest(request.path, socket);
                session.setRtspChannel(channel);
                mRebroadcastSessions.put(channel, session);
                UUID sessionUUID = UUID.fromString(request.path);
                List<RebroadcastSession> rebroadcastSessionList = mRebroadcastSessionsUUIDIndex.get(sessionUUID);
                if(rebroadcastSessionList == null){
                    rebroadcastSessionList = new ArrayList<>();
                    mRebroadcastSessionsUUIDIndex.put(sessionUUID, rebroadcastSessionList);
                }
                rebroadcastSessionList.add(session);

                // If no exception has been thrown, we reply with OK
                response.content = session.getSessionDescription();
                response.attributes = "Content-Base: " + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() + "/\r\n" +
                        "Content-Type: application/sdp\r\n";

                response.status = RtspResponse.STATUS_OK;
            } catch (IllegalArgumentException e) {
                response.status = RtspResponse.STATUS_BAD_REQUEST;
                return response;
            }
        } else {

            String strLocalStreamName = StreamingRecord.getInstance().getLocalStreamingName();
            SessionBuilder builder = StreamingRecord.getInstance().getLocalStreamingBuilder();
            if(builder == null){
                response.status = RtspResponse.STATUS_NOT_FOUND;
                return response;
            }
            Session mLocalStreamingSession = builder.build();
            mLocalStreamingSession.setStreamUUID(localStreamingUUID);
            mLocalStreamingSession.setNameStreaming(strLocalStreamName);
            mLocalStreamingSession.setDestinationAddress(socket.getInetAddress(), false);
            mLocalStreamingSession.setDestinationPort(socket.getPort());
            mLocalStreamingSession.setOriginAddress(socket.getLocalAddress(), false);

            mSessions.put(channel, mLocalStreamingSession);
            mLocalStreamingSession.syncConfigure();

            response.content = mLocalStreamingSession.getSessionDescription();
            response.attributes = "Content-Base: " + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() + "/\r\n" +
                    "Content-Type: application/sdp\r\n";

            // If no exception has been thrown, we reply with OK
            response.status = RtspResponse.STATUS_OK;
        }

        return response;
    }

    // ANNOUNCE Implementation for ServerSessions...
    private RtspResponse ANNOUNCE(RtspRequest request, SelectableChannel channel) throws IOException {
        RtspResponse response = new RtspResponse();
        Socket socket = ((SocketChannel) channel).socket();

        if(request.path.equals("")) {
            response.status = RtspResponse.STATUS_BAD_REQUEST;
        }

        // Parse the requested URI and configure the session
        ReceiveSession session = handleServerRequest(request, socket);
        session.setReceiveNet(mServerSelector.getChannelNetwork(channel));

        UUID streamUUID = UUID.fromString(request.path);

        mServerSessions.get(channel).put(streamUUID, new Streaming(streamUUID, session.getStreamingName(), session));
        response.attributes = "Content-Base: " + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() + "/\r\n" +
                              "Content-Type: application/sdp\r\n" +
                              "Session: " + session.getSessionID() + ";timeout=" + session.getTimeout() +"\r\n";

        // If no exception has been thrown, we reply with OK
        response.status = RtspResponse.STATUS_OK;
        return response;
    }

    // SETUP Implementation for live Sessions...
    private RtspResponse SETUP(RtspRequest request, Session session) throws IOException {
        RtspResponse response = new RtspResponse();
        Pattern p;
        Matcher m;
        int p2, p1, ssrc, trackId, srcPorts[];

        if (session== null) {
            response.status = RtspResponse.STATUS_BAD_REQUEST;
            return response;
        }

        p = Pattern.compile("trackID=(\\w+)", Pattern.CASE_INSENSITIVE);
        m = p.matcher(request.uri);

        if (!m.find()) {
            response.status = RtspResponse.STATUS_BAD_REQUEST;
            return response;
        }

        trackId = Integer.parseInt(m.group(1));

        if (!session.trackExists(trackId)) {
            response.status = RtspResponse.STATUS_NOT_FOUND;
            return response;
        }

        p = Pattern.compile("client_port=(\\d+)(?:-(\\d+))?", Pattern.CASE_INSENSITIVE);
        m = p.matcher(request.headers.get("transport"));

        if (!m.find()) {
            int[] ports = session.getTrack(trackId).getDestinationPorts();
            p1 = ports[0];
            p2 = ports[1];
        } else {
            p1 = Integer.parseInt(m.group(1));
            if (m.group(2) == null) {
                p2 = p1+1;
            } else {
                p2 = Integer.parseInt(m.group(2));
            }

            session.getTrack(trackId).setDestinationPorts(p1, p2);
        }

        ssrc = session.getTrack(trackId).getSSRC();
        srcPorts = session.getTrack(trackId).getLocalPorts();

        session.syncStart(trackId);

        response.attributes = "Transport: RTP/AVP/UDP;" + (session.getDestinationAddress().isMulticastAddress() ? "multicast" : "unicast") +
                ";destination=" + session.getDestinationAddress() +
                ";client_port=" + p1 + "-" + p2 +
                ";server_port=" + srcPorts[0] + "-" + srcPorts[1] +
                //";ssrc=" + Integer.toHexString(ssrc) +
                ";mode=play\r\n" +
                "Session: " + session.getSessionID() + "\r\n" +
                "Cache-Control: no-cache\r\n";

        // If no exception has been thrown, we reply with OK
        response.status = RtspResponse.STATUS_OK;

        return response;
    }

    // SETUP Implementation for ServerSessions...
    private RtspResponse SETUP(RtspRequest request, ReceiveSession session) throws IOException {
        RtspResponse response = new RtspResponse();
        Pattern p;
        Matcher m;
        int p2, p1, ssrc, trackId, srcPorts[];

        if (session== null) {
            response.status = RtspResponse.STATUS_BAD_REQUEST;
            return response;
        }

        p = Pattern.compile("trackID=(\\w+)", Pattern.CASE_INSENSITIVE);
        m = p.matcher(request.uri);

        if (!m.find()) {
            response.status = RtspResponse.STATUS_BAD_REQUEST;
            return response;
        }

        trackId = Integer.parseInt(m.group(1));
        if(!session.trackExists(trackId)) {
            response.status = RtspResponse.STATUS_BAD_REQUEST;
            return response;
        }
        TrackInfo trackInfo = session.getTrack(trackId);

        p = Pattern.compile("client_port=(\\d+)(?:-(\\d+))?", Pattern.CASE_INSENSITIVE);
        m = p.matcher(request.headers.get("transport"));

        if (!m.find()) {
            int[] ports = trackInfo.getRemotePorts();
            p1 = ports[0];
            p2 = ports[1];
        } else {
            p1 = Integer.parseInt(m.group(1));
            if (m.group(2) == null) {
                p2 = p1+1;
            } else {
                p2 = Integer.parseInt(m.group(2));
            }

            trackInfo.setRemotePorts(p1, p2);
        }

        srcPorts = trackInfo.getLocalPorts();
        trackInfo.setLocalAddress(session.getDestinationAddress());
        trackInfo.startServer(session.getReceiveNet());

        response.attributes = "Transport: RTP/AVP/UDP;" + (session.getDestinationAddress().isMulticastAddress() ? "multicast" : "unicast") +
                ";destination=" + session.getDestinationAddress().getHostAddress() +
                ";client_port=" + p1 + "-" + p2 +
                ";server_port=" + srcPorts[0] + "-" + srcPorts[1] +
                ";mode=receive\r\n" +
                "Session: " + session.getSessionID() + "\r\n" +
                "Cache-Control: no-cache\r\n";
        response.status = RtspResponse.STATUS_OK;

        return response;
    }

    // SETUP Implementation for RebroadcastSessions...
    private RtspResponse SETUP(RtspRequest request, RebroadcastSession session) throws IOException {
        RtspResponse response = new RtspResponse();
        Pattern p;
        Matcher m;
        int p2, p1, ssrc, trackId, srcPorts[];

        if (session== null) {
            response.status = RtspResponse.STATUS_BAD_REQUEST;
            return response;
        }

        p = Pattern.compile("trackID=(\\w+)", Pattern.CASE_INSENSITIVE);
        m = p.matcher(request.uri);

        if (!m.find()) {
            response.status = RtspResponse.STATUS_BAD_REQUEST;
            return response;
        }

        trackId = Integer.parseInt(m.group(1));
        if(!session.serverTrackExists(trackId)) {
            response.status = RtspResponse.STATUS_BAD_REQUEST;
            return response;
        }
        RebroadcastSession.RebroadcastTrackInfo rebroadcastTrackInfo = session.getRebroadcastTrack(trackId);

        p = Pattern.compile("client_port=(\\d+)(?:-(\\d+))?", Pattern.CASE_INSENSITIVE);
        m = p.matcher(request.headers.get("transport"));

        if (!m.find()) {
            int[] ports = rebroadcastTrackInfo.getRemotePorts();
            p1 = ports[0];
            p2 = ports[1];
        } else {
            p1 = Integer.parseInt(m.group(1));
            if (m.group(2) == null) {
                p2 = p1+1;
            } else {
                p2 = Integer.parseInt(m.group(2));
            }
            rebroadcastTrackInfo.setRemotePorts(p1, p2);
        }

        srcPorts = session.getServerTrack(trackId).getLocalPorts();
        session.startTrack(trackId);

        response.attributes = "Transport: RTP/AVP/UDP;" + (session.getDestination().isMulticastAddress() ? "multicast" : "unicast") +
                ";destination=" + session.getDestination() +
                ";client_port=" + p1 + "-" + p2 +
                ";server_port=" + srcPorts[0] + "-" + srcPorts[1] +
                //TODO DO WE NEED SSRC? Number used as identifier of the RTP source
                // if so we would need to recover it IN SETUP_RECEIVE AND THEN RESEND HERE
                //";ssrc=" + trackInfo.getSSRCHex() +
                ";mode=play\r\n" +
                "Session: " + session.getSessionID() + "\r\n" +
                "Cache-Control: no-cache\r\n";

        // If no exception has been thrown, we reply with OK
        response.status = RtspResponse.STATUS_OK;

        return response;
    }

    // PLAY Implementation for live Sessions...
    private RtspResponse PLAY(Session requestSession, SelectableChannel channel) throws IOException {
        RtspResponse response = new RtspResponse();
        Socket requestSocket = ((SocketChannel) channel).socket();
        String url = requestSocket.getLocalAddress().getHostAddress() + ":" + requestSocket.getLocalPort();

        String requestAttributes = "RTP-Info: ";
        if (requestSession.trackExists(0))
            requestAttributes += "url=rtsp://" + url + "/trackID=" + 0 + ";seq=0,";
        if (requestSession.trackExists(1))
            requestAttributes += "url=rtsp://" + url + "/trackID=" + 1 + ";seq=0,";
        response.attributes = requestAttributes.substring(0, requestAttributes.length() - 1)
                            + "\r\nSession: " + requestSession.getSessionID() +"\r\n";

        // If no exception has been thrown, we reply with OK
        response.status = RtspResponse.STATUS_OK;

        return response;
    }

    // PLAY Implementation for RebroadcastSessions...
    private RtspResponse PLAY(RebroadcastSession rebroadcastSession, SelectableChannel channel) {
        RtspResponse response = new RtspResponse();
        Socket requestSocket = ((SocketChannel) channel).socket();
        String url = requestSocket.getLocalAddress().getHostAddress() + ":" + requestSocket.getLocalPort();

        String requestAttributes = "RTP-Info: ";
        if (rebroadcastSession.serverTrackExists(0))
            requestAttributes += "url=rtsp://" + url + "/trackID=" + 0 + ";seq=0,";
        if (rebroadcastSession.serverTrackExists(1))
            requestAttributes += "url=rtsp://" + url + "/trackID=" + 1 + ";seq=0,";
        response.attributes = requestAttributes.substring(0, requestAttributes.length() - 1)
                + "\r\nSession: " + rebroadcastSession.getSessionID() +"\r\n";

        // If no exception has been thrown, we reply with OK
        response.status = RtspResponse.STATUS_OK;

        return response;
    }

    // RECORD Implementation for ServerSessions...
    private RtspResponse RECORD(ReceiveSession receiveSession, SelectableChannel channel) {
        RtspResponse response = new RtspResponse();
        response.attributes = "Session: " + receiveSession.getSessionID() + ";timeout=" + receiveSession.getTimeout() +"\r\n";
        response.status = RtspResponse.STATUS_OK;


        UUID streamUUID = UUID.fromString(receiveSession.getPath());

        if(StreamingRecord.getInstance().streamingExist(streamUUID)) {
            response.status = RtspResponse.STATUS_FORBIDDEN;
            receiveSession.stop();
            mServerSessions.get(channel).remove(UUID.fromString(receiveSession.getPath()));
            return response;
        }

        StreamingRecord.getInstance().addStreaming(mServerSessions.get(channel).get(UUID.fromString(receiveSession.getPath())), true);

        return response;
    }

    // TEARDOWN Implementation for live Sessions...
    private RtspResponse TEARDOWN(Session session, SelectableChannel channel) {
        RtspResponse response = new RtspResponse();
        session.stop();
        mSessions.remove(channel);
        response.status = RtspResponse.STATUS_OK;
        return response;
    }

    // TEARDOWN Implementation for RebroadcastSessions...
    private RtspResponse TEARDOWN(RebroadcastSession session, SelectableChannel channel) {
        RtspResponse response = new RtspResponse();

        mRebroadcastSessions.remove(channel);
        List<RebroadcastSession> rebList = mRebroadcastSessionsUUIDIndex.get(UUID.fromString(session.getPath()));
        if(rebList != null) rebList.remove(session);

        onRebroadcastSessionDisconnected(session);
        response.status = RtspResponse.STATUS_OK;
        return response;
    }

    // TEARDOWN Implementation for ServerSessions...
    private RtspResponse TEARDOWN(ReceiveSession session, SelectableChannel channel) {
        RtspResponse response = new RtspResponse();
        Streaming streaming = mServerSessions.get(channel).remove(UUID.fromString(session.getPath()));
        onReceiveSessionDisconnected(streaming);

        response.status = RtspResponse.STATUS_OK;
        return response;
    }

    private void sendTEARDOWN(SelectableChannel channel, String path, String strSessionID){
        Socket socket = ((SocketChannel) channel).socket();
        String request = "TEARDOWN rtsp://"+socket.getLocalAddress().toString()+":"+socket.getLocalPort()+"/"+path+" RTSP/1.0\r\n";
        request += "Session: " + strSessionID + "\r\n" +
                "Terminate-Reason: Internal-Error" + "\r\n" + "\r\n";
        mSelector.send(channel, request.getBytes());
    }

    private RtspResponse PAUSE() {
        RtspResponse response = new RtspResponse();
        response.status = RtspResponse.STATUS_OK;
        return response;
    }


    @Override
    protected void parsePackets(DataReceived dataReceived) {
        //TODO POSIBILIDAD DE QUE LOS PAQUETES SE QUEDEN ABIERTOS...!!! HAY QUE CONTROLAR
        RtspResponse response = new RtspResponse();
        RtspRequest request = new RtspRequest();
        String line = null;
        Matcher matcher;
        Matcher matcherAux;

        BufferedReader inputReader = new BufferedReader(new StringReader(new String(dataReceived.getData())));
        // Parsing request method & uri

        try {
            if ((line = inputReader.readLine())==null) {
                throw new SocketException("Client disconnected");
            }

            matcher = regexMethod.matcher(line);
            matcher.find();
            request.method = matcher.group(1);
            request.uri = matcher.group(2);

            matcher = regexUrlMethod.matcher(request.uri);
            matcherAux = regexUrlMethodAux.matcher(request.uri);
            /** Ellos han hecho que cuando el path es rtsp://xxx/ el cliente solicite el stream de la camara del servidor
             * y cuando es rtsp://xxx/yyy solicite el stream del cliente yyy que proporciona el servidor*/
            if(matcher.find()) {
                request.path = matcher.group(2);
                if(request.path.equals("trackID=0")||request.path.equals("trackID=1")) {
                    if(matcherAux.find()) request.path = matcherAux.group(2);
                }
            } else if(matcherAux.find()){
                request.path = matcherAux.group(2);
            }
            else request.path = "";
            Log.d(TAG, "path: " + request.path);

            // Parsing headers of the request
            while ( (line = inputReader.readLine()) != null && line.length()>3 && (matcher = rexegHeader.matcher(line)).find()) {
                request.headers.put(matcher.group(1).toLowerCase(Locale.US),matcher.group(2));
            }

            if((line = inputReader.readLine()) != null) {
                request.body = line;
                while ((line = inputReader.readLine()) != null) {
                    request.body += "\r\n" + line;
                }
            }

            // It's not an error, it's just easier to follow what's happening in logcat with the request in red
            Logger.e(request.method+" "+request.uri);
            inputReader.close();

            response = processRequest(request, dataReceived.getSocket());

        } catch (IOException e) {
            response.status = RtspResponse.STATUS_BAD_REQUEST;
            e.printStackTrace();
        } catch (IllegalStateException e) {
            response.status = RtspResponse.STATUS_BAD_REQUEST;
            Log.e(TAG, "illegal state with line" + line.toString());
            e.printStackTrace();
        }

        try {
            dataReceived.getSelector().send(dataReceived.getSocket(), response.build().getBytes());
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    /**
     * Check if the request is authorized
     * @param request
     * @return true or false
     */
    private boolean isAuthorized(RtspRequest request) {
        String auth = request.headers.get("authorization");
        if(mUsername == null || mPassword == null || mUsername.isEmpty())
            return true;

        if(auth != null && !auth.isEmpty())
        {
            String received = auth.substring(auth.lastIndexOf(" ")+1);
            String local = mUsername+":"+mPassword;
            String localEncoded = Base64.encodeToString(local.getBytes(),Base64.NO_WRAP);
            if(localEncoded.equals(received))
                return true;
        }

        return false;
    }

    /**
     * By default the RTSP uses {@link UriParser} to parse the URI requested by the client
     * but you can change that behavior by override this method.
     * @param uri The uri that the client has requested
     * @param client The socket associated to the client
     * @return A proper session
     */
    protected Session handleRequest(String uri, Socket client) throws IllegalStateException, IOException {
        Session session = UriParser.parse(uri);
        Log.d(TAG,"handleRequest: Origin" + client.getLocalAddress().getHostAddress());
        session.setOriginAddress(client.getLocalAddress(), true);
        if (session.getDestinationAddress()==null) {
            Log.d(TAG,"handleRequest: Destination" + client.getInetAddress().getHostAddress());
            session.setDestinationAddress(client.getInetAddress(), true);
        }
        return session;
    }

    protected ReceiveSession handleServerRequest(RtspRequest request, Socket client) throws IllegalStateException, IOException {
        ReceiveSession session = new ReceiveSession();
        BufferedReader reader = new BufferedReader(new StringReader(request.body));
        String line = null;

        final Pattern regexAudioDescription = Pattern.compile("m=audio (\\S+)",Pattern.CASE_INSENSITIVE);
        final Pattern regexVideoDescription = Pattern.compile("m=video (\\S+)",Pattern.CASE_INSENSITIVE);
        final Pattern pattern = Pattern.compile("s=(\\w+)", Pattern.CASE_INSENSITIVE);

        //Patrón del custom rtsp header
        final Pattern regexProofFile = Pattern.compile("a=proof:file=(.+)",Pattern.CASE_INSENSITIVE);
        final Pattern regexProofName = Pattern.compile("a=proof:name=(.+)",Pattern.CASE_INSENSITIVE);

        Matcher matcher;
        String streamName = "defaultName";

        while((line = reader.readLine()) != null && line.length()>0) {
            if(regexAudioDescription.matcher(line).find()){
                TrackInfo trackInfo = new TrackInfo();
                trackInfo.setSessionDescription(line +"\r\n"+ reader.readLine() +"\r\n"+ reader.readLine() +"\r\n");
                session.addAudioTrack(trackInfo);
            }

            if(regexVideoDescription.matcher(line).find()){
                TrackInfo trackInfo = new TrackInfo();
                trackInfo.setSessionDescription(line +"\r\n"+ reader.readLine() +"\r\n"+ reader.readLine() +"\r\n");
                session.addVideoTrack(trackInfo);
            }

            //*******************
            if(regexProofName.matcher(line).find()){
                int firstEqualsIndex = line.indexOf('=');
                int secondEqualsIndex = line.indexOf('=', firstEqualsIndex + 1);
                String filename = line.substring(secondEqualsIndex + 1);

                session.setProofFilename(filename);
            }

            if(regexProofFile.matcher(line).find()){
                int firstEqualsIndex = line.indexOf('=');
                int secondEqualsIndex = line.indexOf('=', firstEqualsIndex + 1);
                String fileBytes = line.substring(secondEqualsIndex + 1);


                if(fileBytes!=null){
                    // Decode the incoming data from Base64 to binary
                    byte[] decodedData = java.util.Base64.getDecoder().decode(fileBytes);

                    session.setProofArr(decodedData);

                    // Save the decoded data to a file with the .zip extension
//                    File outputFile = new File(ProofManager.getInstance().getDownloadDir(), ProofManager.getInstance().getFileName() + ".zip");
//                    if (!outputFile.exists()) {
//                        outputFile.createNewFile();
//                    }
//                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
//                        fos.write(decodedData);
//                    }
                }
            }

            matcher = pattern.matcher(line);
            if(matcher.find()){
                streamName = matcher.group(1);
            }
        }

        session.setStreamingName(streamName);
        Log.d(TAG,"StreamingName = " + streamName);

        Log.d(TAG,"handleServerRequest: Origin" + client.getInetAddress().getHostAddress());
        session.setOrigin(client.getInetAddress()); //CLIENTE
        Log.d(TAG,"handleRequest: Destination" + client.getLocalAddress().getHostAddress());
        session.setDestination(client.getLocalAddress()); //SERVIDOR
        session.setDestinationPort(client.getLocalPort()); //SERVIDOR

        session.setPath(request.path);
        return session;
    }

    protected RebroadcastSession handleRebroadcastRequest(String path, Socket client) {
        //Buscar la serverSession que corresponde al path
        RebroadcastSession session = new RebroadcastSession();

        List<Streaming> streamingsList = StreamingRecord.getInstance().getStreamings();
        ReceiveSession receiveSession = null;
        UUID requestedStream = UUID.fromString(path);
        for(Streaming streaming : streamingsList){
            if(streaming.getUUID().equals(requestedStream)){
                receiveSession = streaming.getReceiveSession();
            }
        }
        session.setServerSession(receiveSession);

        if(receiveSession == null) {
            throw new IllegalArgumentException();
        }

        session.setNameStreaming(receiveSession.getStreamingName());


        Log.d(TAG,"handleRequest: Origin" + client.getLocalAddress().getHostAddress());
        session.setOriginAddress(client.getLocalAddress(), false);

        Log.d(TAG,"handleRebroadcastRequest: Destination" + client.getInetAddress().getHostAddress());
        session.setDestinationAddress(client.getInetAddress(), false);

        return session;
    }

    /**
     * Cerramos las sesiones asociadas al socket
     * @param channel
     */
    public synchronized void onClientDisconnected(SelectableChannel channel) {
        Session streamingSession = mSessions.remove(channel);
        if(streamingSession != null) {
            onSessionDisconnected(streamingSession);
        }

        Map<UUID, Streaming> streamings = mServerSessions.remove(channel);
        if(streamings != null){
            for(Streaming streaming : streamings.values()){
                onReceiveSessionDisconnected(streaming);
            }
            streamings.clear();
        }

        RebroadcastSession rebroadcastSession = mRebroadcastSessions.remove(channel);
        if(rebroadcastSession != null) {
            List<RebroadcastSession> rebList = mRebroadcastSessionsUUIDIndex.get(UUID.fromString(rebroadcastSession.getPath()));
            if(rebList != null) rebList.remove(rebroadcastSession);
            onRebroadcastSessionDisconnected(rebroadcastSession);
        }
    }

    private void onSessionDisconnected(Session streamingSession){
        if (streamingSession.isStreaming()) {
            streamingSession.syncStop();
        }
        streamingSession.release();
    }

    private void onReceiveSessionDisconnected(Streaming streaming){
        ReceiveSession receiveSession = streaming.getReceiveSession();
        if(receiveSession != null) {
            StreamingRecord.getInstance().removeStreaming(streaming.getUUID());

            List<RebroadcastSession> rebList = mRebroadcastSessionsUUIDIndex.remove(streaming.getUUID());
            if(rebList != null){
                for(RebroadcastSession rebroadcastSession : rebList){
                    sendTEARDOWN(rebroadcastSession.getRtspChannel(), rebroadcastSession.getPath(), rebroadcastSession.getSessionID());
                    mRebroadcastSessions.remove(rebroadcastSession.getRtspChannel());
                    onRebroadcastSessionDisconnected(rebroadcastSession);
                }
            }

            receiveSession.stop();
            receiveSession.release();
        }
    }

    private void onRebroadcastSessionDisconnected(RebroadcastSession rebroadcastSession){
        rebroadcastSession.stop();
    }


    @Override
    protected synchronized void onWorkerRelease() {
        for(Session session : mSessions.values()){
            onSessionDisconnected(session);
        }
        mSessions.clear();
        for(Map<UUID, Streaming> streamings : mServerSessions.values()){
            for(Streaming streaming : streamings.values()){
                onReceiveSessionDisconnected(streaming);
            }
            streamings.clear();
        }
        mServerSessions.clear();
        for(RebroadcastSession rebroadcastSession : mRebroadcastSessions.values()){
            onRebroadcastSessionDisconnected(rebroadcastSession);
        }
        mRebroadcastSessions.clear();
        mRebroadcastSessionsUUIDIndex.clear();
    }
}
