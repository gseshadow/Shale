package com.shale.server.runtime;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

public final class CompositeServerSessionResolver implements ServerSessionResolver {
    private final List<ServerSessionResolver> resolvers;

    public CompositeServerSessionResolver(List<ServerSessionResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    @Override
    public ServerSessionContext resolve(HttpServletRequest request) {
        for (ServerSessionResolver resolver : resolvers) {
            ServerSessionContext context = resolver.resolve(request);
            if (context != null && context.authenticated()) {
                return context;
            }
        }
        return ServerSessionContext.unauthenticated();
    }
}
