package es.us.isa.restest.util;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

public class LoggerStream extends OutputStream
{
    private final Logger logger;
    private final Level logLevel;
    private final OutputStream outputStream;

    public LoggerStream(Logger logger, Level logLevel, OutputStream outputStream)
    {
        super();

        this.logger = logger;
        this.logLevel = logLevel;
        this.outputStream = outputStream;
    }

    @Override
    public void write(byte[] b) throws IOException
    {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException
    {
        outputStream.write(b, off, len);
        String string = new String(b, off, len);
        if (!string.trim().isEmpty())
            logger.log(logLevel, string);
    }

    @Override
    public void write(int b) throws IOException
    {
        outputStream.write(b);
        String string = String.valueOf((char) b);
        if (!string.trim().isEmpty())
            logger.log(logLevel, string);
    }
}