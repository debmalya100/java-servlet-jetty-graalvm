package com.example.sse;

import com.example.sse.servlet.SSEServlet;
import com.example.sse.servlet.ProductionHealthServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

/**
 * Ultra-simple Jetty server - PHP-style simplicity
 * No complex thread pools, no async complexity - just fast and simple
 */
public class JettySSEServer {
    private static final Logger logger = LoggerFactory.getLogger(JettySSEServer.class);
    private Server server;

    public JettySSEServer() {
        // Nothing complex - keep it simple
    }

    public void start() throws Exception {
        logger.info("🚀 Starting Simple SSE Server...");
        long startTime = System.currentTimeMillis();

        // Get port
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        // Create simple server - no complex configuration
        server = new Server(port);

        // Simple servlet context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");

        // Add servlets - minimal configuration
        addSimpleServlets(context);

        server.setHandler(context);
        server.setStopAtShutdown(true);

        try {
            server.start();
            long startupTime = System.currentTimeMillis() - startTime;
            logger.info("✅ Simple SSE Server started in {}ms on port {}", startupTime, port);
            logEndpoints(port);
            server.join();
        } catch (Exception e) {
            logger.error("Failed to start server", e);
            throw e;
        }
    }

    private void addSimpleServlets(ServletContextHandler context) {
        try {
            // Ultra-simple health check
            context.addServlet(new ServletHolder(new ProductionHealthServlet()), "/health");
            context.addServlet(new ServletHolder(new ProductionHealthServlet()), "/");

            // Main SSE servlet
            context.addServlet(new ServletHolder(new SSEServlet()), "/api/sse/*");

            logger.info("Simple servlets added successfully");

        } catch (Exception e) {
            logger.error("Failed to add servlets", e);
            throw new RuntimeException("Servlet setup failed", e);
        }
    }

    /**
     * Ultra-simple health check servlet
     */
    public static class SimpleHealthServlet extends jakarta.servlet.http.HttpServlet {
        @Override
        protected void doGet(jakarta.servlet.http.HttpServletRequest request,
                jakarta.servlet.http.HttpServletResponse response) throws IOException {

            response.setContentType("application/json");
            response.setStatus(200);
            response.getWriter().write("{\"status\":\"ok\",\"message\":\"Simple SSE Server Running\"}");
            response.getWriter().flush();
        }
    }

    private void logEndpoints(int port) {
        logger.info("=== SIMPLE SSE ENDPOINTS ===");
        logger.info("🏥 Health: http://localhost:{}/health", port);
        logger.info("🔄 SSE Main: http://localhost:{}/api/sse?token=...&type_id=...&type=...", port);
        logger.info("📊 SSE Status: http://localhost:{}/api/sse/session-status?session_id=...", port);
        logger.info("=============================");
    }

    public void stop() {
        if (server != null) {
            try {
                logger.info("Stopping Simple SSE Server...");
                server.stop();
                logger.info("Server stopped");
            } catch (Exception e) {
                logger.error("Error stopping server", e);
            }
        }
    }

    public static void main(String[] args) {
        try {
            JettySSEServer server = new JettySSEServer();
            server.start();
        } catch (Exception e) {
            logger.error("Failed to start Simple SSE Server", e);
            System.exit(1);
        }
    }
}