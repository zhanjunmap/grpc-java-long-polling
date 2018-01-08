package com.payneteasy.grpc.longpolling.server.servlet;

import com.google.common.base.Preconditions;
import com.payneteasy.grpc.longpolling.server.LongPollingServerTransport;
import com.payneteasy.grpc.longpolling.server.servlet.down.DownServletHandler;
import com.payneteasy.grpc.longpolling.server.servlet.registry.ITransportRegistry;
import com.payneteasy.grpc.longpolling.server.servlet.registry.TransportRegistryImpl;
import com.payneteasy.grpc.longpolling.server.servlet.tap.TapServletHandler;
import com.payneteasy.grpc.longpolling.server.servlet.unary.UnaryHandler;
import com.payneteasy.grpc.longpolling.server.servlet.up.UpServletHandler;
import io.grpc.internal.ServerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class LongPollingDispatcherServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(LongPollingDispatcherServlet.class);

    private final ServerListener     serverListener;
    private final UnaryHandler       unaryServlet;
    private final UpServletHandler   upServletHandler;
    private final DownServletHandler downServletHandler;
    private final TapServletHandler  tapServletHandler;

    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(10);

    public LongPollingDispatcherServlet(ServerListener aListener) {
        Preconditions.checkNotNull(aListener, "ServerListener must not be null");
        LongPollingServerTransport serverTransport   = new LongPollingServerTransport(EXECUTOR);
        ITransportRegistry transportRegistry = new TransportRegistryImpl(aListener, serverTransport);

        serverListener     = aListener;
        unaryServlet       = new UnaryHandler(aListener, serverTransport);
        upServletHandler   = new UpServletHandler(transportRegistry);
        downServletHandler = new DownServletHandler(transportRegistry);
        tapServletHandler  = new TapServletHandler(transportRegistry);
    }

    @Override
    protected void service(HttpServletRequest aRequest, HttpServletResponse aResponse) throws ServletException, IOException {
        MethodCall call;
        try {
            call = MethodCall.parse(aRequest.getPathInfo());
        } catch (Exception e) {
            LOG.error("Error while parsing " + aRequest.getRequestURL(), e);
            aResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        LOG.debug("Calling {} ...", call);

        try {
            switch (call.getType()) {
                case UNARY:
                    unaryServlet.handle(aRequest, call, aResponse);
                    break;

                case UP:
                    upServletHandler.handle(aRequest, call);
                    break;

                case DOWN:
                    downServletHandler.handle(call, aResponse);
                    break;

                case TAP:
                    tapServletHandler.handle(call, aRequest);
                    break;

                default:
                    LOG.error("Method {} not implemented yet", call.getType());
                    aResponse.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
                    break;
            }
        } catch (InterruptedException e) {
            LOG.error("Interrupted while processing " + call, e);
            aResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.error("Error while processing " + call, e);
            aResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void destroy() {
        LOG.trace("destroy()");
        serverListener.serverShutdown();
    }

}
