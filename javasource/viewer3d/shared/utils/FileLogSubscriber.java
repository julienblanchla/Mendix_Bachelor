package viewer3d.shared.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import com.mendix.logging.LogLevel;
import com.mendix.logging.LogMessage;
import com.mendix.logging.LogSubscriber;


import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System;
import java.io.File;

/**
 * log everything into a file
 * 
 * console logger only keeps limited logs, 
 * we use this file logger to log everything when required.
 * 
 */
public class FileLogSubscriber extends LogSubscriber {
	private static FileLogSubscriber instance = null;
	private static LogLevel logLevel = LogLevel.INFO;
	private volatile boolean stopped = false;
	private static FileOutputStream fileOutputStream = null;

	/**
	 * Creates a new {@link LogSubscriber} that logs to Mendix objects. Constructor
	 * is private to prevent external instantiation, use {@link #getInstance()}
	 * instead.
	 * 
	 * @param logLevel only messages of this log level or higher are logged ('NONE' disables all logging)
	 */
	private FileLogSubscriber(final LogLevel logLevel) {
		super(FileLogSubscriber.class.getName(), logLevel);
		
		// ensure logfile
		try {	
			String formattedDate = Util.getFormatDate(0L);			
	        String fileName = String.format("log_%s.txt", formattedDate.replaceAll(":", ""));

	        // get current dir
	        String dir = System.getProperty("user.dir");
	        String path = String.format("%s/data/tmp", dir);
	        System.out.println(String.format("log file will be generated within folder %s", path));

	        File file = new File(String.format("%s/%s", path, fileName));
            if (file.createNewFile()) {
                System.out.println(String.format("File %s was created", file.getAbsoluteFile()));
            } else {
                System.out.println("File already existed");
            }
            
			fileOutputStream = new FileOutputStream(file, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns the singleton instance of this class. If you want to set the log
	 * level (default is 'INFO'), make sure to do this prior to the first call to
	 * this method, see {@link #setLogLevel(LogLevel)}.
	 * 
	 * @return {@link FileLogSubscriber} instance
	 */
	public static synchronized FileLogSubscriber getInstance() {
		if (FileLogSubscriber.instance == null) {
			FileLogSubscriber.instance = new FileLogSubscriber(FileLogSubscriber.logLevel);
		}
		return FileLogSubscriber.instance;
	}

	/**
	 * Sets the log level of this class. Only messages of this log level or higher
	 * are logged ('NONE' disables all logging). The default log level is 'INFO'.
	 * <p>
	 * This (static) method can only be called <i>before</i> this class is
	 * initialized, i.e. before the first call to {@link #getInstance()}.
	 * 
	 * @param logLevel only messages of this log level or higher are logged ('NONE' disables all logging)
	 */
	public static synchronized void setLogLevel(final LogLevel logLevel) {
		if (FileLogSubscriber.instance != null) {
			throw new IllegalStateException("Log level cannot be set, because this class is already intitialized. "
					+ "Make sure to set the log level before the first call to getInstance().");
		}
		if (logLevel == null) {
			throw new IllegalArgumentException("Parameter 'logLevel' must not be null.");
		}
		FileLogSubscriber.logLevel = logLevel;
	}

	/**
	 * Stops the logging to Mendix objects by this class.
	 */
	public void stop() {
		this.stopped = true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mendix.core.log.LogSubscriber#processMessage(com.mendix.core.log.LogMessage)
	 */
	@Override
	public void processMessage(final LogMessage logMessage) {
		if (!this.stopped) {
			// timestamp, level, node, message, stacktrace 
			String line = String.format("%s, %s, %s, %s, %s\n", 
					Util.getFormatDate(logMessage.timestamp),
					logMessage.level.toString(),
					logMessage.node.name(),
					logMessage.message.toString(),
					convertCause(logMessage.cause));
			try {
				fileOutputStream.write(line.getBytes());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private String convertCause(final Throwable cause) {
		if (cause == null) {
			return null;
		}
		final Writer stringWriter = new StringWriter();
		cause.printStackTrace(new PrintWriter(stringWriter));
		return stringWriter.toString();
	}
}
