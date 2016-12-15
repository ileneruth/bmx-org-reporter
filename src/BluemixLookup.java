
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class BluemixLookup {

	private static final String AUTHORIZATION = "Authorization";
	private static final String HTTPS = "HTTPS";
	private static String bmxUsername;

	private static final String LOGINURL = "UAALoginServerWAR/oauth/token";

	static String access_token = null;

	static HashMap<String, String> region2login = new HashMap<String, String>();
	static {
		region2login.put("eu-gb", "login.eu-gb.bluemix.net");
		region2login.put("ng", "login.ng.bluemix.net");
		region2login.put("aus", "login.au-syd.bluemix.net");
	}

	static HashMap<String, String> region2api = new HashMap<String, String>();
	static {
		region2api.put("eu-gb", "api.eu-gb.bluemix.net");
		region2api.put("ng", "api.ng.bluemix.net");
		region2api.put("aus", "api.au-syd.bluemix.net");
	}

	static HashMap<String, String> region2mccp = new HashMap<String, String>();
	static {
		region2mccp.put("eu-gb", "mccp.eu-gb.bluemix.net");
		region2mccp.put("ng", "mccp.ng.bluemix.net");
		region2mccp.put("aus", "mccp.au-syd.bluemix.net");
	}

	static String ORGFILENAME = "BMXOrganizations";
	static String SPACEFILENAME = "BMXSpaces";

	static Console console = System.console();
//	public static void main(String[] args) {
//		getBluemixOrgs(mgr, mgrGUID, EU);
//	}

	// convert InputStream to String
	private static String getStringFromInputStream(InputStream is) {

		if (is == null) {
			return "";
		}

		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();

		String line;
		try {
			br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return sb.toString();
	}

	/**
	 * Get access token for invoking Bluemix CF REST endpoints
	 * @param region
	 */
	static void getAccessToken(String region) {
		try {

			char[] bmxPassword = null;
			while ((bmxUsername == null) || (bmxPassword == null)) {
				bmxUsername = console.readLine("Bluemix userid: ");
				bmxPassword = console.readPassword("Bluemix password: ");
			}

			URL url = new URL(HTTPS, region2login.get(region), LOGINURL);
			// System.out.println(url);

			Map<String, Object> params = new LinkedHashMap<>();
			params.put("grant_type", "password");
			params.put("username", bmxUsername);
			params.put("password", new String(bmxPassword));

			StringBuilder postData = new StringBuilder();
			for (Map.Entry<String, Object> param : params.entrySet()) {
				if (postData.length() != 0)
					postData.append('&');
				postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
				postData.append('=');
				postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
			}
			byte[] postDataBytes = postData.toString().getBytes("UTF-8");

			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
			conn.setRequestProperty(AUTHORIZATION, "Basic Y2Y6");
			conn.setDoOutput(true);
			conn.getOutputStream().write(postDataBytes);

			int responseCode = conn.getResponseCode();

			if (responseCode != 200) {
				InputStream errorStream = conn.getErrorStream();
				System.out.println("Failed to get Bluemix Token");
				System.out.println();
				System.out.println("Response Code: " + conn.getResponseCode());
				System.out.println(getStringFromInputStream(errorStream));
				return;
			}

			ObjectMapper mapper = new ObjectMapper();
			Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
			JsonNode response = (JsonNode) mapper.readTree(in);
			access_token = response.get("access_token").textValue();
			
			System.out.println("\nCredentials accepted!\n");
			return;

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Invoke supplied URL as GET, passing access token in Authorization Header 
	 * and returning response.
	 * @param url
	 * @return
	 * @throws IOException
	 */
	static InputStreamReader invoke(URL url) throws IOException {

		System.out.println("Invoking: " + url);

		HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setRequestProperty(AUTHORIZATION, "Bearer " + access_token);
		conn.setDoOutput(true);

		int responseCode = conn.getResponseCode();

		if (responseCode != 200) {
			InputStream errorStream = conn.getErrorStream();
			System.out.println("Invoke failed.");
			System.out.println();
			System.out.println("Response Code: " + conn.getResponseCode());
			System.out.println(getStringFromInputStream(errorStream));
			return null;
		}

		return new InputStreamReader(conn.getInputStream(), "UTF-8");
	}

	/**
	 * Lookup the list of public Bluemix Organizations managed by the logged in user
	 * @param region Region to search
	 * @return A map of Organization GUID and Organization name pairs.
	 */
	static Map<String, String> getBluemixOrgs(String region) {
		try {

			while (access_token == null) {
				getAccessToken(region);
			}
			
			// Find user guid.  Only way I can figure out how to do this is to examine the organization
			// details and pull out information on the user that matches the specified manager
			String mgrGuid = BluemixLookup.getUserGUID(bmxUsername, region);
			
			System.out.println("\n\nLooking up Bluemix Organizations managed by: " + bmxUsername + " in " + region + " region\n\n");

			URL url = new URL(HTTPS, region2api.get(region),
					"/v2/organizations?q=manager_guid:"+mgrGuid);
			InputStreamReader reader = invoke(url);

			if (reader == null) {
				return null;
			}

			/**
			 * Navigate the response to get a listing of Bluemix organizations.
			 */
			ObjectMapper mapper = new ObjectMapper();
			Reader in = new BufferedReader(reader);
			JsonNode response = (JsonNode) mapper.readTree(in);
			ArrayNode resources = (ArrayNode) response.get("resources");

			HashMap<String, String> orgs = new HashMap<String, String>();
			Iterator<JsonNode> iter = resources.iterator();
			JsonNode bmxOrg = null;
			JsonNode entity = null;
			JsonNode metadata = null;
			String bmxOrgName = null;
			String bmxOrgGuid = null;
			while (iter.hasNext()) {
				bmxOrg = iter.next();
				entity = bmxOrg.get("entity");
				bmxOrgName = entity.get("name").textValue();
				metadata = bmxOrg.get("metadata");
				bmxOrgGuid = metadata.get("guid").textValue();
				orgs.put(bmxOrgGuid, bmxOrgName);
				System.out.println(bmxOrgName + ": " + bmxOrgGuid);
			}
			return orgs;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Lookup the list of Bluemix spaces in the specified Bluemix Organization for the 
	 * specified region.
	 * @param bmxOrgGuid GUID of Bluemix Organization
	 * @param bmxOrgName Name of Bluemix Organization
	 * @param region Region to search
	 * @return A map of Bluemix space GUID and name pairs.
	 */
	static Map<String, String> getBluemixSpaces(String bmxOrgGuid, String bmxOrgName, String region) {
		try {
			URL url = new URL(HTTPS, region2mccp.get(region), "/v2/organizations/" + bmxOrgGuid + "/spaces");
			Reader reader = invoke(url);
			if (reader == null) {
				System.out.println("No spaces found in bluemix organization: " + bmxOrgName + "\n\n");
				return null;
			}

			/**
			 * Navigate the response to get a list of Bluemix spaces for the Org and region.
			 */
			ObjectMapper mapper = new ObjectMapper();
			Reader in = new BufferedReader(reader);
			JsonNode response = (JsonNode) mapper.readTree(in);
			ArrayNode resources = (ArrayNode) response.get("resources");

			HashMap<String, String> spaces = new HashMap<String, String>();
			Iterator<JsonNode> iter = resources.iterator();
			JsonNode metadata = null;
			JsonNode entity = null;
			JsonNode space = null;
			String spaceGuid = null;
			String spaceName = null;
			while (iter.hasNext()) {
				space = iter.next();
				entity = space.get("entity");
				spaceName = entity.get("name").textValue();
				metadata = space.get("metadata");
				spaceGuid = metadata.get("guid").textValue();
				spaces.put(spaceGuid, spaceName);
			}
			return spaces;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}

	static String getService(URL url, String region) {

		try {
			Reader reader = invoke(url);
			if (reader == null) {
				System.out.println("No plan found\n\n");
				return null;
			}

			ObjectMapper mapper = new ObjectMapper();
			Reader in = new BufferedReader(reader);
			JsonNode response = mapper.readTree(in);
			JsonNode entity = response.get("entity");
			String serviceURL = entity.get("service_url").textValue();

			reader = invoke(new URL(HTTPS, region2mccp.get(region), serviceURL));
			in = new BufferedReader(reader);
			response = mapper.readTree(in);
			entity = response.get("entity");
			String serviceName = entity.has("label") ? entity.get("label").textValue() : null;
			if ((serviceName == null) || serviceName.isEmpty()) {
				serviceName = entity.has("description") ? entity.get("description").textValue() : null;
			}

			return serviceName;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Given a Bluemix Org, find the spaces in the Org and the users and roles in each space.
	 * Write this out to a csv file.
	 * @param bmxOrgGuid GUID of Bluemix Org
	 * @param bmxOrgName Name of Bluemix Org
	 * @param region Region
	 */
	static void getSpaceUserRoles(String bmxOrgGuid, String bmxOrgName, String region) {
		try {

			// Find all spaces in the specified org
			Map<String, String> bmxSpaces = getBluemixSpaces(bmxOrgGuid, bmxOrgName, region);
			if (bmxSpaces == null) {
				System.out.println("No spaces found");
				return;
			}

			// Create the file and set headings.
			Writer writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(SPACEFILENAME + "_" + bmxOrgName + "_" + region + ".csv"), "UTF-8"));
			writer.write("Bluemix Organization, Space, User, Developer, Manager, Auditor\n");

			String spaceGuid;
			String spaceName;

			// For each space in the org, look up the users and roles
			for (Map.Entry<String, String> entry : bmxSpaces.entrySet()) {
				spaceGuid = entry.getKey();
				spaceName = entry.getValue();

				System.out.println("\nLooking for users in " + spaceName + ", " + spaceGuid);

				// Need to use mccp in order to get username information. The
				// api endpoints have this disabled.
				URL url = new URL(HTTPS, region2mccp.get(region), "/v2/spaces/" + spaceGuid + "/user_roles");
				Reader reader = invoke(url);
				if (reader == null) {
					System.out.println("No users found for bluemix space: " + spaceName + "\n\n");
					continue;
				}

				// Navigate the list of users, collecting their role details
				ObjectMapper mapper = new ObjectMapper();
				Reader in = new BufferedReader(reader);
				JsonNode response = mapper.readTree(in);
				ArrayNode resources = (ArrayNode) response.get("resources");

				Iterator<JsonNode> iter = resources.iterator();
				JsonNode entity = null;
				JsonNode user = null;
				String username = null;

				while (iter.hasNext()) {
					writer.write(bmxOrgName);
					writer.write(",");
					writer.write(spaceName);
					writer.write(",");
					user = iter.next();
					entity = user.get("entity");
					username = entity.has("username") ? entity.get("username").textValue() : "";
					writer.write(username);
					writer.write(",");
					ArrayNode roles = (ArrayNode) entity.get("space_roles");
					// System.out.println("user: " + username + ", roles: ");

					// If user only has no roles, then the user is just a member
					// of the space
					if (roles.size() == 0) {
						writer.write(",,\n");
						continue;
					}
					Boolean[] roleArray = { Boolean.FALSE, Boolean.FALSE, Boolean.FALSE };
					for (int i = 0; i < roles.size(); i++) {
						String role = roles.get(i).textValue();
						if (role.equals("space_developer")) {
							roleArray[0] = Boolean.TRUE;
						} else if (role.equals("space_manager")) {
							roleArray[1] = Boolean.TRUE;
						} else if (role.equals("space_auditor")) {
							roleArray[2] = Boolean.TRUE;
						} else {
							System.out.println("Unrecognized role: " + role);
						}
					}
					
					// If a user has a role, then put an 'X' in the column for user and role.
					// Must align with the headings.
					if (roleArray[0]) {
						writer.write("X,");
					} else {
						writer.write(",");
					}
					if (roleArray[1]) {
						writer.write("X,");
					} else {
						writer.write(",");
					}
					if (roleArray[2]) {
						writer.write("X");
					}

					// System.out.println();
					writer.write("\n");
				}
			}

			writer.write("\n\n\n\n\n");
			writer.write("Bluemix Organization, Space, Application, Application State\n");

			for (Map.Entry<String, String> entry : bmxSpaces.entrySet()) {
				spaceGuid = entry.getKey();
				spaceName = entry.getValue();

				System.out.println("\nLooking for applications in " + spaceName + ", " + spaceGuid);

				// Need to use mccp in order to get username information. The
				// api endpoints have this disabled.
				URL url = new URL(HTTPS, region2mccp.get(region), "/v2/spaces/" + spaceGuid + "/apps");
				Reader reader = invoke(url);
				if (reader == null) {
					System.out.println("No apps found in bluemix space: " + spaceName + "\n\n");
					continue;
				}

				ObjectMapper mapper = new ObjectMapper();
				Reader in = new BufferedReader(reader);
				JsonNode response = mapper.readTree(in);
				ArrayNode resources = (ArrayNode) response.get("resources");

				Iterator<JsonNode> iter = resources.iterator();
				JsonNode entity = null;
				JsonNode user = null;
				String appname = null;
				String appstate = null;

				while (iter.hasNext()) {
					writer.write(bmxOrgName);
					writer.write(",");
					writer.write(spaceName);
					writer.write(",");
					user = iter.next();
					entity = user.get("entity");
					appname = entity.has("name") ? entity.get("name").textValue() : "";
					writer.write(appname);
					writer.write(",");
					appstate = entity.has("state") ? entity.get("state").textValue() : "";
					writer.write(appstate);
					writer.write("\n");
				}
			}

			writer.write("\n\n\n\n\n");
			writer.write("Bluemix Organization, Space, Service Instance Name, Service Type\n");

			for (Map.Entry<String, String> entry : bmxSpaces.entrySet()) {
				spaceGuid = entry.getKey();
				spaceName = entry.getValue();

				System.out.println("\nLooking for services in " + spaceName + ", " + spaceGuid);

				// Need to use mccp in order to get username information. The
				// api endpoints have this disabled.
				URL url = new URL(HTTPS, region2mccp.get(region), "/v2/spaces/" + spaceGuid + "/service_instances");
				Reader reader = invoke(url);
				if (reader == null) {
					System.out.println("No services found in bluemix space: " + spaceName + "\n\n");
					continue;
				}

				ObjectMapper mapper = new ObjectMapper();
				Reader in = new BufferedReader(reader);
				JsonNode response = mapper.readTree(in);
				ArrayNode resources = (ArrayNode) response.get("resources");

				Iterator<JsonNode> iter = resources.iterator();
				JsonNode entity = null;
				JsonNode user = null;
				String serviceName = null;
				String servicePlanURL = null;

				while (iter.hasNext()) {
					writer.write(bmxOrgName);
					writer.write(",");
					writer.write(spaceName);
					writer.write(",");
					user = iter.next();
					entity = user.get("entity");
					serviceName = entity.has("name") ? entity.get("name").textValue() : "";
					writer.write(serviceName);
					writer.write(",");
					servicePlanURL = entity.has("service_plan_url") ? entity.get("service_plan_url").textValue() : null;
					if (servicePlanURL != null) {
						String service = getService(new URL("HTTPS", region2mccp.get(region), servicePlanURL), region);
						if (service != null) {
							writer.write(service);
						}
					}
					writer.write("\n");
				}
			}

			writer.flush();
			writer.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static void getOrganizationUserRoles(String region) {
		try {

			Map<String, String> bmxOrgs = getBluemixOrgs(region);
			if (bmxOrgs == null) {
				System.out.println("No orgs found");
				return;
			}

			Writer writer = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(ORGFILENAME + "_" + region + ".csv"), "UTF-8"));
			writer.write("Bluemix Organization, User, Manager, Billing Manager, Auditor\n");

			for (Map.Entry<String, String> entry : bmxOrgs.entrySet()) {
				String bmxGuid = entry.getKey();
				String bmxName = entry.getValue();
				
				System.out.println("\n\nPress <space> to collect information on Bluemix Org:  " + bmxName);
				String collect = console.readLine("Press anything else to skip this org: ");
				
				if (!collect.equals(" ")) {
					continue;
				}

				getSpaceUserRoles(bmxGuid, bmxName, region);

				System.out.println("\nLooking for users in " + bmxName + ", " + bmxGuid);

				// Need to use mccp in order to get username information. The
				// api endpoints have this disabled.
				URL url = new URL(HTTPS, region2mccp.get(region), "/v2/organizations/" + bmxGuid + "/user_roles");
				Reader reader = invoke(url);
				if (reader == null) {
					System.out.println("No users found for bluemix organization: " + bmxName + "\n\n");
					continue;
				}

				ObjectMapper mapper = new ObjectMapper();
				Reader in = new BufferedReader(reader);
				JsonNode response = (JsonNode) mapper.readTree(in);
				ArrayNode resources = (ArrayNode) response.get("resources");

				Iterator<JsonNode> iter = resources.iterator();
				JsonNode entity = null;
				JsonNode user = null;
				String username = null;

				while (iter.hasNext()) {
					writer.write(bmxName);
					writer.write(",");
					user = iter.next();
					entity = user.get("entity");
					username = entity.get("username").textValue();
					writer.write(username);
					writer.write(",");
					ArrayNode roles = (ArrayNode) entity.get("organization_roles");
					// System.out.println("user: " + username + ", roles: ");

					// If user only has one role, then this is just the implicit
					// 'user' role
					if (roles.size() == 1) {
						writer.write(",,\n");
						continue;
					}
					// Start at '1' to skip the user role
					Boolean[] roleArray = { Boolean.FALSE, Boolean.FALSE, Boolean.FALSE };
					for (int i = 1; i < roles.size(); i++) {
						String role = roles.get(i).textValue();
						if (role.equals("org_manager")) {
							roleArray[0] = Boolean.TRUE;
						} else if (role.equals("billing_manager")) {
							roleArray[1] = Boolean.TRUE;
						} else if (role.equals("org_auditor")) {
							roleArray[2] = Boolean.TRUE;
						} else {
							System.out.println("Unrecognized role: " + role);
						}
					}
					if (roleArray[0]) {
						writer.write("X,");
					} else {
						writer.write(",");
					}
					if (roleArray[1]) {
						writer.write("X,");
					} else {
						writer.write(",");
					}
					if (roleArray[2]) {
						writer.write("X");
					}

					// System.out.println();
					writer.write("\n");
				}
			}
			writer.flush();
			writer.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String getUserGUID(String mgr, String region) {
		
		String bmxGuid = null;
		try {
			// Get organizations
			URL url = new URL(HTTPS, region2mccp.get(region), "/v2/organizations/");
			Reader reader = invoke(url);
			if (reader == null) {
				System.out.println("No bluemix organizations found\n\n");
				return null;
			}	
			
			ObjectMapper mapper = new ObjectMapper();
			Reader in = new BufferedReader(reader);
			JsonNode response = (JsonNode) mapper.readTree(in);
			ArrayNode resources = (ArrayNode) response.get("resources");

			Iterator<JsonNode> iter = resources.iterator();
			JsonNode org = iter.next();
			JsonNode metadata = org.get("metadata");
		    bmxGuid = metadata.get("guid").textValue(); 
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (bmxGuid == null) {
			return null;
		}
		
		try {
			// Need to use mccp in order to get username information. The
			// api endpoints have this disabled.
			URL url = new URL(HTTPS, region2mccp.get(region), "/v2/organizations/" + bmxGuid + "/users");
			Reader reader = invoke(url);
			if (reader == null) {
				System.out.println("No users found for bluemix organization: " + bmxGuid + "\n\n");
				return null;
			}
			
			ObjectMapper mapper = new ObjectMapper();
			Reader in = new BufferedReader(reader);
			JsonNode response = (JsonNode) mapper.readTree(in);
			ArrayNode resources = (ArrayNode) response.get("resources");

			Iterator<JsonNode> iter = resources.iterator();
			JsonNode entity = null;
			JsonNode user = null;
			JsonNode metadata = null;
			String userGuid = null;

			// search through users in this organization until you find the
			// manager's username.  Then grab the guid.  
			while (iter.hasNext()) {
				user = iter.next();
				entity = user.get("entity");
				if (entity.get("username").textValue().equals(mgr)) {
					metadata = user.get("metadata");
					userGuid = metadata.get("guid").textValue();
					break;
				} else {
					continue;
				}
			}
			return userGuid;
			
			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

}
