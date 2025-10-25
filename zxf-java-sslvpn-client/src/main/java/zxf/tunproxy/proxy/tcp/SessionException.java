package zxf.tunproxy.proxy.tcp;

public abstract class SessionException extends Exception {


    public static class SessionEndException extends SessionException {

    }

    public static class SessionResetException extends SessionException {

    }
}
