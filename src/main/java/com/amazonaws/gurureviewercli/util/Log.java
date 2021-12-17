package com.amazonaws.gurureviewercli.util;

import org.beryx.textio.TextTerminal;
import org.beryx.textio.system.SystemTextTerminal;

public final class Log {

    private static final String TEXT_RESET = "\u001B[0m";
    private static final String TEXT_BLACK = "\u001B[30m";
    private static final String TEXT_RED = "\u001B[31m";
    private static final String TEXT_GREEN = "\u001B[32m";
    private static final String TEXT_YELLOW = "\u001B[33m";
    private static final String TEXT_BLUE = "\u001B[34m";
    private static final String TEXT_PURPLE = "\u001B[35m";
    private static final String TEXT_CYAN = "\u001B[36m";
    private static final String TEXT_WHITE = "\u001B[37m";

    private static final String AWS_URL_PREFIX = "https://console.aws.amazon.com/codeguru/reviewer";

    // can be overriden
    private static TextTerminal terminal = new SystemTextTerminal();

    public static void setTerminal(final TextTerminal t) {
        terminal = t;
    }

    public static void print(final String format, final Object... args) {
        terminal.printf(format, args);
    }

    public static void println(final String format, final Object... args) {
        terminal.printf(format + "%n", args);
    }

    public static void info(final String format, final Object... args) {
        terminal.printf(TEXT_GREEN + format + TEXT_RESET + "%n", args);
    }

    public static void warn(final String format, final Object... args) {
        terminal.printf(TEXT_YELLOW + format + TEXT_RESET + "%n", args);
    }

    public static void error(final String format, final Object... args) {
        terminal.printf(TEXT_RED + format + TEXT_RESET + "%n", args);
    }

    public static void awsUrl(final String format, final Object... args) {
        terminal.printf(TEXT_CYAN + AWS_URL_PREFIX + format + TEXT_RESET + "%n", args);
    }

    public static void error(final Throwable t) {
        terminal.println(TEXT_RED + t.getMessage() + TEXT_RESET);
    }

    private Log() {
        // do not initialize
    }
}
