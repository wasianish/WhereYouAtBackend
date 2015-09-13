package com.wasianish.whereyouat;

import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import java.net.*;
import com.sun.net.httpserver.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.json.*;

public class Main {
	
	private static String IV = "AAAAAAAAAAAAAAAA";
	private static Map<String,String> grouphashdb = new HashMap<String,String>();
	private static Map<String,String[]> groupmemberdb = new HashMap<String,String[]>();
	private static Map<String,Double[]> grouplongdb = new HashMap<String,Double[]>();
	private static Map<String,Double[]> grouplatdb = new HashMap<String,Double[]>();
	private static Map<String,Calendar[]> grouptimedb = new HashMap<String,Calendar[]>();

	public static void main(String[] args) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(8000),0);
		server.createContext("/get", new GetHandler());
		server.createContext("/update", new PostHandler());
		server.createContext("/newgroup",new NewGroupHandler());
		server.setExecutor(null);
		server.start();
	}

	static class NewGroupHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String uri = exchange.getRequestURI().getQuery();
			Map<String, String> q = Main.URItoMap(uri);
			if(q.containsKey("userid") && q.containsKey("groupid") && q.containsKey("rhash")) {
				Main.newGroup(q.get("groupid"), q.get("rhash"));
				groupmemberdb.put("groupid",new String[]{q.get("userid")});
				grouplongdb.put("groupid",new Double[1]);
				grouplatdb.put("groupid",new Double[1]);
				grouptimedb.put("groupid",new Calendar[1]);
				success(exchange);
			} else {
				fail(exchange);
			}
		}
	}
	
	static class GetHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String uri = exchange.getRequestURI().getQuery();
			Map<String, String> q = Main.URItoMap(uri);
			if(q.containsKey("groupid") && q.containsKey("hash") && q.containsKey("userid")) {
				String groupid = q.get("groupid");
				String hash = q.get("hash");
				String userid = q.get("userid");
				if(Main.isGroupHash(groupid,hash) && Main.isGroupMember(groupid,userid)) {
					String js = Main.getGroupJSON(groupid).toString();
					exchange.sendResponseHeaders(200, js.length());
					OutputStream os = exchange.getResponseBody();
					os.write(js.getBytes());
					os.close();
				} else {
					fail(exchange);
				}
			} else {
				fail(exchange);
			}
		}
	}

	static class PostHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String uri = exchange.getRequestURI().getQuery();
			Map<String,String>q = Main.URItoMap(uri);
			if(q.containsKey("groupid") && q.containsKey("userid") && q.containsKey("long") && q.containsKey("lat") && q.containsKey("hash")) {
				String groupid = q.get("groupid");
				String userid = q.get("userid");
				String slong = q.get("long");
				String slat = q.get("lat");
				String hash = q.get("hash");
				if(Main.isGroupHash(groupid,hash) && Main.isGroupMember(groupid,userid)) {
					Main.updateUser(groupid, userid, Double.parseDouble(slong), Double.parseDouble(slat));
					Main.success(exchange);
				}
			}
		}
	}

	public static void fail(HttpExchange exchange) throws IOException {
		String fail = "Bad Request";
		exchange.sendResponseHeaders(400,fail.length());
		OutputStream os = exchange.getResponseBody();
		os.write(fail.getBytes());
		os.close();
	}

	public static void success(HttpExchange exchange) throws IOException {
		String success = "Success";
		exchange.sendResponseHeaders(200,success.length());
		OutputStream os = exchange.getResponseBody();
		os.write(success.getBytes());
		os.close();
	}
	
	public static void updateUser(String groupid, String userid, Double lon, Double lat) {
		String[] memberdb = groupmemberdb.get(groupid);
		int i = 0;
		for(; i < memberdb.length; i++) {
			if(memberdb[i].equals(userid)) {
				break;
			}
		}
		grouplongdb.get(groupid)[i] = lon;
		grouplatdb.get(groupid)[i] = lat;
		grouptimedb.get(groupid)[i] = Calendar.getInstance();
	}

	public static JsonObject getGroupJSON(String groupid) {
		JsonObjectBuilder out = Json.createObjectBuilder()
		.add("groupid",groupid);
		JsonArrayBuilder ar = Json.createArrayBuilder();
		int n = groupmemberdb.get(groupid).length;
		for(int i = 0; i < n; i++) {
			ar.add(Json.createObjectBuilder()
				.add("userid",groupmemberdb.get(groupid)[i])
				.add("long",grouplongdb.get(groupid)[i].toString())
				.add("lat",grouplatdb.get(groupid)[i].toString())
				.add("time",grouptimedb.get(groupid)[i].getTime().toString()).build());
		}
		out.add("members",ar.build());
		return out.build();
	}
	
	public static boolean newGroup(String groupid, String rhash) {
		if(grouphashdb.containsKey(groupid))
			return false;
		grouphashdb.put(groupid, rhash);
		groupmemberdb.put(groupid, new String[0]);
		grouplongdb.put(groupid, new Double[0]);
		grouplatdb.put(groupid, new Double[0]);
		grouptimedb.put(groupid, new Calendar[0]);
		return true;
	}

	public static boolean addMember(String userid, String groupid, String hash) {
		if(groupmemberdb.containsKey(groupid) && isGroupHash(groupid, hash) && !contains(groupmemberdb.get(groupid),userid)) {
			groupmemberdb.put(groupid,incrementCapacity(groupmemberdb.get(groupid),userid,String.class));
			grouplongdb.put(groupid,incrementCapacity(grouplongdb.get(groupid),0.0,Double.class));
			grouplatdb.put(groupid,incrementCapacity(grouplatdb.get(groupid),0.0D,Double.class));
			grouptimedb.put(groupid,incrementCapacity(grouptimedb.get(groupid),Calendar.getInstance(),Calendar.class));
			return true;
		}
		return false;
	}

	public static boolean isGroupHash(String groupid, String hash) {
		String rhash = grouphashdb.get(groupid);
		String thash;
		try {
			thash = decrypt(hash.getBytes(),rhash);
		} catch(Exception e) {
			return false;
		}
		return rhash.equals(thash.substring(0,rhash.length()));
	}

	public static boolean isGroupMember(String userid, String groupid) {
		return contains(groupmemberdb.get(groupid),userid);
	}

	public static Map<String,String> URItoMap(String uri) {
		Map<String,String> output = new HashMap<String,String>();
		for(String s : uri.split("&")) {
			String[] pair = s.split("=");
			if(pair.length > 1)
				output.put(pair[0],pair[1]);
			else
				output.put(pair[0],"");
		}
		return output;
	}
	
	public static byte[] encrypt(String text, String key) throws Exception {
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding","SunJCE");
		SecretKeySpec skey = new SecretKeySpec(key.getBytes("UTF-8"),"AES");
		cipher.init(Cipher.ENCRYPT_MODE, skey, new IvParameterSpec(IV.getBytes("UTF-8")));
		return cipher.doFinal(text.getBytes("UTF-8"));
	}

	public static String decrypt(byte[] text, String key) throws Exception {
		Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding","SunJCE");
		SecretKeySpec skey = new SecretKeySpec(key.getBytes("UTF-8"),"AES");
		cipher.init(Cipher.DECRYPT_MODE, skey, new IvParameterSpec(IV.getBytes("UTF-8")));
		return new String(cipher.doFinal(text),"UTF-8");
	}

	private static <T> T[] incrementCapacity(T[] ar, T last, Class<T> cls) {
		T[] out = getArray(cls, ar.length+1);
		System.arraycopy(ar, 0, out, 0, ar.length);
		out[ar.length + 1] = last;
		return out;
	}
	
	private static <T> T[] getArray(Class<T> cl, int size) {
		@SuppressWarnings("unchecked")
		T[] out = (T[]) Array.newInstance(cl, size);
		return out;
	}

	public static <T> boolean contains(T[] ar, T a) {
		for(T s : ar) {
			if(s.equals(a))
				return true;
		}
		return false;
	}
}

