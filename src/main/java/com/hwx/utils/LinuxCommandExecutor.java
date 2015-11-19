package com.hwx.utils;

import com.hwx.utils.config.ConfigManager;
import com.hwx.utils.config.ConfigProperties;
import com.hwx.utils.logging.LogManager;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

public class LinuxCommandExecutor {
	public static final String CONSOLE_INTERRUPT_SIGNAL = "!SIGQUIT";
	private static final String SSH_OPTIONS = "-o StrictHostKeyChecking=no -o GSSAPIAuthentication=no";
	protected static LogManager logger = LogManager.getLoggerInstance(LinuxCommandExecutor.class.getSimpleName());
	static String key = ConfigProperties.OPENSTACK_KEY_PATH.getKey();
	protected ConfigManager conf = ConfigManager.getInstance();

	public static int NORMAL_EXIT = 0;

	public enum IGNORE_ERRORS {
		TRUE, FALSE
	}

	protected String userName;
	protected String password;
	protected String[] commands;
	protected int exitCode;
	protected String hostName;

	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String[] getCommands() {
		return commands;
	}

	public void setCommands(String[] commands) {
		this.commands = commands;
	}

	public void setExitCode(int exitCode) {
		this.exitCode = exitCode;
	}

	public int getExitCode() {
		return exitCode;
	}

	public LinuxCommandExecutor(String hostname, String user, String password, String[] commands) {
		this.hostName = hostname;
		this.userName = user;
		this.password = password;
		this.commands = commands;
	}

	public LinuxCommandExecutor(String hostname, String user, String[] commands) {
		this.hostName = hostname;
		this.userName = user;
		this.commands = commands;
	}

	private String executeCommandLocalHost(String[] command, boolean printToConsole, ProcessData.Types_Of_Data type,
			IGNORE_ERRORS ignore_errors) {

		PrintStream out;
		Process process;

		try {
			process = Runtime.getRuntime().exec("bash");
			out = new PrintStream(process.getOutputStream());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		List<String> commandList = Arrays.asList(command);
		logger.logInfo(String.format("Sending commands to host [%s] ", this.getHostName()));
		for (String newCommand : commandList) {
			logger.logInfo(String.format("Sending command [%s]", newCommand));
			if (newCommand.equals(CONSOLE_INTERRUPT_SIGNAL)) {
				break;
			}
			out.println(newCommand);
			out.flush();
		}

		out.flush();
		out.close();
		ProcessData data_of_process = new ProcessData(process, true);
		Object output_of_process = data_of_process.getData(type);

		logger.logInfo(output_of_process.toString());

		int exit_code = data_of_process.getExitCodeValue();
		this.setExitCode(exit_code);
		if (ignore_errors == IGNORE_ERRORS.FALSE && exit_code != NORMAL_EXIT) {
			logger.logError(String.format(
					"*************************Command '%s' failed with exitcode %s ***************************",
					commandList.toString(), exit_code));
		}
		return output_of_process.toString();
	}

	public String executeCommandSequence(String[] command, boolean printToConsole, ProcessData.Types_Of_Data type,
			IGNORE_ERRORS ignore_errors) {

		String sshCommand = null;
		try {
			sshCommand = "ssh " + SSH_OPTIONS + " -i " + conf.getString(key) + " " + this.userName + "@"
					+ this.getHostName();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		System.out.println("ssh command: " + sshCommand);

		PrintStream out;
		Process process;

		try {
			process = Runtime.getRuntime().exec(sshCommand);
			out = new PrintStream(process.getOutputStream());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		List<String> commandList = Arrays.asList(command);
		logger.logInfo(String.format("Sending commands to host [%s] ", this.getHostName()));
		for (String newCommand : commandList) {
			logger.logInfo(String.format("Sending command [%s]", newCommand));
			if (newCommand.equals(CONSOLE_INTERRUPT_SIGNAL)) {
				break;
			}
			out.println(newCommand);
			out.flush();
		}

		out.flush();
		out.close();
		ProcessData data_of_process = new ProcessData(process, true);
		Object output_of_process = data_of_process.getData(type);

		logger.logInfo(output_of_process.toString());

		int exit_code = data_of_process.getExitCodeValue();
		this.setExitCode(exit_code);
		if (ignore_errors == IGNORE_ERRORS.FALSE && exit_code != NORMAL_EXIT) {
			logger.logError(String.format(
					"*************************Command '%s' failed with exitcode %s ***************************",
					commandList.toString(), exit_code));
		}
		return output_of_process.toString();
	}

	public String executeCommandLocalHost(ProcessData.Types_Of_Data type, IGNORE_ERRORS ignore_errors) {

		return executeCommandLocalHost(setCommandsSequence(), true, type, ignore_errors);
	}

	public String executeCommandSequenceRemotely(ProcessData.Types_Of_Data type, IGNORE_ERRORS ignore_errors) {

		return executeCommandSequence(setCommandsSequence(), true, type, ignore_errors);
	}

	private String[] setCommandsSequence() {

		String[] tempCommandSequence = this.getCommands();
		String[] commandsSequence = new String[tempCommandSequence.length + 2];
		commandsSequence[0] = "bash";
		commandsSequence[1] = "-c";
		int j = 0;
		for (int i = 2; i < commandsSequence.length; i++) {
			commandsSequence[i] = tempCommandSequence[j];
			j++;
		}

		return commandsSequence;
	}

	public static String executeCommand(String host, String user, String command) {
		LinuxCommandExecutor commandExecutor= new LinuxCommandExecutor(host, user,new String[] {command});
		String output = commandExecutor.executeCommandSequenceRemotely(ProcessData.Types_Of_Data.OUTPUT, LinuxCommandExecutor.IGNORE_ERRORS.FALSE);
		int exitCode = commandExecutor.getExitCode();
		if(exitCode != 0) {
			logger.logError(String.format("Command %s execution failed with return code %d and output as \n%s", command, exitCode, output));
			throw new RuntimeException("Command " + command + " execution failed with return code " + exitCode + " and output as " + output);
		}
        return output;
	}
}
