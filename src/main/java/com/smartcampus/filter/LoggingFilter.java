package com.smartcampus.filter;

import jakarta.ws.rs.container.*;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.logging.Logger;

@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOGGER = Logger.getLogger(LoggingFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext req) throws IOException {
        LOGGER.info("[REQUEST]  " + req.getMethod() + " " + req.getUriInfo().getRequestUri());
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) throws IOException {
        LOGGER.info("[RESPONSE] " + req.getMethod() + " " +
                req.getUriInfo().getRequestUri() + " -> " + res.getStatus());
    }
}
