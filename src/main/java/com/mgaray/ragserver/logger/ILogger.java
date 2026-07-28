package com.mgaray.ragserver.logger;

import java.util.Map;

public interface ILogger {

    void log(String message);
    void log(String message, Map<String ,Object> objectMap);
    void log(String message, Object objectMap);
    void error(String message);
    void error(String message, Exception e);

}
