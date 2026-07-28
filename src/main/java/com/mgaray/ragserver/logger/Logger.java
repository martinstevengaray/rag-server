package com.mgaray.ragserver.logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.mgaray.ragserver.util.JsonUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.UUID;

public class Logger implements ILogger {

    private final LambdaLogger lambdaLogger;
    private final String idPrefix;

    public Logger() {
        this(systemOutLogger, null);
    }

    public Logger(Context context) {
        this(context.getLogger(), null);
    }

    private Logger(LambdaLogger lambdaLogger, String id) {
        this.lambdaLogger = lambdaLogger;
        this.idPrefix = (id == null) ? "" : id + ": ";
    }

    @Override
    public void log(String message) {
        this.lambdaLogger.log(idPrefix + message);
    }

    @Override
    public void log(String message, Map<String ,Object> objectMap) {
        this.lambdaLogger.log(idPrefix + message + " : " + JsonUtils.toJson(objectMap));
    }

    @Override
    public void log(String message, Object object) {
        this.lambdaLogger.log(idPrefix + message + " : " + JsonUtils.toJson(object));
    }

    @Override
    public void error(String message) {
        this.lambdaLogger.log(idPrefix + "ERROR: " + message);
    }

    @Override
    public void error(String message, Exception e) {
        String stackTrace = getStackTrace(e);
        error(message + " : " + stackTrace);
    }

    private static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static final SystemOutLogger systemOutLogger = new SystemOutLogger();
    private static class SystemOutLogger implements LambdaLogger {
        @Override
        public void log(String message) {
            System.out.println(message);
        }

        @Override
        public void log(byte[] message) {
            throw new UnsupportedOperationException();
        }

    }

}

