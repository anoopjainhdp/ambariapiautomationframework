package com.hwx.utils.validation;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hwx.DemoTest;
import com.hwx.utils.DatabaseManager;
import com.hwx.utils.config.ConfigManager;
import com.hwx.utils.config.ConfigProperties;
import com.hwx.utils.logging.LogManager;

public class DBValidationUtils {

	static LogManager logger = LogManager.getLoggerInstance(DemoTest.class.getSimpleName());
	protected static ConfigManager conf = ConfigManager.getInstance();
	protected static String url ;
	protected static String username ;
	protected static String password ;



	public enum COMPONENTS_STATE {
		INSTALLED("INSTALLED"),
		STARTED("STARTED");

		private final String key;

		COMPONENTS_STATE(String key) {
			this.key = key;
		}

		public String getKey() {
			return this.key;
		}
	}


	static {

		try {
			url = conf.getString(ConfigProperties.JDBC_URL_AMBARI.getKey());
			username = conf.getString(ConfigProperties.JDBC_USERNAME_AMBARI.getKey());
			password = conf.getString(ConfigProperties.JDBC_PASSOWRD_AMBARI.getKey());
			DatabaseManager.openConnection(url, username, password);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Method for Express upgrade , to validate current cluster version
	 * @param clusterName
	 * @param versionToCompare
	 * @return
	 */
	public static boolean verifyCurrentClusterVersion(String clusterName , String versionToCompare) {
		boolean flag = false;
		String version=null;

		try {

			String sqlQuery = "select cl.cluster_name,r.version from repo_version r INNER JOIN cluster_version c ON r.repo_version_id=c.repo_version_id  inner join clusters cl on cl.cluster_id=c.cluster_id where c.state='CURRENT' AND cl.cluster_name=\'"+ clusterName +"\';";
			ResultSet rs = DatabaseManager.executeQuery(sqlQuery);
			while(rs.next()){
				System.out.println("Result rows" + rs.getRow());
				version = (String)rs.getString(2);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		if(versionToCompare.equalsIgnoreCase(version)){
			flag = true;
		}
		return flag;
	}

	/**
	 * Method for express upgrade to validate all hosts versions
	 * @param hostNames
	 * @param versionToCompare
	 * @return
	 */
	public static boolean verifyHostsVersion(List<String> hostNames , String versionToCompare) {
		boolean flag = true;
		Map<String,String> output = new HashMap<String,String>();

		String sqlQuery = "select h.host_name,r.version,v.state from hosts h inner join host_version v on h.host_id=v.host_id inner join repo_version r on v.repo_version_id=r.repo_version_id where state = 'CURRENT';";
		// TODO - Make this query supported on all Ambari DB

		try {
			ResultSet rs = DatabaseManager.executeQuery(sqlQuery);
			while(rs.next()){

				String dbHostName = (String)rs.getString(1);
				String version = (String)rs.getString(2);
				logger.logInfo("DB Host Name : " +dbHostName + " its version is : " +version );
				output.put(dbHostName, version);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}


		for(String hostName : hostNames) {

			flag = output.containsKey(hostName) ;
			if(flag) {
				String hostVersion = output.get(hostName);
				if(!versionToCompare.equalsIgnoreCase(hostVersion)){
					flag = false;
				}
			}
			else {
				logger.logError(String.format("No matching entry in query '%s' output found for host: %s", sqlQuery, hostName));
				return false;
			}
		}

		return flag;

	}

	/**
	 * Verify registered stack version , currently this method validation holds good for one registered stack version
	 * @param versionToCompare
	 * @return
	 */
	public static boolean verifyStackRegisteredRepo(String versionToCompare) {
		boolean flag = false;
		String version=null;

		try {

			String sqlQuery = "select s.stack_name, s.stack_version, r.version, r.display_name from stack s inner join repo_version r on s.stack_id=r.stack_id where r.version=\'"+versionToCompare+"\';";
			ResultSet rs = DatabaseManager.executeQuery(sqlQuery);

			if(rs.next()) {
				version = (String)rs.getString(3);
				logger.logInfo("Current registered stack version :: " +version);
			}
			else{
				logger.logInfo("There is no registered stack repo with version !! " +versionToCompare);;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		if(versionToCompare.equalsIgnoreCase(version)){
			flag = true;
		}
		return flag;
	}

	public static boolean verifyHostComponentsState(String hostName ,String version, String cluster) {

		boolean flag = true;
		String versionNumber;
		String componentName;
		String currentState;

		try {

			String sqlQuery = "select cl.cluster_name,hs.host_name,h.component_name,h.version,h.current_state from hostcomponentstate h inner join hosts hs on hs.host_id=h.host_id inner join clusters cl on cl.cluster_id=h.cluster_id where cl.cluster_name=\'"+cluster+"\"' and hs.host_name=\'"+hostName+"\' order by hs.host_name;";
			ResultSet rs = DatabaseManager.executeQuery(sqlQuery);
			int i = 1;

			logger.logInfo("SQL query result: " +rs.toString());

			while(rs.next()){

				componentName = (String)rs.getString(3);
				versionNumber = (String)rs.getString(4);
				currentState  = (String)rs.getString(5);
				logger.logInfo("Result row: " +i);
				logger.logInfo("Component Name: " +componentName);
				if(!versionNumber.equalsIgnoreCase(version)){
					flag = false;
					logger.logInfo("Expected version for Component: "+componentName + " is " + version + " but actual: " +versionNumber);
					break;
				}

				if( ! (currentState.equalsIgnoreCase(COMPONENTS_STATE.INSTALLED.getKey()) || currentState.equalsIgnoreCase(COMPONENTS_STATE.STARTED.getKey())) ){
					flag = false;
					logger.logInfo("Cuurent state for Component: "+componentName + " is " + currentState + " which is not expected");
					break;
				}

				i = i + 1;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return flag;
	}


}
