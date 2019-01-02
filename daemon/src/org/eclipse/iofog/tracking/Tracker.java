package org.eclipse.iofog.tracking;

import org.eclipse.iofog.IOFogModule;
import org.eclipse.iofog.utils.Constants;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.xml.bind.DatatypeConverter;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class Tracker implements IOFogModule {
    private final String MODULE_NAME = "Tracker";
    private static Tracker instance = null;
    public static Tracker getInstance() {
        if (instance == null) {
            synchronized (Tracker.class) {
                if (instance == null)
                    instance = new Tracker();
            }
        }
        return instance;
    }

    private String id;
    Timer loggerTimer = null;
    Timer senderTimer = null;
    @Override
    public void start() throws Exception {
        this.id = getUniqueTrackingId();

        loggerTimer = new Timer();
        TimeLoggerTask timeLoggerTask = new TimeLoggerTask();
        loggerTimer.schedule(timeLoggerTask, timeLoggerTask.getTimeTrackingTimeoutMin() * 1000, timeLoggerTask.getTimeTrackingTimeoutMin() * 1000);

        senderTimer = new Timer();
        SenderTask senderTask = new SenderTask();
        senderTimer.schedule(senderTask, senderTask.getSendTimeoutMin() * 1000, senderTask.getSendTimeoutMin() * 1000);
    }

    @Override
    public int getModuleIndex() {
        return Constants.TRACKER;
    }

    @Override
    public String getModuleName() {
        return MODULE_NAME;
    }

    private String getUniqueTrackingId() throws NoSuchAlgorithmException {
        String id;
        try {
            List<String> macs = getAllMacs();
            StringBuffer stringBuffer = new StringBuffer();
            macs.forEach(s -> stringBuffer.append(s + '-'));

            id = stringBuffer.toString();
        } catch (SocketException e) {
            id = "random_" + generateRandomString(32);
        }

        MessageDigest md5 = MessageDigest.getInstance("md5");
        byte[] digest = md5.digest(id.getBytes());
        id = DatatypeConverter.printHexBinary(digest);

        return id;
    }

    private String generateRandomString(final int size) {

        StringBuffer randString = new StringBuffer();
        final String possible = "2346789bcdfghjkmnpqrtvwxyzBCDFGHJKLMNPQRTVWXYZ";
        Random random = new Random();

        for (int i = 0; i < size; i++) {
            randString.append(possible.charAt(random.nextInt(possible.length())));
        }

        return randString.toString();
    }

    private List<String> getAllMacs() throws SocketException {
        List<String> macs = new ArrayList<>();
        Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
        NetworkInterface inter;
        while (networks.hasMoreElements()) {
            inter = networks.nextElement();
            if (inter.isVirtual()) {
                continue;
            }
            byte[] mac = inter.getHardwareAddress();
            if (mac != null) {
                StringBuffer macStr = new StringBuffer();
                for (byte b : mac) {
                    String s = String.format("%02X", b);
                    macStr.append(s);
                }

                macs.add(macStr.toString());
            }
        }
        return macs;
    }

    public void handleEvent(TrackingEventType type, String value) {

        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        JsonObject eventVal = null;
        switch (type) {
            case TIME:
                eventVal = jsonObjectBuilder.add("deltaTime", value).build();
                break;
            case START:
                eventVal = jsonObjectBuilder.build();
                break;
            case ERROR:
                eventVal = jsonObjectBuilder.add("message", value).build();
                break;
            case CONFIG:
                eventVal = jsonObjectBuilder.add("updated_fields", value).build();
                break;
            case PROVISION:
                eventVal = jsonObjectBuilder.add("status", value).build();
                break;
            case MICROSERVICE:
                eventVal = jsonObjectBuilder.add("new_microservices", value).build();
                break;
            default:
                throw new IllegalArgumentException("unhandled event type");

        }
        TrackingEvent event = new TrackingEvent(this.id, new Date().getTime(), type, eventVal);
        TrackingEventsStorage.getInstance().pushEvent(event);
    }

    private class TimeLoggerTask extends TimerTask {
        private final int timeTrackingTimeoutMin = 1;
        private int iterations = 0;

        public int getTimeTrackingTimeoutMin() {
            return timeTrackingTimeoutMin;
        }

        @Override
        public boolean cancel() {
            return super.cancel();
        }

        @Override
        public long scheduledExecutionTime() {
            return super.scheduledExecutionTime();
        }

        @Override
        public void run() {
            iterations++;
            handleEvent(TrackingEventType.TIME, Long.toString(iterations * timeTrackingTimeoutMin));
        }
    }

    private class SenderTask extends TimerTask {
        private final int sendTimeoutMin = 2;

        public int getSendTimeoutMin() {
            return sendTimeoutMin;
        }

        @Override
        public boolean cancel() {
            return super.cancel();
        }

        @Override
        public long scheduledExecutionTime() {
            return super.scheduledExecutionTime();
        }

        @Override
        public void run() {
            List<TrackingEvent> events = TrackingEventsStorage.getInstance().popAllEvents();
            JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();

            events
                    .forEach(el -> {
                        jsonArrayBuilder.add(el.toJsonObject());
                    });

            //TODO send jsonArrayBuilder.buid() here

        }
    }
}
