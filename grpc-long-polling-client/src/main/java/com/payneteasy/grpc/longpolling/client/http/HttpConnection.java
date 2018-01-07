package com.payneteasy.grpc.longpolling.client.http;

import com.payneteasy.grpc.longpolling.common.MessagesContainer;
import com.payneteasy.grpc.longpolling.common.SingleMessageProducer;
import com.payneteasy.grpc.longpolling.common.SlotSender;
import com.payneteasy.tlv.HexUtil;
import io.grpc.Drainable;
import io.grpc.internal.IoUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpConnection {

    private final Logger            log;
    private final HttpURLConnection connection;

    public HttpConnection(Logger aLogger, URL aUrl) throws IOException {
        log        = aLogger;
        connection = (HttpURLConnection) aUrl.openConnection();
    }

//    public void fireMessageAvailable(ClientStreamListener aListener) throws IOException {
//        new Streams(log).messageAvailable(aListener, connection);
//    }

    public void fireMessagesContainerAvailable(SlotSender<SingleMessageProducer> aSlotSender) throws IOException {
        MessagesContainer messages = MessagesContainer.parse(connection.getInputStream());
        for (InputStream inputStream : messages.getInputs()) {
            aSlotSender.onSendMessage(SingleMessageProducer.readFully(getClass(), inputStream));
        }

    }

    public HttpStatus doPost(InputStream aInputStream) throws IOException {
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.connect();
        return sendMessage(aInputStream);
    }

    private HttpStatus sendMessage(InputStream aInputStream) throws IOException {
        OutputStream out = connection.getOutputStream();
        if(aInputStream instanceof Drainable && !log.isDebugEnabled()) {
            ((Drainable) aInputStream).drainTo(out);
        } else {
            byte[] outputBytes = IoUtils.toByteArray(aInputStream);
            log.debug("OUTPUT: {}", HexUtil.toFormattedHexString(outputBytes));
            out.write(outputBytes);
        }
        return new HttpStatus(connection.getResponseCode(), connection.getResponseMessage());
    }

}
