package com.payneteasy.grpc.longpolling.client.http;

import com.payneteasy.grpc.longpolling.client.util.ServerEndPoint;
import com.payneteasy.grpc.longpolling.common.MethodDirection;
import com.payneteasy.grpc.longpolling.common.SingleMessageProducer;
import com.payneteasy.grpc.longpolling.common.SlotSender;
import io.grpc.Status;
import io.grpc.internal.ClientStreamListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;

public class HttpClientTapping implements IHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(HttpClientTapping.class);

    private final ClientStreamListener              listener;
    private final URL                               url;
    private final SlotSender<SingleMessageProducer> slotSender;

    public HttpClientTapping(ClientStreamListener aListener, SlotSender<SingleMessageProducer> aSlotSender, ServerEndPoint aEndpoint) {
        listener   = aListener;
        url        = aEndpoint.createUrl(MethodDirection.TAP);
        slotSender = aSlotSender;
    }

    @Override
    public void sendMessage(InputStream aInputStream) {
        ErrorsTranslator errors = new ErrorsTranslator(LOG, listener, url);
        errors.tryCatch(() -> {
            HttpConnection http   = new HttpConnection(LOG, url);
            HttpStatus     status = http.doPost(aInputStream);

            if(status.wasNotOk()) {
                errors.abort(status);
                return;
            }

            http.fireMessagesContainerAvailable(slotSender);
        });
    }

    @Override
    public void cancelStream(Status aReason) {
        // can't cancel http connection once it sent via HTTPUrlConnection
    }


}
